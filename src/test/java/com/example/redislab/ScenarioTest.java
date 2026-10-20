package com.example.redislab;

import com.example.redislab.scenario.ScenarioResult;
import com.example.redislab.scenario.Scenarios;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시나리오 16개가 매번 같은 결과로 끝나는지 확인합니다.
 * docker compose up -d 로 Redis 를 먼저 띄워야 합니다.
 */
@SpringBootTest(properties = {"lab.db-delay-ms=30", "lab.replica-lag-ms=400", "server.port=0"})
class ScenarioTest {

    @Autowired
    Scenarios scenarios;

    @ParameterizedTest(name = "{0} → 맞음={1}")
    @CsvSource({
            "cache-hit, true",
            "db-only, false",
            "evict-after-commit, true",
            "evict-before-commit, false",
            "evict-after-commit-gap, false",
            "write-through-race, false",
            "replica-lag, false",
            "replica-lag-evict-twice, true",
            "local-no-broadcast, false",
            "local-pubsub, true",
            "stampede, true",
            "stampede-lock, true",
            "annotation-basic, true",
            "annotation-evict-in-tx, false",
            "annotation-tx-aware, true",
            "annotation-self-call, true",
    })
    void scenario(String id, boolean expectedConsistent) {
        ScenarioResult result = scenarios.run(id);
        result.timeline().forEach(System.out::println);
        assertThat(result.consistent()).as(result.verdict()).isEqualTo(expectedConsistent);
    }
}
