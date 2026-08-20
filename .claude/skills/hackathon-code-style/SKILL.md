---
name: hackathon-code-style
description: 이 해커톤 백엔드(Spring Boot 4.1 / Java 25 / MySQL)에서 코드를 작성·수정·리뷰할 때 반드시 따라야 할 코드 수준 기준과 패키지 구조. "사람이 직접 디버깅할 수 있는 적당한 수준" — 과한 추상화(ServiceImpl 인터페이스, MapStruct, QueryDSL, 커스텀 AOP, 이벤트 드리븐, 제네릭 베이스 클래스)도 금지하고, 원시적인 코드(Controller에 로직 몰아넣기, Map<String,Object> 응답, EntityManager 직접 사용, Entity 직접 반환)도 금지한다. 이 레포의 실제 패키지 구조({도메인}/domain·application·infrastructure·presentation, 포트·어댑터는 Repository에만), 팀 공통 규약(ApiResponse, ErrorCode, SESSION 쿠키 인증, ddl-auto validate), 그리고 "다른 사람이 만든 파일은 수정 금지" 규칙이 여기 있다. Java/Spring 파일을 한 줄이라도 쓰거나 고치기 전에 이 스킬을 먼저 읽어라. Entity, DTO, Service, Controller, Repository, 예외 처리, 테스트 중 무엇을 작성하든 적용된다. 코드 리뷰나 "이거 너무 복잡한가?" 판단에도 사용.
---

# 해커톤 백엔드 코드 수준 기준

## 왜 이 기준이 필요한가

이 코드는 해커톤 기간 동안 **사람이 직접 읽고, 고치고, 디버깅한다.** AI가 만들고 사람이 손대는 코드이므로,
스택 트레이스 한 줄에서 원인 파일까지 바로 도달할 수 있어야 한다.

- **너무 고수준이면** 추상화 계층을 타고 내려가느라 버그 위치를 못 찾는다. 해커톤에는 그 시간이 없다.
- **너무 저수준이면** 로직이 한 곳에 뭉쳐 수정할 때마다 다른 게 깨진다.

목표는 **"IDE에서 Cmd+클릭 두 번이면 실제 로직에 도달"** 하는 코드다.

## 프로젝트 고정 사실 (착각 주의)

이 프로젝트는 **Spring Boot 4.1.0 / Java 25** 다. 관례로 알던 것과 다른 부분이 있으니 확인하고 써라.

| 항목 | 이 프로젝트의 값 |
|------|-----------------|
| 웹 스타터 | `spring-boot-starter-webmvc` (구버전의 `-web`이 **아님**) |
| 웹 테스트 스타터 | `spring-boot-starter-webmvc-test` (`-test` 통합 스타터가 아님) |
| JPA 테스트 스타터 | `spring-boot-starter-data-jpa-test` |
| Java | 25 (record, sealed, pattern matching switch 사용 가능) |
| 패키지 루트 | `hackathon.app` |
| 빌드 | Gradle (`./gradlew`) |

새 의존성이 필요하면 임의로 추가하지 말고 **왜 필요한지 한 줄로 밝히고 사용자에게 확인**받아라.
해커톤에서 의존성 추가는 빌드 실패와 CI 실패의 가장 흔한 원인이다.

## 3단계 판정표 — 무엇을 쓰고 무엇을 쓰지 않는가

### 🚫 너무 저수준 (금지)

| 안티패턴 | 왜 안 되는가 |
|---------|-------------|
| Controller 메서드 안에 비즈니스 로직 + DB 접근 | 테스트 불가, 재사용 불가, 한 파일이 500줄로 부풀음 |
| `Map<String, Object>` 나 `HashMap` 을 응답으로 반환 | 응답 형태가 코드에 안 드러나 프론트와 계약이 깨짐 |
| `EntityManager` / `JdbcTemplate` 직접 사용 | JPA Repository로 되는 일을 굳이 어렵게 함 |
| 문자열 이어붙여 SQL 조립 | SQL 인젝션 + 오타를 컴파일러가 못 잡음 |
| `catch (Exception e) {}` 로 예외 삼키기 | 버그가 조용히 사라져 디버깅이 불가능해짐 |
| Entity를 그대로 응답으로 반환 | 지연로딩 직렬화 터짐 + 내부 필드 노출 |

### ✅ 적당한 수준 (목표 — 이렇게 써라)

