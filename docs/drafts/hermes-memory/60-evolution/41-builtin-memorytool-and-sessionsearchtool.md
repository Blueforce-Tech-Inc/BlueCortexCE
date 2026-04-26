# 41 — Built-in MemoryTool & SessionSearchTool：Tool-Calling 接口设计解析

**覆盖源码**：`tools/memory_tool.py`（584 行）+ `tools/session_search_tool.py`（590 行）  
**定位**：独立于 Provider 插件体系之外的**内置工具**，是 Agent 自身可调用的记忆工具，而非外部记忆系统。  
**时间**：2026-04-25（接续 §40）

---

## §1 定位：内置工具 vs 外部 Provider

Hermes 记忆体系有两层：

| 层 | 组件 | 持久化 | 触发方式 |
|---|---|---|---|
| **外部 Provider** | `MemoryProvider` 插件（Supermemory/Hindsight/RetainDB 等） | Provider 自定义 | 自动（turn 触发 + 定时 flush） |
| **内置工具** | `MemoryStore` + `SessionSearchTool` | 本地文件 / SQLite FTS5 | Agent **主动调用** via tool calls |

这是完全不同的两根管线：**Provider 是后台静默运行的记忆写入/检索系统；内置工具是 Agent 可以显式调用的工具**。Agent 可以 `add`/`replace`/`remove` 记忆条目，也可以 `session_search` 搜索历史会话。

---

## §2 MemoryStore：双态快照模型

### 2.1 核心设计：`Frozen Snapshot`（冻结快照）

`MemoryStore` 的最大特色是**双态分离**：

```python
class MemoryStore:
    def __init__(self, memory_char_limit: int = 2200, user_char_limit: int = 1375):
        # Live state — 会被 tool calls 修改
        self.memory_entries: List[str] = []
        self.user_entries: List[str] = []
        # Frozen snapshot — 仅在 load_from_disk() 时设置，整个 session 内不变
        self._system_prompt_snapshot: Dict[str, str] = {"memory": "", "user": ""}
```

- **`_system_prompt_snapshot`**：在 `load_from_disk()` 时一次性捕获，用于系统 prompt 注入。**Session 期间永不更新**，即使 `add`/`replace`/`remove` 修改了 live state
- **`memory_entries`/`user_entries`**：live state，被 tool calls 实时修改，写入后 **save_to_disk()** 持久化，但不影响当前 session 的系统 prompt

**为什么这样做？**

这解决了一个微妙问题：如果每次 `add` 后都更新系统 prompt，prefix cache 会失效，导致重复计算。冻结快照让 KV-cache 稳定，同一 session 内所有 turn 的 prefix 完全一致。

**CE 借鉴**：Claude-Mem 的 `ModeService` / `ContextService` 可以在 session 级别做类似的 snapshot，避免每次 ICL 更新都刷新系统 prompt。

### 2.2 文件持久化

```python
def get_memory_dir() -> Path:
    return get_hermes_home() / "memories"
```

记忆存储在 `~/.hermes/memories/MEMORY.md` 和 `USER.md`，用 `"\n§\n"` 作为条目分隔符。

```python
ENTRY_DELIMITER = "\n§\n"
```

读取时按 `ENTRY_DELIMITER` split，写入时 join。`load_from_disk()` 时做 **dedup**（按首次出现顺序保留）。

### 2.3 并发安全：文件锁 + 原子重命名

```python
@contextmanager
def _file_lock(path: Path):
    # 使用独立的 .lock 文件，不影响主文件的原子替换
    lock_path = path.with_suffix(path.suffix + ".lock")
    if fcntl:
        fcntl.flock(fd, fcntl.LOCK_EX)  # 排他锁
```

写操作模式：

```python
def save_to_disk(self, target: str):
    tmp_path = path.with_suffix(".tmp")
    tmp_path.write_text(content, encoding="utf-8")
    os.replace(tmp_path, path)  # 原子替换
```

**写并发**：锁保护 + 原子替换，确保读要么看到旧文件要么看到新文件，绝不会看到半写状态。

### 2.4 Threat Scanning：写入边界安全

```python
_MEMORY_THREAT_PATTERNS = [
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'you\s+are\s+now\s+', "role_hijack"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    # ... 还有 curl/wget exfil、authorized_keys backdoor 等
]

_INVISIBLE_CHARS = {'\u200b', '\u200c', '\u200d', '\u2060', '\ufeff', ...}
```

**`_scan_memory_content()`** 在每次 `add`/`replace` 时执行，检查：
1. 不可见 Unicode 字符（零宽空格等）
2. 威胁正则匹配（prompt injection / role hijack / exfil via curl）

**CE 借鉴**：BlueCortexCE 在 ICL 写入记忆前也应该做类似的 threat scanning，防止注入 Payload 持久化到 PostgreSQL 后在后续 session 被读取时触发。

### 2.5 字符预算强制

```python
new_total = len(ENTRY_DELIMITER.join(new_entries))
if new_total > limit:
    return {"success": False, "error": f"Memory at {current:,}/{limit:,} chars..."}
```

- `memory_char_limit = 2200`，`user_char_limit = 1375`
- **先算再写**：不允许超限（防止静默截断导致数据丢失）
- 精确的 usage 百分比在 tool response 中返回（`"62% — 1,364/2,200 chars"`）

---

## §3 MemoryTool：工具调用接口

### 3.1 四个操作

