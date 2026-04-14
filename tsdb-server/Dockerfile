FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY common/ common/
COPY tsdb-core/ tsdb-core/
COPY tsdb-server/ tsdb-server/
RUN chmod +x gradlew && ./gradlew :tsdb-server:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/tsdb-server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
