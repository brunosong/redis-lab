package com.example.redislab.lab;

import java.util.List;
import java.util.Map;

/**
 * 시나리오 한 번 돌린 결과.
 *
 * @param consistent 끝났을 때 캐시와 DB가 맞는지. false 면 정합성이 깨진 상태로 끝난 것
 * @param verdict    결과를 한두 문장으로 풀어 쓴 것
 */
public record ScenarioResult(String id, String title, boolean consistent, String verdict,
                             List<String> timeline, Map<String, Object> state) {
}
