# 02. 구현 노트 — 랭킹 조회 API

작성: backend-dev / 2026-08-19
기준: `_workspace/01_spec.md` 6장 (파일 10개)

---

## 생성 파일 (10개, 전부 신규 · 기존 파일 수정 0건)

```
src/main/java/hackathon/app/ranking/
├── domain/
│   ├── RankingType.java                      enum STREAK / COMPLETED_PUZZLES / PUZZLE_PIECES
│   ├── PeriodType.java                       enum DAILY / WEEKLY / MONTHLY / ALL
│   ├── RankingScope.java                     enum OVERALL / CATEGORY
│   ├── RankingTier.java                      enum + static of(rankNo, participants)
│   ├── RankingSnapshot.java                  @Entity, 게터만 (세터·정적 팩토리 없음)
│   └── RankingSnapshotRepository.java        도메인 포트
├── infrastructure/
│   ├── JpaRankingSnapshotRepository.java     @Query JPQL 4종
│   └── RankingSnapshotRepositoryAdapter.java @Repository
├── application/
│   └── RankingService.java                   @Service @Transactional(readOnly = true)
└── presentation/
    └── RankingController.java                @RestController + 중첩 record 3개 + @ExceptionHandler 2개
```

`git status` 로 확인: 소스 변경은 `src/main/java/hackathon/app/ranking/` 하나뿐.
`auth/`, `user/`, `preference/`, `common/`, `application.yaml`, `build.gradle` 전부 무수정. 의존성 추가 없음.

## 명세와 다른 점

**없음.** 엔드포인트·파라미터·응답 필드명·에러 바디 모두 01_spec.md 그대로다.

명세에 세부가 없어 구현에서 정한 것 2건:

1. **`IllegalArgumentException` → fieldErrors 변환 방식**
   서비스가 `"type: 허용되지 않는 값입니다: STREAKS"` 형태로 던지고, 컨트롤러가 **첫 `": "`** 를 기준으로
   앞을 `field`, 뒤를 `message` 로 자른다 → `{"field":"type","message":"허용되지 않는 값입니다: STREAKS"}`.
   명세 5.2 표의 문구와 글자 단위로 일치한다.
   구분자가 없는 예외(우리가 던지지 않은 IAE)는 `field="request"` 로 폴백한다 — 명세에 없는 방어 코드다.
2. **`period` 는 `null` 과 공백 문자열(`?period=`) 둘 다 `ALL`** 로 처리했다.
   명세는 "null/공백이면 ALL"(3.2-2)이라 그대로 따른 것이고, `type` 은 공백을 허용하지 않아 `?type=` 은 400이다.

## 미구현 / TODO

**없음** (이번 범위 기준). 범위 밖 항목은 01_spec.md 7장 참조 —
스냅샷 적재 배치(11번), `GET /users/{userId}/public-puzzles`(7번), 통계 API(10번).

---

## 실제로 실행해서 확인한 것

### 1. 빌드 — 통과

```
./gradlew compileJava --no-daemon -q     → exit 0 (계층마다 4회 실행, 전부 통과)
./gradlew clean build --no-daemon        → BUILD SUCCESSFUL in 9s (9 tasks executed)
```

`build` 에는 기존 `AppApplicationTests.contextLoads` (`@SpringBootTest` + `test` 프로파일 H2)가 포함된다.
**전체 Spring 컨텍스트가 실제로 떴다**는 뜻이므로 아래가 함께 검증됐다:
- `RankingController` / `RankingService` / `RankingSnapshotRepositoryAdapter` / `JpaRankingSnapshotRepository` 빈 주입 성공
- `@Query` JPQL 4개가 부트스트랩 시점에 전부 파싱·검증됨 (문법 오류·필드명 오타 있으면 여기서 기동 실패)

### 2. 런타임 프로브 — 통과 (임시 테스트로 확인 후 파일 삭제)

컴파일로는 절대 잡히지 않는 항목을 H2(MODE=MySQL)에 실제 행을 넣고 확인했다.
**확인용 임시 테스트 파일은 실행 후 삭제했다** (산출물은 `ranking/` 10개뿐).

| 확인 항목 | 결과 |
|---|---|
| `categoryId = null` 로 `findLatestRankingDate` (**최대 함정**) | `Optional[2026-08-19]` — `(:categoryId is null or ...)` 정상. 파생 쿼리였다면 0건 |
| `categoryId = 3` 로 `findLatestRankingDate` | `Optional[2026-08-19]` |
| 조건에 맞는 행 0건 | `Optional.empty` → `rankingDate: null` + 빈 배열 + 200 경로 확인 |
| `findTopRankings(size=1)` | `count=1, rank=1, score=87.00` — `Pageable` 개수 제한 + `rank_no` 오름차순 + **소수점 2자리 유지** |
| `countParticipants` OVERALL / CATEGORY | `2` / `1` — scope 가 서로 섞이지 않음 |
| `findByUser` 존재 / 부재 | `rank=2` / `false` (→ `myRanking: null`) |
| `RankingTier.of` 경계값 | `1/3→DIAMOND`, `5%→DIAMOND`, `15%→PLATINUM`, `34%→SILVER`, `61%→BRONZE` (명세 4.4 일치) |

