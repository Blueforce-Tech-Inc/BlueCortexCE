# Cortex Community Edition

> 🧠 **你的 AI 助手每次会话都辛苦工作。但当你关闭窗口后，它就忘得干干净净。**
>
> Cortex CE 为 Claude Code（以及任何兼容 MCP 的 AI 工具）提供跨会话的持久化记忆。采用稳定的 "Thin Proxy + Async Backend" 架构 — 让你的会话永远不阻塞、不卡顿、不丢工作。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Website](https://img.shields.io/badge/website-cortex.ablueforce.com-blue)](https://cortex.ablueforce.com)

中文 | [English](README.md)

---

## 为什么选择 Cortex CE？

| | **Cortex CE** |
|---|---|
| Hook 响应 | 薄代理 — 始终 <1s 响应 |
| 会话阻塞 | 完全异步 — 会话无需等待 |
| 多客户端 | Claude Code、Cursor、VS Code、任意 MCP 客户端 |
| 许可证 | **MIT — 无部署限制** |
| 存储 | PostgreSQL + pgvector (生产级) |

---

## 核心特性

- 🧠 **持久化记忆** — 跨会话和项目保存和复用上下文
- ⚡ **非阻塞架构** — 薄代理 Hook 1s 内返回；所有重活异步运行
- 🔍 **混合搜索** — 全文检索 (PostgreSQL tsvector) + 向量搜索 (pgvector)
- 🔌 **多客户端 MCP 支持** — 兼容 Claude Code、Cursor 和任意 MCP 工具
- 🐘 **生产级存储** — PostgreSQL + pgvector
- 🔓 **MIT 许可证** — 个人和商业用途免费

---

## 快速开始

### 方式一：Docker（推荐 — 最快启动方式）

```bash
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE
cp .env.example .env
# 编辑 .env 配置文件

docker compose up -d

# 验证服务运行
curl http://localhost:37777/api/health
```

**5 分钟内启动成功！** 🎉

### 方式二：手动部署（需要 Java 21+、PostgreSQL 16+）

```bash
# 前置要求
- Java 21+
- PostgreSQL 16+ (需安装 pgvector 扩展)
- Maven 3.8+

# 克隆并构建
git clone https://github.com/Blueforce-Tech-Inc/BlueCortexCE.git
cd BlueCortexCE/backend
mvn clean install

# 运行
java -jar target/cortex-ce-*.jar
```

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
cp [git目录]/BlueCortexCE/docker-compose.yml .

# 拷贝 proxy 目录
cp -r [git目录]/BlueCortexCE/proxy .

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
curl http://localhost:37777/api/health
```

应返回 `{"status":"UP"}`。

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

### 步骤五：配置 MCP Server（如需主动检索记忆）

```bash
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

验证配置：

```bash
claude mcp list
```

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
```

---

## 架构设计

```
┌─────────────────┐
│   CLI Hook      │  ← 1秒内返回 {"queued":true}
│  (wrapper.js)   │    永不阻塞你的会话
└────────┬────────┘
         │ HTTP (fire-and-forget)
         ▼
┌─────────────────┐         ┌──────────────────┐
│  Thin Proxy     │         │  Fat Server      │
│  (Express)      │────────▶│  (Spring Boot)   │
└─────────────────┘         │  - LLM 处理      │
                            │  - 向量嵌入       │
                            └────────┬─────────┘
                                     │
                            ┌────────▼─────────┐
                            │  PostgreSQL      │
                            │  + pgvector      │
                            └──────────────────┘
```

### 核心组件

- **Thin Proxy**：轻量级 Node.js 代理，CLI Hook 响应快速（<1s）
- **Fat Server**：Spring Boot 后端，异步处理 LLM/向量嵌入
- **PostgreSQL + pgvector**：统一存储向量、关系数据和全文检索

---

## 常见痛点 vs 我们的解决方案

| 常见痛点 | 我们的解决方案 |
|----------|---------------|
| Hook 超时 | **Thin Proxy 架构** — 始终 <1s 响应 |
| 运维复杂 | **单一 PostgreSQL** — 向量 + 关系 + 全文检索 |
| SDK 依赖 | **无 SDK** — 直接 API 调用 |
| 崩溃丢数据 | **Crash Recovery** — 待处理队列 + 自动重试 |
| 向量维度受限 | **多维度向量** — 768/1024/1536 灵活切换 |

---

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_PASSWORD` | PostgreSQL 密码 | - |
| `SPRING_AI_OPENAI_API_KEY` | LLM API 密钥 | - |
| `SPRING_AI_OPENAI_BASE_URL` | LLM API 端点 | - |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 向量模型 | `BAAI/bge-m3` |
| `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` | 向量维度 | `1024` |
| `MEMORY_REFINE_ENABLED` | 启用记忆演化 | `true` |

完整配置请参考 [`.env.example`](.env.example)

---

## API 概览

### 核心端点

| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/ingest/tool-use` | POST | 记录工具执行 |
| `/api/ingest/session-end` | POST | 会话结束处理 |
| `/api/memory/experiences` | POST | 检索经验 (ExpRAG) |
| `/api/memory/icl-prompt` | POST | 构建 ICL 提示 |
| `/api/memory/refine` | POST | 触发记忆精炼 |

完整 API 文档请参考 [API.md](docs/API.md)

---

## IDE 集成

### Claude Code

通过 CLI Hooks 集成。详见上方的[快速配置指南](#快速开始)。

### Cursor IDE

详见 [Cursor IDE 集成指南](proxy/CURSOR-INTEGRATION-zh-CN.md)

### OpenClaw

详见 [OpenClaw 集成指南](openclaw-plugin/OPENCLAW-INTEGRATION-zh-CN.md)

---

## 开发指南

### 构建与测试

```bash
mvn compile    # 编译
mvn test       # 运行测试
mvn package    # 构建 jar 包
```

### 项目结构

```
cortexce/
├── backend/           # Spring Boot 应用 (Java 21)
├── proxy/             # Thin Proxy (Node.js)
├── openclaw-plugin/   # OpenClaw 集成
└── docs/              # 文档
```

---

## 故障排除

### 服务无响应
```bash
curl http://localhost:37777/api/health
lsof -i :37777  # 检查端口占用
```

### Hook 未触发
- 确认 `.claude/settings.local.json` 路径正确
- 确认 `matcher` 匹配你使用的工具名称

### 数据库连接失败
- 确认 `.env` 中 `DB_PASSWORD` 已设置
- 默认 `POSTGRES_PORT` 为 `5433`（避免与本地 PostgreSQL 冲突）

### 无记忆数据
```bash
curl -N http://localhost:37777/stream
docker exec cortex-ce-postgres psql -U postgres -d claude_mem \
  -c "SELECT COUNT(*) FROM mem_observations;"
```

---

## 规划路线

### ✅ 已完成 (Cortex CE)
- [x] 核心薄代理 + 异步后端架构
- [x] PostgreSQL + pgvector 混合搜索
- [x] Claude Code Hooks 集成
- [x] MCP SSE 服务器
- [x] Docker 部署

### 🚧 开发中 (Cortex CE)
- [ ] 一键安装程序
- [ ] WebUI 改进

### 🔒 企业版功能（即将推出）
- 团队共享记忆层
- 多租户支持
- 云托管服务

---

## 贡献指南

我们欢迎任何形式的贡献！

### 🐛 报告问题
- [GitHub Issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)

### 💡 功能请求
- [GitHub Discussions](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions)

### 🔧 代码贡献
```bash
# 1. Fork 项目
# 2. 创建功能分支
git checkout -b feature/amazing-feature

# 3. 提交更改
git commit -m 'Add amazing feature'

# 4. 推送到 GitHub
git push origin feature/amazing-feature

# 5. 提交 Pull Request
```

### 📝 文档改进
- 完善 API 文档
- 翻译成其他语言
- 纠正拼写错误

---

## 许可证

MIT 许可证 — 个人和商业用途免费。详见 [LICENSE](LICENSE)。

---

## 联系我们

- 🌐 网站: [https://cortex.ablueforce.com](https://cortex.ablueforce.com)
- 💬 GitHub Discussions: [https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/discussions)
- 🐛 问题反馈: [https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)

---

*由 [Blueforce Tech Inc.](https://ablueforce.com) ❤️ 构建*
