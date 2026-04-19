
## 70. Holographic HRR — 完整实现分析（v6.0 新增）

### 70.1 核心代数运算

**文件**：`plugins/memory/holographic/holographic.py:1-200`

**三种基本运算**（基于 phase encoding）：

```python
# bind = circular convolution = phase addition
# 绑定两个概念 → 产生一个复合向量（与两者都不相似）
def bind(a, b): return (a + b) % _TWO_PI

# unbind = circular correlation = phase subtraction
# 解绑：从 memory 中检索 key 对应的 value
def unbind(memory, key): return (memory - key) % _TWO_PI

# bundle = superposition = circular mean
# 捆绑：合并多个向量（保留每个输入的相似性）
def bundle(*vectors): return np.angle(np.sum([np.exp(1j * v) for v in vectors], axis=0))
```

**关键性质**：`unbind(bind(a, b), a) ≈ b`（代数上可逆）

### 70.2 确定性 Atom 生成（SHA-256）

```python
# holographic.py:44-58
def encode_atom(word, dim=1024):
    # 每个 SHA-256 digest = 32 bytes = 16 个 uint16 values
    # 缩放到 [0, 2π)：values * (2π / 65536)
    blocks_needed = math.ceil(dim / 16)
    uint16_values = []
    for i in range(blocks_needed):
        digest = hashlib.sha256(f"{word}:{i}".encode()).digest()
        uint16_values.extend(struct.unpack("<16H", digest))
    phases = np.array(uint16_values[:dim], dtype=np.float64) * (_TWO_PI / 65536.0)
    return phases
```

- **跨进程/机器/语言版本一致**（使用 hashlib 而非 numpy RNG）
- `f"{word}:{i}"` 模式确保相同 word 产生相同向量

### 70.3 结构化 Fact 编码

```python
# holographic.py:165-181
def encode_fact(content, entities, dim=1024):
    role_content = encode_atom("__hrr_role_content__", dim)
    role_entity = encode_atom("__hrr_role_entity__", dim)
    components = [bind(encode_text(content), role_content)]
    for entity in entities:
        components.append(bind(encode_atom(entity.lower()), role_entity))
    return bundle(*components)
```

**代数检索能力**：
```python
# 检索某个 entity 相关的事实内容：
content_vector = unbind(fact_vector, bind(encode_atom(entity), role_entity))
```

### 70.4 SNR 容量估计

```python
# holographic.py:195-210
def snr_estimate(dim, n_items):
    snr = math.sqrt(dim / n_items)
    if snr < 2.0:
        logger.warning(
            "HRR storage near capacity: SNR=%.2f (dim=%d, n_items=%d). "
            "Retrieval accuracy may degrade.",
            snr, dim, n_items
        )
    return snr
```

- **SNR = sqrt(dim / n_items)**，SNR < 2.0 时 retrieval 退化
- 暗示 dim=1024 时，n_items > 256 时开始退化（O(sqrt(dim)) 容量）

### 70.5 翻译：旁路型如何借鉴

**这个机制在 Hermes 中是"内置"的，但它的代数检索思想对旁路型有价值**：

1. **Entity-based structured memory**：BlueCortexCE Observation 目前是扁平的，可考虑引入 entity-role 结构化存储
2. **Algebraic retrieval**：不需要精确匹配，通过 unbind 运算检索关联内容
3. **SNR monitoring**：BlueCortexCE 目前无容量监控，可在 SearchService 中添加存储密度警告

**然而注意**：HRR 是为"绑定到 Agent 内部"设计的，在旁路型架构下直接搬套 HRR 意义不大。**真正有价值的是其背后的"结构化编码 + 代数检索"思想**。

**优先级**：低（思想参考，不适合直接实现）

---

## 71. on_pre_compress Hook — 设计意图与实现的双重脱节（v6.1 新增）

### 71.1 发现背景

`on_pre_compress` Hook 被设计用于：**在上下文压缩丢弃历史消息之前，通知外部记忆 Provider 提取洞察**。预期用途是：
1. Provider 从即将被压缩的消息中提取有价值的信息
2. Provider 返回一段文本（insights）
3. 这段文本被注入到压缩后的 context 中（作为 system prompt 或额外消息）
4. **关键**：压缩后，这些信息不会丢失

### 71.2 Bug 1：返回值被静默丢弃

**文件**: `run_agent.py:6804`

