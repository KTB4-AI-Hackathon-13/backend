# AI 계획 플래너 API 명세서 - Notion용

> Notion에서 `가져오기 → Markdown & CSV`로 이 파일을 가져오거나, 아래 표를 그대로 복사해서 사용할 수 있습니다.

## 기본 정보

| 항목 | 내용 |
|---|---|
| API 버전 | v1 |
| Base URL | `/api/v1` |
| 데이터 형식 | `application/json; charset=utf-8` |
| 인증 방식 | 서버 세션 + HttpOnly 쿠키 |
| 날짜 형식 | `YYYY-MM-DD` |
| 일시 형식 | ISO 8601, 예: `2026-08-20T14:20:00+09:00` |
| 기본 시간대 | `Asia/Seoul` |
| 삭제 방식 | `deleted_at`을 이용한 소프트 삭제 |

## 핵심 정책

| 번호 | 정책 |
|---:|---|
| 1 | AI는 시각을 정하지 않고 작업을 날짜 단위로 분배한다. |
| 2 | `schedule_items` 한 건은 퍼즐 조각 한 개에 대응한다. |
| 3 | 유효한 작업을 처음 완료하면 퍼즐 조각 한 개를 지급한다. |
| 4 | 완료 상태를 반복 변경해도 같은 작업에서 조각을 중복 지급하지 않는다. |
| 5 | 스케줄의 퍼즐 수는 저장하지 않고 `schedule_items` 원본 행을 `COUNT(*)`로 계산한다. |
| 6 | 수정 전 메시지와 스케줄 변경 이력은 삭제하지 않고 보존한다. |

## 공통 응답

| 구분 | 형식 |
|---|---|
| 성공 | `{"data": {...}, "meta": {"requestId": "..."}}` |
| 실패 | `{"code": "ERROR_CODE", "message": "오류 설명", "fieldErrors": [], "requestId": "..."}` |
| 목록 | `{"data": {"items": [], "nextCursor": "...", "hasNext": true}}` |

## 공통 상태 코드

| HTTP 상태 | 의미 |
|---:|---|
| 200 | 조회 또는 수정 성공 |
| 201 | 생성 성공 |
| 202 | AI 생성 등 비동기 작업 접수 |
| 204 | 본문 없는 성공 |
| 400 | 잘못된 요청 형식 |
| 401 | 로그인 필요 |
| 403 | 접근 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 중복 또는 상태 충돌 |
| 422 | 비즈니스 규칙 위반 |
| 429 | 요청 횟수 제한 초과 |
| 500 | 서버 내부 오류 |
| 503 | AI 또는 외부 저장소 장애 |

# 1. 회원·인증 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| ✅ 계획 없이 작업 추가 | POST | `/schedule-items` | O | `title`, `scheduledDate`, `estimatedMinutes`, `description?`, `categoryId?`, `workload?`, `priority?` | `scheduleId=null`인 단독 작업 | `schedule_items`, 변경 이력 |
| ✅ 사용자 스케줄 생성 | POST | `/schedules` | O | `title`, `description?`, `startDate`, `endDate` | 생성 스케줄, 퍼즐 수 0 | `schedules`, `schedule_change_logs` |
| 회원가입 | POST | `/auth/signup` | X | `email`, `password`, `passwordConfirmation`, `nickname`, `termsAgreed`, `privacyAgreed` | `userId`, `email`, `nickname` | `users`, `user_auth_accounts` |
| 로그인 | POST | `/auth/login` | X | `email`, `password` | 사용자 요약, 세션 쿠키 | `users`, `auth_sessions` |
| 카카오 로그인 시작 | GET | `/auth/oauth/kakao` | X | 없음 | 카카오 동의 화면으로 302 이동, `OAUTH_STATE` 쿠키 | 없음 |
| 카카오 로그인 콜백 | GET | `/auth/oauth/kakao/callback` | X | `code`, `state` | 계정 가입·연결 후 `SESSION` 쿠키, FE로 302 이동 | `users`, `user_auth_accounts`, `auth_sessions` |
| 로그아웃 | POST | `/auth/logout` | O | 없음 | 204 | `auth_sessions` |

