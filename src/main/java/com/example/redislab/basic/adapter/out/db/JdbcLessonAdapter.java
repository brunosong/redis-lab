package com.example.redislab.basic.adapter.out.db;

import com.example.redislab.basic.application.port.out.LessonDbPort;
import com.example.redislab.basic.domain.Lesson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** lesson 테이블을 JdbcTemplate 으로 읽고 고칩니다. */
@Component
public class JdbcLessonAdapter implements LessonDbPort {

    private static final RowMapper<Lesson> MAPPER =
            (rs, i) -> new Lesson(rs.getLong("id"), rs.getString("title"), rs.getInt("version"));

    private final JdbcTemplate jdbc;

    public JdbcLessonAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Lesson find(long id) {
        return jdbc.queryForObject("SELECT id, title, version FROM lesson WHERE id = ?", MAPPER, id);
    }

    @Override
    public Lesson update(long id, String title) {
        jdbc.update("UPDATE lesson SET title = ?, version = version + 1 WHERE id = ?", title, id);
        return find(id);
    }
}
