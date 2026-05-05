# Hermes 记忆分析 — 巡检日志归档（2026-04-24 → 2026-05-05）

> **说明**：本文件归档 2026-04-24 至 2026-05-05 期间的定时巡检日志。
> 当前 backlog 主文件仅保留最近 3 次巡检记录（2026-05-05 02:33 起）。

**归档范围**：原 `11-research-backlog.md` lines 39-273 (2026-04-24 06:40 → 2026-05-05 02:33)  
**归档时间**：2026-05-05 06:27 CST  
**原文件**：[`11-research-backlog.md`](11-research-backlog.md)（维护中）

---

## 新增分析（2026-04-24 cron 巡检）

- [x] **Context 文件扫描机制深度解析（`_scan_context_content` vs `_scan_memory_content`）**：`agent/prompt_builder.py:55` + `tools/memory_tool.py:90` 两套防线完整源码对照 → [`06-context-file-scanning-deep-dive.md`](20-recommendations/06-context-file-scanning-deep-dive.md)；补充 `05` 安全缺口文档 §2 中"AGENTS.md / SOUL.md class file scanning" 条目；更新 index.md 读序。

- [x] **Prompt Caching 与记忆系统交互分析（2026-04-24）**：`agent/prompt_caching.py`（96 行）+ `run_agent.py` 行 790-791, 8265-8268, 8928-8940 — system_and_3 策略 / 压缩-缓存失效自动恢复（2 轮）/ CE 架构差异（旁路型 vs 进程内）/ 可执行借鉴（分层预算、压缩 metadata、System Prompt 稳定性） → [`28`](60-evolution/28-prompt-caching-and-memory-interaction.md)（8121 字节）
- [x] **辅助 LLM fallback（代码实地核实，2026-04-24）**：CE `LlmService` 仅单 `ChatClient` 无 fallback；失败时静默返回 `LlmResponse.empty()`，7 级 Provider 链完全缺失。代码证据已追加至 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5；可执行行动分短/中/长期三档。

## 旁路型落地（BlueCortexCE）

- [x] **Context 出口 + ingest 侧扫描（代码实地复核，2026-04-23）**：
  - **Context 输出无 fence**：`/api/context/inject`、`/api/context/semantic`、`/api/context/generate` 返回纯文本，`ContextService.renderEmptyState`（行 873）输出 `"# project — no memories yet"`，无任何围栏标记。`context-injection.ts` 写的 `<claude-mem-context>` 用于**文件注入**（CLAUDE.md 写入），不用于 LLM 上下文输出层。
  - **无伪造 fence strip**：即使未来添加围栏，输出层也无对应 strip 逻辑。Hermes `sanitize_context` 先 strip 伪造闭合标签再包装，CE 完全缺失。
  - **TS 层有递归防护 tag stripping**：`webui/src/utils/tag-stripping.ts` 剥离 `<claude-mem-context>`、`<private>`、`<system_instruction>`、`<system-reminder>` 等，**用于防止观察内容被重复注入**，非 LLM 注入安全围栏。
  - **无 injection 模式 + 不可见 Unicode 扫描**：后端 `IngestionController.handleUserPrompt` 仅长度截断，无 `_scan_memory_content` 等效。TS 层 `tag-stripping.ts` 仅递归防护标签，不扫描 injection 模式或零宽字符。
  - 结论：缺口已确认，详见 [`05-ce-context-security-gap-inventory.md`](20-recommendations/05-ce-context-security-gap-inventory.md)（2026-04-23 更新）。
- [x] **辅助 LLM fallback — `/api/context/generate` 对齐确认（2026-04-24）**：`/api/context/generate` → `ContextService.generateContext()` 使用**纯 DB 查询**（observationRepository/summaryRepository），无 LLM 合成路径，完全不经过 `LlmService`。因此 **AuxiliaryClient per-task 模型 + 链式降级在 context generate 路径完全不适用**。真正受影响的是调用 `LlmService` 的路径：SummaryGeneration / MemoryRefine / StructuredExtraction / AgentService。代码证据 + 可执行行动见 [`02b-deep-dives.md`](20-recommendations/02b-deep-dives.md) §14.5。

