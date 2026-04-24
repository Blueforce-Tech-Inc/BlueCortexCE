# 上游新提交分析（2026-04-25）：Write Origin Metadata + Tool Call Repair Chain

**Commit range**: `a5129c72..origin/main`（~30 commits）
**新增时间**: 2026-04-25 06:21 CST
**关联 Backlog**: [`11-research-backlog.md`](../11-research-backlog.md)

---

## §1 `6a957a74 fix(memory): add write origin metadata`（最重要）

**日期**: 2026-04-24 13:34
**作者**: helix4u
**文件**: `agent/memory_manager.py`、`agent/memory_provider.py`、`run_agent.py` + 2 test files

### 1.1 核心变更

在 `on_memory_write` hook 中新增**结构化 provenance metadata**，使外部 Memory Provider（如 CE）能够精确知道记忆写入的来源、上下文和会话信息。

**新增方法 `AIAgent._build_memory_write_metadata()`**（`run_agent.py`）：

```python
def _build_memory_write_metadata(
    self,
    *,
    write_origin: Optional[str] = None,
    execution_context: Optional[str] = None,
    task_id: Optional[str] = None,
    tool_call_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Build provenance metadata for external memory-provider mirrors."""
    metadata: Dict[str, Any] = {
        "write_origin": write_origin or getattr(self, "_memory_write_origin", "assistant_tool"),
        "execution_context": (
            execution_context
            or getattr(self, "_memory_write_context", "foreground")
        ),
        "session_id": self.session_id or "",
        "parent_session_id": self._parent_session_id or "",
        "platform": self.platform or os.environ.get("HERMES_SESSION_SOURCE", "cli"),
        "tool_name": "memory",
    }
    if task_id:
        metadata["task_id"] = task_id
    if tool_call_id:
        metadata["tool_call_id"] = tool_call_id
    return {k: v for k, v in metadata.items() if v not in (None, "")}
```

**Metadata 字段说明**：

| 字段 | 说明 | 示例值 |
|------|------|--------|
| `write_origin` | 写入来源类型 | `"assistant_tool"` / `"memory_flush"` |
| `execution_context` | 执行上下文 | `"foreground"` / `"flush_memories"` |
| `session_id` | 当前会话 ID | `"sess-abc123"` |
| `parent_session_id` | 父会话 ID（子 agent 场景） | `"parent-def456"` |
| `platform` | 来源平台 | `"cli"` / `"gateway"` |
| `tool_name` | 工具名称 | `"memory"` |
| `task_id` | 任务 ID（异步场景） | `str` |
| `tool_call_id` | 工具调用 ID（用于溯源） | `str` |

### 1.2 三处调用点（均传递 metadata）

**① 记忆 flush（`run_agent.py:memory_tool → flush_memories`）**：

```python
self._memory_manager.on_memory_write(
    args.get("action", ""),
    flush_target,
    args.get("content", ""),
    metadata=self._build_memory_write_metadata(
        write_origin="memory_flush",
        execution_context="flush_memories",
    ),
)
```

**② 内置 memory tool 调用（`run_agent.py:execute_single_tool`）**：

```python
self._memory_manager.on_memory_write(
    function_args.get("action", ""),
    target,
    function_args.get("content", ""),
    metadata=self._build_memory_write_metadata(
        task_id=effective_task_id,
        tool_call_id=tool_call_id,
    ),
)
```

**③ 异步工具分发（`run_agent.py:dispatch_async_tool`）**：

```python
self._memory_manager.on_memory_write(
    function_args.get("action", ""),
    target,
    function_args.get("content", ""),
    metadata=self._build_memory_write_metadata(
        task_id=effective_task_id,
        tool_call_id=getattr(tool_call, "id", None),
    ),
)
```

### 1.3 向后兼容设计：`_provider_memory_write_metadata_mode()`

