package com.example.redislab.local.domain;

import java.util.List;

/**
 * 연습장에 있는 서버 두 대. 실제로는 서버마다 JVM 이 따로지만, 여기서는 한 JVM 안에서 이름으로 나눕니다.
 */
public final class Servers {

    public static final String A = "서버A";
    public static final String B = "서버B";
    public static final List<String> ALL = List.of(A, B);

    private Servers() {
    }
}
