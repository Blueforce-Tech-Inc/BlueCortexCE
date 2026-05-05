# BlueCortexCE — Hermes P0 借鉴差距盘点与不安全 UTF-8 威胁分析

> **日期**：2026-05-05
> **目的**：对照 [`02-bluecortexce-recommendations.md`](../02-bluecortexce-recommendations.md) 的 P0/P1 建议，逐项验证 BlueCortexCE 实际代码实现状态；记录安全缺口。
> **上游参考**：Hermes `agent/memory_manager.py` / `tools/memory_tool.py` / `agent/context_engine.py`

---

## 1. P0 差距盘点

### 1.1 内存上下文围栏（Memory Context Fence）

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| 围栏标签 | `<memory-context>` + system note | **未实现** |
| fence 逃逸防护 | `sanitize_context()` 过滤用户/检索结果中的 fence 片段 | **未实现** |
| 位置 | `agent/memory_manager.py` `build_memory_context_block()` | `ContextController` 直接拼接，无 fence |

**验证**：
```bash
grep -rni "memory-context\|fence\|围栏" backend/src/main/java --include="*.java"
# 无匹配（除了 StructuredExtractionService 中的 markdown fence 解析）
```

**风险**：用户输入的 `</memory-context>` 可伪装为普通文本，污染已注入上下文边界。

---

### 1.2 注入扫描（Injection Scanning）

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| 正则扫描 | `_scan_memory_content()` 多层 pattern（HTML/JS injection、不可见 unicode） | **仅长度截断** |
| 零宽字符检测 | ZWJ/ZWNJ/RLI/等 | **未实现** |
| RTL 覆盖检测 | `<U+202E>` 等 | **未实现** |
| 不可见 unicode | `\u200b` 等 | **未实现** |
| HTML 标签过滤 | `<script>` 等 | **未实现** |

**验证**（`IngestionController.java:237-248`）：
```java
// P1: Sanitize promptText to prevent injection
if (promptText == null) {
    promptText = "";
}
// Limit length and sanitize
if (promptText.length() > Constants.MAX_USER_PROMPT_LENGTH) {
    log.warn("Prompt text exceeded max length {}, truncating", Constants.MAX_USER_PROMPT_LENGTH);
    promptText = promptText.substring(0, Constants.MAX_USER_PROMPT_LENGTH);
}
// ⚠️ 没有正则扫描，没有不可见字符过滤
```

**风险**：用户可注入零宽字符 `\u200b\u200b\u200b` 跨observation隐写；RTL 覆盖字符 `\u202e` 可使"安全命令"显示为恶意内容。

---

### 1.3 威胁模型（综合 P0 缺口）

| 威胁 | 攻击场景 | BlueCortexCE 是否脆弱 |
|------|----------|----------------------|
| **Fence 逃逸** | 用户输入 `</memory-context>xxx` → 模型误将后续 context 当用户输入 | ✅ 脆弱 |
| **零宽字符隐写** | `Observation` 内容嵌入不可见字符，影响下游 prompt 构建 | ✅ 脆弱 |
| **RTL 覆盖注入** | 恶意文件名/命令通过 `\u202e` RTL 覆盖显示为无害内容 | ✅ 脆弱 |
| **HTML/JS 注入** | 用户 prompt 含 `<script>alert(1)</script>` → 直接存入 DB | ✅ 脆弱 |
| **Prompt 注入（memory-based）** | 用户通过长期记忆注入 system prompt 对抗性内容 | ✅ 脆弱（无围栏） |

> ⚠️ **与 Hermes doc `05-ce-context-security-gap-inventory.md` 对照**：CE 缺口盘点表中的「无围栏 + 无注入扫描」双重缺陷已由本 doc 源码级验证。

---

## 2. P1 差距盘点

### 2.1 混合搜索（Hybrid Keyword + Vector）

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| Keyword 排名 | BM25（FTS5） | `plainto_tsquery` 基础 FTS，无排名 |
| 向量搜索 | pgvector | pgvector（✅ 已实现） |
| 混合融合 | BM25 40% + Jaccard 30% + HRR 30% | **未实现** |
| 搜索策略选择 | 按查询意图自适应 | 仅手动 `?query_vector=` 参数 |

**验证**（`SearchService.java` + `ObservationRepository.java:167`）：
- FTS 使用 `plainto_tsquery('english', :query)` 无排序权重
- 无 BM25 scoring
- 向量搜索和 FTS 分别调用，结果不融合

