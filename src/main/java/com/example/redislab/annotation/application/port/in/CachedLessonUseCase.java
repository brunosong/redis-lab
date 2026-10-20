package com.example.redislab.annotation.application.port.in;

import com.example.redislab.annotation.domain.Lesson;

/**
 * 어노테이션으로 캐시를 거는 조회와 수정. 위 두 개는 기본 캐시 매니저(lessons),
 * 아래 두 개는 transactionAware 캐시 매니저(lessons-tx)를 씁니다.
 */
public interface CachedLessonUseCase {

    Lesson find(long id);

    Lesson update(long id, String title);

    Lesson findTxAware(long id);

    Lesson updateTxAware(long id, String title);
}
