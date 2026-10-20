package com.example.redislab.annotation;

import com.example.redislab.lesson.Lesson;
import com.example.redislab.support.Gate;
import com.example.redislab.support.Timeline;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 바깥 서비스에 @Transactional 이 있고, 그 안에서 @CacheEvict 가 붙은 메서드를 부르는 흔한 구조.
 * 수정 뒤에 다른 일(이력 저장, 알림 등)을 더 하느라 커밋이 늦어지는 상황을 멈춤 지점으로 흉내 냅니다.
 */
@Service
public class LessonEditService {

    public static final String BEFORE_COMMIT = "쓰기: @CacheEvict 메서드는 끝났고 바깥 트랜잭션 커밋 직전";

    private final AnnotatedLessonStore store;
    private final Gate gate;
    private final Timeline timeline;

    public LessonEditService(AnnotatedLessonStore store, Gate gate, Timeline timeline) {
        this.store = store;
        this.gate = gate;
        this.timeline = timeline;
    }

    @Transactional
    public Lesson edit(long id, String title) {
        Lesson updated = store.update(id, title);
        timeline.log("수정 이력 저장 등 나머지 일 처리 중... (트랜잭션은 아직 열려 있음)");
        gate.pass(BEFORE_COMMIT);
        return updated;
    }

    @Transactional
    public Lesson editTxAware(long id, String title) {
        Lesson updated = store.updateTxAware(id, title);
        timeline.log("수정 이력 저장 등 나머지 일 처리 중... (트랜잭션은 아직 열려 있음)");
        gate.pass(BEFORE_COMMIT);
        return updated;
    }
}
