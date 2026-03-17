# Cortex Community Edition 部署指南

> **English Version**: [DEPLOYMENT.md](DEPLOYMENT.md)

本文档详细说明 Claude-Mem Java 后端的生产环境部署方案，包括 Docker 部署、配置管理、监控和故障排除。

## 目录

- [1. 系统要求](#1-系统要求)
- [2. Docker 部署方案](#2-docker-部署方案)
- [3. 生产环境配置](#3-生产环境配置)
- [4. 数据库迁移](#4-数据库迁移)
- [5. 环境变量说明](#5-环境变量说明)
- [6. 监控和日志](#6-监控和日志)
- [7. 故障排除](#7-故障排除)
- [8. 备份与恢复](#8-备份与恢复)

---

## 1. 系统要求

### 1.1 硬件要求

| 资源 | 最低要求 | 推荐配置 |
|------|---------|---------|
| CPU | 2 cores | 4+ cores |
| 内存 | 4 GB | 8+ GB |
| 磁盘 | 20 GB | 50+ GB SSD |
| 网络 | 100 Mbps | 1 Gbps |

### 1.2 软件要求

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| Docker | ≥ 24.0 | 容器运行时 |
| Docker Compose | ≥ 2.20 | 容器编排 |
| PostgreSQL | 16 + pgvector 0.8.1 | 数据库（Docker 部署自动包含） |

### 1.3 端口要求

| 端口 | 服务 | 说明 |
|------|------|------|
| 37777 | Claude-Mem Java | HTTP API 服务 |
| 5433 | PostgreSQL | 数据库服务（可配置） |

---

## 2. Docker 部署方案

### 2.1 快速启动（推荐）

使用 Docker Compose 一键部署：

```bash
# 1. 克隆仓库
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd claude-mem-java

# 2. 创建环境配置文件
cat > .env << 'EOF'
# 数据库配置
DB_NAME=claude_mem
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password_here

# LLM 配置（OpenAI 兼容 API）
SPRING_AI_OPENAI_API_KEY=your_api_key_here
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# 嵌入模型配置
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_api_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024

# 运行配置
SPRING_PROFILES_ACTIVE=prd
SERVER_PORT=37777
EOF

# 3. 启动服务
docker compose up -d

# 4. 查看日志
docker compose logs -f claude-mem

# 5. 健康检查
curl http://localhost:37777/actuator/health
```

### 2.2 使用预构建镜像

从 GitHub Container Registry 拉取预构建镜像：

```bash
# 拉取最新版本
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main

# 拉取特定版本
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:v0.1.0
```

### 2.3 自定义构建

从源码构建镜像：

```bash
# 构建镜像
docker build -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main -f java/Dockerfile .

# 带构建参数
docker build \
  -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main \
  --build-arg JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=80.0" \
  -f java/Dockerfile .
```

### 2.4 Docker Compose 服务说明

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: claude-mem-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-claude_mem}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Database password required}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres}"]
      interval: 5s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  claude-mem:
    image: ${IMAGE_NAME:-ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main}
    container_name: claude-mem-java
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # 数据库配置
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${DB_NAME:-claude_mem}
      SPRING_DATASOURCE_USERNAME: ${DB_USERNAME:-postgres}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:?Database password required}

      # 服务配置
      SERVER_PORT: 37777
      SERVER_ADDRESS: 0.0.0.0
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prd}

      # LLM 配置
      SPRING_AI_OPENAI_API_KEY: ${SPRING_AI_OPENAI_API_KEY}
      SPRING_AI_OPENAI_BASE_URL: ${SPRING_AI_OPENAI_BASE_URL}
      SPRING_AI_OPENAI_CHAT_MODEL: ${SPRING_AI_OPENAI_CHAT_MODEL}

      # 嵌入配置
      SPRING_AI_OPENAI_EMBEDDING_API_KEY: ${SPRING_AI_OPENAI_EMBEDDING_API_KEY}
      SPRING_AI_OPENAI_EMBEDDING_BASE_URL: ${SPRING_AI_OPENAI_EMBEDDING_BASE_URL}
      SPRING_AI_OPENAI_EMBEDDING_MODEL: ${SPRING_AI_OPENAI_EMBEDDING_MODEL}
      SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS: ${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS}

      # JVM 配置
      JAVA_OPTS: ${JAVA_OPTS:--XX:+UseZGC -XX:MaxRAMPercentage=75.0}
    volumes:
      - claude-mem-logs:/app/logs
    ports:
      - "${SERVER_PORT:-37777}:37777"
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:37777/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

volumes:
  postgres_data:
  claude-mem-logs:
```

---

## 3. 生产环境配置

### 3.1 JVM 配置

推荐的生产环境 JVM 参数：

```bash
# ZGC 垃圾回收器（低延迟）
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

# 或 G1GC 垃圾回收器（吞吐量优先）
JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitiatingHeapOccupancyPercent=45"

# 完整配置示例
JAVA_OPTS="-XX:+UseZGC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heap_dump.hprof \
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m"
```

### 3.2 数据库连接池配置

在 `application.yml` 中配置（默认值已优化）：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 25        # 最大连接数
      minimum-idle: 5              # 最小空闲连接
      connection-timeout: 10000    # 连接超时（毫秒）
      idle-timeout: 300000         # 空闲超时（5分钟）
      max-lifetime: 1680000        # 连接最大生命周期（28分钟）
```

### 3.3 安全配置

#### 3.3.1 网络安全

```bash
# 仅允许本地访问（默认）
SERVER_ADDRESS=127.0.0.1

# 允许所有接口访问（需要配置防火墙）
SERVER_ADDRESS=0.0.0.0
```

#### 3.3.2 数据库安全

```bash
# 使用强密码
DB_PASSWORD=$(openssl rand -base64 32)

# 限制数据库访问
# 在 docker-compose.yml 中移除端口映射，仅保留内部网络
# ports:
#   - "5433:5432"  # 移除此行
```

#### 3.3.3 API 密钥管理

```bash
# 使用环境变量（推荐）
export SPRING_AI_OPENAI_API_KEY="sk-xxx"

# 或使用 Docker Secrets
docker secret create openai_api_key ./secrets/openai_key.txt
```

### 3.4 性能优化

#### 3.4.1 应用层优化

```yaml
# application-prd.yml
spring:
  threads:
    virtual:
      enabled: true              # 启用虚拟线程

  jpa:
    hibernate:
      ddl-auto: none            # 生产环境禁用自动 DDL

  flyway:
    enabled: true               # 启用数据库迁移
    locations: classpath:db/migration
```

#### 3.4.2 数据库优化

```sql
-- 连接到 PostgreSQL 执行优化配置
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '768MB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;
ALTER SYSTEM SET work_mem = '2621kB';
ALTER SYSTEM SET min_wal_size = '1GB';
ALTER SYSTEM SET max_wal_size = '4GB';

-- 重启数据库生效
SELECT pg_reload_conf();
```

---

## 4. 数据库迁移

### 4.1 迁移策略

Claude-Mem 使用 **Flyway** 进行数据库版本管理：

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__init_schema.sql` | 初始化表结构（5 张核心表） |
| V2 | `V2__multi_dimension_embeddings.sql` | 多维嵌入支持 |
| V3 | `V3__add_skipped_status.sql` | 添加 skipped 状态 |
| V4 | `V4__context_caching.sql` | 上下文缓存 |
| V5 | `V5__user_prompt_project.sql` | 用户提示项目关联 |
| V6 | `V6__pending_message_hash.sql` | 消息去重 |
| V7 | `V7__remove_embedding_3072.sql` | 移除 3072 维嵌入 |
| V8 | `V8__add_observation_content_hash.sql` | 内容哈希索引 |

### 4.2 迁移执行

#### 自动迁移（推荐）

应用启动时自动执行迁移：

```bash
# Flyway 配置（application.yml）
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

#### 手动迁移

```bash
# 查看迁移状态
docker exec claude-mem-java java -jar app.jar --spring.flyway.info=true

# 手动触发迁移
docker exec claude-mem-java java -jar app.jar --spring.flyway.migrate=true
```

### 4.3 迁移验证

```bash
# 连接数据库
docker exec -it claude-mem-postgres psql -U postgres -d claude_mem

# 查看迁移历史
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# 验证表结构
\dt mem_*

# 验证索引
\di mem_*
```

### 4.4 回滚策略

Flyway 社区版不支持自动回滚，建议策略：

```bash
# 1. 备份数据库
docker exec claude-mem-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d).sql

# 2. 手动回滚 SQL
psql -U postgres -d claude_mem < rollback_V8.sql

# 3. 更新 Flyway 历史记录
DELETE FROM flyway_schema_history WHERE version = '8';
```

---

## 5. 环境变量说明

### 5.1 核心配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | 否 | `dev` | 环境配置（dev/prd） |
| `SERVER_PORT` | 否 | `37777` | HTTP 服务端口 |
| `SERVER_ADDRESS` | 否 | `127.0.0.1` | 监听地址 |

### 5.2 数据库配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_DATASOURCE_URL` | 是 | - | 数据库连接 URL |
| `SPRING_DATASOURCE_USERNAME` | 是 | `postgres` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | **是** | - | 数据库密码 |
| `DB_NAME` | 否 | `claude_mem` | 数据库名称 |
| `DB_USERNAME` | 否 | `postgres` | 数据库用户名（Docker Compose） |
| `DB_PASSWORD` | **是** | - | 数据库密码（Docker Compose） |

### 5.3 LLM 配置

#### OpenAI 兼容 API（DeepSeek、Moonshot 等）

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_AI_OPENAI_API_KEY` | **是** | - | OpenAI API 密钥 |
| `SPRING_AI_OPENAI_BASE_URL` | 否 | `https://api.openai.com` | API 基础 URL |
| `SPRING_AI_OPENAI_CHAT_MODEL` | 否 | `gpt-4o` | 聊天模型名称 |

#### Anthropic 兼容 API（Claude、GLM 等）

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `ANTHROPIC_API_KEY` | **是** | - | Anthropic API 密钥 |
| `ANTHROPIC_BASE_URL` | 否 | `https://api.anthropic.com` | API 基础 URL |
| `ANTHROPIC_MODEL` | 否 | `claude-sonnet-4-20250514` | 模型名称 |
| `CLAUDEMEM_LLM_PROVIDER` | 否 | `openai` | LLM 提供商（openai/anthropic） |

### 5.4 嵌入模型配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | **是** | - | 嵌入 API 密钥 |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | 否 | `https://api.openai.com` | 嵌入 API URL |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 否 | `text-embedding-3-small` | 嵌入模型名称 |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | 否 | `1536` | 嵌入维度（768/1024/1536） |

### 5.5 运行时配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `CLAUDE_MEM_MODE` | 否 | `code` | 记忆模式（code/default） |
| `CLAUDEMEM_LOG_DIR` | 否 | `~/.claude-mem/logs` | 日志目录 |
| `JAVA_OPTS` | 否 | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` | JVM 参数 |

### 5.6 配置示例

#### 开发环境

```bash
# .env.dev
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=127.0.0.1

# DeepSeek LLM
SPRING_AI_OPENAI_API_KEY=sk-xxx
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# SiliconFlow Embedding
SPRING_AI_OPENAI_EMBEDDING_API_KEY=sk-xxx
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024

# Database
DB_PASSWORD=dev_password_123
```

#### 生产环境

```bash
# .env.prd
SPRING_PROFILES_ACTIVE=prd
SERVER_ADDRESS=0.0.0.0

# Anthropic LLM
ANTHROPIC_API_KEY=sk-ant-xxx
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_MODEL=claude-sonnet-4-20250514
CLAUDEMEM_LLM_PROVIDER=anthropic

# Database
DB_PASSWORD=$(openssl rand -base64 32)

# JVM
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError"
```

---

## 6. 监控和日志

### 6.1 健康检查

#### Spring Boot Actuator

```bash
# 健康状态
curl http://localhost:37777/actuator/health

# 详细信息
curl http://localhost:37777/actuator/health | jq

# 响应示例
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "staleMessageQueue": {
      "status": "UP",
      "details": {
        "staleCount": 0,
        "threshold": 5
      }
    }
  }
}
```

#### 健康检查端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 综合健康状态 |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标数据 |

### 6.2 应用日志

#### 日志配置

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.claudemem: INFO
    org.springframework.web: WARN
    org.hibernate.SQL: WARN
  file:
    name: ${CLAUDEMEM_LOG_DIR}/claude-mem.log
    max-size: 10MB
    max-history: 30
```

#### 日志查看

```bash
# Docker 日志
docker compose logs -f claude-mem

# 实时跟踪日志
docker compose logs -f --tail=100 claude-mem

# 日志文件
docker exec claude-mem-java tail -f /app/logs/claude-mem.log

# 搜索日志
docker exec claude-mem-java grep "ERROR" /app/logs/claude-mem.log
```

#### 结构化日志

Claude-Mem 使用结构化日志格式（JSON）：

```json
{
  "timestamp": "2026-03-13T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.claudemem.server.service.AgentService",
  "message": "Observation saved successfully",
  "context": {
    "sessionId": "abc-123",
    "observationId": "xyz-789",
    "project": "/path/to/project"
  }
}
```

### 6.3 Prometheus 监控（可选）

#### 添加依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

#### 配置端点

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'claude-mem-java'
    static_configs:
      - targets: ['localhost:37777']
    metrics_path: '/actuator/prometheus'
```

### 6.4 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `jvm.memory.used.percent` | JVM 内存使用率 | > 85% |
| `process.cpu.usage` | CPU 使用率 | > 80% |
| `hikaricp.connections.active` | 活跃数据库连接数 | > 20 |
| `http.server.requests` | HTTP 请求延迟 | P99 > 1s |
| `stale.message.queue.count` | 僵死消息数量 | > 5 |

---

## 7. 故障排除

### 7.1 常见问题

#### 7.1.1 服务无法启动

**症状**：容器启动失败或立即退出

**诊断步骤**：

```bash
# 1. 查看容器日志
docker compose logs claude-mem

# 2. 检查配置文件
docker compose config

# 3. 验证环境变量
docker compose exec claude-mem env | grep SPRING

# 4. 检查依赖服务
docker compose ps
```

**常见原因**：

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| `Connection refused` | 数据库未就绪 | 等待数据库健康检查通过 |
| `Authentication failed` | 数据库密码错误 | 检查 `DB_PASSWORD` |
| `Port 37777 already in use` | 端口冲突 | 修改 `SERVER_PORT` 或停止冲突服务 |

#### 7.1.2 数据库连接失败

**症状**：应用日志显示数据库连接错误

**诊断步骤**：

```bash
# 1. 检查数据库状态
docker compose exec postgres pg_isready -U postgres

# 2. 测试连接
docker compose exec postgres psql -U postgres -d claude_mem -c "SELECT 1"

# 3. 检查网络
docker compose exec claude-mem ping postgres

# 4. 查看连接池状态
curl http://localhost:37777/actuator/health | jq '.components.db'
```

**解决方案**：

```bash
# 重启数据库
docker compose restart postgres

# 检查连接池配置
# application.yml - 调整 hikari.maximum-pool-size
```

#### 7.1.3 内存溢出

**症状**：服务崩溃，日志显示 `OutOfMemoryError`

**诊断步骤**：

```bash
# 1. 查看内存使用
docker stats claude-mem-java

# 2. 检查堆转储
docker exec claude-mem-java ls -lh /app/logs/

# 3. 分析堆转储（需要 MAT 工具）
docker cp claude-mem-java:/app/logs/heap_dump.hprof ./
```

**解决方案**：

```bash
# 增加内存限制
JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=85.0"

# 或设置固定堆大小
JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseZGC"
```

#### 7.1.4 LLM API 调用失败

**症状**：观察生成失败，日志显示 API 错误

**诊断步骤**：

```bash
# 1. 检查 API 密钥
docker compose exec claude-mem env | grep API_KEY

# 2. 测试 API 连接
curl -H "Authorization: Bearer $SPRING_AI_OPENAI_API_KEY" \
  https://api.deepseek.com/v1/models

# 3. 查看错误日志
docker compose logs claude-mem | grep -i "llm\|api\|error"
```

**常见错误**：

| 错误代码 | 原因 | 解决方案 |
|---------|------|---------|
| 401 | API 密钥无效 | 检查 `SPRING_AI_OPENAI_API_KEY` |
| 429 | 速率限制 | 降低请求频率或升级套餐 |
| 500 | API 服务异常 | 等待或切换备用 API |

### 7.2 性能问题

#### 7.2.1 响应延迟高

**诊断步骤**：

```bash
# 1. 检查数据库慢查询
docker compose exec postgres psql -U postgres -d claude_mem -c \
  "SELECT * FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10"

# 2. 检查 JVM 线程
docker exec claude-mem-java jstack 1 > thread_dump.txt

# 3. 监控 HTTP 请求
curl http://localhost:37777/actuator/metrics/http.server.requests
```

**优化建议**：

- 启用数据库查询缓存
- 优化 SQL 查询（避免 N+1）
- 调整连接池大小
- 增加 JVM 内存

#### 7.2.2 数据库查询慢

**诊断步骤**：

```bash
# 1. 查看执行计划
EXPLAIN ANALYZE SELECT * FROM mem_observations WHERE project_path = '/path';

# 2. 检查索引使用
SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';

# 3. 查看表统计信息
SELECT * FROM pg_stats WHERE tablename = 'mem_observations';
```

**优化建议**：

```sql
-- 重建索引
REINDEX TABLE mem_observations;

-- 更新统计信息
ANALYZE mem_observations;

-- 添加缺失索引（示例）
CREATE INDEX idx_obs_custom ON mem_observations(column_name);
```

### 7.3 日志分析

#### 错误日志过滤

```bash
# 提取 ERROR 级别日志
docker compose logs claude-mem | grep "ERROR"

# 按时间段过滤
docker compose logs claude-mem --since="2026-03-13T10:00:00" --until="2026-03-13T11:00:00"

# 关键词搜索
docker compose logs claude-mem | grep -i "failed\|exception\|error"
```

#### 审计日志

```bash
# 查看会话创建日志
docker exec claude-mem-java grep "Session created" /app/logs/claude-mem.log

# 查看观察生成日志
docker exec claude-mem-java grep "Observation saved" /app/logs/claude-mem.log
```

---

## 8. 备份与恢复

### 8.1 数据备份

#### 数据库备份

```bash
# 完整备份
docker exec claude-mem-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d_%H%M%S).sql

