package com.example.redislab.local;

import com.example.redislab.lesson.Lesson;
import com.example.redislab.lesson.LessonRepository;
import com.example.redislab.support.Timeline;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서버 두 대(서버A, 서버B)가 각자 Caffeine 로컬 캐시를 들고 있는 상황을 한 JVM 안에서 흉내 냅니다.
 * GA 잡코리아처럼 Redis 대신 로컬 캐시를 쓰는 경우입니다.
 *
 * <p>로컬 캐시는 네트워크 왕복이 없어 빠르지만, 서버A에서 지운다고 서버B 캐시가 지워지지 않습니다.
 * 그래서 고친 서버가 Redis Pub/Sub 채널에 "이 키 지워라"를 발행하고, 모든 서버가 구독하다가 각자 지웁니다.
 */
@Component
public class LocalCacheCluster {

    public static final String CHANNEL = "lesson-evict";

    private final Map<String, Cache<Long, Lesson>> nodes = new LinkedHashMap<>();
    private final LessonRepository repository;
    private final StringRedisTemplate redis;
    private final TransactionTemplate tx;
    private final Timeline timeline;

    public LocalCacheCluster(LessonRepository repository, StringRedisTemplate redis, TransactionTemplate tx,
                             Timeline timeline, RedisMessageListenerContainer container) {
        this.repository = repository;
        this.redis = redis;
        this.tx = tx;
        this.timeline = timeline;
        for (String node : new String[]{"서버A", "서버B"}) {
            Cache<Long, Lesson> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();
            nodes.put(node, cache);
            // 실제로는 서버마다 따로 구독합니다. 여기서는 서버마다 리스너를 하나씩 붙여서 흉내 냅니다.
            container.addMessageListener((message, pattern) -> {
                long id = Long.parseLong(new String(message.getBody()));
                cache.invalidate(id);
                timeline.log(node + " 로컬 캐시에서 lesson " + id + " 지움 (Pub/Sub 받음)");
            }, new ChannelTopic(CHANNEL));
        }
    }

    public Lesson read(String node, long id) {
        Cache<Long, Lesson> cache = nodes.get(node);
        Lesson cached = cache.getIfPresent(id);
        if (cached != null) {
            timeline.log(node + " 로컬 캐시 → " + cached + " (hit)");
            return cached;
        }
        timeline.log(node + " 로컬 캐시 → 없음 (miss)");
        Lesson lesson = repository.findFromPrimary(id);
        cache.put(id, lesson);
        timeline.log(node + " 로컬 캐시에 넣음 ← " + lesson);
        return lesson;
    }

    /** node 서버로 들어온 수정 요청. broadcast 가 false 면 자기 캐시만 지웁니다. */
    public Lesson update(String node, long id, String title, boolean broadcast) {
        return tx.execute(s -> {
            Lesson updated = repository.update(id, title);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (broadcast) {
                        redis.convertAndSend(CHANNEL, String.valueOf(id));
                        timeline.log(node + " → Redis PUBLISH " + CHANNEL + " " + id + " (모든 서버에 지우라고 알림)");
                    } else {
                        nodes.get(node).invalidate(id);
                        timeline.log(node + " 자기 로컬 캐시만 지움. 다른 서버는 모름");
                    }
                }
            });
            return updated;
        });
    }

    public Lesson peek(String node, long id) {
        return nodes.get(node).getIfPresent(id);
    }

    public Iterable<String> nodeNames() {
        return nodes.keySet();
    }

    public void clear() {
        nodes.values().forEach(Cache::invalidateAll);
    }
}
