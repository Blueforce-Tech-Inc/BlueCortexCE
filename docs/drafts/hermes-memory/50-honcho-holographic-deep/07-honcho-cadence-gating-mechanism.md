# 37 — Honcho 双维 cadence 门控机制（2026-04-25，含 c630dfcd 补丁）

> **文件**：`plugins/memory/honcho/__init__.py`（~1290 行）
> **关联**：[`04`](04-honcho-four-tools-routing.md) §21（`write_frequency` / `_turn_counter`）· [`34`](60-evolution/34-upstream-new-commits-session-lifecycle-and-context-engine.md) §11（`cc6e8941` 双层注入重构）· `c630dfcd`（dialectic liveness 三机制，2026-04-25 更新）
> **注意**：本篇聚焦**内部 cadence 管线**，与 [`04`](04-honcho-four-tools-routing.md) 的 `write_frequency`（写入频率）是**不同维度**；两者都在 `on_turn_start` 驱动下工作，但目的不同。

---

## §1 架构概览:为什么需要 cadence

Honcho 是**按需付费**的外部记忆 Provider,每次 `.context()` / `.chat()` 调用都涉及 LLM 推理成本。若在每个对话轮次都发起 API 调用,成本将随对话长度线性增长。

**Cadence 机制**通过**轮次计数门控**控制 API 调用频率:
- 不是"每轮都调用",而是"每 N 轮调用一次"
- 两次调用之间**缓存上次结果**,复用而非重算

Honcho 有**两条独立的 cadence 维度**,控制不同的注入层:

| 维度 | 配置项 | 默认值 | 控制内容 |
|------|--------|--------|---------|
| **Base Context** | `contextCadence` | 1(每轮) | `peer.context()` 调用,生成摘要/表示/卡牌 |
| **Dialectic Supplement** | `dialecticCadence` | 1(历史兼容),新安装=2,`cc6e8941` 后默认=3 | `.chat()` 多轮对话,获取增量信号 |

---

## §2 核心状态变量

```python
# plugins/memory/honcho/__init__.py

# 轮次计数器(on_turn_start 驱动,1-indexed)
self._turn_count = 0                  # 行 207

# Base context 缓存(按 contextCadence 刷新)
self._base_context_cache: Optional[str] = None   # 行 203
self._last_context_turn = -999                   # 行 218

# Dialectic 缓存(按 dialecticCadence 刷新)
self._prefetch_result: Optional[str] = None       # 行 220(在父类)
self._last_dialectic_turn = -999                  # 行 219
self._dialectic_empty_streak = 0                 # 行 221
```

**关键不变量**:`_turn_count` 是 1-indexed(首条用户消息=1),因此 `> 1` 表示"已过第一轮"。

---

## §3 on_turn_start 驱动管线

### 3.1 调用链

```
run_agent.py 每次用户消息 →
  _memory_manager.on_turn_start(user_turn_count, original_user_message) →
    HonchoProvider.on_turn_start(turn_number, message, **kwargs)
```

Honcho 的 `on_turn_start`(行 ~550)处理:

1. **prewarm 落地检测**:若 session 启动 prewarm 已填充 `_prefetch_result` 且 `_last_dialectic_turn == -999`,标记已落地(避免重复 dialectic 调用)
2. **`injection_frequency` 检查**:`"first-turn"` 模式下,仅在第一轮注入,之后跳过
3. **Base context 刷新门控**(§4)
4. **Dialectic 刷新门控**(§5)

### 3.2 prewarm as turn 0

Session 初始化时的 prewarm(行 420-434):

```python
# Treat prewarm as turn 0 so cadence gating starts clean.
self._turn_count = 0
# ... prewarm thread runs ...
self._turn_count = 1  # prewarm 后立即设为 1
```

这样第一条真实用户消息到达时,`_turn_count` 从 1 开始,cadence 门控逻辑从干净的起点计算。

