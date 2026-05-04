# 61. 上游新提交分析（8163d3719 → eeb05cf55）

**扫描范围**：`8163d3719..eeb05cf55`（1830 commits）
**文档版本**：v1 — 2026-05-04

---

## §1 概述

本次扫描覆盖 `8163d3719`（kanban-video-orchestrator，2026-04-25）至 `eeb05cf55`（docs: default custom tool creation to plugins，2026-05-04）共 **1830 个新提交**。按"记忆系统"宽松定义（含 ContextCompressor、Session 管理、MemoryProvider、Tool Result 存储）筛选，共发现 **约 25 个记忆相关提交**，分为以下类别：

| 类别 | 数量 | 代表提交 |
|------|------|----------|
| ContextCompressor Bug/改进 | ~12 | `b7bbc6250`, `6b88f46c5`, `e2211b268` |
| Per-User Memory Scoping | 1 | `c52e59319` ⭐ |
| Honcho Provider 更新 | ~4 | `81088b978`, `2fb2978f4` |
| Memory Provider Plugin 更新 | ~3 | `336bca4fa`, `a83911143` |
| Session/DB 清理 | ~3 | `c653f5dc3`, `8f3e9f80c` |
| Compression Eval Harness | 1 | `1e6285c53` |

---

## §2 ⭐ Per-User Memory Scoping — 多用户网关内存隔离

**Commit**: `c52e59319695b7cdc0be82805d46d21daf67730f`（2026-04-07）  
**问题**: Memory plugins (Mem0, Honcho) 使用静态标识符（`hermes-user`、config `peerName`），导致所有网关用户**共享同一个内存桶**。

### 修复内容

1. **AIAgent.__init__**: 新增 `user_id` 参数，存储为 `self._user_id`
2. **run_agent.py**: 在 `_init_kwargs` 中向 MemoryProvider 传递 `user_id`
3. **gateway/run.py**: 在 primary + background 双路径中将 `source.user_id` 传给 AIAgent
4. **Mem0 plugin**: 优先使用 kwargs `user_id`，回退到 config 默认值
5. **Honcho plugin**: 当存在 gateway `user_id` 时覆盖 `cfg.peer_name`

### 核心 diff 摘录

```python
# gateway/run.py — primary path
+AIAgent(
+    ...
+    user_id=source.user_id,  # 新增
+    ...
+)

# plugins/memory/honcho/__init__.py
+_gw_user_id = kwargs.get("user_id")
+if _gw_user_id:
+    cfg.peer_name = _gw_user_id  # 用 gateway user_id 覆盖

# plugins/memory/mem0/__init__.py
-self._user_id = self._config.get("user_id", "hermes-user")
+self._user_id = kwargs.get("user_id") or self._config.get("user_id", "hermes-user")

# run_agent.py
+if self._user_id:
+    _init_kwargs["user_id"] = self._user_id
```

### 意义

这是 **多用户网关场景下的关键功能**。在 Mem0/Honcho 等外部 Provider 中，每个用户的对话历史、偏好等现在可以完全隔离存储，不会互相干扰。

### BlueCortexCE 借鉴

CE 的多用户隔离目前依赖 Session 粒度。若未来支持多用户共享实例（如 Gateway 模式），可参考此 `user_id` threading 机制，实现 **UserProfile 级别的记忆隔离**。

---

## §3 ContextCompressor 关键改进

### §3.1 `_prune_old_tool_results` Boundary Direction Fix

**Commit**: `b7bbc62503d54cd95de413df7cda2e802fec0206`（2026-04-26）  
**文件**: `agent/context_compressor.py`

#### 问题

旧代码：
```python
prune_boundary = max(boundary, len(result) - min_protect)
```

当 budget 足够大（boundary=0，"保护一切"）时，`max(0, len - min_protect)` 会错误地截断到 `min_protect`，导致"慷慨的 budget 被静默回落到 min_protect"。

#### 修复

在 index space 中做 max 运算是错误的（index 越小 = 保护越多），应在 count space 中操作：

```python
# 在 count-space 做 max（越大 = 保护越多）
budget_protect_count = len(result) - boundary
protected_count = max(budget_protect_count, min_protect)
prune_boundary = len(result) - protected_count
```

#### 测试覆盖

新增 `test_generous_budget_protects_everything_floor_does_not_override`，50 对 assistant/tool 消息，budget 覆盖全部内容，验证 `pruned == 0`。

---

### §3.2 Compressor Timeout Fallback

**Commit**: `6b88f46c5`（2026-04-26）  
**文件**: `agent/context_compressor.py`

#### 问题

Summary 模型调用超时时，compressor 不会 fallback 到主模型，导致**上下文无限累积**。

#### 修复

