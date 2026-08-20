# 01. API 명세 — 랭킹 조회

작성: api-designer / 2026-08-19 (최종 갱신 2026-08-19 — **구현 완료된 코드에 맞춰 동기화됨**)
기준 문서: `_workspace/00_context.md` (리더 컨텍스트), Notion `API_SPECIFICATION_NOTION (1)` 9번
패키지 구조: **계층형** `ranking/{entity,enums,repository,service,controller,dto}` — 사용자 결정으로 헥사고날(포트/어댑터)을 폐기했다

> 이 문서는 `src/main/java/hackathon/app/ranking/` 의 **실제 구현과 1:1로 일치**한다. 코드가 정답이며, 불일치를 발견하면 이 문서를 고친다.

---

## ⛔ 배포 전 필수 — DB DDL 변경이 선행되어야 한다

`PeriodType` 이 `WEEKLY` / `MONTHLY` / `YEARLY` 로 확정됐지만, 현재 `ranking_snapshots` 테이블은 여전히
`period_type ENUM('DAILY','WEEKLY','MONTHLY','ALL')` 이라 **`YEARLY` 값이 DB에 없다.**

```sql
ALTER TABLE ranking_snapshots
  MODIFY COLUMN period_type ENUM('WEEKLY','MONTHLY','YEARLY') NOT NULL DEFAULT 'WEEKLY';
```

이 변경을 하지 않으면:
- `period=YEARLY` 로 조회할 때 MySQL 이 ENUM 에 없는 값을 비교하게 되어 조회가 어긋난다
- `DAILY` / `ALL` 로 적재된 **기존 행을 읽는 순간** `PeriodType.valueOf` 가 실패해 런타임 예외가 난다 (기존 데이터가 있다면 먼저 정리하거나 삭제해야 한다)
- 스키마 검증 설정에 따라 `ddl-auto: validate` 기동 자체가 실패할 수 있다

DDL 변경은 **사용자가 직접 수행**하기로 했다. 11번 배치 태스크 담당자에게도 이 ENUM 변경을 공유해야 한다.

---

## 1. 기능 요약

`ranking_snapshots` 테이블에 배치가 미리 적재해 둔 랭킹 스냅샷을 **읽기 전용**으로 조회하는 API 하나를 만든다.
사용자는 랭킹 기준(`type`)과 선택적으로 카테고리·기간을 지정해 상위 랭커 목록을 볼 수 있고, 로그인 상태라면 같은 응답 안에서 자신의 순위·점수·티어(`myRanking`)를 함께 받는다.
스냅샷을 적재하는 배치(11번 태스크)와 `GET /users/{userId}/public-puzzles` 는 **이번 범위 밖**이며, 스냅샷이 비어 있어도 500이 아니라 200 + 빈 배열을 반환한다.

---

## 2. 도메인 / 엔티티

### 2.1 `RankingSnapshot` (테이블 `ranking_snapshots`)

> `ddl-auto: validate` 다. 아래 컬럼명·타입·nullable 은 00_context.md 의 DDL과 1:1로 일치해야 하며 **컬럼을 새로 만들지 않는다.**

| 필드 | 자바 타입 | 컬럼 | 제약 | 설명 |
|------|----------|------|------|------|
| id | Long | `id` | PK, `IDENTITY` | 식별자 |
| rankingDate | LocalDate | `ranking_date` | not null | 스냅샷 기준일 |
| rankingType | RankingType | `ranking_type` | not null, `@Enumerated(STRING)` | 랭킹 기준 |
| periodType | PeriodType | `period_type` | not null, `@Enumerated(STRING)` | 집계 기간 |
| scope | RankingScope | `scope` | not null, `@Enumerated(STRING)` | 전체/카테고리 |
| categoryId | Long | `category_id` | **nullable** | `scope=CATEGORY` 일 때만 값이 있음 |
| userId | Long | `user_id` | not null | 대상 사용자 (연관관계 매핑 안 함, 값만 보유) |
| rankNo | int | `rank_no` | not null | 순위 (1부터) |
| score | BigDecimal | `score` | not null, `precision=14, scale=2` | 점수 |
| puzzleCount | int | `puzzle_count` | not null | (응답에 안 씀, DDL 매핑용) |
| activeDays | int | `active_days` | not null | (응답에 안 씀, DDL 매핑용) |
| achievementRate | BigDecimal | `achievement_rate` | not null, `precision=5, scale=2` | (응답에 안 씀, DDL 매핑용) |
| createdAt | LocalDateTime | `created_at` | not null | 스냅샷 생성 시각 |

