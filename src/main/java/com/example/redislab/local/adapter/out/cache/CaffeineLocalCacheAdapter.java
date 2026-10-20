package com.example.redislab.local.adapter.out.cache;

import com.example.redislab.lab.Timeline;
import com.example.redislab.local.application.port.out.LocalCachePort;
import com.example.redislab.local.domain.Lesson;
import com.example.redislab.local.domain.Servers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 서버마다 Caffeine 캐시 하나씩. 실제로는 서버마다 JVM 이 따로라 각자 하나만 들고 있습니다.
 */
@Component
public class CaffeineLocalCacheAdapter implements LocalCachePort {

    private final Map<String, Cache<Long, Lesson>> caches = new LinkedHashMap<>();
    private final Timeline timeline;

    public CaffeineLocalCacheAdapter(Timeline timeline) {
        this.timeline = timeline;
        for (String server : Servers.ALL) {
            caches.put(server, Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build());
        }
    }

    @Override
    public Optional<Lesson> get(String server, long id) {
        Lesson cached = caches.get(server).getIfPresent(id);
        timeline.log(server + " 로컬 캐시 → " + (cached == null ? "없음 (miss)" : cached + " (hit)"));
        return Optional.ofNullable(cached);
    }

    @Override
    public void put(String server, Lesson lesson) {
        caches.get(server).put(lesson.id(), lesson);
        timeline.log(server + " 로컬 캐시에 넣음 ← " + lesson);
    }

    @Override
    public void evict(String server, long id) {
        caches.get(server).invalidate(id);
    }

    @Override
    public Lesson peek(String server, long id) {
        return caches.get(server).getIfPresent(id);
    }

    @Override
    public void clearAll() {
        caches.values().forEach(Cache::invalidateAll);
    }
}
