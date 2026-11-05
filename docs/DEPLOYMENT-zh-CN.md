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
| Docker | ≥ 20.10 | 容器运行时 |
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
cd BlueCortexCE

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
curl http://localhost:37777/api/health
```

### 2.2 使用预构建镜像

从 GitHub Container Registry 拉取预构建镜像：

```bash
# 拉取最新版本
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main

# 拉取特定版本
docker pull ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:v0.1.0-beta
```

### 2.3 自定义构建

从源码构建镜像：

```bash
# 构建镜像
docker build -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main .
```

### 2.4 Docker Compose 服务说明

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: cortex-ce-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-claude_mem}
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      # DB_PASSWORD 必须设置在 .env 中（参考 .env.docker 模板）
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Database password is required — set DB_PASSWORD in your .env file}
    volumes:
      - ${POSTGRES_DATA_PATH:-postgres_data}:/var/lib/postgresql/data
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres}"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - claude-mem-network
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
      # 使用 DB_PASSWORD — 必须设置在 .env 中（参考 .env.docker 模板）
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:?Database password is required — set DB_PASSWORD in your .env file}

      # 服务配置
      SERVER_PORT: 37777
      SERVER_ADDRESS: 0.0.0.0
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prd}

      # LLM 配置
      SPRING_AI_OPENAI_API_KEY: ${SPRING_AI_OPENAI_API_KEY:-}
      SPRING_AI_OPENAI_BASE_URL: ${SPRING_AI_OPENAI_BASE_URL:-https://api.openai.com}
      SPRING_AI_OPENAI_CHAT_MODEL: ${SPRING_AI_OPENAI_CHAT_MODEL:-gpt-4o}

      # 嵌入配置
      SPRING_AI_OPENAI_EMBEDDING_API_KEY: ${SPRING_AI_OPENAI_EMBEDDING_API_KEY:-}
      SPRING_AI_OPENAI_EMBEDDING_BASE_URL: ${SPRING_AI_OPENAI_EMBEDDING_BASE_URL:-https://api.openai.com}
      SPRING_AI_OPENAI_EMBEDDING_MODEL: ${SPRING_AI_OPENAI_EMBEDDING_MODEL:-text-embedding-3-small}
      SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS: ${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS:-1536}

      # JVM 配置
      JAVA_OPTS: ${JAVA_OPTS:--XX:+UseZGC -XX:MaxRAMPercentage=75.0}

      # 运行时配置
      CLAUDE_MEM_MODE: ${CLAUDE_MEM_MODE:-code}
      CLAUDEMEM_LOG_DIR: /app/logs
      CLAUDEMEM_LLM_PROVIDER: ${CLAUDEMEM_LLM_PROVIDER:-openai}

      # Anthropic API（可选 — 设置 CLAUDEMEM_LLM_PROVIDER=anthropic 以使用）
      SPRING_AI_ANTHROPIC_API_KEY: ${SPRING_AI_ANTHROPIC_API_KEY:-}
      SPRING_AI_ANTHROPIC_BASE_URL: ${SPRING_AI_ANTHROPIC_BASE_URL:-https://api.anthropic.com}
      SPRING_AI_ANTHROPIC_CHAT_MODEL: ${SPRING_AI_ANTHROPIC_CHAT_MODEL:-claude-sonnet-4-5}

      MEMORY_REFINE_ENABLED: ${MEMORY_REFINE_ENABLED:-true}
    volumes:
      - ${LOGS_PATH:-claude-mem-logs}:/app/logs
    ports:
      - "${SERVER_PORT:-37777}:37777"
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:37777/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - claude-mem-network

networks:
  claude-mem-network:
    driver: bridge

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

> **本表是阅读辅助，不是权威来源。** 权威清单是
> `backend/src/main/resources/db/migration/` 目录里的实际内容——Flyway 会自动应用它们
> （见 §4.2），而本表**不做**与之比对。版本号跳号是故意的：**V9 与 V10 并不存在**。
> 若某个迁移已落地而本表缺行，不影响任何行为；若某行写错了，请以目录为准。

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
| V11 | `V11__observation_quality.sql` | 观察质量评分 |
| V12 | `V12__step_efficiency.sql` | 步骤效率追踪 |
| V13 | `V13__unify_session_id_on_content_session.sql` | 统一 session ID 到 content_session |
| V14 | `V14__observation_source_and_extracted_data.sql` | 观察来源与提取数据 |
| V15 | `V15__add_user_id_to_sessions.sql` | 会话表添加 user_id |
| V16 | `V16__composite_source_index.sql` | 复合来源索引 |
| V17 | `V17__observation_feedback.sql` | 观察反馈追踪 + 扩展字段 |
| V18 | `V18__add_platform_source.sql` | 添加 platform_source 列用于多平台追踪 |
| V19 | `V19__viewer_session_indexes.sql` | WebUI 会话目录与信息流查询的索引 |

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
docker exec -it cortex-ce-postgres psql -U postgres -d claude_mem

# 查看迁移历史
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# 验证表结构
\dt mem_*

# 验证索引
\di mem_*
```

### 4.4 回滚策略

Flyway 社区版不支持自动回滚，并且**本仓库不提供回滚脚本**——每个迁移的逆向 DDL 都必须对照
`backend/src/main/resources/db/migration/` 下的具体文件自行编写。

多数情况下，安全的做法是用第 1 步的备份做恢复，而不是手工反向修改迁移：

```bash
# 1. 先备份 —— 动 schema 之前务必执行
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d).sql

# 2. 如需撤销该迁移，恢复备份
docker exec -i cortex-ce-postgres psql -U postgres claude_mem < backup_20260101_000000.sql
```

如果确实需要逆向某个迁移，请以迁移文件本身为准——它是「当时到底执行了什么」的唯一记录。
下面的示例是撤销 `V8__add_observation_content_hash.sql` 所涉及的内容，而两条警告正是说明
为什么这不是一个机械流程：

