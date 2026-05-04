# 上游新提交分析（a11aed1ac → origin/main）：压缩鲁棒性 + Session 重置 · 85 commits

**日期**: 2026-05-04
**范围**: `a11aed1ac..origin/main`（85 commits）
**发现**: 3 个记忆系统相关提交 + 1 个工具结果存储相关提交

---

## §1 概述

本次扫描范围 85 个新提交，记忆系统相关仅 3 个（1 个压缩器超时 fallback、1 个压缩器 cooldown 重置、1 个 session_search 文档澄清），另有 1 个工具结果存储相关（`read_file` 结果大小封顶）。其余 81 个非记忆相关（Dashboard/Kanban/Model Catalog/TUI/Provider Fixes）。

---

## §2 Compressor Timeout Fallback Fix（`6b88f46c5`）

### 2.1 问题背景

原有 fallback 逻辑仅在以下条件触发：
- HTTP 404/503
- 错误字符串包含 `not found`、`does not exist`、`no available channel`

**超时错误**（HTTP 408/429/502/504 或错误字符串含 `timeout`）进入短 cooldown 分支，导致上下文无限增长。

### 2.2 修复内容

```python
# agent/context_compressor.py
_is_timeout = (
    _status in (408, 429, 502, 504)
    or "timeout" in _err_str
)
if (
    (_is_model_not_found or _is_timeout)  # ← 新增 _is_timeout
    and self.summary_model
    and self.summary_model != self.model
    and not getattr(self, "_summary_model_fallen_back", False)
):
    self._summary_model_fallen_back = True
```

**关键行为变化**：
- 瞬时超时错误现在立即触发 fallback 到主模型
- 不再因为 cooldown 累积导致整会话上下文失控

### 2.3 CE 借鉴

| 方面 | 现状 | 借鉴 |
|------|------|------|
| LLM 调用错误分类 | Phase 3 StructuredExtractionService 无 timeout 处理 | 增加 transient vs permanent 错误分类，永久失败才触发 fallback |
| 上下文增长保护 | 提取失败时无保底机制 | 增加同类 cooldown + fallback 机制 |

---

## §3 Compressor Cooldown Reset on Session Reset（`e2211b268`）

### 3.1 问题背景

`on_session_reset()` 清理了：
- `_previous_summary`
- `_last_summary_error`
- `_ineffective_compression_count`

**但遗漏了** `_summary_failure_cooldown_until`。场景：
1. Summary 模型瞬时错误设置 60s cooldown（或 missing-provider 设置 600s）
2. 用户立即执行 `/reset` 或 `/new`
3. cooldown 携带到新 session
4. 新 session 在 cooldown 过期前达到压缩阈值 → `_generate_summary()` 返回 None → 中间轮次静默丢弃

### 3.2 修复内容

```python
# agent/context_compressor.py
def on_session_reset(self):
    self._previous_summary = None
    self._last_summary_error = None
    self._ineffective_compression_count = 0
    self._summary_failure_cooldown_until = 0.0  # ← 新增：瞬时错误不能阻塞新 session
    self._summary_model_fallen_back = False      # ← 新增：fallback 标志也需重置
```

### 3.3 CE 借鉴

| 方面 | 现状 | 借鉴 |
|------|------|------|
| Session 重置完整性 | ContextService 不清理提取状态 | `on_session_reset()` / `on_session_finalize()` 应清理所有 transient 状态 |
| Cooldown 累积风险 | Phase 3 无 cooldown 机制 | StructuredExtractionService 应在 session 重置时清除所有 cost/cooldown 状态 |

---

## §4 session_search 辅助模型文档澄清（`c653f5dc3`）

### 4.1 变更内容

仅文档/注释更新（`tools/session_search_tool.py` +9/-3 行），澄清辅助模型的用途和限制。

### 4.2 CE 借鉴

CE 的 `/api/memory/search` 端点同样需要类似文档澄清：
- 使用专用 summary 模型 vs 主模型
- 辅助模型失败时的 fallback 行为
- Token 预算限制说明

---

## §5 Tool Result 大小封顶（`e50809b77`，相关）

### 5.1 变更内容

```python
# tools/registry.py 或对应 file-tools
max_result_size_chars=100_000  # 原来是 float('inf')
```

闭合了 `tool_result_storage.py` Layer 2 防御缺口。Layer 1 guard 在 `_handle_read_file` 内部已返回 JSON error。

### 5.2 CE 差距分析

CE 目前 **无** tool result 大小封顶机制。Phase 3 提取结果如果通过工具返回（如 `submitFeedback`），存在潜在无界增长风险。

### 5.3 CE 借鉴

| 层级 | CE 现状 | 建议 |
|------|---------|------|
| Layer 1（tool handler） | 无大小校验 | 工具 handler 内部增加 `max_result_size_chars` 校验 |
| Layer 2（storage） | 无封顶 | `submitFeedback` 增加结果截断 |
| Layer 3（context budget） | 200K budget | 确认 budget 包含 tool result |

---

## §6 `/new` Session 命名支持（`f720751d7`，非记忆但相关）

允许 `/new Refactor auth module` 立即设置 session title，减少事后重命名步骤。

**CE 对应功能**：目前无等价功能，Session title 需手动设置。

---

## §7 非记忆相关提交（81 个）

覆盖：Dashboard Kanban / Model Catalog / Anthropic/Google OAuth / TUI FD Leak Fix / Wecom / Browser / Cronjob / Email / CLI `--profile` / `delegate: null` config guard / Xiaomi MiMo model name dots / Ollama inline thinking detection

---

## §8 结论与维护

**文档状态**：记忆系统分析文档保持最新。

**下次扫描起点**：`origin/main`（`110387d14`）

**下次扫描指令**：
```bash
cd /Users/yangjiefeng/.hermes/hermes-agent
git log --oneline 110387d14..origin/main -- "agent/*memory*" "agent/*context*" "agent/*compress*" "agent/*session*" "plugins/memory/*" "tools/memory*" "tools/session*" "agent/trajectory*"
```
