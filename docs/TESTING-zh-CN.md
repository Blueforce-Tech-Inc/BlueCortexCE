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
| `run-all-e2e.sh` | 编排脚本 — 一次运行此处定义的 10 个本地 E2E 套件（不含 Docker 套件和 test-llm-provider.sh）。**其中 5/10 `mcp-streamable-e2e-test.sh` 是条件执行的**：除非服务器在 `/mcp` 上暴露 Streamable HTTP（向 `/mcp` 发一个 `initialize` 请求必须返回 200），否则会被跳过。后端默认的 MCP 传输是 SSE，因此在默认安装下该编排脚本实际运行 **9** 个套件并报告 1 个跳过——**这是预期行为，不是失败**。 |

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

#### 首选入口：一条命令

```bash
./scripts/verify-local.sh
```

在干净的数据库上，这个脚本不需要手工步骤就能证明测试底座是绿的：

1. 定位一个真正提供 pgvector 的运行中 PostgreSQL 容器（以 `pg_available_extensions`
   为准，不看镜像 tag）。找不到容器是失败，不是静默跳过。
2. 删除并重建专用库 `cortexce_flyway_rehearsal`。
3. 启用 `vector` 扩展并断言其可用。
4. 启动应用一次让 Flyway 完成迁移，随后断言实际版本等于
   `backend/src/main/resources/db/migration` 中最新的文件，且
   `flyway_schema_history` 中没有失败记录。
5. 用一次 `mvn -B clean install` 跑完 L1–L4，`MEMSCOPE_*` 指向该库。
6. 执行 `python3 scripts/check-test-results.py --require-zero-skips
   --min-tests 360 --require-layers`。
7. 执行两项文档一致性检查：检查器自测，再用中英文 API 参考对比运行中的服务。
8. 对该服务跑 proxy E2E，`node --test` 只要报告有跳过就判失败（这是 L3 的 proxy 转发部分）。

每一步都会打印自己的名称。失败时脚本打印 `STEP FAILED: <步骤>` 与命令自身的
原因，以非零退出，绝不继续执行后续步骤去掩盖问题。可覆盖的变量：
`PG_CONTAINER`、`PG_PORT`、`PG_USER`、`PG_PASSWORD`、`TEST_DB`、
`VERIFY_MIN_TESTS`、`VERIFY_APP_PORT`、`VERIFY_E2E_PORT`、`VERIFY_LOG_DIR`。

下面的分步说明保留下来，用于单独复现某一个失败步骤。

#### 四个层次

测试套件分四层。`check-test-results.py` 从类名推断层次
（`com.ablueforce.cortexce.layers.L2*` → L2，`L3*` → L3，`L4*` → L4，其余 → L1），
`--require-layers` 会在某一层一个用例都没执行时判失败，因此「整层被删掉」不可能看起来是绿的。

| 层次 | 证明什么 | 由谁执行 | 单独重跑 |
|------|----------|----------|----------|
| **L1** 单元 + 集成（346 个用例） | 服务、控制器、实体、仓储契约，以及依赖模型的链路（离线替身）。 | `.layers.*` 之外的全部 Surefire 类 | `mvn -B test -Dtest='!L2HttpRegressionTest,!L3AcceptanceTest,!L4EndToEndTest'` |
| **L2** HTTP 回归（7 个用例） | 真实启动的应用（`RANDOM_PORT`）：`/v3/api-docs` 发布的每个操作都被调用一次；缺参数 → 400、旧路径 → 404、非法 UUID → 400、`limit`/`offset` 钳制、`{items,hasMore}` 分页信封。 | `L2HttpRegressionTest` | `mvn -B test -Dtest=L2HttpRegressionTest` |
| **L3** 验收（6 个用例） | 把文档声明的能力变成断言：Hook 生命周期、三类 ingest 写入、检索分支、context inject、settings/modes 字段、SSE 事件、MCP 工具列表加一次真实调用。每条断言在类 javadoc 中标注出处（`docs/API.md`、`AGENTS.md`、`docs/ARCHITECTURE.md`）。proxy 转发由 `verify-local.sh` 第 8 步覆盖。 | `L3AcceptanceTest` + proxy `npm test` | `mvn -B test -Dtest=L3AcceptanceTest`（再 `cd proxy && npm test`） |
| **L4** 端到端（1 个用例） | 真实服务 + 专用库上的一条完整用户路径：会话开始 → 写入 → 检索 → context inject → 会话结束 → 摘要，校验数据库终态与 SSE 事件。等待全部是基于条件的轮询，不使用 `sleep`。 | `L4EndToEndTest` | `mvn -B test -Dtest=L4EndToEndTest` |

