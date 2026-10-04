# 后端审查已解决条目归档（第三批）

> **归档说明**：本文件由 `docs/drafts/backend-review-findings.md` 在第 250 轮后（1019 行，越过 1000 行阈值）逐字迁出，承前两批「已解决条目」归档同规则。
> **归档文件创建后不得修改。**
> 本批为**无条件已解决**的两条（状态已写为 ✅ 已修），决策已定、内容不再变动；
> 原文在 `patrol-rotation.md` 与 `doc-review-task.md` 中另有逐字副本。

### P2-11: 未配置的抽取模板名不被拒绝，拼错与「尚未抽取」得到同样的 `not_found`

- **Scope**: `backend/.../controller/ExtractionController.java` 的 `getLatestExtraction` 与
  `getExtractionHistory`；模板定义在 `application.yml:39`
  （`app.memory.extraction.templates[].name`，目前**只有一项** `"user_preference"`）。
- **Problem**: 两个端点都不校验 `templateName` 是否存在。实测（对运行中的后端）：

  | `templateName` | `/latest` | `/history` |
  |----------------|-----------|------------|
  | `user_preference` | `200` `status: "not_found"`，`template` 回显 `user_preference` | `200` 空列表 |
  | `user-preferences`（不存在的名字） | `200` `status: "not_found"`，`template` 回显 `user-preferences` | `200` 空列表 |

  **数据字段完全相同**：`status` 相同，四个数据字段（`sessionId` / `extractedData` /
  `createdAt` / `observationId`）均为 `null`，`message` 也相同。两者的**唯一**差异是
  `/latest` 会把请求里的名字原样回显进 `template`——所以严格说并非逐字节相同
  （第 184 轮此处表述有误，第 185 轮据实更正）。`/history` 则完全不回显名字，
  返回的是逐字节相同的 `[]`，连间接线索都没有。
  更值得注意的是文档侧的传播：`ExtractionController` 的两个
  `@Parameter(example = "user-preferences")` 举的正是这个不存在的名字，ZH 文档还额外举了
  `allergy-info`——两者都不存在。这使错误进入了生成的 OpenAPI 规范：任何据此生成的客户端
  都会去请求一个永远 `not_found` 的模板，而且**没有任何信号**表明是名字写错了。
- **实际影响**：`status` 本身不携带任何信息，调用方无法从它区分「这个项目还没抽取过」与
  「模板名拼错了」——除非自己把响应里的 `template` 与想请求的名字比对，而这恰恰是四家 SDK
  都没做的事（Java 的 `isFound()` 只看 `status`，另三家同样只判 status/空列表）。
  `p.observationId` 之类的诊断线索一律没有，`/history` 连回显都没有。
  四家 SDK 均不校验该参数（它只是路径片段），因此 SDK 路径同样触发。
- **Status**: ✅ **已修复（文档与注解层）**（2026-10-03，第 184 轮 Backend 轮）。
  `ExtractionController` 两处 `@Parameter` 的 `example` 改为 `user_preference`，并在
  description 中写明模板名取自 `app.memory.extraction.templates[].name`、目前只随附一个
  模板、以及未配置的名字不会被拒绝（`/latest` 返 `not_found`、`/history` 返空列表）。
  `docs/API.md` 与 `docs/API-zh-CN.md` 中 10 处 `user-preferences` 全部更正为
  `user_preference`（正确名称此前出现 0 次），ZH 版多出的 `allergy-info` 一并删除。
  **第 184 轮的修正不完整，第 185 轮补齐**：当时只搜了 `docs/` 与控制器，
  `git grep` 复查后又在两处发现同一错误名字——`backend/.../dto/ApiResponses.java:158` 的
  `@Schema(example = "user-preferences")`（**这才是真正喂给生成 OpenAPI 响应 schema 的
  示例**，比控制器的 `@Parameter` 更靠后也更隐蔽），以及 Java SDK
  `ExtractionResponse.java:13` 的 Javadoc。两处均已更正。测试夹具中的同名字符串
  （Go `client_test.go` / `dto_test.go`、JS `client.test.ts`）**刻意保留**：那里任意字符串
  都是合法输入，且「服务端原样回显请求值」本身就是一个值得覆盖的场景。
  **未修的部分**：后端仍不校验未知模板名。要让拼错的名字明确失败就得返回 400，而那会改变
  现有调用方看到的行为，属对外契约变更，按纪律留给后续 Backend 轮次决策。
