package com.example.redislab.lab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 결과 확인과 화면 상태 표시용으로 Redis 키를 들여다봅니다. 타임라인에 남기지 않습니다.
 * 캐시 동작 자체는 각 기능 패키지에 있고, 이건 연습장 장치입니다.
 */
@Component
public class RedisPeek {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisPeek(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** 키에 든 JSON 을 한 행으로 읽습니다. 기능마다 Lesson 이 따로 있어도 JSON 모양은 같습니다. */
    public Optional<LabDatabase.Row> row(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key)).map(this::read);
    }

    public Long ttlSeconds(String key) {
        return redis.getExpire(key);
    }

    /** 상태 표 한 칸. 값과 남은 TTL. */
    public String describe(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return "(없음)";
        }
        return raw + "  TTL " + ttlSeconds(key) + "초";
    }

    /** 패턴에 맞는 키를 지웁니다. 각 기능이 자기 키만 넘깁니다. */
    public void clear(String... patterns) {
        for (String pattern : patterns) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }

    private LabDatabase.Row read(String json) {
        try {
            return objectMapper.readValue(json, LabDatabase.Row.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