카카오 로그인은 `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `KAKAO_FRONTEND_REDIRECT_URI`로 설정한다. 콜백의 `state`는 5분 유효 HttpOnly 쿠키와 비교한다. 인증된 이메일이 제공되면 실제 이메일을 사용하고, 없으면 `kakao_{회원번호}@oauth.local` 내부 이메일로 가입한다.
| 내 정보 조회 | GET | `/users/me` | O | 없음 | `id`, `email`, `nickname`, `profileImageUrl`, `timezone` | `users`, `images` |
| 내 정보 수정 | PATCH | `/users/me` | O | `nickname?`, `profileImageId?`, `timezone?` | 수정된 회원 정보 | `users` |
| 비밀번호 변경 | PATCH | `/users/me/password` | O | `currentPassword`, `newPassword`, `newPasswordConfirmation` | 204 | `users`, `auth_sessions` |
| 회원탈퇴 | DELETE | `/users/me` | O | `password?`, `reason?` | 204 | `users`, `auth_sessions` |

### 회원·인증 오류

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `EMAIL_ALREADY_EXISTS` | 409 | 이미 가입된 이메일 |
| `NICKNAME_ALREADY_EXISTS` | 409 | 이미 사용 중인 닉네임 |
| `PASSWORD_POLICY_VIOLATION` | 422 | 비밀번호 정책 불충족 |
| `TERMS_REQUIRED` | 422 | 필수 약관 미동의 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `CURRENT_PASSWORD_MISMATCH` | 422 | 현재 비밀번호 불일치 |
| `PASSWORD_CONFIRMATION_MISMATCH` | 422 | 새 비밀번호 확인 불일치 |
| `ACCOUNT_SUSPENDED` | 403 | 이용 정지 회원 |

# 2. 사용자 설정 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| 설정 조회 | GET | `/users/me/preferences` | O | 없음 | 날짜 분배·공개·랭킹·알림 설정 | `user_preferences` |
| 설정 수정 | PATCH | `/users/me/preferences` | O | 변경할 설정 필드 | 수정된 설정 전체 | `user_preferences` |

### 설정 필드

| 필드 | 타입 | 설명 | 현재 ERD |
|---|---|---|---:|
| `maxDailyTasks` | number | 하루 최대 작업 수 | O |
| `weekendScheduleEnabled` | boolean | 주말 작업 배정 허용 | O |
| `aiRescheduleEnabled` | boolean | AI 자동 재분배 허용 | O |
| `notificationEnabled` | boolean | 전체 알림 허용 | O |
| `defaultPuzzleVisibility` | `PUBLIC` 또는 `PRIVATE` | 2차 공개 범위 선택 기능. MVP는 `PUBLIC` 고정 | 2차 추가 |
| `rankingParticipationEnabled` | boolean | 랭킹 참여 여부 | 추가 필요 |
| `galleryNicknameVisible` | boolean | 갤러리 닉네임 공개 여부 | 추가 필요 |
| `likeNotificationEnabled` | boolean | 좋아요 알림 여부 | 추가 필요 |
| `rankingChangeNotificationEnabled` | boolean | 랭킹 변동 알림 여부 | 추가 필요 |

# 3. AI 대화 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| 대화방 생성 | POST | `/conversations` | O | `title?` | `conversationId`, `status` | `conversations` |
| 대화방 목록 | GET | `/conversations` | O | `size?`, `cursor?` | 최근 대화방 목록 | `conversations` |
| 메시지 목록 | GET | `/conversations/{conversationId}/messages` | O | `size?`, `before?` | 메시지 목록 | `conversation_messages` |
| AI 메시지 전송 | POST | `/conversations/{conversationId}/messages` | O | `content` | 사용자 메시지, AI 답변, 계획 준비 상태 | `conversation_messages` |
| 메시지 수정 | POST | `/conversations/{conversationId}/messages/{messageId}/revisions` | O | `content` | 수정 메시지, AI 답변 | `conversation_messages` |
| 대화방 보관 | PATCH | `/conversations/{conversationId}` | O | `status=ARCHIVED` | 수정된 대화방 | `conversations` |

### 메시지 전송 응답 필드

| 필드 | 설명 |
|---|---|
| `userMessage` | DB에 저장된 사용자 메시지 |
| `assistantMessage` | AI가 생성한 답변 |
| `planReadiness.ready` | 계획 생성에 필요한 정보가 충분한지 여부 |
| `planReadiness.missingFields` | 부족한 정보 목록. 예: `END_DATE`, `AVAILABLE_DAYS` |

### AI 대화 오류

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `CONVERSATION_NOT_FOUND` | 404 | 대화방 없음 |
| `MESSAGE_NOT_FOUND` | 404 | 메시지 없음 |
| `AI_RATE_LIMITED` | 429 | AI 호출 한도 초과 |
| `AI_PROVIDER_UNAVAILABLE` | 503 | AI 제공자 장애 |

# 4. AI 계획 생성·수정 API

BE가 외부 AI 서버에 요청하고 응답을 기다린 뒤, 검증과 저장을 완료한 스케줄을 반환한다. 별도의 생성 상태 조회나 AI → BE 콜백 API는 사용하지 않는다.

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| ✅ AI 계획 생성 | POST | `/schedules/ai-generations` | O | `conversationId`, `title`, `categoryId` | 저장 완료된 스케줄 상세(200) | `schedules`, `schedule_items` |
| ✅ AI 계획 수정 | POST | `/schedules/{scheduleId}/ai-revisions` | O | `conversationId`, `goalSummary`, `category`, `templateAnswers`, `currentPlan`, `userMessage` | 확정 시 저장된 스케줄 ID, AI `category`, 무작위 이미지 URL | `schedules`, `schedule_items`, `categories`, `images` |

AI 응답의 `estimated_min`은 `schedule_items.estimated_minutes`, `summary`는 `schedules.description`에 저장한다. 외부 AI 설정은 `AI_BASE_URL`, `AI_CONNECT_TIMEOUT_SECONDS`, `AI_READ_TIMEOUT_SECONDS`를 사용한다.

`/ai-revisions`에서 외부 AI 응답이 `confirmed: true`이면 응답 최상위 한글 `category`를 Java `CategoryType`으로 검증하고 영문 코드로 변환한다. 활성 `categories.code`가 같은 삭제되지 않은 이미지 중 하나를 무작위로 선택하고 `image_id`, 새 S3 서명 `image_url`, `image_url_expires_at`을 함께 반환한다. 지원 카테고리는 운동·다이어트·음악·공부·어학·커리어·습관·마인드셋·인간관계·취미이다.


### AI 계획 오류

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `PLAN_INFORMATION_INCOMPLETE` | 422 | 계획 생성 정보 부족 |
| `AI_RATE_LIMITED` | 429 | 외부 AI 호출 한도 초과 |
| `AI_PROVIDER_UNAVAILABLE` | 503 | 외부 AI 연결 실패, 제한 시간 초과 또는 오류 응답 |

# 5. 스케줄 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| ✅ 스케줄 목록 | GET | `/schedules` | O | `status?`, `size?`, `cursor?` | 스케줄 목록, 전체·완료 퍼즐 수 | `schedules`, `schedule_items` |
| ✅ 스케줄 상세 | GET | `/schedules/{scheduleId}` | O | 없음 | 계획 요약, 날짜별 작업, 퍼즐 수 | `schedules`, `schedule_items` |
| ✅ 스케줄 수정 | PATCH | `/schedules/{scheduleId}` | O | `title?`, `description?`, `startDate?`, `endDate?` | 수정된 스케줄 | `schedules`, `schedule_change_logs` |
| ✅ 스케줄 삭제 | DELETE | `/schedules/{scheduleId}` | O | 없음 | 204 | `schedules` |
| ✅ 월별 캘린더 | GET | `/calendar` | O | `year`, `month` | 날짜별 작업 목록 | `schedule_items` |
| ✅ 오늘 할 일 | GET | `/schedule-items/today` | O | 없음 | 오늘 작업, 완료 수, 전체 수 | `schedule_items` |

### 스케줄 응답 필드

| 필드 | 타입 | 설명 | 저장 여부 |
|---|---|---|---|
| `id` | number | 스케줄 ID | 저장 |
| `title` | string | 스케줄 제목 | 저장 |
| `status` | enum | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` | 저장 |
| `startDate` | date | 시작일 | 저장 |
| `endDate` | date | 종료일 | 저장 |
| `currentVersion` | number | 현재 변경 버전 | 저장 |
| `puzzleCount` | number | 삭제·취소되지 않은 전체 작업 수 🆕 | 조회 시 계산 |
| `completedPuzzleCount` | number | 완료된 작업 수 | 조회 시 계산 |

