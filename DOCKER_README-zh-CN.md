# Cortex 社区版 Docker 部署

> English version: [DOCKER_README.md](./DOCKER_README.md)

## 快速开始

```bash
# 1. 复制环境变量模板
cp .env.docker .env

# 2. 编辑 .env，填入你的 API keys
vim .env

# 3. 启动服务（使用 ghcr.io 预构建镜像）
docker compose up -d

# 4. 检查健康状态
curl http://localhost:37777/api/health

# 5. 查看日志
docker compose logs -f
```

## 使用自定义镜像

默认使用 GitHub Container Registry 的预构建镜像：

```bash
# 默认镜像
docker compose up -d
```

使用自定义镜像版本：

```bash
# 使用指定版本
IMAGE_NAME=ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:sha-abc123 docker compose up -d

# 使用本地镜像（本地构建后）
IMAGE_NAME=cortex-ce:local docker compose up -d
```

## 服务

| 服务 | 主机端口 | 容器端口 | 说明 |
|------|----------|----------|------|
| PostgreSQL | 5433 | 5432 | 数据库 + pgvector |
| CortexCE | 37777 | 37777 | REST API & MCP Server |

## 配置

### 必填环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_NAME` | PostgreSQL 数据库名 | `claude_mem` |
| `DB_USERNAME` | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | -（必填） |
| `SPRING_AI_OPENAI_API_KEY` | OpenAI/DeepSeek API key | -（必填） |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | Embedding API key（如 SiliconFlow） | - |

### 可选环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `IMAGE_NAME` | Docker 镜像 | `ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main` |
| `SPRING_PROFILES_ACTIVE` | Spring profile（`dev`/`prd`） | `prd` |
| `SPRING_AI_OPENAI_BASE_URL` | OpenAI 兼容 API 端点 | `https://api.openai.com` |
| `SPRING_AI_OPENAI_CHAT_MODEL` | 聊天模型 | `gpt-4o` |
| `SPRING_AI_OPENAI_EMBEDDING_BASE_URL` | Embedding API 端点 | `https://api.openai.com` |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | Embedding 模型 | `text-embedding-3-small` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | Embedding 维度 | `1536` |
| `CLAUDE_MEM_MODE` | 应用模式（`code`/`default`） | `code` |
| `CLAUDEMEM_LLM_PROVIDER` | LLM 提供商（`openai`/`anthropic`） | `openai` |
| `SPRING_AI_ANTHROPIC_API_KEY` | Anthropic API key（provider=anthropic 时） | - |
| `SPRING_AI_ANTHROPIC_BASE_URL` | Anthropic API 端点 | `https://api.anthropic.com` |
| `SPRING_AI_ANTHROPIC_CHAT_MODEL` | Anthropic 模型名 | `claude-sonnet-4-5` |
| `MEMORY_REFINE_ENABLED` | 启用记忆自我进化 | `true` |
| `JAVA_OPTS` | JVM 选项 | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` |
| `SERVER_PORT` | 应用在主机上的端口 | `37777` |
| `POSTGRES_PORT` | PostgreSQL 在主机上的端口 | `5433` |

## 健康检查

```bash
# 应用健康检查（推荐）
curl http://localhost:37777/api/health

# Readiness 检查
curl http://localhost:37777/api/readiness

# PostgreSQL 连接检查
docker compose exec postgres pg_isready -U postgres
```

## 日志查看

```bash
# 实时查看所有服务日志
docker compose logs -f

# 仅查看应用日志
docker compose logs -f cortex-ce

# 仅查看数据库日志
docker compose logs -f postgres
```

## 数据持久化

数据通过 Docker volume 持久化：

- `claude-mem-db`：PostgreSQL 数据目录
- `claude-mem-logs`：应用日志目录

## 停止服务

```bash
# 停止服务（保留数据卷）
docker compose down

# 停止服务并删除数据卷（⚠️ 会丢失所有数据）
docker compose down -v
```

## 故障排查

### 服务启动失败

```bash
# 1. 检查容器状态
docker compose ps

# 2. 查看详细日志
docker compose up

# 3. 检查环境变量是否正确配置
docker compose exec cortex-ce env | grep SPRING
```

### 数据库连接问题

```bash
# 1. 确认 PostgreSQL 已就绪
docker compose exec postgres pg_isready -U postgres

# 2. 测试连接
docker compose exec postgres psql -U postgres -d claude_mem -c "SELECT 1;"
```

### 端口冲突

如果 37777 或 5433 端口已被占用：

```bash
# 使用不同端口（编辑 .env）
SERVER_PORT=37778
POSTGRES_PORT=5434

# 然后重启
docker compose down && docker compose up -d
```

## 构建本地镜像

如需从源码构建镜像：

```bash
# 1. 安装依赖
./scripts/prebuild-webui.sh

# 2. 构建镜像
docker build -t cortex-ce:local -f Dockerfile .

# 3. 使用本地镜像
IMAGE_NAME=cortex-ce:local docker compose up -d
```

## 环境变量文件示例

`.env.docker` 模板内容：

```bash
# 数据库
DB_NAME=claude_mem
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password_here

# Spring Profile
SPRING_PROFILES_ACTIVE=prd

# OpenAI / DeepSeek
SPRING_AI_OPENAI_API_KEY=your_api_key_here
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key_here

# 可选：切换到 Anthropic
# CLAUDEMEM_LLM_PROVIDER=anthropic
# SPRING_AI_ANTHROPIC_API_KEY=your_anthropic_key_here
```

## 安全建议

- 生产环境务必修改 `DB_PASSWORD` 为强密码
- API keys 不要提交到版本控制系统
- 生产环境使用 `SPRING_PROFILES_ACTIVE=prd`
- 考虑限制数据库端口（5433）的外部访问
