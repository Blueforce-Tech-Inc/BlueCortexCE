# Backend Review Evidence 54 — 第 353 轮已结案条目的整条正文（第 353 轮迁出）

> **归档规则**：承 `-50` ~ `-53` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）；
> 条目首尾的空行、以及夹在其后的 HTML 注释，一律原样留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**（第 340 轮 `-46` 归档曾因手写
> 字节数出错而整份返工，本批起数字一律取自计算值，不再手写）。

> 本批迁出 `P2-16` / `P2-17` 两条，**全部是 ⏸ 已记录不修**
> （对外契约变更或新增能力，按规则不单方面实施），对本循环**没有待办动作**，故可安全迁出。
> 选它们是因为 `backend-review-findings.md` 已到 **1430 行 / 149395 字节**（上限 1500 / 150000），
> 字节余量不足以为本轮两条新条目让路；未结案条目一律不动。
>
> 迁出前实测 **1430 行 / 149395 字节**，迁出后 **1404 行 / 147454 字节**。

## 块 1 / 2：P2-16 全文（第 353 轮逐字迁出，原 21 行）

### P2-16: Java SDK 没有任何类型化异常，HTTP 状态码只能靠遍历 cause 链取得
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 跨 SDK 错误面严重不对称——Go 有 `APIError` 且 `Unwrap()` 覆盖 11 个哨兵错误、
  Python 有 13 个状态码异常类 + 谓词（共 27）、JS 有 16 个，**Java 为 0**。
  实测（stub 返回 `404 {"error":"Observation not found: abc"}`）：
  抛出的是裸 `java.lang.RuntimeException`，消息为
  `getObservationsByIds failed: Observation not found: abc`（有后端原因、**无状态码**），
  cause 链末端才是 Spring 的 `HttpClientErrorException$NotFound`。
  异常类型本身**没有任何状态访问器**。
- **影响面**：要区分「没有这条观测」（404）与「后端挂了」（5xx）的调用方必须自己写
  cause 链遍历。项目自己的 Java demo 正是为此写了一份
  `examples/cortex-mem-demo/.../DemoErrors.java`（`statusOf` / `messageOf` / `clientStatus`），
  其 javadoc 明确记载了这个痛点——**这是本条最有力的证据**：
  同一仓库内的消费者已经为此付出过实现成本。
- **Status**: ⏸**已记录，不实现**。补齐意味着给本 SDK **新增公开异常类型**
  （如 `CortexMemException` / `APIError`），属新增对外 API 而非修 bug，
  且会改变所有 25 个方法的异常类型，对已有调用方的 `catch` 行为有影响，
  与 P2-13/P2-14/P2-15 同一套判断。本轮已做的是**如实记录**：
  两份 README 的 Error Handling 章节新增「HTTP 状态码不在异常上」小节，
  给出实测的异常形态、可直接复制的 `statusOf` 辅助方法、
  以及「这是与另三家的已知不对称」这一事实。

## 块 2 / 2：P2-17 全文（第 353 轮逐字迁出，原 15 行）

### P2-17: `EXTRACTION_MAX_BATCHES` 在随附默认值下永远不可能生效
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 该上限被文档当作真实生效的调参手段，但**在随附默认值下它是死的**。
  循环条件是 `i < userObs.size() && i < maxTotal`，其中
  `maxTotal = maxObservationsPerBatch × maxBatchesPerTemplate = 20 × 10 = 200`；
  而 `userObs` 是候选列表按用户分组后的一个切片，候选列表本身已被
  `initialRunMaxCandidates`（默认 100）截断。因此单个用户的观测数**永远 ≤ 100**，
  批次数上限是 `ceil(100/20) = 5`，**永远够不到 10**。
- **精确边界**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **影响**: 运维看到「Batches per template per run: 10」这一行，会合理地以为它是
  抽取成本的主要闸门，实际唯一生效的闸门是候选上限。这是**配置契约层面的误导**，
  修法要么调默认值，要么在 `ExtractionConfig` 里对二者做一致性校验。
- **Status**: ⏸**已记录，不实现**。改变任一默认值的取值范围属对外配置契约变更。
  本轮已在 `23.md` §23.5/§23.7、`docs/structured-extraction.md`、`docs/DEPLOYMENT.md`
  四处**按各自措辞**更正为「随附默认值下不生效」并写明生效条件。
