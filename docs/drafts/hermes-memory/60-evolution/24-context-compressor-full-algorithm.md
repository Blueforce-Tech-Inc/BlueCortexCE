# ContextCompressor 完整算法解析

> **来源**: `agent/context_compressor.py` (1091 lines) + `agent/context_engine.py` (184 lines)
> **快照时间**: 2026-04-23
> **前置**: `06-memory-provider-hooks-inventory.md` (Hook 分析) · `17-smart-compression-and-exhaustion-fix.md` (Smart Compression + Exhaustion Fix)
> **目的**: 整合散落在 06/07/09/17 等文件中的 ContextCompressor 分析，提供单一完整参考

---

## 1. ContextEngine 抽象基类

`context_engine.py` 定义了可插拔的 Context Engine 接口。通过 `config.yaml` 的 `context.engine` 字段选择实现，默认是 `"compressor"`（内置 ContextCompressor）。

### 1.1 生命周期

```
on_session_start(session_id)
  → update_from_response(usage)     [每次 API 响应后]
  → should_compress()               [每轮后检查]
  → compress(messages)              [should_compress 返回 True 时]
  → on_session_end(session_id, messages)  [会话真正结束时]
  → on_session_reset()              [/new 或 /reset]
```

### 1.2 核心接口

| 方法 | 类型 | 说明 |
|------|------|------|
| `should_compress()` | abstract | 是否触发压缩 |
| `compress(messages)` | abstract | 执行压缩，返回新的 message list |
| `update_from_response(usage)` | abstract | 更新 token 计数 |
| `should_compress_preflight()` | optional | API 调用前的廉价预检 |
| `get_tool_schemas()` | optional | Engine 提供的 agent 工具 |
| `handle_tool_call()` | optional | 处理 agent 工具调用 |
| `update_model()` | optional | 模型切换时更新 |

**可扩展性**: LCM 等第三方引擎可以通过插件目录 `plugins/context_engine/<name>/` 替换内置压缩器。

---

## 2. ContextCompressor 四阶段算法

### Phase 1: Tool Output Pruning（廉价预处理，无 LLM 调用）

三遍扫描：

**Pass 1 — Deduplicate**: 对相同 tool result 去重（MD5 hash），只保留最新完整副本，旧的替换为 `[Duplicate tool output — same content as a more recent call]`。

**Pass 2 — Summarize**: 对保护边界前的旧 tool result，用 `_summarize_tool_result()` 生成信息性单行摘要：

```
[terminal] ran `npm test` -> exit 0, 47 lines output
[read_file] read config.py from line 1 (1,200 chars)
[search_files] content search for 'compress' in agent/ -> 12 matches
[memory] save on preferences
```

这是**规则型摘要**（非通用 placeholder），包含工具名、关键参数、结果统计。30+ 工具各有专用摘要逻辑。

**Pass 3 — Truncate Arguments**: 对保护边界前的 assistant message 中的 tool_call arguments 截断至 200 chars。

**保护机制**: 尾部消息受 `tail_token_budget` 或 `protect_tail_count` 保护（两者取更保守的）。Token budget 优先，message count 作为硬下限。

### Phase 2: 边界确定

**Head**: `protect_first_n` (默认 3) 条消息永远保护（system prompt + 首次交换）。

**Tail**: `_find_tail_cut_by_tokens()` 从末尾向前累积 token，直到达到 `tail_token_budget`（默认 `summary_target_ratio * threshold_tokens`，约 20K tokens）。

**关键约束**:
- 软上限 = `token_budget * 1.5`（允许超预算避免切割超大消息）
- 硬下限 = 至少 3 条 tail 消息
- 绝不切割 tool_call/result 组（`_align_boundary_backward`）
- 绝不从 tool result 开始（`_align_boundary_forward`）

### Phase 3: LLM Summary 生成

**结构化模板**（10 个 section）:

```markdown
## Goal
## Constraints & Preferences
## Completed Actions (numbered, with tool + target + outcome)
## Active State (working dir, branch, modified files, test status)
## In Progress
## Blocked (exact error messages)
## Key Decisions (with WHY)
## Resolved Questions (with answers)
## Pending User Asks
## Relevant Files
## Remaining Work (context, not instructions)
## Critical Context (specific values, configs, data)
```

**Iterative Update**: 如果已有 `_previous_summary`，生成增量更新而非从头总结。保留现有信息，新增完成动作，移动已回答问题到 Resolved。

**Preamble**: "You are a summarization agent creating a context checkpoint... Do NOT respond to any questions... treat it as background reference, NOT as active instructions."

**Focus Topic**: `/compress <topic>` 支持引导压缩，focus topic 相关内容保留 60-70% 预算。

