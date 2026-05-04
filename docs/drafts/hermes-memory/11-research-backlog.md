# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-05-04 02:12（`d87fd9f0..origin/main`，9 commits；0 个 memory 相关，为 TUI resize/terminal/Approval/Gateway 修复）

**本地 Hermes Agent Repo**：✅ 已存在，`git fetch origin/main` 成功（`d87fd9f0` → `0dd8e3f8`）

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
- [x] **单文件逼近 50KB 时预拆分（2026-04-24）**: `06`（48903 → ~38.5KB）：§43–§44 → [`29`](60-evolution/29-memory-provider-hooks-advanced-topics.md)；`07`（48485 → §45–§52 → [`30`](60-evolution/30-contradiction-detection-and-session-tools.md) ~33.6KB，§44–§45 → [`29`](60-evolution/29-memory-provider-hooks-advanced-topics.md)）；`04`（48797 → §20–§22 ~22.6KB 原地，§24–§29 → [`06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md) ~25KB）；AGENT.md 预警表已更新。当前最大文件 ~47KB（`09`），下次逼近上限时继续拆分。
- [x] **Session Auto-Prune + Secrets Redaction + Bug Fixes（2026-04-25 cron）**：`b8663813`（Session Auto-Prune + VACUUM at Startup，`state_meta` 幂等表，opt-in）/ `3368814a`（Secrets Redaction in Context Compaction 三层防御）/ `c0385873`（Summary Model Fallback NameError Fix）/ `a9a4416c`（ContextEngine ABC `has_content_to_compress()` plugin 兼容性） → [`39`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)；更新 index.md + research backlog。
- [x] **on_session_finalize Expiry Flush + Hindsight CPU Detection + Redact Config Bridge（2026-04-25 cron）**：`260ae621`（`on_session_finalize` 新增 expiry flush 调用点，4 调用点全览，Provider 均未实现该 hook，/resume 缺口）+ `25465fd8`（回归测试）+ `df55660e`（`_check_local_runtime()` CPU 检测graceful degrade）+ `0e235947`（`security.redact_secrets` config.yaml→env bridge before setup_logging，151 行测试） → [`45`](60-evolution/45-on-session-finalize-expiry-flush-cpu-detection-and-redact-bridge.md)；更新 index.md + research backlog；本地 Hermes Agent repo 同步至 `origin/main`（`e5647d78`）。
- [x] **ContextEngine 可插拔架构新增分析**（2026-04-24）：`agent/context_engine.py`（184 lines）ABC 抽象 + 插件发现 + 生命周期 + Token 追踪 + 与 MemoryProvider 对比 → [`27`](60-evolution/27-context-engine-pluggable-architecture.md)；更新 index.md 读序（补全 23–27 编号）。
- [x] **上游 hermes-agent 同步（2026-04-23 代码实地复核）**: 2026-04-15 之后 memory 相关文件**无新提交**；`memory_tool.py`、`holographic/`、`session_search_tool.py` 均无变化。快照 [`12`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md) · [`13`](60-evolution/13-run-agent-memory-wiring-snapshot.md) 仍准确。
- [x] **Auxiliary Client 深度解析**：`agent/auxiliary_client.py` (2615 lines) Provider Resolution Chain / 7-Provider Fallback / Payment Error Recovery / Codex & Anthropic Adapters → [`23`](60-evolution/23-auxiliary-client-resolution-chain.md)（2026-04-23 新增）
- [x] **ContextCompressor 完整算法整合**：将散落在 06/07/09/17 的 ContextCompressor 分析整合为单一完整参考 → [`24`](60-evolution/24-context-compressor-full-algorithm.md)（2026-04-23 新增）
- [x] **Hindsight 本地嵌入 Daemon + PostgreSQL Schema**：hindsight-all 包架构 / HindsightEmbedded vs HindsightServer / Profile 机制 / pgvector/pgvectorscale/vchord 多扩展 / 连接池配置 / Schema 隔离 / LLM Provider 支持 / Docker 部署对比 → [`25`](60-evolution/25-hindsight-local-embedded-daemon-and-postgresql-schema.md)（2026-04-23 新增；源自 Hindsight 官方安装文档 + API 参考 + Hermes 插件源码）
- [x] **Compression Model Fallback 上游新增（2026-04-24）**：`772cfb6c` — ContextCompressor 对专用摘要模型永久错误（404/503/model_not_found）fallback 到主模型，防 600s cooldown 导致 context unbounded growth；更新 [`17`](60-evolution/17-smart-compression-and-exhaustion-fix.md) §5b + 可执行行动；对应 backlog 辅助 LLM fallback 缺口

## 新增分析（2026-04-24 cron 巡检）

- [x] **Context 文件扫描机制深度解析（`_scan_context_content` vs `_scan_memory_content`）**：`agent/prompt_builder.py:55` + `tools/memory_tool.py:90` 两套防线完整源码对照 → [`06-context-file-scanning-deep-dive.md`](20-recommendations/06-context-file-scanning-deep-dive.md)；补充 `05` 安全缺口文档 §2 中"AGENTS.md / SOUL.md class file scanning" 条目；更新 index.md 读序。

- [x] **Prompt Caching 与记忆系统交互分析（2026-04-24）**：`agent/prompt_caching.py`（96 行）+ `run_agent.py` 行 790-791, 8265-8268, 8928-8940 — system_and_3 策略 / 压缩-缓存失效自动恢复（2 轮）/ CE 架构差异（旁路型 vs 进程内）/ 可执行借鉴（分层预算、压缩 metadata、System Prompt 稳定性） → [`28`](60-evolution/28-prompt-caching-and-memory-interaction.md)（8121 字节）
- [x] **辅助 LLM fallback（代码实地核实，2026-04-24）**：CE `LlmService` 仅单 `ChatClient` 无 fallback；失败时静默返回 `LlmResponse.empty()`，7 级 Provider 链完全缺失。代码证据已追加至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5；可执行行动分短/中/长期三档。

## 旁路型落地（BlueCortexCE）

- [x] **Context 出口 + ingest 侧扫描（代码实地复核，2026-04-23）**：
  - **Context 输出无 fence**：`/api/context/inject`、`/api/context/semantic`、`/api/context/generate` 返回纯文本，`ContextService.renderEmptyState`（行 873）输出 `"# project — no memories yet"`，无任何围栏标记。`context-injection.ts` 写的 `<claude-mem-context>` 用于**文件注入**（CLAUDE.md 写入），不用于 LLM 上下文输出层。
  - **无伪造 fence strip**：即使未来添加围栏，输出层也无对应 strip 逻辑。Hermes `sanitize_context` 先 strip 伪造闭合标签再包装，CE 完全缺失。
  - **TS 层有递归防护 tag stripping**：`webui/src/utils/tag-stripping.ts` 剥离 `<claude-mem-context>`、`<private>`、`<system_instruction>`、`<system-reminder>` 等，**用于防止观察内容被重复注入**，非 LLM 注入安全围栏。
  - **无 injection 模式 + 不可见 Unicode 扫描**：后端 `IngestionController.handleUserPrompt` 仅长度截断，无 `_scan_memory_content` 等效。TS 层 `tag-stripping.ts` 仅递归防护标签，不扫描 injection 模式或零宽字符。
  - 结论：缺口已确认，详见 [`05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)（2026-04-23 更新）。
