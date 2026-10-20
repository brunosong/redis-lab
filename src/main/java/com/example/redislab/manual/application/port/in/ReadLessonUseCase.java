package com.example.redislab.manual.application.port.in;

import com.example.redislab.manual.domain.Lesson;

/**
 * 직접 짠 캐시의 읽기 방법들. 전부 Cache-Aside(캐시에 없으면 DB에서 읽어 채움)입니다.
 */
public interface ReadLessonUseCase {

    /** 읽는 쪽이 DB에서 값을 읽은 뒤, 캐시에 넣기 직전. 경쟁 재현에 씁니다. */
    String BEFORE_PUT = "읽기: DB에서 읽고 캐시에 넣기 직전";

    /** 캐시 없이 매번 DB. 비교용입니다. */
    Lesson noCache(long id);

    /** 기본 Cache-Aside. 캐시를 채울 때 원본에서 읽습니다. */
    Lesson cacheAside(long id);

    /** 읽기를 복제본으로 보내는 Cache-Aside. 읽기 부하를 복제본에 넘기는 흔한 구성입니다. */
    Lesson cacheAsideFromReplica(long id);
}
