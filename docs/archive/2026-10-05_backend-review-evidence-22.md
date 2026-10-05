> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-44** 的 **Problem 段中的探针实测表**，
>   逐字迁出，源文件留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测记录是可复现证据**，
>   ⏸ 规则保护的是**决策推理**。该条的 `- **为什么不是示例代码的 bug**` 与 `- **Status**`
>   **一行未动**。
> **本文件创建后不得修改**。

---

<!-- P2-44 -->
  **探针实测**（`CortexSessionContextBridgeAdvisorTest` 旁的一次性用例，未提交）：
  在 `begin("outer-session", "/outer/project")` 已激活时调一次 `adviseCall`，前后状态为 | 时点 | `isActive()` | `getSessionId()` |
    `getProjectPath()` | |---|---|---|---| | 调用前 | `true` | `outer-session` | `/outer/project` | | **调用后** | **`false`** |
    **`unknown-session`** | **（空串）** |
  断言「外层应当存活」**失败**，即缺陷成立。**全程无异常、无告警**——
  此后同一外层作用域里的任何 `@Tool` 调用都会以 `unknown-session` 与空项目路径入库。
  调用**内部**看到的是 advisor 自己的上下文（`/advisor/project|conv-inner`），
  即内层正确、**外层被毁**。