```python
# Notify external memory provider before compression discards context
if self._memory_manager:
    try:
        self._memory_manager.on_pre_compress(messages)  # ← 返回值被丢弃！
    except Exception:
        pass
```

**影响**：`MemoryManager.on_prepress()` 返回 `str`，但调用方完全忽略返回值。即使 Provider 返回了有价值的 insights，这些信息也不会进入压缩后的 context。

**正确的行为应该是**：
```python
pre_insights = self._memory_manager.on_pre_compress(messages)
if pre_insights and pre_insights.strip():
    compressed.insert(0, {"role": "system", "content": pre_insights})
```

### 71.3 Bug 2：ByteRover 实现返回空字符串

**文件**: `plugins/memory/byterover/__init__.py:282-310`

即使 Bug 1 被修复，ByteRover 的实现也不会贡献任何内容：

```python
def on_pre_compress(self, messages: List[Dict[str, Any]]) -> str:
    """Extract insights before context compression discards turns."""
    # ... 提取最后10条消息 ...

    def _flush():
        _run_brv(["curate", "--", f"[Pre-compression context]\n{combined}"], ...)
        # 异步写入，不返回任何内容

    t = threading.Thread(target=_flush, daemon=True, name="brv-flush")
    t.start()
    return ""  # ← 总是返回空字符串！
```

**设计意图 vs 实现**：
- **MemoryManager 层面的设计意图**：Provider 返回文本，注入到压缩 context
- **ByteRover 的实际实现**：异步 flush 到外部存储，不返回任何内容

### 71.4 架构性脱节：两种不同的语义

| 维度 | MemoryManager 预期语义 | ByteRover 实际语义 |
|------|----------------------|------------------|
| 目的 | 返回文本给 context 用 | 异步写外部存储 |
| 返回值 | 有意义的内容字符串 | 始终 `""` |
| 注入位置 | 压缩后的 context | 不注入（N/A） |
| 是否阻塞 | 不阻塞（异步 thread） | 不阻塞（daemon thread） |

**结论**：ByteRover 使用 `on_pre_compress` 做"fire-and-forget async save"，而 MemoryManager 期望"返回文本注入 context"。这是语义层面的设计脱节。

### 71.5 影响范围

| Provider | `on_pre_compress` 实现 | 返回值 | Bug 1 影响 |
|----------|----------------------|--------|-----------|
| ByteRover | ✅ 有实现 | `""` | 无影响（即使修复也无效） |
| Honcho | ❌ no-op（基类） | N/A | 无 |
| Holographic | ❌ no-op（基类） | N/A | 无 |
| Hindsight | ❌ no-op（基类） | N/A | 无 |
| Mem0 | ❌ no-op（基类） | N/A | 无 |
| RetainDB | ❌ no-op（基类） | N/A | 无 |
| Supermemory | ❌ no-op（基类） | N/A | 无 |
| OpenViking | ❌ no-op（基类） | N/A | 无 |

**唯一有实现的 Provider（ByteRover）返回空字符串**，所以 Bug 1 在实践中没有影响——但这是"空实现的空实现"（虽然有代码，但什么都不贡献）。

### 71.6 翻译：旁路型如何借鉴

**Hermes 的 `on_pre_compress` 机制在 BlueCortexCE 中没有直接对应物**，因为：
- BlueCortexCE 是旁路型，不参与 Agent 的上下文压缩决策
- BlueCortexCE 的 Observation 写入是独立发生的，不依赖 context compression 时机

**但这个 Bug 对 BlueCortexCE 有间接的参考价值**：

1. **Hook 返回值必须被使用**：如果 BlueCortexCE 未来引入类似的 hook 机制，必须确保返回值被正确处理，不能静默丢弃

2. **语义必须对齐**：如果 hook 有两种可能的语义（"返回文本" vs "执行副作用"），必须在接口层面明确约定，防止 Provider 实现与接口预期脱节

3. **Bug 的发现方式**：这个 Bug 是通过代码审查发现的，不是通过测试。说明 Hermes 缺少对 `on_pre_compress` 返回值的集成测试。

### 71.7 BlueCortexCE 关联发现：summary.txt 模板损坏

**文件**: `backend/src/main/resources/prompts/summary.txt`

在探索 `on_pre_compress` 相关代码时，意外发现 BlueCortexCE 的 `summary.txt` 模板文件**被损坏**——文件内容是 LLM session 的 PROGRESS SUMMARY CHECKPOINT 输出，而非实际的 summary prompt 模板。

