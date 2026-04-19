
## 54. 内置 Memory Tool — 有界精选 + 冻结快照机制（v4.9 新增）

> **文件**: `tools/memory_tool.py:1-584`
> **本节为 v4.9 新增**，分析 Hermes 内置 `memory` 工具的记忆生命周期管理机制（与外部 Provider 并行的独立系统）。

### 54.1 两套记忆系统的并存架构

Hermes 有两套并行的记忆系统：

| 系统 | 存储位置 | 容量 | 生命周期管理 | 外部同步 |
|------|----------|------|-------------|---------|
| **内置 memory** | `MEMORY.md` / `USER.md` | 2,200 / 1,375 chars（硬限制） | 有界精选 + Agent 显式删除 | `on_memory_write` bridge |
| **外部 Provider** | Honcho/Holographic 等 | Provider 决定 | Provider 决定 | 双向 sync |

**内置 memory 特点**：
- **文件持久化**：`hermes home` 下的 `memories/MEMORY.md` 和 `memories/USER.md`
- **始终开启**：不依赖外部 Provider 配置
- **两段式状态**：冻结快照（系统 prompt 用）+ 实时状态（工具响应用）

### 54.2 有界精选（Bounded Curation）策略

```python
# tools/memory_tool.py:144-145
def __init__(self, memory_char_limit: int = 2200, user_char_limit: int = 1375):
    self.memory_entries: List[str] = []
```

**容量限制**：

| Store | 硬限制 | 说明 |
|-------|--------|------|
| `memory` | 2,200 chars | Agent 的个人笔记（环境事实、项目惯例、工具细节、经验教训） |
| `user` | 1,375 chars | 用户画像（偏好、交流风格、工作流习惯） |

**为什么这样设计**：
- 字符限制而非 token 限制——因为 char count 对模型是稳定的
- Agent 必须在容量内精选——强制质量而非数量
- **超过容量时拒绝写入**（`add` 返回错误，要求先删除或替换）

```python
# tools/memory_tool.py:252-262
if new_total > limit:
    current = self._char_count(target)
    return {
        "success": False,
        "error": (
            f"Memory at {current:,}/{limit:,} chars. "
            f"Adding this entry ({len(content)} chars) would exceed the limit. "
            f"Replace or remove existing entries first."
        ),
        ...
    }
```

### 54.3 冻结快照模式（Frozen Snapshot Pattern）

**核心设计**：mid-session 写入**不改变**系统 prompt 注入的内容。

```python
# tools/memory_tool.py:130-136
# _system_prompt_snapshot: frozen at load time, used for system prompt injection.
# Never mutated mid-session. Keeps prefix cache stable.
self._system_prompt_snapshot: Dict[str, str] = {"memory": "", "user": ""}

def load_from_disk(self):
    # Capture frozen snapshot for system prompt injection
    self._system_prompt_snapshot = {
        "memory": self._render_block("memory", self.memory_entries),
        "user": self._render_block("user", self.user_entries),
    }

def format_for_system_prompt(self, target: str) -> Optional[str]:
    # Returns the state captured at load_from_disk() time, NOT the live state.
    return self._system_prompt_snapshot.get(target, "")
```

**生命周期**：
1. **Session 启动时**：加载 `MEMORY.md` + `USER.md`，捕获冻结快照
2. **Mid-session 写入**：更新实时状态 + 磁盘，但不更新快照
3. **Session 结束时**：快照不变
4. **下次 Session 启动时**：重新加载磁盘（包含 mid-session 写入）

**好处**：
- 系统 prompt 全文在 session 内稳定 → prefix cache 不失效
- mid-session 写入即时持久化到磁盘 → crash 不丢数据
- 工具响应始终显示最新状态 → Agent 能看到自己写入的结果

### 54.4 生命周期管理：Agent 显式删除

**Hermes 没有自动遗忘机制**。所有遗忘都是 Agent 显式调用 `memory remove`。

**工具 schema 明确告知何时删除**：

```python
MEMORY_SCHEMA = {
    "description": (
        "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO "
        "state to memory; use session_search to recall those from past transcripts.\n"
        "SKIP: trivial/obvious info, things easily re-discovered, raw data dumps, and temporary task state."
    ),
    ...
}
```

**何时记忆 vs 何时遗忘的判断**：

| 应该记忆 | 不应该记忆 |
|----------|-----------|
| 用户纠正 / "remember this" | Session 进展 / 已完成的工作日志 |
| 用户偏好（名字、角色、编码风格） | 临时 TODO 状态 |
| 环境事实（OS、工具、项目结构） | 容易重新发现的信息 |
| 特定惯例 / API 怪癖 | 原始数据 dump |
| 有用的稳定事实 | 一次性任务 |

**删除操作**：

```python
# tools/memory_tool.py:303-330
def remove(self, target: str, old_text: str) -> Dict[str, Any]:
    """Remove the entry containing old_text substring."""
    matches = [(i, e) for i, e in enumerate(entries) if old_text in e]
    # 支持模糊匹配（substring），但多匹配时要求更具体
    if len(unique_texts) > 1:
        return {
            "success": False,
            "error": f"Multiple entries matched '{old_text}'. Be more specific.",
            "matches": previews,
        }
```

**设计思想**：记忆是 Agent 的**主动决策**，不是系统的被动积累。Agent 需要自己判断什么值得保留、什么应该删除。

### 54.5 安全性：Prompt 注入扫描

内置 memory 的内容会注入系统 prompt，因此 Hermes 在写入前做严格的安全扫描：

