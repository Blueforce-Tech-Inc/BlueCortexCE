# Gateway Background Session Expiry Watcher — Proactive Memory Flush

> **来源**：`gateway/run.py` `_session_expiry_watcher()` · `gateway/session.py` `SessionEntry.memory_flushed` · `tests/gateway/test_async_memory_flush.py` · `tests/gateway/test_flush_memory_stale_guard.py`
> **快照时间**：2026-04-23
> **关联**：`15-session-db-flush-and-duplicate-fix.md`（消息级 flush）· `13-run-agent-memory-wiring-snapshot.md`（主循环接线）

---

## TL;DR

| 特性 | 说明 | CE 借鉴价值 |
|------|------|-------------|
| **后台主动 flush** | 每 5 分钟扫描过期 session，在用户下次消息**之前**提取记忆 | 高 — 避免用户等待 |
| **`memory_flushed` 持久化标记** | 写入 `sessions.json`，跨 gateway 重启不丢失 | 高 — 防止重复 flush |
| **当前记忆状态注入** | flush prompt 包含 MEMORY.md/USER.md 实时内容 | 高 — 防止旧数据覆盖新数据 |
| **Cron session 跳过** | `cron_` 前缀的 session 不触发 flush | 中 — 无用户对话不需提取 |
| **指数退避重试** | 最多 3 次，超限标记为 flushed 防止无限循环 | 中 — 生产环境稳定性 |

---

## 1. 架构定位

Hermes Agent 有两种记忆 flush 机制，职责不同：

| 机制 | 位置 | 触发时机 | 目标 |
|------|------|---------|------|
| **消息级 flush** | `run_agent.py` `_flush_messages_to_session_db()` | Agent 主循环退出时 | 持久化消息到 SQLite session DB |
| **记忆级 flush** | `gateway/run.py` `_session_expiry_watcher()` | Session 过期时（后台定时器） | 提取对话中的重要信息到 MEMORY.md/USER.md |

**本节聚焦后者**：session 过期时的后台记忆提取。

---

## 2. `_session_expiry_watcher` 完整流程

```python
async def _session_expiry_watcher(self, interval: int = 300):
    """Background task that proactively flushes memories for expired sessions.
    
    Runs every `interval` seconds (default 5 min).  For each session that
    has expired according to its reset policy, flushes memories in a thread
    pool and marks the session so it won't be flushed again.

    This means memories are already saved by the time the user sends their
    next message, so there's no blocking delay.
    """
    await asyncio.sleep(60)  # initial delay — let the gateway fully start
    _flush_failures: dict[str, int] = {}  # session_id -> consecutive failure count
    _MAX_FLUSH_RETRIES = 3
    while self._running:
        try:
            self.session_store._ensure_loaded()
            # 1. 收集过期 session
            _expired_entries = []
            for key, entry in list(self.session_store._entries.items()):
                if entry.memory_flushed:     # 已 flush，跳过
                    continue
                if not self.session_store._is_session_expired(entry):
                    continue
                _expired_entries.append((key, entry))

            # 2. 逐个 flush
            for key, entry in _expired_entries:
                await self._async_flush_memories(entry.session_id, key)
                # 3. 关闭缓存 agent 的 memory provider
                # 4. 标记 memory_flushed = True，持久化到 sessions.json
                with self.session_store._lock:
                    entry.memory_flushed = True
                    self.session_store._save()
        except Exception as e:
            logger.debug("Session expiry watcher error: %s", e)
        # 5. 小粒度 sleep，便于快速停止
        for _ in range(interval):
            if not self._running:
                return
            await asyncio.sleep(1)
```

### 关键设计决策

**① 为什么主动 flush？**

传统做法：用户发送下一条消息 → 触发 session reset → 此时才提取记忆 → 用户等待。

Hermes 方案：session 过期后立即在后台提取 → 用户下次消息时记忆已就绪 → **零等待**。

**② 为什么 5 分钟间隔？**

平衡点：
- 太短：频繁扫描浪费资源
- 太长：用户可能在 flush 完成前发送消息
- 5 分钟：对 idle/daily reset 策略都足够及时

**③ 为什么初始延迟 60 秒？**

让 gateway 完全启动后再开始扫描，避免：
- Session store 还没加载完
- 各平台 adapter 还没就绪
- Agent cache 还没建立

---

## 3. `memory_flushed` 持久化标记

### 问题背景

旧实现使用内存中的 `_pre_flushed_sessions` 集合来跟踪已 flush 的 session。问题是：
- Gateway 重启后集合丢失
- 导致已 flush 的 session 被重复 flush
- 可能产生记忆重复或覆盖

