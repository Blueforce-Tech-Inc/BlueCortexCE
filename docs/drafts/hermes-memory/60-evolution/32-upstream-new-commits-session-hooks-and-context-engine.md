# 32 — 上游新提交分析（2026-04-25）：Session Finalize Hook + ContextEngine ABC 强化

**更新**：2026-04-25 01:35（本地 HEAD `e69526be` vs origin/main `c61547c0`，约 40 commit 差距）

---

## §1 `on_session_finalize` Hook（`260ae621`）

### 1.1 变更内容

**文件**：`gateway/run.py`

**位置**：Session 过期 flush 循环内，`_async_flush_memories` 之后

```python
# _expired_entries 遍历中
for key, entry in _expired_entries:
    try:
        await self._async_flush_memories(entry.session_id, key)
        try:
            from hermes_cli.plugins import invoke_hook as _invoke_hook
            _parts = key.split(":")
            _platform = _parts[2] if len(_parts) > 2 else ""
            _invoke_hook(
                "on_session_finalize",
                session_id=entry.session_id,
                platform=_platform,
            )
        except Exception:
            pass
```

### 1.2 完整 Hook 生命周期（更新版）

| Hook | 触发时机 | 调用方 |
|------|---------|--------|
| `on_session_start` | 会话开始 | `run_agent.py` / CLI |
| `on_session_reset` | `/new` 或 `/reset` | `run_agent.py` |
| `on_session_end` | 会话真正结束（exit/timeout） | `MemoryProvider.on_session_end()`（Provider 级别） |
| **`on_session_finalize`** | **Gateway session 过期 flush 后** | **GatewayRunner（独立于 Provider）** |

### 1.3 关键架构意义

1. **`on_session_finalize` 是 Gateway 层 hook，不经过 MemoryProvider**：这是与 `MemoryProvider.on_session_end` 的根本区别。`MemoryProvider.on_session_end` 是 Provider 级别的 hook，而 `on_session_finalize` 是插件系统级别的 hook，可在 Provider 之外触发。

2. **调用时序**：先 `_async_flush_memories`（将当前记忆状态持久化），再触发 `on_session_finalize`（通知插件系统 session 最终化）。这意味着插件可以在 `on_session_finalize` 中访问已 flush 的记忆状态。

3. **平台参数**：`platform` 从 session key 中提取（如 `telegram`、`discord`、`cli`），插件可根据平台做差异化处理。

4. **错误隔离**：`try/except` 包裹，hook 失败不影响 session cleanup 流程。

### 1.4 与 CE 的差距

CE（BlueCortexCE）的 `SessionEndEvent` / `SummaryEvent` 机制类似于 `on_session_end`，但**没有等效的 `on_session_finalize` plugin hook 系统**。CE 的 Gateway 层（如果存在）没有对应的 session finalize 钩子。

**可执行借鉴**：CE 的 `GatewayRunner`（如未来有）可考虑在 session 过期 flush 后触发类似的 finalize hook，允许插件在此做最终的跨 session 处理（如生成会话总结、通知外部系统等）。

---

## §2 ContextEngine ABC 强化（`a9a4416c`）

### 2.1 `has_content_to_compress()` 新增

```python
def has_content_to_compress(self, messages: List[Dict[str, Any]]) -> bool:
    """Quick check: is there anything in messages that can be compacted?
    
    Used by the gateway /compress command as a preflight guard —
    returning False lets the gateway report "nothing to compress yet"
    without making an LLM call.
    
    Default returns True (always attempt). Engines with a cheap way
    to introspect their own head/tail boundaries should override this
    to return False when the transcript is still entirely protected.
    """
    return True
```

**设计意图**：为 gateway `/compress` 命令提供 preflight guard，避免无意义地调用 LLM。

### 2.2 `compress()` 新增 `focus_topic` 参数

```python
def compress(
    self,
    messages: List[Dict[str, Any]],
    current_tokens: int = None,
    focus_topic: str = None,  # NEW
) -> List[Dict[str, Any]]:
```

**设计意图**：支持手动 `/compress <topic>` 引导压缩主题，引擎可优先保留与主题相关的信息。

### 2.3 Gateway `/compress` Handler 重构

**Before**：reach into `ContextCompressor._align_boundary_forward` / `_find_tail_cut_by_tokens`（private 方法）

**After**：调用 `context_engine.has_content_to_compress(messages)` 和标准 `compress(messages, focus_topic=focus_topic)`

