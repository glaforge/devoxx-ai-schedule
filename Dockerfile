# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Multi-stage Dockerfile for Devoxx AI Scheduler (Micronaut + Java 25)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY gradlew /workspace/
COPY gradle /workspace/gradle
COPY settings.gradle build.gradle gradle.properties /workspace/

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