### 解决方案

将 `memory_flushed` 标记持久化到 `SessionEntry` 中：

```python
@dataclass
class SessionEntry:
    # ...
    # Set by the background expiry watcher after it successfully flushes
    # memories for this session.  Persisted to sessions.json so the flag
    # survives gateway restarts (the old in-memory _pre_flushed_sessions
    # set was lost on restart, causing redundant re-flushes).
    memory_flushed: bool = False
```

**序列化/反序列化**：
```python
def to_dict(self) -> Dict[str, Any]:
    result = {
        # ...
        "memory_flushed": self.memory_flushed,
    }

@classmethod
def from_dict(cls, data: Dict[str, Any]) -> "SessionEntry":
    return cls(
        # ...
        memory_flushed=data.get("memory_flushed", False),
    )
```

### 重试与熔断

```python
_flush_failures: dict[str, int] = {}  # session_id -> consecutive failure count
_MAX_FLUSH_RETRIES = 3

# Flush 失败时
failures = _flush_failures.get(entry.session_id, 0) + 1
_flush_failures[entry.session_id] = failures
if failures >= _MAX_FLUSH_RETRIES:
    logger.warning(
        "Memory flush gave up after %d attempts for %s: %s. "
        "Marking as flushed to prevent infinite retry loop.",
        failures, entry.session_id, e,
    )
    with self.session_store._lock:
        entry.memory_flushed = True
        self.session_store._save()
```

**设计要点**：
- 3 次失败后标记为 flushed，防止无限重试
- 失败计数是内存中的（重启后重试），合理因为重启可能解决了问题
- 成功后立即清除失败计数

---

## 4. `_flush_memories_for_session` — 记忆提取逻辑

### 4.1 Cron Session 跳过

```python
if old_session_id and old_session_id.startswith("cron_"):
    logger.debug("Skipping memory flush for cron session: %s", old_session_id)
    return
```

**原因**：Cron session 是无头运行的系统任务，没有有意义的用户对话可供记忆提取。

### 4.2 最小消息阈值

```python
history = self.session_store.load_transcript(old_session_id)
if not history or len(history) < 4:
    return
```

少于 4 条消息（至少 1 轮完整的 user-assistant 对话）不值得提取。

### 4.3 静默 Agent 构造

```python
tmp_agent = AIAgent(
    **runtime_kwargs,
    model=model,
    max_iterations=8,
    quiet_mode=True,
    skip_memory=True,          # Flush agent — no memory provider
    enabled_toolsets=["memory", "skills"],
    session_id=old_session_id,
)
# Fully silence the flush agent
tmp_agent._print_fn = lambda *a, **kw: None
```

**关键设计**：
- `skip_memory=True`：不加载外部 memory provider，避免 flush 过程触发更多记忆操作
- `enabled_toolsets=["memory", "skills"]`：只允许写入记忆和保存技能
- `max_iterations=8`：限制迭代次数，防止 flush 过程失控
- `_print_fn = lambda: None`：完全静默，不输出到终端

### 4.4 当前记忆状态注入（防覆盖）

```python
_current_memory = ""
try:
    from tools.memory_tool import get_memory_dir
    _mem_dir = get_memory_dir()
    for fname, label in [
        ("MEMORY.md", "MEMORY (your personal notes)"),
        ("USER.md", "USER PROFILE (who the user is)"),
    ]:
        fpath = _mem_dir / fname
        if fpath.exists():
            content = fpath.read_text(encoding="utf-8").strip()
            if content:
                _current_memory += f"\n\n## Current {label}:\n{content}"
except Exception:
    pass  # Non-fatal — flush still works, just without the guard
```

**为什么需要？**

Race condition 场景：
1. Session A 过期，触发 flush
2. 在 flush 运行期间，Session B（另一个对话）更新了 MEMORY.md
3. Flush agent 读取旧的 transcript，可能会覆盖 Session B 的更新

**解决方案**：在 flush prompt 中注入当前记忆的实时状态，让 flush agent 知道：
- 当前已经保存了什么
- 不要覆盖或删除已有的条目
- 只添加 transcript 中的新信息

### 4.5 Flush Prompt 结构