### 3. 엔티티 ↔ DDL 대조 — 컬럼 12개 전부 일치

Hibernate 가 내 엔티티로 생성한 스키마를 `information_schema` 에서 덤프해
`00_context.md` 의 `ranking_snapshots` DDL 과 대조했다. **컬럼 추가·누락·이름 불일치 없음.**

| 컬럼 | DDL | 엔티티 생성 결과 |
|---|---|---|
| id | BIGINT NOT NULL | BIGINT nullable=NO |
| ranking_date | DATE NOT NULL | DATE nullable=NO |
| ranking_type / period_type / scope | ENUM NOT NULL | **ENUM** nullable=NO (`@Enumerated(STRING)`) |
| category_id | BIGINT **NULL** | BIGINT **nullable=YES** |
| user_id | BIGINT NOT NULL | BIGINT nullable=NO |
| rank_no | INT NOT NULL | INTEGER nullable=NO |
| score | DECIMAL(14,2) NOT NULL | NUMERIC p=14 s=2 nullable=NO |
| puzzle_count / active_days | INT NOT NULL | INTEGER nullable=NO |
| achievement_rate | DECIMAL(5,2) NOT NULL | NUMERIC p=5 s=2 nullable=NO |
| created_at | DATETIME NOT NULL | TIMESTAMP nullable=NO |

enum 상수 선언 순서도 DDL의 ENUM 값 순서와 동일하게 맞췄다.

### 4. 정적 검사 — 통과

```
bash .claude/skills/backend-qa-check/scripts/verify.sh --no-build
→ 정적 검사 통과 (package 경로 일치 / 계층 규칙 / datasource / @Valid / 트랜잭션 /
   엔티티 직접 반환 없음 / 메서드 30줄 이하 — 8개 항목 전부 OK)
```

---

## 확인하지 못한 것 (⚠️ QA 인계)

### `bootRun` 기동 + curl 왕복 — **불가 (환경 문제, 코드 무관)**

```
timeout 90 ./gradlew bootRun --no-daemon → exit 1
Access denied for user 'ai_scheduler_app'@'localhost' (using password: YES)
```

- MySQL 포트 3306 은 열려 있으나 레포에 `.env` 가 없어 `DB_PASSWORD` 가 비어 있다 (`.env.example` 은 플레이스홀더).
- **DB 접속 정보를 만들어내지 않았다.** 이 실패는 ranking 코드와 무관하며, 팀의 기존 코드도 이 머신에선 동일하게 기동 실패한다.
- 이 때문에 **`ddl-auto: validate` 가 실제 MySQL 테이블을 상대로 통과하는지는 검증되지 않았다.**
  위 3번(H2 대조)이 최선의 대체 근거다. `.env` 가 있는 환경에서 기동 1회만 확인하면 된다.
- 따라서 `curl` 왕복(200 응답 형태, 400 에러 바디, 비로그인 `myRanking: null`)도 미검증이다.

### 참고 — 명세 예시 (a) 의 tier 값

`{"rank": 34, ..., "tier": "GOLD"}` 는 참가자 수가 **114명 이상**일 때만 GOLD 다
(34/114 = 29.8% ≤ 30%). 100명이면 34% 라서 SILVER 가 된다.
예시에 참가자 수가 없어 모순은 아니지만, QA가 100명 기준으로 검증하면 오탐이 날 수 있다.
계산식 자체는 명세 4.4 그대로이며 `RankingTier.of` 한 곳에만 있다.

### 참고 — 세션 중 브랜치 이동

작업 도중 `dev` 브랜치에 다른 PR(#11 사용자 설정 API)이 머지되어 워킹트리가 갱신됐다.
내가 한 일이 아니며, **갱신된 트리 기준으로 `clean build` 를 다시 돌려 통과를 확인**했다.

---

## 팀 통신 (전달 필요)

- **qa-verifier**: ranking 도메인 완료 → 검증 요청. 위 "확인하지 못한 것" 2건이 QA 몫이다.
- **api-designer**: 명세 변경 없음. 응답 DTO 필드명도 명세 그대로라 **test-writer 에 통지할 변경 없음**.
