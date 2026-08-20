---
name: spring-api-spec
description: 해커톤 백엔드에서 기능 요구사항을 구현 직전 단계의 API 명세로 바꿀 때 사용한다. 엔티티/필드/타입, REST 엔드포인트 표, 요청·응답 JSON 예시, 에러 케이스, 도메인별 파일 목록을 _workspace/01_spec.md 로 산출한다. "이런 기능 만들어줘", "API 설계해줘", "명세 짜줘", "엔티티 뭐 필요해?", "프론트에 넘길 API 문서" 요청 시 사용. 코드를 작성하기 전에 반드시 이 명세가 먼저 있어야 하며, 명세 수정·보완·재작성 요청에도 사용한다.
---

# API 명세 작성

## 왜 명세를 먼저 쓰는가

해커톤에서 가장 비싼 실수는 **백엔드와 프론트가 서로 다른 필드명을 믿고 각자 만드는 것**이다.
명세는 구현 전에 그 불일치를 15분 만에 잡아준다. 또한 명세가 있어야 구현 담당과 테스트 담당이
동시에 작업할 수 있다.

명세는 문서가 아니라 **계약**이다. 구현이 명세와 다르면 둘 중 하나를 반드시 고쳐야 한다.

## 작성 원칙

1. **구현 직전 수준까지 구체적으로.** 필드 타입, nullable 여부, HTTP 상태 코드가 다 정해져 있어야 한다.
   "적당히 유저 정보를 반환" 같은 문장은 명세가 아니다.
2. **JSON 예시는 실제 값으로.** `"string"` 이 아니라 `"김철수"` 처럼 진짜 값을 써라.
   프론트가 그대로 목 데이터로 쓸 수 있다.
3. **해커톤 범위로 자른다.** 요구사항에 없는 기능(페이징, 정렬 옵션, 소프트 삭제)을 상상해서 넣지 마라.
   필요해 보이면 "선택 사항" 섹션에 따로 적고 기본 명세에는 넣지 않는다.
4. **불확실한 것은 추측하지 말고 표시한다.** `⚠️ 확인 필요:` 로 시작하는 줄로 남기고,
   그 항목에 대해 합리적 기본값을 적어 진행한다 (멈추지 않는다).

## 명세 산출물 형식

`_workspace/01_spec.md` 에 아래 구조로 쓴다.

### 1. 기능 요약
2~3문장. 무엇을 만드는지, 사용자가 무엇을 할 수 있게 되는지.

### 2. 도메인 / 엔티티

도메인마다 표 하나:

```markdown
#### ranking (랭킹)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, auto | 식별자 |
| nickname | String | not null, max 50 | 표시 이름 |
| score | int | not null, >= 0 | 점수 |
| createdAt | LocalDateTime | not null | 생성 시각 |

연관관계: 없음  (또는: user (N:1, LAZY))
```

연관관계는 반드시 방향과 fetch 전략을 적는다. 양방향은 쓰지 않으므로 한 방향만 적힌다.

### 3. API 엔드포인트 표

```markdown
| Method | Path | 설명 | 요청 | 성공 응답 |
|--------|------|------|------|----------|
| GET | /api/rankings | 랭킹 전체 조회 (점수 내림차순) | - | 200 RankingResponse[] |
| GET | /api/rankings/{id} | 랭킹 단건 조회 | - | 200 RankingResponse |
| POST | /api/rankings | 랭킹 등록 | RankingCreateRequest | 201 RankingResponse |
| DELETE | /api/rankings/{id} | 랭킹 삭제 | - | 204 (본문 없음) |
```

**경로 규칙:** `/api/{도메인 복수형}` + `/{id}`. 동사를 경로에 넣지 않는다
(`/api/rankings/create` ❌). 상태 변경은 `PATCH /api/rankings/{id}/done` 처럼 하위 자원으로.

### 4. 요청 / 응답 스키마

DTO 마다 필드 표 + 실제 JSON 예시:

```markdown
#### RankingCreateRequest
| 필드 | 타입 | 필수 | 검증 |
|------|------|------|------|
| nickname | String | O | 공백 불가, 50자 이하 |
| score | int | O | 0 이상 |

요청 예시:
{ "nickname": "김철수", "score": 1200 }

#### RankingResponse
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 식별자 |
| nickname | String | 표시 이름 |
| score | int | 점수 |
| createdAt | String (ISO-8601) | 생성 시각 |

응답 예시:
{ "id": 1, "nickname": "김철수", "score": 1200, "createdAt": "2026-08-19T14:03:11" }
```

> `LocalDateTime` 은 JSON에서 ISO-8601 문자열로 나간다. 프론트가 파싱할 형식이므로 명세에 명시하라.

### 5. 에러 응답

모든 에러는 동일한 형태로 나간다 — 프론트가 한 번만 처리하면 된다.

```markdown
{ "message": "Ranking을 찾을 수 없습니다: id=99" }

| 상황 | 상태 코드 | message 예시 |
|------|----------|-------------|
| 리소스 없음 | 404 | Ranking을 찾을 수 없습니다: id=99 |
| 검증 실패 | 400 | nickname: nickname은 필수입니다 |
| 서버 오류 | 500 | 서버 오류가 발생했습니다 |
```

### 6. 생성할 파일 목록

구현 담당이 그대로 따라 만들 수 있도록 전체 경로를 적는다.
패키지 구조는 **도메인 폴더 → 계층 서브패키지** 규칙을 따른다 (`hackathon-code-style` 스킬 참조).

```markdown
src/main/java/hackathon/app/
├── common/exception/{NotFoundException,BadRequestException,GlobalExceptionHandler}.java
├── common/dto/ErrorResponse.java
└── ranking/
    ├── entity/Ranking.java
    ├── repository/RankingRepository.java
    ├── service/RankingService.java
    ├── controller/RankingController.java
    └── dto/{RankingCreateRequest,RankingResponse}.java

설정 변경: src/main/resources/application.yaml (datasource 추가 필요)
의존성 추가: spring-boot-starter-validation (Bean Validation 사용 시)
```

### 7. 확인 필요 / 선택 사항

`⚠️ 확인 필요:` 항목과, 범위 밖으로 미룬 기능을 나열한다.

## 명세를 고칠 때

이미 `_workspace/01_spec.md` 가 있고 수정 요청이 들어오면:
- 파일을 **새로 쓰지 말고 해당 섹션만 수정**한다
- 문서 하단 `## 변경 로그` 에 `- YYYY-MM-DD: 무엇을 왜 바꿈` 한 줄을 추가한다
- 이미 구현된 코드가 있다면, 명세 변경이 어떤 파일에 영향을 주는지 함께 적는다

## 필수 확인

명세를 내놓기 전 자가 점검:

- [ ] 모든 엔드포인트에 성공 상태 코드가 적혀 있는가?
- [ ] 모든 요청 DTO에 검증 규칙이 적혀 있는가?
- [ ] JSON 예시의 필드명이 DTO 필드 표와 정확히 일치하는가? (오타 하나가 프론트 연동을 막는다)
- [ ] 파일 목록 경로가 `도메인/계층/` 구조를 따르는가?
- [ ] 요구사항에 없는 기능을 임의로 추가하지 않았는가?
