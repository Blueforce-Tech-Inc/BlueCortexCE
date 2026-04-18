<!-- split 9/10 | aspect:60-evolution | ≤50KB -->

## 56. Supermemory 完整 Capture 生命周期 — trivial 过滤 + entity_context 注入 + session batch（v5.2 新增）

> **文件**: `plugins/memory/supermemory/__init__.py:550-650`（`sync_turn`），`plugins/memory/supermemory/__init__.py:598-640`（`on_session_end`）
> **本节为 v5.2 新增**，分析 Supermemory 的完整 capture 生命周期——从 trivial 过滤到 entity_context 注入，再到 session 级别的 batch ingest。

### 56.1 sync_turn 完整过滤链

Supermemory 的 `sync_turn` 实现了一套**四层过滤机制**，在写入前做严格的质量控制：

```python
# plugins/memory/supermemory/__init__.py:559-587
def sync_turn(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    if not self._active or not self._auto_capture or not self._write_enabled or not self._client:
        return

    clean_user = _clean_text_for_capture(user_content)
    clean_assistant = _clean_text_for_capture(assistant_content)
    if not clean_user or not clean_assistant:
        return

    # Layer 1: Minimum length check
    if self._capture_mode == "all":
        if len(clean_user) < _MIN_CAPTURE_LENGTH or len(clean_assistant) < _MIN_CAPTURE_LENGTH:
            return  # < 10 chars → skip

        # Layer 2: Trivial message filter
        if _is_trivial_message(clean_user):
            return  # "ok", "thanks", "sure", "yes", "no" → skip
```

**四层过滤**：

| Layer | 条件 | 效果 |
|-------|------|------|
| 1 | `_write_enabled=False` | 整个 capture 禁用 |
| 2 | `clean_user` 或 `clean_assistant` 为空 | 空白内容跳过 |
| 3 | 长度 < 10 chars（`_MIN_CAPTURE_LENGTH`） | 过短内容跳过 |
| 4 | `_is_trivial_message(clean_user)` | 纯确认消息跳过 |

### 56.2 Trivial Message 定义

```python
# plugins/memory/supermemory/__init__.py:32-34
_TRIVIAL_RE = re.compile(
    r"^(ok|okay|thanks|thank you|got it|sure|yes|no|yep|nope|k|ty|thx|np)\.?$",
    re.IGNORECASE,
)

# 使用：plugins/memory/supermemory/__init__.py:581
if _is_trivial_message(clean_user):
    return  # skip
```

**覆盖的消息类型**：纯确认（ok/yes/sure）、感谢（thanks/thx）、否定（no/nope）、俚语（k/ty）。

### 56.3 entity_context 注入 + 格式化为结构化消息

```python
# plugins/memory/supermemory/__init__.py:588-600
content = (
    f"[role: user]\n{clean_user}\n[user:end]\n\n"
    f"[role: assistant]\n{clean_assistant}\n[assistant:end]"
)
metadata = {"source": "hermes", "type": "conversation_turn"}

def _run():
    try:
        self._client.add_memory(
            content,
            metadata=metadata,
            entity_context=self._entity_context  # ← 提取 prompt 注入
        )
    except Exception:
        logger.debug("Supermemory sync_turn failed", exc_info=True)

# 异步写入，防堆积
if self._sync_thread and self._sync_thread.is_alive():
    self._sync_thread.join(timeout=2.0)  # 前一个未完成则等待 2s
self._sync_thread = threading.Thread(target=_run, daemon=True, name="supermemory-sync")
self._sync_thread.start()
```

**关键设计**：
1. **结构化格式**：`[role: user]...[/user:end] / [role: assistant]...[/assistant:end]` — 让 LLM 更容易理解对话边界
2. **entity_context 随每条记录发送** — Supermemory API 在每次写入时都带上提取 prompt（而不是在配置中固定）
3. **前序 join 防堆积**：如果上一个 thread 还没完成，等 2s 再启动新的（而不是覆盖）

### 56.4 on_session_end batch ingest

```python
# plugins/memory/supermemory/__init__.py:602-635
def on_session_end(self, messages: List[Dict[str, Any]]) -> None:
    if not self._active or not self._write_enabled or not self._client or not self._session_id:
        return

    cleaned = []
    for message in messages or []:
        role = message.get("role")
        if role not in ("user", "assistant"):
            continue
        content = _clean_text_for_capture(str(message.get("content", "")))
        if content:
            cleaned.append({"role": role, "content": content})

    if not cleaned:
        return

    # 极短 session 跳过（只有 1 条且 < 20 chars）
    if len(cleaned) == 1 and len(cleaned[0].get("content", "")) < 20:
        return

    try:
        self._client.ingest_conversation(self._session_id, cleaned)  # ← batch API
    except urllib.error.HTTPError:
        logger.warning("Supermemory session ingest failed", exc_info=True)
```

**与 sync_turn 的区别**：

| 维度 | `sync_turn`（turn-level） | `on_session_end`（session-level） |
|------|--------------------------|--------------------------------|
| 粒度 | 单轮（user + assistant） | 整个 session 的所有消息 |
| API | `add_memory`（单条） | `ingest_conversation`（batch） |
| entity_context | ✅ 每条都带 | ❌ 不带（已在 turn-level 处理） |
| Trivial 过滤 | ✅ | ❌（假设已在 turn-level 过滤） |

### 56.5 与 BlueCortexCE 对比

| 维度 | Supermemory Capture | BlueCortexCE |
|------|-------------------|--------------|
| Turn-level 过滤 | 4 层（active/write/min-length/trivial） | ❌ 无（所有 observation 都记录） |
| Trivial 过滤 | ✅ `_TRIVIAL_RE` | ❌ 无 |
| 最小长度过滤 | ✅ 10 chars | ❌ 无 |
| 结构化格式 | `[role: user]...[user:end]` | 纯文本 |
| Session batch ingest | ✅ `ingest_conversation` | ❌ 无（只有 Observation 单条） |
| entity_context 注入 | ✅ 每次写入都带 | ❌ 无 |

