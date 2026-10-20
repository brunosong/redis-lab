package com.example.redislab.basic.application.port.in;

import com.example.redislab.basic.domain.Lesson;

/**
 * 학습 데이터 조회와 수정. 같은 일을 직접 짠 것과 어노테이션 두 가지로 둡니다.
 */
public interface LessonUseCase {

    // ---------------------------------------------------------------- 직접 짠 것

    /** 읽기: 캐시에 있으면 캐시, 없으면 DB에서 읽어 캐시에 넣고 돌려줍니다. */
    Lesson find(long id);

    /** 쓰기: DB를 고치고, 커밋이 끝난 뒤 캐시 키를 지웁니다. 다음 읽기가 새 값을 채웁니다. */
    void update(long id, String title);

    /** Write-Through 방식 쓰기: 커밋 뒤 캐시를 지우는 대신 새 값으로 덮어씁니다. 비교용입니다. */
    void updateWriteThrough(long id, String title);

    // ---------------------------------------------------------------- 어노테이션

    /** find() 와 같은 일을 @Cacheable 로. */
    Lesson findWithAnnotation(long id);

    /** update() 와 같은 일을 @CacheEvict 로. */
    void updateWithAnnotation(long id, String title);

    /** updateWriteThrough() 와 같은 일을 @CachePut 으로. */
    Lesson updateWithCachePut(long id, String title);
}
