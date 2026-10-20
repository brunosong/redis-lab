package com.example.redislab.stampede.adapter.out.db;

import com.example.redislab.lab.LabDatabase;
import com.example.redislab.lab.Timeline;
import com.example.redislab.stampede.application.port.out.LessonDbPort;
import com.example.redislab.stampede.domain.Lesson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * lesson 테이블을 JdbcTemplate 으로 읽습니다. 조회 지연과 횟수는 LabDatabase 가 흉내 냅니다.
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
}
