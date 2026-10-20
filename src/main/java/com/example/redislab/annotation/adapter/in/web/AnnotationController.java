package com.example.redislab.annotation.adapter.in.web;

import com.example.redislab.annotation.adapter.in.scenario.AnnotationScenarios;
import com.example.redislab.annotation.application.port.in.CachedLessonUseCase;
import com.example.redislab.annotation.application.port.in.EditLessonUseCase;
import com.example.redislab.annotation.domain.Lesson;
import com.example.redislab.lab.ScenarioBook;
import com.example.redislab.lab.ScenarioResult;
import com.example.redislab.lab.Timeline;
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

/** 화면 /annotation/index.html 이 부르는 API. HTTP 요청을 유스케이스 호출로 바꿉니다. */
@RestController
@RequestMapping("/api/annotation")
public class AnnotationController {

    private final AnnotationScenarios scenarios;
    private final CachedLessonUseCase store;
    private final EditLessonUseCase editor;
    private final Timeline timeline;

    public AnnotationController(AnnotationScenarios scenarios, CachedLessonUseCase store, EditLessonUseCase editor,
                                Timeline timeline) {
        this.scenarios = scenarios;
        this.store = store;
        this.editor = editor;
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
            case "cacheable" -> store.find(ID);
            case "cacheable-tx-aware" -> store.findTxAware(ID);
            default -> throw new IllegalArgumentException("모르는 읽기: " + how);
        };
        timeline.log("→ 사용자가 받은 값 " + lesson);
        return view();
    }

    /** 손으로 하나씩 눌러 보는 쓰기. */
    @PostMapping("/write")
    public Map<String, Object> write(@RequestParam String how, @RequestParam String title) {
        switch (how) {
            case "cache-evict" -> store.update(ID, title);
            case "cache-evict-in-tx" -> editor.edit(ID, title);
            case "cache-evict-in-tx-aware" -> editor.editTxAware(ID, title);
            default -> throw new IllegalArgumentException("모르는 쓰기: " + how);
        }
        return view();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
