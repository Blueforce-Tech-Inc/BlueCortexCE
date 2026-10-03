# Backend Review — Reproduction Transcripts, batch 4

> **Archived**: 2026-10-04 (round 241) from [`../drafts/backend-review-findings.md`](../drafts/backend-review-findings.md).
> **Contents**: the `- **Reproduction**:` sections of P2-25, P2-26, P2-30 and P2-31, moved **verbatim**.
> **Why this batch — and why it is a different kind of move.** The working file hit the
> `MAX_LINES=1000` threshold for the **ninth** time in fourteen rounds, and the first three
> batches had already exhausted every `复核记录` section. A measured transcript — a captured
> wire body, a live curl result, a table of row counts — is **reproducible evidence**, not the
> reasoning behind a decision. The ⏸ rule protects the latter, so **Scope / Problem /
> Evidence / Status stay in the working file** and only the transcript moved.
> Each pointer in the working file names this file, and every transcript here names the
> date, the endpoint and the technique used, so it is reproducible on demand.
> **This is the first batch to move a section other than `复核记录`; if the project prefers
> the earlier narrower rule, the four sections can be restored from here without loss.**
> **Continues** provenance batches 1-3 — `2026-10-03_backend-review-provenance.md` (round 237),
> `2026-10-04_backend-review-provenance-2.md` (round 238),
> `2026-10-04_backend-review-provenance-3.md` (round 241) — all immutable.
> **Verbatim equality against `git show HEAD` was verified before the source lines were removed.**
> **Do not modify this file.**

### P2-25（Reproduction 原文）
- **Reproduction**（2026-10-03，活体 37777，对同一 task）：

  | 请求 `maxChars` | 响应回显 | 实际 prompt 长度 |
  |---|---|---|
  | 省略 | 4000 | 680 |
  | `0` | **100** | **53** |
  | `-5` | 100 | 53 |
  | `100` | 100 | 53 |
  | `4000` | 4000 | 680 |

- **Status**: ⏸ **记录不修** —— 改 `@Schema` 描述即改**对外 OpenAPI 契约**，
  按既定纪律留待项目决策。**文档层已先行更正**（沿用 P2-11 / P2-22 的先例）：
  `docs/API.md` 与 `docs/API-zh-CN.md` 的 `maxChars` 字段表现已写明 100 的下限、
  `0` 与负数被钳到 100、以及「不存在 0 表示默认的路径」，并说明响应会回显实际生效值。
- **关联修复（第 225 轮已实施，属 SDK 侧、非契约变更）**: Java SDK 的
  `ICLPromptRequest.toWireFormat()` 原为 `if (maxChars != null)`，会把 `0` 原样发到
  wire 上，与 Go SDK 的 `json:"maxChars,omitempty"`、Python SDK 的 `if max_chars:`
  **不一致**——那两家会省略 0 从而正确落到后端默认。已改为 `maxChars != null && maxChars > 0`。
  少发一个可选字段不改变 wire 契约，且与另两家对齐。**JS SDK 无防护**（`buildICLPrompt`
  原样透传 req），其 `examples/http-server` 的 `/chat` 默认 `maxChars: req.body.maxChars ?? 0`，
  属 JS/TS SDK 方向的发现，留待该方向轮次处理。
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）


### P2-26（Reproduction 原文）
- **Reproduction**（2026-10-03，活体 37777，读 `mem_observations` 实际值）：
  设 `{"facts":["alpha","beta"],"concepts":["c1","c2"]}` → DB 为
  `['alpha','beta'] / ['c1','c2']`；发 `{"facts":[],"concepts":[]}` → DB 为 `[] / []`
  （**后端确实接受空数组清空**）；再发 Go `omitempty` 实际产出的 `{"title":"rt226 probe"}`
  → DB **纹丝不动**仍为 `['alpha','beta'] / ['c1','c2']`，HTTP 却是 200 `updated`。
  Go 序列化行为另用探针逐项确认：`ptr("")` → `{"title":""}`、`[]string{}` → `{}`、
  `nil` → `{}`、`map[string]any{}` → `{}`、`["x"]` → `{"facts":["x"]}`。
- **四家对拍**: Go ✗ 无法清空；Java `@JsonInclude(NON_NULL)` 只排除 null、空 list 会发出 ✓；
  Python `if val is not None`（`[]` 非 None）会发出 ✓；JS `JSON.stringify` 保留 `[]` 且
  源码注释明写「null = clear field, undefined = skip」✓。**Go 是唯一的问题家。**
  附带一处文档误导：Python `ObservationUpdate` 的 docstring 写着
  「matching Go's pointer-field-with-omitempty pattern」，但 Go 的 `Facts` **不是指针**，
  两者行为实际不同 —— Python 把一个错误模式当成了对齐基准。
