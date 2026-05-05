# 55 — Compressor Dedup Pass 非字符串 Content 安全防护（2026-05-04）

**上游**：`d87fd9f0..origin/main`（35 commits）

**Commit**：`408dd8aa`（sprmn24，2026-05-04 00:57 +0300）

**相关文件**：`agent/context_compressor.py`

---

## 修复内容

Deduplication pass 中对 `content` 调用 `.encode()` 前，未校验类型，导致非字符串 content（如二进制/特殊格式）触发 `AttributeError`。

```python
# agent/context_compressor.py diff
@@ -569,6 +569,8 @@ class ContextCompressor(ContextEngine):
             # Skip multimodal content (list of content blocks)
             if isinstance(content, list):
                 continue
+            if not isinstance(content, str):
+                continue
             if len(content) < 200:
                 continue
             h = hashlib.md5(content.encode("utf-8", errors="replace")).hexdigest()[:12]
```

**修复前**：非字符串 content → 直接 `.encode()` → `AttributeError`

**修复后**：非字符串 content → `continue` 跳过 → dedup pass 正常完成

---

## 分析

| 维度 | 评估 |
|------|------|
| 影响范围 | 仅 dedup pass；不影响压缩逻辑 |
| 根因 | 多模态 tool result 场景下 content 可能出现非字符串类型（`isinstance(content, list)` 之后仍有漏网） |
| 安全性 | ✅ 防护注入攻击面：非文本 content 不会被意外 MD5 哈希化 |
| CE 对照 | Claude-Mem 场景下 tool result 的 content 字段类型更可控（POJO），但若有自定义工具返回非字符串类型，应考虑同类型 guard |

---

## CE 借鉴

BlueCortexCE 的 `StructuredExtractionService` / `ToolResultProcessor` 处理 tool result 时，应对 content 字段保持类型警觉：

```java
// CE 建议：处理 tool content 时
if (content instanceof String) {
    // process as text
} else if (content instanceof Map || content instanceof List) {
    // structured content — skip or serialize safely
} else {
    // unknown type — skip dedup/hash
}
```

---

## 体量

- **字节数**：~2,800（远低于 50KB 上限）
- **状态**：✅ 合规

## 后续跟进（2026-05-05）

提交 `a7417f8a4`（JasonOA888，2026-05-04）在 Pass 2（summarization/pruning）中应用了相同的 `isinstance(content, str)` guard，延续了 Pass 1 的修复。详见 → [`60-upstream-110387d14-to-origin-main-memory-analysis.md`](60-upstream-110387d14-to-origin-main-memory-analysis.md)