L2–L4 需要 `MEMSCOPE_*` 变量（见下文）；未设置时本地会跳过而不是失败，CI 门禁会拒绝任何跳过：

```bash
export MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/cortexce_flyway_rehearsal"
export MEMSCOPE_TEST_DB_USER=postgres MEMSCOPE_TEST_DB_PASSWORD=123456
export MEMSCOPE_FLYWAY_TEST_URL="$MEMSCOPE_TEST_DB_URL"
export MEMSCOPE_FLYWAY_TEST_USER="$MEMSCOPE_TEST_DB_USER"
export MEMSCOPE_FLYWAY_TEST_PASSWORD="$MEMSCOPE_TEST_DB_PASSWORD"
cd backend
```

#### 前置条件

- 一个正在运行、且 pgvector 扩展**可用**的 PostgreSQL 16 容器
  （用 `pg_available_extensions` 核实，不要只凭镜像名判断——见「运行测试」里的建库步骤）
- Java 21+
- `.env` 中配置必要的 API keys

> **PostgreSQL 端口**：上面 `5432` 是本指南所用本地容器映射到宿主机的端口。
> 本项目自己的 `docker compose up -d` 会把数据库发布到宿主机端口 **5433**
> （`docker-compose.yml` 中的 `"${POSTGRES_PORT:-5433}:5432"`）；两条路径都可以，
> 只要 `SPRING_DATASOURCE_URL` 与 `MEMSCOPE_*` 指向同一个端口即可。
> 排查前先确认实际在跑什么，详见 `docs/DEPLOYMENT.md`。

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

测试脚本与后端对同一批设置读的是**不同的变量**——导出 `DB_USERNAME` 并不能让测试脚本用上它，
`scripts/README.md` 里就是这么写的（「Note: the variable is DB_PASS, not DB_PASSWORD」）。

| 变量 | 由谁读取 | 默认值 | 说明 |
|------|----------|--------|------|
| `SERVER_URL` | 测试脚本 | http://127.0.0.1:37777 | 服务器 URL。`mcp-e2e-test.sh` 的默认值是 `http://localhost:37777`；`phase3-acceptance-test.sh` 读的是 `BACKEND_URL`（默认值相同） |
| `DB_NAME` | `regression-test.sh` 与两个 Docker 套件 | `claude_mem_dev` | 数据库名。`regression-test.sh` 默认 `claude_mem_dev`，`docker-compose-test.sh` 默认 `claude_mem_compose_test`，`docker-e2e-test.sh` 默认 `claude_mem_test`。`claude_mem` 是 **docker-compose** 为应用自身数据库定的默认值，**不是**测试套件的默认值 |
| `DB_HOST` | `regression-test.sh` | 127.0.0.1 | 脚本连接 PostgreSQL 用的主机 |
| `DB_USER` | `regression-test.sh` 与两个 Docker 套件 | postgres | 脚本连接 PostgreSQL 用的用户名 |
| `DB_PASS` | `regression-test.sh` 与两个 Docker 套件 | `123456` | 脚本连接 PostgreSQL 用的密码；两个 Docker 套件默认 `testpassword123` |
| `DB_USERNAME` | **仅后端** | postgres | 后端自己的变量——`application.yml` 读 `${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:postgres}}`。**测试脚本不读它** |
| `DB_PASSWORD` | **仅后端** | `123456` | 后端自己的变量——`application.yml` 读 `${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:123456}}`。**测试脚本不读它** |
| `SPRING_AI_OPENAI_API_KEY` | 后端、两个 Docker 套件、`java-sdk-e2e-test.sh` | - | OpenAI/DeepSeek API key |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY` | 后端、两个 Docker 套件 | - | Embedding API key |
| `MEMSCOPE_TEST_DB_URL` | `mvn test`（`ImportServiceDatabaseTest`、`MemoryScopeServiceDatabaseTest`、`ObservationRepositoryScopeQueryTest`、`OfflineAiChainIntegrationTest`、`LlmProviderFailureTest`、`L2HttpRegressionTest`、`L3AcceptanceTest`、`L4EndToEndTest`） | - | 一个已迁移到最新 Flyway 版本的一次性 PostgreSQL + pgvector 数据库的 JDBC URL。未设置时 48 个 DB 相关测试全部跳过；`ImportServiceDatabaseTest`、`OfflineAiChainIntegrationTest`、`LlmProviderFailureTest` 与三个层次测试额外要求库名匹配 `*_flyway_rehearsal` |
| `MEMSCOPE_TEST_DB_USER` / `MEMSCOPE_TEST_DB_PASSWORD` | 同上 | postgres | `MEMSCOPE_TEST_DB_URL` 的凭据 |
| `MEMSCOPE_FLYWAY_TEST_URL` | `ConversationScopeFlywayDatabaseTest` | - | 一个一次性 `*_flyway_rehearsal` 数据库的 JDBC URL；该测试会自行执行迁移 |
| `MEMSCOPE_FLYWAY_TEST_USER` / `MEMSCOPE_FLYWAY_TEST_PASSWORD` | 同上 | postgres | `MEMSCOPE_FLYWAY_TEST_URL` 的凭据 |

```bash
# 复用本机已经在运行的 PostgreSQL 容器，在它内部建一个专用测试库。
# 以下命令可从项目根目录直接复制执行；若容器名/端口/凭据不同，
# 先用 `docker ps` 和 `docker inspect` 查出来再改这 4 个变量。
PG_CONTAINER=postgresql            # 已运行且带 pgvector 的容器
PG_PORT=5432                       # 该容器映射到宿主机的端口
PG_USER=postgres
PG_PASSWORD=123456
TEST_DB=cortexce_flyway_rehearsal  # 必须以后缀 _flyway_rehearsal 结尾