### 퍼즐 개수 계산

| 값 | 계산 기준 |
|---|---|
| 전체 퍼즐 수 | `schedule_id`가 같고 `deleted_at IS NULL`이고 `status != 'CANCELLED'`인 작업 수 🆕 |
| 완료 퍼즐 수 | 위 조건에 더해 `status = 'COMPLETED'`인 작업 수 |

```sql
SELECT
    COUNT(*) AS puzzle_count,
    SUM(status = 'COMPLETED') AS completed_puzzle_count
FROM schedule_items
WHERE schedule_id = :schedule_id
  AND deleted_at IS NULL
  AND status != 'CANCELLED';  -- 🆕 취소(CANCELLED) 작업 제외
```

# 6. 작업 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| ✅ 작업 추가 | POST | `/schedules/{scheduleId}/items` | O | `title`, `scheduledDate`, `estimatedMinutes`, `description?`, `categoryId?`, `workload?`, `priority?`, `position?` | 생성된 작업 | `schedule_items` |
| ✅ 작업 수정 | PATCH | `/schedule-items/{itemId}` | O | 변경할 작업 필드 | 수정된 작업 | `schedule_items`, `schedule_change_logs` |
| ✅ 작업 상태 변경 | PATCH | `/schedule-items/{itemId}/status` | O | `status` | 완료 시 퍼즐 조각 지급 결과 | `schedule_items`, 퍼즐 조각 테이블 필요 |
| ✅ 작업 삭제 | DELETE | `/schedule-items/{itemId}` | O | 없음 | 204 | `schedule_items` |

