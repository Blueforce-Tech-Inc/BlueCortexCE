# 项目现状与交接

> **Purpose**: 记录本仓库当前**已验证**的状态，让后续 agent/贡献者不必考古、也不必相信过期笔记即可接续。
> **Updated by**: 每批门禁全绿后由人工/定时维护任务更新。
> **Update rule**: 用最新已验证事实覆盖（HEAD、用例数、迁移版本）；不要追加历史——历史在 `docs/drafts/backend-fix-progress.md` 与 `docs/archive/`。

## 快照

2026-11-05 在提交 `bdee0a3` 上验证（即下面门禁跑过的状态，早于本文档加入）：

| 项目 | 数值 |
|------|------|
| 用例 | 共 **601**，0 跳过 |
| L1 单元/集成 | 565 |
| L2 HTTP 回归 | 29 |
| L3 验收（含 proxy 转发） | 6 |
| L4 端到端 | 1 |
| Flyway | **V25**（`backend/src/main/resources/db/migration/`） |
| PostgreSQL / pgvector | 16 / **0.8.7** |
| 架构基线 | controller→repository **0**，返回 entity 的 controller 方法 **0** |

用例棘轮在 `scripts/test-baseline.json`：新增用例必须在同一提交里抬高，只升不降。
同一门槛在 `scripts/verify-local.sh`、`.github/workflows/backend-tests.yml`、
`docs/TESTING.md`、`docs/TESTING-zh-CN.md` 重复登记。

## 一条命令跑全量验证

```bash
PG_CONTAINER=cortexce-flaky-pg PG_PORT=15433 REQUIRE_BROWSER_E2E=1 ./scripts/verify-local.sh
```

前置与行为：

- 需要运行中的 PostgreSQL 16 + pgvector 容器；找不到容器即报错，不会静默跳过。
  用 `PG_CONTAINER` / `PG_PORT`（可选 `PG_USER` / `PG_PASSWORD`）指向它。
- 每次运行创建唯一临时库
  （`cortexce_verify_<timestamp>_<pid>_flyway_rehearsal`），退出即删。
  `_flyway_rehearsal` 后缀是必需的——DB-gated 用例没有它就会跳过。
  `TEST_DB=<name>` 可固定库名，`VERIFY_KEEP_DB=1` 保留库。
- `REQUIRE_BROWSER_E2E=1` 把 Playwright/Chromium 与固定工具链的 SKIP 变成失败；
  本地未装时可暂时不设，但 CI 必须设。
- 日志在 `VERIFY_LOG_DIR`（默认 `/tmp/cortexce-verify-*`）。

## 门禁清单：各自守什么、红了看哪里

| 步骤 | 守什么 | 红了看 |
|------|--------|--------|
| 5 | 一次 Maven 跑完四层全量用例 | `backend/target/surefire-reports/` |
| 6 | 零跳过、至少 601 条、四层齐全、分层基线不降低 | `scripts/check-test-results.py`、`scripts/test-baseline.json` |
| 6b | 评测 floor 只升不降 | `scripts/check-eval-gates.py` |
| 6c | 已记录检索排序可复现 | `scripts/check-retrieval-quality.py` |
| 7 | 文档检查器自测 + EN/ZH API 文档对照活服务（路径、code、示例） | `scripts/check-api-doc-surface.py`、`docs/API*.md` |
| 8 | 迁移安全：23 个存量迁移只报告；**新**迁移含 `DROP`/`TRUNCATE`/类型收窄/非并发索引必须有 `-- rollback:` | `scripts/check-migrations.py`、`scripts/migration-baseline.json` |
| 9 | proxy E2E 转发，0 跳过 | `proxy/` |
| 9b | OpenClaw 插件：占位过滤 + 会话生命周期，0 跳过 | `openclaw-plugin/` |
| 10 | 提交的 viewer bundle 与 `webui/src` 新构建一致；Playwright 21 项（渲染、文件、`hasMore`、SSE 行、刷新、设置、401、断服恢复） | `scripts/viewer-build`、`webui/` |

`webui/` 是 git 子模块，**本仓库不要改它**；后端响应字段名是硬契约
（`hasMore`、`updateFiles`、`CLAUDE_MEM_*`）。

