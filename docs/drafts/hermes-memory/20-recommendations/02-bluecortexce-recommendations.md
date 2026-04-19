
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

## 11. SessionSearch LLM Summarization 成本控制策略

> **本节为 v3.1 新增**，深入分析 Hermes 如何控制 session 搜索中 LLM 摘要调用的成本。

### 11.1 关键参数（hard-coded 守卫）

```python
# tools/session_search_tool.py:23-24
MAX_SESSION_CHARS = 100_000     # 单个 session 截断上限
MAX_SUMMARY_TOKENS = 10_000     # LLM 输出 hard cap

# tools/session_search_tool.py:270
limit = min(limit, 5)           # 最大 5 个 session（cap at 5）
```

**三层硬性限制**：
1. **Session 数量**：`limit` 参数 max 5（超过直接截断）
2. **输入长度**：每个 session 截断至 100k chars
3. **输出长度**：LLM 摘要 max 10k tokens（Gemini Flash 通常远低于此）

### 11.2 并行 summarization（吞吐量优先）

```python
# tools/session_search_tool.py:270-280
async def _summarize_all() -> List[Union[str, Exception]]:
    """Summarize all sessions in parallel."""
    coros = [
        _summarize_session(text, query, meta)
        for _, _, text, meta in tasks
    ]
    return await asyncio.gather(*coros, return_exceptions=True)
```

所有 session **并行**调用 LLM，而非串行。这样 5 个 session 的总延迟 ≈ 1 个 LLM 调用的延迟，而非 5 倍。

### 11.3 超时保护（60s 全局）

```python
# tools/session_search_tool.py:261-276
try:
    from model_tools import _run_async
    results = _run_async(_summarize_all())   # 有 60s timeout
except concurrent.futures.TimeoutError:
    logging.warning("Session summarization timed out after 60 seconds")
    return json.dumps({
        "success": False,
        "error": "Session summarization timed out. Try a more specific query or reduce the limit.",
    })
```

**重要发现**：使用 `_run_async()` 而非 `asyncio.run()`，解决事件循环与 cached httpx clients 的冲突（#2681）。

### 11.4 重试 + 指数退避

```python
# tools/session_search_tool.py:196-216
max_retries = 3
for attempt in range(max_retries):
    try:
        response = await async_call_llm(task="session_search", ...)
        content = extract_content_or_reasoning(response)
        if content:
            return content
    except Exception as e:
        if attempt < max_retries - 1:
            await asyncio.sleep(1 * (attempt + 1))   # 1s, 2s, 3s...
        else:
            return None
```

最多 3 次重试，每次等待 `attempt + 1` 秒。

### 11.5 LLM 失败降级（Raw Preview Fallback）

```python
# tools/session_search_tool.py:308-320
if result:
    entry["summary"] = result
else:
    # Fallback: raw preview — fixes #3409
    preview = (conversation_text[:500] + "\n…[truncated]")
    entry["summary"] = f"[Raw preview — summarization unavailable]\n{preview}"
```

**关键修复 #3409**：当 LLM 不可用时，不静默丢弃匹配结果，而是返回原始前 500 字符。**不浪费有价值的匹配**。

### 11.6 Auxiliary Client 路由（低成本模型优先）

```python
# agent/auxiliary_client.py:1-50（注释原文）
"""
Resolution order for text tasks (auto mode):
  1. OpenRouter  (OPENROUTER_API_KEY)
  2. Nous Portal (~/.hermes/auth.json active provider)
  3. Custom endpoint (config.yaml model.base_url + OPENAI_API_KEY)
  4. Codex OAuth (Responses API via chatgpt.com with gpt-5.3-codex)
  5. Native Anthropic
  6. Direct API-key providers (z.ai/GLM, Kimi/Moonshot, MiniMax)
  7. None
"""
```

`async_call_llm(task="session_search", ...)` 通过 `task="session_search"` 使用**专用 auxiliary 路由**，会自动降级到最便宜可用的 provider。

### 11.7 无缓存（设计取舍）

```python
# tools/session_search_tool.py
# 没有任何 session_search 结果缓存
# 每次搜索都重新调用 LLM
```

**成本代价**：重复搜索相同 query 会重复计费。
**收益**：实现简单，无缓存失效问题，始终返回最新 session 内容。

