package com.example.redislab.basic.adapter.out.redis;

import com.example.redislab.basic.application.port.out.LessonCachePort;
import com.example.redislab.basic.domain.Lesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** StringRedisTemplate 으로 GET, SET, DEL 을 직접 부릅니다. 키는 basic:lesson:{id}, 값은 JSON. */
@Component
public class RedisLessonCacheAdapter implements LessonCachePort {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLessonCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Lesson> get(long id) {
        return Optional.ofNullable(redis.opsForValue().get(key(id))).map(this::fromJson);
    }

    @Override
    public void put(Lesson lesson) {
        redis.opsForValue().set(key(lesson.id()), toJson(lesson), TTL);
    }

    @Override
    public void delete(long id) {
        redis.delete(key(id));
    }

    private static String key(long id) {
        return "basic:lesson:" + id;
    }

    private Lesson fromJson(String json) {
        try {
            return objectMapper.readValue(json, Lesson.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Lesson lesson) {
        try {
            return objectMapper.writeValueAsString(lesson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
