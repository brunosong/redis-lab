package com.example.redislab.annotation.application;

import com.example.redislab.annotation.application.port.in.RenderPageUseCase;
import com.example.redislab.annotation.application.port.out.LessonDbPort;
import com.example.redislab.annotation.domain.Lesson;
import com.example.redislab.lab.Timeline;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 같은 클래스 안에서 @Cacheable 메서드를 부르면 캐시가 안 먹는 함정.
 * 캐시는 스프링이 만든 프록시가 걸어 주는데, this.find() 는 프록시를 거치지 않습니다.
 * @Transactional 이 같은 클래스 호출에서 안 먹는 것과 같은 이유입니다.
 */
@Service
public class SelfCallService implements RenderPageUseCase {

    private final LessonDbPort db;
    private final Timeline timeline;

    public SelfCallService(LessonDbPort db, Timeline timeline) {
        this.db = db;
        this.timeline = timeline;
    }

    @Override
    @Cacheable(cacheNames = "lessons", key = "#id")
    public Lesson find(long id) {
        timeline.log("@Cacheable 메서드 본문 실행");
        return db.findFromPrimary(id);
    }

    @Override
    public void renderPage(long id) {
        for (int i = 1; i <= 3; i++) {
            timeline.log("this.find(" + id + ") " + i + "번째 호출");
            find(id);
        }
    }
}
