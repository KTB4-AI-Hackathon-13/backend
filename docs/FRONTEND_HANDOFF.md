# 프론트엔드 핸드오프 — 스케줄(5번) · 작업(6번) · 퍼즐(7번) API

> 백엔드 담당자(김민서)가 구현한 **5. 스케줄 API / 6. 작업 API / 7. 퍼즐 API(MVP)** 를 프론트에서 바로 연동할 수 있도록 정리한 문서입니다.
> 기준일 2026-08-19. 설계서(노션 `API_SPECIFICATION_NOTION`)와 다르게 정한 것은 §8 "확정 규칙"에 모아두었습니다.
> 다른 도메인(회원·인증, AI 대화/생성, 퍼즐, 이미지, 랭킹…)은 다른 팀원 담당이며 이 문서 범위 밖입니다.

## 1. 접속 정보

| 항목 | 값 |
|---|---|
| Base URL (로컬) | `http://localhost:8080/api/v1` |
| Content-Type | `application/json` (요청·응답) |
| CORS | `http://localhost:5173`, `http://localhost:3000` 허용 (다른 포트면 백엔드 `APP_CORS_ALLOWED_ORIGINS` 환경변수로 추가 요청). `credentials: 'include'` 가능 |
| **인증** | **세션 쿠키 `SESSION`** (HttpOnly). `POST /api/v1/auth/signup` → `POST /api/v1/auth/login` 하면 Set-Cookie 로 내려옴. 이후 모든 요청을 **`credentials: 'include'`** (fetch) / `withCredentials: true` (axios) 로 보내면 브라우저가 쿠키를 자동 첨부. 로그아웃 `POST /api/v1/auth/logout`. 쿠키 없음/만료 → `401 AUTHENTICATION_REQUIRED`. (회원 API 상세는 인증 담당 팀원 문서 참고) |
| 인증 (로컬 개발 편의) | 백엔드를 `APP_AUTH_DEV_HEADER_ENABLED=true` 로 띄우면 쿠키 없는 요청에 한해 헤더 `X-User-Id: 1` 로 seed 사용자 행세 가능. 로그인 화면 만들기 전 단계에서만 사용, 운영엔 없음 |
| 날짜 | `YYYY-MM-DD` (예 `2026-08-19`) |
| 일시 | ISO 8601 + 오프셋 (예 `2026-08-19T21:16:03+09:00`), 초 단위 |
| 요청 추적 | 모든 응답 헤더 `X-Request-Id` + 본문 `requestId` (버그 제보 시 같이 전달) |

## 2. 공통 응답 형식

**성공**
```json
{ "data": { ... }, "meta": { "requestId": "uuid" } }
```
**목록(커서 페이징)** — `data` 안에
```json
{ "items": [ ... ], "nextCursor": "MTA0", "hasNext": true }
```
- 다음 페이지: 이전 응답의 `nextCursor` 를 그대로 `?cursor=` 에 넣어 호출. `hasNext:false` 면 `nextCursor` 는 `null`.
- 커서는 불투명 문자열 — 파싱하거나 만들지 말 것.

**실패**
```json
{
  "code": "INVALID_REQUEST",
  "message": "요청값을 확인해주세요.",
  "fieldErrors": [
    { "field": "title", "message": "제목은 필수입니다." },
    { "field": "priority", "message": "priority 는 1~5 입니다." }
  ],
  "requestId": "104b4ecd-7106-4de3-acde-9bcebe1b50a6"
}
```
- `code` 로 분기, `message` 는 사용자에게 보여줘도 되는 한국어. `fieldErrors[]` (`{field, message}`) 는 본문/파라미터 검증 실패(400)일 때만 채워짐.
- `204 No Content` 는 본문 없음.

