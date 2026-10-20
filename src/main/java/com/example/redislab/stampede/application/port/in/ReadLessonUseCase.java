package com.example.redislab.stampede.application.port.in;

import com.example.redislab.stampede.domain.Lesson;

/**
 * 인기 키가 비는 순간 요청이 몰릴 때의 읽기.
 */
public interface ReadLessonUseCase {

    /** 락 없는 Cache-Aside. 캐시가 비면 모두가 DB로 갑니다. */
    Lesson cacheAside(long id);

    /** 캐시가 비었을 때 락을 잡은 한 요청만 DB에 가고, 나머지는 캐시가 채워지기를 기다립니다. */
    Lesson cacheAsideWithLock(long id);
}