### 11.8 与 BlueCortexCE 对比

| 维度 | Hermes SessionSearch | BlueCortexCE |
|------|---------------------|--------------|
| 并行化 | asyncio.gather 全并行 | 无 parallel LLM |
| 超时保护 | 60s global timeout | 无超时控制 |
| 重试 | 3次 + 指数退避 | 无重试 |
| 失败降级 | Raw preview fallback | 无降级 |
| 缓存 | 无 | 无 |
| 模型路由 | auxiliary auto-fallback | 直接 OpenAI |
| LLM 输出 cap | 10k tokens hard cap | 无专门限制 |
| Session 数量 cap | 5 | N/A |

**借鉴建议**：
- **高优先级**：BlueCortexCE 的 `/api/memory/experiences` 可增加 parallel summarization（当前串行）
- **高优先级**：增加 LLM 调用超时保护（避免 hang）
- **中优先级**：失败降级策略（当 LLM summarization 失败时返回原始观察）
- **中优先级**：`MAX_SUMMARY_TOKENS` 对 BlueCortexCE 的 SummaryEntity.content 也有意义（当前无 cap）

---

## 12. 生命周期 Hook 完整集成分析

> **本节为 v3.1 新增**，对比 BlueCortexCE 和 Hermes 的生命周期 hook 体系。

### 12.1 Hermes Hook 体系（6 个 hooks，MemoryProvider 驱动）

**文件**: `agent/memory_provider.py`, `agent/memory_manager.py`, `run_agent.py`

| Hook | 调用时机 | BlueCortexCE 对应 | 方向 |
|------|----------|-------------------|------|
| `initialize()` | Session 启动时 | `/api/session/start` | 启动时 |
| `on_turn_start()` | 每个 user turn 开始 | **无直接对应** | 每轮 |
| `sync_turn()` | 每个 turn 完成后 | POST `/api/ingest/tool-use` | 每轮 |
| `queue_prefetch()` | 每个 turn 完成后 | **无对应** | 每轮 |
| `prefetch()` | 下一 turn 开始前（API call 前） | **无对应** | 每轮 |
| `on_pre_compress()` | Context 压缩前 | **无直接对应** | 压缩时 |
| `on_session_end()` | Session 结束时 | POST `/api/ingest/session-end` | 结束时 |
| `on_delegation()` | Subagent 完成后（parent 侧） | **无对应** | 委托时 |
| `on_memory_write()` | Builtin memory 写入时 | **无对应** | 写入时 |

**关键发现**：Hermes 有 `on_turn_start()` hook，**BlueCortexCE 没有**。这是 BlueCortexCE 的空白。

### 12.2 BlueCortexCE Hook 体系（5 个 hooks，通过 thin proxy 注入）

**文件**: `backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java`

| Hook | 触发方式 | Hermes 对应 |
|------|----------|-------------|
| `SessionStart` | wrapper.js `session-start` | `initialize()` |
| `UserPromptSubmit` | wrapper.js `user-prompt` | 无直接对应 |
| `PostToolUse` | wrapper.js `tool-use` | `sync_turn()` |
| `Summary` (自动) | `SessionEnd` 后触发 | `on_session_end()` |
| `SessionEnd` | wrapper.js `session-end` | `on_session_end()` |

### 12.3 MemoryManager 编排机制（All Providers + All Hooks）

```python
# agent/memory_manager.py:166-240
def prefetch_all(self, query, *, session_id) -> str:
    """Collect from ALL providers, merge into one string."""
    parts = []
    for provider in self._providers:
        result = provider.prefetch(query, session_id=session_id)  # 全部调用
        if result.strip():
            parts.append(result)
    return "\n\n".join(parts)   # 串联所有 provider 的结果

def on_pre_compress(self, messages) -> str:
    """Collect from ALL providers, merge into one string."""
    parts = []
    for provider in self._providers:
        result = provider.on_pre_compress(messages)  # 全部调用
        if result.strip():
            parts.append(result)
    return "\n\n".join(parts)
```

**设计模式**：MemoryManager 是**所有 provider 所有 hooks 的单一聚合点**。不只是一对多路由，而是多对多聚合。

### 12.4 Prefetch + queue_prefetch 分离机制（最重要的 Hook 模式）

