# Health Check History — batch 8 (rounds 171-172)

> **Archived**: 2026-10-04 (round 242) from [`../drafts/health-check-task.md`](../drafts/health-check-task.md).
> **Contents**: the per-round health check reports for rounds 171 through 172, moved **verbatim**.
> **Why this batch**: that file reached 1027 lines against the `MAX_LINES=1000`
> threshold after round 242. These are the oldest reports still in the working file;
> every block's self-reported round number was asserted against its own heading before
> the source lines were removed, following the pattern batches 4-7 established.
> **Continues** `2026-10-02_health-check-history-7.md` (rounds 163-170), which is immutable.
> **Do not modify this file.**

## 第 171 轮 — 2026-10-02 21:25 — Demo + SDK README

### 代码方向：Demo

**DEMO-1（P2，已修）Java Demo 把「无有效字段的更新」报成 500。**

`PATCH /demo/observations/{id}` 只检查请求体非空，随后把解析出的 update 交给 SDK。
若请求体只含无法识别的键（例如把 `title` 拼成 `titel`），update 为空，而
`CortexMemClientImpl` 对空更新抛 `IllegalArgumentException`——被控制器的
`catch (Exception e)` 接住后返回 **500**。

**活体 A/B（同一请求打两个 demo）**：

| Demo | 响应 |
|------|------|
| **Java** | **HTTP 500** `"Update observation failed: at least one field must be provided for update"` |
| Python | HTTP 400 `"at least one field must be provided for update"` |

Go、Python、JS 三家都返 400，**Java demo 是唯一异常者**——客户端自己的拼写错误被告知
造成了服务端故障。`DemoErrors.statusOf` 帮不上忙：它只认 `RestClientResponseException`，
而空更新是本地失败、根本不会到达后端。现由控制器自行校验可识别字段并返回 400，
消息与另三家一致。

**核实范围**：demo 中其余三个带 `@RequestBody` 的处理器
（`FeedbackController`、`IngestController`、`SessionLifecycleController`）都在调用 SDK
前显式校验必填字段，**该类缺陷仅此一处**。

**顺带关闭第 159 轮记录的 E2E 覆盖缺口**：Java demo 的 `e2e/run-e2e.sh` 原本
**完全没有 `/demo/observations/*` 覆盖**——这正是该缺陷能长期存活的原因。新增第 7 节
共 14 项断言（含本轮修复的回归守卫），并新增 `run_status` 助手，因为原有 `run_ok` 只
匹配响应体、无法区分携带相近消息的 400 与 500。

**14 项新断言全部通过。** 该脚本的 5 项失败为**既有的 LLM 依赖项**（chat ×3、
memory/refine、异步捕获）——本环境 chat 端点返回 500，且均不在本次改动的代码路径上。
demo 单元测试 5/5；diff 为纯增量（+89 / −0）。

**跨方向修复，明写理由**：本轮 Demo 方向完成后，在为 SDK README 查证「空更新」契约时
发现 **PY-2**（见下）。证据、根因、修法均已在手，且不修就无法为四家写出**准确**的统一
契约文档，故一并修复。

### 代码方向（跨方向）：Python SDK

**PY-2（P2，已修）空更新被静默吞掉。**

`update_observation` 在组装出的 body 为空时**直接 return**，既不报错也不发请求——
「更新被丢弃」与「写入成功」完全无法区分。Go（`update.Validate`）、Java（`update.isEmpty`）、
JS（`hasField`）**都拒绝**，Python 是唯一放行的。

修复后**四家抛出完全相同的消息**（活体对拍）：

```
go       RAISED cortex-ce: ObservationUpdate validation error: at least one field must be provided for update
js       RAISED _ValidationError: cortex-ce: validation error on update: at least one field ...
python   RAISED ValidationError: cortex-ce: validation error on update: at least one field ...（0 个请求）
```

三个测试钉住了旧的静默 no-op，其中一个名为 `test_validation_error_field_for_update`
的用例**断言的恰恰是不抛 ValidationError**——与它自己的名字相反，是早期意图的残留。
三个全部按真实行为重写，并新增第四个用例钉住 `extracted_data={}` 的 kwargs 形式
（会真的发出 `{"extractedData": {}}`，与 JS 一致）；数据类形式
`ObservationUpdate(extracted_data={})` 会把它从 `to_wire()` 中省略，因此属空更新。

