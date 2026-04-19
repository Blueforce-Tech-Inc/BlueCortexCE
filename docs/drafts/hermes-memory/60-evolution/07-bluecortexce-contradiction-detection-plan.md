
## 45. BlueCortexCE 矛盾检测工程方案 — Entity Extraction + Similarity Scoring（v4.5 新增）

> **本节为 v4.5 新增**，基于 Holographic `contradict()` 的算法分析，提出 BlueCortexCE 可落地的工程实现方案。

### 45.1 设计目标

在 BlueCortexCE 中实现 `GET /api/memory/contradictions` 端点，返回 Observation 库中的矛盾对。

**矛盾定义**（参考 Holographic）：
> 两个 Observation 矛盾 = **共享实体**（相同主体）+ **内容语义相异**（一个说 A，一个说非 A）

### 45.2 实体提取方案

**方案 A：LLM 提取（推荐，高准确率）**

在 Observation 生成时（`SummaryGenerationService` 或 `AgentService`），要求 LLM 额外输出 `entities: ["entity1", "entity2"]`：

```xml
<observed_from_primary_session>
  <what_happened>{{toolName}}</what_happened>
  <entities>["entity1", "entity2"]</entities>   ← 新增
  <outcome>{{toolOutput}}</outcome>
</observed_from_primary_session>
```

**方案 B：正则提取（低成本，准确率有限）**

```python
def extract_entities_regex(text: str) -> list[str]:
    patterns = [
        r'\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b',  # 大写多词短语
        r'"([^"]+)"',                                # 双引号内容
        r"'([^']+)'",                                # 单引号内容
        r'(\w+(?:\s+\w+)*)\s+(?:aka|also known as)\s+(\w+(?:\s+\w+)*)',
    ]
    # 去重 + 大小写不敏感
```

### 45.3 矛盾检测算法

```sql
-- 伪 SQL：基于 PostgreSQL + pgvector
WITH entity_overlaps AS (
    SELECT
        o1.id AS obs_id_1,
        o2.id AS obs_id_2,
        -- 计算实体重叠度
        (COUNT(o1.entity) / (COUNT(DISTINCT o1.entity) + COUNT(DISTINCT o2.entity) - COUNT(o1.entity))) AS entity_overlap,
        o1.content AS content_1,
        o2.content AS content_2,
        o1.embedding <=> o2.embedding AS content_similarity  -- cosine distance
    FROM observations o1
    JOIN observations o2 ON o1.id < o2.id
    JOIN observation_entities oe1 ON oe1.observation_id = o1.id
    JOIN observation_entities oe2 ON oe2.observation_id = o2.id
    WHERE oe1.entity = oe2.entity  -- 共享实体
    GROUP BY o1.id, o2.id, o1.content, o2.content
),
contradictions AS (
    SELECT
        *,
        entity_overlap * (1 - (content_similarity + 1) / 2) AS contradiction_score
    FROM entity_overlaps
    WHERE entity_overlap >= 0.3
)
SELECT *
FROM contradictions
WHERE contradiction_score >= 0.3
ORDER BY contradiction_score DESC
LIMIT 20;
```

### 45.4 实体重叠度计算

```python
def jaccard_overlap(ents1: set[str], ents2: set[str]) -> float:
    if not ents1 or not ents2:
        return 0.0
    intersection = len(ents1 & ents2)
    union = len(ents1 | ents2)
    return intersection / union if union > 0 else 0.0
```

### 45.5 矛盾分数阈值

| 阈值 | 敏感度 | 适用场景 |
|------|--------|----------|
| 0.2 | 高（更多结果，含误报） | 高频矛盾检测 |
| 0.3 | 中（默认） | 日常使用 |
| 0.5 | 低（极少结果，高精度） | 精确分析 |

### 45.6 容量保护

参考 Holographic 的 500 条上限：

```sql
-- 只比较最近 500 条 Observation
WITH recent_obs AS (
    SELECT id, content, embedding
    FROM observations
    ORDER BY created_at DESC
    LIMIT 500
)
-- 在 recent_obs 上做两两比较
```

**注意**：500 observations → 最多 124,750 对比较。如果有 N 个实体，平均每对有 K 个实体，时间复杂度 O(N² × K²)。

### 45.7 API 设计

```
GET /api/memory/contradictions?threshold=0.3&limit=20

Response:
{
  "contradictions": [
    {
      "observation_a": {...},
      "observation_b": {...},
      "shared_entities": ["用户", "项目A"],
      "entity_overlap": 0.45,
      "content_similarity": -0.62,  // pgvector cosine similarity
      "contradiction_score": 0.41,
    }
  ],
  "total_compared": 500,
  "contradiction_count": 12,
  "threshold": 0.3
}
```

### 45.8 触发时机

| 方式 | 说明 | 优先级 |
|------|------|--------|
| 用户主动查询 | `GET /api/memory/contradictions` | **高** |
| SessionEnd 时自动检查 | 每次 session 结束时检查（异步） | 中 |
| Observation 写入时检查 | 新 observation 与已有 observation 高重叠时检查 | 低 |

---

## 46. 待进一步确认（v4.5 更新）

### 46.1 本轮已确认项目

1. ✅ ~~Holographic memory_banks usage~~ — **已验证**：在 `reason()` 方法中用于代数检索优化（`retrieval.py:143`），不是悬空数据
2. ✅ ~~Holographic related()~~ — **已验证**：`related()` 使用裸原子直接相似度，与 `probe()` 的 role binding 形成互补（`retrieval.py:220`）
3. ✅ ~~Holographic memory_banks rebuild triggers~~ — **已验证**：add_fact/add_alias/set_trust/rebuild_all 四个触发点（`store.py:183,294,316,533`）
4. ✅ ~~BlueCortexCE summary template~~ — **已验证**：5-field（request/investigated/learned/completed/next_steps/notes）vs Hermes 11-field
5. ✅ ~~Supermemory `_detect_category`~~ — **已验证**：4 类纯正则分类（preference/decision/fact/other，`supermemory/__init__.py:158`）
6. ✅ ~~SessionSearch LLM fallback~~ — **已验证**：MAX_SUMMARY_CHARS=2000，输入 >4000 chars 先截断

### 46.2 仍待确认项目

1. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
2. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议
3. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
4. **BlueCortexCE Observation Entity Extraction** — 是否已在 LLM prompt 中实现 entities 字段提取？


---