```python
# run_agent.py:10745-10746
# Turn 完成后：queue 下一个 turn 的预取（后台执行）
self._memory_manager.sync_all(original_user_message, final_response)
self._memory_manager.queue_prefetch_all(original_user_message)

# run_agent.py:8120-8125
# 下一 turn 开始：消费缓存的预取结果（无额外延迟）
_ext_prefetch_cache = self._memory_manager.prefetch_all(_query) or ""
```

**时间线**：
```
Turn N 完成 → queue_prefetch(Turn N content)  [后台线程启动]
                                    ↓
                              provider 后台执行检索
                                    ↓
Turn N+1 开始 → prefetch() 返回缓存结果  [零额外延迟]
```

这是 Hermes 最低延迟的记忆检索机制。**BlueCortexCE 完全缺失这个设计**。

### 12.5 on_pre_compress Hook 的聚合价值

```python
# run_agent.py:6804
# Context 压缩前：让所有 provider 都有机会提取即将丢弃的信息
self._memory_manager.on_pre_compress(messages)
# → honcho.on_pre_compress → Honcho 云端保存
# → holographic.on_pre_compress → HRR 编码提取
# → mem0.on_pre_compress → Mem0 云端保存
```

所有 provider 共享同一份即将压缩的 messages。**无需每个 provider 独立读取数据库**。

### 12.6 BlueCortexCE vs Hermes Hook 对齐矩阵

| BlueCortexCE | Hermes | 对齐状态 | 说明 |
|--------------|--------|----------|------|
| `SessionStart` | `initialize()` | ✅ 对齐 | 启动时初始化 |
| `UserPromptSubmit` | 无直接对应 | ⚠️ 部分空白 | Hermes 无此 hook |
| `PostToolUse` | `sync_turn()` | ✅ 功能对齐 | 都是 turn 后写入 |
| `Summary` | `on_session_end()` | ✅ 功能对齐 | Session 结束时总结 |
| `SessionEnd` | `on_session_end()` | ✅ 对齐 | Session 结束时 |
| - | `on_turn_start()` | ❌ 缺失 | BlueCortexCE 无 turn 级别启动 hook |
| - | `queue_prefetch()` | ❌ 缺失 | BlueCortexCE 无预取队列 |
| - | `prefetch()` | ❌ 缺失 | BlueCortexCE 无 turn 前预取 |
| - | `on_pre_compress()` | ⚠️ 部分对齐 | BlueCortexCE 无专门压缩前 hook |
| - | `on_delegation()` | ❌ 缺失 | BlueCortexCE 无 subagent 委托 |
| - | `on_memory_write()` | ❌ 缺失 | BlueCortexCE 无 builtin 写入镜像 |

### 12.7 借鉴建议

| 优先级 | 建议 | 理由 |
|--------|------|------|
| **高** | 实现 `queue_prefetch` + `prefetch` 机制 | 零延迟记忆召回，Hermes 最重要的设计之一 |
| **高** | 实现 `on_pre_compress` hook | Context 压缩前兜底提取，防止信息永久丢失 |
| **中** | 增加 `on_turn_start` hook | 实现 turn 级别的 periodic maintenance |
| **中** | BlueCortexCE SessionStart → 调用所有 Provider 的 `initialize()` | 当前 SessionStart 只创建 DB session，未初始化 provider |
| **低** | `on_delegation` hook | BlueCortexCE 当前无 subagent 委托机制 |

---
## 13. Hindsight Provider — 知识图谱 + 实体消歧架构

> **文件**: `plugins/memory/hindsight/__init__.py` (~920 行)
> **本节为 v3.3 新增**

### 13.1 与 Holographic 的核心差异

| 维度 | Holographic HRR | Hindsight |
|------|-----------------|-----------|
| 检索范式 | 代数操作（bind/unbind/bundle） | 知识图谱 + 向量检索 |
| 实体处理 | 实体提取 + 链接（EntityExtraction） | **原生实体消歧**（bank_id + document_id 隔离） |
| 存储 | 纯本地 SQLite + numpy | 云端 API 或本地 embedded 引擎 |
| 工具 | 无专门 tool（prefetch only） | `retain`/`recall`/`reflect` 三个独立工具 |
| 推理能力 | `reason()` 代数推理 | `reflect()` LLM 跨记忆综合推理 |
| 依赖 | 无外部依赖 | hindsight-client SDK |

