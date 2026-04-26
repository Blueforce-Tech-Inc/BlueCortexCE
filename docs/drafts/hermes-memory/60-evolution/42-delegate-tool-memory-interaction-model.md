# 42 — Delegate Tool 内存交互模型

**日期**: 2026-04-25
**来源**: `tools/delegate_tool.py`（2384L）+ `agent/memory_manager.py` + `run_agent.py`

---

## 1. 概述

Delegate Tool 是 Hermes Agent 的子代理分发机制，允许主 Agent 并行启动多个子 Agent 执行独立任务。子代理的内存系统与主代理完全隔离，同时通过 `on_delegation()` 钩子实现结果回传。

---

## 2. 核心设计：skip_memory=True

### 2.1 子代理初始化参数

```python
# tools/delegate_tool.py ~line 974
child_config = AgentConfig(
    ephemeral_system_prompt=child_prompt,
    skip_context_files=True,
    skip_memory=True,         # ← 关键：子代理不初始化自己的内存系统
    ...
)
```

### 2.2 run_agent.py 中的 skip_memory 处理

```python
# run_agent.py ~line 1503
if not skip_memory:
    memory_manager = MemoryManager(...)
    _memory_store = MemoryStore(...)  # live 记忆存储
    _run_mcp_servers()               # MCP 服务器初始化
```

**效果**：子代理完全不使用自己的 MemoryStore 或 MemoryManager。它是一个"纯执行器"，没有任何持久化记忆。

### 2.3 内存系统隔离的三层含义

| 层级 | 主代理 | 子代理 |
|------|--------|--------|
| MemoryStore | ✅ live entries | ❌ 不存在 |
| MemoryManager | ✅ 管理 providers | ❌ 不存在 |
| 消息历史 | ✅ 完整上下文 | ❌ 仅 `context` 字段传入 |

子代理只能通过 `context` 参数接收主代理传递的信息，不保留任何执行历史。

---

## 3. on_delegation 钩子：子代理 → 主代理的记忆回传

### 3.1 调用点

```python
# tools/delegate_tool.py ~line 2005
if (
    parent_agent
    and hasattr(parent_agent, "_memory_manager")
    and parent_agent._memory_manager
):
    for entry in results:
        parent_agent._memory_manager.on_delegation(
            task=_task_goal,
            result=entry.get("summary", "") or "",
            child_session_id=(
                getattr(children[entry["task_index"]][2], "session_id", "")
                if entry["task_index"] < len(children)
                else ""
            ),
        )
```

### 3.2 MemoryManager.on_delegation 实现

```python
# agent/memory_manager.py ~line 331
def on_delegation(self, task: str, result: str, *,
                  child_session_id: str = "", **kwargs) -> None:
    """Notify all providers that a subagent completed."""
    for provider in self._providers:
        try:
            provider.on_delegation(
                task, result, child_session_id=child_session_id, **kwargs
            )
        except Exception as e:
            logger.debug(
                "Memory provider '%s' on_delegation failed: %s",
                provider.name, e,
            )
```

### 3.3 传播到 Provider 层

`on_delegation()` 将任务目标 + 执行结果 + 子会话 ID 广播给所有已注册的 MemoryProvider。**目前没有任何 Provider 实现此钩子**（grep 结果为空），这是一个已知的未实现功能。

---

## 4. 子代理的能力边界

```python
# tools/delegate_tool.py ~line 2243-2255
"- Subagents have NO memory of your conversation. Pass all relevant "
"info (file paths, error messages, constraints) via the 'context' field.\n"
"- Leaf subagents (role='leaf', the default) CANNOT call: "
"delegate_task, clarify, memory, send_message, execute_code.\n"
"- Orchestrator subagents (role='orchestrator') retain "
"delegate_task so they can spawn their own workers, but still "
"cannot use clarify, memory, send_message, or execute_code."
```

| 角色 | delegate_task | clarify | memory | send_message | execute_code |
|------|:---:|:---:|:---:|:---:|:---:|
| leaf（默认） | ❌ | ❌ | ❌ | ❌ | ❌ |
| orchestrator | ✅ | ❌ | ❌ | ❌ | ❌ |

**memory tool 对所有子代理都不可用**——无论 leaf 还是 orchestrator。

