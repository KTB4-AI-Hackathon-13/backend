---
name: spring-crud-impl
description: 해커톤 백엔드(Spring Boot 4.1 / Java 25)에서 실제 Java 코드를 작성·수정할 때 사용하는 구현 절차. API 명세를 받아 Entity → Repository → DTO → Service → Controller 순서로 도메인 폴더 아래 계층 서브패키지에 파일을 만들고, 매 도메인마다 컴파일을 확인한다. "구현해줘", "코드 짜줘", "엔티티 만들어줘", "API 만들어줘", "CRUD 붙여줘", "이 기능 추가해줘", "컨트롤러/서비스 수정해줘" 요청 시 사용. 기존 코드 수정·리팩터링·버그 수정에도 동일하게 적용한다.
---

# Spring CRUD 구현 절차

## 시작 전 필수

1. **`hackathon-code-style` 스킬을 먼저 읽는다.** 코드 수준 기준과 패키지 구조가 거기 있다.
   특히 `references/layer-templates.md` 의 템플릿을 그대로 따른다.
2. **`_workspace/01_spec.md` 명세를 읽는다.** 명세가 없으면 구현하지 말고,
   명세 담당(api-designer)에게 요청하거나 사용자에게 알린다.
3. **기존 코드를 먼저 본다.** 같은 도메인이 이미 있으면 새로 만들지 말고 그 파일을 수정한다.

## 구현 순서 (의존성 방향대로)

역순으로 만들면 컴파일이 안 되어 에러가 쌓인다. 반드시 이 순서로:

```
1. common/  (예외 + 핸들러 + ErrorResponse)  ← 프로젝트 최초 1회만
2. {domain}/entity/       Entity
3. {domain}/repository/   Repository
4. {domain}/dto/          Request / Response record
5. {domain}/service/      Service
6. {domain}/controller/   Controller
7. application.yaml       datasource 설정 (첫 엔티티 추가 시 필수)
```

**도메인이 여러 개면 한 도메인을 끝까지 만들고 컴파일한 뒤 다음 도메인으로 간다.**
여러 도메인을 동시에 반쯤 만들어 놓으면 컴파일 에러가 수십 개 쏟아져 원인을 못 찾는다.

## 각 단계에서 지킬 것

| 단계 | 핵심 |
|------|------|
| Entity | 세터 금지, `protected` 기본 생성자, 상태 변경은 의미 있는 메서드명 |
| Repository | `JpaRepository<T, Long>` 상속만. 파생 쿼리 이름이 4단어 넘으면 `@Query` JPQL |
| DTO | `record`. 요청엔 검증 애노테이션, 응답엔 `static from(Entity)` |
| Service | `@Transactional(readOnly = true)` 클래스 + 쓰기 메서드에만 `@Transactional`. DTO 반환 |
| Controller | 로직 없음. `@Valid` 붙이기. 201/204는 `ResponseEntity` |

## 매 도메인 완료 후 — 컴파일 확인

도메인 하나를 다 만들면 **즉시** 컴파일을 돌린다. 전부 만들고 한 번에 돌리지 않는다.

```bash
./gradlew compileJava --no-daemon -q
```

에러가 나면 그 자리에서 고친다. 자주 나오는 원인:

| 에러 | 원인 |
|------|------|
| `package ... does not exist` | package 선언이 폴더 경로와 불일치 |
| `cannot find symbol: class NotFoundException` | `common/exception` import 누락 |
| `constructor Xxx in class Xxx cannot be applied` | Entity 생성자 인자와 Service 호출 불일치 |
| `@Valid` 무시됨 / `NoClassDefFoundError: jakarta.validation` | `spring-boot-starter-validation` 의존성 없음 |

## 애플리케이션 기동 확인

컴파일이 통과했다고 뜨는 게 아니다. 첫 엔티티를 추가한 뒤에는 실제 기동을 확인한다.

```bash
./gradlew bootRun --no-daemon
```

기동 실패의 대부분은 datasource 미설정이다. `hackathon-code-style` 의
`references/layer-templates.md` 8번 섹션을 참고해 `application.yaml` 을 채운다.

기동 후 실제 호출로 확인:

```bash
curl -s -X POST http://localhost:8080/api/rankings \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"김철수","score":1200}' | head -20
curl -s http://localhost:8080/api/rankings | head -40
```

> `bootRun` 은 포그라운드로 붙잡히므로 백그라운드로 실행하고, 확인이 끝나면 반드시 종료하라.

## 명세와 어긋날 때

구현하다 보면 명세가 틀렸거나 부족한 게 드러난다. 이때:

1. **혼자 조용히 다르게 만들지 마라.** 그게 프론트 연동을 깨는 원인이다.
2. 명세 담당에게 알리고, 명세를 고친 뒤 구현한다.
3. 팀 모드가 아니라면 변경 사실을 `_workspace/02_impl_notes.md` 에 기록하고 사용자에게 보고한다.

## 기존 코드를 수정할 때

- 수정 범위를 먼저 좁힌다: 어떤 파일 몇 줄이 바뀌는지 말하고 시작한다
- 리팩터링과 기능 변경을 **한 번에 섞지 않는다**. 해커톤에서 이건 원인 추적을 불가능하게 만든다
- 동작하던 API의 응답 필드명을 바꿀 때는 반드시 명세와 프론트 영향 여부를 먼저 언급한다

## 구현 노트 산출

구현이 끝나면 `_workspace/02_impl_notes.md` 에 짧게 기록한다 (10줄 내외):

```markdown
# 구현 노트

## 생성/수정 파일
- ranking/entity/Ranking.java (신규)
- ...

## 명세와 다른 점
- 없음  (또는: score 필드를 int → long 으로. 이유: ...)

## 미구현 / TODO
- 없음

## 확인한 것
- ./gradlew compileJava 통과
- bootRun 기동 + POST/GET /api/rankings 200 확인
```

## 자가 점검

- [ ] 모든 파일이 `{도메인}/{계층}/` 경로에 있고 package 선언이 일치하는가?
- [ ] Entity에 세터가 없는가? 응답으로 Entity를 반환하는 곳이 없는가?
- [ ] Service의 쓰기 메서드에 `@Transactional` 이 붙어 있는가?
- [ ] 컴파일이 실제로 통과했는가? (추측하지 말고 돌려봤는가)
- [ ] 명세의 엔드포인트/필드명과 코드가 글자 단위로 일치하는가?