```sql
-- V8 的逆向操作，依据 backend/src/main/resources/db/migration/V8__add_observation_content_hash.sql
ALTER TABLE mem_observations DROP CONSTRAINT IF EXISTS fk_obs_memory_session;
DROP INDEX IF EXISTS idx_obs_content_hash;
ALTER TABLE mem_observations DROP COLUMN IF EXISTS content_hash;
DELETE FROM flyway_schema_history WHERE version = '8';
```

> **执行前务必先确认当前代码。** V8 新增的 `content_hash` 列并非历史遗留——
> `ObservationRepository.findDuplicateByContentHash` 与 `AgentService` 都在用它做
> observation 去重，删掉该列会直接破坏写入链路。另外，V8 建的 `fk_obs_memory_session`
> 约束早已被 `V13__unify_session_id_on_content_session.sql` 删除并改用
> `content_session_id` 模型，因此上面第一条语句实际上是空操作，schema 也早已不是
> V8 执行后的状态。请优先使用备份恢复。

---

## 5. 环境变量说明

### 5.1 核心配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | 否 | `prd`（**仅 Docker Compose 下**） | 环境配置（dev/prd）。把默认值定成 `prd` 的是 `docker-compose.yml:52`；原生 JAR 运行若不设该变量，则**没有任何激活的 profile** —— 见 5.4 节的第三种状态 |
| `SERVER_PORT` | 否 | `37777` | HTTP 服务端口 |
| `SERVER_ADDRESS` | 否 | `127.0.0.1`（本地 JAR）；Docker 中硬编码为 `0.0.0.0` | 监听地址 |
| `CORTEXCE_API_TOKEN` | 否 | 空（无鉴权） | 可选 Bearer token（`cortexce.security.api-token`）。配置后 `/api/**` 写操作与 `GET /api/logs/**` 要求 `Authorization: Bearer <token>`；缺失或错误 token 返回 401 与统一错误信封。**对外暴露到 localhost 之外前必须配置。** |

### 5.2 数据库配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_DATASOURCE_URL` | 是 | - | 数据库连接 URL |
| `SPRING_DATASOURCE_USERNAME` | 是 | `postgres` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | **是** | - | 数据库密码 |
| `DB_NAME` | 否 | `claude_mem` | 数据库名称 |
| `DB_USERNAME` | 否 | `postgres` | 数据库用户名（Docker Compose） |
| `DB_PASSWORD` | **是** | - | 数据库密码 — 参考 `.env.docker` 模板（Docker Compose） |
| `POSTGRES_PORT` | 否 | `5433` | PostgreSQL 主机端口（Docker Compose: `host:container`） |

### 5.3 LLM 配置

#### OpenAI 兼容 API（DeepSeek、Moonshot 等）

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_AI_OPENAI_API_KEY` | 否 | - | OpenAI API 密钥 — 仅在 `CLAUDEMEM_LLM_PROVIDER=openai`（默认）时需要（别名：`OPENAI_API_KEY`） |
| `SPRING_AI_OPENAI_BASE_URL` | 否 | `https://api.openai.com` | API 基础 URL（别名：`OPENAI_BASE_URL`） |
| `SPRING_AI_OPENAI_CHAT_MODEL` | 否 | `gpt-4o` | 聊天模型名称（别名：`OPENAI_MODEL`） |

#### Anthropic 兼容 API（Claude、GLM 等）

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_AI_ANTHROPIC_API_KEY` | 否 | - | Anthropic API 密钥 — 仅在 `CLAUDEMEM_LLM_PROVIDER=anthropic` 时需要（别名：`ANTHROPIC_API_KEY`） |
| `SPRING_AI_ANTHROPIC_BASE_URL` | 否 | `https://api.anthropic.com` | API 基础 URL（别名：`ANTHROPIC_BASE_URL`） |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | 否 | `claude-sonnet-4-5` | 模型名称（别名：`ANTHROPIC_MODEL`） |
| `CLAUDEMEM_LLM_PROVIDER` | 否 | `openai` | LLM 提供商（openai/anthropic） |

> **两个 chat API 密钥是二选一，不是一对。** 本表早前版本把
> `SPRING_AI_OPENAI_API_KEY` 标为无条件**必填**，却给镜像的那一行（Anthropic 密钥）
> 加了「仅在 `provider=anthropic` 时需要」的限定——**同一套机制，两行说法不同**。
> 实际上只有与当前 provider 匹配的那个密钥才是需要的：`SpringAiConfig` 用**完全相同**的
> `@ConditionalOnProperty(name = "api-key")` 模式注册 `openAiChatModel` 与
> `anthropicChatModel`，各自在密钥为空或 provider 不匹配时返回 `null`，再由
> `chatClient` bean（`@ConditionalOnMissingBean`）取第一个非 null 的 `ChatModel`。
> 它自己的启动告警写得很直白：*"Configure spring.ai.openai.api-key **or**
> spring.ai.anthropic.api-key."* 因此在 `provider=anthropic` 下，**只配 Anthropic
> 密钥**的部署可以正常启动并提供 LLM 能力。
>
> 但下面的**嵌入**密钥确实是始终必填，且这处不对称是真实的：**嵌入没有 provider 开关**，
> 无论启用哪个 chat 提供商，嵌入请求始终走 OpenAI 兼容端点。

### 5.4 嵌入模型配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | **是** | - | 嵌入 API 密钥（别名：`EMBEDDING_API_KEY`） |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | 否 | `https://api.openai.com` | 嵌入 API URL（别名：`EMBEDDING_BASE_URL`） |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 否 | `text-embedding-3-small` | 嵌入模型名称（别名：`EMBEDDING_MODEL`） |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | 否 | `1536` | 嵌入维度（768/1024/1536）（别名：`EMBEDDING_DIMENSIONS`） |

