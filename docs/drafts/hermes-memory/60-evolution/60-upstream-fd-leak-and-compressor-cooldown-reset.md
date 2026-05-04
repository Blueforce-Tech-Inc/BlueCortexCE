# 60. 上游修复：TUI FD Leak + 压缩机 Cooldown Session Reset（2026-05-04）

**扫描范围**：`8163d3719..origin/main`（约 13 commits；记忆相关 2 个）
**文档版本**：v1 — 2026-05-04

---

## §1 TUI Session Teardown — 关闭 AIAgent 防止 FD Leak

**commit**: `6da970f15d78d81dfc6287e54788acc2f869b64c`（#19562）  
**文件**: `tui_gateway/server.py`

### 问题根因

旧代码 `session.close()` 只关闭了 `slash_worker` 子进程，**从未调用** `AIAgent.close()`。在长运行的 TUI Gateway 进程中：

1. `AIAgent` 的 httpx 客户端处于打开状态
2. OS 回收已关闭 FD 编号给新的活跃连接
3. 旧的 finalizer 关闭了**新的活跃 socket**
4. 后续 LLM API 调用触发 `[Errno 9] Bad file descriptor`

### 修复

```python
# tui_gateway/server.py — session teardown path
try:
    agent = session.get("agent")
    if agent and hasattr(agent, "close"):
        agent.close()  # 关闭 httpx transport pool 和 TCP sockets
except Exception:
    pass
try:
    worker = session.get("slash_worker")
    if worker:
        worker.close()
except Exception:
    pass
```

### BlueCortexCE 借鉴

CE 的 session teardown 路径（SessionController / SessionService）应确保：
- Agent 生命周期正确关闭（httpx/http clients）
- FD 不泄漏到下一个 session
- 验证方法：反复 `/new` 后检查进程 FD 数量

---

## §2 ContextCompressor — `on_session_reset()` 重置 Cooldown Timer

**commit**: `e2211b2683d0dacbdb39af9bc5a2b712a742597d`（#15547）  
**文件**: `agent/context_compressor.py`

### 问题根因

`on_session_reset()` 清理了 `_previous_summary`、`_last_summary_error`、`_ineffective_compression_count`，但**遗漏**了 `_summary_failure_cooldown_until`。

后果：
- 瞬态 summary 错误设置 60s cooldown（provider 缺失时 600s）
- 用户立即执行 `/reset` 或 `/new`
- 新 session 在 cooldown 未到期前达到压缩阈值
- `_generate_summary()` 提前返回 `None`，**中间 turns 被静默丢弃**
- Agent 继续运行，但没有任何压缩摘要，对话历史不完整

### 修复

```python
# agent/context_compressor.py — on_session_reset()
self._last_aux_model_failure_model = None
self._last_compression_savings_pct = 100.0
self._ineffective_compression_count = 0
self._summary_failure_cooldown_until = 0.0  # ← 新增：transient errors 不应阻塞新 session
```

### 设计启示

**Per-session 状态必须成组清理**。`_summary_failure_cooldown_until` 与 `_previous_summary` 逻辑上同属"上一次压缩尝试的结果"，在 session reset 时必须同时重置。CE 的 `ContextCompressor.onSessionReset()` 或等价 hook 应审查所有 per-invocation 状态字段。

### BlueCortexCE 迁移检查

```java
// CE 等价检查点：ContextCompressor/ExtractionService 在 session reset 时
// 是否重置了所有 per-session 状态？
// 关键字段：_lastExtractionResult, _consecutiveFailures, _cooldownUntil 等
```

---

## 附：新提交完整列表

| 提交 | 类别 | 说明 | 记忆相关 |
|------|------|------|----------|
| `062800470` | docs | x-ai/grok-4.20-beta rename | ❌ |
| `c659a1689` | fix | quoted relative paths in file drop | ❌ |
| `08b8465ca` | fix | email Date header requirement | ❌ |
| `51dc98d31` | fix | Qwen3/Ollama inline thinking detection | ❌ |
| `0df7e61d2` | fix | omit empty api_mode in model probing | ❌ |
| `52c539d53` | fix | disable SDK retries on per-request OpenAI clients | ❌ |
| `3c070f9f9` | fix | curator: only mark agent-created for background-review | ❌ |
| `bff484a51` | fix | kanban-dashboard UI widen drawer | ❌ |
| `2a52e2856` | fix | skip AUXILIARY_VISION_MODEL write when blank | ❌ |
| `7d36533ae` | fix | default TERM for pty resize probes | ❌ |
| `99faac212` | fix | prevent trailing space in tui picker completions | ❌ |
| `6da970f15` | fix | **close AIAgent on TUI session teardown (FD leak)** | ✅ |
| `4e2b20b70` | fix | sync use_gateway in _reconfigure_provider | ❌ |
| `ba8337464` | fix | extract usageMetadata from Gemini streaming chunks | ❌ |
| `f6aa1965d` | fix | telegram fallback to document when photo exceeds limits | ❌ |
| `ad4542bf6` | fix | gateway free_response_channels override DISCORD_IGNORE_NO_MENTION | ❌ |
| `54cd63336` | fix | skip AI call when cron script produces no output | ❌ |
| `e2248045f` | fix | drop stale env-var override of persisted provider | ❌ |
| `d7663c780` | fix | exclude compose/profile runtime state from docker build | ❌ |
| `f236cbfec` | fix | declare nanostores dependency | ❌ |

---

## 与已有文档的衔接

- **#58**（lazy session creation）：`6da970f15` 是同一主题（session teardown 生命周期）的补充，完善了 `close()` 语义
- **#51**（compressor model switch + background review）：`e2211b268` 是同一压缩机的 bug fix
- **#55**（compressor dedup non-string content）：`e2211b268` 与之同属 ContextCompressor 修复线

**本轮新增**：2 个记忆相关 bug fix，Session teardown + Compressor cooldown 重置。

---

## 上游 HEAD 确认

```
062800470 docs(model-catalog): rename x-ai/grok-4.20-beta to x-ai/grok-4.20 (#19640)
```

下次 cron 巡检从 `062800470` 起扫描新提交。

---
*2026-05-04 17:59 CST — PM Agent 自动生成*