**根本区别**：Holographic 是**向量符号架构**（HRR），Hindsight 是**知识图谱 + LLM 推理**架构。

---

### 13.2 三工具设计（Retain / Recall / Reflect）

```python
# plugins/memory/hindsight/__init__.py:80-145
RETAIN_SCHEMA = {
    "name": "hindsight_retain",
    "description": "Store information to long-term memory. Hindsight automatically "
                   "extracts structured facts, resolves entities, and indexes for retrieval.",
    "parameters": {
        "properties": {
            "content": {"type": "string", "description": "The information to store."},
            "context": {"type": "string", "description": "Short label (e.g. 'user preference', 'project decision')."},
        },
        "required": ["content"],
    },
}

RECALL_SCHEMA = {
    "name": "hindsight_recall",
    "description": "Search long-term memory. Returns memories ranked by relevance using "
                   "semantic search, keyword matching, entity graph traversal, and reranking.",
}

REFLECT_SCHEMA = {
    "name": "hindsight_reflect",
    "description": "Synthesize a reasoned answer from long-term memories. Unlike recall, "
                   "this reasons across all stored memories to produce a coherent response.",
}
```

**Reflect vs Recall 是本质区别**：
- `recall` = 检索（similarity search）
- `reflect` = 推理（synthesize across all memories with LLM reasoning）

---

### 13.3 Turn Batching 写入策略

```python
# plugins/memory/hindsight/__init__.py:715-770
def sync_turn(self, user_content, assistant_content, *, session_id):
    """Batched retain — 避免每个 turn 都调用 API."""
    messages = [
        {"role": "user", "content": user_content, "timestamp": now},
        {"role": "assistant", "content": assistant_content, "timestamp": now},
    ]
    self._session_turns.append(json.dumps(messages))
    self._turn_counter += 1

    # 每 N 个 turn 才实际 retain（默认 1 = 每个 turn）
    if self._turn_counter % self._retain_every_n_turns != 0:
        return  # skip

    # 整个 session 作为单一 JSON 数组发送（document_id 做去重）
    content = "[" + ",".join(self._session_turns) + "]"
    client.aretain_batch(bank_id=self._bank_id, items=[{"content": content}], ...)
```

**配置参数**：
- `retain_every_n_turns`（默认 1）：每 N 轮 retain 一次
- `retain_async`（默认 True）：服务端异步处理
- `retain_context`：记忆上下文标签

---

### 13.4 专用 Event Loop（避免 aiohttp 泄漏）

```python
# plugins/memory/hindsight/__init__.py:63-80
_loop: asyncio.AbstractEventLoop | None = None
_loop_thread: threading.Thread | None = None

def _get_loop():
    """一个进程只有一个长寿命 event loop，复用而非创建临时 loop."""
    global _loop, _loop_thread
    with _loop_lock:
        if _loop is not None and _loop.is_running():
            return _loop
        _loop = asyncio.new_event_loop()
        def _run():
            asyncio.set_event_loop(_loop)
            _loop.run_forever()
        _loop_thread = threading.Thread(target=_run, daemon=True, name="hindsight-loop")
        _loop_thread.start()
        return _loop

def _run_sync(coro, timeout: float = 120.0):
    """Schedule coroutine on shared loop, block until done."""
    future = asyncio.run_coroutine_threadsafe(coro, _get_loop())
    return future.result(timeout=timeout)
```

**架构洞察**：这是 Hermes 唯一主动管理 event loop 的 provider（Holographic 和 Mem0 用简单 threading，Honcho 用自己的 loop）。Hindsight 这样做是因为其 SDK 使用 aiohttp，临时创建 loop 会泄漏 session。

---

### 13.5 Prefetch 机制（与 Holographic/Mem0 相同模式）

```python
# plugins/memory/hindsight/__init__.py:672-720
def queue_prefetch(self, query, *, session_id):
    def _run():
        client = self._get_client()
        if self._prefetch_method == "reflect":
            resp = _run_sync(client.areflect(bank_id=self._bank_id, query=query, budget=self._budget))
        else:
            resp = _run_sync(client.arecall(bank_id=self._bank_id, query=query, budget=self._budget, ...))
        text = "\n".join(f"- {r.text}" for r in resp.results if r.text)
        with self._prefetch_lock:
            self._prefetch_result = text

    self._prefetch_thread = threading.Thread(target=_run, daemon=True, name="hindsight-prefetch")
    self._prefetch_thread.start()
```

