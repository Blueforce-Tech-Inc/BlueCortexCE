# 上游新提交分析（2026-04-26 ~ 05-04）

**编号**: #74
**基准**: `739b30bc0` → `81cd67829`（224 commits，9 个记忆相关）
**日期**: 2026-05-05
**撰写人**: PM Agent

---

## ⭐ 高优先级发现

### P1: Compressor 双 Pass 非字符串内容守卫（两提交独立触发同一模式）

**提交**: `408dd8aa2`（Pass 1 摘要/dedup）+ `a7417f8a4`（Pass 2 pruning）

**问题**: 当 Provider 返回非字符串类型的 tool content（dict、int 等，如 llama.cpp 等非标准 LLM 提供商），`_content_text_for_contains()` 和 `len()` 调用会抛出 `AttributeError`。

**修复模式**:

```python
# Pass 1 (dedup) 和 Pass 2 (pruning) 均增加相同守卫
if isinstance(content, list):
    continue  # 已有多模态守卫
if not isinstance(content, str):  # ← 新增
    continue
```

**CE 借鉴**: BlueCortexCE Structured Extraction 的 JSON/Map 输出如果走同一压缩管线，需同样守卫。**非字符串 content 类型是压缩层的永久风险点**，建议在 `EmbeddingService` 或 `ContextRefineService` 入口统一做类型规范化，而不是散落在各压缩 Pass 中。

---

### P1: Compressor Pruning Boundary Direction Bug（指数空间 vs 计数空间混淆）

**提交**: `b7bbc6250`（含 41 行回归测试）

**问题**: 原来的边界计算在**索引空间**应用 `max()`，导致慷慨的 budget 反而被静默截断回 `min_protect`：

```python
# 错误（索引空间 max 会反转方向）
prune_boundary = max(boundary, len(result) - min_protect)
# budget=50000, boundary=90, len=100, min_protect=10
# → max(90, 90) = 90（碰巧正确）
# budget=50000, boundary=70, len=100, min_protect=10
# → max(70, 90) = 90（budget 无效，静默截断到 min_protect）

# 正确（先转计数空间，应用 floor，再转回索引空间）
budget_protect_count = len(result) - boundary
protected_count = max(budget_protect_count, min_protect)
prune_boundary = len(result) - protected_count
```

**测试**: 新增 41 行测试覆盖 5 种场景（budget/无 budget、边界交叉、`min_protect` 更大等）。

**CE 借鉴**: BlueCortexCE 的 context 截断逻辑（如 `MemoryRefineService` 中的 token 预算）若存在类似索引/计数混淆，需 review。

---

## 中优先级发现

### P2: Compressor Timeout 时触发 Fallback

**提交**: `6b88f46c5`

**修复**: `model-not-found` 异常时触发 fallback 的逻辑，现在同时在 `TimeoutError` 时触发。

```python
# 原来：只处理 model-not-found
# 现在：同时处理 timeout
except (ModelNotFoundError, TimeoutError) as e:
    self._trigger_fallback_summarizer(...)
```

**CE 借鉴**: CE 的 LLM 调用如果超时不应该静默失败，而应该降级到备选方案。建议在 `LlmService` 中统一增加 TimeoutError fallback 逻辑。

---

### P2: Session Reset 时重置 Compressor Cooldown

**提交**: `e2211b268`

**问题**: `on_session_reset()` hook 触发后，`_summary_failure_cooldown_until` 未重置，导致新 session 的压缩因 cooldown 状态而被阻止。

```python
# 在 on_session_reset() 中
self._summary_failure_cooldown_until = None  # ← 新增 1 行
```

**CE 借鉴**: CE 的各 Service 状态（cooldown、retry count 等）如果依赖 session 级别的生命周期，需要在 session reset/rotate 时显式重置。

---

### P2: Gateway Session 崩溃恢复从 Blanket Suspend 改为 Smart Resume

**提交**: `f1e029251`

**问题**: `suspend_recently_active()` 在每次 Gateway 重启时无条件设置 `suspended=True`，导致 `get_or_create_session()` 擦除对话历史。正确的行为应该是设置 `resume_pending=True` 让 session 自动恢复。

**修复**:

```python
# 原来： blanket suspend
entry.suspended = True

# 现在： smart resume
entry.resume_pending = True
entry.resume_reason = "restart_interrupted"
entry.last_resume_marked_at = _now()
```

