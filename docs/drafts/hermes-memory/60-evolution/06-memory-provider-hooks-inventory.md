<!-- split 6/10 | aspect:60-evolution | ≤50KB -->

## 34. MemoryProvider 生命周期 Hooks 全量清单（v4.3 新增）

> **文件**: `agent/memory_provider.py:144-230`
> **本节为 v4.3 新增**，修正之前"6 Hooks vs 5 Hooks"的错误计数，列出 Hermes MemoryProvider 的**完整 7 个生命周期 Hook**。

### 34.1 完整 Hook 清单

| # | Hook 名称 | 触发时机 | Hermes 用途 | BlueCortexCE 等价 |
|---|-----------|----------|-----------|-------------------|
| 1 | `sync_turn` | 每个对话轮次 | Honcho/Holographic 写入消息 | `POST /api/turn/sync` |
| 2 | `prefetch` | 工具调用前（后台） | Honcho/Holographic 预取记忆 | ❌ 无 |
| 3 | `on_turn_start` | 每个对话轮次开始 | 轮次计数、定期维护 | ❌ 无 |
| 4 | `on_session_end` | Session 结束时 | Honcho flush_all、Holographic 聚合 | `POST /api/session/end` |
| 5 | `on_pre_compress` | 上下文压缩前 | **从未有 Provider 实现** | ❌ 无 |
| 6 | `on_delegation` | 子 Agent 完成后（父侧） | **从未有 Provider 实现** | ❌ 无 |
| 7 | `on_memory_write` | 内置 memory 工具写入时 | Honcho 镜像为 conclusion、Holographic 镜像为 fact | ❌ 无 |

### 34.2 关键修正：`on_pre_compress` Hook

**之前的文档错误地统计为"6 hooks"，遗漏了 `on_pre_compress`**。

```python
# agent/memory_provider.py:163-172
def on_pre_compress(self, messages: List[Dict[str, Any]]) -> str:
    """Called before context compression discards old messages.

    Use to extract insights from messages about to be compressed.
    messages is the list that will be summarized/discarded.

    Return text to include in the compression summary prompt so the
    compressor preserves provider-extracted insights. Return empty
    string for no contribution (backwards-compatible default).
    """
    return ""
```

**设计意图**：在 ContextCompressor 压缩历史消息之前，给 Provider 一个机会提取"即将被压缩的信息"并将其以文本形式注入压缩 prompt，确保 Provider 特有的知识不被丢失。

**当前状态**：**没有任何 Provider 实现此 Hook**（所有 Provider 都返回空字符串）。

**可能的实现场景**：
- Honcho：将即将被压缩的对话中识别的用户偏好提取为文本
- Holographic：将即将被压缩的事实中的实体关系图谱信息提取
- Hindsight：将即将被压缩的多轮对话中的知识图谱关系提取

### 34.3 `on_turn_start` Hook

```python
# agent/memory_provider.py:144-152
def on_turn_start(self, turn_number: int, message: str, **kwargs) -> None:
    """Called at the start of each turn with the user message.

    Use for turn-counting, scope management, periodic maintenance.

    kwargs may include: remaining_tokens, model, platform, tool_count.
    Providers use what they need; extras are ignored.
    """
```

**使用场景**：
- Honcho：轮次计数（用于 `write_frequency=int` 模式）
- Holographic：定期 `contradict()` 健康检查

### 34.4 与 BlueCortexCE 对比总结

| Hook | Hermes | BlueCortexCE |
|------|--------|-------------|
| Turn-level sync | `sync_turn` | `POST /api/turn/sync` ✅ |
| Background prefetch | `prefetch` | ❌ 无（但 `/api/context/generate` 可以实现类似效果） |
| Turn start notification | `on_turn_start` | ❌ 无 |
| Session end | `on_session_end` | `POST /api/session/end` ✅ |
| Pre-compress extraction | `on_pre_compress` | ❌ 无（设计时未考虑） |
| Delegation observation | `on_delegation` | ❌ 无（旁路型不感知子 Agent） |
| Memory tool write bridge | `on_memory_write` | ❌ 无（BlueCortexCE 不实现内置 memory 工具） |

### 34.5 翻译：旁路型如何借鉴

**旁路型如何借鉴**：
- **`on_pre_compress`**：这是一个**极有价值的 Hook**——在上下文即将被压缩时，主动提取 Provider 侧的关键信息。建议 BlueCortexCE 在实现 ContextCompressor 时增加类似机制：当需要对历史 Observation 做摘要压缩时，Provider（如 Holographic）可以贡献额外文本。
- **`on_delegation`**：BlueCortexCE 作为旁路型系统，不感知子 Agent 存在。但如果消费方（如 Claude Code）使用子进程，可以显式调用 BlueCortexCE API 记录子任务结果。

---

## 35. on_pre_compress Hook — 压缩前洞察提取（v4.3 新增）

> **文件**: `agent/memory_provider.py:163-172`（接口），`run_agent.py:上下文压缩调用点`（调用链）
> **本节为 v4.3 新增**，深入分析 `on_pre_compress` Hook 的设计意图与未实现原因。

### 35.1 调用链

```
run_agent.py: ContextCompressor.__init__
  → self._memory_manager.on_pre_compress(self._messages_to_compress)
    → for provider in providers:
        → provider.on_pre_compress(messages_about_to_be_discarded)
          → return extracted_text
  → extracted_text 被注入压缩 prompt（确保 Provider 知识不丢失）
```

