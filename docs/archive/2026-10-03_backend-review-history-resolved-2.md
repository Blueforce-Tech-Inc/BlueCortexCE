# Backend Review History — Resolved Entries (Batch 2)

> **Archived**: 2026-10-03 (round 232) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: P1-3 and P1-4, moved **verbatim** (79 lines, no rewriting, merging or trimming).
> **Selection rule**: entries whose status is *unconditionally* resolved (✅ 已修复) and which carry
> **no** ⏸ "recorded, not fixing" remainder. Entries with an open decision stay in the findings file,
> because they hold the live reasoning behind that decision rather than history.
> **Why this batch**: `backend-review-findings.md` reached 980 lines against the `MAX_LINES=1000`
> threshold, and adding P2-30 would have crossed it.
> **Do not modify this file.**

## P1-3
### P1-3: 四个派生查询按 `created_at` 排序，而该列有 52% 为 NULL —— 「最近」返回的是最旧的数据

- **Scope**: `ObservationRepository.findByProjectPathOrderByCreatedAtDesc`（两个重载）与
  `SummaryRepository.findByProjectPathOrderByCreatedAtDesc`。调用方共 7 处：
  `ClaudeMdService:55,108`、`TimelineService:118`、`ContextService:360,904,933,961`。
  本轮已全部改名为 `…OrderByCreatedAtEpochDesc`。
- **Problem**: 派生方法名里的 `CreatedAt` 让 Spring Data 生成 `ORDER BY created_at DESC`，
  而该列**可空且实际大面积为 NULL**——只有 `ImportService` 会显式 `setCreatedAt(...)`，
  主捕获路径 `AgentService:248` 只设 `createdAtEpoch`，且**没有 `@PrePersist`、没有 JPA auditing**
  兜底。PostgreSQL 在 `DESC` 下把 NULL 排在**最后**，于是「取最近 N 条」实际取回的是
  **最旧的那批非 NULL 行**。活体库实测：`mem_observations` 38,088 行中 **19,711 行（51.8%）**
  `created_at IS NULL`；`mem_summaries` 6,590 行中 **6,589 行（99.98%）** 为 NULL。
  **同一文件里 15 处手写 `@Query` 一律用 `created_at_epoch`**，唯独这三个派生方法不是——
  这正是它们显得「本该正确」的原因。
- **Reproduction**（2026-10-03，修复前，活体 37777）：对本项目取最新一条观测
  `b3ce4c56…`（`created_at_epoch` = 2026-04-09 19:59）作为 timeline 锚点——
  ①`GET /api/timeline?anchorId=b3ce4c56…&project=<repo>` 返回
  **`{"observations": [], "anchor_id": "b3ce4c56…"}`**：锚点在该项目 1,362 条观测里按
  `created_at` 排序**落在 500 条窗口之外**，`findAnchorIndex` 返回 -1，**整个锚点上下文功能静默失效**，
  且响应里连 `anchor_index` 字段都没有（走的是提前返回分支，看起来像「这个锚点没有上下文」）。
  ②同一项目 `POST /api/session/start` 生成的 `updateFiles[].content` 里，
  `## Recent Work` 的日期是 **2026-04-01 10:58 / 04-01 12:04 / 04-02 01:24**，
  而该项目真实的最近观测是 **2026-04-09 19:59**——**最近 8 天的全部工作不出现在 CLAUDE.md 里**。
  ③`ContextService.generateContinuation` 取 `summaries.get(0)` 作为「上次会话的下一步」，
  取到的也是错的行。
- **Fix**: 三个方法改名为 `…OrderByCreatedAtEpochDesc`（排序到 `created_at_epoch`），
  7 处调用方与 `TimelineServiceTest` 的 8 处 stub 同步改名，并在方法上写明**为什么不能用
  `created_at`**——避免下次有人「统一风格」改回去。**未改任何 DTO、端点、字段或序列化**，
  返回结构完全不变，故不属对外契约变更。
- **Verification**（双向，2026-10-03）：修复后 `GET /api/timeline?anchorId=b3ce4c56…` 返回
  **6 条观测、`anchor_index: 0`**，CLAUDE.md 首条日期变为 **2026-04-09 19:59**；
  **把三个方法名注入回 `CreatedAt` 并重新构建后，两个症状同时复现**
  （timeline 回到 `observations: []`、CLAUDE.md 回到 2026-04-01），确认修复被真实钉住。
  `mvn package` 通过；`TimelineServiceTest` 的 11 个 error 为**既有问题**（JDK 25 下
  Mockito inline 无法 mock `EmbeddingService`），已用 `git stash` 在修复前的代码上复现确认与本次无关。
