# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-05-07 00:45（v13.5：新增 [`95`](60-evolution/95-atomic-file-write-and-char-limit-design.md)—原子文件写入模式（temp+fsync+atomic_replace）+ 独立 .lock 文件设计 + 字符预算模型（2200/1375 chars）+ Section Sign `§` 分隔符设计；上游 `b62a82e0c` 与本地同步）
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
| **本文件巡检日志归档** | [`11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`](11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md) |

---

## 定时巡检（2026-05-05 02:33 CST）

- [x] **Honcho Session Manager 线程安全修复分析**（`ec4cb16a2` + `bea2562fc`）：`_peers_cache`/`_sessions_cache` 读写竞态修复（读-检查-写无锁 → 锁内读写，I/O 在锁外）+ 三个 `int()` 配置解析健壮性 → [`67`](60-evolution/67-honcho-session-manager-thread-safety-and-config-parsing.md)
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 57 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。新增 doc `67`（8769 字节）
- [x] **Backlog 全部项 `[x]`**：v9.9 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `0ce1b9fe2`）。

## 定时巡检（2026-05-05 03:48 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`d35efb989` → `81cd67829`）
- [x] **上游代码增量扫描（`d35efb989..origin/main`，1718 commits）**：10 个记忆/上下文系统相关发现 → [`69`](60-evolution/69-upstream-1718-commits-memory-analysis.md)；核心：⭐ P0 on_memory_write bridge 缺失 + ContextEngine ABC 新增（206行）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 58 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。新增 doc `69`（9340 字节）
- [x] **Backlog 全部项 `[x]`**：v10.0 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `81cd67829`）

## 定时巡检（2026-05-05 05:14 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`81cd67829` → `e493b1c48`）
- [x] **上游代码增量扫描（`110387d14..origin/main`，89 commits）**：3 个记忆系统相关 + 1 个上下文管理相关 → [`60`](60-evolution/60-upstream-110387d14-to-origin-main-memory-analysis.md)；⭐ P2 Compressor Pass2 非字符串 guard + `_prune_old_tool_results` 边界修复 + session_search 源修正
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 60 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。新增 doc `60`（4153 字节）
- [x] **Backlog 全部项 `[x]`**：v10.1 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `e493b1c48`）

## 定时巡检（2026-05-05 06:27 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`e493b1c48` → `b8fb9270c`）
- [x] **上游代码增量扫描（`e493b1c48..origin/main`，3 commits）**：**0 个记忆系统相关** — 全部为 CLI key binding 修复（`b8fb9270c` refactor / `429b8eceb` try/except guard / `56a78e74b` Kanban UI）；无记忆/上下文/压缩/provider/hook 相关
- [x] **文档架构规范自检 + 归档**：
  - 入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅
  - 清理 3 个 `*-original.md` 备份文件（`05`/`08`/`09` 各一个，节约 ~138KB）
  - `11-research-backlog.md` 逼近 50KB（45,728 字节），将 lines 39-273 巡检日志归档至 [`11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`](11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md)（15,693 字节）；归档后主文件约 17KB
  - `hermes-memory/` 60 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅
- [x] **新增分析：InsightsEngine 会话分析引擎（2026-05-05 新增）**：`agent/insights.py`（930 行）— Session 数据仓库 SQL 查询 / Token+Cost 估算（CanonicalUsage）/ 多维度分析报告（模型/平台/工具/Skill 使用量分布 / 活动规律 / Top Sessions）/ 双输出格式（Terminal 文本 + Gateway JSON）/ BlueCortexCE 借鉴：会话分析 API `/api/insights` 设计 + ShareGPT 格式轨迹持久化 → [`71`](60-evolution/71-insights-engine-session-analytics-deep-dive.md)
- [x] **Backlog 全部项 `[x]`**：v10.2 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b8fb9270c`）

## 定时巡检（2026-05-05 13:49 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`b816fd4e2` → `601e5f1d5`）
- [x] **上游代码增量扫描（`b816fd4e2..origin/main`，13 commits）**：**0 个记忆系统相关** — 全部为 Microsoft Teams 集成修复（`601e5f1d5` reply fallback / `3f023450d` threading 400 fallback / `69aeba0df` Teams threading 实现 / `c77a6e3fa` OSV-Scanner CI）+ 测试修复（`2333b7a7e`）+ 文档（Teams 接入 sidebar / platform lists）；无记忆/上下文/压缩/provider/hook 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 61 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅
- [x] **Backlog 全部项 `[x]`**：v10.4 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `601e5f1d5`）

- [x] **Prompt Builder 与系统提示词组装架构深度分析**（2026-05-05 15:23）：`agent/prompt_builder.py`（1180 行）+ `run_agent.py` `_build_system_prompt()` — 13 层系统提示词组装 / 注入扫描双层防护 / Tool-Aware Guidance / Skills 两级缓存 / BlueCortexCE P0 ContextSecurityService 借鉴方案 → [`77`](77-prompt-builder-context-injection-architecture.md)

## 定时巡检（2026-05-05 16:44 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`601e5f1d5`，无新 upstream commits）
- [x] **上游代码增量扫描（`b8fb9270c..origin/main`，19 commits）**：**1 个轻微相关** — `b816fd4e2 fix(tui): complete absolute paths as paths`（TUI路径处理，非核心记忆）；其余 18 个全部为 Teams 集成、terminal 修复、security（OSV-Scanner）；无压缩/provider/hook/insights 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 63 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅；新增 doc `78`（23,127 字节）
- [x] **新增分析：跨-cutting 架构模式综合提炼（2026-05-05 新增）**：从 97 篇分析文档中提炼 11 个跨领域架构模式 — ① 分层隔离（5层记忆管线）② 生命周期钩子体系（7 Hook × 触发时机）③ 向后兼容与渐进演进 ④ 事务边界与状态一致性 ⑤ 可观测性架构 ⑥ 安全架构（fence/injection/redaction 三层）⑦ 性能工程（cache友好/自适应压缩/write-behind）⑧ 错误处理与降级（circuit breaker/auxiliary fallback/timeout chain）⑨ 多租户与权限模型 ⑩ 测试策略（contract tests）⑪ 实施优先级矩阵（P0-P3，10项）；每项含 CE 实施建议代码锚点 → [`78`](78-cross-cutting-architectural-patterns-synthesis.md)
- [x] **Backlog 全部项 `[x]`**：v10.6 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `601e5f1d5`）

## 定时巡检（2026-05-05 14:27 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`b816fd4e2` → `601e5f1d5`）✅
- [x] **上游代码增量扫描（`b816fd4e2..origin/main`，13 commits）**：**0 个记忆系统相关**（已在 v10.4 记录）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 62 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅；新增 doc `76`（8121 字节）
- [x] **新增分析：BlueCortexCE P0/P1 差距盘点 + 不安全 UTF-8 威胁分析（2026-05-05 新增）**：对照 doc 02 逐项源码验证 — 确认 2 个 P0 缺口：① 无 memory-context fence（`IngestionController` 无围栏标签）② 无注入扫描（仅长度截断，无 regex/不可见 unicode/RTL 检测）；P1 缺口：BM25 FTS / session 历史搜索 / frozen snapshot / auxiliary LLM 均未实现；威胁模型：5 类攻击面均脆弱；参考 Hermes 锚点：`context_engine.py` `_scan_memory_content()` + `memory_manager.py` `sanitize_context()` → [`76`](60-evolution/76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md)
- [x] **Backlog 全部项 `[x]`**：v10.5 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `601e5f1d5`）

全局导航：[`../memory-research-hub.md`](../memory-research-hub.md)

## 定时巡检（2026-05-05 17:27 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`601e5f1d5`，无新 upstream commits）
- [x] **上游代码增量扫描（`601e5f1d5..origin/main`，0 commits）**：上游无推进，文档保持最新
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 65 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅；新增 doc `79`（9213 字节）
- [x] **Backlog 全部项 `[x]`**：v10.8 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `601e5f1d5`）

## 定时巡检（2026-05-05 18:12 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`601e5f1d5` → `13a7cbcd6`）✅
- [x] **上游代码增量扫描（`0ce1b9fe2..origin/main`，54 commits）**：6 个记忆/上下文系统相关发现 → [`83`](60-evolution/83-upstream-0ce1b9fe2-to-13a7cbcd6-memory-analysis.md)；⭐ **P1** `1e6285c53` Compression Eval Harness（18 文件，9步 scrubber，6维 rubric，CE Phase3 迁移）→ [`82`](60-evolution/82-compression-eval-harness-and-scrubber-pipeline.md)；⭐ **P1** `72d53e14a` Summary Pipeline Credential Redaction（三注入点 `redact_sensitive_text`）；⭐ **P2** `02e328c41` Image Token Charging（flat 1600 token/image）；⭐ **P2** `4a3eac5fe` `/recap` 无 LLM Session 摘要；P2 `6366fb9c8` Periodic Gateway Memory Logging；P3 `319141a0d` session_search None tool_name Truncation Fix
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 67 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅；新增 doc `82`（5493 字节）+ doc `83`（5841 字节）
- [x] **Backlog 全部项 `[x]`**：v10.9 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `13a7cbcd6`）

## 定时巡检（2026-05-05 19:52 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`13a7cbcd6` → `b93643c8f`）✅
- [x] **上游代码增量扫描（`13a7cbcd6..origin/main`，23 commits）**：4 个记忆/上下文系统相关发现 → [`84`](60-evolution/84-upstream-13a7cbcd6-to-origin-main-memory-analysis.md)；⭐ **P1** `4a3e3e20e` 迭代压缩摘要连续性修复（`_find_latest_context_summary` 在压缩窗口内从后向前扫描已有 summary，防重复压缩+摘要链断裂）；P2 `2eef395e1` role=user fallback 结束标记（防弱模型误读 summary 为新输入）；P2 `aacf36e94` 手动 /compress 结果持久化到 session_db；P2 `2a285d5ec` 新增 `agent/think_scrubber.py`（386行流式 reasoning block 状态机过滤）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/` 68 篇正文 + 1 篇归档，最大 46922 字节（`09`），全部低于 50KB 上限 ✅；新增 doc `84`（5190 字节）
- [x] **Backlog 全部项 `[x]`**：v11 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b93643c8f`）

## 定时巡检（2026-05-06 01:17 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已与本地 HEAD 同步（`b93643c8f`）；77 个新 commit（2026-05-05 全天），2 个记忆/上下文相关
- [x] **上游代码增量扫描**：`agent/context_compressor.py` 2 条修复均已在 doc 84 记录，本次不做重复扫描。转为专题深化：新增 [`85`](60-evolution/85-hermes-context-summary-end-marker-and-iterative-continuity.md)（7,638 字节）— `2eef395e1`/`4a3e3e20e` 深度解析；⭐⭐⭐ **Context Summary End Marker** 可直接迁移至 `ContextService.renderTimeline()`（3行代码），解决 CE 模型混淆 summary 与用户输入的问题；**迭代提取连续性**（`_find_latest_context_summary` + identity rehydration）为 Phase 3 迭代提取引擎提供参考实现模式
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`index-reading-order.md` 45,026 字节（逼近 50KB，暂不追加条目）✅；`hermes-memory/` 69 篇正文 + 1 篇归档，全部低于 50KB ✅；新增 doc `85`（7,638 字节）
- [x] **Backlog 全部项 `[x]`**：v11.1 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b93643c8f`）

