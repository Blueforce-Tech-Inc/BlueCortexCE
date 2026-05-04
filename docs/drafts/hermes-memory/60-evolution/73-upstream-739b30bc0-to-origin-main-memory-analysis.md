# 上游新提交分析（739b30bc0 → b816fd4e2）

**扫描范围**：`739b30bc0..b816fd4e2`（237 commits，2026-05-05）  
**下次扫描起点**：`origin/main` `b816fd4e2`  
**核心发现**：3 个记忆系统相关 + 1 个上下文管理相关

---

## 0. 概述

237 个提交中，**4 个**与记忆/上下文系统直接相关：

| 优先级 | 提交 | 主题 |
|--------|------|------|
| ⭐ P2 | `e2211b268` | `on_session_reset()` 未清理 `_summary_failure_cooldown_until`，新 session 被旧 cooldown 阻塞 |
| ⭐ P2 | `d29f90e89` | error_classifier 大上下文误判溢出heuristic，1M context session 被错误降级 |
| P3 | `8bdec8088` | preflight compression 状态反馈从 `_safe_print` 改为 `_emit_status` |
| Test | `ccb5d8707` | max-iterations summary message sanitization 回归测试 |

其余 233 个非记忆相关（Dashboard / Kanban / TUI / Telegram / Teams / Docker / cron / doctor / browser / Google Workspace / Feishu / 模型适配等）。

---

## 1. `e2211b268` — `_summary_failure_cooldown_until` Session Reset 漏清理

**文件**：`agent/context_compressor.py`（+1 行）

### 问题描述

`on_session_reset()` 清理了 `_previous_summary`、`_last_summary_error`、`_ineffective_compression_count`，但**遗漏了** `_summary_failure_cooldown_until`。

当压缩失败时（LLM 超时 / provider 缺失），`_summary_failure_cooldown_until` 被设为 60s（普通错误）或 600s（provider 缺失 RuntimeError）。如果在 cooldown 未过期时用户执行 `/reset` 或 `/new`，cooldown 会**携带到新 session**。新 session 在达到压缩阈值时，`_generate_summary()` 因 cooldown 保护提前返回 `None`，middle turns 被静默丢弃，无任何告警。

### 修复

```python
# agent/context_compressor.py  on_session_reset() 中新增：
self._summary_failure_cooldown_until = 0.0  # transient errors must not block a fresh session
```

### CE 借鉴

**StructuredExtractionService** 中的 transient 错误（如网络超时、LLM 不可用）也应有类似 cooldown 机制，且必须在 session reset/scoped 创建时清理：

```java
// CE: session reset/scoped creation must clear ALL transient error cooldowns
@PostConstruct
public void initSessionScoped() {
    this.extractionCooldownUntil = 0L;
    this.lastErrorType = null;
}
```

**关键**：所有 `*CooldownUntil` / `*FailureCount` / `*BackoffUntil` 类字段必须在 session 创建时初始化为默认值，防止跨 session 状态泄漏。

---

## 2. `d29f90e89` — Error Classifier 大上下文假溢出修复

**文件**：`agent/error_classifier.py`（+14 行，+32 行测试）

### 问题描述

`classify_api_error()` 中的 `is_large` 判断对所有 context window 统一使用绝对 token/message 阈值：

- **disconnect 路径**：`approx_tokens > 120000 or num_messages > 200`
- **400 generic 路径**：`approx_tokens > 80000 or num_messages > 80`

对于 **1M token context** 的 session（如 `gpt-5.5` 1M context 版本），这些绝对阈值过于激进。1M context session 完全可能持有数百条消息、7-8 万 token，但仍远未达到其实际 budget，却会被错误归类为 `context_overflow`，触发不必要的压缩或 failover。

### 修复

```python
# 修复前（两处相同模式）
is_large = approx_tokens > context_length * 0.6 or approx_tokens > 120000 or num_messages > 200

# 修复后：绝对阈值仅在 <= 256K context 时生效
is_large = approx_tokens > context_length * 0.6 or (
    context_length <= 256_000 and (approx_tokens > 120_000 or num_messages > 200)
)
```

### CE 借鉴

**BlueCortexCE 的 ErrorClassifier**（Phase 3 Structured Extraction 错误处理）应采用类似策略：

