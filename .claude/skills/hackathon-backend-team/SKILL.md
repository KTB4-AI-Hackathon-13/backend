---
name: hackathon-backend-team
description: 해커톤 백엔드(Spring Boot) 기능 개발을 api-designer / backend-dev / test-writer / qa-verifier 4인 에이전트 팀으로 실행하는 오케스트레이터. "기능 만들어줘", "API 개발해줘", "이거 구현해줘", "랭킹 기능 붙여줘", "CRUD 만들어줘", "개발 시작하자" 같은 기능 개발 요청에 사용한다. 후속 작업에도 반드시 사용 — "다시 실행", "재실행", "수정해줘", "보완해줘", "이 부분만 다시", "QA만 다시 돌려줘", "명세 바꿔줘", "빌드 깨졌어", "이전 결과 개선" 등. 단순 질문이나 파일 한 줄 수정은 팀을 띄우지 말고 직접 처리한다.
---

# 해커톤 백엔드 개발 팀 오케스트레이터

## 이 팀의 목적

Spring Boot 백엔드 기능을 **명세 → 구현 → 테스트 → 검증** 순으로 만들되,
코드 수준을 **사람이 직접 디버깅할 수 있는 중간 수준**으로 유지한다.
추상화가 과하면 해커톤 중 버그를 못 찾고, 부족하면 수정할 때마다 다른 게 깨진다.

**실행 모드: 에이전트 팀** (4명). 팀원들이 `SendMessage` 로 직접 조율하고,
산출물은 `_workspace/` 파일로 주고받는다.

## 팀 구성

| 에이전트 | 역할 | 주 스킬 | 산출물 |
|---------|------|--------|--------|
| `api-designer` | 요구사항 → API 명세 확정 | `spring-api-spec` | `_workspace/01_spec.md` |
| `backend-dev` | Spring 코드 구현 | `spring-crud-impl` | `src/main/java/...`, `_workspace/02_impl_notes.md` |
| `test-writer` | JUnit 테스트 작성 | `spring-test-writing` | `src/test/java/...` |
| `qa-verifier` | 실행 검증 + 경계면 대조 | `backend-qa-check` | `_workspace/03_qa_report.md` |

**전원이 공유하는 스킬: `hackathon-code-style`** — 코드 수준 기준과 패키지 구조.
모든 팀원이 작업 전 이것을 읽어야 한다.

모든 `Agent` / `TeamCreate` 호출에 **`model: "opus"`** 를 명시한다.

## Phase 0: 컨텍스트 확인 (항상 먼저)

팀을 만들기 전에 지금이 어떤 실행인지 판별한다.

```bash
ls -la _workspace/ 2>/dev/null
ls src/main/java/hackathon/app/
```

| 상황 | 실행 모드 | 행동 |
|------|----------|------|
| `_workspace/` 없음 | **초기 실행** | Phase 1부터 전체 진행 |
| `_workspace/` 있음 + 부분 수정 요청 ("QA만 다시", "이 필드만 바꿔") | **부분 재실행** | 해당 에이전트만 호출. 아래 매트릭스 참조 |
| `_workspace/` 있음 + 새 기능 요청 | **증분 실행** | 기존 명세에 도메인을 **추가**한다 (덮어쓰지 않음) |
| `_workspace/` 있음 + 처음부터 다시 요청 | **새 실행** | `_workspace/` → `_workspace_prev/` 로 옮기고 초기 실행 |

**부분 재실행 매트릭스:**

| 요청 | 호출할 에이전트 |
|------|---------------|
| "명세 바꿔줘" / 필드·엔드포인트 변경 | api-designer → backend-dev → test-writer → qa-verifier (연쇄) |
| "이 버그 고쳐줘" / "빌드 깨졌어" | backend-dev → qa-verifier |
| "테스트 추가해줘" / "테스트가 깨져" | test-writer → qa-verifier |
| "QA만 다시 돌려줘" | qa-verifier 단독 |
| "코드가 너무 복잡해" / "이거 단순화해줘" | backend-dev 단독 (`hackathon-code-style` 기준 적용) |