## 定时巡检（2026-04-24 06:40 CST）

- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，最大 46922 字节（`09-supermemory-capture-lifecycle.md`），远低于 50KB 上限。无需拆分。
- [x] **上游代码同步复核（2026-04-24）**：memory 相关文件无新提交（`memory_tool.py`、`holographic/`、`session_search_tool.py` 均无变化）。`msvcrt` 跨平台修复（`5f36b42b`/`420d2709`）已在 [`08-builtin-memory-tool-bounded-snapshot.md`](60-evolution/08-builtin-memory-tool-bounded-snapshot.md) §4 记录。

## 定时巡检（2026-04-24 08:09 CST）

- [x] **文档体量再验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。

## 新增分析（2026-05-05 cron 巡检）

- [x] **MemoryManager 核心编排器深度解析（2026-05-05 新增）**：`agent/memory_manager.py`（414L，9,708 字节）完整源码 — Provider 注册模型 / 上下文围栏机制 / 工具路由 / 生命周期钩子广播 / 元数据兼容层 / 自动 hermes_home 注入 / shutdown 逆序；CE 差距：围栏缺失、Provider ABC 缺、工具路由分散 → [`65`](60-evolution/65-memory-manager-orchestrator-deep-dive.md)

- [x] **Holographic 三元存储系统（2026-05-05 新增）**：`plugins/memory/holographic/`（holographic.py + store.py + retrieval.py，6,793 字节）— HRR 相位编码 / SQLite 三表 + FTS5 触发器 / Trust Scoring / 混合检索（BM25+Jaccard+HRR 三路）；CE 差距：无多路召回、无 trust 反馈、无 entity 表 → [`66`](60-evolution/66-holographic-triple-storage-hrr-store-retrieval.md)

- [x] **文档体量验证（2026-05-05）**：全部 `.md` ≤50KB 上限。新增 2 份（`65`/`66`，共 ~13.6KB）。⚠️ 上游源码 `honcho/__init__.py` = 54,470 字节违反上限，已在 AGENT.md 预警表中记录。

## 定时巡检（2026-04-24 09:57 CST）

- [x] **上游代码增量扫描（2026-04-24 morning）**：无记忆相关新提交。最近 15 个 commit 涵盖 TUI/WebSocket (`25ba6783`)、Matrix 消息支持 (`03446e06`)、`@` 模糊文件名匹配 (`b08cbc7a`)、工具输出截断可配置 (`f2f1b3f1`)、TUI 崩溃日志 (`7baf370d`)、MCP schema 修复等，均与记忆系统无关。
  - **TUI `@` 模糊匹配**（`b08cbc7a`）：在 TUI 中对 `@<name>` 做仓库内文件名模糊搜索，与 `context_references.py` 是**不同模块**，前者是 TUI 输入增强，后者是 Prompt Builder 层上下文展开。
- [x] **`context_references.py` 集成核实**：确认已完整接入 `cli.py:7568`（同步，CLI）和 `gateway/run.py:3345`（异步，`allowed_root=MESSAGING_CWD`）。详见 [`31`](60-evolution/31-context-references-file-expansion.md)。
- [x] **Evolver E2E 文档现状评估**（`34`/`37` + `46`）：现有 E2E 文档覆盖 Solidify Pipeline（`34`，16KB）、Signal Taxonomy + Gene Selection（`37`，9.7KB）、Hub Ecosystem Integration（`46`，~12.9KB）。三篇覆盖了 Evolver 最核心的三个端到端链路。
- [ ] **`context_references.py` → CE 借鉴**：短期：增强 `IngestionController` 路径安全扫描；中期：`@file` 展开端点；长期：架构变更较大（旁路型不适合 prompt 层 `@` 注入）。详见 [`31`](60-evolution/31-context-references-file-expansion.md) §8。
- [x] **上游代码增量扫描（2026-04-24 08:09）**：本次扫描发现 2 个轻微提交（均非记忆系统核心架构）：
  - `1ace9b4d`：**`memory_setup.py` 非密钥 env var 修复**
  - `9bdfcd1b`：**OpenViking provider 搜索结果排序**
