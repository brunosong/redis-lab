package com.example.redislab.annotation.application;

import com.example.redislab.annotation.application.port.in.CachedLessonUseCase;
import com.example.redislab.annotation.application.port.in.EditLessonUseCase;
import com.example.redislab.annotation.domain.Lesson;
import com.example.redislab.lab.Gate;
import com.example.redislab.lab.Timeline;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 바깥 서비스에 @Transactional 이 있고, 그 안에서 @CacheEvict 가 붙은 메서드를 부르는 흔한 구조.
 * 수정 뒤에 다른 일(이력 저장, 알림 등)을 더 하느라 커밋이 늦어지는 상황을 멈춤 지점으로 흉내 냅니다.
 *
 * <p>@CacheEvict 메서드는 다른 빈(AnnotatedLessonService)에 있어야 프록시를 거쳐 캐시가 동작합니다.
 */
@Service
public class LessonEditService implements EditLessonUseCase {

    private final CachedLessonUseCase store;
    private final Gate gate;
    private final Timeline timeline;

    public LessonEditService(CachedLessonUseCase store, Gate gate, Timeline timeline) {
        this.store = store;
        this.gate = gate;
        this.timeline = timeline;
    }

    @Override
    @Transactional
    public Lesson edit(long id, String title) {
        Lesson updated = store.update(id, title);
        timeline.log("수정 이력 저장 등 나머지 일 처리 중... (트랜잭션은 아직 열려 있음)");
        gate.pass(BEFORE_COMMIT);
        return updated;
    }

    @Override
    @Transactional
    public Lesson editTxAware(long id, String title) {
        Lesson updated = store.updateTxAware(id, title);
        timeline.log("수정 이력 저장 등 나머지 일 처리 중... (트랜잭션은 아직 열려 있음)");
        gate.pass(BEFORE_COMMIT);
        return updated;
    }
}