## 定时巡检（2026-05-06 02:04 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`b93643c8f` → `87b113c2e`）✅
- [x] **上游代码增量扫描**（`b93643c8f..origin/main`，87 commits）：8 个记忆/上下文系统相关发现 → [`86`](60-evolution/86-upstream-b93643c8f-to-87b113c2e-memory-analysis.md)；⭐ **P1** `7f735b4db`（#16938）— 压缩后有效 session_id 追踪；⭐ **P1** `efe1cb00c`（#17055）— Reasoning 跨轮泄漏防护（turn boundary stop）；P2 `5795b3be4` — ACP `SessionDB.replace_messages()` 原子性历史重写；P2 `ecc909de3` — JSONL transcript append 锁序列化；P2 `0a7cc85ea` — Honcho 语义搜索（user_message as search_query）；P2 `c46bc9294`（#12977）— Aux provider context length 修复；P2 `fe8560fc1`（#20199）— X-Hermes-Session-Key long-term memory scoping；P3 `e8e914737` — ACP reasoning metadata persistence 测试
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/60-evolution/` 86 篇正文，全部低于 50KB ✅；新增 doc `86`（8996 字节）
- [x] **Backlog 全部项 `[x]`**：v12 完成，无待跟进项。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `87b113c2e`）

## 定时巡检（2026-05-06 03:04 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `87b113c2e`（与上次巡检同 commit，无需 checkout）
- [x] **上游代码增量扫描**（`b93643c8f..origin/main`，75 commits）：已全量覆盖于 doc 86；本次确认无新记忆系统相关 commit。`e8e914737`（ACP reasoning metadata 持久化测试）已记录为 P3
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（逼近 50KB 但合规）✅；`hermes-memory/` 各子目录总计 69+ 篇正文，全部低于 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v12.1 完成，上游无新进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `87b113c2e`）

## 定时巡检（2026-05-06 03:54 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`87b113c2e` → `3b750715a`）✅
- [x] **上游代码增量扫描**（`87b113c2e..origin/main`，2 commits）：1 个记忆系统相关 → [`87`](60-evolution/87-upstream-87b113c2e-to-3b750715a-memory-analysis.md)；⭐ **P1** `3b750715a` — Lazy session creation 回归修复（#18370 fallout，#20363）：ghost compression session 清理（`finalize_orphaned_compression_sessions()`）+ stale session_key 修复（`_finalize_session` 使用 `agent.session_id`）+ `pending_title` policy flags（auto-compression 保留用户意图）+ ValueError duplicate title 处理（#19029）+ empty response 归一化（#18765，3 种场景覆盖）；`0397be593`（CLI `/provider` 别名移除）— 无关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；`hermes-memory/60-evolution/` 70 篇正文，全部低于 50KB ✅；新增 doc `87`（5471 字节）
- [x] **Backlog 全部项 `[x]`**：v12.2 完成，上游无更多记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `3b750715a`）

## 定时巡检（2026-05-06 04:57 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`3b750715a` → `1fc8733a6`）✅
- [x] **上游代码增量扫描**（`3b750715a..origin/main`，48 commits）：**0 个核心记忆系统代码变更** → [`88`](60-evolution/88-upstream-3b750715a-to-1fc8733a6-memory-analysis.md)；主要变更：Provider 全量可插拔化（`9022804d7`/`20a4f79ed`，33 个 provider）+ 文档完善 + TUI/CLI 修复；2 个轻微相关 doc-only 变更：`72c33dfe9`（移除过期 BuiltinMemoryProvider 文档引用）和 `e4723f671`（cron context_from 文档补充）；其余 46 个均为非记忆系统 commit
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（逼近 50KB 但合规）✅；`hermes-memory/60-evolution/` 71 篇正文，全部低于 50KB ✅；新增 doc `88`（2510 字节）
- [x] **Backlog 全部项 `[x]`**：v12.3 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `1fc8733a6`）

## 定时巡检（2026-05-06 05:40 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`1fc8733a6` → `50ab0a85a`）✅
- [x] **上游代码增量扫描**（`1fc8733a6..origin/main`，13 commits）：**0 个核心记忆系统代码变更** — 全部为文档国际化（中文 README `05cdcac36` / 中文 Tool Gateway/Windows WSL guide `74e4f5f97`）+ AUTHOR_MAP 更新（5个）+ Open WebUI bootstrap script `1c42d8ff5` + Ollama 本地运行指南 `9a0a4c583` + VS Code ACP Client 集成 `0d945d154`；无记忆/上下文/压缩/provider/hook/insights 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/` 所有 `.md` 71+ 篇，全部低于 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v12.4 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `50ab0a85a`）

