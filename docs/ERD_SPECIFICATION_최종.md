# AI 스케줄러 ERD 명세서

기준 파일: `scheduler_erdcloud_mysql.sql`  
DBMS: MySQL 8.x

## 1. 설계 개요

이 서비스는 사용자가 해야 할 일을 입력하면 AI가 특정 시간이 아닌 **날짜 단위로 작업을 분배**하는 스케줄러다. 미완료 작업은 새벽 배치에서 달성률을 기준으로 다시 분배할 수 있다.

주요 데이터 영역은 다음과 같다.

| 영역 | 테이블 |
|---|---|
| 회원·인증 | `users`, `user_auth_accounts`, `auth_sessions`, `user_preferences` |
| 스케줄 | `categories`, `schedules`, `schedule_items`, `schedule_change_logs` |
| AI 대화 | `conversations`, `conversation_messages` |
| 이미지 | `images` |
| 통계·랭킹 | `user_daily_metrics`, `ranking_snapshots` |
| 배치·리스케줄링 | `batch_jobs`, `reschedule_results` |

## 2. 공통 규칙

| 용어 | 의미 |
|---|---|
| PK | 각 행을 유일하게 식별하는 기본키 |
| FK | 다른 테이블의 PK를 참조하는 외래키 |
| `created_at` | 데이터가 처음 생성된 시각 |
| `updated_at` | 데이터가 마지막으로 변경된 시각 |
| `deleted_at` | 소프트 삭제 시각. `NULL`이면 정상 데이터 |
| `BIGINT AUTO_INCREMENT` | DB가 순서대로 발급하는 숫자 ID |
| `CHAR(36)` | 하이픈을 포함한 UUID 문자열 |
| `BOOLEAN` | MySQL에서는 사실상 `TINYINT(1)`. `1`은 참, `0`은 거짓 |

일반 조회에서는 소프트 삭제된 데이터를 제외해야 한다.

```sql
WHERE deleted_at IS NULL
```

## 3. 회원·인증

### 3.1 `users` — 회원 기본 정보

회원가입, 로그인, 회원 정보 조회·수정, 탈퇴에 필요한 핵심 정보를 저장한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 회원 고유 ID, PK |
| `email` | VARCHAR(255) | Y | 로그인 이메일. 중복 불가 |
| `password_hash` | VARCHAR(255) | N | 암호화된 비밀번호. 소셜 로그인 전용 회원은 `NULL` 가능 |
| `nickname` | VARCHAR(50) | Y | 서비스에 표시할 닉네임. 중복 불가 |
| `profile_image_id` | BIGINT | N | 프로필로 사용하는 `images.id` |
| `timezone` | VARCHAR(50) | Y | 날짜 계산 기준 시간대. 기본값 `Asia/Seoul` |
| `status` | ENUM | Y | 회원 상태 |
| `last_login_at` | DATETIME | N | 최근 로그인 시각 |
| `created_at` | DATETIME | Y | 가입 시각 |
| `updated_at` | DATETIME | Y | 회원 정보 최종 수정 시각 |
| `withdrawn_at` | DATETIME | N | 회원탈퇴 시각 |

`status` 값:

| 값 | 의미 |
|---|---|
| `ACTIVE` | 정상 회원 |
| `DORMANT` | 휴면 회원 |
| `WITHDRAWN` | 탈퇴 회원 |
| `SUSPENDED` | 이용 정지 회원 |

### 3.2 `user_auth_accounts` — 로그인 수단

한 회원이 일반 로그인 또는 여러 소셜 로그인 수단을 연결할 수 있도록 인증 계정을 분리한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 인증 계정 고유 ID, PK |
| `user_id` | BIGINT | Y | 인증 계정의 소유 회원, `users.id` FK |
| `provider` | ENUM | Y | 로그인 제공자: `LOCAL`, `GOOGLE`, `KAKAO`, `NAVER` 중 하나 |
| `provider_user_id` | VARCHAR(255) | Y | 로그인 제공자가 발급한 사용자 식별자 |
| `created_at` | DATETIME | Y | 로그인 수단 연결 시각 |
| `updated_at` | DATETIME | Y | 로그인 수단 정보 수정 시각 |