**연관관계: 없음.** `user_id` / `category_id` 는 FK가 걸려 있지만 **`@ManyToOne` 을 쓰지 않고 `Long` 원시 값으로 둔다.**
이유 — (1) `User` 는 남의 엔티티라 연관 추가 시 양쪽 매핑을 건드릴 위험이 있고, (2) `Category` 엔티티는 이 레포에 아직 존재하지 않는다.

**엔티티 규약**: 클래스에 **Lombok `@Getter`** 를 붙이고 게터를 직접 작성하지 않는다.
`protected RankingSnapshot() {}` 기본 생성자만 두고 **세터·정적 팩토리는 없다**
(이 API는 읽기 전용이고 쓰기는 11번 배치 태스크 소관이므로, 여기서 생성 API를 만들면 나중에 배치와 충돌한다).

### 2.2 열거형 (`ranking/enums/`)

| 열거형 | 값 | 비고 |
|--------|-----|------|
| `RankingType` | `STREAK`, `COMPLETED_PUZZLES` | DB `ranking_type` ENUM 과 일치해야 함. 계획을 실천한 일 수 / 완성한 퍼즐 개수 |
| `PeriodType` | `WEEKLY`, `MONTHLY`, `YEARLY` | 기본값 `WEEKLY`. **DDL 변경 선행 필요** (문서 상단 ⛔ 참조) |
| `RankingScope` | `OVERALL`, `CATEGORY` | DB `scope` ENUM 과 동일 |
| `RankingTier` | `DIAMOND`, `PLATINUM`, `GOLD`, `SILVER`, `BRONZE` | **DB에 없음. 코드에서 파생** (4.4 참조) |

> `RankingType` 에서 **`PUZZLE_PIECES`(획득 조각 수)는 제외**됐다. Notion 원문에는 있었으나 이번 범위에서 빠졌다.
> `PeriodType` 의 **`DAILY` / `ALL` 도 제외**됐고 `YEARLY` 가 새로 들어갔다.

---

## 3. API 엔드포인트

| Method | Path | 설명 | 인증 | 요청 | 성공 응답 |
|--------|------|------|------|------|----------|
| GET | `/api/v1/rankings` | 랭킹 조회 (rank 오름차순, **상위 50명 고정**) | **선택** (`SESSION` 쿠키) | 쿼리 파라미터 3종 | 200 `ApiResponse<GetRankingResponse>` |

- 컨트롤러: `@RestController @RequestMapping("/api/v1/rankings")`, 메서드는 팀 관례대로 **package-private** (`public` 안 붙임)
- 인증: `@CookieValue(name = "SESSION", required = false) String sessionId`

### 3.1 요청 쿼리 파라미터

| 이름 | 타입 | 필수 | 기본값 | 허용값 / 검증 |
|------|------|------|--------|--------------|
| `type` | String | **O** | — | `STREAK` \| `COMPLETED_PUZZLES` (대소문자 구분, 정확히 일치) |
| `categoryId` | Long | X | `null` | 있으면 `scope=CATEGORY`, 없으면 `scope=OVERALL` |
| `period` | String | X | `WEEKLY` | `WEEKLY` \| `MONTHLY` \| `YEARLY` |

**파라미터는 이 3개가 전부다.** 개수 조절용 `size` 파라미터는 없다 — 응답은 **항상 상위 50명**이며
`RankingService.RANKING_SIZE = 50` 상수로 고정돼 있다.

> **컨트롤러 파라미터 타입은 `type`/`period` 를 `String` 으로 받는다.** enum 으로 직접 바인딩하면 잘못된 값이 `MethodArgumentTypeMismatchException` 을 던져 팀 `GlobalExceptionHandler` 를 타지 못하고 Spring 기본 에러 바디(`{timestamp,status,error,path}`)가 나간다. 문자열로 받아 서비스에서 파싱하고 우리 예외로 변환해야 응답 형태가 팀 계약과 같아진다.

### 3.2 파라미터 → 조회 조건 매핑 (구현된 동작)

