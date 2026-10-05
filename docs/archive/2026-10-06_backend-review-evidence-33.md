# Backend Review Evidence 33 — P2-47 / P2-42 可复现明细迁出（第 282 轮）

> **归档规则**：⏸ 条目保留 **Problem** 与决策部分；此处逐字保留**可复现的实测细节与逐轮经过**。
> **归档文件创建后不得修改。**

> 本批为解除 findings 双阈值（行数 1000 / 字节 102400）而执行，与 `-31` / `-32` 同轮。

---

## 块 1 / 2：P2-47 — 第 254 轮按断言清扫的逐处经过（逐字）

> 迁出区间：工作文件第 700–707 行（删除前快照口径）。

```text
  "Parent project name (worktree mode)"）记载，并写进示例 body。
  **读者据此会以为传了就有用。**
- **Status**: ⏸ **实现侧记录不修**；**文档侧已于第 254 轮更正**（API 文档方向）。
  改文档而非接上 `WorktreeDetector`，是因为后者属新增行为、同样需项目拍板。
  第 254 轮按「**按断言清扫而非按文件**」把同一断言的**全部**表述处一并更正：
  `API.md` 与 `API-zh-CN.md` 的字段表加注「只被接收并记入日志、不落库、不影响行为」，
  `SessionController` 的请求示例 Javadoc、`@Operation` 描述，
  以及 **`ApiRequests.SessionStartRequest` 上的三个 `@Schema`**——
```

## 块 2 / 2：P2-42 — 第 246 轮改用探针文件的验证方法（逐字）

> 迁出区间：工作文件第 605–609 行（删除前快照口径）。

```text
- **Status**: ⏸ **记录不修** —— 修法是加一道 `tsconfig.test.json`（`extends` 主配置、
  覆盖 `exclude`）并并入 `lint`。这属**构建配置变更**，且一旦接上就会一次性暴露
  三个测试文件（含 `examples/http-server/parse-int-param.test.ts`）里既有的潜在类型错误，
  影响面超出单轮范围，留待项目决策。
  **注意**：本条**不影响**第 246 轮的修复——`ObservationUpdate` 是 `src/dto/` 下的
```