`(provider, provider_user_id)` 조합은 중복될 수 없고, 한 회원은 같은 제공자를 두 번 연결할 수 없다.

### 3.3 `auth_sessions` — 로그인 세션

로그인 상태, 로그아웃, 세션 만료 또는 리프레시 토큰 폐기를 관리한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | CHAR(36) | Y | 세션 UUID, PK |
| `user_id` | BIGINT | Y | 로그인한 회원, `users.id` FK |
| `refresh_token_hash` | VARCHAR(255) | N | JWT 방식 사용 시 리프레시 토큰의 해시. 원문 저장 금지 |
| `user_agent` | VARCHAR(500) | N | 로그인한 브라우저·앱 정보 |
| `ip_address` | VARCHAR(45) | N | 로그인 IP. IPv4와 IPv6 지원 |
| `expires_at` | DATETIME | Y | 세션 만료 시각 |
| `revoked_at` | DATETIME | N | 로그아웃이나 강제 만료 시각 |
| `created_at` | DATETIME | Y | 세션 생성 시각 |
| `last_used_at` | DATETIME | N | 세션을 마지막으로 사용한 시각 |

세션 방식이면 `id`를 세션 식별자로 사용할 수 있고, JWT 방식이면 `refresh_token_hash`를 함께 사용한다.

### 3.4 `user_preferences` — AI 분배 개인 설정

AI가 작업을 날짜별로 분배할 때 적용할 사용자별 정책이다. `user_id`가 PK이므로 회원과 1:1 관계다.

| 컬럼 | 타입 | 필수 | 기본값 | 역할 |
|---|---|---:|---:|---|
| `user_id` | BIGINT | Y | - | 설정 소유 회원, PK이자 `users.id` FK |
| `max_daily_tasks` | INT | Y | 5 | 하루에 배정 가능한 최대 작업 개수 |
| `weekend_schedule_enabled` | BOOLEAN | Y | 1 | 주말에도 작업을 배정할지 여부 |
| `ai_reschedule_enabled` | BOOLEAN | Y | 1 | 미완료 작업의 AI 자동 재분배 허용 여부 |
| `notification_enabled` | BOOLEAN | Y | 1 | 일정·재분배 알림 허용 여부 |
| `created_at` | DATETIME | Y | - | 설정 생성 시각 |
| `updated_at` | DATETIME | Y | - | 설정 최종 수정 시각 |

## 4. 스케줄

### 4.1 `categories` — 작업 카테고리

퍼즐, 공부, 운동 등의 작업 유형을 관리하며 카테고리별 랭킹에도 사용한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 카테고리 고유 ID, PK |
| `name` | VARCHAR(80) | Y | 카테고리명. 중복 불가 |
| `description` | VARCHAR(500) | N | 카테고리 설명 |
| `icon_url` | VARCHAR(1000) | N | 화면에 표시할 아이콘 URL |
| `display_order` | INT | Y | 카테고리 표시 순서. 작은 값부터 먼저 표시 |
| `is_active` | BOOLEAN | Y | 현재 사용 가능한 카테고리인지 여부 |
| `created_at` | DATETIME | Y | 생성 시각 |
| `updated_at` | DATETIME | Y | 수정 시각 |

### 4.2 `schedules` — 스케줄 묶음

사용자의 하나의 목표 또는 계획 전체를 표현한다. 예: `8월 알고리즘 공부 계획`.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 스케줄 고유 ID, PK |
| `user_id` | BIGINT | Y | 스케줄 소유 회원, `users.id` FK |
| `title` | VARCHAR(200) | Y | 스케줄 제목 |
| `description` | TEXT | N | 목표, 조건 등의 상세 설명 |
| `status` | ENUM | Y | 스케줄 진행 상태 |
| `source` | ENUM | Y | 최초 생성 주체 |
| `start_date` | DATE | Y | 계획 시작일 |
| `end_date` | DATE | Y | 계획 종료일 |
| `current_version` | INT | Y | 현재 변경 버전. 변경할 때 증가 |
| `created_at` | DATETIME | Y | 생성 시각 |
| `updated_at` | DATETIME | Y | 수정 시각 |
| `deleted_at` | DATETIME | N | 소프트 삭제 시각 |