## 47. session_search_tool — 双模式设计 + 主动触发机制（v4.6 新增）

> **文件**: `tools/session_search_tool.py:300-410`
> **本节为 v4.6 新增**，分析 session_search 工具的双模式设计（recent vs search）、会话过滤机制、以及工具 schema 中的主动触发指导。

### 47.1 双模式设计：Recent（零成本）vs Search（LLM 合成）

**最关键的成本优化设计**：session_search 工具根据 query 参数自动切换模式：

```python
# tools/session_search_tool.py:300-310
def session_search(query: str, role_filter: str = None, limit: int = 3, ...):
    # Recent sessions mode: when query is empty, return metadata for recent sessions.
    # No LLM calls — just DB queries for titles, previews, timestamps.
    if not query or not query.strip():
        return _list_recent_sessions(db, limit, current_session_id)

    query = query.strip()
    # ... search mode with LLM summarization
```

| 模式 | 触发条件 | LLM 调用 | 延迟 | 用途 |
|------|----------|----------|------|------|
| **Recent** | `query=""` 或无 query | **零** | 极低 | "最近做了什么？" |
| **Search** | `query="keyword"` | **有**（Gemini Flash） | 高 | "上次关于 X 的讨论" |

**Recent 模式返回值**（`_list_recent_sessions`）：

```python
results.append({
    "session_id": sid,
    "title": s.get("title") or None,
    "source": s.get("source", ""),
    "started_at": s.get("started_at", ""),
    "last_active": s.get("last_active", ""),
    "message_count": s.get("message_count", 0),
    "preview": s.get("preview", ""),  # 首条消息预览
})
# 返回示例: "Showing 3 most recent sessions. Use a keyword query to search specific topics."
```

**关键洞察**：
- Recent 模式**不需要 LLM** — 只做 DB 查询（session metadata + preview text）
- 模型在 `session_search()` 无参数时自动触发 Recent 模式
- Schema 明确指导：**"Start here when the user asks what were we working on or what did we do recently"**

### 47.2 会话来源过滤：隐藏第三方 Agent 会话

```python
# tools/session_search_tool.py:244-248
# Sources that are excluded from session browsing/searching by default.
# Third-party integrations (Paperclip agents, etc.) tag their sessions with
# HERMES_SESSION_SOURCE=tool so they don't clutter the user's session history.
_HIDDEN_SESSION_SOURCES = ("tool",)
```

**过滤逻辑**：
- `db.list_sessions_rich(exclude_sources=["tool"])` — 排除所有 source="tool" 的会话
- `db.search_messages(exclude_sources=["tool"])` — 搜索时同样排除
- 目的：防止"回形针 Agent"等第三方集成的会话污染用户的历史记录

**设计背景**：Paperclip agents（轻量级自动化 Agent）会创建大量 session，如果不对其过滤，用户浏览历史时会被干扰。

### 47.3 当前会话链排除：防止返回当前对话

```python
# tools/session_search_tool.py:320-335
# Resolve current session lineage to exclude it
current_root = None
if current_session_id:
    sid = current_session_id
    visited = set()
    while sid and sid not in visited:
        visited.add(sid)
        s = db.get_session(sid)
        parent = s.get("parent_session_id") if s else None
        sid = parent if parent else None
    current_root = max(visited, key=len) if visited else current_session_id

# 排除：
# 1. 当前 session 本身
# 2. 当前 session 的所有祖先（parent_session_id chain）
# 3. 所有 delegation 子会话（parent_session_id 非空）
```

**排除范围**：
1. **当前 session ID** — `sid == current_session_id`
2. **当前 session 的根祖先** — `sid == current_root`（整条 delegation chain）
3. **所有 delegation 子会话** — `s.get("parent_session_id")` 非空

**目的**：避免返回"当前正在进行的对话"，因为 Agent 已经有了完整的当前上下文。

### 47.4 Role Filter：过滤特定角色的消息

```python
# tools/session_search_tool.py:312-320
# Parse role filter
role_list = None
if role_filter and role_filter.strip():
    role_list = [r.strip() for r in role_filter.split(",") if r.strip()]

# FTS5 search -- get matches ranked by relevance
raw_results = db.search_messages(
    query=query,
    role_filter=role_list,
    exclude_sources=list(_HIDDEN_SESSION_SOURCES),
    limit=50,
    offset=0,
)
```

**用途**：可以只搜索 user + assistant 消息，跳过 tool outputs，减少噪音。

**Schema 描述**：`"role_filter": "Optional: only search messages from specific roles (comma-separated). E.g. 'user,assistant' to skip tool outputs."`

### 47.5 主动触发指导（Schema 中的 WHEN 指导）

```python
# tools/session_search_tool.py:492-510
SESSION_SEARCH_SCHEMA = {
    "description": (
        "Search your long-term memory of past conversations, or browse recent sessions. ...\n\n"
        "USE THIS PROACTIVELY when:\n"
        "- The user says 'we did this before', 'remember when', 'last time', 'as I mentioned'\n"
        "- The user asks about a topic you worked on before but don't have in current context\n"
        "- The user references a project, person, or concept that seems familiar but isn't in memory\n"
        "- You want to check if you've solved a similar problem before\n"
        "- The user asks 'what did we do about X?' or 'how did we fix Y?'\n\n"
        "Don't hesitate to search when it is actually cross-session -- it's fast and cheap. "
        "Better to search and confirm than to guess or ask the user to repeat themselves.\n\n"
        "Search syntax: keywords joined with OR for broad recall (elevenlabs OR baseten OR funding), "
        ...
    ),
}
```

**核心思想**：
- **主动触发**：不要等用户明确要求搜索，模型应该根据上下文主动判断是否需要 cross-session recall
- **消除顾虑**："it's fast and cheap" — 鼓励模型放心使用
- **FTS5 语法指导**：OR vs AND、phrase、boolean、prefix

### 47.6 与 BlueCortexCE 对比

| 维度 | Hermes session_search | BlueCortexCE |
|------|---------------------|--------------|
| Recent 模式 | ✅ 零 LLM 成本 | ❌ `/api/memory/sessions` 需要 LLM 生成 session title |
| Search 模式 | FTS5 + Gemini Flash | `/api/memory/search` + LLM synthesis |
| 第三方过滤 | `_HIDDEN_SESSION_SOURCES=("tool",)` | ❌ 无 |
| 当前会话链排除 | ✅ `_resolve_to_parent` + lineage root | ❌ 无（返回所有 session） |
| Role filter | ✅ 跳过 tool outputs | ❌ 无 |
| 主动触发指导 | Schema 明确指导 5 种场景 | ❌ 无 |

