<!-- split 4/10 | aspect:50-honcho-holographic-deep | ≤50KB -->

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

## 24. on_delegation Hook — 子 Agent 记忆归属的架构性未完成（v3.9 新增）

> **文件**: `agent/memory_provider.py:175-183`（接口定义），`tools/delegate_tool.py:795-815`（调用点），`agent/memory_manager.py:319-332`（路由）
> **本节为 v3.9 新增**，分析 Hermes 的 `on_delegation` hook 机制及其当前实现状态。

### 24.1 接口定义

```python
# agent/memory_provider.py:175-183
def on_delegation(self, task: str, result: str, *,
                  child_session_id: str = "", **kwargs) -> None:
    """Called on the PARENT agent when a subagent completes.

    The parent's memory provider gets the task+result pair as an
    observation of what was delegated and what came back. The subagent
    itself has no provider session (skip_memory=True).

    task: the delegation prompt
    result: the subagent's final response
    child_session_id: the subagent's session_id
    """
```

**设计意图**：当 `delegate_task` 工具派生子 Agent 完成任务后，父 Agent 的记忆系统应自动记录：
1. **Task**：派发的目标是什么
2. **Result**：子 Agent 返回的结果摘要
3. **Child session ID**：子 Agent 的 session ID（用于溯源）

### 24.2 调用链

```
delegate_tool.py:800
  └── parent_agent._memory_manager.on_delegation(task, result, child_session_id)
        └── memory_manager.py:324
              └── for provider in self._providers:
                    └── provider.on_delegation(task, result, child_session_id=...)
```

```python
# tools/delegate_tool.py:795-815
if parent_agent and hasattr(parent_agent, '_memory_manager') and parent_agent._memory_manager:
    for entry in results:
        try:
            _task_goal = task_list[entry["task_index"]]["goal"] if entry["task_index"] < len(task_list) else ""
            parent_agent._memory_manager.on_delegation(
                task=_task_goal,
                result=entry.get("summary", "") or "",
                child_session_id=getattr(children[entry["task_index"]][2], "session_id", "") if entry["task_index"] < len(children) else "",
            )
        except Exception:
            pass
```

### 24.3 当前状态：所有 Provider 均未实现

**检查结果**：

| Provider | on_delegation 实现 | 代码位置 |
|----------|-------------------|----------|
| Honcho | ❌ 基类 no-op | 无 `def on_delegation` |
| Holographic | ❌ 基类 no-op | 无 `def on_delegation` |
| Mem0 | ❌ 基类 no-op | 无 `def on_delegation` |
| Honcho Hindsight | ❌ 基类 no-op | 无 `def on_delegation` |

所有 provider 直接继承 `MemoryProvider` 基类，使用默认 no-op 实现。

**影响**：
- **子 Agent 的工作成果不会自动记录到父 session 的记忆**
- 父 Agent 不知道子 Agent 完成了什么工作
- `delegate_task` 的结果只在工具返回值中可见，不进入长期记忆

### 24.4 与 BlueCortexCE 对比

| 维度 | Hermes on_delegation | BlueCortexCE |
|------|---------------------|--------------|
| 设计意图 | 父 session 记录子 Agent 工作成果 | 无对应机制 |
| 实现状态 | 接口存在，但所有 provider 未实现 | N/A |
| 触发时机 | 子 Agent 完成后立即调用 | N/A |
| 传递内容 | task goal + result summary + child_session_id | N/A |
| 实际效果 | **空操作** | N/A |

### 24.5 翻译：旁路型如何借鉴

**Hermes 做法**：在父 Agent 调用 `on_delegation` hook，期望 provider 将子任务结果写入父 session 的记忆。

