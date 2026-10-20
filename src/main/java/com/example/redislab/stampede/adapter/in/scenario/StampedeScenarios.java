package com.example.redislab.stampede.adapter.in.scenario;

import com.example.redislab.lab.Gate;
import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.RedisPeek;
import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.Timeline;
import com.example.redislab.stampede.application.port.in.ReadLessonUseCase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 캐시 스탬피드 시나리오. 캐시가 빈 순간 요청 20개를 동시에 보냅니다.
 */
@Component
public class StampedeScenarios extends ScenarioBook {

    private static final String KEY = "stampede:lesson:" + ID;
    private static final int REQUESTS = 20;

    private final ReadLessonUseCase read;
    private final RedisPeek peek;

    public StampedeScenarios(ReadLessonUseCase read, RedisPeek peek, LabDatabase database, Gate gate, Timeline timeline) {
        super(database, gate, timeline);
        this.read = read;
        this.peek = peek;

        add("stampede", "트래픽 때문에 생기는 경우", "캐시 스탬피드",
                "캐시가 빈 순간 요청 20개가 동시에 오면 DB에 몇 번 가나?", () -> stampede(false));
        add("stampede-lock", "트래픽 때문에 생기는 경우", "스탬피드 대책: 락으로 한 요청만 DB에",
                "Redis SET NX 락을 잡은 한 요청만 DB에 가게 하면?", () -> stampede(true));
    }

    @Override
    protected void clearCaches() {
        peek.clear("stampede:lesson:*", "lock:stampede:lesson:*");
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> state = baseSnapshot();
        state.put("Redis " + KEY, peek.describe(KEY));
        state.put("Redis lock:" + KEY, peek.describe("lock:" + KEY));
        state.put("DB 조회 횟수", database.dbReads());
        return state;
    }

    private Outcome stampede(boolean withLock) {
        database.resetDbReads();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= REQUESTS; i++) {
            threads.add(start(String.format("요청%02d", i), () -> {
                await(go);
                if (withLock) {
                    read.cacheAsideWithLock(ID);
                } else {
                    read.cacheAside(ID);
                }
            }));
        }
        timeline.log("캐시가 빈 상태에서 요청 " + REQUESTS + "개 동시 출발");
        go.countDown();
        threads.forEach(ScenarioBook::join);
        int reads = database.dbReads();
        String verdict = withLock
                ? "DB 조회 " + reads + "번. 락을 잡은 한 요청만 DB에 갔고, 나머지는 잠깐 기다렸다가 채워진 캐시를 읽었습니다."
                : "DB 조회 " + reads + "번. 캐시가 비어 있으니 모두가 동시에 DB로 갔습니다. 인기 키가 만료되는 순간 실제 서비스에서 이렇게 몰립니다.";
        return new Outcome(withLock ? reads == 1 : true, verdict);
    }
}