### 35.2 设计价值

`on_pre_compress` 的核心价值在于**跨 Provider 知识保留**：

- **Honcho 的知识**（用户偏好、对话历史）→ 以文本形式贡献给压缩 prompt
- **Holographic 的知识**（事实关系、实体重叠）→ 以文本形式贡献给压缩 prompt
- **Hindsight 的知识**（知识图谱、多轮关系）→ 以文本形式贡献给压缩 prompt

**问题**：为什么所有 Provider 都选择不实现？

**可能原因**：
1. **重复已有信息**：Provider 的信息已经通过 `sync_turn` 写入了外部系统，压缩时 LLM 可以直接检索
2. **额外 LLM 负担**：在压缩 prompt 中注入 Provider 文本会增加 prompt 长度，可能适得其反
3. **设计过于复杂**：这个 Hook 的语义不够清晰——Provider 应该提取什么？提取后放哪里？

### 35.3 翻译：旁路型如何借鉴

**BlueCortexCE 的 ContextCompressor**（`ContextService.generate`）目前没有类似机制：

```python
# BlueCortexCE 当前：无等价的 pre-compress hook
# 如果要借鉴，应该在压缩前：
# 1. 调用 Provider（Holographic/Honcho）的特殊端点
# 2. 提取 Provider 认为"最不应该丢失"的信息
# 3. 将其注入压缩 prompt
```

**优先级**：低（设计意图好，但实现复杂度高，且所有 Provider 都选择不实现）

---

## 36. Honcho per-repo Session Strategy — `_git_repo_name` 实现（v4.3 新增）

> **文件**: `plugins/memory/honcho/client.py:405-430`（`_git_repo_name`），`client.py:460-475`（`resolve_session_name`）
> **本节为 v4.3 新增**，分析 Honcho 的 `per-repo` session 策略及其 `_git_repo_name` 实现。

### 36.1 Session Strategy 四种模式

```python
# honcho/README.md:118
sessionStrategy: "per-directory"  # 可选: per-directory | per-session | per-repo | global
```

| 策略 | 行为 | Honcho Session 粒度 |
|------|------|-------------------|
| `per-session` | 每次运行新建 session | 一个 CLI session = 一个 Honcho session |
| `per-directory` | 每个工作目录一个 session | `basename(cwd)` |
| `per-repo` | 每个 git 仓库一个 session | `git repo root name` |
| `global` | 全局单一 session | workspace 级别 |

### 36.2 `_git_repo_name` 实现

```python
# plugins/memory/honcho/client.py:405-418
@staticmethod
def _git_repo_name(cwd: str) -> str | None:
    """Return the git repo root directory name, or None if not in a repo."""
    import subprocess

    try:
        root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output, text=True, cwd=cwd, timeout=5,
        )
        if root.returncode == 0:
            return Path(root.stdout.strip()).name
    except (OSError, subprocess.TimeoutExpired):
        pass
    return None
```

**算法**：
1. 在 `cwd` 下执行 `git rev-parse --show-toplevel`
2. 如果成功（returncode == 0），返回仓库根目录的**basename**（不是完整路径）
3. 失败（不在 git repo 中）→ 返回 `None`，调用方 fallback 到 `Path(cwd).name`

**示例**：
- `cwd=/Users/foo/projects/cortex-ce` 且是 git repo → 返回 `"cortex-ce"`
- `cwd=/tmp/some-dir`（非 git）→ 返回 `None`

### 36.3 `resolve_session_name` 完整解析顺序

```python
# client.py:422-475
def resolve_session_name(self, cwd, session_title, session_id):
    """Resolution order:
      1. Manual directory override from sessions map
      2. Hermes session title (from /title command)
      3. per-session strategy — Hermes session_id ({timestamp}_{hex})
      4. per-repo strategy — git repo root directory name
      5. per-directory strategy — directory basename
      6. global strategy — workspace name
    """
```

**优先级**：`per-repo` > `per-session` > `per-directory` > `global`

### 36.4 Honcho 四策略与 BlueCortexCE Session 对比

| 策略 | Honcho 行为 | BlueCortexCE 等价 |
|------|------------|------------------|
| `per-session` | 每次运行新建 session | 每次 CLI 启动新 session |
| `per-directory` | 同一目录共享 session | 同一 `client_id` 共享 session |
| `per-repo` | 同一 git repo 共享 session | ❌ 无（BlueCortexCE 不感知 git） |
| `global` | 全局单一 session | ❌ 无 |

### 36.5 翻译：旁路型如何借鉴

**Honcho `per-repo` 策略的核心洞察**：同一个 git 仓库内的不同目录应该是**同一个 session**，因为代码上下文是共享的。

**BlueCortexCE 现状**：
- BlueCortexCE 通过 `client_id` 区分不同客户端
- 但**不感知 git 仓库边界**
- 同一个 git repo 的不同子目录被当作不同 session

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **中** | BlueCortexCE 增加 `git_repo_name` 作为 session 路由因子 | 类似 Honcho，在 `resolve_session_name` 中增加 git repo 检测 |
| **低** | BlueCortexCE 增加 `session_strategy` 配置 | 支持 `per-client`（当前）/`per-repo`/`per-directory` |

