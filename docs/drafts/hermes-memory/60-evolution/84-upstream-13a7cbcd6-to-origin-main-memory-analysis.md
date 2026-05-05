# 上游新提交分析（13a7cbcd6 → origin/main，23 commits）

**时间**：2026-05-05 19:52 CST
**扫描范围**：`13a7cbcd6..origin/main`（23 commits）
**上次扫描起点**：`13a7cbcd6`（doc 83 覆盖）
**下次扫描起点**：`origin/main` `b93643c8f`

---

## 总览

23 个新 commit，大部分为 Teams/Telegram/Kanban/TUI 改进，记忆/上下文系统相关仅 **4 个**：

| 优先级 | Commit | 主题 |
|--------|--------|------|
| ⭐ P1 | `4a3e3e20e` | **Compression: 迭代压缩摘要连续性修复** |
| P2 | `2eef395e1` | Compaction: role=user fallback 添加结束标记 |
| P2 | `aacf36e94` | CLI: 手动 /compress 结果持久化到 session_db |
| P2 | `2a285d5ec` | Agent: 新增 `think_scrubber.py`（流式 reasoning block 过滤） |

---

## ⭐ P1 — `4a3e3e20e` fix(compression): preserve iterative summary continuity

### 背景问题

Hermes 的上下文压缩支持**迭代压缩**（多轮压缩）——当单次压缩不足以将上下文压缩到目标大小时，会触发多次压缩 pass。问题出在：当压缩窗口（`compress_start:compress_end`）内已经包含一个旧的 context summary（handoff 格式）时：

1. 旧 summary 被当作普通用户消息再次压缩
2. 导致信息丢失（summary 描述的历史被再次压缩）
3. `_previous_summary` 无法正确衔接，迭代链断裂
4. 成本浪费：重复压缩已有摘要内容

### 修复方案

新增 3 个方法到 `ContextCompressor`：

```python
@staticmethod
def _strip_summary_prefix(summary: str) -> str:
    """移除当前或旧版 handoff prefix，返回纯摘要体。"""
    text = (summary or "").strip()
    for prefix in (SUMMARY_PREFIX, LEGACY_SUMMARY_PREFIX):
        if text.startswith(prefix):
            return text[len(prefix):].lstrip()
    return text

@staticmethod
def _is_context_summary_content(content: Any) -> bool:
    """判断 content 是否为 handoff summary（用于压缩窗口内检测）。"""
    text = _content_text_for_contains(content).lstrip()
    return text.startswith(SUMMARY_PREFIX) or text.startswith(LEGACY_SUMMARY_PREFIX)

@classmethod
def _find_latest_context_summary(
    cls, messages: List[Dict[str, Any]], start: int, end: int
) -> tuple[Optional[int], str]:
    """在压缩窗口内从后向前扫描，找到最新的 handoff summary。"""
    for idx in range(end - 1, start - 1, -1):
        content = messages[idx].get("content")
        if cls._is_context_summary_content(content):
            return idx, cls._strip_summary_prefix(_content_text_for_contains(content))
    return None, ""
```

在 `_compress_inner` 中集成：

```python
summary_idx, summary_body = self._find_latest_context_summary(
    messages, compress_start, compress_end
)
if summary_idx is not None:
    # 如果发现已有 summary 且当前没有 _previous_summary，用它初始化
    if summary_body and not self._previous_summary:
        self._previous_summary = summary_body
    # 从 summary 之后开始压缩，避免重复压缩摘要本身
    turns_to_summarize = messages[summary_idx + 1:compress_end]
```

### 关键设计决策

1. **从后向前扫描**（`range(end-1, start-1, -1)`）：压缩窗口中最新的 summary 才是相关的
2. **支持新旧两种 prefix**：`SUMMARY_PREFIX` 和 `LEGACY_SUMMARY_PREFIX` 都识别
3. **不修改已存在的 `_previous_summary`**：只有当 `_previous_summary` 未设置时才用窗口内的 summary 初始化
4. **仅跳过到 summary_idx+1**：summary 本身保留，只是不重复压缩其主体内容

### BlueCortexCE 借鉴

