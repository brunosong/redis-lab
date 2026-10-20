package com.example.redislab.local.adapter.in.web;

import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.ScenarioResult;
import com.example.redislab.lab.Timeline;
import com.example.redislab.local.adapter.in.scenario.LocalScenarios;
import com.example.redislab.local.application.port.in.LocalCacheUseCase;
import com.example.redislab.local.domain.Lesson;
import com.example.redislab.local.domain.Servers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.example.redislab.lab.ScenarioBook.ID;

/** 화면 /local/index.html 이 부르는 API. HTTP 요청을 유스케이스 호출로 바꿉니다. */
@RestController
@RequestMapping("/api/local")
public class LocalController {

    private final LocalScenarios scenarios;
    private final LocalCacheUseCase local;
    private final Timeline timeline;

    public LocalController(LocalScenarios scenarios, LocalCacheUseCase local, Timeline timeline) {
        this.scenarios = scenarios;
        this.local = local;
        this.timeline = timeline;
    }

    @GetMapping("/scenarios")
    public List<ScenarioBook.Info> list() {
        return scenarios.list();
    }

    @PostMapping("/scenarios/{id}")
    public ScenarioResult run(@PathVariable String id) {
        return scenarios.run(id);
    }

    @GetMapping("/state")
    public Map<String, Object> view() {
        return Map.of("state", scenarios.snapshot(), "timeline", timeline.lines());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        scenarios.reset();
        timeline.start("처음 상태로 되돌림");
        return view();
    }

    /** 손으로 하나씩 눌러 보는 읽기. */
    @PostMapping("/read")
    public Map<String, Object> read(@RequestParam String how) {
        Lesson lesson = switch (how) {
            case "server-a" -> local.read(Servers.A, ID);
            case "server-b" -> local.read(Servers.B, ID);
            default -> throw new IllegalArgumentException("모르는 읽기: " + how);
        };
        timeline.log("→ 사용자가 받은 값 " + lesson);
        return view();
    }

    /** 손으로 하나씩 눌러 보는 쓰기. 수정 요청은 늘 서버A로 들어온다고 칩니다. */
    @PostMapping("/write")
    public Map<String, Object> write(@RequestParam String how, @RequestParam String title) {
        switch (how) {
            case "server-a" -> local.update(Servers.A, ID, title, false);
            case "server-a-pubsub" -> local.update(Servers.A, ID, title, true);
            default -> throw new IllegalArgumentException("모르는 쓰기: " + how);
        }
        return view();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