`status` 값:

| 값 | 의미 |
|---|---|
| `DRAFT` | 작성 중 |
| `ACTIVE` | 진행 중 |
| `COMPLETED` | 완료 |
| `ARCHIVED` | 종료되어 보관 중 |

`source` 값:

| 값 | 의미 |
|---|---|
| `USER` | 사용자가 직접 생성 |
| `AI` | AI가 생성 |
| `RESCHEDULE_BATCH` | 새벽 재분배 배치가 생성·변경 |

스케줄의 퍼즐 조각 수는 별도 컬럼에 중복 저장하지 않고 `schedule_items` 원본 데이터에서 조회할 때 계산한다. 작업 한 건이 퍼즐 조각 한 개이므로 다음 쿼리의 결과가 해당 스케줄의 퍼즐 조각 수다.

```sql
SELECT COUNT(*) AS puzzle_count
FROM schedule_items
WHERE schedule_id = :schedule_id
  AND deleted_at IS NULL;
```

### 4.3 `schedule_items` — 날짜별 실제 작업

스케줄 안에서 사용자가 실제로 수행할 최소 작업 단위다. 예: `2026-08-20 알고리즘 문제 10개 풀기`.

| 컬럼 | 타입 | 필수 | 기본값 | 역할 |
|---|---|---:|---:|---|
| `id` | BIGINT | Y | - | 작업 고유 ID, PK |
| `schedule_id` | BIGINT | N | - | 소속 스케줄, `schedules.id` FK. 단독 작업은 `NULL` |
| `user_id` | BIGINT | Y | - | 작업 소유 사용자, `users.id` FK. 단독 작업 권한·조회 기준 |
| `category_id` | BIGINT | N | `NULL` | 작업 카테고리, `categories.id` FK |
| `parent_item_id` | BIGINT | N | `NULL` | 상위 작업, 자기 자신 테이블의 `id` FK |
| `title` | VARCHAR(200) | Y | - | 작업 제목 |
| `description` | TEXT | N | `NULL` | 작업 상세 내용 |
| `scheduled_date` | DATE | Y | - | AI 또는 사용자가 작업을 배정한 날짜 |
| `workload` | INT | Y | 1 | 작업의 상대적 업무량. 숫자가 클수록 무거운 작업 |
| `priority` | TINYINT | Y | 3 | 우선순위. 1이 가장 높고 5가 가장 낮음 |
| `status` | ENUM | Y | `TODO` | 작업 진행 상태 |
| `source` | ENUM | Y | `USER` | 작업 생성·변경 주체 |
| `position` | INT | Y | 0 | 같은 날짜 화면에서 표시할 순서 |
| `completed_at` | DATETIME | N | `NULL` | 실제 완료 처리 시각 |
| `created_at` | DATETIME | Y | - | 생성 시각 |
| `updated_at` | DATETIME | Y | - | 수정 시각 |
| `deleted_at` | DATETIME | N | `NULL` | 소프트 삭제 시각 |

`status` 값:

| 값 | 의미 |
|---|---|
| `TODO` | 시작 전 |
| `IN_PROGRESS` | 진행 중 |
| `COMPLETED` | 완료 |
| `SKIPPED` | 해당 날짜에 수행하지 않고 건너뜀 |
| `CANCELLED` | 더 이상 수행하지 않도록 취소 |

`parent_item_id` 사용 예시:

```text
Spring 강의 듣기 (상위 작업)
├─ 1강 듣기
├─ 2강 듣기
└─ 예제 코드 작성
```

`workload`는 서로 다른 작업을 날짜별로 균형 있게 분배할 때 사용한다. 예를 들어 가벼운 작업은 1, 보통은 2, 무거운 작업은 3으로 정할 수 있다.