| 규칙 | 구체적 형태 |
|------|-----------|
| **3계층 고정** | `Controller → Service → Repository`. 그 이상 계층을 만들지 않는다 |
| **생성자 주입** | `private final XxxService service;` + 생성자 1개. `@Autowired` 필드 주입 금지 |
| **엔티티 게터는 Lombok `@Getter`** | 클래스에 `@Getter` 하나. `@Data`/`@Builder`/`@Setter` 는 쓰지 않는다 |
| **DTO는 record** | `public record TodoResponse(Long id, String title, boolean done) {}` |
| **DTO ↔ Entity 변환은 손으로** | DTO 안에 `static from(Entity)` 정적 메서드. 변환 라이브러리 쓰지 않음 |
| **Repository는 인터페이스만** | `interface TodoRepository extends JpaRepository<Todo, Long> {}` + 파생 쿼리 |
| **복잡한 조회는 @Query JPQL** | 파생 쿼리 이름이 4단어를 넘으면 `@Query`로 JPQL을 직접 쓴다 |
| **예외는 커스텀 1~2종 + 핸들러 1개** | `NotFoundException`, `BadRequestException` + `@RestControllerAdvice` 클래스 하나 |
| **검증은 Bean Validation** | 요청 record에 `@NotBlank`, `@Positive` + 컨트롤러 파라미터에 `@Valid` |
| **트랜잭션은 Service에** | 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional` |

### 🚫 너무 고수준 (금지)

| 안티패턴 | 왜 안 되는가 |
|---------|-------------|
| 헥사고날 / 클린 아키텍처 / 포트&어댑터 | 파일 수가 3배가 되고, 버그 하나 찾는 데 5개 파일을 열어야 함 |
| `TodoService` 인터페이스 + `TodoServiceImpl` (구현체 1개) | Cmd+클릭이 인터페이스로 가서 실제 코드를 한 번 더 찾아야 함 |
| MapStruct, QueryDSL, Lombok `@Builder`/`@Data` | 생성 코드를 디버거로 못 따라감. `@Getter` 는 예외적으로 허용(아래 참조) |
| 커스텀 애노테이션 + AOP (`@LogExecutionTime` 등) | 호출 흐름이 코드에 안 보임. 해커톤에서 최악의 디버깅 경험 |
| `ApplicationEventPublisher` 이벤트 드리븐, CQRS | 어디서 처리되는지 추적 불가 |
| 제네릭 추상 베이스 (`AbstractCrudService<T, ID>`) | 타입 파라미터 지옥. 에러 메시지를 읽을 수 없게 됨 |
| WebFlux / 리액티브 / `CompletableFuture` 남발 | 스택 트레이스가 무의미해짐. 이 프로젝트는 MVC다 |
| 리플렉션, 동적 프록시 직접 구현 | 말할 것도 없음 |

> **경계 판단이 애매하면**: "이 추상화 없이도 오늘 안에 기능이 돌아가는가?" → 돌아가면 추상화하지 마라.
> 해커톤에서 두 번째 구현체는 거의 오지 않는다.

## 트랜잭션 함정 (실제로 500을 낸 사례)

**`@Transactional` 안에서 다른 `@Transactional` 메서드가 던진 예외를 잡아 삼키면, 잡아도 500이 난다.**

```java
// ❌ 이렇게 하면 커밋 시점에 UnexpectedRollbackException(500)
try {
    return authService.requireUser(sessionId);   // 예외를 던짐 → 공유 트랜잭션이 rollback-only 로 표시됨
} catch (ApiException e) {
    return null;                                  // 여기서 잡아도 표시는 지워지지 않는다
}
```

내부 메서드가 같은 트랜잭션에 참여(`PROPAGATION_REQUIRED`)한 상태에서 런타임 예외를 던지면,
트랜잭션 인터셉터가 그 트랜잭션을 **rollback-only** 로 표시한다. 호출부에서 예외를 잡아도 표시는 남아 있고,
바깥 트랜잭션이 커밋하려는 순간 `UnexpectedRollbackException` 이 터진다.

**해결: 예외를 흐름 제어에 쓰지 말고, 없을 수도 있는 값은 `Optional` 로 조회하라.**

```java
// ✅ 예외가 발생하지 않으므로 트랜잭션이 오염되지 않는다
return authSessionRepository.findById(sessionId)
        .filter(AuthSession::isUsable)
        .map(AuthSession::getUserId)
        .orElse(null);
```

**이 버그는 조기 반환 때문에 데이터가 없을 때는 드러나지 않았다.** 빈 DB로 테스트하면 통과하고,
실제 데이터가 들어온 뒤에야 500이 났다. QA 시 **데이터가 있는 상태와 없는 상태를 모두** 호출해 봐야 하는 이유다.

## 디버깅 가능성 규칙 (수준과 별개로 항상 적용)

이 규칙들은 "고수준/저수준" 문제가 아니라 **읽을 수 있는가**의 문제다.

1. **한 메서드 30줄 이하.** 넘으면 같은 클래스 안에서 private 메서드로 쪼갠다 (새 클래스 만들지 말 것).
2. **스트림 체이닝 3단계 이하.** `.stream().map().toList()` 까지가 상한. 중첩 람다 금지 — for 문이 낫다.
3. **`var` 는 우변에 타입이 명시적으로 보일 때만.** `var list = repository.findAll();` 처럼 타입이 안 보이면 명시하라.
4. **매직값 금지.** 숫자·문자열 리터럴이 로직에 등장하면 `private static final` 상수로 뽑는다.
5. **예외 메시지에 값을 담아라.** `"Todo not found"` ❌ → `"Todo not found: id=" + id` ✅. 로그만 보고 재현 가능해야 한다.
6. **조기 반환으로 중첩을 줄여라.** `if` 중첩은 2단계까지.
7. **주석은 "왜"만.** "무엇"은 코드가 말한다. 단, 비직관적인 우회(버전 이슈 회피 등)는 반드시 주석으로 남긴다.
8. **한 클래스는 한 파일, 한 책임.** 내부 클래스로 여러 개 숨기지 않는다 (예외: 응답 record 묶음).

## 패키지 구조 (내가 만드는 코드의 고정 규칙)

**도메인 폴더 먼저, 그 안에서 계층별 서브패키지.** 2026-08-19 사용자가 확정했다.

```
hackathon/app/ranking/
├── entity/            RankingSnapshot.java          @Entity
├── enums/             RankingType.java, PeriodType.java, ...
├── repository/        RankingSnapshotRepository.java  JpaRepository 상속 인터페이스 1개
├── service/           RankingService.java
├── controller/        RankingController.java
└── dto/
    ├── response/      GetRankingResponse.java
    └── request/       (POST/PATCH 가 생길 때만 만든다)
