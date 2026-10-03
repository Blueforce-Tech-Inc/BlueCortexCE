# 测试指南

> English version: [docs/TESTING.md](./TESTING.md)

## 概述

本文档描述 Cortex 社区版的测试方法。

## 测试类别

### 1. 端到端测试

位于 `scripts/` 目录：

| 脚本 | 说明 |
|------|------|
| `regression-test.sh` | 核心功能回归测试 |
| `thin-proxy-test.sh` | 精简代理集成测试 |
| `mcp-e2e-test.sh` | MCP 服务器端到端测试（SSE 模式） |
| `mcp-streamable-e2e-test.sh` | MCP 服务器端到端测试（Streamable HTTP 模式） |
| `docker-compose-test.sh` | Docker Compose 部署测试 |
| `docker-e2e-test.sh` | Docker 独立端到端测试 |
| `webui-integration-test.sh` | WebUI 集成测试 |

### 2. Phase 3 验收测试

位于 `scripts/` 目录：

| 脚本 | 说明 |
|------|------|
| `phase3-acceptance-test.sh` | Phase 3 userId 隔离 + extraction 功能验收测试（15 个测试函数） |

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
| `python-sdk-e2e-test.sh` | Python SDK 端到端测试 |
| `python-demo-e2e-test.sh` | Python Flask 演示 E2E 测试 |
| `demo-v14-test.sh` | Demo v14 功能测试 |
| `demo-v15-test.sh` | Demo v15 功能测试 |
| `demo-v15-extraction-test.sh` | Demo v15 extraction 功能测试 |
| `evo-memory-e2e-test.sh` | 进化记忆 E2E 测试 |
| `openclaw-plugin-test.sh` | OpenClaw 插件集成测试 |
| `codex-watcher-test.sh` | Codex CLI 监听器集成测试 |
| `export-test.sh` | 导出功能端到端测试 |
| `folder-claudemd-test.sh` | 文件夹 CLAUDE.md 更新功能测试 |
| `js-demo-e2e-test.sh` | JS/TS Express 演示 E2E 验收测试 |
| `evo-memory-value-test.sh` | 进化记忆商业价值演示测试 |

#### 3.5 其他测试工具

| 脚本 | 说明 |
|------|------|
| `seed-diverse-data.sh` | 为 WebUI 测试植入多样化测试数据（多种类型、概念、内容） |
| `test-llm-provider.sh` | LLM 提供商连接和响应验证测试 |
| `run-all-e2e.sh` | 编排脚本 — 一次运行全部 10 个本地 E2E 套件（不含 Docker 套件和 test-llm-provider.sh） |

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

> **PostgreSQL 端口**：上面的 `5432` 假设的是原生安装，或故障排查一节里用的
> `docker run -p 5432:5432`。本项目自己的 `docker compose up -d` 会把数据库发布到宿主机
> 端口 **5433**（`docker-compose.yml` 中的 `"${POSTGRES_PORT:-5433}:5432"`），该路径还需要
> 把 `SPRING_DATASOURCE_URL` 指向发布出来的端口。在断定数据库没起来之前，先确认自己用的是
> 哪一个——详见 `docs/DEPLOYMENT.md`。

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
| `DB_NAME` | claude_mem | 数据库名称 |
| `DB_USERNAME` | postgres | 数据库用户名 |
| `DB_PASSWORD` | - | 数据库密码 |
| `SPRING_AI_OPENAI_API_KEY` | - | OpenAI/DeepSeek API key |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | - | Embedding API key |

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

如果你是用本项目的 `docker compose up -d` 启动后端的，PostgreSQL **已经在 5433 上运行**，
数据也在那个容器里——上面这条 `docker run` 只会再起一个 5432 上的**空**数据库，让问题看起来
更严重。正确做法是把 `SPRING_DATASOURCE_URL` 指向
`jdbc:postgresql://127.0.0.1:5433/claude_mem`。

### 服务未运行

```bash
# 推荐：自动加载 .env、释放 37777 端口、等待健康检查通过
./scripts/start.sh

# 等价的手动方式 —— 但注意它不会读取 .env
cd backend
./mvnw spring-boot:run
```