---

## 5. skip_context_files 补充隔离

```python
child_config = AgentConfig(
    skip_context_files=True,  # 同样隔离，不读取主代理的上下文文件
    skip_memory=True,
    ...
)
```

子代理既不继承父代理的 MemoryStore（内存记忆），也不继承父代理的 Context References 文件扫描结果。完全独立的执行上下文。

---

## 6. 委托结果的结构

每个子代理返回的结果条目格式：

```python
{
    "task_index": 0,
    "summary": "执行摘要字符串",
    # 其他字段...
}
```

`summary` 字段是子代理执行结果的唯一记忆载体，会通过 `on_delegation(result=entry.get("summary", ""))` 注入主代理的内存系统。

---

## 7. on_delegation 未实现的现状

### 7.1 Provider 实现状态

```bash
grep -rn "def on_delegation" plugins/memory/  # 无结果
```

所有内置 MemoryProvider（Hindsight、Supermemory、RetainDB、OpenViking 等）均未实现 `on_delegation()` 钩子。钩子调用被 `try/except` 吞掉，不影响主流程，但**委托结果的记忆化完全失效**。

### 7.2 影响

当主代理通过 `delegate_task` 启动子代理时：
1. 子代理执行复杂任务并生成 `summary`
2. `on_delegation(task=..., result=summary, ...)` 被调用
3. 所有 Provider 的 `on_delegation` 是空实现（raise NotImplementedError 或静默失败）
4. **委托结果没有写入任何记忆存储**

这意味着子代理的成果对主代理来说是"一次性"的，不会进入未来的上下文检索。

---

## 8. 与 CE（BlueCortexCE）的对比

| 维度 | Hermes Agent | BlueCortexCE |
|------|-------------|--------------|
| 子代理内存隔离 | `skip_memory=True` | 无对应机制 |
| 委托结果回传 | `on_delegation` 钩子（未实现） | 无对应机制 |
| 子代理不可用工具 | memory 工具全禁 | 无特殊限制 |
| 上下文传递 | `context` 字段手动传递 | 无对应机制 |
| orchestrator 层级 | 支持（depth-bounded） | 无对应机制 |

---

## 9. 可执行借鉴

### 9.1 立即可做（低风险）

1. **记录 CE 子任务结果到记忆**：当 CE 通过某种机制启动子任务时，将结果摘要写入 `SummaryEntity`，字段标注 `source=delegate` 或类似标签
2. **禁止子任务调用记忆工具**：通过工具白名单机制，确保子任务不能执行 `memory` 类工具调用

### 9.2 中期设计

1. **实现类似 `on_delegation` 的钩子**：在 `StructuredExtractionService` 或 `ContextService` 中增加委托结果写入路径
2. **支持 `context` 参数传递**：CE 的任务分发机制应支持将父上下文的关键信息（文件路径、约束条件）注入子任务

### 9.3 长期架构

1. **orchestrator 模式**：支持多层嵌套的任务分发，每层有独立的记忆边界
2. **委托结果的结构化摘要**：不只是字符串 summary，而是结构化的 `ExtractedData` JSONB，记录子任务的执行参数、输出 Schema、关键发现

---

## 10. 关键源码位置

| 文件 | 行号 | 含义 |
|------|------|------|
| `tools/delegate_tool.py` | 974 | `skip_memory=True` 子代理配置 |
| `tools/delegate_tool.py` | 2005-2028 | `on_delegation()` 调用点 |
| `tools/delegate_tool.py` | 2243-2255 | 子代理能力边界文档 |
| `agent/memory_manager.py` | 331-343 | `on_delegation` ABC 定义 |
| `run_agent.py` | 1503 | `skip_memory` 内存初始化分支 |

---

## 11. 附：max_concurrent_children 成本警告（2026-04-25 新增）

```python
# tools/delegate_tool.py ~line 276
if result > 10:
    logger.warning(
        "delegation.max_concurrent_children=%d: each child consumes API tokens "
        "independently. High values multiply cost linearly.",
        result,
    )
```

当并发子代理 > 10 时会触发成本警告。每个子代理独立初始化模型调用，成本线性叠加。这是内存系统之外的经济学约束，影响记忆系统的调用频率设计。
