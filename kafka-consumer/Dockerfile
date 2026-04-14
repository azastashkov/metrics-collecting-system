FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY common/ common/
COPY tsdb-core/ tsdb-core/
COPY kafka-consumer/ kafka-consumer/
RUN chmod +x gradlew && ./gradlew :kafka-consumer:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/kafka-consumer/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
