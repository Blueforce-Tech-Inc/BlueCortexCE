# 60-evolution/16-extended-memory-provider-hooks.md

# Extended MemoryProvider Hooks 详解

> **来源**：Hermes Agent `agent/memory_manager.py` + 各 Provider 实现
> **快照时间**：2026-04-19
> **关联**：`06-memory-provider-hooks-inventory.md`（核心 Hook），`13` §5

---

## 1. 概述

除 `06` 中已覆盖的 `on_session_end`、`get_tool_schemas`、`system_prompt_block` 等基础 Hook 外，`MemoryManager` 还暴露了以下扩展 Hook：

| Hook | 触发时机 | 用途 |
|------|----------|------|
| `on_pre_compress(messages)` | 上下文压缩前 | 提取 Provider 特定信息，注入压缩摘要提示词 |
| `on_memory_write(action, target, content)` | 内置 memory tool 写操作后 | 镜像写入到外部 Provider |
| `on_delegation(task, result, child_session_id, **kwargs)` | 子 Agent 完成后 | 传递子 Agent 记忆到父上下文 |
| `queue_prefetch(query)` / `prefetch(query)` | turn 结束时 fire，下 turn 开始时 consume | 预取下轮所需上下文 |

---

## 2. `on_pre_compress` — 压缩前的贡献注入

```python
def on_pre_compress(self, messages: List[Dict[str, Any]]) -> str:
    """通知所有 Provider 在上下文压缩前提供额外信息。
    
    Returns: 各 Provider 返回文本的拼接（"\n\n" 分隔）。
    空字符串 = 无 Provider 贡献。
    """
    parts = []
    for provider in self._providers:
        try:
            result = provider.on_pre_compress(messages)
            if result and result.strip():
                parts.append(result)
        except Exception as e:
            logger.debug("Provider '%s' on_pre_compress failed: %s", provider.name, e)
    return "\n\n".join(parts)
```

**典型用途**（RetainDB）：
- 将 Provider 特定的 profile 信息注入到压缩 prompt
- 提示 LLM"哪些是重要上下文，哪些可丢弃"

**与 CE 对照**：
- CE 的上下文压缩（`ContextRefineService`）目前无对应 Hook
- Hermes 的设计允许 Provider 在压缩前注入自己的"高优先级信号"

---

## 3. `on_memory_write` — 内置记忆写入镜像

```python
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """内置 memory tool 写入后通知外部 Provider。
    
    跳过 builtin Provider 本身（它就是写入源）。
    """
    for provider in self._providers:
        if provider.name == "builtin":
            continue
        try:
            provider.on_memory_write(action, target, content)
        except Exception as e:
            logger.debug("Provider '%s' on_memory_write failed: %s", provider.name, e)
```

**RetainDB 实现示例**：
```python
def on_memory_write(self, action: str, target: str, content: str) -> None:
    if action != "add" or not content or not self._client:
        return
    memory_type = "preference" if target == "user" else "factual"
    self._client.add_memory(self._user_id, self._session_id, content,
                             memory_type=memory_type)
```

**用途**：用户通过内置 memory tool 写入时，自动同步到 RetainDB/supermemory 等外部 Provider。

---

## 4. `on_delegation` — 子 Agent 记忆传递

```python
def on_delegation(self, task: str, result: str, *,
                  child_session_id: str = "", **kwargs) -> None:
    """子 Agent 完成后通知所有 Provider。"""
    for provider in self._providers:
        try:
            provider.on_delegation(task, result,
                                   child_session_id=child_session_id, **kwargs)
        except Exception as e:
            logger.debug("Provider '%s' on_delegation failed: %s", provider.name, e)
```

**用途**：当 Agent 通过 `honcho delegate` 或类似机制创建子 Agent 时，父 Agent 可以：
- 将子 Agent 的 session_id 关联到自己的上下文
- 在子 Agent 完成后，将子 Agent 的关键记忆合并到父上下文

**与 CE 对照**：
- CE 目前无子 Agent / delegation 机制
- 若未来引入，可参考此 Hook 传递跨 Agent 记忆

---

## 5. `queue_prefetch` / `prefetch` — Turn 间预取

这是 Hermes 最复杂的 Hook 模式，由 **RetainDB Provider** 完整实现。

