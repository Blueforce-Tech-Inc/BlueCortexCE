# Hermes 对齐 / 本仓库跟进 — 研究接力

> **角色**：可勾选短队列；**不**重复 [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md) 表格全文。  
> **CE 安全与出口现状盘点**：[`20-recommendations/05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)  
> **最后更新**：2026-05-05 19:52（`13a7cbcd6..origin/main`，23 commits，4 个记忆相关发现）
> **旧巡检日志（2026-04-24 → 2026-05-05 02:33）**：→ [`11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md`](11-research-backlog-inspection-logs-2026-04-24-to-2026-05-05.md)

**本地 Hermes Agent Repo**：✅ 已存在，`git fetch origin/main` 成功（`601e5f1d5` → `13a7cbcd6`）

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
