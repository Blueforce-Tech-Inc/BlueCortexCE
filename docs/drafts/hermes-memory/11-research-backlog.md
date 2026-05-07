# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-05-07 07:32（v15.0：归档 v9.9–v13.6 巡检日志 → `11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md`；`11-research-backlog.md` 17KB；上游 `3cdbf334d` 无记忆系统变更）
> **旧巡检日志（2026-04-24 → 2026-05-05 02:33）**：→ [`11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`](11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md)

**本地 Hermes Agent Repo**：⚠️ 本地 HEAD `946ef0ea1`，origin/main `f1a8e9994`（落后 8 commits）；需 `cd ~/.hermes/hermes-agent && git fetch origin main && git checkout origin/main` 同步后再进行源码实地分析

---

## 使用方式

- 完成一项：`[x]`，并补「结论链接」（PR、commit、或对应 `.md` 增补说明）。
- 条目膨胀：迁入 `60-evolution/` 新文件或 `02` 相应节，此处仅留指针。

---

## 文档与上游

- [x] **Multi-Provider 插件发现架构**：`plugins/memory/__init__.py` 全解 → [`14`](60-evolution/14-multi-provider-plugin-discovery.md)（discover/load/Collector/CLI 发现模式；8 Provider 清单）
- [x] **Session DB Flush duplicate-write fix**：`run_agent.py` `_flush_messages_to_session_db` + `_last_flushed_db_idx` → [`15`](60-evolution/15-session-db-flush-and-duplicate-fix.md)
- [x] **Extended MemoryProvider Hooks**：`on_pre_compress` / `on_memory_write` / `on_delegation` / `queue_prefetch` → [`16`](60-evolution/16-extended-memory-provider-hooks.md)（RetainDB 三线程预取模型详解）
- [x] **上游 Smart Compression + Exhaustion Loop Fix（2026-04-14）**：→ [`17`](60-evolution/17-smart-compression-and-exhaustion-fix.md)（Smart tool collapse with 20+ tool-specific summaries / MD5 dedup / Anti-thrashing 2-pass <10% / `failed: True` + session auto-reset via gateway）
- [x] **三新增 Provider 分析**（byterover/hindsight/openviking）：→ [`18`](60-evolution/18-three-new-memory-providers.md)（层级 Context Tree / 知识图谱+Reflect / 文件系统 URI+tiered context+atexit；更新 `14` Provider 清单）
- [x] **Gateway 后台 Session 过期 Watcher**：`gateway/run.py` `_session_expiry_watcher` + `memory_flushed` 持久化 + 当前记忆状态注入防覆盖 + cron 跳过 + 重试熔断 → [`19`](60-evolution/19-gateway-session-expiry-watcher.md)（2026-04-23 新增）
- [x] **Tool Result Persistence 3-Layer Defense**：`tools/tool_result_storage.py` + `tools/budget_config.py` — per-tool self-truncation / per-result sandbox persist (`maybe_persist_tool_result`) / per-turn 200K aggregate budget (`enforce_turn_budget`) / `read_file` pinned at `inf` 防循环 → [`20`](60-evolution/20-tool-result-persistence.md)（2026-04-23 新增）
- [x] **Session Search Tool — FTS5 + LLM Recall**：`tools/session_search_tool.py` — 双模式（空查询=recent / 有查询=FTS5 search）/ 智能截断（position-aware windowing）/ 并行 summarization / auxiliary model / delegation chain resolution → [`21`](60-evolution/21-session-search-tool.md)（2026-04-23 新增）
- [x] **Hindsight 知识图谱深度解析**（TEMPR 四路检索 / Observation 合并 / 实体消解 / 双时间模型 / Reflect Agentic Loop / Disposition System）：→ [`22`](60-evolution/22-hindsight-knowledge-graph-deep-dive.md)（2026-04-23 新增）
- [x] **单文件逼近 50KB 时预拆分（2026-04-24）**: `06` → [`29`]；`07` → [`30`]；`04` → [`06-honcho-holographic-deep-advanced.md`](50-honcho-holographic-deep/06-honcho-holographic-deep-advanced.md)；AGENT.md 预警表已更新
- [x] **Session Auto-Prune + Secrets Redaction + Bug Fixes（2026-04-25）** → [`39`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)
- [x] **on_session_finalize Expiry Flush + Hindsight CPU Detection + Redact Config Bridge（2026-04-25）** → [`45`](60-evolution/45-on-session-finalize-expiry-flush-cpu-detection-and-redact-bridge.md)
- [x] **ContextEngine 可插拔架构（2026-04-24）** → [`27`](60-evolution/27-context-engine-pluggable-architecture.md)
- [x] **上游 hermes-agent 同步（2026-04-23）**: memory 相关文件无新提交
- [x] **Auxiliary Client 深度解析（2026-04-23）** → [`23`](60-evolution/23-auxiliary-client-resolution-chain.md)
- [x] **ContextCompressor 完整算法整合（2026-04-23）** → [`24`](60-evolution/24-context-compressor-full-algorithm.md)
- [x] **Hindsight 本地嵌入 Daemon + PostgreSQL Schema（2026-04-23）** → [`25`](60-evolution/25-hindsight-local-embedded-daemon-and-postgresql-schema.md)
- [x] **Compression Model Fallback（2026-04-24）** → [`17`](60-evolution/17-smart-compression-and-exhaustion-fix.md) §5b