```bash
$ cat backend/src/main/resources/prompts/summary.txt
PROGRESS SUMMARY CHECKPOINT
===========================
Write progress notes of what was done...
[... LLM session output ...]
```

**影响评估**：
- `SummaryGenerationService` 目前通过硬编码的 system prompt 绕过损坏的模板
- 系统仍然工作（`digest` observations 才是真正驱动 LLM 输出的内容）
- 但 `summary.txt` 作为模板的接口设计意图被破坏

**根本原因**：`summary.txt` 在初始 commit 时就是损坏的（`git log` 证实），疑似导入时的文件替换错误。

**建议**：立即重建 `summary.txt`，参考 `code.json` 中的 `summary_instruction` / `summary_format_instruction` 等字段，设计符合 Hermes 11段式模板精神的 summary prompt。

---

## 72. Honcho per-repo Session 策略确认 + MemoryTool 实现细节（v6.2 新增）

### 72.1 Honcho per-repo 策略 — `_git_repo_name` 确认

> **文件**: `plugins/memory/honcho/client.py:405-419`

**问题**：上轮遗留：Honcho `per-repo` session 策略中，`_git_repo_name` 如何实现？在无 git 环境下是否退化到 `per-directory`？

**确认结论**：实现非常简洁，逻辑清晰：

```python
# client.py:405-419
def _git_repo_name(cwd: str) -> str | None:
    """Return the git repo root directory name, or None if not in a repo."""
    import subprocess
    try:
        root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, cwd=cwd, timeout=5,
        )
        if root.returncode == 0:
            return Path(root.stdout.strip()).name
    except (OSError, subprocess.TimeoutExpired):
        pass
    return None
```

**关键设计点**：
- 5 秒超时保护：防止 git 命令卡住（如网络文件系统）
- `returncode != 0` 时返回 None（不是空字符串）—— 调用方能区分"无 git repo"和"执行失败"
- 返回 `Path(...).name`（只取目录名，不是完整路径）

**退化行为**：在 `resolve_session_name` 中：
```python
# client.py:462
base = self._git_repo_name(cwd) or Path(cwd).name  # 退化到 per-directory
```
当 `_git_repo_name` 返回 None 时，fallback 到 `Path(cwd).name` — 即 `per-directory` 策略。

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的 session 隔离策略可以借鉴这个设计
- `workspace_id`（cwd hash）可以作为 session 隔离的第二维度
- git repo 检测的 5 秒超时值得参考

### 72.2 Honcho `resolve_session_name` 六级解析顺序

> **文件**: `plugins/memory/honcho/client.py:420-470`

**完整解析顺序**（优先级从高到低）：

| 优先级 | 策略 | 来源 | 说明 |
|--------|------|------|------|
| 1 | Manual override | `sessions` map（用户配置） | 手动指定目录→session 映射，最高层级 |
| 2 | /title remap | `session_title` 参数 | 用户通过 `/title` 命令重命名 session |
| 3 | per-session | `session_id`（Hermes 生成） | 每次运行新建 Honcho session |
| 4 | per-repo | `git rev-parse --show-toplevel` | 每个 git 仓库一个 session |
| 5 | per-directory | `Path(cwd).name` | 每个工作目录一个 session（默认） |
| 6 | global | `workspace name` | 全局单一 session |

**Title sanitization**（优先级 2 特有）：
```python
# client.py:441-445
sanitized = re.sub(r'[^a-zA-Z0-9_-]', '-', session_title).strip('-')
```
只允许字母、数字、下划线、连字符，空格替换为 `-`。

**`session_peer_prefix` 条件**：优先级 3/4/5 在有 `session_peer_prefix` 和 `peer_name` 时，会给 session name 加上 `{peer_name}-` 前缀。

### 72.3 MemoryTool Schema 与实现的细微不一致

> **文件**: `tools/memory_tool.py:464-509`

**Schema 定义**（`MEMORY_SCHEMA`）：
```python
"properties": {
    "old_text": {
        "type": "string",
        "description": "Short unique substring identifying the entry to replace or remove.",
    },
    ...
},
"required": ["action", "target"],  # old_text 不在 required 中
```

**实现检查**（`memory_tool` 函数）：
```python
elif action == "remove":
    if not old_text:  # ← 实现了 required 检查，但 schema 未声明
        return tool_error("old_text is required for 'remove' action.", ...)
```

