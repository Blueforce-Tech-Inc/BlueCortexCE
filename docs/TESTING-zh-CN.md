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
| `soak-test.py` | 手动一小时混合速率长稳测试（真实供应商、真实 PostgreSQL）：固定速率 ingest + search，每 5 分钟抓一次 Prometheus 采样，并机械判定堆增长、线程增长、连接池饱和、累计 failed 与卡 processing。绝不进入 CI。 |
| `eval-retrieval-quality.py` | 手动检索质量评测（真实 embedding、一次性数据库）：逐条候选生成规则给出 hit@1 / hit@5 / MRR，生产 SQL 原样复制。绝不进入 CI。 |
| `upgrade-rehearsal.sh` | 手动老库升级演练：让 Flyway 停在 V12，灌入带 embedding 与中文的 V13 之前形态数据，原地升级到 V25，校验行数 / id / V13 关联 / 中文检索，再恢复 V12 dump 作为回滚证明。绝不进入 CI。 |
| `hook-payload-e2e.sh` | 手动 Hook 端到端：用真实 Claude Code 载荷（UserPromptSubmit / PostToolUse / SessionEnd）经 `proxy/wrapper.js` 转发，校验字段映射、落库、重复 PostToolUse 去重、summary 生成，以及后端不可达时的优雅失败。绝不进入 CI。 |
| `webui-browser-e2e.sh` | 查看器级浏览器端到端：在一次性数据库上启动真实后端、灌入 60 条 observation，再用无头 Chromium（Playwright）验证内置查看器能渲染卡片、能读到 `files_modified`、按 `hasMore` 翻完整个信息流、不经刷新合并 SSE 广播、刷新后看到加载后新建的记录、设置开关在「修改 → 保存 → 刷新」后保持、后端要求 token 而浏览器不带 token 时给出可读 401 错误（绝不显示成空数据），以及后端停服再重启后的可见错误态（不卡加载）与恢复。Playwright 仅为测试依赖。该脚本是 `verify-local.sh` 第 9 步（未安装 Playwright 时 SKIP），也是 `browser-e2e` CI job 的执行体；CI 中未安装直接失败而不是跳过。 |
| `check-viewer-bundle.sh` | 比对提交在 `backend/.../static` 的查看器与 `webui/src` 的全新构建结果；构建在临时副本中进行，不改动子模块。构建使用 `scripts/viewer-build` 中带锁文件的固定工具链：子模块本身没有锁文件，`npm install` 会解析到更新的 react 补丁版本，从而改变压缩后的字节。退出码：0 一致、1 漂移（打印逐文件哈希与重建步骤）、2 固定工具链缺失（`cd scripts/viewer-build && npm ci`）。由 `verify-local.sh` 第 9 步开头与 `webui` CI job 调用。 |
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
2. 为本次运行创建唯一命名的临时库
   （`cortexce_verify_<时间戳>_<PID>_flyway_rehearsal`），迁移后使用，退出时删除。
   绝不复用已存在的共享库：本轮曾有 65 个 DB 测试因为共享库被无关项目迁移过
   （V47–V49 租户字段，`tenant_id NOT NULL`）而误报。`_flyway_rehearsal` 后缀是
   DB 测试的准入条件。
3. 启用 `vector` 扩展并断言其可用。
4. 启动应用一次让 Flyway 完成迁移，随后断言实际版本等于
   `backend/src/main/resources/db/migration` 中最新的文件，且
   `flyway_schema_history` 中没有失败记录。
5. 用一次 `mvn -B clean install` 跑完 L1–L4，`MEMSCOPE_*` 指向该库。
6. 执行 `python3 scripts/check-test-results.py --require-zero-skips
   --min-tests 580 --require-layers --require-baseline`。下限是硬性
   **不得低于** 值（当前 580 个用例）；baseline 额外提供按层棘轮，任何一层
   悄悄减少用例都会失败。随后执行评估下限门禁与检索质量回放门禁。

