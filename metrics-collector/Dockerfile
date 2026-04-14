FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY common/ common/
COPY tsdb-core/ tsdb-core/
COPY metrics-collector/ metrics-collector/
RUN chmod +x gradlew && ./gradlew :metrics-collector:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/metrics-collector/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
