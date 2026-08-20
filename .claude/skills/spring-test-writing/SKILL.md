---
name: spring-test-writing
description: 해커톤 백엔드에 JUnit 5 테스트를 작성하거나 고칠 때 사용한다. Spring Boot 4.1의 스타터 이름(spring-boot-starter-webmvc-test, spring-boot-starter-data-jpa-test)과 H2 테스트 설정을 포함하며, 해커톤 범위에 맞게 핵심 경로만 얇게 테스트한다. "테스트 짜줘", "테스트 코드 추가", "테스트가 깨져", "./gradlew test 실패", "MockMvc", "@DataJpaTest", "@WebMvcTest", "CI 빌드 실패" 상황에서 사용. 테스트 수정·보완 요청에도 사용.
---

# 테스트 작성 (해커톤 범위)

## 테스트 철학 — 얇게, 그러나 진짜로

해커톤에서 커버리지 100%를 노리면 기능을 못 만든다. 반대로 테스트가 0이면
CI(`./gradlew build`)가 무엇도 지켜주지 못한다. **이 프로젝트의 CI는 PR마다 `./gradlew build` 를
돌리므로, 테스트가 깨지면 머지가 막힌다.**

목표는 이것이다:

| 테스트 | 목적 | 도메인당 개수 |
|--------|------|-------------|
| 컨텍스트 로딩 | 스프링이 뜨는가 (설정·빈 주입 오류 조기 발견) | 프로젝트당 1개 (이미 있음) |
| Controller 슬라이스 | 요청/응답 JSON 계약이 맞는가 | 2~4개 (성공 1~2 + 에러 1~2) |
| Service 단위 | 핵심 비즈니스 규칙 | 규칙이 있을 때만 1~2개 |
| Repository | 커스텀 `@Query` 가 실제로 도는가 | `@Query` 를 썼을 때만 1개 |

**단순 위임만 하는 코드는 테스트하지 않는다.** 테스트가 코드를 그대로 베낀 수준이면 지워라.

## 스타터 이름 주의

이 프로젝트는 Spring Boot 4.1이다. 익숙한 `spring-boot-starter-test` 하나가 아니라
용도별 스타터로 나뉘어 있고, `build.gradle` 에 이미 아래가 들어 있다:

```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
testRuntimeOnly 'com.h2database:h2'
```

없는 스타터를 import 하려다 컴파일이 깨지면, 먼저 `build.gradle` 을 확인하라.

## 테스트용 DB 설정

현재 상태(실측): `testRuntimeOnly com.h2database:h2` 덕분에 테스트에서는 **H2 인메모리 DB가
자동 구성**되며, 별도 설정 없이 `./gradlew build` 가 통과한다. 즉 테스트 설정 파일은 지금 당장 필수는 아니다.

