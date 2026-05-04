# Lazy Session 创建与 Ghost Session 防护（2026-05-04 新增）

**commit**: `c5b4c481656634ff919b214a037b830077d3bbd1` (#18370)  
**文件**: `run_agent.py` · `hermes_state.py` · `cli.py` · `tui_gateway/server.py`  
**关联**: BlueCortexCE SessionEntity 惰性创建策略

---

## 问题背景

Hermes Agent 在 `AIAgent.__init__()` 中**急于（eagerly）**创建 Session DB row：

```python
# 旧代码 (run_agent.py)
def __init__(self, ...):
    if self._session_db:
        self._session_db.create_session(
            session_id=self.session_id,
            source=...,
            model=self.model,
            ...
        )
```

这导致一个严重问题：TUI 或 Web Dashboard 打开又关闭但从未发送消息时，数据库中会留下**空 session 记录**（无 message，无 title）。这些"ghost sessions"累积在 `hermes sessions` 列表和 Web Dashboard 中，造成：

- Session 列表污染
- Dashboard 空会话显示混乱
- 每次启动/关闭都产生垃圾数据

---

## 解决方案：惰性创建 + 按需插入

### 核心设计

```python
# run_agent.py — AIAgent.__init__()
self._session_db_created = False  # 标志位：DB row 是否已创建
self._session_init_model_config = {
    "max_iterations": self.max_iterations,
    "reasoning_config": reasoning_config,
    "max_tokens": max_tokens,
}

def _ensure_db_session(self) -> None:
    """Create session DB row on first use. Disables _session_db on failure."""
    if self._session_db_created or not self._session_db:
        return  # 已创建或无 DB → 跳过
    try:
        self._session_db.create_session(
            session_id=self.session_id,
            source=self.platform or os.environ.get("HERMES_SESSION_SOURCE", "cli"),
            model=self.model,
            model_config=self._session_init_model_config,
            system_prompt=self._cached_system_prompt,
            user_id=None,
            parent_session_id=self._parent_session_id,
        )
        self._session_db_created = True  # ✅ 成功
    except Exception as e:
        # 瞬态失败（如 SQLite lock）→ 保持 _session_db 活跃，下次重试
        logger.warning("Session DB creation failed (will retry next turn): %s", e)
```

调用点：`_flush_messages_to_session_db()` 在每次消息 flush 前调用：

```python
def _flush_messages_to_session_db(self, messages, ...):
    if not self._session_db_created:
        self._ensure_db_session()  # 按需创建
    # ... 继续 flush
```

### INSERT OR IGNORE 语义

`_insert_session_row()` 使用 `INSERT OR IGNORE`（在 `hermes_state.py` 的 `_do()` 中）：

```python
conn.execute(
    """INSERT OR IGNORE INTO sessions
       (id, source, user_id, model, model_config, ...)""",
    ...
)
```

这意味着 `_ensure_db_session()` 被多次调用是**安全的**幂等操作。

---

## Ghost Session 清理机制

一次性迁移清理已有 ghost sessions：

```python
def prune_empty_ghost_sessions(self, sessions_dir=None) -> int:
    """Remove empty TUI ghost sessions (no messages, no title, >24hr old)."""
    cutoff = time.time() - 86400  # 仅清理 24h 前的

    rows = conn.execute("""
        SELECT id FROM sessions
        WHERE source = 'tui'
          AND title IS NULL
          AND ended_at IS NOT NULL
          AND started_at < ?
          AND NOT EXISTS (
              SELECT 1 FROM messages WHERE messages.session_id = sessions.id
          )
    """, (cutoff,)).fetchall()

    # 删除对应 session 文件
    if sessions_dir and removed_ids:
        for sid in removed_ids:
            self._remove_session_files(sessions_dir, sid)
    return len(removed_ids)
```

调用时机：
1. CLI 启动时一次性执行（`cli.py` 的 `_run_state_db_auto_maintenance`）
2. 通过 `session_db.set_meta("ghost_session_prune_v1", "1")` 确保只执行一次

---

## 其他修改点

| 文件 | 变更 |
|------|------|
| `tui_gateway/server.py` | 移除 `_start_agent_build()` 中的 eager `db.create_session()`；改用 `_ensure_db_session()` |
| `hermes_cli/main.py` | TUI 退出摘要增加 `message_count > 0` guard，避免空会话显示 resume 信息 |
| `cli.py` | `/title` 命令在设置前先调用 `_ensure_db_session()`；失败时保留 `_pending_title` 重试 |

---

## BlueCortexCE 借鉴

### 直接可迁移设计

1. **SessionEntity 惰性创建**  
   CE 的 `SessionEntity` 应该在首次 `recordUserPrompt` 时创建，而非 Agent 初始化时：
   ```java
   // CortexMemClientImpl.java
   private void ensureSession(String sessionId) {
       if (sessionCreated.get(sessionId)) return;
       sessionRepository.findById(sessionId)
           .orElseGet(() -> sessionRepository.save(new SessionEntity(sessionId)));
       sessionCreated.set(sessionId, true);
   }
   ```

2. **幂等插入**  
   使用 `INSERT OR IGNORE` 或 `ON CONFLICT DO NOTHING` 防止重复创建：
   ```sql
   INSERT INTO sessions(id, started_at) VALUES (?, ?)
   ON CONFLICT(id) DO NOTHING
   ```

3. **Ghost Session 清理**  
   启动时清理无消息的古老空 session：
   ```sql
   DELETE FROM sessions
   WHERE message_count = 0
     AND started_at < NOW() - INTERVAL '24 hours'
   ```

4. **瞬态失败容错**  
   `_session_db` 失败时不永久禁用 session DB，保持可用以便下次重试。

### 实施优先级

- **P1（立即）**：SessionEntity 创建时机检查，确认是否在 Agent 初始化时 eager 创建
- **P2（短期）**：添加幂等插入保护
- **P3（中期）**：添加 ghost session 清理 cron

---

## 附：Crash/Restart 后 Session Resume 机制（相关 commit）

**commit**: `f1e0292517c15be09f9f1fb6a61046993b562586`  
**影响文件**: `gateway/run.py` · `gateway/session.py`

### 问题

旧行为：`suspend_recently_active()` 在每次启动时无条件设置 `suspended=True`，导致 `get_or_create_session()` 清除对话历史。

### 修复

改为设置 `resume_pending=True` 而非 `suspended=True`：
- Session 可自动 resume
- Stuck-loop 会在 3 次失败后 escalation

```python
# gateway/session.py
# 旧：
suspended=True  # 阻止 resume

# 新：
resume_pending=True  # 允许 resume，失败后 escalation
```

这保证了 crash/restart 后 session 历史不丢失，对记忆系统有直接影响（`messages` 表数据得以保留）。

### 与记忆系统的关系

Session DB row 是 `messages` 表的外键。若 session 在 crash 后被错误地 suspended 而不是标记为 resume_pending，则：
- 消息 flush 可能写入错误的 session_id
- Provider 的 `on_session_end()` / `on_session_finalize()` 触发时机异常

CE 应确保 crash recovery 不触发 session 重建逻辑。