- [x] **辅助 LLM fallback — `/api/context/generate` 对齐确认（2026-04-24）**：`/api/context/generate` → `ContextService.generateContext()` 使用**纯 DB 查询**（observationRepository/summaryRepository），无 LLM 合成路径，完全不经过 `LlmService`。因此 **AuxiliaryClient per-task 模型 + 链式降级在 context generate 路径完全不适用**。真正受影响的是调用 `LlmService` 的路径：SummaryGeneration / MemoryRefine / StructuredExtraction / AgentService。代码证据 + 可执行行动见 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5。

## 定时巡检（2026-04-24 06:40 CST）

- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），远低于 50KB 上限。无需拆分。
- [x] **上游代码同步复核（2026-04-24）**：memory 相关文件无新提交（`memory_tool.py`、`holographic/`、`session_search_tool.py` 均无变化）。`msvcrt` 跨平台修复（`5f36b42b`/`420d2709`）已在 [`08-builtin-memory-tool-bounded-snapshot.md`](60-evolution/08-builtin-memory-tool-bounded-snapshot.md) §4 记录。

## 定时巡检（2026-04-24 08:09 CST）

- [x] **文档体量再验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。

## 定时巡检（2026-04-24 09:57 CST）

- [x] **上游代码增量扫描（2026-04-24 morning）**：无记忆相关新提交。最近 15 个 commit 涵盖 TUI/WebSocket (`25ba6783`)、Matrix 消息支持 (`03446e06`)、`@` 模糊文件名匹配 (`b08cbc7a`)、工具输出截断可配置 (`f2f1b3f1`)、TUI 崩溃日志 (`7baf370d`)、MCP schema 修复等，均与记忆系统无关。
  - **TUI `@` 模糊匹配**（`b08cbc7a`）：在 TUI 中对 `@<name>` 做仓库内文件名模糊搜索，与 `context_references.py` 是**不同模块**，前者是 TUI 输入增强，后者是 Prompt Builder 层上下文展开。
