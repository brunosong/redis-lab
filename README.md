# 캐시 정합성 연습장 (redis-lab)

메타엠 면접 4일차 "Redis는 어떻게 써 봤나요? 캐시 정합성은 어떻게 맞췄나요?"를 준비하면서 만든 연습용 프로젝트입니다. 학습 데이터 한 건(lesson 1번)을 두고, 캐시와 DB가 어긋나는 경우와 막는 방법을 16개 시나리오로 재현합니다.

캐시 정합성은 캐시에 든 값과 DB 값이 맞는지를 말합니다. 캐시는 DB 값을 복사해 둔 것이라, DB가 바뀌었는데 캐시가 그대로면 사용자는 옛 값을 봅니다.

---

## 0. 소스는 여기부터

| 파일 | 내용 |
|---|---|
| `src/main/java/com/example/redislab/basic/BasicLessonService.java` | 실무에서 짜는 모양 그대로. 직접 짠 Cache-Aside(`find`, `update`)와 어노테이션(`findWithAnnotation`, `updateWithAnnotation`)을 나란히 둠. 비교용 Write-Through(`updateWriteThrough`, `@CachePut`의 `updateWithCachePut`)도 있음 |
| `src/test/java/com/example/redislab/BasicLessonServiceTest.java` | 읽기, 몰래 고치기(캐시에서 왔다는 증거), 서비스로 고치기, 다시 읽기를 위에서 아래로 확인 |

이 두 파일에는 연습장 장치가 없습니다. 아래 시나리오 코드(`manual`, `annotation`, `scenario` 패키지)는 경쟁 상황을 화면에서 재현하려고 멈춤 지점과 기록 코드가 섞여 있으니, `basic`을 먼저 이해한 뒤에 보세요.

테스트 하나만 돌릴 때:

```
mvnw test -Dtest=BasicLessonServiceTest
```

---

## 1. 실행

```
cd D:\STUDY\redis-lab
docker compose up -d
mvnw spring-boot:run
```

- 화면: http://localhost:8096
- Redis는 6390 포트입니다. 6379는 `brunosong-redis` 컨테이너가 쓰고 있어서 뺐습니다.
- DB는 H2 메모리 DB라 껐다 켜면 처음 상태로 돌아갑니다. 화면의 "처음부터" 버튼도 같은 일을 합니다.
- 테스트: `mvnw test` (시나리오 16개). Redis를 먼저 띄워야 합니다.

연습장 값은 눈으로 보기 쉽게 실제보다 크게 잡았습니다(`application.properties`).

| 값 | 설정 | 뜻 |
|---|---|---|
| DB 조회 지연 | `lab.db-delay-ms=300` | 조회마다 0.3초. 캐시가 왜 필요한지 보이게 |
| 캐시 TTL | `lab.ttl-seconds=20` | 20초 뒤 만료 |
| 복제 지연 | `lab.replica-lag-ms=1500` | 원본 커밋 뒤 1.5초 지나야 복제본에 반영 |

---

## 2. 화면 보는 법

- 왼쪽: 시나리오 목록. "재현"을 누르면 처음 상태로 되돌린 뒤 실행하고, 위에 [맞음] 또는 [어긋남]과 설명이 뜹니다.
- 현재 상태: lesson 1번이 DB 원본, 복제본, Redis 키 세 개, 서버A와 서버B 로컬 캐시에 각각 무슨 값으로 들어 있는지와 TTL이 몇 초 남았는지.
- 직접 눌러 보기: 읽기와 쓰기를 하나씩 눌러 볼 수 있습니다. 예: "Cache-Aside" 읽기 → "DB만" 쓰기 → 다시 "Cache-Aside" 읽기 → 옛 값이 나오고 TTL이 줄어드는 걸 봄.
- 타임라인: 누가(스레드) 몇 ms에 무엇을 했는지. `[쓰기]`, `[읽기]`는 시나리오가 띄운 스레드, `[요청]`은 화면에서 누른 요청, `[복제]`는 복제본 반영입니다.