```python
# tools/memory_tool.py:61-100
_MEMORY_THREAT_PATTERNS = [
    # Prompt injection
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'you\s+are\s+now\s+', "role_hijack"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    (r'system\s+prompt\s+override', "sys_prompt_override"),
    # Exfiltration via curl/wget with secrets
    (r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)', "exfil_curl"),
    # Invisible unicode injection
    '\u200b', '\u200c', '\u200d', '\ufeff', ...
]

def _scan_memory_content(content: str) -> Optional[str]:
    """Scan memory content for injection/exfil patterns. Returns error string if blocked."""
    # Check invisible unicode
    for char in _INVISIBLE_CHARS:
        if char in content:
            return f"Blocked: content contains invisible unicode character U+{ord(char):04X}."
    # Check threat patterns
    for pattern, pid in _MEMORY_THREAT_PATTERNS:
        if re.search(pattern, content, re.IGNORECASE):
            return f"Blocked: content matches threat pattern '{pid}'."
```

### 54.6 原子写入：跨 Session 并发安全

多 session 可能并发写入同一个 memory 文件。Hermes 用原子 rename 避免竞态：

```python
# tools/memory_tool.py:438-460
@staticmethod
def _write_file(path: Path, entries: List[str]):
    """Atomic temp-file + rename. Readers always see complete file."""
    content = ENTRY_DELIMITER.join(entries) if entries else ""
    fd, tmp_path = tempfile.mkstemp(dir=str(path.parent), suffix=".tmp", prefix=".mem_")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_path, str(path))  # Atomic on same filesystem
    except BaseException:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise
```

同时读取时**重新加载磁盘**（在文件锁下）确保读取到最新内容：

```python
# tools/memory_tool.py:218-220
def add(self, target: str, content: str) -> Dict[str, Any]:
    with self._file_lock(self._path_for(target)):
        # Re-read from disk under lock to pick up writes from other sessions
        self._reload_target(target)
```

### 54.7 与 BlueCortexCE 对比

| 维度 | Hermes 内置 Memory | BlueCortexCE |
|------|------------------|--------------|
| 存储形式 | 文件（MEMORY.md/USER.md） | PostgreSQL + pgvector |
| 容量控制 | 硬字符限制（2,200/1,375） | ❌ 无硬限制（SessionEnd Summary 长度无明确限制） |
| 快照机制 | ✅ 冻结 snapshot，mid-session 写入不更新 | ❌ 无（SessionEnd Summary 一次性生成） |
| 遗忘机制 | Agent 显式 remove | ❌ 无（所有 observation/summary 永久保留） |
| 并发安全 | ✅ 文件锁 + atomic rename | ⚠️ 依赖 PostgreSQL 事务 |
| 注入安全 | ✅ Prompt 注入扫描 | ❌ 无 |
| Entry 标识 | 唯一 delimiter `§` | Observation 无固定 delimiter |
| 系统 prompt 注入 | ✅ 直接注入系统 prompt | ⚠️ `/api/context/generate` API 拉取 |

### 54.8 翻译：旁路型如何借鉴

**核心差距**：Hermes 内置 memory 的"有界精选"模式（硬限制 + Agent 显式删除）在 BlueCortexCE 中完全缺失。

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE SessionEnd Summary 增加长度硬限制 | 建议 2,000-3,000 chars（参考 Hermes 的 2,200/1,375 双限制） |
| **高** | BlueCortexCE Observation 增加 TTL 或 max_entries | 防止 observation 无限积累 |
| **高** | BlueCortexCE `/api/observations` 增加"删除"API | 目前只有 create，没有 delete |
| **中** | BlueCortexCE Observation prompt 增加负面指令 | "Do NOT record: trivial responses, temporary state, one-time tasks" |
| **中** | BlueCortexCE 的"冻结快照"思想 | Session 期间不更新 summary，只有 SessionEnd 才生成/更新 |
| **低** | BlueCortexCE 增加 prompt 注入扫描 | Observation 内容会注入 context，高度敏感 |
| **低** | BlueCortexCE Observation 支持 category | 区分 preference/fact/procedure（参考 Supermarket） |

---

## 55. OpenViking Provider — 分层上下文加载 + Filesystem-Style URI 架构（v5.0 新增）

> **文件**: `plugins/memory/openviking/__init__.py:1-637`
> **本节为 v5.0 新增**，分析 OpenViking Provider 的核心设计：分层上下文（L0/L1/L2）+ URI 抽象。

### 55.1 架构定位

OpenViking（字节跳动/Volcengine）是**唯一同时实现读+写+浏览工具**的 Provider：

| 工具 | 语义 | 层级 |
|------|------|------|
| `viking_search` | 语义搜索（hierarchical directory retrieval） | 查询 |
| `viking_read` | 按 URI 读取内容（abstract/overview/full） | 读取 |
| `viking_browse` | 文件系统风格浏览（tree/ls/stat） | 浏览 |
| `viking_remember` | 显式记忆（带 category hint） | 写入 |
| `viking_add_resource` | 索引 URL/文档 | 资源导入 |

**对比**：其他 Provider（Honcho/Supermemory/RetainDB）只有 search/store/forget，OpenViking 是最接近"完整知识库"的实现。

### 55.2 分层上下文（L0/L1/L2 Tiered Context）

```python
# plugins/memory/openviking/__init__.py:1-40
"""
Capabilities:
  - Tiered context: L0 (~100 tokens), L1 (~2k), L2 (full)
"""
```

**三层上下文设计**：

| 层级 | 容量 | 用途 | 加载方式 |
|------|------|------|---------|
| L0 | ~100 tokens | 快速探针 | 始终加载（系统 prompt block） |
| L1 | ~2,000 tokens | 轻量级上下文 | 按需加载 |
| L2 | Full | 完整内容 | explicit `viking_read level=full` |

**与 BlueCortexCE 对比**：BlueCortexCE 的 `/api/context/generate` 没有分层设计，所有 context 一次性返回（或被 Summary 压缩）。

### 55.3 Filesystem-Style URI 抽象

OpenViking 用 `viking://` URI 表达所有资源：