`MemoryManager._provider_memory_write_metadata_mode()` 使用 **signature inspection** 判断 Provider 是否接受 metadata 参数，**无需修改 Provider ABC**：

```python
@staticmethod
def _provider_memory_write_metadata_mode(provider: MemoryProvider) -> str:
    """Return how to pass metadata to a provider's memory-write hook."""
    try:
        signature = inspect.signature(provider.on_memory_write)
    except (TypeError, ValueError):
        return "keyword"

    params = list(signature.parameters.values())
    if any(p.kind == inspect.Parameter.VAR_KEYWORD for p in params):
        return "keyword"   # **kwargs → keyword 传递
    if "metadata" in signature.parameters:
        return "keyword"   # 显式 metadata 参数 → keyword 传递

    accepted = [p for p in params if p.kind in (
        inspect.Parameter.POSITIONAL_ONLY,
        inspect.Parameter.POSITIONAL_OR_KEYWORD,
        inspect.Parameter.KEYWORD_ONLY,
    )]
    if len(accepted) >= 4:
        return "positional"  # 4+ 参数（已扩展 signature）→ positional 传递
    return "legacy"          # 3 参数 → 旧接口，不传 metadata
```

**三种传递模式**：

| 模式 | 条件 | 调用方式 |
|------|------|----------|
| `keyword` | 有 `**kwargs` 或显式 `metadata` 参数 | `on_memory_write(action, target, content, metadata=dict(...))` |
| `positional` | ≥4 个接受参数 | `on_memory_write(action, target, content, dict(...))` |
| `legacy` | 3 参数（原始 ABC signature） | `on_memory_write(action, target, content)` — 忽略 metadata |

**MemoryProvider ABC docstring 更新**：

```python
# agent/memory_provider.py
# old:
on_memory_write(action, target, content) — mirror built-in memory writes
# new:
on_memory_write(action, target, content, metadata=None) — mirror built-in memory writes
```

ABC 方法签名本身不变（保持 3 参数），实际传递由 `MemoryManager` 的 signature inspection 决定。

### 1.4 CE 借鉴分析

**现状（CE）**：CE 的 `ContextService` 在 `IngestionController.handleUserPrompt` / `SummaryGenerationService` / `ObservationService` 中直接写 DB，**无法区分**写入来源是：
- 用户 prompt 触发的自动摘要
- 显式 memory tool 调用
- Session 过期时的 flush
- 子 agent 委托任务

**CE 可借鉴的实现方向**：

| 方向 | 说明 | 实施难度 |
|------|------|----------|
| **Observation 增强字段** | 在 `ObservationEntity` 中增加 `write_origin`/`execution_context`/`platform` 字段 | 低 — Schema 变更 + 写入路径标注 |
| **IngestionController 分源** | `handleUserPrompt` 标注 `"user_prompt"` / `handleAssistantObservation` 标注 `"assistant_observation"` / Memory flush 标注 `"memory_flush"` | 低 — 已有 metadata map，直接写入字段 |
| **Signature-inspected Provider** | CE 的 `MemoryProvider` interface 暂无 ABC，但可以用 duck typing + inspect 实现类似兼容性 | 中 — 需引入 `inspect` 模块，避免破坏现有实现 |

**CE 实施路径（建议）**：

```python
# 伪代码：在 ObservationEntity 中新增字段
class ObservationEntity:
    # ... existing fields ...
    write_origin: str  # "user_prompt" | "assistant_observation" | "memory_flush" | "structured_extraction"
    execution_context: str  # "foreground" | "background" | "session_end"
    platform: str  # "cli" | "api" | "web" | "feishu"
    parent_session_id: Optional[str]  # 子 agent 场景
    task_id: Optional[str]  # 异步任务场景
    tool_call_id: Optional[str]  # 工具调用溯源
```

**关键启发**：signature inspection 是在不破坏向后兼容的前提下扩展 ABC 的精妙范式。CE 在设计 `MemoryProvider` interface 时应参考此模式。

