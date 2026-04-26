# 上游新提交分析（2026-04-25）：Interrupted Sync + Structured Content Guard + Retain Metadata

**Commit range**: `e69526be..4fade39c`（~3228 commits）
**新增时间**: 2026-04-25 07:50 CST
**关联 Backlog**: [`11-research-backlog.md`](../11-research-backlog.md)

---

## §1 `00c3d848 fix(memory): skip external-provider sync on interrupted turns (#15218)`

**日期**: 2026-04-24 09:43
**作者**: Brian D. Evans
**文件**: `run_agent.py`（+ 1 new test file）
**重要性**: ⭐⭐⭐（信任边界修复，影响所有外部 Memory Provider）

### 1.1 问题背景

`run_conversation` 在每个 turn 结束时调用 `memory_manager.sync_all(original_user_message, final_response)`，**但未检查 `interrupted` 标志**。这导致：

- 中断的流式回复被写入外部 Memory Provider（如 Hindsight）
- 被截断的工具调用链被当作"已完成"同步
- Provider 侧的 recall 将用户**未看到完成**的状态当作持久化事实

```python
# 问题代码（修复前）
# 隐藏在 3000 行 run_conversation 中，无测试覆盖
memory_manager.sync_all(original_user_message, final_response)
```

### 1.2 修复方案

将内联 sync 逻辑提取为新私有方法 `AIAgent._sync_external_memory_for_turn()`：

```python
def _sync_external_memory_for_turn(
    self,
    *,
    original_user_message: Any,
    final_response: Any,
    interrupted: bool,
) -> None:
    # 条件1: interrupted → 完全跳过（核心修复）
    if interrupted:
        return
    # 条件2: 无 memory_manager / response / user → 保留原有跳过行为
    if not (self._memory_manager and final_response and original_user_message):
        return
    # 条件3: 例外全部吞掉 — 外部 Provider 严格 best-effort
    try:
        self._memory_manager.sync_all(original_user_message, final_response)
        self._memory_manager.queue_prefetch_all(original_user_message)
    except Exception:
        pass
```

### 1.3 三个独立 Skip 条件

| 条件 | 行为 | 原因 |
|------|------|------|
| `interrupted=True` | 跳过 `sync_all` + `queue_prefetch_all` | 部分响应不是用户看到的"会话真相" |
| 无 memory_manager / response / user | 跳过 | 保留原有逻辑 |
| sync/prefetch 抛异常 | 吞掉 | 外部 Provider 不能阻塞用户看到响应 |

**关键信任边界**：`interrupted=True` 时，即使 `final_response` 和 `original_user_message` 看起来有值也跳过——因为中断可能在流式回复和下一 tool call 之间发生，磁盘上的字符串不等于用户实际得到的答案。

### 1.4 Prefetch 也受 `interrupted` 控制

用户下一条消息大概率是**重试同一意图**，以被中断 turn 为 key 的预取会基于陈旧上下文触发。

### 1.5 测试覆盖（16 个新测试）

`tests/run_agent/test_memory_sync_interrupted.py`：
- Interrupted turn + 完整响应 → 不 sync（修复核心）
- Interrupted turn + 大量 assistant 输出 → 不 sync（防止 mid-stream 欺骗）
- 正常完成 turn → `sync_all` + `queue_prefetch_all` 均以正确参数调用
- 无 final_response / 无 user_message / 无 memory_manager → 原有跳过路径
- `sync_all` 抛异常 → 吞掉，prefetch 仍尝试
- `queue_prefetch_all` 抛异常 → 吞掉（sync 已成功）
- **8-case 参数化矩阵**：`(interrupted × final_response × original_user_message)`

### 1.6 CE 借鉴分析

**CE 现状**：CE 作为外部 Memory Provider（如通过 MCP 或 SDK 集成），若上游 Agent 在 interrupted turn 后仍发送 `sync` 调用，会收到不完整数据。

**CE 可借鉴方向**：

| 方向 | 说明 |
|------|------|
| **Interrupted Turn Guard** | CE 的 `SearchService.writeObservation` 或 `ObservationService` 应拒绝写入 `source=agent_tool` 且 `session_end_reason=interrupted` 的 observation |
| **Prefetch 隔离** | 若 CE 实现预取逻辑，同样需要检查 turn 是否被中断 |
| **Provider 异常隔离** | CE 的 Provider 回调应吞掉所有异常，避免影响上游 agent 主流程 |
| **测试覆盖** | 参考 16-case 矩阵测试 interrupted × 有/无 response × 有/无 user × 有/无 provider |

