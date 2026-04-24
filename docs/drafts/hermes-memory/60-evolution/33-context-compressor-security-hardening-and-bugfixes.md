# 33 — Context Compressor 安全强化 + Bug Fix #10896（2026-04-25）

**更新**：2026-04-25 02:15（`e69526be` 之后 ~40 commits；本篇覆盖新增的 context_compressor + memory_manager + redact + gateway/hooks + hermes_cli/hooks 变更）

---

## §1 Critical Bug Fix #10896：压缩丢失活跃用户请求

### 1.1 问题根因

`_find_tail_cut_by_tokens()` 使用 `_align_boundary_backward()` 保护 tool_call/result 分组，但这会把 `cut_idx` 向后拉，可能越过最后一个 `user` 消息。当最后一个 user 消息落入压缩区域时：

1. LLM summarizer 会把它写入 "Pending User Asks" section
2. `SUMMARY_PREFIX` 指示模型 **只响应 summary 之后的 user 消息**
3. 实际任务请求从活跃上下文中消失
4. Agent 表现为：stall、重复已完成工作、或静默丢失最新请求

### 1.2 修复方案：`_ensure_last_user_message_in_tail()`

```python
def _ensure_last_user_message_in_tail(
    self, messages: List[Dict[str, Any]], cut_idx: int, head_end: int,
) -> int:
    """Guarantee the most recent user message is in the protected tail."""
    last_user_idx = self._find_last_user_message_idx(messages, head_end)
    if last_user_idx < 0:
        return cut_idx  # 无 user 消息，无事可做
    if last_user_idx >= cut_idx:
        return cut_idx  # 已在 tail，无需操作

    # 用户消息落入压缩区域 → 将 cut_idx 拉回到 user 消息处
    # user 消息本身是安全边界（无 tool_call/split 风险），无需再调 _align_boundary_backward
    return max(last_user_idx, head_end + 1)
```

在 `_find_tail_cut_by_tokens()` 末尾被调用：
```python
cut_idx = self._align_boundary_backward(messages, cut_idx)
cut_idx = self._ensure_last_user_message_in_tail(messages, cut_idx, head_end)
return max(cut_idx, head_end + 1)
```

### 1.3 架构意义

这是压缩算法层面的**语义完整性保护**。之前的压缩正确性只关注"不截断 tool_call 分组"，现在额外确保"不丢失活跃任务"——两者在某些边界情况下会冲突，需要显式优先级处理。

### 1.4 与 CE 的差距

CE 的 `ContextCompressor` **无等效保护机制**。CE 的 `_IclService.findTruncationIndex()` 只做 token 预算截断，不考虑 user 消息语义位置。如果 CE 未来引入类似压缩，**必须**考虑此 bug。

---

## §2 Tool Call Args JSON 截断修复（防止 Provider 400）

### 2.1 问题根因

之前的实现对 tool call `function.arguments` JSON 字符串做字节级截断：

```python
# BEFORE（有bug）
args = args[:200] + "...[truncated]"  # 直接字节截断
```

问题：`function.arguments` 是 JSON 编码字符串，直接字节截断会破坏 JSON 结构：

```json
{"path": "/foo/bar", "content": "# long markdown
...[truncated]   ← unterminated string, missing closing brace
```

MiniMax 等 Provider 对损坏的 JSON 直接返回 `invalid function arguments json string` + 400。**session 陷入死循环**：每轮重发相同损坏历史。

Issue: #11762

### 2.2 修复方案：`_truncate_tool_call_args_json()`

```python
def _truncate_tool_call_args_json(args: str, head_chars: int = 200) -> str:
    try:
        parsed = json.loads(args)  # 解析为对象
    except (ValueError, TypeError):
        return args  # 非JSON → 不处理

    def _shrink(obj: Any) -> Any:
        if isinstance(obj, str) and len(obj) > head_chars:
            return obj[:head_chars] + "...[truncated]"
        if isinstance(obj, dict):
            return {k: _shrink(v) for k, v in obj.items()}
        if isinstance(obj, list):
            return [_shrink(v) for v in obj]
        return obj

    shrunken = _shrink(parsed)
    return json.dumps(shrunken, ensure_ascii=False)  # 保持 JSON 有效性
```

