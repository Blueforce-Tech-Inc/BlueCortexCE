# Cortex Community Edition Deployment Guide

> **Version**: 0.1.0-beta

This guide provides comprehensive instructions for deploying Cortex Community Edition in production environments.

[中文版](DEPLOYMENT-zh-CN.md)

## Table of Contents

- [1. System Requirements](#1-system-requirements)
- [2. Docker Deployment](#2-docker-deployment)
- [3. Production Configuration](#3-production-configuration)
- [4. Database Migration](#4-database-migration)
- [5. Environment Variables](#5-environment-variables)
- [6. Monitoring and Logging](#6-monitoring-and-logging)
- [7. Troubleshooting](#7-troubleshooting)

## 1. System Requirements

### Hardware Requirements

- **CPU**: 2+ cores
- **Memory**: 4GB+ RAM
- **Disk**: 20GB+ SSD

### Software Requirements

- **Java**: 17 or higher
- **PostgreSQL**: 16 with pgvector extension
- **Docker**: 20.10+ (for container deployment)
- **Docker Compose**: 2.0+ (optional)

## 2. Docker Deployment

### Using Docker Compose

```bash
# Clone the repository
git clone https://github.com/your-repo/cortexce.git
cd cortexce

# Start all services
docker-compose up -d
```

### Manual Docker Deployment

```bash
# Build the image
docker build -t cortexce:latest .

# Run the container
docker run -d \
  -p 37777:37777 \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  cortexce:latest
```

## 3. Production Configuration

### Database Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cortexce
    username: cortexce
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
```

### JVM Options

```bash
JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC"
```

## 4. Database Migration

### Run Migrations

```bash
# Using Flyway
mvn flyway:migrate

# Or manually
psql -U cortexce -d cortexce -f src/main/resources/db/migration/V1__initial_schema.sql
```

## 5. Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| POSTGRES_HOST | Database host | localhost |
| POSTGRES_PORT | Database port | 5432 |
| POSTGRES_DB | Database name | cortexce |
| POSTGRES_USER | Database user | cortexce |
| POSTGRES_PASSWORD | Database password | - |
| JWT_SECRET | JWT signing secret | - |
| API_RATE_LIMIT | API rate limit | 100 |

## 6. Monitoring and Logging

### Health Check

```
GET /actuator/health
```

### Metrics

```
GET /actuator/metrics
```

### Log Configuration

Configure logging levels in `application.yml`:

```yaml
logging:
  level:
    root: INFO
    com.ablueforce.cortexce: DEBUG
```

## 7. Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check PostgreSQL is running
   - Verify connection credentials
   - Check firewall settings

2. **Out of Memory**
   - Increase JVM heap size
   - Check memory usage with `/actuator/metrics`

3. **Slow Response**
   - Check database query performance
   - Review connection pool settings
   - Monitor CPU and memory usage

---

*See also: [Chinese Version](DEPLOYMENT-zh-CN.md)*