### 47.7 翻译：旁路型如何借鉴

| 优先级 | 借鉴点 | 说明 |
|--------|--------|------|
| **高** | BlueCortexCE 增加 `/api/memory/sessions/recent` | 返回最近 session 的 metadata（title + preview + timestamp），零 LLM 成本 |
| **高** | BlueCortexCE 增加第三方 session 过滤 | 消费方可以标记哪些 session 是"第三方工具"，搜索时过滤 |
| **高** | BlueCortexCE 实现当前 session 链排除 | 搜索结果排除当前 session 及其 delegation 子 session |
| **中** | BlueCortexCE 增加 role filter | API 支持 `?role=user,assistant` 过滤 tool outputs |
| **中** | SDK 层增加主动触发指导 | JS/Go/Python SDK 文档中明确指导何时调用 session recall |

---

## 48. memory_tool — 完整操作语义 + Schema 指导（v4.6 新增）

> **文件**: `tools/memory_tool.py:200-400`
> **本节为 v4.6 新增**，分析 memory 工具的精确操作语义（add/replace/remove）、歧义处理、以及 Schema 中的优先级指导。

### 48.1 add/replace/remove 精确语义

**add — 追加新 entry**：

```python
# tools/memory_tool.py:218-250
def add(self, target: str, content: str) -> Dict[str, Any]:
    # 1. 扫描 injection/exfiltration
    scan_error = _scan_memory_content(content)
    if scan_error:
        return {"success": False, "error": scan_error}

    # 2. Re-read from disk under lock（处理多进程并发）
    self._reload_target(target)

    entries = self._entries_for(target)
    limit = self._char_limit(target)

    # 3. 拒绝 exact duplicate
    if content in entries:
        return self._success_response(target, "Entry already exists (no duplicate added).")

    # 4. 检查 char limit
    new_entries = entries + [content]
    new_total = len(ENTRY_DELIMITER.join(new_entries))
    if new_total > limit:
        current = self._char_count(target)
        return {
            "success": False,
            "error": f"Memory at {current:,}/{limit:,} chars. "
                     f"Adding this entry ({len(content)} chars) would exceed the limit. "
                     f"Replace or remove existing entries first.",
            "current_entries": entries,
            "usage": f"{current:,}/{limit:,}",
        }

    entries.append(content)
    self._set_entries(target, entries)
    self.save_to_disk(target)
```

**replace — 精确 substring 匹配**：

```python
# tools/memory_tool.py:252-300
def replace(self, target: str, old_text: str, new_content: str) -> Dict[str, Any]:
    # 1. 扫描 new_content
    scan_error = _scan_memory_content(new_content)
    if scan_error:
        return {"success": False, "error": scan_error}

    self._reload_target(target)
    entries = self._entries_for(target)

    # 2. 找所有包含 old_text 的 entry
    matches = [(i, e) for i, e in enumerate(entries) if old_text in e]

    if not matches:
        return {"success": False, "error": f"No entry matched '{old_text}'."}

    # 3. 多 match 歧义处理
    if len(matches) > 1:
        unique_texts = set(e for _, e in matches)
        if len(unique_texts) > 1:
            # 多个不同 entry 都包含 old_text → 要求更具体
            previews = [e[:80] + "..." if len(e) > 80 else e for _, e in matches]
            return {
                "success": False,
                "error": f"Multiple entries matched '{old_text}'. Be more specific.",
                "matches": previews,
            }
        # 全部相同 → 只替换第一个（去重后的 safe case）

    # 4. 检查替换后是否超 limit
    test_entries = entries.copy()
    test_entries[idx] = new_content
    new_total = len(ENTRY_DELIMITER.join(test_entries))
    if new_total > limit:
        return {"success": False, "error": f"Replacement would put memory at {new_total:,}/{limit:,} chars."}

    entries[idx] = new_content
    self._set_entries(target, entries)
    self.save_to_disk(target)
```

**remove — 同 replace 的歧义处理**：

```python
# tools/memory_tool.py:302-340
def remove(self, target: str, old_text: str) -> Dict[str, Any]:
    # 完全相同的歧义处理逻辑
    matches = [(i, e) for i, e in enumerate(entries) if old_text in e]
    if not matches:
        return {"success": False, "error": f"No entry matched '{old_text}'."}
    if len(matches) > 1:
        unique_texts = set(e for _, e in matches)
        if len(unique_texts) > 1:
            # 要求更具体
            return {"success": False, "error": "Multiple entries matched..."}
        # 全部相同 → 只删除第一个
```

### 48.2 歧义处理的关键设计

**问题**：如果用户说"remember X"，但 memory 中有多个 entry 都包含 X，replace/remove 应该用哪个？

**Hermes 的处理**：
1. 如果多个 entry 的**文本完全相同**（exact duplicate）→ 操作第一个（合理）
2. 如果多个 entry 的**文本不同**（不同 entry 都恰好包含 old_text substring）→ 返回错误，要求用户更具体

**设计意图**：防止误操作。用户需要提供足够长的 `old_text` 来唯一确定目标 entry。

### 48.3 Schema 中的优先级指导

```python
# tools/memory_tool.py:502-530
MEMORY_SCHEMA = {
    "description": (
        "WHEN TO SAVE (do this proactively, don't wait to be asked):\n"
        "- User corrects you or says 'remember this' / 'don't do that again'\n"
        "- User shares a preference, habit, or personal detail (name, role, timezone, coding style)\n"
        "- You discover something about the environment (OS, installed tools, project structure)\n"
        "- You learn a convention, API quirk, or workflow specific to this user's setup\n"
        "- You identify a stable fact that will be useful again in future sessions\n\n"
        "PRIORITY: User preferences and corrections > environment facts > procedural knowledge. "
        "The most valuable memory prevents the user from having to repeat themselves.\n\n"
        "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO "
        "state to memory; use session_search to recall those from past transcripts.\n"
        ...
    ),
}
```

**三层优先级**：
1. **最高**：User preferences and corrections（用户偏好和纠正）
2. **中等**：Environment facts（环境事实）
3. **最低**：Procedural knowledge（流程性知识）