## 数据库与关键 schema 决策

- **中文检索**（V24）：`search_vector`（`to_tsvector('english', …)`）被
  `search_tokens` 取代——由 `cortex_tokens()` 生成的 `tsvector`，保留完整 ASCII
  标识符、对 CJK 取字符 bigram，GIN 索引，文件路径权重 C。镜像里没有
  `pg_bigm` / `zhparser`，因此没有采用。实测 0.3 s、hit@5 0.867。
- **类型过滤列表**（V25）：`idx_observations_scope_type_created`
  （`memory_scope_id`、`type`、`created_at_epoch DESC`），让 limit 在首页停止扫描
  而不是排序整个类型。加之前 `type_filter` p95 在 10 万行为 29.38 ms、1 千行为
  4.94 ms（见 V25 文件头）；scope 谓词改为 `UNION ALL`（而非 `OR`），使规划器能把
  它当作索引条件。
- **向量索引**：embedding 列上建 HNSW；有效性通过 `cortexce_vector_indexes_valid`
  （1=有效）暴露并由指标测试断言。重建用 `scripts/rebuild-vector-index.sh`。
- 恢复参数不是 schema 决策但影响运维：见 `docs/DEPLOYMENT.md` 的「默认值代价」表。

## 已知边界与「被数据否决」的方案

没有新证据不要重试：

- **300 万行单张 HNSW 图**：否决。按 scope 分片（8 × 37.5 万）实测 261 µs/行且
  无溢写；单库 1M 图为 550–802 µs/行，约 90 万行后开始溢写。策略：≤1.5M 保持单库；
  3M+ 用 `PARTITION BY LIST (project_path)` + 每分区本地 HNSW。
  证据：`docs/archive/2026-11-05_vector-sharding-3m.md`。
- **1M 行用默认恢复 GUC**：按延迟否决。服务器默认（64 MB / 0 workers）恢复
  2,447.9 s，另加约 550 s 重建 HNSW；`RESTORE_WORK_MEM=1GB RESTORE_WORKERS=2`
  整套演练 99.8 s。证据：`docs/archive/2026-11-05_ops-loop-1m.md`、`docs/DEPLOYMENT.md`。
- **架构豁免**：已清零。controller 不 import repository，也不返回 entity；唯一被接受的
  包依赖环是 `config`/`entity`/`event`/`repository`/`service`/`util`（Spring 装配所致）。
  要新增例外必须同时改 `ArchitectureGuardTest` 与 `docs/ARCHITECTURE*.md` 并写理由。
- **纯文本降级语义**：`fell_back` 只在 embedding 调用失败时为 true，主动纯文本检索为
  false；只读库写请求返回 503 `service_unavailable`，检索仍 200。
  证据：`docs/drafts/backend-fix-progress.md`。

## 尚未验证 / 下一批输入

写本文时明确未启动，不要假设已存在：

- 按 README 快速开始从零复跑并自动校验的脚本（依赖→启动→健康→写一条→检索到）。
- CI 各 job 耗时/缓存实测，以及连续 3 次远端无 flaky 的确认。
- 可执行的 Prometheus 告警规则（队列积压、失败率、熔断 OPEN、embedding backlog、
  检索 p95、SSE 连接数上限）。本地不保证有 `promtool`；未跑 `promtool check rules`
  前必须标注「未验证」。
- 剩余「不做/待定」审计项的复核。

## 历史证据去哪找

- `docs/archive/YYYY-MM-DD_<topic>.md`：不可修改的时间点报告（压测、故障演练、审计）。
  日期是内容最后有效的日期，不是归档日期。索引：`docs/archive/README.md`。
- `docs/drafts/backend-fix-progress.md`：按批次的实施记录（最新在上），含实测数字与提交区间。
- `docs/performance-test-results.md`：当前性能总结（Live）。
- `docs/TESTING.md` / `docs/TESTING-zh-CN.md`：分层定义与运行方式。
- `docs/ARCHITECTURE.md` / `docs/ARCHITECTURE-zh-CN.md`：强制架构规则与基线。
