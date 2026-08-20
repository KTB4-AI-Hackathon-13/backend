# 계층별 표준 코드 템플릿

`hackathon-code-style` 스킬의 부속 문서. 새 도메인 기능을 작성할 때 이 형태를 그대로 따른다.
예시 도메인은 `ranking` 이며, 실제 도메인 이름으로 치환해서 사용하라.

## 목차

1. 패키지 구조 (필수 규칙)
2. Entity
3. Repository
4. 요청/응답 DTO
5. Service
6. Controller
7. 예외 + 전역 핸들러
8. 설정 파일 (application.yaml)

---

## 1. 패키지 구조 (필수 규칙)

**도메인 폴더를 먼저 만들고, 그 안에서 계층별 서브패키지로 나눈다.**

기능 하나를 고칠 때 도메인 폴더 하나만 열면 되고, 그 안에서 `entity/` `service/` 로 바로 이동할 수 있다.
계층을 최상위에 두는 방식(`controller/` 아래에 모든 도메인의 컨트롤러)은 **쓰지 않는다** —
도메인이 늘어날수록 관련 파일이 폴더 5개로 흩어져 추적이 어려워진다.

```
src/main/java/hackathon/app/
├── AppApplication.java
├── common/                          ← 도메인 공통 (예외, 핸들러)
│   ├── exception/
│   │   ├── NotFoundException.java
│   │   ├── BadRequestException.java
│   │   └── GlobalExceptionHandler.java
│   └── dto/
│       └── ErrorResponse.java
├── ranking/                         ← 도메인 1
│   ├── entity/
│   │   └── Ranking.java
│   ├── repository/
│   │   └── RankingRepository.java
│   ├── service/
│   │   └── RankingService.java
│   ├── controller/
│   │   └── RankingController.java
│   └── dto/
│       ├── RankingCreateRequest.java
│       └── RankingResponse.java
└── user/                            ← 도메인 2 (동일한 5개 서브패키지)
    ├── entity/
    ├── repository/
    ├── service/
    ├── controller/
    └── dto/
```

**규칙:**

| 규칙 | 내용 |
|------|------|
| 도메인 폴더명 | 단수형 소문자 (`ranking`, `user`, `post`) |
| 서브패키지 | `entity`, `repository`, `service`, `controller`, `dto` 이 5개만 사용 |
| 서브패키지 추가 금지 | `mapper/`, `facade/`, `port/`, `adapter/` 같은 폴더를 만들지 않는다 |
| 클래스 1개 = 파일 1개 | 서브패키지 안에 클래스를 몰아넣지 않는다 |
| 도메인 간 참조 | 다른 도메인은 **Service를 통해서만** 호출한다. 남의 Repository를 직접 부르지 않는다 |
| 공통 코드 | `common/` 아래에 두되, 예외·에러응답 정도로 최소한만 |

패키지 선언은 항상 폴더 경로와 일치해야 한다:
`hackathon.app.ranking.entity`, `hackathon.app.ranking.service`, ...

---

## 2. Entity — `ranking/entity/Ranking.java`

- `@Entity` + 기본 생성자는 `protected` (JPA 요구사항)
- 세터를 만들지 않는다. 상태 변경은 **의미 있는 이름의 메서드**로 (`updateScore()`)
- 게터는 클래스에 Lombok `@Getter` 하나로 대신한다 (`build.gradle` 에 lombok + annotationProcessor 있음). `@Data`/`@Builder`/`@Setter` 는 쓰지 않는다

```java
package hackathon.app.ranking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rankings")
public class Ranking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Ranking() {
        // JPA 전용 기본 생성자
    }

    public Ranking(String nickname, int score) {
        this.nickname = nickname;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }

    public void updateScore(int score) {
        this.score = score;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public int getScore() { return score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**연관관계 규칙 (해커톤용):**
- `@ManyToOne` 은 **항상** `fetch = FetchType.LAZY`
- `@OneToMany` 는 정말 필요할 때만. 대부분 `@ManyToOne` 한쪽만으로 충분하다
- 양방향 연관관계는 만들지 않는다 (순환 참조 → 직렬화 무한루프가 해커톤 최다 버그)
- 다른 도메인의 엔티티를 참조할 때는 그 도메인의 `entity` 패키지를 import 한다

```java
import hackathon.app.user.entity.User;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

---

## 3. Repository — `ranking/repository/RankingRepository.java`

인터페이스 하나. 메서드 이름으로 안 되는 조회만 `@Query`.

```java
package hackathon.app.ranking.repository;

import hackathon.app.ranking.entity.Ranking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    List<Ranking> findAllByOrderByScoreDesc();

    // 파생 쿼리 이름이 길어지면 JPQL을 직접 쓴다 — 메서드 이름이 짧아져 읽기 쉽다
    @Query("select r from Ranking r where r.score >= :minScore order by r.score desc")
    List<Ranking> findTopScores(@Param("minScore") int minScore);
}
```

---

## 4. 요청 / 응답 DTO — `ranking/dto/`

- 전부 `record`
- 요청 DTO에 Bean Validation 애노테이션
- 응답 DTO에 `static from(Entity)` 팩토리 — 변환 로직이 한 곳에 모인다

```java
package hackathon.app.ranking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RankingCreateRequest(
        @NotBlank(message = "nickname은 필수입니다")
        @Size(max = 50, message = "nickname은 50자 이하여야 합니다")
        String nickname,

        @PositiveOrZero(message = "score는 0 이상이어야 합니다")
        int score
) {}
```

