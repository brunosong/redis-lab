package com.example.redislab.manual.adapter.out.redis;

import com.example.redislab.lab.Timeline;
import com.example.redislab.manual.application.port.out.LessonCachePort;
import com.example.redislab.manual.domain.Lesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 직접 짠 Redis 캐시. 키는 lesson:{id}, 값은 JSON 문자열입니다.
 * redis-cli 로 GET lesson:1, TTL lesson:1 을 쳐 보면 여기서 넣은 값이 그대로 보입니다.
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
        return "lesson:" + id;
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
    public void delete(long id) {
        Boolean deleted = redis.delete(key(id));
        timeline.log("Redis DEL " + key(id) + (Boolean.TRUE.equals(deleted) ? " → 지움" : " → 원래 없었음"));
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
