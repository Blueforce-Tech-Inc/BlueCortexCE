# Backend Review Evidence 23 — P2-59 / P2-62 迁移块（第 274 轮）

> **归档规则**：工作文件 `docs/drafts/backend-review-findings.md` 保留各条目的
> **Problem**（问题是什么）与 **Status**（为什么这么定）；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 2：P2-59 原始条目（逐字，第 263 轮建立）

### P2-59: Java demo 十个控制器把后端 4xx 变成 500，**其中两个方向相反**——凭空造 404，和把 404 放大成 500

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-15.md`](../archive/2026-10-04_backend-review-evidence-15.md)（第 265 轮）。
- **Problem**: 四家 demo 在本机同时起（Java 37778、Go 37779、Python 37780、JS 37781），
  对**同一个请求**打同一句话，结果是**两个相反方向**的分裂：
  ①**放大**——后端 `PATCH /api/session/{sessionId}/user` 对未知 session 返 **404**
  `{"error":"Session not found: no-such-session-xyz-263"}`；
  Python / Go / JS 三个 demo **原样透传 404**，Java demo 返 **500**，
  且 body 是 `{"error":"Failed to update session user: 404 Not Found: \"{\\\"error\\\":...\\\"}\""}`
  ——**后端那段 JSON 被当成字符串二次转义塞进 `error` 字段**，调用方解析出来是一坨带转义的 JSON 文本。
  ②**凭空造**——方向相反。后端 `GET /api/extraction/{templateName}/latest` 在**没有抽取结果**时
  返的是 **HTTP 200** + in-band `{"status":"not_found", ...}`（活体实测，非 404）；
  Python / Go / JS 三个 demo 透传 **200**，Java demo 却判 `!result.isFound()` 后**自己造了个 404**。
  这不是边角：`ExtractionResponse` 的 Javadoc 写明 `user_preference` 是**唯一随包的模板**，
  而任何新项目上它必然处于「还没抽过」的状态——**所以 Java demo 的这条路由在常见路径上就返 404**。
  根因很干净：12 个控制器共 **40 个 `catch (Exception e)` 块**（脚本按花括号深度统计），
  **只有 3 个**走到 `DemoErrors`——`ObservationsController` 2 个、`FeedbackController` 1 个，
  **其余 10 个控制器一个都没有**，一律 `internalServerError()`。
  顺带排除一个伪线索：Go demo 的 `/batch-observations`、`/create-observation` 与另三家不同名，
  是**有意为之**（源码注释写明为避开 Go 1.25+ ServeMux 与 `/observations/{id}` 的路径歧义），
  其 README 也已登记该差异——不是缺陷。
- **已修**: `DemoErrors` 的类 Javadoc 原先写着「**Controllers** use `statusOf` / `messageOf`」，
  在只有 2/12 控制器这么做时读起来像全覆盖声明。已按现状改写为精确表述
  （12 个控制器 / 40 个 catch 块 / 3 个走 helper / 10 个控制器没有），
  并点名 `PATCH /demo/session/user` 作为反例。**零行为变更**，`mvn -o test` 通过。
- **不修的理由**: 修它要改 10 个控制器的 catch 块，**改的是 demo 对外的 HTTP 状态契约**
  （500→404/400，且要决定 `error` 字段是否保留 SDK 前缀文本——Go/JS 加前缀、Python 不加，
  三家自己就不一致）。按既定规则**对外契约变更记录不单方面实施**。
  另注：`ErrorField` 的正则对 Spring 默认错误体（`{"timestamp":...,"error":"Bad Request"}`）
  会取出 `"Bad Request"`，**这条是后端本身就没给解释**，不算信息丢失，故不单列。

---

## 块 2 / 2：P2-62 原始条目（逐字，第 270 轮建立）

### P2-62: `ProjectFilterService` 的 `~username` 展开**丢弃用户名**、改写到当前用户家目录——Javadoc 说的是另一回事
- **Scope / Evidence**: `backend/.../service/ProjectFilterService.java:109-152`（含本轮已修正的
  Javadoc）；测试头 `src/test/java/.../ProjectFilterServiceTest.java:10-14`。
- **Problem**: 原 Javadoc 写 "Handles both `~` (current user) and `~username` (specific user)
  forms"，行内注释写 "expand to that user's home (best effort)"——**代码里不存在 resolve 分支**：
  `replaceFirst("^~" + username, userHome)` 把 `~username` **整段**替换成**当前**用户的家目录，
  用户名被**静默丢弃**；且 `username` 未转义即拼进**正则**。**反射实测**：`~/proj →
  /Users/<当前>/proj`；`~alice/proj → /Users/<当前>/proj`（**alice 消失**）；`~alice`（无斜杠）
  原样返回；`~a.b/proj → /Users/<当前>/proj`（`.` 成通配）。
- **影响面**：该类**未注册为 Bean**、生产代码**零引用**，类 Javadoc 自陈 "not currently wired
  into any processing pipeline" —— **当前影响为零**；风险是有人照该 Javadoc 接上后静默改写路径。
  **测试头曾声称覆盖 "expandHomeDirectory edge cases"，而全文零个 `~` 用例**。
- **Status**: ⏸ **记录不修** —— 正确的 `~username` 解析属**设计决策**，且**无生产调用方可验证**。
  **已修（零行为变更）**：Javadoc 改为如实描述并附实测结果；测试头的不实声明已更正，并写明
  **为何不补测试**——补了就等于把可疑行为钉死。后端 **167 测试全绿**、`mvn package` EXIT=0。
