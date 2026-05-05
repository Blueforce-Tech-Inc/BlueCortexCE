# Hermes Agent 记忆系统 — 文档索引

本目录按**主题方面（aspect）**组织多份 Markdown（单文件 ≤50KB；文件名英文，正文可为中文）。**入口**：原根文件 [`../hermes-memory-analysis.md`](../hermes-memory-analysis.md) 仅作跳转，勿在其中堆长文。

> **体量（2026-05-05）**：根入口文件不足 50KB；本目录内最大单稿 ~43KB（`05`）。`06`/`07`/`04` 均已拆分（见下文）；`09`/`08`/`05` 已预防性拆分（各产出一个 `*a` 续写文件）。所有正文 ≤43KB。续写逼近上限前请先读 [`AGENT.md`](./AGENT.md) **「体量预警」** 并按表拆分。


## 建议阅读顺序

> **完整列表（83 项，~44KB）已移至**：[`index-reading-order.md`](index-reading-order.md)（2026-05-05 预防性拆分，规避 50KB 上限）

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

## 本仓库其他记忆分析草稿（交叉索引）

| 目录 | 侧重点 |
|------|--------|
| **总导航（推荐）** | [`../memory-research-hub.md`](../memory-research-hub.md) — Evolver / Hermes / 论文线 **一页选入口** |
| [`../evolver-memory/`](../evolver-memory/index.md) | Evolver ↔ CE：**09** · **10** · **12** · **14** · **16** · **17** 会话 · **15** |

Hermes 与 Evolver **可同时读**：前者给「Agent 内记忆管线」参照，后者给「因果/签名/叙事」参照；落地时以 CE 架构与 `10` 为准。

## 维护（给作者与自动化）

约定见 [`AGENT.md`](AGENT.md)。草稿可先放 [`staging.md`](staging.md)；不便归类摘录见 [`misc.md`](misc.md)。

**合并前自检**：各正文 `.md` 字节数 ≤51200；`index.md` 外链仍有效。
