# Hermes 记忆分析文档 — 维护约定

供后续演进 `docs/drafts/hermes-memory/` 时对齐「单文件有界 + 主题清晰」原则。

## 必须遵守

1. **单文件体量**：正文 Markdown 不超过 **50KB**（约 `wc -c` ≤51200）；将超限则先拆文件或挪章节，再写新内容。
2. **主题**：每文件服务一个明确方面（架构、检索、上下文、某 Provider、某 Hook 等）。不确定时写入 [`staging.md`](staging.md)，整理后再归入子目录。
3. **命名**：新建文件用**英文**文件名；内容语言可与现稿一致（中文为主）。
4. **搬迁不失真**：移动段落时保留代码路径、表格与结论；大段合并可在 `staging` 里暂存对照（可选）。

## 工作流

1. 从 [`index.md`](index.md) 定位 aspect 与已有文档。
2. 小改：直接编辑对应 `.md`。
3. 大段新增：`staging.md` → 拆成 ≤50KB → 归入子目录 → 更新 `index.md` 的阅读顺序或表格。

## 分析目标（不变）

为 **BlueCortexCE 旁路型**记忆服务提炼可落地思想：做「翻译」而非照搬 Hermes 进程内实现。

**与 Evolver 草稿分工**：[`../evolver-memory/index.md`](../evolver-memory/index.md) 侧重 GEP/图谱/信号与 CE 方面对照；本目录侧重 **Hermes 内置记忆管线**。落地路径锚点见 Evolver [`10-aspect-bluecortex-implementation-map.md`](../evolver-memory/10-aspect-bluecortex-implementation-map.md) 与本目录 [`20-recommendations/04-ce-injection-and-context-api-surface.md`](20-recommendations/04-ce-injection-and-context-api-surface.md)。

**全局导航**：[`../memory-research-hub.md`](../memory-research-hub.md)。**Hermes→CE 可勾选队列**：[`11-research-backlog.md`](11-research-backlog.md)。

## 合并前自检

- 各正文文件 `wc -c` ≤51200  
- `index.md` 中的相对链接仍有效  
- 若引入新结论，标明复核日期或上游提交/版本（若已知）

## 体量预警（cron / 续写前扫一眼）

**硬上限**：单文件 **51200 字节**（约 50KB）——超限须**先拆后写**，见上文工作流。

**建议命令**（仓库根）：

```bash
find docs/drafts/hermes-memory -name '*.md' -exec wc -c {} + | sort -n | tail -15
```

**复核快照（2026-04-24）**：下列文件已 **≥45KB**，新增大段前优先拆分或开新 aspect 文件，避免顶格爆线：

| 字节数（约） | 路径 |
|-------------|------|
| ~33.6KB | [`60-evolution/30-contradiction-detection-and-session-tools.md`](60-evolution/30-contradiction-detection-and-session-tools.md)（2026-04-24 拆分自 `07` §45–§52） |
| ~25KB | [`50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md)（2026-04-24 拆分自 `04` §24–§29） |
| ~16000 | [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)（§11–§15 深度专题已拆分至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md)） |
| 46922 | [`60-evolution/09-supermemory-capture-lifecycle.md`](60-evolution/09-supermemory-capture-lifecycle.md) |

**已处理**：
- `06`（48903 → ~38.5KB）：§43–§44 迁入 [`29-memory-provider-hooks-advanced-topics.md`](60-evolution/29-memory-provider-hooks-advanced-topics.md)（2026-04-24）
- `07`（48485 → §45–§52 → [`30`](60-evolution/30-contradiction-detection-and-session-tools.md) ~33.6KB；§44–§45 RetainDB/Supermemory → [`29`](60-evolution/29-memory-provider-hooks-advanced-topics.md)）（2026-04-24）
- `04`（48797 → §20–§22 ~22.6KB 保留原地；§24–§29 → [`06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md) ~25KB）（2026-04-24）

> **新增（2026-04-25）**：[`60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)（8,021 bytes）— Session Auto-Prune+VACUUM / Secrets Redaction in Compaction / Summary Fallback Fix / ContextEngine ABC Fix

**上游源码（本地常见路径）**：内置工具侧 **`MemoryStore`** 等可在 `hermes-agent/tools/memory_tool.py` 对照（与 backlog「`memory_manager` / `memory_provider`」条目联动）；路径以本机克隆为准。
