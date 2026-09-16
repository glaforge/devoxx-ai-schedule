# Multi-stage Dockerfile for Devoxx AI Scheduler (Micronaut + Java 25)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY gradlew /workspace/
COPY gradle /workspace/gradle
COPY settings.gradle build.gradle /workspace/

# Copy source code and resources
COPY src /workspace/src

# Build the standalone shadow JAR
RUN ./gradlew shadowJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the standalone executable fat JAR
COPY --from=builder /workspace/build/libs/dvxaisched-0.1-all.jar /app/application.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
