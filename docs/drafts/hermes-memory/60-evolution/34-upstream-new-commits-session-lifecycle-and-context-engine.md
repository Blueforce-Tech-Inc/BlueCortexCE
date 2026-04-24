# 34 — 上游新提交分析（2026-04-21 ~ 04-25）

**覆盖范围**：`e69526be` → `origin/main`（`6f1eed39`），约 1590 commits。内存相关核心提交 13 个。

---

## §1 ContextEngine ABC 强化（`a9a4416c`）

### 1.1 问题根因

手动 `/compress` 命令通过 gateway handler 访问 `tmp_agent.context_compressor._align_boundary_forward` 和 `_find_tail_cut_by_tokens`。这两个方法属于 `ContextCompressor` 私有，不在通用 `ContextEngine` ABC 上。当用户安装了第三方 context engine 插件（如 LCM Engine）时：

```
'LCMEngine' object has no attribute '_align_boundary_forward'
```

### 1.2 修复方案

**新增 ABC 方法** `has_content_to_compress(messages)`：

```python
# agent/context_engine.py
class ContextEngine(ABC):
    def has_content_to_compress(self, messages: List[Dict[str, Any]]) -> bool:
        """Quick check: is there anything in messages that can be compacted?"""
        return True  # 默认返回 True（总是尝试）

# agent/context_compressor.py
class ContextCompressor(ContextEngine):
    def has_content_to_compress(self, messages: List[Dict[str, Any]]) -> bool:
        compress_start = self._align_boundary_forward(messages, self.protect_first_n)
        compress_end = self._find_tail_cut_by_tokens(messages, compress_start)
        return compress_start < compress_end
```

**`focus_topic` 加入 ABC 签名**：

```python
# agent/context_engine.py
class ContextEngine(ABC):
    def compress(
        self,
        messages: List[Dict[str, Any]],
        current_tokens: int = None,
        focus_topic: str = None,  # 新增参数
    ) -> List[Dict[str, Any]]:
        """focus_topic: Optional topic string from manual /compress <focus>."""
```

**Gateway 简化**：

```python
# gateway/run.py
# 之前（访问私有方法）：
compressor = tmp_agent.context_compressor
compress_start = compressor._align_boundary_forward(msgs, compressor.protect_first_n)
compress_end = compressor._find_tail_cut_by_tokens(msgs, compress_start)
if compress_start >= compress_end: ...

# 之后（调用 ABC 方法）：
if not compressor.has_content_to_compress(msgs):
    return "Nothing to compress yet ..."
```

**向后兼容 fallback**：

```python
# run_agent.py
try:
    compressed = self.context_compressor.compress(
        messages, current_tokens=approx_tokens, focus_topic=focus_topic
    )
except TypeError:
    # 第三方 strict-sig 插件不接收 focus_topic
    compressed = self.context_compressor.compress(
        messages, current_tokens=approx_tokens
    )
```

**CE 借鉴**：CE 的 `StructuredExtractionService` 目前直接暴露内部方法给 gateway handler。未来应设计类似的 ABC interface，先用 `has_content_to_extract()` 做预检，避免调用私有方法。

---

## §2 Structured Content 安全处理（`1e8254e5`）

### 2.1 问题

压缩过程需要向消息 content 追加文本（compression note / summary merge）。代码直接做：

```python
existing = msg.get("content") or ""
msg["content"] = existing + "\n\n" + note  # 不安全！
```

当 `content` 是多模态 block list（`[{"type": "text", "text": "..."}, {"type": "image", "url": "..."}]`）时，直接字符串拼接会破坏数据结构。

### 2.2 修复

新增两个辅助函数：

