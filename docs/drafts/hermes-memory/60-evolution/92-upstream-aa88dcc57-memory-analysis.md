# 上游 Commit `aa88dcc57` — Memory Analysis

**Commit**: `aa88dcc57b1717cbcfb80e4eca580a3a77056702`
**Date**: 2026-05-06 11:02:50 +0530
**Author**: kshitijk4poor
**Branch**: `origin/main`
**起点**: `735349c67`

---

## 📋 变更概览

| 文件 | 变更类型 | 重要性 |
|------|----------|--------|
| `agent/context_compressor.py` | 提示词修订 + 指令强化 | ⭐ P1 |
| `agent/memory_manager.py` | 权威性语言升级 + 正则兼容 | ⭐ P1 |
| `gateway/run.py` | 压缩后 Agent 缓存清除 | ⭐⭐⭐ P0 |
| `scripts/release.py` | AUTHOR_MAP 更新 | — |

**依据 3 个 PR**: #20027 (LeonSGP43) · #18767 (MacroAnarchy) · #17380 (vominh1919)

---

## 1. Agent 缓存清除 — `gateway/run.py` ⭐⭐⭐ P0

### 问题背景

压缩（compaction）操作后，`gateway/run.py` 的压缩逻辑会创建一个临时 agent（`tmp_agent`）来执行压缩任务。压缩完成后：

- 临时 agent 被清理（`_cleanup_agent_resources`）
- **但cached agent（`self._cached_agents[session_key]`）从未被清除**
- 下一次请求时，同一个 cached agent 被复用
- 该 cached agent 的 system prompt 是在压缩**之前**构建的，包含**旧的** SOUL.md / memory / skills 状态

### 影响范围

两个注入点（均已修复）：

```python
# Point 1: Session hygiene 之后（_cleanup_agent_resources 调用之后）
finally:
    self._evict_cached_agent(session_key)    # ← 新增
    self._cleanup_agent_resources(_hyg_agent)

# Point 2: /compress 压缩之后（tmp_agent 使用完之后）
finally:
    self._evict_cached_agent(session_key)    # ← 新增
    self._cleanup_agent_resources(tmp_agent)
```

### 修复内容

```python
# 新增：在清理临时 agent 之后，清除 session 缓存
self._evict_cached_agent(session_key)
```

### BlueCortexCE 借鉴 ⭐⭐⭐

**这是 CE 目前缺失的关键功能。** `ContextService` 目前没有缓存 agent 实例（CE 是无状态请求/响应模型），但等价的风险是：

> 压缩后，`renderTimeline()` 或 `/api/context/generate` 返回的历史摘要（包含旧 memory 状态）如果被缓存或重用，模型会用旧的 memory 上下文响应。

CE 应在以下场景强制刷新 context：
1. 压缩完成后（`POST /api/compress` 返回后）
2. Memory 写入后（`POST /api/memory` / `ObservationService` 写入后）
3. Session 切换后（`on_session_switch` hook 触发后）

实现建议：在 `ContextService` 中引入 `contextVersion` 或 `lastMemoryUpdateTs`，在 `/api/context` 响应中暴露，Gateway/Proxy 层据此决定是否强制刷新。

---

## 2. Memory 权威性升级 — `agent/memory_manager.py` + `agent/context_compressor.py` ⭐⭐⭐ P1

### `memory_manager.py`: `build_memory_context_block()` 修订

```python
# 旧：
"[System note: The following is recalled memory context, "
"NOT new user input. Treat as informational background data.]\n\n"

# 新：
"[System note: The following is recalled memory context, "
"NOT new user input. Treat as authoritative reference data — "
"this is the agent's persistent memory and should inform all responses.]\n\n"
```

**从 "informational background data" → "authoritative reference data"**

语义从"仅供参考的背景信息"升级为"权威参考数据，应指导所有响应"。这是一个提示词层面的权威性强化。

### `context_compressor.py`: `SUMMARY_PREFIX` 强化

