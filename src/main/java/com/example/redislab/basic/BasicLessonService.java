package com.example.redislab.basic;

import com.example.redislab.lesson.Lesson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 소스는 여기부터 보세요. 연습장 장치(Gate, Timeline) 없이 실무에서 짜는 모양 그대로입니다.
 *
 * <p>같은 일을 두 가지로 짰습니다.
 * <ol>
 *   <li>직접 짠 것: StringRedisTemplate 으로 GET, SET, DEL 을 직접 부름</li>
 *   <li>어노테이션: @Cacheable, @CacheEvict, @CachePut 이 위 일을 대신 해 줌</li>
 * </ol>
 *
 * <p>쓰기는 두 방식입니다. 커밋 뒤 캐시를 지우는 방식(update, updateWithAnnotation)이 기본이고,
 * 새 값으로 덮어쓰는 Write-Through 방식(updateWriteThrough, updateWithCachePut)은 비교용입니다.
 */
@Service
public class BasicLessonService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BasicLessonService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------ 1. 직접 짠 Cache-Aside

    /** 읽기: 캐시에 있으면 캐시, 없으면 DB에서 읽어 캐시에 넣고 돌려줍니다. */
    public Lesson find(long id) {
        String key = "basic:lesson:" + id;

        String cached = redis.opsForValue().get(key);           // 1. 캐시부터 봄
        if (cached != null) {
            return fromJson(cached);                             // 2. 있으면 끝 (hit)
        }

        Lesson lesson = jdbc.queryForObject(                     // 3. 없으면 DB (miss)
                "SELECT id, title, version FROM lesson WHERE id = ?",
                (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version")),
                id);

        redis.opsForValue().set(key, toJson(lesson), TTL);       // 4. 캐시에 넣음. TTL은 안전장치
        return lesson;
    }

    /** 쓰기: DB를 고치고, 커밋이 끝난 뒤 캐시 키를 지웁니다. 다음 읽기가 새 값을 채웁니다. */
    @Transactional
    public void update(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1 WHERE id = ?", title, id);

        // 여기서 바로 redis.delete 하면 커밋 전에 지워져서, 그 틈에 다른 요청이 옛 값을 다시 넣을 수 있습니다.
        // 그래서 커밋 뒤로 미룹니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.delete("basic:lesson:" + id);
            }
        });
    }

    /**
     * Write-Through 방식 쓰기: DB를 고치고, 커밋 뒤 캐시를 지우는 대신 새 값으로 덮어씁니다.
     * 읽기는 위 find() 를 그대로 씁니다.
     *
     * <p>장점은 고친 직후 첫 조회도 캐시에서 바로 나온다는 것(miss 가 없음).
     * 단점은 두 명이 동시에 고치면 DB에는 나중 값, 캐시에는 먼저 쓴 값이 남을 수 있다는 것입니다.
     * 시나리오 "지우지 않고 새 값으로 덮어쓰면"이 그 상황을 재현합니다.
     */
    @Transactional
    public void updateWriteThrough(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1 WHERE id = ?", title, id);
        Lesson updated = jdbc.queryForObject(               // 같은 트랜잭션이라 방금 고친 값이 보임
                "SELECT id, title, version FROM lesson WHERE id = ?",
                (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version")),
                id);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redis.opsForValue().set("basic:lesson:" + id, toJson(updated), TTL);   // 지우지 않고 덮어씀
            }
        });
    }

    // ------------------------------------------------------------ 2. 어노테이션

    /**
     * 위 find() 와 같은 일. 캐시에 있으면 메서드 본문을 아예 실행하지 않고 캐시 값을 돌려줍니다.
     * 키는 "basic-lessons::1" 모양으로 만들어집니다.
     */
    @Cacheable(cacheNames = "basic-lessons", key = "#id", cacheManager = "txAwareCacheManager")
    public Lesson findWithAnnotation(long id) {
        return jdbc.queryForObject(
                "SELECT id, title, version FROM lesson WHERE id = ?",
                (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version")),
                id);
    }

    /**
     * 위 update() 와 같은 일. cacheManager 를 transactionAware 로 켠 것을 써서 지우기가 커밋 뒤로 미뤄집니다.
     * 기본 캐시 매니저를 쓰면 메서드가 끝나는 즉시(커밋 전에) 지웁니다. 시나리오 "@Transactional 안에서 부른 @CacheEvict" 참고.
     */
    @Transactional
    @CacheEvict(cacheNames = "basic-lessons", key = "#id", cacheManager = "txAwareCacheManager")
    public void updateWithAnnotation(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1 WHERE id = ?", title, id);
    }

    /**
     * 위 updateWriteThrough() 의 어노테이션 버전. @CachePut 은 @Cacheable 과 달리 항상 본문을 실행하고,
     * 돌려준 값으로 캐시를 덮어씁니다. 그래서 반드시 새 값을 return 해야 합니다.
     * 동시 수정 때 순서가 꼬이는 문제도 똑같이 있습니다.
     */
    @Transactional
    @CachePut(cacheNames = "basic-lessons", key = "#id", cacheManager = "txAwareCacheManager")
    public Lesson updateWithCachePut(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1 WHERE id = ?", title, id);
        return jdbc.queryForObject(
                "SELECT id, title, version FROM lesson WHERE id = ?",
                (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version")),
                id);
    }

    // ------------------------------------------------------------

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