1. `type` → `RankingType.valueOf(type)`. 실패 시 400 (5.2)
2. `period` → `null`/공백이면 `PeriodType.WEEKLY`, 아니면 `PeriodType.valueOf(period)`. 실패 시 400
3. `categoryId` → `null` 이면 `scope = OVERALL`, 값이 있으면 `scope = CATEGORY`
   - `categoryId` 존재 여부는 **검증하지 않는다** (`Category` 엔티티가 없어 확인 불가). 없는 카테고리면 결과가 빈 배열로 나간다
   - `scope = OVERALL` 로 조회할 때 `categoryId` 조건은 걸지 않는다 (해당 행들은 DDL상 `category_id IS NULL`)
4. **조회 개수**: 요청과 무관하게 `PageRequest.of(0, 50)` 로 상위 50명
5. **`rankingDate` 결정**: 위 4개 조건(`rankingType`, `periodType`, `scope`, `categoryId`)에 해당하는 행들 중 **`MAX(ranking_date)`** 을 기준일로 잡는다.
   요청 파라미터에 날짜가 없으므로 "가장 최근에 적재된 스냅샷"이 곧 조회 대상이다.
   결과가 없으면(=해당 조건의 스냅샷이 한 건도 없으면) 조회를 중단하고 `rankingDate: null`, `items: []`, `myRanking: null` 로 200 반환.

### 3.3 인증 처리 (선택 인증)

```
User me = null;
try { me = authService.requireUser(sessionId); }
catch (ApiException e) { /* 비로그인으로 간주 */ }
```

- 세션 쿠키가 없거나 만료·폐기됐으면 `AUTHENTICATION_REQUIRED` 가, 사용자가 없으면 `USER_NOT_FOUND` 가 던져진다. **둘 다 잡아서 비로그인으로 처리**한다 (`ApiException` 한 번만 catch 하면 둘 다 걸린다).
- **이 엔드포인트는 어떤 경우에도 401을 내지 않는다.**
- 세션 유효성 판정을 우리 쪽에 복제하지 않는다 (복제하면 auth 담당이 규칙을 바꿀 때 어긋난다).

---

## 4. 요청 / 응답 스키마

모든 성공 응답은 `ApiResponse.of(data)` 로 감싸지므로 실제 JSON은 **`{"data": {...}}`** 형태다. meta 없음.

### 4.1 `GetRankingResponse` (`data` 본문)

클래스 위치: `ranking/dto/response/GetRankingResponse.java` — **응답 DTO는 이 한 파일이며**
`RankingItem`, `MyRanking` 을 중첩 record 로 품고 있다.

| 필드 | 타입 | null 가능 | 설명 |
|------|------|-----------|------|
| `rankingDate` | String (ISO-8601 date, `yyyy-MM-dd`) | **O** | 스냅샷 기준일. 데이터가 없으면 `null` |
| `items` | RankingItem[] | X (빈 배열 가능) | rank 오름차순, **최대 50개** |
| `myRanking` | MyRanking | **O** | 비로그인이거나 내 스냅샷 행이 없으면 `null` |

> 자바 타입은 `LocalDate rankingDate` 로 두면 Jackson 이 `"2026-08-19"` 로 직렬화한다.

정적 팩토리 2개를 갖는다:
- `GetRankingResponse.of(rankingDate, snapshots, nicknames, mySnapshot, participants)` — 정상 결과 조립
- `GetRankingResponse.empty()` — 스냅샷이 없을 때 `(null, List.of(), null)`

### 4.2 `RankingItem` (중첩 record)

| 필드 | 타입 | null 가능 | 설명 |
|------|------|-----------|------|
| `rank` | int | X | `rank_no` 값 (1부터) |
| `userId` | Long | X | 사용자 식별자 |
| `nickname` | String | X | `users.nickname`. 조회 실패 시 `"알 수 없음"` |
| `score` | Number | X | `DECIMAL(14,2)` → `BigDecimal`. JSON에 **따옴표 없는 숫자**로 나가며 소수점 2자리가 유지된다 (`1250.00`) |

생성: `RankingItem.of(RankingSnapshot snapshot, String nickname)`

### 4.3 `MyRanking` (중첩 record)

| 필드 | 타입 | null 가능 | 설명 |
|------|------|-----------|------|
| `rank` | int | X | 내 `rank_no` |
| `score` | Number | X | 내 `score` (BigDecimal) |
| `tier` | String | X | `DIAMOND` \| `PLATINUM` \| `GOLD` \| `SILVER` \| `BRONZE` |

생성: `MyRanking.of(RankingSnapshot snapshot, long participants)` — 내부에서 `RankingTier.of(rankNo, participants)` 를 호출한다.

