package com.example.redislab.lab;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DB 서버 쪽 흉내. 기능 코드가 아니라 연습장 장치입니다.
 *
 * <p>각 기능의 DB 어댑터(adapter/out/db)는 SQL을 직접 날리고, 이 클래스에는 "DB 서버라면 저절로 생기는 일"만 맡깁니다.
 * <ul>
 *   <li>조회 지연: 조회마다 lab.db-delay-ms 만큼 느리게 해서 캐시가 왜 필요한지 보이게 합니다.</li>
 *   <li>조회 횟수: 캐시 스탬피드 때 DB에 몇 번 갔는지 셉니다.</li>
 *   <li>복제 지연: 원본이 커밋된 뒤 lab.replica-lag-ms 만큼 지나서야 복제본(lesson_replica)에 반영됩니다.</li>
 * </ul>
 * 화면 상태 표와 시나리오 결과 확인용 peek 도 여기 있습니다.
 */
@Component
public class LabDatabase {

    /** 상태 표시용 한 행. 각 기능의 domain.Lesson 과 같은 모양으로 찍힙니다. */
    public record Row(long id, String title, int version) {

        @Override
        public String toString() {
            return "'" + title + "' (v" + version + ")";
        }
    }

    private static final RowMapper<Row> MAPPER =
            (rs, i) -> new Row(rs.getLong("id"), rs.getString("title"), rs.getInt("version"));

    private final JdbcTemplate jdbc;
    private final Timeline timeline;
    private final long dbDelayMs;
    private final long replicaLagMs;
    private final AtomicInteger dbReads = new AtomicInteger();
    /** 처음 상태로 되돌릴 때마다 올라갑니다. 앞 시나리오가 예약해 둔 복제가 다음 시나리오에 끼어들지 않게 합니다. */
    private final AtomicInteger epoch = new AtomicInteger();
    private final ScheduledExecutorService replicator =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "복제"));

    public LabDatabase(JdbcTemplate jdbc, Timeline timeline,
                       @Value("${lab.db-delay-ms}") long dbDelayMs,
                       @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        this.jdbc = jdbc;
        this.timeline = timeline;
        this.dbDelayMs = dbDelayMs;
        this.replicaLagMs = replicaLagMs;
    }

    /** 어댑터가 SELECT 하기 전에 부릅니다. 횟수를 세고 일부러 느리게 합니다. */
    public void slowQuery() {
        dbReads.incrementAndGet();
        try {
            Thread.sleep(dbDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 어댑터가 원본을 UPDATE 한 뒤 부릅니다. 트랜잭션 안이면 커밋된 뒤에야 복제가 시작됩니다.
     *
     * @return 트랜잭션 안이라 아직 커밋 전이면 true
     */
    public boolean wrotePrimary(long id) {
        boolean inTx = TransactionSynchronizationManager.isSynchronizationActive();
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
        return inTx;
    }

    /** 화면 상태 표시용. 느리게 하지도, 횟수를 세지도 않습니다. */
    public Row peekPrimary(long id) {
        return jdbc.queryForObject("SELECT * FROM lesson WHERE id = ?", MAPPER, id);
    }

    public Row peekReplica(long id) {
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
}