### 56.6 翻译：旁路型如何借鉴

**Hermes 做法**：Supermemory 在**源头**就做严格过滤，只将高质量的记忆写入存储。

**BlueCortexCE 现状**：BlueCortexCE 的 SessionEnd summary 倾向于总结一切，没有 trivial 过滤。

**翻译：旁路型如何借鉴**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE SessionEnd summary 前增加 trivial 过滤 | 如果 user message 只有 "ok"/"thanks"/"sure"，跳过该轮的 summary 生成 |
| **高** | BlueCortexCE Observation 写入时增加最小长度过滤 | < 10 chars 的 user message + < 10 chars 的 assistant response 组合，跳过 observation |
| **中** | BlueCortexCE Observation content 格式化为结构化格式 | `[user]: ... / [assistant]: ...` 替代纯文本，方便后续 LLM 理解对话边界 |
| **中** | BlueCortexCE SessionEnd 考虑 batch observation | 不只是 summary，SessionEnd 可以同时生成多个精选的 Observation（而非依赖后续的 refinement） |

---

## 57. Dialectic Synthesis 对比 — Honcho peer.chat() vs RetainDB ask_user（v5.2 新增）

> **文件**: `plugins/memory/honcho/session.py:415-460`（Honcho dialectic），`plugins/memory/retaindb/__init__.py:269-275`（RetainDB ask_user）
> **本节为 v5.2 新增**，分析两种不同的 LLM-powered 用户理解合成方法。

### 57.1 两种架构的本质差异

Hermes 中有两个 Provider 支持 LLM 合成式的用户理解（dialectic synthesis），但架构完全不同：

| 维度 | Honcho `peer.chat()` | RetainDB `ask_user()` |
|------|---------------------|----------------------|
| 架构 | 多 Agent（AI peer ↔ User peer） | 单一用户问答 |
| 观察模式 | `ai_observe_others` 控制交叉/自我 | 无观察模式 |
| 推理级别 | 动态（query length 驱动） | 静态（low/medium/high 参数） |
| Session 关联 | 跨 session（conclusion 写回 profile） | Session 粒度 |
| 本地控制 | 极少（云端全权处理） | 极少（云端全权处理） |

### 57.2 Honcho dialectic_query — 多 Agent 观察架构

```python
# plugins/memory/honcho/session.py:415-460
def dialectic_query(self, session_key: str, query: str,
                    reasoning_level: str | None = None,
                    peer: str = "user") -> str:
    level = reasoning_level or self._dynamic_reasoning_level(query)  # 动态推理级别

    if self._ai_observe_others:
        # 交叉观察路由
        if peer == "ai":
            ai_peer_obj = self._get_or_create_peer(session.assistant_peer_id)
            result = ai_peer_obj.chat(query, reasoning_level=level) or ""
        else:
            # AI peer 观察 User peer
            ai_peer_obj = self._get_or_create_peer(session.assistant_peer_id)
            result = ai_peer_obj.chat(query, target=session.user_peer_id, reasoning_level=level)
    else:
        # 自我观察路由
        peer_id = session.assistant_peer_id if peer == "ai" else session.user_peer_id
        target_peer = self._get_or_create_peer(peer_id)
        result = target_peer.chat(query, reasoning_level=level)
```

**Honcho 的核心模型**：
- **两个 Peer**：User Peer（代表用户）和 AI Peer（代表 AI 自身）
- **交叉观察**：`ai_observe_others=True` 时，AI Peer 可以"观察"User Peer（通过 `target=user_peer_id` 参数）
- **AI 观察 AI**：AI Peer 也可以观察自己的历史（`peer="ai"`）
- **Conclusion 写回**：通过 `create_conclusion` 写回 Honcho 云端，跨 session 持久化

### 57.3 RetainDB ask_user — 简化问答架构

```python
# plugins/memory/retaindb/__init__.py:269-275
def ask_user(self, user_id: str, query: str, reasoning_level: str = "low") -> dict:
    return self.request("POST", f"/v1/memory/profile/{quote(user_id, safe='')}/ask", json_body={
        "project": self.project,
        "query": query,
        "reasoning_level": reasoning_level,  # 低/中/高
    }, timeout=8.0)
```

**RetainDB 的核心模型**：
- **单一 User**：没有 AI peer，只有 User
- **直接问答**：直接对用户 memory 提问，返回合成答案
- **推理级别参数化**：`low` / `medium` / `high` 三档（由调用方根据 query 长度选择）
- **Prefetch 支持**：在 `_prefetch_dialectic` 中预取，下个 turn 使用

### 57.4 推理级别决策对比

**Honcho（动态）**：
```python
# plugins/memory/honcho/session.py:475-480
def _dynamic_reasoning_level(self, query: str) -> str:
    n = len(query)
    if n < 120:
        return "low"
    if n < 400:
        return "medium"
    return "high"
```

**RetainDB（静态参数）**：
```python
# plugins/memory/retaindb/__init__.py:589-594
@staticmethod
def _reasoning_level(query: str) -> str:
    n = len(query)
    if n < 120:
        return "low"
    if n < 400:
        return "medium"
    return "high"
```

**两者算法完全相同**！都是基于 query length 的简单分段（120/400 chars）。

### 57.5 BlueCortexCE `/api/context/generate` 当前实现

BlueCortexCE 的 `/api/context/generate`（ContextService）只有一个实现，没有分层推理机制。

### 57.6 翻译：旁路型如何借鉴

**Honcho 的设计思想**：多 Agent 观察模型——AI 不仅要理解用户，还要理解自身。这对旁路型有借鉴意义：**BlueCortexCE 可以提供"AI 自我认知"的端点**（类似 Honcho 的 `peer="ai"`）。

**RetainDB 的设计思想**：单一用户问答 + 推理级别参数化。这更简单直接：**BlueCortexCE 可以提供推理级别参数**（类似 `reasoning_level`），让消费方控制 LLM 合成深度。

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE `/api/context/generate` 增加 `reasoning_level` 参数 | low（简单匹配）/medium（中等推理）/high（深度综合），类似 RetainDB 的三级参数 |
| **中** | BlueCortexCE 增加 `/api/context/generate` AI-self 查询模式 | 类似 Honcho 的 `peer="ai"`，允许查询"AI 过去对这个项目的理解" |
| **低** | BlueCortexCE 考虑 Conclusion 写回机制 | 类似 Honcho `create_conclusion`，允许消费方将"结论"写回 profile |

