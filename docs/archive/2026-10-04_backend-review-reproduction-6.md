# 后端审查实测记录归档（第六批）

> **归档说明**：本文件由 `docs/drafts/backend-review-findings.md` 在第 247 轮后（997 行，逼近 1000 行阈值）
> 逐字迁出，承前五批同规则。**归档文件创建后不得修改。**
> 迁出的是 **Evidence** 层（活体/比对证据）；**Scope / Problem / Status** 即决策推理留在原处，
> 与 `复核记录`、`Reproduction` 的处置一致。原文在 `patrol-rotation.md` 与 `doc-review-task.md` 中另有逐字副本。

<!-- 以下原属 P2-35 的 Evidence 段 -->
- **Evidence**:
  | 事实 | 证据 |
  |------|------|
  | 失败有独立评分档 | `QualityScorer.java:26` `FAILURE_BASE = 0.20f`；`:61` `FAILURE, // Task failed` |
  | 捕获跳过失败 | `CortexToolAspect.java:60` 的 `proceed()` 不在 try 内，无 catch 兜底 |
  | **零测试覆盖** | `CortexToolAspectTest` 共 **4** 条：context 激活/未激活、大小输入截断/不截断——**无一条让工具抛异常** |
  | 另一条捕获路径同样如此 | 薄代理只有 `PostToolUse` 钩子，**没有「工具失败」钩子**；故两条路径都产不出失败记录 |

<!-- 以下原属 P2-36 的 Evidence 段 -->
- **Evidence（wire 级用真实 `cortexmem.NewClient` 打 httptest，非 mock；后端活体取有 22,763
  条观测的真实 project）**:
  | 适配器 | 入口 | 实际发上 wire 的报文 |
  |--------|------|---------------------|
  | eino | `WithRetrieverCount(4)` | `{"task":…,"project":…,"count":4}` |
  | eino | `WithRetrieverCount(0)` | `{"task":…,"project":…}` — 被 `omitempty` 省掉 |
  | eino | `WithRetrieverCount(-1)` | `{…,"count":-1}` ← **原样发出** |
  | eino | `WithRetrieverCount(-100)` | `{…,"count":-100}` ← **原样发出** |
  | genkit | per-call `Count:-1`（构造值 4） | `{…,"count":4}` — 兜底**生效** |
  | genkit | 构造 `WithRetrieverCount(-1)` | `{…,"count":-1}` ← 兜底**不生效** |
  | langchaingo | `WithMemoryMaxChars(0)` | `{"task":…,"project":…}` — 被省掉 |
  | langchaingo | `WithMemoryMaxChars(-1)` | `{…,"maxChars":-1}` ← **原样发出** |
  | 后端 | `POST /experiences` `count:4` | 4 条，HTTP 200 |
  | 后端 | `POST /experiences` `count:-1` | **0 条，HTTP 200** |
  | 后端 | `POST /experiences` `count:0` | 0 条，HTTP 200 |
  | 后端 | `POST /icl-prompt` `maxChars:4000` | 提示词 **528** 字符，回显 4000 |
  | 后端 | `POST /icl-prompt` `maxChars:-1` | 提示词 **53** 字符（**-90%**），回显 **100**，`experienceCount` **仍为 4**，HTTP 200 |
  > 现有适配器测试**全部使用 mock client**（`mockClient` / `captureClient`），它们在
  > `dto.ExperienceRequest` 层面取值，**结构上无法观测序列化**——这就是这条缺陷能长期
  > 存活的原因，也是本轮必须换成真实客户端 + httptest 的原因。两条路径**都不报错**：
  > 负 `count` 让 eino/genkit 静默返回「没有相关记忆」，而 eino 自己的注释明确写着它
  > 之所以向上抛错正是因为「静默空结果与『没有相关记忆』无法区分」；负 `maxChars` 更隐蔽
  > ——4 条经验**确实检索到了**，只是被压进 53 字符里。

<!-- 以下原属 P2-37 的 Evidence 段 -->
- **Evidence（活体，非推断）** 本轮启动 Java demo（37778，PID 43601）实测：
  - 照抄另三家的 `POST` + JSON body → **`{"status":405,"error":"Method Not Allowed","path":"/chat"}`**
  - 用 Java 自己的 `GET /chat?message=…&project=…` → 请求**确实进入了 handler**
    （返回 500 是本机 LLM 密钥失效这一**已知环境问题**，不作为缺陷计）

<!-- 以下原属 P2-38 的 Evidence 段 -->
- **Evidence（真实 JUnit 探针，两条路径并排）**:
  | 路径 | 输入 | 实际结果 |
  |------|------|----------|
  | `Builder.count(0)` | 0 | **REJECTED** — `count must be positive (got 0)` |
  | `new ExperienceRequest("t","/p",0)` | 0 | wire = `{task=t, count=0, project=/p}` |
  | `new ExperienceRequest("t","/p",-1)` | -1 | wire = `{task=t, count=-1, project=/p}` |
  | `new ExperienceRequest("t","/p",null)` | null | wire = `{task=t, count=4, project=/p}` ✅ |

<!-- 以下原属 P2-39 的 Evidence 段 -->
- **Evidence（活体，2026-10-04，同一份三行输入打两个端点）**:
  | 端点 | 外层事务 | 结果 |
  |------|----------|------|
  | `POST /api/import/sessions` | **无** | **HTTP 200**，`imported: 2, errors: 1`，错误信息精确到 `value too long for type character varying(255)`；**两条合法行成功落库** |
  | `POST /api/import` | **有** | **HTTP 500**，仅 `{"status":500,"error":"Internal Server Error"}`；**两条合法行一条未落库** |
  - 直查库确认：`r242-a-ok1` / `r242-a-ok2` 存在，`r242-b-ok1` / `r242-b-ok2` **不存在**（已回滚）。
  - 后端日志中确认出现 **`UnexpectedRollbackException`** 与 **`Transaction silently rolled back`**。
  - 探针数据已清理（3 行删除，残留 0）。

<!-- 以下原属 P2-40 的 Evidence 段 -->
- **Evidence（活体）**:
  | 端点 | 活体 | 文档 | SDK 方法 | demo 端点 |
  |------|------|------|----------|-----------|
  | `GET /api/summaries?limit=2` | **200**，返回 items | ✅ | ❌ | ❌ |
  | `GET /api/prompts?limit=1` | **200**，返回 items | ✅ | ❌ | ❌ |
  | `POST /api/ingest/session-end` | ✅ | ✅ | ✅ 四家 | ✅ |
  | `POST /api/ingest/user-prompt` | ✅ | ✅ | ✅ 四家 | ✅ |
