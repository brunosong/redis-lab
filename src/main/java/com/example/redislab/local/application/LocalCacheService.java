package com.example.redislab.local.application;

import com.example.redislab.lab.Timeline;
import com.example.redislab.local.application.port.in.EvictLocalUseCase;
import com.example.redislab.local.application.port.in.InspectLocalCacheUseCase;
import com.example.redislab.local.application.port.in.LocalCacheUseCase;
import com.example.redislab.local.application.port.out.EvictBroadcastPort;
import com.example.redislab.local.application.port.out.LessonDbPort;
import com.example.redislab.local.application.port.out.LocalCachePort;
import com.example.redislab.local.domain.Lesson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * GA 잡코리아처럼 Redis 대신 서버마다 로컬 캐시를 쓰는 경우의 흐름.
 *
 * <p>로컬 캐시는 네트워크 왕복이 없어 빠르지만, 서버A에서 지운다고 서버B 캐시가 지워지지 않습니다.
 * 그래서 고친 서버가 커밋 뒤 "이 키 지워라"를 알리고, 모든 서버가 받아서 각자 지웁니다.
 * 알리는 수단(Redis Pub/Sub)은 EvictBroadcastPort 뒤에 숨어 있습니다.
 */
@Service
public class LocalCacheService implements LocalCacheUseCase, EvictLocalUseCase, InspectLocalCacheUseCase {

    private final LessonDbPort db;
    private final LocalCachePort cache;
    private final EvictBroadcastPort broadcaster;
    private final TransactionTemplate tx;
    private final Timeline timeline;

    public LocalCacheService(LessonDbPort db, LocalCachePort cache, EvictBroadcastPort broadcaster,
                             TransactionTemplate tx, Timeline timeline) {
        this.db = db;
        this.cache = cache;
        this.broadcaster = broadcaster;
        this.tx = tx;
        this.timeline = timeline;
    }

    @Override
    public Lesson read(String server, long id) {
        Optional<Lesson> cached = cache.get(server, id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = db.findFromPrimary(id);
        cache.put(server, lesson);
        return lesson;
    }

    @Override
    public Lesson update(String server, long id, String title, boolean broadcast) {
        return tx.execute(s -> {
            Lesson updated = db.update(id, title);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (broadcast) {
                        broadcaster.broadcast(server, id);
                    } else {
                        cache.evict(server, id);
                        timeline.log(server + " 자기 로컬 캐시만 지움. 다른 서버는 모름");
                    }
                }
            });
            return updated;
        });
    }

    @Override
    public void evictLocal(String server, long id) {
        cache.evict(server, id);
        timeline.log(server + " 로컬 캐시에서 lesson " + id + " 지움 (Pub/Sub 받음)");
    }

    @Override
    public Lesson peek(String server, long id) {
        return cache.peek(server, id);
    }

    @Override
    public void clearAll() {
        cache.clearAll();
    }
}