# 1. 确认容器，以及 pgvector 是否真的可用。
#    不要只看镜像名：postgres:16 镜像可能带、也可能不带 pgvector，
#    以 pg_available_extensions 的查询结果为准。
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i postgres
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -tAc \
  "SELECT default_version FROM pg_available_extensions WHERE name='vector'"

# 2. 创建专用数据库并启用扩展。不要复用业务库，测试会清空表。
#    先 DROP 再 CREATE，保证这段命令可以从零重复执行。
docker exec "$PG_CONTAINER" psql -U "$PG_USER" \
  -c "DROP DATABASE IF EXISTS $TEST_DB WITH (FORCE)"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -c "CREATE DATABASE $TEST_DB"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" \
  -c "CREATE EXTENSION IF NOT EXISTS vector"

# 3. 让后端对该库启动一次，由 Spring Boot 在启动时执行 Flyway 到 V20；
#    健康检查通过后立即停止进程。
#    （target/cortex-ce-*.jar 已存在时可跳过 package）
cd backend
mvn -B -DskipTests package
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
SPRING_DATASOURCE_USERNAME="$PG_USER" SPRING_DATASOURCE_PASSWORD="$PG_PASSWORD" \
SERVER_PORT=37888 java -jar target/cortex-ce-*.jar &
APP_PID=$!
until curl -sf http://127.0.0.1:37888/api/health >/dev/null; do sleep 1; done
kill "$APP_PID" && wait "$APP_PID" 2>/dev/null || true

# 期望输出 20
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"

