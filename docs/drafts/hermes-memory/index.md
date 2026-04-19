# Hermes Agent 记忆系统 — 文档索引

本目录按**主题方面（aspect）**组织多份 Markdown（单文件 ≤50KB；文件名英文，正文可为中文）。**入口**：原根文件 [`../hermes-memory-analysis.md`](../hermes-memory-analysis.md) 仅作跳转，勿在其中堆长文。

## 建议阅读顺序

1. **总览与立场** → [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md)（含历史级「目录」清单；跨文件锚点无效，请用本索引跳转）
2. **借鉴总表** → [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)
3. **可执行优先级（综述）** → [`20-recommendations/03-borrowing-synthesis-executable-priorities.md`](20-recommendations/03-borrowing-synthesis-executable-priorities.md)
4. **CE 注入面与 `/api/context` 对照（本仓库路径）** → [`20-recommendations/04-ce-injection-and-context-api-surface.md`](20-recommendations/04-ce-injection-and-context-api-surface.md)
5. **上下文安全缺口盘点（对照 Hermes 扫描）** → [`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)
6. **上下文 / 注入 / Prefetch（Hermes 机制长文）** → [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)
7. **Honcho / 多模态等深度** → `50-honcho-holographic-deep/` 下各篇
8. **演进与 Provider / 工具细节** → `60-evolution/` 下各篇（含 [`11-field-review-and-bypass-roadmap.md`](60-evolution/11-field-review-and-bypass-roadmap.md)）

## 按 aspect 浏览

| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、章节索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | 借鉴总表、优先级综述、注入面（`04`）、**安全缺口盘点**（`05`） |
| （根目录） | [`11-research-backlog.md`](11-research-backlog.md) | Hermes→CE **可勾选接力队列** |
| 40-context-compression | [`40-context-compression/`](40-context-compression/) | Memory context 注入、Prefetch、Session 截断等 |
| 50-honcho-holographic-deep | [`50-honcho-holographic-deep/`](50-honcho-holographic-deep/) | Honcho 四工具、多模态澄清等 |
| 60-evolution | [`60-evolution/`](60-evolution/) | Hooks、Supermemory、内置 Memory Tool、HRR；现场复核与路线图 |

## 本仓库其他记忆分析草稿（交叉索引）

| 目录 | 侧重点 |
|------|--------|
| **总导航（推荐）** | [`../memory-research-hub.md`](../memory-research-hub.md) — Evolver / Hermes / 论文线 **一页选入口** |
| [`../evolver-memory/`](../evolver-memory/index.md) | Evolver ↔ CE：**09** · **10** · **12** · **14** 读出 · **16** 写入 · **15** Worker/Java |

Hermes 与 Evolver **可同时读**：前者给「Agent 内记忆管线」参照，后者给「因果/签名/叙事」参照；落地时以 CE 架构与 `10` 为准。

## 维护（给作者与自动化）

约定见 [`AGENT.md`](AGENT.md)。草稿可先放 [`staging.md`](staging.md)；不便归类摘录见 [`misc.md`](misc.md)。

**合并前自检**：各正文 `.md` 字节数 ≤51200；`index.md` 外链仍有效。