```python
_is_timeout = (
    _status in (408, 429, 502, 504)
    or "timeout" in _err_str
)
if (
    (_is_model_not_found or _is_timeout)  # 新增 _is_timeout
    and self.summary_model
    and self.summary_model != self.model
    and not getattr(self, "_summary_model_fallen_back", False)
):
    self._summary_model_fallen_back = True
    # Fall back to main model for compression
```

#### BlueCortexCE 借鉴

CE 的 StructuredExtractionService 可参考此错误分类模式：
- **Transient errors** (timeout, 408, 429, 502, 504): 重试 / fallback
- **Permanent errors** (model not found, API key invalid): 快速失败

---

### §3.3 `on_session_reset()` 清理 Cooldown

**Commit**: `e2211b2683d0dacbdb39af9bc5a2b712a742597d`（2026-04-25）  
**文件**: `agent/context_compressor.py`

#### 问题

`on_session_reset()` 清理了 `_previous_summary`, `_last_summary_error`, `_ineffective_compression_count`，但**遗漏了 `_summary_failure_cooldown_until`**。

用户在 `/reset` 或 `/new` 后，如果上一个 session 的 summary 失败设置了 60s（或 600s）cooldown，该 cooldown 会带入新 session，导致压缩被静默跳过。

#### 修复

```python
def on_session_reset(self) -> None:
    ...
+   self._summary_failure_cooldown_until = 0.0  # 新增，与 __init__ 对称
```

---

### §3.4 Image Input Size Cap + Token Charging

**Commit**: `02e328c41`（2026-04-26）  
**文件**: `agent/context_compressor.py`

#### 功能

1. 图像输入**按大小上限 + 缩放**处理
2. 在 compressor 中**按实际 token 计费**（而非原始大小）

这确保了大图像不会因为 token 估算不准确导致上下文溢出。

---

### §3.5 Compression Eval Harness

**Commit**: `1e6285c53`（2026-04-27）  
**文件**: `scripts/compression_eval/`

#### 设计

Probe-based 压缩质量评估框架：

**两阶段**：
1. **Continuation**: 给压缩后的上下文继续任务，验证能力延续
2. **Grading**: LLM 评委打分

**6 维度 Rubric**：
- accuracy（准确性）
- context_awareness（上下文感知）
- artifact_trail（产物轨迹）
- completeness（完整性）
- continuity（连续性）
- instruction_following（指令遵循）

**Fixture Scrub Pipeline**：
- Redact（脱敏）
- Paraphrase（改写）
- Truncate（截断）

#### BlueCortexCE 借鉴

CE 的 **Structured Extraction 质量评估**可直接复用此框架：

```python
# Phase 3 acceptance test 补充
Session observations (as fixture)
    ↓
StructuredExtractionService output (as answer)
    ↓
Adapted rubric scoring
```

这比纯功能正确性测试（`acceptance_test.sh`）更能捕捉**提取质量维度**。

---

## §4 Honcho Provider 更新

### §4.1 Observation Mode Default 变更防护

**Commit**: `2fb2978f4`（2026-04-07）

Observation mode 默认值变更可能破坏现有用户配置，添加 migration guard 防止静默行为变更。

### §4.2 CLI Registration 重构

**Commit**: `824c691ec`（2026-04-07）

Plugin CLI 命令注册系统解耦：
- Plugin 命令不再硬绑定到 core
- 只为**当前激活的 memory provider** 注册 CLI 命令

```python
# 新系统
if is_active_memory_provider(plugin_name):
    register_plugin_cli_commands(plugin)
```

---

## §5 Supermemory Provider 完善

**Commits**: `336bca4fa`, `a83911143`（2026-04-15~04-25）

- Supermemory provider 清理 PR scaffolding
- 线程安全改进
- 与既有 doc 46 分析一致，无新增重大架构变更

---

## §6 Session Search / DB 相关

### §6.1 Session Search 文档澄清

**Commit**: `c653f5dc3`（2026-04-26）

`session_search` auxiliary model 文档澄清，无代码变更。

### §6.2 CLI Session DB Cleanup

**Commit**: `8f3e9f80c`（2026-04-26）

- MRU lookup 严格化
- Session DB cleanup 改进

---

## §7 总结与 CE 借鉴

### 7.1 本次新发现

| 发现 | CE 借鉴优先级 |
|------|--------------|
| Per-user memory scoping (`c52e59319`) | ⭐⭐⭐ 多用户场景必需 |
| Compressor timeout fallback (`6b88f46c5`) | ⭐⭐⭐ 可靠性提升 |
| Cooldown reset on session reset (`e2211b268`) | ⭐⭐ transient state 清理模式 |
| Compression eval harness (`1e6285c53`) | ⭐⭐⭐ Structured Extraction 质量保障 |
| Image size cap + token charging | ⭐⭐ multimodal 安全 |

### 7.2 下次扫描起点

下次 cron 巡检从 `eeb05cf55`（当前 origin/main HEAD）起扫描新提交。

---

*2026-05-04 21:00 CST — PM Agent 自动生成*
