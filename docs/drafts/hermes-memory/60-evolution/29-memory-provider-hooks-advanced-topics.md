# MemoryProvider Hooks — 高级专题（续）

> **来源拆分**：本文件从 [`06-memory-provider-hooks-inventory.md`](06-memory-provider-hooks-inventory.md) 末尾拆分而来（原文 §43–§44 已迁入本文）。  
> **体量**：约 9.2KB ≪ 50KB 上限。  
> **总览索引**：[`hermes-memory/index.md`](../index.md)

---


## 43. BlueCortexCE vs Hermes Summary Template — 逐项逐字段对比（v4.5 新增）

> **文件**: `backend/src/main/resources/prompts/summary.txt`（BlueCortexCE）vs `agent/context_compressor.py:570-650`（Hermes）
> **本节为 v4.5 新增**，对两个模板进行**字段级别的逐项对比**，明确指出 BlueCortexCE 缺少的每个维度。

### 43.1 模板结构对比

**Hermes 12 段式（preamble + 11 sections）**：

```
[Preamble] "Do NOT respond" + "different assistant" 指令

## Goal
[What the user is trying to accomplish]

## Constraints & Preferences          ← BlueCortexCE 缺失
[User preferences, coding style, constraints, important decisions]

## Completed Actions                 ← BlueCortexCE 有类似（completed）
[Numbered list of concrete actions taken]
Format: N. ACTION target — outcome [tool: name]

## Active State                      ← BlueCortexCE 缺失
[Current working state — cwd/branch, modified files, test status, running processes]

## In Progress                       ← BlueCortexCE 缺失
[Work currently underway]

## Blocked                          ← BlueCortexCE 缺失
[Any blockers, errors, or issues not yet resolved]

## Key Decisions                    ← BlueCortexCE 缺失
[Important technical decisions and WHY they were made]

## Resolved Questions                ← BlueCortexCE 缺失
[Questions already answered — include the answer]

## Pending User Asks                ← BlueCortexCE 有类似（request）
[Questions or requests NOT yet answered or fulfilled]

## Relevant Files                   ← BlueCortexCE 缺失
[Files read, modified, or created — with brief note on each]

## Remaining Work                   ← BlueCortexCE 有类似（next_steps）
[What remains to be done — framed as context, not instructions]

## Critical Context                 ← BlueCortexCE 缺失
[Any specific values, error messages, configuration details]
```

**BlueCortexCE 5+1 段式**：

```
## Request                          ← 类似 Hermes Goal
[Short title capturing user's request AND substance]

## Investigated                     ← Hermes 无对应
[What has been explored so far? What was examined?]

## Learned                         ← Hermes 无对应
[What have you learned about how things work?]

## Completed                       ← 类似 Hermes Completed Actions
[What work has been completed so far?]

## Next Steps                      ← 类似 Hermes Remaining Work
[What are you actively working on or planning to work on next?]

## Notes                           ← 类似 Hermes Critical Context
[Additional insights or observations]
```

### 43.2 逐字段对比

| 字段 | Hermes | BlueCortexCE | 差距 | 优先级 |
|------|--------|-------------|------|--------|
| `Goal` | ✅ 11-section 有 | ✅ request 涵盖 | 部分重叠 | — |
| `Constraints & Preferences` | ✅ 有 | ❌ **缺失** | **高** | **高** |
| `Completed Actions` | ✅ 编号列表 + tool 标注 | ⚠️ completed（无格式） | 中 | **高** |
| `Active State` | ✅ 有 | ❌ **缺失** | **高** | **高** |
| `In Progress` | ✅ 有 | ❌ **缺失** | 中 | **高** |
| `Blocked` | ✅ 有 | ❌ **缺失** | **高** | **高** |
| `Key Decisions` | ✅ 有 + WHY | ❌ **缺失** | **高** | **高** |
| `Resolved Questions` | ✅ 有 | ❌ **缺失** | 中 | **中** |
| `Pending User Asks` | ✅ 有 | ⚠️ request 部分涵盖 | 低 | **中** |
| `Relevant Files` | ✅ 有 | ❌ **缺失** | **高** | **高** |
| `Remaining Work` | ✅ 有 | ⚠️ next_steps 部分涵盖 | 低 | — |
| `Critical Context` | ✅ 有 | ⚠️ notes 部分涵盖 | 中 | — |
| `Investigated` | ❌ 无 | ✅ 有 | 低 | — |
| `Learned` | ❌ 无 | ✅ 有 | 低 | — |

### 43.3 BlueCortexCE 独特字段

| 字段 | BlueCortexCE 独有 | 价值 |
|------|-----------------|------|
| `investigated` | ✅ 有 | 记录探索路径（对 debug 有价值） |
| `learned` | ✅ 有 | 记录"学到了什么"（对 knowledge capture 有价值） |

### 43.4 缺失字段对 BlueCortexCE 的实际影响

**最严重缺失（高优先级）**：

1. **`Constraints & Preferences`** — 没有这个字段，Summary 无法记录用户的偏好和约束。下次对话时，Agent 可能不知道"用户偏好用 TypeScript"这类关键信息。

2. **`Active State`** — 没有当前工作状态记录。下次对话时，Agent 不知道当前在哪个目录、有什么文件被修改、测试状态如何。

3. **`Blocked`** — 没有阻塞点记录。下次对话时，Agent 可能重复尝试已经失败的方法。