```

**규칙:**

| 규칙 | 내용 |
|------|------|
| 서브패키지 | `entity` / `enums` / `repository` / `service` / `controller` / `dto` |
| DTO | `dto/response/`, `dto/request/` 로 나눈다. 이름은 `{동작}{도메인}Response` (예: `GetRankingResponse`) |
| 중첩 응답 | 응답이 중첩이면 **한 파일 안에 중첩 record** 로 둔다 (`GetRankingResponse` 안에 `RankingItem`, `MyRanking`) |
| Repository | **`JpaRepository` 를 상속한 인터페이스 하나.** 포트/어댑터(도메인 인터페이스 + Adapter)를 만들지 않는다 |
| Service | 클래스 1개. 인터페이스 + Impl 로 나누지 않는다 |
| Controller | 클래스 1개. 로직 없이 받아서 넘긴다 |
| 서브패키지 추가 금지 | `mapper/`, `facade/`, `port/`, `adapter/`, `application/`, `infrastructure/` 를 만들지 않는다 |
| 도메인 간 참조 | 다른 도메인은 Service 를 통해 부른다. 남의 Repository 직접 호출은 꼭 필요할 때만 하고 이유를 주석으로 남긴다 |

**복잡한 구조를 만들지 마라.** 계층 하나당 파일 하나가 기본이다.

> **주의 — 팀의 기존 코드는 구조가 다르다.** `auth/`, `user/`, `preference/` 는 헥사고날
> (`domain`/`application`/`infrastructure`/`presentation` + Repository 포트·어댑터)로 되어 있다.
> 그건 **다른 사람이 만든 코드이므로 읽기만 하고 절대 수정하지 않는다.** 내가 새로 만드는 도메인은 위 구조를 쓴다.

## 팀 공통 규약 (남의 코드 — 읽기만 하고 반드시 따를 것)

| 항목 | 규약 |
|------|------|
| Base path | `@RequestMapping("/api/v1/...")` |
| 성공 응답 | `ApiResponse.of(data)` → `{"data": ...}` |
| 에러 | `throw new ApiException(ErrorCode.XXX)` → `GlobalExceptionHandler` 가 `{code,message,fieldErrors,requestId}` 로 변환 |
| 인증 | `@CookieValue(name="SESSION", required=false) String sessionId` + `authService.requireUser(sessionId)` |
| DB | MySQL + **`ddl-auto: validate`** — 엔티티가 DDL과 다르면 기동 실패한다. 컬럼을 임의로 추가하지 마라 |
| 테스트 | `@ActiveProfiles("test")` → `src/test/resources/application-test.yaml` (H2). 이 파일도 남의 것이라 수정 금지 |
| 접속 정보 | `.env`/환경변수. **하드코딩 금지** |

**`common/error/ErrorCode.java` 에 enum 상수를 추가하는 것도 남의 파일 수정이므로 금지다.**
내 도메인 컨트롤러 안에 `@ExceptionHandler` 를 두어 처리한다.

## 표준 코드 형태

각 계층의 정확한 코드 템플릿은 `references/layer-templates.md` 에 있다.
새 도메인 기능(엔티티 + CRUD 한 벌)을 작성하기 전에 이 파일을 읽어라.

## 커밋 메시지

기존 히스토리 컨벤션을 따른다: `feat: 한글 설명`, `fix: 한글 설명`.
커밋과 푸시는 **사용자가 명시적으로 요청할 때만** 수행한다.

## 자가 점검

코드를 내놓기 전에 스스로 물어라:

- [ ] 이 파일에서 실제 비즈니스 로직이 몇 번의 점프 만에 보이는가? (2회 이하여야 함)
- [ ] 인터페이스를 만들었다면 구현체가 2개 이상인가? 아니면 지워라.
- [ ] 30줄 넘는 메서드가 있는가?
- [ ] 예외 메시지에 식별자(id 등)가 들어 있는가?
- [ ] `spring-boot-starter-webmvc` 계열 이름을 맞게 썼는가?
- [ ] 파일이 `도메인/계층/` 경로에 있고 package 선언이 경로와 일치하는가?