### 작업 상태

| 상태 | 의미 |
|---|---|
| `TODO` | 시작 전 |
| `IN_PROGRESS` | 진행 중 |
| `COMPLETED` | 완료, 최초 완료 시 조각 지급 |
| `SKIPPED` | 해당 날짜에 건너뜀 |
| `CANCELLED` | 작업 취소 |

### 작업 완료 응답

| 필드 | 타입 | 설명 |
|---|---|---|
| `itemId` | number | 완료한 작업 ID |
| `status` | string | `COMPLETED` |
| `completedAt` | datetime | 완료 처리 시각 |
| `puzzlePieceAwarded` | boolean | 이번 요청에서 조각을 지급했는지 |
| `puzzlePieceId` | number 또는 null | 지급된 조각 ID |

### 작업 오류

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `SCHEDULE_NOT_FOUND` | 404 | 스케줄 없음 |
| `SCHEDULE_ITEM_NOT_FOUND` | 404 | 작업 없음 |
| `DATE_OUTSIDE_SCHEDULE_PERIOD` | 422 | 작업 날짜가 스케줄 기간 밖 |
| `MAX_DAILY_TASKS_EXCEEDED` | 422 | 하루 최대 작업 수 초과 |
| `ITEMS_OUTSIDE_SCHEDULE_PERIOD` | 409 | 기간 변경 시 범위 밖 작업 존재 |
| `INVALID_SCHEDULE_PERIOD` | 422 | 스케줄 시작일이 종료일보다 늦음 🆕 |
| `INVALID_CURSOR` | 400 | 목록 조회 cursor 값이 유효하지 않음 🆕 |