**旁路型如何借鉴**：
- **高优先级**：BlueCortexCE 需要思考：当消费方（Claude Code/OpenClaw）使用子进程/子 Agent 时，记忆归属于谁？
- **当前 BlueCortexCE 现状**：完全由消费方决定如何处理，旁路型系统不知道子任务的存在
- **借鉴意义**：如果 BlueCortexCE 要支持"子 Agent 记忆归属父 session"，需要：
  1. 消费方显式传递"父 session_id"和"子任务摘要"
  2. BlueCortexCE 提供 `/api/delegation` 或在 `sync_turn` 中支持 `parent_session_id` 参数
- **注意**：这是旁路型架构的优势——消费方可以自己决定如何处理子任务记忆，不需要等系统自动处理

---

## 25. on_memory_write 桥接机制 — 内置记忆与外部 Provider 的双向同步（v3.9 新增）

> **文件**: `run_agent.py:6968-6975`（调用点），`agent/memory_manager.py:303-318`（路由），`plugins/memory/honcho/__init__.py:611-630`（Honcho 实现），`plugins/memory/holographic/__init__.py:243-253`（Holographic 实现）
> **本节为 v3.9 新增**，分析 Hermes 如何将内置 `memory` 工具的写入同步到外部记忆 Provider。

### 25.1 背景：两套记忆系统的并存

Hermes 有两套并行的记忆系统：

| 系统 | 工具 | 存储位置 | 用途 |
|------|------|----------|------|
| **内置记忆** | `memory` 工具（add/replace/remove） | `hermes_state.py` SQLite | 始终开启，Agent 直接使用 |
| **外部 Provider** | Honcho/Holographic/Mem0 等 | 各 Provider 自有存储 | 可插拔，Provider 特定能力 |

**问题**：当 Agent 使用 `memory` 工具添加用户事实时，外部 Provider 如何知道并同步？

### 25.2 桥接机制：`on_memory_write` Hook

```python
# run_agent.py:6968-6975
elif function_name == "memory":
    target = function_args.get("target", "memory")
    from tools.memory_tool import memory_tool as _memory_tool
    result = _memory_tool(
        action=function_args.get("action"),
        target=target,
        content=function_args.get("content"),
        old_text=function_args.get("old_text"),
        store=self._memory_store,
    )
    # Bridge: notify external memory provider of built-in memory writes
    if self._memory_manager and function_args.get("action") in ("add", "replace"):
        try:
            self._memory_manager.on_memory_write(
                function_args.get("action", ""),
                target,
                function_args.get("content", ""),
            )
        except Exception:
            pass
```

**关键点**：当 `memory` 工具执行 `add` 或 `replace` 时，自动触发 `on_memory_write` 广播给所有 Provider。

### 25.3 MemoryManager 路由

```python
# agent/memory_manager.py:303-318
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Notify all providers of a built-in memory write."""
    for provider in self._providers:
        try:
            provider.on_memory_write(action, target, content)
        except Exception as e:
            logger.debug(
                "Memory provider '%s' on_memory_write failed: %s",
                provider.name, e,
            )
```

### 25.4 Honcho 的实现：镜像为 Conclusion

```python
# plugins/memory/honcho/__init__.py:611-625
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in user profile writes as Honcho conclusions."""
    if action != "add" or target != "user" or not content:
        return  # Only mirror "add user" actions
    if self._cron_skipped:
        return
    if not self._manager or not self._session_key:
        return

    def _write():
        try:
            self._manager.create_conclusion(self._session_key, content)
        except Exception as e:
            logger.debug("Honcho memory mirror failed: %s", e)

    t = threading.Thread(target=_write, daemon=True, name="honcho-memwrite")
    t.start()
```

**特点**：
1. **仅镜像 `add user`** — 其他 target/action 忽略
2. **异步写入** — 后台线程调用 `create_conclusion`
3. **用户事实 → Honcho conclusion** — 内置记忆中的用户偏好写入 Honcho 云端

### 25.5 Holographic 的实现：镜像为 Fact