다만 스키마를 매번 초기화해 테스트 간 간섭을 없애려면 테스트 전용 설정을 두는 편이 안전하다.
운영 설정과 테스트 설정을 섞으면 "로컬은 되는데 CI만 깨지는" 상황이 오므로, 만든다면 반드시
`src/test/resources/application.yaml` 로 분리한다 (운영 `application.yaml` 을 고치지 않는다).

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
```

`ddl-auto: create-drop` — 테스트마다 깨끗한 스키마에서 시작한다.

## Controller 슬라이스 테스트

`@WebMvcTest` 는 웹 계층만 띄운다 (DB 안 뜸 → 빠름). Service는 `@MockitoBean` 으로 대체한다.

```java
package hackathon.app.ranking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import hackathon.app.common.exception.NotFoundException;
import hackathon.app.ranking.dto.RankingCreateRequest;
import hackathon.app.ranking.dto.RankingResponse;
import hackathon.app.ranking.service.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RankingController.class)
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RankingService rankingService;

    @Test
    @DisplayName("랭킹 목록 조회 - 200과 배열을 반환한다")
    void findAll() throws Exception {
        RankingResponse response = new RankingResponse(1L, "김철수", 1200, LocalDateTime.now());
        given(rankingService.findAll()).willReturn(List.of(response));

        mockMvc.perform(get("/api/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nickname").value("김철수"))
                .andExpect(jsonPath("$[0].score").value(1200));
    }

    @Test
    @DisplayName("랭킹 등록 - 201과 생성된 리소스를 반환한다")
    void create() throws Exception {
        RankingCreateRequest request = new RankingCreateRequest("김철수", 1200);
        given(rankingService.create(any())).willReturn(
                new RankingResponse(1L, "김철수", 1200, LocalDateTime.now()));

        mockMvc.perform(post("/api/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("김철수"));
    }

    @Test
    @DisplayName("랭킹 등록 - nickname이 비면 400을 반환한다")
    void createWithBlankNickname() throws Exception {
        RankingCreateRequest request = new RankingCreateRequest("", 1200);

        mockMvc.perform(post("/api/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 랭킹 조회 - 404와 에러 메시지를 반환한다")
    void findByIdNotFound() throws Exception {
        given(rankingService.findById(anyLong()))
                .willThrow(new NotFoundException("Ranking을 찾을 수 없습니다: id=99"));

        mockMvc.perform(get("/api/rankings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}
```

> **`@MockBean` 은 Spring Boot 3.4부터 폐기(deprecated)되었고 4.x에서 제거되었다.**
> `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`) 을 써라.
> 컴파일이 안 되면 이 import부터 의심하라.

## Repository 테스트 (`@Query` 를 쓴 경우만)

```java
package hackathon.app.ranking.repository;

import hackathon.app.ranking.entity.Ranking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RankingRepositoryTest {

    @Autowired
    private RankingRepository rankingRepository;

    @Test
    @DisplayName("점수 내림차순으로 조회된다")
    void findAllByOrderByScoreDesc() {
        rankingRepository.save(new Ranking("김철수", 100));
        rankingRepository.save(new Ranking("이영희", 300));

        List<Ranking> result = rankingRepository.findAllByOrderByScoreDesc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNickname()).isEqualTo("이영희");
    }
}
```

## Service 단위 테스트 (비즈니스 규칙이 있을 때만)

스프링을 띄우지 않고 Mockito만 쓴다 — 가장 빠르다.

```java
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    @DisplayName("없는 id로 조회하면 NotFoundException이 발생한다")
    void findByIdNotFound() {
        given(rankingRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("id=99");
    }
}
```

## 작성 규칙

1. **`@DisplayName` 은 한글로, 무엇이 어떻게 되는지 쓴다.** 테스트가 깨졌을 때 이름만 보고 원인을 안다.
2. **한 테스트 = 한 가지 확인.** assertion을 10개 넣지 마라.
3. **`assertThat` 은 AssertJ 를 쓴다** (`org.assertj.core.api.Assertions.assertThat`).
4. **랜덤 값·현재 시각에 의존하지 마라.** CI에서만 깨지는 테스트가 된다.
5. **테스트를 통과시키려고 프로덕션 코드를 이상하게 바꾸지 마라.** 반대다.

## 실행 확인

```bash
./gradlew test --no-daemon
```

실패하면 리포트를 직접 열어 원인을 확인한다 (요약만 보고 추측하지 않는다):

```bash
./gradlew test --no-daemon 2>&1 | tail -40
cat build/reports/tests/test/index.html >/dev/null 2>&1 && echo "리포트: build/reports/tests/test/index.html"
```

CI와 동일한 명령으로 최종 확인:

```bash
./gradlew build --no-daemon
```

## 자가 점검

- [ ] `@MockitoBean` 을 썼는가? (`@MockBean` 은 4.x에서 제거됨)
- [ ] 테스트 설정을 추가했다면 `src/test/resources/` 에 넣었는가? (운영 yaml 을 건드리지 않았는가)
- [ ] 테스트가 실제로 통과하는 것을 **실행해서** 확인했는가?
- [ ] 위임만 하는 코드를 굳이 테스트하고 있지 않은가?
- [ ] `@DisplayName` 이 한글로 명확한가?