**问题背景**：当第三方 ContextEngine 插件（如 LCM）激活时，原代码 reach into private 方法导致 `AttributeError: 'LCMEngine' object has no attribute '_align_boundary_forward'`。

### 2.4 与 CE 的差距

CE 的 `ContextService` 没有等效的 `has_content_to_compress()` preflight check。`/api/context/generate` 或压缩端点直接执行，无 cheap 预检。

**可执行借鉴**：
- 短期：在 CE 的 context 压缩端点添加 cheap preflight check（检查消息数量、token 估算）
- 中期：考虑 `ContextEngine` ABC 抽象，允许第三方压缩引擎插入

---

## §3 Hindsight Provider 新增（`edff2fbe`）

### 3.1 `bank_id_template` 功能

支持动态 bank_id 模板，在 `initialize()` 时根据运行时上下文派生：

| 占位符 | 含义 | 示例 |
|--------|------|------|
| `{profile}` | 活跃 Hermes profile | `default` |
| `{workspace}` | Hermes 工作区路径 | `/home/user/hermes` |
| `{platform}` | 平台（cli/telegram/discord 等） | `telegram` |
| `{user}` | 平台用户 ID | `ou_123` |
| `{session}` | Session ID | `sess_abc` |

**示例配置**：
```yaml
bank_id_template: "hermes-{user}-{profile}"
# 渲染结果（user=ou_123, profile=default）: "hermes-ou_123-default"
```

**安全处理**：不安全字符被 sanitize，空模板结果优雅降级。

### 3.2 架构意义

这使得 Hindsight 可以实现 **per-user / per-agent bank 隔离**，支持多租户场景（Gateway 多用户共用一个 Hindsight 实例，但数据隔离）。

### 3.3 与 CE 的差距

CE 的 PostgreSQL Schema 使用固定的 `bank_id = 'default'`，没有多租户隔离机制。

**可执行借鉴**：CE 的 `ObservationEntity` / `SummaryEntity` 可考虑添加 `user_id` 或 `profile` 字段，支持多用户隔离。

---

## §4 Hindsight Bug Fixes（2026-04-24 批量）

| Commit | 内容 | 重要性 |
|--------|------|--------|
| `f9c6c5ab` | document_id per-process（防止 /resume 覆盖） | ⭐⭐⭐ 高 |
| `d6b65bbc` | 保留 non-ASCII text | ⭐⭐ 低 |
| `127048e6` | snake_case api_key config | ⭐ 低 |
| `a5c7422f` | HINDSIGHT_LLM_API_KEY 即使为空也写入 .env | ⭐ 低 |
| `f1ba2f0c` | 所有 async 操作超时 | ⭐⭐ 中 |
| `3e994e38` | materialize profile env during setup | ⭐⭐ 中 |
| `93a74f74` | 保留 shared event loop across provider shutdowns | ⭐⭐ 中 |

### 4.1 `f9c6c5ab` 详细分析

**问题**：`session_id` 被同时用作 `document_id` 和会话标识。/resume 时新进程复用相同 session_id，导致：
1. 新进程 `_session_turns` 从空开始
2. 下一条消息触发 retain，**覆盖**之前存储的整个文档

**解决方案**：每个进程生命周期获得独立的 document_id = `{session_id}-{startup_timestamp}`：
- 同 session 同进程：turn 累积到同一文档（保持）
- Resume（新进程，同 session）：写入新文档，旧文档保留
- Fork：子进程获得自己的文档，父进程文档不受影响

---

## §5 可执行借鉴清单

| 优先级 | 行动项 | 对应 CE 缺口 |
|--------|--------|-------------|
| 中 | 在 CE Gateway（如有）中添加 `on_session_finalize` 等效 hook，在 session 过期 flush 后触发 | 无等效插件 hook 系统 |
| 中 | 在 CE context 压缩端点添加 cheap preflight check（`has_content_to_compress` 等效） | 直接执行无预检 |
| 低 | 为 `ObservationEntity`/`SummaryEntity` 添加 `user_id` 字段，支持多租户隔离 | 固定 bank_id = 'default' |
| 低 | 考虑 `ContextEngine` ABC 抽象（长期架构目标） | 无可插拔压缩引擎 |

---

## §6 文档体量记录

- 本文件：~12KB（远低于 50KB 上限）
- 上次最大文件：`09`（46922 字节），无变化