门禁在以下任一情况失败：有用例被跳过；总执行数低于 580；某一层一个用例都没执行；
L1–L4 中任何一层的执行数低于 `scripts/test-baseline.json` 记录。只有在新增用例后，
才应同时抬高下限并重写 baseline（`--write-baseline`）。
7. 执行两项文档一致性检查：检查器自测，再用中英文 API 参考对比运行中的服务。
8. 对该服务跑 proxy E2E，`node --test` 只要报告有跳过就判失败（这是 L3 的 proxy 转发部分）。
9. 校验仓库中提交的查看器 bundle 与 `webui/src` 的全新构建逐字节一致
   （`scripts/check-viewer-bundle.sh` 在临时副本中构建，绝不修改子模块），
   再用真实 Chromium（Playwright）驱动该 bundle：页面渲染、卡片上的
   `files_modified`、`hasMore` 分页、页面开着时通过 API 写入并仅靠 SSE 出现的
   新记录、刷新后可见、设置开关的往返保存、后端要求 token 而浏览器不带 token
   时给出可读的 401 错误（绝不会显示成空数据），以及后端停服再重启后的可见错误
   态（不卡在加载动画）与恢复。未安装 Playwright/Chromium 或
   `scripts/viewer-build/node_modules` 时该步打印 `SKIP` 并继续；`REQUIRE_BROWSER_E2E=1`
   会把该 SKIP 变成失败，CI 的独立 job 始终要求浏览器执行。

每一步都会打印自己的名称。失败时脚本打印 `STEP FAILED: <步骤>` 与命令自身的
原因，以非零退出，绝不继续执行后续步骤去掩盖问题。可覆盖的变量：
`PG_CONTAINER`、`PG_PORT`、`PG_USER`、`PG_PASSWORD`、`TEST_DB`（固定使用指定库，
不自动生成）、`VERIFY_KEEP_DB`（保留自动生成的库以便检查）、
`VERIFY_MIN_TESTS`、`VERIFY_APP_PORT`、`VERIFY_E2E_PORT`、
`VERIFY_BROWSER_E2E_PORT`、`REQUIRE_BROWSER_E2E`、`VERIFY_LOG_DIR`。

下面的分步说明保留下来，用于单独复现某一个失败步骤。

#### 四个层次

测试套件分四层。`check-test-results.py` 从类名推断层次
（`com.ablueforce.cortexce.layers.L2*` → L2，`L3*` → L3，`L4*` → L4，其余 → L1），
`--require-layers` 会在某一层一个用例都没执行时判失败，因此「整层被删掉」不可能看起来是绿的。

| 层次 | 证明什么 | 由谁执行 | 单独重跑 |
|------|----------|----------|----------|
| **L1** 单元 + 集成（488 个用例） | 服务、控制器、实体、仓储契约，以及依赖模型的链路（离线替身）。 | `.layers.*` 之外的全部 Surefire 类 | `mvn -B test -Dtest='!L2HttpRegressionTest,!L3AcceptanceTest,!L4EndToEndTest'` |
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

# 期望输出 db/migration 中的最新版本（当前为 23）
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$TEST_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"