```
[System: This session is about to be automatically reset due to 
inactivity or a scheduled daily reset. The conversation context 
will be cleared after this turn.

Review the conversation above and:
1. Save any important facts, preferences, or decisions to memory 
   (user profile or your notes) that would be useful in future sessions.
2. If you discovered a reusable workflow or solved a non-trivial 
   problem, consider saving it as a skill.
3. If nothing is worth saving, that's fine — just skip.

IMPORTANT — here is the current live state of memory. Other 
sessions, cron jobs, or the user may have updated it since this 
conversation ended. Do NOT overwrite or remove entries unless 
the conversation above reveals something that genuinely 
supersedes them. Only add new information that is not already 
captured below.

## Current MEMORY (your personal notes):
...

## Current USER PROFILE (who the user is):
...

Do NOT respond to the user. Just use the memory and skill_manage 
tools if needed, then stop.]
```

**Prompt 设计要点**：
1. 明确告知 session 即将 reset
2. 给出具体的保存指导（3 条）
3. 注入当前记忆状态 + 防覆盖指令
4. 明确禁止回复用户，只使用工具

---

## 5. Session 过期判断

```python
def _is_session_expired(self, entry: SessionEntry) -> bool:
    """Check if a session has expired based on its reset policy.
    
    Works from the entry alone — no SessionSource needed.
    Used by the background expiry watcher to proactively flush memories.
    Sessions with active background processes are never considered expired.
    """
    if self._has_active_processes_fn:
        if self._has_active_processes_fn(entry.session_key):
            return False

    policy = self.config.get_reset_policy(
        platform=entry.platform,
        session_type=entry.chat_type,
    )

    if policy.mode == "none":
        return False

    now = _now()

    if policy.mode in ("idle", "both"):
        idle_deadline = entry.updated_at + timedelta(minutes=policy.idle_minutes)
        if now > idle_deadline:
            return True

    if policy.mode in ("daily", "both"):
        today_reset = now.replace(
            hour=policy.at_hour,
            minute=0, second=0, microsecond=0,
        )
        if now.hour < policy.at_hour:
            today_reset -= timedelta(days=1)
        if entry.updated_at < today_reset:
            return True

    return False
```

**支持的过期策略**：
| 模式 | 说明 |
|------|------|
| `none` | 永不过期 |
| `idle` | N 分钟无活动后过期 |
| `daily` | 每天固定时间过期 |
| `both` | idle 和 daily 取或 |

**安全守卫**：有活跃后台进程的 session 永不过期（防止 flush 正在运行的 agent）。

---

## 6. CE 借鉴分析

### 6.1 可直接借鉴

| Hermes 设计 | CE 落地方案 |
|-------------|-------------|
| 后台定时扫描过期 session | Worker 或 proxy 层：定时检查 session 空闲时间，触发记忆提取 API |
| `memory_flushed` 持久化标记 | 在 session 元数据中增加 `memory_flushed` 字段，写入数据库 |
| 当前记忆状态注入 | 调用 `/api/memory/search` 获取当前记忆，注入提取 prompt |
| Cron session 跳过 | 对 cron 类型的 session 跳过记忆提取 |

### 6.2 需要适配

| Hermes 设计 | CE 差异 | 适配方案 |
|-------------|---------|---------|
| 进程内构造临时 Agent | CE 是旁路服务，没有 Agent 进程 | 通过 `/api/context/generate` 端点触发记忆提取 |
| 直接读写 MEMORY.md 文件 | CE 使用 PostgreSQL 存储 | 通过 API 读写记忆，不直接操作文件 |
| 5 分钟轮询间隔 | CE 可能有更多 session | 可配置间隔，或基于事件触发 |

### 6.3 核心思想提炼

**"预提取优于等待提取"**：不要等到用户发送下一条消息才开始提取记忆，而是在 session 过期后立即在后台完成。这样用户感知到的是"记忆已就绪"，而不是"正在提取记忆，请稍候"。

**"注入当前状态防覆盖"**：任何异步记忆提取都应该获取当前记忆的最新状态，避免并发更新导致的数据丢失。

**"持久化标记防重复"**：跨重启的幂等性需要持久化状态，不能依赖内存中的集合。

---

## 7. 测试覆盖

相关测试文件：
- `tests/gateway/test_async_memory_flush.py`：验证 `memory_flushed` 标记的持久化、`_is_session_expired` 的各种策略
- `tests/gateway/test_flush_memory_stale_guard.py`：验证 cron session 跳过、当前记忆注入、空记忆文件处理

**测试模式值得借鉴**：
1. 使用 `tmp_path` fixture 隔离文件系统
2. 使用 `patch` mock 外部依赖（`run_agent.AIAgent`）
3. 测试边界情况：空 transcript、cron session、记忆文件不存在
