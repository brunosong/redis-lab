package com.example.redislab.scenario;

import com.example.redislab.annotation.AnnotatedLessonStore;
import com.example.redislab.annotation.LessonEditService;
import com.example.redislab.annotation.SelfCallExample;
import com.example.redislab.cache.LessonRedisCache;
import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.local.LocalCacheCluster;
import com.example.redislab.manual.ReadPaths;
import com.example.redislab.manual.WritePaths;
import com.example.redislab.support.Gate;
import com.example.redislab.support.Timeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static com.example.redislab.scenario.LabState.ID;

/**
 * 캐시 정합성 케이스 전체. 시나리오마다 처음 상태로 되돌린 뒤 정해진 순서대로 실행하고,
 * 끝났을 때 캐시와 DB가 맞는지 확인합니다.
 */
@Component
public class Scenarios {

    public record Info(String id, String group, String title, String question) {
    }

    private record Scenario(Info info, Supplier<Outcome> body) {
    }

    private record Outcome(boolean consistent, String verdict) {
    }

    private final Map<String, Scenario> scenarios = new LinkedHashMap<>();

    private final LessonRepository repository;
    private final LessonRedisCache cache;
    private final ReadPaths read;
    private final WritePaths write;
    private final LocalCacheCluster cluster;
    private final AnnotatedLessonStore store;
    private final LessonEditService editService;
    private final SelfCallExample selfCall;
    private final Gate gate;
    private final Timeline timeline;
    private final LabState state;
    private final long replicaLagMs;

    public Scenarios(LessonRepository repository, LessonRedisCache cache, ReadPaths read, WritePaths write,
                     LocalCacheCluster cluster, AnnotatedLessonStore store, LessonEditService editService,
                     SelfCallExample selfCall, Gate gate, Timeline timeline, LabState state,
                     @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        this.repository = repository;
        this.cache = cache;
        this.read = read;
        this.write = write;
        this.cluster = cluster;
        this.store = store;
        this.editService = editService;
        this.selfCall = selfCall;
        this.gate = gate;
        this.timeline = timeline;
        this.state = state;
        this.replicaLagMs = replicaLagMs;

        add("cache-hit", "1. 기본", "캐시가 왜 빠른가",
                "같은 학습 데이터를 세 번 읽을 때, 캐시가 없으면 DB에 몇 번 가나?", this::cacheHit);
        add("db-only", "1. 기본", "DB만 고치고 캐시는 그대로 (TTL만 믿음)",
                "관리자가 제목을 고쳤는데 사용자는 언제까지 옛 제목을 보나?", this::dbOnly);
        add("evict-after-commit", "1. 기본", "정석: 커밋 뒤 캐시 키 삭제",
                "고친 뒤 바로 새 값이 보이나?", this::evictAfterCommit);

        add("evict-before-commit", "2. 순서가 꼬이는 경우", "커밋 전에 지우면",
                "지운 뒤 커밋하기 전, 그 틈에 다른 요청이 읽으면?", this::evictBeforeCommit);
        add("evict-after-commit-gap", "2. 순서가 꼬이는 경우", "정석에도 남는 아주 작은 틈",
                "읽는 쪽이 옛 값을 DB에서 읽은 뒤 캐시에 넣기 전에, 쓰는 쪽이 커밋하고 지우면?", this::evictAfterCommitGap);
        add("write-through-race", "2. 순서가 꼬이는 경우", "지우지 않고 새 값으로 덮어쓰면",
                "두 관리자가 거의 동시에 고치면 캐시에 누구 값이 남나?", this::writeThroughRace);

        add("replica-lag", "3. 구성 때문에 생기는 경우", "복제본에서 읽어 캐시를 채우면",
                "원본에 커밋하고 지웠는데, 바로 다음 조회가 아직 안 따라온 복제본을 읽으면?", this::replicaLag);
        add("replica-lag-evict-twice", "3. 구성 때문에 생기는 경우", "복제 지연 대책: 지연 이중 삭제",
                "복제가 끝날 즈음 한 번 더 지우면?", this::replicaLagEvictTwice);
        add("local-no-broadcast", "3. 구성 때문에 생기는 경우", "로컬 캐시 서버 두 대, 자기 것만 지움",
                "서버A로 수정 요청이 왔을 때 서버B 사용자는 무엇을 보나?", () -> local(false));
        add("local-pubsub", "3. 구성 때문에 생기는 경우", "로컬 캐시 + Redis Pub/Sub 무효화",
                "고친 서버가 모든 서버에 지우라고 알리면?", () -> local(true));

        add("stampede", "4. 트래픽 때문에 생기는 경우", "캐시 스탬피드",
                "캐시가 빈 순간 요청 20개가 동시에 오면 DB에 몇 번 가나?", () -> stampede(false));
        add("stampede-lock", "4. 트래픽 때문에 생기는 경우", "스탬피드 대책: 락으로 한 요청만 DB에",
                "Redis SET NX 락을 잡은 한 요청만 DB에 가게 하면?", () -> stampede(true));

        add("annotation-basic", "5. @Cacheable, @CacheEvict", "어노테이션 기본 동작",
                "@Cacheable 은 언제 메서드 본문을 실행하고, @CacheEvict 는 언제 지우나?", this::annotationBasic);
        add("annotation-evict-in-tx", "5. @Cacheable, @CacheEvict", "@Transactional 안에서 부른 @CacheEvict",
                "바깥 트랜잭션이 커밋되기 전에 @CacheEvict 가 지워 버리면?", this::annotationEvictInTx);
        add("annotation-tx-aware", "5. @Cacheable, @CacheEvict", "대책: transactionAware 캐시 매니저",
                "지우기를 커밋 뒤로 미루면?", this::annotationTxAware);
        add("annotation-self-call", "5. @Cacheable, @CacheEvict", "같은 클래스 안에서 부르면",
                "this.find() 로 부르면 캐시가 먹나?", this::annotationSelfCall);
    }

