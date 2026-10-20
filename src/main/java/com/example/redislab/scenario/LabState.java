package com.example.redislab.scenario;

import com.example.redislab.cache.LessonRedisCache;
import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.local.LocalCacheCluster;
import com.example.redislab.support.Gate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * lesson 1번이 지금 어디에 어떤 값으로 들어 있는지 한눈에 모읍니다. 화면 위쪽 "현재 상태" 표입니다.
 */
@Component
public class LabState {

    public static final long ID = 1L;

    private final LessonRepository repository;
    private final LessonRedisCache cache;
    private final LocalCacheCluster cluster;
    private final Gate gate;

    public LabState(LessonRepository repository, LessonRedisCache cache, LocalCacheCluster cluster, Gate gate) {
        this.repository = repository;
        this.cache = cache;
        this.cluster = cluster;
        this.gate = gate;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("DB 원본", repository.peekPrimary(ID).toString());
        state.put("DB 복제본", repository.peekReplica(ID).toString());
        state.put("Redis lesson:1", redis(LessonRedisCache.key(ID)));
        state.put("Redis lessons::1 (@Cacheable)", redis("lessons::" + ID));
        state.put("Redis lessons-tx::1 (@Cacheable, transactionAware)", redis("lessons-tx::" + ID));
        for (String node : cluster.nodeNames()) {
            Lesson local = cluster.peek(node, ID);
            state.put(node + " 로컬 캐시", local == null ? "(없음)" : local.toString());
        }
        state.put("DB 조회 횟수", repository.dbReads());
        return state;
    }

    public void reset() {
        gate.clear();
        repository.reset();
        cache.clearLabKeys();
        cluster.clear();
    }

    private String redis(String key) {
        String raw = cache.raw(key);
        if (raw == null) {
            return "(없음)";
        }
        Long ttl = cache.ttlSeconds(key);
        return raw + "  TTL " + ttl + "초";
    }
}