```python
# plugins/memory/holographic/__init__.py:243-253
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in memory writes as facts."""
    if action == "add" and self._store and content:
        try:
            category = "user_pref" if target == "user" else "general"
            self._store.add_fact(content, category=category)
        except Exception as e:
            logger.debug("Holographic memory_write mirror failed: %s", e)
```

**特点**：
1. **镜像所有 `add` 操作**
2. **target=user → category="user_pref"**，其他 → category="general"
3. **同步写入**（无后台线程）

### 25.6 与 BlueCortexCE 对比

| 维度 | Hermes 两套记忆桥接 | BlueCortexCE |
|------|-------------------|--------------|
| 触发条件 | `memory` 工具 `add`/`replace` | N/A（单一系统） |
| 广播范围 | 所有注册 Provider | N/A |
| Honcho 行为 | 异步写入云端 conclusion | N/A |
| Holographic 行为 | 同步写入本地 fact | N/A |
| 过滤策略 | Honcho 仅 `add user`，Holographic 所有 `add` | N/A |

### 25.7 翻译：旁路型如何借鉴

**Hermes 的两套记忆系统**对应 BlueCortexCE 的情况：
- BlueCortexCE 是纯旁路型，**没有**内置记忆系统
- 但如果有消费方同时使用 BlueCortexCE 和自己的本地记忆，**桥接机制**的思想仍然有价值

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **低** | BlueCortexCE 增加"写入同步"能力 | 当 BlueCortexCE 记录 Observation 时，可选同步到消费方的本地记忆系统（如果消费方暴露了 API） |
| **中** | BlueCortexCE 增加 `on_memory_write` 类似 hook | 让消费方可以注册回调，当 BlueCortexCE 写入时触发消费方的处理逻辑 |
| **高** | BlueCortexCE 的 SDK（JS/Go/Python）应该实现"双向同步" | 消费方本地的用户偏好变化时，同步到 BlueCortexCE；BlueCortexCE 的记录也可以写回消费方（如果有对应 API） |

---

## 26. Holographic 遗忘机制 — 指数衰减 + Trust Scoring（v3.9 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:28-35`（初始化），`plugins/memory/holographic/retrieval.py:569-595`（`_temporal_decay`），`plugins/memory/holographic/retrieval.py:95-110`（评分时应用衰减）
> **本节为 v3.9 新增**，分析 Holographic Provider 的时序遗忘机制。

### 26.1 配置项

```python
# plugins/memory/holographic/__init__.py:15
temporal_decay_half_life: 0  # days, 0 = disabled
```

```python
# plugins/memory/holographic/retrieval.py:28-35
@dataclass
class FactRetrieverConfig:
    temporal_decay_half_life: int = 0,  # days, 0 = disabled

    self.half_life = temporal_decay_half_life
```

**`half_life=0`（默认）= 遗忘机制禁用**。用户需要显式配置才启用。

### 26.2 指数衰减算法

```python
# plugins/memory/holographic/retrieval.py:569-595
def _temporal_decay(self, timestamp_str: str | None) -> float:
    """Exponential decay: 0.5^(age_days / half_life_days).

    Returns 1.0 if decay is disabled or timestamp is missing.
    """
    if not self.half_life or not timestamp_str:
        return 1.0  # No decay

    try:
        ts = datetime.fromisoformat(timestamp_str.replace("Z", "+00:00"))
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)

        age_days = (datetime.now(timezone.utc) - ts).total_seconds() / 86400
        if age_days < 0:
            return 1.0

        return math.pow(0.5, age_days / self.half_life)
    except (ValueError, TypeError):
        return 1.0
```

**衰减公式**：`score *= 0.5^(age_days / half_life)`

| 年龄 / 半衰期 | 衰减系数 |
|--------------|---------|
| 0（刚写入） | 1.0 |
| half_life / 2 | ~0.71 |
| half_life | 0.5 |
| 2 × half_life | 0.25 |
| 3 × half_life | 0.125 |