**分析**：
- `content` 对 `add`/`replace`：Schema 和实现一致（都 required）
- `old_text` 对 `remove`：Schema 说 optional，实现说 required — **不一致**
- 这意味着 LLM 可能生成一个不带 `old_text` 的 `remove` 调用，触发工具错误

**影响评估**：低。因为 LLM 在调用 `remove` 时通常会提供 `old_text` 来标识要删除的 entry。但如果 LLM 忘记提供，工具会返回错误而非正常执行。

**更严重的不一致**：`content` 对 `replace` 的 required 检查：
```python
elif action == "replace":
    if not content:
        return tool_error("content is required for 'replace' action.", ...)
```
Schema 中 `content` 也标记为 optional（不在 required list 中），但实现要求它。

### 72.4 MemoryTool `_success_response` 返回完整 Entry 列表

> **文件**: `tools/memory_tool.py:391-403`

**关键发现**：每次工具调用成功后，返回的 JSON 中包含**当前完整的 entries 列表**：

```python
def _success_response(self, target: str, message: str = None) -> Dict[str, Any]:
    entries = self._entries_for(target)
    current = self._char_count(target)
    limit = self._char_limit(target)
    pct = min(100, int((current / limit) * 100)) if limit > 0 else 0

    resp = {
        "success": True,
        "target": target,
        "entries": entries,                    # ← 当前所有 entries
        "usage": f"{pct}% — {current:,}/{limit:,} chars",
        "entry_count": len(entries),          # ← 条目数
    }
```

**这个设计让 LLM 在每次 `memory` 工具调用后，都能获得当前记忆的完整快照**，从而知道剩余空间、已有条目数量。

**与 BlueCortexCE 对比**：
- BlueCortexCE 的 `/api/observations` 返回的是**分页**结果，不是一次性完整快照
- Hermes 的设计更适合"bounded memory"（有总字符限制），因为返回 entries 列表让 LLM 知道还剩多少空间
- BlueCortexCE 是 unbounded（理论上可以无限存储），所以不需要这个设计

### 72.5 本轮新增借鉴点汇总

| 发现 | Hermes 做法 | BlueCortexCE 现状 | 优先级 |
|------|-------------|------------------|--------|
| `old_text` Schema 不一致 | 实现 required，Schema optional | 一致 | 低（Hermes bug，不值得借鉴） |
| 工具返回完整 entries 列表 | 每次工具调用返回当前所有 entries + usage | 分页返回，无 usage 信息 | 低（BlueCortexCE unbounded，不需要） |
| `resolve_session_name` 解析顺序 | 6 级优先级，手动 override > /title > per-session > per-repo > per-directory > global | 较简单 | 中（可作为 BlueCortexCE session 策略设计参考） |
| `_git_repo_name` 5s 超时 | `subprocess.run(timeout=5)` | N/A | 低（旁路型架构不需要） |

---

## 73. Session Search Tool — `_format_conversation` 截断 + 亲缘链排除（v6.3 新增）

> **本节为 v6.3 新增**，分析 `session_search_tool.py` 中的两个之前未详细覆盖的机制：工具输出截断算法和会话列表的亲缘链排除逻辑。

### 73.1 `_format_conversation` — 500-char 双端截断

**文件**: `tools/session_search_tool.py:56-89`

`session_search_tool.py` 的 `_format_conversation` 函数在将对话序列化为文本供 LLM summarizer 使用时，对工具输出进行了特殊的双端截断：

```python
# session_search_tool.py:66-68
if role == "TOOL" and tool_name:
    if len(content) > 500:
        content = content[:250] + "\n...[truncated]...\n" + content[-250:]
    parts.append(f"[TOOL:{tool_name}]: {content}")
```

**关键参数**：
- 阈值：500 chars（超过才截断）
- 截断方式：**头部 250 + 尾部 250**（保留首尾两端）
- 分隔符：`\n...[truncated]...\n`
- 目的：保留工具输出的**首尾关键信息**（如命令输出的开头错误信息和最终结果）

**与 ContextCompressor 的 `_summarize_tool_result` 对比**：