```
viking://profile/              # 用户 profile
viking://preferences/         # 偏好
viking://entities/            # 实体
viking://events/              # 事件
viking://cases/              # 案例
viking://patterns/           # 模式
```

**6 大记忆类别**（session commit 时自动提取）：
- `profile`：用户身份和背景
- `preferences`：偏好和习惯
- `entities`：实体（人/项目/工具）
- `events`：事件和交互
- `cases`：案例和解决方案
- `patterns`：模式和行为

### 55.4 `system_prompt_block` — 知识库活跃度探测

```python
# plugins/memory/openviking/__init__.py:310-335
def system_prompt_block(self) -> str:
    if not self._client:
        return ""
    try:
        resp = self._client.get("/api/v1/fs/ls", params={"uri": "viking://"})
        result = resp.get("result", [])
        children = len(result) if isinstance(result, list) else 0
        if children == 0:
            return ""  # 空知识库，不显示 block
        return (
            "# OpenViking Knowledge Base\n"
            f"Active. Endpoint: {self._endpoint}\n"
            "Use viking_search to find information, viking_read for details "
            "(abstract/overview/full), viking_browse to explore.\n"
            ...
        )
    except Exception:
        return (
            "# OpenViking Knowledge Base\n"
            f"Active. Endpoint: {self._endpoint}\n"
            "Use viking_search, viking_read, viking_browse, ..."
        )
```

**设计细节**：只有当知识库**有内容时才显示**系统 prompt block（`children == 0 → return ""`）。这是"沉默优于空提示"的设计。

### 55.5 `prefetch` + `queue_prefetch` — 后台预取机制

```python
# plugins/memory/openviking/__init__.py:336-355
def prefetch(self, query: str, *, session_id: str = "") -> str:
    """Return prefetched results from the background thread."""
    if self._prefetch_thread and self._prefetch_thread.is_alive():
        self._prefetch_thread.join(timeout=3.0)
    with self._prefetch_lock:
        result = self._prefetch_result
        self._prefetch_result = ""
    return f"## OpenViking Context\n{result}" if result else ""

def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    """Fire a background search to pre-load relevant context."""
    def _run():
        client = _VikingClient(self._endpoint, self._api_key)
        resp = client.post("/api/v1/search/find", {
            "query": query, "top_k": 5,
        })
        result = resp.get("result", {})
        parts = []
        for ctx_type in ("memories", "resources"):
            for item in result.get(ctx_type, [])[:3]:
                uri = item.get("uri", "")
                abstract = item.get("abstract", "")
                score = item.get("score", 0)
                if abstract:
                    parts.append(f"- [{score:.2f}] {abstract} ({uri})")
        with self._prefetch_lock:
            self._prefetch_result = "\n".join(parts)
    self._prefetch_thread = threading.Thread(target=_run, daemon=True, name="openviking-prefetch")
    self._prefetch_thread.start()
```

**关键行为**：
- `prefetch` 最多等待 3s（`join(timeout=3.0)`），然后返回结果或空字符串
- 结果包含 top-3 memories + top-3 resources（按 score 排序）
- 格式：`## OpenViking Context\n- [0.95] abstract (viking://...)`

### 55.6 `sync_turn` — 非阻塞写入 + 前序 Join

```python
# plugins/memory/openviking/__init__.py:380-412
def sync_turn(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    self._turn_count += 1
    def _sync():
        client = _VikingClient(self._endpoint, self._api_key)
        client.post(f"/api/v1/sessions/{self._session_id}/messages", {
            "role": "user", "content": user_content[:4000],
        })
        client.post(f"/api/v1/sessions/{self._session_id}/messages", {
            "role": "assistant", "content": assistant_content[:4000],
        })
    # Wait for previous sync to finish before starting a new one
    if self._sync_thread and self._sync_thread.is_alive():
        self._sync_thread.join(timeout=5.0)
    self._sync_thread = threading.Thread(target=_sync, daemon=True, name="openviking-sync")
    self._sync_thread.start()
```

**关键行为**：
- 前序 join（最多 5s）：确保按顺序写入
- 每条消息截断到 4000 chars
- daemon thread：进程退出时不等待

### 55.7 `on_session_end` — Commit 触发提取

```python
# plugins/memory/openviking/__init__.py:414-435
def on_session_end(self, messages: List[Dict[str, Any]]) -> None:
    """Commit the session to trigger memory extraction (6 categories)."""
    if self._sync_thread and self._sync_thread.is_alive():
        self._sync_thread.join(timeout=10.0)  # 先等 pending sync
    if self._turn_count == 0:
        return
    self._client.post(f"/api/v1/sessions/{self._session_id}/commit")
```

**提取类别**（commit 时触发）：profile / preferences / entities / events / cases / patterns。

### 55.8 `on_memory_write` — 内置 memory 同步镜像

```python
# plugins/memory/openviking/__init__.py:438-458
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in memory writes to OpenViking as explicit memories."""
    if not self._client or action != "add" or not content:
        return
    def _write():
        client = _VikingClient(self._endpoint, self._api_key)
        client.post(f"/api/v1/sessions/{self._session_id}/messages", {
            "role": "user",
            "parts": [{"type": "text", "text": f"[Memory note — {target}] {content}"}],
        })
    t = threading.Thread(target=_write, daemon=True, name="openviking-memwrite")
    t.start()
```

**关键**：只处理 `action == "add"` 的情况（remove/update 不同步）。

### 55.9 `atexit` 安全网 — 进程退出时 commit

```python
# plugins/memory/openviking/__init__.py:50-68
_last_active_provider: Optional["OpenVikingMemoryProvider"] = None

def _atexit_commit_sessions():
    global _last_active_provider
    if provider := _last_active_provider:
        _last_active_provider = None
        try:
            provider.on_session_end([])  # 触发 commit
        except Exception:
            pass

atexit.register(_atexit_commit_sessions)
```

