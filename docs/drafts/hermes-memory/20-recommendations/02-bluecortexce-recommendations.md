
## 10. BlueCortexCE 借鉴建议汇总

> **行动顺序综述**（可验收优先级、围栏/检索/fallback 边界）：见同目录 [`03-borrowing-synthesis-executable-priorities.md`](03-borrowing-synthesis-executable-priorities.md)。

### 10.1 架构层面

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|-------------|-------------------|----------|--------|
| 多 provider | MemoryManager 支持 builtin + 1 external | 单一实现 | 考虑抽象 Provider 接口，保留当前实现为 DefaultProvider | 中 |
| Session 存储 | SQLite + FTS5 | PostgreSQL + pgvector | 可互补：FTS5 做 keyword search，pgvector 做 semantic search | 高 |
| curated memory | 平面文件（MEMORY.md/USER.md） | PostgreSQL 存储 | 保留 DB 方案，但可借鉴 bounded char limit 设计 | 中 |

### 10.2 检索层面（重要！）

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|-------------|-------------------|----------|--------|
| Keyword search | FTS5 (BM25 ranking) | 无专门 keyword 搜索 | 增加 keyword search path via FTS5 或 pg_trgm，与 vector search 互补 | **高** |
| Session 历史摘要 | 先 FTS5 匹配 → 截断 → Gemini Flash 摘要 | 无此功能 | 实现 session_history_search，流程照搬：FTS → 父子解析 → 截断 → LLM 摘要 | **高** |
| 截断策略 | phrase + proximity + individual term 三级窗口 | 无专门截断策略 | `_truncate_around_matches` 算法值得借鉴 | 中 |
| 父子 session 解析 | delegation chain walk | 无 | 实现 parent_session_id 链式解析 | 中 |

### 10.3 上下文注入层面

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|-------------|-------------------|----------|--------|
| System prompt 稳定性 | Frozen snapshot（session start 捕获，之后不变） | 每次 generate 重新组装 | **直接借鉴** — mid-session writes 不改变已注入 context，解决 prefix cache 失效问题 | **高** |
| Memory context fence | `<memory-context>` 标签 + system note | 无 | 增加 fence tag，防止 model 把 recall 当用户输入 | **高** |
| 背景预取 | queue_prefetch + prefetch 分离 | 无 | turn 结束时预取下一 turn 所需 context | 中 |

### 10.4 生命周期层面

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|-------------|-------------------|----------|--------|
| 容量硬限制 | MEMORY.md 2200 / USER.md 1375 chars | Observation/Summary 无硬上限 | 考虑对各 table 设置合理容量上限（如 summary 总 chars），强制用户/系统管理遗忘 | 中 |
| Session 过期 | prune_sessions (默认 90 天) | 无 | 实现 session prune cron job | 低 |
| End-of-session 提取 | `on_session_end` hook | 有 Summary hook（类似） | 当前设计已足够，可对比补充 | 低 |

### 10.5 安全层面（重要！）

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|-------------|-------------------|----------|--------|
| Injection 扫描 | `_scan_memory_content` 对所有写入内容扫描 | 无 | **立即添加** — 对所有 ingest 内容做 injection pattern + invisible unicode 扫描 | **高** |
| Invisible unicode | 零宽字符、RTL 覆盖等检测 | 无 | 同上 | **高** |
| Context file 扫描 | prompt_builder 对 AGENTS.md/SOUL.md 扫描 | AGENTS.md/SOUL.md 直接注入 | 增加扫描 gate，阻断含 injection 模式的内容 | **高** |

### 10.6 Write timing / Nudge 层面

Hermes 的 memory nudge 机制（每 10 轮提示一次写入，最少 6 轮后才允许提示）：

```python
# run_agent.py:1136-1137
_memory_nudge_interval = 10      # 每 N 轮 nudge
_memory_flush_min_turns = 6      # 最少 N 轮后才第一次 nudge
_turns_since_memory = 0
```

**借鉴**: BlueCortexCE 可增加定期 nudge prompt（当用户未触发 Observation 写入时）。

---

### 10.7 重要澄清：BuiltinMemoryProvider 不存在