**明确排除**：
- Task progress（任务进度）→ 用 session_search 召回
- Session outcomes（会话结果）→ 用 session_search 召回
- Completed-work logs → 用 session_search 召回
- Temporary TODO state → 不要写入 memory

**反面指导的价值**：告诉模型什么**不应该**记住，比告诉它什么应该记住更重要。

### 48.4 与 BlueCortexCE 对比

| 维度 | Hermes memory_tool | BlueCortexCE |
|------|------------------|--------------|
| 操作接口 | add/replace/remove（substring 匹配） | Observation 写入（append-only） |
| 歧义处理 | 多 match → 要求更具体 | ❌ 无（append-only 不会有歧义） |
| 精确性要求 | old_text 必须唯一匹配 | N/A |
| Character limit | 硬限制（超限拒绝写入） | Observation 无硬 limit |
| 优先级指导 | 偏好 > 环境 > 流程 | ❌ 无 |
| 反面指导 | 明确排除 task progress / session outcomes | ❌ 无 |
| Injection 扫描 | ✅ `_scan_memory_content` | ❌ 无 |

### 48.5 翻译：旁路型如何借鉴

| 优先级 | 借鉴点 | 说明 |
|--------|--------|------|
| **高** | BlueCortexCE 增加优先级/分类字段 | Observation 增加 `category: preference/environment/fact/procedure` |
| **高** | BlueCortexCE 增加反面指导 | API 文档明确说明什么**不应该**写入（task progress、raw outputs） |
| **中** | BlueCortexCE 增加 injection 扫描 | 对所有写入内容做威胁模式扫描 |
| **低** | BlueCortexCE Observation append-only vs 可修改 | 当前 append-only 是正确设计（避免歧义） |
| **低** | BlueCortexCE 增加 char limit | 可以对 summary/observation 设置合理的 soft limit |

---

## 50. Tool Result Pre-pass — ContextCompressor Phase 1 算法（v4.7 新增）

> **文件**: `agent/context_compressor.py:63-180`
> **本节为 v4.7 新增**，分析压缩算法 Phase 1 — 在调用 LLM summarizer **之前**，用规则型方法对旧 tool outputs 做预处理。

### 50.1 设计动机

压缩对话时，传统的"直接丢弃旧 tool results"会导致**信息真空**——模型只知道"有个工具被调用了"，但不知道它做了什么。另一个极端是保留完整输出，但这对长 context 不可接受。

Hermes 的解法：**两阶段压缩**：
1. **Phase 1（规则型，零 LLM 成本）**：用正则提取工具名、参数、关键结果，生成 1 行信息性摘要
2. **Phase 2（LLM summarizer）**：对剩余内容做语义压缩

### 50.2 `_summarize_tool_result` 实现

```python
# agent/context_compressor.py:63-180
def _summarize_tool_result(tool_name: str, tool_args: str, tool_content: str) -> str:
    """用 1 行描述工具调用的关键信息，而非通用 placeholder。"""
```

**核心原则**：不是返回 `"[Old tool output cleared]"` 这种零信息占位符，而是保留**可区分性**。

**工具特定格式化规则**：

| 工具 | 格式 |
|------|------|
| `terminal` | `[terminal] ran \`{cmd}\` -> exit {code}, {n} lines output` |
| `read_file` | `[read_file] read {path} from line {offset} ({n} chars)` |
| `write_file` | `[write_file] wrote to {path} ({n} lines)` |
| `search_files` | `[search_files] {target} search for '{pattern}' in {path} -> {n} matches` |
| `patch` | `[patch] {mode} in {path} ({n} chars result)` |
| `browser_*` | `[{tool_name}]{url or ref} ({n} chars)` |

**实现细节**：
```python
# agent/context_compressor.py:94-97
content_len = len(content)
line_count = content.count("\n") + 1 if content.strip() else 0

# terminal: 从 JSON 输出中提取 exit_code
exit_match = re.search(r'"exit_code"\s*:\s*(-?\d+)', content)
exit_code = exit_match.group(1) if exit_match else "?"

# search_files: 从 JSON 中提取 total_count
match_count = re.search(r'"total_count"\s*:\s*(\d+)', content)
count = match_count.group(1) if match_count else "?"
```

**触发条件**（`_prune_old_tool_results`）：
```python
# agent/context_compressor.py:435-442
# 只有超过 _CONTENT_MAX (约 500 chars) 的 tool result 才被替换
if len(content) > _CONTENT_MAX:
    summary = _summarize_tool_result(tool_name, tool_args, content)
```

### 50.3 与 BlueCortexCE 对比

| 维度 | Hermes Phase 1 Pre-pass | BlueCortexCE |
|------|----------------------|--------------|
| 压缩方式 | 规则型 1-line 摘要 | Observation 全量保留 |
| Token 节省 | 仅摘要部分节省（非全量丢弃） | 全量存储，无压缩 |
| 信息损失 | 工具名 + 参数 + 关键结果保留 | 零信息损失 |
| LLM 成本 | Phase 1 零成本 | N/A（无压缩） |
| 应用场景 | 压缩时调用 | 不适用 |

### 50.4 翻译：旁路型如何借鉴

**Hermes 做法**：压缩时用规则型方法预处理 tool results。

**Hermes 为什么这样做**：节省 LLM summarizer 的输入 token，降低 summarization 成本和质量损失。

**BlueCortexCE 现状**：当前没有压缩机制，所有 observation 全量存储。

**翻译：旁路型如何落地**：
- **Phase 3 Structured Extraction** 中，可以考虑对 ToolResult observation 应用类似的规则型摘要
- 例如：`{tool_name} called with {args_summary} -> {result_summary}`
- 但需要注意：BlueCortexCE 是**持久化存储**，不是临时的 context 压缩——存储摘要 vs 存储原始内容的取舍需要权衡
- **高优先级**：对 `source=tool_output` 的 observation 增加字段 `toolSummary`（可选），API 消费者可以自主选择存储粒度

---

## 51. SessionDB v6 — Reasoning Chain 持久化存储（v4.7 新增）

> **文件**: `hermes_state.py:314-325`
> **本节为 v4.7 新增**，分析 v6 schema migration 为 messages 表新增的 reasoning 相关列，以及 reasoning chain 连续性问题的根因和解决方案。

### 51.1 问题背景

