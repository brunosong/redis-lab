package com.example.redislab.web;

import com.example.redislab.annotation.AnnotatedLessonStore;
import com.example.redislab.annotation.LessonEditService;
import com.example.redislab.lesson.Lesson;
import com.example.redislab.local.LocalCacheCluster;
import com.example.redislab.manual.ReadPaths;
import com.example.redislab.manual.WritePaths;
import com.example.redislab.scenario.LabState;
import com.example.redislab.scenario.ScenarioResult;
import com.example.redislab.scenario.Scenarios;
import com.example.redislab.support.Timeline;
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

import static com.example.redislab.scenario.LabState.ID;

@RestController
@RequestMapping("/api")
public class LabController {

    private final Scenarios scenarios;
    private final LabState state;
    private final Timeline timeline;
    private final ReadPaths read;
    private final WritePaths write;
    private final LocalCacheCluster cluster;
    private final AnnotatedLessonStore store;
    private final LessonEditService editService;

    public LabController(Scenarios scenarios, LabState state, Timeline timeline, ReadPaths read, WritePaths write,
                         LocalCacheCluster cluster, AnnotatedLessonStore store, LessonEditService editService) {
        this.scenarios = scenarios;
        this.state = state;
        this.timeline = timeline;
        this.read = read;
        this.write = write;
        this.cluster = cluster;
        this.store = store;
        this.editService = editService;
    }

    @GetMapping("/scenarios")
    public List<Scenarios.Info> list() {
        return scenarios.list();
    }

    @PostMapping("/scenarios/{id}")
    public ScenarioResult run(@PathVariable String id) {
        return scenarios.run(id);
    }

    @GetMapping("/state")
    public Map<String, Object> view() {
        return Map.of("state", state.snapshot(), "timeline", timeline.lines());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        state.reset();
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
            case "server-a" -> cluster.read("서버A", ID);
            case "server-b" -> cluster.read("서버B", ID);
            case "cacheable" -> store.find(ID);
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
            case "server-a" -> cluster.update("서버A", ID, title, false);
            case "server-a-pubsub" -> cluster.update("서버A", ID, title, true);
            case "cache-evict-in-tx" -> editService.edit(ID, title);
            default -> throw new IllegalArgumentException("모르는 쓰기: " + how);
        }
        return view();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
