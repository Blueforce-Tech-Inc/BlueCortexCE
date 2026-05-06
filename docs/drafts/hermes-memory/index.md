# Hermes Agent 记忆系统 — 文档索引

本目录按**主题方面（aspect）**组织多份 Markdown（单文件 ≤50KB；文件名英文，正文可为中文）。**入口**：原根文件 [`../hermes-memory-analysis.md`](../hermes-memory-analysis.md) 仅作跳转，勿在其中堆长文。

> **体量（2026-05-06）**：根入口文件不足 50KB；本目录内最大单稿 ~43KB（`05`）。`06`/`07`/`04` 均已拆分（见下文）；`09`/`08`/`05` 已预防性拆分（各产出一个 `*a` 续写文件）。所有正文 ≤43KB。续写逼近上限前请先读 [`AGENT.md`](./AGENT.md) **「体量预警」** 并按表拆分。


## 建议阅读顺序

> **完整列表（84 项，~45KB）已移至**：[`index-reading-order.md`](index-reading-order.md)（2026-05-05 预防性拆分，规避 50KB 上限；2026-05-06 新增条目 84）

## 新增（2026-05-07）

| 编号 | 路径 | 说明 |
|------|------|------|
| **95** | [`60-evolution/95-atomic-file-write-and-char-limit-design.md`](60-evolution/95-atomic-file-write-and-char-limit-design.md) | **原子文件写入 + Char-Limit 设计模式**：temp file + `os.fsync` + `atomic_replace` 三步走；独立 `.lock` 文件设计；字符预算模型（2200/1375 chars，model-independent）；`ENTRY_DELIMITER = "\n§\n"` Section Sign 分隔符；CE 落地：P1 原子写入（安全关键）+ P1 字符限制（MEMORY.md）+ P2 ingest 入口扫描 |
| **97** | [`60-evolution/97-curator-skill-lifecycle-and-background-maintenance-orchestrator.md`](60-evolution/97-curator-skill-lifecycle-and-background-maintenance-orchestrator.md) | **Curator 技能生命周期管理与后台维护编排器**：1674 行；空闲触发调度（`should_run_now()` + idle hours）+ 纯函数状态机（active → stale → archived）+ LLM 驱动的伞形化评审 + 原子状态持久化 + 分类启发式（consolidation vs pruning）+ 双输出报告 + Dry-run；CE 借鉴：后台 Observation 生命周期管理 |
| **98** | [`60-evolution/98-tool-call-loop-guardrails-and-file-safety.md`](60-evolution/98-tool-call-loop-guardrails-and-file-safety.md) | **Tool Call Loop Guardrails + File Safety**：455+111 行；`ToolCallGuardrailController` 三模式检测（exact failure / same-tool failure / idempotent no-progress）+ SHA256 签名 + opt-in hard stop；`FileSafety` 双防线（denylist + safe root 隔离）；CE 落地：P1 MCPTools GuardrailController + P1 文件路径 denylist；CE 安全纵深 L1-L4 缺口对照 |

## 按 aspect 浏览


| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、章节索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | 借鉴总表、优先级综述、注入面（`04`）、安全缺口盘点（`05`）、**Context 文件扫描深度解析**（`06`） |
| （根目录） | [`11-research-backlog.md`](11-research-backlog.md) | Hermes→CE **可勾选接力队列** |
| 40-context-compression | [`40-context-compression/`](40-context-compression/) | Memory context 注入、Prefetch、Session 截断等 |
| 50-honcho-holographic-deep | [`50-honcho-holographic-deep/`](50-honcho-holographic-deep/) | Honcho 四工具、多模态澄清等 |
| 60-evolution | [`60-evolution/`](60-evolution/) | Hooks、Supermemory、内置 Memory Tool、HRR；上游快照 [`12`](60-evolution/12-upstream-hermes-agent-memory-snapshot.md)、[`13` `run_agent` 接线](60-evolution/13-run-agent-memory-wiring-snapshot.md)；现场复核与路线图 |
| **速查卡** | [`79-ce-developer-quick-reference.md`](79-ce-developer-quick-reference.md) | **CE 开发者 Top-10 落地借鉴**（注入防护/Memory Fence/会话搜索/Tool Result 持久化/可插拔压缩/Auxiliary LLM/Per-User/Redaction/Frozen Snapshot/Eval Harness） |
| **60-evolution 新增** | [`60-evolution/85-hermes-context-summary-end-marker-and-iterative-continuity.md`](60-evolution/85-hermes-context-summary-end-marker-and-iterative-continuity.md) | **Context Summary End Marker + 迭代压缩连续性**深度解析（`2eef395e1` / `4a3e3e20e`）；CE 落地：围栏信号（⭐⭐⭐ 立即可落地） + 迭代提取身份保护（Phase 3 参考） |
| **60-evolution 新增** | [`60-evolution/91-streaming-scrubber-and-memory-security-scanning.md`](60-evolution/91-streaming-scrubber-and-memory-security-scanning.md) | **双 Scrubber 管道**（`StreamingContextScrubber` + `StreamingThinkScrubber`）**+ 内存写入安全扫描**（`_scan_memory_content`）深度解析；CE 落地：proxy 层 SSE 过滤（P1）+ ingest 入口安全扫描（P1）+ 冻结快照（P2） |
| **60-evolution 新增** | [`60-evolution/92-upstream-aa88dcc57-memory-analysis.md`](60-evolution/92-upstream-aa88dcc57-memory-analysis.md) | **`aa88dcc57` salvage batch**（2026-05-06）：⭐⭐⭐ **P0** 压缩后 cached agent 未清除 + ⭐⭐⭐ **P1** Memory authority 升级（`informational → authoritative reference data`）+ ⭐⭐ **P1** Summary prefix 权威性声明 + **P2** 正则向后兼容新旧措辞 |
| **60-evolution 新增** | [`60-evolution/96-hermes-memory-architecture-synthesis-and-ce-roadmap.md`](60-evolution/96-hermes-memory-architecture-synthesis-and-ce-roadmap.md) | **架构综合与 CE 落地路线图**（2026-05-07）：三记忆层×双系统全貌 / `aa88dcc57` P0 发现（压缩后 cached agent 未清除 + Memory authority 升级）/ 安全三层防护 / 原子写入 / 优先级矩阵 |

## 本仓库其他记忆分析草稿（交叉索引）

| 目录 | 侧重点 |
|------|--------|
| **总导航（推荐）** | [`../memory-research-hub.md`](../memory-research-hub.md) — Evolver / Hermes / 论文线 **一页选入口** |
| [`../evolver-memory/`](../evolver-memory/index.md) | Evolver ↔ CE：**09** · **10** · **12** · **14** · **16** · **17** 会话 · **15** |

Hermes 与 Evolver **可同时读**：前者给「Agent 内记忆管线」参照，后者给「因果/签名/叙事」参照；落地时以 CE 架构与 `10` 为准。

## 维护（给作者与自动化）

约定见 [`AGENT.md`](AGENT.md)。草稿可先放 [`staging.md`](staging.md)；不便归类摘录见 [`misc.md`](misc.md)。

**合并前自检**：各正文 `.md` 字节数 ≤51200；`index.md` 外链仍有效。
