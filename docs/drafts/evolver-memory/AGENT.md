# AI 文档助理：EvoMap/evolver 记忆分析目录

与任务「分析 Evolver 记忆架构并服务 BlueCortexCE 借鉴」对齐。演进文档时遵守下列约束，避免退回单文件巨型 Markdown。

## 约束

1. **单文件上限**：`docs/drafts/evolver-memory/` 下正文建议 **≤50KB**；将超限则**新建方面文件**（如 `09-*.md`）或拆分后再写。
2. **索引优先**：新增强内容时更新 [`index.md`](./index.md) 的导航，而非只堆在某一文件末尾。方面结论在 `09`，代码锚点在 [`10`](./10-aspect-bluecortex-implementation-map.md)，HTTP/数据平面在 [`12`](./12-bluecortex-api-memory-surface.md)（**§1.1** `semantic` · **§3.1** `workerHttpRequest` · **§3.2** MCP 工具分流），Java **读出 / 写入 / 会话头尾**在 [`14`](./14-context-output-pipeline-sketch.md) / [`16`](./16-ingestion-write-path-sketch.md) / [`17`](./17-session-lifecycle-java-sketch.md)，**Worker vs Java / wrapper / 各集成客户端默认进程** 在 [`15`](./15-runtime-integration-surfaces.md) **§2–§5**，未决课题在 [`11`](./11-research-backlog.md)。
3. **方面聚焦**：每个文件一个清晰主题；零散片段可 [`misc.md`](./misc.md)；**可勾选接力队列**用 [`11`](./11-research-backlog.md)，极短临时草稿用 [`staging.md`](./staging.md)。
4. **重构先于堆砌**：发现单文件膨胀时先拆分并核对无信息丢失，再继续追加。
5. **历史入口**：[`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) 保持为短入口，**不要**把完整长文写回该路径。模块级细节以 `01`–`08` 为准；对照 BlueCortexCE 的「方面」演进写在 `09-*.md` 等，**不**纳入下面所述的拼接校验范围。  
6. **多线导航**：记忆相关草稿总表见 [`../memory-research-hub.md`](../memory-research-hub.md)。

## 对照短文速查

**`09`–`17` 一句话职责表**以 [`index.md`](./index.md) 中的 **「附录：BlueCortexCE 对照短文」** 为唯一维护点；新增编号文件时**同步更新该附录**与 [`../memory-research-hub.md`](../memory-research-hub.md)「按任务」表，避免各文件重复粘贴同表导致漂移。

## 分片边界

- `01` … `08` 为已定顺序分片；新增主题用 `09+` 文件名前缀，并在 `index.md` 增加一行说明。

## `CANONICAL.sha256`

仅当需要维持「`01`–`08` 去注释头后按序拼接」与某次迁移前全文的字节级一致时：修改对应分片后重算拼接体 SHA256 并更新 [`CANONICAL.sha256`](./CANONICAL.sha256)。**日常阅读与分析可忽略该文件。**
