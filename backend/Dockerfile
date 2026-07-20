# syntax=docker/dockerfile:1

# --- build stage: compile and package the boot jar with the committed wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copy build definition first so dependency resolution is cached across source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version --no-daemon
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

# --- runtime stage: slim JRE with only the jar and a health probe ---
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
