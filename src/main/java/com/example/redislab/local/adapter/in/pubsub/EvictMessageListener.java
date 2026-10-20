package com.example.redislab.local.adapter.in.pubsub;

import com.example.redislab.local.adapter.out.pubsub.RedisEvictBroadcastAdapter;
import com.example.redislab.local.application.port.in.EvictLocalUseCase;
import com.example.redislab.local.domain.Servers;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub 으로 들어온 "이 키 지워라"를 유스케이스 호출로 바꿉니다. HTTP 대신 Redis 메시지가 들어오는 in 어댑터입니다.
 * 실제로는 서버마다 따로 구독합니다. 여기서는 서버마다 리스너를 하나씩 붙여서 흉내 냅니다.
 */
@Component
public class EvictMessageListener {

    public EvictMessageListener(RedisMessageListenerContainer container, EvictLocalUseCase evict) {
        for (String server : Servers.ALL) {
            container.addMessageListener((message, pattern) -> {
                long id = Long.parseLong(new String(message.getBody()));
                evict.evictLocal(server, id);
            }, new ChannelTopic(RedisEvictBroadcastAdapter.CHANNEL));
        }
    }
}