**注意**：作为旁路型系统，BlueCortexCE 需要消费方传递 `cwd`，然后内部调用 `git rev-parse --show-toplevel` 获取 repo name。

---

## 37. Holographic Entity Extraction 深度分析（v4.3 新增）

> **文件**: `plugins/memory/holographic/store.py:76-92`（正则定义），`store.py:394-430`（`_extract_entities` + `_resolve_entity`）
> **本节为 v4.3 新增**，深入分析 Holographic 的正则规则实体提取和别名解析算法。

### 37.1 四套正则规则

```python
# store.py:76-92
# Entity extraction patterns
_RE_CAPITALIZED  = re.compile(r'\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b')
_RE_DOUBLE_QUOTE = re.compile(r'"([^"]+)"')
_RE_SINGLE_QUOTE = re.compile(r"'([^']+)'")
_RE_AKA          = re.compile(
    r'(\w+(?:\s+\w+)*)\s+(?:aka|also known as)\s+(\w+(?:\s+\w+)*)',
    re.IGNORECASE,
)
```

| 规则 | 正则 | 匹配示例 | 提取内容 |
|------|------|----------|----------|
| `_RE_CAPITALIZED` | `\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b` | `"John Doe"`, `"BlueCortexCE"` | 大写开头的多词短语 |
| `_RE_DOUBLE_QUOTE` | `"([^"]+)"` | `"Python"`, `"my project"` | 双引号内的任意内容 |
| `_RE_SINGLE_QUOTE` | `'([^']+)'` | `'pytest'`, `'config'` | 单引号内的任意内容 |
| `__RE_AKA` | `aka\|also known as` | `"Guido aka BDFL"` | 别名对（提取两个实体） |

### 37.2 `_extract_entities` 算法

```python
# store.py:394-425
def _extract_entities(self, text: str) -> list[str]:
    seen: set[str] = set()
    candidates: list[str] = []

    def _add(name: str) -> None:
        stripped = name.strip()
        if stripped and stripped.lower() not in seen:
            seen.add(stripped.lower())
            candidates.append(stripped)

    for m in _RE_CAPITALIZED.finditer(text):
        _add(m.group(1))
    for m in _RE_DOUBLE_QUOTE.finditer(text):
        _add(m.group(1))
    for m in _RE_SINGLE_QUOTE.finditer(text):
        _add(m.group(1))
    for m in _RE_AKA.finditer(text):
        _add(m.group(1))
        _add(m.group(2))

    return candidates
```

**关键特性**：
1. **顺序保留**（first-seen order）— 按发现顺序返回，不是字母序
2. **大小写去重**（case-insensitive dedup）— `"John Doe"` 和 `"john doe"` 被视为相同
3. **AKA 双重提取** — `"Guido aka BDFL"` 同时提取 `"Guido"` 和 `"BDFL"` 作为两个独立实体
4. **大小写保留** — `seen` 用 `lower()` 做去重，但 `candidates` 保留原始大小写

### 37.3 `_resolve_entity` 算法：别名解析

```python
# store.py:430-455
def _resolve_entity(self, name: str) -> int:
    # 1. Exact name match (case-insensitive with LIKE)
    row = self._conn.execute(
        "SELECT entity_id FROM entities WHERE name LIKE ?", (name,)
    ).fetchone()
    if row is not None:
        return int(row["entity_id"])

    # 2. Search aliases — comma-separated, bounded LIKE
    alias_row = self._conn.execute(
        """
        SELECT entity_id FROM entities
        WHERE ',' || aliases || ',' LIKE '%,' || ? || ',%'
        """,
        (name,),
    ).fetchone()
    if alias_row is not None:
        return int(alias_row["entity_id"])

    # 3. Create new entity
    cur = self._conn.execute(
        "INSERT INTO entities (name) VALUES (?)", (name,)
    )
    self._conn.commit()
    return int(cur.lastrowid)
```

**别名存储格式**：`aliases` 列存储为逗号分隔的字符串，如 `"guido,bdfl,van rossum"`

**查询技巧**：
- 用 `',' || aliases || ','` 包裹前后加逗号
- 查询用 `%,' || ? || ',%` 精确匹配边界
- 防止部分匹配：`"guido"` 不会匹配 `" guidob"` 或 `"guido2"`

### 37.4 实体 → 事实链接（Fact-Entity Graph）

```python
# store.py:458-468
def _link_fact_entity(self, fact_id: int, entity_id: int) -> None:
    """Insert into fact_entities, silently ignore if link already exists."""
    self._conn.execute(
        """
        INSERT OR IGNORE INTO fact_entities (fact_id, entity_id)
        VALUES (?, ?)
        """,
        (fact_id, entity_id),
    )
    self._conn.commit()
```

**`INSERT OR IGNORE`**：幂等性写入，重复链接不报错。

### 37.5 与 BlueCortexCE 对比

| 维度 | Holographic Entity Extraction | BlueCortexCE |
|------|------------------------------|--------------|
| 提取规则 | 4 套正则（CAPITALIZED/DOUBLE/SINGLE/AKA） | ❌ 无（Observation 不做实体提取） |
| 去重策略 | 大小写不敏感 + 顺序保留 | ❌ 无 |
| 别名解析 | 逗号分隔字符串 + bounded LIKE | ❌ 无 |
| 存储结构 | entities 表 + fact_entities 链接表 | ❌ 无（Observation 是扁平的） |
| 图谱构建 | 实体-事实二部图，支持 `reason()` 多实体 JOIN | ❌ 无 |
| 别名注册 | 在 `add_alias` 时添加到 entities 表 | ❌ 无 |