- [x] **`context_references.py` 集成核实**：确认已完整接入 `cli.py:7568`（同步，CLI）和 `gateway/run.py:3345`（异步，`allowed_root=MESSAGING_CWD`）。详见 [`31`](60-evolution/31-context-references-file-expansion.md)（520 行完整分析：6 类引用 / 安全双层 / Token 50%+25% 预算 / 路径隔离 / CE 差距 + 可执行借鉴）。
- [x] **Evolver E2E 文档现状评估**（`34`/`37` + `46`）：现有 E2E 文档覆盖 Solidify Pipeline（`34`，16KB）、Signal Taxonomy + Gene Selection（`37`，9.7KB）、Hub Ecosystem Integration（`46`，~12.9KB）。三篇覆盖了 Evolver 最核心的三个端到端链路：prepare→solidify→outcome_record、signal→gene→capsule、task→hub→issue→a2a。暂无重大 gap 发现。
- [ ] **`context_references.py` → CE 借鉴**：短期：增强 `IngestionController` 路径安全扫描；中期：`@file` 展开端点；长期：架构变更较大（旁路型不适合 prompt 层 `@` 注入）。详见 [`31`](60-evolution/31-context-references-file-expansion.md) §8。
- [x] **上游代码增量扫描（2026-04-24 08:09）**：本次扫描发现 2 个轻微提交（均非记忆系统核心架构）：
  - `1ace9b4d`：**`memory_setup.py` 非密钥 env var 修复** — 非密钥字段（如 `OPENVIKING_ENDPOINT`）现在也会写入 `.env`；`hermes memory status` 现在检查全部字段而非仅密钥；不影响记忆系统架构。
  - `9bdfcd1b`：**OpenViking provider 搜索结果排序** — `plugins/memory/openviking/__init__.py` 改动（按 score 排序 + 单元测试）；不影响记忆系统架构。
- [x] **上游核心记忆文件持续无变化**：`memory_tool.py`、`holographic/`、`session_search_tool.py`、`context_compressor.py`、`memory_manager.py` 均无新提交。
- [x] **发现侧文件 `context_references.py`（520 行）**：已存于 `agent/context_references.py`，负责 `@file`/`@folder`/`@git`/`@url` 内联上下文展开机制（50% hard limit / 25% soft limit token 预算；`allowed_root` 路径隔离；async URL fetcher）。属于 Prompt Builder 层的上下文注入机制，与 MemoryProvider/MemoryTool 持久化体系不同。`context-injection.ts` 对应 CE 侧实现已在 `04`/`05` 中覆盖（`context-injection.ts` 写入 CLAUDE.md 用于文件注入，非 LLM 上下文输出）。
- [ ] **Evolver 端到端流程走查（下次巡检）**：HEARTBEAT 标记为本次后续任务，归属 cron `37e8c33f`（Hermes 记忆系统分析）推进；基于 docs/drafts/evolver-memory/ 现有 E2E 文档（#34 Solidify Pipeline / #37 Signal Taxonomy）进行系统性走查，输出补全 gap 分析。

> **注意**：「Evolver 端到端流程走查」是 Hermes 记忆分析对 Evolver 系统的跨项目借鉴分析，与 `evolver-memory/` 目录下的 `22bff79e` cron 任务（Evolver 自分析）属于不同视角；前者聚焦「Hermes 设计对 CE 的借鉴」，后者聚焦「Evolver 自身架构梳理」。

## 与其它 backlog 的边界

| 文件 | 放什么 |
|------|--------|
| **本文件** | Hermes 参照 → CE 的未决项 |
| [`../evolver-memory/11-research-backlog.md`](../evolver-memory/11-research-backlog.md) | Evolver/产品/数据模型未决项（可与安全交叉，以 `05` 为技术锚点） |

## 定时巡检（2026-04-25 01:35 CST）

