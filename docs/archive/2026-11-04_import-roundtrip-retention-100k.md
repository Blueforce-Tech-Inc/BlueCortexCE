# 10 万行导入导出往返 + 保留策略实删验证（2026-11-04）

历史快照。对照文档：[2026-11-04_migration-cost-100k.md](2026-11-04_migration-cost-100k.md)（V24 迁移代价）。

## 环境与数据集

- PostgreSQL 16（本机 `postgresql` 容器，宿主 5432），后端为同一份 `cortex-ce-0.1.0-beta.jar`。
- 源库 `cortexce_f1_source`：100,000 条 observation
  - 99,300 条会话 observation（100 个 session，每个 993 条，中文/英文混排）
  - 700 条 standalone（500 manual / 100 extraction / 100 dlq）
  - 1,000 条 prompt，100 条 summary，100 个 session
  - 9,930 条 1024 维向量（`embedding_1024`），其余为 NULL
  - 通过 Flyway V25 建表后灌入；导出/导入全部走现有 HTTP API，未手写 SQL 搬运
- 目标库 `cortexce_f1_target`：同一份 Flyway 迁移后的空库。

## F1：导出 → 空库导入 → 逐项比对

| 项目 | 源库 | 目标库 | 结果 |
|------|------|--------|------|
| observations | 100,000 | 100,000 | 一致 |
| sessions | 100 | 100 | 一致 |
| summaries | 100 | 100 | 一致 |
| prompts | 1,000 | 1,000 | 一致 |
| `embedding_1024` 非空 | 9,930 | 9,930 | 一致 |
| 1024 维校验 | 9,930 | 9,930 | 一致 |
| 会话 observation | 99,300 | 99,300 | 一致 |
| standalone | 700 | 700 | 一致 |
| 孤儿 conversation / summary / prompt | 0 | 0 | 一致 |

内容指纹（按业务字段排序后 `md5(string_agg(...))`）四表全部相同：

- observations：`content_session_id,type,title,content,created_at_epoch,embedding_1024,embedding_model_id,standalone_kind,platform_source`
- sessions：`content_session_id,project_path,platform_source,status,started_at_epoch,completed_at_epoch,user_prompt,last_assistant_message`
- summaries：`content_session_id,request,investigated,learned,completed,next_steps,created_at_epoch,prompt_number`
- prompts：`content_session_id,prompt_number,prompt_text,created_at_epoch,project_path`

检索：中文 query「中文检索 向量往返」，`/api/search?limit=5&project=/f1-project`。两库前 5 名均为 `search_score=6.0`；同 query 的分数分布完全相同（99,300 行命中，全部 6.0）。因导入后内部 UUID 重新生成，`ORDER BY score DESC, o.id` 在同分时按新 UUID 排，前 5 名的具体条目不同但排名等价。若需要逐条一致，应把 `o.id` 换成业务键（title + created_at_epoch）作为 tie-break。

### 边界

| 场景 | 结果 |
|------|------|
| 重复导入同一份（四表全量重放） | 全部 `skipped`（duplicates），四表计数不变，幂等 |
| 部分损坏条目（4 条：合法、缺标题、缺 session、3 维向量） | HTTP 200，`imported=1, errors=3`，逐条错误信息返回，合法行正常入库 |
| 超大单条（8 MB narrative） | HTTP 200，`skipped=1`（此前已成功导入），内容截断为 20,000 字符，耗时约 0.09 s，无崩溃 |

### 本轮修掉的问题

