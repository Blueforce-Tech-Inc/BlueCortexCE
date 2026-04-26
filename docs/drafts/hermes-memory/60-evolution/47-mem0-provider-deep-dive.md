# Mem0 Provider 深度解析（2026-04-25）

**源码**：`plugins/memory/mem0/__init__.py`（373 行）  
**架构定位**：云端 LLM 提取平台（Mem0 Platform API），server-side fact extraction vs local embedding。

---

## 1. 核心架构：Server-Side Extraction

Mem0 Provider 与 Supermemory 的根本区别在于**提取职责归属**：

| 架构 | 提取位置 | 代表 Provider |
|------|----------|---------------|
| **Server-side extraction** | 云端 LLM 完成事实抽取，本地只存储结构化 facts | Mem0 |
| **Local extraction + cloud storage** | 本地做初步过滤，云端 SDK 调用外部 API | Supermemory |
| **Local embedding + local storage** | 本地 embedding 模型 + 向量 DB | Holographic, Hindsight |

```python
# Mem0 的写入：消息直接发给 Mem0 Platform，由其 LLM 做 extraction
def _sync(self):
    client.add(messages, **self._write_filters())  # 无 extraction prompt 参数

# Mem0 的显式存储：infer=False 跳过 LLM extraction，直接存储
client.add([{"role": "user", "content": conclusion}], infer=False, ...)
```

---

## 2. Circuit Breaker 弹性模式（§2）

```python
_BREAKER_THRESHOLD = 5         # 连续 5 次失败
_BREAKER_COOLDOWN_SECS = 120  # 120s 熔断窗口

def _is_breaker_open(self) -> bool:
    if self._consecutive_failures < _BREAKER_THRESHOLD:
        return False
    if time.monotonic() >= self._breaker_open_until:
        # 冷却期结束，reset 并允许重试
        self._consecutive_failures = 0
        return False
    return True

def _record_failure(self):
    self._consecutive_failures += 1
    if self._consecutive_failures >= _BREAKER_THRESHOLD:
        self._breaker_open_until = time.monotonic() + _BREAKER_COOLDOWN_SECS
        logger.warning("Mem0 circuit breaker tripped...")

def _record_success(self):
    self._consecutive_failures = 0
```

**影响范围**：所有 Mem0 API 调用（prefetch / sync / profile / search / conclude）均受 circuit breaker 保护。

**CE 借鉴（高优先级）**：Claude-Mem 目前所有 LLM 调用（SummaryGeneration / MemoryRefine / StructuredExtraction）无熔断机制，API 失败时静默 `LlmResponse.empty()`，可能导致 session 状态不一致。建议：
```java
// LlmService.java 新增 circuit breaker
private final CircuitBreaker llmBreaker = CircuitBreaker.of("llm",
    CircuitBreakerConfig.of(e -> e.failureRateThreshold(50)
        .slidingWindowSize(10)
        .minimumNumberOfCalls(5)
        .waitDurationInOpenState(Duration.ofSeconds(120))));
```

---

## 3. Per-User + Per-Agent 过滤双层（§3）

```python
def _read_filters(self) -> Dict[str, Any]:
    """搜索时只按 user_id 过滤（跨 agent 共享记忆）"""
    return {"user_id": self._user_id}

def _write_filters(self) -> Dict[str, Any]:
    """写入时按 user_id + agent_id 双重过滤（记录来源）"""
    return {"user_id": self._user_id, "agent_id": self._agent_id}

# initialize() 中 gateway 可覆盖 user_id
self._user_id = kwargs.get("user_id") or self._config.get("user_id", "hermes-user")
```

**设计意图**：
- `user_id` 粒度：同一用户在不同 agent 间共享记忆
- `agent_id` 粒度：记录记忆来源（哪个 agent 创建的），但不限制读取

**CE 差距**：Claude-Mem 的 session-observation 映射只有 session_id，无 user_id / agent_id 字段。若 Gateway 支持多用户，无法实现 per-user memory isolation。

---

## 4. Reranking 分级搜索（§4）

```python
SEARCH_SCHEMA = {
    "parameters": {
        "properties": {
            "rerank": {"type": "boolean", "description": "Enable reranking for precision"},
            "top_k": {"type": "integer", "description": "Max results (default: 10, max: 50)"},
        }
    }
}

# profile（快速概览）：无 rerank
memories = self._unwrap_results(client.get_all(filters=self._read_filters()))

# search（精确检索）：可选 rerank
results = self._unwrap_results(client.search(
    query=query, filters=self._read_filters(),
    rerank=self._rerank, top_k=top_k
))
```

