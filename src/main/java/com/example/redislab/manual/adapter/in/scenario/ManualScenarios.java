package com.example.redislab.manual.adapter.in.scenario;

import com.example.redislab.lab.Gate;
import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.RedisPeek;
import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.Timeline;
import com.example.redislab.manual.application.port.in.EditLessonUseCase;
import com.example.redislab.manual.application.port.in.ReadLessonUseCase;
import com.example.redislab.manual.domain.Lesson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 직접 짠 Redis 캐시의 정합성 시나리오. 화면과 같은 바깥쪽(in 어댑터)이라 유스케이스만 부릅니다.
 */
@Component
public class ManualScenarios extends ScenarioBook {

    private static final String KEY = "lesson:" + ID;

    private final ReadLessonUseCase read;
    private final EditLessonUseCase write;
    private final RedisPeek peek;
    private final long replicaLagMs;

    public ManualScenarios(ReadLessonUseCase read, EditLessonUseCase write, RedisPeek peek,
                           LabDatabase database, Gate gate, Timeline timeline,
                           @Value("${lab.replica-lag-ms}") long replicaLagMs) {
        super(database, gate, timeline);
        this.read = read;
        this.write = write;
        this.peek = peek;
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

        add("replica-lag", "3. 복제본 때문에 생기는 경우", "복제본에서 읽어 캐시를 채우면",
                "원본에 커밋하고 지웠는데, 바로 다음 조회가 아직 안 따라온 복제본을 읽으면?", this::replicaLag);
        add("replica-lag-evict-twice", "3. 복제본 때문에 생기는 경우", "복제 지연 대책: 지연 이중 삭제",
                "복제가 끝날 즈음 한 번 더 지우면?", this::replicaLagEvictTwice);
    }

    @Override
    protected void clearCaches() {
        peek.clear("lesson:*");
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> state = baseSnapshot();
        state.put("DB 복제본", database.peekReplica(ID).toString());
        state.put("Redis " + KEY, peek.describe(KEY));
        state.put("DB 조회 횟수", database.dbReads());
        return state;
    }

    // ---------------------------------------------------------------- 1. 기본

    private Outcome cacheHit() {
        timeline.log("--- 캐시 없이 세 번 ---");
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            read.noCache(ID);
        }
        long noCacheMs = System.currentTimeMillis() - t0;
        int noCacheReads = database.dbReads();

        database.resetDbReads();
        timeline.log("--- Cache-Aside 로 세 번 ---");
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            read.cacheAside(ID);
        }
        long cachedMs = System.currentTimeMillis() - t1;
        int cachedReads = database.dbReads();

        return new Outcome(true, String.format(
                "캐시 없음: DB %d번, %dms / 캐시 사용: DB %d번, %dms. 첫 번째만 DB에 가고 나머지는 Redis에서 바로 옵니다.",
                noCacheReads, noCacheMs, cachedReads, cachedMs));
    }

    private Outcome dbOnly() {
        read.cacheAside(ID);
        write.dbOnly(ID, "1단원 일차방정식 (개정)");
        Lesson seen = read.cacheAside(ID);
        Long ttl = peek.ttlSeconds(KEY);
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
        gate.arm(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> write.evictBeforeCommit(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(EditLessonUseCase.BEFORE_COMMIT, "쓰기");

        Thread reader = start("읽기", () -> read.cacheAside(ID));
        join(reader);

        gate.release(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        join(writer);
        return compareRedis(peek, KEY, "캐시를 먼저 지운 뒤 커밋 전 틈에 읽기가 들어와, 아직 커밋 안 된 탓에 "
                + "옛 값을 읽어 캐시에 다시 넣었습니다. 쓰기는 이미 지웠으니 TTL이 끝날 때까지 옛 값이 남습니다.");
    }

    private Outcome evictAfterCommitGap() {
        gate.arm(ReadLessonUseCase.BEFORE_PUT, "읽기");
        Thread reader = start("읽기", () -> read.cacheAside(ID));
        gate.awaitArrival(ReadLessonUseCase.BEFORE_PUT, "읽기");

        Thread writer = start("쓰기", () -> write.evictAfterCommit(ID, "1단원 일차방정식 (개정)"));
        join(writer);

        gate.release(ReadLessonUseCase.BEFORE_PUT, "읽기");
        join(reader);
        Long ttl = peek.ttlSeconds(KEY);
        return compareRedis(peek, KEY, "읽기가 옛 값을 들고 있는 사이 쓰기가 커밋하고 지웠고(지울 게 없었음), "
                + "그 뒤 읽기가 옛 값을 넣었습니다. 실제로는 DB 읽기와 캐시 넣기 사이가 아주 짧아 드물지만 0은 아닙니다. "
                + "그래서 정석을 써도 TTL(" + ttl + "초 남음)을 안전장치로 겁니다.");
    }

    private Outcome writeThroughRace() {
        read.cacheAside(ID);
        gate.arm(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기A");
        gate.arm(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기B");

        Thread a = start("쓰기A", () -> write.writeThrough(ID, "A안"));
        gate.awaitArrival(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기A");
        Thread b = start("쓰기B", () -> write.writeThrough(ID, "B안"));
        gate.awaitArrival(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기B");

        gate.release(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기B");
        join(b);
        gate.release(EditLessonUseCase.BEFORE_CACHE_PUT, "쓰기A");
        join(a);
        return compareRedis(peek, KEY, "DB에는 나중에 커밋한 B안이 남았는데, 캐시에는 늦게 넣은 A안이 남았습니다. "
                + "DB 순서와 캐시 순서가 달라서입니다. 지우기 방식이었다면 둘 다 DEL 이라 순서가 꼬여도 다음 조회가 DB의 B안을 가져갑니다. "
                + "@CachePut 도 같은 문제가 있습니다.");
    }

    // ---------------------------------------------------------------- 3. 복제본 때문에 생기는 경우

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
}