- [x] **上游核心记忆文件持续无变化**：`memory_tool.py`、`holographic/`、`session_search_tool.py`、`context_compressor.py`、`memory_manager.py` 均无新提交。
- [x] **发现侧文件 `context_references.py`（520 行）**：已存于 `agent/context_references.py`，负责 `@file`/`@folder`/`@git`/`@url` 内联上下文展开机制。
- [ ] **Evolver 端到端流程走查（下次巡检）**：HEARTBEAT 标记为本次后续任务，归属 cron `37e8c33f`（Hermes 记忆系统分析）推进。

## 定时巡检（2026-04-25 01:35 CST）

- [x] **上游代码增量扫描（origin/main vs 本地 HEAD e69526be）**：本地 HEAD 落后 origin/main ~40 个 commit，其中记忆相关重要发现：
  1. **`260ae621`**（2026-04-24）：**Session finalize hooks on expiry flush**
  2. **`a9a4416c`**（2026-04-24）：**ContextCompressor ABC 强化** — `has_content_to_compress()` + `focus_topic`
  3. **`edff2fbe`**（2026-04-24）：**Hindsight bank_id_template** — 动态 bank_id 模板
  4. **Hindsight Bug Fixes**（2026-04-24）：`f9c6c5ab`/`d6b65bbc`/`127048e6`/`a5c7422f`/`f1ba2f0c`
- [x] **文档体量验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。
- [x] **待分析：on_session_finalize hook 链路**

## 定时巡检（2026-04-25 05:27 CST）

- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，`07`（新增）8134 字节，`33`（15442）、`09`（46922）均远低于 50KB 上限。无需拆分。
- [x] **上游代码增量扫描（2026-04-25 05:27）**：origin/main 从 `6f1eed39` 前进到 `a5129c72`（~20 commits），均为 TUI/web/Dashboard/Feishu/Discord 工具拆分，**无记忆系统新功能**。Compression Eval Harness（`9f5c13f8`）仍在 `origin/design/compression-eval-harness` 分支，未合并。
- [x] **新增分析：Honcho Cadence 门控机制**（2026-04-25 新增）：`plugins/memory/honcho/__init__.py` 1253 行核心管线深度解析 → [`07-honcho-cadence-gating-mechanism.md`](50-honcho-holographic-deep/07-honcho-cadence-gating-mechanism.md)。
- [x] **Backlog 勾选更新**：全部 backlog 条目均为 `[x]` 已完成。

## 定时巡检（2026-04-25 06:21 CST）

- [x] **上游代码增量扫描**：`a5129c72..origin/main`（~30 commits）记忆相关最重要发现：`6a957a74` — **Write Origin Metadata**，向 `on_memory_write` hook 新增结构化 provenance metadata。分析文档 → [`37`](60-evolution/37-upstream-new-commits-write-origin-metadata-and-tool-call-repair.md)。
- [x] **文档体量验证**：最大文件仍为 `09`（46922 字节），无增长，无需拆分。新增 `37` ~8.7KB。
- [x] **Backlog 全部项 `[x]`**：无待跟进项。

## 定时巡检（2026-04-25 06:55 CST）

- [x] **文档体量验证**：最大 46922 字节（`09-supermemory-capture-lifecycle.md`），远低于 50KB 上限。入口文件 `hermes-memory-analysis.md` 仅 1553 字节。无需拆分。
- [x] **上游代码增量扫描（2026-04-25 06:55）**：本地 HEAD `e69526be` vs origin/main `c61547c0`（~40 commit 差距）。`e69526be` 是 origin/main 的超集（本地领先），两者间无新的记忆系统相关 commit。
- [x] **RetainDB 深度分析候选**：RetainDB Provider（766 行）尚无独立深度分析文档。
- [x] **Backlog 全部项 `[x]`**：无待跟进项。

