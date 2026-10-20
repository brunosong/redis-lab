package com.example.redislab.stampede.application;

import com.example.redislab.lab.Timeline;
import com.example.redislab.stampede.application.port.in.ReadLessonUseCase;
import com.example.redislab.stampede.application.port.out.LessonCachePort;
import com.example.redislab.stampede.application.port.out.LessonDbPort;
import com.example.redislab.stampede.domain.Lesson;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 읽기 흐름만 담습니다. 락을 Redis 로 잡는지 다른 걸로 잡는지는 포트 뒤에 숨어 있습니다.
 */
@Service
public class ReadLessonService implements ReadLessonUseCase {

    private static final Duration LOCK_HOLD = Duration.ofSeconds(3);
    private static final long WAIT_MS = 3000;

    private final LessonDbPort db;
    private final LessonCachePort cache;
    private final Timeline timeline;

    public ReadLessonService(LessonDbPort db, LessonCachePort cache, Timeline timeline) {
        this.db = db;
        this.cache = cache;
        this.timeline = timeline;
    }

    @Override
    public Lesson cacheAside(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = db.findFromPrimary(id);
        cache.put(lesson);
        return lesson;
    }

    @Override
    public Lesson cacheAsideWithLock(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (cache.tryLock(id, LOCK_HOLD)) {
            try {
                Optional<Lesson> again = cache.get(id);   // 락을 잡는 사이 남이 채웠을 수 있음
                if (again.isPresent()) {
                    return again.get();
                }
                Lesson lesson = db.findFromPrimary(id);
                cache.put(lesson);
                return lesson;
            } finally {
                cache.unlock(id);
            }
        }
        long until = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < until) {
            sleep(50);
            Optional<Lesson> filled = cache.get(id);
            if (filled.isPresent()) {
                return filled.get();
            }
        }
        timeline.log("3초 기다려도 안 채워져서 직접 DB로 감");
        return db.findFromPrimary(id);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