## 与其它 backlog 的边界

| 文件 | 放什么 |
|------|--------|
| **本文件** | Hermes 参照 → CE 的未决项 |
| [`../evolver-memory/11-research-backlog.md`](../evolver-memory/11-research-backlog.md) | Evolver/产品/数据模型未决项 |
| **本文件巡检日志归档** | 第一部分 → [`11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`](11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md)；第二部分 → [`11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md`](11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md)（v9.9–v13.6）|

---

## 定时巡检（2026-05-07 02:01 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`b62a82e0c` → `28299afc2`）✅
- [x] **上游代码增量扫描**（`b62a82e0c..origin/main`，9 commits）：**0 个记忆系统相关** — 全部为 Feishu topic thread 修复（`28299afc2`/`441ef75d1`）+ SearXNG web search（`48c241840`/`94016dd1a`/`5c906d702`/`cd2cbc73b`）+ Dashboard theme（`6388aafbd`）+ OpenCode Go 修复（`a24789d73`）+ Linear docs（`ad7aad251`）；无记忆/上下文/压缩/provider/hook/hindsight 相关
- [x] **新增分析：Curator 技能生命周期管理与后台维护编排器（2026-05-07 新增）**：`agent/curator.py`（1674 行）— 空闲触发调度（`should_run_now()` + idle hours）+ 纯函数状态机（active → stale → archived）+ LLM 驱动的伞形化评审（`CURATOR_REVIEW_PROMPT`）+ 原子状态持久化（temp+fsync+replace，与 doc 95 一致）+ 分类启发式（consolidation vs pruning）+ 双输出报告系统（run.json + REPORT.md）+ Dry-run 模式；CE 借鉴：后台 Observation 生命周期管理 + 相似 Observation 自动合并 + Session 自动过期刷新 → [`97`](60-evolution/97-curator-skill-lifecycle-and-background-maintenance-orchestrator.md)（12,067 字节）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 78 篇正文，全部低于 50KB ✅；新增 doc `97`（12,067 字节）
- [x] **Backlog 全部项 `[x]`**：v14.0 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `28299afc2`）