### 26.3 检索时应用衰减

```python
# plugins/memory/holographic/retrieval.py:95-110
# Stage 2: Rerank with Jaccard + trust + optional decay
for fact in candidates:
    score = fact.get("base_score", 0.0)
    # ...
    if self.half_life > 0:
        score *= self._temporal_decay(fact.get("updated_at") or fact.get("created_at"))
    fact["score"] = score
    scored.append(fact)
```

**关键点**：衰减在**检索重排序阶段**应用，不影响原始存储。fact 本身永远不删除（除非用户手动操作）。

### 26.4 Trust Scoring 的协同

Holographic 的评分机制结合了多个维度：

```python
score = (
    jaccard_similarity
    * trust_score          # 来源可信度（0.0-1.0）
    * temporal_decay       # 时间衰减（0.0-1.0）
    * (1 + 0.1 * helpful_count)  # 正向反馈加成
)
```

**三者协同**：
- **Trust score**：事实来源的可信度（手动设置或自动推断）
- **Temporal decay**：随时间降低相关性
- **Helpful count**：用户确认/赞成的次数

### 26.5 与 BlueCortexCE 对比

| 维度 | Holographic 遗忘机制 | BlueCortexCE |
|------|---------------------|--------------|
| 遗忘策略 | 指数衰减（评分时应用，不删除数据） | ❌ 无遗忘机制 |
| 半衰期配置 | 用户可配置（天数） | ❌ 无 |
| Trust scoring | ✅ 有（来源可信度） | ❌ 无 |
| Helpful count | ✅ 有（用户确认） | ❌ 无 |
| 检索时衰减 | ✅ 在 reranking 时应用 | ❌ 无 |

### 26.6 翻译：旁路型如何借鉴

**Hermes 做法**：检索时动态应用衰减，不物理删除数据。fact 保留历史，但随着时间推移，在检索结果中排名下降。

**BlueCortexCE 现状**：所有 Observation/Summary 永久存储，无时间衰减机制。

**翻译：旁路型如何借鉴**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE 增加 `temporal_decay` 配置项 | 类似 `temporal_decay_half_life`，检索时降低旧记录的分数 |
| **中** | BlueCortexCE 增加 trust/quality 字段 | Observation/Summary 增加来源质量评分（可由消费方提供） |
| **中** | BlueCortexCE 的检索排序考虑时间因素 | 最近的相关记忆排名更高（可配置） |
| **低** | BlueCortexCE 增加"确认/反对"机制 | 消费方可以标记某条记忆是有用还是无用，影响后续检索权重 |
| **高** | **不需要物理删除** | Hermes 的"软遗忘"（评分衰减）比物理删除更安全，BlueCortexCE 应采用同样策略 |

**关键借鉴点**：BlueCortexCE 应该在**检索 API**（`/api/memory/search`）层面实现衰减，而不是在存储层面删除数据。这样既保留历史，又让旧记忆自动"沉淀"到搜索结果底部。

---

## 27. Holographic 矛盾检测 — 实体重叠 + 内容相异度算法（v4.0 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:338-442`
> **本节为 v4.0 新增**，深度分析 Holographic 的 `contradict()` 方法——**自动化记忆卫生检测**，这是目前所有记忆系统中独一无二的特性。

### 27.1 核心洞察：什么是"矛盾"？

Hermes 对"矛盾"的定义非常精确：

> **两个事实矛盾 = 共享实体（相同主体）+ 内容向量差异大（不同声明）**

这个定义背后的直觉是：
- 如果两个事实都说"关于 X 的一些事情"，但内容向量差异很大（一个是正面评价，一个是负面），则可能是矛盾的
- 如果两个事实没有共享实体，它们可能是完全无关的声明，不构成矛盾

### 27.2 算法完整流程