**`myRanking` 은 필드를 생략하지 않는다.** 값이 없을 때도 키는 항상 존재하고 값이 `null` 이다
(`{"myRanking": null}`). 프론트는 `data.myRanking === null` 하나만 분기하면 된다. 이를 위해 응답 record에 `@JsonInclude` 를 붙이지 않는다 (Jackson 기본 동작이 null 포함이므로 아무것도 하지 않으면 된다).

`myRanking` 이 `null` 이 되는 경우는 두 가지다:
1. 비로그인 (세션 없음/만료)
2. 로그인했지만 해당 `(rankingDate, type, period, scope, categoryId)` 조합에 내 행이 없음 (랭킹 미참여, 신규 가입 등)

내가 상위 50위 밖에 있어도 `myRanking` 은 채워진다 — `items` 와 **별도 쿼리**(`findByUser`)로 조회한다.

### 4.4 `tier` 산정 규칙 (DB 컬럼 없음 → 코드 파생) — **확정**

`ranking_snapshots` 에도 `users` 에도 tier 컬럼이 없으므로 **순위 백분위**로 계산한다.

```
participants = 같은 (rankingDate, rankingType, periodType, scope, categoryId) 행의 총 개수   // COUNT(*)
topPercent   = (double) rankNo / participants * 100
```

| 조건 | tier |
|------|------|
| `rankNo == 1` | `DIAMOND` |
| `topPercent <= 5` | `DIAMOND` |
| `topPercent <= 15` | `PLATINUM` |
| `topPercent <= 30` | `GOLD` |
| `topPercent <= 60` | `SILVER` |
| 그 외 | `BRONZE` |

- `rankNo == 1` 을 먼저 특수 처리하는 이유: 참가자가 3명일 때 1등이 33% 라서 `GOLD` 로 떨어지는 것을 막는다.
- 경계값은 **이하(`<=`)** 로 통일한다.
- 구현 위치: `ranking/enums/RankingTier.of(int rankNo, long participants)` **정적 메서드** (기준이 바뀌면 이 파일 하나만 고치면 된다). 경계값 5/15/30/60 은 `private static final double` 상수다.
- `participants <= 0` 이면 계산 불가 → `BRONZE` 반환. 실제로는 `MyRanking` 을 만들 때만 호출되고 그때 `participants` 는 `countParticipants` 결과라 최소 1이다.
- `participants` 카운트 쿼리는 **내 스냅샷이 있을 때만** 실행된다 (비로그인/미참여면 `0` 을 넘기고 `myRanking` 자체가 `null` 이므로 tier 계산도 하지 않는다).

### 4.5 응답 예시

**(a) 로그인 상태, `GET /api/v1/rankings?type=STREAK`** (period 생략 → `WEEKLY`, `items` 는 최대 50개이며 아래는 앞 3개만 표시)

```json
{
  "data": {
    "rankingDate": "2026-08-19",
    "items": [
      { "rank": 1, "userId": 12, "nickname": "김철수", "score": 87.00 },
      { "rank": 2, "userId": 45, "nickname": "박영희", "score": 81.00 },
      { "rank": 3, "userId": 7,  "nickname": "이민준", "score": 76.00 }
    ],
    "myRanking": { "rank": 34, "score": 21.00, "tier": "GOLD" }
  }
}
```

**(b) 비로그인, `GET /api/v1/rankings?type=COMPLETED_PUZZLES&categoryId=3&period=MONTHLY`** (앞 2개만 표시)

```json
{
  "data": {
    "rankingDate": "2026-08-19",
    "items": [
      { "rank": 1, "userId": 45, "nickname": "박영희", "score": 12.00 },
      { "rank": 2, "userId": 12, "nickname": "김철수", "score": 9.00 }
    ],
    "myRanking": null
  }
}
```

**(c) 스냅샷이 아직 적재되지 않음 (배치 미실행) — 200**

```json
{
  "data": {
    "rankingDate": null,
    "items": [],
    "myRanking": null
  }
}
```

---

## 5. 에러 응답

### 5.1 응답 형태