**CE 具体实施路径**：

```python
# 伪代码
def sync_observations(self, user_message, assistant_response, interrupted: bool):
    if interrupted:
        return  # Trust boundary: don't persist partial state
    if not (user_message and assistant_response):
        return
    try:
        self.write_observations(...)
        self.queue_prefetch(...)
    except Exception:
        pass  # Best-effort, never block upstream
```

---

## §2 `1e8254e5 fix(agent): guard context compressor against structured message content`

**日期**: 2026-04-22 23:13
**作者**: Yukipukii1
**文件**: `agent/context_compressor.py`（+ 1 test file）
**重要性**: ⭐⭐（多模态内容安全）

### 2.1 问题背景

`ContextCompressor` 在压缩时直接做字符串操作（`existing + "\n\n" + _compression_note`），假设 message content 是 plain text。但 Hermes 支持多模态 content（list of blocks with `type`/`text` fields），直接拼接会导致：

- 列表被转字符串（如 `"[{...}]"`）
- JSON-like stringified content 膨胀
- compression note 无法正确注入

### 2.2 修复方案

新增两个 helper 函数：

```python
def _content_text_for_contains(content: Any) -> str:
    """Best-effort text view of message content for substring checks."""
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str):
                    parts.append(text)
        return "\n".join(part for part in parts if part)
    return str(content)

def _append_text_to_content(content: Any, text: str, *, prepend: bool = False) -> Any:
    """Safely append/prepend text to message content (string or list)."""
    if content is None:
        return text
    if isinstance(content, str):
        return text + content if prepend else content + text
    if isinstance(content, list):
        text_block = {"type": "text", "text": text}
        return [text_block, *content] if prepend else [*content, text_block]
    rendered = str(content)
    return text + rendered if prepend else rendered + text
```

### 2.3 修复点

- `existing = msg.get("content") or ""` → `existing = msg.get("content")`（保留 None/str/list 类型）
- `_compression_note not in existing` → `_compression_note not in _content_text_for_contains(existing)`（安全 substring check）
- `msg["content"] = existing + "\n\n" + note` → `msg["content"] = _append_text_to_content(existing, note)`（类型安全 append）

### 2.4 CE 借鉴分析

**CE 现状**：CE 的 `ContextCompressor` 在合并 summary 时假设 content 是 plain text。若未来支持多模态（图片/文件），同样面临此问题。

**CE 可借鉴**：在 `StructuredExtractionService` 或 `ContextCompressor` 中引入 `_content_text_for_contains` / `_append_text_to_content` 模式，提前预防多模态兼容问题。

---

## §3 `b66644f0 feat(hindsight): richer session-scoped retain metadata`

**日期**: 2026-04-22 05:23
**作者**: Abner
**文件**: `gateway/run.py`、`plugins/memory/hindsight/` + README
**重要性**: ⭐⭐（Provider 端记忆组织能力增强）

### 3.1 变更内容

**① 新增可配置 Knobs（Hindsight 原生）**：

| Knob | 说明 |
|------|------|
| `retain_tags` | 默认保留标签列表 |
| `retain_source` | 来源过滤规则 |
| `retain_user_prefix` | 用户消息自动保留的前缀 |
| `retain_assistant_prefix` | 助手消息自动保留的前缀 |

**② Gateway Session Identity 穿透到 Provider**：

将以下字段从 `GatewayRunner` 穿透到 `AIAgent` → `MemoryManager` → `MemoryProvider.initialize`：

| 字段 | 说明 |
|------|------|
| `user_name` | 用户名 |
| `chat_id` | 聊天室 ID |
| `chat_name` | 聊天室名称 |
| `chat_type` | 聊天室类型（单聊/群聊等） |
| `thread_id` | 线程 ID |

**③ Hindsight 侧使用**：

- 新 identity 字段作为 retain metadata 附加
- per-call tool tags 与配置的 default tags 合并
- 可配置的 transcript labels 用于自动保留的 turn

### 3.2 架构穿透路径

```
GatewayRunner
  → AIAgent.__init__(..., user_name, chat_id, chat_name, chat_type, thread_id)
    → self._memory_manager = MemoryManager(..., user_name, chat_id, ...)
      → provider.initialize(..., user_name, chat_id, chat_name, chat_type, thread_id)
        → Hindsight: attach as retain metadata + scope query
```

