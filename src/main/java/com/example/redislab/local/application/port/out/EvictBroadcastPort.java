package com.example.redislab.local.application.port.out;

/** 모든 서버에 "이 키 지워라"를 알립니다. 구현은 adapter/out/pubsub. */
public interface EvictBroadcastPort {

    void broadcast(String fromServer, long id);
}
