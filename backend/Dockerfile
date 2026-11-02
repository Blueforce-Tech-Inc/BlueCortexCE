# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml ./

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app

# Run as an unprivileged user: the worker reads tool traces and holds the LLM key,
# so it must not be able to write anywhere the image happened to leave world-writable.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/logs && chown -R app:app /app

# Copy the built jar from builder stage
COPY --from=builder --chown=app:app /app/target/cortex-ce-*.jar app.jar

# Default port (37777 — can be overridden by SERVER_PORT env var)
ENV SERVER_PORT=37777
# The container's own network namespace is the boundary, so it listens on all
# interfaces there; the host mapping in docker-compose is bound to 127.0.0.1.
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 37777

USER app

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- http://localhost:${SERVER_PORT}/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
