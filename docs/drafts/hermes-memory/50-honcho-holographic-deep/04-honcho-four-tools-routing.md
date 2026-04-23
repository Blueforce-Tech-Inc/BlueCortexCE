
## 20. Honcho 四工具完整路由分析（v3.7 新增）

> **文件**: `plugins/memory/honcho/__init__.py:1-450`（完整 provider 实现）
> **本节为 v3.7 新增**，详细分析 Honcho 的四个工具的路由逻辑和底层实现。

### 20.1 四工具概览与定位

| 工具 | 函数 | LLM 成本 | 延迟 | 用途 |
|------|------|---------|------|------|
| `honcho_profile` | `get_peer_card()` | **无** | 极低 | 快速获取用户关键事实（curated facts） |
| `honcho_search` | `search_context()` | **无** | 低 | 语义搜索 raw excerpts，无 LLM 合成 |
| `honcho_context` | `dialectic_query()` | **有**（Honcho LLM） | 高 | 自然语言 Q&A，LLM 合成答案 |
| `honcho_conclude` | `create_conclusion()` | **有**（Honcho LLM） | 高 | 写回持久化结论到用户 profile |

**关键洞察**：Honcho 四工具的设计体现了**成本-效益梯度**：
- 低成本工具（profile/search）：快速、廉价、无 LLM
- 高成本工具（context/conclude）：LLM 推理、语义理解、跨记忆综合

### 20.2 honcho_profile — 零成本 curated facts 检索

```python
# plugins/memory/honcho/__init__.py:360-368
elif tool_name == "honcho_profile":
    card = self._manager.get_peer_card(self._session_key)
    if not card:
        return json.dumps({"result": "No profile facts available yet."})
    return json.dumps({"result": card})
```

**底层实现**（`HonchoSessionManager.get_peer_card`）：
```python
# plugins/memory/honcho/session.py:580-595
def get_peer_card(self, session_key: str) -> list[str]:
    """
    Fetch the user peer's card — a curated list of key facts.
    Fast, no LLM reasoning. Returns raw structured facts Honcho has
    inferred about the user (name, role, preferences, patterns).
    """
    session = self._cache.get(session_key)
    if not session:
        return []
    try:
        return self._fetch_peer_card(session.user_peer_id)
    except Exception as e:
        logger.debug("Failed to fetch peer card from Honcho: %s", e)
        return []
```

**`_fetch_peer_card` 直接调用 peer.card()**（无 LLM 推理）：
```python
# plugins/memory/honcho/session.py:610-625
def _fetch_peer_card(self, peer_id: str) -> list[str]:
    """Fetch a peer card directly from the peer object."""
    peer = self._get_or_create_peer(peer_id)
    getter = getattr(peer, "get_card", None)
    if callable(getter):
        return self._normalize_card(getter())
    # Fallback: legacy .card attribute
    legacy_getter = getattr(peer, "card", None)
    if callable(legacy_getter):
        return self._normalize_card(legacy_getter())
    return []
```

**特点**：
1. **无 LLM 调用** — 直接从 Honcho 的 peer card 数据结构读取
2. **零延迟** — 本地缓存 + Honcho peer 对象直接访问
3. **返回结构化 facts** — Honcho 已完成推理和结构化
4. **Tool description 明确建议使用场景**：对话开始时快速获取用户快照

### 20.3 honcho_search — 零 LLM 成本的语义搜索

```python
# plugins/memory/honcho/__init__.py:370-380
elif tool_name == "honcho_search":
    query = args.get("query", "")
    if not query:
        return tool_error("Missing required parameter: query")
    max_tokens = min(int(args.get("max_tokens", 800)), 2000)
    result = self._manager.search_context(
        self._session_key, query, max_tokens=max_tokens
    )
    if not result:
        return json.dumps({"result": "No relevant context found."})
    return json.dumps({"result": result})
```

**底层实现**（`HonchoSessionManager.search_context`）：
```python
# plugins/memory/honcho/session.py:598-620
def search_context(self, session_key: str, query: str, max_tokens: int = 800) -> str:
    """
    Semantic search over Honcho session context.
    Returns raw excerpts ranked by relevance to the query. No LLM
    reasoning — cheaper and faster than dialectic_query. Good for
    factual lookups where the model will do its own synthesis.
    """
    session = self._cache.get(session_key)
    if not session:
        return ""
    try:
        ctx = self._fetch_peer_context(session.user_peer_id, search_query=query)
        parts = []
        if ctx["representation"]:
            parts.append(ctx["representation"])
        card = ctx["card"] or []
        if card:
            parts.append("\n".join(f"- {f}" for f in card))
        return "\n\n".join(parts)
    except Exception as e:
        logger.debug("Honcho search_context failed: %s", e)
        return ""
```