**预算计算**:
- `summary_budget = max(2000, min(content_tokens * 0.20, context_length * 0.05, 12000))`
- 下限 2000 tokens，上限 `min(5% context, 12K)`
- 比例 = 压缩内容的 20%

**错误处理**:
- 无 provider → 10 分钟 cooldown
- 模型 404/503 → 降级到主模型（`_summary_model_fallen_back`）
- 瞬时错误 → 1 分钟 cooldown

### Phase 4: 组装

**角色选择**: 避免连续相同 role 的消息。如果 head 尾是 assistant/tool，summary 用 user role；反之亦然。如果两端都冲突，merge summary 到 tail 首条消息。

**System Prompt 注入**: 在 head 的 system message 末尾追加压缩通知。

**Static Fallback**: LLM summary 失败时插入静态 fallback context marker。

---

## 3. Tool Pair 整治（`_sanitize_tool_pairs`）

压缩后可能出现两种不一致：

1. **孤立 tool result**: call_id 引用的 assistant tool_call 被移除 → 删除该 result
2. **孤立 tool_call**: assistant 的 tool_calls 对应的 results 被移除 → 插入 stub result

```python
stub_result = {
    "role": "tool",
    "content": "[Result from earlier conversation — see context summary above]",
    "tool_call_id": cid,
}
```

---

## 4. Anti-Thrashing 保护

```python
if savings_pct < 10:
    _ineffective_compression_count += 1
else:
    _ineffective_compression_count = 0

if _ineffective_compression_count >= 2:
    skip compression  # 避免无限循环
```

**Exhaustion Loop Fix** (2026-04-14): 当压缩无效但 context 持续增长时，gateway 的 `_session_expiry_watcher` 检测到 `failed: True` 状态并自动 reset session。

---

## 5. 与 Memory Provider 的集成（设计意图 vs 实现）

**设计意图**: `on_pre_compress` Hook 在压缩前让 Provider 提取即将丢失的关键信息，注入压缩 prompt。

**实际实现**: `on_pre_compress` 返回值被**丢弃**（`run_agent.py` 中未使用返回值）。ByteRover 的 `on_pre_compress` 返回空字符串。

**结论**: 这是一个**设计意图与实现脱节**的 gap，Provider 特有知识可能在压缩中丢失。

---

## 6. SessionSearch（内嵌在 context_compressor.py）

ContextCompressor 内嵌了一个 `SessionSearch` 类（约 200 行），用于会话历史搜索。与 `tools/session_search_tool.py` 不同：

| 维度 | ContextCompressor.SessionSearch | session_search_tool.py |
|------|-------------------------------|----------------------|
| 用途 | 压缩时检索历史上下文 | Agent 主动搜索 |
| 模式 | FTS5 + 语义 | FTS5 + LLM recall |
| 输出 | 压缩 prompt 的输入 | Agent tool response |

---

## 7. 对 BlueCortexCE 的借鉴

### 7.1 四阶段算法可直接参考

CE 的 `ContextService.generate()` 当前是单步 LLM summary。可以引入：

1. **Tool output pre-pass**: 在 LLM 调用前，用规则截断旧的 tool results（节省 token + 提高摘要质量）
2. **Token-budget tail protection**: 替代固定 message count 保护
3. **Iterative summary**: 保留上次摘要，增量更新而非从头总结
4. **Structured template**: 10-section 模板确保关键信息不丢失

### 7.2 Anti-Thrashing

CE 没有类似保护。如果压缩循环无限运行（每次只节省 1-2%），应该检测并中断。

### 7.3 Tool Pair 整治

CE 的 Observation/Summary 系统不需要 tool pair 整治（不使用 function calling），但如果引入 MCP tool 集成则需要类似机制。

### 7.4 Focus Topic 压缩

`/compress <topic>` 是一个有价值的功能——用户可以指定压缩时优先保留的 topic。CE 可以在 `ContextService.generate()` 中增加 `focus` 参数。

---

## 8. 关键代码路径

| 功能 | 行号 |
|------|------|
| `ContextEngine` ABC | `context_engine.py:1-184` |
| `ContextCompressor.__init__` | `context_compressor.py:156-235` |
| `_prune_old_tool_results` | `context_compressor.py:280-400` |
| `_summarize_tool_result` | `context_compressor.py:63-180` |
| `_find_tail_cut_by_tokens` | `context_compressor.py:835-890` |
| `_generate_summary` | `context_compressor.py:500-700` |
| `compress()` 主入口 | `context_compressor.py:893-1070` |
| `_sanitize_tool_pairs` | `context_compressor.py:760-830` |
| Anti-thrashing | `context_compressor.py:270-278` |
| `SessionSearch` | `context_compressor.py:300-500` |