> **「默认值」一列是按 profile 分化的。** 上方两张表写的都是 `prd` profile 的值，
> 而 `docker compose up` 选中的正是它——`SPRING_PROFILES_ACTIVE` 默认 `prd`。
> `dev` profile 则是另一套：
>
> | 变量名 | `prd` 默认值 | `dev` 默认值 |
> |--------|--------------|--------------|
> | `SPRING_AI_OPENAI_BASE_URL` | `https://api.openai.com` | `https://api.deepseek.com` |
> | `SPRING_AI_OPENAI_CHAT_MODEL` | `gpt-4o` | `deepseek-chat` |
> | `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | `https://api.openai.com` | `https://api.siliconflow.cn` |
> | `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | `BAAI/bge-m3` |
> | `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1536` | `1024` |
>
> 显式设置则与 profile 无关；不设置则由 profile 决定。5.8 节的开发环境示例正是
> 依赖这一点——它把五个值全部显式写出，而不是继承默认值。
>
> **还有第三种状态，而原生 JAR 运行恰好落在这一种。**
> `prd` 只是 **Docker Compose 下的**默认值（`docker-compose.yml:52`：
> `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prd}`）。原生启动 JAR 且不设该变量时，
> Spring 会打印「No active profile set, falling back to 1 default profile: "default"」——
> 此时**上面两张表都不适用**，因为 `application.yml` 自身根本没有定义任何 `spring.ai.openai.*`
> 配置项。真正生效的是 `SpringAiConfig` 里的 `@Value` 兜底值，恰好与 `dev` 那一列一致：
>
> | 变量 | 无 profile 时实际生效的值 |
> |------|--------------------------|
> | `SPRING_AI_OPENAI_BASE_URL` | `https://api.deepseek.com` |
> | `SPRING_AI_OPENAI_CHAT_MODEL` | `deepseek-chat` |
> | `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | `https://api.siliconflow.cn` |
> | `SPRING_AI_OPENAI_EMBEDDING_MODEL` | `BAAI/bge-m3` |
> | `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | `1024` |
>
> **而且在没有 profile 时，上表里大部分变量会失效。** 2026-10-20 实测（无 profile 启动 JAR）：
> 设置 `SPRING_AI_OPENAI_BASE_URL` 与 `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` **确实生效**，
> 但 `SPRING_AI_OPENAI_CHAT_MODEL`、`SPRING_AI_OPENAI_EMBEDDING_MODEL` 与
> `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` **被静默忽略**，短别名 `OPENAI_BASE_URL` /
> `OPENAI_MODEL` 同样无效。原因在属性名上：`SpringAiConfig` 读模型与维度用的是
> `spring.ai.openai.chat.options.model` 与 `spring.ai.openai.embedding.options.dimensions`，
> 而 Spring 的宽松绑定把 `SPRING_AI_OPENAI_CHAT_MODEL` 映到 `spring.ai.openai.chat.model`、
> 把 `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` 映到 `spring.ai.openai.embedding.dimensions`
> —— **差一段**，值到不了字段。两个 base URL 读的是 `spring.ai.openai.base-url` /
> `spring.ai.openai.embedding.base-url`，**恰好对得上**，所以它们仍然生效。在 `prd` 与 `dev`
> 下这个问题不出现，因为那两个配置文件**自己**就把变量名写了出来
> （`model: ${SPRING_AI_OPENAI_CHAT_MODEL:...}`）—— **映射变量名的是 profile，不是代码**。
> 所以：**请显式设置 `SPRING_PROFILES_ACTIVE`，否则模型名会被忽略。**
>
> 上表的短别名只定义在 `application-prd.yml` 中。`dev` profile 识别
> `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL`，但**没有**对应的
> `EMBEDDING_*` 别名，因此 `dev` 下必须写全名。
>
> **改维度之前值得先知道它的后果。** 写入侧按维度分列，768 维或 1536 维模型都能
> 正确存储；但读取侧不是：语义检索恒定与 `embedding_1024` 比较，维度不匹配会抛出
> `different vector dimensions <n> and 1024`，查询退化为全文检索。这不是静默失败——
> 检索响应会返回 `strategy: "tsvector"` 与 `fellBack: true`——但嵌入配置错误的唯一
> 外在表现，也仅仅是「没有任何查询走到向量索引」。`BAAI/bge-m3` + 1024 维才是检索
> 路径所围绕构建的配置。

### 5.5 运行时配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `CLAUDE_MEM_MODE` | 否 | `code` | 记忆模式（code、code--<lang>、email-investigation 等） |
| `CLAUDE_MEM_MODES_DIR` | 否 | *（空）* | 自定义模式配置目录。留空时，生产用 JAR 内嵌的 `classpath:modes/`，开发用 `plugin/modes/`。 |
| `CLAUDEMEM_LOG_DIR` | 否 | `~/.claude-mem/logs` | 日志目录 |
| `MEMORY_REFINE_ENABLED` | 否 | `true` | 启用记忆精炼（自我进化） |
| `JAVA_OPTS` | 否 | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` | JVM 参数 |

#### 数据保留（第 20 轮）

`RetentionService` 按天清理用户提示词、observation、summary 与滚动日志文件。
所有开关都默认在安全一侧：**作业关闭**、运行是 **dry-run**、每个窗口都是 `0`
（**永久保留**）。运维显式开启之前，不会删除任何行或文件。

