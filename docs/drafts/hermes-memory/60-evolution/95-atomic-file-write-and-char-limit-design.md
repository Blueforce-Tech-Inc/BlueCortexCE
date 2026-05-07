# 95 — Atomic File Write Pattern + Char-Limit Budget Model

**commit**: `b62a82e0c` (2026-05-06 latest)
**来源**: `tools/memory_tool.py` + `utils.py` (atomic_replace)

---

## 1. 核心问题：文件写入的竞态窗口

传统的 read-modify-write 模式存在竞态窗口：

```python
# ❌ 不安全：truncate 发生在 lock 之前
with open(path, "w") as f:  # 截断文件!
    f.write(content)         # 其他进程看到空文件

# ❌ 仍然不安全：lock 之后截断
with open(path, "w") as f:
    fcntl.flock(f, LOCK_EX)  # 太晚了，文件已被截断
    f.write(content)
```

**问题**：`open(path, "w")` 在获取文件锁**之前**就已截断文件。并发读取进程会看到空文件。

---

## 2. Hermes 解决方案：Temp File + Atomic Replace

```python
# tools/memory_tool.py:445-453
fd, tmp_path = tempfile.mkstemp(dir=str(path.parent), suffix=".tmp", prefix=".mem_")
try:
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(content)
        f.flush()
        os.fsync(f.fileno())  # 强制刷到磁盘
    atomic_replace(tmp_path, path)  # 原子替换
except BaseException:
    os.unlink(tmp_path)
    raise
```

**三步走**：
1. **写临时文件**：`mkstemp` 在同一目录创建 `.mem_*.tmp`
2. **强制刷盘**：`os.fsync()` 确保数据落盘，不是只到 page cache
3. **原子替换**：`os.rename()` 或 `atomic_replace()` 原子性地替换目标文件

**`atomic_replace` 实现**（`utils.py`）：
```python
def atomic_replace(src: str, dst: str) -> None:
    """Atomically replace dst with src (overwrites dst if it exists)."""
    if os.name == "nt":
        # Windows: must close dst before replacing
        os.remove(dst)
        os.rename(src, dst)
    else:
        os.replace(src, dst)  # POSIX 原子 rename
```

**为什么原子替换安全**：
- 读取进程看到的是**旧完整文件**或**新完整文件**，永远不是空文件或半写文件
- `os.replace()` 在 POSIX 系统上是原子的（同一文件系统内）
- Windows 有特殊处理（先删除目标）

---

## 3. 文件锁：Separate Lock File Pattern

```python
# tools/memory_tool.py:131-161
@staticmethod
@contextmanager
def _file_lock(path: Path):
    lock_path = path.with_suffix(path.suffix + ".lock")  # e.g., MEMORY.md.lock
    # ...
    fd = open(lock_path, "a+")
    fcntl.flock(fd, LOCK_EX)  # 锁定 lock 文件
    yield
    fcntl.flock(fd, LOCK_UN)
    fd.close()
```

**关键设计：独立的 `.lock` 文件**

| 设计 | 优点 | 缺点 |
|------|------|------|
| **独立 lock 文件** | `atomic_replace` 替换 `MEMORY.md` 时 lock 文件不受影响 | 需要额外文件 |
| 锁住目标文件本身 | 简单 | `atomic_replace` 需要先 unlink，破坏 lock |

**为什么不用 `fcntl.flock` 直接锁 `MEMORY.md`**：
因为 `atomic_replace` 在 POSIX 上用 `os.replace()`（即 `rename`），`rename` 会删除旧文件。如果锁住 `MEMORY.md` 然后 `rename` 会怎样？`rename` 的语义是原子的，但 Linux 上已删除的文件描述符仍然有效（直到所有 fd 关闭）。这会产生微妙问题。

**所以用独立的 `.lock` 文件**：读写都锁 `.lock`，`MEMORY.md` 本身可以被 `atomic_replace` 自由替换。

---

## 4. Char-Limit Budget Model

```python
# tools/memory_tool.py:113-114
def __init__(self, memory_char_limit: int = 2200, user_char_limit: int = 1375):
    self.memory_char_limit = memory_char_limit  # MEMORY.md: 2200 chars
    self.user_char_limit = user_char_limit     # USER.md: 1375 chars
```

**为什么用字符数而不是 token 数**：

> "Character limits (not tokens) because char counts are model-independent."