```python
# plugins/memory/holographic/retrieval.py:338-442
def contradict(self, category: str | None = None, threshold: float = 0.3, limit: int = 10):
    """
    1. 从 SQLite 获取所有有 HRR 向量的事实
    2. 对每对事实 (O(n²))：
       a. 提取共享实体（Jaccard overlap）
       b. 如果 overlap >= 0.3：
          - 计算 HRR 内容相似度
          - contradiction_score = entity_overlap * (1 - (content_sim + 1) / 2)
          - 如果 score >= threshold，标记为矛盾
    3. 按 contradiction_score 降序返回
    """
```

**关键参数**：
- `entity_overlap >= 0.3`（Jaccard）才进入矛盾判断——避免不相关实体的事实被误判
- `contradiction_score >= 0.3`（默认）才报告——可调整敏感度

### 27.3 矛盾分数计算公式

```
contradiction_score = entity_overlap * (1 - (content_similarity + 1) / 2)

其中：
- entity_overlap = |ents1 ∩ ents2| / |ents1 ∪ ents2|  (Jaccard, 0-1)
- content_similarity = HRR similarity between two fact vectors (-1 to 1)
- (content_similarity + 1) / 2 将 HRR 范围映射到 (0, 1)

所以：
- entity_overlap = 1.0（完全相同实体）+ content_similarity = -1.0（完全相反内容）
  → contradiction_score = 1.0 * (1 - 0/2) = 1.0（最高矛盾）
- entity_overlap = 1.0 + content_similarity = 1.0（完全相同内容）
  → contradiction_score = 1.0 * (1 - 1) = 0.0（无矛盾）
```

### 27.4 O(n²) 比较的防护机制

```python
# retrieval.py:370-378
_MAX_CONTRADICT_FACTS = 500
if len(rows) > _MAX_CONTRADICT_FACTS:
    rows = sorted(rows, key=lambda r: r["updated_at"] or r["created_at"], reverse=True)
    rows = rows[:_MAX_CONTRADICT_FACTS]
```

**保护**：当 facts 超过 500 条时，只比较最近更新的 500 条。500 facts → 最多 125,000 对比较，这是可接受的上限。

### 27.5 返回结构

```python
{
    "fact_a": {...},           # 第一个事实（去掉 hrr_vector）
    "fact_b": {...},           # 第二个事实（去掉 hrr_vector）
    "entity_overlap": 0.857,    # Jaccard 重叠度
    "content_similarity": -0.23,  # HRR 内容相似度
    "contradiction_score": 0.541,  # 最终矛盾分数
    "shared_entities": ["peppi", "backend"]  # 共享实体列表
}
```

### 27.6 工具 Schema 中的体现

```python
# plugins/memory/holographic/__init__.py:49-51
"• contradict — Memory hygiene: find facts making conflicting claims.\n"
```

用户可以通过 `memory` 工具调用 `contradict` action，主动检查记忆库中是否存在矛盾：

```python
# holographic.py:308-309
elif action == "contradict":
    results = retriever.contradict(category=category, threshold=threshold, limit=limit)
```

### 27.7 与 BlueCortexCE 对比

| 维度 | Holographic contradict | BlueCortexCE |
|------|----------------------|--------------|
| 矛盾定义 | 实体重叠 + 内容相异 | ❌ 无对应机制 |
| 算法 | O(n²) 两两比较 | ❌ 无 |
| 防护 | 500 条上限 | ❌ 无 |
| 调用方式 | `memory` 工具 + `contradict` action | ❌ 无 |
| 输出 | 矛盾对列表 + 分数 | ❌ 无 |

### 27.8 翻译：旁路型如何借鉴

**核心洞察**：这是目前所有记忆系统中**唯一**实现了自动化矛盾检测的。BlueCortexCE 完全缺少这个能力。

**实现方案（高优先级）**：

1. **Entity linking**：为 BlueCortexCE 的 Observation 增加实体提取能力
   - 可以用简单的正则（类似 Holographic）
   - 或用 LLM 在提取 Observation 时顺便提取实体列表

