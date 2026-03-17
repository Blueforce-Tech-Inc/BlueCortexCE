# =============================================================================
# Cortex Community Edition Dockerfile
# =============================================================================
# Multi-stage build for optimized image size
# 
# Build: 
#   1. Run: ./scripts/prebuild-webui.sh  (prepare WebUI resources)
#   2. Run: docker build -t cortex-ce:latest .
#
# Run:   docker run -p 37777:37777 cortex-ce:latest
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build Java Application
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS java-builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn ./.mvn
COPY backend/src ./src

# Build the application (WebUI should be pre-built via prebuild-webui.sh)
RUN ./mvnw clean package -DskipTests -q

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# Create non-root user for security
RUN groupadd -r cortexce && useradd -r -g cortexce cortexce

WORKDIR /app

# Copy the built artifact from builder stage
COPY --from=java-builder /app/target/cortex-ce-*.jar app.jar

# Create log directories
RUN mkdir -p /app/logs /home/cortexce/.cortexce/logs && \
    chown -R cortexce:cortexce /app /home/cortexce

# Switch to non-root user
USER cortexce

# Expose the application port
EXPOSE 37777

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:37777/actuator/health || exit 1

# JVM options for production
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError"

# Default command
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
