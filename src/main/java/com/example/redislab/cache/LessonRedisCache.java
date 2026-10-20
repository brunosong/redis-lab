package com.example.redislab.cache;

import com.example.redislab.lesson.Lesson;
import com.example.redislab.support.Timeline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 직접 짠 Redis 캐시. 키는 lesson:{id}, 값은 JSON 문자열입니다.
 * redis-cli 로 GET lesson:1, TTL lesson:1 을 쳐 보면 여기서 넣은 값이 그대로 보입니다.
 */
@Component
public class LessonRedisCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Timeline timeline;
    private final Duration ttl;

    public LessonRedisCache(StringRedisTemplate redis, ObjectMapper objectMapper, Timeline timeline,
                            @Value("${lab.ttl-seconds}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.timeline = timeline;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public static String key(long id) {
        return "lesson:" + id;
    }

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

    public void put(Lesson lesson) {
        redis.opsForValue().set(key(lesson.id()), write(lesson), ttl);
        timeline.log("Redis SET " + key(lesson.id()) + " ← " + lesson + " (TTL " + ttl.toSeconds() + "초)");
    }

    public void delete(long id) {
        Boolean deleted = redis.delete(key(id));
        timeline.log("Redis DEL " + key(id) + (Boolean.TRUE.equals(deleted) ? " → 지움" : " → 원래 없었음"));
    }

    /** 스탬피드 막기용 락. 먼저 잡은 한 요청만 DB에 갑니다. */
    public boolean tryLock(long id, Duration hold) {
        boolean locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("lock:" + key(id), "1", hold));
        if (locked) {
            timeline.log("Redis SET lock:" + key(id) + " NX → 락 잡음, 내가 DB에 감");
        }
        return locked;
    }

    public void unlock(long id) {
        redis.delete("lock:" + key(id));
    }

    /** 결과 확인용. 타임라인에 남기지 않고 키에 든 값을 꺼내 봅니다. */
    public Optional<Lesson> peek(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key)).map(this::read);
    }

    public String raw(String key) {
        return redis.opsForValue().get(key);
    }

    public Long ttlSeconds(String key) {
        return redis.getExpire(key);
    }

    /** 연습장이 쓰는 키만 지웁니다. */
    public void clearLabKeys() {
        for (String pattern : new String[]{"lesson:*", "lock:lesson:*", "lessons::*", "lessons-tx::*", "basic:*", "basic-lessons::*"}) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
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