### 2.2 Session 历史搜索（FTS → 截断 → LLM 摘要）

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| FTS 匹配 | ✅ | ❌ 无专门 endpoint |
| 父子 session 解析 | ✅ delegation chain walk | ❌ 无 |
| 截断算法 | `_truncate_around_matches()` 三级窗口 | ❌ 无 |
| LLM 摘要 | auxiliary model parallel summarization | ❌ 无 |
| 对应端点 | `tools/session_search_tool.py` | 无等价 |

### 2.3 冻结系统提示快照

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| 快照机制 | `MemoryStore._system_prompt_snapshot` session start 时捕获 | ❌ 每次 `/api/context/generate` 重新组装 |
| Prompt cache 兼容性 | frozen snapshot 保证 prefix cache 命中 | ❌ 动态组装破坏 cache |
| 影响范围 | `tools/memory_tool.py` | `ContextController.generate()` |

**验证**：grep `ContextController` 无 snapshot/frozen 相关逻辑。

### 2.4 辅助 LLM（摘要/搜索）

| 维度 | Hermes | BlueCortexCE 现状 |
|------|--------|-------------------|
| 独立模型 | auxiliary model + primary model fallback chain | ❌ 所有 LLM 调用走主模型 |
| Session 摘要 | Gemini Flash 80k 并行 | `SummaryGenerationService`（同步，非独立模型） |
| 错误处理 | circuit breaker | 无 |

---

## 3. 已实现项（对照 doc 02）

✅ **已实现**（doc 02 建议 vs 实际）：

| 建议 | 状态 | 证据 |
|------|------|------|
| PostgreSQL + pgvector | ✅ | `ObservationRepository` 已有 `fullTextSearch` + pgvector |
| source 字段 | ✅ | `ObservationEntity.source` |
| maxChars truncation | ✅ | `ContextController` ICL prompt 有 `maxChars` 截断 |
| StructuredExtractionService | ✅ | Phase 3 核心，完整实现 |
| Session userId | ✅ | `SessionEntity.userId` + `ApiRequests.userId` |
| PATCH/DELETE observation | ✅ | `MemoryController` |
| 冲突检测 | ✅ | LLM re-extraction 隐式处理 |

---

## 4. 优先修复建议（按影响/成本排序）

| 优先级 | 修复项 | 预估成本 | 理由 |
|--------|--------|----------|------|
| **P0** | **添加基础注入扫描**（正则过滤 `<`, `>`, `&`, 不可见 unicode） | 低 | 防止即时安全风险 |
| **P0** | **实现 memory-context fence 标签**（注入结果包 fence + 取出时 strip） | 低 | 防止 fence 逃逸 |
| **P1** | **添加 BM25 排名到 FTS**（`ts_rank()` 权重） | 低 | 搜索质量显著提升 |
| **P1** | **实现辅助 LLM 模型**（独立于主模型的摘要/搜索） | 中 | 成本控制 + 可用性 |
| **P1** | **实现 session 历史搜索**（FTS → 截断 → LLM 摘要） | 高 | 需要完整 new feature |
| **P2** | **实现 frozen snapshot**（session start 时捕获 system prompt） | 中 | 性能优化（prompt cache） |

---

## 5. 参考实现锚点（Hermes 源码）

| 功能 | Hermes 文件 | 关键方法/类 |
|------|------------|-------------|
| 注入扫描 | `agent/context_engine.py` `_scan_memory_content()` | 多层正则 + invisible unicode pattern |
| Fence 构建 | `agent/memory_manager.py` `build_memory_context_block()` | `<memory-context>` 标签注入 |
| Fence 逃逸防护 | `agent/memory_manager.py` `sanitize_context()` | strip fence 标签和 system note |
| BM25 排名 | `tools/session_search_tool.py` | FTS5 + `bm25()` 自定义函数 |
| Session 截断 | `tools/session_search_tool.py` `_truncate_around_matches()` | 三级窗口 phrase/proximity/term |
| 快照机制 | `tools/memory_tool.py` `MemoryStore` | `_system_prompt_snapshot` |

---

## 6. 与 doc 05（安全缺口盘点）的关联

doc `05-ce-context-security-gap-inventory.md` 列出 CE 上下文安全缺口，本 doc 提供源码级验证补充：

| 缺口 | doc 05 描述 | 本 doc 验证结果 |
|------|------------|----------------|
| 无围栏 | `</memory-context>` 可逃逸 | ✅ 源码确认无 fence |
| 无注入扫描 | 用户输入直接入库 | ✅ 仅有长度截断 |
| 无不可见字符过滤 | 零宽字符可隐写 | ✅ 源码确认无检测 |
| 无上下文隔离 | memory context 与 user context 混合 | ✅ `ContextController` 直接拼接 |

---

**下次巡检**：上游新增 memory 相关 commit 后更新本 doc；P0 修复实施后标记完成。
