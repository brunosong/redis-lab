package com.example.redislab.basic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

public class Strings {

    @Test
    void strings() {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            // 1. 기본 SET: 키가 이미 있으면 덮어씀
            String r1 = jedis.set("user:1:name", "brunosong");
            System.out.println(r1);

            // 2. 만료 시간 지정 (SET key value EX 10)
            jedis.set("session:abc", "data", SetParams.setParams().ex(10));
            System.out.println(jedis.ttl("session:abc"));    // 10

            // 3. NX: 키가 없을 때만 저장 (락이나 중복 방지에 씀)
            String r3 = jedis.set("lock:order:1", "owner-A", SetParams.setParams().nx().ex(30));
            String r4 = jedis.set("lock:order:1", "owner-B", SetParams.setParams().nx().ex(30));
            System.out.println(r3);                          // OK
            System.out.println(r4);                          // null  (이미 있어서 실패)

            // 4. XX: 키가 있을 때만 저장
            String r5 = jedis.set("not:exists", "v", SetParams.setParams().xx());
            System.out.println(r5);                          // null

            // 5. KEEPTTL: 값만 바꾸고 남은 TTL은 유지
            jedis.set("session:abc", "data2", SetParams.setParams().keepTtl());
            System.out.println(jedis.ttl("session:abc"));    // 남은 TTL 그대로

            // 6. GET 옵션: 새 값을 넣으면서 이전 값을 돌려받음 (Redis 6.2+)
            String old = jedis.setGet("user:1:name", "song");
            System.out.println(old);                         // bruno

        }
    }
}