팀 `GlobalExceptionHandler.ErrorResponse` 와 **완전히 동일한 형태**로 나가야 한다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청값을 확인해주세요.",
  "fieldErrors": [ { "field": "type", "message": "허용되지 않는 값입니다: STREAKS" } ],
  "requestId": "4b1c8f2e-9a3d-4a77-b0c1-2f5e6d7a8b90"
}
```

> 이 응답은 `common/error/GlobalExceptionHandler.ErrorResponse` / `FieldError` **public 중첩 record 를 그대로 재사용**한다 (읽기만 하는 것이므로 남의 파일 수정이 아니다).

### 5.2 에러 케이스 표

| 상황 | 상태 코드 | `code` | `fieldErrors[0]` |
|------|----------|--------|------------------|
| `type` 파라미터 누락 | 400 | `INVALID_REQUEST` | `field="type"`, `message="필수 파라미터입니다."` |
| `type` 이 허용값이 아님 | 400 | `INVALID_REQUEST` | `field="type"`, `message="허용되지 않는 값입니다: {입력값}"` |
| `period` 가 허용값이 아님 | 400 | `INVALID_REQUEST` | `field="period"`, `message="허용되지 않는 값입니다: {입력값}"` |
| `categoryId` 가 숫자가 아님 | 400 | `INVALID_REQUEST` | `field="categoryId"`, `message="숫자여야 합니다: {입력값}"` |
| 세션 쿠키가 없음 / 만료 / 폐기 | **200** | — | 비로그인 취급, `myRanking: null` |
| 해당 조건 스냅샷 0건 | **200** | — | `rankingDate: null`, `items: []` |
| 존재하지 않는 `categoryId` | **200** | — | `rankingDate: null`, `items: []` (존재 검증 안 함) |

**이 엔드포인트는 401 / 404 를 반환하지 않는다.**

### 5.3 에러를 우리 계약대로 내보내는 방법 (제약 준수)

`common/error/ErrorCode.java` 에 상수를 추가할 수 없고, GET 요청이라 `@RequestBody` 검증(`MethodArgumentNotValidException`) 경로도 탈 수 없다.
**별도 예외 클래스도, 별도 advice 클래스도 만들지 않는다.** 대신:

1. **`RankingService` 가 표준 `IllegalArgumentException` 을 던진다.**
   메시지는 **`"{필드명}: {상세}"`** 형식이 계약이다 — 예: `"type: 허용되지 않는 값입니다: STREAKS"`.
   컨트롤러 핸들러가 첫 `": "` 를 기준으로 잘라 `fieldErrors[0].field` 와 `.message` 로 나눈다.
   구분자가 없으면 `field` 는 `"request"` 로 떨어진다. **이 형식을 깨면 `field` 값이 망가진다.**
2. **`RankingController` 안에 `@ExceptionHandler` 메서드를 직접 둔다.**
   컨트롤러에 선언한 `@ExceptionHandler` 는 **그 컨트롤러에서 발생한 예외만** 처리하는 표준 Spring 기능이라,
   별도 advice 클래스가 필요 없고 남의 `GlobalExceptionHandler` 에도 전혀 영향을 주지 않는다.
   처리 대상 3종:

   | 예외 | 발생 상황 | 응답 |
   |------|----------|------|
   | `IllegalArgumentException` | `type`/`period` 가 허용값이 아님 | 400 |
   | `MissingServletRequestParameterException` | `type` 누락 | 400 |
   | `MethodArgumentTypeMismatchException` | `categoryId` 가 숫자가 아님 | 400 |

3. 응답 바디 타입은 **`hackathon.app.common.error.GlobalExceptionHandler.ErrorResponse` / `.FieldError` 를 import 해서 그대로 재사용**한다.
   public 중첩 record 라 참조만 하면 되고, 읽기 전용 사용이므로 "남의 파일 수정 금지" 에 걸리지 않는다.
   - `code` = `"INVALID_REQUEST"`, `message` = `"요청값을 확인해주세요."` (팀 검증 경로와 동일 문구)
   - `fieldErrors` = `List.of(new FieldError(파라미터명, 상세 메시지))`
   - `requestId` = `UUID.randomUUID().toString()` (팀 핸들러와 동일)
   - 반환 타입은 `ResponseEntity<ErrorResponse>` + `@ExceptionHandler`, 상태는 `HttpStatus.BAD_REQUEST`

> `IllegalArgumentException` 을 400으로 매핑하는 범위가 **`RankingController` 하나로 한정**된다는 점이 이 설계의 핵심이다.
> 같은 매핑을 `@RestControllerAdvice` 로 만들면 전역에 적용되어 남의 컨트롤러 에러 응답까지 바뀐다 — 하지 말 것.

---

## 6. 파일 목록 (구현 완료)

전부 신규 파일이며 **모두 `ranking/` 아래**다. 기존 파일은 한 줄도 수정하지 않았다.

```
src/main/java/hackathon/app/ranking/
├── entity/
│   └── RankingSnapshot.java                 @Entity @Getter, 테이블 ranking_snapshots
├── enums/
│   ├── RankingType.java                     STREAK / COMPLETED_PUZZLES
│   ├── PeriodType.java                      WEEKLY / MONTHLY / YEARLY
│   ├── RankingScope.java                    OVERALL / CATEGORY
│   └── RankingTier.java                     DIAMOND~BRONZE + static of(rankNo, participants)
├── repository/
│   └── RankingSnapshotRepository.java       extends JpaRepository<RankingSnapshot, Long>, @Query 4개
├── service/
│   └── RankingService.java                  @Service @Transactional(readOnly = true)
├── controller/
│   └── RankingController.java               @RestController + @ExceptionHandler 2개
└── dto/response/
    └── GetRankingResponse.java              record + 중첩 RankingItem / MyRanking
