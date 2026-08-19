# 5. 스케줄 API · 6. 작업 API — 로컬 테스트 가이드

## 1. 사전 준비

```bash
# (1) MySQL 기동 확인 (docker: ai-hackathon-mysql, 127.0.0.1:3307)
docker ps | grep ai-hackathon-mysql

# (2) 테스트 데이터 넣기 (재실행 가능 — 수정/삭제 테스트 후 다시 돌리면 초기화됨)
mysql -h 127.0.0.1 -P 3307 -uhackathon -phackathon ai_hackathon < docs/test-data/seed_schedule.sql

# (3) 서버 실행
./gradlew bootRun
```

## 2. Postman 에서 돌리기

컬렉션 파일 2개 (둘 다 같은 seed 사용, 각각 독립 실행 가능):
- `docs/postman/schedule-api.postman_collection.json` — 5번 스케줄 API (33 요청)
- `docs/postman/schedule-item-api.postman_collection.json` — 6번 작업 API (32 요청)

1. Postman → **Import** → 위 파일
2. 컬렉션 변수 확인 (컬렉션 클릭 → Variables 탭)
   | 변수 | 기본값 | 설명 |
   |---|---|---|
   | `baseUrl` | `http://localhost:8080/api/v1` | 서버 주소 |
   | `userId` | `1` | `X-User-Id` 헤더 값 (임시 인증) |
   | `scheduleId` | `101` | 상세 조회용 |
   | `nextCursor` | (자동 세팅) | 목록 커서 테스트에서 자동 저장 |
3. 컬렉션 우클릭 → **Run collection** → 폴더 순서대로 실행 (1 목록 → 2 상세 → 3 수정 → 4 캘린더/오늘 → 5 삭제).
   - 각 요청의 **Tests 탭**에 상태 코드·응답 구조 검증 스크립트가 들어있어서 Runner 결과에서 ✅/❌ 로 바로 보임.
   - 3번(수정)·5번(삭제)은 데이터를 바꾸므로, 다시 돌리기 전에 seed SQL 을 재실행.
4. 개별 요청은 그냥 Send 해도 됨. 인증 헤더 `X-User-Id: 1` 이 들어가 있음 (`2` 로 바꾸면 tester2 시점).

CLI 로 돌리려면: `npx newman run docs/postman/schedule-api.postman_collection.json` / `npx newman run docs/postman/schedule-item-api.postman_collection.json` (각 실행 전 seed 재실행)

## 3. 인증(임시) 방식

설계서는 "서버 세션 + HttpOnly 쿠키"지만 1번 회원·인증 도메인이 아직 없어서, 지금은 **`X-User-Id` 헤더**로 사용자를 식별한다.

- `global/auth/LoginUserProvider` (인터페이스) ← `HeaderLoginUserProvider` (임시 구현)
- 세션 인증이 구현되면 **`LoginUserProvider` 구현체만 교체**하면 컨트롤러/서비스는 그대로.
- 헤더가 없거나 숫자가 아니면 `401 UNAUTHORIZED`.

## 4. seed 데이터 요약

| 사용자 | 스케줄 | 상태 | 기간 | 비고 |
|---|---|---|---|---|
| 1 | 101 `8월 알고리즘 공부` | ACTIVE | 08-17 ~ 08-30 | 작업 9건(1 삭제, 1 취소 → puzzleCount 7, 완료 2), 오늘(08-19) 2건, 08-19 는 position 이 priority 와 반대(1005→1004) |
| — | 카테고리 | | | 1 = `공부`(사용 가능), 2 = 사용 중지(is_active=0 → 400) |
| — | user_preferences | | | user 1·2 모두 기본값 → max_daily_tasks = 5. 08-19 는 user1 작업 3건이라 2개 더 추가하면 한도 |
| 1 | 102 `스프링 강의 완강` | DRAFT | 08-20 ~ 09-05 | 09-03 에 작업 → 기간 축소 시 409 테스트 |
| 1 | 103 `7월 영어 단어` | COMPLETED | 07-01 ~ 07-10 | 삭제 테스트 대상 |
| 1 | 104 `운동 루틴` | ACTIVE | 08-01 ~ 08-31 | 오늘(08-19) 1건 → 캘린더에 101 과 섞임 |
| 1 | 105 `삭제된 스케줄` | (soft deleted) | | 목록/캘린더에 나오면 안 됨 |
| 2 | 201 `tester2의 스케줄` | ACTIVE | | 타인 소유 → 403 테스트 |