## Phase 1: 요구사항 확인 및 범위 고정

1. 사용자 요구사항에서 **도메인 개수와 엔드포인트 대략 개수**를 뽑는다.
2. **범위가 크면 자른다.** 해커톤에서 한 번에 3개 도메인을 넘기면 완주하지 못한다.
   3개를 넘으면 우선순위를 물어보고 1차 범위를 확정한다.
3. `_workspace/` 디렉토리를 만든다.

```bash
mkdir -p _workspace
```

## Phase 2: 명세 (단독 실행 — 병렬 아님)

**api-designer 를 먼저 단독으로 돌린다.** 명세 없이 구현을 시작하면 팀 전체가
서로 다른 필드명을 믿고 작업하게 되어, 해커톤에서 가장 비싼 실패가 발생한다.

```
Agent(subagent_type: "api-designer", model: "opus",
      prompt: 요구사항 + "_workspace/01_spec.md 에 명세를 작성하라")
```

명세가 나오면 **사용자에게 요약을 보여주고 진행 확인**을 받는다
(엔드포인트 표 + `⚠️ 확인 필요` 항목). 여기서 5분 쓰면 나중에 1시간을 아낀다.

## Phase 3: 구현 + 테스트 (팀 실행)

명세가 확정되면 팀을 구성한다.

```
TeamCreate(team_name: "hackathon-backend",
           members: [backend-dev, test-writer, qa-verifier], model: "opus")
```

`TaskCreate` 로 **도메인 단위**로 작업을 쪼갠다. 계층 단위가 아니라 도메인 단위다 —
도메인 하나가 끝나야 실제로 동작하는 것이 하나 생긴다.

```
도메인마다:
  Task A: backend-dev  — {도메인} 구현 (entity→repository→dto→service→controller) + compileJava
  Task B: test-writer  — {도메인} 컨트롤러 슬라이스 테스트 (A 의존)
  Task C: qa-verifier  — {도메인} 검증 (A 의존, B와 병렬 가능)
```

**진행 규칙:**
- backend-dev 는 도메인 하나를 끝낼 때마다 qa-verifier 에게 통지한다 (전부 끝난 뒤가 아님)
- qa-verifier 가 🔴 P1 을 찾으면 리포트 완성을 기다리지 않고 즉시 backend-dev 에게 보낸다
- test-writer 는 **명세를 근거로** 테스트를 쓴다 (구현 코드를 베끼면 버그까지 굳는다)
- 응답 DTO 필드명 변경은 **api-designer + test-writer 양쪽에 통지** — 한쪽만 알면 그게 곧 버그다

## Phase 4: 최종 검증

팀 작업이 끝나면 리더가 CI와 동일한 명령으로 직접 확인한다.

```bash
bash .claude/skills/backend-qa-check/scripts/verify.sh
./gradlew build --no-daemon
```

`build` 가 통과해야 PR이 머지 가능하다 (`.github/workflows/CI.yml` 이 같은 명령을 돌린다).

실패하면 원인에 따라 backend-dev 또는 test-writer 를 재호출한다. **실패를 숨기고 완료 보고하지 않는다.**

## Phase 5: 보고 및 피드백

사용자에게 보고할 것:

1. 만들어진 엔드포인트 표 (Method / Path / 상태 코드)
2. `./gradlew build` 결과와 기동 확인 결과 — **실행한 근거와 함께**
3. 미해결 문제 / 미구현 항목 (있으면 숨기지 말고)
4. 프론트 담당에게 넘길 API 명세 위치 (`_workspace/01_spec.md`)

그리고 물어본다: **"코드 수준이 적당한가요? 더 단순하게 갈까요?"**
이 팀의 존재 이유가 코드 수준이므로, 피드백을 받으면
`hackathon-code-style` 스킬을 고치고 CLAUDE.md 변경 이력에 기록한다.

