package com.example.redislab.local.adapter.out.pubsub;

import com.example.redislab.lab.Timeline;
import com.example.redislab.local.application.port.out.EvictBroadcastPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 채널에 지울 id 를 발행합니다. 받는 쪽은 adapter/in/pubsub.
 * Pub/Sub 은 보내고 끝이라, 그 순간 연결이 끊긴 서버는 메시지를 못 받습니다.
 */
@Component
public class RedisEvictBroadcastAdapter implements EvictBroadcastPort {

    public static final String CHANNEL = "lesson-evict";

    private final StringRedisTemplate redis;
    private final Timeline timeline;

    public RedisEvictBroadcastAdapter(StringRedisTemplate redis, Timeline timeline) {
        this.redis = redis;
        this.timeline = timeline;
    }

    @Override
    public void broadcast(String fromServer, long id) {
        redis.convertAndSend(CHANNEL, String.valueOf(id));
        timeline.log(fromServer + " → Redis PUBLISH " + CHANNEL + " " + id + " (모든 서버에 지우라고 알림)");
    }
}
