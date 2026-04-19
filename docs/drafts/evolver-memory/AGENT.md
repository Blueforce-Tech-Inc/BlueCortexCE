# 给 AI 文档助理的执行说明（EvoMap/evolver 记忆分析）

本目录与仓库任务「深入分析 EvoMap/evolver 记忆系统架构，并服务 BlueCortexCE 借鉴」对齐。助理在**演进**既有文档时，请按下列规范操作，避免退回单体巨型 Markdown。

## 硬性约束（与任务说明一致）

1. **单文件上限**：`docs/drafts/evolver-memory/` 下任意 `.md` 正文建议 **≤ 50KB**（与 `evolver-memory-analysis.md` 占位说明一致）。若单篇将超限，**先拆分或新建方面文件**，再写入。
2. **渐进式披露**：优先维护 [`index.md`](./index.md) 的导航（阅读路径、文档地图、按主题入口）；新增大块内容时更新索引，而非只堆在某一文件末尾。
3. **方面优先**：每个文件应聚焦明确「方面」（如：某模块、某版本增补、安全/Hub 等）。不便归类可先放 [`misc.md`](./misc.md)，草稿与进度用 [`staging.md`](./staging.md)。
4. **重构先于堆砌**：若发现某文件膨胀或结构混乱，先拆分并核对无信息丢失，再继续追加分析。
5. **历史入口**：仓库根相对路径 [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 为**占位符**，勿把完整长文写回该文件；**模块级溯源**以 `01`–`08` 分片为准；**方面级演进**（对照 BlueCortexCE 等）用 `09-*.md` 等单独成篇，不并入 `CANONICAL.sha256` 锚定范围。

## 推荐工作流

1. 阅读 [`index.md`](./index.md)，确定内容落在哪一分片或是否需新文件。
2. 大段初稿可写在 [`staging.md`](./staging.md)，再拆入目标 `0x-*.md`。
3. 若**有意变更** `01`–`08` 正文，须按 [`CANONICAL.sha256`](./CANONICAL.sha256) 中的约定重新计算「去注释头后的分片按序拼接」的 SHA256，并更新该文件，以保持与迁移前单体全文可对齐的锚点。

## 分片约定

- 顺序正文：`01-intro-toc-memory-through-curriculum.md` … `08-llmreview-assetstore-and-roadmap-v15.md` 为**已定稿的拆分边界**；若需新方面文件，优先在 `index.md` 增加一行地图说明，并采用清晰文件名前缀（如 `09-*.md`），避免与 `01`–`08` 编号混淆。