**设计**：记录最后一个活跃的 provider，进程退出时自动 commit。防止 gateway crash / SIGKILL 时 session 数据丢失。

### 55.10 与 BlueCortexCE 对比

| 维度 | OpenViking | BlueCortexCE |
|------|------------|--------------|
| 分层上下文 | ✅ L0/L1/L2 三层 | ❌ 无分层 |
| URI 抽象 | ✅ viking:// 6 类 | ❌ 无 |
| 知识库活跃检测 | ✅ 空时 return "" | ❌ 始终有输出 |
| 预取机制 | ✅ queue_prefetch 后台搜索 | ❌ 无 |
| 工具完整性 | 5 tools（search/read/browse/remember/add_resource） | 仅 search API |
| 提取类别 | 6 类（profile/preferences/entities/events/cases/patterns） | ❌ 无 category |
| atexit 安全网 | ✅ process exit 时 commit | ❌ 无 |

### 55.11 翻译：旁路型如何借鉴

**Hermes 做法**：OpenViking 通过分层上下文和 URI 抽象实现了"按需加载"能力。Agent 可以先拿 L0 探针，再按需获取完整内容。

**翻译：旁路型如何落地**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE `/api/context/generate` 增加分层响应 | 返回 `context_tiers: {brief: "~100 tokens", standard: "~2k tokens", full: "all"}`，让消费方选择 |
| **高** | BlueCortexCE Observation 增加 `category` 字段 | 提取时自动分类：profile/preference/entity/event/case/pattern（参考 OpenViking 6 类） |
| **中** | BlueCortexCE system prompt block 增加"空时不显示"逻辑 | 当 session 无 summary 时，返回空字符串（参考 OpenViking 的 `children == 0 → return ""`） |
| **中** | BlueCortexCE `/api/context/generate` 增加 `prefetch` 参数 | 后台预取相关 context，下次请求时返回缓存结果（参考 `queue_prefetch` 机制） |
| **中** | BlueCortexCE 增加 atexit 处理 | 进程退出时 flush pending writes（参考 `_atexit_commit_sessions`） |
| **低** | BlueCortexCE 考虑 URI-like 抽象 | `cortex://observations/{category}/{id}`（长期可行） |

---

## 56. ByteRover Provider — CLI Wrapper + Tiered Retrieval 架构（v5.0 新增）

> **文件**: `plugins/memory/byterover/__init__.py:1-383`
> **本节为 v5.0 新增**，分析 ByteRover Provider（最小的 Provider，383 行）。

### 56.1 架构定位

ByteRover 是最轻量的 Provider——完全依赖外部 `brv` CLI，不维护本地状态：

```
ByteRoverMemoryProvider
  └── brv CLI (npm global install)
        └── Local-first context tree ($HERMES_HOME/byterover/)
              └── Optional cloud sync
```

**与 OpenViking 对比**：OpenViking 是 REST API 客户端（自己维护连接），ByteRover 是 CLI wrapper（`subprocess.run`）。

### 56.2 工具集（3 tools）

| 工具 | 行为 | timeout |
|------|------|---------|
| `byterover_query` | 模糊文本搜索 → LLM-driven search | 10s |
| `byterover_curate` | LLM 驱动的记忆策展 | 120s |
| `byterover_status` | 显示缓存状态和统计 | — |

### 56.3 `system_prompt_block` — 动态显示状态

```python
# plugins/memory/byterover/__init__.py:205-214
def system_prompt_block(self) -> str:
    if not self._client:
        return ""
    result = _run_brv(["status"], cwd=self._cwd)
    if not result["success"]:
        return ""
    output = result["output"]
    return f"## ByteRover Context\n{output}" if output else ""
```

**不同于 OpenViking**：ByteRover 总是显示状态（即使是空的），因为 `brv status` 本身就会返回有用的信息（即使是 "No memories yet"）。

### 56.4 `sync_turn` — CLI 批量写入

```python
# plugins/memory/byterover/__init__.py:237-264
def sync_turn(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    def _sync():
        result = _run_brv(
            ["add", "-u", clean_user, "-a", clean_assistant],
            cwd=self._cwd,
        )
    if self._sync_thread and self._sync_thread.is_alive():
        self._sync_thread.join(timeout=2.0)
    self._sync_thread = threading.Thread(target=_sync, daemon=True, name="byterover-sync")
    self._sync_thread.start()
```

**关键差异**：
- 单条 CLI 调用 `brv add -u ... -a ...`（而非 API 的两条消息）
- 前序 join 最多 2s（比 OpenViking 的 5s 更短）
- `clean_user` / `clean_assistant` 经过清理

### 56.5 `on_pre_compress` — LLM 驱动的策展（120s timeout）

```python
# plugins/memory/byterover/__init__.py:282-312
def on_pre_compress(self, messages: List[Dict[str, Any]]) -> str:
    """Run LLM curation on the conversation before compression."""
    def _flush():
        result = _run_brv(["curate"], timeout=_CURATE_TIMEOUT, cwd=self._cwd)
    if self._flush_thread and self._flush_thread.is_alive():
        self._flush_thread.join(timeout=5.0)
    self._flush_thread = threading.Thread(target=_flush, daemon=True, name="byterover-curate")
    self._flush_thread.start()
    return ""  # 异步，不阻塞
```

**关键设计**：这是**唯一使用 `on_pre_compress` hook** 的 Provider（Honcho/Supermemory/RetainDB 都用 `sync_turn` 写入）。ByteRover 选择在压缩前执行策展，而不是实时写入。

### 56.6 `_tool_query` — 模糊文本 + LLM 混合

```python
# plugins/memory/byterover/__init__.py:332-355
def _tool_query(self, args: dict) -> str:
    query = args.get("query", "")
    result = _run_brv(["query", query], cwd=self._cwd)
    if not result["success"]:
        return tool_error(result.get("error", "Query failed"))
    output = result["output"].strip()
    if not output:
        return json.dumps({"result": "No relevant memories found."})
    return json.dumps({"result": output})
```