**可配置**：`_prefetch_method`（`reflect` vs `recall`）决定预取时用推理还是检索。

---

### 13.6 与 BlueCortexCE 对比

| 维度 | Hindsight | BlueCortexCE |
|------|------------|--------------|
| 存储架构 | 知识图谱 + 云端/本地 | PostgreSQL + pgvector |
| 实体消歧 | bank_id + document_id 多层隔离 | Observation 扁平存储 |
| Reflect 推理 | 跨记忆 LLM 综合推理 | 无（只有 similarity search） |
| Turn batching | `retain_every_n_turns` 控制 | 无 batching |
| SDK 依赖 | hindsight-client | 无外部依赖（自托管） |

**翻译：旁路型如何借鉴**：
- **高优先级**：`reflect` 思想值得借鉴——BlueCortexCE 的 `/api/context/generate` 可以是类似的"综合推理"能力（给定当前上下文，从记忆中推理出相关背景）
- **中优先级**：Turn batching 对高频写入场景有意义（当前每条 tool-use 都立即 ingest）
- **低优先级**：专用 event loop 管理对 BlueCortexCE（Java）不适用

---

## 14. AuxiliaryClient 完整路由链解析

> **文件**: `agent/auxiliary_client.py` (~2615 行)
> **本节为 v3.3 新增**，基于实际代码而非仅注释分析。

### 14.1 核心设计原则

**问题**：所有 auxiliary 消费者（context compression、session search、web extraction、vision analysis）都需要调用 LLM，如果各自实现 fallback 逻辑会造成大量重复代码。

**解决**：`_resolve_task_provider_model()` + `_get_cached_client()` 统一封装所有路由逻辑。

### 14.2 Auto-Detection 完整路由链（实际代码路径）

```python
# agent/auxiliary_client.py:1126-1200
def _resolve_auto(main_runtime=None):
    """
    实际执行顺序：
    1. 如果主 provider 不是 aggregator（非 OpenRouter/Nous），直接用主 provider
       → 目的：DeepSeek、ZAI、Alibaba 用户无需额外配置 OpenRouter
    2. aggregator 链：OpenRouter → Nous Portal → custom → Codex → API-key providers
    """
    runtime_provider = runtime.get("provider", "")
    main_provider = runtime_provider or _read_main_provider()

    # Step 1: 非聚合主 provider 优先
    if (main_provider and main_model
            and main_provider not in _AGGREGATOR_PROVIDERS
            and main_provider not in ("auto", "")):
        client, resolved = resolve_provider_client(main_provider, main_model, ...)
        if client is not None:
            return client, resolved  # 直接返回，无需走 fallback 链

    # Step 2: aggregator / fallback 链
    for label, try_fn in _get_provider_chain():
        client, model = try_fn()
        if client is not None:
            return client, model
        tried.append(label)
    return None, None
```

**关键洞察 1**：非 aggregator 主 provider 用户（DeepSeek、ZAI 等）**永远不会触发 OpenRouter fallback**——auxiliary tasks 直接用他们已有的 API key。这对 BlueCortexCE 有重要参考价值。

### 14.3 Provider Chain（`_get_provider_chain`）

```python
# agent/auxiliary_client.py:1126 附近
# Chain: OpenRouter → Nous → custom → Codex → API-key providers (zai/kimi/minimax...)
```

每个 `try_fn` 都返回 `(client, default_model)` 或 `(None, None)`。第一个返回非 None 的胜出。

### 14.4 Payment Exhaustion Retry（自动切换）

```python
# agent/auxiliary_client.py:2592-2620
try:
    return _validate_llm_response(
        await client.chat.completions.create(**kwargs), task)
except Exception as first_err:
    if "max_tokens" in str(first_err) or "unsupported_parameter" in str(first_err):
        # 重试用 max_completion_tokens
        kwargs.pop("max_tokens", None)
        kwargs["max_completion_tokens"] = max_tokens
        try:
            return _validate_llm_response(...)
        except Exception as retry_err:
            if not (_is_payment_error(retry_err) or _is_connection_error(retry_err)):
                raise

    # payment error → 尝试 fallback chain
    if _is_payment_error(first_err):
        client, model = _try_fallback_provider(client, resolved_provider)
        if client:
            return _validate_llm_response(
                await client.chat.completions.create(**kwargs), task)
```

