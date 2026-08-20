---
name: backend-qa-check
description: 해커톤 백엔드 구현 결과를 실제로 검증할 때 사용한다. ./gradlew build 실행은 물론, "컴파일은 되는데 실행하면 터지는" Spring 특유의 경계면 버그(파생 쿼리 메서드명 오타, datasource 누락, @Valid 누락, Entity 직접 반환, 명세와 응답 필드명 불일치)를 교차 비교로 잡는다. "검증해줘", "QA 해줘", "제대로 돌아가는지 확인", "빌드 되는지 봐줘", "버그 있나 봐줘", "명세대로 됐는지 확인", "CI 통과할까?" 요청 시 사용. 구현 직후 그리고 커밋/PR 직전에 사용한다.
---

# 백엔드 QA 검증

## 핵심 전제 — 빌드 통과는 증거가 아니다

Spring에서 가장 위험한 버그들은 **컴파일을 통과한다.**

| 버그 | 컴파일 | 실제 결과 |
|------|--------|----------|
| `findByNickName(...)` (Entity 필드는 `nickname`) | ✅ 통과 | **기동 실패** — 빈 생성 시점에 터짐 |
| `application.yaml` 에 datasource 없음 | ✅ 통과 | **기동 실패** |
| 컨트롤러에 `@Valid` 누락 | ✅ 통과 | 검증이 조용히 무시됨 → 잘못된 데이터가 DB로 |
| Service 쓰기 메서드에 `@Transactional` 누락 | ✅ 통과 | 더티 체킹 안 됨 → **수정이 저장 안 됨** |
| Entity를 응답으로 반환 + LAZY 연관 | ✅ 통과 | 500 `LazyInitializationException` |
| 명세는 `nickname`, DTO는 `nickName` | ✅ 통과 | 프론트 연동 실패 |
| `@Transactional` 안에서 예외를 잡아 삼킴 | ✅ 통과 | 커밋 시 **500** (rollback-only) |

따라서 QA는 **① 실행 검증 + ② 경계면 교차 비교** 두 축으로 한다.
"파일이 존재하는가"만 확인하는 QA는 아무 가치가 없다.

## 언제 도는가

**전부 끝난 뒤 한 번이 아니라, 도메인 하나가 끝날 때마다** 돈다.
버그는 쌓일수록 원인 분리가 어려워진다. 해커톤에서는 특히 그렇다.

## 1단계: 자동 검사 스크립트

정적으로 잡을 수 있는 항목은 스크립트가 먼저 훑는다.

```bash
bash .claude/skills/backend-qa-check/scripts/verify.sh
```

이 스크립트는 빌드 실행 + 아래 정적 규칙 위반을 리포트한다:
package 선언/경로 불일치, `@MockBean` 사용, Entity 반환 의심, `@Valid` 누락 의심,
`@Transactional` 누락 의심, `Map<String, Object>` 응답, datasource 미설정, 금지 아키텍처 흔적.

**스크립트 결과를 그대로 믿지 마라.** 스크립트는 의심 지점을 좁혀줄 뿐이고,
각 항목은 실제 파일을 열어 확인해야 한다 (특히 "의심" 항목은 오탐이 있다).

## 2단계: 경계면 교차 비교 (사람 대신 반드시 직접)

스크립트로 못 잡는 부분이다. **두 파일을 동시에 열어 대조**하는 것이 핵심이다.

### 2-1. 명세 ↔ 컨트롤러

`_workspace/01_spec.md` 의 엔드포인트 표와 `*/controller/*Controller.java` 를 나란히 놓고:

- [ ] Method + Path가 글자 단위로 일치하는가 (`/api/rankings` vs `/api/ranking` — 흔한 실수)
- [ ] 명세의 성공 상태 코드와 실제 반환이 같은가 (201인데 200을 반환하고 있지 않은가)
- [ ] 명세에 있는데 구현이 없는 엔드포인트, 구현에 있는데 명세에 없는 엔드포인트

### 2-2. 명세 JSON 예시 ↔ 응답 DTO 필드명

**프론트 연동을 깨는 1순위 원인.** 명세의 JSON 예시 키와 `*Response` record의 컴포넌트 이름을
하나씩 대조한다. 대소문자·복수형까지 본다 (`createdAt` vs `createAt`, `score` vs `scores`).

### 2-3. Entity 필드 ↔ Repository 파생 쿼리 메서드명

**기동 실패의 1순위 원인.** `findByXxxOrderByYyyDesc` 의 `Xxx`, `Yyy` 가 Entity에 그 이름 그대로
존재하는지 확인한다. 없으면 애플리케이션이 아예 뜨지 않는다.