이 서비스에서는 **작업 한 건을 퍼즐 조각 한 개로 간주**하지만 `schedule_items`나 `schedules`에 퍼즐 개수를 중복 저장하지 않는다. 필요한 시점에 해당 `schedule_id`의 삭제되지 않은 작업 수를 조회한다.

```text
배정 퍼즐 수 = 유효한 schedule_items 행 개수
완료 퍼즐 수 = status가 COMPLETED인 유효한 schedule_items 행 개수
```

여기서 유효한 작업은 `deleted_at IS NULL`이고 `status != 'CANCELLED'`인 작업을 의미한다. `SKIPPED`를 배정 수에 포함할지는 통계 정책에서 일관되게 결정해야 한다.

### 4.4 `schedule_change_logs` — 스케줄 변경 이력

AI나 사용자가 스케줄을 바꿨을 때 변경 전·후 상태를 보존한다. 되돌리기, 감사 로그, AI 변경 이유 설명에 사용한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 변경 이력 ID, PK |
| `schedule_id` | BIGINT | N | 변경된 스케줄, `schedules.id` FK. 단독 작업 변경은 `NULL` |
| `schedule_item_id` | BIGINT | N | 특정 작업 변경이라면 해당 `schedule_items.id` |
| `actor_user_id` | BIGINT | N | 직접 변경한 회원. 자동 배치 변경이면 `NULL` 가능 |
| `action` | ENUM | Y | 변경 종류 |
| `source` | ENUM | Y | 변경 주체: 사용자, AI 또는 배치 |
| `version` | INT | Y | 변경 후 스케줄 버전 |
| `before_data` | JSON | N | 변경 전 값 또는 스냅샷 |
| `after_data` | JSON | N | 변경 후 값 또는 스냅샷 |
| `reason` | TEXT | N | 변경 사유 또는 AI 판단 설명 |
| `batch_job_id` | BIGINT | N | 배치 변경이면 관련 `batch_jobs.id` |
| `created_at` | DATETIME | Y | 변경 시각 |

`action`은 `CREATE`, `UPDATE`, `DELETE`, `RESTORE`, `RESCHEDULE` 중 하나다.

예시:

```json
{
  "before_data": {"scheduled_date": "2026-08-20"},
  "after_data": {"scheduled_date": "2026-08-21"},
  "reason": "8월 20일 미완료 작업을 다음 날로 재분배"
}
```

## 5. AI 대화

### 5.1 `conversations` — 대화방

사용자와 AI가 스케줄을 논의한 하나의 채팅방이다. 하나의 스케줄과 연결할 수도 있고, 일반 대화라면 `schedule_id`는 `NULL`일 수 있다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | CHAR(36) | Y | 대화방 UUID, PK |
| `user_id` | BIGINT | Y | 대화방 소유 회원, `users.id` FK |
| `schedule_id` | BIGINT | N | 대화로 관리하는 스케줄, `schedules.id` FK |
| `title` | VARCHAR(200) | N | 대화방 제목 |
| `status` | ENUM | Y | `ACTIVE` 또는 `ARCHIVED` |
| `last_message_at` | DATETIME | N | 마지막 메시지 생성 시각. 최근 대화 정렬에 사용 |
| `created_at` | DATETIME | Y | 대화방 생성 시각 |
| `updated_at` | DATETIME | Y | 대화방 정보 수정 시각 |
| `deleted_at` | DATETIME | N | 소프트 삭제 시각 |

### 5.2 `conversation_messages` — 대화 메시지

사용자 메시지, AI 응답, 시스템 지침, 도구 실행 결과를 메시지 한 건당 한 행으로 저장한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | CHAR(36) | Y | 메시지 UUID, PK |
| `conversation_id` | CHAR(36) | Y | 소속 대화방, `conversations.id` FK |
| `role` | ENUM | Y | 메시지 작성 주체 |
| `content` | LONGTEXT | N | 실제 메시지 본문 |
| `external_document_id` | VARCHAR(100) | N | 본문을 MongoDB에 저장할 때 사용하는 외부 문서 ID |
| `replaces_message_id` | CHAR(36) | N | 수정 전 원본 메시지의 `id` |
| `model_name` | VARCHAR(100) | N | 응답을 생성한 AI 모델명 |
| `prompt_tokens` | INT | N | AI 요청의 입력 토큰 수 |
| `completion_tokens` | INT | N | AI가 생성한 출력 토큰 수 |
| `created_at` | DATETIME | Y | 메시지 생성 시각 |
| `deleted_at` | DATETIME | N | 소프트 삭제 시각 |

