# Hermes Agent 上游重大变更：flush_memories 彻底移除 + 背景评审循环接管

> **快照日期**：2026-04-26  
> **上游根路径**：`/Users/yangjiefeng/Documents/NousResearch/hermes-agent`  
> **配对**：背景评审机制深度见 [`09`](../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)；记忆注入对照见 [`04`](../20-recommendations/04-ce-injection-and-context-api-surface.md)；安全缺口见 [`05`](../20-recommendations/05-ce-context-security-gap-inventory.md)

---

## 1. 变更概述

| 提交 | 日期 | 描述 |
|------|------|------|
| `ea01bdce` | 2026-04-25 | **refactor(memory): remove flush_memories entirely (#15696)** — 核心：移除 ~248 LOC 的 `AIAgent.flush_memories()` 方法及其所有调用点 |
| `5401a008` | 2026-04-25 | fix: ContextCompressor 模型切换时重新计算 token 预算（附回归测试） |

**影响范围**：`run_agent.py` · `cli.py` · `gateway/run.py` · `agent/context_compressor.py` · `agent/auxiliary_client.py` · `agent/anthropic_adapter.py`

---

## 2. 旧架构：flush_memories 的问题

### 2.1 是什么

`AIAgent.flush_memories()` 是一个**阻塞式同步调用**，在以下时机触发：

- **CLI**：`/new` 会话创建前 + CLI 退出时
- **压缩前**（`_compress_context`）：上下文即将被压缩前
- **Gateway**：`/new` · `/resume` · 会话过期监听器（`session expiry watcher`）

调用链：
1. 在当前 `messages` 中注入一条 "flush" system prompt
2. 用 memory-only 工具列表构建**临时对话前缀**（system prompt + memory 工具 schema）
3. 发起一次 API 调用（`auxiliary_client`），执行 memory 工具写操作
4. 从 message list 中**剥离所有 flush 产物**

### 2.2 四大缺陷

| 缺陷 | 说明 |
|------|------|
| **阻塞** | 在压缩前同步运行于主 agent 流程中，**阻塞用户可见响应** |
| **破坏 Prompt Cache** | Flush 构建的临时对话前缀（`system + memory-only tools`）与**主会话缓存的前缀**（`system + 全量工具`）**不一致**，导致缓存失效 |
| **Gateway 双进程额外破坏缓存** | Gateway 端的 flush 为每个 finalize 的会话**新建一个 `AIAgent`**，用新的干净 prompt 重新初始化，同样破坏缓存 |
| **冗余** | 背景评审循环每 **10 轮** 触发一次（在 CLI 和 Gateway 上都一样），远比压缩或会话重置更频繁，内容相同但路径更轻量 |

### 2.3 关键代码证据

```python
# --- run_agent.py (e5647d78, 已删除) ---
def flush_memories(self, messages: list = None, min_turns: int = None):
    """Give the model one turn to persist memories before context is lost."""
    if self._memory_flush_min_turns == 0 and min_turns is None:
        return
    if "memory" not in self.valid_tool_names or not self._memory_store:
        return
    effective_min = min_turns if min_turns is not None else self._memory_flush_min_turns
    if self._user_turn_count < effective_min:
        return
    # 构建临时 system prompt（与 live 前缀不同 → 破坏 prompt cache）
    flush_system = (
        "You are about to lose context. Save memories using the memory tool.\n\n"
        f"Review these messages and save important information.\n\n"
        "Tools: memory only."
    )
    ...
    response = self._ensure_primary_openai_client(reason="flush_memories").chat.completions.create(...)
    # 执行 memory 工具写 → 从 message list 剥离 flush 产物
```

**调用点（均已删除）**：
- `cli.py`：`/new` + exit（2处）
- `run_agent.py`：`flush_memories(messages, min_turns=0)` 在 `_compress_context` 前
- `gateway/run.py`：3个调用点（session expiry watcher, `/new`, `/resume`）

---

## 3. 新架构：背景评审循环接管

### 3.1 核心机制

背景评审循环（**Background Review Loop**）在 `run_agent.py` 中实现：

```
每 _memory_nudge_interval (默认 10) 轮用户消息 → _spawn_background_review()
```

关键参数：
- `_memory_nudge_interval`：触发间隔（默认 10，可通过 `config.yaml` 的 `memory.nudge_interval` 配置）
- `_turns_since_memory`：轮次计数器，每次用户消息后 +1

```python
# --- run_agent.py (line ~9425) ---
if (self._memory_nudge_interval > 0
    and self._turns_since_memory >= self._memory_nudge_interval):
    self._spawn_background_review(
        messages_snapshot=deepcopy(self._session_messages),
        review_memory=True,
        review_skills=review_skills,
    )
    self._turns_since_memory = 0
```

### 3.2 `_spawn_background_review` 实现

```python
def _spawn_background_review(
    self,
    messages_snapshot: List[Dict],
    review_memory: bool = False,
    review_skills: bool = False,
) -> None:
    """Spawn a background thread to review the conversation for memory/skill saves."""
    def _run_review():
        review_agent = AIAgent(
            model=self.model,
            max_iterations=8,
            quiet_mode=True,
            platform=self.platform,
            provider=self.provider,
            parent_session_id=self.session_id,
        )
        # 共享 MemoryStore（写入同一份 MEMORY.md / USER.md）
        review_agent._memory_store = self._memory_store
        review_agent._memory_nudge_interval = 0  # 评审 agent 不再递归触发
        review_agent._skill_nudge_interval = 0
        # 关键：在 live 会话上下文（messages_snapshot）中运行，不破坏 prompt cache
        review_agent.run_conversation(
            user_message=prompt,
            conversation_history=messages_snapshot,
        )
        ...
        self._safe_print(f"  💾 {summary}")  # 用户可见提示
    t = threading.Thread(target=_run_review, daemon=True, name="bg-review")
    t.start()
```

### 3.3 为什么背景评审解决了 flush_memories 的问题

| flush_memories 问题 | 背景评审如何解决 |
|---------------------|-----------------|
| 阻塞 | 独立 daemon 线程，不阻塞主对话 |
| 破坏 Prompt Cache | 在 live 会话上下文（`messages_snapshot = deepcopy(self._session_messages)`）中运行，前缀一致 |
| Gateway 双进程破坏缓存 | 直接复用主 agent 的 `_memory_store`，无需新建 `AIAgent` |
| 冗余 | 本身即替代方案，每 10 轮触发（比 flush 更频繁） |

### 3.4 会话轮换：`commit_memory_session` 替代 flush

**会话 ID 轮换时**（`/new`、压缩后）不再调用 `flush_memories`，改为：

```python
def commit_memory_session(self, messages: list = None) -> None:
    """Trigger end-of-session extraction without tearing providers down.
    Called when session_id rotates (e.g. /new, context compression)."""
    if not self._memory_manager:
        return
    try:
        self._memory_manager.on_session_end(messages or [])
    except Exception:
        pass
```

→ 调用外部 `MemoryProvider.on_session_end()` 钩子，完成会话结束提取。

---

## 4. `SessionEntry.memory_flushed` → `expiry_finalized`

会话数据字典中的字段**重命名**（保留向后兼容）：

```
memory_flushed  →  expiry_finalized
```

**向后兼容**：
```python
# from_dict() 读取时优先新名，fallback 旧名
expiry_finalized = data.get('expiry_finalized', data.get('memory_flushed', False))
```

**语义变化**：不再表示"记忆已 flush"，而表示"会话过期后处理已完成"（finalize + eviction）。

---

## 5. ContextCompressor 模型切换修复（`5401a008`）

**问题**：模型从 200K 切换到 32K 时，`tail_token_budget` 和 `max_summary_tokens` 未重新计算，仍保持 200K 时的值（200K * 10% = 20K），实际应重置为 32K * 10% = 3.2K。

**修复**：
```python
# agent/context_compressor.py, update_model()
def update_model(self, model: str, context_length: int, ...) -> None:
    # ... threshold_tokens 重新计算 ...
    # 新增：
    target_tokens = int(self.threshold_tokens * self.summary_target_ratio)
    self.tail_token_budget = target_tokens
    self.max_summary_tokens = min(
        int(context_length * 0.05), _SUMMARY_TOKENS_CEILING,
    )
```

---

## 6. 对 BlueCortexCE 的借鉴意义

### 6.1 背景评审 vs 阻塞提取

| 维度 | flush_memories 方式 | 背景评审方式（推荐） |
|------|---------------------|---------------------|
| 用户体验 | 阻塞（用户等待） | 异步（用户无感知） |
| Prompt Cache | 破坏 | 保持 |
| 实现复杂度 | 低（同步调用） | 中（daemon thread + 结果汇总） |
| 触发频率 | 压缩/会话结束时（低频） | 每 N 轮（高频） |

### 6.2 CE 可借鉴的设计

**借鉴 `commit_memory_session` 的会话边界处理**：
- 会话 ID 轮换时，调用 provider 的 `on_session_end()` 而非重建 agent
- 保持 provider 状态连续性

**借鉴 `_spawn_background_review` 的结果汇总**：
- 后台线程写 memory，主线程通过 callback 接收摘要（`_summarize_background_review_actions`）
- 避免重复展示已存在的工具消息（`prior_snapshot` 去重）

**CE 当前 Phase 3 StructuredExtractionService 的定位对比**：
- Hermes 背景评审：通用 AI 判断"是否值得记忆"（完全提示词驱动）
- CE Phase 3：YAML 配置模板驱动的结构化提取（用户偏好、过敏信息等固定字段）
- **两者互补**：Phase 3 提取结构化字段（确定性），背景评审补充自由形式记忆（探索性）

---

## 7. 源码位置索引

| 文件 | 关键内容 | 位置 |
|------|----------|------|
| `run_agent.py` | `_spawn_background_review` | ~3123 |
| `run_agent.py` | `_summarize_background_review_actions` | ~3061 |
| `run_agent.py` | `_COMBINED_REVIEW_PROMPT` / `_MEMORY_REVIEW_PROMPT` | ~3000 |
| `run_agent.py` | `_trigger_memory_nudge` (nudge check) | ~9425 |
| `run_agent.py` | `commit_memory_session` | ~4153 |
| `run_agent.py` | `flush_memories` | **已删除**（原 ~7913） |
| `run_agent.py` | `_memory_nudge_interval` / `_turns_since_memory` | ~1580 |
| `cli.py` | `new_session` 中 flush_memories 调用 | **已删除** |
| `gateway/run.py` | `_flush_memories_for_session` | **已删除**（原 ~922） |
| `agent/context_compressor.py` | `update_model` token 预算重算 | ~318（新修复） |
| `plugins/memory/*/` | 各 Provider 的 `on_session_end` | 各 provider 目录 |

---

## 8. 变更文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `run_agent.py` | 删除 ~248 LOC + 移除 3 调用点 | 删除 `flush_memories()`；删除 `_memory_flush_min_turns` 配置 |
| `cli.py` | 删除 2 调用点 | `/new` + exit |
| `gateway/run.py` | 删除 `_flush_memories_for_session` 等 | 删除 gateway 端 flush 逻辑 |
| `agent/context_compressor.py` | 新增 ~7 LOC | 模型切换 token 预算重算 |
| `agent/auxiliary_client.py` | 注释更新 | 删除 `flush_memories` 相关注释 |
| `agent/anthropic_adapter.py` | 注释更新 | 删除 `flush_memories` 相关注释 |
| `hermes_cli/config.py` | 清理 | 删除 `flush_min_turns` 配置项 |
| `tests/gateway/test_async_memory_flush.py` | 删除 | flush 专用测试 |
| `tests/gateway/test_flush_memory_stale_guard.py` | 删除 | flush 专用测试 |
| `tests/agent/test_context_compressor.py` | 新增 | 模型切换回归测试 |

**测试覆盖**：383 个定向测试通过（run_agent/ · agent/ · cli/ · gateway/ session-boundary）。