`version`은 고칠 때마다 1씩 올라서, 캐시에 든 값이 몇 번째 값인지 `(v1)`, `(v2)`로 구분합니다.

---

## 3. Redis를 직접 들여다보기

다른 터미널에서 Redis가 받는 명령을 실시간으로 볼 수 있습니다. 시나리오를 돌리면서 같이 보면 타임라인과 똑같이 찍힙니다.

```
docker exec -it redis-lab-redis-1 redis-cli MONITOR
```

값과 남은 시간을 직접 볼 때:

```
docker exec -it redis-lab-redis-1 redis-cli
> KEYS *
> GET lesson:1
> TTL lesson:1
> GET lessons::1          # @Cacheable 이 만든 키. "캐시이름::키" 모양
> SUBSCRIBE lesson-evict  # 로컬 캐시 무효화 알림을 같이 받아 보기
```

---

## 4. 시나리오 16개

### 1. 기본

| 시나리오 | 결과 | 한 줄 |
|---|---|---|
| 캐시가 왜 빠른가 | 맞음 | 세 번 읽을 때 캐시 없으면 DB 3번, 캐시 있으면 1번 |
| DB만 고치고 캐시는 그대로 | 어긋남 | TTL이 끝날 때까지 옛 값. 잠깐 옛 값이 보여도 되는 데이터면 이것도 선택지 |
| 정석: 커밋 뒤 캐시 키 삭제 | 맞음 | 다음 조회가 DB에서 새 값을 읽어 다시 채움 |

### 2. 순서가 꼬이는 경우

| 시나리오 | 결과 | 한 줄 |
|---|---|---|
| 커밋 전에 지우면 | 어긋남 | 지운 뒤 커밋 전 틈에 읽기가 옛 값을 다시 넣음 |
| 정석에도 남는 아주 작은 틈 | 어긋남 | 읽기가 옛 값을 들고 있는 사이 쓰기가 커밋하고 지우면, 그 뒤 옛 값이 들어감. 드물지만 0은 아님. 그래서 TTL을 안전장치로 같이 검 |
| 지우지 않고 새 값으로 덮어쓰면 | 어긋남 | 두 명이 동시에 고치면 DB는 B안, 캐시는 A안. 지우기 방식이면 순서가 꼬여도 다음 조회가 DB 값을 가져감 |

### 3. 구성 때문에 생기는 경우

| 시나리오 | 결과 | 한 줄 |
|---|---|---|
| 복제본에서 읽어 캐시를 채우면 | 어긋남 | 지운 직후 아직 안 따라온 복제본의 옛 값이 캐시에 들어감 |
| 지연 이중 삭제 | 맞음 | 복제가 끝날 즈음 한 번 더 지워서 어긋나는 시간을 복제 지연 정도로 줄임 |
| 로컬 캐시 서버 두 대, 자기 것만 지움 | 어긋남 | 서버A 사용자는 새 값, 서버B 사용자는 옛 값 |
| 로컬 캐시 + Redis Pub/Sub | 맞음 | 고친 서버가 알리고 모든 서버가 각자 지움. 메시지를 놓칠 수 있어 로컬 캐시에도 짧은 만료를 같이 검 |

복제 지연은 대책이 하나 더 있습니다. 캐시를 채울 때만 원본에서 읽는 것입니다. 연습장의 기본 Cache-Aside(`ReadPaths.cacheAside`)가 그 모양입니다.

### 4. 트래픽 때문에 생기는 경우

| 시나리오 | 결과 | 한 줄 |
|---|---|---|
| 캐시 스탬피드 | DB 20번 안팎 | 캐시가 빈 순간 동시 요청 20개가 모두 DB로 감 |
| 락으로 한 요청만 DB에 | DB 1번 | `SET lock:lesson:1 1 NX`를 잡은 한 요청만 DB에 가고 나머지는 기다렸다가 캐시를 읽음 |

### 5. @Cacheable, @CacheEvict