- **Status**: ✅ **已修复**（2026-10-03，第 218 轮）。指纹变化（`.java`），完整验收
  45/0/1 + EXTRACTION 25/0/0 全通过。**遗留（未修，需项目决策）**：`created_at` 本身仍是
  稀疏的——`ExpRagService:237` 已为此写了「null 则回退到 epoch」的补丁，`ContextService:917`
  仍把可能为 null 的 `createdAt` 直接放进响应。**补 `DEFAULT`/`@PrePersist` 属数据层变更**，
  且会改变既有行的取值，超出「修排序」的范围，故记录不修。
- **复核记录**: 第 218 轮代码方向发现（Backend）。取证：`information_schema` 无关，
  直接对活体库 `count(*) FILTER (WHERE created_at IS NULL)`；`grep -rn "setCreatedAt"`
  确认只有 `ImportService` 与 `ExtractionStorageService` 会赋值；`grep '@PrePersist|EnableJpaAuditing'`
  零命中；`grep -rn "ORDER BY created_at"` 确认 15 处手写查询全用 epoch 列。

### P1-4: `/api/context/semantic` 的 `@RequestBody` 解析成了 Swagger 注解 —— 该端点自上线起 100% 返 500

- **Scope**: `ContextController.semanticContext(...)`（第 421 行起）。同方法的
  `q` / `project` 裸强转为第二处缺陷，一并修复。
- **Problem**: 该文件第 18 行 `import io.swagger.v3.oas.annotations.parameters.RequestBody`
  是为下面几个方法的 OpenAPI `@ApiResponse` 内容块而引入的。于是方法参数上**简写**的
  `@RequestBody` 解析到的是 **Swagger 那个注解**，不是 Spring 的。Spring 拿到一个
  没有任何可识别 body 注解的裸 `Map`，按 `@ModelAttribute` 处理，绑定时抛
  `No primary or single unique constructor found for interface java.util.Map`，
  **任何请求都返回 500**——包括 `docs/API.md:1085` 里那段可以直接复制粘贴的
  curl 示例。**全仓另外七个 controller 存在同样的 import 冲突，全部写成全限定名**
  （同文件的 `/generate` 在第 271 行也是），**此方法是唯一的例外**。
  第二处：`body.get("q")` 与 `body.get("project")` 是裸 `(String)` 强转，wire 传
  `{"q": 123}` 即 `ClassCastException` → 500；而**相邻的 `limit` 字段早已用
  `instanceof Number` 守卫**，同一方法内两种写法并存。
- **Reproduction**（2026-10-03，修复前，活体 37777）：`docs/API.md:1085` 的示例请求
  → **500**；`{"q": 123, "project": "<repo>"}` → **500**；`{"q": ["a"], "project": "<repo>"}`
  → **500**；`{"q": "valid long query...", "project": 99}` → **500**；`{}` → **500**。
  修复后上述全部 → **200**。
- **Verification**（双向，2026-10-03）：**注入 1**——把注解改回简写，文档示例立即复现
  **500**；**注入 2**——恢复 `q` / `project` 的裸强转，`{"q": 123, ...}` 立即复现
  **500**。两次注入均确认修复被真实钉住。同批回归
  `/api/context/{recent,preview,generate}` 三个兄弟端点**无回归**。
- **Status**: ✅ **已修复**（2026-10-03，第 224 轮）。注解改为
  `@org.springframework.web.bind.annotation.RequestBody` 并加注释记录该陷阱；
  `q` / `project` 改为 `instanceof` 守卫（非字符串视为缺失，落到与「缺失 q」相同的
  「query 不足 20 字符」答案）。`mvn package` 通过；指纹变化（`.java`），完整验收
  回归 45/0/1 + EXTRACTION 25/0/0 全通过。`docs/API.md` 的字段表（`q` 为 string、
  min 20 字符）**本来就是正确的**，未改，端点现已真正可达。
- **复核记录**: 第 224 轮代码方向发现（Backend）。取证：`grep -rn "import io.swagger.v3.oas.annotations.parameters.RequestBody"`
  在八个 controller 中命中，逐一检查其方法参数写法确认只有 `ContextController` 用简写；
  `grep -rn "@org.springframework.web.bind.annotation.RequestBody"` 确认其余七处均为全限定名；
  `mvn package` 后对活体 37777 逐例发请求复现。**零测试覆盖、零 SDK 暴露**是该缺陷
  长期存活的直接原因——`grep -rn "context/semantic"` 在四家 SDK 与全部测试中零命中。
