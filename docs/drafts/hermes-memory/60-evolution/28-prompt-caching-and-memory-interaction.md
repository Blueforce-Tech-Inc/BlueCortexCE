# Prompt Caching 与记忆系统交互分析

> **来源**：Hermes Agent `agent/prompt_caching.py` + `run_agent.py` 源码实地复核  
> **文件**：`hermes-agent/agent/prompt_caching.py`（96 行）+ `run_agent.py` 行 790-791, 8265-8268, 8928-8940  
> **日期**：2026-04-24

---

## 1. 概述

Hermes 采用**三层独立优化**架构：

| 层次 | 机制 | 目标 |
|------|------|------|
| **Memory Storage** | MemoryProvider 插件（外部存储） | 跨会话语义检索 |
| **Context Compression** | ContextCompressor（内置） | 管理上下文长度 |
| **Prompt Caching** | `apply_anthropic_cache_control`（API 层） | 降低 API 成本 |

三层各司其职，互不替代。

---

## 2. Prompt Caching 实现：`system_and_3` 策略

### 2.1 源码核心

```python
# agent/prompt_caching.py

def apply_anthropic_cache_control(
    api_messages: List[Dict[str, Any]],
    cache_ttl: str = "5m",
    native_anthropic: bool = False,
) -> List[Dict[str, Any]]:
    """Apply system_and_3 caching strategy to Anthropic models.
    
    Places up to 4 cache_control breakpoints:
      1. System prompt (stable across all turns)
      2-4. Last 3 non-system messages (rolling window)
    """
    messages = copy.deepcopy(api_messages)  # 绝不修改原始
    marker = {"type": "ephemeral"}
    if cache_ttl == "1h":
        marker["ttl"] = "1h"

    breakpoints_used = 0
    if messages[0].get("role") == "system":
        _apply_cache_marker(messages[0], marker, ...)
        breakpoints_used += 1

    remaining = 4 - breakpoints_used
    non_sys = [i for i in range(len(messages)) if messages[i].get("role") != "system"]
    for idx in non_sys[-remaining:]:
        _apply_cache_marker(messages[idx], marker, ...)

    return messages
```

### 2.2 触发条件

```python
# run_agent.py:790-791
is_openrouter = "openrouter" in (base_url or "")
is_native_anthropic = api_mode == "anthropic_messages"
is_claude = "claude" in model_name.lower()
self._use_prompt_caching = is_openrouter and is_claude or is_native_anthropic
self._cache_ttl = "5m"  # Default; 可配置为 "1h"
```

自动检测：仅对 Anthropic Claude 模型（经 OpenRouter 或原生）启用。

### 2.3 调用位置（`run_agent.py:8265-8268`）

```
消息构建顺序：
  1. system prompt
  2. prefetch messages（内嵌）
  3. context compression 结果（compaction 摘要）
  4. conversation history（带 memory context 注入）
  5. ── prompt caching 标记注入（在此步）──
  6. sanitize_api_messages（孤立 tool result 清理）
  7. API 调用
```

**Prompt Caching 在 Context Compression 之后执行**，确保压缩后的消息也被缓存。

### 2.4 缓存效率追踪（行 8928-8940）

```python
if self._use_prompt_caching:
    if self.api_mode == "anthropic_messages":
        cached = response.usage.cache_read_input_tokens or 0
        written = response.usage.cache_creation_input_tokens or 0
    else:  # OpenRouter
        cached = response.usage.prompt_tokens_details.cached_tokens or 0
        written = response.usage.prompt_tokens_details.cache_write_tokens or 0
    
    hit_pct = (cached / prompt_tokens * 100) if prompt_tokens > 0 else 0
    # 日志输出: "Token usage: prompt=X, completion=Y, total=Z"
```

**关键指标**：
- `cache_read_input_tokens`：从缓存读取的 token 数
- `cache_creation_input_tokens`：写入缓存的 token 数
- `hit_pct`：缓存命中率

---

## 3. 与 Context Compression 的交互

### 3.1 文档描述（`website/docs/developer-guide/context-compression-and-caching.md`）

> "After compression, the cache is invalidated for the compressed region but the system prompt cache survives. The rolling 3-message window re-establishes caching within 1-2 turns."

### 3.2 交互机制分析