---

## 58. RetainDB Agent Self-Model — SOUL.md 播种 + Self-Model Prefetch 机制（v5.2 新增）

> **文件**: `plugins/memory/retaindb/__init__.py:505-530`（seed），`plugins/memory/retaindb/__init__.py:579-620`（prefetch + 组装）
> **本节为 v5.2 新增**，详细分析 RetainDB 的 Agent Self-Model 机制——如何将 SOUL.md 播种到云端并在每次启动时检索回来。

### 58.1 设计意图

RetainDB 是**唯一实现 Agent Self-Model** 的 Provider。它的设计理念是：
- Agent 的**自我认知**（SOUL.md 定义的身份、价值观、工作方式）应该作为记忆被存储和检索
- 这使得 Agent 的自我认知可以在**跨 session** 中保持一致
- 同时允许多个 Agent（不同 persona）共享同一个 RetainDB 实例

### 58.2 SOUL.md 播种（initialize 时）

```python
# plugins/memory/retaindb/__init__.py:505-528
def _seed_soul(self, content: str) -> None:
    """Seed the agent's SOUL.md content as its self-model."""
    try:
        self._client.seed_agent_identity(self._agent_id, content, source="soul_md")
    except Exception as exc:
        logger.debug("RetainDB soul seed failed: %s", exc)

def initialize(self, session_id: str, **kwargs) -> None:
    # ...
    from hermes_constants import get_hermes_home
    hermes_home_path = get_hermes_home()
    db_path = hermes_home_path / "retaindb_queue.db"
    self._queue = _WriteQueue(self._client, db_path)

    # Seed agent identity from SOUL.md in background
    soul_path = hermes_home_path / "SOUL.md"
    if soul_path.exists():
        soul_content = soul_path.read_text(encoding="utf-8", errors="replace").strip()
        if soul_content:
            threading.Thread(
                target=self._seed_soul,
                args=(soul_content,),
                name="retaindb-soul-seed",
                daemon=True,  # 后台异步，不阻塞 initialize
            ).start()
```

**关键细节**：
1. 在 `initialize()` 中启动一个 **daemon thread** 异步播种
2. 读取 `SOUL.md` 的原始内容（与 BlueCortexCE 的 SOUL.md 完全相同）
3. 调用 `seed_agent_identity` API 将内容写入 RetainDB 云端

### 58.3 Self-Model 检索（prefetch + 组装）

```python
# plugins/memory/retaindb/__init__.py:579-586
def _prefetch_agent_model(self) -> None:
    """Retrieve agent's self-model and cache it."""
    try:
        model = self._client.get_agent_model(self._agent_id)
        if model.get("memory_count", 0) > 0:
            with self._lock:
                self._agent_model = model
    except Exception as exc:
        logger.debug("RetainDB agent model prefetch failed: %s", exc)

# plugins/memory/retaindb/__init__.py:598-620
def prefetch(self, query: str, *, session_id: str = "") -> str:
    # ...
    if agent_model and agent_model.get("memory_count", 0) > 0:
        model_lines: list[str] = []
        if agent_model.get("persona"):
            model_lines.append(f"Persona: {agent_model['persona']}")
        if agent_model.get("persistent_instructions"):
            model_lines.append("Instructions:\n" + "\n".join(f"- {i}" for i in agent_model["persistent_instructions"]))
        if agent_model.get("working_style"):
            model_lines.append(f"Working style: {agent_model['working_style']}")
        if model_lines:
            parts.append("[RetainDB Agent Self-Model]\n" + "\n".join(model_lines))
```

**检索结果的结构**（由云端从 `seed_agent_identity` 内容中解析）：

| 字段 | 来源 | 说明 |
|------|------|------|
| `persona` | SOUL.md 中解析出的 persona 描述 | AI 的人格特征 |
| `persistent_instructions` | SOUL.md 中解析出的持久指令 | Agent 的行为准则 |
| `working_style` | SOUL.md 中解析出的工作风格 | Agent 的工作方式 |
| `memory_count` | 云端统计 | 关联的记忆数量 |

**注意**：RetainDB 云端对 SOUL.md 内容做了**LLM 解析**，将非结构化的内容转换为结构化的 persona/instructions/working_style 字段。

### 58.4 三个并行 Prefetch 的完整流程

RetainDB 的 `queue_prefetch` 同时启动**三个**后台 prefetch：

```python
# plugins/memory/retaindb/__init__.py:543-556
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    if not self._client:
        return
    # Wait for previous prefetch threads to avoid accumulation
    for t in self._prefetch_threads:
        t.join(timeout=2.0)

    threads = [
        threading.Thread(target=self._prefetch_context, args=(query,), name="retaindb-ctx", daemon=True),
        threading.Thread(target=self._prefetch_dialectic, args=(query,), name="retaindb-dialectic", daemon=True),
        threading.Thread(target=self._prefetch_agent_model, name="retaindb-agent-model", daemon=True),
    ]
    self._prefetch_threads = threads
    for t in threads:
        t.start()
```

**三个并行 prefetch 的目标**：

| Prefetch | 目标内容 | LLM 成本 | 用途 |
|----------|---------|---------|------|
| `_prefetch_context` | profile + query context overlay | 无（vector search） | 快速事实检索 |
| `_prefetch_dialectic` | `ask_user` 合成答案 | 有（低/中/高级） | 深度用户理解 |
| `_prefetch_agent_model` | Agent 自我认知 | 无（API 直接返回） | 身份一致性 |

### 58.5 与 BlueCortexCE 对比

