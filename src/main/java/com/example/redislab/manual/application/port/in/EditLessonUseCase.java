package com.example.redislab.manual.application.port.in;

import com.example.redislab.manual.domain.Lesson;

/**
 * 직접 짠 캐시의 쓰기 방법들. 데이터를 고칠 때 캐시를 어떻게 다루느냐가 정합성을 가릅니다.
 */
public interface EditLessonUseCase {

    /** 쓰는 쪽이 DB를 고쳤지만 아직 커밋하지 않은 지점. */
    String BEFORE_COMMIT = "쓰기: DB UPDATE 했고 커밋 직전";
    /** 쓰는 쪽이 커밋했고 캐시에 새 값을 넣기 직전. */
    String BEFORE_CACHE_PUT = "쓰기: 커밋 끝, 캐시에 새 값 넣기 직전";

    /** 1. DB만 고치고 캐시는 그대로. TTL이 끝날 때까지 옛 값이 보입니다. */
    Lesson dbOnly(long id, String title);

    /** 2. 정석. 커밋이 끝난 뒤 캐시 키를 지웁니다. 다음 조회가 DB에서 새 값을 채웁니다. */
    Lesson evictAfterCommit(long id, String title);

    /** 3. 커밋 전에 먼저 지움. 그 사이 다른 요청이 옛 값을 읽어 다시 채울 수 있습니다. */
    Lesson evictBeforeCommit(long id, String title);

    /** 4. 커밋 뒤 캐시에 새 값을 덮어씀(Write-Through 비슷). 동시에 두 명이 고치면 순서가 꼬일 수 있습니다. */
    Lesson writeThrough(long id, String title);

    /** 5. 커밋 뒤 지우고, 복제 지연이 끝날 즈음 한 번 더 지움(지연 이중 삭제). */
    Lesson evictTwice(long id, String title);
}