### 3.3 Stale-Thread Watchdog(c630dfcd 强化)

**问题**:在 `c630dfcd` 之前,`_thread_is_live()` 仅检查线程是否还活着(`.is_alive()`)。若 Honcho API 调用 hang 住但线程未退出,会阻塞后续所有触发。

**修复**(`c630dfcd`):

```python
# 行 797-816
_STALE_THREAD_MULTIPLIER = 2.0  # 行 797

def _thread_is_live(self) -> bool:
    thread = self._prefetch_thread
    if thread is None:
        return False
    # Treat any prefetch thread older than timeout × 2.0 as dead.
    age = time.monotonic() - self._prefetch_thread_started_at  # 行 809
    if age > self._honcho_timeout * _STALE_THREAD_MULTIPLIER:
        logger.warning(
            "Honcho prefetch thread age %.1fs exceeds stale threshold "
            "%.1fs - treating as dead",
            age, self._honcho_timeout * _STALE_THREAD_MULTIPLIER,
        )
        return False
    return thread.is_alive()  # 行 817
```

**防护效果**:
- Hang 住的 Honcho 调用(> `timeout × 2.0` 秒)被主动判死,后续 `on_turn_start` 可以重新触发新的 prefetch 线程
- 不再因单一慢调用导致整个 dialectic 管线阻塞

---

## §4 Base Context Cadence(contextCadence)

### 4.1 刷新条件

```python
# 行 719
if self._context_cadence <= 1 or \
   (self._turn_count - self._last_context_turn) >= self._context_cadence:
    self._last_context_turn = self._turn_count
    # 调用 peer.context() 生成 base context
```

- `contextCadence=1`(默认):每轮都刷新
- `contextCadence=3`:`turn_count - last_context_turn >= 3` 时才刷新

### 4.2 Base Context 缓存

```python
self._base_context_cache: Optional[str] = None  # 行 203
```

Base context 结果被缓存,仅在 `contextCadence` 到达时刷新。`peer.context()` 生成包含摘要/表示/卡牌的结构化上下文。

---

## §5 Dialectic Cadence(dialecticCadence)

### 5.1 刷新条件

```python
# 行 736-739
effective = self._effective_cadence()
if (self._turn_count - self._last_dialectic_turn) < effective:
    # Skip - within cadence window
    return
```

### 5.2 Effective Cadence(动态回退)

```python
# 行 821-828
_BACKOFF_MAX = 8  # 行 804

def _effective_cadence(self) -> int:
    """Cadence plus empty-streak backoff, capped at _BACKOFF_MAX × base."""
    if self._dialectic_empty_streak <= 0:
        return self._dialectic_cadence
    widened = self._dialectic_cadence + self._dialectic_empty_streak
    ceiling = self._dialectic_cadence * _BACKOFF_MAX
    return min(widened, ceiling)
```

**回退机制**(empty streak backoff):
- 当 dialectic 连续返回空结果(`dialectic_empty_streak`),effective cadence 逐步增加
- `dialecticCadence=3` + `empty_streak=5` → `effective=8`
- 上限:`dialecticCadence × 8`(即最多回退 8 倍,减少无效 LLM 调用)

### 5.3 空结果累积

```python
# 行 625:dialectic 返回空
self._dialectic_empty_streak += 1

# 行 634:dialectic 返回有效内容
self._dialectic_empty_streak = 0
```

### 5.4 Stale-Thread Watchdog(dialectic prefetch 线程)

**问题**:若 Honcho prefetch 线程 hang 住但未退出,后续调用会堆积。

**修复**(`c630dfcd` 后):

```python
# 行 727-733
# Thread-alive guard with stale-thread recovery
if self._thread_is_live():  # 含 timeout × 2.0 判死
    logger.debug("Honcho prefetch already in flight - skip")
    return  # 线程还活着则跳过,不重复触发
```