| 维度 | RetainDB Agent Self-Model | BlueCortexCE |
|------|--------------------------|--------------|
| SOUL.md 播种 | ✅ 启动时自动播种到云端 | N/A（SOUL.md 定义在本地） |
| 自我认知检索 | ✅ prefetch 后注入 system prompt | ❌ 无 |
| 结构化解析 | ✅ 云端 LLM 解析 persona/instructions | ❌ 无 |
| 跨 Agent 共享 | ✅ 多个 agent 可以播种到同一 RetainDB | ❌ 无 |
| 注入方式 | `[RetainDB Agent Self-Model]` 块注入 | N/A |

### 58.6 翻译：旁路型如何借鉴

**核心洞察**：RetainDB 的 Agent Self-Model 机制将 SOUL.md 从"静态定义"变成"动态记忆"。在 Hermes 内置型架构中，这意味着 Agent 的自我认知可以跨 session 累积和更新。

**对 BlueCortexCE 的意义**：在旁路型架构中，消费方（Claude Code/OpenClaw）自己管理 SOUL.md，不需要我们提供 Agent Self-Model。

**但有借鉴价值的点**：
1. **自我认知端点**：BlueCortexCE 可以提供 `/api/agent/profile` 端点，允许消费方播种 agent 身份信息
2. **Phase 3 Structured Extraction 的 category**：RetainDB 云端将 SOUL.md 解析为 persona/instructions/working_style，Phase 3 的 extraction templates 可以借鉴这个分类

---

## 60. ContextCompressor Phase 1-4 压缩算法（v5.3 新增）

**文件**: `agent/context_compressor.py` (1091 lines)

### 60.1 架构定位

`ContextCompressor` 是 Hermes 内置的**默认 Context Engine**，通过插件机制可替换为 LCM 等第三方引擎。架构设计在 `agent/context_engine.py`（184行，抽象基类）中定义：

```python
# agent/context_engine.py:1-50
class ContextEngine(ABC):
    """Base class all context engines must implement."""
    # Core interface: update_from_response(), should_compress(), compress()
    # Optional: on_session_start/end/reset, get_tool_schemas(), handle_tool_call()
    # Token tracking: last_prompt_tokens, last_completion_tokens, threshold_tokens
```

**Context Engine 在 Hermes 中的位置**：记忆系统（MemoryManager/Provider）与 Context Engine 是**两个独立子系统**，通过 run_agent.py 协同工作：
- MemoryManager: 负责外部记忆的读写/prefetch
- ContextEngine: 负责对话上下文的压缩

### 60.2 四阶段压缩算法

```python
# agent/context_compressor.py:927-1070
def compress(self, messages, current_tokens=None, focus_topic=None):
    # Phase 1: Prune old tool results (cheap, no LLM call)
    messages, pruned_count = self._prune_old_tool_results(...)

    # Phase 2: Determine boundaries
    compress_start = self.protect_first_n  # 默认3条消息
    compress_end = self._find_tail_cut_by_tokens(messages, compress_start)
    turns_to_summarize = messages[compress_start:compress_end]

    # Phase 3: Generate structured summary (LLM call)
    summary = self._generate_summary(turns_to_summarize, focus_topic=focus_topic)

    # Phase 4: Assemble compressed message list
    compressed = [protected head messages...]
    if not _merge_summary_into_tail:
        compressed.append({"role": summary_role, "content": summary})
    compressed.extend([protected tail messages...])
    return self._sanitize_tool_pairs(compressed)
```

#### Phase 1: Tool Result Pruning（无 LLM 成本）

`_prune_old_tool_results()` 在不调用 LLM 的情况下做三件事：

1. **Deduplication**（去重）：相同文件多次读取，只保留最新完整副本，旧的替换为 `[Duplicate tool output — same content as a more recent call]`
   ```python
   # context_compressor.py:397-413
   for i in range(len(result) - 1, -1, -1):
       if role == "tool" and len(content) >= 200:
           h = md5(content[:12])
           if h in content_hashes:
               result[i] = {**msg, "content": "[Duplicate tool output — same content as a more recent call]"}
   ```

2. **长工具输出摘要**：>200 字符的工具输出替换为 1 行摘要
   ```python
   # context_compressor.py:50-75
   def _summarize_tool_result(tool_name, tool_args, tool_content) -> str:
       # [terminal] ran `npm test` -> exit 0, 47 lines output
       # [read_file] read config.py from line 1 (3,400 chars)
       # [search_files] content search for 'compress' in agent/ -> 12 matches
   ```

3. **工具参数截断**：assistant 消息中的超长 tool_call arguments（前200字符 + `...truncated]`）

**Token-budget tail protection**：Pruning 边界由 `protect_tail_tokens`（默认 `tail_token_budget`）决定，而非固定的 `protect_last_n` 消息数。

#### Phase 2: 边界确定

```python
# context_compressor.py:871-916
def _find_tail_cut_by_tokens(self, messages, head_end, token_budget=None):
    # 硬性最少保护3条消息
    # 软上限 = token_budget * 1.5（允许在 oversized 消息上溢出）
    # 永远不在 tool_call/result 组内切割（_align_boundary_backward）
```

**关键参数**：
- `tail_token_budget = threshold_tokens * summary_target_ratio` = `context_length * 50% * 20%` = `10% context_length`
- `max_summary_tokens = min(context_length * 5%, 12,000)` = 约 8K for 200K context

#### Phase 3: LLM Summarization（见第 62 节详情）

#### Phase 4: 组装 + 工具对完整性

```python
# context_compressor.py:755-808
def _sanitize_tool_pairs(self, messages):
    # 1. 删除没有匹配 assistant tool_call 的 orphaned tool results
    # 2. 为没有 tool result 的 orphaned tool_calls 注入 stub:
    #    "Result from earlier conversation — see context summary above"
```

**Summary 角色选择**：避免与 head/tail 角色连续冲突。若都冲突，则 merge 到第一个 tail message 内部（而非独立消息）。

### 60.3 与 BlueCortexCE 对比