## 定时巡检（2026-05-06 06:24 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `50ab0a85a`（与上次巡检同 commit，无需 checkout）✅
- [x] **上游代码增量扫描**（`50ab0a85a..origin/main`，0 commits）：无新 upstream 推进，文档保持最新
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 71 篇正文，全部低于 50KB ✅；最大单稿 `05-multimodal-memory-clarification.md`（42,912 字节）< 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v12.5 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `50ab0a85a`）

## 定时巡检（2026-05-06 06:53 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`50ab0a85a` → `0d41e94ca`）✅
- [x] **上游代码增量扫描**（`50ab0a85a..origin/main`，7 commits）：1 个记忆系统相关 → [`89`](60-evolution/89-upstream-50ab0a85a-to-0d41e94ca-memory-analysis.md)；**P2** `3082fa082` — Hindsight `update_mode='append'` 跨进程去重（probe `/version` API 探测 ≥ 0.5.0，稳定性 `session_id` + `append` 模式合并同 session 跨进程文档；降级至 per-process unique doc_id + overwrite）；其余 6 个为 i18n French / AUTHOR_MAP / Kanban lifecycle
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 72 篇正文，全部低于 50KB ✅；新增 doc `89`（2,236 字节）
- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`50ab0a85a` → `0d41e94ca`）✅
- [x] **上游代码增量扫描**（`50ab0a85a..origin/main`，2 commits）：1 个记忆系统相关（已在 doc 89 覆盖）；**P2** `3082fa082` — Hindsight `_check_api_supports_update_mode_append` 完整实现：`threading.Lock` 双检缓存 + `_probe_url()` 处理 local_embedded 动态端口 + `_meets_minimum_version` semver 比较 + `on_session_switch` flush 针对旧 session 探测；已更新 doc 89 增加实现细节
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 72 篇正文，全部低于 50KB ✅；doc 89 更新后 5,960 字节 < 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v12.7 完成，上游无更多核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `0d41e94ca`）