```python
def _content_text_for_contains(content: Any) -> str:
    """Best-effort text view of message content for substring checks."""
    if content is None: return ""
    if isinstance(content, str): return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str): parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str): parts.append(text)
        return "\n".join(parts)
    return str(content)

def _append_text_to_content(content: Any, text: str, *, prepend: bool = False) -> Any:
    """Safely append/prepend plain text to any content structure."""
    if content is None: return text
    if isinstance(content, str):
        return text + content if prepend else content + text
    if isinstance(content, list):
        text_block = {"type": "text", "text": text}
        return [text_block, *content] if prepend else [*content, text_block]
    return text + str(content) if prepend else str(content) + text
```

**使用点 1** — 系统消息追加 compression note：

```python
existing = msg.get("content")
_compression_note = "[Note: Some earlier conversation turns have been compacted...]"
if _compression_note not in _content_text_for_contains(existing):
    msg["content"] = _append_text_to_content(
        existing,
        "\n\n" + _compression_note if isinstance(existing, str) and existing else _compression_note,
    )
```

**使用点 2** — Tail 消息前置 summary：

```python
merged_prefix = (summary + "\n\n--- END OF CONTEXT SUMMARY — respond to the message below, not the summary above ---\n\n")
msg["content"] = _append_text_to_content(msg.get("content"), merged_prefix, prepend=True)
```

**CE 借鉴**：CE 的 `mergeIntoContext()` 目前是否处理了多模态 content？需要检查 `ObservationSerializer` 和 context injection 代码。

---

## §3 语言感知 Summaries（`13294c2d`）

### 3.1 问题

压缩摘要总是用英文生成，注入到非英文对话时会造成语言混乱。

### 3.2 修复

在 `_summarizer_preamble` 中加一行语言感知指令：

```python
# agent/context_compressor.py
_SUMMARIZER_PREAMLBE = """\
This is a summary of a {lang} language conversation...  # 新增一行

Generate a concise summary of the conversation above in the SAME LANGUAGE as the conversation.
"""
```

**CE 借鉴**：CE 的 StructuredExtraction 结果目前固定英文 prompt。若用户用中文对话，extraction 结果和 context 可能语言不一致。应考虑语言检测后调整 prompt。

---

## §4 Summary Model Fallback NameError 修复（`c0385873`）

### 4.1 问题

`_generate_summary()` 的签名是 `(turns_to_summarize, focus_topic)`。fallback 路径错误传递了 `(messages, summary_budget)`，其中 `messages` 不在 scope 内，触发 `NameError`。

### 4.2 修复

```python
# 之前（错误）：
summary, tokens_used = await self._generate_summary(
    messages, summary_budget
)

# 之后（正确）：
summary, tokens_used = await self._generate_summary(
    turns_to_summarize, focus_topic
)
```

**CE 借鉴**：CE 的 extraction fallback 路径是否测试过？需要验证 `EXTRACTION_ENABLED=true` 且 primary model 失败时，fallback 是否正常工作。

---

## §5 Session 生命周期大重构（`7cb06e3b` + `8275fa59`）

### 5.1 架构变更概览

**删除 `on_session_reset` hook**：`on_session_reset(new_session_id)` 被完全移除。

**原因**：OpenViking 的 session 历史跨 `/new` 和 `/compress` 透明处理，extraction 是幂等的，不需要 rebind 到新 session_id。Session 边界只需要触发 extraction。

**变更文件**：
| 文件 | 变更 |
|------|------|
| `agent/memory_provider.py` | 删除 `on_session_reset()` base method |
| `agent/memory_manager.py` | 删除 `on_session_reset()` fan-out |
| `plugins/memory/openviking/__init__.py` | 删除 `on_session_reset()` override |
| `cli.py` / `run_agent.py` | 替换为 `commit_memory_session` / `rotate_memory_session` |
| `tests/` | 替换覆盖率测试 |

### 5.2 新接口

**`AIAgent.commit_memory_session(messages)`**：
- 只调用 `memory_manager.on_session_end()`（触发 OV extraction）
- 不做 rebind，保持 provider 存活

