package com.example.redislab.annotation.application.port.out;

import com.example.redislab.annotation.domain.Lesson;

/** 원본 DB. 구현은 adapter/out/db. 캐시는 모릅니다(캐시는 서비스 메서드의 어노테이션이 겁니다). */
public interface LessonDbPort {

    Lesson findFromPrimary(long id);

    Lesson update(long id, String title);
}