**查询超时**：10s（`brv query` 应该很快）。

### 56.7 `_tool_curate` — 主动策展

```python
# plugins/memory/byterover/__init__.py:355-370
def _tool_curate(self, args: dict) -> str:
    result = _run_brv(["curate"], timeout=_CURATE_TIMEOUT, cwd=self._cwd)
    ...
    return json.dumps({"status": "curated", "result": result.get("output", "")})
```

**策展超时**：120s（LLM 处理可能很慢）。

### 56.8 与 BlueCortexCE 对比

| 维度 | ByteRover | BlueCortexCE |
|------|----------|--------------|
| 写入模式 | CLI `brv add`（turn 级别） | PostgreSQL insert |
| 策展时机 | `on_pre_compress`（压缩前） | SessionEnd Summary |
| 查询模式 | 模糊文本 + LLM-driven | 向量相似度 |
| 存储位置 | 本地 `$HERMES_HOME/byterover/` | 远程 PostgreSQL |
| 工具数量 | 3（query/curate/status） | 1（search API） |

### 56.9 翻译：旁路型如何借鉴

**Hermes 做法**：ByteRover 将策展时机从"实时写入"推迟到"压缩前"，减少写入延迟但增加了策展失败的风险。

**翻译：旁路型如何落地**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **中** | BlueCortexCE 考虑增加 `on_pre_compress` hook | 在 SessionEnd Summary 之前，先对 observations 做一次策展过滤（删除 trivial） |
| **低** | BlueCortexCE 考虑 LLM-driven 查询 | 当向量搜索结果不足时，用 LLM 做二次 re-rank 或补充 |

---

## 57. Memory Provider 全景对比（v5.0 更新）

> **本节为 v5.0 更新**，将 ByteRover 和 OpenViking 加入对比表（原 v4.1 对比表已过时）。

### 57.1 Provider 特性矩阵

| 维度 | Honcho | Supermemory | RetainDB | Holographic | OpenViking | ByteRover |
|------|--------|-------------|----------|-------------|------------|-----------|
| **存储位置** | 云端 API | 云端 API | SQLite 本地 | SQLite 本地 | REST API | CLI 本地 |
| **向量检索** | ✅ | ✅ | ✅ (FTS5 BM25) | ✅ (HRR) | ✅ | ❌ (LLM) |
| **写入时机** | async/turn/session/int | 每 turn | Write-behind queue | Turn + expire | Turn + commit | Turn + pre-compress |
| **遗忘机制** | API 管理 | API 管理 | TTL + importance | 指数衰减 | API 管理 | API 管理 |
| **提取类别** | ❌ | ✅ (preference/decision/fact) | ✅ (6 type enum) | ✅ (fact/entity) | ✅ (6 categories) | ❌ |
| **工具数量** | 4 | 4 | 0 | 0 | 5 | 3 |
| **profile recall** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **prefetch** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **on_pre_compress** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **on_memory_write** | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| **entity extraction** | ❌ | ✅ (entity_context) | ✅ (LLM) | ✅ (HRR) | ✅ (commit时) | ❌ |
| **multi-container** | ✅ (session_strategy) | ✅ (container_tag) | ❌ | ❌ | ❌ | ❌ |

### 57.2 BlueCortexCE 缺失功能优先级排序

基于所有 Provider 分析，整理 BlueCortexCE 缺失功能（按优先级）：

| 优先级 | 缺失功能 | 来源 Provider | 说明 |
|--------|----------|--------------|------|
| **高** | Observation 长度硬限制 | 内置 memory (2,200/1,375 chars) | 防止 summary 无限增长 |
| **高** | Observation TTL / max_entries | RetainDB (TTL + importance) | 防止 observation 无限积累 |
| **高** | 删除 API (`/api/observations/{id}`) | 所有 Provider | 当前只有 create |
| **高** | Negative 指令在 Summary prompt 中 | Supermemory (entity_context) | 减少噪声进入 summary |
| **高** | Trivial 消息过滤 | Supermemory (`_is_trivial_message`) | 跳过 ok/thanks 等 |
| **高** | Observation category 字段 | OpenViking (6 类) / Supermemory (3 类) | 区分记忆类型 |
| **中** | 分层上下文响应 | OpenViking (L0/L1/L2) | `/api/context/generate` 分层 |
| **中** | "空时沉默" 系统 prompt | OpenViking (`children == 0 → return ""`) | 无内容时不显示 block |
| **中** | Profile recall API | Honcho / Supermemory | 用户画像专用检索 |
| **中** | `on_pre_compress` hook | ByteRover | 压缩前策展 |
| **低** | Multi-container 隔离 | Supermemory (container_tag) | 不同 project 隔离 |
| **低** | Prompt 注入扫描 | 内置 memory (security scan) | Observation 内容注入敏感 |
| **低** | atexit 安全网 | OpenViking (`_atexit_commit_sessions`) | 进程退出时 flush |

### 57.3 最高优先级落地路线图

**Phase 1（立即可做）**：
1. Observation 增加 `category` 字段（enum: preference/fact/decision/event/other）
2. Summary prompt 增加 negative 指令（"Do NOT remember: trivial responses, temporary intents..."）
3. `/api/observations` 增加 `DELETE /{id}` 端点

**Phase 2（1-2 周）**：
4. Summary 增加长度硬限制（建议 2,500 chars）
5. Trivial 消息过滤（正则跳过 ok/thanks/sure 等）
6. `/api/context/generate` 增加 `level` 参数（brief/standard/full）

**Phase 3（长期）**：
7. Multi-container 架构（不同 project 隔离）
8. Profile recall API（用户画像专用检索）
9. Prompt 注入扫描

---

