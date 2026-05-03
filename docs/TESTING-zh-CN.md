# 测试指南

> English version: [docs/TESTING.md](./TESTING.md)

## 概述

本文档描述 Cortex 社区版的测试方法。

## 测试类别

### 1. 端到端测试

位于 `scripts/` 目录：

| 脚本 | 说明 | 行数 |
|------|------|------|
| `regression-test.sh` | 核心功能回归测试 | 1535 |
| `thin-proxy-test.sh` | 精简代理集成测试 | 775 |
| `mcp-e2e-test.sh` | MCP 服务器端到端测试（SSE 模式） | 555 |
| `mcp-streamable-e2e-test.sh` | MCP 服务器端到端测试（Streamable HTTP 模式） | 292 |
| `docker-compose-test.sh` | Docker Compose 部署测试 | 546 |
| `docker-e2e-test.sh` | Docker 独立端到端测试 | 701 |
| `webui-integration-test.sh` | WebUI 集成测试 | 229 |

### 2. Phase 3 验收测试

位于 `scripts/` 目录：

| 脚本 | 说明 | 行数 |
|------|------|------|
| `phase3-acceptance-test.sh` | Phase 3 userId 隔离 + extraction 功能验收测试（15 个测试函数） | 714 |

**前置条件：** 后端运行在 37777 端口，测试项目干净。

```bash
# 从项目根目录
./scripts/phase3-acceptance-test.sh
```

### 3. SDK 和演示集成测试

位于 `scripts/` 目录：

| 脚本 | 说明 |
|------|------|
| `go-sdk-e2e-test.sh` | Go SDK 端到端测试 |
| `go-sdk-unit-test.sh` | Go SDK 单元测试（所有子模块：root + dto + eino + genkit + langchaingo） |
| `java-sdk-e2e-test.sh` | Java SDK 端到端测试 |
| `js-sdk-e2e-test.sh` | JavaScript SDK 端到端测试 |
| `python-demo-e2e-test.sh` | Python SDK 演示测试 |
| `demo-v14-test.sh` | Demo v14 功能测试 |
| `demo-v15-test.sh` | Demo v15 功能测试 |
| `demo-v15-extraction-test.sh` | Demo v15 extraction 功能测试 |
| `evo-memory-e2e-test.sh` | 进化记忆 E2E 测试 |
| `openclaw-plugin-test.sh` | OpenClaw 插件集成测试 |
| `codex-watcher-test.sh` | Codex CLI 监听器集成测试 |

**前置条件：** 与回归测试相同（后端运行，数据库已配置）。

```bash
# 运行指定 SDK 测试
./scripts/go-sdk-e2e-test.sh

# 运行所有演示测试
./scripts/demo-v15-test.sh
```

### 4. Git 子模块设置（WebUI）

本项目使用 git 子模块引入 WebUI。构建前需初始化子模块：

```bash
# 从项目根目录
git submodule update --init --recursive
```

### 5. 运行测试

#### 前置条件

- PostgreSQL 16 + pgvector 运行在 localhost:5432
- Java 21+
- `.env` 中配置必要的 API keys

#### 运行回归测试

```bash
cd scripts
./regression-test.sh
```

**选项：**

| 选项 | 说明 |
|------|------|
| `--skip-build` | 跳过 Maven 构建（假设 JAR 已存在） |
| `--cleanup` | 测试完成后清理测试数据 |
| `--parallel` | 并行运行独立测试 |
| `--verbose` | 显示详细输出 |
| `--help, -h` | 显示帮助信息 |

**示例：**

```bash
# 使用现有 JAR 运行测试
./regression-test.sh --skip-build

# 显示详细输出
./regression-test.sh --verbose

# 测试后清理数据
./regression-test.sh --cleanup
```

#### 运行 Thin Proxy 测试

```bash
./thin-proxy-test.sh
```

#### 运行 MCP 端到端测试

```bash
./mcp-e2e-test.sh
```

#### 运行 Docker 部署测试

```bash
# Docker Compose 部署测试
./scripts/docker-compose-test.sh

# Docker 独立端到端测试
./scripts/docker-e2e-test.sh
```

### 6. 测试环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_URL` | http://127.0.0.1:37777 | 服务器 URL |
| `DB_HOST` | 127.0.0.1 | 数据库主机 |
| `DB_NAME` | claude_mem_dev | 数据库名称 |
| `DB_USER` | postgres | 数据库用户 |
| `DB_PASS` | 123456 | 数据库密码 |
| `SPRING_AI_OPENAI_API_KEY` | - | OpenAI/DeepSeek API key |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | - | Embedding API key |
| `SPRING_AI_MCP_SERVER_PROTOCOL` | SSE | MCP 协议（SSE 或 STREAMABLE） |

### 7. MCP 协议自动检测

MCP E2E 测试脚本（`mcp-e2e-test.sh` 和 `mcp-streamable-e2e-test.sh`）**自动检测**服务器运行的协议：

- **SSE 模式**：`/sse` 返回 200，`/mcp` 返回 404
- **STREAMABLE 模式**：`/mcp` 返回 200，`/sse` 返回 404

统一脚本自动运行相应测试，无需手动选择协议！

- 测试会话 ID：`e2e-regression-{timestamp}`
- 测试项目：`/tmp/claude-mem-test-{pid}`

### 8. CI/CD 集成

GitHub Actions 工作流配置在 `.github/workflows/`：

- `docker.yml` - Docker 镜像构建和推送

## 最佳实践

1. **幂等性**：测试可安全重复运行
2. **不自动清理**：测试数据保留以便调试
3. **使用 `--cleanup`**：完成后删除测试数据
4. **查看日志**：检查测试输出中的失败信息

## 故障排查

### PostgreSQL 连接失败

```bash
# 检查 PostgreSQL 状态
docker ps | grep postgres

# 启动 PostgreSQL
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=123456 pgvector/pgvector:pg16
```

### 服务未运行

```bash
# 启动服务
cd backend
./mvnw spring-boot:run
```

### 测试失败

1. 检查服务器日志
2. 验证数据库连接
3. 确认 API keys 已配置
4. 查看测试输出中的具体错误

---

## 变更日志

| 日期 | 变更 |
|------|------|
| 2026-05-03 | 在第 3 节 SDK 表格中新增 `go-sdk-unit-test.sh` 和 `codex-watcher-test.sh`（9→11 个脚本）；中英文同步更新 |
| 2026-04-26 | 新增第 3 节：SDK 和演示集成测试（9 个脚本）；修复章节编号缺失问题（原缺少 ### 3，现为 1–8 连续编号） |
| 2026-04-03 | 新增 Phase 3 验收测试章节；在 E2E 表格中添加 webui-integration-test.sh 和 docker-e2e-test.sh |