# 4. 对该库跑完整测试套件：360 个用例、0 跳过。
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_TEST_DB_USER="$PG_USER" MEMSCOPE_TEST_DB_PASSWORD="$PG_PASSWORD" \
MEMSCOPE_FLYWAY_TEST_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_FLYWAY_TEST_USER="$PG_USER" MEMSCOPE_FLYWAY_TEST_PASSWORD="$PG_PASSWORD" \
mvn -B clean install
```

> 数据库必须已处于最新 Flyway 版本：这些测试断言的是 V20 conversation-scope 表结构。
> 使用 `MEMSCOPE_TEST_DB_URL` 的测试不会自行迁移；只有 `ConversationScopeFlywayDatabaseTest`
> 会迁移它自己的数据库。
>
> CI 会跑同一套测试，然后执行 `python3 scripts/check-test-results.py
> --require-zero-skips --min-tests 360 --require-layers`；因此「构建成功但 48 个
> DB 用例被跳过」或「某一层整个消失」都会直接失败。
> CI 使用自己的 `pgvector/pgvector:pg16` service 容器，而不是复用本地容器。

#### 三条大模型链路的离线验证

自动 observation 提取、自动摘要、语义检索这三条链路原本都依赖真实模型。
`backend/src/test/java/com/ablueforce/cortexce/offline/` 用测试作用域的
`@TestConfiguration`（`OfflineAiTestModels`）提供 `ChatModel` / `EmbeddingModel`
替身，因此**不需要 API Key、不需要外网**也能跑。生产配置完全未改动：
`SpringAiConfig` 仍按原逻辑创建真实模型与 `ChatClient`，替身只存在于测试源码，
且只在测试显式导入时生效。

| 链路 | 测试 | 关键断言 |
|------|------|----------|
| 自动 observation 提取 | `ingestProducesObservation` | `enqueueToolUse` → `processPendingMessage` 后 `mem_observations` 恰好多一行，标题/facts 来自替身，`embedding_1024` 为 1024 维；队列行终态 `processed` |
| 自动摘要 | `sessionEndProducesSummary` | `enqueueSessionSummaryJob` → `processSummaryJob` 后 `mem_summaries` 恰好多一行；job 终态 `processed` 且带 `result_summary_id`；会话终态 `completed` |
| 语义向量检索 | `embeddingAvailableSearchUsesVectorBranch` | `ViewerController.search` 返回 `strategy=hybrid`、`fell_back=false` 且命中该行；同一 query 不传向量时返回 `tsvector` 且**零行**，证明命中只能来自向量分支 |

替身是输入的纯函数：observation / summary 提示词返回固定 XML，任意文本经
SHA-256 扩展成 1024 维向量。测试不 sleep、不等待 `@Async` 事件监听器，而是
同步调用两个 worker 入口并直接断言队列状态，因此重复运行结果一致。与其他 DB
测试一样，仅在 `MEMSCOPE_TEST_DB_URL` 匹配 `*_flyway_rehearsal` 时执行。

```bash
# 复用第 4 步的 MEMSCOPE_* 变量，只跑这三条离线链路验证
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/cortexce_flyway_rehearsal" \
MEMSCOPE_TEST_DB_USER=postgres MEMSCOPE_TEST_DB_PASSWORD=123456 \
mvn -B -Dtest=OfflineAiChainIntegrationTest test
```

#### 供应商异常行为（401/403、429、超时、不可解析、空响应）

`LlmService.chatCompletionWithUsage` 不再把所有失败都折叠成空字符串，而是返回
带 `FailureKind` 的 `LlmResponse`；observation / 摘要 worker 据此执行统一策略：

| 类型 | 来源 | 是否重试 |
|------|------|----------|
| `AUTHENTICATION` | 401/403、`invalid api key`、`permission_denied`（会遍历整条 cause 链，HTTP 包装层藏不住状态码） | **否** —— 第一次即终态 |
| `RATE_LIMIT` | 429、`rate limit`、`too many requests`、`quota exceeded` | 有界重试（`claudemem.max-retries`，默认 3） |
| `TIMEOUT` | `SocketTimeoutException`、`TimeoutException`、`timed out` | 有界重试 |
| `UNAVAILABLE` | 5xx、`ConnectException`、`connection refused/reset`、Spring AI `TransientAiException` | 有界重试 |
| `INVALID_RESPONSE` | `ChatResponse` 为 null 或内容为空 | **否** —— 重复同一提示词无济于事 |
| `UNKNOWN` | 无法识别 | **否**（按永久失败处理） |

`LlmProviderFailureTest` 通过同步调用 worker 入口（不 sleep）断言三条保证：

- **可观测**：失败任务终态为 `failed`，写入 `retry_count` 与 `failed_at_epoch`，
  绝不会变成 `processed`。
- **有界**：瞬时失败用尽配置预算后停止；鉴权失败与确定性不可解析答案在第一次
  就终止，不会白烧三次相同重试（`AgentService.isRetryableException`、
  `SummaryJobService`）。
- **不污染后续**：去重查询排除 `failed`，下一次正常写入仍能成功，会话保持
  `active`，不会被这次失败破坏。

`chatCompletion(...)` 的签名与行为保持不变，新增的失败元数据是附加的，
因此没有任何对外端点发生变化。

```bash
# 复用同一批 MEMSCOPE_* 变量，只跑异常矩阵
mvn -B -Dtest=LlmProviderFailureTest test
```

#### 手动真实供应商冒烟（绝不进入 CI）

`scripts/real-llm-smoke.sh` 会消耗真实额度，因此只是手动步骤，`verify-local.sh`
与任何 workflow 都不会调用它。它会加载 `backend/.env` 但不打印任何值，检测到
`CI` 环境变量即拒绝运行；启动带真实 bean 的应用，最多 5 次调用
（2 次 chat + 3 次 embedding），客户端超时 30 秒，不重试：

1. `GET /api/test/embedding` —— 1 次 embedding，断言返回维度等于
   `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS`（1024），且
   `mem_observations.embedding_1024` 为 `vector(1024)`；
2. `GET /api/test/llm` —— 1 次 chat；
3. `POST /api/ingest/tool-use` —— worker 的真实 chat 调用及其 embedding，
   随后轮询 `mem_observations`（只有 `XmlParser` 接受答案时才会有行）；
4. `GET /api/search` —— 1 次 embedding，断言 `strategy=hybrid` 且命中；
5. `GET /api/context/inject`，再回读 `mem_pending_messages`，确认任务终态不是 `failed`。

任一探针失败（401/402/403/429/超时）时，脚本输出 HTTP 状态与供应商错误（key 已脱敏），
跳过剩余调用并以非零退出。`SMOKE_ONLY=embedding` 只跑第 1 步（1 次调用），
是验证新 embedding 供应商最省额度的方式。

供应商由配置显式指定，脚本不根据 key 猜测：

| `.env` / shell 变量 | 含义 |
|--------------------|------|
| `CLAUDEMEM_LLM_PROVIDER` | `openai`（默认）或 `anthropic`，决定 chat bean |
| `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL` | OpenAI 兼容 chat（`openai` 分支） |
| `ANTHROPIC_API_KEY`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_MODEL` | Anthropic 协议 chat（`anthropic` 分支），例如 MiniMax 的 `https://api.minimaxi.com/anthropic` |
| `SPRING_AI_OPENAI_EMBEDDING_API_KEY`、`..._BASE_URL`、`..._MODEL`、`..._DIMENSIONS` | embedding 供应商，与 chat 供应商相互独立 |
| `SPRING_AI_OPENAI_EMBEDDING_SEND_DIMENSIONS` | 默认 `false`，不发送 OpenAI 专属的 `dimensions` 字段；仅对真正支持该字段的模型（`text-embedding-3-*`）设为 `true` |