## 58. 核心架构澄清：Built-in Memory 与 Plugin Provider 双系统（v5.1 新增）

> **本节为 v5.1 新增**，澄清 Hermes Agent 中 Built-in Memory 与 External Provider 的架构关系，这是理解 Hermes 记忆系统的关键前提。

### 58.1 关键发现：Built-in Memory 独立于 MemoryManager 插件系统

**文件**: `run_agent.py:1148-1153` + `run_agent.py:1192-1198`

之前分析假设 Built-in Memory (`MEMORY.md`/`USER.md`) 是通过 `MemoryManager` 管理的，但实际架构完全不同：

```
┌─────────────────────────────────────────────────────────────┐
│                      run_agent.py                            │
│  ┌──────────────────────────┐  ┌─────────────────────────┐ │
│  │  Built-in Memory          │  │  MemoryManager          │ │
│  │  (MemoryStore, direct)    │  │  (Plugin system)        │ │
│  │  ┌─────────────────────┐  │  │  ┌──────────────────┐   │ │
│  │  │ MEMORY.md (2200ch)  │  │  │  │ External Plugin  │   │ │
│  │  │ USER.md (1375ch)    │  │  │  │ (one at a time)  │   │ │
│  │  │                     │  │  │  └──────────────────┘   │ │
│  │  │ self._memory_store  │  │  │                         │ │
│  │  │ (direct, not plugin)│  │  │ self._memory_manager   │ │
│  │  └─────────────────────┘  │  │                         │ │
│  └──────────────────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**代码证据**：

```python
# run_agent.py:1148-1153 — Built-in memory 直接初始化，不走 MemoryManager
if self._memory_enabled or self._user_profile_enabled:
    from tools.memory_tool import MemoryStore
    self._memory_store = MemoryStore(
        memory_char_limit=mem_config.get("memory_char_limit", 2200),
        user_char_limit=mem_config.get("user_char_limit", 1375),
    )
    self._memory_store.load_from_disk()

# run_agent.py:1192-1198 — MemoryManager 只管理外部插件
if not skip_memory:
    from agent.memory_manager import MemoryManager as _MemoryManager
    from plugins.memory import load_memory_provider as _load_mem
    self._memory_manager = _MemoryManager()
    _mp = _load_mem(_mem_provider_name)
    if _mp and _mp.is_available():
        self._memory_manager.add_provider(_mp)
```

**结论**：`BuiltinMemoryProvider` 并不存在！Built-in Memory 是 `run_agent.py` 直接管理的模块，不属于 `MemoryManager` 的插件体系。`MemoryManager` 的 `add_provider()` 只能添加一个外部插件，Built-in memory 永远独立运行。

### 58.2 两套记忆系统的职责分工

| 维度 | Built-in Memory (MemoryStore) | External Provider (MemoryManager) |
|------|-------------------------------|----------------------------------|
| **存储** | `MEMORY.md`/`USER.md` 平面文件 | Honcho/Mem0/Hindsight 等云服务 |
| **边界** | 硬字符限制（2200/1375 chars） | 云端 API（无本地限制） |
| **生命周期** | 进程生命周期（下次启动读取） | 跨进程（云端持久化） |
| **注入时机** | System prompt（冻结快照） | Prefetch（每轮前注入） |
| **管理方式** | Agent 直接调用 `memory_tool()` | Agent 通过 `MemoryManager` 调用 |
| **写入触发** | Agent 显式调用 memory tool | `sync_turn()` 自动写入 |

### 58.3 翻译：旁路型如何理解这个架构

**Hermes 内置记忆的本质**：对 Hermes 来说，"内置记忆" 是 Agent 的**核心有界记忆**（Bounded Memory），用文件存储 + 硬限制保证永远不会无限增长。外部 Provider 是**扩展记忆**（Extended Memory），提供无限容量但依赖第三方。

**对于 BlueCortexCE 的意义**：BlueCortexCE 就是 Hermes 的 "External Provider"，但我们是**自托管的外部记忆服务**。我们提供：
- 比 Hermes 内置记忆更大的容量（PostgreSQL 存储）
- 比 Honcho/Mem0 更透明的存储（自托管）
- 比 Holographic 更可靠的检索（pgvector）

**这不是"内置 vs 外部"的区别，而是"谁控制记忆的边界"**。Hermes 的内置记忆由 Agent 自己管理（有硬限制），BlueCortexCE 作为外部服务也需要考虑如何帮助消费方管理记忆边界（TTL、max_entries、长度限制）。

### 58.4 架构对比图

```
Hermes Agent (内置型):
  Agent ←→ Built-in Memory (文件, 硬限制)
         ↘ External Provider (云服务, 可选)

BlueCortexCE (旁路型):
  Claude Code/OpenClaw ←→ BlueCortexCE (PostgreSQL, 自主服务)
                       ↘ (相当于 Hermes 的 External Provider)
```

---

## 59. Honcho 动态推理级别 — Query-Length 驱动的自适应 LLM 成本控制（v5.1 新增）

> **文件**: `plugins/memory/honcho/session.py:489-516`
> **本节为 v5.1 新增**，分析 Honcho 的 `_dynamic_reasoning_level` 机制——根据查询长度自动选择推理深度，以控制 LLM 成本。

### 59.1 机制原理

```python
# plugins/memory/honcho/session.py:489-516
def _dynamic_reasoning_level(self, query: str) -> str:
    """
    Pick a reasoning level for a dialectic query.
    
    When dialecticDynamic is true (default), auto-bumps based on query
    length so Honcho applies more inference where it matters:

      < 120 chars  -> configured default (typically "low")
      120-400 chars -> +1 level above default (cap at "high")
      > 400 chars  -> +2 levels above default (cap at "high")
    
    "max" is never selected automatically -- reserve it for explicit config.
    """
    levels = ["minimal", "low", "mid", "high"]
    default_idx = levels.index(self._dialectic_reasoning_level)  # default: "low" → index 1
    n = len(query)
    if n < 120:
        bump = 0
    elif n < 400:
        bump = 1
    else:
        bump = 2
    # Cap at "high" (index 3) for auto-selection
    idx = min(default_idx + bump, 3)
    return levels[idx]
