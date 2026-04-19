# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-04-19（`12-upstream-*` 快照链入）

---

## 使用方式

- 完成一项：`[x]`，并补「结论链接」（PR、commit、或对应 `.md` 增补说明）。
- 条目膨胀：迁入 `60-evolution/` 新文件或 `02` 相应节，此处仅留指针。

---

## 文档与上游

- [ ] **单文件逼近 50KB 时预拆分**：顶格稿件清单与 `find | wc` 命令见 [`AGENT.md`](./AGENT.md) **体量预警**（2026-04-19 快照含 `06-memory-provider-hooks-inventory` 等）；避免在单文件末尾无限堆节。
- [ ] **上游 hermes-agent 同步**：`memory_manager` / `memory_provider` 模块说明是否仍与 **`MemoryStore`**（`tools/memory_tool.py`）分叉；**2026-04-19 现场快照**见 [`60-evolution/12-upstream-hermes-agent-memory-snapshot.md`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md)。若有差分则更新 [`03`](20-recommendations/03-borrowing-synthesis-executable-priorities.md) §1 与本快照或新增 `12b-*` 增量稿。

## 旁路型落地（BlueCortexCE）

- [ ] **Context 出口**：`/api/context/*` 与插件路径是否统一 **fence + 消毒**（语义对齐 Hermes `sanitize_context` / `build_memory_context_block`）；验收见 [`04-ce-injection-and-context-api-surface.md`](20-recommendations/04-ce-injection-and-context-api-surface.md) §4。（集成表首跳与 Worker/Java 判别以 `04` §2.1 与 [`../evolver-memory/15-runtime-integration-surfaces.md`](../evolver-memory/15-runtime-integration-surfaces.md) §5 为准，避免只按 §3 误判「Hook = Java `/semantic`」。）
- [ ] **ingest 侧扫描范围**：是否扩展 Hermes 类 **injection pattern + 不可见 Unicode**（[`02`](20-recommendations/02-bluecortexce-recommendations.md) §10.5）；与 [`05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md) 缺口表合并决策。
- [ ] **辅助 LLM fallback**：`/api/context/generate` 等是否与 [`02`](20-recommendations/02-bluecortexce-recommendations.md) §14 AuxiliaryClient 思想对齐（per-task 模型 + 链式降级）。

## 与其它 backlog 的边界

| 文件 | 放什么 |
|------|--------|
| **本文件** | Hermes 参照 → CE 的未决项 |
| [`../evolver-memory/11-research-backlog.md`](../evolver-memory/11-research-backlog.md) | Evolver/产品/数据模型未决项（可与安全交叉，以 `05` 为技术锚点） |

全局导航：[`../memory-research-hub.md`](../memory-research-hub.md)
