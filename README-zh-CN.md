# Cortex Community Edition

> 🧠 Memory-Enhanced Agent System with Persistent Context

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-brightgreen.svg)](https://spring.io/projects/spring-boot)

中文 | [English](README.md)

Cortex Community Edition (cortexce) 是一个为 AI 助手提供持久化记忆和智能上下文管理的记忆增强系统。通过质量评分、智能精炼和上下文感知检索，帮助 AI 助手实现跨会话的经验积累和学习。

---

## 核心特性

- 🧠 **持久化记忆** - 跨会话保存和复用上下文信息
- 🔄 **智能质量评估** - 规则评分 + LLM 评分，自动评估记忆质量
- 🎯 **上下文感知检索** - 基于向量的语义搜索，精准定位相关信息
- 📊 **记忆演化机制** - 自动精炼、合并和优化记忆库
- 🔌 **RESTful API** - 简单易用的 HTTP 接口
- 🐘 **PostgreSQL + pgvector** - 可扩展的向量化存储方案

---

## 快速开始

### 前置要求

- Java 17 或更高版本
- PostgreSQL 16+ (with pgvector extension)
- Maven 3.8+ (for building)

### 安装步骤

1. **克隆代码库**

```bash
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd cortexce
```

2. **配置数据库**

```bash
# 创建 PostgreSQL 数据库
createdb cortexce

# 启用 pgvector 扩展
psql -d cortexce -c "CREATE EXTENSION vector;"
```

3. **配置环境变量**

```bash
cp .env.example .env
# 编辑 .env 文件，填入你的配置
```

4. **构建项目**

```bash
mvn clean install
```

5. **运行服务**

```bash
java -jar backend/target/cortexce-backend-*.jar
```

服务将在 `http://localhost:37777` 启动。

---

## 架构概览

Cortex 采用 "Thin Proxy + Fat Server" 架构，解决了 CLI Hook 超时问题：

```
┌─────────────────┐
│   CLI Hook      │
│  (wrapper.js)   │
└────────┬────────┘│         ┌──────────────────┐
         │ HTTP    │         │  Fat Server      │
         ▼         │         │  (Spring Boot)   │
┌─────────────────┐│         │                  │
│  Thin Proxy     │├────────▶│  - LLM 处理      │
│  (Express)      ││         │  - 向量嵌入      │
│  - 快速响应     ││         │  - 记忆精炼      │
│  - 请求转发     ││         │  - 质量评估      │
└─────────────────┘│         └──────────────────┘
                   │                   │
                   │                   ▼
                   │         ┌──────────────────┐
                   │         │  PostgreSQL      │
                   │         │  + pgvector      │
                   │         └──────────────────┘
                   │
```

### 核心组件

- **Thin Proxy**: 轻量级代理，快速响应 CLI Hook
- **Fat Server**: 核心业务逻辑，异步处理
- **PostgreSQL + pgvector**: 持久化存储和向量检索

---

## API 文档

### 核心端点

#### 1. 健康检查

```http
GET /api/health
```

#### 2. 观察记录

```http
POST /api/observations
Content-Type: application/json

{
  "session_id": "session-123",
  "project_path": "/path/to/project",
  "content": "观察内容...",
  "embedding": [0.1, 0.2, ...]
}
```

#### 3. 语义搜索

```http
GET /api/search?query=查询内容&project=/path/to/project&limit=10
```

#### 4. 记忆精炼

```http
POST /api/refine
Content-Type: application/json

{
  "project_path": "/path/to/project"
}
```

完整 API 文档请参考 [API.md](docs/API.md)

---

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DATABASE_URL` | PostgreSQL 连接字符串 | `jdbc:postgresql://localhost:5432/cortexce` |
| `DATABASE_USER` | 数据库用户名 | `postgres` |
| `DATABASE_PASSWORD` | 数据库密码 | - |
| `LLM_API_KEY` | LLM API 密钥 | - |
| `LLM_BASE_URL` | LLM API 地址 | - |
| `EMBEDDING_MODEL` | 嵌入模型名称 | `text-embedding-3-small` |

完整配置请参考 [`.env.example`](.env.example)

---

## 开发指南

### 项目结构

```
cortexce/
├── backend/           # Spring Boot 主应用
│   ├── src/main/java/
│   └── src/test/java/
├── proxy/             # Thin Proxy (Node.js)
├── openclaw-plugin/   # OpenClaw 集成插件
├── scripts/           # 工具脚本
└── docs/              # 文档
```

### 构建和测试

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 运行
java -jar backend/target/cortexce-backend-*.jar
```

详细开发指南请参考 [DEVELOPMENT.md](docs/DEVELOPMENT.md)

---

## 部署指南

### Docker 部署

```bash
# 构建镜像
docker build -t ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main .

# 运行容器
docker run -d \
  -p 37777:37777 \
  -e DATABASE_URL=... \
  -e LLM_API_KEY=... \
  ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main
```

详细部署指南请参考 [DEPLOYMENT.md](docs/DEPLOYMENT.md)

---

## 最终用户快速配置指南

本节指导你将 Claude Code 与 Cortex CE 记忆系统进行集成。

### 前置条件

- **Docker 和 Docker Compose** 已安装
- **Node.js** 已安装（运行 `node` 命令需要）
- **Claude Code** 已安装

### 预期目录结构

下文假设你将在用户主目录下创建一个子目录 `.cortexce` 部署 Cortex CE 记忆系统：

```
~/.cortexce/                         # 部署目录
├── .env                            # 环境变量配置
├── docker-compose.yml               # Docker Compose 配置
└── proxy/                          # Claude Code Hooks 代理
    ├── wrapper.js                   # 主入口脚本
    ├── package.json
    └── ...
```

### 步骤一：创建工作目录并配置环境变量

创建工作目录和 `.env` 文件：

```bash
mkdir -p ~/.cortexce
cat > ~/.cortexce/.env << 'EOF'
# 数据库配置
DB_PASSWORD=your_secure_password

# LLM 配置（Spring AI OpenAI 格式）
SPRING_AI_OPENAI_API_KEY=your_openai_key
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# 嵌入模型配置（Spring AI OpenAI 格式）
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_siliconflow_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024

# ⚡ 可选：关闭自演化记忆（记忆精炼）功能
# 该功能会自动精炼/合并记忆，但会消耗 LLM token。
# 设置为 'false' 可关闭此功能，节省 token 消耗。
MEMORY_REFINE_ENABLED=false
EOF
```

### 步骤二：拷贝必要文件

假设你已克隆本仓库到本地，目录为 `[git目录]`，进入工作目录并拷贝文件：

```bash
cd ~/.cortexce

# 拷贝 docker-compose.yml
cp [git目录]/docker-compose.yml .

# 拷贝 proxy 目录
cp -r [git目录]/proxy .

# 安装 proxy 依赖
cd proxy && npm install
```

### 步骤三：启动服务

```bash
cd ~/.cortexce

# 启动服务
docker compose up -d

# 或指定镜像
IMAGE_NAME=ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main docker compose up -d

# ARM64 Mac (Apple Silicon) 会自动拉取对应版本
# 如需强制使用 AMD64 (Intel Mac):
# docker pull --platform linux/amd64 ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main
```

等待服务启动完成，验证健康状态：

```bash
curl http://localhost:37777/actuator/health
```

应返回 `{"status":"UP",...}`。

### 步骤四：配置 Claude Code Hooks

编辑项目级配置文件 `.claude/settings.local.json`（如不存在则创建）：

```json
{
  "hooks": {
    "SessionStart": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-start --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "SessionEnd": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-end --url http://127.0.0.1:37777",
        "async": true
      }]
    }],
    "UserPromptSubmit": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js user-prompt --url http://127.0.0.1:37777",
        "async": true
      }]
    }]
  }
}
```

#### Hook 说明

| Hook | 触发时机 |
|------|----------|
| SessionStart | Claude Code 启动时 |
| PostToolUse | 工具执行后（Edit/Write/Read/Bash） |
| SessionEnd | 会话结束时 |
| UserPromptSubmit | 用户提交提示时 |

#### 可选：启用文件夹 CLAUDE.md 更新（如不需要可跳过）

如需自动更新子文件夹的 `CLAUDE.md` 文件（注入相关记忆），可添加 `--enable-folder-claudemd` 参数（注意：这会增加一些噪音，非必需不建议启用）。

### 步骤五：配置 MCP Server（如需主动检索记忆）

```bash
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