**`AIAgent.rotate_memory_session(new_sid, messages)`**：
- 替换原来的 `commit_memory_session` + `reinitialize_memory_session`
- 单次调用，collapse 两个 wrapper

**`MemoryManager.restart_session(new_session_id)`**：
- 对实现了 `reset_session()` 的 provider 调用 `reset_session()`
- 对没实现的 provider fallback 到 `initialize()`（兼容非 OpenViking provider）
- 内置 provider 被跳过（无 per-session state）

### 5.3 OpenViking 的 `reset_session()`

```python
# plugins/memory/openviking/__init__.py
def reset_session(self, new_session_id: str):
    # 1. 等待 in-flight 后台线程
    self._pending_semaphore.acquire()
    try:
        # 2. 重置 per-session 计数器
        self._session_msg_count = 0
        self._last_sync_idx = 0
        # 3. POST /api/v1/sessions 创建新 OV session
        # （不关闭 HTTP client，避免 /new 时的连接开销）
    finally:
        self._pending_semaphore.release()
```

### 5.4 调用点

**CLI `new_session()`**：

```python
# cli.py
# 1. 先 commit（确保 OV extraction 在正确 session 上运行）
await agent.commit_memory_session(messages)
# 2. session_id 切换
agent.session_id = new_id
# 3. 再 reinitialize（新 session 立即可用）
await agent.reinitialize_memory_session(new_id)
```

**`_compress_context()`**：

```python
# run_agent.py
if self._session_db:
    # 1. commit
    await self.commit_memory_session(messages)
    # 2. session_id split
    self._session_id = self._new_session_id()
    # 3. reinitialize
    await self.reinitialize_memory_session(self._session_id)
```

### 5.5 CE 借鉴

CE 目前使用的是 `on_session_end` / `on_pre_compress` hook 体系。OpenViking 的 `reset_session()` 模式（wait for in-flight → reset counters → create new remote session）值得参考，当 CE 支持多 session 时可能需要类似机制。

---

## §6 OpenViking Commit on /new and Context Compression（`7856d304`）

### 6.1 问题根因

OpenViking 的 extraction 依赖 `POST /api/v1/sessions/{id}/commit`。修复前 CLI 有两条路径在切换 `session_id` 时没有 commit：

1. **`/new`**（`cli.py new_session()`）— 调用 `flush_memories()` 写 MEMORY.md 后直接丢弃旧 `session_id`，OV session 从未 commit，extraction 丢失。
2. **`/compress` 和 auto-compress**（`_compress_context()`）— split 了 SQLite session 但 OV provider 仍指向旧 `session_id`，所有同步到 OV 的消息静默孤立。

### 6.2 修复

引入 session 过渡生命周期，轻量于完整 shutdown：

```python
# cli.py new_session():
await agent.commit_memory_session(messages)      # commit 旧 session
agent.session_id = new_id
await agent.reinitialize_memory_session(new_id)  # 初始化新 session

# run_agent.py _compress_context():
await self.commit_memory_session(messages)      # commit
self._session_id = self._new_session_id()
await self.reinitialize_memory_session(self._session_id)  # reinit
```

**CE 借鉴**：CE 的 `flush_memories()` 在 session 切换时是否 commit 了所有 active providers？需要检查 session reset 路径。

---

## §7 Session Finalize Hooks on Expiry Flush（`260ae621`）

### 7.1 问题

Gateway session 过期时，执行了 `flush_memories()` 但没有触发 `on_session_finalize` hooks。

### 7.2 修复

```python
# gateway/run.py
# 在 expiry flush 流程末尾：
for hook in self._session_finalize_hooks:
    await hook(session_id=session_id, reason="idle_expiry")
```

**注意**：doc 33 中已经记录了 `32-upstream-new-commits-session-hooks-and-context-engine.md`，这个 commit 在那个 doc 之后。所以本篇 doc 34 的 `§7` 与 doc 33 的 §1 是连续覆盖关系。