```java
package hackathon.app.ranking.dto;

import hackathon.app.ranking.entity.Ranking;
import java.time.LocalDateTime;

public record RankingResponse(
        Long id,
        String nickname,
        int score,
        LocalDateTime createdAt
) {
    public static RankingResponse from(Ranking ranking) {
        return new RankingResponse(
                ranking.getId(),
                ranking.getNickname(),
                ranking.getScore(),
                ranking.getCreatedAt()
        );
    }
}
```

> Bean Validation을 쓰려면 `spring-boot-starter-validation` 의존성이 필요하다.
> 없다면 추가 전에 사용자에게 알려라.

---

## 5. Service — `ranking/service/RankingService.java`

- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`
- 조회 실패는 `orElseThrow` 로 즉시 예외 — null을 반환하지 않는다
- 서비스가 DTO를 반환한다 (Entity를 컨트롤러로 내보내지 않음)

```java
package hackathon.app.ranking.service;

import hackathon.app.common.exception.NotFoundException;
import hackathon.app.ranking.dto.RankingCreateRequest;
import hackathon.app.ranking.dto.RankingResponse;
import hackathon.app.ranking.entity.Ranking;
import hackathon.app.ranking.repository.RankingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RankingService {

    private final RankingRepository rankingRepository;

    public RankingService(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    public List<RankingResponse> findAll() {
        return rankingRepository.findAllByOrderByScoreDesc().stream()
                .map(RankingResponse::from)
                .toList();
    }

    public RankingResponse findById(Long id) {
        Ranking ranking = getOrThrow(id);
        return RankingResponse.from(ranking);
    }

    @Transactional
    public RankingResponse create(RankingCreateRequest request) {
        Ranking saved = rankingRepository.save(
                new Ranking(request.nickname(), request.score())
        );
        return RankingResponse.from(saved);
    }

    @Transactional
    public RankingResponse updateScore(Long id, int score) {
        Ranking ranking = getOrThrow(id);
        ranking.updateScore(score);   // 더티 체킹으로 저장됨 — save 호출 불필요
        return RankingResponse.from(ranking);
    }

    @Transactional
    public void delete(Long id) {
        Ranking ranking = getOrThrow(id);
        rankingRepository.delete(ranking);
    }

    // 조회 실패 메시지를 한 곳에서 관리한다
    private Ranking getOrThrow(Long id) {
        return rankingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ranking을 찾을 수 없습니다: id=" + id));
    }
}
```

---

## 6. Controller — `ranking/controller/RankingController.java`

- `@RestController` + `@RequestMapping("/api/...")`
- 상태 코드가 200이 아닐 때만 `ResponseEntity` 를 쓴다 (프로젝트 전체 통일)
- 컨트롤러에는 분기 로직을 두지 않는다. 받아서 넘기고 상태 코드만 정한다

```java
package hackathon.app.ranking.controller;

import hackathon.app.ranking.dto.RankingCreateRequest;
import hackathon.app.ranking.dto.RankingResponse;
import hackathon.app.ranking.service.RankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    public List<RankingResponse> findAll() {
        return rankingService.findAll();
    }

    @GetMapping("/{id}")
    public RankingResponse findById(@PathVariable Long id) {
        return rankingService.findById(id);
    }

    @PostMapping
    public ResponseEntity<RankingResponse> create(@Valid @RequestBody RankingCreateRequest request) {
        RankingResponse response = rankingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        rankingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 7. 예외 + 전역 핸들러 — `common/`

프로젝트 전체에 예외 클래스 2개와 핸들러 1개면 충분하다. 도메인마다 예외를 만들지 마라.

```java
package hackathon.app.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

```java
package hackathon.app.common.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

```java
package hackathon.app.common.dto;

public record ErrorResponse(String message) {}
```

```java
package hackathon.app.common.exception;

import hackathon.app.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        log.warn("404: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("400: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage()));
    }

    // @Valid 검증 실패 — 어떤 필드가 왜 틀렸는지 그대로 내려준다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다");
        log.warn("400 validation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message));
    }

    // 예상 못 한 예외는 스택 트레이스를 반드시 남긴다 — 해커톤 디버깅의 생명줄
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("500 처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버 오류가 발생했습니다"));
    }
}
```

---

## 8. 설정 파일

**현재 `application.yaml` 에 datasource 설정이 없다.** JPA 엔티티를 처음 추가하는 순간
애플리케이션이 기동에 실패한다. 첫 엔티티를 만들 때 아래 중 하나를 반드시 함께 설정하라.

로컬 개발 + 데모용 (별도 DB 서버 불필요, 가장 빠름):

```yaml
spring:
  application:
    name: app
  datasource:
    url: jdbc:h2:mem:hackathon;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
    show-sql: true
  h2:
    console:
      enabled: true   # http://localhost:8080/h2-console
```

> 주의: 현재 `build.gradle` 에서 H2는 `testRuntimeOnly` 다. 위 설정을 로컬 실행에도 쓰려면
> `runtimeOnly 'com.h2database:h2'` 로 바꿔야 한다. 실제 DB(MySQL 등)를 쓰기로 했다면
> 그 사실을 먼저 사용자에게 확인하고, 접속 정보는 환경변수(`${DB_URL}` 등)로 주입하라.
> **접속 정보를 코드나 yaml에 하드코딩해서 커밋하지 마라.**

운영/배포용 값은 환경변수로:

```yaml
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```