## 定时巡检（2026-05-07 02:58 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`28299afc2` → `946ef0ea1`）✅
- [x] **上游代码增量扫描**（`28299afc2..origin/main`，5 commits）：**0 个记忆系统相关** — 全部为 TUI 修复（`946ef0ea1` virtual history offset bounds）+ Kanban 修复（`a2ff19305`/`b1d420e75`）+ typecheck merge（`a345f7b6e`）；无记忆/上下文/压缩/provider/hook/hindsight 相关
- [x] **新增分析：Tool Call Loop Guardrails + File Safety（2026-05-07 新增）**：`agent/tool_guardrails.py`（455 行）+ `agent/file_safety.py`（111 行）— ⭐⭐ **ToolCallGuardrailController** 三模式检测（exact failure / same-tool failure / idempotent no-progress）+ SHA256 签名标准化 + opt-in hard stop + 可配置阈值矩阵 + synthetic result 输出；⭐⭐ **FileSafety** 双防线（denylist 精确路径 + 目录前缀 + safe root 隔离 + Hermes 内部缓存读取拦截）；CE 借鉴：P1 MCPTools 层 GuardrailController（防止工具循环）+ P1 文件路径 denylist（`ContextService` 补充）；CE 安全纵深 L1-L4 缺口对照；Tool Signature 模式值得在 CE SDK 层实现 → [`98`](60-evolution/98-tool-call-loop-guardrails-and-file-safety.md)（10,038 字节）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 79 篇正文，全部低于 50KB ✅；新增 doc `98`（10,038 字节）
- [x] **Backlog 全部项 `[x]`**：v14.1 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `946ef0ea1`）

## 定时巡检（2026-05-07 03:36 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`b62a82e0c` → `946ef0ea1`）✅（本地曾退回到 `b62a82e0c`，现已恢复到 origin/main）
- [x] **上游代码增量扫描**（`b62a82e0c..origin/main`，16 commits）：**0 个记忆系统相关** — 全部为 Feishu topic threading 修复（`28299afc2`/`441ef75d1`）+ TUI virtual history bounds（`946ef0ea1`）+ Web SearXNG 搜索（`48c241840`/`94016dd1a`/`5c906d702`/`cd2cbc73b`）+ Dashboard theme（`6388aafbd`）+ OpenCode Go 修复（`a24789d73`）+ Linear docs（`ad7aad251`）+ CI typecheck（`9627ee70e`）+ Kanban 修复（`a2ff19305`/`b1d420e75`）+ typecheck merge（`a345f7b6e`）；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 46,443 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 79 篇正文，全部低于 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.2 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `946ef0ea1`）

## 定时巡检（2026-05-07 04:39 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `946ef0ea1`（与上次巡检同 commit，无需 checkout）✅
- [x] **上游代码增量扫描**（`946ef0ea1..origin/main`，0 commits）：无新 upstream 推进，repo 已是最新 ✅
- [x] **上游代码增量扫描（从 b8fb9270c 补扫）**：b8fb9270c..946ef0ea1 共 30 commits，**0 个记忆系统相关** — TUI virtual history bounds（`946ef0ea1`）+ CI typecheck（`a345f7b6e`/`9627ee70e`）+ Feishu topic threading（`28299afc2`/`441ef75d1`）+ SearXNG web search（`48c241840`/`94016dd1a`/`5c906d702`/`cd2cbc73b`）+ Dashboard theme（`6388aafbd`）+ OpenCode Go（`a24789d73`）+ Linear docs（`ad7aad251`）+ Checkpoints v2（`a0fedfbb1`）+ Kanban 修复（`a2ff19305`/`b1d420e75`）；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 46,443 字节（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.3 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `946ef0ea1`）

## 定时巡检（2026-05-07 05:03 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`5044e1cbf` → `f1a8e9994`）❌ 本地 HEAD `946ef0ea1` 仍落后 8 commits，下次巡检前需手动同步
- [x] **上游代码增量扫描**（`5044e1cbf..origin/main`，8 commits）：**0 个记忆系统相关** — TUI skin highlight colors（`f1a8e9994`）+ TUI virtual offset refresh（`da6019820`）+ CLI thin PTY LF enter（`5044e1cbf`）+ gateway per-platform restart notification（`b71f80e6c`）+ auth JSON fallback（`33bf5f629`）+ tool-gateway docs rewrite（`d514dd405`）；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights/compressor 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 44,860 字节（合规 < 50KB）✅；归档 entries 1-20 → `index-reading-order-archive-1.md`（3,445 字节）；`index.md` 更新为 7,281 字节 ✅；`AGENT.md` 更新体量预警表 ✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.4 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `f1a8e9994`）