- **Status**: ⏸ **记录不修** —— 对齐只有两条路，都属**公开 API 变更**：把三个字段改成
  `*[]string`（**破坏所有现有调用点**，源码不兼容），或给结构体加自定义 `MarshalJSON`
  （**改变现有代码发上 wire 的内容**，`[]string{}` 从「不变」变成「清空」）。
  按既定纪律留待项目决策。**文档层已先行说明**：`README.md` / `README-zh-CN.md` 新增
  「List And Map Fields Cannot Be Cleared / 列表与映射字段无法清空」小节，写明两种表现、
  指针字段为何不受影响、与其他三家的差异及两条修复路径各自的代价。
  **Go SDK 代码一字未改。**
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）


### P2-30（Reproduction 原文）
- **Reproduction**（2026-10-03，httptest 抓实际出参，非读码推断）:

  | 调用 | 实际 rawQuery |
  |------|--------------|
  | `ListObservations(Limit: -5)` | `""`（参数被丢弃） |
  | `ListObservations(Limit: 0)` | `""` |
  | `ListObservations(Limit: 100)` | `limit=100` |
  | `Search(Limit: -5)` | `project=%2Fp&query=q`（无 `limit`） |
  | `GetExtractionHistory(limit: -5)` | 返回 `cortex-ce: validation error on limit: limit must not be negative` |

  后端裁定（活体 37777，`ViewerController` 的 `Math.min(Math.max(1, limit), 100)`）：
  `GET /api/observations?limit=-5` → **1 条**；`?limit=0` → **1 条**；不带 `limit` → **20 条**。
  `GET /api/search?...&limit=-5` → **1 条**，不带 → **5 条**。
- **Impact**: 真实伤害在**移植路径**上。Java 是四家中**唯一**会把这个错误告诉调用方的；
  把 Java 代码移植到 Go 或 JS，校验**整个消失**且没有任何提示——Go/JS 的调用方拿到的是
  一页**满额 20 条**、结构完全正常的结果，比报错更难发现；Python 拿到的是被钳成 1 条的
  退化页。典型触发场景是调用方自己算分页（`limit = total - offset` 之类）算出负数。
- **Status**: ⏸ **记录不修** —— 让 Go 对负数抛错，会让**当前能正常返回**的调用方开始失败，
  属公开 API 行为变更；四家对齐更属跨 SDK 契约决策。**文档层已先行更正**：
  Go SDK 两份 README 现明写 `Search` / `ListObservations` / `GetExtractionHistory`
  三者对负数的**不同**处理，并附四家对拍表与后端裁定值。**Go SDK 代码一字未改。**
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md)（第 238 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。

### P2-31（Reproduction 原文）
- **Reproduction**（2026-10-03，活体 37777，同一条 task）：

  | 请求 | 响应回显 `maxChars` | 实际 prompt 长度 |
  |------|--------------------|-----------------|
  | 省略字段 | 4000 | **564** 字符 |
  | `maxChars: 0` | 100 | **53** 字符 |
  | `maxChars: -5` | 100 | **53** 字符 |
  | `maxChars: 4000` | 4000 | 564 字符 |

  **200 OK、无任何错误**，调用方只会看到一份被压到 53 字符的注入。
- **Status**: ⏸ **记录不修** —— Go 里没有 `if x > 0` 这种写法可用；两条路都属
  **公开 API 变更**：把字段改成 `*int`（破坏所有调用点）或写自定义 `MarshalJSON`
  （改变现有 wire 内容）。**本轮已修 Python**：守卫由 `if max_chars:` 改为
  `max_chars > 0`，与第 225 轮的 Java、第 228 轮的 JS 同一处修法。
  **同时更正了两处源码注释**——Java 的 `ICLPromptRequest` 原写「matches the Go SDK
  … and the Python SDK … so all four SDKs agree on what 0 means」，JS 的
  `buildICLPrompt` 原写「and the Go and Python SDKs, which omit 0 as well」：
  **两句都只对 0 成立**，现已写明 Go 是唯一例外、且注明在 P2-31 关闭前**不要再说
  四家一致**。**双向注入验证为真**：把 Python 守卫改回 `if max_chars:` →
  负数用例**恰好 1 条**失败（零值与正值用例理应不失败，正数那条的作用正是防过度修复）；
  恢复后 Python **431** 全过（原 428），Java **192**、JS **239** 均与基线一致。
- **复核记录**: 已归档 → [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md)（第 238 轮逐字迁出；Scope / Problem / Evidence / Status 按 ⏸ 规则全部保留在本文件）。