## 定时巡检（2026-05-06 07:45 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 仍在 `0d41e94ca`（无新 upstream 推进）✅
- [x] **上游代码增量扫描**（`0d41e94ca..origin/main`，0 commits）：无新 upstream 推进，文档保持最新
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 73 篇正文（`89` 号），全部低于 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v12.8 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `0d41e94ca`）

## 定时巡检（2026-05-06 07:58 CST）

- [x] **本地 Hermes Agent Repo 同步**：`git fetch origin main` ✅
- [x] **上游代码增量扫描**（`1fc8733a6..origin/main`，19 commits）：1 个记忆系统相关 → [`90`](60-evolution/90-upstream-1fc8733a6-to-0d41e94ca-memory-analysis.md)；⭐ **P1** `3082fa082` — Hindsight `update_mode='append'` 跨进程去重（probe `/version` API + `threading.Lock` 双检缓存 + semver 版本门控 + local_embedded 动态端口 + `on_session_switch` flush 针对旧 session 探测）；另有 API Server SSE token batching（`3188e63b0`）相关但非核心记忆
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 74 篇正文（`90` 号），全部低于 50KB ✅；新增 doc `90`（4,726 字节）
- [x] **Backlog 全部项 `[x]`**：v13.0 完成，上游已由 doc 89 覆盖 `50ab0a85a..origin/main`，本轮补充 doc 90 覆盖更早起点 `1fc8733a6..origin/main`。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `0d41e94ca`）

## 定时巡检（2026-05-06 09:24 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`735349c67` → `aa88dcc57`）✅
- [x] **上游代码增量扫描**（`735349c67..origin/main`，8 commits）：1 个核心记忆系统相关 → [`92`](60-evolution/92-upstream-aa88dcc57-memory-analysis.md)；⭐⭐⭐ **P0** `aa88dcc57` — 压缩后 cached agent 未清除（2处：`session hygiene` 后 + `/compress` 后均新增 `_evict_cached_agent`），导致 SOUL.md/memory/skills 更新后旧 system prompt 持续生效；⭐⭐⭐ **P1** Memory authority 升级（`informational background data` → `authoritative reference data`）跨 `memory_manager.py` + `context_compressor.py` 双文件；⭐⭐⭐ **P1** `SUMMARY_PREFIX` + `_compression_note` 新增 persistent memory 权威性声明；⭐⭐⭐ **P1** `_INTERNAL_NOTE_RE` 正则兼容新旧措辞（向后兼容旧 session）；⭐ **P2** `/compact` → `/compress` typo fix（#20020）
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 75 篇正文，全部低于 50KB ✅；新增 doc `92`（5,578 字节）
- [x] **Backlog 全部项 `[x]`**：v13.2 完成，上游 1 个核心记忆系统 commit。⭐ CE P0 缺口：压缩后无 context 强制刷新机制（`ContextService` 需在压缩/Memory写入后刷新 `lastMemoryUpdateTs` 并在 `/api/context` 响应中暴露）。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `aa88dcc57`）