### 37.6 翻译：旁路型如何借鉴

**核心差距**：Holographic 的实体-事实图谱支持**多实体 JOIN 查询**（`reason()` 方法），这是纯向量搜索无法实现的。

**高优先级借鉴**：

| 建议 | 说明 |
|------|------|
| BlueCortexCE 增加 Observation 分类标签 | 类似 Holographic 的 category，支持"preference"/"decision"/"fact"/"other" |
| BlueCortexCE 增加实体引用字段 | Observation 可以声明关联的实体（`entities: ["BlueCortexCE", "Claude"]`） |
| BlueCortexCE 增加实体别名解析 | 类似 `_resolve_entity`，支持 `"Guido"` → entity_id |

**中优先级借鉴**：
- BlueCortexCE 增加"实体图谱"扩展：额外的 `entities` 表，支持多实体 JOIN 查询
- 作用：实现类似 `reason(["peppi", "backend"])` 的多实体组合查询

**注意**：这些是 Holographic 作为**内置型**系统的优势——它的实体图谱与 Agent 的推理过程紧密耦合。旁路型系统要实现类似能力，需要消费方在调用 API 时主动传递实体信息。

---

## 38. 待进一步确认（v4.3 更新）

### 38.1 本轮已确认项目

1. ✅ ~~Honcho write_frequency 机制~~ — **已验证完整实现**（async 后台线程 + turn 同步 + session flush_all + int 批量）
2. ✅ ~~Holographic contradiction detection~~ — **已验证**（`retrieval.py:343` O(n²) 比较、entity_overlap < 0.3 跳过、500 条上限）
3. ✅ ~~Holographic reason() 代数检索~~ — **已验证**（多实体 AND 语义，HRR bind/unbind，`retrieval.py:260`）
4. ✅ ~~Holographic Entity Extraction 算法~~ — **已详细分析**（4 套正则 + 大小写去重 + 顺序保留 + 别名解析）
5. ✅ ~~多模态记忆澄清~~ — **已澄清**：Hermes 无多模态记忆存储，vision tools 仅做分析
6. ✅ ~~Honcho Dialectic 完整 prompt~~ — **无法验证**（云端 API，本地无 prompt 模板）
7. ✅ ~~Hindsight knowledge graph~~ — **已澄清**：全部委托给 Hindsight 云端服务，local mode 仅启动 daemon
8. ✅ ~~Honcho per-repo 策略~~ — **已验证**：`_git_repo_name` 通过 `git rev-parse --show-toplevel` 获取 repo root basename
9. ✅ ~~MemoryProvider Hooks 数量~~ — **修正**：共 7 个 hooks（不是 6 个），新增 `on_pre_compress`
10. ✅ ~~on_pre_compress 实现状态~~ — **已确认**：所有 Provider 都未实现（返回空字符串）
11. ✅ ~~on_delegation 实现状态~~ — **已确认**：所有 Provider 都未实现（基类 no-op）

### 38.2 仍待确认项目

1. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
2. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（需要看 honcho SDK 源码）
3. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知（需要看 mem0 文档）
4. **Holographic trust decay 半衰期算法** — `retrieval.py` 中的 `temporal_decay_half_life` 具体计算公式
5. **Honcho `seed_ai_identity`** — 确认是手动 API 调用还是自动集成（之前分析说是手动）

---

## 39. RetainDB 超时兜底 + Supermemory 轻量分类（v4.4 新增）

> **文件**: `plugins/memory/retaindb/__init__.py:1-400`（RetainDB），`plugins/memory/supermemory/__init__.py:155-260`（Supermemory）
> **本节为 v4.4 新增**，分析 RetainDB 的双重 API 兜底 + SQLite crash-safe write queue，以及 Supermemory 的纯正则分类。

### 39.1 RetainDB 的双重 API 路由兜底

**文件**: `plugins/memory/retaindb/__init__.py:220-270`

RetainDB API 的一个显著特点：**所有写操作都有兜底路由**。当主路由失败时，自动尝试备用路由：

```python
def add_memory(self, user_id: str, session_id: str, content: str,
               memory_type: str = "factual", importance: float = 0.7) -> dict:
    try:
        # Primary: /v1/memory (singular)
        return self.request("POST", "/v1/memory", json_body={
            "project": self.project, "content": content, "memory_type": memory_type,
            "user_id": user_id, "session_id": session_id, "importance": importance,
            "write_mode": "sync",
        }, timeout=5.0)
    except Exception:
        # Fallback: /v1/memories (plural) — different endpoint
        return self.request("POST", "/v1/memories", json_body={
            "project": self.project, "content": content, "memory_type": memory_type,
            "user_id": user_id, "session_id": session_id, "importance": importance,
        }, timeout=5.0)

def delete_memory(self, memory_id: str) -> dict:
    try:
        # Primary: /v1/memory/{id}
        return self.request("DELETE", f"/v1/memory/{quote(memory_id, safe='')}", timeout=5.0)
    except Exception:
        # Fallback: /v1/memories/{id}
        return self.request("DELETE", f"/v1/memories/{quote(memory_id, safe='')}", timeout=5.0)
```