```
Turn N:    [System] [History-3] [History-2] [History-1] [Current]
           Cache:    ✓          ✓           ✓           ✓

压缩后 →  [System] [COMPACTED-Summary] [History-1] [Current]
           Cache:    ✗          ✗           ✓           ✓
           （压缩区缓存失效，但 System 缓存幸存）

Turn N+1:  [System] [COMPACTED-Summary] [History-1] [Current]
           Cache:    ✓          ✗           ✓           ✓
           （System 恢复，滚动窗口重建）

Turn N+2:  [System] [COMPACTED-Summary] [History-1] [Current]
           Cache:    ✓          ✓           ✓           ✓
           （完全恢复）
```

**结论**：压缩后缓存命中率短暂下降，但 2 轮内自动恢复。

---

## 4. 对 BlueCortexCE 的借鉴分析

### 4.1 CE 的架构差异

```
Hermes（进程内）:
  Memory Storage → Context Compression → Prompt Caching → API Call
  (全部在 Hermes 进程内，共享 api_messages 列表)

BlueCortexCE（旁路型）:
  Memory Storage → Context API (/api/context/*)
  → 客户端自行注入 → 客户端自行调用 LLM API
  (CE 不控制 API 调用层)
```

**根本差异**：Prompt Caching 是 API 调用层优化，CE 作为旁路服务无法直接实现。

### 4.2 CE 可借鉴的思路

#### 4.2.1 分层 Token 预算设计

Hermes 三层各有预算分配：

| 层 | 预算策略 |
|---|---------|
| Memory Storage | 外部存储，不占用 context |
| Context Compression | `target_tokens` 参数控制压缩强度 |
| Prompt Caching | 75% 成本节省（cache_read vs 正常 input）|

**CE 借鉴**：可设计 `ContextBudget` 配置，客户端声明每层预算：
```yaml
context:
  max_total_tokens: 160000
  compression_target: 40000  # 压缩后上限
  cache_hint: "enable"       # 提示客户端启用 prompt caching
```

#### 4.2.2 缓存命中率追踪

Hermes 在每次 API 响应后记录 `cache_read_input_tokens`。CE 的 `TokenUsageService` 可以：

```java
// 扩展 TokenUsageService
public class TokenUsageService {
    public void recordCacheStats(Long sessionId, CacheStats stats) {
        // 记录 cache_read / cache_write tokens
        // 用于分析会话活跃度和成本优化
    }
}
```

#### 4.2.3 压缩-缓存交互的提示

CE 可以通过 API 响应头或 metadata 告知客户端压缩已发生：

```json
{
  "compressionApplied": true,
  "compressedTurns": 47,
  "cacheHint": "rolling_window_recovering",
  "systemPromptTokens": 2048
}
```

让客户端知道在压缩后暂时降低 prompt caching 期望。

#### 4.2.4 System Prompt 稳定性设计

Hermes 的 prompt caching 依赖 System Prompt 在所有 turn 中保持不变（breakpoint 1）。如果 system prompt 变化，缓存完全失效。

**CE 借鉴**：CE 的 System Prompt（通过 `/api/settings` 获取）也应该尽量稳定。ContextService 的 `buildSystemPrompt` 不应在会话中频繁变化。

### 4.3 不可借鉴的部分

| Hermes 实现 | CE 无法借鉴的原因 |
|------------|-----------------|
| `apply_anthropic_cache_control()` | API 调用由客户端控制，CE 不发送 LLM 请求 |
| `cache_control` breakpoints | Anthropic Messages API 特定机制 |
| Cache TTL 配置 | 运行时 API 参数，CE 不参与 |
| Cache hit logging | 需要 API 响应中的 usage 对象，CE 无法访问 |

---

## 5. 可执行行动

| 优先级 | 行动 | 难度 |
|--------|------|------|
| **低** | 在 `TokenUsageService` 中增加 `cacheReadTokens` / `cacheWriteTokens` 字段（客户端上报） | 低 |
| **中** | 在 `/api/context/generate` 响应中增加 `compressionApplied` / `compressedTurns` metadata | 中 |
| **低** | 文档补充：CE 不支持也不需要 Prompt Caching（架构差异说明） | 低 |

---

## 6. 结论

Prompt Caching 是 Hermes 进程内三层优化架构的最外层（API 层）。由于 CE 是旁路型服务，无法直接实现 Anthropic `cache_control` 机制。但其**分层设计思想**和**压缩-缓存交互分析**对 CE 有借鉴价值：CE 应在 API 层提供足够的 metadata（压缩状态、token 预算）供客户端实现自身优化。

**核心教训**：不要在 CE 层面实现 API 调用层优化（如 prompt caching），而是提供足够信息让客户端自行优化。CE 的价值在语义层和结构化记忆层。
