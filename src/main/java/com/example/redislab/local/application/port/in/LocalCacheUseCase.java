package com.example.redislab.local.application.port.in;

import com.example.redislab.local.domain.Lesson;

/**
 * 서버마다 로컬 캐시를 들고 있을 때의 읽기와 쓰기.
 */
public interface LocalCacheUseCase {

    /** server 로 들어온 조회. 그 서버 로컬 캐시에 없으면 DB에서 읽어 채웁니다. */
    Lesson read(String server, long id);

    /** server 로 들어온 수정. broadcast 가 false 면 자기 캐시만 지우고, true 면 모든 서버에 지우라고 알립니다. */
    Lesson update(String server, long id, String title, boolean broadcast);
}
