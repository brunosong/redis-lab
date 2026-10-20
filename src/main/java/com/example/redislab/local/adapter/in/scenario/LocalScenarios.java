package com.example.redislab.local.adapter.in.scenario;

import com.example.redislab.lab.Gate;
import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.Timeline;
import com.example.redislab.local.application.port.in.InspectLocalCacheUseCase;
import com.example.redislab.local.application.port.in.LocalCacheUseCase;
import com.example.redislab.local.domain.Lesson;
import com.example.redislab.local.domain.Servers;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 서버 두 대의 로컬 캐시 시나리오.
 */
@Component
public class LocalScenarios extends ScenarioBook {

    private final LocalCacheUseCase local;
    private final InspectLocalCacheUseCase inspect;

    public LocalScenarios(LocalCacheUseCase local, InspectLocalCacheUseCase inspect,
                          LabDatabase database, Gate gate, Timeline timeline) {
        super(database, gate, timeline);
        this.local = local;
        this.inspect = inspect;

        add("local-no-broadcast", "서버가 여러 대인 경우", "로컬 캐시 서버 두 대, 자기 것만 지움",
                "서버A로 수정 요청이 왔을 때 서버B 사용자는 무엇을 보나?", () -> run(false));
        add("local-pubsub", "서버가 여러 대인 경우", "로컬 캐시 + Redis Pub/Sub 무효화",
                "고친 서버가 모든 서버에 지우라고 알리면?", () -> run(true));
    }

    @Override
    protected void clearCaches() {
        inspect.clearAll();
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> state = baseSnapshot();
        for (String server : Servers.ALL) {
            Lesson cached = inspect.peek(server, ID);
            state.put(server + " 로컬 캐시", cached == null ? "(없음)" : cached.toString());
        }
        state.put("DB 조회 횟수", database.dbReads());
        return state;
    }

    private Outcome run(boolean broadcast) {
        local.read(Servers.A, ID);
        local.read(Servers.B, ID);
        local.update(Servers.A, ID, "1단원 일차방정식 (개정)", broadcast);
        if (broadcast) {
            waitUntil(() -> inspect.peek(Servers.B, ID) == null, 2000);
        }
        Lesson onA = local.read(Servers.A, ID);
        Lesson onB = local.read(Servers.B, ID);
        String db = database.peekPrimary(ID).toString();
        boolean ok = onA.toString().equals(db) && onB.toString().equals(db);
        String verdict = broadcast
                ? "서버A가 커밋 뒤 Pub/Sub 으로 알렸고, 두 서버가 각자 자기 로컬 캐시를 지웠습니다. 다만 Pub/Sub 은 보내고 끝이라, "
                  + "그 순간 연결이 끊긴 서버는 메시지를 못 받습니다. 그래서 로컬 캐시에도 짧은 만료 시간을 같이 겁니다."
                : "서버A 사용자는 새 값, 서버B 사용자는 옛 값을 봅니다. 로드밸런서가 어느 서버로 보내느냐에 따라 새로고침할 때마다 "
                  + "제목이 바뀌어 보입니다.";
        return new Outcome(ok, verdict);
    }
}