| 维度 | `_format_conversation` (session_search) | `_summarize_tool_result` (context_compressor) |
|------|----------------------------------------|----------------------------------------------|
| 目标 | session 历史检索的输入 | 上下文压缩 Phase 1 预热 |
| 截断阈值 | 500 chars | 200 chars（摘要化而非截断） |
| 保留策略 | 头部 250 + 尾部 250 | 工具特定 1 行摘要 |
| LLM 调用 | 后续 summarizer 会调用 | 无（规则型） |
| 工具 call arguments | ❌ 不包含（仅显示 tool name） | ✅ 包含在摘要中 |

**特别注意**：对于 ASSISTANT 消息，`_format_conversation` **只显示 tool call names**，不包含 arguments：

```python
# session_search_tool.py:73-79
if tool_calls and isinstance(tool_calls, list):
    tc_names = [tc.get("name") or tc.get("function", {}).get("name", "?") for tc in tool_calls]
    parts.append(f"[ASSISTANT]: [Called: {', '.join(tc_names)}]")
    if content:
        parts.append(f"[ASSISTANT]: {content}")
```

这与 `ContextCompressor._serialize_for_summary`（包含完整 tool arguments）形成鲜明对比——**session_search 刻意丢弃 tool arguments 以节省 token**。

### 73.2 `_list_recent_sessions` — 双重排除机制

**文件**: `tools/session_search_tool.py:245-290`

`_list_recent_sessions` 函数（无 query 时的快速路径）实现了**双重会话排除**，确保返回的列表中不包含当前会话的任何亲缘成员：

```python
# session_search_tool.py:255-270
# 第一重：排除当前 root session 及其整个亲缘链
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

# 遍历时排除
for s in sessions:
    if current_root and (sid == current_root or sid == current_session_id):
        continue
    # 第二重：排除任何有 parent_session_id 的子会话
    if s.get("parent_session_id"):
        continue
```

**排除逻辑**：
1. **上行遍历**：从当前 session_id 开始，遍历 parent_session_id 链，找到最远的 root
2. **排除 root + 整链**：排除 root session 及其所有后代
3. **排除所有子会话**：排除任何有 `parent_session_id` 的会话（delegation 子会话）

**效果**：返回的"最近会话"列表中，**绝对不会出现**与当前会话相关的任何会话（无论亲缘关系多近）。

### 73.3 Supermemory `capture_mode="everything"` trivial 过滤确认

**文件**: `plugins/memory/supermemory/__init__.py:563-580`

**待确认项**：Supermemory trivial 过滤在 `capture_mode="everything"` 下的行为

**确认结果**（v6.3 确认）：

```python
# supermemory/__init__.py:571-576
if self._capture_mode == "all":
    if len(clean_user) < _MIN_CAPTURE_LENGTH or len(clean_assistant) < _MIN_CAPTURE_LENGTH:
        return
    if _is_trivial_message(clean_user):
        return
# capture_mode != "all"（即 "everything"）时：跳过以上所有过滤
```

**行为差异**：

| 过滤条件 | `capture_mode="all"` | `capture_mode="everything"` |
|---------|---------------------|----------------------------|
| 最小长度检查 | ✅ 两者均 >= 10 chars | ❌ 跳过 |
| Trivial regex 检查 | ✅ 跳过 `^(ok\|okay\|thanks...)$` | ❌ 跳过 |
| 空内容检查 | ✅ 两者均非空 | ✅ 仍保留 |

**即**：在 `capture_mode="everything"` 下，**只有空内容会被跳过**，所有短消息和 acknowledgment 都会被 capture。

### 73.4 翻译：旁路型如何借鉴

| 维度 | Hermes 做法 | BlueCortexCE 现状 | 借鉴思路 |
|------|-----------|-----------------|---------|
| 工具输出截断 | 500-char 双端截断（session_search） | 无对应（我们不做 session 检索摘要） | 可为 session history API 提供类似截断策略 |
| Tool arguments 包含 | session_search 不包含，compressor 包含 | N/A | 作为 API 设计参考：不同用途可以有不同的信息粒度 |
| 亲缘链排除 | 双重排除（root 链 + 子会话） | 无 | BlueCortexCE 的 `/api/sessions` 可以借鉴：排除当前会话的 delegation 链 |
| Supermemory trivial | "all" 模式过滤，"everything" 不过滤 | N/A | 可配置的 trivial 过滤对 BlueCortexCE 有参考价值 |

**优先级**：低（均为 session_search_tool 专用逻辑，BlueCortexCE 无直接对应功能）

---

## 67. 待进一步确认（v6.2 更新）

### 67.1 本轮已确认项目（v6.2）

