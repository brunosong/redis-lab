package com.example.redislab.manual.application.port.out;

import com.example.redislab.manual.domain.Lesson;

/** 원본 DB와 복제본 DB. 구현은 adapter/out/db. */
public interface LessonDbPort {

    Lesson findFromPrimary(long id);

    Lesson findFromReplica(long id);

    /**
     * 원본을 고칩니다. 트랜잭션 안에서 부르면 커밋된 뒤에야 복제가 시작됩니다.
     * 고친 결과를 돌려줍니다(같은 트랜잭션 안이라 자기가 고친 값이 보입니다).
     */
    Lesson update(long id, String title);
}
