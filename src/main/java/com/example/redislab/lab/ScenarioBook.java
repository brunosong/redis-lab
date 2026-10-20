package com.example.redislab.lab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * 기능마다 하나씩 두는 시나리오 묶음의 바탕. 시나리오마다 처음 상태로 되돌린 뒤 정해진 순서대로 실행하고,
 * 끝났을 때 캐시와 DB가 맞는지 확인합니다.
 *
 * <p>기능 패키지는 이걸 상속해서 시나리오를 add 하고, 자기 캐시를 비우는 법(clearCaches)과
 * 화면 "현재 상태" 표에 무엇을 보일지(snapshot)만 정합니다.
 */
public abstract class ScenarioBook {

    public static final long ID = 1L;

    /** 모든 기능이 lesson 1번, DB, 타임라인을 같이 쓰니 시나리오는 한 번에 하나만 돕니다. */
    private static final Object RUNNING = new Object();

    public record Info(String id, String group, String title, String question) {
    }

    public record Outcome(boolean consistent, String verdict) {
    }

    private record Scenario(Info info, Supplier<Outcome> body) {
    }

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();

    protected final LabDatabase database;
    protected final Gate gate;
    protected final Timeline timeline;

    protected ScenarioBook(LabDatabase database, Gate gate, Timeline timeline) {
        this.database = database;
        this.gate = gate;
        this.timeline = timeline;
    }

    /** 이 기능이 쓰는 캐시를 비웁니다. reset 이 DB를 되돌린 뒤 부릅니다. */
    protected abstract void clearCaches();

    /** lesson 1번이 이 기능의 어디에 어떤 값으로 들어 있는지. 화면 위쪽 "현재 상태" 표입니다. */
    public abstract Map<String, Object> snapshot();

    public List<Info> list() {
        return scenarios.values().stream().map(Scenario::info).toList();
    }

    public void reset() {
        gate.clear();
        database.reset();
        clearCaches();
    }

    public ScenarioResult run(String id) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            throw new IllegalArgumentException("없는 시나리오: " + id);
        }
        synchronized (RUNNING) {
            reset();
            timeline.start(scenario.info().title());
            Outcome outcome;
            try {
                outcome = scenario.body().get();
            } finally {
                gate.clear();
            }
            timeline.note("");
            timeline.note((outcome.consistent() ? "[맞음] " : "[어긋남] ") + outcome.verdict());
            return new ScenarioResult(id, scenario.info().title(), outcome.consistent(), outcome.verdict(),
                    timeline.lines(), snapshot());
        }
    }

    // ---------------------------------------------------------------- 도우미

    protected void add(String id, String group, String title, String question, Supplier<Outcome> body) {
        scenarios.put(id, new Scenario(new Info(id, group, title, question), body));
    }

    /**
     * 사용자가 본 값과 DB 원본을 비교합니다. 기능마다 domain.Lesson 이 따로 있어서,
     * 화면에 찍히는 모양('제목' (v버전))으로 비교합니다.
     */
    protected Outcome compare(Object seen, String verdict) {
        LabDatabase.Row db = database.peekPrimary(ID);
        timeline.log("확인: 사용자가 본 값 " + seen + " / DB 원본 " + db);
        return new Outcome(String.valueOf(seen).equals(db.toString()), verdict);
    }

    /** Redis 키에 남은 값과 DB 원본을 비교합니다. 키가 비어 있으면 다음 조회가 DB에서 채우니 맞는 것으로 봅니다. */
    protected Outcome compareRedis(RedisPeek peek, String key, String verdict) {
        Optional<LabDatabase.Row> cached = peek.row(key);
        LabDatabase.Row db = database.peekPrimary(ID);
        timeline.log("확인: Redis " + key + " = " + cached.map(LabDatabase.Row::toString).orElse("(없음)") + " / DB 원본 " + db);
        return new Outcome(cached.isEmpty() || cached.get().equals(db), verdict);
    }

    /** 모든 상태 표에 공통으로 들어가는 DB 원본 줄로 시작하는 표를 만듭니다. */
    protected Map<String, Object> baseSnapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("DB 원본", database.peekPrimary(ID).toString());
        return state;
    }

    protected static Thread start(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.start();
        return thread;
    }

    protected static void join(Thread thread) {
        try {
            thread.join(15_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static void waitUntil(Supplier<Boolean> condition, long timeoutMs) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.get() && System.currentTimeMillis() < until) {
            sleep(20);
        }
    }
}