**`_fetch_peer_context` 关键参数 `search_query`**：
```python
# plugins/memory/honcho/session.py:628-660
def _fetch_peer_context(self, peer_id: str, search_query: str | None = None) -> dict[str, Any]:
    """Fetch representation + peer card directly from a peer object."""
    peer = self._get_or_create_peer(peer_id)
    # ...
    try:
        ctx = peer.context(search_query=search_query) if search_query else peer.context()
        # → Honcho 云端：用 search_query 做语义检索
        # → 返回相关 excerpts
    except Exception as e:
        logger.debug("Direct peer.context() failed for '%s': %s", peer_id, e)
```

**关键洞察**：
- `search_query` 参数传递给 Honcho 云端做语义匹配
- 但返回的是 **raw excerpts**（`representation` + `card`），**无 LLM 合成**
- 模型自己决定如何使用这些 raw facts
- `max_tokens` 限制返回量（默认 800，max 2000）

### 20.4 honcho_context — 高成本 LLM 合成 Q&A

```python
# plugins/memory/honcho/__init__.py:382-392
elif tool_name == "honcho_context":
    query = args.get("query", "")
    if not query:
        return tool_error("Missing required parameter: query")
    peer = args.get("peer", "user")
    result = self._manager.dialectic_query(
        self._session_key, query, peer=peer
    )
    return json.dumps({"result": result or "No result from Honcho."})
```

**底层实现**（`HonchoSessionManager.dialectic_query`）：
```python
# plugins/memory/honcho/session.py:415-460
def dialectic_query(
    self, session_key: str, query: str,
    reasoning_level: str | None = None,
    peer: str = "user",
) -> str:
    """
    Query Honcho's dialectic endpoint about a peer.
    Runs an LLM on Honcho's backend against the target peer's full
    representation. Higher latency than context() — call async via
    prefetch_dialectic() to avoid blocking the response.
    """
    # Guard: truncate query to Honcho's dialectic input limit
    if len(query) > self._dialectic_max_input_chars:
        query = query[:self._dialectic_max_input_chars].rsplit(" ", 1)[0]

    level = reasoning_level or self._dynamic_reasoning_level(query)

    try:
        if self._ai_observe_others:
            # AI peer can observe user — use cross-observation routing
            if peer == "ai":
                ai_peer_obj = self._get_or_create_peer(session.assistant_peer_id)
                result = ai_peer_obj.chat(query, reasoning_level=level) or ""
            else:
                ai_peer_obj = self._get_or_create_peer(session.assistant_peer_id)
                result = ai_peer_obj.chat(
                    query,
                    target=session.user_peer_id,
                    reasoning_level=level,
                ) or ""
        else:
            # AI can't observe others — each peer queries self
            peer_id = session.assistant_peer_id if peer == "ai" else session.user_peer_id
            target_peer = self._get_or_create_peer(peer_id)
            result = target_peer.chat(query, reasoning_level=level) or ""
    except Exception as e:
        logger.warning("Honcho dialectic query failed: %s", e)
        return ""
```

**Dialectic 的核心特点**：
1. **LLM 推理** — `peer.chat()` 在 Honcho 云端运行 LLM 对用户的 full representation 做综合
2. **动态 reasoning level** — 根据 query 长度自动选择推理深度（`dialectic_dynamic`）：
   - `< 120 chars` → 低推理
   - `120-400 chars` → 中等推理
   - `> 400 chars` → 高级推理
3. **跨观察路由** — `ai_observe_others` 决定是否 cross-observation 或 self-query
4. **Prefetch 机制** — 通常通过 `prefetch_dialectic()` 异步预取，避免阻塞

### 20.5 honcho_conclude — 写回持久化结论

```python
# plugins/memory/honcho/__init__.py:394-405
elif tool_name == "honcho_conclude":
    conclusion = args.get("conclusion", "")
    if not conclusion:
        return tool_error("Missing required parameter: conclusion")
    ok = self._manager.create_conclusion(self._session_key, conclusion)
    if ok:
        return json.dumps({"result": f"Conclusion saved: {conclusion}"})
    return tool_error("Failed to save conclusion.")
```