`_thread_is_live()`(行 806-817):`thread.is_alive()` 且 `age <= timeout × 2.0` 才返回 True。超时的 prefetch 线程被主动判死,后续调用可重新触发新线程。

### 5.5 Stale-Result Discard(c630dfcd 新增)

**问题**:dialectic prefetch 结果在后台线程中异步返回。若两次触发之间隔了多轮"无意义提问"(如一句话对话),消费者读取时结果已经过时。

**修复**(`c630dfcd`):prefetch result 携带触发时的轮次标签,读取时检查是否过期:

```python
# 行 655-670
self._prefetch_result_fired_at: int = -999  # 新增状态(行 220)

# prefetch 触发时记录轮次
_fired_at = self._turn_count
self._prefetch_result_fired_at = _fired_at

# prefetch 读取时检查 staleness
fired_at = self._prefetch_result_fired_at  # 行 655
self._prefetch_result_fired_at = -999      # 重置
if dialectic_result and fired_at >= 0 and \
   (self._turn_count - fired_at) > stale_limit:
    logger.debug(
        "Honcho pending dialectic discarded as stale: fired_at=%d, "
        "turn=%d, limit=%d", fired_at, self._turn_count, stale_limit,
    )
    dialectic_result = None  # 丢弃过期结果,强制重新获取
```

- `stale_limit = dialectic_cadence × 2`(行 663)
- 若 prefetch 结果在超过 2× cadence 轮次后才被消费,直接丢弃
- 防止过期辩证内容注入当前上下文

### 5.6 空结果回退(empty-streak backoff)

```python
# 行 821-828(c630dfcd 后仍相同)
_BACKOFF_MAX = 8  # 行 804

def _effective_cadence(self) -> int:
    """Cadence plus empty-streak backoff, capped at _BACKOFF_MAX × base."""
    if self._dialectic_empty_streak <= 0:
        return self._dialectic_cadence
    widened = self._dialectic_cadence + self._dialectic_empty_streak
    ceiling = self._dialectic_cadence * _BACKOFF_MAX
    return min(widened, ceiling)
```

**回退机制**:
- 连续空结果(`dialectic_empty_streak`)使 effective cadence 逐步增加
- `dialecticCadence=3` + `empty_streak=5` → `effective=8`
- 上限:`dialecticCadence × 8`
- 有实质内容时,`empty_streak` 重置为 0

---

## §6 双层注入(cc6e8941 重构)

`cc6e8941` 将 Honcho 的上下文注入重构为**两个独立层**:

### 6.1 Base Context(基础层)

- 由 `peer.context()` 生成
- 按 `contextCadence` 刷新
- 包含:摘要(summary)、表示(representation)、卡牌(card)
- 缓存于 `_base_context_cache`

### 6.2 Dialectic Supplement(辩证补充层)

- 由 `.chat()` 多轮对话生成
- 按 `dialecticCadence` 刷新(默认 3,即约每 3 轮)
- 包含:增量信号、辩证分析
- **独立缓存**,不覆盖 base context

### 6.3 双重缓存隔离

```python
# Base cache(thread-safe)
self._base_context_cache: Optional[str] = None
self._base_context_lock = threading.Lock()

# Dialectic cache(在父类 MemoryProvider)
self._prefetch_result: Optional[str] = None
```

两条缓存线完全独立,base 层刷新不驱逐 dialectic 层缓存,反之亦然。

---

## §7 Liveness Snapshot(可观测性)

```python
# 行 829-844
def liveness_snapshot(self) -> dict:
    return {
        "turn_count": self._turn_count,
        "last_dialectic_turn": self._last_dialectic_turn,
        "pending_result_fired_at": self._prefetch_result_fired_at,
        "empty_streak": self._dialectic_empty_streak,
        "effective_cadence": self._effective_cadence(),
        "thread_alive": thread_age is not None,
        "thread_age_seconds": thread_age,
    }
```