不同模型的 token 计数差异巨大：
- Claude: 1 token ≈ 4 字符
- GPT-4: 1 token ≈ 4-5 字符
- Gemini: 1 token ≈ 4 字符

用字符数的好处：
1. **模型无关**：同一预算在不同模型下效果一致
2. **可预测**：用户可以准确计算还剩多少空间
3. **无需 API 调用**：不需要调用 tiktoken 等 token 计数库
4. **适合 bounded memory**：MEMORY.md 是 curated memory，不是存储桶，本质上就是紧凑的

**预算分配**：
- `MEMORY.md`：2200 chars（环境事实、项目惯例、工具 quirks、经验教训）
- `USER.md`：1375 chars（用户偏好、沟通风格、工作流习惯）

USER.md 更小是因为用户信息通常比 agent 自己的笔记更简洁。

---

## 5. ENTRY_DELIMITER 设计：Section Sign `§`

```python
ENTRY_DELIMITER = "\n§\n"
```

**为什么用 `§`（Section Sign, U+00A7）**：

```python
# _read_file 分割_entries
entries = [e.strip() for e in raw.split(ENTRY_DELIMITER)]
# ENTRY_DELIMITER = "\n§\n"，不是单独的 "§"
```

如果用 `"§"` 作为分隔符，而 entry 内容里恰好有 `§`，会被错误分割。

用 `"\n§\n"` 作为分隔符：
- Entry 内部可以有 `§` 字符（不会被分割）
- Entry 必须是完整的段落（首尾有换行）
- 相邻两个 `§` 只会产生空 entry，被 `strip()` 过滤掉

---

## 6. BlueCortexCE 落地建议

### 6.1 采用原子文件写入

CE 的 MEMORY.md/USER.md 实现应使用相同的 temp-file + `atomic_replace` 模式：

```python
import tempfile
import os

def atomic_write(path: Path, content: str):
    """原子写入：temp file + fsync + rename."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(
        dir=str(path.parent),
        suffix=".tmp",
        prefix=".mem_"
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)  # POSIX 原子
    except:
        os.unlink(tmp)
        raise
```

### 6.2 考虑字符限制而非 token 限制

Bounded MEMORY.md/USER.md 用字符预算是更简单的模型。但 CE 的向量数据库存储没有这个问题。

**CE 适用场景**：
- MEMORY.md / USER.md 等文件-backed memory：字符限制
- PostgreSQL 向量存储：按 token 计量更准确（成本/质量控制）

### 6.3 注入扫描在 ingest 入口

CE 应在 `ContextService.ingest()` / `ObservationService.ingest()` 入口增加 `_scan_memory_content` 等效扫描：

```python
def _scan_memory_content(content: str) -> Optional[str]:
    """Scan for injection/exfiltration patterns."""
    for char in _INVISIBLE_CHARS:
        if char in content:
            return f"Blocked: invisible unicode U+{ord(char):04X}"
    for pattern, pid in _MEMORY_THREAT_PATTERNS:
        if re.search(pattern, content, re.IGNORECASE):
            return f"Blocked: threat pattern '{pid}'"
    return None
```

覆盖：
- 零宽字符：`\u200b`, `\u200c`, `\u200d`, `\u2060`, `\ufeff`
- Bidi 控制字符：`\u202a-\u202e`
- Prompt injection 正则
- Exfiltration 模式（curl/wget + secret）

---

## 7. 设计权衡总结

| 决策 | Hermes 选择 | 适用场景 | CE 迁移优先级 |
|------|------------|----------|-------------|
| 原子文件替换 | temp + fsync + os.replace | 高并发文件写入 | P1（安全关键） |
| 独立 lock 文件 | `.lock` 后缀 | 允许 atomic replace | P1 |
| 字符预算 | 2200/1375 chars | bounded curated memory | P1（CE MEMORY.md） |
| Section Sign 分隔符 | `§` | 支持多行 entry | P2（低风险） |

---

## 8. 相关文档

- [`08-builtin-memory-tool-bounded-snapshot.md`](08-builtin-memory-tool-bounded-snapshot.md) — 包含 atomic write 源码
- [`91-streaming-scrubber-and-memory-security-scanning.md`](91-streaming-scrubber-and-memory-security-scanning.md) — `_scan_memory_content` 扫描逻辑
- [`76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md`](76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md) — CE 缺口盘点
- `backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java` — CE inject 入口（待增加扫描）
