package com.example.redislab.manual.application.port.out;

import com.example.redislab.manual.domain.Lesson;

import java.util.Optional;

/** 캐시. 구현은 adapter/out/redis. */
public interface LessonCachePort {

    Optional<Lesson> get(long id);

    void put(Lesson lesson);

    void delete(long id);
}