1. ✅ ~~Honcho per-repo `_git_repo_name` 实现~~ — **v6.2 已确认**：`git rev-parse --show-toplevel`，5s 超时，返回 None 时退化到 `Path(cwd).name`（per-directory）
2. ✅ ~~Honcho `resolve_session_name` 六级解析顺序~~ — **v6.2 已确认**：manual override → /title → per-session → per-repo → per-directory → global
3. ✅ ~~MemoryTool Schema 不一致~~ — **v6.2 已确认**：`old_text` 和 `content` 在 Schema 中 optional，但实现 required
4. ✅ ~~MemoryTool `_success_response` 返回完整 entries 列表~~ — **v6.2 已确认**：每次成功调用返回当前所有 entries + usage 百分比

### 67.2 本轮已确认项目（v6.3）

1. ✅ ~~Supermemory trivial 过滤在 capture_mode="everything" 下的行为~~ — **v6.3 已确认**：`capture_mode="everything"` 完全跳过 trivial regex 检查和最小长度检查，只保留空内容过滤
2. ✅ ~~`_format_conversation` 500-char 双端截断~~ — **v6.3 已确认**：超过 500 chars 时保留头部 250 + 尾部 250，不包含 tool arguments；超过 500 chars 时才截断
3. ✅ ~~`_list_recent_sessions` 亲缘链双重排除~~ — **v6.3 已确认**：上行遍历找到 root session + 排除所有有 parent_session_id 的子会话

### 67.3 仍待确认项目（v6.3）

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model — 云端 LLM 如何解析 SOUL.md** — 需要看 RetainDB 云端实现
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
6. ~~ContextCompressor 与 memory_manager 的真实集成方式~~ — ✅ **已确认**：双重脱节（返回值丢弃 + ByteRover 返回空字符串）
7. **Honcho write_frequency="realtime" 的具体实现** — 确认是轮询还是事件驱动
8. **Supermemory 多容器检索隔离的具体行为** — container_tag 如何影响 search_results 排序？
9. **Supermemory `add_memory` 云端 LLM 如何使用 entity_context**
10. **ByteRover `brv query` 算法** — fuzzy text → LLM-driven search 的具体实现
11. **Honcho seed_ai_identity 的完整实现** — 是否真的通过 Honcho API 写入 SOUL.md 内容？

### 67.4 本轮已确认项目（v6.1）

1. ✅ ~~on_pre_compress Hook Bug 1~~ — **v6.1 已确认**：`run_agent.py:6804` 返回值被静默丢弃
2. ✅ ~~on_pre_compress Hook Bug 2~~ — **v6.1 已确认**：ByteRover 实现返回空字符串，设计意图与实现双重脱节
3. ✅ ~~BlueCortexCE summary.txt 损坏~~ — **v6.1 新发现**：初始 commit 即损坏，当前依赖硬编码 system prompt 绕过

### 67.5 仍待确认项目（v6.1）

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model — 云端 LLM 如何解析 SOUL.md** — 需要看 RetainDB 云端实现
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
6. ~~ContextCompressor 与 memory_manager 的真实集成方式~~ — ✅ **已确认**：双重脱节（返回值丢弃 + ByteRover 返回空字符串）
7. **Honcho write_frequency="realtime" 的具体实现** — 确认是轮询还是事件驱动
8. **Supermemory 多容器检索隔离的具体行为** — container_tag 如何影响 search_results 排序？
9. **Supermemory `add_memory` 云端 LLM 如何使用 entity_context**
10. **ByteRover `brv query` 算法** — fuzzy text → LLM-driven search 的具体实现
11. **Honcho seed_ai_identity 的完整实现** — 是否真的通过 Honcho API 写入 SOUL.md 内容？
12. **Supermemory trivial 过滤在 capture_mode="everything" 下的行为**

### 67.6 本轮已确认项目（v6.0）