**底层实现**（`HonchoSessionManager.create_conclusion`）：
```python
# plugins/memory/honcho/session.py:665-695
def create_conclusion(self, session_key: str, content: str) -> bool:
    """
    Write a conclusion about the user back to Honcho.
    Conclusions are facts the AI peer observes about the user —
    preferences, corrections, clarifications, project context.
    They feed into the user's peer card and representation.
    """
    if not content or not content.strip():
        return False
    session = self._cache.get(session_key)
    if not session:
        logger.warning("No session cached for '%s', skipping conclusion", session_key)
        return False
    try:
        if self._ai_observe_others:
            # AI peer creates conclusion about user (cross-observation)
            assistant_peer = self._get_or_create_peer(session.assistant_peer_id)
            conclusions_scope = assistant_peer.conclusions_of(session.user_peer_id)
        else:
            # AI can't observe others — user peer creates self-conclusion
            user_peer = self._get_or_create_peer(session.user_peer_id)
            conclusions_scope = user_peer.conclusions_of(session.user_peer_id)

        conclusions_scope.create([{
            "content": content.strip(),
            "session_id": session.honcho_session_id,
        }])
        return True
    except Exception as e:
        logger.error("Failed to create conclusion: %s", e)
        return False
```

**设计意图**：
- **AI 观察用户 → 写结论** — `assistant_peer.conclusions_of(user_peer)` 表示"AI 观察到的关于用户的事实"
- **写的是什么** — 用户偏好、纠正、澄清、项目上下文
- **写后效果** — 结论进入用户的 peer card 和 representation（被 profile/search/context 使用）

### 20.6 四工具的协作模式

```
用户说: "我偏好用 TypeScript，不要用 JavaScript"

     ┌─────────────────────────────────────────────┐
     │  honcho_conclude                           │
     │  create_conclusion("用户偏好 TypeScript")   │
     └─────────────────┬───────────────────────────┘
                       ↓
              Honcho 云端处理
                       ↓
     ┌─────────────────────────────────────────────┐
     │  用户 Peer Card 更新                        │
     │  representation + card 包含新结论           │
     └─────────────────────────────────────────────┘
                       ↓
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
  honcho_profile  honcho_search  honcho_context
  (下次对话)     (语义检索)     (复杂 Q&A)
```

### 20.7 Honcho 四工具与 BlueCortexCE 对比

| 维度 | Honcho 四工具 | BlueCortexCE 等价 |
|------|-------------|------------------|
| honcho_profile | curated facts，无 LLM | Observation raw data |
| honcho_search | semantic search，raw excerpts | `/api/memory/search` |
| honcho_context | dialectic Q&A，LLM 合成 | `/api/context/generate` |
| honcho_conclude | 写回 conclusion | Observation 写入 |

### 20.8 翻译：旁路型如何借鉴

**核心差距**：Honcho 四工具体现了**成本-效益梯度设计**（无 LLM → 有 LLM），BlueCortexCE 目前只有一个 `/api/context/generate`（相当于 honcho_context）。

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE 增加 `/api/memory/raw_search` | 类似 honcho_search，返回 raw vector search 结果，无 LLM 合成 |
| **高** | BlueCortexCE 增加 `/api/profile` | 类似 honcho_profile，返回 curated facts（当前由消费方自己做） |
| **中** | BlueCortexCE 优化 `/api/context/generate` prompt | 借鉴 dialectic query 的动态 reasoning level 思想 |
| **中** | BlueCortexCE 增加 conclusion 写回机制 | 类似 honcho_conclude，消费方可以写回"结论"更新用户 profile |

---

## 21. Honcho write_frequency 机制验证（v3.7 新增）

> **文件**: `plugins/memory/honcho/session.py:220-260`（`_async_writer_loop`），`plugins/memory/honcho/session.py:260-290`（`save()` 方法）
> **本节为 v3.7 新增**，验证 "async" / "turn" / "session" 配置是否实际实现。

### 21.1 配置解析

```python
# plugins/memory/honcho/client.py:120-135
@dataclass
class HonchoClientConfig:
    # Write frequency: "async" (background thread), "turn" (sync per turn),
    # "session" (flush on session end), or int (every N turns)
    write_frequency: str | int = "async"
```

**配置解析**（`client.py:185-195`）：
```python
raw_wf = (
    host_block.get("writeFrequency")
    or raw.get("writeFrequency")
    or "async"
)
try:
    write_frequency: str | int = int(raw_wf)
except (TypeError, ValueError):
    write_frequency = str(raw_wf)
```

