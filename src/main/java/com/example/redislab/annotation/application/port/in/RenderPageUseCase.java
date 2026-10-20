package com.example.redislab.annotation.application.port.in;

import com.example.redislab.annotation.domain.Lesson;

/**
 * 같은 클래스 안에서 @Cacheable 메서드를 부르는 함정을 보여 주는 화면 그리기.
 */
public interface RenderPageUseCase {

    /** 화면 하나에서 같은 학습 데이터를 세 번 쓰는 상황. 안에서 this.find() 를 부릅니다. */
    void renderPage(long id);

    /** 밖에서 프록시를 거쳐 부르는 @Cacheable 조회. */
    Lesson find(long id);
}