2. **矛盾检测 API**：`GET /api/memory/contradictions`
   - 实现类似公式：entity_overlap * (1 - content_similarity)
   - 限制最大比较对数（如 500 条 Observation）
   - 返回矛盾对列表

3. **触发时机**：
   - 用户主动查询（类似 Hermes 的 `contradict` tool）
   - 或在 Observation 写入时检查（如果新 Observation 与已有 Observation 有高实体重叠但内容相异）

**注意**：Holographic 的矛盾检测是纯本地的（SQLite + numpy），BlueCortexCE 使用 PostgreSQL + pgvector，需要用 SQL/pgvector 实现类似逻辑。

---

## 28. Holographic reason() — 多实体代数检索（v4.0 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:260-337`
> **本节为 v4.0 新增**，分析 HRR 代数检索的核心能力——**多实体组合查询**。

### 28.1 核心洞察：什么是"代数检索"？

传统向量数据库只能做：**"找与 query 向量最相似的 K 个结果"**。

HRR 代数检索能做到：**"找同时与 [A, B, C] 都有结构关联的事实"**——这是传统 embedding 无法做到的。

### 28.2 算法核心

```python
# retrieval.py:260-337
def reason(self, entities: list[str], category: str | None = None, limit: int = 10):
    """
    1. 对每个 entity，计算 probe_key = bind(encode_atom(entity), role_entity)
    2. 对每个 fact：
       - 对每个 entity，从 fact_vec 中 unbinding 出 residual
       - 比较 residual 与 role_content 的相似度
       - 取所有 entity 相似度的 min（AND 语义）
    3. 只返回所有 entity 都"结构相关"的事实
    """
```

**关键设计**：
- `probe_key = bind(entity_vec, role_entity)` — 将实体绑定到"实体角色"
- `residual = unbinding(fact_vec, probe_key)` — 从事实中提取关于该实体的信号
- `min(entity_scores)` — 所有实体都必须有关联（AND 语义）

### 28.3 AND 语义 vs OR 语义

```python
# 注释原文
# A fact scores high only if ALL entities have structural presence
# (AND semantics via min, vs OR which would use mean/max).
```

**为什么用 min 而不是 mean/max？**
- `min`：所有实体都必须结构相关 → AND 语义
- `mean/max`：任一实体相关即可 → OR 语义

对于"找同时与 peppi 和 backend 都相关的事实"，必须用 AND 语义。

### 28.4 Fallback 机制

```python
# retrieval.py:264-268
if not hrr._HAS_NUMPY or not entities:
    # Fallback: search with all entities as keywords
    query = " ".join(entities)
    return self.search(query, category=category, limit=limit)
```

当 numpy 不可用时，退化为关键词搜索（将所有 entity 作为空格分隔的 query）。

### 28.5 与 BlueCortexCE 对比

| 维度 | Holographic reason() | BlueCortexCE |
|------|---------------------|--------------|
| 查询类型 | 多实体 AND 语义 | ❌ 无（只支持单 query） |
| 算法基础 | HRR 代数（bind/unbind） | pgvector 余弦相似度 |
| 语义 | AND（所有实体都相关） | OR（任一实体相关） |
| 实现难度 | 高（需要 HRR 代数） | 低（pgvector 不支持） |

### 28.6 翻译：旁路型如何借鉴

**现实评估**：HRR 代数检索在 BlueCortexCE 中**无法直接实现**（pgvector 不支持 bind/unbind 代数操作）。

**替代方案（中优先级）**：
1. **多实体查询 API**：`POST /api/memory/search` 接受 `entities: ["peppi", "backend"]`
2. **实现方式**：
   - 先分别搜索每个 entity 的相关 Observation
   - 取交集（AND 语义）或并集（OR 语义）
   - 这是工程上的近似，不是代数上的等价
