# CE 开发者速查卡 — Hermes 记忆系统 Top-10 落地借鉴

> **用途**：CE 开发过程中快速查阅「Hermes 怎么做 → CE 对应实现」。
> **来源**：77 篇 `hermes-memory/` 分析文档。
> **限制**：每项不超过 8 行，详细分析见对应链接。

---

## 1. P0：注入攻击防护（Hermes: `_scan_context_content` + `sanitize_context`）

**Hermes 做法**：
- `prompt_builder.py` `_scan_context_content()` 检测 10 类威胁：HTML/SQL/JS 注入、正则注入、路径遍历、shell 注入、prompt 注入（`<memory-context>` 围栏逃逸）、不可见 Unicode（零宽字符、RLO、RTL覆盖）
- `memory_manager.py` `sanitize_context()` 在 `</memory-context>` 后截断，防止围栏逃逸
- `context_engine.py` `_scan_memory_content()` 用于 memory 文件扫描

**CE 现状**：❌ 无注入扫描，仅长度截断

**CE 落地**：
- 创建 `ContextSecurityService`：`scanContextContent(text, rules)` 返回 `SecurityScanResult{hasInjection, type, position}`
- 在 `ContextService.generate()` 组装 context 字符串后调用扫描
- 规则库：10 类 threat patterns，正则 + 不可见 Unicode 检测
- **锚点**：[`76`](60-evolution/76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md) P0 级分析 | [`77`](77-prompt-builder-context-injection-architecture.md) 源码级解析

---

## 2. P0：Memory Context 围栏（Fence）

**Hermes 做法**：
- `<memory-context>` / `</memory-context>` 标签包裹注入记忆
- `sanitize_context()` 在围栏关闭标签后截断（防止注入内容逃逸）
- 围栏标签本身也经过 injection scan

**CE 现状**：❌ 无围栏标签，记忆直接拼接在 system prompt 中

**CE 落地**：
- 在 `ContextService.generate()` 中用 `<!-- memory-context -->` / `<!-- /memory-context -->` 包裹记忆部分
- 扫描围栏标签是否闭合，检测逃逸尝试
- **锚点**：[`76`](60-evolution/76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md)

---

## 3. P1：会话历史搜索（Hones: FTS5 + LLM Summarization）

**Hermes 做法**：
- `session_search_tool.py`：空查询 → recent sessions（FTS5 `last_active DESC`）；有查询 → BM25 全文检索
- 匹配结果按 position-aware 窗口截断（每 match ±2 turns），避免 blob 淹没上下文
- 并行 LLM summarization（semaphore 控制并发），auxiliary model 降级
- 多跳委托链解析（delegation chain → 最终 session）

**CE 现状**：❌ 无 session 历史搜索；`GET /api/search` 仅支持向量语义搜索

**CE 落地**：
- 在 `SessionRepository` 添加 `findRecentSessions(userId, limit)` 和 `searchByKeyword(keyword, userId, limit)` 方法
- PostgreSQL FTS（`to_tsvector('english', content)`）实现全文搜索
- 搜索结果经 LLM summarization 再注入上下文
- **锚点**：[`21`](60-evolution/21-session-search-tool.md)

---

## 4. P1：Tool Result 分层持久化（Hermes: 3-Layer Defense）

**Hermes 做法**：
- Layer 1：`ToolResultStorage` per-tool self-truncation（`max_length` 字段）
- Layer 2：`maybe_persist_tool_result()` sandboxed persist（`~/.hermes/tool_results/`）
- Layer 3：`enforce_turn_budget()` per-turn 200K token 预算，超出则 `read_file` pinned at `inf` 截断
- `tool_result_storage.py` + `budget_config.py`

**CE 现状**：⚠️ `maxChars` truncation 存在但不完整

**CE 落地**：
- `StructuredExtractionService` 处理 tool results 时应用 `maxChars` 截断
- 考虑 tool results 持久化到磁盘（`/tmp/cortex-ce/tool-results/`）
- 审计 `EmbeddingService.processRawContent()` 的 content 处理路径
- **锚点**：[`20`](60-evolution/20-tool-result-persistence.md)

---

## 5. P1：ContextCompressor 可插拔架构（Hermes: ContextEngine ABC）

**Hermes 做法**：
- `ContextEngine` ABC（`has_content_to_compress`, `compress`, `focus_topic`）
- 插件发现：`fec7b222` 实现 4 层优先级选择（built-in → plugin → custom → disabled）
- `context_compressor.py` 作为默认实现
- `CompressorConfig` 可配置启用/禁用/自定义策略

**CE 现状**：❌ 无可插拔压缩架构，StructuredExtractionService 功能单一

**CE 落地**：
- 定义 `ContextCompressor` interface/contract
- Phase 3 后的压缩层演进：extraction 结果作为压缩输入
- **锚点**：[`27`](60-evolution/27-context-engine-pluggable-architecture.md)

---

## 6. P1：Auxiliary LLM 模型降级（Hermes: AuxiliaryClient + Fallback Chain）

**Hermes 做法**：
- `AuxiliaryClient` 7-Provider 自动检测链（OpenAI → Azure → Anthropic → ... → 报错）
- 每个 provider 有 `max_tokens` / `timeout` 配置
- `SessionSearchTool` 用 auxiliary model 做 summarization，主模型做决策
- Compression timeout → fallback 到主模型（`6b88f46c5`）