- **同轮核实无误**：四家 SDK 都**正确**地把抽取结果的键建模为**驼峰**
  （`sessionId` / `extractedData` / `createdAt` / `observationId`）——Map 键不受后端全局
  `SNAKE_CASE` 策略影响，Go 更有专门的 `TestExtractionResult_CamelCaseFields` 钉住这一点。
  且四家都能处理 `not_found` 响应中的 `null`（Python 用 `or ""` 与 `_to_dict`/`_to_int`，
  JS 用 `safeStringOr`/`safeRecord(...) ?? {}`/`safeNumberOr`），不存在解析崩溃。
  本轮另修正文档的 `not_found` 示例：它原本只列 3 个键，而**实际响应有 7 个**
  （`sessionId`/`extractedData`/`createdAt`/`observationId` 均为 `null`）——处理器返回的是
  同一个 `GetLatestExtractionResponse` record，只是把四个构造参数置空，故这些键出现在
  JSON 中而非被省略。


### P2-38: Java SDK 的 `ExperienceRequest` 两条构造路径校验不一致——构造器把非正数 `count` 原样发上 wire

- **Scope**: `cortex-mem-spring-integration/.../dto/ExperienceRequest.java`
  的 `Builder.count()`（第 53-55 行）与公开构造器（第 32-40 行）、
  `toWireFormat()`（第 85-104 行）。
- **Problem**: 同一个类有**两条校验强度不同的构造路径**：
  `Builder.count(Integer)` 显式拒绝非正数（`count must be positive (got N)`），
  而**公开构造器完全不校验**，`toWireFormat()` 又**无条件**执行
  `map.put("count", count != null ? count : 4)` —— 于是经构造器传入的 `0` 或负数
  **原样上线**，而后端 `ExpRagService` 对 `count <= 0` **返回空列表且 HTTP 200**，
  调用方拿到「零条相关记忆」而**无法与真实的空结果区分**。
- **Evidence**: 活体/比对证据已逐字迁入 [`2026-10-04_backend-review-reproduction-6.md`](../archive/2026-10-04_backend-review-reproduction-6.md)（P2-38）。
- **严重度低于 P2-36 的姊妹项**：与第 239/240 轮修掉的 Python、JS 不同，
  Java 的 **builder 路径是受保护的**，README 推荐的也正是 builder；
  **只有公开构造器这条路漏**。但「同一个类两条路径校验不一致」本身仍是缺陷。
- **Status**: ✅ **已修（第 243 轮，Java SDK 方向）** —— 改用**紧凑构造器**校验，
  它同时覆盖规范构造器、两个便捷构造器与 builder，**任何构造路径都绕不过**；
  `Builder.count()` 改为复用同一处 `requirePositiveCount()`，避免消息重复漂移。
  `null` 仍然合法（`toWireFormat()` 仍映射为后端默认 4），**合法输入的 wire 行为一字未变**。
  **按断言清扫确认 `ICLPromptRequest` 本就无此问题**：它的守卫在**序列化层**
  （`toWireFormat()` 只在 `maxChars > 0` 时下发），任何构造路径都绕不过——
  这也正是本条的根因：`ExperienceRequest` 走的是**下发**而非丢弃，
  只能依赖构造期校验，而那个校验当初只写在了 builder 上。
  **4 条新测试**（三条构造路径拒绝非正数 / 正数 1 仍上线 / null 仍映射为 4），
  Java client **139 → 143**，总测试数 **192 → 196**（两份 README 已同步）。
  **双向注入验证为真**：移除紧凑构造器（保留 builder 侧校验，即修复前状态）后
  **恰好 1 条失败**——即构造器路径那条，而 builder、正值 1、null 默认三条对照
  **理应不失败**。
- **复核记录**: 第 241 轮 Demo 方向**计划外发现**。起因是文档方向核对四家 SDK README 时
  注意到：它们详述了 `limit` 负数（P2-30），却对 `count` / `maxChars` 非正数**只字未提**——
  而那正是第 239、240 轮连续出缺陷、第 238 轮记为 P2-36 的字段。**一处自我修正**：
  最初假设「Java 是连续第三家同型缺陷」，读代码时发现 `Builder.count()` **有校验**，
  遂把结论收窄为「构造器与 builder 校验分裂」，并用探针把两条路径并排实测后才落笔
  ——**没有把更耸动的说法直接写进记录**。