3. **注意**：这种实现的信息召回率可能低于真正的 HRR 代数检索

---

## 29. Holographic 实体提取算法（v4.0 新增）

> **文件**: `plugins/memory/holographic/store.py:391-428`
> **本节为 v4.0 新增**，分析 Holographic 的轻量级实体提取机制。

### 29.1 正则规则 vs LLM 提取

**关键发现**：Holographic 的实体提取**不使用 LLM**，而是纯正则规则。

这与 Hindsight 的"实体消歧"（基于 LLM + knowledge graph）形成对比。

### 29.2 四条正则规则

```python
# store.py:394-428
_RE_CAPITALIZED  = re.compile(r'\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b')
# 匹配：大写字母开头的多词短语
# 例："John Doe" → "John Doe"

_RE_DOUBLE_QUOTE = re.compile(r'"([^"]+)"')
# 匹配：双引号内的内容
# 例：'"Python"' → "Python"

_RE_SINGLE_QUOTE = re.compile(r"'([^']+)'")
# 匹配：单引号内的内容
# 例："'pytest'" → "pytest"

_RE_AKA          = re.compile(
    r'(\w+(?:\s+\w+)*)\s+(?:aka|also known as)\s+(\w+(?:\s+\w+)*)',
    re.IGNORECASE,
)
# 匹配：X aka Y 或 X also known as Y
# 例："Guido aka BDFL" → "Guido" 和 "BDFL"
```

### 29.3 去重策略

```python
# store.py:403-413
seen: set[str] = set()
candidates: list[str] = []

def _add(name: str) -> None:
    stripped = name.strip()
    if stripped and stripped.lower() not in seen:
        seen.add(stripped.lower())
        candidates.append(stripped)
```

**去重规则**：
- 大小写不敏感（`"John"` 和 `"john"` 被视为相同）
- 保留首次出现的原始大小写形式

### 29.4 Entity 解析（_resolve_entity）

```python
# store.py:429-458
def _resolve_entity(self, name: str) -> int:
    # 1. 精确匹配 name 字段
    row = self._conn.execute(
        "SELECT entity_id FROM entities WHERE name LIKE ?", (name,)
    ).fetchone()
    if row is not None:
        return int(row["entity_id"])

    # 2. 在 aliases 字段中搜索（逗号分隔的别名列表）
    alias_row = self._conn.execute(
        """
        SELECT entity_id FROM entities
        WHERE ',' || aliases || ',' LIKE '%,' || ? || ',%'
        """,
        (name,),
    ).fetchone()
    if alias_row is not None:
        return int(alias_row["entity_id"])

    # 3. 创建新 entity
    cur = self._conn.execute(
        "INSERT INTO entities (name) VALUES (?)", (name,)
    )
    self._conn.commit()
    return int(cur.lastrowid)
```

**别名支持**：存储为逗号分隔字符串，查询时用 `LIKE '%...%'` 匹配。

### 29.5 与 BlueCortexCE 对比

| 维度 | Holographic 实体提取 | BlueCortexCE |
|------|-------------------|--------------|
| 提取方式 | 纯正则（无 LLM） | 无实体提取 |
| 实体解析 | SQLite 本地解析 | N/A |
| 别名支持 | ✅ 有 | ❌ 无 |
| 大小写处理 | 去重时忽略大小写 | N/A |

### 29.6 翻译：旁路型如何借鉴

**低优先级（但有价值）**：
- BlueCortexCE 可以在 Observation 写入时增加实体提取
- 实现方式：
  1. LLM 提取（更准确，但有成本）：在 Observation prompt 中要求输出 `entities: ["entity1", "entity2"]`
  2. 正则提取（无成本，但有限）：类似 Holographic 的正则规则
- 实体字段可用于：
  - 矛盾检测（如上所述）
  - 多实体 AND 查询（如上所述）
  - 实体级别的记忆统计（"这个实体的记忆有多少条"）

---