```

**총 9개 파일. `application.yaml` 변경 없음.**
의존성은 **Lombok(`@Getter`)** 이 `build.gradle` 에 추가됐다 (`compileOnly` + `annotationProcessor`).
DB 스키마는 문서 상단 ⛔ 의 `period_type` ENUM 변경이 **선행되어야 한다.**

> **포트/어댑터를 쓰지 않는다.** 도메인 인터페이스 + `Jpa*Repository` + `*RepositoryAdapter` 3종 세트를 폐기하고
> **`JpaRepository` 를 상속한 인터페이스 하나**로 끝냈다 (사용자 결정: "복잡한 구조 ㄴ").
> 서비스 인터페이스, 별도 advice 클래스, `dto/request/` 패키지도 만들지 않는다 (GET 이라 요청 DTO가 필요 없다).

### 6.1 `RankingSnapshotRepository` 시그니처

`JpaRepository<RankingSnapshot, Long>` 를 상속하며 아래 4개를 `@Query` 로 직접 정의한다.

```java
LocalDate findLatestRankingDate(RankingType type, PeriodType period, RankingScope scope, Long categoryId);
List<RankingSnapshot> findTopRankings(LocalDate date, RankingType type, PeriodType period, RankingScope scope, Long categoryId, Pageable pageable);
Optional<RankingSnapshot> findByUser(LocalDate date, RankingType type, PeriodType period, RankingScope scope, Long categoryId, Long userId);
long countParticipants(LocalDate date, RankingType type, PeriodType period, RankingScope scope, Long categoryId);
```

모든 파라미터에 `@Param` 을 붙인다 (`pageable` 제외).
`findLatestRankingDate` 는 `Optional` 이 아니라 **`LocalDate` 를 그대로 반환하고, 행이 없으면 `null`** 이다 — 서비스가 `null` 체크로 분기한다.

**⚠️ 파생 쿼리 메서드명으로 만들지 말고 `@Query` JPQL 로 쓸 것.** `categoryId` 가 nullable 이라 `findBy...CategoryId(null)` 은 `= null` 로 번역되어 **아무 행도 매칭되지 않는다.** 공통 where 절:

```
where s.rankingType = :type
  and s.periodType  = :period
  and s.scope       = :scope
  and (:categoryId is null or s.categoryId = :categoryId)