## 5. 엔드포인트 & 응답 요약

| Method | URL | 성공 | 주요 오류 |
|---|---|---|---|
| GET | `/schedules?status&size&cursor` | 200 `{items[], nextCursor, hasNext}` | 400 INVALID_REQUEST / INVALID_CURSOR |
| GET | `/schedules/{id}` | 200 요약 + `days[{date,totalCount,completedCount,items[]}]` + 퍼즐 수 | 404 SCHEDULE_NOT_FOUND / 403 FORBIDDEN(타인) |
| PATCH | `/schedules/{id}` | 200 수정된 스케줄 (`currentVersion`+1, change log 기록) | 400 / 422 INVALID_SCHEDULE_PERIOD / 409 ITEMS_OUTSIDE_SCHEDULE_PERIOD / 404 / 403 |
| DELETE | `/schedules/{id}` | 204 (스케줄+하위 작업 소프트 삭제) | 404 / 403 |
| GET | `/calendar?year&month` | 200 `{year,month,totalCount,completedCount,days[]}` | 400 |
| GET | `/schedule-items/today` | 200 `{date,totalCount,completedCount,items[]}` | — |

| POST | `/schedules/{id}/items` | 201 생성된 작업 (`position` 미지정 시 그 날짜 마지막) | 422 DATE_OUTSIDE_SCHEDULE_PERIOD / 422 MAX_DAILY_TASKS_EXCEEDED / 400(카테고리·검증) / 404 / 403 |
| PATCH | `/schedule-items/{id}` | 200 수정된 작업 (날짜 변경 시 기간·한도 재검사, 상태는 불가) | 422 / 400 / 404 SCHEDULE_ITEM_NOT_FOUND / 403 |
| PATCH | `/schedule-items/{id}/status` | 200 `{itemId,status,completedAt,puzzlePieceAwarded,puzzlePieceId}` | 400 / 404 / 403 |
| DELETE | `/schedule-items/{id}` | 204 (소프트 삭제) | 404 / 403 |

모든 엔드포인트: 인증 없으면 401. 모든 응답에 `X-Request-Id` 헤더 + `meta.requestId`(성공) / `requestId`(실패).

## 6. 확정된 규칙 (설계서에 명시 안 됐던 부분 — 2026-08-19 합의)