**CE 借鉴**: CE 的 session 管理（如 `SessionService`）在服务重启场景下，应该用 `resume_pending` 而非 `suspended`，避免误删用户对话。

---

### P2: Honcho Session Manager Cache 线程安全修复

**提交**: `ec4cb16a2`

**问题**: `_get_peer()` 和 `_get_or_create_honcho_session()` 访问 `_peers_cache` 和 `_sessions_cache` 时未持有 `_cache_lock`，而同类其他路径正确使用了锁。在并发 tool calls 或 prefetch 线程下会产生 stale reads 或丢失 cache 更新。

**修复模式**:

```python
# 读操作也需要锁（check-then-act 经典竞态）
with self._cache_lock:
    if peer_id in self._peers_cache:
        return self._peers_cache[peer_id]

# 网络调用放在锁外（避免 I/O 持有锁）
peer = self.honcho.peer(peer_id)

# 写操作也需要锁
with self._cache_lock:
    self._peers_cache[peer_id] = peer
```

**CE 借鉴**: CE 的 `TimelineService`/`SearchService` 如果有并发 cache 访问（特别是 `@Async` 方法），需要 review 是否有类似 unguarded cache access。**网络 I/O 不应持有锁**（与 Honcho 修复一致）。

---

### P2: SessionSearch 报告已解析的父 Session 而非 FTS5 子 Session

**提交**: `6b4ccb9b1`（附 62 行测试）

**问题**: delegation child session（如 source='telegram'）包含 FTS5 命中，但 `_resolve_to_parent()` 将其映射到不同的根 session（source='api_server'）。结果中的 source/model/started_at 错误地使用了 child session 的值。

**修复**: 优先使用已解析的父 session 的 `session_meta`：

```python
# 使用已解析父 session 的 metadata，而非 match_info（child session 的）
entry = {
    "session_id": session_id,  # 已解析为父 session
    "when": _format_timestamp(
        session_meta.get("started_at") or match_info.get("session_started")
    ),
    "source": session_meta.get("source") or match_info.get("source", "unknown"),
    "model": session_meta.get("model") or match_info.get("model"),
}
```

**CE 借鉴**: CE 的 search 结果如果涉及 delegation/session chain，需要类似"优先使用根 session metadata"逻辑，避免 delegation 中间层污染结果。

---

## 低优先级发现

### P3: SessionSearch 按最近活动时间排序而非启动时间

**提交**: `142b4bf3c`（81 行测试）

**修复**: `recent` 模式现在按 `last_activity` 而非 `started_at` 排序，更符合"最近活跃"的语义。

**CE 借鉴**: CE 的 session 列表 API 如果有"最近"排序需求，应使用 `updated_at` 而非 `created_at`。

---

### P3: Honcho Config 解析用 Safe Helper 替代 Raw `int()`

**提交**: `bea2562fc`

**问题**: `honcho CadenceIngestConfig` 的 `int()` 解析在输入无效时直接崩溃。

**修复**: 引入 safe helper 处理解析失败（如非数字输入）。

---

## 汇总表

| 提交 | 组件 | 严重度 | 类型 | CE 关联 |
|------|------|--------|------|---------|
| `a7417f8a4` | Compressor | P1 | Bug Fix | Structured extraction JSON output needs type guard |
| `408dd8aa2` | Compressor | P1 | Bug Fix | Same root cause as above, different pass |
| `b7bbc6250` | Compressor | P1 | Bug Fix | Token budget boundary direction; CE context truncation review |
| `6b88f46c5` | Compressor | P2 | Resilience | Timeout fallback in LlmService |
| `e2211b268` | Compressor | P2 | Bug Fix | Session reset must clear service-level cooldown state |
| `f1e029251` | Gateway | P2 | Bug Fix | Session restart → resume_pending not suspended |
| `ec4cb16a2` | Honcho | P2 | Thread Safety | Check-then-act race on cache; review CE cache access |
| `6b4ccb9b1` | SessionSearch | P2 | Correctness | Parent session metadata over child in delegation chains |
| `142b4bf3c` | SessionSearch | P3 | UX | Order by last_activity not start time |
| `bea2562fc` | Honcho | P3 | Robustness | Safe config parsing |
