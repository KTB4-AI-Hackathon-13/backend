# -----| 빌드 스테이지 |----- #
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# 셸 스크립트 실행
COPY gradlew ./
# gradle/ 폴더 안의 gradle-wrapper 사용 (jar, properties)
COPY gradle ./gradle
# settings.gradle 이랑 build.gradle 사용해서 의존성 다운로드
COPY settings.gradle build.gradle ./

# 실행 권한 주기
RUN chmod +x gradlew
# 의존성 설치 - 캐시 사용 위해
RUN ./gradlew dependencies --no-daemon

# /app/src에 main 만 COPY
COPY src/main ./src/main
# 테스트 빼고 빌드
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/app/.gradle \
    ./gradlew bootJar -x test --no-daemon

# -----| 실행 스테이지 |----- #
FROM eclipse-temurin:25-jre
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