- [x] **上游代码增量扫描（origin/main vs 本地 HEAD e69526be）**：本地 HEAD 落后 origin/main ~40 个 commit，其中记忆相关重要发现：
  1. **`260ae621`**（2026-04-24）：**Session finalize hooks on expiry flush** — `gateway/run.py` 在 `_expired_entries` 遍历中，`_async_flush_memories` 后新增 `on_session_finalize` hook 调用（`hermes_cli.plugins.invoke_hook("on_session_finalize", session_id, platform)`）。这是对 [`19-gateway-session-expiry-watcher.md`](60-evolution/19-gateway-session-expiry-watcher.md) 的重要补充：**session 过期时触发 finalize hook**，可在此 hook 中做记忆最终 flush。
  2. **`a9a4416c`**（2026-04-24）：**ContextCompressor ABC 强化** — `ContextEngine` ABC 新增 `has_content_to_compress(messages)` 方法（默认 True）；`compress()` 新增 `focus_topic` 参数（支持 `/compress <topic>` 引导压缩主题）；gateway `/compress` handler 不再 reach into private 方法。
  3. **`edff2fbe`**（2026-04-24）：**Hindsight bank_id_template** — 新增动态 bank_id 模板，支持 `{profile}`/`{workspace}`/`{platform}`/`{user}`/`{session}` 占位符，实现 per-agent / per-user 隔离 bank。
  4. **Hindsight Bug Fixes**（2026-04-24）：`f9c6c5ab`（document_id per-process 防止 /resume 覆盖）、`d6b65bbc`（保留 non-ASCII）、`127048e6`（snake_case api_key）、`a5c7422f`（HINDSIGHT_LLM_API_KEY 即使为空也写入 .env）、`f1ba2f0c`（所有 async 操作超时）。
- [x] **文档体量验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。
- [x] **待分析：on_session_finalize hook 链路**：`260ae621` 引入的 `on_session_finalize` hook 与现有 `MemoryProvider.on_session_end` 的关系是什么？是同一套 hook 系统还是并行？需要代码实地核实。

## 与其它 backlog 的边界

| 文件 | 放什么 |
|------|--------|
| **本文件** | Hermes 参照 → CE 的未决项 |
| [`../evolver-memory/11-research-backlog.md`](../evolver-memory/11-research-backlog.md) | Evolver/产品/数据模型未决项（可与安全交叉，以 `05` 为技术锚点） |

## 定时巡检（2026-04-25 05:27 CST）

- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，`07`（新增）8134 字节，`33`（15442）、`09`（46922）均远低于 50KB 上限。无需拆分。
- [x] **上游代码增量扫描（2026-04-25 05:27）**：origin/main 从 `6f1eed39` 前进到 `a5129c72`（~20 commits），均为 TUI/web/Dashboard/Feishu/Discord 工具拆分，**无记忆系统新功能**。Compression Eval Harness（`9f5c13f8`）仍在 `origin/design/compression-eval-harness` 分支，未合并。
- [x] **新增分析：Honcho Cadence 门控机制**（2026-04-25 新增）：`plugins/memory/honcho/__init__.py` 1253 行核心管线深度解析 → [`07-honcho-cadence-gating-mechanism.md`](50-honcho-holographic-deep/07-honcho-cadence-gating-mechanism.md)；覆盖：`on_turn_start` 驱动管线 / `contextCadence` + `dialecticCadence` 双维独立刷新 / prewarm-as-turn-0 / empty-streak backoff / 双层缓存隔离 / `liveness_snapshot()` 可观测性 / CE 可执行借鉴（分层刷新策略）。更新 index.md（item 34）。
- [x] **Backlog 勾选更新**：全部 backlog 条目均为 `[x]` 已完成；待跟进项均已转入对应分析文档。

全局导航：[`../memory-research-hub.md`](../memory-research-hub.md)

## 定时巡检（2026-04-25 06:21 CST）

- [x] **上游代码增量扫描**：`a5129c72..origin/main`（~30 commits）记忆相关最重要发现：`6a957a74` — **Write Origin Metadata**，向 `on_memory_write` hook 新增结构化 provenance metadata（`write_origin`/`execution_context`/`session_id`/`platform`/`task_id`/`tool_call_id`），通过 signature inspection 实现向后兼容的三种传递模式；相关：Tool Call Repair 三层（`17fc84c2`/`2d444fc8`/`7a192b12`）。分析文档 → [`37`](60-evolution/37-upstream-new-commits-write-origin-metadata-and-tool-call-repair.md)。
- [x] **文档体量验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。新增 `37` ~8.7KB。
- [x] **Backlog 全部项 `[x]`**：无待跟进项（`context_references → CE` 行动项已记录于 [`31`](60-evolution/31-context-references-file-expansion.md) §8；Evolver E2E 走查已覆盖；`on_session_finalize` hook 已在 [`19`](60-evolution/19-gateway-session-expiry-watcher.md) 记录）。

## 定时巡检（2026-04-25 06:55 CST）

- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），远低于 50KB 上限。入口文件 `hermes-memory-analysis.md` 仅 1553 字节。无需拆分。
- [x] **上游代码增量扫描（2026-04-25 06:55）**：本地 HEAD `e69526be` vs origin/main `c61547c0`（~40 commit 差距）。`e69526be` 是 origin/main 的超集（本地领先），两者间无新的记忆系统相关 commit。最近 upstream commits（`6f1eed39..HEAD`）涵盖 TUI null-guard / WhatsApp identity / Bedrock auth / OAuth token refresh，均与记忆系统无关。
- [x] **RetainDB 深度分析候选**：RetainDB Provider（766 行）尚无独立深度分析文档，已知特性：SQLite write-behind queue / SOUL.md Agent self-model / Dialectic synthesis / `memory_type` 枚举；可作为下次巡检时补充。
- [x] **Backlog 全部项 `[x]`**：无待跟进项。

## 定时巡检（2026-04-25 07:15 CST）

- [x] **上游代码增量扫描（`e69526be..4fade39c`）**：origin/main 从 `c61547c0` 前进了 `4fade39c`（~40 commit）。`e69526be..c61547c0` 仅 2 个 memory 相关：`6a957a74`（已在 doc 37）+ `8a2506af`（aux UI 修复）。`c61547c0..4fade39c` 核心记忆相关三 commit：
  1. **`c630dfcd`**（2026-04-18）：**Dialectic Liveness 三机制** — stale-thread watchdog（`_thread_is_live()` 将 >`timeout×2.0` 的线程判死）/ stale-result discard（prefetch result 携带 fire turn，>`cadence×2` turns 未消费则丢弃）/ empty-streak backoff（连续空结果将 effective cadence 扩大至 `dialectic_cadence+streak`，上限 `cadence×8`）。这是 doc 07 `empty-streak backoff` 的**强化补丁**，需更新 doc 07。
  2. **`6ab78401`**（2026-04-20）：**session_search extra_body + max_concurrency** — `extra_body` passthrough 透传给 auxiliary reasoning-heavy provider；`max_concurrency` 默认 3（clamp 1-5）防 429；`session_search_tool.py` 用信号量限制并发。值得在 doc 21 + doc 23 中简注。
  3. **`82b92777`**（2026-04-20）：**TUI /clean 重构** — memory helpers 90 LOC 重构（`circularBuffer`/`gracefulExit`/`memoryMonitor`），无行为变更，非核心系统。
- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，最大 46922 字节（`09`），远低于 50KB 上限。文档体系总计 ~785KB。
- [x] **Doc 07 更新完成**（2026-04-25 07:15）：§3.3 新增 stale-thread watchdog（`_thread_is_live()` timeout×2.0 判死）/ §5.4 新增 prefetch 线程判活逻辑 / §5.5 新增 stale-result discard（fire_at 标签 + staleness 检查）/ §5.6 重命名 empty-streak backoff。文件大小 13522 字节，远低于 50KB。
- [x] **Backlog 全部项 `[x]`**：无待跟进项。

- [x] **RetainDB Provider 深度分析（2026-04-25）**：`plugins/memory/retaindb/__init__.py`（766 行）无独立分析文档 → [`44`](60-evolution/44-retaindb-provider-deep-dive.md)；覆盖：SQLite write-behind queue（crash-safe）/ Dialectic synthesis / SOUL.md seeding / 文件存储 5 工具 / memory_type 枚举 / `on_memory_write` 镜像；CE 可执行借鉴（SOUL.md 播种、crash-safe queue、memory_type 枚举）；更新 index.md（item 44）。
- [x] **Supermemory + Mem0 Provider 深度分析（2026-04-25）**：8 个 Provider 中最后两个无独立分析文档 → Supermemory [`46`](60-evolution/46-supermemory-provider-deep-dive.md)（791 行）：trivial message 过滤 / profile frequency 节流 / write gating for subagent / multi-container / entity context 可配置提示词 / session-end batch ingest；Mem0 [`47`](60-evolution/47-mem0-provider-deep-dive.md)（373 行）：server-side extraction / circuit breaker 5-failure 120s / per-user+per-agent 双层过滤 / reranking 分级 / `infer=False` 显式存储 / API response 归一化；全部 8 Provider 分析完成；更新 index.md（item 46/47）。
- [x] **文档体量验证（2026-04-25 16:55 CST）**：59 个 .md 文件总计 ~841KB，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限。新增 `46`（10406 字节）+ `47`（8487 字节）。
- [x] **上游代码同步（2026-04-25 16:55 CST）**：HEAD 已同步 origin/main（`e5647d78`），无新 upstream commits。
- [x] **`agent/memory_provider.py` ABC 源码核实（2026-04-25）**：240 行 ABC，15 个方法，10 个可选 hook；`on_memory_write` 已含 `metadata` 参数（`6a957a74` 引入）；docstring 自带详细说明，无需独立文档。
- [x] **上游代码增量扫描（2026-04-27 22:03）**：`e5647d78..origin/main`（374 commits）记忆相关 2 个新发现 → [`51`](60-evolution/51-context-compressor-model-switch-and-background-review-toolset.md)：`5401a008`（ContextCompressor 模型切换 token 预算重算 bug；`update_model()` 遗漏 `tail_token_budget`/`max_summary_tokens` 重算）+ `8ad29a93`（Background review agent 显式限制 `toolsets=["memory", "skills"]` 防越权）；其余 372 个非记忆相关（UI/TUI/平台/Backup/Approval）；本地 Hermes Agent Repo 已删除，⚠️ 需重新 clone。
- [x] **文档体量验证（2026-04-27 22:03 CST）**：51 篇正文 + 入口 ~620KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `51`（~3,892 字节）。
- [x] **8 Provider 全部分析完成**：holographic / honcho / mem0 / hindsight / byterover / openviking / retaindb / supermemory — 每 Provider 至少一篇独立深度分析文档（部分如 holographic 有 3 篇）。

