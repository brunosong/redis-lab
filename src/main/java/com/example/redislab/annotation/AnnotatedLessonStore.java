package com.example.redislab.annotation;

import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.support.Timeline;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 어노테이션 방식. 실무에서 가장 많이 보는 모양입니다.
 * 조회 메서드에 @Cacheable, 수정 메서드에 @CacheEvict 를 붙입니다.
 *
 * <p>위쪽 두 개는 기본 캐시 매니저(lessons), 아래 두 개는 transactionAware 캐시 매니저(lessons-tx)를 씁니다.
 * 코드는 똑같고 cacheManager 만 다릅니다.
 */
@Component
public class AnnotatedLessonStore {

    private final LessonRepository repository;
    private final Timeline timeline;

    public AnnotatedLessonStore(LessonRepository repository, Timeline timeline) {
        this.repository = repository;
        this.timeline = timeline;
    }

    @Cacheable(cacheNames = "lessons", key = "#id")
    public Lesson find(long id) {
        timeline.log("@Cacheable 메서드 본문 실행 (캐시에 없어서 들어옴)");
        return repository.findFromPrimary(id);
    }

    @CacheEvict(cacheNames = "lessons", key = "#id")
    public Lesson update(long id, String title) {
        return repository.update(id, title);
    }

    @Cacheable(cacheNames = "lessons-tx", key = "#id", cacheManager = "txAwareCacheManager")
    public Lesson findTxAware(long id) {
        timeline.log("@Cacheable 메서드 본문 실행 (캐시에 없어서 들어옴)");
        return repository.findFromPrimary(id);
    }

    @CacheEvict(cacheNames = "lessons-tx", key = "#id", cacheManager = "txAwareCacheManager")
    public Lesson updateTxAware(long id, String title) {
        Lesson updated = repository.update(id, title);
        timeline.log("@CacheEvict 메서드 끝. transactionAware 라 지우기는 커밋 뒤로 미뤄짐");
        return updated;
    }
}