这些普通变量名在 `application.yml` 中显式映射到 Spring 属性
（`spring.ai.openai.api-key/base-url/chat.options.model`、
`spring.ai.anthropic.api-key/base-url/chat.options.model`、
`spring.ai.openai.embedding.*`），因为 Spring Boot 不会自动松散绑定它们。
单次运行可用 `SMOKE_PROVIDER=anthropic` 切换分支。402
`insufficient_balance_error` 表示鉴权已通过但账户余额不足以生成内容，
不是协议或配置错误。

**MiniMax 复验，2026-11-02：** 第一次更换后的 key 仍返回 HTTP 402
`insufficient_balance_error`（code 1008）。再次更换 key 后，
`SMOKE_PROVIDER=anthropic SMOKE_SKIP_PROBES=1` 在 `MiniMax-M3` 上端到端通过：
HTTP 200，回答被 `XmlParser` 接受（observation 标题
`Fixed PaymentService authorization failure for missing order IDs`）；入库向量为
**1024 维**；`GET /api/search` 返回 `strategy=hybrid`、`fell_back=false`、
`hits=1`，耗时 **0.131 秒**；`/api/context/inject` 返回 observation 标题与
`updateFiles`；队列终态 **`processed:1`**，无 failed/pending 残留。从队列
admission 到 observation 落库耗时 **2.72 秒**（chat + ingest embedding），
chat 本身的耗时未单独埋点。该次冒烟消耗 **3 次真实调用**（1 chat + 2 embedding），
在 5 次上限内。启动日志确认分支真实生效：
`Creating Anthropic-compatible ChatModel: baseUrl=https://api.minimaxi.com/anthropic,
model=MiniMax-M3` 与 `Creating ChatClient with model: AnthropicChatModel`。
切换本身用两次重启复验（V20 rehearsal 库、两个 key 同时存在）：
`provider=openai` 只创建 `OpenAiChatModel`（LongCat），`provider=anthropic`
只创建 `AnthropicChatModel`（MiniMax），两次均健康，无
`BeanDefinitionOverrideException`；重启检查未消耗模型调用。

#### 两种协议的切换方式

两种协议均已支持，且只靠配置切换，不改代码：

| 项目 | OpenAI 兼容（默认） | Anthropic 兼容 |
|------|--------------------|----------------|
| 切换 | `CLAUDEMEM_LLM_PROVIDER=openai` | `CLAUDEMEM_LLM_PROVIDER=anthropic` |
| Key | `OPENAI_API_KEY` | `ANTHROPIC_API_KEY` |
| Base URL | `OPENAI_BASE_URL`，例如 `https://api.longcat.chat/openai` | `ANTHROPIC_BASE_URL`，例如 `https://api.minimaxi.com/anthropic` |
| 模型 | `OPENAI_MODEL`，例如 `LongCat-2.5` | `ANTHROPIC_MODEL`，例如 `MiniMax-M3` |
| 实际请求 | `POST {base}/v1/chat/completions` | `POST {base}/v1/messages` |
| Embedding | 不随切换变化，始终走 OpenAI 兼容 embedding 分支 | 不随切换变化，同上 |

注意点：

- 修改 provider 或其 key 后必须重启应用；`ChatModel` 只在启动时构建一次，
  未实现热切换。
- 确认当前生效分支看启动日志：`Creating OpenAI-compatible ChatModel:
  baseUrl=…, model=…` 或 `Creating Anthropic-compatible ChatModel: baseUrl=…,
  model=…`。非 `prod` profile 下可用 `GET /api/test/llm` 做真实连通性检查。