> **一处推测被实测纠正**：我原以为「`extracted_data={}` 被当作 unset」是 Python 独有的
> 处理。实测四家一致（都发 `{"extractedData": {}}`），分歧精确地只在「**完全没有设置
> 任何字段**」这一点上。

Python SDK 394 → **395 全绿**；**退回验证：3 个测试失败**（全是「应当抛出」的用例）；
python-sdk-e2e **28/28**；python-demo-e2e **27/27**。

### 文档方向：SDK README

**DOC-1（P2，已修）四家 README 都没有记录「空更新会被拒绝」这一契约**——而这正是本轮
DEMO-1 的知识前提：不知道它，调用方就会把客户端错误报成服务端错误。

四家 SDK README（中英共 8 个文件）统一新增「Empty Updates Are Rejected / 空更新会被拒绝」
小节，置于 Wire Format 之前：规则、各家异常类型、以及**为什么要拦**（不设置任何字段的
PATCH 在 wire 上是一次静默 no-op，调用方会以为写入成功）。Python 一家额外说明了
`extracted_data={}` 的两种形式差异。

结构校验：8 个文件 U+FFFD=0、围栏平衡；`/tmp/readme_parity.py` 全通过，中英标题层级
逐位一致（Go 20/20、Java 43/43）。

### 验收

本轮代码有**两次**改动，指缝两次变化：
`ebcc4945…` → `ab02b3f8…`（DEMO-1）→ `3610433b…`（PY-2）。
两次均跑了完整验收：每次 `regression-test.sh --skip-build` **45 passed / 0 failed /
1 skipped**；`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
最终基线推进至 `f188553` / `3610433b…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）—— Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（`GET /api/sessions` 活体 404）—— 该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 11 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 E2E 脚本未纳入标准门控**——`run-all-e2e.sh` 的 10 个套件不含它，
   `scripts/` 下也无 `java-demo-e2e-test.sh`；本轮补的 14 项断言因此不会自动运行。
6. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
7. **Python SDK 无响应体大小限制**（四家 README 已显式记录为有意差异）。
8. **Java Demo 的 LLM 路径在本环境不可用**（chat 端点 500），其 E2E 相关断言无法验证。
9. **LICENSE 版权归属不一致**（J-2）—— 仍待用户决策。
10. `docs/drafts/` 三个超 50KB 文件 —— 待明确规范适用范围。
11. push 权限阻塞（`wubuku` 403）。
12. 并行巡检进程争写状态文件。
13. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Demo 完成（新循环第二十四轮），下一方向 Backend；
文档审查轮换推进：SDK README 完成（七十二轮），下一方向 设计文档。

---

## 第 172 轮 — 2026-10-02 21:56 — Backend + 设计文档

### 代码方向：Backend

**BACK-7（P2，已修）DLQ 记录正在混入精炼流水线——排除条件因类型改名而失效。**

`findLowQualityObservations`、`findStaleObservations`、`findOverdueForRefine`
三处查询各带一条 `AND type != 'extraction_failed'`。但
`ExtractionStorageService.storeDLQ` 实际写入的是 `type = "dlq_" + templateName`
配 `source = "dlq"`（存进专门的 `dlq:extraction` session）。**全仓搜索确认：如今再无
任何代码写入 `extraction_failed`**——该字符串只出现在这三处条件、文档和已归档的评审
历史里。于是排除条件命中不了任何东西。

`storeDLQ` 也从不设置 `quality_score`／`refined_at`／`last_accessed_at`，三者皆为 NULL，
而这**正是** `findStaleObservations`（`last_accessed_at IS NULL OR …`）与
`findOverdueForRefine`（`refined_at IS NULL OR …`）选中的画像。
`MemoryRefineService` 把这两条查询的结果直接送进精炼流水线，而流水线的步骤包含合并、
LLM 改写与 `deleteLowQualityObservations`——**一条失败记录可能被改写、被合并掉或被删除**，
这恰好废掉了死信队列的用途。`findLowQualityObservations` 幸免，只因 `quality_score < :threshold`
对 NULL 求值为 NULL。

现改为 `AND COALESCE(source, '') != 'dlq'`。**`COALESCE` 是承重的，也是本修复的主要陷阱**：
`mem_observations.source` 可空（V14 以 `TEXT` 加入且无 NOT NULL），且约一半观察记录没有
source——活体抽样 50 条中 26 条有 source、24 条没有。裸写 `source != 'dlq'` 会对这些行
求值为 NULL，从而**把普通观察记录也一并静默排除出精炼**。