**`_is_payment_error`** 检测 HTTP 402 或 credit exhaustion 相关错误。触发后自动切换到下一个可用 provider。

### 14.5 Per-Task Override 配置

```yaml
# config.yaml
auxiliary:
  session_search:
    provider: "openrouter"
    model: "google/gemini-3-flash"
    timeout: 60
  compression:
    provider: "auto"   # 使用默认链
    model: "gpt-4o-mini"
```

```python
# agent/auxiliary_client.py:2031
def _resolve_task_provider_model(task, provider, model, base_url, api_key):
    """从 config.yaml 的 auxiliary.{task}.* 读取 override。"""
    if task:
        task_config = _get_task_config(task)  # 读取 auxiliary.{task}.*
        # 优先级：显式参数 > task config > 环境变量 > auto
```

**架构设计**：Per-task override 允许不同 consumer 使用不同 provider（比如 session_search 用 Gemini Flash，compression 用 GPT-4o-mini）。

### 14.6 与 BlueCortexCE 对比

| 维度 | Hermes AuxiliaryClient | BlueCortexCE |
|------|----------------------|--------------|
| LLM 路由 | 统一封装，链式 fallback | 直接 OpenAI |
| 非聚合优先 | DeepSeek/ZAI 等直接用主 provider | 无 |
| Payment retry | 自动切换 fallback provider | 无 |
| Per-task override | config.yaml 配置 | 无 |
| 缓存 | `_get_cached_client` 缓存 | 无 |
| 专用 event loop | 无（每个 provider 自行管理） | N/A |

**翻译：旁路型如何借鉴**：
- **高优先级**：BlueCortexCE 的 LLM 调用（如 `/api/context/generate` 的 summarization）应实现 fallback 链——当主 provider 不可用时自动切换
- **中优先级**：引入 `payment error` 检测 + 自动切换，避免单个 provider 枯竭导致整个服务不可用
- **中优先级**：per-task model 选择（compression 用便宜模型，structured extraction 用好模型）

---

## 15. Honcho Dialectic — Peer Q&A + Observation 模式架构（v3.4 新增）

> **文件**: `plugins/memory/honcho/session.py:520-575`, `plugins/memory/honcho/client.py:105-145`, `plugins/memory/honcho/__init__.py:130-160,460-530`  
> **本节为 v3.4 新增**，分析 Honcho Provider 最独特的设计——**Peer Q&A（dialectic）** 机制

### 15.1 核心概念：Peer（对等体）建模

**Honcho 的核心抽象**：每个对话参与者（用户、AI）都是 **Peer**（对等体），每个 Peer 都有自己的：
- **representation** — Honcho 后端 LLM 生成的关于该 Peer 的综合描述
- **peer_card** — 关键事实的列表（可枚举）
- **conclusions** — 从对话中提取并写回的事实

这与 BlueCortexCE 的 Observation/Summary 模型**本质上不同**：
- BlueCortexCE：外部存储的记录（Observation），检索后由消费方（Claude Code）决定如何使用
- Honcho：Peer representation 是 Honcho 后端 **LLM 合成** 的产物，不暴露原始记录，由 LLM 直接生成综合回答

---

### 15.2 Dialectic Query 机制（Peer.chat）

**文件**: `plugins/memory/honcho/session.py:520-575`

**本质**：不是向量检索，而是 **Q&A 问答**——向 Honcho 后端提问关于某个 Peer 的问题，Honcho 的 LLM 在后端综合该 Peer 的所有记忆生成回答。

```python
# session.py:520-575
def dialectic_query(
    self, session_key: str, query: str,
    reasoning_level: str | None = None,
    peer: str = "user",
) -> str:
    """
    Query Honcho's dialectic endpoint about a peer.

    Runs an LLM on Honcho's backend against the target peer's full
    representation. Higher latency than context() — call async via
    prefetch_dialectic() to avoid blocking the response.
    """
    # Cross-observation routing: AI peer observes user
    if self._ai_observe_others:
        if peer == "ai":
            result = ai_peer_obj.chat(query, reasoning_level=level)
        else:
            result = ai_peer_obj.chat(
                query,
                target=session.user_peer_id,  # ask about the user
                reasoning_level=level,
            )
    else:
        # AI can't observe others — each peer queries self
        target_peer = self._get_or_create_peer(peer_id)
        result = target_peer.chat(query, reasoning_level=level)
```