4. **`Key Decisions`** — 没有决策记录和原因。下次对话时，Agent 不知道"为什么选择了这个方案"。

5. **`Relevant Files`** — 没有文件变更记录。下次对话时，Agent 不知道哪些文件被修改过。

**中优先级缺失**：

6. **`In Progress`** — 当前正在做的工作没有独立字段，与 Completed 混在一起。

7. **`Completed Actions` 格式** — BlueCortexCE 的 completed 是自由文本，Hermes 要求编号 + tool 标注（`[tool: read_file]`）。

### 43.5 翻译：BlueCortexCE Summary Template 改进建议

**建议的改进后模板**（在 BlueCortexCE 的基础上增加高优先级缺失字段）：

```
## Request
[Short title capturing the user's request]

## Constraints & Preferences          ← 新增
[User preferences, coding style, constraints]
例: "用户偏好 TypeScript，不要用 JavaScript；项目使用 macOS"

## Active State                       ← 新增
- CWD: /path/to/project
- Modified: src/index.ts, src/utils.ts
- Test Status: 3/50 failing (test_parse, test_validate)
- Running: None

## Completed Actions                  ← 格式增强
1. READ config.py:45 — found `==` should be `!=` [tool: read_file]
2. PATCH config.py:45 — changed `==` to `!=` [tool: patch]
3. TEST `pytest tests/` — 3/50 failed: test_parse, test_validate [tool: terminal]

## Investigated
[What has been explored so far?]

## Learned
[What have you learned about how things work?]

## Blocked                            ← 新增
[Any blockers or errors not yet resolved]
例: "API 认证失败，401 Unauthorized"

## Key Decisions                      ← 新增
- 为什么选择 PostgreSQL：因为需要向量搜索 + 结构化查询
- 为什么不用 Redis：因为数据量超过内存容量

## Relevant Files                     ← 新增
- src/index.ts — 新增用户认证逻辑
- src/utils.ts — 新增日期格式化函数
- docs/api.md — 更新了接口文档

## Next Steps
[What are you actively working on or planning to work on next?]

## Notes / Critical Context
[Additional insights or critical values/configs to preserve]
```

**实现优先级**：

| 优先级 | 字段 | 实现说明 |
|--------|------|----------|
| **P0** | `Constraints & Preferences` | Prompt 增加此字段，解析后存储 |
| **P0** | `Active State` | 当前 CWD、modified files 可以从 session context 自动获取 |
| **P0** | `Blocked` | 从 last assistant message 中推断（关键词：错误/failed/无法） |
| **P0** | `Key Decisions` | 需要 LLM 主动输出 |
| **P1** | `Relevant Files` | 从 observations 中自动聚合 |
| **P1** | `Completed Actions` 格式 | Prompt 要求编号 + tool 标注格式 |
| **P2** | `In Progress` | 可以从 last observation 中推断 |
| **P2** | `Resolved Questions` | 需要记录 Q&A 对 |

---

## 44. SessionSearch LLM 截断策略 — Final Fallback 与字数限制（v4.5 新增）

> **文件**: `agent/context_compressor.py:300-500`（SessionSearch 类）
> **本节为 v4.5 新增**，分析 Hermes 的 SessionSearch 在 LLM summarization 失败时的 fallback 策略。

### 44.1 SessionSearch 三层降级策略

```python
# SessionSearch: 三层降级
# Layer 1: LLM summarization（如果配置了 model + api_key）
# Layer 2: phrase → proximity → individual term 截断（Section 17 详细分析）
# Layer 3: raw preview（直接返回原文前 N chars）
```

### 44.2 LLM summarization 的截断保护

```python
# context_compressor.py:SessionSearch
MAX_SUMMARY_CHARS = 2000   # LLM summary 最大长度

def summarize_with_llm(self, query: str, excerpts: list[str]) -> str:
    # 1. 如果 excerpts 总长度 > 4000 chars，先截断
    combined = "\n".join(excerpts)
    if len(combined) > 4000:
        combined = combined[:4000] + "\n\n[... truncated ...]"

    # 2. 调用 LLM summarization
    summary = self._llm_summarize(query, combined)

    # 3. 如果 summary > 2000 chars，截断
    if len(summary) > MAX_SUMMARY_CHARS:
        summary = summary[:MAX_SUMMARY_CHARS]

    return summary
```

### 44.3 与 BlueCortexCE 对比

| 维度 | Hermes SessionSearch | BlueCortexCE |
|------|---------------------|--------------|
| 多层 fallback | LLM → phrase → raw | ❌ 无（只有 LLM） |
| 输入长度保护 | > 4000 chars 截断 | ❌ 无 |
| 输出长度保护 | > 2000 chars 截断 | ❌ 无 |
| 空结果处理 | raw preview | ❌ 无 |

### 44.4 翻译：旁路型如何借鉴

**低优先级建议**：
- BlueCortexCE 的 `/api/context/generate` 可以增加多层 fallback：
  1. 完整 LLM summarization（当前）
  2. 如果 LLM 不可用 → 返回原始 relevant observations（按时间排序）
  3. 如果observation 太多 → phrase → proximity → term 截断

---


---

## 45. RetainDB — SQLite Write-Behind Queue + memory_type 枚举 + Agent Self-Model（v4.8 新增）

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

## 46. Supermemory — 精确提取 Prompt + Trivial 过滤 + 多容器架构（v4.8 新增）

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

