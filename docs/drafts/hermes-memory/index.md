# Hermes Agent 记忆系统 — 文档索引（渐进式披露）

本目录将原 `hermes-memory-analysis.md` **无损拆分**为多份 Markdown（每份 ≤50KB），按**主题方面（aspect）**分子目录；**文件名均为英文**，便于跨工具与版本管理。正文可为中文。

## 阅读顺序（建议）

1. **总览与立场** → [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md)（含原文档级「目录」清单；章节正文已分散至下列文件，锚点链接仅在同文件内有效）
2. **借鉴总表** → [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)
3. **上下文 / 注入 / Prefetch** → [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)
4. **Honcho / 多模态等深度** → `50-honcho-holographic-deep/` 下各篇
5. **演进与 Provider / 工具细节** → `60-evolution/` 下各篇

## 按方面（aspect）浏览

| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、原「目录」索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | BlueCortexCE 借鉴建议汇总（及紧密相关小节） |
| 40-context-compression | [`40-context-compression/`](40-context-compression/) | Memory context 注入、Prefetch、Session 截断等 |
| 50-honcho-holographic-deep | [`50-honcho-holographic-deep/`](50-honcho-holographic-deep/) | Honcho 四工具、多模态澄清等 |
| 60-evolution | [`60-evolution/`](60-evolution/) | Hooks、Supermemory、内置 Memory Tool、HRR 等后续版本增量 |

## 维护说明

- **权威正文**：`docs/drafts/hermes-memory/**/*.md` 各分片（勿在根文件 `hermes-memory-analysis.md` 堆长文）。
- **给 AI 文档助理**：见 [`AGENT.md`](AGENT.md)。

## 相关占位

- [`misc.md`](misc.md) — 暂不便归入某一方面的摘录
- [`staging.md`](staging.md) — 工作进度与待合并草稿

## 自检（维护时建议）

| # | 检查项 |
|---|--------|
| 1 | 各分片 `wc -c` ≤ 51200（50KB 上限） |
| 2 | `index.md` 中显式链接的路径仍存在 |
| 3 | 新增分片文件名保持英文、归入合适 aspect 子目录 |