**CE 借鉴**：CE 的 `/api/session/end` 是否触发了所有 configured hooks？需要验证 expiry 场景。

---

## §8 Shell Hooks — 用户脚本作为 Hook 回调（`3988c3c2`）

### 8.1 架构概览

用户可在 `config.yaml` 的 `hooks:` block 中声明 shell 脚本，脚本在 plugin hook 事件上触发（`pre_tool_call`, `post_tool_call`, `pre_llm_call`, `subagent_stop` 等）。

### 8.2 关键设计

**零侵入集成**：在现有 `PluginManager._hooks` dict 上注册闭包，不修改 `invoke_hook()` 调用点。

**安全模型**：
- `subprocess.run(shell=False)` via `shlex.split` — 无 shell 注入
- 首次使用需用户同意，allowlist 持久化到 JSON
- 绕过方式：`--accept-hooks`、`HERMES_ACCEPT_HOOKS=1`、`hooks_auto_accept`

**数据流**：
```
hook event → _make_callback(spec) → _spawn(spec, stdin_json) → subprocess
                                                     ↓
                                             stdout JSON → return
                                                     ↓
                                    block tool / inject context pre-LLM
```

**脚本响应 shapes**（支持 Claude Code 兼容）：
```python
# 阻止工具调用：
{"decision": "deny", "reason": "..."}

# 注入手写上下文：
{"decision": "allow", "inject": {"context": "..."}}

# 完全接管：
{"decision": "handled", "response": "..."}
```

**新 hook 事件** `subagent_stop`：在 `delegate_task` 子 agent 退出后触发。

### 8.3 CE 借鉴

CE 的 MCP tool handler 是否支持 pre/post tool hook？Shell hook 模式比直接代码修改更安全（用户可控、无需修改核心代码）。建议 CE 未来支持类似的可插拔 hook 系统。

---

## §9 Redact Config Bridge Fix（`0e235947`）

### 9.1 问题

`agent/redact.py` 在**模块导入时**快照 `_REDACT_ENABLED = os.environ.get("HERMES_REDACT_SECRETS")`。但 `hermes_cli/main.py` 在 `setup_logging()` 调用时**导入**了 `agent.redact`，而 `setup_logging()` 在 config.yaml bridge 之前运行。所以用户在 `config.yaml` 中设置 `security.redact_secrets: false` 会被静默忽略。

### 9.2 修复

在 `hermes_cli/main.py` 的 `setup_logging()` **之前** bridge：

```python
# hermes_cli/main.py（setup_logging 之前）
if "HERMES_REDACT_SECRETS" not in os.environ:
    cfg_path = get_hermes_home() / "config.yaml"
    if cfg_path.exists():
        with open(cfg_path) as f:
            sec_cfg = yaml.safe_load(f).get("security", {})
        redact_val = sec_cfg.get("redact_secrets")
        if redact_val is not None:
            os.environ["HERMES_REDACT_SECRETS"] = str(redact_val).lower()
```

`.env` 仍优先（仅在未设置时才从 config.yaml 读取）。

**CE 借鉴**：CE 的安全配置（API key 脱敏、logging level 等）是否有类似的 config-vs-env 优先级问题？

---

## §10 Plugin Slash Commands + emit_collect（`51ca5759`）

### 10.1 emit_collect()

新增 `HookRegistry.emit_collect()`，与 `emit()` 相同触发 handler，但**收集非 None 返回值**：

```python
# gateway/hooks.py
async def emit_collect(self, event: str, **kwargs) -> List[Dict[str, Any]]:
    """Fire handlers and collect non-None return values."""
    results = []
    for handler in self._handlers.get(event, []):
        result = await self._call_handler(handler, **kwargs)
        if result is not None:
            results.append(result)
    return results
```

向后兼容：fire-and-forget telemetry hooks 仍通过 `emit()` 工作。

### 10.2 Decision Protocol