**设计意图**：
- `get_all` / `profile`：快速全量拉取，无 rerank
- `search`：按需启用 rerank（accuracy vs speed tradeoff）

**CE 借鉴**：`SearchService.semanticSearch()` 目前只有单一 search path，无 reranking 支持。若引入 rerank model（交叉编码器），可参照此设计：
- 默认 path：`embedding similarity top-K`（快速）
- 高精度 path：`embedding top-K → rerank model → final top-K`

---

## 5. `infer=False` — 显式存储跳过提取（§5）

```python
elif tool_name == "mem0_conclude":
    conclusion = args.get("conclusion", "")
    client.add(
        [{"role": "user", "content": conclusion}],
        **self._write_filters(),
        infer=False,  # 不经 LLM extraction，直接存储
    )
```

**`mem0_conclude` 工具**：显式记忆存储接口，存储 verbatim（不经过 LLM 事实提取）。用于：
- 用户主动声明的偏好/决定（"我更喜欢用 Python"）
- 用户纠正错误信息（"我其实不喝酒"）
- 重要决策记录

**CE 差距**：Claude-Mem 无等效的"跳过 extraction 直接存储"接口，所有 observation 都经过 LLM summarization/generation。对于用户显式声明的偏好，目前需要通过 observation + summarization pipeline，额外一次 LLM 调用。

**Phase 3 关联**：StructuredExtraction 的 `TemplateConfig` 中可增加 `skip_llm_extraction` 选项，接收原始 user input 直接存储（对应 Hermes 的 `infer=False`）。

---

## 6. Thread-Safe 懒初始化 Client（§6）

```python
self._client_lock = threading.Lock()

def _get_client(self):
    with self._client_lock:
        if self._client is not None:
            return self._client
        from mem0 import MemoryClient
        self._client = MemoryClient(api_key=self._api_key)
        return self._client
```

**模式**：Double-checked locking（双重检查锁定），避免每次调用都加锁，同时保证只初始化一次。

**CE 借鉴**：`LlmService` 或 `EmbeddingService` 的 client 懒初始化可参照此模式。

---

## 7. API Response 归一化（§7）

```python
@staticmethod
def _unwrap_results(response: Any) -> list:
    """Normalize Mem0 API response — v2 wraps results in {"results": [...]}."""
    if isinstance(response, dict):
        return response.get("results", [])
    if isinstance(response, list):
        return response
    return []
```

**背景**：Mem0 API 可能在 v1/v2 版本间变化，response 格式不一致。本方法兼容两种格式，避免版本升级导致 breaking change。

**CE 借鉴**：第三方 API response 归一化（如 OpenAI API 错误格式变化）可参照此模式，统一 error handling。

---

## 8. Supermemory vs Mem0 对比

| 维度 | Supermemory | Mem0 |
|------|-------------|------|
| 提取方式 | 云端 SDK + entity_context 提示词 | Server-side LLM extraction |
| 容错 | 无 circuit breaker | 5-failure 熔断 120s |
| 存储模型 | documents.add + conversations ingest | unified memory store |
| 工具 | store/search/forget/profile + multi-container | profile/search/conclude |
| 过滤维度 | user_id + agent_id | user_id + agent_id |
| 批量摄入 | on_session_end HTTP POST | 无独立 session-end batch |
| 成本控制 | profile_frequency 节流 | rerank 可选 |
| 本地依赖 | supermemory Python SDK | mem0ai Python SDK |

---

## 9. CE 可执行借鉴优先级

| 优先级 | 借鉴项 | 工作量 | 难度 |
|--------|--------|--------|------|
| 🔴 高 | Circuit breaker for LLM calls（5-failure, 120s cooldown） | 中 | 中 |
| 🔴 高 | `infer=False` 显式存储路径（跳过 extraction） | 低 | 低 |
| 🟡 中 | Per-user + per-agent filtering | 中 | 中 |
| 🟡 中 | Reranking 分级搜索 | 高 | 高 |
| 🟢 低 | API response 归一化 | 低 | 低 |
| 🟢 低 | Thread-safe 懒初始化 | 低 | 低 |

---

## 10. CE 当前实现差距

**已确认差距**：

1. **无 circuit breaker**：所有 LLM 调用静默失败，无熔断重试
2. **无 per-user scoping**：session 粒度，无 user_id 维度的数据隔离
3. **无显式存储跳过路径**：所有 observation 都经过 LLM summarization
4. **无 reranking 支持**：只有 embedding similarity，无 cross-encoder rerank
5. **API response 无归一化层**：OpenAI API 错误直接透传，无版本兼容

---

*文档创建：2026-04-25 16:52 CST；源码行数：373；CE 差距分析已覆盖。*