### 공통 상태 코드
| HTTP | 의미 | 프론트 처리 |
|---|---|---|
| 200 / 201 / 204 | 성공 | |
| 400 `INVALID_REQUEST` | 형식·검증 오류 (`fieldErrors` 참고) | 입력 폼 에러 표시 |
| 400 `INVALID_CURSOR` | 커서 깨짐 | 첫 페이지부터 다시 |
| 401 `AUTHENTICATION_REQUIRED` | 세션 없음/만료 | 로그인 화면 |
| 403 `FORBIDDEN` | 남의 스케줄/작업 | "권한 없음" |
| 404 `SCHEDULE_NOT_FOUND` / `SCHEDULE_ITEM_NOT_FOUND` / `PUZZLE_NOT_FOUND` | 없음 또는 삭제됨 | 목록으로 |
| 403 `PUZZLE_NOT_PUBLIC` | 타인의 비공개·미완성 퍼즐 | "비공개 퍼즐" |
| 409 `ITEMS_OUTSIDE_SCHEDULE_PERIOD` | 기간 축소 시 범위 밖 작업 존재 | 메시지 표시 |
| 422 `INVALID_SCHEDULE_PERIOD` | 시작일 > 종료일 | 폼 에러 |
| 422 `DATE_OUTSIDE_SCHEDULE_PERIOD` | 작업 날짜가 스케줄 기간 밖 | 폼 에러 |
| 422 `MAX_DAILY_TASKS_EXCEEDED` | 그 날짜 작업이 하루 한도(기본 5) | 메시지 표시 |
| 500 | 서버 오류 | `requestId` 와 함께 제보 |

## 3. 도메인 모델 (프론트 타입 정의용)

```ts
type ScheduleStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
type ItemStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED';

interface ScheduleSummary {          // 목록 항목 / 수정 응답
  id: number; title: string; status: ScheduleStatus;
  startDate: string; endDate: string;   // YYYY-MM-DD
  currentVersion: number;
  puzzleCount: number;                  // 퍼즐 조각 수 = 유효한 작업 수 (삭제·CANCELLED 제외)
  completedPuzzleCount: number;         // 그중 COMPLETED
}
interface ScheduleDetail extends ScheduleSummary {
  days: { date: string; totalCount: number; completedCount: number; items: ScheduleItem[] }[];
}
interface ScheduleItem {
  id: number; scheduleId: number; categoryId: number | null; parentItemId: number | null;
  title: string; description: string | null;
  scheduledDate: string;                // YYYY-MM-DD
  position: number;                     // 같은 날짜 안 표시 순서 (0부터)
  workload: number;                     // 상대적 업무량 (기본 1)
  priority: number;                     // 1(높음) ~ 5(낮음), 기본 3
  status: ItemStatus;
  completedAt: string | null;           // ISO 8601
}
interface DailyItem {                   // 캘린더 / 오늘 할 일 항목 (어느 스케줄인지 포함)
  id: number; scheduleId: number; scheduleTitle: string; categoryId: number | null;
  title: string; position: number; workload: number; priority: number;
  status: ItemStatus; completedAt: string | null;
}
```

규칙: **작업 1건 = 퍼즐 조각 1개.** `CANCELLED` 작업은 목록에는 나오지만 `puzzleCount`/`totalCount` 에는 안 들어감(완성 불가 방지). 같은 날짜 안 정렬은 항상 **`position` ↑ → `priority` ↑ → `id` ↑** (서버가 정렬해서 줌 — 프론트에서 다시 정렬하지 말 것).

## 4. 스케줄 API (5번)

> 스케줄 **생성 API 는 없음** — 스케줄은 AI 계획 생성(4번 도메인, 다른 담당)으로만 만들어짐. 개발 중에는 seed 데이터(§7) 사용.

### 4.1 목록 `GET /schedules?status=&size=&cursor=`
- `status?` ScheduleStatus, `size?` 1~100 (기본 20), `cursor?`
- 정렬: 최신(id 큰) 순
```json
{
  "data": {
    "hasNext": true,
    "items": [
      {
        "id": 104,
        "title": "운동 루틴",
        "status": "ACTIVE",
        "startDate": "2026-08-01",
        "endDate": "2026-08-31",
        "currentVersion": 1,
        "puzzleCount": 3,
        "completedPuzzleCount": 1
      }
    ],
    "nextCursor": "MTA0"
  },
  "meta": {
    "requestId": "8c9933e1-c149-4b1f-9938-e9eb2880cd2d"
  }
}
```

