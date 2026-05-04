# Compressor Pass 2 Non-String Content Fix（2026-05-04 新增）

**上游提交**：`a7417f8a4`（2026-05-04 13:01）

**相关历史**：`408dd8aa`（doc 55）— Pass 1 (dedup) 修复

---

## 发现

`a7417f8a4` — `fix(compressor): skip non-string tool content in summarization pass to prevent AttributeError`

Commit `408dd8aa`（doc 55）曾在 Pass 1（dedup）中添加了 `isinstance(content, str)` guard，但同样的模式问题存在于 Pass 2（summarization/pruning）——`content.startswith()` 和 `len()` 在非字符串 content 上调用时会导致 `AttributeError`。

当 provider 返回非字符串类型的 tool result 时（如 llama.cpp 返回的 dict 或 int），pruning pass 会崩溃。

---

## 修复内容

`agent/context_compressor.py`，在 Pass 2 的 tool result 遍历中新增 guard：

```python
# Skip multimodal content (list of content blocks)
if isinstance(content, list):
    continue
if not isinstance(content, str):   # ← 新增
    continue
if not content or content == _PRUNED_TOOL_PLACEHOLDER:
    continue
```

与 Pass 1（`408dd8aa`）保持一致。

---

## 适用范围

- **影响范围**：`ContextCompressor` Pass 2（summarization/pruning pass）
- **触发条件**：第三方 MemoryProvider 返回非字符串 tool content
- **风险等级**：低（仅特定 provider 的特定 tool result 场景触发）

---

## BlueCortexCE 借鉴

**StructuredExtractionService tool result 处理**：

Tool result 的 `content` 字段可能来自多种来源（文件读取、命令输出、结构化数据）。处理前应始终检查类型：

```java
if (content instanceof String) {
    // string-specific operations: startsWith, length, etc.
} else {
    // log type, skip or handle gracefully
}
```

避免对 `content` 字段假设特定类型，尤其是在处理来自外部工具的输出时。

---

**下次扫描起点**：`origin/main` `54e78cadb`
