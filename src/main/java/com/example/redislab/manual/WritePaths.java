package com.example.redislab.manual;

import com.example.redislab.cache.LessonRedisCache;
import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.support.Gate;
import com.example.redislab.support.Timeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 직접 짠 캐시의 쓰기 방법들. 데이터를 고칠 때 캐시를 어떻게 다루느냐가 정합성을 가릅니다.
 */
@Service
public class WritePaths {

    /** 쓰는 쪽이 DB를 고쳤지만 아직 커밋하지 않은 지점. */
    public static final String BEFORE_COMMIT = "쓰기: DB UPDATE 했고 커밋 직전";
    /** 쓰는 쪽이 커밋했고 캐시에 새 값을 넣기 직전. */
    public static final String BEFORE_CACHE_PUT = "쓰기: 커밋 끝, 캐시에 새 값 넣기 직전";

    private final LessonRepository repository;
    private final LessonRedisCache cache;
    private final TransactionTemplate tx;
    private final Gate gate;
    private final Timeline timeline;
    private final long replicaLagMs;
    private final ScheduledExecutorService delayed =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "지연삭제"));

    public WritePaths(LessonRepository repository, LessonRedisCache cache, TransactionTemplate tx,
                      Gate gate, Timeline timeline, @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        this.repository = repository;
        this.cache = cache;
        this.tx = tx;
        this.gate = gate;
        this.timeline = timeline;
        this.replicaLagMs = replicaLagMs;
    }

    /** 1. DB만 고치고 캐시는 그대로. TTL이 끝날 때까지 옛 값이 보입니다. */
    public Lesson dbOnly(long id, String title) {
        Lesson updated = tx.execute(s -> repository.update(id, title));
        timeline.log("캐시는 건드리지 않음");
        return updated;
    }

    /** 2. 정석. 커밋이 끝난 뒤 캐시 키를 지웁니다. 다음 조회가 DB에서 새 값을 채웁니다. */
    public Lesson evictAfterCommit(long id, String title) {
        return tx.execute(s -> {
            Lesson updated = repository.update(id, title);
            afterCommit(() -> cache.delete(id));
            return updated;
        });
    }

    /** 3. 커밋 전에 먼저 지움. 그 사이 다른 요청이 옛 값을 읽어 다시 채울 수 있습니다. */
    public Lesson evictBeforeCommit(long id, String title) {
        cache.delete(id);
        return tx.execute(s -> {
            Lesson updated = repository.update(id, title);
            gate.pass(BEFORE_COMMIT);
            return updated;
        });
    }

    /** 4. 커밋 뒤 캐시에 새 값을 덮어씀(Write-Through 비슷). 동시에 두 명이 고치면 순서가 꼬일 수 있습니다. */
    public Lesson writeThrough(long id, String title) {
        Lesson updated = tx.execute(s -> repository.update(id, title));
        gate.pass(BEFORE_CACHE_PUT);
        cache.put(updated);
        return updated;
    }

    /**
     * 5. 커밋 뒤 지우고, 복제 지연이 끝날 즈음 한 번 더 지움(지연 이중 삭제).
     * 복제본에서 읽은 옛 값이 그 사이 캐시에 들어가도 두 번째 삭제가 치웁니다.
     */
    public Lesson evictTwice(long id, String title) {
        Lesson updated = evictAfterCommit(id, title);
        long delay = replicaLagMs + 500;
        timeline.log(delay + "ms 뒤에 한 번 더 지우도록 예약");
        int epoch = repository.epoch();
        delayed.schedule(() -> {
            if (epoch == repository.epoch()) {
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
