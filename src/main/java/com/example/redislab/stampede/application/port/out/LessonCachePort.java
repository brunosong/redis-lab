package com.example.redislab.stampede.application.port.out;

import com.example.redislab.stampede.domain.Lesson;

import java.time.Duration;
import java.util.Optional;

/** 캐시와 캐시 채우기 락. 구현은 adapter/out/redis. */
public interface LessonCachePort {

    Optional<Lesson> get(long id);

    void put(Lesson lesson);

    /** 먼저 잡은 한 요청만 true. hold 가 지나면 저절로 풀립니다(잡은 요청이 죽어도 영원히 막히지 않게). */
    boolean tryLock(long id, Duration hold);

    void unlock(long id);
}
