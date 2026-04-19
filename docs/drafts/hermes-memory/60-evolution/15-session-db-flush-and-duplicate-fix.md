# 60-evolution/15-session-db-flush-and-duplicate-fix.md

# Session DB 批量 Flush 与 Duplicate-Write Bug Fix 分析

> **来源**：Hermes Agent `run_agent.py` — `_flush_messages_to_session_db()`
> **快照时间**：2026-04-19
> **关联**：issue #860，`13-run-agent-memory-wiring-snapshot.md` §3.2

---

## 1. 问题背景

在 Hermes Agent 的早期实现中，Agent 主循环可能从**多个退出路径**调用 `_flush_messages_to_session_db()`（例如正常结束、异常恢复、signal handler）。若单纯按消息列表长度追加写入，会导致**重复写入同一条消息**（duplicate-write bug #860）。

---

## 2. 解决方案：游标跟踪

```python
self._last_flushed_db_idx = 0   # 实例变量，初始化

def _flush_messages_to_session_db(self, messages, conversation_history=None):
    """持久化未 flush 的消息到 SQLite session store。
    
    Uses _last_flushed_db_idx 跟踪已写入游标，
    多次调用（来自多出口路径）只写入真正的新消息。
    """
    if not self._session_db:
        return

    start_idx = len(conversation_history) if conversation_history else 0
    flush_from = max(start_idx, self._last_flushed_db_idx)

    for msg in messages[flush_from:]:
        role = msg.get("role", "unknown")
        content = msg.get("content")
        # ... 序列化 tool_calls, finish_reason, reasoning 等
        self._session_db.append_message(
            session_id=self.session_id,
            role=role,
            content=content,
            tool_name=msg.get("tool_name"),
            tool_calls=tool_calls_data,
            tool_call_id=msg.get("tool_call_id"),
            finish_reason=msg.get("finish_reason"),
            reasoning=msg.get("reasoning") if role == "assistant" else None,
            # ...
        )

    self._last_flushed_db_idx = len(messages)
```

**核心保证**：`flush_from = max(start_idx, self._last_flushed_db_idx)` 确保：
- 首次调用：`flush_from = 0`，写入全部历史
- 第二次调用（正常路径）：`flush_from = 上次 len`，只写入新增
- 异常路径提前触发：`_last_flushed_db_idx` 已推进，重复调用安全

---

## 3. 双重安全：session 行存在性保证

```python
self._session_db.ensure_session(
    self.session_id,
    source=self.platform or "cli",
    model=self.model,
)
```

`ensure_session()` 使用 `INSERT OR IGNORE`，若 session 行已存在则 no-op。解决：
> 若 `create_session()` 在启动时因瞬时锁失败（如 DB 占用），session 行不存在，但 flush 仍需写入到已有 session。

---

## 4. `_get_messages_up_to_last_assistant` — 不完整消息回滚

```python
def _get_messages_up_to_last_assistent(self, messages):
    """获取到（不含）最后一条 assistant 消息的消息列表。
    
    用于"回滚"到会话最后成功点——当最终 assistant 消息
    不完整或格式异常时，从此处截断。
    """
    if not messages:
        return []
    # 从末尾反向查找最后一条 role=assistant 的消息
    last_assistant_idx = None
    for i in range(len(messages) - 1, -1, -1):
        if messages[i].get("role") == "assistant":
            last_assistant_idx = i
            break
    if last_assistant_idx is None:
        return messages
    # 返回之前的所有消息（不含最后不完整的 assistant）
```

**使用场景**：当 LLM 返回的最终 assistant 消息异常时（如流式中断），用此方法截断到上一个健康点。

---

## 5. 消息序列化字段（完整清单）

| 字段 | 来源 | 说明 |
|------|------|------|
| `role` | `msg.get("role")` | user/assistant/system/tool |
| `content` | `msg.get("content")` | 消息正文 |
| `tool_name` | `msg.get("tool_name")` | tool role 时的工具名 |
| `tool_calls` | `msg.tool_calls` / `msg["tool_calls"]` | 工具调用列表 |
| `tool_call_id` | `msg.get("tool_call_id")` | 工具调用 ID |
| `finish_reason` | `msg.get("finish_reason")` | stop/tool_calls 等 |
| `reasoning` | 仅 assistant | reasoning content |
| `reasoning_details` | 仅 assistant | reasoning 元数据 |
| `codex_reasoning_items` | 仅 assistant | Codex 推理步骤 |

---

## 6. 与 CE 的对照

| 维度 | Hermes | BlueCortexCE |
|------|--------|--------------|
| Duplicate-write 防护 | `_last_flushed_db_idx` 游标 | 需确认 SessionService 是否已有类似机制 |
| Session 存在性保证 | `ensure_session()` INSERT OR IGNORE | 需确认 DB 操作层 |
| 不完整消息处理 | `_get_messages_up_to_last_assistant` | CE 的流式处理是否有对应截断？ |
| 多出口路径调用 | 单一 flush 方法 + 游标保证幂等 | 同上 |

### 借鉴价值

1. **游标模式**：CE 的 SessionService 可引入 `_last_persisted_id` 游标，防止重复写入
2. **幂等 flush**：多入口调用同一 flush 方法，靠游标保证幂等性
3. **INSERT OR IGNORE**：启动失败恢复路径的安全保障
4. **消息截断**：流式 LLM 中断时需要类似的不完整消息回滚机制

---

## 7. 待跟进

- [ ] CE SessionService 是否已有游标/幂等 flush 机制？（需查 `SessionService.java`）
- [ ] `_get_messages_up_to_last_assistant` 是否在 shutdown 路径中被使用？