**支持三种模式 + int**：
- `"async"` → 后台线程异步写入
- `"turn"` → 同步每个 turn 写入
- `"session"` → 仅在 session 结束时 flush
- `int`（如 `3`）→ 每 N 个 turn 写入一次

### 21.2 异步写入线程（`_async_writer_loop`）

```python
# plugins/memory/honcho/session.py:220-265
class HonchoSessionManager:
    def __init__(self, ...):
        write_frequency = (config.write_frequency if config else "async")
        self._write_frequency = write_frequency
        self._turn_counter: int = 0

        # Async write queue — started lazily on first enqueue
        self._async_queue: queue.Queue | None = None
        self._async_thread: threading.Thread | None = None
        if write_frequency == "async":
            self._async_queue = queue.Queue()
            self._async_thread = threading.Thread(
                target=self._async_writer_loop,
                name="honcho-async-writer",
                daemon=True,
            )
            self._async_thread.start()

    def _async_writer_loop(self) -> None:
        """Background daemon thread: drains the async write queue."""
        while True:
            try:
                item = self._async_queue.get(timeout=5)
                if item is _ASYNC_SHUTDOWN:
                    break
                try:
                    success = self._flush_session(item)
                except Exception as e:
                    success = False
                    first_error = e
                if not success:
                    # Retry once after 2s
                    import time as _time
                    _time.sleep(2)
                    try:
                        retry_success = self._flush_session(item)
                    except Exception as e2:
                        logger.error("Honcho async write retry failed, dropping batch: %s", e2)
            except queue.Empty:
                continue
            except Exception as e:
                logger.error("Honcho async writer error: %s", e)
```

**异步写入关键特性**：
1. **后台 daemon 线程** — 不阻塞主线程
2. **队列 + 超时** — 5s 超时防止线程空转
3. **失败重试一次** — `_flush_session` 失败后 sleep 2s 重试
4. **优雅 shutdown** — `_ASYNC_SHUTDOWN` 信号关闭线程

### 21.3 `save()` 方法路由

```python
# plugins/memory/honcho/session.py:268-295
def save(self, session: HonchoSession) -> None:
    """Save messages to Honcho, respecting write_frequency."""
    self._turn_counter += 1
    wf = self._write_frequency

    if wf == "async":
        if self._async_queue is not None:
            self._async_queue.put(session)
    elif wf == "turn":
        self._flush_session(session)           # 同步写入
    elif wf == "session":
        # Accumulate; caller must call flush_all() at session end
        pass
    elif isinstance(wf, int) and wf > 0:
        if self._turn_counter % wf == 0:
            self._flush_session(session)
```

**结论**：✅ **已验证实现**，三种模式 + int 全部有完整代码。

### 21.4 `flush_all()` Session 结束时的兜底

```python
# plugins/memory/honcho/session.py:297-320
def flush_all(self) -> None:
    """Flush all pending unsynced messages for all cached sessions."""
    for session in list(self._cache.values()):
        try:
            self._flush_session(session)
        except Exception as e:
            logger.error("Honcho flush_all error for %s: %s", session.key, e)

    # Drain async queue synchronously if it exists
    if self._async_queue is not None:
        while not self._async_queue.empty():
            try:
                item = self._async_queue.get_nowait()
                if item is not _ASYNC_SHUTDOWN:
                    self._flush_session(item)
            except queue.Empty:
                break
```

**关键设计**：`flush_all()` 不仅 flush 所有 session，还会**同步 drain 异步队列**，确保 session 结束时不丢失未写入的消息。

### 21.5 与 BlueCortexCE 对比

| 维度 | Honcho write_frequency | BlueCortexCE |
|------|----------------------|--------------|
| async 模式 | 后台 daemon 线程 + queue | ❌ 无（每次写入同步） |
| turn 模式 | 每个 turn 同步写入 | ⚠️ 每次 `sync_turn()` 写入（实际是后台 thread） |
| session 模式 | 累积 + flush_all | 类似 SessionEnd 时写入 |
| int 模式 | 每 N 个 turn 批量写入 | ❌ 无 |
| 重试机制 | 失败重试一次 | ❌ 无 |
| 优雅 shutdown | drain queue + shutdown signal | ❌ 无 |

### 21.6 翻译：旁路型如何借鉴