# 7. 퍼즐 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| 내 퍼즐 목록 | GET | `/puzzles/mine` | O | `status?`, `size?`, `cursor?` | 진행·완성 퍼즐 목록 | `puzzles` 추가 필요 |
| 내 퍼즐 상세 | GET | `/puzzles/{puzzleId}` | O | 없음 | 퍼즐 정보, 조각별 획득 상태 | `puzzles`, `puzzle_pieces` 추가 필요 |
| 사용자 공개 퍼즐 | GET | `/users/{userId}/public-puzzles` | 선택 | `sort?`, `size?`, `cursor?` | 특정 사용자의 공개 퍼즐 | 퍼즐 테이블 필요 |

### 2차 퍼즐 API

| 기능 | Method | URL | 도입 시점 | 비고 |
|---|---|---|---|---|
| 공개 범위 변경 | PATCH | `/puzzles/{puzzleId}/visibility` | 2차 | MVP는 완성 퍼즐 기본 공개 |
| 공개 갤러리 | GET | `/gallery` | 2차 | 최신순·인기순 목록 |
| 공개 퍼즐 상세 | GET | `/gallery/puzzles/{puzzleId}` | 2차 | 작품·작성자·완성일·좋아요 표시 |
| 좋아요 등록 | PUT | `/gallery/puzzles/{puzzleId}/like` | 2차 | 사용자별 중복 방지 |
| 좋아요 취소 | DELETE | `/gallery/puzzles/{puzzleId}/like` | 2차 | 204 반환 |

### 퍼즐 상태·공개 범위

| 구분 | 값 | 의미 |
|---|---|---|
| 상태 | `IN_PROGRESS` | 조각 수집 중 |
| 상태 | `COMPLETED` | 모든 조각 획득 |
| MVP 공개 범위 | `PUBLIC` | 완성 퍼즐은 기본 공개 |
| 2차 공개 범위 | `PRIVATE` | 선택 기능 도입 후 소유자만 조회 가능 |

### 퍼즐 오류

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `PUZZLE_NOT_FOUND` | 404 | 퍼즐 없음 |
| `PUZZLE_NOT_COMPLETED` | 422 | 미완성 퍼즐 공개 요청 |
| `PUZZLE_NOT_PUBLIC` | 403 | 2차 공개 범위 선택 기능에서 비공개 퍼즐 접근 |

# 8. 이미지 API

| 기능 | Method | URL | 인증 | Content-Type | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|---|
| 이미지 업로드 | POST | `/images` | O | `multipart/form-data` | `file`, `ownerType`, `ownerId`, `categoryId?` | 이미지 ID·URL·크기·카테고리 | `images`, `categories` |
| 이미지 조회 | GET | `/images/{imageId}` | 권한별 | JSON | 없음 | 메타데이터, categoryId, 서명 URL | `images`, `categories` |
| 이미지 삭제 | DELETE | `/images/{imageId}` | O | JSON | 없음 | 204 | `images` |

### 이미지 업로드 제한

| 검증 항목 | 정책 예시 |
|---|---|
| 허용 형식 | PNG, JPEG, WebP |
| 최대 크기 | 10MB 이하 |
| 파일 검증 | 확장자, MIME 타입, 이미지 디코딩 모두 확인 |
| 파일 저장 | 오브젝트 스토리지 |
| DB 저장 | 경로와 메타데이터만 저장 |