### 4.2 상세 `GET /schedules/{scheduleId}`
- 요약 8개 필드 + `days[]` (작업 있는 날짜만, 날짜 오름차순)
```json
{
  "data": {
    "id": 101,
    "title": "8월 알고리즘 공부",
    "status": "ACTIVE",
    "startDate": "2026-08-17",
    "endDate": "2026-08-30",
    "currentVersion": 1,
    "puzzleCount": 7,
    "completedPuzzleCount": 2,
    "days": [
      {
        "date": "2026-08-17",
        "totalCount": 1,
        "completedCount": 1,
        "items": [
          {
            "id": 1001,
            "scheduleId": 101,
            "categoryId": null,
            "parentItemId": null,
            "title": "BFS 문제 2개",
            "description": null,
            "scheduledDate": "2026-08-17",
            "position": 0,
            "workload": 2,
            "priority": 2,
            "status": "COMPLETED",
            "completedAt": "2026-08-17T21:00:00+09:00"
          }
        ]
      },
      {
        "date": "2026-08-18",
        "totalCount": 2,
        "completedCount": 1,
        "items": [
          {
            "id": 1002,
            "scheduleId": 101,
            "categoryId": null,
            "parentItemId": null,
            "title": "DFS 문제 2개",
            "description": null,
            "scheduledDate": "2026-08-18",
            "position": 0,
            "workload": 2,
            "priority": 2,
            "status": "COMPLETED",
            "completedAt": "2026-08-18T22:00:00+09:00"
          }
        ]
      }
    ]
  },
  "meta": {
    "requestId": "ac25bd9f-2071-4642-a73f-900b11ac15ca"
  }
}
```

### 4.3 수정 `PATCH /schedules/{scheduleId}`
- 본문: `title?`(1~200자), `description?`, `startDate?`, `endDate?` — **보낸 필드만 변경**(`null`/미포함 = 유지). 전부 비우면 400.
- 기간 축소 시 범위 밖(CANCELLED 아닌) 작업 있으면 **409**, 시작>종료면 **422**
```json
{
  "data": {
    "id": 102,
    "title": "스프링 강의 (수정)",
    "status": "DRAFT",
    "startDate": "2026-08-20",
    "endDate": "2026-09-10",
    "currentVersion": 2,
    "puzzleCount": 3,
    "completedPuzzleCount": 0
  },
  "meta": {
    "requestId": "f5089959-78b8-40ce-bcbf-0de4d64da134"
  }
}
```
409 예:
```json
{
  "code": "ITEMS_OUTSIDE_SCHEDULE_PERIOD",
  "fieldErrors": [],
  "message": "변경하려는 기간 밖에 작업이 2건 존재합니다.",
  "requestId": "5e601fb7-85d6-4266-8b0c-e7f8bcde3d1b"
}
```

### 4.4 삭제 `DELETE /schedules/{scheduleId}` → `204`
- 스케줄 + 그 안의 작업 전부 소프트 삭제. 이후 조회 404.

### 4.5 월별 캘린더 `GET /calendar?year=2026&month=8`
- `year`(2000~2100), `month`(1~12) 필수. 작업 있는 날짜만 `days[]`. 여러 스케줄 작업이 섞여 나오므로 `scheduleId`/`scheduleTitle` 로 구분.
```json
{
  "data": {
    "year": 2026,
    "month": 8,
    "totalCount": 12,
    "completedCount": 3,
    "days": [
      {
        "date": "2026-08-17",
        "totalCount": 2,
        "completedCount": 2,
        "items": [
          {
            "id": 1001,
            "scheduleId": 101,
            "scheduleTitle": "8월 알고리즘 공부",
            "categoryId": null,
            "title": "BFS 문제 2개",
            "position": 0,
            "workload": 2,
            "priority": 2,
            "status": "COMPLETED",
            "completedAt": "2026-08-17T21:00:00+09:00"
          },
          {
            "id": 1301,
            "scheduleId": 104,
            "scheduleTitle": "운동 루틴",
            "categoryId": null,
            "title": "하체 운동",
            "position": 0,
            "workload": 2,
            "priority": 2,
            "status": "COMPLETED",
            "completedAt": "2026-08-17T19:00:00+09:00"
          }
        ]
      }
    ]
  },
  "meta": {
    "requestId": "379c6d76-45a1-475e-8a5c-4e42667e7813"
  }
}
```

