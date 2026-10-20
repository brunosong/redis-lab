package com.example.redislab.stampede.application.port.out;

import com.example.redislab.stampede.domain.Lesson;

/** 원본 DB. 구현은 adapter/out/db. */
public interface LessonDbPort {

    Lesson findFromPrimary(long id);
}
