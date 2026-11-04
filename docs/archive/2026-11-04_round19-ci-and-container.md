# 第 19 轮：远端 CI 核对 + 容器全栈验证

- 日期：2026-11-04
- 仓库：`Blueforce-Tech-Inc/BlueCortexCE`
- 相关提交：`9a80a30`（CI 修复）、`563b640`（检索实验与长稳脚本）

## 1. 远端 GitHub Actions 实跑核对

| 运行 | 提交 | 结论 |
|---|---|---|
| [41769617418](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41769617418) | `bc8c13b` | **失败**：JDK 21 与 JDK 25 都在 `L2MetricsEndpointTest.valuesChangeWithOneIngestAndOneSearch` 上失败（`expected: <1.0> but was: <0.0>`），WebUI typecheck 通过 |
| [41770330872](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41770330872) | `9a80a30` | **成功**：Backend (JDK 25)、Backend (JDK 21)、WebUI typecheck 三个 job 全绿 |
| [41770330974](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41770330974) | `9a80a30` | Docker Build 成功 |

### 失败根因

`POST /api/ingest/tool-use` 在写入队列行之后发布 `PendingMessageEvent`，其监听器是
`@Async @EventListener`，会立刻把这一行从 `pending` 推进到 `processing`。测试在拿到 200
响应后立刻抓取 `/actuator/prometheus`，因此断言与 worker 在赛跑：本机 scrape 抢先
（`pending=1`），CI 机器更慢（`pending=0`）。`app.pending-message.enabled=false` 只关掉
定时轮询，关不掉事件驱动的监听器，所以这个 flake 与 JDK 无关，两个矩阵同时失败。

### 修复

[L2MetricsEndpointTest.java](file:///Users/yangjiefeng/Documents/Blueforce-Tech-Inc/BlueCortexCE/backend/src/test/java/com/ablueforce/cortexce/layers/L2MetricsEndpointTest.java)
改为直接用 repository 插入一条 `pending` 行并断言 gauge 跟着变，再把同一行改成
`processing` 并断言它从 pending 计数里消失。断言的是 gauge 的契约（"抓取时读取共享队列"），
不再依赖 worker 的胜负；测试结束删除该行，避免给共享 rehearsal 库的其他层留下 processing 行。
CI 的 `--min-tests` 同时从 493 提到本地棘轮值 528。

## 2. 容器全栈验证（空卷起栈）

镜像 `cortex-ce:local` 由 `backend/Dockerfile` 本地构建（290 MB），compose 项目名
`cortexce-r19` 保证卷是全新的：`postgres_data`、`claude-mem-logs` 均为首次创建。
宿主端口用 `POSTGRES_PORT=5450`（默认 5433 与本机测试容器冲突）、`SERVER_PORT=37777`。

| 检查 | 证据 |
|---|---|
| 迁移自动跑到最新 | `SELECT max(version::numeric) FROM flyway_schema_history WHERE success` → `25`，与 `db/migration` 最新文件一致 |
| 健康检查 | `GET /api/health` → 200 `{"service":"claude-mem-java","status":"ok",...}`；compose healthcheck 报 `Healthy` |
| 写入中文 observation | `POST /api/ingest/observation` → 200，0.49 s，返回 `中文容器验证` 的实体（写库 + embedding 一步完成） |
| 中文检索命中 | `GET /api/search?project=/tmp/compose-zh-project&query=中文容器` → `strategy=hybrid`、`count=1`，命中 `中文容器验证` |
| WebUI 可达 | `GET /` → 200、67 421 B、`text/html`，`<title>claude-mem viewer`；`GET /viewer-bundle.js` → 200、397 179 B |
| 重启后数据仍在 | `docker compose restart claude-mem-java` → 健康 200，`mem_observations` 仍为 1，同一中文查询仍 `hybrid count=1` |
| 收尾 | `docker compose -p cortexce-r19 down -v`，卷与网络已删除 |

一次失败尝试值得记录：不注入供应商 key 时，`POST /api/ingest/observation` 会尝试调用默认的
`api.openai.com` embedding，SSL 握手失败导致整条写入回滚（`mem_observations` 为 0）。也就是说
**compose 全栈要能写入，必须配置 embedding key**，这与 `DEPLOYMENT.md` 的要求一致，不是代码缺陷。

## 3. 检索候选生成（结论见另一份报告）

会话/文件聚簇扩展与 query 侧高频标识符扩展全部未达门槛，生产检索未改动。数据与判定在
[2026-11-04_retrieval-quality-round19.md](./2026-11-04_retrieval-quality-round19.md)。

## 4. 一小时长稳

数据与判定在 [2026-11-04_soak-1h.md](./2026-11-04_soak-1h.md)（由 `scripts/soak-test.py` 生成）。