**默认问题示例**（session init 预热）：
```python
# __init__.py:315
self._manager.prefetch_dialectic(self._session_key, "What should I know about this user?")
```

---

### 15.3 Reasoning Level 动态调节

**文件**: `session.py:487-518`

**Reasoning levels**: `"minimal" | "low" | "medium" | "high" | "max"`

**动态调节规则**（`dialectic_dynamic=true` 时）：
```
< 120 chars  → configured default（通常 "low"）
120-400 chars → +1 level above default
> 400 chars  → +2 levels above default（capped at "high"）
```

```python
# session.py:487-507
def _dynamic_reasoning_level(self, query: str) -> str:
    levels = self._REASONING_LEVELS  # ("minimal", "low", "medium", "high", "max")
    default_idx = levels.index(self._dialectic_reasoning_level)
    n = len(query)
    if n < 120:
        bump = 0
    elif n < 400:
        bump = 1
    else:
        bump = 2
    idx = min(default_idx + bump, 3)  # cap at "high" (index 3)
    return levels[idx]
```

**关键设计**：动态 bumping 让简单问题用便宜推理，复杂问题自动升级到更深度推理。**"max" 永远不会自动选择**——保留给显式配置。

---

### 15.4 Dialectic Cadence 控制

**文件**: `plugins/memory/honcho/__init__.py:142-145`

```python
self._dialectic_cadence = 1  # minimum turns between dialectic API calls
self._last_dialectic_turn = -999

# queue_prefetch() 中的 cadence 检查：
# __init__.py:491-495
if self._dialectic_cadence > 1:
    if (self._turn_count - self._last_dialectic_turn) < self._dialectic_cadence:
        logger.debug("Honcho dialectic prefetch skipped: cadence %d, turns since last: %d",
                     self._dialectic_cadence, self._turn_count - self._last_dialectic_turn)
        return
```

**配置**：`dialecticCadence`（默认 1 = 每轮都可触发）。设为 > 1 时降低 API 调用频率。

---

### 15.5 Observation 模式（谁观察谁）

**文件**: `plugins/memory/honcho/client.py:109-145`

**两个预设**：

| 模式 | User observes AI | AI observes User | 说明 |
|------|-----------------|-----------------|------|
| `directional` | ✅ | ✅ | 双向观察（默认） |
| `unified` | ✅ | ❌ | 隐私保护：AI 不能观察用户 |

```python
_OBSERVATION_PRESETS = {
    "directional": {
        "user_observe_me": True, "user_observe_others": True,
        "ai_observe_me": True, "ai_observe_others": True,
    },
    "unified": {
        "user_observe_me": True, "user_observe_others": False,
        "ai_observe_me": False, "ai_observe_others": True,
    },
}
```

**`unified` 模式的设计意图**：某些部署场景下，不希望 AI 持续"监控"用户，只让用户观察 AI。这对 BlueCortexCE 的隐私设计有参考价值。

---

### 15.6 Dialectic 完整注入流程

```
Session Init
    ↓
prefetch_dialectic("What should I know about this user?")  [后台线程，无阻塞]
    ↓                              ↓
Honcho 后端 LLM 综合      turn N queue_prefetch(...)
    ↓                              ↓
set_dialectic_result()          turn N+1 pop_dialectic_result()
    ↓                              ↓
注入 system prompt                  注入 <memory-context>
(dialectic_max_chars=600)         (dialectic_max_chars=600)
```

**注入上限**（`dialectic_max_chars`，默认 600 chars）：
```python
# session.py:572-573
if result and self._dialectic_max_chars and len(result) > self._dialectic_max_chars:
    result = result[:self._dialectic_max_chars].rsplit(" ", 1)[0] + " …"
```

---

### 15.7 Conclusion 写回机制（记忆沉淀）

**文件**: `session.py:970-1010`

Honcho 允许 AI peer 将观察到的结论**写回**用户 Peer 的 representation：

