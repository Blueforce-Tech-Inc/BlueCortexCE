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

**复核快照（2026-05-05 07:52 CST）**：全部 `.md` 文件字节数扫描（见下表）。新增 1 份分析（`73`，7662 字节）。全部低于 50KB 上限。

⚠️ **硬上限违反**：上游源码文件 `plugins/memory/honcho/__init__.py` = **54,470 字节**（>50KB 上限），需拆分。该文件是上游 Hermes 源码，非本仓库文档，但其在 `docs/drafts/hermes-memory/` 中的分析文档应避免再增长。

**已处理**：
- `09`（46,922B）→ `09-supermemory-capture-lifecycle.md`（§56–§69，41,910B）+ `09a-supermemory-lifecycle-continued.md`（§70–§73，5,012B）
- `08`（46,224B）→ `08-builtin-memory-tool-bounded-snapshot.md`（§54–§61，40,571B）+ `08a-builtin-memory-providers-continued.md`（§62+§53，5,653B）
- `05`（46,001B）→ `05-multimodal-memory-clarification.md`（§30–§35，42,912B）+ `05a-honcho-holographic-continued.md`（§36，3,089B）
- 原始文件保留为 `*-original.md` 备份。

| 字节数（约） | 路径 |
|-------------|------|
| 41,910 | [`60-evolution/09-supermemory-capture-lifecycle.md`](60-evolution/09-supermemory-capture-lifecycle.md)（§56–§69；续写至 `09a`） |
| 5,012 | [`60-evolution/09a-supermemory-lifecycle-continued.md`](60-evolution/09a-supermemory-lifecycle-continued.md)（§70–§73） |
| 42,912 | [`50-honcho-holographic-deep/05-multimodal-memory-clarification.md`](50-honcho-holographic-deep/05-multimodal-memory-clarification.md)（§30–§35；续写至 `05a`） |
| 3,089 | [`50-honcho-holographic-deep/05a-honcho-holographic-continued.md`](50-honcho-holographic-deep/05a-honcho-holographic-continued.md)（§36） |
| 40,571 | [`60-evolution/08-builtin-memory-tool-bounded-snapshot.md`](60-evolution/08-builtin-memory-tool-bounded-snapshot.md)（§54–§61；续写至 `08a`） |
| 5,653 | [`60-evolution/08a-builtin-memory-providers-continued.md`](60-evolution/08a-builtin-memory-providers-continued.md)（§62+§53） |
| 41590 | [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md) |
| ~39.5KB | [`60-evolution/06-memory-provider-hooks-inventory.md`](60-evolution/06-memory-provider-hooks-inventory.md) |
| ~37.8KB | [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md) |
| ~45.7KB（⚠️ 逼近 50KB 上限） | [`11-research-backlog.md`](11-research-backlog.md)（每次巡检追加一行，接近上限时需考虑拆分或归档历史巡检条目） |
| ~34.2KB | [`60-evolution/10-holographic-hrr-implementation.md`](60-evolution/10-holographic-hrr-implementation.md) |
| ~33.6KB | [`60-evolution/30-contradiction-detection-and-session-tools.md`](60-evolution/30-contradiction-detection-and-session-tools.md) |
| 9,708 | [`60-evolution/65-memory-manager-orchestrator-deep-dive.md`](60-evolution/65-memory-manager-orchestrator-deep-dive.md)（2026-05-05 新增；MemoryManager 414L 全解 + CE 对照） |
| 6,793 | [`60-evolution/66-holographic-triple-storage-hrr-store-retrieval.md`](60-evolution/66-holographic-triple-storage-hrr-store-retrieval.md)（2026-05-05 新增；HRR+store+retrieval 三元组） |
| 8,769 | [`60-evolution/67-honcho-session-manager-thread-safety-and-config-parsing.md`](60-evolution/67-honcho-session-manager-thread-safety-and-config-parsing.md)（2026-05-05 新增；Honcho RLock + 配置解析） |
| 931 | [`60-evolution/68-upstream-zero-memory-commits-telegram-topic-mode.md`](60-evolution/68-upstream-zero-memory-commits-telegram-topic-mode.md)（2026-05-05 新增；0 记忆相关，Telegram topic mode） |
| 5,657 | [`60-evolution/60-upstream-110387d14-to-origin-main-memory-analysis.md`](60-evolution/60-upstream-110387d14-to-origin-main-memory-analysis.md)（2026-05-05 新增；89 commits，3 记忆发现；⭐ P2 Compressor Pass2+Prune boundary+SessionSearch source fix） |
| 12,310 | [`60-evolution/69-upstream-1718-commits-memory-analysis.md`](60-evolution/69-upstream-1718-commits-memory-analysis.md)（2026-05-05 新增；1718 commits，10 个记忆发现；⭐ P0 on_memory_write bridge 缺失 + ContextEngine ABC） |
| 7,662 | [`60-evolution/73-upstream-739b30bc0-to-origin-main-memory-analysis.md`](60-evolution/73-upstream-739b30bc0-to-origin-main-memory-analysis.md)（2026-05-05 新增；237 commits，4 个记忆发现；⭐ P2 cooldown漏清理 + 大上下文假溢出 + preflight状态广播） |
| 7,764 | [`60-evolution/74-upstream-compressor-honcho-session-fixes.md`](60-evolution/74-upstream-compressor-honcho-session-fixes.md)（2026-05-05 新增；224 commits，9 个记忆发现；⭐ P1 Compressor 双Pass非字符串guard + 边界方向bug；⭐ P2 Timeout fallback + cooldown清理 + resume_pending + Honcho竞态 + SessionSearch父session） |

**已处理**：
- `06`（48903 → ~38.5KB）：§43–§44 迁入 [`29-memory-provider-hooks-advanced-topics.md`](60-evolution/29-memory-provider-hooks-advanced-topics.md)（2026-04-24）
- `07`（48485 → §45–§52 → [`30`](60-evolution/30-contradiction-detection-and-session-tools.md) ~33.6KB；§44–§45 RetainDB/Supermemory → [`29`](60-evolution/29-memory-provider-hooks-advanced-topics.md)）（2026-04-24）
- `04`（48797 → §20–§22 ~22.6KB 保留原地；§24–§29 → [`06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md) ~25KB）（2026-04-24）

> **新增（2026-04-25）**：[`60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)（8,021 bytes）— Session Auto-Prune+VACUUM / Secrets Redaction in Compaction / Summary Fallback Fix / ContextEngine ABC Fix

**上游源码（本地常见路径）**：内置工具侧 **`MemoryStore`** 等可在 `hermes-agent/tools/memory_tool.py` 对照（与 backlog「`memory_manager` / `memory_provider`」条目联动）；路径以本机克隆为准。