### 서명 URL(만료) 🆕

업로드·조회 응답의 `url`은 영구 링크가 아니라 매 요청마다 새로 발급하는 S3 서명(presigned) URL이다.
`urlExpiresAt`(기본 발급 후 10분) 이후에는 이 URL로 접근할 수 없다.

| 항목 | 내용 |
|---|---|
| 만료 시간 | 기본 10분 (서버 설정값, 응답의 `urlExpiresAt`로 매번 확인) |
| 캐싱 여부 | 프론트에서 `url`을 저장해두고 재사용하지 말 것 |
| 갱신 방법 | 화면에 다시 표시할 때마다 `GET /images/{imageId}`를 호출해 새 서명 URL을 받는다 |

### 소유권 규칙 🆕

업로드·조회·삭제 모두 `ownerType` + `ownerId`가 **로그인한 사용자 소유의 실제 리소스**를 가리켜야 한다.
즉 부모 리소스(스케줄·작업·퍼즐 등)를 먼저 만들어 id를 받은 뒤, 그 id로 이미지를 업로드하는 순서여야 한다.

| `ownerType` | `ownerId`가 가리키는 것 | 검증 기준 |
|---|---|---|
| `USER` | 자기 자신의 회원 id | `ownerId == 내 userId` |
| `SCHEDULE` | 내 스케줄 id | `schedules.user_id == 내 userId` |
| `SCHEDULE_ITEM` | 내 스케줄에 속한 작업 id | 상위 `schedules.user_id == 내 userId` |
| `MESSAGE` | 내 대화에 속한 메시지 id | 상위 `conversations.user_id == 내 userId` |
| `PUZZLE` | 내 퍼즐 id | `puzzles.user_id == 내 userId` |

기준을 만족하지 않으면 업로드 시점에 `IMAGE_ACCESS_DENIED`(403)로 거부된다.

### 이미지 오류 🆕

| 오류 코드 | HTTP | 조건 |
|---|---:|---|
| `IMAGE_NOT_FOUND` | 404 | 이미지 없음(또는 이미 삭제됨) |
| `IMAGE_ACCESS_DENIED` | 403 | 본인 소유가 아니거나 `ownerId`가 내 리소스가 아님 |
| `IMAGE_FILE_REQUIRED` | 400 | `file` 파트가 없거나 빈 파일 |
| `IMAGE_TOO_LARGE` | 413 | 10MB 초과 |
| `UNSUPPORTED_IMAGE_TYPE` | 415 | PNG, JPEG, WebP 이외 형식 |
| `INVALID_IMAGE_FILE` | 422 | 확장자·MIME은 맞지만 이미지 디코딩 실패(손상 파일) |
| `IMAGE_STORAGE_FAILED` | 500 | 오브젝트 스토리지 업로드·삭제·서명 URL 발급 실패 |

# 9. 랭킹 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| 랭킹 조회 | GET | `/rankings` | 선택 | `type`, `categoryId?`, `period?`, `size?` | 순위 목록, 내 순위 | `ranking_snapshots` |
| 랭킹 사용자 퍼즐 | GET | `/users/{userId}/public-puzzles` | 선택 | `sort?`, `size?`, `cursor?` | 공개 완성 퍼즐 | 퍼즐 테이블 추가 필요 |

### 랭킹 기준

| type | 설명 |
|---|---|
| `STREAK` | 연속 실천 일수 |
| `COMPLETED_PUZZLES` | 완성한 퍼즐 작품 수 |
| `PUZZLE_PIECES` | 완료 작업으로 획득한 조각 수 |

### 랭킹 응답 필드

