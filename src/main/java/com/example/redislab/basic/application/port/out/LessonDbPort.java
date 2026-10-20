package com.example.redislab.basic.application.port.out;

import com.example.redislab.basic.domain.Lesson;

/** DB. 구현은 adapter/out/db. */
public interface LessonDbPort {

    Lesson find(long id);

    /** 고친 결과를 돌려줍니다(같은 트랜잭션 안이라 자기가 고친 값이 보입니다). */
    Lesson update(long id, String title);
}