1. **导出只取第一页**：`/api/observations|summaries|prompts|sessions` 的 `limit` 被后端钳到 100，脚本原先单次请求，10 万行只能导出 100 行。改为 100/页分页并用 JSONL 临时文件累积，避免 argv/内存超限。
2. **向量静默丢失**：导出实体的 `embedding_768/1024/1536` 线名为 `embedding_768` 等，导入 DTO 默认按 `embedding768` 解析（Jackson SNAKE_CASE 不拆分数字），别名缺失导致向量全部丢失、语义检索退化为全文。已补 `@JsonAlias` 与单测。
3. **session 元数据丢失**：batch 会话响应没有 `last_assistant_message`，导入 DTO/INSERT 也没有该字段。已补导出字段、导入字段和 `insertIfIdentityAbsent` 列。
4. **prompt 项目路径丢失**：导入 DTO 没有 `project_path`，导入后为空。已补字段并回退到父会话路径。
5. **单条坏向量拖垮整批**：批量 `saveAll` 中一条 3 维向量触发 PostgreSQL `vector(1024)` 约束，整批回滚并返回 500。导入前校验 768/1024/1536 维度，坏条目跳过并计入 errors。
6. **超大单条阻塞写入**：8 MB 文本进入 generated `search_tokens` 后，PostgreSQL 在 `to_tsvector` 上跑了 24 分钟并持有 `FOR NO KEY UPDATE` 锁，后续导入全部排队（观察到的 8 MB 请求 HTTP 卡在 100 Continue）。导入时把 narrative 截断到 20,000 字符，单条 8 MB 请求变为 0.09 s 成功。

## F2：保留策略真实开启（30 天，20% 过期）

配置：`CORTEXCE_RETENTION_ENABLED=true`，`PROMPTS/OBSERVATIONS/SUMMARIES_DAYS=30`，`LOGS_DAYS=0`，`PROJECT=/f1-project`，cron 定点触发。

| 阶段 | 结果 |
|------|------|
| dry-run | `would delete prompts=200 observations=20000 summaries=20 logFiles=0` |
| 真删 | `deleted prompts=200 observations=20000 summaries=20 logFiles=0`，与 dry-run 完全一致 |
| 删除耗时 | 539 ms（轮询 `pg_stat_activity` 从出现 DELETE 到结束，含提交） |
| 锁级别 | `mem_observations` 上 `RowExclusiveLock`（受控 `LOCK TABLE ... IN ROW EXCLUSIVE MODE` 复核） |
| 删除期间写入 | 并发导入 233 条，0 失败；最大 130 ms，基线 11–30 ms，仅一次约 100 ms 抖动 |
| 删除后检索 | `/api/search` HTTP 200，前 5 条均为未过期记录，过期行 0 |
| 删除后计数 | observations 80,233（80,000 + 233 条并发探针）、prompts 800、summaries 80；口径与 dry-run 一致 |

结论：30 天保留策略在 10 万行规模是**亚秒级在线操作**，`RowExclusiveLock` 不阻塞 `INSERT`，无需停写。DELETE 会留下 dead tuple，后续依赖 autovacuum 回收（本轮未测 bloat/回收窗口）。

### 执行窗口建议（与迁移代价对照）

| 数据量 | 保留删除预估 | 建议窗口 |
|--------|--------------|----------|
| < 100k | < 1 s | 任意时间 |
| 100k – 1M | ~0.5–5 s（线性外推） | 低峰期即可，写入不阻塞 |
| 1M – 10M | ~5–50 s | 低峰期；按 5–10 万行分批 DELETE，批间留出 autovacuum 时间 |
| > 10M | 分钟级，WAL/IO 压力明显 | 维护窗口或严格分批；先评估索引 bloat 与 autovacuum 配置 |

对照 V24 迁移（同 10 万行）：

- 迁移的 `ADD COLUMN ... GENERATED` 是 `ACCESS EXCLUSIVE` 重写，实测约 4.3 s 完全停写，总迁移约 5.1 s；
- 保留删除同规模仅 0.54 s，且只拿 `RowExclusiveLock`，写入不中断。

因此：**保留策略可以常开并跑在业务时段；真正需要窗口的是带生成列重写的 schema 迁移**。当单表进入千万行后，两者都应改为分批 + `CONCURRENTLY`/低峰执行。
