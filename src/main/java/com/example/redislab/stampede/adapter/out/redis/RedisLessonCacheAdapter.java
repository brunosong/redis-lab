package com.example.redislab.stampede.adapter.out.redis;

import com.example.redislab.lab.Timeline;
import com.example.redislab.stampede.application.port.out.LessonCachePort;
import com.example.redislab.stampede.domain.Lesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 캐시와 SET NX 락. 키는 stampede:lesson:{id}, 락은 lock:stampede:lesson:{id} 입니다. manual 기능의 lesson:{id} 와 섞이지 않게 앞에 기능 이름을 붙였습니다.
 */
@Component
public class RedisLessonCacheAdapter implements LessonCachePort {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Timeline timeline;
    private final Duration ttl;

    public RedisLessonCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper, Timeline timeline,
                                   @Value("${lab.ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.timeline = timeline;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public static String key(long id) {
        return "stampede:lesson:" + id;
    }

    @Override
    public Optional<Lesson> get(long id) {
        String json = redis.opsForValue().get(key(id));
        if (json == null) {
            timeline.log("Redis GET " + key(id) + " → 없음 (miss)");
            return Optional.empty();
        }
        Lesson lesson = read(json);
        timeline.log("Redis GET " + key(id) + " → " + lesson + " (hit)");
        return Optional.of(lesson);
    }

    @Override
    public void put(Lesson lesson) {
        redis.opsForValue().set(key(lesson.id()), write(lesson), ttl);
        timeline.log("Redis SET " + key(lesson.id()) + " ← " + lesson + " (TTL " + ttl.toSeconds() + "초)");
    }

    @Override
    public boolean tryLock(long id, Duration hold) {
        boolean locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("lock:" + key(id), "1", hold));
        if (locked) {
            timeline.log("Redis SET lock:" + key(id) + " NX → 락 잡음, 내가 DB에 감");
        }
        return locked;
    }

    @Override
    public void unlock(long id) {
        redis.delete("lock:" + key(id));
    }

    private Lesson read(String json) {
        try {
            return objectMapper.readValue(json, Lesson.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Lesson lesson) {
        try {
            return objectMapper.writeValueAsString(lesson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
