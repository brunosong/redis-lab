package com.example.redislab.lesson;

import com.example.redislab.support.Timeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 원본 DB와 복제본 DB를 흉내 냅니다.
 *
 * <p>조회는 일부러 lab.db-delay-ms 만큼 느리게 해서 캐시가 왜 필요한지 보이게 했습니다.
 * 원본을 고치면 커밋된 뒤 lab.replica-lag-ms 만큼 지나서야 복제본에 반영됩니다(복제 지연).
 * DB 조회 횟수를 세어 두어서, 캐시 스탬피드 때 DB에 몇 번 갔는지 볼 수 있습니다.
 */
@Repository
public class LessonRepository {

    private static final RowMapper<Lesson> MAPPER =
            (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version"));

    private final JdbcTemplate jdbc;
    private final Timeline timeline;
    private final long dbDelayMs;
    private final long replicaLagMs;
    private final AtomicInteger dbReads = new AtomicInteger();
    /** 처음 상태로 되돌릴 때마다 올라갑니다. 앞 시나리오가 예약해 둔 복제가 다음 시나리오에 끼어들지 않게 합니다. */
    private final AtomicInteger epoch = new AtomicInteger();
    private final ScheduledExecutorService replicator =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "복제"));

    public LessonRepository(JdbcTemplate jdbc, Timeline timeline,
                            @Value("${lab.db-delay-ms}") long dbDelayMs,
                            @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        this.jdbc = jdbc;
        this.timeline = timeline;
        this.dbDelayMs = dbDelayMs;
        this.replicaLagMs = replicaLagMs;
    }

    public Lesson findFromPrimary(long id) {
        slowQuery();
        Lesson lesson = jdbc.queryForObject("SELECT * FROM lesson WHERE id = ?", MAPPER, id);
        timeline.log("DB(원본) 조회 → " + lesson);
        return lesson;
    }

    public Lesson findFromReplica(long id) {
        slowQuery();
        Lesson lesson = jdbc.queryForObject("SELECT * FROM lesson_replica WHERE id = ?", MAPPER, id);
        timeline.log("DB(복제본) 조회 → " + lesson);
        return lesson;
    }

    /**
     * 원본을 고칩니다. 트랜잭션 안에서 부르면 커밋된 뒤에야 복제가 시작됩니다.
     * 고친 결과를 돌려줍니다(같은 트랜잭션 안이라 자기가 고친 값이 보입니다).
     */
    public Lesson update(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                title, id);
        Lesson updated = peekPrimary(id);
        boolean inTx = TransactionSynchronizationManager.isSynchronizationActive();
        timeline.log("DB(원본) UPDATE → " + updated + (inTx ? " (아직 커밋 전)" : " (바로 커밋)"));
        if (inTx) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    timeline.log("DB(원본) 커밋 완료");
                    scheduleReplication(id);
                }
            });
        } else {
            scheduleReplication(id);
        }
        return updated;
    }

    /** 화면 상태 표시용. 느리게 하지도, 횟수를 세지도 않습니다. */
    public Lesson peekPrimary(long id) {
        return jdbc.queryForObject("SELECT * FROM lesson WHERE id = ?", MAPPER, id);
    }

    public Lesson peekReplica(long id) {
        return jdbc.queryForObject("SELECT * FROM lesson_replica WHERE id = ?", MAPPER, id);
    }

    public int dbReads() {
        return dbReads.get();
    }

    public void resetDbReads() {
        dbReads.set(0);
    }

    public void reset() {
        jdbc.update("UPDATE lesson SET title = '1단원 일차방정식', version = 1 WHERE id = 1");
        jdbc.update("UPDATE lesson SET title = '2단원 연립방정식', version = 1 WHERE id = 2");
        jdbc.update("UPDATE lesson SET title = '3단원 일차함수', version = 1 WHERE id = 3");
        jdbc.update("DELETE FROM lesson_replica");
        jdbc.update("INSERT INTO lesson_replica SELECT * FROM lesson");
        resetDbReads();
        epoch.incrementAndGet();
    }

    public int epoch() {
        return epoch.get();
    }

    private void scheduleReplication(long id) {
        int scheduledAt = epoch.get();
        replicator.schedule(() -> {
            if (scheduledAt != epoch.get()) {
                return;
            }
            jdbc.update("MERGE INTO lesson_replica KEY (id) SELECT * FROM lesson WHERE id = ?", id);
            timeline.log("복제본에 반영됨 → " + peekReplica(id) + " (" + replicaLagMs + "ms 지연)");
        }, replicaLagMs, TimeUnit.MILLISECONDS);
    }

    private void slowQuery() {
        dbReads.incrementAndGet();
        try {
            Thread.sleep(dbDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
