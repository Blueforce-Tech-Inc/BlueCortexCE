## 36. Supermemory 轻量分类 — Regex-Based Memory Categorization（v4.2 新增）

> **文件**: `plugins/memory/supermemory/__init__.py:158-168`（`_detect_category`），`plugins/memory/supermemory/__init__.py:693`（使用点）
> **本节为 v4.2 新增**，分析 Supermemory 的轻量级记忆分类机制。

### 36.1 `_detect_category` 算法

```python
# supermemory/__init__.py:158-168
def _detect_category(text: str) -> str:
    lowered = text.lower()
    if re.search(r"prefer|like|love|hate|want", lowered):
        return "preference"
    if re.search(r"decided|will use|going with", lowered):
        return "decision"
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered):
        return "fact"
    return "other"
```

**分类规则（优先级顺序）**：

| 顺序 | 类别 | 关键词模式 | 含义 |
|------|------|-----------|------|
| 1 | `preference` | `prefer`\|`like`\|`love`\|`hate`\|`want` | 用户偏好 |
| 2 | `decision` | `decided`\|`will use`\|`going with` | 已达成决策 |
| 3 | `fact` | `\bis\b`\|`\bare\b`\|`\bhas\b`\|`\bhave\b` | 事实性陈述 |
| 4 | `other` | （默认） | 其他类型 |

**使用位置**（`__init__.py:693`）：
```python
# Supermemory 在接收外部 recall 结果时自动分类
metadata.setdefault("type", _detect_category(content))
```

### 36.2 设计权衡：Regex vs LLM

Supermemory 选择**纯正则**而非 LLM 做分类，背后的权衡：

| 方案 | 准确性 | 成本 | 延迟 | 适用场景 |
|------|--------|------|------|----------|
| Regex | 低~中（覆盖常见模式） | 零 | 极低 | 实时、大量、简单分类 |
| LLM | 高（理解语义） | 高 | 高 | 少量、复杂、需要理解 |

**Supermemory 的选择**：零成本 + 极低延迟，适合作为"快速初步分类"，后续可以有人工审核或 LLM 复核。

### 36.3 对比：Holographic 的 Category

Holographic 也支持 category，但需要**用户显式指定**：

```python
# holographic/store.py — add_fact
self._store.add_fact(content, category=category)  # category 由调用方传入
```

**Supermemory vs Holographic**：
- Supermemory：自动推断 category（无调用方负担）
- Holographic：调用方指定 category（更精确但需要主动）

### 36.4 翻译：旁路型如何借鉴

**建议**：BlueCortexCE 在 Observation 生成时，增加轻量级 category 推断：

```python
def _detect_observation_category(text: str) -> str:
    """Lightweight category detection for observations (no LLM)."""
    lowered = text.lower()
    # 偏好
    if re.search(r"\bprefer\b|\blike\b|\blove\b|\bhate\b|\bwant\b", lowered):
        return "preference"
    # 决策
    if re.search(r"\bdecided\b|\bwill use\b|\bgoing with\b", lowered):
        return "decision"
    # 问题/阻塞
    if re.search(r"\berror\b|\bfailed\b|\bblocked\b|\bissue\b", lowered):
        return "problem"
    # 事实
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered):
        return "fact"
    return "observation"
```

**优先级**：中（属于"nice to have"，不是核心功能）

---

