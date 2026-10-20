package com.example.redislab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * 기능마다 패키지를 따로 두고 코드가 겹쳐도 각자 가집니다. 그래서 manual 의 JdbcLessonAdapter 와
 * stampede 의 JdbcLessonAdapter 처럼 이름이 같은 클래스가 생깁니다. 빈 이름을 패키지까지 붙인
 * 전체 이름으로 지어서 서로 부딪치지 않게 합니다.
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class RedisLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisLabApplication.class, args);
    }
}