返回 `{'decision': 'deny'|'handled'|'rewrite'|'allow'}` 的 handler 可拦截 slash command 分发，统一了原本分散的 `pre_gateway_command` hook surface。

**CE 借鉴**：CE 的 gateway（如果未来有）是否需要类似 decision-capable hooks？

---

## §11 Honcho Context Injection 全面重构（`cc6e8941`）

### 11.1 插件变更（5-tool surface）

| 工具 | 说明 |
|------|------|
| `honcho_context` | 原有，base context injection |
| `honcho_summary` | 原有，session summary injection |
| `honcho_card` | 原有，representation + card |
| `honcho_recall` | 原有，semantic recall |
| **`honcho_reasoning`** | **新增第四工具**，split LLM reasoning from context |

### 11.2 双层 Context Injection

| 层 | 触发 | 内容 |
|----|------|------|
| Base context | `contextCadence` | summary + representation + card |
| Dialectic supplement | `dialecticCadence` | 多轮 LLM 调用增量补充 |

**dialecticCadence 默认从 1 改为 3**：减少 ~66% 的 Honcho LLM 调用。

### 11.3 Multi-pass Dialectic Depth

```python
for pass_i in range(1, max_dialectic_passes + 1):
    dialectic_result = self._honcho_reasoning(...)
    signal = self._measure_signal_strength(dialectic_result)
    if signal > STRONG_SIGNAL_THRESHOLD:
        break  # 提前退出
```

### 11.4 核心修改（3 文件，约 20 行）

| 文件 | 变更 |
|------|------|
| `agent/memory_manager.py` | `sanitize_context()` 强化：strip 完整 `<memory-context>` blocks 和 system notes |
| `run_agent.py` | `gateway_session_key` param（稳定 per-chat Honcho sessions）、`on_turn_start()` 在 `prefetch_all()` 前调用 |
| `gateway/run.py` | `skip_memory=True` 在 2 个 temp agents 上（防止 orphan sessions） |

### 11.5 CE 借鉴

CE 的 context injection 目前是单层（StructuredExtraction 结果直接拼接）。双层 dialectic 模式（基础层 + 增量补充层）可能是未来提升 context efficiency 的方向。

---

## §12 Context Compressor 后续修复（`3128d9fc` + `f19ca50c`）

> 注：这两个 commit 在 doc 33 的覆盖范围内（`e69526be` 之前），此处仅记录供完整性。

| Commit | 内容 |
|--------|------|
| `f19ca50c` | Bug Fix #10896：`_ensure_last_user_message_in_tail()` 保证最后一个 user 消息在 protected tail 中 |
| `3128d9fc` | JSON-aware tool call args 截断：`_truncate_tool_call_args_json()` 保证 JSON 有效性，防 400 死循环 |

---

## §13 小结：新提交全景

| 类别 | 提交数 | 代表性 |
|------|--------|--------|
| ContextEngine ABC 强化 | 1 | `a9a4416c` |
| Structured content 安全 | 1 | `1e8254e5` |
| 语言感知摘要 | 1 | `13294c2d` |
| Fallback NameError | 1 | `c0385873` |
| Session lifecycle 重构 | 4 | `7cb06e3b` + `8275fa59` + `7856d304` + `260ae621` |
| Shell hooks | 1 | `3988c3c2` |
| Redact config bridge | 1 | `0e235947` |
| Plugin commands + emit_collect | 1 | `51ca5759` |
| Honcho 全面重构 | 1 | `cc6e8941` |

**核心趋势**：
1. **Session 生命周期统一化**：从多 hook（`on_session_end` + `on_session_reset`）简化为单一 commit path
2. **ContextEngine ABC 成熟化**：第三方插件兼容性成为一等公民
3. **Structured content 鲁棒性**：多模态 content 安全操作覆盖
4. **Hook 系统扩展**：shell scripts 作为可插拔 hook + decision-capable protocol
