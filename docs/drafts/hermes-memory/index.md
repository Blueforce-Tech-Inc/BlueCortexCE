# Hermes Agent 记忆系统 — 文档索引

本目录按**主题方面（aspect）**组织多份 Markdown（单文件 ≤50KB；文件名英文，正文可为中文）。**入口**：原根文件 [`../hermes-memory-analysis.md`](../hermes-memory-analysis.md) 仅作跳转，勿在其中堆长文。

> **体量（2026-04-19）**：根入口文件不足 50KB；本目录内**最大单稿约 49KB**（[`60-evolution/06-memory-provider-hooks-inventory.md`](60-evolution/06-memory-provider-hooks-inventory.md)）。续写逼近上限前请先读 [`AGENT.md`](./AGENT.md) **「体量预警」** 并按表拆分。

## 建议阅读顺序

1. **总览与立场** → [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md)（含历史级「目录」清单；跨文件锚点无效，请用本索引跳转）
2. **借鉴总表** → [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)
3. **可执行优先级（综述）** → [`20-recommendations/03-borrowing-synthesis-executable-priorities.md`](20-recommendations/03-borrowing-synthesis-executable-priorities.md)
4. **CE 注入面与 `/api/context` 对照（本仓库路径）** → [`20-recommendations/04-ce-injection-and-context-api-surface.md`](20-recommendations/04-ce-injection-and-context-api-surface.md)（§2.1 会话首跳 → [`../evolver-memory/15-runtime-integration-surfaces.md`](../evolver-memory/15-runtime-integration-surfaces.md) §5）
5. **上下文安全缺口盘点（对照 Hermes 扫描）** → [`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)
6. **上下文 / 注入 / Prefetch（Hermes 机制长文）** → [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)
7. **Honcho / 多模态等深度** → `50-honcho-holographic-deep/` 下各篇
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

## 按 aspect 浏览

| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、章节索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | 借鉴总表、优先级综述、注入面（`04`）、**安全缺口盘点**（`05`） |
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