## 定时巡检（2026-04-25 07:15 CST）

- [x] **上游代码增量扫描（`e69526be..4fade39c`）**：origin/main 从 `c61547c0` 前进了 `4fade39c`（~40 commit）。核心记忆相关三 commit：
  1. **`c630dfcd`**（2026-04-18）：**Dialectic Liveness 三机制** — stale-thread watchdog / stale-result discard / empty-streak backoff
  2. **`6ab78401`**（2026-04-20）：**session_search extra_body + max_concurrency**
  3. **`82b92777`**（2026-04-20）：**TUI /clean 重构** — memory helpers 90 LOC 重构，无行为变更
- [x] **文档体量验证**：全部 `.md` 文件字节数扫描，最大 46922 字节（`09`），远低于 50KB 上限。文档体系总计 ~785KB。
- [x] **Doc 07 更新完成**（2026-04-25 07:15）：§3.3 新增 stale-thread watchdog / §5.4 新增 prefetch 线程判活逻辑 / §5.5 新增 stale-result discard / §5.6 重命名 empty-streak backoff。
- [x] **Backlog 全部项 `[x]`**：无待跟进项。

- [x] **RetainDB Provider 深度分析（2026-04-25）**：`plugins/memory/retaindb/__init__.py`（766 行）→ [`44`](60-evolution/44-retaindb-provider-deep-dive.md)
- [x] **Supermemory + Mem0 Provider 深度分析（2026-04-25）**：8 个 Provider 中最后两个无独立分析文档 → Supermemory [`46`](60-evolution/46-supermemory-provider-deep-dive.md)（791 行）+ Mem0 [`47`](60-evolution/47-mem0-provider-deep-dive.md)（373 行）；全部 8 Provider 分析完成
- [x] **文档体量验证（2026-04-25 16:55 CST）**：59 个 .md 文件总计 ~841KB，最大 46922 字节（`09`），全部低于 50KB 上限。新增 `46`（10406 字节）+ `47`（8487 字节）。
- [x] **上游代码同步（2026-04-25 16:55 CST）**：HEAD 已同步 origin/main（`e5647d78`），无新 upstream commits。
- [x] **`agent/memory_provider.py` ABC 源码核实（2026-04-25）**：240 行 ABC，15 个方法，10 个可选 hook
- [x] **上游代码增量扫描（2026-04-27 22:03）**：`e5647d78..origin/main`（374 commits）记忆相关 2 个新发现 → [`51`](60-evolution/51-context-compressor-model-switch-and-background-review-toolset.md)：`5401a008`（ContextCompressor 模型切换 token 预算重算 bug）+ `8ad29a93`（Background review agent 显式限制 `toolsets=["memory", "skills"]` 防越权）
- [x] **文档体量验证（2026-04-27 22:03 CST）**：51 篇正文 + 入口 ~620KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `51`（~3,892 字节）。
- [x] **8 Provider 全部分析完成**：holographic / honcho / mem0 / hindsight / byterover / openviking / retaindb / supermemory — 每 Provider 至少一篇独立深度分析文档

## 定时巡检（2026-04-27 22:12 CST）

