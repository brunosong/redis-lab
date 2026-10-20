package com.example.redislab.local;

import com.example.redislab.local.adapter.in.scenario.LocalScenarios;
import com.example.redislab.lab.ScenarioResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 캐시 시나리오 2개가 매번 같은 결과로 끝나는지 확인합니다.
 * docker compose up -d 로 Redis 를 먼저 띄워야 합니다.
 */
@SpringBootTest(properties = {"lab.db-delay-ms=30", "lab.replica-lag-ms=400", "server.port=0"})
class LocalScenarioTest {

    @Autowired
    LocalScenarios scenarios;

    @ParameterizedTest(name = "{0} → 맞음={1}")
    @CsvSource({
            "local-no-broadcast, false",
            "local-pubsub, true",
    })
    void scenario(String id, boolean expectedConsistent) {
        ScenarioResult result = scenarios.run(id);
        result.timeline().forEach(System.out::println);
        assertThat(result.consistent()).as(result.verdict()).isEqualTo(expectedConsistent);
    }
}
