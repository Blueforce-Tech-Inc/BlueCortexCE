# 记忆相关研究草稿 — 总导航

> **用途**：给人类或 Agent **选入口**，避免在 `docs/drafts/` 下迷路。各子目录仍各自维护 `index.md` / `AGENT.md`。  
> **最后更新**：2026-04-19（Hermes `12-upstream-*` 上游快照）

## 按系统 / 体裁

| 入口 | 侧重点 | 索引 |
|------|--------|------|
| **Hermes Agent**（内置型 Python Agent） | 记忆管线、Provider、压缩；**上游快照** [`hermes-memory/60-evolution/12-upstream-hermes-agent-memory-snapshot.md`](hermes-memory/60-evolution/12-upstream-hermes-agent-memory-snapshot.md) | [`hermes-memory/index.md`](hermes-memory/index.md) · 短链 [`hermes-memory-analysis.md`](hermes-memory-analysis.md) |
| **EvoMap / Evolver**（Node GEP / 图谱） | 因果、信号、叙事、与 CE 方面对照 | [`evolver-memory/index.md`](evolver-memory/index.md) · 短链 [`evolver-memory-analysis.md`](evolver-memory-analysis.md) |
| **Evo-Memory 论文** | 基准与 ReMem 架构、与 Claude-Mem 差距 | [`evo-memory-paper-analysis/index.md`](evo-memory-paper-analysis/index.md) · [`evo-memory-paper-analysis.md`](evo-memory-paper-analysis.md) |

## 按任务（BlueCortexCE 落地）

| 任务 | 优先读 |
|------|--------|
| **本仓库类 / 迁移 / 混合检索锚点** | [`evolver-memory/10-aspect-bluecortex-implementation-map.md`](evolver-memory/10-aspect-bluecortex-implementation-map.md) |
| **HTTP 速查**（§1.1 `semantic` · §3.2 **MCP** `search`/`timeline` **≠** `semantic` POST） | [`evolver-memory/12-bluecortex-api-memory-surface.md`](evolver-memory/12-bluecortex-api-memory-surface.md)；双栈语义 [`evolver-memory/11-research-backlog.md`](evolver-memory/11-research-backlog.md) |
| **Java：`generateContext` / `semantic` / `ExpRagService` 调用链** | [`evolver-memory/14-context-output-pipeline-sketch.md`](evolver-memory/14-context-output-pipeline-sketch.md) |
| **Java：瘦代理摄入 → `AgentService` 观察写入** | [`evolver-memory/16-ingestion-write-path-sketch.md`](evolver-memory/16-ingestion-write-path-sketch.md) |
| **Java：`/api/session/start`、缓存与 session-end 对照** | [`evolver-memory/17-session-lifecycle-java-sketch.md`](evolver-memory/17-session-lifecycle-java-sketch.md) |
| **Bun Worker vs Java Spring**（§1–§3；**§2** 各集成客户端；**§2.1** Hook 基址、`~/.claude-mem/settings.json`、`SettingsRoutes`→`clearPortCache`） | [`evolver-memory/15-runtime-integration-surfaces.md`](evolver-memory/15-runtime-integration-surfaces.md) |
| **会话开局双路径**（`POST /api/sessions/init` Worker vs `POST /api/session/start` Java） | [`evolver-memory/15-runtime-integration-surfaces.md`](evolver-memory/15-runtime-integration-surfaces.md) **§5**；Java 细节 [`evolver-memory/17-session-lifecycle-java-sketch.md`](evolver-memory/17-session-lifecycle-java-sketch.md) |
| **OpenClaw / Hook / Spring 注入与 `/api/context`**（`04` §2.1；会话首跳另见 `15` §5） | [`hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md`](hermes-memory/20-recommendations/04-ce-injection-and-context-api-surface.md) |
| **上下文出口安全 vs Hermes 扫描** | [`hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md) |
| **方面优先级与反模式（Evolver ↔ CE）** | [`evolver-memory/09-aspect-bluecortex-bridge.md`](evolver-memory/09-aspect-bluecortex-bridge.md) |
| **Evo-Memory 论文（Refine / WriteBack）** | [`evo-memory-paper-analysis/02-remem-architecture.md`](evo-memory-paper-analysis/02-remem-architecture.md)、[`07-roadmap-and-bypass-adaptation.md`](evo-memory-paper-analysis/07-roadmap-and-bypass-adaptation.md) |
| **可勾选接力队列** | Evolver：[`evolver-memory/11-research-backlog.md`](evolver-memory/11-research-backlog.md) · Hermes 课题：[`hermes-memory/11-research-backlog.md`](hermes-memory/11-research-backlog.md) |

## 维护约定

- 新增一整条「分析线」时：在本表加一行，并在该线目录的 `index.md` 加回链至本文件（可选）。  
- 单文件仍遵守各目录 **≤50KB** 规则（见各 `AGENT.md`）。  
- **续写建议**：CE 行为增量优先落在 `evolver-memory/10` §3、`12`、`14`（读出）、`16`/`17`（写入与会话头尾）、`15`（Worker/Java 判别）之一，避免只在 `09` 堆长段；Hermes 对照落在 `hermes-memory/20-recommendations` 或 `60-evolution`。