多轮推理（multi-turn reasoning）面临一个关键问题：**当对话被压缩或 session 被重新加载时，assistant 的 reasoning chain 会被丢弃**。这导致：
- Provider（OpenRouter、OpenAI、Nous）重新加载 session 时，看到的 assistant 消息没有 reasoning context
- 模型不知道自己之前的推理过程，无法保持推理连续性

### 51.2 v6 Schema Migration

```python
# hermes_state.py:314-325
if current_version < 6:
    # v6: add reasoning columns to messages table — preserves assistant
    # reasoning text and structured reasoning_details across gateway
    # session turns.  Without these, reasoning chains are lost on
    # session reload, breaking multi-turn reasoning continuity for
    # providers that replay reasoning (OpenRouter, OpenAI, Nous).
    for col_name, col_type in [
        ("reasoning", "TEXT"),
        ("reasoning_details", "TEXT"),
        ("codex_reasoning_items", "TEXT"),
    ]:
        try:
            safe = col_name.replace('"', '""')
            cursor.execute(
                f'ALTER TABLE messages ADD COLUMN "{safe}" {col_type}'
            )
        except sqlite3.OperationalError:
            pass  # Column already exists
    cursor.execute("UPDATE schema_version SET version = 6")
```

**三个新列**：

| 列名 | 类型 | 用途 |
|------|------|------|
| `reasoning` | TEXT | Assistant 的完整 thinking/reasoning 内容 |
| `reasoning_details` | TEXT | 结构化的 reasoning 元数据（JSON） |
| `codex_reasoning_items` | TEXT | Codex 特有的结构化 reasoning items（JSON） |

### 51.3 写入与恢复

**写入**（`Message` dataclass）：
```python
# hermes_state.py:801-818
reasoning: str = None,
reasoning_details: Any = None,
codex_reasoning_items: Any = None,

reasoning_details_json = (
    json.dumps(reasoning_details)
    if reasoning_details else None
)
codex_reasoning_items_json = (
    json.dumps(codex_reasoning_items)
    if codex_reasoning_items else None
)
```

**恢复**（`_load_messages_from_db`）：
```python
# hermes_state.py:912-924
# Restore reasoning fields on assistant messages so providers
# that replay reasoning (OpenRouter, OpenAI, Nous) receive
# coherent multi-turn reasoning context.
if row["reasoning"]:
    msg["reasoning"] = row["reasoning"]
if row["reasoning_details"]:
    msg["reasoning_details"] = json.loads(row["reasoning_details"])
if row["codex_reasoning_items"]:
    msg["codex_reasoning_items"] = json.loads(row["codex_reasoning_items"])
```

### 51.4 与 BlueCortexCE 对比

| 维度 | Hermes SessionDB v6 | BlueCortexCE |
|------|-------------------|--------------|
| 存储内容 | reasoning chain 完整保留 | Observation 中无 reasoning 字段 |
| 持久化 | SQLite messages 表列 | PostgreSQL observations 表（当前无） |
| 压缩后连续性 | reasoning 通过 summary 传递 | ❌ 无 reasoning chain 概念 |
| Provider 可见性 | Provider 可访问 reasoning | 不适用（旁路型） |

### 51.5 翻译：旁路型如何借鉴

**Hermes 做法**：在 messages 表中新增专用列存储 reasoning，并在 session reload 时恢复。

**Hermes 为什么这样做**：内置型 Agent 需要维护完整的 reasoning chain 连续性，压缩后的 summary 无法保留推理过程。

**BlueCortexCE 现状**：
- 当前 Observation entity **没有** `reasoning` 相关字段
- 也没有 `type=reasoning` 或类似的分类

**翻译：旁路型如何落地**：
- **中优先级**：考虑在 Observation entity 中增加 `observationType` 字段（如 `user_prompt` / `assistant_response` / `reasoning` / `tool_result`），这样 BlueCortexCE 可以保留 reasoning chain
- **但是**：这取决于 API 消费者（Claude Code/OpenClaw）是否会主动提交 reasoning content。如果它们不提交，这个字段就是空的
- **更实际的路径**：在 Phase 3 extraction 的 prompt schema 中定义 `reasoningChain` 字段，让 LLM 在提取时判断当前 observation 是否包含推理过程
- **根因**：旁路型架构下，"何时触发写入" 由消费方决定；我们只能提供存储能力，无法强制消费方提交 reasoning

---

## 52. Honcho write_frequency — 四种写入模式实现（v4.7 新增）

> **文件**: `plugins/memory/honcho/client.py:168-170` + `plugins/memory/honcho/__init__.py:573-610`
> **本节为 v4.7 新增**，分析 HonchoMemoryProvider 的 write_frequency 配置及其 sync_turn 实现细节。

### 52.1 write_frequency 配置

```python
# plugins/memory/honcho/client.py:168-170
# Write frequency: "async" (background thread), "turn" (sync per turn),
# "session" (flush on session end), or int (every N turns)
write_frequency: str | int = "async"
```

**四种模式**：

| 模式 | 行为 | 延迟 | 可靠性 |
|------|------|------|--------|
| `"async"` | 后台线程写入（daemon thread） | 异步，最多等待 5s | 可能丢数据（进程退出时 daemon 来不及 flush） |
| `"turn"` | 每轮同步写入 Honcho API | 实时 | 高可靠 |
| `"session"` | Session 结束时 flush | 最少 API 调用 | 最高效，但 crash 会丢整轮 |
| `int` | 每 N 轮 flush 一次 | 批量 | 平衡效率和可靠性 |

**配置解析**：
```python
# plugins/memory/honcho/client.py:306-315
if raw_wf == "async":
    write_frequency = "async"
elif raw_wf == "turn":
    write_frequency = "turn"
elif raw_wf == "session":
    write_frequency = "session"
elif raw_wf.isdigit() or (
    raw_wf.lstrip("-").isdigit()
):
    write_frequency: str | int = int(raw_wf)
else:
    write_frequency = "async"
```

### 52.2 sync_turn 实际实现

```python
# plugins/memory/honcho/__init__.py:573-610
def sync_turn(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    """Record the conversation turn in Honcho (non-blocking)."""
    def _sync():
        try:
            session = self._manager.get_or_create(self._session_key)
            for chunk in self._chunk_message(user_content, msg_limit):
                session.add_message("user", chunk)
            for chunk in self._chunk_message(assistant_content, msg_limit):
                session.add_message("assistant", chunk)
            self._manager._flush_session(session)
        except Exception as e:
            logger.debug("Honcho sync_turn failed: %s", e)

    if self._sync_thread and self._sync_thread.is_alive():
        self._sync_thread.join(timeout=5.0)
    self._sync_thread = threading.Thread(
        target=_sync, daemon=True, name="honcho-sync"
    )
    self._sync_thread.start()
```

