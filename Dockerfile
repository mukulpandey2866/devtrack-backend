# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/devtrack-backend-*.jar extract --layers --destination /workspace/layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S devtrack && adduser -S -G devtrack devtrack
WORKDIR /app
COPY --from=build --chown=devtrack:devtrack /workspace/layers/dependencies/ ./
COPY --from=build --chown=devtrack:devtrack /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=devtrack:devtrack /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=devtrack:devtrack /workspace/layers/application/ ./

USER devtrack
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
EXPOSE 8080

# Liveness only: a database outage must not make the host restart-loop the container.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health/liveness" > /dev/null || exit 1

# exec so the JVM receives SIGTERM and Spring's graceful shutdown runs.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/devtrack-backend-0.0.1-SNAPSHOT.jar"]