BC 的 `StructuredExtractionService` 若实现迭代压缩（Phase 3.4），**必须**参考此逻辑。关键 insight：

> **迭代压缩时，必须识别并跳过已存在的摘要消息，防止重复压缩和摘要链断裂。**

建议在 `CompressionService` 中增加：
- `findLatestSummaryMessage(messages, fromIndex, toIndex)` 方法
- 迭代压缩循环开始时检测是否有遗留 summary
- 摘要体内容复用而非重新提取

---

## P2 — `2eef395e1` fix(compaction): mark end of context summary in role=user fallback

### 问题

当 head 以 assistant/tool 结尾、tail 以 assistant 开头时，summary 被插入为独立的 `role="user"` 消息。弱模型（如本地模型）会将正文中 verbatim 的 `"## Active Task"` 当作用户新输入（#11475, #14521）。

### 修复

在 standalone `role="user"` 路径上追加结束标记（merge-into-tail 路径已有）：

```python
if not _merge_summary_into_tail and summary_role == "user":
    summary = (
        summary
        + "\n\n--- END OF CONTEXT SUMMARY — "
        "respond to the message below, not the summary above ---"
    )
```

### 意义

这是一个防误读的 UX 修复，确保 summary 作为元信息而非用户消息被模型理解。

---

## P2 — `aacf36e94` fix(cli): persist manual compress handoff

### 变化

手动执行 `/compress` 时，新增调用：

```python
self.agent._flush_messages_to_session_db(self.conversation_history, None)
```

将压缩后的 handoff 消息持久化到 session_db（从 offset 0 开始），使 resume 能在退出后恢复压缩连续性。

### 意义

之前手动压缩只更新内存中的 `conversation_history`，重启后 resume 无法找到压缩状态。现在 `/compress` 和自动压缩在持久化行为上保持一致。

---

## P2 — `2a285d5ec` fix(agent): stateful streaming scrubber for reasoning-block leaks

### 新文件

`agent/think_scrubber.py`（386 行）— 状态ful reasoning block 过滤，替代 `run_agent._strip_think_blocks` 在流式 delta 处理中的使用。

### 背景问题

原方案在每个 delta 上运行 regex 剥离 `<think>...</think>` 块，但 delta 边界可能切断标签：

```
delta1 = "<think>"
delta2 = "Let me check their config"
delta3 = "</think>"
```

纯 regex 方案中，delta1 匹配不到完整对、delta2 完全漏过、delta3 匹配到但 delta1 的开始标签已丢失。结果：`"Let me check"` → `" me check"`（被截断）。

### 解决方案

`StreamingThinkScrubber` 状态机：

- **闭合标签对**：直接剥离（case 1）
- **流边界未终止的开始标签**：进入 block 状态，丢弃内容直到 close 到达
- **流结束时残留的 block 内容**：丢弃
- **孤立关闭标签**：无边界门控直接剥离
- **delta 边界部分标签**：暂存直到解析完成

流结束时（turn end）flush，使 benign 的尾随 `<` 能到达 UI。

### BlueCortexCE 借鉴

BC 的 SSE/WebUI 流式输出若涉及 thinking/reasoning 块过滤，需实现类似的状态机。纯 regex 无法处理流式边界问题。

---

## 下游 BlueCortexCE 适用性评估

| 上游变更 | BC 适用性 | 理由 |
|----------|----------|------|
| `4a3e3e20e` 迭代压缩连续性 | ⭐⭐⭐ | Phase 3.4 若实现迭代压缩必须参考 |
| `2eef395e1` summary 结束标记 | ⭐⭐ | BC 可在 ICL prompt 中添加类似提示 |
| `aacf36e94` 手动压缩持久化 | ⭐ | BC 目前无手动压缩 CLI 命令 |
| `2a285d5ec` 流式 think scrubber | ⭐⭐ | BC SSE 输出暂无 think block 过滤需求 |

---

## 相关文件

- `agent/context_compressor.py`（核心压缩逻辑）
- `agent/think_scrubber.py`（新增，流式 scrubbing）
- `cli.py`（手动压缩入口）
