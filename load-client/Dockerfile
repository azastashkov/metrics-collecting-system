FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY common/ common/
COPY tsdb-core/ tsdb-core/
COPY load-client/ load-client/
RUN chmod +x gradlew && ./gradlew :load-client:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/load-client/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
