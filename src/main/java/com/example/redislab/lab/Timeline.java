package com.example.redislab.lab;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 무엇이 어떤 순서로 일어났는지 적는 기록장. 화면 오른쪽 "타임라인"에 그대로 보입니다.
 * 줄마다 시작부터 몇 ms 지났는지와 누가(스레드) 했는지를 붙입니다.
 */
@Component
public class Timeline {

    private static final int MAX = 300;

    private final List<String> lines = new ArrayList<>();
    private long startedAt = System.currentTimeMillis();

    public synchronized void start(String title) {
        lines.clear();
        startedAt = System.currentTimeMillis();
        lines.add("=== " + title + " ===");
    }

    public synchronized void log(String message) {
        long elapsed = System.currentTimeMillis() - startedAt;
        lines.add(String.format("%6dms  [%s] %s", elapsed, who(), message));
        if (lines.size() > MAX) {
            lines.remove(0);
        }
    }

    public synchronized void note(String message) {
        lines.add("          " + message);
    }

    public synchronized List<String> lines() {
        return List.copyOf(lines);
    }

    private static String who() {
        String name = Thread.currentThread().getName();
        return name.startsWith("http-nio") ? "요청" : name;
    }
}
