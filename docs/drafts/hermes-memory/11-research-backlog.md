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

- [x] **Multi-Provider 插件发现架构**：`plugins/memory/__init__.py` 全解 → [`14`](60-evolution/14-multi-provider-plugin-discovery.md)（discover/load/Collector/CLI 发现模式；8 Provider 清单）
- [x] **Session DB Flush duplicate-write fix**：`run_agent.py` `_flush_messages_to_session_db` + `_last_flushed_db_idx` → [`15`](60-evolution/15-session-db-flush-and-duplicate-fix.md)
- [x] **Extended MemoryProvider Hooks**：`on_pre_compress` / `on_memory_write` / `on_delegation` / `queue_prefetch` → [`16`](60-evolution/16-extended-memory-provider-hooks.md)（RetainDB 三线程预取模型详解）
- [x] **上游 Smart Compression + Exhaustion Loop Fix（2026-04-14）**：→ [`17`](60-evolution/17-smart-compression-and-exhaustion-fix.md)（Smart tool collapse with 20+ tool-specific summaries / MD5 dedup / Anti-thrashing 2-pass <10% / `failed: True` + session auto-reset via gateway）
- [x] **三新增 Provider 分析**（byterover/hindsight/openviking）：→ [`18`](60-evolution/18-three-new-memory-providers.md)（层级 Context Tree / 知识图谱+Reflect / 文件系统 URI+tiered context+atexit；更新 `14` Provider 清单）
- [x] **Gateway 后台 Session 过期 Watcher**：`gateway/run.py` `_session_expiry_watcher` + `memory_flushed` 持久化 + 当前记忆状态注入防覆盖 + cron 跳过 + 重试熔断 → [`19`](60-evolution/19-gateway-session-expiry-watcher.md)（2026-04-23 新增）
- [x] **Tool Result Persistence 3-Layer Defense**：`tools/tool_result_storage.py` + `tools/budget_config.py` — per-tool self-truncation / per-result sandbox persist (`maybe_persist_tool_result`) / per-turn 200K aggregate budget (`enforce_turn_budget`) / `read_file` pinned at `inf` 防循环 → [`20`](60-evolution/20-tool-result-persistence.md)（2026-04-23 新增）
- [x] **Session Search Tool — FTS5 + LLM Recall**：`tools/session_search_tool.py` — 双模式（空查询=recent / 有查询=FTS5 search）/ 智能截断（position-aware windowing）/ 并行 summarization / auxiliary model / delegation chain resolution → [`21`](60-evolution/21-session-search-tool.md)（2026-04-23 新增）
- [x] **Hindsight 知识图谱深度解析**（TEMPR 四路检索 / Observation 合并 / 实体消解 / 双时间模型 / Reflect Agentic Loop / Disposition System）：→ [`22`](60-evolution/22-hindsight-knowledge-graph-deep-dive.md)（2026-04-23 新增；源自 Hindsight 官方文档 + Hermes Agent 源码；对照 CE 差距并按实施难度排序可执行借鉴项）
- [ ] **单文件逼近 50KB 时预拆分**：顶格稿件清单与 `find | wc` 命令见 [`AGENT.md`](./AGENT.md) **体量预警**（2026-04-19 快照含 `06-memory-provider-hooks-inventory` 等）；避免在单文件末尾无限堆节。
- [ ] **上游 hermes-agent 同步**：`memory_manager` / `memory_provider` 模块说明是否仍与 **`MemoryStore`** + **`run_agent` 双线接线**一致（**`BuiltinMemoryProvider`** 文档 vs 实现）；快照 [`12`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md) · [`13`](60-evolution/13-run-agent-memory-wiring-snapshot.md)。若有差分则更新 [`03`](20-recommendations/03-borrowing-synthesis-executable-priorities.md) §1 或新增增量稿。
- [x] **Auxiliary Client 深度解析**：`agent/auxiliary_client.py` (2615 lines) Provider Resolution Chain / 7-Provider Fallback / Payment Error Recovery / Codex & Anthropic Adapters → [`23`](60-evolution/23-auxiliary-client-resolution-chain.md)（2026-04-23 新增）
- [x] **ContextCompressor 完整算法整合**：将散落在 06/07/09/17 的 ContextCompressor 分析整合为单一完整参考 → [`24`](60-evolution/24-context-compressor-full-algorithm.md)（2026-04-23 新增）

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