1. ✅ ~~Built-in Memory Tool Frozen Snapshot Pattern~~ — **v6.0 已详细分析**：`_system_prompt_snapshot` vs live entries，os.replace 原子写入，独立 .lock 文件
2. ✅ ~~Built-in Memory Tool 威胁扫描~~ — **v6.0 已详细分析**：13 个威胁 pattern + 不可见 Unicode 检查
3. ✅ ~~Built-in Memory Tool Schema 指导~~ — **v6.0 已详细分析**：WHEN TO SAVE + SKIP 规则 + 双 target 语义
4. ✅ ~~Session Search 三阶段截断算法~~ — **v6.0 已详细分析**：phrase → proximity(200-char) → individual term
5. ✅ ~~Session Search 亲缘链排除~~ — **v6.0 已详细分析**：_resolve_to_parent 上行遍历，排除整个 delegation 链
6. ✅ ~~Session Search Fallback~~ — **v6.0 已详细分析**：summarizer 失败 → raw preview（fixes #3409）
7. ✅ ~~Session Search Zero-LLM Browse~~ — **v6.0 已详细分析**：无 query → 直接 DB 返回，无 LLM 调用
8. ✅ ~~Holographic HRR 代数运算~~ — **v6.0 已详细分析**：bind/unbind/bundle + 确定性 SHA-256 atom
9. ✅ ~~Holographic SNR 估计~~ — **v6.0 已详细分析**：SNR = sqrt(dim/n_items)，SNR<2.0 检索退化

### 67.7 仍待确认项目（v6.0）

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model — 云端 LLM 如何解析 SOUL.md** — 需要看 RetainDB 云端实现
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
6. **ContextCompressor 与 memory_manager 的真实集成方式** — `on_pre_compress` 被丢弃说明设计意图与实现脱节，是否有计划修复？
7. **Honcho write_frequency="realtime" 的具体实现** — 确认是轮询还是事件驱动
8. **Supermemory 多容器检索隔离的具体行为** — container_tag 如何影响 search_results 排序？
9. **Supermemory `add_memory` 云端 LLM 如何使用 entity_context**
10. **ByteRover `brv query` 算法** — fuzzy text → LLM-driven search 的具体实现
11. **Honcho seed_ai_identity 的完整实现** — 是否真的通过 Honcho API 写入 SOUL.md 内容？
12. **Supermemory trivial 过滤在 capture_mode="everything" 下的行为**

---

## 附录：BlueCortexCE 可落地的高优先级借鉴点（v6.0 汇总）

### 🔴 立即可落地（高优先级）

1. **静默失败防止**（Session Search fix #3409）：`/api/context/generate` 即使 LLM summarization 失败，也要返回降级响应（raw content 或明确的 marker），而非 empty
2. **Zero-LLM-Cost Recent Sessions**：在 SearchService 添加"无 query → 直接 DB 返回 session 列表"的快速路径
3. **Observation 内容安全扫描**：MEMORY_THREAT_PATTERNS 思想可移植，为 Observation 提供可选的 content 安全扫描 API

### 🟡 中期改进（中优先级）

4. **智能截断策略**：借鉴 `_truncate_around_matches` 的三层降级（phrase → proximity → individual term）优化 BlueCortexCE 的 context 截断
5. **SNR/容量监控**：为 SearchService 添加存储密度警告机制
6. **Frozen Snapshot 模式**：为 `/api/context/generate` 提供"session snapshot" 返回模式

### 🟢 长期探索（低优先级）

7. **Entity-Role 结构化存储**：借鉴 Holographic 的 fact encoding 思想，在 Observation 中引入 entity-role 结构
8. **代数检索**：通过 bind/unbind 实现更灵活的关联检索


1. ✅ ~~Supermemory trivial filtering 具体 regex~~ — **v5.4 已详细分析**：`^(ok|okay|thanks|thank you|got it|sure|yes|no|yep|nope|k|ty|thx|np)\.?$` + min-length 双保险，sync_turn 中 4 层过滤
2. ✅ ~~Supermemory entity_context 具体内容~~ — **v5.4 已详细分析**：DEFAULT 模板 1500 chars 上限，`_clamp_entity_context` 截断，add_memory 传递 entity_context 给云端 LLM
3. ✅ ~~Supermemory profile_frequency 节流~~ — **v5.4 已详细分析**：默认 50 轮节流 static 画像获取，dynamic facts 和 search_results 始终包含
4. ✅ ~~Supermemory container_tag sanitization~~ — **v5.4 已详细分析**：`_sanitize_tag` 防止 injection（只允许 `[a-zA-Z0-9_]`），`_resolve_tool_container_tag` 拒绝未知容器
5. ✅ ~~ByteRover on_pre_compress 唯一实现~~ — **v5.4 已详细分析**：唯一真正执行 pre-compress flush 的 provider（提取最近 10 条消息，500 chars 截断，异步 curate）；所有其他 provider 均 no-op
6. ✅ ~~ByteRover queue_prefetch no-op~~ — **v5.4 已详细分析**：prefetch() 直接同步执行（最多 10s），不依赖 queue_prefetch + prefetch 分离
7. ✅ ~~ByteRover CLI wrapper 超时分离~~ — **v5.4 已详细分析**：query=10s / curate=120s，线程安全 brv 路径缓存
8. ✅ ~~OpenViking atexit 安全网~~ — **v5.4 已详细分析**：`_atexit_commit_sessions` 在进程退出时 commit pending sessions，daemon thread 不 join
9. ✅ ~~OpenViking 6 类自动提取~~ — **v5.4 已详细分析**：profile/preferences/entities/events/cases/patterns，session commit 时触发
10. ✅ ~~Supermemory `_clean_text_for_capture`~~ — **v5.4 已详细分析**：去除 `<supermemory-context>` 和 `<supermemory-containers>` 标签，防止循环注入