> **`.env` 不会被自动加载。** Spring Boot 的配置里没有任何读取 `.env` 的机制
> （既无 dotenv 依赖，也没有 `spring.config.import`），所以 `./mvnw spring-boot:run`
> 只会用当前 shell 里已导出的变量启动服务。如果 API key 只存在于某个 `.env` 文件里，
> 服务仍然能起来，但第一次调用 LLM 或 embedding 时就会失败。要么先 `export` 这些变量，
> 要么用 `scripts/start.sh`：它的第一步就是加载 `.env`。
>
> **两个启动脚本读的不是同一个文件。** `scripts/start.sh` 会先切到 `backend/` 再读
> `backend/.env`；`scripts/start-all.sh` 则切到 `scripts/` 读 `../.env`，也就是
> `docker compose` 使用的仓库根 `.env`（模板见 `.env.docker`、`.env.example`）。
> 如果你照 compose 的说明只建了根目录的 `.env`，那么 `start.sh` 会在完全没有
> key 的情况下把后端启动起来。
>
> `scripts/start.sh --build` 会先重新构建 JAR，`--background` 则以后台方式启动并轮询
> `/api/health` 直到就绪。无论用哪种方式，后端监听的都是 **37777**，不是 8080。

### 测试失败

1. 检查服务器日志
2. 验证数据库连接
3. 确认 API keys 已配置
4. 查看测试输出中的具体错误

---

## 变更日志

| 日期 | 变更 |
|------|------|
| 2026-10-03 | 「服务未运行」一节只给出 `./mvnw spring-boot:run`，而该命令**不会**读取 `.env`——Spring Boot 配置中既无 dotenv 依赖也无 `spring.config.import`，key 只存在于 `.env` 文件里的用户会得到一个能启动、却在首次调用 LLM/embedding 时失败的服务。补充 `scripts/start.sh`（加载 `.env`、固定 37777、支持 `--build`/`--background`），并说明 `start.sh` 读的是 `backend/.env`、而 `start-all.sh` 与 `docker compose` 读的是仓库根 `.env`——只建了根 `.env` 的用户用 `start.sh` 会启动出一个没有 key 的后端；中英文同步更新 |
| 2026-10-02 | 在「前置条件」与「PostgreSQL 连接失败」中说明端口分野（`:5433`）——用 `docker compose up -d` 启动后端的用户若照排障里的 `docker run -p 5432:5432` 操作，会在 5432 上再起一个**空**数据库，而数据其实在 compose 容器的 5433 上。已核实 `run-all-e2e.sh` 确实运行 10 个本地套件、`phase3-acceptance-test.sh` 确实定义 15 个测试函数，两个计数均保持不变；中英文同步更新 |
| 2026-05-04 | 第 6 节修复 4 个环境变量错误——移除不存在的 `DB_HOST` 和 `SPRING_AI_MCP_SERVER_PROTOCOL`，修正 `DB_USER`→`DB_USERNAME` 和 `DB_PASS`→`DB_PASSWORD`，修正 `DB_NAME` 默认值 `claude_mem_dev`→`claude_mem`（与 docker-compose.yml 一致）；中英文同步更新 |
| 2026-05-03 | 在第 3 节 SDK 表格中新增 `go-sdk-unit-test.sh` 和 `codex-watcher-test.sh`（10→12 个脚本）；补充遗漏的 `python-sdk-e2e-test.sh`；中英文同步更新 |
| 2026-05-02 | 新增遗漏的「运行 Docker 部署测试」小节（第 5 节的第 5 个小节）；中英文小节结构对齐 |
| 2026-04-26 | 新增第 3 节：SDK 和演示集成测试（10 个脚本）；修复章节编号缺失问题（原缺少 ### 3，现为 1–8 连续编号）；注：`python-sdk-e2e-test.sh` 已存在但被遗漏 |
| 2026-04-03 | 新增 Phase 3 验收测试章节；在 E2E 表格中添加 webui-integration-test.sh 和 docker-e2e-test.sh |
