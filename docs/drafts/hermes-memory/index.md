# Hermes Agent 记忆系统 — 文档索引

本目录按**主题方面（aspect）**组织多份 Markdown（单文件 ≤50KB；文件名英文，正文可为中文）。**入口**：原根文件 [`../hermes-memory-analysis.md`](../hermes-memory-analysis.md) 仅作跳转，勿在其中堆长文。

> **体量（2026-04-24）**：根入口文件不足 50KB；本目录内**最大单稿约 47KB**（[`09-supermemory-capture-lifecycle.md`](60-evolution/09-supermemory-capture-lifecycle.md)）。`06`/`07`/`04` 均已拆分（见下文）；所有正文 ≤34KB。续写逼近上限前请先读 [`AGENT.md`](./AGENT.md) **「体量预警」** 并按表拆分。

## 建议阅读顺序

1. **总览与立场** → [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md)（含历史级「目录」清单；跨文件锚点无效，请用本索引跳转）
2. **借鉴总表** → [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)（§11–§15 深度专题 → [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md)；2026-04-24 preemptive split）
3. **可执行优先级（综述）** → [`20-recommendations/03-borrowing-synthesis-executable-priorities.md`](20-recommendations/03-borrowing-synthesis-executable-priorities.md)（2026-04-24 preemptive split：§11–§15 深度专题移至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md)）
4. **CE 注入面与 `/api/context` 对照（本仓库路径）** → [`20-recommendations/04-ce-injection-and-context-api-surface.md`](20-recommendations/04-ce-injection-and-context-api-surface.md)（§2.1 会话首跳 → [`../evolver-memory/15-runtime-integration-surfaces.md`](../evolver-memory/15-runtime-integration-surfaces.md) §5）
5. **上下文安全缺口盘点（对照 Hermes 扫描）** → [`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)
5a. **Context 文件扫描机制深度解析（`_scan_context_content` vs `_scan_memory_content`）** → [`20-recommendations/06-context-file-scanning-deep-dive.md`](20-recommendations/06-context-file-scanning-deep-dive.md)（2026-04-24 新增；`_scan_context_content`/`_scan_memory_content` 源码对照 + CE 缺口 + 实施建议）
6. **上下文 / 注入 / Prefetch（Hermes 机制长文）** → [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)
7. **Honcho / 多模态等深度** → `50-honcho-holographic-deep/` 下各篇（[`04`](50-honcho-holographic-deep/04-honcho-four-tools-routing.md) §20–§22 · [`06`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md) §24–§29；2026-04-24 拆分）
8. **演进与 Provider / 工具细节** → `60-evolution/` 下各篇（含 [`11-field-review-and-bypass-roadmap.md`](60-evolution/11-field-review-and-bypass-roadmap.md)）
9. **上游源码现场快照（MemoryManager / Provider / MemoryStore）** → [`60-evolution/12-upstream-hermes-agent-memory-snapshot.md`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md)（读代码接力起点；与 `06`/`08` 交叉）
10. **`run_agent` 主循环接线**（prefetch / sync / 工具分发 / 双线 `_memory_store`∥`_memory_manager`） → [`60-evolution/13-run-agent-memory-wiring-snapshot.md`](60-evolution/13-run-agent-memory-wiring-snapshot.md)
11. **Multi-Provider 插件发现架构** → [`60-evolution/14-multi-provider-plugin-discovery.md`](60-evolution/14-multi-provider-plugin-discovery.md)（discover/load/Collector 模式 + Provider 清单；byterover/hindsight/openviking 三新增 Provider 详见 [`18`](60-evolution/18-three-new-memory-providers.md)）
12. **Session DB Flush 与 Duplicate-Write Bug Fix** → [`60-evolution/15-session-db-flush-and-duplicate-fix.md`](60-evolution/15-session-db-flush-and-duplicate-fix.md)（`_last_flushed_db_idx` 游标、幂等 flush、消息截断）
13. **Extended Hooks**（`on_pre_compress` / `on_memory_write` / `on_delegation` / `queue_prefetch`） → [`60-evolution/16-extended-memory-provider-hooks.md`](60-evolution/16-extended-memory-provider-hooks.md)（RetainDB 三线程预取模型）
14. **Smart Compression + Exhaustion Loop Fix**（2026-04-14 上游） → [`60-evolution/17-smart-compression-and-exhaustion-fix.md`](60-evolution/17-smart-compression-and-exhaustion-fix.md)（Smart tool collapse / MD5 dedup / Anti-thrashing / `failed: True` + session auto-reset）
15. **三新增 Provider 分析**（byterover/hindsight/openviking） → [`60-evolution/18-three-new-memory-providers.md`](60-evolution/18-three-new-memory-providers.md)（ByteRover 层级 Tree / Hindsight 知识图谱+Reflect+本地嵌入 / OpenViking URI+tiered context+atexit）
16. **Gateway 后台 Session 过期 Watcher**（proactive flush + `memory_flushed` 持久化 + 防覆盖注入 + cron 跳过） → [`60-evolution/19-gateway-session-expiry-watcher.md`](60-evolution/19-gateway-session-expiry-watcher.md)
17. **Tool Result Persistence 3-Layer Defense**（per-tool truncation / per-result sandbox persist / per-turn 200K budget） → [`60-evolution/20-tool-result-persistence.md`](60-evolution/20-tool-result-persistence.md)
18. **Session Search Tool — FTS5 + LLM Recall**（dual mode / smart truncation / parallel summarize / auxiliary model） → [`60-evolution/21-session-search-tool.md`](60-evolution/21-session-search-tool.md)
19. **Hindsight 知识图谱深度解析**（TEMPR 四路检索 / Observation 合并 / 实体消解 / 双时间模型 / Reflect Agentic Loop / Disposition System） → [`60-evolution/22-hindsight-knowledge-graph-deep-dive.md`](60-evolution/22-hindsight-knowledge-graph-deep-dive.md)（2026-04-23 新增）
20. **Auxiliary Client Provider Resolution Chain**（Auto-Detect / 7-Provider Fallback / Payment Error Recovery / Codex & Anthropic Adapters / Per-Task Config） → [`60-evolution/23-auxiliary-client-resolution-chain.md`](60-evolution/23-auxiliary-client-resolution-chain.md)（2026-04-23 新增）
21. **ContextCompressor 完整算法解析**（四阶段压缩 / Tool Pruning 3-Pass / Token-Budget Tail / Structured Template / Iterative Update / Anti-Thrashing / Tool Pair 整治） → [`60-evolution/24-context-compressor-full-algorithm.md`](60-evolution/24-context-compressor-full-algorithm.md)（2026-04-23 新增；整合 06/07/09/17 散落分析）
22. **Hindsight 本地嵌入 Daemon + PostgreSQL Schema**（hindsight-all 包架构 / HindsightEmbedded vs HindsightServer / Profile 机制 / pgvector/pgvectorscale/vchord 多扩展 / 连接池 / Schema 隔离 / LLM Provider 支持 / Docker 部署对比） → [`60-evolution/25-hindsight-local-embedded-daemon-and-postgresql-schema.md`](60-evolution/25-hindsight-local-embedded-daemon-and-postgresql-schema.md)（2026-04-23 新增；源自 Hindsight 官方安装文档 + API 参考 + Hermes 插件源码）
23. **Supermemory Multi-Container & Search Mode**（多容器架构 / 搜索模式对比） → [`60-evolution/26-supermemory-multi-container-and-search-mode.md`](60-evolution/26-supermemory-multi-container-and-search-mode.md)
24. **ContextEngine 可插拔压缩架构**（ABC / 插件发现 / 生命周期 / Token 追踪 / 与 MemoryProvider 对比） → [`60-evolution/27-context-engine-pluggable-architecture.md`](60-evolution/27-context-engine-pluggable-architecture.md)（2026-04-24 新增；源自 `agent/context_engine.py`）
25. **Prompt Caching 与记忆系统交互分析**（system_and_3 策略 / 压缩-缓存失效恢复 / CE 架构差异 / 可执行借鉴） → [`28`](60-evolution/28-prompt-caching-and-memory-interaction.md)（2026-04-24 新增；源自 `agent/prompt_caching.py` + `run_agent.py` 源码）
26. **MemoryProvider Hooks 高级专题（续）**（BlueCortexCE vs Hermes Summary Template 逐字段对比 / SessionSearch LLM 截断三层 fallback / RetainDB Supermemory 补充） → [`60-evolution/29-memory-provider-hooks-advanced-topics.md`](60-evolution/29-memory-provider-hooks-advanced-topics.md)（2026-04-24 拆分自 `06` §43–§44 + 合并自 `07` §44–§45）
27. **矛盾检测工程方案 + Session Tools 分析**（Entity Extraction / SessionSearch 双模式 / memory_tool 语义 / Tool Result Pre-pass / SessionDB v6 Reasoning Chain / Honcho write_frequency） → [`60-evolution/30-contradiction-detection-and-session-tools.md`](60-evolution/30-contradiction-detection-and-session-tools.md)（2026-04-24 拆分自 `07` §45–§52）
28. **Context References — @-Prefix 文件/URL/Git 展开**（6 类引用 / Token 50%+25% 预算 / 路径安全双层 / `allowed_root` 隔离 / async URL fetcher / `cli.py`/`gateway/run.py` 双集成点 / CE 差距 + 可执行借鉴） → [`31-context-references-file-expansion.md`](60-evolution/31-context-references-file-expansion.md)（2026-04-24 新增；`agent/context_references.py` 520 行完整解析）
29. **上游新提交分析（2026-04-25）**：Session Finalize Hook（`260ae621` / expiry flush 后触发）/ ContextEngine ABC 强化（`has_content_to_compress` / `focus_topic` / `a9a4416c`）/ Hindsight bank_id_template（`edff2fbe`）/ Hindsight Bug Fixes 批量（`f9c6c5ab` document_id per-process 等） → [`32-upstream-new-commits-session-hooks-and-context-engine.md`](60-evolution/32-upstream-new-commits-session-hooks-and-context-engine.md)（2026-04-25 新增）
30. **Context Compressor 安全强化 + Bug Fix #10896（2026-04-25）**：Bug Fix #10896（压缩丢失活跃 user 消息 / `_ensure_last_user_message_in_tail`）/ JSON-aware tool call args 截断（防 400 死循环）/ `redact.py` 全链路 secrets 过滤（JWT/URL params/form bodies/Discord mentions）/ multimodal content 安全操作 / Structured Summary "Active Task" 首位字段 + 语言感知 / `sanitize_context()` 防递归注入强化 / `emit_collect()` decision-style hooks / `hermes_cli/hooks.py` 新增 CLI 管理工具（list/test/revoke/doctor） → [`33-context-compressor-security-hardening-and-bugfixes.md`](60-evolution/33-context-compressor-security-hardening-and-bugfixes.md)（2026-04-25 新增）
31. **上游新提交分析（2026-04-25 ~ 04-26）**：`e69526be` → `origin/main`（~1590 commits）中 13 个内存相关核心提交：ContextEngine ABC `has_content_to_compress()` 修复 plugin 兼容性（`a9a4416c`）/ 多模态 content 安全操作（`1e8254e5`）/ 语言感知摘要（`13294c2d`）/ Summary fallback NameError 修复（`c0385873`）/ Session 生命周期大重构 — 删除 `on_session_reset` hook，简化为 `commit_memory_session` + `rotate_memory_session` 双接口（`7cb06e3b` + `8275fa59` + `7856d304`）/ Shell Hooks — 用户 shell 脚本作为 hook 回调（`3988c3c2`）/ Redact config bridge fix（`0e235947`）/ `emit_collect()` decision protocol（`51ca5759`）/ Honcho 5-tool + 双层 dialectic context injection（`cc6e8941`） → [`34-upstream-new-commits-session-lifecycle-and-context-engine.md`](60-evolution/34-upstream-new-commits-session-lifecycle-and-context-engine.md)（2026-04-25 新增）
32. **Hindsight 批量修复 + Compression Eval Harness 设计**（`cc6e8941` → `6f1eed39`，~1340 commits）：Hindsight 5 连击 — 共享 event loop 关停修复（`93a74f74` #11923）/ 可配置 `HINDSIGHT_TIMEOUT`（`403c82b6`）/ `_run_sync` 中配置 timeout 实际生效（`f1ba2f0c`）/ document_id per-process 修复 session resume 数据丢失（`f9c6c5ab` #6602）/ `bank_id_template` 运行时动态派生（`edff2fbe`）/ Codex OAuth context length 缓存 400k 硬上限主动失效（`346601ca` #15078）/ Compression Eval Harness 设计 PR（`9f5c13f8`，未合并，Phase 3 Structured Extraction 质量保障直接可迁移）/ `pre_gateway_dispatch` gateway hook（`1ef1e4c6`） → [`35-hindsight-batch-fixes-and-compression-eval-harness.md`](60-evolution/35-hindsight-batch-fixes-and-compression-eval-harness.md)（2026-04-25 新增）

## 按 aspect 浏览

| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、章节索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | 借鉴总表、优先级综述、注入面（`04`）、安全缺口盘点（`05`）、**Context 文件扫描深度解析**（`06`） |
| （根目录） | [`11-research-backlog.md`](11-research-backlog.md) | Hermes→CE **可勾选接力队列** |
| 40-context-compression | [`40-context-compression/`](40-context-compression/) | Memory context 注入、Prefetch、Session 截断等 |
| 50-honcho-holographic-deep | [`50-honcho-holographic-deep/`](50-honcho-holographic-deep/) | Honcho 四工具、多模态澄清等 |
| 60-evolution | [`60-evolution/`](60-evolution/) | Hooks、Supermemory、内置 Memory Tool、HRR；上游快照 [`12`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md)、[`13` `run_agent` 接线](60-evolution/13-run-agent-memory-wiring-snapshot.md)；现场复核与路线图 |

## 本仓库其他记忆分析草稿（交叉索引）

| 目录 | 侧重点 |
|------|--------|
| **总导航（推荐）** | [`../memory-research-hub.md`](../memory-research-hub.md) — Evolver / Hermes / 论文线 **一页选入口** |
| [`../evolver-memory/`](../evolver-memory/index.md) | Evolver ↔ CE：**09** · **10** · **12** · **14** · **16** · **17** 会话 · **15** |

Hermes 与 Evolver **可同时读**：前者给「Agent 内记忆管线」参照，后者给「因果/签名/叙事」参照；落地时以 CE 架构与 `10` 为准。

## 维护（给作者与自动化）

约定见 [`AGENT.md`](AGENT.md)。草稿可先放 [`staging.md`](staging.md)；不便归类摘录见 [`misc.md`](misc.md)。

**合并前自检**：各正文 `.md` 字节数 ≤51200；`index.md` 外链仍有效。