### 4.6 오늘 할 일 `GET /schedule-items/today`
- 서버 기준 오늘(Asia/Seoul). 파라미터 없음.
```json
{
  "data": {
    "date": "2026-08-19",
    "totalCount": 3,
    "completedCount": 0,
    "items": [
      {
        "id": 1302,
        "scheduleId": 104,
        "scheduleTitle": "운동 루틴",
        "categoryId": null,
        "title": "상체 운동",
        "position": 0,
        "workload": 2,
        "priority": 2,
        "status": "TODO",
        "completedAt": null
      },
      {
        "id": 1005,
        "scheduleId": 101,
        "scheduleTitle": "8월 알고리즘 공부",
        "categoryId": null,
        "title": "그리디 문제 1개",
        "position": 0,
        "workload": 1,
        "priority": 3,
        "status": "IN_PROGRESS",
        "completedAt": null
      }
    ]
  },
  "meta": {
    "requestId": "efb3b77a-7ddc-4c98-b4cb-a312dfe142de"
  }
}
```

## 5. 작업 API (6번)

### 5.1 추가 `POST /schedules/{scheduleId}/items` → `201`
- 본문: `title`(필수, ≤200), `scheduledDate`(필수, 스케줄 기간 안), `description?`, `categoryId?`(존재·활성 카테고리), `workload?`(≥1, 기본 1), `priority?`(1~5, 기본 3), `position?`(≥0, 미지정 시 그 날짜 맨 뒤)
- 오류: 422 `DATE_OUTSIDE_SCHEDULE_PERIOD`, 422 `MAX_DAILY_TASKS_EXCEEDED`(**사용자의 모든 스케줄 합산**으로 그 날짜 한도 초과), 400(검증/카테고리), 403, 404
```json
{
  "data": {
    "id": 2008,
    "scheduleId": 101,
    "categoryId": 1,
    "parentItemId": null,
    "title": "새 작업 A",
    "description": null,
    "scheduledDate": "2026-08-19",
    "position": 2,
    "workload": 2,
    "priority": 2,
    "status": "TODO",
    "completedAt": null
  },
  "meta": {
    "requestId": "580df54f-bb43-40f4-bc7d-77d0abb59bd3"
  }
}
```

### 5.2 수정 `PATCH /schedule-items/{itemId}`
- 본문: `title?`, `description?`, `scheduledDate?`, `categoryId?`, `workload?`, `priority?`, `position?` — 보낸 것만 변경. **`status` 는 여기서 못 바꿈**(5.3 사용). 날짜를 바꾸면 기간·한도 재검사.
```json
{
  "data": {
    "id": 2008,
    "scheduleId": 101,
    "categoryId": 1,
    "parentItemId": null,
    "title": "새 작업 A (수정)",
    "description": null,
    "scheduledDate": "2026-08-20",
    "position": 2,
    "workload": 2,
    "priority": 2,
    "status": "TODO",
    "completedAt": null
  },
  "meta": {
    "requestId": "cea82ec6-689f-4cc0-970f-383f0b0a9e54"
  }
}
```

### 5.3 상태 변경 `PATCH /schedule-items/{itemId}/status`
- 본문: `{ "status": "COMPLETED" }` (ItemStatus 중 하나)
- 응답은 설계서 "작업 완료 응답" 형식. `COMPLETED` 로 바꾸면 `completedAt` 이 찍히고 퍼즐 조각 지급 결과가 옴. 다른 상태로 돌리면 `completedAt: null`.
- `puzzlePieceAwarded: true` 면 이번 요청에서 퍼즐 조각을 새로 얻은 것 → "조각 획득" 연출. 같은 작업을 다시 완료하면 `false`(조각은 1개만).
```json
{
  "data": {
    "itemId": 1004,
    "status": "COMPLETED",
    "completedAt": "2026-08-19T21:16:03+09:00",
    "puzzlePieceAwarded": false,
    "puzzlePieceId": null
  },
  "meta": {
    "requestId": "38ed17bf-3da3-4208-b9cb-87e2edfd625c"
  }
}
```

