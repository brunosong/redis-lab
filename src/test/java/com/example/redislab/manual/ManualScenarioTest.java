package com.example.redislab.manual;

import com.example.redislab.manual.adapter.in.scenario.ManualScenarios;
import com.example.redislab.lab.ScenarioResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 직접 짠 Redis 캐시 시나리오 8개가 매번 같은 결과로 끝나는지 확인합니다.
 * docker compose up -d 로 Redis 를 먼저 띄워야 합니다.
 */
@SpringBootTest(properties = {"lab.db-delay-ms=30", "lab.replica-lag-ms=400", "server.port=0"})
class ManualScenarioTest {

    @Autowired
    ManualScenarios scenarios;

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
    })
    void scenario(String id, boolean expectedConsistent) {
        ScenarioResult result = scenarios.run(id);
        result.timeline().forEach(System.out::println);
        assertThat(result.consistent()).as(result.verdict()).isEqualTo(expectedConsistent);
    }
}