可在运行时诊断:当前轮次、上次 dialectic 成功轮次、待处理结果触发轮次、空结果连续次数、有效 cadence、后台线程存活状态。

---

## §8 与 write_frequency 的关系(补充 §21)

| 维度 | 驱动事件 | 控制目标 | CE 类比 |
|------|---------|---------|---------|
| **`write_frequency`**(doc 04 §21) | `on_turn_start` 计数 | 何时将对话内容**写入** Honcho | Observation 批量写入频率 |
| **`contextCadence`** | `on_turn_start` 计数 | 何时调用 `peer.context()` 读取 | 定期语义检索/上下文生成 |
| **`dialecticCadence`** | `on_turn_start` 计数 | 何时调用 `.chat()` 获取辩证信号 | 周期性总结/反思触发 |

三者共享同一个 `_turn_count`,但各自独立判断门控条件。

---

## §9 BlueCortexCE 可执行借鉴

### 9.1 轮次驱动的分层刷新

CE 的 context 生成目前是"一次性"或"按需"模式。可以借鉴 Honcho 的双 cadence 机制:

```java
// 伪代码:CE 中的分层刷新
public class ContextRefreshScheduler {
    private int turnCount = 0;
    private int contextCadence = 3;      // 每3轮刷新摘要
    private int summaryCadence = 5;       // 每5轮触发深度总结
    private int lastSummaryTurn = -999;

    public void onTurnStart(int turn, String userMessage) {
        turnCount++;
        if (turnCount - lastSummaryTurn >= summaryCadence) {
            triggerDeepSummary();
            lastSummaryTurn = turnCount;
        }
    }
}
```

### 9.2 空结果回退(防止无效 API 调用)

```java
// CE 中的 empty streak backoff
private int emptyStreak = 0;
private static final int BACKOFF_MAX = 8;
private static final int SUMMARY_CADENCE = 5;

public int effectiveCadence() {
    int widened = SUMMARY_CADENCE + emptyStreak;
    int ceiling = SUMMARY_CADENCE * BACKOFF_MAX;
    return Math.min(widened, ceiling);
}

public void onSummaryResult(boolean hasContent) {
    if (hasContent) {
        emptyStreak = 0;
    } else {
        emptyStreak++;
    }
}
```

### 9.3 Liveness Snapshot(可观测性)

Honcho 的 `liveness_snapshot()` 是纯内存诊断 API,可返回当前轮次、空结果计数、有效 cadence、后台线程状态。CE 可在 `/api/health` 或 `/api/memory/status` 中暴露类似字段。

---

## §10 关键配置项汇总

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `contextCadence` | int | 1 | base context 刷新周期(轮) |
| `dialecticCadence` | int | 1(兼容),2(新装),3(cc6e8941+) | dialectic supplement 刷新周期 |
| `dialectic_depth` | int | 1-3 | 每轮 dialectic `.chat()` 调用次数 |
| `injectionFrequency` | str | `"every-turn"` | 注入频率模式:`every-turn` / `first-turn` |
| `reasoning_heuristic` | bool | `true` | 按查询长度自动缩放 reasoning level |
| `reasoning_level_cap` | str | `"high"` | reasoning level 上限 |

---

## §11 相关文档

| 文档 | 覆盖内容 |
|------|---------|
| [`04-honcho-four-tools-routing.md`](04-honcho-four-tools-routing.md) §21 | `write_frequency` 写入频率机制 |
| [`06-honcho-holographic-deep-advanced.md`](06-honcho-holographic-deep-advanced.md) | Honcho+Holographic 高级特性 |
| [`34-upstream-new-commits-session-lifecycle-and-context-engine.md`](60-evolution/34-upstream-new-commits-session-lifecycle-and-context-engine.md) §11 | `cc6e8941` 双层注入重构 |
| [`16-extended-memory-provider-hooks.md`](60-evolution/16-extended-memory-provider-hooks.md) | MemoryProvider hooks 全景 |
