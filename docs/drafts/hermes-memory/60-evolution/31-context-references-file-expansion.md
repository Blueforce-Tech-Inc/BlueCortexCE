# Context References — @-Prefix File/URL/Git Expansion

> **Source**: `agent/context_references.py` (520 lines, wired in `cli.py:7568` + `gateway/run.py:3345`)
> **Date**: 2026-04-24
> **Related**: `04`/`06` context injection · [`20-recommendations/06-context-file-scanning-deep-dive.md`](../20-recommendations/06-context-file-scanning-deep-dive.md)（`_scan_memory_content` contrast）

---

## 1. 定位与目的

`context_references.py` 是 Hermes Agent 的**用户消息预处理层**，在消息进入模型之前，将用户用 `@` 前缀引用的**本地文件、文件夹、Git 内容、URL** 展开为可直接阅读的文本块。

与 `_scan_memory_content`（`memory_tool.py`）的关系：

| 维度 | `_scan_memory_content` | `preprocess_context_references` |
|------|----------------------|-------------------------------|
| **触发时机** | Tool result 返回后（`memory_tool` 执行后） | User prompt 提交前（模型推理前） |
| **扫描对象** | Tool result 中的敏感路径 | User message 中的 `@` 引用 |
| **功能** | 敏感文件泄露检测 | 主动上下文注入 |
| **方向** | 输出安全 | 输入增强 |

两者互为补充：`_scan_memory_content` 防泄露，`preprocess_context_references` 做注入。

---

## 2. 支持的引用类型

```
@file:main.py              → 文件全部内容
@file:"src/utils.py":10-20 → 文件指定行范围（支持多种引号格式）
@file:src/utils.py:10-20   → 同上（无引号格式）
@folder:src/               → 文件夹目录树（200 entry 上限）
@diff                      → git diff
@staged                    → git diff --staged
@git:3                     → 最近 3 次 commit 的 patch
@url:https://example.com   → 网页内容（web_extract_tool + markdown）
```

### 引用格式的正则

```python
REFERENCE_PATTERN = re.compile(
    rf"(?<![\w/])@(?:(?P<simple>diff|staged)\b|(?P<kind>file|folder|git|url):(?P<value>{_QUOTED_REFERENCE_VALUE}(?::\d+(?:-\d+)?)?|\S+))"
)
```

- `(?<![\w/])` — `@` 前面不能是字母/数字/斜杠（防误匹配邮箱）
- 支持三种引号：反引号、单引号、双引号
- 行范围语法：`:start-end`（无引号）或 `:start`（单行）

---

## 3. 安全机制（Path Containment）

### 3.1 精确阻断

```python
_SENSITIVE_HOME_FILES = (
    Path(".ssh") / "authorized_keys",
    Path(".ssh") / "id_rsa",
    Path(".ssh") / "id_ed25519",
    Path(".ssh") / "config",
    Path(".bashrc"), Path(".zshrc"), Path(".profile"),
    Path(".bash_profile"), Path(".zprofile"),
    Path(".netrc"), Path(".pgpass"), Path(".npmrc"), Path(".pypirc"),
)
blocked_exact.add(hermes_home / ".env")
```

### 3.2 目录阻断

```python
_SENSITIVE_HOME_DIRS = (".ssh", ".aws", ".gnupg", ".kube", ".docker", ".azure", ".config/gh")
_SENSITIVE_HERMES_DIRS = (Path("skills") / ".hub",)
```

**路径解析流程**：
1. `os.path.expanduser(target)` 展开 `~`
2. 相对路径 → 拼接 `cwd`
3. `.resolve()` 解析符号链接
4. 若设 `allowed_root`，验证 `resolved.relative_to(allowed_root)`（不在外部）
5. 精确匹配阻断或父目录包含检测

### 3.3 对比 CE

CE `IngestionController.handleUserPrompt` **仅有长度截断**，无路径安全扫描、无 `@` 展开机制。

---

## 4. Token Budget 管控

```python
hard_limit = max(1, int(context_length * 0.50))   # 50% 硬上限
soft_limit = max(1, int(context_length * 0.25))   # 25% 软警告
```

- **超过 50%**：拒绝注入，返回警告消息（CLI）或发送警告给用户（Gateway）
- **超过 25%**：发出警告但不阻断，注入仍执行
- `injected_tokens` 通过 `estimate_tokens_rough` 估算

**注意**：Token 计数基于 `model_metadata.estimate_tokens_rough`，是估算而非精确计数。

---

## 5. 文件展开细节

### 5.1 行范围支持

```python
# 三种格式等价：
@file:"src/utils.py":10-20
@file:`src/utils.py`:10-20
@file:src/utils.py:10-20
```

- `line_start = max(ref.line_start - 1, 0)`（0-indexed internal）
- `line_end = min(ref.line_end or ref.line_start, len(lines))`

### 5.2 二进制文件检测

```python
def _is_binary_file(path: Path) -> bool:
    # 1. MIME type check（非 text/ 前缀）
    # 2. 已知白名单：.py .md .txt .json .yaml .yml .toml .js .ts
    # 3. 读前 4096 字节查 \x00
```

### 5.3 文件夹列表构建