> ⚠️ **关键发现**：`agent/memory_manager.py` 和注释中多次提及 `BuiltinMemoryProvider`，但实际上**这个类并不存在**。

**实际架构**：
- `agent/memory_provider.py` 中的 `MemoryProvider` 是**抽象基类**，仅用于**外部插件 providers**（honcho、hindsight、mem0 等）
- 内置的 MEMORY.md/USER.md 通过 `MemoryStore`（`tools/memory_tool.py`）直接管理，**不由 MemoryManager 处理**
- `run_agent.py` 中通过 `self._memory_store` 直接引用 `MemoryStore` 实例

```python
# run_agent.py:1136-1153（实际初始化代码）
self._memory_store = None
self._memory_enabled = False
self._user_profile_enabled = False
self._memory_nudge_interval = 10
self._memory_flush_min_turns = 6
self._turns_since_memory = 0
if not skip_memory:
    mem_config = _agent_cfg.get("memory", {})
    self._memory_enabled = mem_config.get("memory_enabled", False)
    self._user_profile_enabled = mem_config.get("user_profile_enabled", False)
    if self._memory_enabled or self._user_profile_enabled:
        from tools.memory_tool import MemoryStore
        self._memory_store = MemoryStore(...)
        self._memory_store.load_from_disk()
```

**MemoryManager 只处理外部 providers**（`run_agent.py:1192-1198`）：
```python
from agent.memory_manager import MemoryManager as _MemoryManager
self._memory_manager = _MemoryManager()
_mp = _load_mem(_mem_provider_name)  # 从插件加载
self._memory_manager.add_provider(_mp)  # 只有一个 external provider
```

**架构分离**：
| 组件 | 管理方 | 用途 |
|------|--------|------|
| MEMORY.md / USER.md | AIAgent (`self._memory_store`) | 内置 curated memory |
| 外部 provider (honcho/mem0) | MemoryManager | 插件化 semantic memory |

**对 BlueCortexCE 借鉴**：
- BlueCortexCE 的 "旁路型" 设计与 Hermes 内置记忆直接管理类似
- 但 Hermes 外部 provider 与内置 store 完全隔离，BlueCortexCE 是否需要这种分离？

---

### 10.8 ContextCompressor 深度解析（上下文压缩引擎）

> **文件**: `agent/context_compressor.py` (~570 行)
> 这是 Hermes 最复杂的模块之一，负责在 context 即将溢出时压缩对话历史。

#### 核心设计原则

1. **保护 head**（系统提示 + 前 N 条消息）
2. **保护 tail**（最近 N 条消息，按 token 预算而非固定条数）
3. **压缩 middle**（中间消息 LLM summarization）
4. **迭代更新**（后续压缩时在前一个 summary 基础上增量更新）

#### Phase 1: 工具结果修剪（廉价预热，无 LLM 调用）

```python
# context_compressor.py:237-290
def _prune_old_tool_results(self, messages, protect_tail_count, protect_tail_tokens):
    """
    三次 Pass：
    1. 去重：相同内容只保留最新，旧的替换为 "[Duplicate tool output]"
    2. 摘要化：超过 200 chars 的旧 tool result → 1行信息摘要
    3. 截断：assistant 消息中超过 500 chars 的 tool_call arguments
    """
```

**工具结果摘要生成器**（`_summarize_tool_result`）：
```python
# 常见工具的摘要模板：
[terminal] ran `npm test` -> exit 0, 47 lines output
[read_file] read config.py from line 1 (3,400 chars)
[write_file] wrote to config.py (120 lines)
[search_files] content search for 'compress' in agent/ -> 12 matches
[patch] replace in config.py:45 (200 chars result)
```

#### Phase 2: Tail 保护（Token 预算策略）

```python
# context_compressor.py:380-420
def _find_tail_cut_by_tokens(self, messages, head_end, token_budget=None):
    """
    从后向前累加 token，保护最近 tail_token_budget 数量的 tokens。
    硬性最小保护 3 条消息。
    软上限 1.5x 避免在 oversized message 中切割。
    永远不会切割 tool_call/result group。
    """
```

**Tail token budget 计算**：
```python
target_tokens = int(threshold_tokens * summary_target_ratio)  # 20% of threshold
self.tail_token_budget = target_tokens  # ~20K tokens for 128K context
```