### 67.8 仍待确认项目（v5.4）

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model — 云端 LLM 如何解析 SOUL.md** — 需要看 RetainDB 云端实现
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
6. **ContextCompressor 与 memory_manager 的真实集成方式** — `on_pre_compress` 被丢弃说明设计意图与实现脱节，是否有计划修复？（需要看 GitHub issue/PR）
7. **Honcho write_frequency="realtime" 的具体实现** — 确认是轮询还是事件驱动
8. **OpenViking 6 类记忆提取的具体 prompt** — 云端 API，本地无具体 prompt
9. **Supermemory 多容器检索隔离的具体行为** — container_tag 如何影响 search_results 排序？
10. **Supermemory `add_memory` 云端 LLM 如何使用 entity_context** — 是否真的是 LLM 处理 content + entity_context 的组合？
11. **ByteRover `brv query` 算法** — fuzzy text → LLM-driven search 的具体实现
12. **Honcho seed_ai_identity 的完整实现** — 是否真的通过 Honcho API 写入 SOUL.md 内容？
13. **Supermemory trivial 过滤在 capture_mode="everything" 下的行为** — 是否跳过 trivial 检查？

### 59.1 本轮已确认项目（v5.3）

1. ✅ ~~Supermemory sync_turn 完整过滤链~~ — **v5.2 已详细分析**：4 层过滤（active/write/min-length/trivial） + 结构化格式 + entity_context 注入
2. ✅ ~~Supermemory on_session_end batch ingest~~ — **v5.2 已详细分析**：ingest_conversation API + 极短 session 跳过
3. ✅ ~~Honcho vs RetainDB dialectic 架构对比~~ — **v5.2 已详细分析**：多 Agent 观察 vs 单一问答 + 推理级别算法相同
4. ✅ ~~RetainDB Agent Self-Model 完整流程~~ — **v5.2 已详细分析**：seed_agent_identity + get_agent_model + 三并行 prefetch
5. ✅ ~~RetainDB reasoning_level 算法~~ — **v5.2 已确认**：与 Honcho 算法完全相同（120/400 chars 分段）
6. ✅ ~~ContextCompressor Phase 1-4 算法~~ — **v5.3 已详细分析**：Prune(去重+摘要) → 边界确定 → LLM摘要 → 组装 + _sanitize_tool_pairs
7. ✅ ~~on_pre_compress Hook Bug~~ — **v5.3 已确认**：run_agent.py:6804 返回值被完全丢弃，静默失败
8. ✅ ~~11段式 Summary Template~~ — **v5.3 已详细分析**：Goal/Completed Actions/Active State/In Progress/Blocked/Key Decisions/Resolved Questions/Pending User Asks/Relevant Files/Remaining Work/Critical Context + iterative update 机制
9. ✅ ~~Anti-thrashing + Fallback~~ — **v5.3 已确认**：连续2次<10%保存则跳过 + summary_model fallback chain + static fallback marker

### 59.2 仍待确认项目（v5.3）

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model — 云端 LLM 如何解析 SOUL.md** — 需要看 RetainDB 云端实现
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）
6. **Supermemory 容器隔离的具体实现** — container_tag 如何影响检索？（需要看 Supermemory API 行为）
7. **ContextCompressor 与 memory_manager 的真实集成方式** — `on_pre_compress` 被丢弃说明设计意图与实现脱节，是否有计划修复？（需要看 GitHub issue/PR）
8. **Honcho write_frequency="realtime" 的具体实现** — 确认是轮询还是事件驱动