```python
def _build_folder_listing(path: Path, cwd: Path, limit: int = 200) -> str:
    # 优先用 rg --files（快），失败则 os.walk
    # 每个 entry 显示：类型(目录/文件) + 行数或大小
```

优先使用 `ripgrep --files` 获取文件列表（`rg_files`），如果 `rg` 不可用则降级到 `os.walk`。最多 200 个 entry。

---

## 6. URL 抓取

```python
async def _default_url_fetcher(url: str) -> str:
    from tools.web_tools import web_extract_tool
    raw = await web_extract_tool([url], format="markdown", use_llm_processing=True)
    payload = json.loads(raw)
    docs = payload.get("data", {}).get("documents", [])
    return str(doc.get("content") or doc.get("raw_content") or "").strip()
```

支持自定义 `url_fetcher`，支持同步/异步两种模式（CLI/Gateway 线程安全处理）。

---

## 7. 集成点

### 7.1 CLI (`cli.py:7568`)

```python
if isinstance(message, str) and "@" in message:
    _ctx_result = preprocess_context_references(
        message, cwd=os.getcwd(), context_length=_ctx_len)
    if _ctx_result.blocked:
        return "\n".join(_ctx_result.warnings) or "Context injection refused."
    message = _ctx_result.message
```

- 同步调用，使用当前工作目录
- 阻断时返回纯文本警告，不进入模型

### 7.2 Gateway (`gateway/run.py:3345`)

```python
if "@" in message_text:
    _ctx_result = await preprocess_context_references_async(
        message_text,
        cwd=_msg_cwd,
        context_length=_msg_ctx_len,
        allowed_root=_msg_cwd,  # 严格限制在工作目录
    )
    if _ctx_result.blocked:
        await _adapter.send(source.chat_id, "\n".join(_ctx_result.warnings))
        return None
```

- 异步调用，`allowed_root` 显式设为 `MESSAGING_CWD` 环境变量
- 阻断时通过平台 adapter 发警告给用户

---

## 8. CE 借鉴分析

### 8.1 差距

| 能力 | Hermes | CE |
|------|--------|-----|
| `@file` 展开 | ✅ 完整（行范围/二进制检测/敏感路径阻断） | ❌ |
| `@folder` 展开 | ✅ 200 entry 上限 | ❌ |
| `@git` 展开 | ✅ diff/staged/log | ❌ |
| `@url` 展开 | ✅ web_extract_tool | ❌ |
| Token budget 管控 | ✅ 25%/50% 双限 | ❌ |
| 路径安全扫描 | ✅ 双层（exact + dir） | ❌ 仅长度截断 |
| User message `@` 注入 | ✅ 预处理层 | ❌ |

### 8.2 可执行借鉴

**短期（低风险）**：
1. **输入扫描增强**：`IngestionController.handleUserPrompt` 增加基础路径安全扫描（检测 `~/.ssh/`、`.env` 等敏感路径模式），防止用户通过 prompt 注入路径遍历尝试
2. **Token budget 文档化**：明确 `/api/context/inject` 和 `/api/context/generate` 的 token 上限，并在响应 header 中体现使用量

**中期（中等成本）**：
3. **@文件引用注入 API**：参考 `context_references.py` 的引用格式解析，新增可选的 `@file`/`@folder` 展开端点，**作为 ICL 注入的补充**（不对应 Hermes 的 prompt 层注入，而是在 context generate 层面做文件内容展开）

**长期（高成本）**：
4. **完整 `@` 引用系统**：如果要实现类似 Hermes 的用户 prompt `@` 展开，需要在前端（webui/cli）拦截含 `@` 的消息，在后端展开后再提交。这涉及架构变更（旁路型系统需要在注入层而非 prompt 层处理）

---

## 9. 关键代码片段

### 引用解析

```python
def parse_context_references(message: str) -> list[ContextReference]:
    refs: list[ContextReference] = []
    for match in REFERENCE_PATTERN.finditer(message):
        # simple forms: @diff, @staged
        # kinded forms: @file:path, @folder:path, @git:N, @url:URL
        refs.append(ContextReference(
            raw=match.group(0),
            kind=kind,
            target=target,
            start=match.start(),
            end=match.end(),
            line_start=line_start,
            line_end=line_end,
        ))
    return refs
```

### Token budget 强制

```python
if injected_tokens > hard_limit:
    warnings.append(f"@ context injection refused: {injected_tokens} tokens exceeds 50% hard limit.")
    return ContextReferenceResult(
        message=message,  # 未注入，保留原始引用
        expanded=False, blocked=True,
        ...
    )
if injected_tokens > soft_limit:
    warnings.append(f"@ context injection warning: {injected_tokens} tokens exceeds 25% soft limit.")
```

### 引用 token 移除（注入后）

```python
def _remove_reference_tokens(message: str, refs: list[ContextReference]) -> str:
    pieces: list[str] = []
    cursor = 0
    for ref in refs:
        pieces.append(message[cursor:ref.start])
        cursor = ref.end
    pieces.append(message[cursor:])
    text = "".join(pieces)
    # 清理多余空格和标点
    text = re.sub(r"\s{2,}", " ", text)
    text = re.sub(r"\s+([,.;:!?])", r"\1", text)
    return text.strip()
```