关键设计：
- **先解析**：将 JSON 字符串转为对象
- **递归收缩**：只在叶节点（字符串）上截断，保留结构完整性
- **ensure_ascii=False**：避免 CJK/emoji 被转义为 `\uXXXX` 而膨胀
- **非 JSON 输入**：直接返回原字符串（某些 backend 用非标准格式）

### 2.3 与 CE 的差距

CE 的 Java 实现**使用 Jackson JSON 处理器**，天然避免此问题（Jackson 处理截断时保持 JSON 有效性）。但 CE 应注意：若未来在字符串字段（如 `extractedData` JSONB）做截断，不要使用字节级截断。

---

## §3 `redact.py` 全面强化：Secrets 不进入 Summary

### 3.1 新增敏感信息检测

| 检测类型 | 正则/逻辑 | 示例 |
|---------|----------|------|
| JWT tokens | `eyJ[A-Za-z0-9_-]{10,}(?:\.[A-Za-z0-9_=-]{4,}){0,2}` | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` |
| URL query 参数 | `_redact_query_string()` | `?access_token=xxx&code=yyy` |
| URL userinfo | `_redact_url_userinfo()` | `https://user:token@api.example.com` |
| Form body | `_redact_form_body()` | `access_token=xxx&refresh_token=yyy` |
| Discord mentions | `_DISCORD_MENTION_RE` | `<@123456789012345678>` |
| OAuth query 参数名 | `_SENSITIVE_QUERY_PARAMS` | `access_token`, `refresh_token`, `code`, `signature`... |
| OAuth body 参数名 | `_SENSITIVE_BODY_KEYS` | `authorization`, `private_key`, `client_secret`... |

### 3.2 调用点覆盖（压缩管线）

```python
# 1. tool args 在传给 summarizer 前
args = redact_sensitive_text(fn.get("arguments", ""))

# 2. message content 在传给 summarizer 前
content = redact_sensitive_text(msg.get("content") or "")

# 3. summarizer 输出 → 防止 LLM 忽略 prompt 指令直接回显 secrets
summary = redact_sensitive_text(content.strip())

# 4. structured summary prompt 新增：
#    "NEVER include API keys, tokens, passwords, secrets, credentials,
#     or connection strings in the summary — replace any that appear
#     with [REDACTED]."
```

### 3.3 架构意义：Defense in Depth

Prompt 指令告诉 LLM"不要写 secrets"，但 `redact()` 在**数据层面**强制执行。两者结合实现 defense-in-depth：即使 prompt 被忽略，secrets 也不会进入 summary。

### 3.4 与 CE 的差距

CE 的 `StructuredExtractionService` **无等效 redaction 机制**。这意味着：
- 用户可能在 observation prompt 中提及 credentials
- LLM 可能将这些信息写入 `extractedData`
- 未来若 CE 实现压缩/摘要功能，需要类似 redaction 管线

**可执行借鉴**：CE 可以在 `EmbeddingService` 或存储层引入 redaction，作为安全纵深防御。

---

## §4 Multimodal Content 安全操作

### 4.1 问题

Message content 可能是：
- 普通字符串
- 多模态 blocks 列表：`[{"type": "text", "text": "..."}, {"type": "image", "url": "..."}]`

之前的代码直接做字符串拼接，会破坏 multimodal 结构。

### 4.2 新增工具函数

```python
def _content_text_for_contains(content: Any) -> str:
    """Extract plain text from message content for substring checks."""
    if isinstance(content, list):
        return "\n".join(
            item.get("text", "")
            for item in content
            if isinstance(item, dict) and isinstance(item.get("text"), str)
        )
    return str(content)

def _append_text_to_content(content: Any, text: str, *, prepend: bool = False) -> Any:
    """Safely append/prepend text to message content."""
    if content is None:
        return text
    if isinstance(content, str):
        return text + content if prepend else content + text
    if isinstance(content, list):
        text_block = {"type": "text", "text": text}
        return [text_block, *content] if prepend else [*content, text_block]
    return str(content)
```