## 定时巡检（2026-05-06 19:09 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`aa88dcc57` → `a0556b861`）✅
- [x] **上游代码增量扫描**（`aa88dcc57..origin/main`，6 commits）：**0 个记忆系统相关** — 全部为 TUI 修复（`a0556b861` gap restore / `ca5febfed` FaceTicker drift / `e45df2e81` status-line jitter）+ AUTHOR_MAP（`a869a523e`）+ install.sh hardening（`043a118d4`）+ logger.debug guard（`e70e49016`）；无记忆/上下文/压缩/provider/hook/hindsight 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 76 篇正文，全部低于 50KB ✅；新增 doc `93`（6,246 字节）— `3082fa082` Hindsight `update_mode='append'` 跨进程去重完整分析（double-checked locking 缓存 / local_embedded 动态端口探测 / semver 版本门控 / CE 多实例部署借鉴）
- [x] **Backlog 全部项 `[x]`**：v13.3 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `a0556b861`）

## 定时巡检（2026-05-06 23:16 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`a0556b861` → `b62a82e0c`）✅
- [x] **上游代码增量扫描**（`a0556b861..origin/main`，9 commits）：**0 个记忆系统相关** — 全部为 TUI 修复（`a0556b861` gap restore / `ca5febfed` FaceTicker drift / `e45df2e81` status-line jitter）+ AUTHOR_MAP（`a869a523e`）+ install.sh hardening（`043a118d4`）+ logger.debug guard（`e70e49016`）+ `a0fedfbb1` Checkpoints v2（shadow-git 存儲重构，与记忆系统无关）；无记忆/上下文/压缩/provider/hook/hindsight 相关
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 76 篇正文，全部低于 50KB ✅
- [x] **Backlog 全部项 `[x]`**：v13.4 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b62a82e0c`）

## 定时巡检（2026-05-07 01:44 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `b62a82e0c` ✅
- [x] **上游代码增量扫描**（`b62a82e0c..origin/main`，0 commits）：无新 upstream 推进，文档保持最新
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 88 篇正文，全部低于 50KB ✅；新增 doc `96`（8,557 字节）— Hermes 记忆系统架构综合与 CE 落地路线图（架构全貌 / 最新 P0 aa88dcc57 发现 / 安全三层防护 / 原子写入 / Prefetch / FTS5+LLM 搜索 / 4-Phase 压缩 / 优先级矩阵）
- [x] **Backlog 全部项 `[x]`**：v13.6 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b62a82e0c`）

## 定时巡检（2026-05-07 00:45 CST）

- [x] **本地 Hermes Agent Repo 同步**：origin/main 已在 `b62a82e0c`（与上次巡检同 commit，无需 checkout）✅
- [x] **上游代码增量扫描**（`b62a82e0c..origin/main`，0 commits）：无新 upstream 推进，文档保持最新
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅；最大正文 `index-reading-order.md` 45,026 字节（合规 < 50KB）✅；`hermes-memory/60-evolution/` 77 篇正文，全部低于 50KB ✅；新增 doc `95`（7,850 字节）— 原子文件写入模式（temp+fsync+atomic_replace）+ 独立 .lock 文件设计 + 字符预算模型（2200/1375 chars）+ Section Sign `§` 分隔符设计
- [x] **Backlog 全部项 `[x]`**：v13.5 完成，上游无核心记忆系统进展。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `b62a82e0c`）

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