| 필드 | 설명 |
|---|---|
| `rankingDate` | 랭킹 산정 기준일 |
| `items[].rank` | 순위 |
| `items[].userId` | 사용자 ID |
| `items[].nickname` | 공개 닉네임 |
| `items[].score` | 랭킹 점수 |
| `myRanking.rank` | 로그인 사용자의 순위 |
| `myRanking.score` | 로그인 사용자의 점수 |
| `myRanking.tier` | 사용자 티어 |

# 10. 통계 API

| 기능 | Method | URL | 인증 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| 내 달성 통계 | GET | `/users/me/metrics` | O | `from`, `to`, `categoryId?` | 계획·완료 작업 수, 조각 수, 달성률, 연속 일수 | `user_daily_metrics` |

### 통계 필드

| 필드 | 설명 |
|---|---|
| `plannedItemCount` | 기간 내 계획된 작업 수 |
| `completedItemCount` | 기간 내 완료한 작업 수 |
| `puzzlePieceCount` | 획득한 퍼즐 조각 수 |
| `achievementRate` | `완료 작업 수 ÷ 계획 작업 수 × 100` |
| `consecutiveDays` | 연속 실천 일수 |
| `daily` | 날짜별 세부 통계 |

# 11. 내부 배치 API

| 기능 | Method | URL | 공개 여부 | 주요 요청값 | 주요 응답값 | 연결 테이블 |
|---|---|---|---:|---|---|---|
| AI 리스케줄링 실행 | POST | `/internal/batches/ai-reschedule` | 내부 전용 | `targetDate` | `batchJobId`, `status` | `batch_jobs` |

### 배치 대상

| 조건 | 기준 |
|---|---|
| 회원 상태 | `users.status = ACTIVE` |
| 자동 재분배 | `ai_reschedule_enabled = true` |
| 대상 작업 | 지정일까지 완료되지 않은 유효 작업 |
| 실행 시간 | 매일 02:00~06:00 |

# 12. ERD 보완 필요 목록

| 우선순위 | 추가 구조 | 주요 컬럼·제약 | 필요한 기능 |
|---:|---|---|---|
| 1 | `ai_generation_jobs` | `id`, `conversation_id`, `schedule_id`, `status`, `failure_reason`, `created_at`, `finished_at` | AI 로딩, 실패, 재시도 |
| 1 | `puzzles` | `id`, `schedule_id`, `title`, `status`, `visibility`, `image_id`, `completed_at` | 내 퍼즐, 공개 갤러리 |
| 1 | `puzzle_pieces` | `id`, `puzzle_id`, `schedule_item_id UNIQUE`, `position`, `earned_at` | 작업 완료당 조각 1개, 중복 방지 |
| 2 | `puzzle_likes` | `puzzle_id`, `user_id`, `created_at`, 복합 UNIQUE | 좋아요 |
| 2 | 설정 컬럼 확장 | 공개 범위·랭킹 참여·닉네임 공개·알림 종류 | 설정 화면 |
| 2 | 메시지 분기 필드 | `parent_message_id` 또는 `branch_id` | 수정 메시지 이후 흐름 보존 |

# 13. 구현 우선순위

| 단계 | 기능 |
|---|---|
| MVP-1 | 회원가입, 로그인, 로그아웃 |
| MVP-2 | AI 대화 저장·조회 |
| MVP-3 | 동기식 AI 계획 생성 |
| MVP-4 | 스케줄·작업 CRUD |
| MVP-5 | 캘린더와 오늘 할 일 |
| MVP-6 | 작업 완료와 퍼즐 조각 지급 |
| MVP-7 | 내 퍼즐 조회 및 완성 퍼즐 기본 공개 |
| MVP-8 | 이미지 업로드·조회 |
| 2차-1 | AI 계획 수정과 자동 리스케줄링 |
| 2차-2 | 공개 범위 선택과 공개 퍼즐 갤러리 |
| 2차-3 | 좋아요 등록·취소 |
| 2차-4 | 전체·카테고리별 랭킹 |
| 2차-5 | 알림 발송과 세부 알림 설정 |
