FROM eclipse-temurin:25-jre
WORKDIR /app
# CI가 gradle로 빌드한 부트 jar를 복사 (plain jar는 -SNAPSHOT.jar에 안 걸림)
COPY build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