**这说明**：RetainDB 的 API 可能经历了版本变迁（singular vs plural endpoints），Hermes 通过 try/except 优雅地兼容了两个版本。

**与 BlueCortexCE 对比**：BlueCortexCE 目前没有类似的 API 版本兼容兜底机制。

### 39.2 RetainDB SQLite Write-Behind Queue — Crash-Safe Async Ingest

**文件**: `plugins/memory/retaindb/__init__.py:320-470`

RetainDB 实现了一个**持久化的 SQLite write-behind queue**，这是目前所有 Provider 中最完善的 crash-safe 机制：

```python
class _WriteQueue:
    """SQLite-backed async write queue. Survives crashes — pending rows replay on startup."""

    def __init__(self, client: _Client, db_path: Path):
        # 1. 初始化 SQLite DB（存储 pending rows）
        self._init_db()
        # 2. 启动后台线程
        self._thread.start()
        # 3. CRASH RECOVERY: replay 任何上次未完成的 rows
        for row_id, user_id, session_id, msgs_json in self._pending_rows():
            self._q.put((row_id, user_id, session_id, json.loads(msgs_json)))
```

**SQLite Schema**：
```python
conn.execute("""CREATE TABLE IF NOT EXISTS pending (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT, session_id TEXT, messages_json TEXT,
    created_at TEXT, last_error TEXT
)""")
```

**关键特性**：

| 特性 | 实现 |
|------|------|
| Crash recovery | 启动时 replay `_pending_rows()` 中所有未完成的 rows |
| Thread-local DB | 每个线程独立 SQLite connection，避免锁竞争 |
| 重试机制 | 失败后 sleep 再重试（无死循环保护） |
| Timeout | 30s SQLite timeout |
| Shutdown | `join(timeout=10)` 等待线程结束 |

**与 Honcho async write 对比**：

| 维度 | RetainDB `_WriteQueue` | Honcho `_async_writer_loop` |
|------|----------------------|---------------------------|
| 持久化 | ✅ SQLite pending 表 | ❌ 仅内存 queue |
| Crash recovery | ✅ 启动时 replay | ❌ 丢失 |
| 重试策略 | sleep + retry | retry once + drop |
| Queue 类型 | SQLite + Python queue | Python queue.Queue |

### 39.3 RetainDB 7 Memory Types + Agent Self-Model

**文件**: `plugins/memory/retaindb/__init__.py:101-145`

RetainDB 比其他 Provider 定义了更精细的 memory types：

```python
REMEMBER_SCHEMA = {
    "memory_type": {
        "enum": ["factual", "preference", "goal", "instruction", "event", "opinion"],
        # 注意：比 Honcho 多 3 个（goal, instruction, opinion）
    },
    "importance": {"type": "number", "description": "Importance 0-1 (default: 0.7)"},
}
```

**6 种 Memory Type**：

| Type | 含义 | 示例 |
|------|------|------|
| `factual` | 事实性知识 | "用户使用 macOS" |
| `preference` | 用户偏好 | "用户偏好用 TypeScript" |
| `goal` | 目标/意图 | "用户想完成登录功能" |
| `instruction` | 指令/约束 | "用户要求所有 API 必须有鉴权" |
| `event` | 事件记录 | "上次讨论了性能优化" |
| `opinion` | 观点/态度 | "用户认为当前架构太复杂" |

**Agent Self-Model 能力**：

```python
def seed_agent_identity(self, agent_id: str, content: str, source: str = "soul_md") -> dict:
    """Write agent's SOUL.md-like content to RetainDB for self-modeling."""
    return self.request("POST", f"/v1/memory/agent/{agent_id}/seed", json_body={
        "project": self.project, "content": content, "source": source,
    }, timeout=20.0)

def get_agent_model(self, agent_id: str) -> dict:
    """Retrieve agent's persona/instructions from RetainDB."""
    return self.request("GET", f"/v1/memory/agent/{agent_id}/model", ...)
```

**设计意图**：Agent 可以将自己的 SOUL.md 种子化到 RetainDB，并在每次启动时检索回来——实现 Agent 的"自我记忆"。

### 39.4 Supermemory 纯正则分类算法

**文件**: `plugins/memory/supermemory/__init__.py:158-175`

Supermemory 使用**最简单的正则规则**进行 memory type 分类：

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

**分类逻辑**：

| 类别 | 关键词 | 思想 |
|------|--------|------|
| `preference` | prefer/like/love/hate/want | 表达喜好的句子 |
| `decision` | decided/will use/going with | 已做出的决策 |
| `fact` | is/are/has/have | 描述状态的句子 |
| `other` | 其他 | 兜底 |

**设计评价**：
- ✅ **零成本**：无 LLM 调用，实时分类
- ✅ **确定性**：不会因 prompt 变化而变化
- ⚠️ **准确率有限**：简单的正则无法理解复杂语义
- ⚠️ **无法处理混合类型**：一个句子包含多种类型时只能归一类

**与 BlueCortexCE 对比**：BlueCortexCE 的 Observation 目前没有分类维度。Supermemory 的正则方法可以低成本借鉴。

### 39.5 Supermemory 三层去重机制

**文件**: `plugins/memory/supermemory/__init__.py:189-220`

