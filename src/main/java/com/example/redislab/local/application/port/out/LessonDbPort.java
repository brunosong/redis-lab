package com.example.redislab.local.application.port.out;

import com.example.redislab.local.domain.Lesson;

/** 원본 DB. 구현은 adapter/out/db. */
public interface LessonDbPort {

    Lesson findFromPrimary(long id);

    Lesson update(long id, String title);
}
