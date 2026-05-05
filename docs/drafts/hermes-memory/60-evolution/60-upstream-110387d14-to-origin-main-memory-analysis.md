# 上游新提交分析（110387d14 → origin/main，89 commits，2026-05-05 新增）

**扫描区间**：`110387d14`（docs(open-webui): fill gaps in quick setup #19654）→ `origin/main`（`e493b1c48` docs(skill): add hyperframes inspect command）

**范围**：89 个新提交，其中 **3 个记忆系统相关** + 1 个上下文管理相关。

---

## 1. Compressor Pass 2 非字符串 Content 安全防护（⭐ P2）

**提交**：`a7417f8a4`（JasonOA888，2026-05-04）

**文件**：`agent/context_compressor.py`（+2 行）

### 问题

提交 `408dd8aa`（doc 55 分析）曾在 Pass 1（dedup）中增加了 `isinstance(content, str)` guard，防止非字符串 content（如 llama.cpp 返回的 dict/int）导致 `.encode()` AttributeError。

但 Pass 2（summarization/pruning）存在**相同模式**：

```python
# Pass 2 原有代码（崩溃路径）
if not content or content == _PRUNED_TOOL_PLACEHOLDER:
    continue
if content.startswith(...)  # AttributeError if content is dict
```

### 修复

扩展 guard 到 Pass 2：

```python
if isinstance(content, list):
    continue          # 已有多模态保护
if not isinstance(content, str):  # 新增
    continue
if not content or content == _PRUNED_TOOL_PLACEHOLDER:
    continue
```

### CE 借鉴

**StructuredExtractionService** 的 tool result processor 应在访问 `.encode()` / `.startswith()` / `len()` 前始终校验 `isinstance(content, str)`。非字符串 content 可能来自：
- 本地模型（llama.cpp 等）返回 dict/int
- 多模态 tool result 的中间处理状态
- Provider 写入的未归一化数据

---

## 2. `_prune_old_tool_results` 边界方向修复（⭐ P2）

**提交**：`b7bbc6250`（swithek，2026-04-26）

**文件**：`agent/context_compressor.py`（+11 行，+41 行测试）

### 问题

原代码将 token budget 预算直接转换为 index-space 的 prune boundary，但 `max(boundary, len(result) - min_protect)` 的方向在 budget-space 和 count-space 之间被混淆：

```python
# 原代码（有 bug）
prune_boundary = max(boundary, len(result) - min_protect)
```

- `boundary`：从消息开头向后累加 token，**第一个超出预算的消息**的索引
- `len(result) - min_protect`：至少保留 `min_protect` 条消息的 index

**Bug**：当 budget 较大时，`boundary` 也会较大（向后推进更多），但 `max()` 总是选较大值——实际效果是**慷慨的 budget 被静默截断回 `min_protect`**。

### 修复

将问题转换为 count-space，在 count-space 中执行 floor，再转回 index-space：

```python
# 正确实现
budget_protect_count = len(result) - boundary  # 预算保护了多少条消息
protected_count = max(budget_protect_count, min_protect)  # 至少保护 min_protect 条
prune_boundary = len(result) - protected_count  # 转换回 index-space
```

### 测试覆盖

41 行新测试覆盖：
- 正常 budget 足够保护所有消息
- Budget 刚好不够（部分保护）
- Budget 严重不足（仅保护 tail）

### CE 借鉴

**StructuredExtractionService** 的 token budget 分配逻辑（如 `MAX_EXTRACTION_TOKENS`）应使用相同的 count-space 转换模式，避免 budget 被静默截断。

---

## 3. Session Search 报告来源从 FTS5 子 Session 修正为已解析父 Session（⭐ P2）

**提交**：`6b4ccb9b1`（briandevans，2026-04-25）

**文件**：`tools/session_search_tool.py`（+11/-4 行，+62 行测试）

### 问题

委托子 session（如 `source='telegram'`）包含 FTS5 命中，但 `_resolve_to_parent()` 将其映射到不同的根 session（`source='api_server'`）。原代码在循环中丢弃了 `session_meta` 作为 `_` 并回退到 `match_info.get('source')`，导致结果仍报告子 session 的 source。

### 修复

使用已解析父 session 的 `session_meta` 而非 `match_info` 的 fallback：

```python
# 修复后
entry = {
    "session_id": session_id,
    "when": _format_timestamp(
        session_meta.get("started_at") or match_info.get("session_started")
    ),
    "source": session_meta.get("source") or match_info.get("source", "unknown"),
    "model": session_meta.get("model") or match_info.get("model"),
}
```

### CE 借鉴

**BlueCortexCE** 的 `/api/memory/search` 如果支持跨 session 搜索，应注意返回的 session 元信息应来自**根 session**而非中间 child session。

---

## 4. Error Classifier 大上下文假溢出修复（非直接记忆）

**提交**：`d29f90e89`（Dejie Guo，2026-04-27）

**文件**：`agent/error_classifier.py`（+14/-4 行，+32 行测试）

### 修复内容

Generic 400 和 server-disconnect 启发式使用**绝对 token/消息数**作为后备判断，但这些绝对阈值对 1M context session 过于激进：

```python
# 修复后：绝对阈值仅在 ≤256K context 窗口生效
is_large = approx_tokens > context_length * 0.6 or (
    context_length <= 256000 and (approx_tokens > 120000 or num_messages > 200)
)
```

### 与记忆系统关系

错误分类器帮助判断何时触发压缩，但这是上下文管理逻辑，非记忆系统核心。文档内仅记录，不独立成篇。

---

## 非记忆相关（89 提交中其余 86 个）

涵盖：Telegram topic mode 多 session / Gateway PATH / 模型 ID 规范化 / Cron job 并发回归测试 / `IterationBudget.used` 锁获取 / `HINDSIGHT_TIMEOUT` 配置 / `hermes status` 显示自定义 provider API key 等。

---

## 下次扫描起点

`origin/main` `e493b1c48`（docs(skill): add hyperframes inspect command，2026-05-03）

---

*文档：hermes-memory/60-evolution/60-upstream-110387d14-to-origin-main-memory-analysis.md | 2026-05-05 05:14 CST*