使用场景：
1. 检查 compression note 是否已注入：`if _compression_note not in _content_text_for_contains(existing)`
2. 注入 compression note 到 system message：`msg["content"] = _append_text_to_content(existing, "\n\n" + _compression_note)`
3. 注入 summary 到 tail：`msg["content"] = _append_text_to_content(msg.get("content"), merged_prefix, prepend=True)`

### 4.3 与 CE 的差距

CE 使用的是**纯 JSON 结构**（Spring Boot/Jackson），不涉及 multimodal，因此无此问题。但 CE 在设计数据结构时也应考虑"类型安全的内容操作"。

---

## §5 Structured Summary 模板强化

### 5.1 新增 "Active Task" 字段（首位）

```markdown
## Active Task
[THE SINGLE MOST IMPORTANT FIELD. Copy the user's most recent request or
task assignment verbatim — the exact words they used. If multiple tasks
were requested and only some are done, list only the ones NOT yet completed.
The next assistant must pick up exactly here.]
```

**设计意图**：`## Goal` 描述整体目标，但"**未完成的最新请求**"才是 task continuity 的关键。之前的模板没有显式捕获这个信息，导致压缩后 agent 可能从已完成的子任务继续。

### 5.2 语言感知

```python
# summarization prompt 新增：
# "Write the summary in the same language the user was using in the
#  conversation — do not translate or switch to English."
```

### 5.3 `focus_topic` 扩展安全指令

```python
prompt += f"""
Even for the focus topic, NEVER preserve API keys, tokens, passwords,
or credentials — use [REDACTED]."""
```

### 5.4 与 CE 的差距

CE 的 Summary prompt 模板**无等效 "Active Task" 字段**，也**无语言感知指令**。CE 的 Summary 主要用于 retrieval，而非 compression，故影响较小。但未来若 CE 实现 context compression，应考虑类似字段设计。

---

## §6 `memory_manager.py`：`sanitize_context()` 强化

### 6.1 防止递归上下文注入

之前的 `sanitize_context()` 只移除 `<memory-context>` fence tags。现在的版本：

```python
_INTERNAL_CONTEXT_RE = re.compile(
    r'<\s*memory-context\s*>[\s\S]*?</\s*memory-context\s*>',
    re.IGNORECASE,
)
_INTERNAL_NOTE_RE = re.compile(
    r'\[System note:\s*The following is recalled memory context,\s*NOT new user input\.\s*Treat as informational background data\.\]\s*',
    re.IGNORECASE,
)

def sanitize_context(text: str) -> str:
    """Strip fence tags, injected context blocks, and system notes."""
    text = _INTERNAL_CONTEXT_RE.sub('', text)  # 移除 <memory-context>...</memory-context>
    text = _INTERNAL_NOTE_RE.sub('', text)       # 移除系统备注
    text = _FENCE_TAG_RE.sub('', text)           # 移除旧式 fence tags
    return text
```

**安全意义**：防止用户/插件通过注入假的 `<memory-context>` 块来欺骗 memory system。这是之前 CE 安全缺口盘点（`05-ce-context-security-gap-inventory.md`）中识别的攻击面之一。

### 6.2 与 CE 的差距

CE 的 `SearchService` **无等效 sanitization**。如果 CE memory 通过 LLM 生成的内容（作为 observation）注入 context，恶意用户可能注入类似的标记性内容欺骗检索。

---

## §7 `gateway/hooks.py`：`emit_collect()` 新增

### 7.1 Decision-Style Hooks

之前的 `emit()` 只触发 handler，丢弃返回值。新增 `emit_collect()`：

```python
async def emit_collect(
    self,
    event_type: str,
    context: Optional[Dict[str, Any]] = None,
) -> List[Any]:
    """Fire handlers and return their non-None return values in order.

    Used for decision-style hooks (e.g. ``command:<name>`` policies
    that want to allow/deny/rewrite the command before normal dispatch).

    Exceptions from individual handlers are logged but do not abort
    the remaining handlers.
    """
    results: List[Any] = []
    for fn in self._resolve_handlers(event_type):
        try:
            result = fn(event_type, context)
            if asyncio.iscoroutine(result):
                result = await result
            if result is not None:
                results.append(result)
        except Exception as e:
            print(f"[hooks] Error in handler for '{event_type}': {e}", flush=True)
    return results
```

