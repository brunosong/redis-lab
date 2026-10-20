package com.example.redislab.basic;

import com.example.redislab.basic.application.BasicLessonService;
import com.example.redislab.basic.domain.Lesson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BasicLessonService 를 위에서 아래로 읽히게 확인합니다.
 * docker compose up -d 로 Redis 를 먼저 띄워야 합니다.
 */
@SpringBootTest(properties = "server.port=0")
class BasicLessonServiceTest {

    @Autowired
    BasicLessonService service;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE lesson SET title = '1단원 일차방정식', version = 1 WHERE id = 1");
        redis.delete("basic:lesson:1");
        redis.delete("basic-lessons::1");
    }

    @Test
    void 직접_짠_Cache_Aside() {
        // 처음 읽으면 캐시에 없어서 DB에서 읽고, 캐시에 넣습니다.
        Lesson first = service.find(1);
        assertThat(first.title()).isEqualTo("1단원 일차방정식");
        assertThat(redis.hasKey("basic:lesson:1")).isTrue();

        // 캐시를 안 거치고 DB만 몰래 고치면, 다음 읽기는 여전히 캐시의 옛 값입니다. 캐시에서 왔다는 증거입니다.
        jdbc.update("UPDATE lesson SET title = '몰래 고침' WHERE id = 1");
        assertThat(service.find(1).title()).isEqualTo("1단원 일차방정식");

        // 서비스로 고치면 커밋 뒤 캐시 키가 지워지고, 다음 읽기가 DB의 새 값을 가져옵니다.
        service.update(1, "1단원 일차방정식 (개정)");
        assertThat(redis.hasKey("basic:lesson:1")).isFalse();
        assertThat(service.find(1).title()).isEqualTo("1단원 일차방정식 (개정)");
    }

    @Test
    void Write_Through_는_고친_뒤_캐시에_새_값이_바로_들어간다() {
        service.find(1);

        // 지우기 방식은 고친 뒤 키가 사라지지만, Write-Through 는 키가 남아 있고 새 값이 들어 있습니다.
        service.updateWriteThrough(1, "1단원 일차방정식 (개정)");
        assertThat(redis.hasKey("basic:lesson:1")).isTrue();

        // DB를 몰래 고쳐도 캐시의 새 값이 나옵니다. 고친 직후 첫 조회도 DB에 안 갔다는 증거입니다.
        jdbc.update("UPDATE lesson SET title = '몰래 고침' WHERE id = 1");
        assertThat(service.find(1).title()).isEqualTo("1단원 일차방정식 (개정)");
    }

    @Test
    void 어노테이션_CachePut() {
        service.findWithAnnotation(1);

        service.updateWithCachePut(1, "1단원 일차방정식 (개정)");
        assertThat(redis.hasKey("basic-lessons::1")).isTrue();

        jdbc.update("UPDATE lesson SET title = '몰래 고침' WHERE id = 1");
        assertThat(service.findWithAnnotation(1).title()).isEqualTo("1단원 일차방정식 (개정)");
    }

    @Test
    void 어노테이션_Cacheable_CacheEvict() {
        Lesson first = service.findWithAnnotation(1);
        assertThat(first.title()).isEqualTo("1단원 일차방정식");
        assertThat(redis.hasKey("basic-lessons::1")).isTrue();

        jdbc.update("UPDATE lesson SET title = '몰래 고침' WHERE id = 1");
        assertThat(service.findWithAnnotation(1).title()).isEqualTo("1단원 일차방정식");

        service.updateWithAnnotation(1, "1단원 일차방정식 (개정)");
        assertThat(redis.hasKey("basic-lessons::1")).isFalse();
        assertThat(service.findWithAnnotation(1).title()).isEqualTo("1단원 일차방정식 (개정)");
    }
}