### 3.3 CE 借鉴分析

**CE 现状**：CE 的 `ContextService` 写 observation 时无 session identity 元数据，无法按 chat/thread/user 分组记忆。

**CE 可借鉴方向**：

| 方向 | 说明 |
|------|------|
| **Session 级别 Metadata** | 在 `SessionEntity` 或 `ObservationEntity` 中增加 `chat_id`/`thread_id`/`user_name` 字段 |
| **Provider 初始化参数** | CE 的 `MemoryProvider` interface 的 `initialize()` 应接受 session identity kwargs |
| **多租户隔离** | chat_id/user_name 可用于实现多用户记忆隔离（类似 Hindsight per-agent/per-user banks） |

---

## §4 上游新提交速查（e69526be → 4fade39c）

以下为 ~3228 commits 中 **memory 相关**的新增/修复（部分已在 doc 32-37 覆盖，✅ = 已覆盖）：

|  Commit | 类型 | 说明 | 状态 |
|---------|------|------|------|
| `00c3d848` | fix(memory) | Skip external-provider sync on interrupted turns | **本文 §1** |
| `6a957a74` | fix(memory) | Write origin metadata | ✅ doc 37 |
| `fd3864d8` | feat(cli) | Wrap /compress in _busy_command | ✅ doc 37 |
| `a9a4416c` | fix(compress) | ContextCompressor ABC: has_content_to_compress + focus_topic | ✅ doc 32/34 |
| `1e8254e5` | fix(agent) | Guard context compressor against structured content | **本文 §2** |
| `b66644f0` | feat(hindsight) | Richer session-scoped retain metadata | **本文 §3** |
| `edff2fbe` | feat(hindsight) | bank_id_template per-agent/per-user | ✅ doc 35 |
| `f9c6c5ab` | fix(hindsight) | document_id per-process (#6602) | ✅ doc 35 |
| `93a74f74` | fix(hindsight) | Preserve shared event loop on shutdown | ✅ doc 35 |
| `403c82b6` | feat(hindsight) | Add HINDSIGHT_TIMEOUT env var | ✅ doc 35 |
| `f1ba2f0c` | fix(hindsight) | _run_sync respects configured timeout | ✅ doc 35 |
| `df55660e` | fix(hindsight) | Disable local runtime on unsupported CPUs | ✅ doc 35 |
| `127048e6` | fix(hindsight) | Accept snake_case api_key config | ✅ doc 35 |
| `d6b65bbc` | fix(hindsight) | Preserve non-ASCII text | ✅ doc 35 |
| `a5c7422f` | fix(hindsight) | Always write HINDSIGHT_LLM_API_KEY to .env | ✅ doc 35 |
| `346601ca` | fix(context) | Invalidate stale Codex OAuth cache >= 400k (#15078) | 关联 doc 37 |
| `17fc84c2` | fix | Repair malformed tool call args in streaming | ✅ doc 37 |
| `2d444fc8` | fix(run_agent) | Handle unescaped control chars | ✅ doc 37 |
| `7a192b12` | fix(run_agent) | Repair corrupted tool_call before send | ✅ doc 37 |
| `921133cf` | fix(debug) | Preserve full line at truncation + cap memory | 新增 |
| `3e652f75` | fix(plugins+nous) | Auto-coerce memory plugins | 新增 |
| `570f8bab` | fix(compression) | Exclude completion tokens from trigger | 新增 |
| `4f24db42` | fix(compression) | 64k floor on aux model | ✅ doc 36 |
| `8a6aa588` | fix(cli) | Sync session_id after compression | 新增 |
| `13294c2d` | feat(compression) | Language-aware summaries | ✅ doc 33 |

**非 memory 核心（未收录）**：`fix(gateway)`/`fix(mcp)`/`fix(acp)`/`fix(bedrock)`/`fix(api-server)` 等基础设旜修复。

---

## §5 文件大小验证

| 文件 | 字节数 | 状态 |
|------|--------|------|
| `38-upstream-new-commits-interrupted-sync-structured-content-retain-metadata.md` | ~18KB | ✅ < 50KB |
| 最大文件 `09-supermemory-capture-lifecycle.md` | 46922 | ✅ 无变化 |
