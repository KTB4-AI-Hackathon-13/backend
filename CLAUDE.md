# app — KTB 해커톤 백엔드

Spring Boot 4.1.0 / Java 25 / JPA + WebMVC. 패키지 루트 `hackathon.app`.
빌드·배포 파이프라인(CI/CD, ECR, docker-compose)은 이미 구성되어 있다.

## 하네스: 해커톤 백엔드 개발

**목표:** 기능을 빠르게 만들되, 코드 수준을 **사람이 직접 디버깅할 수 있는 중간 수준**으로 유지한다.
과한 추상화(헥사고날, ServiceImpl, MapStruct, AOP)도, 원시적인 코드(Controller에 로직, Map 응답)도 쓰지 않는다.

**트리거:**
- 기능 개발·구현·수정 요청 시 `hackathon-backend-team` 스킬을 사용하라
  ("기능 만들어줘", "API 구현", "빌드 깨졌어", "다시 실행", "QA만 다시" 등 후속 요청 포함).
- Java/Spring 파일을 **한 줄이라도** 쓰거나 고치기 전에는 `hackathon-code-style` 스킬을 먼저 읽어라.
  팀을 띄우지 않는 단순 수정에도 적용된다.
- 단순 질문, 파일 조회는 팀을 띄우지 말고 직접 응답하라.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-08-19 | 초기 구성 (4인 팀 + 스킬 6개) | 전체 | - |
| 2026-08-19 | 패키지 구조를 도메인 우선(`{도메인}/{계층}/`)으로 고정 | hackathon-code-style | 사용자 요청 |
| 2026-08-19 | 패키지 구조를 팀 실제 구조(헥사고날: domain/application/infrastructure/presentation)로 정정, 팀 공통 규약(ApiResponse/ErrorCode/세션/ddl-auto:validate) 추가, 남의 파일 수정 금지 규칙 명문화 | hackathon-code-style, backend-qa-check/verify.sh | origin/dev 의 기존 팀 코드와 충돌 발견 |
| 2026-08-19 | 패키지 구조 재확정: 내 코드는 `{도메인}/{entity,enums,repository,service,controller,dto/{response,request}}`, Repository 포트·어댑터 금지. 팀 레거시(헥사고날)는 읽기 전용 | hackathon-code-style, verify.sh | 사용자 지시 (복잡한 구조 배제) |
| 2026-08-19 | 엔티티 게터에 Lombok `@Getter` 허용(`@Data`/`@Builder`/`@Setter` 는 계속 금지) | hackathon-code-style | 사용자가 build.gradle 에 annotationProcessor 추가 |
| 2026-08-20 | 트랜잭션 rollback-only 함정 + 데이터 유무 양쪽 QA 규칙 추가 | hackathon-code-style, backend-qa-check | 랭킹 API 비로그인 500 버그를 실제 curl 로 발견 |