```python
def _deduplicate_recall(static_facts: list, dynamic_facts: list,
                         search_results: list) -> tuple[list, list, list]:
    seen = set()  # 全局去重集合（基于字符串）
    out_static, out_dynamic, out_search = [], [], []

    for fact in static_facts or []:
        if fact and fact not in seen:
            seen.add(fact)
            out_static.append(fact)

    for fact in dynamic_facts or []:
        if fact and fact not in seen:
            seen.add(fact)
            out_dynamic.append(fact)

    for item in search_results or []:
        memory = item.get("memory", "")
        if memory and memory not in seen:
            seen.add(memory)
            out_search.append(item)
```

**三层去重的目的**：
- **static facts**：用户明确记录的持久事实（长期记忆）
- **dynamic facts**：本次会话中产生的上下文（短期记忆）
- **search results**：向量搜索结果（可能有重复）

**去重策略**：基于字符串的精确匹配（`fact not in seen`）。这比 Holographic 的 MD5 hash 去重更简单直接。

**格式输出示例**：
```
<supermemory-context>
The following is background context from long-term memory. Use it silently when relevant.

## User Profile (Persistent)
- 用户偏好使用 TypeScript
- 用户使用 macOS 系统

## Recent Context
- 当前在开发登录功能

## Relevant Memories
- [2h ago] [85%] 用户上次讨论了性能优化
- [1d ago] [72%] 用户的项目使用 Next.js
</supermemory-context>
```

### 39.6 OpenViking Filesystem-Style URI Abstraction

**文件**: `plugins/memory/openviking/__init__.py:1-100`

OpenViking 的核心特点是**filesystem-style URI 抽象**：

```python
TOOLS = [
    "viking_search",   # 语义搜索，返回 viking:// URIs
    "viking_read",    # 读取 viking:// URI 指定深度的内容
    "viking_browse",  # 文件系统式浏览（ls/tree/stat）
    "viking_remember", # 记忆提取（session commit 时）
    "viking_add_resource", # 摄入 URL/doc 到知识库
]
```

**viking:// URI 示例**：
- `viking://resources/docs/` — 浏览 resources/docs/ 目录
- `viking://user/memories/` — 浏览用户记忆目录
- `viking://user/memories/2024-03-15` — 浏览特定日期的记忆

**读取深度控制**（`viking_read`）：
- `abstract`：摘要级别
- `overview`：概览级别
- `full`：完整内容

**设计思想**：将记忆抽象为文件系统，Agent 可以像浏览文件一样浏览记忆。这比传统 RAG 的"黑盒搜索"更直观。

### 39.7 翻译：旁路型如何借鉴

**核心发现总结**：

| Provider | 核心创新 | 对 BlueCortexCE 的借鉴 |
|----------|----------|----------------------|
| **RetainDB** | SQLite write-behind queue + crash replay | **高优先级**：BlueCortexCE 增加 SQLite 本地 pending queue，服务崩溃时不丢失写入 |
| **RetainDB** | 双重 API 兜底路由 | **中优先级**：BlueCortexCE 的 SDK 增加 API 版本兼容兜底 |
| **RetainDB** | 6 种 memory type + importance | **高优先级**：BlueCortexCE 增加 memory type 和 importance 字段 |
| **RetainDB** | Agent self-model (seed_agent_identity) | **高优先级**：BlueCortexCE 的 SDK 提供"Agent 自我记忆"支持 |
| **Supermemory** | 纯正则分类（零成本） | **中优先级**：BlueCortexCE 增加 observation 的自动分类（preference/decision/fact/other） |
| **Supermemory** | 三层去重机制 | **高优先级**：BlueCortexCE 的 `/api/context/generate` 增加 static/dynamic/search 去重 |
| **Supermemory** | `<supermemory-context>` XML 包裹 | **低优先级**：BlueCortexCE 可以用类似格式包裹上下文注入 |
| **OpenViking** | Filesystem-style URI | **低优先级**：纯设计思想，BlueCortexCE 不需要模拟文件系统 |

**最高优先级借鉴**：
1. **RetainDB 的 SQLite crash-safe write queue** — BlueCortexCE 的 Java SDK（JS/Go/Python）可以实现本地 pending queue，避免网络抖动时丢失写入
2. **RetainDB 的 6 种 memory type + importance** — BlueCortexCE 的 Observation 增加分类维度
3. **Supermemory 的三层去重** — BlueCortexCE 的 context generate 增加去重逻辑

---

## 40. 待进一步确认（v4.4 更新）

### 40.1 本轮已确认项目

1. ✅ ~~RetainDB 双重 API 兜底路由~~ — **已验证**（`/v1/memory` → `/v1/memories` fallback）
2. ✅ ~~RetainDB SQLite write-behind queue~~ — **已验证**（crash replay on startup，thread-local connection）
3. ✅ ~~RetainDB 6 种 memory type~~ — **已验证**（factual/preference/goal/instruction/event/opinion）
4. ✅ ~~RetainDB Agent self-model~~ — **已验证**（`seed_agent_identity` + `get_agent_model` API）
5. ✅ ~~Supermemory 纯正则分类~~ — **已验证**（4 类：preference/decision/fact/other）
6. ✅ ~~Supermemory 三层去重~~ — **已验证**（static/dynamic/search 全局去重）
7. ✅ ~~Supermemory `<supermemory-context>` 包裹格式~~ — **已验证**（XML 格式上下文注入）
8. ✅ ~~OpenViking filesystem-style URI~~ — **已验证**（viking:// 前缀浏览记忆）