`@Query` JPQL을 썼다면 JPQL 안의 엔티티명/필드명도 확인한다 (JPQL은 테이블명이 아니라 **엔티티 클래스명**을 쓴다).

### 2-4. Entity 필드 ↔ Response.from() 매핑

`from(Entity)` 에서 필드를 빠뜨리거나 순서를 바꿔 넣지 않았는지 본다.
record 생성자는 타입만 맞으면 순서가 바뀌어도 컴파일된다 — `nickname`과 다른 String 필드가
서로 뒤바뀌어도 컴파일러는 침묵한다.

### 2-5. 요청 DTO 검증 애노테이션 ↔ 컨트롤러 `@Valid`

요청 record에 `@NotBlank` 등이 있는데 컨트롤러 파라미터에 `@Valid` 가 없으면 검증은 **전혀 동작하지 않는다.**
쌍으로 확인한다.

### 2-6. Service 쓰기 메서드 ↔ `@Transactional`

`save`, `delete`, 그리고 **엔티티 상태를 바꾸는 메서드**(`updateScore` 등)에 `@Transactional` 이 있는지.
특히 더티 체킹에 의존하는 수정 메서드에 없으면 조용히 저장이 안 된다.

## 3단계: 실제 기동 + 호출 검증

정적 검사가 끝나면 실제로 띄운다. **이 단계를 생략한 QA는 완료가 아니다.**

```bash
./gradlew bootRun --no-daemon > /tmp/bootrun.log 2>&1 &
# 기동 대기 후
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/rankings
```

- 기동 로그에 `Started AppApplication` 이 있는가
- 명세의 주요 엔드포인트를 `curl` 로 실제 호출 — POST로 만들고 GET으로 되읽어 **왕복**을 확인한다
  (POST만 200이면 저장이 안 됐어도 통과해 버린다)
- 에러 케이스도 한 번: 없는 id 조회 → 404 + 팀 에러 바디 형태인가
- **데이터가 있는 상태와 없는 상태를 모두 호출하라.** 빈 테이블에서는 조기 반환 때문에
  통과하다가, 데이터가 들어온 뒤에야 터지는 버그가 있다 (실제로 발생했다)
- 로그인·비로그인·만료 세션을 **모두** 호출하라. 선택 인증 API 는 비로그인 경로가 따로 깨진다
- 확인 후 프로세스를 반드시 종료한다

## 리포트 작성

결과를 `_workspace/03_qa_report.md` 에 쓴다. **애매한 표현을 쓰지 마라.**

```markdown
# QA 리포트 (YYYY-MM-DD HH:MM)

## 실행 결과
| 검사 | 결과 | 비고 |
|------|------|------|
| ./gradlew build | ✅ 통과 | |
| 애플리케이션 기동 | ❌ 실패 | RankingRepository 빈 생성 실패 |
| API 왕복 호출 | ⏭️ 미실행 | 기동 실패로 진행 불가 |

## 발견된 문제

### 🔴 P1 - RankingRepository.findByNickName 메서드명 오타
- 파일: ranking/repository/RankingRepository.java:12
- 증상: 기동 시 PropertyReferenceException — No property 'nickName' found for type 'Ranking'
- 원인: Entity 필드는 nickname(소문자 n)인데 메서드는 NickName
- 수정: findByNickname 으로 변경

### 🟡 P2 - ...

## 문제 없음을 확인한 항목
- 명세 ↔ 컨트롤러 경로/메서드 5개 전부 일치
- 응답 DTO 필드명 ↔ 명세 JSON 예시 일치
```

**우선순위:** 🔴 P1 = 기동/빌드 실패 또는 API가 동작 안 함 / 🟡 P2 = 동작하지만 명세와 다름 /
🔵 P3 = 스타일·개선 제안.

## 지켜야 할 태도

1. **실행하지 않은 것을 "통과"라고 쓰지 마라.** 미실행은 `⏭️ 미실행`으로 정직하게 적는다.
2. **문제를 발견하면 파일:줄 + 증상 + 원인 + 수정안**을 함께 적는다. "확인 필요" 같은 말은 리포트가 아니다.
3. **QA는 고치지 않는다** — 구현 담당에게 넘긴다. 단, 오타 한 글자처럼 명백하고 위험이 없는 건 고치고 리포트에 적는다.
4. **문제가 없으면 없다고 명확히 쓴다.** 없는 문제를 만들어내지 마라.

## 자가 점검

- [ ] `./gradlew build` 를 **실제로 실행**했는가?
- [ ] 애플리케이션을 **실제로 띄워** 봤는가?
- [ ] 명세 파일과 컨트롤러를 **동시에 열어** 대조했는가?
- [ ] 리포트의 모든 ✅ 가 실행 근거를 가지고 있는가?