```python
def create_conclusion(self, session_key: str, content: str) -> bool:
    """Write a conclusion about the user back to Honcho.
    Conclusions are facts the AI peer observes about the user —
    preferences, corrections, clarifications, project context.
    """
    if self._ai_observe_others:
        # AI peer creates conclusion about user (cross-observation)
        conclusions_scope = assistant_peer.conclusions_of(session.user_peer_id)
    else:
        # AI can't observe others — user peer creates self-conclusion
        conclusions_scope = user_peer.conclusions_of(session.user_peer_id)
    conclusions_scope.create([{"content": content.strip(), "session_id": session.honcho_session_id}])
```

**设计价值**： Conclusions 是一种**有方向的记忆沉淀**——不是所有 Observation 平等记录，而是 AI 主动提炼后写回，成为 Peer representation 的一部分。后续 `dialectic_query("user preferences")` 时会用到。

---

### 15.8 与 Hindsight.reflect() 的本质对比

**Honcho Dialectic vs Hindsight Reflect**：

| 维度 | Honcho Dialectic (peer.chat) | Hindsight Reflect (areflect) |
|------|------------------------------|------------------------------|
| 机制 | 向 Peer 提问，综合 Peer representation | 跨记忆 LLM 综合推理 |
| 存储 | Peer representation（Honcho 后端管理） | 记忆本身（由 Hindsight 管理） |
| 输入 | 自然语言 query | 自然语言 query + budget |
| 粒度 | Peer 级别（用户/AI） | 记忆级别（all memories） |
| 观察模式 | directional/unified | bank_id 隔离 |
| 依赖 | Honcho 云端 SDK | Hindsight SDK |

**根本差异**：Honcho 是**对等体视角**（"关于这个用户我知道什么？"），Hindsight 是**记忆库视角**（"关于这个话题我记得什么？"）。Honcho 多了"谁在观察谁"的维度。

---

### 15.9 与 BlueCortexCE 对比

| 维度 | Honcho Dialectic | BlueCortexCE |
|------|-----------------|--------------|
| 核心抽象 | Peer（对等体） | Observation/Summary（记录） |
| 检索方式 | Q&A（LLM 合成回答） | 向量相似度检索 |
| 记忆沉淀 | Conclusion（AI 提炼写回） | Observation（原始提取） |
| LLM 推理 | Honcho 后端（云端） | 消费方决定 |
| Observation 模式 | directional/unified | 无对应 |
| Dialectic cadence | `dialecticCadence` | 无 |
| Dynamic reasoning | query 长度自动升级 | 无 |
| System prompt 注入 cap | 600 chars dialectic_max_chars | Observation 全量注入 |

### 15.10 翻译：旁路型如何借鉴

| 维度 | Hermes/Honcho 做法 | BlueCortexCE 现状 | 借鉴建议 | 优先级 |
|------|------------------|-------------------|----------|--------|
| Peer Q&A 模式 | `dialectic_query("What should I know about this user?")` → LLM 合成回答 | 无对应 | `/api/context/generate` 可以实现类似 Q&A 能力（给问题，返回综合背景） | **高** |
| Dynamic reasoning | query 短用 low，长用 high/max | 无对应 | LLM 调用时根据 query 复杂度选择模型 | 中 |
| Dialectic cadence | `dialecticCadence` 控制调用频率 | 无 | 限制 `/api/context/generate` 调用频率 | 中 |
| Observation 模式 | directional/unified 控制观察边界 | 无隐私模式 | 考虑增加用户级别的 observation 开关 | 低 |
| Conclusion 写回 | AI 提炼结论写回 Peer representation | 无主动提炼 | BlueCortexCE 可实现"高优先级 Observation 提炼" | 中 |
| System prompt 注入 cap | 600 chars dialectic_max_chars | Observation 全量注入 | 考虑对 `/api/context/generate` 结果加 chars cap | 低 |

**最高优先级借鉴**：BlueCortexCE 的 `/api/context/generate` 本质上就是 Dialectic Query 的自托管版本——给定当前上下文，从记忆中推理出相关背景。**当前实现是简单的向量检索，缺少 LLM 合成推理层**。这是 BlueCortexCE 与 Honcho 最大的能力差距，也是最有价值的改进方向。

---