### 5.1 设计动机

传统的 `system_prompt_block()` / `get_context_block()` 在 turn 边界同步返回。RetainDB 需要：
1. **当前 turn 结束时**（`queue_prefetch`）：异步 fire 3 个 background 线程
2. **下 turn 开始时**（`prefetch`）：消费预取结果，注入上下文

### 5.2 RetainDB 的预取实现

```python
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    """Fire context + dialectic + agent model 预取线程。"""
    if not self._client:
        return
    # 等待旧线程完成（最多 2s），防止线程堆积
    for t in self._prefetch_threads:
        t.join(timeout=2.0)
    threads = [
        threading.Thread(target=self._prefetch_context, args=(query,)),
        threading.Thread(target=self._prefetch_dialectic, args=(query,)),
        threading.Thread(target=self._prefetch_agent_model),
    ]
    self._prefetch_threads = threads
    for t in threads:
        t.start()

def prefetch(self, query: str, *, session_id: str = "") -> str:
    """消费预取结果，返回格式化上下文块。"""
    with self._lock:
        context = self._context_result
        dialectic = self._dialectic_result
        agent_model = self._agent_model
        # 清空缓存
        self._context_result = ""
        self._dialectic_result = ""
        self._agent_model = {}

    parts = []
    if context:
        parts.append(context)
    if dialectic:
        parts.append(f"[RetainDB User Synthesis]\n{dialectic}")
    if agent_model and agent_model.get("memory_count", 0) > 0:
        # ... 格式化 persona / instructions / working_style
        parts.append("[RetainDB Agent Self-Model]\n" + "\n".join(model_lines))

    return "\n\n".join(parts)
```

### 5.3 预取内容详解

| 预取线程 | 内容 | 用途 |
|----------|------|------|
| `_prefetch_context` | `query_context()` + `get_profile()` → overlay | 当前任务相关记忆 |
| `_prefetch_dialectic` | `ask_user()` — LLM 合成的用户理解 | 用户偏好的深层推理 |
| `_prefetch_agent_model` | `get_agent_model()` — Agent 自模型 | Persona / 持久指令 / 工作风格 |

**Dialectic synthesis**：RetainDB 的 `ask_user(query)` 调用 LLM 对用户历史记忆做深度推理，返回"用户为什么问这个"的高层理解。

### 5.4 线程安全

- 使用 `threading.local()` 为每个线程缓存独立 DB 连接
- 使用 `threading.Lock()` 保护 `_context_result` 等共享状态
- 线程 join 超时（2s）防止 rapid-fire 调用导致线程堆积

### 5.5 与 CE 的对照

| 维度 | Hermes RetainDB | BlueCortexCE |
|------|-----------------|--------------|
| 预取时机 | turn 边界（queue_prefetch → prefetch） | 无对应机制 |
| 异步线程管理 | 手动 `threading.Thread` + join | 可用 `@Async` + `CompletableFuture` |
| 线程堆积防护 | join(timeout=2.0) 等待旧线程 | 需类似机制 |
| 消费者模式 | 轮询 consume（prefetch 返回并清空） | 可用 `Future` / `Deque` |

---

## 6. Hook 触发链路总结

```
Agent Turn 结束
  ├── on_delegation(task, result, child_session_id)     ← 子 Agent 完成后
  ├── sync_turn(user_content, assistant_content)         ← Supermemory/RetainDB
  └── MemoryManager.on_session_end(messages)             ← session 结束时

Agent Turn 开始（下一轮）
  └── MemoryManager.prefetch(query) → context block     ← RetainDB queue_prefetch 结果

上下文压缩前
  └── MemoryManager.on_pre_compress(messages)            ← 各 Provider 注入压缩提示

内置 Memory Tool 写入后
  └── MemoryManager.on_memory_write(action, target, content)  ← 镜像到外部 Provider
```

---

## 7. 待跟进

- [ ] Supermemory Provider 是否实现 `on_pre_compress`？（需读源码确认）
- [ ] `queue_prefetch` 在 `run_agent.py` 的哪个方法中被调用？（需 grep）
- [ ] CE 的 `ContextRefineService` 是否需要类似的 `on_pre_compress` Hook？