# 压缩备份
docker exec claude-mem-postgres pg_dump -U postgres claude_mem | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# 仅结构备份
docker exec claude-mem-postgres pg_dump -U postgres --schema-only claude_mem > schema.sql

# 仅数据备份
docker exec claude-mem-postgres pg_dump -U postgres --data-only claude_mem > data.sql
```

#### 自动备份脚本

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backups/claude-mem"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

# 创建备份目录
mkdir -p $BACKUP_DIR

# 执行备份
docker exec claude-mem-postgres pg_dump -U postgres claude_mem | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# 清理旧备份
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: backup_$DATE.sql.gz"
```

#### Cron 定时任务

```bash
# 每天凌晨 2 点备份
0 2 * * * /path/to/backup.sh >> /var/log/claude-mem-backup.log 2>&1
```

### 8.2 数据恢复

#### 完整恢复

```bash
# 1. 停止应用
docker compose stop claude-mem

# 2. 恢复数据库
gunzip -c backup_20260313_020000.sql.gz | \
  docker exec -i claude-mem-postgres psql -U postgres claude_mem

# 3. 启动应用
docker compose start claude-mem

# 4. 验证恢复
curl http://localhost:37777/actuator/health
```

#### 表级恢复

```bash
# 仅恢复观察表
docker exec -i claude-mem-postgres psql -U postgres claude_mem << EOF
TRUNCATE TABLE mem_observations CASCADE;
EOF

gunzip -c backup.sql.gz | grep -A 1000000 "COPY mem_observations" | \
  docker exec -i claude-mem-postgres psql -U postgres claude_mem
```

