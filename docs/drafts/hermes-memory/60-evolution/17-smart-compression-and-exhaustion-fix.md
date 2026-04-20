# 上游 Smart Compression + Exhaustion Loop Fix（2026-04-14）

> **上游 commit**：`9855190f`（smart compression）· `c5688e7c`（exhaustion loop）  
> **本地路径**：`agent/context_compressor.py` · `run_agent.py` · `gateway/run.py`  
> **复核日期**：2026-04-19

---

## TL;DR

| 特性 | upstream commit | CE 借鉴价值 |
|------|----------------|-------------|
| **Smart tool collapse** | `9855190f` | 高 — 信息保留优于占位符 |
| **Dedup pass (MD5)** | `9855190f` | 高 — 避免重复读取同一文件 |
| **Anti-thrashing** | `9855190f` | 高 — 防止无限小步压缩 |
| **Exhaustion infinite loop fix** | `c5688e7c` | 高 — 必须配套 session reset |
| **`/compress <topic>` fallback** | `9855190f` | 中 — 手动压缩入口 |

---

## 1. Smart Tool Output Collapse（`_summarize_tool_result`）

### 核心思想

旧版用通用占位符替换工具输出（如 `[Tool output omitted]`），完全不保留工具语义。新版对 20+ 种工具实现**工具专用 1 行摘要**，格式：

```
[terminal] ran `npm test` -> exit 0, 47 lines output
[read_file] read config.py from line 1 (1,200 chars)
[search_files] content search for 'compress' in agent/ -> 12 matches
[patch] replace in src/main.py (340 chars result)
```

### 支持的工具清单

| 工具 | 摘要格式 |
|------|---------|
| `terminal` | `ran \`cmd\` -> exit N, N lines output` |
| `read_file` | `read path from line N (N chars)` |
| `write_file` | `wrote to path (N lines)` |
| `search_files` | `{target} search for 'pattern' in {path} -> N matches` |
| `patch` | `{mode} in {path} (N chars result)` |
| `browser_*` | `[{tool}] {url} ({N chars})` |
| `web_search` | `query='...' ({N chars result})` |
| `web_extract` | `{url} (+N more) ({N chars})` |
| `delegate_task` | `'{goal}' ({N chars result})` |
| `execute_code` | `` `code_preview` (N lines output)`` |
| `memory` | `{action} on {target}` |
| 通用 fallback | `[{tool}] key=val... ({N chars result})` |

### CE 借鉴

Cortex CE 在压缩时也应实现工具感知摘要：
- `EmbeddingService` / `MemoryRefineService` 可复用相同思路
- 摘要模板应区分工具类型，避免丢失"读了什么文件"等关键上下文

---

## 2. Deduplication Pass（MD5 Hash）

### 实现

在 prune pass 之前，先对 `tool` role 消息做 MD5 去重：

```python
content_hashes: dict = {}  # hash -> (index, tool_call_id)
for i in range(len(result) - 1, -1, -1):
    content = msg.get("content") or ""
    if isinstance(content, list):
        continue  # 跳过 multimodal
    if len(content) < 200:
        continue  # 小内容不处理
    h = hashlib.md5(content.encode("utf-8", errors="replace")).hexdigest()[:12]
    if h in content_hashes:
        result[i] = {**msg, "content": "[Duplicate tool output — same content as a more recent call]"}
        pruned += 1
    else:
        content_hashes[h] = (i, msg.get("tool_call_id", "?"))
```

**关键点**：
- 只对 ≥200 字符的内容做 dedup（小内容不值得）
- 跳过 multimodal content（list of content blocks）
- 从后向前遍历，保留最近的完整副本

### CE 借鉴

CE 的 `ContextService.compact()` 或 `MemoryRefineService` 可以借鉴：
- 同一次会话中反复读取同一文件，只保留最后一次完整内容
- 节省 embedding token 预算

---

## 3. Anti-Thrashing（无效压缩跳过）

### 问题

当压缩每次只节省 <10% token 时，会进入"小步快跑"陷阱：每次压缩移除 1-2 条消息 → token 仍超阈值 → 再次压缩 → 无限循环。

### 实现

```python
# ContextCompressor.__init__ 和 reset()
self._last_compression_savings_pct = 100.0
self._ineffective_compression_count = 0

# should_compress() 新增检查
if self._ineffective_compression_count >= 2:
    logger.warning(
        "Compression skipped — last %d compressions saved <10%% each. "
        "Consider /new to start a fresh session, or /compress <topic> "
        "for focused compression.",
        self._ineffective_compression_count,
    )
    return False

# 压缩完成后记录效果
savings_pct = (saved_estimate / display_tokens * 100) if display_tokens > 0 else 0
self._last_compression_savings_pct = savings_pct
if savings_pct < 10:
    self._ineffective_compression_count += 1
else:
    self._ineffective_compression_count = 0
```

