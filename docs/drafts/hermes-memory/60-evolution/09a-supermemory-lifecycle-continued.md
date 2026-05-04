## 69. Session Search Tool — FTS5 + 三阶段截断 + 亲缘链排除（v6.0 新增）

### 69.1 双模式设计（Zero-LLM-Cost Browse vs LLM Summarization）

**文件**：`tools/session_search_tool.py:258-276`

```python
def session_search(query, role_filter=None, limit=3, db=None, current_session_id=None):
    # 模式1：无 query → recent sessions browse（零 LLM 调用）
    if not query or not query.strip():
        return _list_recent_sessions(db, limit, current_session_id)
    # 模式2：有 query → FTS5 search + LLM summarization
    ...
```

**模式1（Browse）**：直接查 DB 返回 `session_id / title / source / started_at / last_active / message_count / preview`，**零 LLM 成本**，即时返回。

**模式2（Search）**：
1. FTS5 搜索 → 50 条原始匹配
2. 按 parent session 去重（compression/delegation 创建的子 session 合并到父 session）
3. 排除当前 session 亲缘链
4. 截断到 ~100k chars（围绕匹配位置优化）
5. Gemini Flash 并行 summarization（最多 5 sessions，每条最多 10000 tokens）
6. 返回 per-session summary + metadata

### 69.2 三阶段匹配定位截断算法（`_truncate_around_matches`，session_search_tool.py:59-145）

这是该工具最复杂也最有技术含量的部分：

```python
def _truncate_around_matches(full_text, query, max_chars=100_000):
    # 阶段1：完整短语匹配（case-insensitive）
    phrase_positions = [m.start() for m in re.finditer(re.escape(query_lower), text_lower)]

    # 阶段2：多 term  proximity co-occurrence（200-char 窗口内所有 term 都出现）
    if not phrase_positions:
        terms = query_lower.split()
        rarest = min(terms, key=lambda t: len(term_positions[t]))
        for pos in term_positions[rarest]:
            if all(any(abs(p-pos) < 200 for p in term_positions[t]) for t in terms if t != rarest):
                match_positions.append(pos)

    # 阶段3：单个 term 位置（最后兜底）
    if not match_positions:
        for t in terms:
            for m in re.finditer(re.escape(t), text_lower):
                match_positions.append(m.start())

    # 选择覆盖最多匹配位置的窗口（25% 前置偏差）
    best_start = 0
    best_count = 0
    for candidate in match_positions:
        ws = max(0, candidate - max_chars // 4)  # 25% before
        we = ws + max_chars
        count = sum(1 for p in match_positions if ws <= p < we)
        if count > best_count:
            best_count = count
            best_start = ws
```

**关键设计**：
- **Phrase > Proximity > Individual Term** 三层降级
- Proximity 窗口 200 chars（经验值）
- 选择"覆盖最多匹配"的窗口，而非简单的前 N 字符
- 25% 前置偏差（匹配前的 context 更有信息量）

### 69.3 亲缘链排除机制（`_resolve_to_parent`，session_search_tool.py:298-322）

Compression 和 delegation 会产生子 session，但用户的对话主体在父 session 中：

```python
def _resolve_to_parent(session_id):
    visited = set()
    sid = session_id
    while sid and sid not in visited:
        visited.add(sid)
        session = db.get_session(sid)
        parent = session.get("parent_session_id")
        if parent:
            sid = parent
        else:
            break
    return sid
```

- **上行遍历**：一直找到根 parent session
- 排除当前 session 的**整个亲缘链**（不仅仅是当前 session 本身）
- `seen_sessions[resolved_sid]` 保证每个根 session 只出现一次

### 69.4 Fallback：当 LLM Summarizer 不可用时

```python
# session_search_tool.py:463-468
if result:
    entry["summary"] = result
else:
    # Fixes #3409: 不要静默丢弃匹配到的 session
    preview = (conversation_text[:500] + "\n…[truncated]")
    entry["summary"] = f"[Raw preview — summarization unavailable]\n{preview}"
```

**这是文档 v5.3 提到的 Anti-thrashing 模式在 session_search 中的具体体现**：
- 如果 summarizer 完全失败，返回 raw preview 而非空结果
- 避免"匹配到了但结果丢失"的无声失败

### 69.5 翻译：旁路型如何借鉴

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 旁路型借鉴 |
|------|-----------|-----------------|----------|
| Zero-LLM-Cost Browse | 无 query → 直接 DB 返回 session 列表 | `/api/memory/sessions` 返回完整记录列表 | 可在 SearchService 中添加"recent sessions" 快速路径（不需要 embedding/search） |
| 截断算法 | phrase → proximity → individual term | 无智能截断（简单 limit） | 可借鉴三层降级 + 窗口优化策略 |
| 亲缘链排除 | delegation/compression 子 session 合并到父 | 无 delegation 概念 | Session 合并对于多轮对话记忆完整性有价值 |
| 静默失败防止 | summarizer 失败 → raw preview | SearchService 失败时直接返回空 | 可借鉴"降级返回 raw content" 而非静默失败 |

**优先级**：高（截断算法和静默失败防止对旁路型有直接参考价值）

---