**CE 现状**：❌ 所有 LLM 调用共用主模型

**CE 落地**：
- `LlmService` 支持 auxiliary 模型配置（用于 extraction/summarization）
- Structured extraction 使用 auxiliary 模型降级链
- **锚点**：[`23`](60-evolution/23-auxiliary-client-resolution-chain.md)

---

## 7. P2：Per-User Memory Scoping（Hermes: c52e5931）

**Hermes 做法**：
- Gateway `user_id` 透传到 MemoryProvider（多层传递链）
- `on_memory_write(user_id=...)` 写入时标记用户
- 检索时 `per_user=True` 过滤

**CE 现状**：⚠️ SessionEntity 有 `userId` 字段，但记忆检索未使用

**CE 落地**：
- `ObservationEntity` 添加 `userId` 字段
- `SearchService.search()` 支持 `userId` 过滤参数
- **锚点**：[`43-on-memory-write-bridge-and-per-user-scoping.md`](60-evolution/43-on-memory-write-bridge-and-per-user-scoping.md)

---

## 8. P2：Secrets Redaction 三层防御（Hermes: 1f804d17 + fcae077d）

**Hermes 做法**：
- Input redaction：压缩前扫描并替换 secrets（JWT/URL params/form bodies/Discord mentions）
- Prompt 禁止：禁止 secrets 出现在 `MEMORY_GUIDANCE` 等 guidance 文本中
- Output redaction：压缩结果再扫描，防止 secrets 泄露

**CE 现状**：❌ `extractedData` JSONB 字段无 redaction（已在 [`39`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md) 标注）

**CE 落地**：
- StructuredExtractionService 输出前调用 redaction 扫描
- 使用正则库检测：Bearer token、API key patterns、URL credentials
- **锚点**：[`39`](60-evolution/39-session-auto-prune-secrets-redaction-and-bugfixes.md)

---

## 9. P2：Frozen System Prompt Snapshot（Hermes: MemoryStore Snapshot）

**Hermes 做法**：
- `MemoryStore` 在 session 开始时 snapshot system prompt（`_system_prompt_snapshot`）
- 压缩后使用 snapshot 而非 live rebuild，避免 prompt cache 失效
- 快照与 live entries 分离管理

**CE 现状**：❌ 每次 `context/generate` 重新组装 system prompt + memories

**CE 落地**：
- Session 启动时计算 `systemPromptHash`，仅在 hash 变化时重建
- 快照结果缓存，避免重复 token 消耗
- **锚点**：[`08`](60-evolution/08-builtin-memory-tool-bounded-snapshot.md)

---

## 10. P2：Compression Eval Harness（Hermes: 1e6285c5）

**Hermes 做法**：
- `scripts/compression_eval/` probe-based 质量评估框架
- 两阶段：Continuation（连续性）+ Grading（6 维度 rubric）
- Fixture scrub pipeline（redaction + paraphrase + truncate）
- 6 维度：accuracy / context_awareness / artifact_trail / completeness / continuity / instruction_following

**CE 现状**：❌ Structured extraction 无自动化质量评估

**CE 落地**：
- Phase 3 acceptance test 补充 extraction 质量维度
- Session observations 作为 fixture，extraction output 作为 answer
- 迁移 Hermes rubric：accuracy / context_awareness / completeness
- **锚点**：[`49`](60-evolution/49-compression-eval-harness-and-structured-extraction-quality.md)

---

## 快速跳转索引

| 主题 | 文档 |
|------|------|
| MemoryManager 编排器 | [`65`](60-evolution/65-memory-manager-orchestrator-deep-dive.md) |
| MemoryProvider Hooks 完整清单 | [`06`](60-evolution/06-memory-provider-hooks-inventory.md) |
| ContextCompressor 完整算法 | [`24`](60-evolution/24-context-compressor-full-algorithm.md) |
| Holographic HRR 存储 | [`66`](60-evolution/66-holographic-triple-storage-hrr-store-retrieval.md) |
| Honcho Cadence 门控 | [`50-honcho-holographic-deep/07-honcho-cadence-gating-mechanism.md`](50-honcho-holographic-deep/07-honcho-cadence-gating-mechanism.md) |
| Session Teardown 真实历史传递 | [`52`](60-evolution/52-session-teardown-fix-cross-provider-reasoning-and-filesystem-cleanup.md) |
| InsightsEngine 会话分析 | [`71`](60-evolution/71-insights-engine-session-analytics-deep-dive.md) |
| Prompt Builder 架构 | [`77`](77-prompt-builder-context-injection-architecture.md) |
| P0/P1 差距完整清单 | [`76`](60-evolution/76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md) |
| 跨-cutting 架构模式（11 项） | [`78`](78-cross-cutting-architectural-patterns-synthesis.md) |
| 上游最新：0 新记忆 commit（2026-05-05） | [`68`](60-evolution/68-upstream-zero-memory-commits-telegram-topic-mode.md) |

---

**维护**：本卡随上游 Hermes 新发现更新。上次更新：2026-05-05（v1，基于 77 篇分析文档）。
