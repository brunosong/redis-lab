package com.example.redislab.manual.application;

import com.example.redislab.lab.Gate;
import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.Timeline;
import com.example.redislab.manual.application.port.in.EditLessonUseCase;
import com.example.redislab.manual.application.port.out.LessonCachePort;
import com.example.redislab.manual.application.port.out.LessonDbPort;
import com.example.redislab.manual.domain.Lesson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 쓰기 흐름만 담습니다. 커밋과 캐시 지우기의 순서가 이 클래스에서 정해집니다.
 */
@Service
public class EditLessonService implements EditLessonUseCase {

    private final LessonDbPort db;
    private final LessonCachePort cache;
    private final TransactionTemplate tx;
    private final Gate gate;
    private final Timeline timeline;
    /** 지연 이중 삭제가 "처음부터" 뒤에 끼어들지 않게 확인하는 데만 씁니다. */
    private final LabDatabase labDatabase;
    private final long replicaLagMs;
    private final ScheduledExecutorService delayed =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "지연삭제"));

    public EditLessonService(LessonDbPort db, LessonCachePort cache, TransactionTemplate tx, Gate gate,
                             Timeline timeline, LabDatabase labDatabase,
                             @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        this.db = db;
        this.cache = cache;
        this.tx = tx;
        this.gate = gate;
        this.timeline = timeline;
        this.labDatabase = labDatabase;
        this.replicaLagMs = replicaLagMs;
    }

    @Override
    public Lesson dbOnly(long id, String title) {
        Lesson updated = tx.execute(s -> db.update(id, title));
        timeline.log("캐시는 건드리지 않음");
        return updated;
    }

    @Override
    public Lesson evictAfterCommit(long id, String title) {
        return tx.execute(s -> {
            Lesson updated = db.update(id, title);
            afterCommit(() -> cache.delete(id));
            return updated;
        });
    }

    @Override
    public Lesson evictBeforeCommit(long id, String title) {
        cache.delete(id);
        return tx.execute(s -> {
            Lesson updated = db.update(id, title);
            gate.pass(BEFORE_COMMIT);
            return updated;
        });
    }

    @Override
    public Lesson writeThrough(long id, String title) {
        Lesson updated = tx.execute(s -> db.update(id, title));
        gate.pass(BEFORE_CACHE_PUT);
        cache.put(updated);
        return updated;
    }

    /** 복제본에서 읽은 옛 값이 그 사이 캐시에 들어가도 두 번째 삭제가 치웁니다. */
    @Override
    public Lesson evictTwice(long id, String title) {
        Lesson updated = evictAfterCommit(id, title);
        long delay = replicaLagMs + 500;
        timeline.log(delay + "ms 뒤에 한 번 더 지우도록 예약");
        int epoch = labDatabase.epoch();
        delayed.schedule(() -> {
            if (epoch == labDatabase.epoch()) {
                cache.delete(id);
            }
        }, delay, TimeUnit.MILLISECONDS);
        return updated;
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