| 维度 | Hermes ContextCompressor | BlueCortexCE |
|------|------------------------|--------------|
| 压缩触发 | `should_compress()` 基于 token threshold | N/A（我们不压缩自己的上下文） |
| Pruning 算法 | Hash 去重 + 工具输出摘要 | N/A |
| 摘要模板 | 11段式结构化模板 | N/A |
| 迭代摘要 | `_previous_summary` 跨压缩轮次传递 | N/A |
| 工具对完整性 | `_sanitize_tool_pairs()` 自动修复 | N/A |

### 60.4 翻译：旁路型如何借鉴

**核心价值**：ContextCompressor 展示了一套完整的"上下文压缩"工程实现。虽然 BlueCortexCE 不压缩自己的上下文，但其中的思想可以借鉴：

1. **Phase 1 Pruning 思想**：对于 BlueCortexCE 的 `/api/context/generate` 响应，可以实现类似去重——相同 session 的重复 observation 只返回最新
2. **Tool output 摘要化**：BlueCortexCE 的 observation 存储可以考虑类似策略：大量相似 observation 时做合并而非全部返回
3. **11段式 Summary Template**：可以直接借鉴到 BlueCortexCE 的 session summary 功能

**优先级**：中（Phase 1/2/4 思想对 BlueCortexCE 参考价值一般；11段式模板参考价值高）

---

## 61. Critical Bug：`on_pre_compress` Hook 返回值被丢弃（v5.3 新增）

### 61.1 Bug 详情

**文件**: `run_agent.py:6800-6810`

```python
# run_agent.py:6800-6810
def _compress_context(self, messages, system_message, *, approx_tokens=None, focus_topic=None):
    # ...
    # Pre-compression memory flush: let the model save memories before they're lost
    self.flush_memories(messages, min_turns=0)

    # Notify external memory provider before compression discards context
    if self._memory_manager:
        try:
            self._memory_manager.on_pre_compress(messages)  # ❌ 返回值被丢弃！
        except Exception:
            pass

    compressed = self.context_compressor.compress(messages, current_tokens=approx_tokens, focus_topic=focus_topic)
```

**问题**：`memory_manager.on_pre_compress(messages)` 返回一个合并的字符串（来自所有 provider 的 pre-compression insight），但这个返回值**完全没有被使用**——既没有传给 `compress()` 也没有注入到任何地方。

### 61.2 `on_pre_compress` 的设计意图

根据 `agent/memory_provider.py:163-175` 的文档注释：

```python
def on_pre_compress(self, messages: List[Dict[str, Any]]) -> str:
    """Called before context compression discards old messages.

    Use to extract insights from messages about to be compressed.
    Return text to include in the compression summary prompt so the
    compressor preserves provider-extracted insights. Return empty
    string to skip.
    """
```

**设计意图**：让 memory provider 在压缩前"抢救"即将丢弃的上下文，将关键洞察以文本形式返回，**注入到压缩 summary prompt 中**，供 LLM summarizer 在生成摘要时保留这些信息。

### 61.3 现有 Provider 的 on_pre_compress 实现

| Provider | 实现 | 返回值 |
|----------|------|--------|
| Base MemoryProvider | 默认空实现 | `""` |
| ByteRover | 后台异步写入外部存储 | `""` |
| Honcho | 未覆盖 | `""` |
| RetainDB | 未覆盖 | `""` |
| Hindsight | 未覆盖 | `""` |

**没有任何 Provider 返回非空字符串**，所以即使 bug 修复，当前也不会有实际效果。但 ByteRover 的实现表明设计意图是：在这个时机将即将丢失的上下文 flush 到外部 memory 系统。

### 61.4 Bug 影响

1. **Memory Provider 无法在压缩前"抢救"即将丢失的上下文** — ByteRover 等 provider 虽有实现，但返回值被丢弃意味着没有任何文本被注入到 summary prompt
2. **压缩摘要丢失 Provider 洞察** — 外部 memory provider 提取的洞察无法被 LLM summarizer 保留在压缩摘要中
3. **这是一个静默失败** — 没有任何日志或警告，开发者很难发现

### 61.5 正确修复方式

`on_pre_compress` 的返回值应该被传递给 `compress()` 并注入到 summary prompt 的 system preamble 中：

```python
# run_agent.py:6804（修复后）
if self._memory_manager:
    try:
        precompress_block = self._memory_manager.on_pre_compress(messages)
    except Exception:
        precompress_block = ""
else:
    precompress_block = ""

compressed = self.context_compressor.compress(
    messages,
    current_tokens=approx_tokens,
    focus_topic=focus_topic,
    precompress_insights=precompress_block,  # 新增参数
)
```

然后在 `_generate_summary()` 的 prompt 中注入：

```python
# context_compressor.py（修复后）
if precompress_insights:
    prompt += f"\n\n[External Memory Provider Pre-Compression Insights]\n{precompress_insights}"
```

### 61.6 与 BlueCortexCE 的关系

**架构差异**：BlueCortexCE 作为旁路型系统，不参与 Agent 的上下文压缩决策。这个 bug 不会直接映射到 BlueCortexCE。

**间接价值**：
- 这个 bug 说明 Hermes 的 memory provider 与 context engine 之间的**集成存在设计不完整**
- BlueCortexCE 的 `/api/context/generate` 实现应该确保：调用方传入的 session messages 压缩/整理逻辑与返回的 context 之间的关系是**明确的、无隐式丢弃**

**翻译：旁路型如何借鉴**

在 BlueCortexCE 中，我们可以提供类似 `on_pre_compress` 的机制：

1. **提取前钩子**：在 context 生成前，允许消费方注入 pre-processing 逻辑
2. **明确的返回值契约**：如果某个 hook 返回文本，必须明确这个文本是否/如何影响最终输出

**优先级**：低（对我们暂无直接影响，但提醒我们在设计 API 时要注意"返回值被丢弃"的静默失败模式）

---

## 62. 11段式 Summary Template + Iterative Update 机制（v5.3 新增）

### 62.1 Summary Template 完整结构

**文件**: `agent/context_compressor.py:538-610`