**关键设计点**：

1. **Daemon thread**：每次调用创建新线程，`daemon=True` 意味着进程退出时自动终止
2. **前一次 join**：如果上一次 sync 还没完成，等最多 5 秒。这避免了在快速连续调用时堆积大量 pending writes
3. **Message chunking**：超过 `message_max_chars`（默认 25k）的消息会被分块
4. **静默失败**：`sync_turn` 失败只记录 debug 日志，不抛出异常，不阻塞主流程

### 52.3 写入流程总结

```
on_turn_start (turn N)
  → sync_turn(user_content, assistant_content)
    → spawn daemon thread
      → session.add_message("user", chunk1)
      → session.add_message("assistant", chunk2)
      → _flush_session() → Honcho API
```

### 52.4 与 BlueCortexCE 对比

| 维度 | Honcho write_frequency | BlueCortexCE |
|------|----------------------|--------------|
| 写入触发 | turn-based + 配置策略 | POST /api/ingest/observation（消费方控制） |
| 异步能力 | 后台 daemon thread | WebSocket streaming（非 async thread） |
| 批量策略 | write_frequency 控制 | 消费方自行决定批量或实时 |
| 可靠性 | daemon 可能丢数据 | 同步 HTTP POST，失败会返回 error |
| 错误处理 | 静默（只 debug log） | 显式 HTTP error 响应 |

### 52.5 翻译：旁路型如何借鉴

**Hermes 做法**：多种 write_frequency 策略（async/turn/session/int），后台 daemon thread 非阻塞写入。

**Hermes 为什么这样做**：Honcho 是云端 API，每次 turn 都同步调用会引入延迟。async 模式保证不阻塞主流程。

**BlueCortexCE 现状**：
- `/api/ingest/observation` 是同步 HTTP POST
- 失败会返回 error code
- 没有内置的"批量延迟写入"或"异步后台写入"机制

**翻译：旁路型如何借鉴**：
- **低优先级**：BlueCortexCE 作为旁路型服务，HTTP 同步写入是正确的设计——消费方（Claude Code/OpenClaw）应该负责自己的本地缓冲和重试逻辑
- **更实际的建议**：在 SDK 层（JS/Go/Python）提供可选的"buffered write"模式，允许消费方本地批量缓冲后一次性发送，而不是每次 observation 都发一个 HTTP 请求
- **已具备**：BlueCortexCE 的 SSE streaming（`/api/stream`）本质上已经是"异步推送"机制，只是触发点不同

---

## 44. RetainDB — SQLite Write-Behind Queue + memory_type 枚举 + Agent Self-Model（v4.8 新增）

> **文件**: `plugins/memory/retaindb/__init__.py`（766 行）
> **本节为 v4.8 新增**，分析 RetainDB 的三项独特机制：SQLite write-behind queue、structured memory_type 枚举、Agent Self-Model

### 44.1 RetainDB 定位概览

RetainDB 是 Hermes 中**唯一同时实现 SQLite 本地持久化和 structured memory_type** 的 Provider：

| 特性 | RetainDB | Honcho | Holographic | Mem0 |
|------|----------|--------|-------------|------|
| 本地 SQLite | ✅ Write-behind queue | ❌ | ❌ | ❌ |
| Structured memory_type | ✅ 6 类 + importance | ❌ | ❌ | ❌ |
| Agent Self-Model | ✅ SOUL.md seeding | ❌ | ❌ | ❌ |
| Shared File Store | ✅ rdb:// URI | ❌ | ❌ | ❌ |
| Dialectic synthesis | ✅ ask_user API | ✅ | ✅ | ❌ |

**RetainDB 不实现的 Hooks**：`on_turn_end`、`on_compress`、`on_delegation` — 完全依赖工具调用（显式记忆）而非自动提取。

### 44.2 SQLite Write-Behind Queue — 崩溃安全的异步写入

RetainDB 的 `_WriteQueue`（`plugins/memory/retaindb/__init__.py:333-405`）实现**进程内 SQLite 本地持久化 + API 异步发送**：

```python
# plugins/memory/retaindb/__init__.py:333-345
class _WriteQueue:
    """SQLite-backed async write queue. Survives crashes — pending rows replay on startup."""
    def __init__(self, client: _Client, db_path: Path):
        self._client = client
        self._db_path = db_path
        self._q: queue.Queue = queue.Queue()
        self._thread = threading.Thread(target=self._loop, name="retaindb-writer", daemon=True)
        self._db_path.parent.mkdir(parents=True, exist_ok=True)
        self._local = threading.local()  # Thread-local connection cache
        self._init_db()
        self._thread.start()
        # Replay any rows left from a previous crash
        for row_id, user_id, session_id, msgs_json in self._pending_rows():
            self._q.put((row_id, user_id, session_id, json.loads(msgs_json)))
```

**数据库 Schema**：
```python
# plugins/memory/retaindb/__init__.py:356-360
conn.execute("""CREATE TABLE IF NOT EXISTS pending (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT, session_id TEXT, messages_json TEXT,
    created_at TEXT, last_error TEXT
)""")
```

**三个阶段的工作流**：
1. **Enqueue**（`sync_turn` 调用时）：写入 SQLite → 放入 Python `queue.Queue`
2. **Background loop**（daemon thread）：从 queue 取任务 → 调用 `client.ingest_session()` → 成功后 DELETE row
3. **Crash Recovery**（下次启动）：读取所有 `pending` 表中未 DELETE 的 rows → 重新入队

```python
# plugins/memory/retaindb/__init__.py:369-375
def enqueue(self, user_id: str, session_id: str, messages: list) -> None:
    now = datetime.now(timezone.utc).isoformat()
    conn = self._get_conn()
    cur = conn.execute(
        "INSERT INTO pending (user_id, session_id, messages_json, created_at) VALUES (?,?,?,?)",
        (user_id, session_id, json.dumps(messages, ensure_ascii=False), now),
    )
    row_id = cur.lastrowid
    conn.commit()
    self._q.put((row_id, user_id, session_id, messages))

# plugins/memory/retaindb/__init__.py:380-391
def _flush_row(self, row_id: int, user_id: str, session_id: str, messages: list) -> None:
    try:
        self._client.ingest_session(user_id, session_id, messages)
        conn = self._get_conn()
        conn.execute("DELETE FROM pending WHERE id = ?", (row_id,))  # 成功后删除
        conn.commit()
    except Exception as exc:
        logger.warning("RetainDB ingest failed (will retry): %s", exc)
        conn = self._get_conn()
        conn.execute("UPDATE pending SET last_error = ? WHERE id = ?", (str(exc), row_id))
        conn.commit()
        time.sleep(2)  # 失败后 sleep 2s，但 row 仍留在 queue 中（下一次 loop 会重试）
```

