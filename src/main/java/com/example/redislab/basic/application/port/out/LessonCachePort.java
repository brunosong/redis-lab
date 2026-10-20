package com.example.redislab.basic.application.port.out;

import com.example.redislab.basic.domain.Lesson;

import java.util.Optional;

/** 캐시. 구현은 adapter/out/redis. TTL 은 어댑터가 정합니다. */
public interface LessonCachePort {

    Optional<Lesson> get(long id);

    void put(Lesson lesson);

    void delete(long id);
}
