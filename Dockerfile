# syntax=docker/dockerfile:1.7

# ---- Build stage -------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Prime the wrapper and the buildscript classpath before the source is copied, so source changes
# do not invalidate the plugin-resolution layer.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain help

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --console=plain bootJar

# Normalize the artifact name and drop the plain jar that the Spring Boot plugin also produces.
RUN set -eux; \
    jars="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar')"; \
    test "$(printf '%s\n' "$jars" | wc -l)" -eq 1; \
    cp "$jars" /workspace/application.jar

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/application.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
