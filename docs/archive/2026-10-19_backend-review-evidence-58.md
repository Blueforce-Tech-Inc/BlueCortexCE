# Backend Review Evidence 58 — 第 360 轮已结案条目的整条正文（第 360 轮迁出）

> **归档规则**：承 `-57` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 `P2-103` / `P2-104` / `P2-110` / `P2-111` 四条，**全部是 ✅ 已修**
> （注释或文档的失实陈述修正，零行为变更），对本循环**没有待办动作**，故可安全迁出。
> 选它们是因为 `backend-review-findings.md` 的**字节余量仅剩 3173 字节**
> （1380 行 / 146827 字节 vs 上限 1500 / 150000），不足以容纳本轮新条目；
> **未结案条目一律不动**。
>
> **四条并不相邻**，故切割区间**不得按升序复用**（每次替换会让后续区间整体前移）。
> 本批采用**单遍顺序重建**：从头扫一遍，遇到目标块就收集到归档并改写为指针行，
> 最后一次性拼回，从根上消除索引前移这一类事故（第 258 轮曾因之毁掉 P2-47 整块）。
>
> 迁出前实测 **1380 行 / 146827 字节**；本归档实测 **39 行 / 8121 字节**。
### P2-103: `ExpRagService` 把 `created_at` 为 NULL 的成因写成「pre-migration data」——而**当前的捕获路径就在持续制造 NULL**

- **Status**: ✅ **已修（第 341 轮，纯注释零行为变更）** —— 注释改为如实写出「DDL 默认值只对 SQL 插入生效、JPA 捕获路径只设 `createdAtEpoch`、只有 `ImportService` 赋值」，并指向 `ARCHITECTURE.md`。`git diff -U0` 逐行核验**可执行行 0**；`mvn -o clean package -DskipTests` EXIT=0；重建 jar 后停掉本轮自己启动的 37777 进程并以新 jar 重启，按实跑满足新鲜度闸门；回归 **45/0/1** + EXTRACTION **25/0/0**。条目全文已逐字迁入 [`…-49.md`](../archive/2026-10-07_backend-review-evidence-49.md)（第 341 轮）。

### P2-104: 架构文档 REST API 表的**最后一行被挤到表外** —— 2026-10-03 插入的 SSE 引用块落在表格中间，GFM 只渲染到 Health 行为止
- **Status**: ✅ **已修（第 344 轮，纯排版零行为变更）** —— 双语各把 `| Test |` 行接回表尾、引用块整体下移至表后；表格行数 15 未丢行，pandoc 全文件复核 `tables=13` / 字面管道段落 `0`。**扫描器两版仪器错误均由自检拦下**（详见归档）。条目全文已逐字迁入 [`…-50.md`](../archive/2026-10-07_backend-review-evidence-50.md)（第 344 轮）。