```python
_template_sections = f"""## Goal
[What the user is trying to accomplish]

## Constraints & Preferences
[User preferences, coding style, constraints, important decisions]

## Completed Actions
[Numbered list of concrete actions taken — include tool used, target, and outcome.
Format each as: N. ACTION target — outcome [tool: name]
Example:
1. READ config.py:45 — found `==` should be `!=` [tool: read_file]
2. PATCH config.py:45 — changed `==` to `!=` [tool: patch]
3. TEST `pytest tests/` — 3/50 failed: test_parse, test_validate, test_edge [tool: terminal]
Be specific with file paths, commands, line numbers, and results.]

## Active State
[Current working state — include:
- Working directory and branch (if applicable)
- Modified/created files with brief note on each
- Test status (X/Y passing)
- Any running processes or servers
- Environment details that matter]

## In Progress
[Work currently underway — what was being done when compaction fired]

## Blocked
[Any blockers, errors, or issues not yet resolved. Include exact error messages.]

## Key Decisions
[Important technical decisions and WHY they were made]

## Resolved Questions
[Questions the user asked that were ALREADY answered — include the answer so the next assistant does not re-answer them]

## Pending User Asks
[Questions or requests from the user that have NOT yet been answered or fulfilled. If none, write "None."]

## Relevant Files
[Files read, modified, or created — with brief note on each]

## Remaining Work
[What remains to be done — framed as context, not instructions]

## Critical Context
[Any specific values, error messages, configuration details, or data that would be lost without explicit preservation]
"""
```

### 62.2 Preamble：防止回答的指令

```python
# context_compressor.py:549-555
_summarizer_preamble = (
    "You are a summarization agent creating a context checkpoint. "
    "Your output will be injected as reference material for a DIFFERENT "
    "assistant that will continue the conversation. "
    "Do NOT respond to any questions or requests in the conversation — "
    "only output the structured summary. "
    "Do NOT include any preamble, greeting, or prefix."
)
```

**两个关键设计**：
1. "Do NOT respond to any questions" — 防止 summarizer 在摘要中回答问题
2. "for a DIFFERENT assistant" — 让 summarizer 以"交接"而非"继续"的视角工作

### 62.3 Iterative Update（跨压缩轮次）

当 `_previous_summary` 存在时（即不是第一次压缩），prompt 结构变为：

```
PREVIOUS SUMMARY:
{_previous_summary}

NEW TURNS TO INCORPORATE:
{content_to_summarize}

Update the summary using this exact structure. PRESERVE all existing information
that is still relevant. ADD new completed actions to the numbered list (continue numbering).
Move items from "In Progress" to "Completed Actions" when done.
Move answered questions to "Resolved Questions". Update "Active State" to reflect current state.
Remove information only if it is clearly obsolete.
```

**关键区别**：第一次压缩是 "Create a structured handoff summary from scratch"；后续压缩是 "Update the existing summary by incorporating new turns"。

### 62.4 Focus Topic（/compress <topic>）

当用户通过 `/compress <focus>` 指定主题时，prompt 末尾追加：

```python
# context_compressor.py:609-614
if focus_topic:
    prompt += f"""

FOCUS TOPIC: "{focus_topic}"
The user has requested that this compaction PRIORITISE preserving all information
related to the focus topic above. For content related to "{focus_topic}", include
full detail — exact values, file paths, command outputs, error messages, and decisions.
For content NOT related to the focus topic, summarise more aggressively...
The focus topic sections should receive roughly 60-70% of the summary token budget."""
```

**60-70% 预算聚焦**在 focus topic 相关的内容上，而非均匀分配。

### 62.5 与 BlueCortexCE 对比

| 维度 | Hermes Summary Template | BlueCortexCE |
|------|------------------------|--------------|
| Template 段数 | 11段 | N/A（我们不做 summarization） |
| 动作格式 | `N. ACTION target — outcome [tool: name]` | 类似但不区分 tool |
| 迭代更新 | ✅ `_previous_summary` 跨轮次 | ❌ 无 |
| Focus 聚焦 | ✅ 60-70% 预算集中 | ❌ 无 |
| 交接语气 | "different assistant" + "do not answer" | N/A |

### 62.6 翻译：旁路型如何借鉴

**对 BlueCortexCE 最有价值的借鉴**：这个模板**可以直接移植**用于 BlueCortexCE 的 session summary 生成。

我们可以用 `/api/sessions/{id}/summary` 端点返回类似的结构化摘要，而不是纯文本摘要。

```json
{
  "goal": "...",
  "constraints": "...",
  "completed_actions": [
    "1. READ config.py:45 — found `==` should be `!=`"
  ],
  "active_state": "...",
  "in_progress": "...",
  "blocked": "...",
  "key_decisions": "...",
  "resolved_questions": "...",
  "pending_user_asks": "...",
  "relevant_files": "...",
  "remaining_work": "...",
  "critical_context": "..."
}
```

**Phase 3 关联**：Phase 3 的 extraction templates 可以参考 `Completed Actions` 和 `Active State` 的格式。

**优先级**：高（11段式模板对 BlueCortexCE 的 session summary 功能有直接参考价值）

---

## 63. Anti-thrashing + Fallback 机制（v5.3 新增）

### 63.1 Anti-thrashing（防震荡）

```python
# context_compressor.py:307-330
def should_compress(self, prompt_tokens: int = None) -> bool:
    # 如果最近2次压缩每次节省都 <10%，跳过压缩
    if self._ineffective_compression_count >= 2:
        logger.warning(
            "Compression skipped — last %d compressions saved <10%% each. "
            "Consider /new to start a fresh session, or /compress <topic> "
            "for focused compression.",
            self._ineffective_compression_count,
        )
        return False
    return True
```

**判断标准**：连续 2 次压缩节省 token 均 <10%。这防止在内容极少的对话中无限循环压缩。

**用户引导**：`Consider /new` 或 `/compress <topic>` — 告知用户如何打破僵局。

### 63.2 Summary Model Fallback

```python
# context_compressor.py:710-740
# 如果 summary_model 不可用（404/503/model_not_found），fallback 到主模型
if (_is_model_not_found and self.summary_model
        and self.summary_model != self.model
        and not getattr(self, "_summary_model_fallen_back", False)):
    self.summary_model = ""  # empty = use main model
    return self._generate_summary(messages, summary_budget)  # retry immediately
```