## 定时巡检（2026-05-07 05:40 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`f1a8e9994` → `53a024994`）✅（v14.4 记录本地落后 8 commits，现已同步至最新 `53a024994`）
- [x] **上游代码增量扫描**（`f1a8e9994..53a024994`，2 commits）：**0 个记忆系统相关** — Docker CI 修复（`53a024994` merge #20890 / `f4031df05` overlapping builds guard）；无 Python 源码变更；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights/compressor 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`11-research-backlog.md` 41,180 字节（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.5 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `53a024994`）

## 定时巡检（2026-05-07 05:51 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`53a024994` → `5ccab51fa`）✅
- [x] **上游代码增量扫描**（`53a024994..5ccab51fa`，1 commit）：**0 个记忆系统相关** — TUI scrollbar 修复（`5ccab51fa` fix #20917：`ui-tui/src/__tests__/` + `ui-tui/src/app/useInputHandlers.ts` + `ui-tui/src/components/appChrome.tsx` + `ui-tui/src/lib/precisionWheel.ts` + `ui-tui/src/lib/viewportStore.ts`）；无 Python 源码变更；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights/compressor 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`index-reading-order.md` 44,860 字节（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.6 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `5ccab51fa`）

## 定时巡检（2026-05-07 06:40 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `5ccab51fa`（与上次巡检同 commit，无需 checkout）✅
- [x] **上游代码增量扫描**（`5ccab51fa..origin/main`，0 commits）：repo 已是最新 ✅
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`index-reading-order.md` 从 44,860B 更新到 48,048B（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Reading Order 补全**：新增 entries 90-95（docs 95-98 + 上游扫描 100/101），`index-reading-order.md` 48,048 字节（< 50KB）✅；`index.md` 更新为 95 项；`AGENT.md` 体量预警表已更新；`staging.md` 待同步 AGENT.md 体量表
- [x] **Backlog 全部项 `[x]`**：v14.8 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `5ccab51fa`）

## 定时巡检（2026-05-07 06:10 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `5ccab51fa`（与上次巡检同 commit，无需 checkout）✅
- [x] **上游代码增量扫描**（`5ccab51fa..origin/main`，0 commits）：repo 已是最新 ✅
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`index-reading-order.md` 44,860 字节（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.7 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `5ccab51fa`）

## 定时巡检（2026-05-07 06:55 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch origin/main（`5ccab51fa` → `04cf4788c`）✅
- [x] **上游代码增量扫描**（`5ccab51fa..origin/main`，1 commit）：**0 个记忆系统相关** — TUI voice push-to-talk 修复（`04cf4788c` fix #20897：TUI voice + regression tests）；无 Python 源码变更；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights/compressor 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`11-research-backlog.md` 现 ~45KB（合规 < 50KB）✅；`hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v14.9 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `04cf4788c`）

## 定时巡检（2026-05-07 07:32 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch origin/main（`04cf4788c` → `3cdbf334d`）✅
- [x] **上游代码增量扫描**（`04cf4788c..origin/main`，1 commit）：**0 个记忆系统相关** — Gateway setup wizard 修复（`3cdbf334d fix(gateway): don't dead-end setup wizard when only system-scope unit is installed`）；无 Python 源码变更；无记忆/上下文/压缩/provider/hook/hindsight/curator/guardrail/insights/compressor 相关
- [x] **文档架构规范自检 + 归档**：
  - 入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅
  - `11-research-backlog.md` 逼近 50KB（45,467 字节），归档 v9.9–v13.6（lines 50-237）至 [`11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md`](11-research-backlog-inspection-logs-2026-05-05-to-2026-05-07.md)（28,824 字节）
  - 归档后 `11-research-backlog.md` 17,089 字节 ✅
  - `index-reading-order.md` 48,048 字节（合规 < 50KB）✅
  - `hermes-memory/` 全部正文 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v15.0 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `3cdbf334d`）
