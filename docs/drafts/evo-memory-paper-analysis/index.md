# Evo-Memory 论文深入解读与 Claude-Mem 改进方向

> **论文**: [Evo-Memory: Benchmarking LLM Agent Test-time Learning with Self-Evolving Memory](https://arxiv.org/abs/2511.20857)  
> **作者**: Google DeepMind + UIUC  
> **发布日期**: 2025年11月  
> **文档创建**: 2026-03-12  
> **HTML版本**: https://arxiv.org/html/2511.20857v1  

本目录将原单体长文拆成若干主题文件，形成**可渐进发现**的阅读路径：从动机与框架到 ReMem、实验结论、与 Claude-Mem 的差距、改进建议、路线图与旁路适配，最后到附录与总结。每个文件聚焦一个方面，单文件控制在便于浏览的体量。

## 阅读路径建议

1. **通读**：按 `01` → `08` 顺序即可覆盖全文逻辑。
2. **偏实施**：优先 [04-claude-mem-gap-analysis.md](./04-claude-mem-gap-analysis.md) → [05](./05-improvements-short-term.md) / [06](./06-improvements-mid-long-term.md) → [07-roadmap-and-bypass-adaptation.md](./07-roadmap-and-bypass-adaptation.md)。
3. **落地细节**：附录、总结与「附录 C 历史存档」见 [08-appendices-and-summary.md](./08-appendices-and-summary.md)（**C.3** 保留拆分前的完整 YAML 草稿便于核对）。

## 文档地图

| 文档 | 主题 |
|------|------|
| [01-motivation-and-framework.md](./01-motivation-and-framework.md) | §1–2：核心问题、Evo-Memory 框架、流式任务流、ExpRAG |
| [02-remem-architecture.md](./02-remem-architecture.md) | §3：ReMem、Refine、WriteBack、Refine 候选策略等 |
| [03-experiments-and-findings.md](./03-experiments-and-findings.md) | §4：关键实验与 RQ |
| [04-claude-mem-gap-analysis.md](./04-claude-mem-gap-analysis.md) | §5：当前 Java 版差距 |
| [05-improvements-short-term.md](./05-improvements-short-term.md) | §6.1：短期可落地改进 |
| [06-improvements-mid-long-term.md](./06-improvements-mid-long-term.md) | §6.2–6.3：中长期与架构级方向 |
| [07-roadmap-and-bypass-adaptation.md](./07-roadmap-and-bypass-adaptation.md) | §7–8：路线图、旁路架构适配 |
| [08-appendices-and-summary.md](./08-appendices-and-summary.md) | 附录 A–D、全文总结；**附录 C** 含权威节选 + **C.3** 拆分前正文存档 |

## 本目录内其他用途

| 文件 | 用途 |
|------|------|
| [misc.md](./misc.md) | 暂未归入上表主题的片段、链接与备忘 |
| [staging.md](./staging.md) | 进行中草稿、修订记录与任务进度 |

## 仓库中的历史入口

仓库根路径下的 [`evo-memory-paper-analysis.md`](../evo-memory-paper-analysis.md) 保留为**占位符**，指向本索引，避免旧书签失效。