| 变量 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `CORTEXCE_RETENTION_ENABLED` | 否 | `false` | 定时清理总开关 |
| `CORTEXCE_RETENTION_CRON` | 否 | `0 0 3 * * *` | 运行时间（仅启用时生效） |
| `CORTEXCE_RETENTION_DRY_RUN` | 否 | `true` | 只统计并打印将要删除的数量，不写库、不删文件 |
| `CORTEXCE_RETENTION_PROMPTS_DAYS` | 否 | `0` | 提示词保留天数；`0` 永久保留 |
| `CORTEXCE_RETENTION_OBSERVATIONS_DAYS` | 否 | `0` | observation 保留天数；`0` 永久保留 |
| `CORTEXCE_RETENTION_SUMMARIES_DAYS` | 否 | `0` | summary 保留天数；`0` 永久保留 |
| `CORTEXCE_RETENTION_LOGS_DAYS` | 否 | `0` | 滚动日志保留天数；`0` 永久保留 |
| `CORTEXCE_RETENTION_PROJECT` | 否 | 空 | 精确匹配 `project_path` 的 scope；空表示所有项目 |
| `CORTEXCE_RETENTION_LOG_DIR` | 否 | `LOG_DIR` 或 `~/.claude-mem/logs` | 滚动日志目录 |
| `CORTEXCE_RETENTION_LOG_PREFIX` | 否 | `claude-mem` | 只有以该前缀开头的文件才参与清理 |

上线分两步：先启用并保持 `DRY_RUN=true`，读取日志里的
`Retention dry run: would delete prompts=… observations=… summaries=… logFiles=…`，
确认数量后再设 `DRY_RUN=false`。行按 `created_at_epoch < cutoff` 选取，恰好落在
边界上的行会保留；设置 `PROJECT` 后，计数与 `DELETE` 都带 `project_path` 条件，
因此限定 scope 的运行不会碰到其他项目的行。日志清理不会删除正在写入的活动日志
（其 mtime 晚于 cutoff），也不会触碰目录之外或前缀不匹配的文件。

#### 检索重排（Round 21）

可选重排会用对话模型重排混合检索的前几条结果。它**默认关闭**；任何失败路径（缺 key、
超时、非 2xx、无法解析的响应）都原样返回融合排序，所以开启只会增加延迟，不会丢结果。
在 90 条真实 query 上实测，top-5 关闭 thinking：hit@1 0.833 → 0.867，串行 p95 3.7 秒。
证据见 `docs/archive/2026-11-04_rerank-real.md`。

| 变量 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `CORTEXCE_SEARCH_RERANK_ENABLED` | 否 | `false` | 总开关；关闭时保持融合排序 |
| `CORTEXCE_SEARCH_RERANK_TOP_N` | 否 | `5` | 参与重排的前 N 条结果 |
| `CORTEXCE_SEARCH_RERANK_TIMEOUT_MS` | 否 | `8000` | 单次调用超时，超时后回退原排序 |
| `CORTEXCE_SEARCH_RERANK_THINKING` | 否 | `false` | `true` 使用模型推理（串行 p95 33.5 秒，2.2% 空排序） |

#### Evo-Memory 与结构化抽取调参

`MEMORY_REFINE_ENABLED` 决定精炼开关，但**不决定精炼做什么**——下面三个 `MEMORY_REFINE_*`
阈值决定哪些观测会被处理，且彼此独立。`MEMORY_QUALITY_THRESHOLD` 排在首位，是因为它是
唯一一个**不属于精炼**的质量旋钮：它只管经验检索。结构化抽取（Phase 3）是另一项功能，
默认关闭，有自己的三个批量控制参数。

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `MEMORY_QUALITY_THRESHOLD` | 否 | `0.6` | 观测被 `POST /api/memory/experiences`（ExpRagService）选为 ICL 经验样本所需的最低质量分。不是 search 或 context 检索的过滤器。`MemoryRefineService.findStaleObservations` 中另有一处字面量 `0.6` 负责精炼候选门槛，不由本值驱动。 |
| `MEMORY_REFINE_DELETE_THRESHOLD` | 否 | `0.3` | 质量低于此值的观测在精炼时被删除（剪枝） |
| `MEMORY_REFINE_STALE_DAYS` | 否 | `30` | 超过多少天未访问即视为陈旧 |
| `MEMORY_REFINE_COOLDOWN_DAYS` | 否 | `7` | 精炼过的观测多少天后可再次精炼 |
| `EXTRACTION_ENABLED` | 否 | `false` | 启用 Phase 3 结构化抽取 |
| `EXTRACTION_MAX_CANDIDATES` | 否 | `100` | 模板首次运行时考察的候选观测数 |
| `EXTRACTION_BATCH_SIZE` | 否 | `20` | 每批送入 LLM 的观测数 |
| `EXTRACTION_MAX_BATCHES` | 否 | `10` | 每个模板每次运行的批次数上限 |

`MEMORY_REFINE_DELETE_THRESHOLD` 是其中最具破坏性的一个：精炼运行时，**已有**质量分
且低于该阈值的候选观测会被删除而非重写。完全没有评分的记录不在此列，它们走重写
分支。调高阈值是安全方向，调低会丢数据。

**后端自带的限流器单独配置，而且它的键在 `application.yml` 里一个都没有。**
`RateLimitService` 直接从 `@Value` 默认值读取，因此这些键是真实生效且可调的，
只是在上表中看不到：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS` | `10` | 每个窗口内允许的请求数 |
| `CLAUDEMEM_RATE_LIMIT_WINDOW_SECONDS` | `60` | 窗口长度（秒）。它是**固定**窗口、到期即整窗重置，而不是滑动窗口 —— 见下文说明 |
| `CLAUDEMEM_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | `300` | 空闲窗口条目的清理间隔（秒） |

**它只守一个端点，不是整个 API。** 唯一调用点是 `IngestionController`，
把它用在 `POST /api/ingest/tool-use` 上、以内容会话 id 为键。另外三个 ingest
端点（`user-prompt`、`session-end`、`observation`）与全部读端点**都不限流**。
活体实测：同一会话连续 10 次 tool-use 均成功，第 11 次返回

```json
{"retry_after": "60", "error": "Rate limit exceeded"}
```

而同一时段内 13 次 `GET /api/search` 全部返回 `200`。`retry_after` 的值是
**当前窗口的剩余秒数**（`RateLimitService.getRemainingSeconds()` =
`windowSeconds - (now - windowStart)`），因此只有当被拒请求落在开窗的同一秒里
它才是 `60` —— 晚一秒读到的就是 `59`。按它退避仍然是正确的：窗口到期、计数归零。
`cleanup-interval` 是内存控制项：服务最多保留 10,000 条窗口记录，间隔越短，
在会话数很多时增长越受控。