| 시나리오 | 결과 | 한 줄 |
|---|---|---|
| 어노테이션 기본 동작 | 맞음 | `@Cacheable`은 캐시에 없을 때만 본문 실행, `@CacheEvict`는 메서드가 끝난 뒤 지움 |
| `@Transactional` 안에서 부른 `@CacheEvict` | 어긋남 | `@CacheEvict`는 자기 메서드가 끝나는 순간 지우는데, 바깥 트랜잭션은 아직 커밋 전. "커밋 전에 지우면"과 같은 문제가 어노테이션 뒤에 숨어 있음 |
| transactionAware 캐시 매니저 | 맞음 | 트랜잭션 안의 지우기를 커밋 뒤로 미룸. 손으로 짠 "커밋 뒤 삭제"와 같은 효과 |
| 같은 클래스 안에서 부르면 | DB 3번 vs 1번 | `this.find()`는 프록시를 안 거쳐서 캐시가 아예 안 먹음. `@Transactional`이 같은 클래스 호출에서 안 먹는 것과 같은 이유 |

---

## 5. 코드 위치

| 파일 | 내용 |
|---|---|
| `manual/ReadPaths.java` | 직접 짠 읽기. Cache-Aside, 복제본에서 채우기, 스탬피드 락 |
| `manual/WritePaths.java` | 직접 짠 쓰기. DB만, 커밋 뒤 삭제, 커밋 전 삭제, 덮어쓰기, 지연 이중 삭제 |
| `annotation/AnnotatedLessonStore.java` | `@Cacheable`, `@CacheEvict`. 위 두 개는 기본, 아래 두 개는 transactionAware |
| `annotation/LessonEditService.java` | 바깥 `@Transactional`에서 `@CacheEvict` 메서드를 부르는 흔한 구조 |
| `annotation/CacheConfig.java` | 캐시 매니저 두 개. `setTransactionAware(true)` 한 줄 차이 |
| `annotation/SelfCallExample.java` | 같은 클래스 안 호출 함정 |
| `local/LocalCacheCluster.java` | Caffeine 로컬 캐시 서버 두 대와 Redis Pub/Sub 무효화 |
| `lesson/LessonRepository.java` | 원본, 복제본(복제 지연 흉내), DB 조회 횟수 |
| `scenario/Scenarios.java` | 시나리오 16개 |
| `support/Gate.java` | 경쟁 상황을 매번 같은 순서로 재현하려고 넣은 멈춤 지점 |

연습장이라 실제와 다른 점이 있습니다.

- 경쟁 상황은 실제로는 "가끔" 일어납니다. 연습장은 `Gate`로 스레드를 세워 두었다가 원하는 순서로 풀어서 항상 일어나게 했습니다.
- 복제본은 진짜 복제가 아니라 같은 H2 안의 `lesson_replica` 테이블에 일정 시간 뒤 복사하는 흉내입니다.
- 서버A, 서버B는 한 JVM 안의 Caffeine 캐시 두 개입니다. Pub/Sub은 진짜 Redis를 거칩니다.

---

## 6. 면접에서 말할 것으로 연결

- 정합성은 데이터 성격에 따라 맞춥니다. 잠깐 옛 값이 보여도 되면 TTL만(시나리오 "DB만"), 바뀌면 바로 보여야 하면 커밋 뒤 삭제(정석), 돈처럼 틀리면 안 되면 캐시하지 않습니다.
- 갱신하지 않고 지우는 이유는 "지우지 않고 새 값으로 덮어쓰면" 시나리오입니다.
- 커밋 뒤에 지우는 이유는 "커밋 전에 지우면" 시나리오입니다. `@Transactional` 안에서 `@CacheEvict`를 쓰면 같은 문제가 숨어 있고, transactionAware로 막습니다.
- 정석에도 틈이 남아서 TTL을 안전장치로 겁니다("정석에도 남는 아주 작은 틈").
- 서버가 여러 대인 로컬 캐시는 Pub/Sub으로 무효화를 뿌리고, 메시지를 놓칠 수 있으니 짧은 만료를 같이 겁니다.
