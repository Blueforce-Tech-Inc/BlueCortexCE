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

**本地 Hermes Agent Repo**：已同步至 `origin/main` `3cdbf334d`（gateway setup wizard 修复，非记忆系统）

## 体量预警（cron / 续写前扫一眼）

**硬上限**：单文件 **51200 字节**（约 50KB）——超限须**先拆后写**，见上文工作流。

**建议命令**（仓库根）：

```bash
find docs/drafts/hermes-memory -name '*.md' -exec wc -c {} + | sort -n | tail -15
```

**最后更新**：2026-05-07 08:49 CST

**复核快照（2026-05-07 08:49 CST）**：`index-reading-order.md` 阅读顺序 95+4 项 → 48,419B；entries 1-20 归档 → [`index-reading-order-archive-1.md`](index-reading-order-archive-1.md)（3,445B）；`11-research-backlog.md` 18,383B；`11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md` 28,824B（v9.9–v13.6）；`index.md` 现为 7.3KB。全部 `.md` 文件字节数扫描（见下表）。全部低于 50KB 上限。

⚠️ **硬上限违反**：上游源码文件 `plugins/memory/honcho/__init__.py` = **54,470 字节**（>50KB 上限），需拆分。该文件是上游 Hermes 源码，非本仓库文档，但其在 `docs/drafts/hermes-memory/` 中的分析文档应避免再增长。

**归档文件**（不在正文计数内）：
- `index-reading-order-archive-1.md`（3,445 字节）— entries 1-20 归档
- `11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`（15,693 字节）— 巡检日志归档第一部分
- `11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md`（28,824 字节）— 巡检日志归档第二部分（v9.9–v13.6）

**已处理**：
- `09`（46,922B）→ `09-supermemory-capture-lifecycle.md`（§56–§69，41,910B）+ `09a-supermemory-lifecycle-continued.md`（§70–§73，5,012B）
- `08`（46,224B）→ `08-builtin-memory-tool-bounded-snapshot.md`（§54–§61，40,571B）+ `08a-builtin-memory-providers-continued.md`（§62+§53，5,653B）
- `05`（46,001B）→ `05-multimodal-memory-clarification.md`（§30–§35，42,912B）+ `05a-honcho-holographic-continued.md`（§36，3,089B）
- 原始文件保留为 `*-original.md` 备份。