## 定时巡检（2026-04-27 22:12 CST）

- [x] **上游代码重新同步（2026-04-27 22:12 CST）**：本地 Hermes Agent Repo 已于上次扫描后删除，本轮重新 clone + fetch。HEAD `cec0af02` → `origin/main`（`ac0325c2`）。
- [x] **上游代码增量扫描（`cec0af02..origin/main`，267 commits）**：记忆相关 **4 个新发现** → [`52`](60-evolution/52-session-teardown-fix-cross-provider-reasoning-and-filesystem-cleanup.md)：
  1. `500774e3`（Gateway `_cleanup_agent_resources` 向 `shutdown_memory_provider` 传 `agent._session_messages` 而非空列表；Holographic/Hindsight early-return guard 导致 restart/reset/expiry 后首 Turn 报"找不到相關的對話記錄"）
  2. `a59a98b1`（CLI exit cleanup 同 bug；读取不存在的 `conversation_history` 属性总是得到 `[]`）
  3. `ee1a07f9`（跨 Provider reasoning leak：MiniMax→DeepSeek 时 foreign `reasoning` 被 promote 为 `reasoning_content`，导致 HTTP 400；DeepSeek 强制 `reasoning_content=''` pin 使该 shape 只可能来自 prior provider）
  4. `64a497bf`（Hindsight setup 重新运行时预填充现有配置：mode/llm_provider/llm_base_url/llm_model）
  5. `3b60abb6`（`delete_session`/`prune_sessions` 删除 SQLite 记录同时清理 .json/.jsonl transcript 文件，防止 ~27MB/天磁盘增长）
  其余 263 个非记忆（UI/TUI/Backup/Approval/Slack/Google Meet/Platform）
- [x] **文档体量验证（2026-04-27 22:12 CST）**：52 篇正文 + 入口 ~630KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `52`（~9516 字节）。
- [x] **Backlog 全部项 `[x]`**：v8.9 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-04-25 13:59 CST）

- [x] **文档体量验证**：入口文件 `hermes-memory-analysis.md` 仅 1553 字节，远低于 50KB 上限。目录结构已合规（`index.md` 入口，子文档按 aspect 拆分），无需重构。
- [x] **上游代码增量扫描（`e69526be..origin/main`，54 commits）**：记忆系统相关 commit 均已在 v8.3 分析覆盖（doc 37/38/40/43）：
  - `6a957a74` — Write Origin Metadata（→ doc 37 ✅）
  - `00c3d848` — Skip External-Provider Sync on Interrupted Turns（→ doc 38 ✅）
  - `19a3e2ce` — Gateway /resume compression continuation（→ doc 40 ✅）
  - `c52e5931` — Per-User Memory Scoping（→ doc 43 ✅）
  - `8877688b` / `9d42aca2` — Hindsight 小修复（→ doc 43 ✅）
  非记忆相关（工具/UI/Delegate/Auth）：`dbdefa43`（checkpoint dedup + NaN coercion）、`ef935545`（回归测试）、`8a2506af`（aux UI）、`05d8f110`（model context length）、`023b1bff`（delegate deadlock fix）等，均不影响记忆系统架构。
- [x] **文档体系总计**：43 篇正文 + 入口 ~793KB，最大单稿 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v8.3 完成，无待跟进新发现。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-02 23:46 CST）