```java
// CE: relative pressure + large-context-aware heuristics
public ErrorClassification classify(ExtractionException e, int contextTokens, int maxContext) {
    double pressure = (double) contextTokens / maxContext;
    boolean isLargeContext = maxContext > 200_000;  // 200K as inflection point
    
    boolean isOverflow = pressure > 0.85 
        || (!isLargeContext && contextTokens > 80_000)  // absolute only for small contexts
        || (!isLargeContext && e.getMessageCount() > 100);
    
    return isOverflow ? ErrorClassification.TRANSIENT_OVERFLOW : ErrorClassification.PERMANENT;
}
```

**设计原则**：相对阈值（压力比）跨 context 大小通用；绝对阈值仅作为小 context 的安全 guard。

---

## 3. `8bdec8088` — Preflight Compression 状态反馈迁移到 `_emit_status`

**文件**：`run_agent.py`（-5/+5 行）；`tests/run_agent/test_413_compression.py`（+6 行）

### 修复内容

Preflight compression（session 加载时若超过 active context threshold 触发的同步压缩）原通过 `_safe_print` 输出状态，Gateway/WebUI 用户看不到任何进度反馈，长时间压缩看起来像"消息丢失"。

```python
# 修复前
if not self.quiet_mode:
    self._safe_print(
        f"📦 Preflight compression: ~{_preflight_tokens:,} tokens "
        f">= {self.context_compressor.threshold_tokens:,} threshold"
    )

# 修复后：通过 _emit_status 统一生命周期状态广播
self._emit_status(
    f"📦 Preflight compression: ~{_preflight_tokens:,} tokens "
    f">= {self.context_compressor.threshold_tokens:,} threshold. "
    "This may take a moment."
)
```

### CE 借鉴

**StructuredExtractionService** 在执行 LLM 调用前应通过统一状态 API 广播进度（哪怕是 Spring Boot 的 `ApplicationEventPublisher`），让 WebUI / CLI 客户端能感知长时间运行的提取任务状态：

```java
// CE: broadcast extraction lifecycle events
applicationEventPublisher.publishEvent(
    new ExtractionLifecycleEvent(this, "EXTRACTING", 
        "Phase 1/3: Extracting user preferences..."));
```

---

## 4. `ccb5d8707` — Max-Iterations Summary Message Sanitization 回归测试

**文件**：`tests/run_agent/test_run_agent.py`（+49 行）

### 测试场景

1. **`test_summary_request_removes_orphan_tool_result`**：验证 summary 请求中**不包含**孤儿 tool result（`tool_call_id` 无对应 assistant `tool_calls` 的 tool 消息）
2. **`test_summary_request_inserts_stub_for_missing_tool_result`**：验证若 assistant `tool_calls` 在 summary 请求中缺少对应 tool result，**插入 stub** 满足 API 契约

### CE 借鉴

**ContextService / SummaryService** 的 `_prepare_summary_messages()` 方法应同样防御：

```java
// CE: orphan tool result must be excluded from summary request
List<Message> cleanMessages = messages.stream()
    .filter(m -> !isOrphanToolResult(m, assistantToolCallIds))
    .collect(Collectors.toList());
```

---

## 5. 汇总：CE 可执行行动项

| 行动项 | 来源 | 优先级 |
|--------|------|--------|
| Session/scoped 创建时清理所有 transient error cooldown 字段 | `e2211b268` | P2 |
| ErrorClassifier 增加 large-context-aware heuristics | `d29f90e89` | P2 |
| StructuredExtractionService 广播 lifecycle 状态事件 | `8bdec8088` | P3 |
| Summary preparation 排除 orphan tool results，插入 missing stubs | `ccb5d8707` | P3 |

---

## 附：非记忆相关重要提交速览

| 提交 | 主题 | 与记忆的间接关系 |
|------|------|----------------|
| `6da970f15` | TUI session teardown 关闭 AIAgent，修复 FD leak | session teardown 资源清理 |
| `f1e029251` | Gateway crash/restart 后 resume sessions 而非 blanket suspend | session 持久化韧性 |
| `a175f3957` | Nous OAuth token 跨 profile 持久化 | 认证 token 管理 |
| `d35efb989` | Telegram topic mode 新功能 | 非记忆 |
| `8163d3719` | kanban-video-orchestrator skill | 非记忆 |
