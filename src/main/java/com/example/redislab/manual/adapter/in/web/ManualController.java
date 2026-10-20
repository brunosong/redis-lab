package com.example.redislab.manual.adapter.in.web;

import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.ScenarioResult;
import com.example.redislab.lab.Timeline;
import com.example.redislab.manual.adapter.in.scenario.ManualScenarios;
import com.example.redislab.manual.application.port.in.EditLessonUseCase;
import com.example.redislab.manual.application.port.in.ReadLessonUseCase;
import com.example.redislab.manual.domain.Lesson;
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

/** 화면 /manual/index.html 이 부르는 API. HTTP 요청을 유스케이스 호출로 바꿉니다. */
@RestController
@RequestMapping("/api/manual")
public class ManualController {

    private final ManualScenarios scenarios;
    private final ReadLessonUseCase read;
    private final EditLessonUseCase write;
    private final Timeline timeline;

    public ManualController(ManualScenarios scenarios, ReadLessonUseCase read, EditLessonUseCase write,
                            Timeline timeline) {
        this.scenarios = scenarios;
        this.read = read;
        this.write = write;
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
            case "no-cache" -> read.noCache(ID);
            case "cache-aside" -> read.cacheAside(ID);
            case "replica" -> read.cacheAsideFromReplica(ID);
            default -> throw new IllegalArgumentException("모르는 읽기: " + how);
        };
        timeline.log("→ 사용자가 받은 값 " + lesson);
        return view();
    }

    /** 손으로 하나씩 눌러 보는 쓰기. */
    @PostMapping("/write")
    public Map<String, Object> write(@RequestParam String how, @RequestParam String title) {
        switch (how) {
            case "db-only" -> write.dbOnly(ID, title);
            case "evict-after-commit" -> write.evictAfterCommit(ID, title);
            case "evict-before-commit" -> write.evictBeforeCommit(ID, title);
            case "write-through" -> write.writeThrough(ID, title);
            case "evict-twice" -> write.evictTwice(ID, title);
            default -> throw new IllegalArgumentException("모르는 쓰기: " + how);
        }
        return view();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
