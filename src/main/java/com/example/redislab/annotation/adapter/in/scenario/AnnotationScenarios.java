package com.example.redislab.annotation.adapter.in.scenario;

import com.example.redislab.annotation.application.port.in.CachedLessonUseCase;
import com.example.redislab.annotation.application.port.in.EditLessonUseCase;
import com.example.redislab.annotation.application.port.in.RenderPageUseCase;
import com.example.redislab.annotation.domain.Lesson;
import com.example.redislab.lab.Gate;
import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.RedisPeek;
import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.Timeline;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Cacheable, @CacheEvict 시나리오.
 */
@Component
public class AnnotationScenarios extends ScenarioBook {

    private static final String KEY = "lessons::" + ID;
    private static final String TX_KEY = "lessons-tx::" + ID;

    private final CachedLessonUseCase store;
    private final EditLessonUseCase editor;
    private final RenderPageUseCase page;
    private final RedisPeek peek;

    public AnnotationScenarios(CachedLessonUseCase store, EditLessonUseCase editor, RenderPageUseCase page,
                               RedisPeek peek, LabDatabase database, Gate gate, Timeline timeline) {
        super(database, gate, timeline);
        this.store = store;
        this.editor = editor;
        this.page = page;
        this.peek = peek;

        add("annotation-basic", "@Cacheable, @CacheEvict", "어노테이션 기본 동작",
                "@Cacheable 은 언제 메서드 본문을 실행하고, @CacheEvict 는 언제 지우나?", this::basic);
        add("annotation-evict-in-tx", "@Cacheable, @CacheEvict", "@Transactional 안에서 부른 @CacheEvict",
                "바깥 트랜잭션이 커밋되기 전에 @CacheEvict 가 지워 버리면?", this::evictInTx);
        add("annotation-tx-aware", "@Cacheable, @CacheEvict", "대책: transactionAware 캐시 매니저",
                "지우기를 커밋 뒤로 미루면?", this::txAware);
        add("annotation-self-call", "@Cacheable, @CacheEvict", "같은 클래스 안에서 부르면",
                "this.find() 로 부르면 캐시가 먹나?", this::selfCall);
    }

    @Override
    protected void clearCaches() {
        peek.clear("lessons::*", "lessons-tx::*");
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> state = baseSnapshot();
        state.put("Redis " + KEY + " (기본)", peek.describe(KEY));
        state.put("Redis " + TX_KEY + " (transactionAware)", peek.describe(TX_KEY));
        state.put("DB 조회 횟수", database.dbReads());
        return state;
    }

    private Outcome basic() {
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

    private Outcome evictInTx() {
        store.find(ID);
        gate.arm(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> editor.edit(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(EditLessonUseCase.BEFORE_COMMIT, "쓰기");

        join(start("읽기", () -> store.find(ID)));

        gate.release(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        join(writer);
        return compareRedis(peek, KEY, "@CacheEvict 는 자기 메서드가 끝나는 순간 지웁니다. 바깥 @Transactional 은 아직 커밋 전이라, "
                + "그 틈에 읽기가 옛 값을 다시 넣었습니다. 코드로는 \"수정하면 캐시를 지운다\"가 맞아 보여서 찾기 어려운 버그입니다.");
    }

    private Outcome txAware() {
        store.findTxAware(ID);
        gate.arm(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        Thread writer = start("쓰기", () -> editor.editTxAware(ID, "1단원 일차방정식 (개정)"));
        gate.awaitArrival(EditLessonUseCase.BEFORE_COMMIT, "쓰기");

        join(start("읽기", () -> store.findTxAware(ID)));

        gate.release(EditLessonUseCase.BEFORE_COMMIT, "쓰기");
        join(writer);
        timeline.log("다음 조회");
        Lesson seen = store.findTxAware(ID);
        return compare(seen, "transactionAware 캐시 매니저는 트랜잭션 안의 지우기를 커밋 뒤로 미룹니다. 커밋 전 틈에 옛 값이 들어갔어도 "
                + "커밋 뒤 지우기가 치워서 다음 조회는 새 값입니다. 손으로 짠 \"커밋 뒤 삭제\"와 같은 효과입니다.");
    }

    private Outcome selfCall() {
        database.resetDbReads();
        timeline.log("--- 같은 클래스 안에서 this.find() 세 번 ---");
        page.renderPage(ID);
        int selfReads = database.dbReads();

        database.resetDbReads();
        timeline.log("--- 밖에서 프록시를 거쳐 find() 세 번 ---");
        for (int i = 0; i < 3; i++) {
            page.find(ID);
        }
        int proxyReads = database.dbReads();
        return new Outcome(proxyReads == 1, "같은 클래스 안에서 부르면 DB " + selfReads + "번, 밖에서 부르면 DB " + proxyReads
                + "번. 캐시는 프록시가 걸어 주는데 this 호출은 프록시를 안 거쳐서, 어노테이션이 아예 없는 것처럼 동작합니다. "
                + "타임라인에 [어노테이션] 줄이 위쪽에는 하나도 없는 걸 보면 됩니다.");
    }
}