> **窗口是固定的，不是滑动的，且上限在边界处可以翻倍。**
> `RateLimitService.SlidingWindow.tryIncrement` 在 `now - windowStart >= windowSeconds`
> 时**整窗把计数归零** —— 窗口不会逐个请求地老化。2026-10-20 活体实测（同一会话）：
> t+0.1s 发 1 次，t+58.1s 再发 9 次（全部 `200`，计数到 10），
> 然后在 t+60.6s **再发 10 次、全部 `200`** —— 计数恰在边界处归零，
> 而先前那 10 个**才 2.5 秒大**，若真是滑动窗口这 10 个应当**全部被拒**。
> 于是 20 个请求彼此相隔**几秒**就全部放行 —— 那一次是 2.5 秒，
> 同一探针复现是 3.3 秒 —— 第 21 个（同一会话）返回 `429`。
> 关键是这间隔是**几秒**、而不是 60 秒：「每窗口 10 个请求」应理解为**每个窗口**的上限，
> 而**不是**任意 60 秒区间内的硬上限。后端自己的 javadoc 里这个类仍叫
> `SlidingWindow`、变量仍被写成滑动窗口 —— 两个名字描述的都是代码并未实现的算法。

这个限流器与 LLM 提供方可能返回的 `429` 无关——见上文排障表。

### 5.6 数据持久化路径（Docker Compose）

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `POSTGRES_DATA_PATH` | 否 | `postgres_data` | PostgreSQL 数据卷路径（Docker Compose 主机路径） |
| `LOGS_PATH` | 否 | `claude-mem-logs` | 应用日志卷路径（Docker Compose 主机路径） |

### 5.7 多用户部署

`userId` 是**会话级**属性，在会话开始时设置（`POST /api/session/start` 的 `user_id`
字段），或事后挂接（`PATCH /api/session/{sessionId}/user`）。它**不是**观测的属性：
`mem_observations` 没有用户列，因此无法从一条已存记录上读出它属于谁。

**部分端点认 `userId`，而两个常用端点不认。** 在一个只含 alice 一条观测的项目上实测：

| 端点 | 是否接受 `userId` | 结果 |
|------|------------------|------|
| `POST /api/memory/experiences` | 是 | `alice` → 1 条经验，`bob` → 0 条 |
| `POST /api/memory/icl-prompt` | 是 | 同样按用户限定 |
| `GET /api/extraction/{templateName}/latest`、`/history` | 是 | 同样按用户限定 |
| `GET /api/search` | **否** | 返回 alice 的观测，且无法再收窄 |
| `GET /api/observations`、`POST /api/observations/batch` | **否** | 返回该项目的全部观测 |

ICL 这一侧隔离是正确的：Agent 要 alice 的提示就拿到 alice 的经验，传错或不存在的
id 返回空结果而不是别人的。`GET /api/search` 则不论传什么都返回整个项目——因为压根
没有用户可供过滤。

若一个项目要服务多个用户，请把 `/api/search` 与 `/api/observations` 当作
**项目级、而非用户级**接口。若需要在这两个端点上也做按用户隔离，就给每个用户分配
各自的项目路径；或者干脆不要把它们暴露给终端用户——四家 SDK 都暴露了这两个方法，
因此任何持有 API key 的客户端都能调到。

### 5.8 配置示例

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
SPRING_AI_ANTHROPIC_API_KEY=sk-ant-xxx
SPRING_AI_ANTHROPIC_BASE_URL=https://api.anthropic.com
SPRING_AI_ANTHROPIC_CHAT_MODEL=claude-sonnet-4-5
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
# 自定义健康端点（推荐）
curl http://localhost:37777/api/health

# Spring Boot Actuator 健康检查
curl http://localhost:37777/actuator/health

# 详细信息
curl http://localhost:37777/actuator/health | jq