### 63.3 Summary Generation Failure Fallback

当 LLM summarization 完全失败时，注入**静态 fallback marker** 而非静默丢弃：

```python
# context_compressor.py:1019-1028
if not summary:
    summary = (
        f"{SUMMARY_PREFIX}\n"
        f"Summary generation was unavailable. {n_dropped} conversation turns were "
        f"removed to free context space but could not be summarized. The removed "
        f"turns contained earlier work in this session. Continue based on the "
        f"recent messages below and the current state of any files or resources."
    )
```

**关键**：即使 LLM summarization 失败，也要告知模型"上下文被删除了"，而非静默丢失。

### 63.4 与 BlueCortexCE 对比

| 维度 | Hermes Anti-thrashing | BlueCortexCE |
|------|----------------------|--------------|
| 防震荡机制 | 2次<10%保存则跳过 | ❌ 无（我们不压缩） |
| Fallback chain | summary_model → main model | N/A |
| 静默失败防止 | ✅ 生成 static fallback marker | ❌ 无对应 |

### 63.5 翻译：旁路型如何借鉴

**对我们最有价值的点**：
1. **静默失败防止**：`/api/context/generate` 即使在某些 session 找不到记忆，也应该返回明确的"no memory found" marker，而非 empty 响应
2. **连续失败上报**：如果 context generation 连续失败 N 次，应该返回警告/降级响应，而非重复尝试

**优先级**：中（思想借鉴，具体实现不适用）

---

## 67. 待进一步确认（v5.4 更新）

# v6.0 本轮新增（2026-04-17 05:00）

本轮聚焦于两个之前未深入分析的内置工具：**Built-in Memory Tool**（MEMORY.md/USER.md bounded curated memory）和 **Session Search Tool**（FTS5 + LLM summarization），以及 **Holographic HRR Vector Store** 完整实现分析。

---

## 68. Built-in Memory Tool — 有界精选 + 冻结快照 + 原子写入（v6.0 新增）

### 68.1 核心架构：Frozen Snapshot Pattern

**文件**：`tools/memory_tool.py`

Built-in Memory Tool 采用 **Frozen Snapshot Pattern**，这是其最独特的设计：

```python
# memory_tool.py:90-93
class MemoryStore:
    # 两种并行状态：
    self._system_prompt_snapshot: Dict[str, str]  # session 开始时冻结，永不改变
    self.memory_entries / self.user_entries        # live state，随时可变
```

**核心原则**：
- 系统 prompt 中的 memory 在 session 开始时**快照注入一次**，之后不再改变
- Session 中途的 `memory add/replace/remove` **立即持久化到磁盘**，但不改变已注入的 system prompt
- 下一轮 session 开始时，新快照会包含最新的 memory

**为什么这样做**：保持 system prompt **前缀缓存稳定**。如果每次 memory 变更都更新 system prompt，prefix cache 就会失效，导致每轮都需要重新 tokenize 前缀（昂贵）。

### 68.2 写入安全：威胁扫描 + 文件锁

**注入防护**（`_scan_memory_content`，memory_tool.py:59-83）：

```python
_MEMORY_THREAT_PATTERNS = [
    # Prompt injection patterns
    r'ignore\s+(previous|all|above|prior)\s+instructions',
    r'you\s+are\s+now\s+',
    r'do\s+not\s+tell\s+the\s+user',
    r'system\s+prompt\s+override',
    r'disregard\s+(your|all|any)\s+(instructions|rules|guidelines)',
    # Exfiltration via curl/wget with secrets
    r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)',
    r'wget\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)',
    r'cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass|\.npmrc|\.pypirc)',
    r'authorized_keys',
    r'\$HOME/\.ssh|\~/\.ssh',
    r'\$HOME/\.hermes/\.env|\~/\.hermes/\.env',
]
```

- 检查不可见 Unicode 字符（U+200B zero-width space 等）
- 检查威胁 pattern，拒绝注入内容进入 system prompt

**原子写入**（`_write_file`，memory_tool.py:310-335）：

```python
# 先写临时文件，fsync，再 os.replace（同一文件系统上原子替换）
fd, tmp_path = tempfile.mkstemp(dir=str(path.parent), suffix=".tmp", prefix=".mem_")
with os.fdopen(fd, "w", encoding="utf-8") as f:
    f.write(content)
    f.flush()
    os.fsync(f.fileno())
os.replace(tmp_path, str(path))  # 原子替换
```

**文件锁**（`_file_lock`，memory_tool.py:115-140）：使用独立 `.lock` 文件而非锁住 data 文件本身，这样 `os.replace()` 的读者永远看到完整旧文件或完整新文件。

### 68.3 双存储 + 字符限制

- **MEMORY.md**：agent 个人笔记（环境事实、项目惯例、工具特点），限制 2200 chars
- **USER.md**：用户画像（偏好、沟通风格、工作流习惯），限制 1375 chars
- 字符限制（而非 token 限制）：`len(ENTRY_DELIMITER.join(entries))` — 模型无关
- `§` (section sign) 作为 entry 分隔符

### 68.4 Schema 指导（MEMORY_SCHEMA）

```python
MEMORY_SCHEMA = {
    "description": (
        "Save durable information to persistent memory that survives across sessions.\n\n"
        "WHEN TO SAVE (do this proactively, don't wait to be asked):\n"
        "- User corrects you or says 'remember this' / 'don't do that again'\n"
        "- User shares a preference, habit, or personal detail\n"
        "- You discover something about the environment (OS, installed tools, project structure)\n"
        "- You learn a convention, API quirk, or workflow specific to this user's setup\n\n"
        "TWO TARGETS: 'user' (who the user is) vs 'memory' (your notes)\n"
        "SKIP: trivial/obvious info, things easily re-discovered, raw data dumps, temporary task state.\n"
        "Do NOT save task progress — use session_search to recall those."
    ),
}
```