### 7.2 架构意义

这使得 Hook 系统可以用于**决策**（如权限检查、命令重写），而不仅仅是通知。示例场景：
- `command:reset` handler 返回 `{allow: false, reason: "in-progress-task"}`
- Gateway 检查返回值决定是否执行 reset

### 7.3 与 CE 的差距

CE **没有等效的 Hook 系统**。CE 的 lifecycle hooks（`SummaryEvent`, `ObservationEvent` 等）是单播的，**无收集返回值机制**。

---

## §8 `hermes_cli/hooks.py`：Shell Hooks CLI 管理工具

### 8.1 新增 385 行模块

这是全新的 CLI 功能，提供 `hermes hooks` 子命令系列：

| 子命令 | 功能 |
|--------|------|
| `hermes hooks list` | 列出所有配置的 shell hooks（事件、命令、allowlist 状态、脚本修改时间） |
| `hermes hooks test <event>` | 用合成 payload 测试 hooks（可加 `--for-tool` 过滤） |
| `hermes hooks revoke <command>` | 从 allowlist 移除指定命令 |
| `hermes hooks doctor` | 检查脚本存在性 + allowlist 状态 + mtime drift + JSON 输出有效性 |

### 8.2 `doctor` 检查项

1. 脚本存在且可执行（`chmod +x`）
2. 在 allowlist 中（否则 hook 不会 fire）
3. 脚本自 approval 后未被修改（mtime drift 检测）
4. 在 allowlist 中时：执行 JSON smoke test（用合成 payload）

### 8.3 设计亮点

- **安全优先**：`doctor` 对未 allowlist 的脚本**跳过执行**（不先执行未审查脚本）
- **Synthetic payload**：默认 payload 与 `invoke_hook()` 实际调用 shape 一致，确保测试真实
- **支持 `--payload-file`**：用户可提供自定义 payload JSON 文件

### 8.4 与 CE 的差距

CE 无等效 CLI 工具。CE 的 hook/插件管理依赖代码配置，无用户友好的检查/测试/撤销界面。

---

## §9 其他重要变更

### 9.1 `compress()` 重试路径 Bug Fix

```python
# BEFORE（错误）
return self._generate_summary(messages, summary_budget)  # retry with wrong var

# AFTER（正确）
return self._generate_summary(turns_to_summarize, focus_topic=focus_topic)  # passes correct var + focus_topic
```

### 9.2 `has_content_to_compress()` 实现

已在 `32` §2 记录，此处补充完整实现：

```python
def has_content_to_compress(self, messages: List[Dict[str, Any]]) -> bool:
    compress_start = self._align_boundary_forward(messages, self.protect_first_n)
    compress_end = self._find_tail_cut_by_tokens(messages, compress_start)
    return compress_start < compress_end  # 有内容可压缩？
```

---

## §10 可执行借鉴清单

| 优先级 | 行动项 | 对应 CE 缺口 |
|--------|--------|-------------|
| ⭐⭐⭐ 高 | CE 的 context 压缩（未来）必须实现 `_ensure_last_user_message_in_tail` 等效保护 | 无压缩语义完整性保护 |
| ⭐⭐ 高 | CE 的任何字符串截断必须使用 JSON-aware 截断（非字节截断） | 无此问题（Jackson 处理），但需注意 |
| ⭐⭐ 高 | CE 应在存储/检索层引入 `redact()` 等效 secrets 过滤 | 完全缺失 redaction 管线 |
| ⭐⭐ 中 | CE 的 `sanitize_context()` 应扩展支持移除注入的假 memory-context 块 | SearchService 无 sanitization |
| ⭐⭐ 中 | CE 的 lifecycle event system 可考虑 `emit_collect()` 模式（返回值收集） | 单播无收集 |
| ⭐ 低 | CE 的 Summary prompt 模板可考虑 "Active Task" 字段（类比"## Active Task"） | Summary 主要用于 retrieval，非压缩 |
| ⭐ 低 | CE 的 CLI 可考虑 `hermes hooks doctor` 类审计工具 | 无等效 |

---

## §11 文档体量

- 本文件：~13KB（远低于 50KB 上限）
- 最大单稿：`08`（46922 字节），无变化
