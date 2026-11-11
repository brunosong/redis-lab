package com.example.redislab.basic;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Set;

public class ListAndSet {

    @Test
    public void test(){
        try (JedisPool jedisPool = new JedisPool("localhost", 6379)) {
            Jedis jedis = jedisPool.getResource();

            jedis.rpush("stack1", "100");
            jedis.rpush("stack1", "200");
            jedis.rpush("stack1", "300");

            System.out.println(jedis.lpop("stack1"));

            List<String> lrange = jedis.lrange("stack1", 0, -1);
            System.out.println(lrange);

            jedis.sadd("user:1:name2", "song");
            jedis.sadd("user:1:name2", "bruno");
            jedis.sadd("user:1:name2", "asdf");

            Set<String> smembers = jedis.smembers("user:1:name2");
            System.out.println(smembers);
        }
    }
}
