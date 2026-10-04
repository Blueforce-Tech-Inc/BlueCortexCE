# Backend Review — 证据归档（第 263 轮第十四批）

> 源文件：`docs/drafts/backend-review-findings.md`　归档日期：2026-10-04
> 规则：⏸ 条目的 `Scope` / `Evidence` 属可复现证据，可迁出；`Problem` 与 `Status`（决策推理）留在工作文件。

## P2-58 · Scope

- **Scope**: 四家的观测响应类型：
  `go-sdk/cortex-mem-go/dto/observation.go`、
  `cortex-mem-spring-integration/.../dto/ObservationResponse.java`、
  `js-sdk/cortex-mem-js/src/dto/observation.ts`、
  `python-sdk/cortex-mem-python/cortex_mem/dto.py`。
  **这是第 258 轮记录的请求侧 `platformSource` 缺口（无一家 SDK 暴露它）的响应侧同族问题。**

## P2-58 · Evidence

- **Evidence**: 四家逐家实测（活体字段集 = 同一条观测的 34 个键）：

  | SDK | 覆盖 | 未覆盖（已排除三个向量列） |
  |-----|------|--------------------------|
  | Go | 24/34 | 上表 **7** 个 |
  | JavaScript | 24/34 | 上表 **7** 个（完全相同） |
  | Python | 23/34 | 上表 7 个 + `extractedData`（该 SDK 另有 camelCase 别名，**大概率是我的探针没匹配到，非缺陷**） |
  | Java | — | 上表 **7** 个（`ObservationResponse` 40 个组件） |

  **关键时间证据**：V17 与 V18 迁移均提交于 **2026-04-16**，
  而 `go-sdk/cortex-mem-go/dto/observation.go` 的**最近一次改动是 2026-10-02**——
  **DTO 在 V17/V18 之后被改过，却没有补上这两个迁移新增的列**。
  所以这不是「SDK 早于迁移、没来得及跟上」，而是**改过之后仍然漏了**。