### 8.3 灾难恢复

#### 灾难恢复计划

1. **数据备份策略**：
   - 每日完整备份（保留 30 天）
   - 每周增量备份（保留 12 周）
   - 异地备份（推荐）

2. **恢复时间目标（RTO）**：< 1 小时

3. **恢复点目标（RPO）**：< 24 小时

#### 快速恢复流程

```bash
#!/bin/bash
# disaster_recovery.sh

# 1. 停止所有服务
docker compose down

# 2. 清理数据卷（谨慎操作！）
docker volume rm claude-mem_postgres_data

# 3. 启动服务
docker compose up -d postgres

# 4. 等待数据库就绪
sleep 10

# 5. 恢复数据
gunzip -c /backups/latest.sql.gz | \
  docker exec -i claude-mem-postgres psql -U postgres claude_mem

# 6. 启动应用
docker compose up -d claude-mem

# 7. 验证
curl http://localhost:37777/actuator/health
```

---

## 附录

### A. Docker 常用命令

```bash
# 服务管理
docker compose up -d                  # 启动服务
docker compose down                   # 停止服务
docker compose restart claude-mem     # 重启应用
docker compose logs -f claude-mem     # 查看日志

# 镜像管理
docker compose pull                   # 拉取最新镜像
docker compose build                  # 构建镜像
docker images | grep claude-mem       # 查看镜像

# 容器管理
docker compose ps                     # 查看状态
docker stats claude-mem-java          # 资源监控
docker exec -it claude-mem-java bash  # 进入容器

# 清理
docker system prune -a               # 清理未使用资源
docker volume prune                  # 清理未使用卷
```