```

- `findLatestRankingDate` → `select max(s.rankingDate) from RankingSnapshot s where <공통>`. 행이 없으면 `null` 이 온다.
- `findTopRankings` → 위 조건 + `and s.rankingDate = :date order by s.rankNo asc`, **`Pageable` 파라미터**(`PageRequest.of(0, 50)`)로 개수를 제한하고 반환 타입은 `List<RankingSnapshot>` (`Page` 를 쓰면 불필요한 count 쿼리가 하나 더 나간다).
- `findByUser` → 위 조건 + `and s.rankingDate = :date and s.userId = :userId`
- `countParticipants` → `select count(s) ...`

### 6.2 닉네임 조회 — `JpaUserRepository` 직접 재사용

`RankingSnapshot` 은 `userId` 만 들고 있으므로 `items[].nickname` 을 채우려면 `users` 를 따로 읽어야 한다.
`user/domain/UserRepository` 에는 `findById` 밖에 없고 그 파일은 남의 것이라 메서드를 추가할 수 없다.

**`RankingService` 가 `hackathon.app.user.infrastructure.JpaUserRepository` 를 생성자 주입으로 받아 `findAllById(userIds)` 를 한 번 호출한다.**

```java
List<Long> userIds = snapshots.stream().map(RankingSnapshot::getUserId).toList();
if (userIds.isEmpty()) return Map.of();          // 빈 목록이면 쿼리하지 않는다
for (User user : userRepository.findAllById(userIds)) { ... }   // 쿼리 1회
```

- `JpaRepository` 가 기본 제공하는 메서드이므로 남의 파일을 **읽어 쓰기만** 하는 것이다 (수정 아님 → 제약 위반 아님).
- **반복문 안에서 `findById` 를 부르는 N+1 구현 금지.** 반드시 `findAllById` 한 번이다.
- 조회 결과를 `Map<Long, String>`(userId → nickname)으로 만들어 `GetRankingResponse.of(...)` 에 넘긴다.
- 맵에 없는 userId 는 닉네임을 `"알 수 없음"` 으로 채운다
  (`GetRankingResponse.UNKNOWN_NICKNAME` 상수 — 대체값 처리는 서비스가 아니라 **DTO 조립부**에 있다).
- **도메인 간 의존이 생기는 지점이다**: `ranking.service` → `user.infrastructure`.
  포트를 따로 만드는 것보다 파일을 줄이는 쪽이 해커톤에서 낫다고 판단해 의도적으로 허용했다.

### 6.3 `RankingService` 처리 순서

`getRankings(String sessionId, String type, Long categoryId, String period)` → `GetRankingResponse`

```
1. type / period 파싱 → 실패 시 IllegalArgumentException("{필드}: 허용되지 않는 값입니다: {입력값}")
2. scope 결정 (categoryId == null ? OVERALL : CATEGORY)
3. findLatestRankingDate(...) → null 이면 GetRankingResponse.empty() 즉시 반환
4. findTopRankings(..., PageRequest.of(0, 50))
5. findMySnapshot(...) → 내부에서 authService.requireUser 를 try/catch(ApiException) 후 findByUser
6. mySnapshot != null 이면 countParticipants(...), 아니면 participants = 0
7. GetRankingResponse.of(rankingDate, snapshots, findNicknames(snapshots), mySnapshot, participants)
```

생성자 주입 대상 3개: `RankingSnapshotRepository`, `AuthService`, `JpaUserRepository`.
상수: `RANKING_SIZE = 50`, `FIRST_PAGE = 0`, `DEFAULT_PERIOD = PeriodType.WEEKLY`.

- **서비스가 `GetRankingResponse` 를 직접 반환한다.** 중간 결과 record 를 따로 두지 않는다 —
  컨트롤러는 `ApiResponse.of(rankingService.getRankings(...))` 한 줄뿐이다.
- 메서드 30줄을 넘기지 말 것. 인증·닉네임·파싱은 private 메서드(`findMySnapshot`, `currentUserOrNull`, `findNicknames`, `parseType`, `parsePeriod`)로 쪼개져 있다.
- 쿼리 수: 데이터 있을 때 비로그인 3회(date + top + nicknames), 로그인 5회(+ findByUser + count). 반복문 안 쿼리(N+1) 금지.

### 6.4 `RankingController` — 엔드포인트 + `@ExceptionHandler`

```java
@GetMapping
ApiResponse<GetRankingResponse> getRankings(
    @CookieValue(name = "SESSION", required = false) String sessionId,
    @RequestParam String type,
    @RequestParam(required = false) Long categoryId,
    @RequestParam(required = false) String period)
```

응답 DTO는 `ranking/dto/response/GetRankingResponse` 한 파일에 있다 (컨트롤러 중첩 record 아님).
컨트롤러 본문은 `ApiResponse.of(rankingService.getRankings(...))` 한 줄이다.

같은 클래스 안에 예외 핸들러 메서드를 둔다 (5.3 참조). 별도 advice 클래스를 만들지 않는다:

```java
@ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<ErrorResponse> handleInvalidParameter(IllegalArgumentException exception) { ... }

@ExceptionHandler({ MissingServletRequestParameterException.class,
                    MethodArgumentTypeMismatchException.class })