**实证**（`/tmp/clamp-probe-172` 等项目）：在被改的 `/tmp/claude-mem-test-44483` 上跑 refine，
该项目 5 条中 4 条 source 为 NULL，日志显示「Found 10 candidates / Refined 10 observations」
——**source 为 NULL 的记录仍被正常选中**，COALESCE 陷阱被实证排除。

**诚实标注未覆盖的部分**：DLQ 本身未被实证——extraction 默认关闭，活体库里根本没有 DLQ
session（`POST /api/sdk-sessions/batch` 查 `dlq:extraction` 返回 `[]`）。这是**由源码与
列可空性确立的潜在路径，不是已实证的活跃缺陷**，报告与 `backend-review-findings.md`
（P2-7）均如此写明。后端无 `@DataJpaTest` 基建，三条查询无法隔离测试，完整验收是唯一信号。

**核实无误**（扫描后未改）：`catch (Exception)` 返回空/default 的五处
（`LlmQualityScorer`、`MemoryRefineService`、`QualityScorer`、`ContextService.generateContinuation`、
`TimelineService`）全部属于「LLM/DB 可选、降级并记日志」的既有设计。

### 文档方向：设计文档

**DOC-1（P2，已修）DLQ 的文档描述与实现完全脱节。**

六份设计文档与两份面向用户的功能文档都称 DLQ 使用 `type=extraction_failed`，并称有
**定时重试任务**处理条目。两者都不成立：实现写的是 `dlq_{template}`，且**不存在定时重试
任务**（grep 不到 `@Scheduled` 的 DLQ 作业），`findByTypeGlobal` 被声明但**无任何调用方**。
DLQ 条目实际只供人工检查。

已改：`11.md` §11.3（规范本体，标注原设计已被取代）、`00-quick-ref.md` 管道图、
`18.md` 命名空间表、`14.md`／`15.md`／`25.md` 加指向说明，以及
`docs/structured-extraction.md` + `-zh-CN.md`（用户最可能读到的地方）。

**DOC-2（P2，已修）两处既有的围栏缺陷**（与上一项无关，查证过程中发现）：

- `phase-3-design/15.md` 第 420 行是一个**孤立的重复围栏**，把随后的
  「Alternative approach」散文吞进了代码块。
- `phase-3-design/7.md` 第 206 行的围栏**提前闭合**，导致 207–209 行的代码片段掉出代码块，
  210 行成了孤立围栏。

两处均为 HEAD 处即存在（本轮插入前后围栏数都是 33 / 17），已分别删除多余围栏；
**全目录 31 个文件复验后无一不平衡**，链接 0 断裂、无文件超 50KB。

`15.md` 的修复一度让管道图对齐被破坏（`→` 是多字节字符导致宽度算错、`│` 位置错位），
已按同族行宽度重排。

### 验收

指纹 `3610433b…` → `75c2bd3e…`（后端 SQL 有代码改动）→ **用新 jar 重启后端**后跑完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
基线推进至 `44ab14e` / `75c2bd3e…`。

> 途中一次插曲：`scripts/start.sh --help` 并不会打印帮助，而是**执行了重启**（它把
> `--help` 当启动参数，停了旧进程并用新 jar 起了新的）。这本是任务文件规定的服务管理方式，
> 但我原本只想看用法。重启后该进程一度退出，EXTRACTION 验收首次因「Backend not running」
> 失败，改用 `scripts/start.sh --background` 重新拉起后重跑才拿到 25/0/0——**如实记录：
> 第一次验收是失败后重跑的，不是首次即过。**

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）—— Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（`GET /api/sessions` 活体 404）—— 该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 11 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 E2E 脚本未纳入标准门控**（`run-all-e2e.sh` 的 10 个套件不含它）。
6. **LLM API key 已失效**（日志 401「Token is invalid」）——这解释了 Java Demo E2E 的
   chat 三项失败，属环境问题而非代码缺陷。
7. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
8. **Python SDK 无响应体大小限制**（四家 README 已显式记录为有意差异）。
9. **后端无 `@DataJpaTest` 基建**——仓储层排序/谓词改动只能靠完整验收间接验证。
10. **LICENSE 版权归属不一致**（J-2）—— 仍待用户决策。
11. `docs/drafts/` 三个超 50KB 文件 —— 待明确规范适用范围。
12. push 权限阻塞（`wubuku` 403）。
13. 并行巡检进程争写状态文件。
14. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Backend 完成，下一方向 Java SDK（回到轮换起点）；
文档审查轮换推进：运维/用户指南 完成（八十五轮），下一方向 API 文档。