```python
# 在 SUMMARY_PREFIX 末尾新增：
"IMPORTANT: Your persistent memory (MEMORY.md, USER.md) in the system "
"prompt is ALWAYS authoritative and active — never ignore or deprioritize "
"memory content due to this compaction note. "
```

**在压缩摘要提示词中，明确告知模型"persistent memory 永远权威，不因压缩备注而被降权"。**

### `context_compressor.py`: `_compression_note` 更新

```python
# 旧：
"[Note: Some earlier conversation turns have been compacted... "
"...rather than re-doing work.]"

# 新：
"[Note: Some earlier conversation turns have been compacted... "
"...rather than re-doing work. Your persistent memory (MEMORY.md, USER.md) "
"remains fully authoritative regardless of compaction.]"
```

### `_INTERNAL_NOTE_RE` 正则兼容性

```python
# 旧：
r'\[System note:\s*The following is recalled memory context,\s*NOT new user input\.\s*Treat as informational background data\.\]\s*'

# 新：
r'\[System note:\s*The following is recalled memory context,\s*NOT new user input\.\s*Treat as (?:informational background data|authoritative reference data[^\]]*)\.\]\s*'
```

**向后兼容**：旧 session 的 system prompt 使用 "informational background data"，新系统使用 "authoritative reference data"，`_INTERNAL_NOTE_RE` 均能正确匹配和清理。

### BlueCortexCE 借鉴 ⭐⭐

**CE 的 `ContextService.renderTimeline()` 输出应增加权威性声明。**

建议在 `## Memory` section header 之后加入：

```
[System note: The following is your persistent memory context. 
Treat as authoritative — it reflects your accumulated knowledge 
about this project and should inform all responses.]
```

且在压缩摘要生成提示词中，增加：
```
IMPORTANT: Your persistent memory is ALWAYS authoritative.
```

---

## 3. `/compact` → `/compress` 修正

`agent/context_compressor.py` 中注释和提示词文本将 `/compact` 修正为 `/compress`（issue #20020 修复）。

### BlueCortexCE 借鉴

CE 目前使用 `/api/compress` 端点，命名正确。但需注意：文档和提示词中的命令名称需与实际 API 端点保持一致。

---

## 4. 三方 PR 来源分析

| PR | 作者 | 内容 |
|----|------|------|
| #20027 | LeonSGP43 | Cache eviction after compression |
| #18767 | MacroAnarchy | Memory authority upgrade |
| #17380 | vominh1919 | /compress guidance fix |

---

## 总结

| 编号 | 发现 | 级别 | CE 行动 |
|------|------|------|---------|
| 1 | 压缩后未清除 cached agent → 旧 memory 持续生效 | P0 | 在压缩/Memory写入后强制 context 刷新机制 |
| 2 | Memory 从 "background data" 升级为 "authoritative reference" | P1 | CE context 输出增加权威性声明 |
| 3 | 压缩备注未提及 persistent memory 权威性 | P1 | 压缩摘要生成提示词增加权威性声明 |
| 4 | `_INTERNAL_NOTE_RE` 向后兼容新旧 authority 措辞 | P2 | CE 跨版本兼容处理参考 |

---

## CE 相关现有文档

- 压缩上下文: [`17-smart-compression-and-exhaustion-fix.md`](17-smart-compression-and-exhaustion-fix.md)
- ContextEngine 可插拔: [`27-context-engine-pluggable-architecture.md`](27-context-engine-pluggable-architecture.md)
- Context Summary End Marker: [`85-hermes-context-summary-end-marker-and-iterative-continuity.md`](85-hermes-context-summary-end-marker-and-iterative-continuity.md)
- CE Gap Inventory P0: [`../20-recommendations/05-ce-context-security-gap-inventory.md`](../20-recommendations/05-ce-context-security-gap-inventory.md)

**CE 代码锚点**:
- `ContextService.renderTimeline()` — memory 输出位置
- `ContextService.generate()` — context 刷新控制点