**失败重试机制**：row 写入后不会从 queue 中移除（只有 `DELETE` 成功时才移除），因此下次 loop 迭代时会自动重试。`last_error` 字段记录最近一次错误。

**Thread-local SQLite connections**：每个线程复用同一个 SQLite 连接（`_get_conn()`），避免跨线程连接竞争。

### 44.3 memory_type 枚举 + importance 重要性分数

RetainDB 的 `add_memory` API 支持**structured 分类**：

```python
# plugins/memory/retaindb/__init__.py:245-256
def add_memory(self, user_id: str, session_id: str, content: str,
               memory_type: str = "factual", importance: float = 0.7) -> dict:
    return self.request("POST", "/v1/memory", json_body={
        "project": self.project,
        "content": content,
        "memory_type": memory_type,  # 6 分类
        "user_id": user_id,
        "session_id": session_id,
        "importance": importance,    # 0-1 浮点
        "write_mode": "sync",
    }, timeout=5.0)
```

**6 类 memory_type**（`plugins/memory/retaindb/__init__.py:245`）：

| memory_type | 含义 | 示例 |
|------------|------|------|
| `factual` | 客观事实 | "用户的工作目录是 /project" |
| `preference` | 用户偏好 | "用户喜欢用 TypeScript" |
| `goal` | 目标 | "用户希望完成 MVP" |
| `instruction` | 指令 | "每次提交前运行测试" |
| `event` | 事件 | "用户上周参加了会议" |
| `opinion` | 观点 | "用户认为这个方案更好" |

**importance 分数（0.0-1.0）**：显式重要性评分，用于决定记忆的权重。

### 44.4 Agent Self-Model — SOUL.md 自动播种

RetainDB 是**唯一实现 Agent Self-Model** 的 Provider — 它将 SOUL.md 的内容播种到云端，使 Agent 的"自我认知"可被检索：

```python
# plugins/memory/retaindb/__init__.py:525-528
def _seed_soul(self, content: str) -> None:
    try:
        self._client.seed_agent_identity(self._agent_id, content, source="soul_md")
    except Exception as exc:
        logger.debug("RetainDB soul seed failed: %s", exc)
```

**调用时机**（`initialize` 中）：
```python
# plugins/memory/retaindb/__init__.py:518-524
soul_path = hermes_home_path / "SOUL.md"
if soul_path.exists():
    soul_content = soul_path.read_text(encoding="utf-8", errors="replace").strip()
    if soul_content:
        threading.Thread(
            target=self._seed_soul,
            args=(soul_content,),
            name="retaindb-soul-seed",
            daemon=True,
        ).start()
```

**Agent Self-Model 检索**（`prefetch_agent_model`）：
```python
# plugins/memory/retaindb/__init__.py:579-586
def _prefetch_agent_model(self) -> None:
    try:
        model = self._client.get_agent_model(self._agent_id)
        if model.get("memory_count", 0) > 0:
            with self._lock:
                self._agent_model = model
    except Exception as exc:
        logger.debug("RetainDB agent model prefetch failed: %s", exc)
```

**在 prefetch 中的组装**：
```python
# plugins/memory/retaindb/__init__.py:597-617
def prefetch(self, query: str, *, session_id: str = "") -> str:
    # ...
    if agent_model and agent_model.get("memory_count", 0) > 0:
        model_lines: list[str] = []
        if agent_model.get("persona"):
            model_lines.append(f"Persona: {agent_model['persona']}")
        if agent_model.get("persistent_instructions"):
            model_lines.append("Instructions:\n" + "\n".join(f"- {i}" for i in agent_model["persistent_instructions"]))
        if agent_model.get("working_style"):
            model_lines.append(f"Working style: {agent_model['working_style']}")
        if model_lines:
            parts.append("[RetainDB Agent Self-Model]\n" + "\n".join(model_lines))
```

**prefetch 完整内容**（三个并行 prefetch）：
1. `_prefetch_context` — profile + query context 叠加去重
2. `_prefetch_dialectic` — LLM 合成用户理解（`ask_user` API）
3. `_prefetch_agent_model` — Agent 自我认知（SOUL.md 播种的内容）

### 44.5 与 BlueCortexCE 对比

| 维度 | RetainDB | BlueCortexCE |
|------|----------|--------------|
| Write-behind queue | SQLite 本地持久化 + daemon | ❌ 无（同步 HTTP POST） |
| memory_type | 6 类枚举 | ❌ 无（Observation 无类型分类） |
| importance | 0-1 浮点 | ❌ 无 |
| Agent Self-Model | SOUL.md 播种到云端 | N/A（SOUL.md 定义自身） |
| 显式记忆工具 | retaindb_remember | `/api/ingest/observation` |
| 自动提取 | ❌（无 on_turn_end/on_compress） | ✅ SessionEnd Summary |
| Project 隔离 | `RETAINDB_PROJECT` 或 `hermes-<profile>` | session_id 隔离 |

### 44.6 翻译：旁路型如何借鉴

**Hermes 做法**：RetainDB 是一个云端 API + 本地 SQLite 缓存的混合架构，通过工具显式写入记忆，支持 memory_type 分类和 importance 评分。

**Hermes 为什么这样做**：内置型 Agent 需要在进程 crash 后恢复未发送的记忆，本地 SQLite queue 提供了 durability；同时 structured memory_type 帮助后续检索时过滤和排序。

**翻译：旁路型如何借鉴**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE Observation 增加 `memory_type` 字段 | 6 类分类（factual/preference/goal/instruction/event/opinion），当前 Observation 无类型 |
| **高** | BlueCortexCE Observation 增加 `importance` 字段 | 0-1 浮点，允许消费方标记重要性 |
| **中** | BlueCortexCE SDK 增加可选的"buffered write" | 本地 SQLite queue，消费方批量发送（降低 API 调用频率） |
| **低** | BlueCortexCE 增加 Agent Self-Model 端点 | 允许消费方播种 agent 身份信息，供后续检索（Phase 3 extraction templates 可借鉴） |

