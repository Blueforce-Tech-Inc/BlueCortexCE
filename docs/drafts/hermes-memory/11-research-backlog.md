# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-04-23（代码实地复核 + 安全缺口更新）

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
- [x] **单文件逼近 50KB 时预拆分**: AGENT.md 体量预警已维护；`06` 在 48903 字节（逼近 45KB 预警线，暂未超限但后续写大段应先拆）。
- [x] **Preemptive split doc 02**（2026-04-24）：`02` 47794 字节 → 拆出 §11–§15 深度专题至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md)（16066 + 2730 字节）；更新 index.md 读序 + AGENT.md 预警表。
- [x] **ContextEngine 可插拔架构新增分析**（2026-04-24）：`agent/context_engine.py`（184 lines）ABC 抽象 + 插件发现 + 生命周期 + Token 追踪 + 与 MemoryProvider 对比 → [`27`](60-evolution/27-context-engine-pluggable-architecture.md)；更新 index.md 读序（补全 23–27 编号）。
- [x] **上游 hermes-agent 同步（2026-04-23 代码实地复核）**: 2026-04-15 之后 memory 相关文件**无新提交**；`memory_tool.py`、`holographic/`、`session_search_tool.py` 均无变化。快照 [`12`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md) · [`13`](60-evolution/13-run-agent-memory-wiring-snapshot.md) 仍准确。
- [x] **Auxiliary Client 深度解析**：`agent/auxiliary_client.py` (2615 lines) Provider Resolution Chain / 7-Provider Fallback / Payment Error Recovery / Codex & Anthropic Adapters → [`23`](60-evolution/23-auxiliary-client-resolution-chain.md)（2026-04-23 新增）
- [x] **ContextCompressor 完整算法整合**：将散落在 06/07/09/17 的 ContextCompressor 分析整合为单一完整参考 → [`24`](60-evolution/24-context-compressor-full-algorithm.md)（2026-04-23 新增）
- [x] **Hindsight 本地嵌入 Daemon + PostgreSQL Schema**：hindsight-all 包架构 / HindsightEmbedded vs HindsightServer / Profile 机制 / pgvector/pgvectorscale/vchord 多扩展 / 连接池配置 / Schema 隔离 / LLM Provider 支持 / Docker 部署对比 → [`25`](60-evolution/25-hindsight-local-embedded-daemon-and-postgresql-schema.md)（2026-04-23 新增；源自 Hindsight 官方安装文档 + API 参考 + Hermes 插件源码）
- [x] **Compression Model Fallback 上游新增（2026-04-24）**：`772cfb6c` — ContextCompressor 对专用摘要模型永久错误（404/503/model_not_found）fallback 到主模型，防 600s cooldown 导致 context unbounded growth；更新 [`17`](60-evolution/17-smart-compression-and-exhaustion-fix.md) §5b + 可执行行动；对应 backlog 辅助 LLM fallback 缺口

## 新增分析（2026-04-24 cron 巡检）

- [x] **Context 文件扫描机制深度解析（`_scan_context_content` vs `_scan_memory_content`）**：`agent/prompt_builder.py:55` + `tools/memory_tool.py:90` 两套防线完整源码对照 → [`06-context-file-scanning-deep-dive.md`](20-recommendations/06-context-file-scanning-deep-dive.md)；补充 `05` 安全缺口文档 §2 中"AGENTS.md / SOUL.md class file scanning" 条目；更新 index.md 读序。
- [x] **辅助 LLM fallback（代码实地核实，2026-04-24）**：CE `LlmService` 仅单 `ChatClient` 无 fallback；失败时静默返回 `LlmResponse.empty()`，7 级 Provider 链完全缺失。代码证据已追加至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5；可执行行动分短/中/长期三档。

## 旁路型落地（BlueCortexCE）

- [x] **Context 出口 + ingest 侧扫描（代码实地复核，2026-04-23）**：
  - **Context 输出无 fence**：`/api/context/inject`、`/api/context/semantic`、`/api/context/generate` 返回纯文本，`ContextService.renderEmptyState`（行 873）输出 `"# project — no memories yet"`，无任何围栏标记。`context-injection.ts` 写的 `<claude-mem-context>` 用于**文件注入**（CLAUDE.md 写入），不用于 LLM 上下文输出层。
  - **无伪造 fence strip**：即使未来添加围栏，输出层也无对应 strip 逻辑。Hermes `sanitize_context` 先 strip 伪造闭合标签再包装，CE 完全缺失。
  - **TS 层有递归防护 tag stripping**：`webui/src/utils/tag-stripping.ts` 剥离 `<claude-mem-context>`、`<private>`、`<system_instruction>`、`<system-reminder>` 等，**用于防止观察内容被重复注入**，非 LLM 注入安全围栏。
  - **无 injection 模式 + 不可见 Unicode 扫描**：后端 `IngestionController.handleUserPrompt` 仅长度截断，无 `_scan_memory_content` 等效。TS 层 `tag-stripping.ts` 仅递归防护标签，不扫描 injection 模式或零宽字符。
  - 结论：缺口已确认，详见 [`05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)（2026-04-23 更新）。
- [x] **辅助 LLM fallback — `/api/context/generate` 对齐确认（2026-04-24）**：`/api/context/generate` → `ContextService.generateContext()` 使用**纯 DB 查询**（observationRepository/summaryRepository），无 LLM 合成路径，完全不经过 `LlmService`。因此 **AuxiliaryClient per-task 模型 + 链式降级在 context generate 路径完全不适用**。真正受影响的是调用 `LlmService` 的路径：SummaryGeneration / MemoryRefine / StructuredExtraction / AgentService。代码证据 + 可执行行动见 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5。

## 与其它 backlog 的边界

| 文件 | 放什么 |
|------|--------|
| **本文件** | Hermes 参照 → CE 的未决项 |
| [`../evolver-memory/11-research-backlog.md`](../evolver-memory/11-research-backlog.md) | Evolver/产品/数据模型未决项（可与安全交叉，以 `05` 为技术锚点） |

全局导航：[`../memory-research-hub.md`](../memory-research-hub.md)
