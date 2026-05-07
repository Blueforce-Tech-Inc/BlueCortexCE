# 上游增量分析 `87b113c2e..3b750715a`（2 commits）

**时间**：2026-05-06 03:54 CST  
**起点**：`87b113c2e`（上次巡检，2026-05-06 02:04）  
**终点**：`origin/main`  
**新增 commit**：2 个（`0397be593`、`3b750715a`）

---

## 记忆/上下文系统相关 commit

| Hash | 标题 | 记忆相关度 |
|------|------|----------|
| `3b750715a` | fix: resolve lazy session creation regressions (#18370 fallout) (#20363) | ⭐⭐ **P1** |
| `0397be593` | feat(tui): remove /provider alias for /model (#20358) | ❌ 无关 |

---

## `3b750715a` — P1: Lazy Session Creation 回归修复（#18370 遗留问题）

### 背景

PR #18370 引入了 lazy session creation（延迟会话创建），但带来了 3 个回归问题：

1. `_finalize_session()` 在压缩后使用了 **stale session_key**（#20001）
2. `run_conversation` 中 auto-compression 后 **session_key 未同步**（#20001）
3. 累积了大量 **ghost compression continuation sessions** 需要清理（#20001）

### 修复一：`finalize_orphaned_compression_sessions()` 新增

**文件**：`hermes_state.py`

```python
def finalize_orphaned_compression_sessions(self) -> int:
    """Mark orphaned compression continuation sessions as ended.
    Targets child sessions that were never finalized:
    - parent is ended with reason='compression'
    - child has messages but no end_reason/ended_at
    - api_call_count=0
    Non-destructive: preserves all messages.
    Fix for #20001.
    """
    cutoff = time.time() - 604800  # 7 days
```

**SQL 逻辑**：
```sql
UPDATE sessions
SET ended_at = ?,
    end_reason = 'orphaned_compression'
WHERE api_call_count = 0
  AND end_reason IS NULL
  AND ended_at IS NULL
  AND started_at < ?
  AND parent_session_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM sessions p
      WHERE p.id = sessions.parent_session_id
        AND p.end_reason = 'compression'
        AND p.ended_at IS NOT NULL
  )
  AND EXISTS (
      SELECT 1 FROM messages m
      WHERE m.session_id = sessions.id
  )
```

**特点**：
- 非破坏性：保留所有 messages，只标记 `end_reason='orphaned_compression'`
- 7 天清理窗口：避免误伤仍在使用的 session
- 仅处理有 messages 的 child session（有实际数据才需要修复）
- `api_call_count=0` 过滤掉真正的活跃 session

**CE 借鉴**：当前 CE 没有压缩链 session 管理，但 Phase 2 引入 `/api/session/{id}/compress` 后可能面临类似问题。需要在 `SessionEntity` 中增加 `parent_session_id` 和 `end_reason` 字段，并在压缩时记录链关系。

### 修复二：`_finalize_session()` 使用 `session_id` 而非 `session_key`

**文件**：`tui_gateway/server.py`

```python
# BEFORE (bug):
if session_key:
    db.end_session(session_key, end_reason)

# AFTER (fix):
# Use session_id (from agent.session_id) not session_key — after compression,
# session_key may be stale (the ended parent) while session_id is the live
# continuation. Fix for #20001.
if session_id:
    db.end_session(session_id, end_reason)
```

**问题根因**：压缩后 `AIAgent._compress_context` 结束当前 SessionDB session 并创建新的 continuation session（新的 `session_id`），但 `session["session_key"]` 仍指向旧的 ended parent session。

### 修复三：`_sync_session_key_after_compress` Policy Flags

**文件**：`tui_gateway/server.py`

```python
def _sync_session_key_after_compress(
    sid: str,
    session: dict,
    *,
    clear_pending_title: bool = True,      # 新增
    restart_slash_worker: bool = True,     # 新增
) -> None:
```

**Policy 语义**：

| 场景 | `clear_pending_title` | `restart_slash_worker` |
|------|----------------------|----------------------|
| 手动 `/compress` | `True`（title 属于旧 session） | `True` |
| 自动压缩（post-turn） | `False`（保留用户意图） | `True` |

**调用点变化**：

```python
# 手动压缩（cli.py）：
_sync_session_key_after_compress(sid, session)

# 自动压缩（在 run_conversation 内部，新增）：
_sync_session_key_after_compress(
    sid, session, clear_pending_title=False, restart_slash_worker=True,
)
```

**关键**：`clear_pending_title=False` 保留 `pending_title`（用户意图），使其能应用到 continuation session，而不是被清空。

### 修复四：ValueError 处理 for Duplicate Title

**文件**：`tui_gateway/server.py`

```python
try:
    if _pdb.set_session_title(_session_key, _pending):
        session["pending_title"] = None
except ValueError as exc:
    # Invalid/duplicate title — non-retryable, drop it.
    # Auto-title will take over. Fix for #19029.
    session["pending_title"] = None
except Exception:
    # Transient DB failure — keep pending_title for retry.
    pass
```

- `ValueError`：非重试错误（重复 title），丢弃 `pending_title`，auto-title 接替
- 其他 `Exception`：瞬态 DB 错误，保留 `pending_title` 供重试

### 修复五：`_normalize_empty_agent_response()` 重构

**文件**：`gateway/run.py`

将分散的错误处理逻辑提取为独立函数，覆盖 3 种空响应场景：

1. **Agent failed**（`agent_result.get("failed")`）：原有逻辑，context overflow 检测
2. **Agent did work but returned no text**（`api_calls > 0 and not partial`）：**新增** — 可能是瞬态错误
3. **Partial interrupted**（`partial: True`）：**新增**

**CE 借鉴**：CE 当前 `AgentService` 没有类似统一错误归一化。当 `llm.chat()` 返回空响应时，应该：
- 检测 `failed` flag → 返回具体错误信息
- 检测 `api_calls > 0` 但无 text → 提示重试
- 区分 context overflow vs 其他错误

### 测试覆盖

**新增**：`tests/test_lazy_session_regressions.py`（608 行，14 个回归测试）

测试场景覆盖：
- Auto-compression 后 `agent.session_id` 旋转检测
- `pending_title` 在 auto-compression 后保留
- `session_key` 在 compression 后正确同步
- `set_session_title` ValueError 处理
- Ghost compression session 清理

---

## CE 差距分析

| 发现 | 严重度 | CE 当前状态 |
|------|--------|------------|
| 压缩后 session_id 追踪 | P1 | ❌ CE 无压缩链 session 概念 |
| Ghost session 清理 | P2 | ❌ CE 无此场景 |
| `pending_title` policy flags | P2 | ❌ CE 无 equivalent |
| Empty response 归一化 | P2 | ⚠️ CE `AgentService` 无统一归一化 |
| ValueError for duplicate title | P3 | ❌ CE 无此场景 |

**最直接的借鉴**：在 Phase 2 压缩实现中，参考 `_sync_session_key_after_compress` 的 policy flag 模式，避免 `pending_title` 在压缩后被误清空。

---

## 文档架构合规

- ✅ `hermes-memory-analysis.md`：1553 字节 < 50KB
- ✅ 新增 doc `87`：预估 < 8KB < 50KB
- ✅ 所有 `hermes-memory/` 子文档 < 50KB
