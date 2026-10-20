package com.example.redislab.local.application.port.in;

/**
 * "이 키 지워라" 알림을 받은 서버가 하는 일. Pub/Sub 구독(adapter/in/pubsub)이 부릅니다.
 */
public interface EvictLocalUseCase {

    void evictLocal(String server, long id);
}
