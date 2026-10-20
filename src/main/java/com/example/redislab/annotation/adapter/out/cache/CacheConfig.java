package com.example.redislab.annotation.adapter.out.cache;

import com.example.redislab.annotation.domain.Lesson;
import com.example.redislab.lab.Timeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * @Cacheable, @CacheEvict 가 쓰는 캐시 매니저 두 개.
 *
 * <ul>
 *   <li>plainCacheManager: 기본 설정. @CacheEvict 가 메서드가 끝나는 즉시 지웁니다.
 *       바깥에 트랜잭션이 아직 열려 있어도 상관하지 않습니다.</li>
 *   <li>txAwareCacheManager: transactionAware 를 켠 것. 트랜잭션 안에서 부르면
 *       지우기와 넣기를 커밋이 끝난 뒤로 미룹니다.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager plainCacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper,
                                          Timeline timeline, @Value("${lab.ttl-seconds}") long ttlSeconds) {
        return manager(factory, objectMapper, timeline, ttlSeconds, false, "lessons");
    }

    @Bean
    public CacheManager txAwareCacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper,
                                            Timeline timeline, @Value("${lab.ttl-seconds}") long ttlSeconds) {
        return manager(factory, objectMapper, timeline, ttlSeconds, true, "lessons-tx");
    }

    private static CacheManager manager(RedisConnectionFactory factory, ObjectMapper objectMapper, Timeline timeline,
                                        long ttlSeconds, boolean transactionAware, String cacheName) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, Lesson.class)));

        RedisCacheManager manager = new RedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(factory), config, cacheName) {
            @Override
            protected Cache decorateCache(Cache cache) {
                // 기록 껍데기를 안쪽에 두어야, transactionAware 가 미룬 뒤 "실제로 지운 시점"이 찍힙니다.
                return super.decorateCache(new LoggingCache(cache, timeline));
            }
        };
        manager.setTransactionAware(transactionAware);
        return manager;
    }
}