验证配置：

```bash
claude mcp list
```

或在 Claude Code 输入框中输入 `/mcp` 查看。

### 常用命令

```bash
cd ~/.cortexce

# 启动服务
docker compose up -d

# 重启服务
docker compose restart

# 查看日志
docker compose logs -f cortex-ce

# 停止服务
docker compose down

# 完全重置（包括数据）
docker compose down -v
```

### 故障排除

#### 无法连接 Java API

```bash
# 检查服务是否运行
curl http://localhost:37777/actuator/health

# 检查端口占用
lsof -i :37777
```

#### Hook 未触发

- 确认 `.claude/settings.local.json` 路径正确
- 确认 `matcher` 匹配工具名称
- 查看 Claude Code 日志

#### 数据库连接失败

- 确认 `.env` 中 `DB_PASSWORD` 已设置
- 确认 `.env` 中 `POSTGRES_PORT`（默认 5433，避免与本地 PostgreSQL 冲突）
- 等待 PostgreSQL 健康检查通过（首次启动需等待）

#### WebUI 无数据

- 检查 `/stream` SSE 端点是否正常：

```bash
curl -N http://localhost:37777/stream
```

- 确认数据库有数据：

```bash
docker exec cortex-ce-postgres psql -U postgres -d claude_mem -c "SELECT COUNT(*) FROM mem_observations;"
```

---

## IDE 集成

### Cursor IDE

 Cursor IDE 集成记忆系统配置文档：
- [中文版](proxy/CURSOR-INTEGRATION-zh-CN.md)
- [English版](proxy/CURSOR-INTEGRATION.md)

### OpenClaw

 OpenClaw 集成记忆系统配置文档：
- [中文版](openclaw-plugin/OPENCLAW-INTEGRATION-zh-CN.md)
- [English版](openclaw-plugin/OPENCLAW-INTEGRATION.md)

---

## 贡献指南

我们欢迎所有形式的贡献！

- 🐛 报告 Bug: [提交 Issue](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)
- 💡 提出新功能: [功能请求](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)
- 📝 改进文档: Fork 并提交 PR
- 🔧 贡献代码: 请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 致谢

本项目受到持久化记忆系统概念的启发，感谢所有为 AI 记忆系统发展做出贡献的研究者和开发者。

特别感谢：
- 持久化记忆系统的研究社区
- PostgreSQL 和 pgvector 团队
- Spring Boot 社区

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 联系方式

- GitHub Issues: [https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)
- 文档: 见 [docs/](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/docs) 文件夹