### 5.4 삭제 `DELETE /schedule-items/{itemId}` → `204`

## 5-A. 퍼즐 API (7번, MVP)

퍼즐 = 스케줄 1개당 1개. 작업 1건이 조각 1개이고, **그 스케줄의 작업을 처음 완료할 때 퍼즐이 자동으로 생긴다** (따로 만드는 API 없음).

```ts
type PuzzleStatus = 'IN_PROGRESS' | 'COMPLETED';

interface PuzzleSummary {
  id: number; scheduleId: number; title: string;      // 제목 = 스케줄 제목
  status: PuzzleStatus; visibility: 'PUBLIC' | 'PRIVATE';
  imageId: number | null;        // 퍼즐 그림 (이미지 도메인 전까지 null)
  pieceCount: number;            // 전체 칸 수 = 유효한 작업 수
  earnedPieceCount: number;      // 획득한 조각 수
  completedAt: string | null;
}
interface PuzzleDetail extends PuzzleSummary {
  pieces: {
    pieceId: number | null; scheduleItemId: number; scheduleItemTitle: string;
    scheduledDate: string; earned: boolean;
    position: number | null;     // 획득 순서(0부터). 못 받았으면 null
    earnedAt: string | null;
  }[];
}
```

### 5-A.1 내 퍼즐 목록 `GET /puzzles/mine?status=&size=&cursor=`
`status?` `IN_PROGRESS`/`COMPLETED`, 커서 페이징(스케줄 목록과 동일 구조). 진행률은 `earnedPieceCount/pieceCount`.

### 5-A.2 퍼즐 상세 `GET /puzzles/{puzzleId}`
`pieces[]` 에 **퍼즐 칸이 전부** 들어온다 — 못 받은 칸은 `earned:false`. 그리드에 그대로 렌더링하면 됨.
- 본인 퍼즐: 항상 조회 가능
- 타인 퍼즐: 공개+완성된 것만, 아니면 `403 PUZZLE_NOT_PUBLIC` / 없으면 `404 PUZZLE_NOT_FOUND`

### 5-A.3 사용자 공개 퍼즐 `GET /users/{userId}/public-puzzles?size=&cursor=`
공개(PUBLIC) + 완성(COMPLETED) 퍼즐만. **인증 없이도 조회 가능**(비로그인 프로필 화면용).

### 알아둘 동작
| 상황 | 결과 |
|---|---|
| 작업 완료 | 조각 1개 지급 (`puzzlePieceAwarded:true`) |
| 같은 작업 다시 완료 | 지급 안 함 (`false`) — 조각은 영구 1개 |
| 완료 → TODO 되돌림 | **조각 유지** (회수 없음) |
| 유효한 작업 전부 조각 획득 | 퍼즐 `COMPLETED` + `completedAt` |
| 작업 추가 | 칸이 늘어 다시 `IN_PROGRESS` (`completedAt` null) |
| 작업 취소/삭제 | 칸이 줄어 다시 `COMPLETED` 가 될 수 있음 |

> 5번의 `completedPuzzleCount` 와 퍼즐의 `earnedPieceCount` 는 다를 수 있다(완료를 되돌려도 조각은 유지되므로). 퍼즐 화면은 퍼즐 API 숫자를 쓰면 됨.

> 2차 예정(미구현): 공개 범위 변경, 공개 갤러리, 좋아요.

## 6. 화면 ↔ API 매핑 (참고)

| 화면 | 호출 |
|---|---|
| 스케줄 목록(무한 스크롤) | `GET /schedules?size=20` → `nextCursor` 로 추가 로드 |
| 스케줄 상세(날짜별 작업, 퍼즐 진행도 `completedPuzzleCount/puzzleCount`) | `GET /schedules/{id}` |
| 스케줄 제목/기간 편집 | `PATCH /schedules/{id}` |
| 월 캘린더 | `GET /calendar?year&month` (월 바뀔 때마다) |
| 오늘 할 일 / 체크박스 | `GET /schedule-items/today` + `PATCH /schedule-items/{id}/status` |
| 작업 추가 폼 | `POST /schedules/{id}/items` (날짜는 스케줄 기간 안으로 제한해서 보여주기) |
| 작업 편집 | `PATCH /schedule-items/{id}` |
| 작업 삭제 | `DELETE /schedule-items/{id}` |
| 내 퍼즐 목록 / 진행률 | `GET /puzzles/mine` |
| 퍼즐 상세(조각 그리드) | `GET /puzzles/{puzzleId}` |
| 다른 사람 프로필의 공개 퍼즐 | `GET /users/{userId}/public-puzzles` |