### 阈值

- 连续 **2 次**压缩节省率 <10% → 跳过下次自动压缩
- 提示用户使用 `/new`（开新会话）或 `/compress <topic>`（主题聚焦压缩）

### CE 借鉴

CE 的 `MemoryRefineService` 应实现相同保护：
- 连续 N 次 refine 收益 < 阈值 → 跳过 refine，提示用户
- 需要在 `MemoryRefineService` 中追踪 `_last_refine_savings_pct`

---

## 4. Compression Exhaustion Infinite Loop Fix（`c5688e7c`）

### 问题

压缩在达到 `max_compression_attempts` 后仍然返回 `completed: False`，但 `run_agent.py` 没有设置 `failed: True`，导致：

1. Gateway 的 `agent_failed_early` guard 检查 `failed AND not final_response`
2. 但 `_run_agent_blocking` 总是把错误转换成 `final_response`
3. 结果 guard 永远不触发 → oversized session 持久化 → 无限 fail loop

### 修复内容

#### run_agent.py（5 处压缩耗尽返回路径）

所有 `compression_exhausted` 路径现在都添加：
```python
return {
    "completed": False,
    "partial": True,
    "failed": True,                    # ← 新增
    "compression_exhausted": True,     # ← 新增
    ...
}
```

#### gateway/run.py

```python
# _handle_message_with_agent() 中的新逻辑
agent_failed_early = bool(agent_result.get("failed"))
if agent_failed_early:
    # ... 处理失败

# compression_exhausted 时自动 reset session
if agent_result.get("compression_exhausted") and session_entry and session_key:
    await self._auto_reset_session_on_exhaustion(session_key, session_entry)
```

### CE 借鉴

CE 的 `/api/context/generate` 和 `/api/session/start` 也应实现类似逻辑：
- 压缩/上下文生成在 N 次重试后仍失败 → 返回 `failed: true`
- Gateway（或调用方）在收到 `compression_exhausted: true` 时，清空 session 上下文，重新开始
- **这是 CE 当前缺失的关键保护**

---

## 5. 其他改进

### max_tokens 硬化（1.3x cap）

```python
# 旧
"max_tokens": summary_budget * 2,

# 新
"max_tokens": int(summary_budget * 1.3),
```

### 摘要模板升级

**旧**：
```
## Progress
### Done
[Completed work]
### In Progress
[Work currently underway]
### Blocked
[Any blockers]
```

**新**：
```
## Completed Actions
[Numbered list — N. ACTION target — outcome [tool: name]]
Format each as: N. ACTION target — outcome [tool: name]
Example:
1. READ config.py:45 — found `==` should be `!=` [tool: read_file]
2. PATCH config.py:45 — changed `==` to `!=` [tool: patch]

## Active State
[Current working state — include modified files, test status, etc.]

## In Progress
[Work currently underway]

## Blocked
[Any blockers or errors not yet resolved]
```

关键改进：
- **Numbered actions** 强制具体化（避免"made some changes"）
- **Active State** 替代 In Progress，反映即时状态
- **Completed Actions** 替代 Done，强调动作而非状态

### `/compress <topic>` 手动压缩

用户可指定主题进行聚焦压缩，作为 anti-thrashing 后的逃生舱。

---

## 6. 与现有 CE 文档的关联

| 本节 | 对应已有 CE 文档 |
|------|----------------|
| §1-3（smart compress） | `40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md` §压缩部分 |
| §4（exhaustion fix） | `20-recommendations/04-ce-injection-and-context-api-surface.md` §4（Context 出口与 session reset） |
| §4（exhaustion fix） | `20-recommendations/05-ce-context-security-gap-inventory.md` 缺口表 |

---

## 7. 可执行行动（CE）

- [ ] **Anti-thrashing for MemoryRefineService**：在 `MemoryRefineService` 添加 `refine_savings_threshold` 检查，连续 N 次收益 < 阈值时跳过 refine 并 warn
- [ ] **Compression exhaustion → session reset**：在 `ContextService.compact()` 或 `ContextController` 中，当压缩耗尽时返回 `failed: true`；调用方在收到 `compression_exhausted` 时清空 session 上下文
- [ ] **Smart tool collapse**：在 CE 的摘要提示词中引入工具类型感知摘要策略（参考 `_summarize_tool_result` 工具清单）
- [ ] **Dedup pass**：在 `compact()` 中对重复 tool results 做 MD5 去重