**关键约束**：
- 主动写入，不要等用户问
- 记忆优先级：用户偏好/纠正 > 环境事实 > 程序性知识
- **不能**保存任务进度 — 那属于 session_search 的职责

### 68.5 翻译：旁路型如何借鉴

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 旁路型借鉴思路 |
|------|-----------|-----------------|--------------|
| Frozen snapshot | 系统 prompt 快照不变，磁盘持久化 | 无（API 每次实时返回） | 可以为 `/api/context/generate` 提供"session snapshot" 模式，返回固定上下文，减少 token 变化 |
| 威胁扫描 | content 进入 system prompt 前必须扫描 | 无专门的注入防护 | Observation 内容进入外部 Agent 的 context 前，可提供可选的 content 安全扫描 API |
| 原子写入 | temp+fsync+replace | SessionWrite 原子性？ | 确认 SessionWrite 是否保证原子性 |
| 字符限制 | 固定 char 限制（非 token） | 无限制 | 可为 Observation 单条内容提供建议性 char 上限 |

**优先级**：中（Frozen Snapshot Pattern 思想对旁路型有参考价值，但具体实现不适用）

---

## 69. Session Search Tool — FTS5 + 三阶段截断 + 亲缘链排除（v6.0 新增）

### 69.1 双模式设计（Zero-LLM-Cost Browse vs LLM Summarization）

**文件**：`tools/session_search_tool.py:258-276`

```python
def session_search(query, role_filter=None, limit=3, db=None, current_session_id=None):
    # 模式1：无 query → recent sessions browse（零 LLM 调用）
    if not query or not query.strip():
        return _list_recent_sessions(db, limit, current_session_id)
    # 模式2：有 query → FTS5 search + LLM summarization
    ...
```

**模式1（Browse）**：直接查 DB 返回 `session_id / title / source / started_at / last_active / message_count / preview`，**零 LLM 成本**，即时返回。

**模式2（Search）**：
1. FTS5 搜索 → 50 条原始匹配
2. 按 parent session 去重（compression/delegation 创建的子 session 合并到父 session）
3. 排除当前 session 亲缘链
4. 截断到 ~100k chars（围绕匹配位置优化）
5. Gemini Flash 并行 summarization（最多 5 sessions，每条最多 10000 tokens）
6. 返回 per-session summary + metadata

### 69.2 三阶段匹配定位截断算法（`_truncate_around_matches`，session_search_tool.py:59-145）

这是该工具最复杂也最有技术含量的部分：

```python
def _truncate_around_matches(full_text, query, max_chars=100_000):
    # 阶段1：完整短语匹配（case-insensitive）
    phrase_positions = [m.start() for m in re.finditer(re.escape(query_lower), text_lower)]

    # 阶段2：多 term  proximity co-occurrence（200-char 窗口内所有 term 都出现）
    if not phrase_positions:
        terms = query_lower.split()
        rarest = min(terms, key=lambda t: len(term_positions[t]))
        for pos in term_positions[rarest]:
            if all(any(abs(p-pos) < 200 for p in term_positions[t]) for t in terms if t != rarest):
                match_positions.append(pos)

    # 阶段3：单个 term 位置（最后兜底）
    if not match_positions:
        for t in terms:
            for m in re.finditer(re.escape(t), text_lower):
                match_positions.append(m.start())

    # 选择覆盖最多匹配位置的窗口（25% 前置偏差）
    best_start = 0
    best_count = 0
    for candidate in match_positions:
        ws = max(0, candidate - max_chars // 4)  # 25% before
        we = ws + max_chars
        count = sum(1 for p in match_positions if ws <= p < we)
        if count > best_count:
            best_count = count
            best_start = ws
```

**关键设计**：
- **Phrase > Proximity > Individual Term** 三层降级
- Proximity 窗口 200 chars（经验值）
- 选择"覆盖最多匹配"的窗口，而非简单的前 N 字符
- 25% 前置偏差（匹配前的 context 更有信息量）

### 69.3 亲缘链排除机制（`_resolve_to_parent`，session_search_tool.py:298-322）

Compression 和 delegation 会产生子 session，但用户的对话主体在父 session 中：

```python
def _resolve_to_parent(session_id):
    visited = set()
    sid = session_id
    while sid and sid not in visited:
        visited.add(sid)
        session = db.get_session(sid)
        parent = session.get("parent_session_id")
        if parent:
            sid = parent
        else:
            break
    return sid
```

- **上行遍历**：一直找到根 parent session
- 排除当前 session 的**整个亲缘链**（不仅仅是当前 session 本身）
- `seen_sessions[resolved_sid]` 保证每个根 session 只出现一次

### 69.4 Fallback：当 LLM Summarizer 不可用时

```python
# session_search_tool.py:463-468
if result:
    entry["summary"] = result
else:
    # Fixes #3409: 不要静默丢弃匹配到的 session
    preview = (conversation_text[:500] + "\n…[truncated]")
    entry["summary"] = f"[Raw preview — summarization unavailable]\n{preview}"
```

**这是文档 v5.3 提到的 Anti-thrashing 模式在 session_search 中的具体体现**：
- 如果 summarizer 完全失败，返回 raw preview 而非空结果
- 避免"匹配到了但结果丢失"的无声失败

### 69.5 翻译：旁路型如何借鉴

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 旁路型借鉴 |
|------|-----------|-----------------|----------|
| Zero-LLM-Cost Browse | 无 query → 直接 DB 返回 session 列表 | `/api/memory/sessions` 返回完整记录列表 | 可在 SearchService 中添加"recent sessions" 快速路径（不需要 embedding/search） |
| 截断算法 | phrase → proximity → individual term | 无智能截断（简单 limit） | 可借鉴三层降级 + 窗口优化策略 |
| 亲缘链排除 | delegation/compression 子 session 合并到父 | 无 delegation 概念 | Session 合并对于多轮对话记忆完整性有价值 |
| 静默失败防止 | summarizer 失败 → raw preview | SearchService 失败时直接返回空 | 可借鉴"降级返回 raw content" 而非静默失败 |

**优先级**：高（截断算法和静默失败防止对旁路型有直接参考价值）

---