- `GET /api/version` 只返回应用/JDK/Spring 版本，不报告 chat provider。
- 两个分支在 **bean 注册层**互斥：由 `claudemem.llm.provider` 决定注册哪个
  `@Bean`，未选中的分支根本没有 bean 定义（选中的分支若 key 为空则再被抑制）。
  `SpringAiConfigProviderBranchTest` 在真实 `ApplicationContext` 中离线固定该
  行为——两个 key 同时存在时仍只有一个 `ChatModel`，`getBean(ChatModel.class)`
  不会歧义——并固定 embedding 不随 chat provider 切换。
- Base URL 末尾不要再带 `/v1`；协议路径由 SDK 自行拼接。

### 7. MCP 协议自动检测

MCP E2E 测试脚本（`mcp-e2e-test.sh` 和 `mcp-streamable-e2e-test.sh`）**自动检测**服务器运行的协议：

- **SSE 模式**：`/sse` 返回 200，`/mcp` 返回 404
- **STREAMABLE 模式**：`/mcp` 返回 200，`/sse` 返回 404

统一脚本自动运行相应测试，无需手动选择协议！

- 测试会话 ID：`e2e-regression-{timestamp}`
- 测试项目：`/tmp/claude-mem-test-{pid}`

### 8. CI/CD 集成

GitHub Actions 工作流配置在 `.github/workflows/`：

- `docker.yml` — Docker 镜像构建和推送。
- `backend-tests.yml` — **Quality Gates**，判断测试底座是否真绿的关卡：
  - `backend`（JDK 21 与 JDK 25 矩阵）启动一个 `pgvector/pgvector:pg16` service
    容器，映射到宿主机 **5433**（GitHub runner 自带的 PostgreSQL 已占用 5432），
    用应用自身的 Flyway 配置迁移到 V20，执行 `mvn -B clean install`（不带
    `-DskipTests`），然后执行 `python3 scripts/check-test-results.py
    --require-zero-skips --min-tests 360 --require-layers`。任何一个 DB 用例被
    跳过、用例总数下降、或某一层没有执行任何用例，都会让 job 失败，因此
    「BUILD SUCCESS 但 48 个跳过」不可能通过 CI。
  - 仅 JDK 21 的 job 会在 37777 启动服务，运行
    `scripts/check-api-doc-surface.py`（API 参考文档与实际 REST/actuator/MCP
    接口逐一比对，中英文必须一致），再跑 proxy E2E（`npm ci && npm test`，
    带 `PROXY_E2E_*`）。当 `GITHUB_ACTIONS=true` 时 proxy 测试拒绝跳过。
  - `webui` 检出子模块，执行 `npm install --no-audit --no-fund` 与
    `npm run typecheck`。上游 Bun 测试套件**有意不在范围内**：它属于 WebUI
    fork，把本仓库的门禁绑在上游用例对某个 Bun 版本的兼容性上会阻塞无关
    改动。我们与 viewer 的契约改由后端测试固定（`WebUiWireContractTest`：
    `hasMore`、`updateFiles`、`CLAUDE_MEM_*`）。该 job 不修改子模块。

## 最佳实践

1. **幂等性**：测试可安全重复运行
2. **不自动清理**：测试数据保留以便调试
3. **使用 `--cleanup`**：完成后删除测试数据
4. **查看日志**：检查测试输出中的失败信息

## 故障排查

### PostgreSQL 连接失败

```bash
# 检查实际在运行的容器
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i postgres

# 复用已有容器，不要另起一个空库；建库步骤见「运行测试」
docker exec <container> psql -U postgres -tAc \
  "SELECT default_version FROM pg_available_extensions WHERE name='vector'"
```