| 항목 | 확정 | 비고 |
|---|---|---|
| 스케줄 응답 필드 | **설계서 8개만** (`id`,`title`,`status`,`startDate`,`endDate`,`currentVersion`,`puzzleCount`,`completedPuzzleCount`) | `source`,`description`,`createdAt`,`updatedAt` 는 응답에서 제외 |
| 타인 소유 스케줄 접근 | **403 `FORBIDDEN`** | 없거나 삭제된 스케줄은 404 `SCHEDULE_NOT_FOUND` |
| "유효한 작업" 정의 | **`deleted_at IS NULL` AND `status != CANCELLED`** (ERD 4.3) | 퍼즐 수(전체/완료), 날짜별 `totalCount`, 기간 축소 충돌 검사 모두 이 기준. CANCELLED 작업은 목록에는 나오지만 수에는 안 들어감 |
| 추가 오류 코드 | `INVALID_SCHEDULE_PERIOD`(422, startDate > endDate), `INVALID_CURSOR`(400) | 설계서 오류표에 추가 예정 |
| 날짜별 작업 응답 | `days: [{date, totalCount, completedCount, items[]}]` — 작업 있는 날짜만, 날짜 오름차순 | 상세·캘린더 공통 |
| 같은 날짜 안 정렬 | **`position` ASC → `priority` ASC → `id` ASC** | `schedule_items.position` 컬럼 추가(DB ALTER + ERD SQL 반영). 값은 작업 추가/수정의 `position?` 로만 지정 (순서 일괄 변경 API 는 제거됨 — 2026-08-19) |
| 목록 정렬/커서 | `id DESC`, 커서 = 마지막 id 를 Base64 로 감싼 불투명 문자열, `size` 기본 20·최대 100 | |
| PATCH 빈 본문 `{}` | 400 `INVALID_REQUEST` | 변경 없이 버전만 오르는 것 방지 |
| 스케줄 삭제 | 스케줄 + 하위 작업 **모두 소프트 삭제**, change log `DELETE` 기록 | |
| 오늘 기준 시간대 | `Asia/Seoul` 고정 (`ClockConfig`) | `users.timezone` 은 회원 도메인 확정 후 |
| 일시 포맷 | `2026-08-19T17:33:31+09:00` (초 단위, ISO 8601 + 오프셋) | |
| User/Category 연관 | `userId`, `categoryId` 를 Long 컬럼으로만 보유 (JPA 연관 X) | DB FK 는 ERD SQL 에 있음 |
| 인증 | 임시 `X-User-Id` 헤더 (`LoginUserProvider` 구현체만 교체하면 세션으로 전환) | |

### 6번 작업 API 추가 규칙 (2026-08-19 합의)

| 항목 | 확정 | 비고 |
|---|---|---|
| 하루 최대 작업 수 | **`user_preferences.max_daily_tasks`** 를 PK 단건 조회(없으면 5) — `DailyTaskLimitProvider` | 엔티티 없이 `JdbcClient` 로 읽어 2번 도메인과 충돌 없음 |
| 한도 계산 범위 | **사용자의 모든 스케줄 합산**, 그 날짜의 유효한 작업(삭제·CANCELLED 제외) | 작업 추가·날짜 변경 시 검사. 날짜를 옮길 땐 자기 자신 제외 |
| 작업 날짜 | 스케줄 기간(`startDate`~`endDate`) 안 | 422 DATE_OUTSIDE_SCHEDULE_PERIOD |
| `position` 기본값 | 미지정 시 같은 스케줄·같은 날짜의 `max(position)+1` (맨 뒤) | |
| 상태 변경 | `completedAt` 은 COMPLETED 첫 진입 시 기록, 다른 상태로 가면 null. 다시 COMPLETED 되면 새로 기록 | 상태 변경은 change log·버전에 남기지 않음 |
| 퍼즐 조각 지급 | **`PuzzlePieceAwarder` 포트**만 두고 임시 구현 `NoOpPuzzlePieceAwarder`(항상 awarded=false) | 7번 담당자가 `puzzles`/`puzzle_pieces` 저장 구현체를 만들어 교체(`@Primary` 또는 NoOp 제거). 중복 지급 방지(`schedule_item_id` UNIQUE)는 구현체 책임 |
| 카테고리 | `categoryId` 지정 시 `categories.is_active = TRUE` 행이 있어야 함 — `CategoryChecker` | 없으면 400 INVALID_REQUEST |
| 변경 이력·버전 | 추가(CREATE)/수정(UPDATE)/삭제(DELETE) 마다 `schedule_change_logs` 기록 + `schedules.current_version` +1 | 설계서는 수정만 명시했지만 핵심 정책 6(이력 보존)에 따라 전부 기록 |
| 타인 작업 | 403 FORBIDDEN; 없음/삭제됨/스케줄 삭제됨 → 404 SCHEDULE_ITEM_NOT_FOUND | |