```

**推理级别**: `minimal → low → mid → high`（从不自动选 `max`）

### 59.2 为什么这样设计

**内置型架构动机**：每次 `dialectic_query` 调用 Honcho 云端 API，都会产生 LLM 推理成本。短查询通常只需要简单的事实查找，不需要深度推理；长查询通常代表复杂问题，需要更深的推理。

**这是"按需推理"的经典成本控制模式**：不是为所有查询支付最高推理成本，而是根据问题的复杂度动态调整。

### 59.3 BlueCortexCE 现状

BlueCortexCE 目前对所有 Observation 检索使用固定的向量相似度排序，没有根据查询复杂度选择不同的检索策略。

### 59.4 翻译：旁路型如何借鉴

**核心思想**：根据查询复杂度选择检索/推理深度，而非一刀切。

**具体建议**：

| 场景 | BlueCortexCE 可借鉴的方式 |
|------|--------------------------|
| **短 query（如 "我的用户名是什么"）** | 直接精确匹配 `facts` 字段，不需要向量检索 |
| **中等 query（如 "上次我怎么部署的"）** | 使用向量相似度检索 |
| **复杂 query（如 "我在哪个项目里用过 Redis，具体怎么配置的"）** | 先向量检索，再对结果做 LLM 重排序或补充检索 |

**这不是"旁路型 vs 内置型"的问题，而是成本控制的通用原则**。对于 BlueCortexCE：
- 简单查询 → 直接 SQL 精确匹配（零向量计算成本）
- 复杂查询 → pgvector 检索 + 可选的 LLM rerank

---

## 60. Honcho 观察模式 — ai_observe_others 双 Peering 架构（v5.1 新增）

> **文件**: `plugins/memory/honcho/session.py:122-123` + `session.py:553-569` + `session.py:965-998`
> **本节为 v5.1 新增**，分析 Honcho 的 `_ai_observe_others` 配置如何决定结论的归属模式。

### 60.1 两种观察模式

```python
# plugins/memory/honcho/session.py:122-123
self._ai_observe_me: bool = config.ai_observe_me if config else True
self._ai_observe_others: bool = config.ai_observe_others if config else True
```

**`ai_observe_others = True`（交叉观察模式）**：
- AI peer 可以观察用户（创建关于用户的结论）
- 用户是被观察的对象，不能观察 AI
- 结论创建者：`assistant_peer.conclusions_of(user_peer_id)`
- 语义："AI 记住了关于用户的观察"

**`ai_observe_others = False`（自我观察模式）**：
- AI peer 不能观察其他人，只能观察自己
- 用户创建关于自己的结论（self-conclusion）
- 结论创建者：`user_peer.conclusions_of(user_peer_id)`
- 语义："用户自我记录的 profile"

### 60.2 结论创建的双重路由

```python
# plugins/memory/honcho/session.py:965-998
def create_conclusion(self, session_key: str, content: str) -> bool:
    if self._ai_observe_others:
        # AI peer creates conclusion about user (cross-observation)
        assistant_peer = self._get_or_create_peer(session.assistant_peer_id)
        conclusions_scope = assistant_peer.conclusions_of(session.user_peer_id)
    else:
        # AI can't observe others — user peer creates self-conclusion
        user_peer = self._get_or_create_peer(session.user_peer_id)
        conclusions_scope = user_peer.conclusions_of(session.user_peer_id)

    conclusions_scope.create([{
        "content": content.strip(),
        "session_id": session.honcho_session_id,
    }])
```

### 60.3 与 BlueCortexCE 对比

| 维度 | Honcho (ai_observe_others=True) | Honcho (ai_observe_others=False) | BlueCortexCE |
|------|---------------------------------|----------------------------------|--------------|
| **观察者** | AI peer 观察用户 | 用户自我观察 | N/A（旁路型） |
| **结论归属** | AI peer 的 memory | User peer 的 memory | 统一 Observation 表 |
| **适用场景** | 个人 AI 助手 | 共享 AI 助手 | 任意消费方 |
| **隐私性** | AI 主导 | 用户主导 | 取决于消费方 |

**翻译：旁路型如何落地**：
- BlueCortexCE 作为外部服务，**不关心"谁观察谁"**——这是消费方（Claude Code/OpenClaw）的职责
- 但我们可以通过 API 设计支持这种概念：增加 `observation_type` 字段（`user_self_report` / `agent_observation`），让消费方自行标记
- **实际上**：这进一步说明 BlueCortexCE 的定位是**存储基础设施**，不做语义层的假设

---

## 61. on_memory_write 桥接机制 — create_conclusion 完整语义确认（v5.1 确认）

> **文件**: `plugins/memory/honcho/session.py:965-998` + `plugins/memory/honcho/__init__.py:622-644`
> **本节为 v5.1 更新**，确认 `on_memory_write` 的完整语义（之前标记为"待确认"）。

### 61.1 完整路由链

```
Hermes Built-in Memory Tool (USER.md write)
         ↓
run_agent.py 检测到 memory tool write
         ↓
MemoryManager.on_memory_write("add", "user", content)
         ↓
HonchoMemoryProvider.on_memory_write("add", "user", content)
         ↓
HonchoSessionManager.create_conclusion(session_key, content)
         ↓