# 响应示例
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {"database": "PostgreSQL", "validationQuery": "isValid()"}
    },
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "messageQueue": {
      "status": "UP",
      "details": {"pending": 0, "processing": 0, "stale": 0, "last_processed": "2026-05-04T03:08:25.906Z"}
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

#### 日志位置与上限

服务把结构化日志写到 `${CLAUDEMEM_LOG_DIR}`（默认 `~/.claude-mem/logs`）：

| 文件 | 写入方 | 上限 |
|------|--------|------|
| `claude-mem-YYYY-MM-DD.log` | `ClaudeMemLogAppender`；`GET /api/logs` 读取的也是它 | 达到 `cortexce.logging.max-file-size`（100 MB）时轮转 |
| `claude-mem-YYYY-MM-DD.<时间戳>.log` | 上面文件的轮转分片 | 目录总量超过 `cortexce.logging.total-size-cap`（1 GB）时从最旧的分片开始删除 |

轮转后**日期命名文件仍是活动文件（最新内容）**：写满后它被改名为带时间戳的分片，再新建一个空的日期文件继续写。因此轮转不会让 `/api/logs` 读不到最新日志。

```yaml
cortexce:
  logging:
    max-file-size: ${CORTEXCE_LOG_MAX_FILE_SIZE:100MB}
    total-size-cap: ${CORTEXCE_LOG_TOTAL_SIZE_CAP:1GB}
```

清理在启动时和每次轮转后执行，所以两次轮转之间目录最多超出上限一个活动文件（≤ `max-file-size`）。

#### 何时清理、清理什么

- **日志增长不需要人工清理。** 轮转 + 总量上限已经兜住目录大小。改造前参考机器上 105 个按天文件累计到 1.5 GB（单日最大 105 MB）。
- **按天保留**是可选的第二层策略：合规要求「即使没到体积上限也要删除 N 天前日志」时，启用既有保留任务（`cortexce.retention.enabled=true`、`cortexce.retention.logs-days=N`）。它与 prompts / observations / summaries 共用同一个调度器和 dry-run 模式。
- **需要手工清理的只有** `~/.claude-mem/logs/claude-mem-*.log`。同目录的 `settings.json`、`.env`、`cursor-projects.json`、`transcript-watch.json` 是几 KB 的配置/状态文件，不是缓存，删除会丢配置。
- **另一处增长是数据库行**：用 `cortexce.retention.*`（先 `dry-run=true` 看计数）而不是手工删表。

#### 查看日志

```bash
# Docker 日志
docker compose logs -f claude-mem

# 实时跟踪日志
docker compose logs -f --tail=100 claude-mem

# 结构化文件（最新内容在日期命名文件里）
tail -f ~/.claude-mem/logs/claude-mem-$(date +%F).log

# 搜索日志
grep "ERROR" ~/.claude-mem/logs/claude-mem-$(date +%F).log
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
# 注意：需要 micrometer-registry-prometheus 依赖并启用
# spring.boot.actuator.prometheus.metrics.export.enabled=true
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
| `hikaricp.connections.pending` | 等待中的数据库连接请求数 | > 5 |

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
| `Port 37777 already in use` | 端口冲突 | 停止冲突服务。在已发布镜像上**只改 `SERVER_PORT` 行不通**：其健康检查被写死在 37777，应用改了端口而 Docker 仍去探测 37777，结果是把容器判成 unhealthy |

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
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem > backup_$(date +%Y%m%d_%H%M%S).sql

# 压缩备份
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# 仅结构备份
docker exec cortex-ce-postgres pg_dump -U postgres --schema-only claude_mem > schema.sql

# 仅数据备份
docker exec cortex-ce-postgres pg_dump -U postgres --data-only claude_mem > data.sql
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
docker exec cortex-ce-postgres pg_dump -U postgres claude_mem | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# 清理旧备份
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +$RETENTION_DAYS -delete

echo "Backup completed: backup_$DATE.sql.gz"
```

#### Cron 定时任务

```bash
# 每天凌晨 2 点备份
0 2 * * * /path/to/backup.sh >> /var/log/claude-mem-backup.log 2>&1
```

#### 备份校验巡检（可定时）

`scripts/backup-restore-drill.sh` 把原来的手工 dump/恢复验证包成无人值守巡检，一次运行完成整条闭环：

1. `pg_dump -Fc` 导出在线数据库。
2. 对 dump 计算 SHA-256，用 `pg_restore --list` 证明归档可读、确认包含
   `TABLE DATA public mem_observations`，恢复前再次计算哈希比对。
3. 恢复到一次性临时库。
4. 逐表比对行数：`mem_sessions`、`mem_observations`、`mem_user_prompts`、
   `mem_summaries`、`mem_pending_messages`、`mem_memory_scopes`。
5. 在副本上重放一次词法检索（`ZH_QUERY`，默认 `检索`），比对命中数与首条命中标题。
   `RETRIEVAL_COLUMN=auto` 会在 schema 有 `search_tokens` 时用它、否则用 `search_vector`，
   同一脚本因此能跨越中文词法迁移前后使用。
6. `INTEGRITY_AUDIT=1` 在副本上跑 `scripts/data-integrity-audit.py`，任何发现都会让本次
   巡检失败（这里永远不加 `--apply`：副本用完即删）。
7. `REBUILD_HNSW=1` 在副本上 drop 并重建向量索引、校验有效性，并记录耗时与索引字节数。
   这与回填部署时的索引重建是同一个操作，等于在恢复数据上做了一次演练。
8. 输出 JSON 报告，删除临时库；保留 dump 需 `KEEP_DUMP=1`，超过 `DUMP_KEEP_DAYS` 的旧
   dump 一并清理。

巡检期间后台采样器每 `DISK_SAMPLE_SECONDS`（默认 2 秒）记录一次磁盘占用——源库 + 副本 +
dump 文件，以及容器文件系统已用字节数；报告里保存各自峰值（`disk_peak`）。原因很实际：
盘快满时失败发生在恢复阶段，只有文件系统那一列能说明这次离阈值有多近。

**多久跑一次**：每周一次纯巡检（`VALIDATE_FLYWAY=0`，不依赖 jar）；每次 schema 迁移前后各跑一次；存储或容器故障之后立刻跑一次。夜间运行加 `KEEP_DUMP=1` 时，它同时就是真实备份——留下的 dump 正是刚刚被验证可恢复的那一份。

**放在哪里**：报告写到 `${HOME}/.claude-mem/backup-drill/reports/backup-drill-<时间戳>.json`，只保留最近 30 份（`REPORT_DIR`、`REPORT_KEEP`）。dump 写到 `DUMP_DIR`；`KEEP_DUMP=1` 时超过 `DUMP_KEEP_DAYS`（默认 30 天）的旧 dump 会被清理。

```bash
# 每天 02:10 —— 产出备份、验证备份、保留 dump
10 2 * * * cd /opt/cortexce && SOURCE_DB=claude_mem DUMP_DIR=/backups/cortexce KEEP_DUMP=1 \
  ./scripts/backup-restore-drill.sh >> /var/log/cortexce-backup.log 2>&1

# 每周一 03:15 —— 纯巡检，dump 用完即删
15 3 * * 1 cd /opt/cortexce && SOURCE_DB=claude_mem \
  ./scripts/backup-restore-drill.sh >> /var/log/cortexce-backup-drill.log 2>&1
```

退出码按阶段区分，调度器或告警钩子可据此区分「容器没起来」和「备份恢复出来是坏的」：

| 退出码 | 阶段 | 处置 |
|--------|------|------|
| 0 | 通过 | 无需操作；看报告里的体积与耗时。 |
| 2 | 前置检查 | 容器不可达、源库不存在，或 `VALIDATE_FLYWAY=1` 但没有 jar。修环境；本次没有产生备份。 |
| 3 | dump | `pg_dump` 失败——磁盘满、权限、或并发建表。清空间/修凭据后重跑。 |
| 4 | checksum / 归档 | dump 读不出或落盘后发生变化。按存储故障处理，**不要**覆盖上一份可用的备份。 |
| 5 | 恢复 | dump 可读但重建失败——通常是 Postgres 版本不一致或归档不完整。 |
| 6 | 行数比对 | 某张表行数不一致。保留 dump（`KEEP=1` 连临时库一起留）并人工比对后再信任它。 |
| 7 | 检索比对 | 词法检索结果不一致（命中数或探针标题）。源库稳定时说明问题在 dump/恢复链路，不在数据本身。 |
| 8 | Flyway 校验 | 恢复出来的 schema 校验不通过，不要用这份库做部署。 |
| 10 | 完整性巡检 | 副本能恢复但违反不变量（孤儿行、scope 不一致、embedding 状态漂移）。用 `KEEP=1` 留下副本，读报告里指名的巡检报告。 |
| 11 | HNSW 重建 | 副本上的向量索引建不出来（内存不足以容纳图，或索引定义漂移）。副本会被删除；用更大的 `HNSW_WORK_MEM` 重跑。 |
| 9 | 未预期 | 其余错误；上方日志会指出具体命令。 |

任何非 0 退出都要保留 dump 和报告，且**不要**删除上一份已知可用的备份。`KEEP=1` 会保留临时库便于排查；如果 dump 已被删除，用 `KEEP_DUMP=1` 重跑。若同一退出码连续出现，把最新的 dump 手工恢复到临时库逐项比对——报告里已经写好了期望的行数。

2026-11-04 实测（10 万条 observation，158 MB 库）：dump 3.5 MB / 0.5 s，checksum 0.3 s，恢复 4.7–6.3 s，行数比对 0.6–1.0 s，检索 0.4–0.5 s，端到端 8–10 s；`VALIDATE_FLYWAY=1` 额外约 3.2 s（启动 jar）。

2026-11-05 实测（1,000,198 条 observation、18 GB 库、100 万条向量 + HNSW 索引）：dump **526 s**（4.74 GB，约 3.8×），checksum 31 s，恢复 **2,448 s（40.8 分钟）**，行数比对 1.7 s，检索 7.6 s（两侧均 1,000,000 命中），完整性巡检 5.9 s（9 项检查、0 问题），HNSW 重建 **550 s**（7,582,621,696 B，7,581 B/行）——**端到端 59.6 分钟**。磁盘峰值 **39.5 GB（36.8 GiB）**：源库 19.6 GB + 恢复副本 15.2 GB + dump 4.7 GB，约为线上库的 **2.7×**；在生产主机上跑这套闭环前先按此预留空间。

恢复时间几乎全是建图时间：`pg_restore` 回放 `CREATE INDEX ... USING hnsw` 时用的是服务器当前的 `maintenance_work_mem`。默认 64 MB 时会打出 `hnsw graph no longer fits into maintenance_work_mem after 898,013 tuples`，41 分钟的恢复里约 40 分钟耗在这一条索引上；同样的建图在 4 GB 下只要 9.2 分钟。做法：恢复前先调大 `maintenance_work_mem`，或 dump 时不带向量索引、恢复后用 `REBUILD_HNSW=1` 一次性重建。完整报告见 `docs/archive/2026-11-05_ops-loop-1m.md`。

#### 批量导入后的向量索引重建

带向量的批量写入代价由 HNSW 图维护主导：pgvector 每写一行都要插入图，实测 100 万行时只有 **38–53 rows/s**（约 19–26 ms/行），而没有图时是 **900–1,300 rows/s**——同一批数据慢 20–30 倍。所以推荐的做法是：先灌数据，最后一次建图。

```bash
# 1. 批量写入（走 proxy 导入或直接 SQL）——此时 embedding_1024 上的索引必须不存在
# 2. 一次性建图，内存要足够把图放进 RAM
SOURCE_DB=claude_mem WORK_MEM=8GB WORKERS=2 ./scripts/rebuild-vector-index.sh
# 3. 校验：脚本会检查 indisvalid，并打印建图耗时与索引字节数
```

实测建图成本（独立容器、`m=16, ef_construction=64`）：

| 行数 | 索引体积 | `maintenance_work_mem` | 建图耗时 |
|------|----------|------------------------|----------|
| 10 万 | 781 MB | 1 GB | 36.9 s |
| 30 万 | 2,344 MB | 2 GB | 70.5 s |
| 100 万 | 7.2–7.8 GB | 8 GB（4 GB 时约 89.8 万行后开始溢写） | 801.6 s |

索引恒为 **每向量行约 8,192 字节**，因此 `maintenance_work_mem` 必须大于最终索引体积，否则会溢写到磁盘——Postgres 会打出 `hnsw graph no longer fits into maintenance_work_mem after N tuples`，耗时明显变长。按 10 万 / 100 万 / 300 万规划 1 GB / 8 GB / 24 GB。超过约 200 万行后，单图在 16–32 GB 主机上已放不下，应改为按 scope 分片向量表（已在完整 300 万容量下实测，见 `docs/archive/2026-11-05_vector-sharding-3m.md`）。

**重跑是安全的。** `scripts/rebuild-vector-index.sh` 以索引的真实状态为准：

| 发现的状态 | 行为 |
|------------|------|
| 索引有效且 `FORCE=0` | 打印 `skip: … already valid` 并退出 0，不重建 |
| 索引无效（上次构建被打断留下） | 先 drop 再重建 |
| 没有索引 | 直接建 |
| 构建失败或被中断 | drop 半成品索引，退出 3 |

`CONCURRENTLY=1`（默认）在建图期间不阻塞 INSERT；`CONCURRENTLY=0` 更快，但会取 SHARE 锁，写入要等整次构建结束。`scripts/test-vector-index-rebuild.sh` 会在临时库上验证上述全部行为，改动建图路径后必须重跑。12 万行实测（`WORK_MEM=1GB`、2 workers）：

| 用例 | 结果 |
|------|------|
| 首次建图 | **48 s**，索引有效，983,048,192 B（8,192 B/行） |
| 重跑 | `skip: … already valid`，**1 s** 返回，不建图 |
| `pg_terminate_backend` 打断 | **退出 3**，半成品索引已删除，状态 `absent` |
| 打断后重跑 | **50 s** 恢复为有效索引 |
| 并发建图（`CONCURRENTLY=1`） | **918 次写入、0 失败**，p50 48 ms、p95 84 ms、max 206 ms（含 docker exec 往返），索引有效 |


#### 数据完整性巡检（可定时）

`scripts/data-integrity-audit.py` 对应用默认成立的不变量做**只读**扫描，报告 9 类问题，每类都带样本与可直接执行的修复语句：

| 检查项 | 抓什么 |
|--------|--------|
| `orphan_observations` / `orphan_summaries` | `conversation_id` 在 `mem_sessions` 里没有父行 |
| `scope_project_mismatch` | project scope 的 `scope_key` 与 `project_path` 不一致（该行会串到别的项目检索结果） |
| `conversation_project_mismatch` | observation 的 `project_path` 与会话不一致 |
| `embedding_state_mismatch` | `ready` 却没有向量（语义检索永远看不到），或 `failed`/`skipped` 却带着向量（永不重试） |
| `duplicate_hash_unsuppressed` | 同一会话、相同 `content_hash`、落在 30 秒去重窗口内 |
| `stuck_processing` | `processing` 超过 `--stuck-minutes`（默认 15 分钟） |
| `pending_backlog` | pending 数量超过 `--backlog-max`（100）或队头超过 `--backlog-age-minutes`（60 分钟） |
| `summary_snapshot_missing_obs` | summary 输入快照引用了已不存在的 observation |

默认是 **dry-run**：不写库，只打印修复 SQL 并写入 JSON 报告。`--apply` 仅执行安全修复（embedding 状态、卡住的队列行）；`--apply-destructive` 才会删除孤儿/重复行并改写 scope 链接，执行前必须人工复核。退出码：`0` 干净、`1` 有发现、`2` 无法巡检（容器/库/schema 问题）。

```bash
# 每天，只读，跑在线库
python3 scripts/data-integrity-audit.py --database claude_mem \
  --json "$HOME/.claude-mem/integrity-audit/audit-$(date +%Y%m%d_%H%M%S).json"

# 每周，跑备份脚本刚恢复出来的副本（有发现时该阶段退出码 10）
SOURCE_DB=claude_mem INTEGRITY_AUDIT=1 ./scripts/backup-restore-drill.sh
```

**多久跑一次**：在线库每天一次（10 万行亚秒级），因为 `stuck_processing` 与 `pending_backlog` 是前置指标；每周通过 `INTEGRITY_AUDIT=1` 跑一次恢复副本，证明备份里的数据也是一致的。每次恢复后、任何手工 SQL 操作后各补跑一次。

**放在哪里**：报告与备份报告放在一起——备份脚本触发时是 `${HOME}/.claude-mem/backup-drill/reports/integrity-drill-<时间戳>.json`；cron 触发时放 `${HOME}/.claude-mem/integrity-audit/`，用 `find ... -mtime +30 -delete` 清理 30 天前的报告。报告内嵌 `repair_sql`，排查不依赖本文档是否同步。

**发现后怎么处置**：先打一份 dump，读样本 id 与 `detail`，在恢复副本上用 `--apply` 验证修复 SQL 后再动生产。孤儿与重复行删除是破坏性操作；`conversation_project_mismatch` 和 `summary_snapshot_missing_obs` 只报告不自动修（哪一侧正确需要人判断），请手工修正或重新生成 summary。`pending_backlog` 不由巡检修复：去查 worker 日志与 provider 健康。检测逻辑由 `python3 scripts/test-data-integrity-audit.py` 用脏数据自测，任何 schema 变更后必须重跑并通过。

### 8.2 数据恢复

#### 完整恢复

```bash
# 1. 停止应用
docker compose stop claude-mem

# 2. 恢复数据库
gunzip -c backup_20260313_020000.sql.gz | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem

# 3. 启动应用
docker compose start claude-mem

# 4. 验证恢复
curl http://localhost:37777/actuator/health
```

#### 表级恢复

```bash
# 仅恢复观察表
docker exec -i cortex-ce-postgres psql -U postgres claude_mem << EOF
TRUNCATE TABLE mem_observations CASCADE;
EOF

gunzip -c backup.sql.gz | grep -A 1000000 "COPY mem_observations" | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem
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
# 注：卷名使用 COMPOSE_PROJECT_NAME 前缀（默认：目录名）。
# 运行 `docker volume ls | grep postgres` 查看实际卷名。
docker volume rm ${COMPOSE_PROJECT_NAME:-bluecortexce}_postgres_data

# 3. 启动服务
docker compose up -d postgres

# 4. 等待数据库就绪
sleep 10

# 5. 恢复数据
gunzip -c /backups/latest.sql.gz | \
  docker exec -i cortex-ce-postgres psql -U postgres claude_mem

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
curl http://localhost:37777/api/health

# 创建测试会话
curl -X POST http://localhost:37777/api/session/start \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test-session-001",
    "project_path": "/tmp/test-project"
  }'

# 记录观察
curl -X POST http://localhost:37777/api/ingest/observation \
  -H "Content-Type: application/json" \
  -d '{
    "content_session_id": "test-session-001",
    "project_path": "/tmp/test-project",
    "title": "部署验证",
    "content": "Test observation for deployment verification"
  }'

# 搜索观察（project 为必填参数）
curl "http://localhost:37777/api/search?project=/tmp/test-project&query=test&limit=10"

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

**最后更新**: 2026-05-04
**版本**: 0.1.0-beta