`role` 값:

| 값 | 의미 |
|---|---|
| `SYSTEM` | AI 동작 규칙·시스템 지침 |
| `USER` | 사용자가 입력한 메시지 |
| `ASSISTANT` | AI가 생성한 답변 |
| `TOOL` | 스케줄 API 같은 내부 도구의 실행 결과 |

현재처럼 MySQL의 `content`에 대화 본문을 저장한다면 `external_document_id`는 사용하지 않아도 된다. 메시지를 수정할 때 기존 행을 덮어쓰지 않고 새로운 행을 만든 후 `replaces_message_id`로 원본을 연결하면 수정 이력을 보존할 수 있다.

## 6. 이미지

### 6.1 `images` — 이미지 메타데이터

이미지 바이너리 자체가 아니라 S3 같은 오브젝트 스토리지에 저장된 이미지의 위치와 정보를 관리한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 이미지 고유 ID, PK |
| `uploader_user_id` | BIGINT | Y | 업로드한 회원, `users.id` FK |
| `owner_type` | ENUM | Y | 이미지가 연결된 대상 종류 |
| `owner_id` | VARCHAR(100) | Y | 연결 대상의 ID. 타입에 따라 숫자 또는 UUID 문자열 |
| `storage_key` | VARCHAR(1000) | Y | 오브젝트 스토리지 내부 파일 경로. 중복 불가 |
| `original_filename` | VARCHAR(255) | N | 사용자가 업로드한 원본 파일명 |
| `content_type` | VARCHAR(100) | Y | MIME 타입. 예: `image/png` |
| `byte_size` | BIGINT | Y | 파일 크기, 바이트 단위 |
| `width` | INT | N | 이미지 너비, 픽셀 단위 |
| `height` | INT | N | 이미지 높이, 픽셀 단위 |
| `checksum` | VARCHAR(128) | N | 파일 무결성·중복 검사 해시값 |
| `created_at` | DATETIME | Y | 업로드 시각 |
| `deleted_at` | DATETIME | N | 소프트 삭제 시각 |

`owner_type` 값:

| 값 | `owner_id`가 가리키는 대상 |
|---|---|
| `USER` | `users.id` |
| `MESSAGE` | `conversation_messages.id` |
| `SCHEDULE` | `schedules.id` |
| `SCHEDULE_ITEM` | `schedule_items.id` |

`owner_id`는 여러 테이블을 가리키는 다형 관계이므로 DB 외래키를 설정할 수 없다. 대상 존재 여부는 애플리케이션이 검증해야 한다.

## 7. 통계·랭킹

### 7.1 `user_daily_metrics` — 사용자 일별 실적

랭킹 계산과 리스케줄링 판단을 빠르게 수행하기 위해 사용자별 하루 실적을 집계해 저장한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 일별 실적 ID, PK |
| `user_id` | BIGINT | Y | 대상 회원, `users.id` FK |
| `category_id` | BIGINT | N | 카테고리별 실적이면 `categories.id`, 전체 실적이면 `NULL` |
| `metric_date` | DATE | Y | 집계 기준일 |
| `planned_item_count` | INT | Y | 해당 날짜에 계획된 작업 수 |
| `completed_item_count` | INT | Y | 완료한 작업 수 |
| `planned_minutes` | INT | Y | 계획된 시간(분). 현재 날짜 기반 정책과 맞지 않는 잔여 컬럼 |
| `completed_minutes` | INT | Y | 완료한 시간(분). 현재 날짜 기반 정책과 맞지 않는 잔여 컬럼 |
| `puzzle_count` | INT | Y | 완료 작업 수와 동일한 완료 퍼즐 수. 랭킹 조회를 위한 집계값 |
| `achievement_rate` | DECIMAL(5,2) | Y | 달성률. 일반적으로 0.00~100.00 |
| `consecutive_days` | INT | Y | 연속 달성 일수 |
| `created_at` | DATETIME | Y | 집계 생성 시각 |
| `updated_at` | DATETIME | Y | 집계 수정 시각 |

