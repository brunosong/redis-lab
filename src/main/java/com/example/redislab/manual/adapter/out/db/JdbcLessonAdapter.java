package com.example.redislab.manual.adapter.out.db;

import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.Timeline;
import com.example.redislab.manual.application.port.out.LessonDbPort;
import com.example.redislab.manual.domain.Lesson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * lesson(원본), lesson_replica(복제본) 테이블을 JdbcTemplate 으로 읽고 고칩니다.
 * 조회 지연, 조회 횟수, 복제 지연은 LabDatabase 가 흉내 냅니다.
 */
@Component
public class JdbcLessonAdapter implements LessonDbPort {

    private static final RowMapper<Lesson> MAPPER =
            (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version"));

    private final JdbcTemplate jdbc;
    private final LabDatabase labDatabase;
    private final Timeline timeline;

    public JdbcLessonAdapter(JdbcTemplate jdbc, LabDatabase labDatabase, Timeline timeline) {
        this.jdbc = jdbc;
        this.labDatabase = labDatabase;
        this.timeline = timeline;
    }

    @Override
    public Lesson findFromPrimary(long id) {
        labDatabase.slowQuery();
        Lesson lesson = jdbc.queryForObject("SELECT * FROM lesson WHERE id = ?", MAPPER, id);
        timeline.log("DB(원본) 조회 → " + lesson);
        return lesson;
    }

    @Override
    public Lesson findFromReplica(long id) {
        labDatabase.slowQuery();
        Lesson lesson = jdbc.queryForObject("SELECT * FROM lesson_replica WHERE id = ?", MAPPER, id);
        timeline.log("DB(복제본) 조회 → " + lesson);
        return lesson;
    }

    @Override
    public Lesson update(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                title, id);
        Lesson updated = jdbc.queryForObject("SELECT * FROM lesson WHERE id = ?", MAPPER, id);
        boolean inTx = labDatabase.wrotePrimary(id);
        timeline.log("DB(원본) UPDATE → " + updated + (inTx ? " (아직 커밋 전)" : " (바로 커밋)"));
        return updated;
    }
}