#### Phase 3: LLM Summarization（结构化摘要模板）

```python
# context_compressor.py:450-520
_SUMMARY_TEMPLATE = f"""
## Goal
[What the user is trying to accomplish]

## Constraints & Preferences
[User preferences, coding style, constraints, important decisions]

## Completed Actions
[Numbered list with FORMAT: N. ACTION target — outcome [tool: name]
Example: 1. READ config.py:45 — found `==` should be `!=` [tool: read_file]]

## Active State
[Current working state — files, test status, running processes]

## In Progress
[Work currently underway when compaction fired]

## Blocked
[Any blockers, errors, or issues not yet resolved]

## Key Decisions
[Important technical decisions and WHY they were made]

## Resolved Questions
[Questions ALREADY answered — include the answer]

## Pending User Asks
[Questions NOT yet answered or fulfilled. If none, write "None."]

## Relevant Files
[Files read, modified, or created — with brief note]

## Remaining Work
[What remains to be done — framed as context, not instructions]

## Critical Context
[Any specific values, error messages, configuration details]
"""
```

**迭代更新**（第二次及后续压缩）：
```python
# 如果已有 previous_summary，prompt 改为：
prompt = f"""PREVIOUS SUMMARY:
{self._previous_summary}

NEW TURNS TO INCORPORATE:
{content_to_summarize}

Update the summary using this exact structure.
PRESERVE all existing information that is still relevant.
ADD new completed actions to the numbered list.
Move items from "In Progress" to "Completed Actions" when done.
"""
```

#### Phase 4: 完整性修复（Orphan 清理）

```python
# context_compressor.py:340-375
def _sanitize_tool_pairs(self, messages):
    """
    两种失败模式：
    1. tool result 的 call_id 在 assistant 中找不到 → 删除该 result
    2. assistant 的 tool_calls 没有对应 result → 插入 stub result

    防止 API 报错："No tool call found for function call output with call_id ..."
    """
```

#### Anti-Thrashing（防止压缩震荡）

```python
# context_compressor.py:296-308
def should_compress(self, prompt_tokens):
    # 如果最近两次压缩每次节省 < 10%，跳过压缩
    if self._ineffective_compression_count >= 2:
        logger.warning("Compression skipped — last N compressions saved <10%% each.")
        return False
    return True
```

#### Focus Topic（/compact <topic> 引导压缩）

```python
# 用户执行 /compress git，下发 focus_topic 参数
# Summarizer 会优先保留与 focus_topic 相关的信息：
# - 相关 → 保留完整细节（file paths, 命令输出, error messages）
# - 不相关 → 激进压缩（brief one-liners 或 omit）
# Focus topic 获得 ~60-70% 的 summary token budget
```

#### 与 BlueCortexCE 对比

| 维度 | Hermes ContextCompressor | BlueCortexCE |
|------|-------------------------|--------------|
| Middle 压缩 | LLM summarization（结构化模板） | Observation/Summary 分级 |
| Tail 策略 | Token budget（非固定条数） | 按 maxChars 截断 |
| 迭代压缩 | previous_summary 增量更新 | 无 |
| Orphan 保护 | tool pair 完整性检查 | 无 |
| 工具摘要 | 1行摘要（非占位符） | 无专门处理 |
| 专注模式 | focus_topic 参数引导 | 无 |
| Anti-thrashing | savings < 10% 时跳过 | 无 |

**借鉴建议**：
- **高优先级**：实现工具结果摘要（BlueCortexCE 的 tool result 目前直接截断，无信息保留）
- **中优先级**：Tail 保护从固定 chars 改为 token budget
- **中优先级**：添加 previous_summary 迭代更新机制
- **低优先级**：添加 focus_topic 引导压缩

---

### 10.9 Memory Nudge 机制详解

> **文件**: `run_agent.py:2157-2235`（nudge prompt）, `run_agent.py:7914-7930`（触发逻辑）, `run_agent.py:10750-10752`（执行）

#### 触发时机

