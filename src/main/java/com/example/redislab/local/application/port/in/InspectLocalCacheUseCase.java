package com.example.redislab.local.application.port.in;

import com.example.redislab.local.domain.Lesson;

/**
 * 화면 상태 표시와 시나리오 확인용. 타임라인에 남기지 않고 서버별 로컬 캐시를 들여다봅니다.
 */
public interface InspectLocalCacheUseCase {

    /** 없으면 null. */
    Lesson peek(String server, long id);

    void clearAll();
}
