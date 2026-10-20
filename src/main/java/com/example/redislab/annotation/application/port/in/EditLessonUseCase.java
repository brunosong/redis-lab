package com.example.redislab.annotation.application.port.in;

import com.example.redislab.annotation.domain.Lesson;

/**
 * 바깥에 @Transactional 이 있고, 그 안에서 @CacheEvict 메서드를 부르는 수정.
 */
public interface EditLessonUseCase {

    String BEFORE_COMMIT = "쓰기: @CacheEvict 메서드는 끝났고 바깥 트랜잭션 커밋 직전";

    /** 기본 캐시 매니저. @CacheEvict 가 커밋 전에 지웁니다. */
    Lesson edit(long id, String title);

    /** transactionAware 캐시 매니저. 지우기가 커밋 뒤로 미뤄집니다. */
    Lesson editTxAware(long id, String title);
}