```python
# run_agent.py:7914-7930
# 每个用户 turn 计数
self._user_turn_count += 1

# 检查是否应该 nudge
_should_review_memory = False
if (self._memory_nudge_interval > 0
        and "memory" in self.valid_tool_names
        and self._memory_store):
    self._turns_since_memory += 1
    if self._turns_since_memory >= self._memory_nudge_interval:
        _should_review_memory = True
        self._turns_since_memory = 0
```

**条件**：
1. `_memory_nudge_interval > 0`（配置项，默认 10 轮）
2. `memory` tool 在 `valid_tool_names` 中（即 memory 已启用）
3. `_memory_store` 存在（即 MemoryStore 已加载）

#### Nudge Prompt 模板

```python
# run_agent.py:2157-2172
_MEMORY_REVIEW_PROMPT = (
    "Review the conversation above and consider saving to memory if appropriate.\n\n"
    "Focus on:\n"
    "1. Has the user revealed things about themselves — their persona, desires, "
    "preferences, or personal details worth remembering?\n"
    "2. Has the user expressed expectations about how you should behave, their work "
    "style, or ways they want you to operate?\n\n"
    "If something stands out, save it using the memory tool. "
    "If nothing is worth saving, just say 'Nothing to save.' and stop."
)
```

#### 后台 Review 执行（_spawn_background_review）

```python
# run_agent.py:2198-2282
def _spawn_background_review(self, messages_snapshot, review_memory=False, review_skills=False):
    """创建完整的 AIAgent fork，包含相同 model/tools/context"""

    def _run_review():
        review_agent = AIAgent(
            model=self.model,
            max_iterations=8,       # 最多 8 轮工具调用
            quiet_mode=True,
            platform=self.platform,
            # ... 继承所有配置
        )
        # 共享 _memory_store 和 _memory_enabled
        review_agent._memory_store = self._memory_store
        review_agent._memory_enabled = self._memory_enabled
        review_agent._memory_nudge_interval = 0  # 防止递归
        # 在 forked 对话中追加 nudge prompt 作为下一个 user turn
        review_agent.run_conversation(messages_snapshot + [nudge_msg])

    import threading
    t = threading.Thread(target=_run_review, daemon=True)
    t.start()
```

**关键特性**：
- Fork 完整的 AIAgent（相同 model、tools、context）
- 共享同一个 `MemoryStore`（`_memory_store` 是同一对象引用）
- `max_iterations=8` 限制工具调用次数
- 后台线程执行，不阻塞主对话
- `quiet_mode=True` 静默运行，不产生用户可见输出

#### Memory Flush（压缩前写入）

```python
# run_agent.py:6633-6690
def flush_memories_if_needed(self, messages=None, min_turns=None):
    """在 context 压缩前给模型一次机会保存记忆"""
    if self._memory_flush_min_turns == 0 and min_turns is None:
        return
    if self._user_turn_count < effective_min:
        return

    flush_content = (
        "[System: The session is being compressed. "
        "Save anything worth remembering — prioritize user preferences, "
        "corrections, and recurring patterns over task-specific details.]"
    )
    _sentinel = f"__flush_{id(self)}_{time.monotonic()}"
    flush_msg = {"role": "user", "content": flush_content, "_flush_sentinel": _sentinel}
    messages.append(flush_msg)

    # 单次 API call 执行 memory tool calls
    # 执行后 strip flush artifacts
```

#### 与 BlueCortexCE 对比

| 维度 | Hermes Nudge | BlueCortexCE |
|------|-------------|--------------|
| 触发机制 | Turn-based counter（每 N 轮） | 无主动 nudge |
| Nudge 内容 | 聚焦 user preferences/persona | Observation 一般性提取 |
| 执行方式 | Forked AIAgent 后台执行 | N/A |
| 压缩前 flush | `flush_memories_if_needed` | 无 |
| 最小轮数保护 | `_memory_flush_min_turns`（默认 6） | 无 |

**借鉴建议**：
- **高优先级**：BlueCortexCE 可实现 turn-based nudge counter
- **中优先级**：添加 `flush_memories_if_needed` 在 session 结束时兜底提取
- **低优先级**："forked agent" 执行方式过于重量，BlueCortexCE 可考虑在主 agent 中内联 nudge

---

> **SSS11-15 deep dives split to**: [02b-deep-dives.md](02b-deep-dives.md) (preemptive split; 2026-04-24).