| 字节数（约） | 路径 |
|-------------|------|
| 48,419 | [`index-reading-order.md`](index-reading-order.md)（2026-05-07 更新；阅读顺序 95+4 项，新增 entries 90-95，docs 95-98 + 上游扫描 100/101/102；staging 更新） |
| 3,445 | [`index-reading-order-archive-1.md`](index-reading-order-archive-1.md)（2026-05-07 新增；entries 1-20 归档） |
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
| 23,127 | [`78-cross-cutting-architectural-patterns-synthesis.md`](78-cross-cutting-architectural-patterns-synthesis.md)（2026-05-05 新增；11 个跨-cutting 架构模式；5层隔离 + 7 Hook + circuit breaker + frozen snapshot + 实施优先级矩阵 P0-P3） |
| 9,213 | [`79-ce-developer-quick-reference.md`](79-ce-developer-quick-reference.md)（2026-05-05 新增；CE 开发者 Top-10 落地借鉴；P0注入防护/Memory Fence/P1会话搜索/Tool Result持久化/可插拔压缩/Auxiliary LLM/Per-User Scoping/Secrets Redaction/Frozen Snapshot/Compression Eval Harness） |
| 5,493 | [`60-evolution/82-compression-eval-harness-and-scrubber-pipeline.md`](60-evolution/82-compression-eval-harness-and-scrubber-pipeline.md)（2026-05-05 新增；Compression Eval Harness 完整分析；9步 scrubber + 6维 rubric + CE Phase3 迁移路径） |
| 5,841 | [`60-evolution/83-upstream-0ce1b9fe2-to-13a7cbcd6-memory-analysis.md`](60-evolution/83-upstream-0ce1b9fe2-to-13a7cbcd6-memory-analysis.md)（2026-05-05 新增；54 commits，6 个记忆发现；⭐ P1: Compression Eval Harness + Credential Redaction；P2: Image Token Charging + /recap + Memory Logging） |
| 7,310 | [`60-evolution/84-upstream-13a7cbcd6-to-origin-main-memory-analysis.md`](60-evolution/84-upstream-13a7cbcd6-to-origin-main-memory-analysis.md)（2026-05-05 新增；23 commits，2 个记忆发现；⭐ P1 迭代压缩摘要连续性 + P2 role=user fallback 结束标记） |
| 7,638 | [`60-evolution/85-hermes-context-summary-end-marker-and-iterative-continuity.md`](60-evolution/85-hermes-context-summary-end-marker-and-iterative-continuity.md)（2026-05-06 新增；`2eef395e1`/`4a3e3e20e` 深度解析；⭐⭐⭐ Context Summary End Marker CE 落地 + 迭代提取身份保护 Phase 3 借鉴） |
| 12,266 | [`60-evolution/86-upstream-b93643c8f-to-87b113c2e-memory-analysis.md`](60-evolution/86-upstream-b93643c8f-to-87b113c2e-memory-analysis.md)（2026-05-06 新增；87 commits，8 个记忆发现；⭐ P1 压缩后有效 session_id 追踪 + Reasoning 跨轮泄漏防护） |
| 6,795 | [`60-evolution/87-upstream-87b113c2e-to-3b750715a-memory-analysis.md`](60-evolution/87-upstream-87b113c2e-to-3b750715a-memory-analysis.md)（2026-05-06 新增；2 commits，1 个记忆发现；⭐ P1 Lazy session creation 回归修复：ghost compression session 清理 + stale session_key 修复 + pending_title policy flags + empty response 归一化） |
| 3,498 | [`60-evolution/88-upstream-3b750715a-to-1fc8733a6-memory-analysis.md`](60-evolution/88-upstream-3b750715a-to-1fc8733a6-memory-analysis.md)（2026-05-06 新增；48 commits，0 个核心记忆系统代码变更；Provider 全量可插拔化（33 个）+ 文档完善） |
| 4,726 | [`60-evolution/90-upstream-1fc8733a6-to-0d41e94ca-memory-analysis.md`](60-evolution/90-upstream-1fc8733a6-to-0d41e94ca-memory-analysis.md)（2026-05-06 新增；19 commits，1 个记忆发现；⭐ P1 Hindsight `update_mode='append'` 跨进程去重：`threading.Lock` 双检缓存 + semver 版本探测 + local_embedded 动态端口 + CE 借鉴：API 兼容性探测模式）
7,676 | [`60-evolution/99-upstream-append-mode-and-honcho-prefetch-semantic.md`](60-evolution/99-upstream-append-mode-and-honcho-prefetch-semantic.md)（2026-05-07 新增；2 commits：⭐⭐⭐ P0 Hindsight Append-Mode + ⭐⭐ P1 Honcho Prefetch 语义搜索） |

**已处理**：
- `06`（48903 → ~38.5KB）：§43–§44 迁入 [`29-memory-provider-hooks-advanced-topics.md`](60-evolution/29-memory-provider-hooks-advanced-topics.md)（2026-04-24）
- `07`（48485 → §45–§52 → [`30`](60-evolution/30-contradiction-detection-and-session-tools.md) ~33.6KB；§44–§45 RetainDB/Supermemory → [`29`](60-evolution/29-memory-provider-hooks-advanced-topics.md)）（2026-04-24）
- `04`（48797 → §20–§22 ~22.6KB 保留原地；§24–§29 → [`06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md) ~25KB）（2026-04-24）

> **新增（2026-04-25）**：[`60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)（8,021 bytes）— Session Auto-Prune+VACUUM / Secrets Redaction in Compaction / Summary Fallback Fix / ContextEngine ABC Fix

**上游源码（本地常见路径）**：内置工具侧 **`MemoryStore`** 等可在 `hermes-agent/tools/memory_tool.py` 对照（与 backlog「`memory_manager` / `memory_provider`」条目联动）；路径以本机克隆为准。