conclusions_scope.create([{"content": content, "session_id": honcho_session_id}])
```

**注意**：只有 `action="add"` 和 `target="user"` 的写入才会同步到 Honcho。`memory` store（Agent 个人笔记）不会同步到 Honcho。

### 61.2 结论确认

- **Honcho Dialectic 完整 prompt**：云端 API，无本地 LLM prompt（✅ 确认）
- **Honcho memory mirror (on_memory_write)**：`create_conclusion` 的语义是"将被观察者（用户）的 profile 事实写入 Honcho"（✅ 确认）
- **结论和 session 记忆的关系**：结论通过 `session_id` 与特定 Honcho session 关联，但结论本身是跨 session 的（属于 peer card）

---

## 62. BlueCortexCE Observation 现状确认（v5.1 更新）

> **本节为 v5.1 更新**，确认 BlueCortexCE 的 Observation Entity 字段现状，更新"待确认"列表。

### 62.1 BlueCortexCE Category 字段现状

**文件**: `backend/src/main/resources/prompts/init.txt` + `backend/.../entity/ObservationEntity.java`

**Summary Prompt（init.txt）定义的 `type` 字段**：
```xml
<type>[ bugfix | feature | refactor | change | discovery | decision ]</type>
```
- **bugfix**: something was broken, now fixed
- **feature**: new capability or functionality added
- **refactor**: code restructured, behavior unchanged
- **change**: generic modification (docs, config, misc)
- **discovery**: learning about existing system
- **decision**: architectural/design choice with rationale

**ObservationEntity.java 中的 `type` 字段**：
```java
@Column(name = "type", nullable = false)
@JsonProperty("type")
private String type;
```
- **现状**：`type` 是 `String`，没有 enum 验证
- **问题**：LLM 可能输出任意字符串，后端不校验

**结论**：✅ Category 系统在 **prompt 层**已实现（6 个类型），但 **entity 层**没有 enum 约束。LLM 可能输出非标准类型。

### 62.2 BlueCortexCE Entity Extraction 字段现状

**ObservationEntity 中没有 `entities` 字段**。现有相关字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `facts` | `List<String>` (JSONB) | 事实列表 |
| `concepts` | `List<String>` (JSONB) | 知识类别标签 |
| `source` | String | 来源：tool_result / user_statement / llm_inference / manual |
| `extractedData` | `Map<String, Object>` (JSONB) | 结构化提取数据 |

**没有 dedicated entity extraction**。如果需要提取"实体"（如人名、地点、技术名词），目前只能放在 `facts` 列表中。

**结论**：❌ 尚未实现 dedicated entity extraction（`entities` 字段）。Phase 3 设计中也没有包含 entity extraction。

### 62.3 建议优先级调整

基于确认的现状，更新建议优先级：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | Observation 增加 `category` enum 约束 | 后端增加 enum 校验，而非依赖 prompt 约束 |
| **高** | 确认 `type` 字段是否被 API 消费者使用 | 如果 WebUI 不显示 type，添加 enum 价值有限 |
| **中** | 考虑增加 `entities` 字段 | 如果需要从文本中提取命名实体 |
| **低** | source 字段在 Summary prompt 中提取 | 目前 source 由调用方在 API 层设置 |

---

## 53. 待进一步确认（v5.1 更新）

### 53.1 本轮已确认项目

1. ✅ ~~ContextCompressor Phase 1 tool result pre-pass~~ — **已详细分析**：规则型 1-line 摘要，非通用 placeholder
2. ✅ ~~SessionDB v6 reasoning chain columns~~ — **已验证**：reasoning/reasoning_details/codex_reasoning_items 三列
3. ✅ ~~Honcho write_frequency mechanism~~ — **已验证**：async/turn/session/int 四种模式 + daemon thread 实现
4. ✅ ~~Honcho sync_turn threading model~~ — **已验证**：daemon thread + 5s 前序 join 防堆积
5. ✅ ~~RetainDB SQLite write-behind queue~~ — **v4.8 已详细分析**：pending 表 + crash replay + thread-local connections
6. ✅ ~~RetainDB memory_type enum~~ — **v4.8 已验证**：factual/preference/goal/instruction/event/opinion + importance 0-1
7. ✅ ~~Supermemory entity_context~~ — **v4.8 已验证**：negative 指令 + "When in doubt, store less" + trivial filter
8. ✅ ~~Hermes 内置 memory 生命周期机制~~ — **v4.9 已详细分析**：有界精选（硬字符限制）+ 冻结快照 + Agent 显式删除 + 注入扫描
9. ✅ ~~OpenViking Provider~~ — **v5.0 已详细分析**：分层上下文（L0/L1/L2）+ 6 类提取 + filesystem URI + atexit 安全网
10. ✅ ~~ByteRover Provider~~ — **v5.0 已详细分析**：CLI wrapper + on_pre_compress 策展 + 3 tools
11. ✅ ~~Built-in Memory 双系统架构~~ — **v5.1 已澄清**：Built-in Memory (MemoryStore) 不属于 MemoryManager 插件系统，run_agent.py 直接管理
12. ✅ ~~Honcho 动态推理级别~~ — **v5.1 已分析**：Query-Length 驱动 (<120/low, 120-400/mid, >400/high) + cap at high
13. ✅ ~~Honcho ai_observe_others 观察模式~~ — **v5.1 已分析**：交叉观察 (AI→User) vs 自我观察 (User→Self) 的双路由
14. ✅ ~~Honcho on_memory_write → create_conclusion 语义~~ — **v5.1 已确认**：写入 USER profile 事实，与 session 关联但结论跨 session
15. ✅ ~~BlueCortexCE Observation Category 现状~~ — **v5.1 已确认**：type 字段在 prompt 层已实现 6 类（bugfix/feature/refactor/change/discovery/decision），entity 层无 enum 约束
16. ✅ ~~BlueCortexCE Observation Entity Extraction~~ — **v5.1 已确认**：无 dedicated entities 字段，只有 facts/concepts

### 53.2 仍待确认项目

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model** — RetainDB 的 `Agent Self-Model` 具体如何影响 behavior？（需要查看 agent/ 相关代码）
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）

---

