package com.example.redislab.manual.domain;

/**
 * 학습 콘텐츠 한 건. 아이스크림에듀 학습 데이터처럼 조회는 많고 변경은 드문 데이터입니다.
 * version 은 수정할 때마다 1씩 올라서, 캐시에 든 값이 몇 번째 값인지 눈으로 구분하는 데 씁니다.
 */
public record Lesson(long id, String title, int version) {

    @Override
    public String toString() {
        return "'" + title + "' (v" + version + ")";
    }
}