작업 개수 기준 달성률 예시:

```text
achievement_rate = completed_item_count / planned_item_count * 100
```

현재 서비스가 시간을 사용하지 않으므로 `planned_minutes`, `completed_minutes`는 `planned_workload`, `completed_workload`로 변경하는 것을 권장한다.

### 7.2 `ranking_snapshots` — 랭킹 결과

매 조회 때 전체 데이터를 다시 계산하지 않도록 배치가 계산한 특정 날짜의 랭킹 결과를 저장한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 랭킹 결과 ID, PK |
| `ranking_date` | DATE | Y | 랭킹 기준일 |
| `scope` | ENUM | Y | `OVERALL`은 전체, `CATEGORY`는 카테고리별 랭킹 |
| `category_id` | BIGINT | N | 카테고리 랭킹일 때 `categories.id`; 전체 랭킹은 `NULL` |
| `user_id` | BIGINT | Y | 랭킹 대상 회원, `users.id` FK |
| `rank_no` | INT | Y | 해당 범위 내 순위 |
| `score` | DECIMAL(14,2) | Y | 랭킹 계산에 사용한 최종 점수 |
| `puzzle_count` | INT | Y | 랭킹 기간의 완료 작업 수와 동일한 완료 퍼즐 수 |
| `active_days` | INT | Y | 랭킹 기간 동안 활동·달성한 일수 |
| `achievement_rate` | DECIMAL(5,2) | Y | 랭킹 기간의 달성률 |
| `created_at` | DATETIME | Y | 스냅샷 생성 시각 |

`score` 계산 규칙은 ERD가 아니라 서비스 정책으로 별도 정의해야 한다. 예:

```text
score = 퍼즐 수 × 퍼즐 가중치 + 활동 일수 × 일수 가중치
```

## 8. 배치·자동 리스케줄링

### 8.1 `batch_jobs` — 배치 실행 기록

일별 통계, 랭킹, AI 리스케줄링 배치가 언제 실행되고 성공했는지 기록한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 배치 실행 ID, PK |
| `job_type` | ENUM | Y | 배치 종류 |
| `target_date` | DATE | Y | 처리 대상 날짜 |
| `status` | ENUM | Y | 실행 상태 |
| `started_at` | DATETIME | N | 실행 시작 시각 |
| `finished_at` | DATETIME | N | 실행 종료 시각 |
| `total_count` | INT | Y | 전체 처리 대상 수 |
| `success_count` | INT | Y | 성공한 대상 수 |
| `failure_count` | INT | Y | 실패한 대상 수 |
| `error_message` | TEXT | N | 작업 전체 실패 사유 |
| `created_at` | DATETIME | Y | 배치 기록 생성 시각 |

`job_type` 값:

| 값 | 의미 |
|---|---|
| `DAILY_METRICS` | 사용자 일별 달성률 집계 |
| `RANKING` | 전체·카테고리별 랭킹 계산 |
| `AI_RESCHEDULE` | 미완료 작업 AI 재분배 |

`status` 값:

| 값 | 의미 |
|---|---|
| `PENDING` | 실행 대기 |
| `RUNNING` | 실행 중 |
| `SUCCEEDED` | 전체 성공 |
| `PARTIALLY_SUCCEEDED` | 일부 성공·일부 실패 |
| `FAILED` | 전체 실패 |

같은 날짜의 같은 배치 종류는 한 번만 생성되도록 `(job_type, target_date)`에 유니크 제약조건이 있다.

### 8.2 `reschedule_results` — 사용자별 재분배 결과

하나의 AI 리스케줄링 배치 안에서 사용자·스케줄별 처리 결과를 저장한다. 재시도와 장애 분석에 사용한다.