---

## §2 Tool Call Repair Chain（三个相关修复）

### 2.1 `17fc84c2 fix: repair malformed tool call args in streaming assembly`

**日期**: 2026-04-22
**问题**: 流式组装路径检测到 JSON 损坏时设置 `has_truncated_tool_args=True`，但传递损坏的 args 给 truncation handler，导致 session 被强制结束。

**修复**: 在流式组装检测到 JSON 解析失败时，**先调用** `_repair_tool_call_arguments()` 尝试修复，修复成功则正常流程，只有真正无法修复才走 truncation handler。

### 2.2 `2d444fc8 fix(run_agent): handle unescaped control chars in tool_call arguments`

**日期**: 2026-04-24
**问题**: llama.cpp/Ollama 等本地模型后端在 JSON string 值内发送字面量 tab 和换行符（`\n`/`\t`），导致 `json.loads` 失败。

**修复**: 扩展 `_repair_tool_call_arguments()`，新增两轮修复：
- **Pass 0**: `json.loads(strict=False)` + 重新序列化为规范格式
- **Pass 4**: 转义 `0x00-0x1F` 控制字符后重试

### 2.3 `7a192b12 fix(run_agent): repair corrupted tool_call arguments before sending to provider`

**日期**: 2026-04-25
**问题**: 当 session 被 context compression 在 mid-tool-call 处分割时，assistant message 的 `tool_calls[*].function.arguments` 可能包含截断/无效 JSON。下次 turn 重放时被 provider 拒绝（HTTP 400 `invalid_tool_call_format`），导致无法恢复的死循环。

**修复**: 在 `AIAgent.run_conversation()` 的 `client.chat.completions.create()` **之前**增加防御性 sanitizer：
- 验证每个 `tool_calls[*].function.arguments` 的 `json.loads`
- 无效/空时替换为 `'{}'`
- 注入合成 tool response（或在已有 response 前面加 marker）保持 `tool_call_id` 配对
- 记录 session_id / message_index / preview 供可观测性

**关键架构意义**: 将 repair 作为"防御纵深"的最外层 chokepoint，在 send 路径捕获所有来源的损坏（compression split / manual edits / plugin bugs）。

### 2.4 Tool Call Repair 三层总结

| 层 | 位置 | 触发条件 | 修复策略 |
|----|------|----------|----------|
| **Streaming Assembly** | `run_agent.py` 流式组装 | JSON 解析失败 | `_repair_tool_call_arguments()` 后重试 |
| **Control Char** | `_repair_tool_call_arguments()` 内部 | 控制字符导致解析失败 | Pass 0 + Pass 4 转义重试 |
| **Send Chokepoint** | `run_agent.py:run_conversation()` 发送前 | 任意残留损坏 | 强制 `{}` + 合成 response |

**CE 借鉴**：CE 目前**无等效** tool call 截断修复机制。若 Claude Code 在 context compression 后重放损坏的 tool_calls，CE 不会修复，但 CE 作为旁路架构（不是直接转发消息给 LLM），受此问题影响较小。

---

## §3 `fd3864d8 feat(cli): wrap /compress in _busy_command`

**日期**: 2026-04-24
**问题**: 在 CLI 中执行 `/compress` 时，用户输入被经典 CLI prompt 接受并落入下一轮 prompt，导致每次压缩浪费一个按键。

**修复**: 用 `_busy_command('Compressing context...')` 包装 `/compress` 执行体，阻塞输入渲染（与其他慢命令如 `/skills install` 一致）。

**架构相关性**: 无

---

## §4 文件大小验证

| 文件 | 字节数 | 状态 |
|------|--------|------|
| `37-upstream-new-commits-write-origin-metadata-and-tool-call-repair.md` | ~13KB | ✅ < 50KB |
| 最大文件 `09-supermemory-capture-lifecycle.md` | 46922 | ✅ 无变化 |