- [x] **本地 Hermes Agent Repo 恢复**：本轮重新 clone + fetch。HEAD `cec0af02` → `origin/main`（`ac0325c2`）。
- [x] **上游代码增量扫描（`cec0af02..origin/main`，267 commits）**：记忆相关 **4 个新发现** → [`52`](60-evolution/52-session-teardown-fix-cross-provider-reasoning-and-filesystem-cleanup.md)：
  1. `500774e3`（Gateway `_cleanup_agent_resources` 传 `agent._session_messages` 而非空列表；Holographic/Hindsight early-return guard 导致 restart/reset/expiry 后首 Turn 报"找不到相關的對話記錄"）
  2. `a59a98b1`（CLI exit cleanup 同 bug）
  3. `ee1a07f9`（跨 Provider reasoning leak：MiniMax→DeepSeek 时 foreign `reasoning` 被 promote 为 `reasoning_content`，导致 HTTP 400）
  4. `64a497bf`（Hindsight setup 重新运行时预填充现有配置）
  5. `3b60abb6`（`delete_session`/`prune_sessions` 删除 SQLite 记录同时清理 .json/.jsonl transcript 文件，防止 ~27MB/天磁盘增长）
- [x] **文档体量验证（2026-04-27 22:12 CST）**：52 篇正文 + 入口 ~630KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `52`（~9516 字节）。
- [x] **Backlog 全部项 `[x]`**：v8.9 完成，无待跟进项。

## 定时巡检（2026-04-25 13:59 CST）

- [x] **文档体量验证**：入口文件 `hermes-memory-analysis.md` 仅 1553 字节，远低于 50KB 上限。目录结构已合规，无需重构。
- [x] **上游代码增量扫描（`e69526be..origin/main`，54 commits）**：记忆系统相关 commit 均已在 v8.3 分析覆盖（doc 37/38/40/43）
- [x] **文档体系总计**：43 篇正文 + 入口 ~793KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v8.3 完成，无待跟进新发现。

## 定时巡检（2026-05-02 23:46 CST）

- [x] **本地 Hermes Agent Repo 恢复**：完成 fetch + checkout origin/main（`5d3be898`）。
- [x] **上游代码增量扫描（`cec0af02..origin/main`，991 commits）**：11 个记忆相关核心发现 → [`53`](60-evolution/53-session-switch-hooks-context-compressor-and-hindsight-refinements.md)：
  1. **`13683c08`**（MAJOR）：MemoryProvider ABC 新增 `on_session_switch()` 钩子
  2. **`f0dc919f`**（MAJOR）：Token 估算现在包含 system prompt + tool schemas（修复 234x 低估差距）
  3. **`b194617d`**：ContextCompressor tail protection off-by-one fix
  4. **`dad02174`**：Honcho `HonchoSessionManager._cache` RLock 线程安全修复
  5. **`0a5ee01e`**：Hindsight flush-on-switch 从 raw thread 改为 writer queue 路由
  6. **`c38dac74`**：Hindsight session switch 时 flush buffered turns + drop stale prefetch result
  7. **`0565497d`**：Hindsight 单 writer + queue 替代 per-sync daemon thread
  8. **`6ea5699e`**：Compression aux model 失败时 fallback 仍通知用户
  9. **`e553f6f3`**：Memory scrub surface 从 8 个 site 收缩到 3 个
  10. **`142b4bf3`**：session_search recent mode 改为按 `last_active` 而非 `start_time` 排序
  11. **`b29b709a`**：tool_call id 字段支持 `call_id` 优先（OpenAI Responses API 兼容）
- [x] **文档体量验证（2026-05-02 23:46 CST）**：53 篇正文 + 入口 ~850KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。新增 `53`（~19.9KB）。
- [x] **Backlog 全部项 `[x]`**：v9.0 完成，无待跟进项。

## 定时巡检（2026-05-03 00:53 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 同步，无新 commits。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。
- [x] **文档体量验证**：53 篇正文 ~850KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.1 完成，无待跟进项。

## 定时巡检（2026-05-03 01:29 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 完全同步。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：无新 upstream commits。
- [x] **文档体量验证（2026-05-03 01:29 CST）**：53 篇正文 ~917KB，最大单稿 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.1 完成，无待跟进项。

## 定时巡检（2026-05-03 02:40 CST）

