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

# Copy the built jar from builder stage
COPY --from=builder /app/target/cortex-ce-*.jar app.jar

# Default port (can be overridden by SERVER_PORT env var)
ENV SERVER_PORT=8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- http://localhost:${SERVER_PORT}/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