### B. 数据库常用查询

```sql
-- 查看表大小
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- 查看活跃连接
SELECT
  pid,
  usename,
  application_name,
  client_addr,
  state,
  query_start,
  query
FROM pg_stat_activity
WHERE datname = 'claude_mem';

-- 查看索引使用率
SELECT
  schemaname,
  tablename,
  indexname,
  idx_scan,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- 查看最近迁移
SELECT
  version,
  description,
  type,
  script,
  installed_on,
  execution_time
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

### C. API 快速测试

```bash
# 健康检查
curl http://localhost:37777/actuator/health

# 创建测试会话
curl -X POST http://localhost:37777/api/ingest/session-start \
  -H "Content-Type: application/json" \
  -d '{
    "contentSessionId": "test-session-001",
    "projectPath": "/tmp/test-project",
    "source": "manual"
  }'

# 记录观察
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "contentSessionId": "test-session-001",
    "observation": "Test observation for deployment verification"
  }'

# 搜索观察
curl "http://localhost:37777/api/search?query=test&limit=10"

# 查看统计
curl http://localhost:37777/api/stats
```

### D. 性能基准测试

```bash
# 安装 wrk
# macOS: brew install wrk
# Linux: apt-get install wrk

# 健康检查基准测试
wrk -t4 -c100 -d30s http://localhost:37777/actuator/health

# 搜索 API 基准测试
wrk -t4 -c50 -d30s \
  -s scripts/bench_search.lua \
  http://localhost:37777/api/search
```

---

## 联系与支持

- **GitHub Issues**: https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues
- **文档**: https://github.com/Blueforce-Tech-Inc/BlueCortexCE/docs
- **社区**: (待添加)

---

**最后更新**: 2026-03-13
**版本**: 1.0.0