- [x] **上游代码增量扫描（`5d3be898..origin/main`，39 commits）**：**0 个记忆系统相关新提交**。全部 39 个 commit 均为平台特定修复。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.2 完成，无待跟进项。

## 定时巡检（2026-05-03 04:03 CST）

- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。
- [x] **文档架构规范自检**：入口 `hermes-memory-analysis.md` 仅 1553 字节；`hermes-memory/` 53 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.3 完成，无待跟进项。

## 定时巡检（2026-05-03 05:54 CST）

- [x] **本地 Hermes Agent Repo 状态**：本地 HEAD `5d3be898` 与 origin/main 完全同步。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，0 commits）**：完全同步，无新 upstream commits。
- [x] **文档架构规范自检**：53 篇正文 ~940KB，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.4 完成，无待跟进项。

## 定时巡检（2026-05-03 21:07 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`5d3be898` → `d87fd9f0`）。
- [x] **上游代码增量扫描（`5d3be898..origin/main`，26 commits）**：**0 个记忆系统相关新提交**。2 个 gateway session 相关但非核心记忆架构：`f1e02925`（MAJOR session 行为变更：Crash/Restart 后 Session Resume 替代 Blanket Suspend）+ `93410347`（/new response 顺序修复）
- [x] **文档架构规范自检**：53 篇正文 ~940KB，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.5 完成，无待跟进项。

## 定时巡检（2026-05-03 22:54 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`d87fd9f0` → `e527240b`）。
- [x] **上游代码增量扫描（`d87fd9f0..origin/main`，5 commits，0 记忆相关）**：仅 `e527240b`(tools/write_file) / `6b4fb9f8`(cron) / `69dd0f7c`(approval) / `3c59566c`(release) / `b59bb4e3`(gateway)；无记忆相关
- [x] **文档架构规范自检**：53 篇正文 ~940KB，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.6 完成，无待跟进项。

## 定时巡检（2026-05-04 15:40 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`ac0325c25` → `8163d3719`）。
- [x] **上游代码增量扫描（`ac0325c25..origin/main`，~250 commits）**：记忆/上下文/压缩/session 相关核心发现：
  1. **`408dd8aa`**（2026-05-04 补充）：Compressor deduplication pass 对非字符串 content 安全防护 → [`55`](60-evolution/55-compressor-dedup-non-string-content-fix.md) ✅
  2. **`f1e02925`**（2026-05-03）：**Crash/Restart 后 Session Resume 替代 Blanket Suspend**
  3. **`c5b4c481`**（2026-04-29）：**Lazy Session 创建** — `defer DB row until first message`（#18370）
  4. **`93410347`**（2026-05-02）：**/new response 顺序修复**
- [x] **文档体量验证**：55 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.7 完成，无待跟进项。

## 定时巡检（2026-05-04 19:37 CST）

- [x] **本地 Hermes Agent Repo 同步**：fetch + checkout origin/main（`8163d3719` → `110387d14`）。
- [x] **上游代码增量扫描（`a11aed1ac..origin/main`，85 commits）**：3 个记忆系统相关 + 1 个工具结果存储相关 → [`59`](60-evolution/59-upstream-a11aed1ac-to-origin-main-memory-analysis.md)：
  1. **`6b88f46c5`**：Compressor timeout fallback — HTTP 408/429/502/504 及 `timeout` 字符串触发 fallback 到主模型
  2. **`e2211b268`**：`on_session_reset()` 清理 `_summary_failure_cooldown_until`
  3. **`c653f5dc3`**：session_search auxiliary model 文档澄清
  4. **`e50809b77`**：`read_file` max_result_size_chars=100K 封顶，闭合 tool_result_storage.py Layer 2 防御缺口
- [x] **文档体量验证**：56 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限。
- [x] **Backlog 全部项 `[x]`**：v9.8 完成，无待跟进项（起点：`origin/main` `110387d14`）。

---

*归档结束 — 以下为当前 backlog 主文件保留的巡检记录（2026-05-05 02:33 起）*
