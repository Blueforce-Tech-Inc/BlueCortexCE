# Evolver / EvoMap 记忆系统分析

**目标**：理解 EvoMap `evolver` 的记忆与进化相关设计，为 BlueCortexCE（旁路记忆）提炼可翻译、可落地的思想（非照搬 GEP 运行时）。

**数据来源**：本地 `EvoMap/evolver/` 源码；本仓库见 `docs/ARCHITECTURE-zh-CN.md` 等。

**最后更新**：2026-04-19

## 入口

| 说明 | 路径 |
|------|------|
| **记忆研究总导航**（Hermes / Evolver / 论文） | [docs/drafts/memory-research-hub.md](./memory-research-hub.md) |
| CE 上下文 **Java 调用链**（`generateContext` / `semantic` / ICL） | [docs/drafts/evolver-memory/14-context-output-pipeline-sketch.md](./evolver-memory/14-context-output-pipeline-sketch.md) |
| **Bun Worker vs Java**（端口、**§2** 集成客户端、Chroma/pgvector、OpenClaw 配置名） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) |
| **Hook → Worker 基址**（`workerHttpRequest`、`CLAUDE_MEM_WORKER_*`） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) **§2.1** |
| **会话首跳**（`POST /api/sessions/init` Worker vs `POST /api/session/start` Java） | [docs/drafts/evolver-memory/15-runtime-integration-surfaces.md](./evolver-memory/15-runtime-integration-surfaces.md) **§5** |
| **Java 摄入 / 写入链**（`IngestionController`、`processToolUseAsync`） | [docs/drafts/evolver-memory/16-ingestion-write-path-sketch.md](./evolver-memory/16-ingestion-write-path-sketch.md) |
| **Java 会话 start / end**（`SessionController`、`/api/session/start`） | [docs/drafts/evolver-memory/17-session-lifecycle-java-sketch.md](./evolver-memory/17-session-lifecycle-java-sketch.md) |
| 总索引（阅读顺序、按主题跳转） | [docs/drafts/evolver-memory/index.md](./evolver-memory/index.md) |
| 方面级对照：Evolver ↔ BlueCortexCE | [docs/drafts/evolver-memory/09-aspect-bluecortex-bridge.md](./evolver-memory/09-aspect-bluecortex-bridge.md) |
| 本仓库实现映射（迁移、Repository、§3 三路读出） | [docs/drafts/evolver-memory/10-aspect-bluecortex-implementation-map.md](./evolver-memory/10-aspect-bluecortex-implementation-map.md) |
| 记忆 HTTP + 数据平面（**§1.1** `semantic` 契约 · §2 写入链） | [docs/drafts/evolver-memory/12-bluecortex-api-memory-surface.md](./evolver-memory/12-bluecortex-api-memory-surface.md) |
| Hermes 参照 + **CE 注入面与 `/api/context`** | [docs/drafts/hermes-memory/index.md](./hermes-memory/index.md)；[04 对照表](./hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md) |
| 研究 / 决策 backlog | [docs/drafts/evolver-memory/11-research-backlog.md](./evolver-memory/11-research-backlog.md) |
| 文档维护约定（体量、索引、staging） | [docs/drafts/evolver-memory/AGENT.md](./evolver-memory/AGENT.md) |