| 컬럼 | 타입 | 필수 | 역할 |
|---|---|---:|---|
| `id` | BIGINT | Y | 재분배 결과 ID, PK |
| `batch_job_id` | BIGINT | Y | 관련 배치, `batch_jobs.id` FK |
| `user_id` | BIGINT | Y | 대상 회원, `users.id` FK |
| `schedule_id` | BIGINT | Y | 대상 스케줄, `schedules.id` FK |
| `achievement_rate` | DECIMAL(5,2) | N | 재분배 판단 시 사용한 달성률 |
| `status` | ENUM | Y | 사용자별 처리 상태 |
| `ai_request_id` | VARCHAR(100) | N | AI API 호출 추적 ID |
| `result_summary` | TEXT | N | 어떤 작업이 어느 날짜로 이동했는지 요약 |
| `error_message` | TEXT | N | 해당 사용자 처리 실패 사유 |
| `started_at` | DATETIME | N | 사용자별 처리 시작 시각 |
| `finished_at` | DATETIME | N | 사용자별 처리 종료 시각 |
| `created_at` | DATETIME | Y | 결과 기록 생성 시각 |

## 9. 핵심 관계

| 부모 | 관계 | 자식 | 설명 |
|---|---:|---|---|
| `users` | 1:N | `user_auth_accounts` | 한 회원은 여러 로그인 수단 연결 가능 |
| `users` | 1:N | `auth_sessions` | 여러 기기·브라우저 로그인 가능 |
| `users` | 1:1 | `user_preferences` | 회원별 AI 분배 설정 하나 |
| `users` | 1:N | `schedules` | 한 회원은 여러 스케줄 보유 가능 |
| `schedules` | 1:N | `schedule_items` | 스케줄은 여러 날짜별 작업으로 구성 |
| `schedule_items` | 1:N | `schedule_items` | 상위 작업과 하위 작업 구성 가능 |
| `schedules` | 1:N | `schedule_change_logs` | 스케줄 변경 이력 누적 |
| `users` | 1:N | `conversations` | 한 회원은 여러 AI 대화방 보유 가능 |
| `conversations` | 1:N | `conversation_messages` | 대화방에 메시지 누적 |
| `batch_jobs` | 1:N | `reschedule_results` | 한 배치가 여러 사용자 스케줄 처리 |

## 10. 대표 데이터 흐름

### 스케줄 생성

```text
사용자가 AI와 대화
→ conversations / conversation_messages 저장
→ schedules 생성
→ AI가 날짜별 schedule_items 생성
→ schedule_change_logs에 CREATE 기록
```

### 새벽 리스케줄링

```text
02:00~06:00 AI_RESCHEDULE 배치 시작
→ batch_jobs 생성
→ user_daily_metrics에서 달성률 확인
→ ai_reschedule_enabled 회원만 처리
→ 미완료 schedule_items의 scheduled_date 변경
→ schedule_change_logs에 RESCHEDULE 기록
→ reschedule_results에 사용자별 성공·실패 저장
```

## 11. 현재 스키마 정리 권장사항

명세 작성 과정에서 현재 정책과 맞지 않는 다음 항목이 확인되었다.

1. 대화 본문을 MySQL에 저장하므로 `conversation_messages.external_document_id`는 제거 가능하다.
2. 시간 기반 스케줄링을 하지 않으므로 `user_daily_metrics.planned_minutes`와 `completed_minutes`를 각각 `planned_workload`, `completed_workload`로 바꾸는 것이 적절하다.
3. 메시지 생성 시간이 같은 경우에도 순서를 보장하려면 `conversation_messages.sequence_no` 추가를 권장한다.
4. 대화 수정 이후 분기 흐름까지 보존하려면 `parent_message_id` 또는 `branch_id` 추가를 권장한다.
5. `achievement_rate`, `score`, `workload`의 계산·부여 규칙은 별도의 비즈니스 정책 문서로 확정해야 한다.
6. `schedule_items`에는 `puzzle_count`를 저장하지 않으며, 작업 한 건을 퍼즐 조각 한 개로 계산한다.
7. 스케줄별 퍼즐 조각 수는 저장하지 않고 `schedule_items` 원본 행을 `COUNT(*)`로 계산한다.
