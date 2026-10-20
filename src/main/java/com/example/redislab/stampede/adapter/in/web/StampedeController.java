package com.example.redislab.stampede.adapter.in.web;

import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.ScenarioResult;
import com.example.redislab.lab.Timeline;
import com.example.redislab.stampede.adapter.in.scenario.StampedeScenarios;
import com.example.redislab.stampede.application.port.in.ReadLessonUseCase;
import com.example.redislab.stampede.domain.Lesson;
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

/** 화면 /stampede/index.html 이 부르는 API. HTTP 요청을 유스케이스 호출로 바꿉니다. */
@RestController
@RequestMapping("/api/stampede")
public class StampedeController {

    private final StampedeScenarios scenarios;
    private final ReadLessonUseCase read;
    private final Timeline timeline;

    public StampedeController(StampedeScenarios scenarios, ReadLessonUseCase read, Timeline timeline) {
        this.scenarios = scenarios;
        this.read = read;
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

    /** 손으로 하나씩 눌러 보는 읽기. 요청 하나라 몰리지는 않고, 락을 잡고 푸는 모양만 보입니다. */
    @PostMapping("/read")
    public Map<String, Object> read(@RequestParam String how) {
        Lesson lesson = switch (how) {
            case "cache-aside" -> read.cacheAside(ID);
            case "with-lock" -> read.cacheAsideWithLock(ID);
            default -> throw new IllegalArgumentException("모르는 읽기: " + how);
        };
        timeline.log("→ 사용자가 받은 값 " + lesson);
        return view();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