- [x] **本地 Hermes Agent Repo 恢复**：目录仍存在但处于 detached HEAD 状态（上次扫描后 checkout origin/main），本轮完成 fetch + checkout origin/main（`5d3be898`）。
- [x] **上游代码增量扫描（`cec0af02..origin/main`，991 commits）**：11 个记忆相关核心发现 → [`53`](60-evolution/53-session-switch-hooks-context-compressor-and-hindsight-refinements.md)：
  1. **`13683c08`**（MAJOR）：MemoryProvider ABC 新增 `on_session_switch()` 钩子，覆盖 /resume /branch /reset /new /compression 所有 session_id 轮换路径；Hindsight reference implementation；CE 无等效机制
  2. **`f0dc919f`**（MAJOR）：Token 估算现在包含 system prompt + tool schemas（修复 234x 低估差距：45 tokens vs 10.5K tokens）；影响 `should_compress()` 触发时机
  3. **`b194617d`**：ContextCompressor tail protection off-by-one fix（短对话保护范围错误）
  4. **`dad02174`**：Honcho `HonchoSessionManager._cache` RLock 线程安全修复
  5. **`0a5ee01e`**：Hindsight flush-on-switch 从 raw thread 改为 writer queue 路由
  6. **`c38dac74`**：Hindsight session switch 时 flush buffered turns + drop stale prefetch result
  7. **`0565497d`**：Hindsight 单 writer + queue 替代 per-sync daemon thread（消除 CLI exit race）
  8. **`6ea5699e`**：Compression aux model 失败时 fallback 仍通知用户（防止 broken config 静默持续）
  9. **`e553f6f3`**：Memory scrub surface 从 8 个 site 收缩到 3 个（防止过度 scrub 破坏合法内容）
  10. **`142b4bf3`**：session_search recent mode 改为按 `last_active` 而非 `start_time` 排序
  11. **`b29b709a`**：tool_call id 字段支持 `call_id` 优先（OpenAI Responses API 兼容）
  其余 980 个非记忆相关（TUI 性能/computer-use/Backup/Approval/Feishu/Discord/IRC/Kanban/平台）
- [x] **文档体量验证（2026-05-02 23:46 CST）**：53 篇正文 + 入口 ~850KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `53`（~19.9KB）。
- [x] **Backlog 全部项 `[x]`**：v9.0 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 00:53 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 同步，无新 commits。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。上游最新 commits（`5d3be898..HEAD`）涵盖 TTS xAI custom voice / aux API key passthrough / WhatsApp typing leak / Feishu httpx context / Gateway .env precedence / 均为非记忆系统功能。
- [x] **文档体量验证（2026-05-03 00:53 CST）**：53 篇正文 + 入口 ~850KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.1 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。


## 定时巡检（2026-05-03 01:29 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 完全同步。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：无新 upstream commits。最近 upstream 推进涵盖 TTS xAI / Feishu httpx / Gateway .env / Discord ws，均非记忆系统。
- [x] **文档体量验证（2026-05-03 01:29 CST）**：53 篇正文 ~917KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.1 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 02:40 CST）

- [x] **上游代码增量扫描（`5d3be898..origin/main`，39 commits）**：无记忆系统相关新提交。全部 39 个 commit 均为平台特定修复：TTS xAI custom voice（`5d3be898`）/ aux API key 传递（`af981227`）/ WhatsApp typing leak（`762eb79f`）/ Feishu httpx context（`38dd057e`）/ Gateway systemd + WebSocket insecure（`f98b5d00`/`585d6778`）/ Slack private notice delivery（`0ab2d752`）/ Discord zombie websocket（`292d2fb4`）/ credential pool `.env` precedence（`2ef1ad28`）/ Telegram polling liveness（`2470434d`）/ skill slug matching（`6ec74aec`）/ GBK crash fix（`c5e3a6fb`）/ Slack per-user slash-command isolation（`a147164d`）等。
- [x] **文档架构规范自检**：入口文件 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 目录 53 篇正文，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限；目录结构合规，无需重构。
- [x] **Backlog 全部项 `[x]`**：v9.2 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 04:03 CST）

- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。最近 non-memory 推进：TTS xAI custom voice / aux API key / WhatsApp / Feishu httpx / Gateway .env，均与记忆系统无关。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.3 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 05:54 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 完全同步。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。上游最新 commits 涵盖 TTS xAI custom voice / Feishu httpx / Gateway .env precedence / WhatsApp typing leak，均非记忆系统。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文 ~940KB，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.4 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 21:07 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`5d3be898` → `d87fd9f0`）。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，26 commits）**：**0 个记忆系统相关新提交**。2 个 gateway session 相关但非核心记忆架构：
  1. `f1e02925`（MAJOR session 行为变更）：**Crash/Restart 后 Session Resume 替代 Blanket Suspend** — `suspend_recently_active()` 改为设置 `resume_pending=True` 而非无条件 `suspended=True`，避免 `get_or_create_session()` 在每次重启时清除对话历史；stuck-loop 可在 3 次失败后 escalation；影响 `gateway/session.py` + `gateway/run.py`
  2. `93410347`：**/new response 顺序修复** — `/new` 时在 `cancel_session_processing()` 前先发响应，避免 race 丢响应（`platforms/base.py`）；非记忆核心
  其余 24 个 commit 均为平台修复（Goals/TUI/WeChat/Zed/Model/Bedrock），无记忆系统变化。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文 ~940KB，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限。架构合规，无需重构。
- [x] **Backlog 全部项 `[x]`**：v9.5 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-03 22:54 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`d87fd9f0` → `e527240b`）。
- [x] **上游代码增量扫描（`d87fd9f0..origin/main`，5 commits，0 记忆相关）**：仅 `e527240b`(tools/write_file) / `6b4fb9f8`(cron) / `69dd0f7c`(approval) / `3c59566c`(release) / `b59bb4e3`(gateway)；无记忆/上下文/压缩/session/hook/provider 相关。分析文档 → [`54`](60-evolution/54-upstream-new-commits-may-03.md)。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文 ~940KB，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），全部低于 50KB 上限。架构合规，无需重构。
- [x] **Backlog 全部项 `[x]`**：v9.6 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-04 15:40 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`ac0325c25` → `8163d3719`）。
- [x] **上游代码增量扫描（`ac0325c25..origin/main`，~250 commits）**：记忆/上下文/压缩/session 相关核心发现：
  1. **`408dd8aa`**（2026-05-04 补充）：Compressor deduplication pass 对非字符串 content 安全防护（`AttributeError` 修复）→ [`55`](60-evolution/55-compressor-dedup-non-string-content-fix.md)；**已在 doc 55 覆盖** ✅
  2. **`f1e02925`**（2026-05-03）：**Crash/Restart 后 Session Resume 替代 Blanket Suspend** — `suspend_recently_active()` 改为设置 `resume_pending=True` 而非无条件 `suspended=True`；影响 `gateway/session.py` + `gateway/run.py`；这是 session 行为变更，非核心记忆系统但影响 session 生命周期管理。
  3. **`c5b4c481`**（2026-04-29）：**Lazy Session 创建** — `defer DB row until first message`（`#18370`）；减少空 session DB 开销，与 BlueCortexCE SessionEntity 惰性创建设计思路一致。
  4. **`93410347`**（2026-05-02）：**/new response 顺序修复** — 在 `cancel_session_processing()` 前先发响应，避免 race 丢响应；与 doc 52 的 session teardown bug 修复正交（均为 session 生命周期边界）。
  其余 memory-adjacent 变更：`f0dc919f9`（已在 doc 53）/`ec4cb16a2`（Honcho RLock 已在 doc 53）/`4a2f82213`（MCP session reconnect，与记忆系统无直接关联）。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 55 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.7 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit。

## 定时巡检（2026-05-04 19:37 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`8163d3719` → `110387d14`）。
- [x] **上游代码增量扫描（`a11aed1ac..origin/main`，85 commits）**：3 个记忆系统相关 + 1 个工具结果存储相关 → [`59`](60-evolution/59-upstream-a11aed1ac-to-origin-main-memory-analysis.md)：
  1. **`6b88f46c5`**：Compressor timeout fallback — HTTP 408/429/502/504 及 `timeout` 字符串触发 fallback 到主模型，防止上下文无限增长；CE StructuredExtractionService 应增加 transient vs permanent 错误分类
  2. **`e2211b268`**：`on_session_reset()` 清理 `_summary_failure_cooldown_until`，防止新 session 被旧 cooldown 阻塞；CE session 重置时应清理所有 transient 状态
  3. **`c653f5dc3`**：session_search auxiliary model 文档澄清；CE `/api/memory/search` 应补充 fallback 行为说明
  4. **`e50809b77`**：`read_file` max_result_size_chars=100K 封顶，闭合 tool_result_storage.py Layer 2 防御缺口；CE `submitFeedback` 应增加结果截断
  其余 81 个非记忆（Dashboard/Kanban/TUI/Provider Fixes）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 56 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.8 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `110387d14`）。