## 데이터 전달 규약

| 방식 | 용도 |
|------|------|
| 파일 (`_workspace/`) | 명세, 구현 노트, QA 리포트 — 다음 세션에서도 읽힌다 |
| `TaskCreate`/`TaskUpdate` | 도메인별 진행 상태, 의존 관계 |
| `SendMessage` | 즉시 알려야 하는 것 — P1 버그, 필드명 변경, 명세 위반 |

파일명 규약: `_workspace/{순번}_{산출물}.md`. `_workspace/` 는 사후 추적용으로 **지우지 않는다**.

> `_workspace/` 를 커밋에 포함할지는 팀 규칙에 따른다. 포함하지 않으려면 `.gitignore` 에 추가하되,
> 사용자에게 먼저 확인한다.

## 에러 핸들링

| 상황 | 대응 |
|------|------|
| 명세를 쓸 수 없을 만큼 요구사항이 막연함 | api-designer 가 3개 이내 질문으로 정리 → 사용자에게 확인 |
| 컴파일 에러 3회 시도 후에도 미해결 | 에러 전문 + 시도 내역과 함께 사용자에게 보고. 혼자 붙잡지 않는다 |
| 애플리케이션 기동 실패 | 로그의 **첫 번째** `Caused by` 를 근본 원인으로 본다 (마지막 줄 아님) |
| 명세 ↔ 구현 충돌 | 구현을 조용히 바꾸지 말고 api-designer 가 판정 → 명세 갱신 → 재구현 |
| 한 도메인이 막힘 | 그 도메인만 남기고 **나머지 도메인은 완주**한다. 막힌 것을 명시적으로 보고 |
| 의존성 추가가 필요 | 임의 추가 금지. 사유와 함께 사용자 확인 (CI 실패의 최다 원인) |
| 팀원 응답 없음 | 1회 재시도 → 재실패 시 해당 산출물 없이 진행하고 **보고서에 누락을 명시** |

## 하지 않는 것

- **커밋·푸시·PR 생성은 사용자가 명시적으로 요청할 때만.** 자동으로 하지 않는다
- DB 접속 정보, 시크릿을 코드나 yaml에 하드코딩하지 않는다
- 요구사항에 없는 기능(인증, 페이징, 소프트 삭제)을 임의로 추가하지 않는다
- 배포(CD) 관련 파일(`Dockerfile`, `docker-compose.yml`, `.github/workflows/`)은
  사용자가 요청하지 않는 한 건드리지 않는다 — 이미 동작 중인 파이프라인이다

## 테스트 시나리오

**정상 흐름:** "랭킹 등록/조회 API 만들어줘"
→ Phase 0: `_workspace/` 없음 → 초기 실행
→ Phase 2: api-designer 가 `ranking` 도메인 + 4개 엔드포인트 명세 작성, 사용자 확인
→ Phase 3: backend-dev 가 `ranking/{entity,repository,dto,service,controller}` 생성 + `application.yaml` datasource 추가,
  compileJava 통과 → qa-verifier 통지 → test-writer 가 컨트롤러 슬라이스 3개 작성
→ Phase 4: `verify.sh` + `./gradlew build` 통과, 기동 후 POST→GET 왕복 200 확인
→ Phase 5: 엔드포인트 표 + 명세 경로 보고, 코드 수준 피드백 요청

**에러 흐름:** 구현 후 애플리케이션 기동 실패
→ qa-verifier 가 `verify.sh` 로 "파생 쿼리 `findByNickName` 의 `NickName` 이 엔티티 필드에 없음" 검출
→ 리포트 완성 전 `SendMessage` 로 backend-dev 에 즉시 통지 (🔴 P1)
→ backend-dev 가 `findByNickname` 으로 수정 → compileJava → qa-verifier 재검증
→ 기동 성공, 왕복 호출 200 → Phase 4 진행
→ 리포트에 해결/미해결 표로 기록