### P2-110: `SearchService` 的策略树注释列了一个**代码里不存在的维度**（`files`），并把 filter 路径说成「via tsvector」
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/service/SearchService.java`（原 14~22 行类 Javadoc）。原第 1 条写 `No query → PostgreSQL filter search (type/concepts/files/dateRange via tsvector)`。三条互相独立的证据：① **该词在整个文件里只出现这一次**——`grep -n files` 命中行号 19，正是这条注释本身；`SearchRequest` record（245~257 行）没有该字段，`findByAllFiltersWithOffset` 的 SQL（`ObservationRepository` 203~222 行）没有该条件，三个调用点（`ViewerController:324`、`ClaudeMemMcpTools:115`、`ContextController:517`）也都不传；② **filter 路径的 SQL 一处 tsvector 都没有**——`search_vector` / `ts_rank` / `plainto_tsquery` 在该 query 段命中 **0** 次，而同文件 `hybridSearch` 段命中 **2** 次（阳性对照）；③ **运行时自述也不符**——`GET /api/search` 活体实测，无 query 无 filter 返回 `strategy=recent`、无 query 带 `type` filter 返回 `strategy=filter`，而 `"tsvector"` 字面量在代码里只出现在 PATH 3（104 行）。
- **Problem**: 三处失实。① **`files` 是幽灵维度**，注释却把它列为 filter 的四个维度之一；② **「via tsvector」不成立**——filter 路径走的是 `:p IS NULL OR col = :p` 组合谓词，全文索引只在 PATH 3 才被触及；③ **漏掉了实际存在的 `source`**，它是 record 字段、SQL 条件、`hasAnyFilter` 与 `applyPostFilters` 四处都处理的第四个维度。此外第 2 条把实为 hybrid（pgvector 半边 + tsvector 半边 UNION）的路径写成 `pgvector semantic search`，第 3 条把 fallback 写成无条件——`isRetryableException` 命中的连接类异常（SocketTimeout / Connect / Timeout / CannotGetJdbcConnection）是**重抛**而非降级。
- **「per cookbook」不记为缺陷**: 仓库内确实没有 cookbook 文档（`docs/` 下 20 个 `.md` 零命中；同一 grep 查 "semantic search" 命中 22 个文件、"hybrid search" 命中 21 个，作为阳性对照），但三处 Java 注释用的是同一措辞（`AgentService:37`、`XmlParser:20`），指向的是**外部规范**。按「没匹配到 ≠ 不存在」，只在新注释里写明无法对账，**不据此记 finding**。
- **Status**: ✅ **已修（第 359 轮，零行为变更）** —— 注释按真实分支重写：filter 路径写明是谓词 SQL 且**不触及** `search_vector`、hybrid 写明是 pgvector + tsvector 合并、fallback 写明**仅非可重试**异常降级而连接类异常重抛；并把 `files` 与「per cookbook」两处旧断言作为**明确不再复述**的对象记录在案。`git diff` 过滤注释行后**非注释变更 0**（grep 无匹配，退出码 1）。

### P2-111: `ContextService` 的 `@param types` 写「empty = all types」，而实现把它展开成**六项硬编码白名单**——同段的 `concepts` 却是真·全量
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java:307`（原 Javadoc）与 359~361 行的实现。原文 `@param types List of observation types to include (empty = all types)`；实现是 `List<String> safeTypes = types.isEmpty() ? List.of("bugfix", "feature", "refactor", "discovery", "decision", "change") : types;`。**端到端 A/B（`GET /api/context/preview`，项目 `/tmp/phase3-acceptance-test`）**：**A（`observationTypes` 留空）83 字节**，正文为 `# phase3-acceptance-test — no memories yet`；**B（显式传那六项）83 字节，与 A 逐字节相同**；**C（传全部 14 种）24065 字节、`📊 500 observations`**。C 是阳性对照，证明端点与探针本身通；A≡B 证明「空」精确等价于那六项。
- **Problem**: 注释失实，且**掩盖了一个真实的静默丢弃**。该项目的三种 type —— `extracted_user_preference`(18369)、`user_statement`(3937)、`test`(727) —— **无一在白名单内**，于是空 types 下它们全部不出现，接口返回 **HTTP 200** 加一句「no memories yet」，**没有任何错误迹象**。全库 14 种 type 中被白名单排除的有 8 种，条数最多的两种（`extracted_user_preference` 18373、`user_statement` 3948）都在其中。同段的 `@param concepts … (empty = all concepts)` **是真的**——362 行的 `conceptsEmpty` 一路传到 SQL，由 `:conceptsEmpty = true OR …` 跳过谓词；故同一段 Javadoc 用同一措辞描述了**两种语义不同**的参数。
- **为什么不改实现**: 把空 types 变成真·全量，会让现有调用方（`ContextController:399` 的 `/api/context/preview`，WebUI 设置面板经由它）**突然多出全部 8 类观测**，属**对外契约变更**，按规则只记录不单方面实施。
- **Status**: ✅ **注释已修（第 359 轮，零行为变更）** —— `@param types` 改为写明展开成哪六项、哪几类会被排除因而须显式传入，并点明**与 `concepts` 不对称**。`git diff` 过滤注释行后**非注释变更 0**。**新鲜度闸门**：改的是后端源码，豁免路径**不适用**，已 `mvn -o clean package -DskipTests`（BUILD SUCCESS）并重启 37777 后跑完整验收。
