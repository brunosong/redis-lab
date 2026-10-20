package com.example.redislab.annotation;

import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.support.Timeline;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 같은 클래스 안에서 @Cacheable 메서드를 부르면 캐시가 안 먹는 함정.
 * 캐시는 스프링이 만든 프록시가 걸어 주는데, this.find() 는 프록시를 거치지 않습니다.
 * @Transactional 이 같은 클래스 호출에서 안 먹는 것과 같은 이유입니다.
 */
@Component
public class SelfCallExample {

    private final LessonRepository repository;
    private final Timeline timeline;

    public SelfCallExample(LessonRepository repository, Timeline timeline) {
        this.repository = repository;
        this.timeline = timeline;
    }

    @Cacheable(cacheNames = "lessons", key = "#id")
    public Lesson find(long id) {
        timeline.log("@Cacheable 메서드 본문 실행");
        return repository.findFromPrimary(id);
    }

    /** 화면 하나에서 같은 학습 데이터를 세 번 쓰는 상황. 안에서 this.find() 를 부릅니다. */
    public void renderPage(long id) {
        for (int i = 1; i <= 3; i++) {
            timeline.log("this.find(" + id + ") " + i + "번째 호출");
            find(id);
        }
    }
}