낙관적 업데이트 후 실패하면 `code` 별 메시지로 롤백. 상태 변경은 멱등(같은 상태로 다시 보내도 200).

## 7. 로컬 개발 환경 / 테스트 데이터

```bash
# 백엔드 레포에서
docker ps | grep ai-hackathon-mysql          # MySQL 127.0.0.1:3307 (hackathon/hackathon, DB ai_hackathon)
mysql -h 127.0.0.1 -P 3307 -uhackathon -phackathon ai_hackathon < docs/migration/2026-08-19_schedule_items_add_position.sql  # 최초 1회
mysql -h 127.0.0.1 -P 3307 -uhackathon -phackathon ai_hackathon < docs/test-data/seed_schedule.sql
# .env: DB_URL / DB_USERNAME / DB_PASSWORD + APP_AUTH_DEV_HEADER_ENABLED=true (docs/postman/README.md 참고)
./gradlew bootRun                             # http://localhost:8080
```
seed 데이터 (헤더 `X-User-Id: 1` 로 호출):
| 사용자 | 스케줄 | 비고 |
|---|---|---|
| 1 | 101 `8월 알고리즘 공부` (ACTIVE, 08-17~08-30) | 작업 여러 건, 오늘(08-19) 2건, 취소 1건 |
| 1 | 102 `스프링 강의 완강` (DRAFT, 08-20~09-05) | |
| 1 | 103 `7월 영어 단어` (COMPLETED) | |
| 1 | 104 `운동 루틴` (ACTIVE, 8월) | 오늘 1건 |
| 2 | 201 | 타인 소유 → 403 확인용 (`X-User-Id: 2` 로 바꾸면 본인) |
| 카테고리 | 1 `공부`(사용 가능), 2(사용 중지 → 400) | |
| 하루 한도 | user 1·2 모두 5 | 08-19 에 user1 작업 3건 → 2개 더 추가 가능 |

Postman: `docs/postman/schedule-api.postman_collection.json`(5번 33요청), `docs/postman/schedule-item-api.postman_collection.json`(6번 38요청) — Import 해서 Run 하면 모든 케이스를 실제로 볼 수 있음. 가이드: `docs/postman/README.md`.

## 8. 확정 규칙 (설계서와 다르거나 설계서에 없던 것)

| 항목 | 확정 |
|---|---|
| 스케줄 응답 필드 | 설계서 8개만 (`source`, `description`, `createdAt`, `updatedAt` 없음) |
| 타인 소유 | 403 `FORBIDDEN` (없음/삭제는 404) |
| 유효한 작업 | 삭제 X, `CANCELLED` X → 퍼즐 수·날짜별 `totalCount`·한도 계산 모두 이 기준. CANCELLED 는 목록엔 보임 |
| 같은 날짜 정렬 | `position` → `priority` → `id` |
| `position` | 미지정 시 맨 뒤. 순서를 바꾸려면 `PATCH /schedule-items/{id}` 로 개별 작업의 `position` 수정 (일괄 순서 변경 API 없음) |
| 하루 최대 작업 수 | `user_preferences.max_daily_tasks`(설정 API, 기본 5). **사용자 전체 스케줄 합산** |
| 상태 변경 | `completedAt` 은 COMPLETED 첫 진입 시, 다른 상태로 가면 null. 변경 이력/버전에 남기지 않음 |
| 추가/수정/삭제 | `currentVersion` +1 (프론트는 표시만) |
| 인증 | 세션 쿠키 `SESSION` (`credentials: 'include'` 필수). 로컬 개발 한정 `X-User-Id` 폴백 |
| 퍼즐 | 스케줄당 1개, 첫 작업 완료 시 자동 생성. 조각 = 작업, position = 획득 순서. 회수 없음 |
