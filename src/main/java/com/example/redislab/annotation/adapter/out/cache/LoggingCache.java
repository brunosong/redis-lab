package com.example.redislab.annotation.adapter.out.cache;

import com.example.redislab.lab.Timeline;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * 스프링 캐시 어노테이션이 Redis에 실제로 무엇을 언제 하는지 타임라인에 남기려고 감싼 껍데기.
 * 어노테이션은 코드에 GET, DEL 이 안 보여서, 언제 지워지는지 눈으로 확인하기 어렵기 때문입니다.
 */
class LoggingCache implements Cache {

    private final Cache delegate;
    private final Timeline timeline;

    LoggingCache(Cache delegate, Timeline timeline) {
        this.delegate = delegate;
        this.timeline = timeline;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = delegate.get(key);
        timeline.log("[어노테이션] Redis GET " + name(key) + (value == null ? " → 없음 (miss)" : " → " + value.get() + " (hit)"));
        return value;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return delegate.get(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
        timeline.log("[어노테이션] Redis SET " + name(key) + " ← " + value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        timeline.log("[어노테이션] Redis DEL " + name(key));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private String name(Object key) {
        return getName() + "::" + key;
    }
}
