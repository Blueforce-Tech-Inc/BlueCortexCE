# Evolver 记忆系统深度分析（目录入口）

> **文档状态**: v1.5 分片正文 + **v1.6 起**在 `09` 起增补「方面级」演进稿（不并入 `CANONICAL` 所锚定的 `01`–`08` 拼接体）。  
> **分析目标**: 为 BlueCortexCE（旁路型记忆系统）提供可落地的借鉴建议  
> **数据来源**: `/Users/yangjiefeng/Documents/EvoMap/evolver/`（及本仓库架构文档）  
> **最后更新**: 2026-04-19  

本目录将原单体长文（约 326KB）拆成若干**体量可控**的 Markdown 文件，形成可渐进发现的阅读路径：先读定位与总览，再按模块或按时间线深入。顺序分片 `01`–`08` 单文件均 **< 50KB**，便于编辑与 diff；**演进内容**以独立「方面」文件（如 `09`）追加，避免再次膨胀单体。

## 阅读路径建议

1. **通读**：按 `01` → `08` 编号顺序即可覆盖**全部**原文章节（含原「目录」中的锚点列表与各版本增补）。
2. **先抓 BlueCortexCE 落点**：读完 [01](./01-intro-toc-memory-through-curriculum.md) 中的「架构定位」与 [01](./01-intro-toc-memory-through-curriculum.md) 内 **§8 BlueCortexCE 借鉴建议汇总**，再按需跳到相关模块文件。
3. **按主题跳读**：见下表「按主题的入口」。
4. **旁路落地对照**（推荐）：读完 §8 或各模块后，读 [09-aspect-bluecortex-bridge.md](./09-aspect-bluecortex-bridge.md)，按存储/检索/上下文等维度对齐 BlueCortexCE。

## 文档地图（顺序拆分）

| 文件 | 内容范围（原文章节概览） |
|------|--------------------------|
| [01-intro-toc-memory-through-curriculum.md](./01-intro-toc-memory-through-curriculum.md) | 文首元数据、架构定位、**原完整目录**、§1–§11（memoryGraph → curriculum） |
| [02-skilldistiller-through-evolution-v04.md](./02-skilldistiller-through-evolution-v04.md) | §12–§23、待进一步确认、下轮探索方向、§18–§23 等 v0.4 前后增补 |
| [03-skillpublisher-through-signals-v07.md](./03-skillpublisher-through-signals-v07.md) | §24 起至 §34（含信号链路 v0.7 等） |
| [04-mutation-through-policy-v09.md](./04-mutation-through-policy-v09.md) | §35–§43（mutation、evolve、prompt、strategy、idle、git、localState、policyCheck 深度补充等） |
| [05-sanitize-through-execution-trace-v10.md](./05-sanitize-through-execution-trace-v10.md) | §44–§55（sanitize、crypto、analyzer、安全与隐私总览、Hub v1.0、executionTrace 等） |
| [06-assetcalllog-through-questiongen-v12.md](./06-assetcalllog-through-questiongen-v12.md) | §56–§65（assetCallLog、directory、deviceId、a2a v1.1、prompt/strategy v1.2、memoryGraphAdapter、innovation、questionGenerator 等） |
| [07-idle-through-skillpublisher-v14.md](./07-idle-through-skillpublisher-v14.md) | §66–§75（idleScheduler、localState、gitOps、bridge、a2a、privacy、assets、candidates、candidateEval、skillPublisher v1.4） |
| [08-llmreview-assetstore-and-roadmap-v15.md](./08-llmreview-assetstore-and-roadmap-v15.md) | §77–§78（llmReview、assetStore）及 **§76 下轮探索方向（v1.5 更新）** |

### 演进增补（方面级，在 `01`–`08` 之外）

| 文件 | 主题 |
|------|------|
| [09-aspect-bluecortex-bridge.md](./09-aspect-bluecortex-bridge.md) | 架构/存储/检索/上下文/可观测性等维度：**Evolver ↔ BlueCortexCE** 对照与可借鉴动作 |

## 按主题的入口（渐进披露）

| 主题 | 建议入口 |
|------|----------|
| 架构定位与 Hermes 对比 | [01](./01-intro-toc-memory-through-curriculum.md) 开篇表格；**§7–§8** 在同文件后部 |
| 因果记忆图谱（JSONL 事件流） | [01](./01-intro-toc-memory-through-curriculum.md) **§1** |
| 叙事记忆（MD） | [01](./01-intro-toc-memory-through-curriculum.md) **§2** |
| 信号 / 人格 / learningSignals | [01](./01-intro-toc-memory-through-curriculum.md) **§3–§5**；深化见 [03](./03-skillpublisher-through-signals-v07.md)、[04](./04-mutation-through-policy-v09.md) |
| 进化主循环与 GEP 相关 | [01](./01-intro-toc-memory-through-curriculum.md) **§6**；多版本 **evolve / prompt / strategy** 见 [02](./02-skilldistiller-through-evolution-v04.md)–[04](./04-mutation-through-policy-v09.md)、[06](./06-assetcalllog-through-questiongen-v12.md) |
| 固化、选择器、课程、蒸馏 | [01](./01-intro-toc-memory-through-curriculum.md) **§9–§11**；[02](./02-skilldistiller-through-evolution-v04.md) **§29–§32** 等深度补充 |
| Hub / 联邦 / A2A / 目录 | [04](./04-mutation-through-policy-v09.md) 起多处；[05](./05-sanitize-through-execution-trace-v10.md)–[07](./07-idle-through-skillpublisher-v14.md) |
| 安全、隐私、脱敏、加密、验证 | [04](./04-mutation-through-policy-v09.md) **§43** 起；[05](./05-sanitize-through-execution-trace-v10.md) **§44–§51** |
| 资产与存储（assetStore 等） | [02](./02-skilldistiller-through-evolution-v04.md)、[07](./07-idle-through-skillpublisher-v14.md)、[08](./08-llmreview-assetstore-and-roadmap-v15.md) |
| 版本历史与 TODO | 原 **§33** 位于 [03](./03-skillpublisher-through-signals-v07.md)（随批次包含对应章节） |
| **方面级旁路映射**（任务说明中的「架构概览、存储设计、检索…」） | [09](./09-aspect-bluecortex-bridge.md) |

> 若某条「§ 编号」在多次版本增补中出现多节同名标题，以**原文章内编号与版本标注**为准；完整列表见 [01](./01-intro-toc-memory-through-curriculum.md) 中的**原目录**一节。

## 本目录内其他用途

| 文件 | 用途 |
|------|------|
| [AGENT.md](./AGENT.md) | **给 AI 文档助理**：单文件上限、索引优先、staging / misc 用法与演进流程 |
| [misc.md](./misc.md) | 暂未归入上表、不便归类的片段与备忘 |
| [staging.md](./staging.md) | 进行中草稿、修订记录与任务进度 |

## 仓库中的历史入口

仓库路径 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 保留为**占位符**，指向本索引，避免旧书签失效。

**完整性锚点**：[`CANONICAL.sha256`](./CANONICAL.sha256) 记录迁移前单体全文与「`01`–`08` 分片（各文件顶部 `<!-- part ... -->` 注释行除外）按序拼接」一致的 SHA256；修改分片正文后须**重新计算拼接正文**并更新该文件中的哈希值。
