package com.example.redislab.manual;

import com.example.redislab.cache.LessonRedisCache;
import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.support.Gate;
import com.example.redislab.support.Timeline;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 직접 짠 캐시의 읽기 방법들. 전부 Cache-Aside(캐시에 없으면 DB에서 읽어 채움)입니다.
 */
@Service
public class ReadPaths {

    /** 읽는 쪽이 DB에서 값을 읽은 뒤, 캐시에 넣기 직전. 경쟁 재현에 씁니다. */
    public static final String BEFORE_PUT = "읽기: DB에서 읽고 캐시에 넣기 직전";

    private final LessonRepository repository;
    private final LessonRedisCache cache;
    private final Gate gate;
    private final Timeline timeline;

    public ReadPaths(LessonRepository repository, LessonRedisCache cache, Gate gate, Timeline timeline) {
        this.repository = repository;
        this.cache = cache;
        this.gate = gate;
        this.timeline = timeline;
    }

    /** 캐시 없이 매번 DB. 비교용입니다. */
    public Lesson noCache(long id) {
        return repository.findFromPrimary(id);
    }

    /** 기본 Cache-Aside. 캐시를 채울 때 원본에서 읽습니다. */
    public Lesson cacheAside(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = repository.findFromPrimary(id);
        gate.pass(BEFORE_PUT);
        cache.put(lesson);
        return lesson;
    }

    /** 읽기를 복제본으로 보내는 Cache-Aside. 읽기 부하를 복제본에 넘기는 흔한 구성입니다. */
    public Lesson cacheAsideFromReplica(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = repository.findFromReplica(id);
        cache.put(lesson);
        return lesson;
    }

    /**
     * 스탬피드 막기. 캐시가 비었을 때 락을 잡은 한 요청만 DB에 가고,
     * 나머지는 잠깐씩 기다리며 캐시가 채워지기를 봅니다.
     */
    public Lesson cacheAsideWithLock(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (cache.tryLock(id, Duration.ofSeconds(3))) {
            try {
                Optional<Lesson> again = cache.get(id);   // 락을 잡는 사이 남이 채웠을 수 있음
                if (again.isPresent()) {
                    return again.get();
                }
                Lesson lesson = repository.findFromPrimary(id);
                cache.put(lesson);
                return lesson;
            } finally {
                cache.unlock(id);
            }
        }
        long until = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < until) {
            sleep(50);
            Optional<Lesson> filled = cache.get(id);
            if (filled.isPresent()) {
                return filled.get();
            }
        }
        timeline.log("3초 기다려도 안 채워져서 직접 DB로 감");
        return repository.findFromPrimary(id);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
