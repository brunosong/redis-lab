package com.example.redislab.local.application.port.out;

import com.example.redislab.local.domain.Lesson;

import java.util.Optional;

/** 서버별 로컬 캐시. 구현은 adapter/out/cache. */
public interface LocalCachePort {

    Optional<Lesson> get(String server, long id);

    void put(String server, Lesson lesson);

    void evict(String server, long id);

    /** 상태 표시용. 타임라인에 남기지 않습니다. 없으면 null. */
    Lesson peek(String server, long id);

    void clearAll();
}