**核心差距**：BlueCortexCE 的每次写入（`recordObservation` 等）都是同步的，没有 async 队列机制。

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE 实现 async write queue | 大量 observation 写入时，后台队列 + 批量 flush |
| **中** | 实现 turn-based batching | 类似 `int` 模式，每 N 个 turn 批量写入 |
| **中** | 实现重试机制 | 写入失败后重试一次 |
| **低** | 优雅 shutdown drain | 服务关闭时 drain write queue |

---

## 22. Honcho Recall Mode — 三种记忆注入模式（v3.7 新增）

> **文件**: `plugins/memory/honcho/__init__.py:80-150`（schema），`plugins/memory/honcho/__init__.py:190-250`（initialize）
> **本节为 v3.7 新增**，分析 Honcho 的三种 recall mode 如何控制记忆的注入方式。

### 22.1 三种 Recall Mode 配置

```python
# plugins/memory/honcho/client.py:95-100
@dataclass
class HonchoClientConfig:
    # Recall mode: how memory retrieval works when Honcho is active.
    # "hybrid"  — auto-injected context + Honcho tools available (model decides)
    # "context" — auto-injected context only, Honcho tools removed
    # "tools"   — Honcho tools only, no auto-injected context
    recall_mode: str = "hybrid"
```

### 22.2 recall_mode 在 System Prompt 中的体现

```python
# plugins/memory/honcho/__init__.py:230-260
def system_prompt_block(self) -> str:
    if self._recall_mode == "context":
        header = (
            "# Honcho Memory\n"
            "Active (context-injection mode). Relevant user context is automatically "
            "injected before each turn. No memory tools are available."
        )
    elif self._recall_mode == "tools":
        header = (
            "# Honcho Memory\n"
            "Active (tools-only mode). Use honcho_profile, honcho_search, "
            "honcho_context, and honcho_conclude tools to access user memory."
        )
    else:  # hybrid
        header = (
            "# Honcho Memory\n"
            "Active (hybrid mode). Relevant context is auto-injected AND memory tools are available."
        )
```

### 22.3 recall_mode 对工具可见性的控制

```python
# plugins/memory/honcho/__init__.py:340-350
def get_tool_schemas(self) -> List[Dict[str, Any]]:
    """Return tool schemas, respecting recall_mode."""
    if self._cron_skipped:
        return []
    if self._recall_mode == "context":
        return []  # No tools in context-only mode
    return list(ALL_TOOL_SCHEMAS)
```

### 22.4 recall_mode 对 Prefetch 的控制

```python
# plugins/memory/honcho/__init__.py:280-295
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    """Fire a background dialectic query for the upcoming turn."""
    # B1: tools-only mode — no prefetch
    if self._recall_mode == "tools":
        return
    # ...

def prefetch(self, query: str, *, session_id: str = "") -> str:
    """Return prefetched dialectic context from background thread."""
    # B1: tools-only mode — no auto-injection
    if self._recall_mode == "tools":
        return ""
    # ...
```

### 22.5 三种模式总结

| 模式 | Auto-injection | Tools 可用 | Prefetch | 适用场景 |
|------|---------------|-----------|----------|----------|
| `context` | ✅ 自动注入 | ❌ 无 | ✅ 启用 | 模型完全自主记忆检索 |
| `tools` | ❌ 无 | ✅ 四工具 | ❌ 禁用 | 模型主动调用工具获取记忆 |
| `hybrid` | ✅ 自动注入 | ✅ 四工具 | ✅ 启用 | 两者兼备 |

### 22.6 与 BlueCortexCE 对比

| 维度 | Honcho Recall Mode | BlueCortexCE |
|------|------------------|--------------|
| context 模式 | ✅ | ❌ 无对应模式 |
| tools 模式 | ✅ | ❌ 无对应模式 |
| hybrid 模式 | ✅ | ⚠️ 部分（API 可用但无 auto-injection） |
| 模式切换 | 运行时可配置 | 固定 |

### 22.7 翻译：旁路型如何借鉴

**核心洞察**：Honcho 的 recall mode 本质上是**控制记忆检索的主动性**：
- `context` = 被动（记忆自动注入，模型无需主动）
- `tools` = 主动（模型必须调用工具获取记忆）
- `hybrid` = 两者兼备

**BlueCortexCE 现状**：消费方通过 API 调用获取记忆，无自动注入机制。

**借鉴建议**：
- **中优先级**：BlueCortexCE 增加 "context mode" API，自动生成并返回记忆摘要（类似 honcho_context 的结果），供消费方直接注入 system prompt
- **低优先级**：BlueCortexCE 提供"被动注入"的能力（将记忆摘要直接写入消费方的 context）

---

