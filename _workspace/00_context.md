# 랭킹 API 작업 컨텍스트 (리더 작성, 2026-08-19)

## 내 태스크 범위
Notion `API_SPECIFICATION_NOTION (1)` 의 **9번 랭킹 API**.
(10번 통계, 11번 내부 배치도 내 태스크지만 이번 작업 범위 아님)

이번에 구현할 것: **`GET /api/v1/rankings` 하나만.**
`GET /users/{userId}/public-puzzles` 는 7번 퍼즐 API 담당자와 겹치므로 **이번 범위에서 제외** (사용자 결정).

## 절대 규칙
**다른 사람이 작성한 파일은 절대 수정하지 않는다. 읽기만 한다.**
- 건드리면 안 되는 것: `auth/`, `user/`, `preference/`, `common/` 전체
- `common/error/ErrorCode.java` 에 enum 상수를 추가하는 것도 **수정이므로 금지**
  → 기존 코드(`AUTHENTICATION_REQUIRED`, `USER_NOT_FOUND`)로 해결하거나, 검증 실패는 `MethodArgumentNotValidException` 경로를 탄다
- 새로 만드는 것은 `ranking/` 패키지 아래로만

## 패키지 구조 (사용자 결정: 팀 기존 헥사고날 따름)
```
hackathon/app/ranking/
├── domain/          RankingSnapshot, RankingType, PeriodType, RankingScope, RankingRepository(인터페이스)
├── infrastructure/  JpaRankingSnapshotRepository, RankingSnapshotRepositoryAdapter
├── application/     RankingService
└── presentation/    RankingController (+ 중첩 record DTO)
```
> 하네스 기본 규칙(`entity/repository/service/controller/dto`)과 다르다. 팀 레포 일관성을 위해 사용자가 헥사고날을 선택했다.
> 단, 코드 수준 원칙(메서드 30줄 이하, 스트림 3단계, 생성자 주입, 세터 금지, 예외 메시지에 값 포함)은 그대로 지킨다.

## 팀 규약 (실제 코드에서 확인함 — 반드시 따를 것)
| 항목 | 실제 컨벤션 |
|------|-----------|
| Base path | `@RequestMapping("/api/v1/...")` |
| 성공 응답 | `ApiResponse.of(data)` → `{"data": ...}`. **meta 없음** (Notion 명세엔 meta가 있으나 팀 구현은 없음) |
| 에러 응답 | `GlobalExceptionHandler` 가 `{code, message, fieldErrors, requestId}` 로 처리 |
| 예외 | `throw new ApiException(ErrorCode.XXX)` |
| DTO 위치 | **컨트롤러 안 중첩 record** (`public record XxxResponse(...) { static from(...) }`) — 별도 dto 패키지 안 씀 |
| 컨트롤러 메서드 | `public` 안 붙임 (package-private) |
| 인증 | `@CookieValue(name="SESSION", required=false) String sessionId` |
| 로그인 필수 | `authService.requireUser(sessionId)` → 실패 시 `ApiException(AUTHENTICATION_REQUIRED)` |
| Repository | 도메인 인터페이스 + `Jpa*Repository` + `*RepositoryAdapter(@Repository)` 3종 세트 |
| Entity | `protected` 기본 생성자, 정적 팩토리, 세터 없음, 게터만 |

## DB (매우 중요)
- **MySQL, `ddl-auto: validate`** → 엔티티가 DDL과 조금이라도 다르면 **기동 실패**한다. 컬럼명·타입·nullable 정확히 맞출 것
- 접속 정보는 `.env` / 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). **하드코딩 금지**

### ranking_snapshots 실제 DDL
```sql
CREATE TABLE ranking_snapshots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ranking_date DATE NOT NULL,
  ranking_type ENUM('STREAK','COMPLETED_PUZZLES','PUZZLE_PIECES') NOT NULL,
  period_type ENUM('DAILY','WEEKLY','MONTHLY','ALL') NOT NULL DEFAULT 'ALL',
  scope ENUM('OVERALL','CATEGORY') NOT NULL,
  category_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  rank_no INT NOT NULL,
  score DECIMAL(14,2) NOT NULL,
  puzzle_count INT NOT NULL DEFAULT 0,
  active_days INT NOT NULL DEFAULT 0,
  achievement_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_user (ranking_date, ranking_type, period_type, scope, category_id, user_id),
  KEY idx_ranking_lookup (ranking_date, ranking_type, period_type, scope, category_id, rank_no)
);
-- FK: user_id → users(id), category_id → categories(id)
```
`users` 테이블에 `nickname`(VARCHAR 50, NOT NULL, UNIQUE) 이 있고 `User` 엔티티가 이미 존재한다.

## 확정된 설계 결정 (리더)
1. **스냅샷은 읽기만 한다.** `ranking_snapshots` 를 채우는 배치는 이번 범위 밖 (11번 태스크 / `batch_jobs.job_type='RANKING'`).
   따라서 데이터가 없으면 빈 목록을 반환해야 하고, 500이 나면 안 된다.
2. **선택 인증 처리**: `authService.requireUser(sessionId)` 를 호출하고 `ApiException` 을 잡아 비로그인으로 간주한다.
   세션 유효성 판정 로직을 우리 쪽에 복제하지 않기 위함이다 (복제하면 나중에 어긋난다).
   비로그인이면 `myRanking` 은 `null`.

## ⚠️ 명세와 DB가 어긋나는 지점 (설계에서 반드시 다룰 것)
1. **`myRanking.tier` 에 대응하는 컬럼이 DB에 없다.** `ranking_snapshots` 에도 `users` 에도 tier가 없다.
   → 컬럼을 새로 추가하면 `ddl-auto: validate` 로 기동이 깨지므로 **코드에서 파생**해야 한다 (예: 순위 백분위 구간).
   기준을 명세에 명시하고 `⚠️ 확인 필요` 로 표시할 것.
2. **`rankingDate` 기준일을 어떻게 정하는가.** 요청 파라미터에 없다 → 저장된 스냅샷 중 최신 `ranking_date` 를 쓰는 것이 자연스럽다.
3. **`period` / `categoryId` 파라미터와 `scope` 컬럼의 관계**: `categoryId` 가 있으면 `scope='CATEGORY'`, 없으면 `'OVERALL'`.
4. **`user_preferences.ranking_participation_enabled`, `gallery_nickname_visible`** 이 존재한다.
   랭킹 노출/닉네임 공개에 반영해야 하는지 `⚠️ 확인 필요` 로 남길 것 (preference는 남의 코드라 읽기만 가능).
5. `size` 기본값·상한을 정할 것 (커서 페이징은 `/rankings` 명세에 없다 — `size` 만 있다).
