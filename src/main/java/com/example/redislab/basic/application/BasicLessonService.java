package com.example.redislab.basic.application;

import com.example.redislab.basic.application.port.in.LessonUseCase;
import com.example.redislab.basic.application.port.out.LessonCachePort;
import com.example.redislab.basic.application.port.out.LessonDbPort;
import com.example.redislab.basic.domain.Lesson;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * 소스는 여기부터 보세요. 연습장 장치(Gate, Timeline) 없이 실무에서 짜는 모양 그대로입니다.
 *
 * <p>같은 일을 두 가지로 짰습니다.
 * <ol>
 *   <li>직접 짠 것: 캐시 포트로 GET, SET, DEL 을 직접 부름</li>
 *   <li>어노테이션: @Cacheable, @CacheEvict, @CachePut 이 위 일을 대신 해 줌</li>
 * </ol>
 *
 * <p>쓰기는 두 방식입니다. 커밋 뒤 캐시를 지우는 방식(update, updateWithAnnotation)이 기본이고,
 * 새 값으로 덮어쓰는 Write-Through 방식(updateWriteThrough, updateWithCachePut)은 비교용입니다.
 *
 * <p>서비스는 Redis 도 H2 도 직접 모르고 포트만 압니다. 실제 GET, SET, DEL 은 adapter/out/redis,
 * SQL 은 adapter/out/db 에 있습니다.
 */
@Service
public class BasicLessonService implements LessonUseCase {

    private final LessonDbPort db;
    private final LessonCachePort cache;

    public BasicLessonService(LessonDbPort db, LessonCachePort cache) {
        this.db = db;
        this.cache = cache;
    }

    // ------------------------------------------------------------ 1. 직접 짠 Cache-Aside

    @Override
    public Lesson find(long id) {
        Optional<Lesson> cached = cache.get(id);    // 1. 캐시부터 봄
        if (cached.isPresent()) {
            return cached.get();                    // 2. 있으면 끝 (hit)
        }
        Lesson lesson = db.find(id);                // 3. 없으면 DB (miss)
        cache.put(lesson);                          // 4. 캐시에 넣음. TTL은 안전장치
        return lesson;
    }

    @Override
    @Transactional
    public void update(long id, String title) {
        db.update(id, title);

        // 여기서 바로 cache.delete 하면 커밋 전에 지워져서, 그 틈에 다른 요청이 옛 값을 다시 넣을 수 있습니다.
        // 그래서 커밋 뒤로 미룹니다.
        afterCommit(() -> cache.delete(id));
    }

    /**
     * 장점은 고친 직후 첫 조회도 캐시에서 바로 나온다는 것(miss 가 없음).
     * 단점은 두 명이 동시에 고치면 DB에는 나중 값, 캐시에는 먼저 쓴 값이 남을 수 있다는 것입니다.
     * manual 기능의 시나리오 "지우지 않고 새 값으로 덮어쓰면"이 그 상황을 재현합니다.
     */
    @Override
    @Transactional
    public void updateWriteThrough(long id, String title) {
        Lesson updated = db.update(id, title);
        afterCommit(() -> cache.put(updated));      // 지우지 않고 덮어씀
    }

    // ------------------------------------------------------------ 2. 어노테이션

    /**
     * 캐시에 있으면 메서드 본문을 아예 실행하지 않고 캐시 값을 돌려줍니다.
     * 키는 "basic-lessons::1" 모양으로 만들어집니다. 캐시 매니저는 adapter/out/cache/BasicCacheConfig.
     */
    @Override
    @Cacheable(cacheNames = "basic-lessons", key = "#id", cacheManager = "basicCacheManager")
    public Lesson findWithAnnotation(long id) {
        return db.find(id);
    }

    /**
     * transactionAware 로 켠 캐시 매니저를 써서 지우기가 커밋 뒤로 미뤄집니다.
     * 기본 캐시 매니저를 쓰면 메서드가 끝나는 즉시(커밋 전에) 지웁니다. annotation 기능의 시나리오
     * "@Transactional 안에서 부른 @CacheEvict" 참고.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "basic-lessons", key = "#id", cacheManager = "basicCacheManager")
    public void updateWithAnnotation(long id, String title) {
        db.update(id, title);
    }

    /**
     * @CachePut 은 @Cacheable 과 달리 항상 본문을 실행하고, 돌려준 값으로 캐시를 덮어씁니다.
     * 그래서 반드시 새 값을 return 해야 합니다. 동시 수정 때 순서가 꼬이는 문제도 똑같이 있습니다.
     */
    @Override
    @Transactional
    @CachePut(cacheNames = "basic-lessons", key = "#id", cacheManager = "basicCacheManager")
    public Lesson updateWithCachePut(long id, String title) {
        return db.update(id, title);
    }

    // ------------------------------------------------------------

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
