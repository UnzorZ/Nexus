# syntax=docker/dockerfile:1
#
# Nexus API — production image (multi-stage).
# The BUILD CONTEXT MUST be the repository root: the build needs settings.gradle,
# the Gradle wrapper (gradlew + gradle/wrapper) and apps/api. See compose.prod.yaml.
#
# Result: a non-root JRE image running the Spring Boot fat jar on :8080, with a
# readiness healthcheck wired to /actuator/health/readiness.

# ---- build stage: compile + bootJar (skip tests; CI runs them separately) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle ./
COPY gradle ./gradle
COPY apps/api ./apps/api
RUN chmod +x gradlew \
    && ./gradlew :apps:nexus-api:bootJar -x test --no-daemon --console=plain

# ---- runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexus && adduser -S nexus -G nexus
WORKDIR /app
# bootJar emits nexus-api-<version>-SNAPSHOT.jar. The '-plain' classifier ends
# differently so this glob matches only the fat (runnable) jar.
COPY --from=build /workspace/apps/api/build/libs/nexus-api-*-SNAPSHOT.jar app.jar
USER nexus
EXPOSE 8080
ENV JAVA_OPTS=""
# wget ships with alpine (busybox) — sufficient for the readiness probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