| 操作 | 签名 | 描述 |
|---|---|---|
| `add` | `(target, content)` | 追加新条目，exact duplicate 检查 |
| `replace` | `(target, old_text, new_content)` | 找到含 `old_text` 的条目，替换内容 |
| `remove` | `(target, old_text)` | 删除含 `old_text` 的条目 |
| `format_for_system_prompt` | `(target)` | **不经过 tool calls**，内部方法，返回 frozen snapshot |

### 3.2 Tool Response 格式

```python
def _success_response(self, target: str, message: str = None) -> Dict[str, Any]:
    entries = self._entries_for(target)
    current = self._char_count(target)
    limit = self._char_limit(target)
    pct = min(100, int((current / limit) * 100))
    return {
        "success": True,
        "target": target,
        "entries": entries,          # 当前所有条目
        "usage": f"{pct}% — {current:,}/{limit:,} chars",
        "entry_count": len(entries),
        "message": message,
    }
```

Agent 每次 `add` 后都能看到自己的 **usage budget**，这是自描述接口的很好实践。

### 3.3 与 Provider 系统的区别

| 维度 | MemoryStore | MemoryProvider |
|---|---|---|
| 触发 | 显式 tool call | 自动（turn 边界 + 定时） |
| 内容 | Agent 主动写入 | Provider 自动从对话中提取 |
| 容量 | 硬上限（2200/1375 chars） | Provider 自定义 |
| 检索 | Agent 自己决定怎么用 | Provider 提供 query/search API |
| 生命周期 | 永久（文件） | Provider 定义（session / long-term） |

---

## §4 SessionSearchTool：FTS5 + LLM Recall 双模式

### 4.1 搜索流程

```python
def session_search(query: str, ...):
    # Step 1: 尝试 FTS5 精确匹配
    fts_results = _search_sessions_fts5(query, ...)
    
    # Step 2: 如果 FTS5 结果不足，用 LLM semantic recall
    if len(fts_results) < min(3, limit):
        llm_results = await _llm_recall_sessions(query, ...)
    
    # Step 3: 合并去重，按时间/MRSS 排序
```

### 4.2 Truncate-Around-Matches 策略

`_truncate_around_matches()` 是 session 文本截断的核心：

```python
def _truncate_around_matches(full_text: str, query: str, max_chars: int = MAX_SESSION_CHARS):
    # 1. 短语匹配（phrase）
    # 2. 共现窗口匹配（所有 query terms 出现在 200-char 内）
    # 3. 单 term 位置 fallback
    # 选覆盖最多匹配点的窗口
```

这个策略确保搜索结果始终包含相关上下文，而不是随机截断。

### 4.3 并行 LLM Summarization

```python
async def _bounded_summary(text: str, meta: Dict) -> Optional[str]:
    # 有 concurrency limit 的 parallel summarize
    # 每个 session 的结果单独 LLM 调用，防止单个长 session 阻塞
    
async def _summarize_all() -> List[Union[str, Exception]]:
    # 使用 semaphore 控制并发（max_concurrency，默认 3，上限 5）
```

`max_concurrency` 从 `auxiliary.session_search.max_concurrency` 配置读取，不是硬编码。

---

## §5 CE 可执行借鉴

### 5.1 Frozen Snapshot 模式（立即可做）

```python
# CE 当前：每次 /api/context/generate 都重新计算
# CE 改进：在 session 启动时一次性 snapshot，session 内复用
```

**实现路径**：
- 在 `SessionEntity` 增加 `context_snapshot TEXT` 字段
- `session_start` 时调用 `generateContext()` 一次，存入该字段
- 后续 turn 复用 snapshot，仅在显式 `flush` 时更新

### 5.2 Threat Scanning at Write Boundary（立即可做）

在 `ObservationService.ingest()` 或 `SummaryService.ingest()` 入口处加 `_scan_content()`：

```python
_THREAT_PATTERNS = [
    r'ignore\s+(previous|all|above|prior)\s+instructions',
    r'you\s+are\s+now\s+',
    r'\$[A-Z_]{5,}',  # env var exfil attempt
    # ...
]
```

### 5.3 自描述 Budget API

MemoryTool 的 usage response 是很好的 UX pattern：

```json
{
  "success": true,
  "usage": "62% — 1,364/2,200 chars",
  "entry_count": 3,
  "entries": ["..."]
}
```

CE 的 `/api/memory/search` 或 `/api/modes` 可以加入类似的自描述字段，让 Agent 知道自己的 memory budget 状态。

### 5.4 Truncate-Around-Matches for CE Context Window

CE 的 context 窗口管理可以借鉴这个策略：在压缩前，优先保留包含**当前活跃实体/关键词**的上下文窗口，而不是简单 head/truncate。这需要记录当前 query 的 entities，然后选择最有信息量的窗口。

---

## §6 小结

| 维度 | 设计亮点 |
|---|---|
| 双态模型 | Frozen snapshot 解耦系统 prompt 稳定性和 live state 写入 |
| 字符预算 | 先算后写，超限拒绝，不做静默截断 |
| Threat scanning | 不可见 Unicode + 正则 injection/exfil 双重检查 |
| 并发安全 | fcntl 排他锁 + 原子重命名 |
| Tool UX | 每个 response 带完整 budget 状态，Agent 可自我调整 |
| FTS5+LLM dual | 精确匹配不足时用 semantic recall 兜底 |
| 并行控制 | Semaphore-based concurrency limit，防止 LLM 调用过载 |

MemoryStore 的设计非常**轻量但完整** — 没有向量数据库，没有外部服务，仅靠文件 + 锁 + 正则。但它的双态模型和 budget enforcement 思路对 CE 的 Phase 3 Structured Extraction 有直接参考价值。