# 4. 对该库跑完整测试套件：472 个用例、0 跳过。
MEMSCOPE_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_TEST_DB_USER="$PG_USER" MEMSCOPE_TEST_DB_PASSWORD="$PG_PASSWORD" \
MEMSCOPE_FLYWAY_TEST_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$TEST_DB" \
MEMSCOPE_FLYWAY_TEST_USER="$PG_USER" MEMSCOPE_FLYWAY_TEST_PASSWORD="$PG_PASSWORD" \
mvn -B clean install
```

> 数据库必须已处于最新 Flyway 版本：这些测试断言的是 V20 conversation-scope 表结构。
> 使用 `MEMSCOPE_TEST_DB_URL` 的测试不会自行迁移；`ConversationScopeFlywayDatabaseTest`
> 与 V20 冲突演练会各自创建并迁移一次性数据库。
>
> CI 会跑同一套测试，然后执行 `python3 scripts/check-test-results.py
> --require-zero-skips --min-tests 580 --require-layers --require-baseline`；因此
> 「构建成功但有用例被跳过」「某一层整个消失」或「某层用例数低于 baseline」都会直接失败。
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
    用应用自身的 Flyway 配置迁移到最新版本，执行 `mvn -B clean install`（不带
    `-DskipTests`），然后执行 `python3 scripts/check-test-results.py
    --require-zero-skips --min-tests 580 --require-layers --require-baseline`。
    任何一个 DB 用例被跳过、用例总数下降、或某一层低于 baseline，都会让 job 失败，
    因此「BUILD SUCCESS 但有跳过」不可能通过 CI。
  - 仅 JDK 21 的 job 会在 37777 启动服务，运行
    `scripts/check-api-doc-surface.py`（API 参考文档与实际 REST/actuator/MCP
    接口逐一比对，中英文必须一致），再跑 proxy E2E（`npm ci && npm test`，
    带 `PROXY_E2E_*`）。当 `GITHUB_ACTIONS=true` 时 proxy 测试拒绝跳过。
  - `webui` 检出子模块，执行 `npm install --no-audit --no-fund` 与
    `npm run typecheck`。上游 Bun 测试套件**有意不在范围内**：它属于 WebUI
    fork，把本仓库的门禁绑在上游用例对某个 Bun 版本的兼容性上会阻塞无关
    改动。我们与 viewer 的契约改由后端测试固定（`WebUiWireContractTest`：
    `hasMore`、`updateFiles`、`CLAUDE_MEM_*`）。该 job 不修改子模块；它还会安装
    `scripts/viewer-build` 中的固定工具链并运行 `scripts/check-viewer-bundle.sh`，
    因此 `backend/.../static` 里的过期 bundle 会让 CI 失败（最近一次通过：
    [41805175336](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41805175336)）。
  - `browser-e2e` 用打包好的 jar 连一个 `pgvector/pgvector:pg16` service（宿主机
    **5433**；`PG_CONTAINER=''` 让脚本改用 `psql` 客户端而非 `docker exec`），
    用 `npx playwright install --with-deps chromium` 安装 Playwright 1.59.1 与
    Chromium，然后运行 `scripts/webui-browser-e2e.sh`（脚本会启动 jar 三次：健康
    状态下的渲染/翻页/SSE/设置、要求 token 而浏览器不带 token、以及停服再重启）。
    缺少 Playwright 会直接让 job 失败而不是跳过；断言失败打印
    `FAIL: ASSERTION FAILED: ...`，浏览器异常打印 `FAIL: PAGE ERROR: ...`。
    最近一次通过的运行：
    [41805175336](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41805175336)
    （2026-11-04）。

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
| 2026-11-05 | **瓶颈在响应体，不在查询：向量退出线上载荷。** `ObservationEntity` 用 `@JsonProperty` 暴露了 `embedding_768`/`embedding_1024`/`embedding_1536`，所有观察记录端点都会序列化三份浮点数组：顺序探针实测每个响应体 **125.2 KB**，其中 **79.3%** 是向量，`/api/observations` p95 为 **293.9 ms**。三个列现已改为 `@JsonIgnore`。观察记录的其余字段与硬契约（`hasMore`、`updateFiles`、`CLAUDE_MEM_*`）不变，`ImportService` 仍通过自己的 record 写入向量，导入不会丢向量。`WebUiWireContractTest` 新增第 5 个用例：先把三个向量都填进实体再序列化，断言单行与 `GET /api/search` 信封都不含向量，同时 `id`/`content_hash`/`platform_source`/`memory_scope_type` 仍在——重新加回 `@JsonProperty` 无法靠 null 空过。改动前后证据：`docs/archive/2026-11-05_response-payload-before.md` 与 `…-after.md`。用例 579 → **580**（L1 545 → 546），0 跳过；`--min-tests` 579 → 580（文档里的下限此前漂到了 528）。中英文同步 |
| 2026-11-04 | **第 18 轮：统一错误形态、可选 token 与有数据的候选池拒绝。** *候选池：* 不假设第 17 轮定位的根因（相关记录在融合 top-40 之外），而是实测。`eval-retrieval-quality.py` 新增 `--pool-depths`；90 条真实查询在 `LIMIT 40/100/200` 下 hit@1 **0.833 / 0.833 / 0.833**、hit@5 **0.911 / 0.911 / 0.911**、7 天衰减后的 MRR **0.866 / 0.867 / 0.868**——全部落在 0.011（一条查询）的噪声区间内。**1,000,198 行**下延迟 p95 为 2.00 / 2.11 / 2.32 ms，远低于 50 ms 预算，因此瓶颈不在延迟；该改动以「质量无提升」为由拒绝（`docs/archive/2026-11-04_retrieval-pool-round18.md`）。*可选鉴权：* `cortexce.security.api-token`（环境变量 `CORTEXCE_API_TOKEN`）默认为空，本地用法不变；配置后 `/api/**` 写操作与 `GET /api/logs/**` 要求 `Authorization: Bearer <token>`，使用填充缓冲上的 `MessageDigest.isEqual` 做常量时间比较，缺失或错误 token 返回 401，带 `WWW-Authenticate` 与统一信封。`ApiTokenAuthFilterTest`（5 条）覆盖未配置放行、无 token 401、错 token 401、正确放行与作用域规则；`L2ApiTokenAuthTest`（6 条）验证真实接线，包括正确 token 能走到参数校验。文档明确写明**对外暴露前必须配置 token**。*错误信封：* 三种形态收敛为一种——`{"error": {"code", "message", "details"?}, "message"}`；`GlobalExceptionHandler` 统一渲染框架层失败，44 处 handler 改用 `ErrorResponses`；顶层 `message` 镜像保证 `webui/src/ui/viewer/utils/save-error.ts`（读取字符串字段）不破坏。HTTP 状态码语义不变。`check-api-doc-surface.py` 现在既校验线上错误体符合信封，也校验中英文文档中的每个错误示例，并新增自检用例：拒绝裸字符串、缺少顶层镜像、未知 code。*SSE：* `L2SseContractTest` 固定 `initial_load` / `processing_status` / `new_prompt` / `new_observation` / `new_summary` 五个事件名及其载荷字段，改名或删字段会先让测试失败，而不是静默到达 Viewer。用例 514 → **528**（L1 504 / L2 17 / L3 6 / L4 1），0 跳过；中英文同步更新 |
| 2026-11-04 | **第 17 轮：可观测、可估算、有边界。** *Prometheus：* 引入 `micrometer-registry-prometheus`，新增 `CortexMetrics`（`MeterBinder`），暴露 `cortexce.queue.depth{status=pending|processing|failed|stale}`、`cortexce.embedding.backlog`、`cortexce.worker.processing`、`cortexce.llm.calls{outcome,failure_kind}`、`cortexce.llm.retries{failure_kind}`、`cortexce.search.requests{path}`、`cortexce.search.duration{path}`（vector/lexical/hybrid/filter/recent，含分位数）；队列 gauge 在抓取时读库，异常时发布 NaN 而不让整次抓取失败。`SpringAiConfig` 挂上 `RetryMetricsListener`（`onError` 里 `retryCount < maxAttempts` 即“将被重试”），`LlmService.classify` 改为 public，重试与失败共用同一套 failure kind。`/actuator` 只暴露 health/metrics/info/prometheus，env/beans/configprops 返回 404。L2 测试启动应用、真实抓取端点、断言指标名与标签，并验证一次 ingest 改变 pending gauge、一次 search 改变 `path=hybrid`。*迁移预检：* `scripts/migration-preflight.sh` 统计行数、堆与索引体积，输出全表重写/分阶段耗时、锁级别与 DIRECT / MAINTENANCE WINDOW / PHASED ONLINE 三档结论；`--calibrate` 克隆真实表（含全部索引）到临时表实测两条路径。10 万行校准：全表重写实测 7,201 ms vs 模型 7,655（**+6.3%**），分阶段 11,815 ms vs 11,590（**−1.9%**）；系数 6,657 / 998 / 10,189 / 1,400 ms per 100k。在 100 万行库上投影 **76.6 s 锁窗口** 与 **115.9 s 分阶段**，并明确输出 MAINTENANCE WINDOW REQUIRED。*100 万行容量：* 1,000,198 行时堆 976.8 MB、索引 1,033.2 MB（V24 token GIN 167 MB，project/scope btree 122–132 MB）；三条列表路径均未越过 2× p95 拐点（recent 3.32、deep_page 3.05、type_filter 2.94、query 5.94 ms）；ingest 56.82 行/秒，0 重复哈希、0 卡住。对 1024 维 HNSW 做 2 万行探针：**7.8 kB/行**（推算 100 万行 7.8 GB）、建索引 **0.88 ms/行**（推算约 15 分钟；默认 64 MB `maintenance_work_mem` 下图已放不下），因此**单库推荐上限为 100 万行全量 embedding（约 13.8 GB）**；ANALYZE 0.94 s、VACUUM ANALYZE 12.6 s。*检索第 17 轮：* 以已上线的 0.833 为基线，按 intent/type 加权下降 0.133（标题动词只是弱的类型代理），top-20 的 body/title embedding 重排下降 0.155 / 0.377；通用中英同义词扩展（37 个词条，90 条中命中 62 条）只多命中 1 条，达到 **0.844**，落在 ±1 条的 embedding 抖动内，因此不上线。同义词 + 近重复合并 + 7 天衰减可达 **0.867 / 0.933 / 0.894**，但该合并会对 76/90 条查询隐藏相关记录，属于产品取舍而非检索调参，未上线。基线 15 条未命中里有 5 条的相关记录根本不在 40 条候选池内（候选生成是下限）。用例 511 → **514**（L1 497 / L2 10 / L3 6 / L4 1），0 跳过；中英文同步更新 |
| 2026-11-04 | **第 16 轮：多节点、可恢复、可运营。** *type_filter 拐点：* 10 万行 p95 29.38 ms 的根因是 `memory_scope_id IN (SELECT ... WHERE a OR b)`——规划器无法把 scope 列变成索引条件，只能先对 `type` 建位图、与 scope 集合 hash join、再排序（`Bitmap Heap Scan -> Hash Join -> Sort -> Limit`）。把 scope 谓词改成两个标量子查询的 OR（`InitPlan` 得到常量 scope id）后，规划器可用一段有序索引区间并在 `LIMIT` 处停止；V25 新增 `idx_observations_scope_type_created (memory_scope_id, type, created_at_epoch DESC, id DESC)`。10 万行 type_filter p95 从 **29.38 → 6.58 ms**（有索引 0.14 ms，无索引 17.33 ms，`Limit -> Index Scan`）；DB 测试在 `enable_seqscan=off` 下断言索引存在且计划走索引。*V24 存量代价：* `ADD COLUMN ... GENERATED ... STORED` 在 `ACCESS EXCLUSIVE` 下全表重写——10 万行 **4337 ms**（需停机窗口）；普通 GIN 索引 **731 ms** 持 `SHARE` 锁，阻塞并发插入 **209 ms**；`CREATE INDEX CONCURRENTLY` 消除该阻塞（909 ms）；分阶段方案（普通列 0.89 ms、10 批 × 1 万回填 7187 ms、并发索引 734 ms）把最长锁降到 **0.89 ms**。结论：≤10 万行走维护窗口，≥100 万行走分阶段。*双实例：* 两个 worker 消费同一队列 60 个任务（50/10 领取），**0 重复内容哈希、0 卡住、0 遗留 pending、0 次 stale 回收**；70 秒的 provider 调用超过 1 分钟 stale 阈值，因此新增 `ProcessingHeartbeat`，每 5 秒为本实例在途 id 刷新 `started_processing_at_epoch`（`heartbeatProcessing`），实例崩溃后停止心跳、任务仍可被回收——DB 测试钉住“心跳阻止误回收”和“完成后不再刷新”。*备份恢复：* `scripts/backup-restore-drill.sh` 对 137 MB / 10 万行库执行 `pg_dump`（**478 ms**，压缩后 **4.0 MiB**）、恢复到新库（**5744 ms**）、启动 jar 做 Flyway 校验（**2176 ms**），比对四表计数与 schema history，并要求中文 `检索` 查询命中数一致（**100,000**）且探针 observation 标题一致；V25 索引与 V24 token 在恢复后仍在。*检索：* 语料 60 → **90 条 query**（83 条中英混合 / 7 条中文；真实 transcript 中没有纯英文请求），每条带语言标签、`asked_at` 时间，observation 带真实时间戳；评测改为只允许“请求发生时已存在”的记录参与排序（消除未来泄漏）。Hybrid hit@1 **0.778** / hit@5 **0.900** / MRR **0.821**，纯向量 0.700 / 0.889 / 0.767。融合分上叠加 7 天半衰期的时间衰减后 hit@1 **0.833** / hit@5 **0.911** / MRR **0.866**（30 天为 0.811 / 0.900 / 0.849），因此以 `cortexce.search.recency-half-life-days:7` 上线，并由 DB 测试证明较新的同标题副本排在 30 天前副本之前。近重复合并（同标题归一化只保留最新）得分 0.789 / 0.922 / 0.842，但对 **90 条中的 76 条**隐藏了相关记录，判定不可用并拒绝。用例 508 → **511**（L1 497 / L2 7 / L3 6 / L4 1），0 跳过；中英文同步更新 |
| 2026-11-04 | **第 15 轮：中文全文检索不再恒空，供应商调用有了上限。** `english` tsvector 在 60 条中文 query 上全部返回 0 条，因为一段连续中文只被切成一个 lexeme。V24 新增 `cortex_tokens()`（中文重叠二元组、ASCII 标识符整词）写入生成列 `search_tokens tsvector`（title A / content B / 文件路径 C）并建 GIN 索引——不依赖编译扩展，镜像里没有 `pg_bigm`/`zhparser`/`pgroonga`。`pg_trgm` 先实测后淘汰：`word_similarity` hit@5 **0.20**，`LIKE ANY(二元组)` 在 10 万行 **7.63 s**，而 token coverage 为 **0.33 s**。60 条 query 实测：纯全文 hit@1 **0.767** / hit@5 **0.867** / MRR **0.805**（原为 0 / 0 / 0，60 条全空）；生产 hybrid（加权 RRF，全文权重 2.0）hit@1 **0.733** / hit@5 **0.883** / MRR **0.799**，对照纯向量 0.617 / 0.817 / 0.696；additive 融合把 hit@5 冻结在 0.817，因此权重经过扫描（1.0–3.0、k 10/60、pool 40），2.0/60/40 最优。`scripts/degraded-search-e2e-test.sh` 在 `env -i` 下启动 jar、不配任何 embedding key，断言日志出现 `No EmbeddingModel configured`，写入一条中文 observation，要求查询返回 `strategy=tsvector`、`fell_back=false`、该行排第一，且不存在的 token 返回 0 条。LongCat 分支改为自建 JDK 客户端：连接 **5 秒**、读取 **120 秒**、最多 **3 次**、退避 **2 秒 / 5 秒**，只重试超时/429/5xx/连接重置，`HttpTimeoutException` 归类为 `TIMEOUT`/retry；用永不响应的本地服务实测，在 800 ms 读超时下 **6 秒内**失败且恰好 3 次请求。A11 不受影响（其 60 次调用 0 重试，p95 38.9 秒低于 120 秒读上限），并用真实 smoke 走通 chat + ingest + hybrid 检索。**10 万行**（`2026-11-04_load-test-100k.md`）时 `type_filter` 第一个越过 2× 拐点（p95 **29.38 ms**，1k 时 4.94），recent 2.53 / deep_page 2.36 / hybrid query 5.16 未越过；ingest 51.45 行/秒，0 重复哈希、0 卡住。用例 502 → **508**（L1 494 / L2 7 / L3 6 / L4 1），0 跳过；中英文同步更新 |
| 2026-11-04 | **第 14 轮：每次验证使用独立数据库，并补齐三个未测量的页面。** `verify-local.sh` 每次运行创建 `cortexce_verify_<时间戳>_<PID>_flyway_rehearsal`，退出时用 `WITH (FORCE)` 删除，任何一次运行都不可能继承别的项目的 schema——第 13 轮曾有 65 个 DB 测试因为共享库被 V47–V49 加了租户列（`tenant_id NOT NULL`）而误报。`TEST_DB` 固定指定库，`VERIFY_KEEP_DB=1` 保留自动生成的库，两者都写进第 2 步。压测工具（`scripts/load-test.py`）现在记录 p99 与 max、上报服务真实使用的 `strategy`、在同一库内开关 V23 索引、计算 2× 劣化拐点，并按行数命名报告。**5 万条 observation**（`2026-11-04_load-test-50k.md`）：recent p50/p95/p99 **8.59 / 31.05 / 33.73 ms**，deep_page 1.46 / 1.98 / 2.50，type_filter 12.01 / 14.99 / 20.56，hybrid query 5.92 / 7.41 / 8.32；有索引 0.04–0.05 ms，无索引 12.5–13.7 ms（`Limit -> [Gather Merge ->] Sort -> Seq Scan`）；ingest 200 提交 / 200 落库，**45.75 行/秒**，0 重复哈希、0 卡在 `processing`；拐点在 5 万条时出现在 `recent` 与 `type_filter`，另两条未越过。首次测量检索相关性：**60 条真实会话 query / 126 条按时间归因的 observation**，使用真实 `BAAI/bge-m3`（`scripts/build-retrieval-corpus.py`、`scripts/eval-retrieval-quality.py`）。纯向量 hit@1 **0.600** / hit@5 **0.817** / MRR **0.688**；tsvector **0 / 0 / 0，60/60 全空**；hybrid 与纯向量**完全相同**，因为 `search_vector` 用 `english` 配置且 `plainto_tsquery` 是 AND 语义，中文 query 匹配不到任何词。identifier-OR（MRR 0.565）、RRF、query 截断（0.595 / 0.512）与 `simple` 分析器都实测过，均未超过向量，因此不改生产融合，改为把 `scripts/check-retrieval-quality.py` 作为第 6c 步回放基线（hybrid hit@1 ≥ 0.50、hit@5 ≥ 0.75、MRR ≥ 0.63、query ≥ 40、hybrid MRR 与 vector 差距 ≤ 0.02）。LongCat p95 38,925 ms 由流式探针复放 A11 同一批 prompt 归因：聚合时长 **网络 7.2% / 推理 69.8% / 生成 23.0%**，p95 样本中推理占 **95.9%**，30 次基线调用与 60 次 A11 调用均为 **0 重试**。删掉 `<narrative>` 与可选字段只影响生成（生成 p50 −34% / −55% / −39%），总时长 p50 仅 −20.8% / −8.0% / −15.1%；`reasoning_effort: low` 没有帮助（总 p50 +37.8%、推理 +61.1%），`enable_thinking: false` 被忽略。报告因此建议在 worker 层设上限（连接 5 秒 / 读取 120 秒、最多 3 次尝试、退避 2 秒 / 5 秒），超时后把消息标记 `retry`；A11 维持 1.5× 基线，供应商抖动交给 A13 覆盖。用例数保持 502 / 0 跳过；中英文同步更新 |
| 2026-11-03 | **第 9 轮：指标可达性与结果可复现。** 标识符未达 100% 的剩余缺口全部归因为模型漏抄：逐条判定后既没有标题截断，也没有 trace 中不可获取的标识，**剔除数为 0**，因此按能力边界记录并留证，未下调任何门槛。主体评分收紧为「token 必须真实出现在 trace 中」，不可达标签退出 A6b 分母并逐条列出，旧口径保留为诊断指标。`temperature 0` 下全量重复 3 次，量化逐指标方差（type 标准差 LongCat 2.75pp / MiniMax 3.27pp），并列出 3 次中 type 或动作类发生变化的样本 **18/87（LongCat）**、**13/87（MiniMax）**；不一致横跨所有类别，因此提取流程改为**默认采样 3 次并多数投票**（`cortexce.extraction.consistency-samples`，设为 `1` 可关闭；代价为延迟与 token ×3）。新增 `scripts/gen-real-golden.py`，从真实 commit 机械生成 **36 条样例**，通过 `EVAL_GOLDEN=real` 独立统计（标识符 LongCat 81.8%、MiniMax 61.1%）。新增 p50/p95 延迟与 token 基线，A11/A12 门槛为 **p95 ≤ 基线 1.5×**。用例数 462 → **472** / 0 跳过；`--min-tests` 423 → 472。中英文同步更新 |
| 2026-11-03 | **审计缺口收口 + 按层 baseline 棘轮。** 门禁改为 `--min-tests 423 --require-baseline`，基线文件 `scripts/test-baseline.json`（L1 409 / L2 7 / L3 6 / L4 1），即使总数仍高于下限，任何一层用例减少也会失败。新增数据库测试：8 线程并发领取（只允许一个成功）、队列深度把 `processing` 计入、failed 计数与最近失败原因可查询、批量与单条会话查询结果一致、V23 索引集存在、V20 legacy 冲突演练（可读的 `TECHNICAL_BUCKET_CONFLICT` / `OBSERVATION_WITHOUT_SCOPE`，按文档清理后重跑成功）。契约检查器现在校验错误响应体与真实健康形态（`/api/health` 为 `{service,status,timestamp}`，checks 在 `/api/readiness`）。`verify-local.sh` 优先使用专用容器 `claude-mem-test-pg`，因为共享的 `postgresql` 容器可能被无关客户端占满连接。用例数 360 → **423** / 0 跳过；中英文同步更新 |
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