### 40.2 仍待确认项目

1. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
2. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（需要看 honcho SDK 源码）
3. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知（需要看 mem0 文档）
4. **Holographic trust decay 半衰期算法** — `retrieval.py` 中的 `temporal_decay_half_life` 具体计算公式（已在 section 26 确认公式：0.5^(age_days/half_life)）
5. **Honcho `seed_ai_identity`** — 确认是手动 API 调用还是自动集成（已确认是手动 API）



---

## 41. Holographic `memory_banks` 优化路径 — Category-Level HRR Bundle 加速 Algebraic Retrieval（v4.5 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:143-160`（`reason()` 方法中的 bank 使用），`store.py:494-530`（`_rebuild_bank` 机制）
> **本节为 v4.5 新增**，澄清 `memory_banks` 表在 `reason()` 中的实际使用路径——这是一个**被文档遗漏但确实存在的优化**。

### 41.1 澄清：`memory_banks` 确实被使用

之前 v3.x/v4.x 版本的分析中，提到 `memory_banks` 是"已生成但 `search_facts` 未使用"的悬空数据。这个说法**不准确**——`memory_banks` 在 `reason()` 方法中被用于**代数检索的优化**。

### 41.2 `reason()` 中的 Bank 优化路径

```python
# retrieval.py:143-160
def reason(self, entities: list[str], category: str | None = None, limit: int = 10):
    # ...
    role_entity = hrr.encode_atom("__hrr_role_entity__", self.hrr_dim)
    entity_vec = hrr.encode_atom(entity.lower(), self.hrr_dim)
    probe_key = hrr.bind(entity_vec, role_entity)

    # Try category-specific bank first, then all facts
    if category:
        bank_name = f"cat:{category}"
        bank_row = conn.execute(
            "SELECT vector FROM memory_banks WHERE bank_name = ?",
            (bank_name,),
        ).fetchone()
        if bank_row:
            bank_vec = hrr.bytes_to_phases(bank_row["vector"])
            # 优化：用 bank vector 直接 unbinding，而非逐个 fact 计算
            extracted = hrr.unbind(bank_vec, probe_key)
            # 用 extracted signal 对所有 facts 打分
            return self._score_facts_by_vector(
                extracted, category=category, limit=limit
            )

    # Fallback：没有 bank 或没有 category → 逐个 fact 计算
    # (每条 fact 都需要 bind/unbind，O(n))
```

### 41.3 优化原理

**无优化时（O(n) fact scoring）**：
```
对每个 entity：
  对每个 fact：
    unbinding(fact_vec, probe_key) → residual
    similarity(residual, content_vec) → score
```

**有 bank 优化时（2 步）**：
```
Step 1: unbinding(bank_vec, probe_key) → extracted_category_signal（只做 1 次）
Step 2: 对每个 fact：
    similarity(fact_vec, extracted) → score（只用一次相似度计算，无 bind/unbind）
```

**为什么这个优化有效**：
- `bank_vec = bundle(*all_category_facts)` 是 category 内所有 facts 的**叠加向量**
- `unbinding(bank_vec, probe_key)` 得到的是"category 内所有 facts 中与 entity 相关的信号"的**统计聚合**
- `similarity(fact_vec, extracted)` 度量的是：这条 fact 的向量与 category 整体相关信号的**相似度**
- 这避免了**对每条 fact 都执行一次完整的 bind/unbind** 操作

### 41.4 `_rebuild_bank` 触发时机

```python
# store.py:183  — add_fact 时触发
self._rebuild_bank(category)

# store.py:294-298 — add_alias 时（entity alias 变化可能影响 fact 与 entity 的关联）
if changed:
    for cat in self._categories_for_entity(entity_id):
        self._rebuild_bank(cat)

# store.py:316 — set_trust 时（trust 变化影响 fact vector 权重）
self._rebuild_bank(row["category"])

# store.py:533 — rebuild_all 时（全量重建）
for category in categories:
    self._rebuild_bank(category)
```

**Bank 重建触发条件**：
1. `add_fact` — 新 fact 加入 category
2. `add_alias` — entity 别名变化（可能影响 fact 的 HRR 编码）
3. `set_trust` — trust score 变化（HRR 向量本身不含 trust，但 bank 是 bundle of fact vectors）
4. `rebuild_all` — 全量重建

### 41.5 Bank Vector 的 SNR 保护

```python
# store.py:514-516
hrr.snr_estimate(self.hrr_dim, fact_count)
# 输出 warning if fact_count > dim/4（容量警告）
```

**Bank 的容量上限与单个 fact HRR 相同**：`dim/4 ≈ 256` 个 facts。超过时 SNR 下降，bundled 向量的信息密度降低。

### 41.6 与 BlueCortexCE 对比

| 维度 | Holographic memory_banks | BlueCortexCE |
|------|-------------------------|--------------|
| 用途 | `reason()` 代数检索优化 | ❌ 无 |
| 存储 | SQLite `memory_banks` 表 | N/A |
| 重建触发 | add_fact/add_alias/set_trust | N/A |
| 容量 | dim/4 ≈ 256 facts/bank | N/A |
| 计算 | `bundle(*fact_vectors)` | N/A |