    public List<Info> list() {
        return scenarios.values().stream().map(Scenario::info).toList();
    }

    public synchronized ScenarioResult run(String id) {
        Scenario scenario = scenarios.get(id);
        if (scenario == null) {
            throw new IllegalArgumentException("없는 시나리오: " + id);
        }
        state.reset();
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
                timeline.lines(), state.snapshot());
    }

    // ---------------------------------------------------------------- 1. 기본

    private Outcome cacheHit() {
        timeline.log("--- 캐시 없이 세 번 ---");
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            read.noCache(ID);
        }
        long noCacheMs = System.currentTimeMillis() - t0;
        int noCacheReads = repository.dbReads();

        repository.resetDbReads();
        timeline.log("--- Cache-Aside 로 세 번 ---");
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            read.cacheAside(ID);
        }
        long cachedMs = System.currentTimeMillis() - t1;
        int cachedReads = repository.dbReads();

        return new Outcome(true, String.format(
                "캐시 없음: DB %d번, %dms / 캐시 사용: DB %d번, %dms. 첫 번째만 DB에 가고 나머지는 Redis에서 바로 옵니다.",
                noCacheReads, noCacheMs, cachedReads, cachedMs));
    }

    private Outcome dbOnly() {
        read.cacheAside(ID);
        write.dbOnly(ID, "1단원 일차방정식 (개정)");
        Lesson seen = read.cacheAside(ID);
        Long ttl = cache.ttlSeconds(LessonRedisCache.key(ID));
        return compare(seen, "사용자는 옛 값을 봅니다. TTL이 끝나는 " + ttl
                + "초 뒤에야 새 값이 보입니다. \"잠깐 옛 값이 보여도 되는 데이터\"라면 이것도 선택지입니다.");
    }

    private Outcome evictAfterCommit() {
        read.cacheAside(ID);
        write.evictAfterCommit(ID, "1단원 일차방정식 (개정)");
        Lesson seen = read.cacheAside(ID);
        return compare(seen, "커밋 뒤 키를 지웠으니 다음 조회가 DB에서 새 값을 읽어 다시 채웠습니다.");
    }

    // ---------------------------------------------------------------- 2. 순서가 꼬이는 경우

    private Outcome evictBeforeCommit() {
        read.cacheAside(ID);
        gate.arm(WritePaths.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> write.evictBeforeCommit(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(WritePaths.BEFORE_COMMIT, "쓰기");

        Thread reader = start("읽기", () -> read.cacheAside(ID));
        join(reader);

        gate.release(WritePaths.BEFORE_COMMIT, "쓰기");
        join(writer);
        return compareRedis(LessonRedisCache.key(ID), "캐시를 먼저 지운 뒤 커밋 전 틈에 읽기가 들어와, 아직 커밋 안 된 탓에 "
                + "옛 값을 읽어 캐시에 다시 넣었습니다. 쓰기는 이미 지웠으니 TTL이 끝날 때까지 옛 값이 남습니다.");
    }

    private Outcome evictAfterCommitGap() {
        gate.arm(ReadPaths.BEFORE_PUT, "읽기");
        Thread reader = start("읽기", () -> read.cacheAside(ID));
        gate.awaitArrival(ReadPaths.BEFORE_PUT, "읽기");

        Thread writer = start("쓰기", () -> write.evictAfterCommit(ID, "1단원 일차방정식 (개정)"));
        join(writer);

        gate.release(ReadPaths.BEFORE_PUT, "읽기");
        join(reader);
        Long ttl = cache.ttlSeconds(LessonRedisCache.key(ID));
        return compareRedis(LessonRedisCache.key(ID), "읽기가 옛 값을 들고 있는 사이 쓰기가 커밋하고 지웠고(지울 게 없었음), "
                + "그 뒤 읽기가 옛 값을 넣었습니다. 실제로는 DB 읽기와 캐시 넣기 사이가 아주 짧아 드물지만 0은 아닙니다. "
                + "그래서 정석을 써도 TTL(" + ttl + "초 남음)을 안전장치로 겁니다.");
    }

    private Outcome writeThroughRace() {
        read.cacheAside(ID);
        gate.arm(WritePaths.BEFORE_CACHE_PUT, "쓰기A");
        gate.arm(WritePaths.BEFORE_CACHE_PUT, "쓰기B");

        Thread a = start("쓰기A", () -> write.writeThrough(ID, "A안"));
        gate.awaitArrival(WritePaths.BEFORE_CACHE_PUT, "쓰기A");
        Thread b = start("쓰기B", () -> write.writeThrough(ID, "B안"));
        gate.awaitArrival(WritePaths.BEFORE_CACHE_PUT, "쓰기B");

        gate.release(WritePaths.BEFORE_CACHE_PUT, "쓰기B");
        join(b);
        gate.release(WritePaths.BEFORE_CACHE_PUT, "쓰기A");
        join(a);
        return compareRedis(LessonRedisCache.key(ID), "DB에는 나중에 커밋한 B안이 남았는데, 캐시에는 늦게 넣은 A안이 남았습니다. "
                + "DB 순서와 캐시 순서가 달라서입니다. 지우기 방식이었다면 둘 다 DEL 이라 순서가 꼬여도 다음 조회가 DB의 B안을 가져갑니다. "
                + "@CachePut 도 같은 문제가 있습니다.");
    }

    // ---------------------------------------------------------------- 3. 구성 때문에 생기는 경우

    private Outcome replicaLag() {
        read.cacheAsideFromReplica(ID);
        join(start("쓰기", () -> write.evictAfterCommit(ID, "1단원 일차방정식 (개정)")));
        join(start("읽기", () -> read.cacheAsideFromReplica(ID)));
        timeline.log("복제가 끝날 때까지 기다림...");
        sleep(replicaLagMs + 300);
        Lesson seen = read.cacheAsideFromReplica(ID);
        return compare(seen, "지운 직후 읽기가 아직 안 따라온 복제본에서 옛 값을 읽어 캐시에 넣었습니다. "
                + "복제본은 곧 따라왔지만 캐시는 TTL이 끝날 때까지 옛 값입니다. 대책은 캐시를 채울 때만 원본에서 읽거나, 지연 이중 삭제입니다.");
    }

    private Outcome replicaLagEvictTwice() {
        read.cacheAsideFromReplica(ID);
        join(start("쓰기", () -> write.evictTwice(ID, "1단원 일차방정식 (개정)")));
        join(start("읽기", () -> read.cacheAsideFromReplica(ID)));
        timeline.log("두 번째 삭제가 돌 때까지 기다림...");
        sleep(replicaLagMs + 900);
        Lesson seen = read.cacheAsideFromReplica(ID);
        return compare(seen, "첫 삭제 직후에는 여전히 옛 값이 들어갔지만(잠깐 어긋남), 복제가 끝난 뒤 두 번째 삭제가 그걸 치웠고 "
                + "다음 조회가 따라온 복제본에서 새 값을 읽었습니다. 어긋나는 시간이 TTL 전체에서 복제 지연 정도로 줄어듭니다.");
    }

    private Outcome local(boolean broadcast) {
        cluster.read("서버A", ID);
        cluster.read("서버B", ID);
        cluster.update("서버A", ID, "1단원 일차방정식 (개정)", broadcast);
        if (broadcast) {
            waitUntil(() -> cluster.peek("서버B", ID) == null, 2000);
        }
        Lesson onA = cluster.read("서버A", ID);
        Lesson onB = cluster.read("서버B", ID);
        Lesson db = repository.peekPrimary(ID);
        boolean ok = onA.equals(db) && onB.equals(db);
        String verdict = broadcast
                ? "서버A가 커밋 뒤 Pub/Sub 으로 알렸고, 두 서버가 각자 자기 로컬 캐시를 지웠습니다. 다만 Pub/Sub 은 보내고 끝이라, "
                  + "그 순간 연결이 끊긴 서버는 메시지를 못 받습니다. 그래서 로컬 캐시에도 짧은 만료 시간을 같이 겁니다."
                : "서버A 사용자는 새 값, 서버B 사용자는 옛 값을 봅니다. 로드밸런서가 어느 서버로 보내느냐에 따라 새로고침할 때마다 "
                  + "제목이 바뀌어 보입니다.";
        return new Outcome(ok, verdict);
    }

    // ---------------------------------------------------------------- 4. 트래픽 때문에 생기는 경우

    private Outcome stampede(boolean withLock) {
        repository.resetDbReads();
        int n = 20;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            threads.add(start(String.format("요청%02d", i), () -> {
                await(go);
                if (withLock) {
                    read.cacheAsideWithLock(ID);
                } else {
                    read.cacheAside(ID);
                }
            }));
        }
        timeline.log("캐시가 빈 상태에서 요청 " + n + "개 동시 출발");
        go.countDown();
        threads.forEach(Scenarios::join);
        int reads = repository.dbReads();
        String verdict = withLock
                ? "DB 조회 " + reads + "번. 락을 잡은 한 요청만 DB에 갔고, 나머지는 잠깐 기다렸다가 채워진 캐시를 읽었습니다."
                : "DB 조회 " + reads + "번. 캐시가 비어 있으니 모두가 동시에 DB로 갔습니다. 인기 키가 만료되는 순간 실제 서비스에서 이렇게 몰립니다.";
        return new Outcome(withLock ? reads == 1 : true, verdict);
    }

    // ---------------------------------------------------------------- 5. 어노테이션

    private Outcome annotationBasic() {
        timeline.log("store.find(1) 첫 번째");
        store.find(ID);
        timeline.log("store.find(1) 두 번째");
        store.find(ID);
        timeline.log("store.update(1) (@CacheEvict, 바깥 트랜잭션 없음)");
        store.update(ID, "1단원 일차방정식 (개정)");
        timeline.log("store.find(1) 세 번째");
        Lesson seen = store.find(ID);
        return compare(seen, "@Cacheable 은 캐시에 없을 때만 본문을 실행하고 결과를 넣습니다. @CacheEvict 는 메서드가 끝난 뒤 지웁니다. "
                + "바깥 트랜잭션이 없으면 UPDATE 가 바로 커밋되니 문제가 없습니다.");
    }

    private Outcome annotationEvictInTx() {
        store.find(ID);
        gate.arm(LessonEditService.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> editService.edit(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(LessonEditService.BEFORE_COMMIT, "쓰기");

        join(start("읽기", () -> store.find(ID)));

        gate.release(LessonEditService.BEFORE_COMMIT, "쓰기");
        join(writer);
        return compareRedis("lessons::" + ID, "@CacheEvict 는 자기 메서드가 끝나는 순간 지웁니다. 바깥 @Transactional 은 아직 커밋 전이라, "
                + "그 틈에 읽기가 옛 값을 다시 넣었습니다. 코드로는 \"수정하면 캐시를 지운다\"가 맞아 보여서 찾기 어려운 버그입니다.");
    }

    private Outcome annotationTxAware() {
        store.findTxAware(ID);
        gate.arm(LessonEditService.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> editService.editTxAware(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(LessonEditService.BEFORE_COMMIT, "쓰기");

        join(start("읽기", () -> store.findTxAware(ID)));

        gate.release(LessonEditService.BEFORE_COMMIT, "쓰기");
        join(writer);
        timeline.log("다음 조회");
        Lesson seen = store.findTxAware(ID);
        return compare(seen, "transactionAware 캐시 매니저는 트랜잭션 안의 지우기를 커밋 뒤로 미룹니다. 커밋 전 틈에 옛 값이 들어갔어도 "
                + "커밋 뒤 지우기가 치워서 다음 조회는 새 값입니다. 손으로 짠 \"커밋 뒤 삭제\"와 같은 효과입니다.");
    }

    private Outcome annotationSelfCall() {
        repository.resetDbReads();
        timeline.log("--- 같은 클래스 안에서 this.find() 세 번 ---");
        selfCall.renderPage(ID);
        int selfReads = repository.dbReads();

        repository.resetDbReads();
        timeline.log("--- 밖에서 프록시를 거쳐 find() 세 번 ---");
        for (int i = 0; i < 3; i++) {
            selfCall.find(ID);
        }
        int proxyReads = repository.dbReads();
        return new Outcome(proxyReads == 1, "같은 클래스 안에서 부르면 DB " + selfReads + "번, 밖에서 부르면 DB " + proxyReads
                + "번. 캐시는 프록시가 걸어 주는데 this 호출은 프록시를 안 거쳐서, 어노테이션이 아예 없는 것처럼 동작합니다. "
                + "타임라인에 [어노테이션] 줄이 위쪽에는 하나도 없는 걸 보면 됩니다.");
    }

    // ---------------------------------------------------------------- 도우미

    private void add(String id, String group, String title, String question, Supplier<Outcome> body) {
        scenarios.put(id, new Scenario(new Info(id, group, title, question), body));
    }

    private Outcome compare(Lesson seen, String verdict) {
        Lesson db = repository.peekPrimary(ID);
        timeline.log("확인: 사용자가 본 값 " + seen + " / DB 원본 " + db);
        return new Outcome(seen.equals(db), verdict);
    }

    private Outcome compareRedis(String key, String verdict) {
        Optional<Lesson> cached = cache.peek(key);
        Lesson db = repository.peekPrimary(ID);
        timeline.log("확인: Redis " + key + " = " + cached.map(Lesson::toString).orElse("(없음)") + " / DB 원본 " + db);
        boolean ok = cached.isEmpty() || cached.get().equals(db);
        return new Outcome(ok, verdict);
    }

    private static Thread start(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.start();
        return thread;
    }

    private static void join(Thread thread) {
        try {
            thread.join(15_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(Supplier<Boolean> condition, long timeoutMs) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.get() && System.currentTimeMillis() < until) {
            sleep(20);
        }
    }
}