如果你是用本项目的 `docker compose up -d` 启动后端的，PostgreSQL **已经在 5433 上运行**，
数据也在那个容器里——再起一个容器只会得到一个不同端口上的**空**数据库，让问题看起来更严重。
正确做法是把 `SPRING_DATASOURCE_URL` 指向 `jdbc:postgresql://127.0.0.1:5433/claude_mem`。

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
| 2026-11-02 | **Golden 样例集输出质量评估与 MiniMax 最终复验。** 新增仅手动执行、在 CI 下拒绝运行的评估工具（`scripts/eval-model-quality.sh` + `GoldenSetEvaluator`，`EVAL_MAX_CALLS` 默认上限 20），对 **12 条真实感 tool-use 轨迹**打分，覆盖 bug 修复、重构、测试新增、配置变更、安全修复、文档更新，以及 2 条应跳过的低价值变更；每条含期望（是否产 observation / 是否 skip、type、title 关键词、facts/concepts 关键词）。打分逻辑为纯离线 `GoldenSetScorer`，由 6 个单测覆盖。LongCat 实测：**12/12 次调用，0 失败、0 格式错误；契约合格率 92%，skip 判定准确率 92%（TP/FP/FN/TN 2/1/0/9），type 100%，title 完全命中 44%，facts 54%/94%，concepts 57%/83%**。唯一的漏产是一条时区小回归测试被跳过；title 未命中属改写措辞，不是格式错误。报告见 `docs/archive/2026-11-02_model-output-quality-eval.md`。MiniMax 在 key 再次更新后复验**端到端通过**：HTTP 200，回答被 `XmlParser` 接受，入库向量 1024 维，`GET /api/search` 返回 `strategy=hybrid`/`fell_back=false`/`hits=1`，耗时 0.131 秒；`/api/context/inject` 返回 observation 标题与 `updateFiles`；队列终态 `processed:1`；**3 次真实调用**，队列 admission 到落库 2.72 秒。随后用 `EVAL_PROVIDER=anthropic` 对同样 12 条样例跑 `MiniMax-M3`（**12 次调用**）：契约 83%，skip 判定准确率 83%（TP/FP/FN/TN **0/0/2/10**，两条低价值轨迹都产出了 observation），type 90%（一条 `test-add` 被判为 `bugfix`），title 完全命中 20%，facts 65%/90%，concepts 52%/80%，0 格式错误；报告见 `docs/archive/2026-11-02_model-output-quality-eval-minimax.md`。用例数 354 → **360** / 0 跳过（新增 6 个离线打分单测）；`--min-tests` 354 → 360。中英文同步更新 |
| 2026-11-02 | **双协议 provider、LongCat 真实链路、embedding 请求修复。** LongCat（OpenAI 兼容，`https://api.longcat.chat/openai`，`LongCat-2.5`）跑通完整真实路径：提取 chat → `XmlParser` 接受回答 → observation 落库并写入 **1024 维**向量 → `GET /api/search` 返回 `strategy=hybrid`、`fell_back=false`、命中 1 条（0.11 秒）→ `/api/context/inject` 含 observation 标题与 `updateFiles` → 队列表终态 `processed`，非 `failed`。embedding 探针用 `BAAI/bge-m3` 返回 **1024 维、0.29 秒**。由此产生两处修复：(1) OpenAI 专属的 `dimensions` 字段被 SiliconFlow 拒绝（`400 code 20015`），改为由 `spring.ai.openai.embedding.send-dimensions` 显式开启（默认 `false`）；(2) 提取提示词让跳过的模型「不必输出」，而解析器只认 `<skip>`，导致纯文本跳过被判为 malformed——两个模板现要求 `<skip>reason</skip>`，并补齐 `<subtitle>` / `<narrative>` 闭合标签。`OPENAI_API_KEY` 已映射进 `spring.ai.openai.api-key`，普通变量名真正生效。openai/anthropic 切换现由 **bean 注册层**决定（`@ConditionalOnProperty` 绑 `claudemem.llm.provider`），而不是靠方法返回 null：两个 key 都存在时，旧写法仍会留下两个 `ChatModel` 定义，`getBean(ChatModel.class)` 会抛 `NoUniqueBeanDefinitionException`——由新增的上下文级测试抓出。新增 `SpringAiConfigProviderBranchTest`（5 个用例：互斥、真实 Spring 上下文中只存在一个 bean、空 key 抑制、embedding 不随 chat provider 切换）与 `XmlParserTest`（4 个用例：围栏内 observation、`<skip>`、无标签纯文本拒绝、未闭合标签）。文档补上 openai/anthropic 切换对照表、重启要求与确认当前分支的方法。MiniMax 用当时更新的 `ANTHROPIC_API_KEY` 重新实测，仍返回 **`402 insufficient_balance_error`（code 1008）**，那次没有可解析回答，Anthropic 链路未跑通；随后再次更换 key 的复验已端到端通过，记录见上一条。Anthropic bean 本身已由启动日志确认生效；两次重启的切换检查通过（`provider=openai` 只创建 `OpenAiChatModel`，`provider=anthropic` 只创建 `AnthropicChatModel`，两次均健康），无 `BeanDefinitionOverrideException`。用例数 345 → 354 / 0 跳过，`--min-tests` 345 → 354。中英文同步更新 |
| 2026-11-02 | 为三条依赖大模型的链路（自动 observation 提取、自动摘要、语义检索）补上测试作用域离线替身（`OfflineAiTestModels` 的 `ChatModel` + `EmbeddingModel`）与 `OfflineAiChainIntegrationTest`。生产配置未改动。实测 `cortexce_flyway_rehearsal` 上 319 个用例 / 0 失败 / 0 跳过；不设 `MEMSCOPE_*` 时为 319 个用例 / 22 跳过。CI 门禁的 `--min-tests` 由 300 调至 319；中英文同步更新 |
| 2026-11-02 | 第 8 节：补全 `backend-tests.yml` Quality Gates（JDK 21/25 的 DB 全量测试 + 零跳过门禁、API 文档一致性、proxy E2E）与 `webui` typecheck job，并写明上游 Bun 套件为何不在范围内、viewer 契约改由 `WebUiWireContractTest` 固定。第 6 节建库步骤加入可重复执行的 `DROP DATABASE ... WITH (FORCE)`。已在本机已运行的 `postgres:16` 容器（vector `0.8.2`）上复验：JDK 21 与 JDK 25 均为 316 个用例 / 0 失败 / 0 跳过；中英文同步更新 |
| 2026-11-02 | 第 6 节：把「再 `docker run -p 5432:5432 ...` 起一个新容器」改成可直接复制执行的流程——复用本机已在运行的 PostgreSQL 容器，在其中创建专用库 `cortexce_flyway_rehearsal`，用 `pg_available_extensions` 核实 pgvector，迁移到 V20，再用 `MEMSCOPE_*` 跑 `mvn -B clean install`。同时纠正「本机容器没有 pgvector」的错误判断：本机 `postgres:16` 容器报告 vector `0.8.2`，镜像 tag 不能作为证据。已在该库实测 313 个用例 / 0 失败 / 0 跳过（新增 `WebUiWireContractTest` 后为 316）；中英文同步更新 |
| 2026-10-20 | 第 6 节：表里把**后端**的 `DB_USERNAME` / `DB_PASSWORD` 写成了测试脚本的变量——脚本读的是 `DB_USER` / `DB_PASS`，`scripts/README.md` 里明写着（「Note: the variable is DB_PASS, not DB_PASSWORD」）；同时把 `DB_NAME` 的默认值写成了 docker-compose 的 `claude_mem` 而非各套件的 `claude_mem_dev`，并删掉了 `DB_HOST`——而 `regression-test.sh:41` 至今仍在读它。现表已逐行标明**由谁读取**与真实默认值，并补回 `DB_HOST` / `DB_USER` / `DB_PASS` 三行。此条**补充而非改写**下方的 2026-05-04 条目：那次为后端改了变量名，却没注意到这张表讲的是测试变量，于是把 `DB_HOST` 当作「不存在的」删掉，而脚本仍在读它；中英文同步更新 |
| 2026-10-03 | 「服务未运行」一节只给出 `./mvnw spring-boot:run`，而该命令**不会**读取 `.env`——Spring Boot 配置中既无 dotenv 依赖也无 `spring.config.import`，key 只存在于 `.env` 文件里的用户会得到一个能启动、却在首次调用 LLM/embedding 时失败的服务。补充 `scripts/start.sh`（加载 `.env`、固定 37777、支持 `--build`/`--background`），并说明 `start.sh` 读的是 `backend/.env`、而 `start-all.sh` 与 `docker compose` 读的是仓库根 `.env`——只建了根 `.env` 的用户用 `start.sh` 会启动出一个没有 key 的后端；中英文同步更新 |
| 2026-10-02 | 在「前置条件」与「PostgreSQL 连接失败」中说明端口分野（`:5433`）——用 `docker compose up -d` 启动后端的用户若照排障里的 `docker run -p 5432:5432` 操作，会在 5432 上再起一个**空**数据库，而数据其实在 compose 容器的 5433 上。已核实 `run-all-e2e.sh` 确实运行 10 个本地套件、`phase3-acceptance-test.sh` 确实定义 15 个测试函数，两个计数均保持不变；中英文同步更新 |
| 2026-05-04 | 第 6 节修复 4 个环境变量错误——移除不存在的 `DB_HOST` 和 `SPRING_AI_MCP_SERVER_PROTOCOL`，修正 `DB_USER`→`DB_USERNAME` 和 `DB_PASS`→`DB_PASSWORD`，修正 `DB_NAME` 默认值 `claude_mem_dev`→`claude_mem`（与 docker-compose.yml 一致）；中英文同步更新 |
| 2026-05-03 | 在第 3 节 SDK 表格中新增 `go-sdk-unit-test.sh` 和 `codex-watcher-test.sh`（10→12 个脚本）；补充遗漏的 `python-sdk-e2e-test.sh`；中英文同步更新 |
| 2026-05-02 | 新增遗漏的「运行 Docker 部署测试」小节（第 5 节的第 5 个小节）；中英文小节结构对齐 |
| 2026-04-26 | 新增第 3 节：SDK 和演示集成测试（10 个脚本）；修复章节编号缺失问题（原缺少 ### 3，现为 1–8 连续编号）；注：`python-sdk-e2e-test.sh` 已存在但被遗漏 |
| 2026-04-03 | 新增 Phase 3 验收测试章节；在 E2E 表格中添加 webui-integration-test.sh 和 docker-e2e-test.sh |