ResponseEntity<ErrorResponse> handleBadParameter(Exception exception) { ... }
```

- `ErrorResponse` / `FieldError` 는 `hackathon.app.common.error.GlobalExceptionHandler` 의 중첩 record 를 import 해 재사용한다.
- 바디 조립은 `private ResponseEntity<ErrorResponse> badRequest(String field, String message)` 하나로 모았다.
- 두 번째 핸들러는 `switch` 패턴 매칭으로 예외 종류별 `field`/`message` 를 뽑는다 (Java 25).
- 리터럴은 전부 `private static final` 상수 (`INVALID_REQUEST_CODE`, `INVALID_REQUEST_MESSAGE`, `REQUIRED_PARAMETER_MESSAGE`, `NOT_A_NUMBER_MESSAGE`, `FIELD_SEPARATOR`, `FALLBACK_FIELD`).

---

## 7. 확정 사항 / 선택 사항

### 확정 (⚠️ 없음 — 사용자 결정 완료, 2026-08-19)

1. **`tier` 산정 기준** (4.4) — 백분위 5/15/30/60% + **1등은 무조건 `DIAMOND`** 로 확정. `RankingTier.of(...)` 한 곳에만 있으므로 나중에 기준이 바뀌어도 파일 하나만 고치면 된다.
2. **`user_preferences.ranking_participation_enabled` 반영 안 함** — 스냅샷에 있는 행은 전부 노출한다.
   근거: 랭킹 미참여자를 거르는 것은 **스냅샷을 생성하는 11번 배치의 책임**이다. 조회 시점에 거르면 `rank_no` 에 구멍이 생겨 순위가 1,2,4,5… 로 튄다.
3. **`user_preferences.gallery_nickname_visible` 반영 안 함** — 닉네임 마스킹 없이 `users.nickname` 을 그대로 내보낸다 (근거는 2번과 동일).
4. **`period` 기본값 = `ALL`** — DDL 기본값과 일치시킨다.
5. **`type` / `period` 는 대문자 정확 일치만 허용** — `streak` 은 400이다. 프론트는 대문자로 보내야 한다.
6. **`size` 범위(1~100) 초과 시 400** — 조용히 클램핑하지 않는다. 프론트 버그를 숨기지 않기 위해서다.

### 범위 밖 (이번에 만들지 않음)

- `GET /users/{userId}/public-puzzles` — 7번 퍼즐 API 담당자 소관 (사용자 결정)
- `ranking_snapshots` 적재 배치 (`batch_jobs.job_type='RANKING'`) — 11번 태스크
- 10번 통계 API
- 커서/오프셋 페이징, 정렬 옵션, 특정 날짜 지정 조회 — Notion 명세에 `size` 만 있으므로 넣지 않는다
- 랭킹 응답 캐싱

---

## 변경 로그

- 2026-08-19: 최초 작성. `GET /api/v1/rankings` 단일 엔드포인트 명세.
- 2026-08-19: **파일 13개 → 10개로 축소.** 사용자가 팀 코드를 확인한 결과 팀은 포트/어댑터를 Repository 에만 쓰고 `application`/`presentation` 에는 파일을 하나씩만 둔다. 그 형태에 맞춰 아래 3개를 삭제하고 대체 방법을 명시했다. (수정 섹션: 6, 6.2, 6.3, 6.4)
  - `RankingUserRepository` + `RankingUserRepositoryAdapter` 삭제 → `RankingService` 가 `JpaUserRepository.findAllById(...)` 를 직접 호출 (6.2)
  - `InvalidRankingRequestException` + `RankingExceptionHandler` 삭제 → `IllegalArgumentException` + `RankingController` 안 `@ExceptionHandler` (5.3, 6.4)
- 2026-08-19: ⚠️ 확인 필요 6건 전부 **확정**으로 전환 (사용자 결정). tier 기준 그대로 확정, `ranking_participation_enabled`/`gallery_nickname_visible` 미반영(11번 배치 책임), `period` 기본값 `ALL`, 대문자만 허용, `size` 범위 초과 400. (수정 섹션: 4.4, 7)
- 유지된 필수 주의사항 2건 (변경 없음): `categoryId` nullable 때문에 파생 쿼리 금지 → `@Query` JPQL 필수 (6.1), `type`/`period` 를 `String` 으로 받아 서비스에서 파싱 (3.1)

**구현 영향**: 아직 `ranking/` 아래에 작성된 코드가 없으므로 기존 파일에 대한 소급 수정은 없다. backend-dev 는 이 문서의 6장 파일 목록(10개)만 만들면 된다.
