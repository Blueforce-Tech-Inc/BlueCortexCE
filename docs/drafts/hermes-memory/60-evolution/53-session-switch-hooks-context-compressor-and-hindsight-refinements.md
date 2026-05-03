# 上游新提交分析（2026-04-28 ~ 05-02）：Session Switch Hooks + ContextCompressor 多项修复 + Hindsight Single-Writer 重构

**扫描范围**：`cec0af02..origin/main`（`5d3be898`），共 **991 commits**

**最后更新**：2026-05-02

---

## 目录

- [§1 `13683c08` — `on_session_switch()` 新钩子（重大架构）](#s1)
- [§2 `b194617d` — ContextCompressor off-by-one tail 保护修复](#s2)
- [§3 `f0dc919f` — Token 估算现在包含 system prompt + tool schemas（重大 bug fix）](#s3)
- [§4 `dad02174` — Honcho session cache RLock 线程安全修复](#s4)
- [§5 `0a5ee01e` — Hindsight flush-on-switch 改为 writer queue 路由](#s5)
- [§6 `c38dac74` — Hindsight session switch 时 flush buffered turns + drop stale prefetch](#s6)
- [§7 `0565497d` — Hindsight 单 writer 线程替代 per-sync daemon thread（消除 shutdown race）](#s7)
- [§8 `6ea5699e` — Compression 辅助模型失败时仍通知用户](#s8)
- [§9 `e553f6f3` — Memory scrub surface 收缩到 3 个 site（安全强化）](#s9)
- [§10 `142b4bf3` — session_search recent mode 改为按 last activity 排序](#s10)
- [§11 `b29b709a` — Codex tool-call history summaries sanitization（`call_id` vs `id`）](#s11)
- [§12 CE 可执行借鉴汇总](#s12)

---

## §1 `13683c08` — `on_session_switch()` 新钩子（重大架构）<a id="s1"></a>

**Commit**: `13683c0842f08f6f5e05cec5ccf97c29a37f77f9`（2026-04-29，Teknium）
**PR**: #17409 | **Fixes**: #6672

### 问题背景

之前 MemoryProvider 的 `initialize()` 中初始化的 per-session 状态（如 Hindsight 的 `_session_id`、`_document_id`、`_session_turns`、`_turn_counter`）在 `AIAgent.session_id` 中途轮换时不会刷新。导致 `/resume`、`/branch`、`/reset`、`/new` 和 context compression 触发 session_id 轮换后，provider 继续往旧 session 的 record 里写数据。

### 架构变更

**MemoryProvider ABC 新增钩子**：

```python
def on_session_switch(
    self,
    new_session_id: str,
    *,
    parent_session_id: str = "",
    reset: bool = False,
    **kwargs,
) -> None:
    """Notify provider that AIAgent.session_id has rotated mid-process."""
    # No-op default for backward compat
```

- `reset=True`：表示 `/reset` 或 `/new`，provider 应 flush 累积的 per-session buffer
- `reset=False`：表示 `/resume`、`/branch`、compression，逻辑对话继续，lineage 应保留

**MemoryManager fan-out**：

```python
def on_session_switch(self, new_session_id: str, *, parent_session_id="", reset=False, **kwargs):
    for provider in self._providers:
        try:
            provider.on_session_switch(new_session_id, parent_session_id=parent_session_id, reset=reset, **kwargs)
        except Exception as e:
            logger.warning(f"Provider {provider.name} on_session_switch failed: {e}")
```

隔离的 try/except，确保一个坏的 provider 不阻塞其他 provider。空/None `new_session_id` 是 no-op（避免 shutdown 路径破坏 provider 状态）。

**run_agent.py**：

- `_sync_external_memory_for_turn` 现在向 `sync_all()` 和 `queue_prefetch_all()` 传递 `session_id=self.session_id`
- Compression block 在触发 session 轮换时额外调用 `_memory_manager.on_session_switch(reason='compression')`

**cli.py**：

- `new_session()` 触发 `reset=True, reason='new_session'`
- `_handle_resume_command` 触发 `reset=False, reason='resume'`，携带 `parent_session_id`
- `_handle_branch_command` 触发 `reset=False, reason='branch'`

**gateway/run.py**：

- `_handle_resume_command` 现在驱逐缓存的 AIAgent（`evict cached agent`），与 `/branch` 和 `/reset` 模式一致

**Hindsight reference implementation**：

```python
def on_session_switch(self, new_session_id, *, parent_session_id="", reset=False, **kwargs):
    # 1. 更新 _session_id
    # 2. mint 新的 _document_id（防止 vectorize-io/hindsight#1303 覆盖）
    # 3. 清空 _session_turns / _turn_counter / _turn_index
    #    — 确保 in-flight batches 不会在新的 document_id 下 flush
    # 4. parent_session_id 只在提供时才覆盖（避免 bare switch 时覆盖）
```

**测试覆盖**：

- `tests/agent/test_memory_session_switch.py`：新文件，ABC no-op default / manager fan-out / failure isolation / empty-id no-op / session_id propagation through sync_all+queue_prefetch_all / Hindsight state transitions
- `tests/cli/test_branch_command.py`：验证 /branch 触发正确参数
- `tests/gateway/test_resume_command.py`：验证 /resume 驱逐 cached agent

### CE 借鉴

**高优先级**：CE 的 `ContextService` / `AgentService` 在 `/new`、`/reset`、`/resume` 时如何处理 session 切换？

- CE 目前是否有等效的 "provider notification on session switch" 机制？
- 如果没有，需要在 `SessionService` 或 `ContextService` 中新增类似 `on_session_switch()` 的 hook 链
- 特别是：`StructuredExtractionService` 如果缓存了 per-session 状态（如 pending buffer），在 session switch 时如何处理？

---

## §2 `b194617d` — ContextCompressor off-by-one tail 保护修复 <a id="s2"></a>

**Commit**: `b194617d00981d8ea850f100ed262698090963da`（2026-04-28，0z!）

短对话（消息数 < `protect_tail_count`）时，tail 保护边界 off-by-one 修复：

```python
# Before（错误）
min_protect = min(protect_tail_count, len(result) - 1)

# After（正确）
min_protect = min(protect_tail_count, len(result))
```

当 `protect_tail_count=3` 且 `len(result)=3` 时，旧代码 `min(3, 2)=2`，导致最后一条消息不在保护范围内；新代码 `min(3, 3)=3`，保护全部 3 条。

**CE 影响**：CE 的 `ContextCompressor`（如果有）需检查类似边界条件。

---

## §3 `f0dc919f` — Token 估算现在包含 system prompt + tool schemas（重大 bug fix）<a id="s3"></a>

**Commit**: `f0dc919f92c5327cf8033e06c039126f1288e89c`（2026-04-30，Teknium）
**Fixes**: #14695, #6217

### 问题

用户可见的 `/compress` banner 和 post-compression `last_prompt_tokens` 写回只计算了 raw message transcript（chars/4）。对于 15KB system prompt + 30 tool schemas（~26KB），4 条消息的 transcript 表面看起来 ~45 tokens，实际请求压力是 ~10.5K tokens — **234x 差距**。

两个后果：

1. Banner 显示 `Compressing … (~45 tokens)…`，用户困惑为什么 compression 触发（实际压力 10K+ tokens）
2. Post-compression `last_prompt_tokens` 写回遗漏 tool schemas，导致下次 `should_compress()` 检查用低估的 usage 比较，compression 触发过晚（可能超过小 context 模型限制）

### 修复

在所有用户可见位置和 post-compression 写回处，用 `estimate_request_tokens_rough()` 替换 `estimate_messages_tokens_rough()`：

```python
# Before
from agent.model_metadata import estimate_messages_tokens_rough

# After
from agent.model_metadata import estimate_request_tokens_rough
```

受影响的调用点：

- `run_agent.py`：post-compression `last_prompt_tokens` 写回、post-tool `should_compress()` fallback
- `cli.py`：`/compress` banner + summary
- `gateway/run.py`：gateway `/compress` banner + summary
- `tui_gateway/server.py`：TUI `/compress` status + summary
- `acp_adapter/server.py`：ACP `/compact` before/after

**CE 借鉴**：

**高优先级** — CE 的 token 估算是否也只算 transcript 而忽略 system prompt + tool schemas？如果是，需修复以防 compression 触发过晚。检查 `should_compress()` / `estimate_tokens()` 相关路径。

---

## §4 `dad02174` — Honcho session cache RLock 线程安全修复 <a id="s4"></a>

**Commit**: `dad021745000b717cef99e31d88b426eecf61ba`（2026-04-21）

**问题**：Honcho `HonchoSessionManager` 的 `_cache` 读写没有锁保护。并发 gateway sessions（如 Telegram + Discord 同时访问 Honcho）会竞争，导致静默丢失结论或记忆写入。

**修复**：

```python
self._cache_lock = threading.RLock()

def get_session(self, key):
    with self._cache_lock:
        if key in self._cache:
            return self._cache[key]
    # 昂贵的 I/O 操作在锁外执行（Honcho persistence 是 source of truth）
    ...
    with self._cache_lock:
        self._cache[key] = session
```

所有 cache 变更在 RLock 下进行；I/O（Honcho API 调用）在锁外执行，避免长时间持锁。

**CE 借鉴**：

CE 的 `ContextService` 或 `ObservationService` 是否有类似的并发读写 cache？如果有静态 `self._sessions` 或类似字典，需检查线程安全性。

---

## §5 `0a5ee01e` — Hindsight flush-on-switch 改为 writer queue 路由 <a id="s5"></a>

**Commit**: `0a5ee01e487a5a4e0e3637ecce6c2a41546c9457`（2026-04-29，Teknium）
**PR**: #17447（follow-up）

**问题**：原 flush-on-switch spawn 一个 bare `threading.Thread` 覆盖 `self._sync_thread`（aliased 到 long-lived writer thread）。两个后果：

1. 无法与 writer queue 序列化 — 如果旧 session 的 retains 还在 `_retain_queue` 中排队，flush 与 writer 并发执行，两者同时调用 `aretain_batch` 到同一 `document_id`
2. `self._sync_thread.join(timeout=5.0)` 尝试 join long-lived writer（永不退出），实际是 no-op

**修复**：将 flush closure 放入 `_retain_queue`（通过 `_ensure_writer().put()`），天然 FIFO 顺序，无需新线程：

```python
# Before（错误）
self._sync_thread = threading.Thread(target=_flush, daemon=True)
self._sync_thread.start()

# After（正确）
if not self._shutting_down.is_set():
    self._ensure_writer()
    self._register_atexit()
    self._retain_queue.put(_flush)  # FIFO behind pending retains
```

---

## §6 `c38dac74` — Hindsight session switch 时 flush buffered turns + drop stale prefetch <a id="s6"></a>

**Commit**: `c38dac742b22c55581d4105a9727e55ba620a984`（2026-04-29，nicoloboschi）

**两个数据丢失 / leak 缺口**：

### 6a — Buffered turns 在 session switch 时静默丢失

当 `retain_every_n_turns > 1` 时，`on_session_switch` 无条件清空 `_session_turns` 而不 flush。用户在这些中间轮换时（/reset, /new, /resume, /branch, compression）缓冲的 turns 消失。

**注意**：`commit_memory_session()` → `on_session_end()` 在 `/reset` 时先于 `on_session_switch` 执行，但 Hindsight 没有实现 `on_session_end`，所以 buffer 在那步存活下来，在 clear 时死亡。`/resume`、`/branch`、compression 完全跳过 `commit_memory_session`。

**修复**：在轮换前快照旧 identifiers，spawn 一次 final retain 写入 OLD `document_id`：

```python
if self._session_turns:
    old_id = self._session_id
    old_doc_id = self._document_id
    old_parent = self._parent_session_id
    old_turns = list(self._session_turns)
    old_index = self._turn_index
    # ... snapshot everything ...
    # then rotate
    self._session_id = new_id
    self._document_id = self._mint_document_id()
    self._session_turns = []
    self._turn_index = 0
    # spawn retain for old session under old_doc_id
```

### 6b — Stale `_prefetch_result` 跨 session switch 泄露

如果旧 session 的 `queue_prefetch` 跑了但结果还没被 `prefetch()` 消费，`on_session_switch` 留下了缓存的 recall text。下一 session 首条 `prefetch()` 会返回来自旧 session bank/query 的文本。

**修复**：join 任何 in-flight `_prefetch_thread`（3s bounded），然后在轮换前清空 `_prefetch_result`：

```python
if self._prefetch_thread and self._prefetch_thread.is_alive():
    self._prefetch_thread.join(timeout=3.0)
with self._prefetch_lock:
    self._prefetch_result = ""
```

**CE 借鉴**：

**高优先级** — CE 的 `StructuredExtractionService` 如果有 per-session buffer（如 `pending_extractions`），在 session switch 时是否 flush？是否有类似 prefetch result 泄露的风险？

---

## §7 `0565497d` — Hindsight 单 writer 线程替代 per-sync daemon thread（消除 shutdown race）<a id="s7"></a>

**Commit**: `0565497dcc2f566fc40249b2db65184bc6466628`（2026-04-28，nicoloboschi）

### 问题

旧模式：每次 `sync_turn()` spawn 一个 daemon thread 做 `aretain_batch` 网络写入。CLI 退出时与 interpreter shutdown 竞争——最后一次 retain 可能在 asyncio "cannot schedule new futures" guard 触发后到达，产生噪音日志并静默丢失最后未保存的 turn。

### 修复

切换到**单 writer + queue**模型：

```
sync_turn() → snapshot state → enqueue job → writer thread drains sequentially
```

**Shutdown 流程**：

1. 新的 `sync_turn()` / `queue_prefetch()` 调用被丢弃（不排队）
2. sentinel 唤醒 writer 完成 in-flight work
3. `shutdown()` join writer（10s）然后 null client
4. 在第一次 `sync_turn()` 时注册 idempotent `atexit` hook（覆盖不走 `MemoryManager.shutdown_all()` 的 Ctrl-C 等路径）

**CE 借鉴**：

CE 的异步写入（DB 操作、网络调用）是否使用类似 queue + single-worker 模式？还是每操作 spawn 一个 task/channel？如果是后者，需要检查 shutdown 路径是否有竞态。

---

## §8 `6ea5699e` — Compression 辅助模型失败时仍通知用户 <a id="s8"></a>

**Commit**: `6ea5699e3fc35971ef6ed65587033d072e3ee410`（2026-04-27，Teknium）
**PR**: #16775

### 问题

之前辅助摘要模型永久错误（404/503/model_not_found）时 fallback 到主模型的过程是静默的。用户配置损坏（broken aux model config）无法修复，因为没有任何提示。

### 修复

跟踪两个新字段：

```python
_last_aux_model_failure_error: Optional[str] = None
_last_aux_model_failure_model: Optional[str] = None
```

在 aux model 错误时（无论 fallback 是否成功）记录：

```python
self._last_aux_model_failure_error = str(e)[:220]
self._last_aux_model_failure_model = self.summary_model
```

在三个位置暴露：

- gateway hygiene auto-compress：`ℹ` note to platform adapter
- gateway `/compress`：`ℹ` line appended to reply
- CLI via `_emit_warning`：deduped on `(model, error)` 避免重复 spam

**CE 借鉴**：

CE 的 `LlmService` 是否有 fallback 机制？如果有，fallback 发生时是否通知用户？还是静默 fallback？Structured Extraction 失败后的 fallback 行为需要明确 UX 策略。

---

## §9 `e553f6f3` — Memory scrub surface 收缩到 3 个 site（安全强化）<a id="s9"></a>

**Commit**: `e553f6f3e4c61adc529615caea07f2a50a81f555`（2026-04-27，Erosika）

### 过度修复问题

之前的 boundary-hardening commits 有三处过度修复（将 plugin-specific policy 拉入 shared core paths）：

1. **`gateway/run.py`** hardcoded `'## Honcho Context'` literal split — plugin-format heading 在 framework 代码中，可能截断包含该字面量的合法输出
2. **`run_agent.run_conversation`** scrub `user_message` 和 `persist_user_message` — 用户输入中包含 `<memory-context>` 字面量时不应被静默删除（用户文本是神圣的）
3. **`_build_assistant_message`** 在持久化前 scrub model 输出 — 模型输出的包含字面量标记的合法文档/代码不应被静默修改

### 修复

1. 移除 `gateway/run.py` 的 `'## Honcho Context'` literal split；保留 generic `sanitize_context()`
2. 移除用户输入的 scrub — `build_memory_context_block` 是唯一合法的 emitter
3. 移除 persist-time assistant 输出 scrub — streaming scrubber 在 delta 层面捕获真实泄露，persist-time scrub 是多余
4. `_fire_stream_delta` 的 `lstrip` 改为仅对 stream 首个 delta 执行（之前每条 delta 都 strip leading newlines）

**净结果**：scrub surface 从 8 个 site 收缩到 3 个：

- `StreamingContextScrubber`（output deltas）
- `plugin→backend send`
- `build_memory_context_block`（input validation）

新增：`build_memory_context_block` 在发现 provider 返回已包装文本时记录 warning（provider contract violation）。

**CE 借鉴**：

CE 的 context 输出是否有类似过度 scrub 问题？检查 `/api/context/generate` 和 context injection 路径是否有不当的 tag stripping。

---

## §10 `142b4bf3` — session_search recent mode 改为按 last activity 排序 <a id="s10"></a>

**Commit**: `142b4bf3ce1b490e0c15f9c3c3d1a9a26e6f8de6`（2026-04-30，simbam99）

### 变更

```python
# Before
ORDER BY s.started_at DESC

# After (when order_by_last_active=True)
ORDER BY last_active DESC, s.started_at DESC, s.id DESC
```

`last_active` 在 compression-tip projection 后计算，确保压缩会话的 live tip 在正确 slot 可见，而非显示已压缩的初始消息。

**CE 借鉴**：

CE 的 session list API 是否按创建时间或最后活动时间排序？如果是按创建时间，且有压缩会话场景，需考虑切换到按最后活动时间排序，保证 UX 一致性。

---

## §11 `b29b709a` — Codex tool-call history summaries sanitization <a id="s11"></a>

**Commit**: `b29b709a71273cccbd9752035acb8104dc5d7cc5`（2026-04-29，stephenschoettler）

### 问题

OpenAI Responses API 使用 `call_id` 而非 `id` 作为 tool call 的标识符。`_get_tool_call_id()` 只查 `id` 字段，导致 Responses API tool call 的 `"id": ""` 产生孤立的 tool_result。

### 修复

```python
# Before
return tc.get("id", "") or ""

# After
return tc.get("call_id", "") or tc.get("id", "") or ""
```

同时在 `run_agent.py` 的 summary 前加 `_sanitize_api_messages()` 调用（与主循环相同 safety net）。

**CE 借鉴**：

CE 的 tool call pairing 是否只依赖 `id` 字段？如果使用多模型（Claude / GPT / 其他），需检查是否所有模型都使用相同字段名，或需要类似的多字段 fallback。

---

## §12 CE 可执行借鉴汇总 <a id="s12"></a>

### 高优先级

| # | 发现 | 来源 | CE 影响 | 行动 |
|---|------|------|---------|------|
| 1 | `on_session_switch()` 钩子 | `13683c08` | CE 无 provider switch 通知机制 | 评估在 `SessionService` 添加 hook 链的可行性 |
| 2 | Token 估算遗漏 system prompt + schemas | `f0dc919f` | CE `should_compress()` 可能触发过晚 | 检查 CE token 估算路径是否包含 system prompt |
| 3 | Session switch 时 buffered data 丢失 | `c38dac74` | CE per-session buffer 可能未 flush | 检查 `StructuredExtractionService` buffer flush 逻辑 |
| 4 | Prefetch result 跨 session 泄露 | `c38dac74` | CE 如果有 prefetch/cache 可能有同问题 | 检查 context prefetch 机制 session isolation |

### 中优先级

| # | 发现 | 来源 | CE 影响 | 行动 |
|---|------|------|---------|------|
| 5 | 单 writer + queue 替代 per-op thread | `0565497d` | CE 异步 DB 操作是否有 shutdown race | 检查 `ObservationRepository` / `SummaryRepository` 写路径 |
| 6 | Honcho RLock cache 线程安全 | `dad02174` | CE cache 字典是否线程安全 | 检查 `ContextService._sessions` 等静态 cache |
| 7 | Scrub surface 过度收缩教训 | `e553f6f3` | CE 是否有类似过度 scrub | 检查 CE context injection 的 tag stripping |
| 8 | Auxiliary model failure 静默 fallback | `6ea5699e` | CE LLM fallback 是否有 UX 通知 | 评估 fallback 时的 user-facing feedback |

### 低优先级

| # | 发现 | 来源 | CE 影响 |
|---|------|------|---------|
| 9 | Session list 按 last_active 排序 | `142b4bf3` | 评估 CE session list 是否需同样修改 |
| 10 | Tool call `call_id` vs `id` 多模型兼容 | `b29b709a` | 检查 CE tool call ID 字段一致性 |
| 11 | Tail protection off-by-one | `b194617d` | 检查 CE 是否有类似边界条件 |

---

## 文档信息

- **扫描范围**：`cec0af02..origin/main`（`5d3be898`），991 commits
- **记忆相关发现**：11 个主要 commit 涵盖新钩子、bug fix、安全强化、Hindsight 重构
- **最大非记忆相关领域**：TUI 性能（0399d4b9 等）、computer-use、Backup、Approval、Feishu、Discord、IRC、Kanban
- **CE 阻断项**：无新的本地 Hermes Agent repo 删除问题（本次成功 clone 并 checkout origin/main）
