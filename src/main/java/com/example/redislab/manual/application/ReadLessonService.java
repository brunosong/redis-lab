package com.example.redislab.manual.application;

import com.example.redislab.lab.Gate;
import com.example.redislab.manual.application.port.in.ReadLessonUseCase;
import com.example.redislab.manual.application.port.out.LessonCachePort;
import com.example.redislab.manual.application.port.out.LessonDbPort;
import com.example.redislab.manual.domain.Lesson;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 읽기 흐름만 담습니다. Redis, H2 를 직접 모르고 포트만 압니다.
 * gate.pass 는 연습장 장치라 시나리오가 걸어 두지 않으면 그냥 지나갑니다.
 */
@Service
public class ReadLessonService implements ReadLessonUseCase {

    private final LessonDbPort db;
    private final LessonCachePort cache;
    private final Gate gate;

    public ReadLessonService(LessonDbPort db, LessonCachePort cache, Gate gate) {
        this.db = db;
        this.cache = cache;
        this.gate = gate;
    }

    @Override
    public Lesson noCache(long id) {
        return db.findFromPrimary(id);
    }

    @Override
    public Lesson cacheAside(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = db.findFromPrimary(id);
        gate.pass(BEFORE_PUT);
        cache.put(lesson);
        return lesson;
    }

    @Override
    public Lesson cacheAsideFromReplica(long id) {
        Optional<Lesson> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        Lesson lesson = db.findFromReplica(id);
        cache.put(lesson);
        return lesson;
    }
}