---

## 45. Supermemory — 精确提取 Prompt + Trivial 过滤 + 多容器架构（v4.8 新增）

> **文件**: `plugins/memory/supermemory/__init__.py`（791 行）
> **本节为 v4.8 新增**，分析 Supermemory 的三项独特设计：实体提取 prompt engineering、trivial response 过滤、多容器架构

### 45.1 Supermemory 定位概览

Supermemory 是一个**以提取质量为核心**的 Provider，强调"只记值得记忆的"：

| 特性 | Supermemory | Honcho | RetainDB | Holographic |
|------|------------|--------|----------|-------------|
| 提取 Prompt | ✅ 自定义 `entity_context` | ❌ | ❌ | ✅ 正则提取 |
| Trivial 过滤 | ✅ `_TRIVIAL_RE` | ❌ | ❌ | ❌ |
| 多容器 | ✅ custom containers | ❌ | ❌ | ❌ |
| Shared files | ❌ | ❌ | ✅ | ❌ |
| Agent Self-Model | ❌ | ❌ | ✅ | ❌ |
| 自动提取 hooks | `on_turn_end` | ✅ | ❌ | ✅ |

### 45.2 精确提取 Prompt — "When in doubt, store less"

Supermemory 的**最独特设计**是允许用户自定义 `entity_context` prompt（`plugins/memory/supermemory/__init__.py:60-80`），其中包含了**negative 指令**（告诉模型什么不要记）：

```python
# plugins/memory/supermemory/__init__.py:60-80
_DEFAULT_ENTITY_CONTEXT = (
    "User-assistant conversation. Format: [role: user]...[user:end] and "
    "[role: assistant]...[assistant:end].\n\n"
    "Only extract things useful in future conversations. Most messages are not worth remembering.\n\n"
    "Remember lasting personal facts, preferences, routines, tools, ongoing projects, working context, "
    "and explicit requests to remember something.\n\n"
    "Do not remember temporary intents, one-time tasks, assistant actions, implementation details, or in-progress status.\n\n"
    "When in doubt, store less."
)
```

**关键设计洞察**：
1. **`Most messages are not worth remembering`** — 明确告知模型大多数对话无价值
2. **Negative 列举**：`temporary intents`、`one-time tasks`、`assistant actions`、`implementation details`、`in-progress status`
3. **`When in doubt, store less`** — 最终指令，保守策略

### 45.3 Trivial Response 过滤

Supermemory 的 `on_turn_end` 会过滤掉无意义的响应（`plugins/memory/supermemory/__init__.py:32-34`）：

```python
# plugins/memory/supermemory/__init__.py:32-34
_TRIVIAL_RE = re.compile(
    r"^(ok|okay|thanks|thank you|got it|sure|yes|no|yep|nope|k|ty|thx|np)\.?$",
    re.IGNORECASE,
)
```

**使用点**（在 `on_turn_end` 或 `capture` 逻辑中）：如果 user message 匹配 `_TRIVIAL_RE`，跳过该轮的记忆提取。

**这与 BlueCortexCE 的 Observation 设计完全相反**：BlueCortexCE 的 SessionEnd summary 倾向于总结一切，而 Supermemory 在**源头就做过滤**。

### 45.4 多容器架构 + custom_container_instructions

Supermemory 支持**多容器**（`plugins/memory/supermemory/__init__.py:120-125`），允许按 topic/functionality 划分记忆：

```python
# 配置项
"enable_custom_container_tags": True,
"custom_containers": ["project-alpha", "personal", "work"],  # 用户定义的容器标签
"custom_container_instructions": "project-alpha containers should prioritize code decisions..."
```

**容器级别隔离**：不同容器的记忆互相隔离，检索时可以指定容器范围。

### 45.5 Supermemory 的 category 自动推断

`plugins/memory/supermemory/__init__.py:172-183`：

```python
def _detect_category(text: str) -> str:
    lowered = text.lower()
    if re.search(r"prefer|like|love|hate|want", lowered):
        return "preference"
    if re.search(r"decided|will use|going with", lowered):
        return "decision"
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered):
        return "fact"
    return "other"
```

**自动推断类别**：`preference` / `decision` / `fact` / `other`。这是在**提取时**做分类，而不是依赖用户显式标记。

### 45.6 与 BlueCortexCE 对比

| 维度 | Supermemory | BlueCortexCE |
|------|------------|--------------|
| 提取 Prompt | 可自定义 `entity_context` | ❌ 无（LLM 自己决定） |
| Negative 指令 | ✅ "Do not remember X" | ❌ 无 |
| Trivial 过滤 | ✅ 跳过 ok/yes/thanks | ❌ 无 |
| 多容器 | ✅ 按 topic 隔离 | ❌ 无（单 session） |
| 自动 category | ✅ preference/decision/fact | ❌ 无 |
| "Store less" 策略 | ✅ "When in doubt, store less" | ❌（倾向总结一切） |

### 45.7 翻译：旁路型如何借鉴

**Hermes 做法**：Supermemory 在提取阶段就做严格的过滤和质量控制，用 negative 指令告诉 LLM 什么不值得记忆。

**Hermes 为什么这样做**：内置型 Agent 的 context 是有限的，如果什么都记会导致 context 被无用信息淹没。Supermemory 选择**质量 over 数量**。

**翻译：旁路型如何借鉴**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE `/api/summaries` prompt 增加 negative 指令 | "Do NOT remember: trivial responses, temporary intents, one-time tasks, implementation details, assistant's own actions" |
| **高** | BlueCortexCE Observation 过滤 trivial messages | 对于 user message 中的 "ok", "thanks", "sure" 等，跳过 observation 记录 |
| **中** | BlueCortexCE SessionEnd summary prompt 增加 "When in doubt, store less" | 明确告诉 summary LLM：宁可少记，不要记噪声 |
| **中** | BlueCortexCE Observation 增加 `category` 字段 | auto-infer: preference/decision/fact（参考 Supermemory 的 `_detect_category` 正则规则） |
| **低** | BlueCortexCE 考虑 multi-container 架构 | 不同 project 的记忆容器隔离（Phase 3 长期可考虑） |

---