### 41.7 翻译：旁路型如何借鉴

**这个优化对 BlueCortexCE 无直接意义**（HRR 代数是 Hermes 特有技术，pgvector 不支持），但揭示了一个**通用优化思想**：

**"预计算共享查询结果"**：
- 如果某类查询被频繁执行，可以预先计算并缓存中间结果
- 查询时只计算一次"差值"（类似 `unbind(bank, key)`），而非每次都全量计算
- 对于 BlueCortexCE：如果某个 `user_id` 的所有 observation 经常被组合查询，可以预计算一个"用户记忆向量"作为缓存

---

## 42. Holographic `related()` 方法 — Structural Adjacency 邻接发现（v4.5 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:196-258`
> **本节为 v4.5 新增**，分析 Hermes 独有的 `related()` 方法——**结构邻接发现**，与 `probe()` 的"直接关联"形成互补。

### 42.1 `probe()` vs `related()` 的根本区别

| 方法 | 查询语义 | 编码方式 | 找到的内容 |
|------|----------|----------|-----------|
| `probe(entity)` | 找**关于**该实体的 facts | `bind(entity, ROLE_ENTITY)` → 在 fact 结构中"解开" | facts **直接描述**该实体 |
| `related(entity)` | 找**与**该实体**相关的** facts | `encode_atom(entity)` **裸原子**（无 role binding） | facts **提到过**该实体，但不一定关于它 |

**`related()` 的直觉**：
- 如果 fact A 提到了 entity X（比如"用 pip 安装了 pytest"），X 绑定在 fact 的 entity role 中
- 如果 fact B 也提到了 X，B 也是 related
- 如果 X 与 Y 在同一 fact 中共同出现，X 和 Y 也是 related

### 42.2 `related()` 实现

```python
# retrieval.py:220-258
def related(self, entity: str, category: str | None = None, limit: int = 10):
    if not hrr._HAS_NUMPY:
        return self.search(entity, category=category, limit=limit)

    # 关键：裸原子编码（无 role binding）
    entity_vec = hrr.encode_atom(entity.lower(), self.hrr_dim)

    # 获取所有有 HRR 向量的 facts
    rows = conn.execute(
        f"""SELECT ... FROM facts WHERE hrr_vector IS NOT NULL {where}"""
    ).fetchall()

    scored = []
    for row in rows:
        fact = dict(row)
        fact_vec = hrr.bytes_to_phases(fact.pop("hrr_vector"))
        # 用裸原子直接与 fact 向量做相似度
        # （fact 向量 = bind(content, ROLE_CONTENT) + Σbind(entity, ROLE_ENTITY)）
        # 裸原子可以"部分匹配" fact 向量中的任意 role（content 或 entity）
        sim = hrr.similarity(entity_vec, fact_vec)  # 直接相似度，非 unbinding
        fact["score"] = (sim + 1.0) / 2.0 * fact["trust_score"]
        scored.append(fact)
```

**关键代码**：
```python
sim = hrr.similarity(entity_vec, fact_vec)  # 非 unbinding！
```

裸原子 `entity_vec` 与完整的 `fact_vec` 直接做相似度计算。这意味着：
- `entity_vec` 可以在 fact 向量的**任意叠加分量**上匹配
- 不管 entity 是出现在 content 部分还是 entity 部分，都能匹配到

### 42.3 与 `probe()` 的算法对比

```python
# probe（直接关联）
probe_key = hrr.bind(entity_vec, role_entity)  # 绑定到 ENTITY role
residual = hrr.unbind(fact_vec, probe_key)     # 解开 entity 部分
sim = hrr.similarity(residual, content_vec)     # 比较解开后的 content 信号

# related（邻接发现）
entity_vec = hrr.encode_atom(entity.lower(), self.hrr_dim)  # 裸原子，无 role
sim = hrr.similarity(entity_vec, fact_vec)           # 直接与完整 fact 向量比较
```

**对比总结**：

| 维度 | probe() | related() |
|------|---------|-----------|
| Role binding | ✅ 有（bind + unbind） | ❌ 无（裸原子） |
| 找到 | 关于 entity 的 facts | 提到过 entity 的 facts |
| 语义 | 精确关联 | 邻接相关 |
| 适用场景 | "X 是什么/做了什么" | "X 与什么有关联" |

### 42.4 与 BlueCortexCE 对比

| 维度 | Holographic related() | BlueCortexCE |
|------|---------------------|--------------|
| 语义 | 邻接发现（提到了就算） | ❌ 无 |
| 实现 | 裸原子直接相似度 | N/A |
| 用途 | "X 和什么相关/一起出现" | ❌ 无 |

### 42.5 翻译：旁路型如何借鉴

**这个能力在旁路型架构下难以直接实现**（HRR 代数），但揭示了一个**重要的认知区别**：

**"直接关联" vs "邻接相关"**：
- `probe` = "关于 X 的事实"（X 是 subject/predicate）
- `related` = "提到 X 的事实"（X 出现在任意上下文中）

**BlueCortexCE 的实际意义**：
- 如果 BlueCortexCE 的 Observation 带有实体标签，可以实现类似的"邻接发现"：
  - 搜索"提到过 X 的 observation"
  - 与"与 X 相关的 observation"可能不同
- **但优先级低**：普通向量搜索（余弦相似度）已经能近似"邻接相关"的效果

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

