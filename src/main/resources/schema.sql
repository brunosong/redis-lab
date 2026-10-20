-- 테스트 클래스마다 스프링 컨텍스트가 따로 떠도 같은 메모리 DB를 쓰기 때문에 먼저 지우고 만듭니다.
DROP TABLE IF EXISTS lesson;
DROP TABLE IF EXISTS lesson_replica;

-- 원본(master). 쓰기는 항상 여기로 갑니다.
CREATE TABLE lesson (
    id         BIGINT PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    version    INT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 복제본(slave) 흉내. 원본이 바뀌면 lab.replica-lag-ms 뒤에 따라옵니다.
CREATE TABLE lesson_replica (
    id         BIGINT PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    version    INT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
