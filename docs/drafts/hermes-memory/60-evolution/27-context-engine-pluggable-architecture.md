# ContextEngine 可插拔架构解析

> **日期**：2026-04-24（cron 巡检）
> **来源**：`agent/context_engine.py`（184 lines）+ `agent/context_compressor.py` 实现对照

---

## 1. 架构定位

`ContextEngine` 是 Hermes 中**上下文压缩引擎的抽象基类**（ABC），定义了压缩引擎必须实现的接口规范。

```
plugins/context_engine/<name>/     ← 插件发现目录
agent/context_compressor.py        ← 默认实现（ContextCompressor）
```

**配置驱动选择**：`context.engine` in `config.yaml`，默认 `"compressor"`。

---

## 2. 核心接口

| 方法 | 性质 | 职责 |
|------|------|------|
| `name` | 抽象属性 | 引擎标识符（如 `"compressor"`、`"lcm"`） |
| `update_from_response(usage)` | 抽象方法 | 从 API 响应更新 token 使用量 |
| `should_compress(prompt_tokens)` | 抽象方法 | 判断本轮是否触发压缩 |
| `compress(messages, current_tokens)` | 抽象方法 | 执行压缩，返回压缩后消息列表 |
| `should_compress_preflight(messages)` | 可选方法 | API 调用前的廉价预检（默认 False） |
| `on_session_start(session_id)` | 可选方法 | Session 启动时加载持久化状态 |
| `on_session_end(session_id, messages)` | 可选方法 | Session 真正结束时（CLI 退出、/reset、gateway 过期） |
| `on_session_reset()` | 可选方法 | /new 或 /reset 时重置状态 |
| `get_tool_schemas()` | 可选方法 | 返回引擎向 Agent 暴露的工具（如 `lcm_grep`） |
| `handle_tool_call(name, args)` | 可选方法 | 处理工具调用 |
| `get_status()` | 可选方法 | 状态字典（供 display/logging） |
| `update_model(model, context_length, ...)` | 可选方法 | 模型切换或 fallback 激活时更新 |

---

## 3. 生命周期流程

```
on_session_start(session_id)
    ↓
每次 LLM 调用后:
    update_from_response(usage)    ← 更新 token 计数
    ↓
should_compress_preflight(messages) ← 预检（可选）
    ↓
should_compress(prompt_tokens)      ← 压缩决策
    ↓
compress(messages, current_tokens) ← 执行压缩
    ↓
on_session_end(session_id, messages) ← Session 结束时
```

**关键语义**：`on_session_end` 在 Session 真正结束时调用（CLI 退出、/reset、gateway session 过期），**不是每轮调用**。

---

## 4. Token 状态追踪（引擎必须维护）

```python
last_prompt_tokens: int = 0       # 最近一次 prompt 的 token 数
last_completion_tokens: int = 0  # 最近一次 completion 的 token 数
last_total_tokens: int = 0       # 最近一次总 token 数
threshold_tokens: int = 0        # 压缩触发阈值
context_length: int = 0          # 模型上下文长度
compression_count: int = 0       # 累计压缩次数
```

这些字段由 `run_agent.py` 直接读取用于显示/日志。

---

## 5. 压缩参数（子类可覆盖）

```python
threshold_percent: float = 0.75  # 触发阈值 = context_length * 0.75
protect_first_n: int = 3         # 保护前 N 条消息不压缩
protect_last_n: int = 6          # 保护后 N 条消息不压缩
```

---

## 6. 与 MemoryProvider 的对比

| 维度 | ContextEngine | MemoryProvider |
|------|--------------|---------------|
| 职责 | 上下文压缩/摘要 | 记忆存储与检索 |
| 生命周期 | `should_compress` / `compress` | `sync_turn` / `prefetch` |
| 插件发现 | `plugins/context_engine/<name>/` | `plugins/memory/<name>/` |
| 默认实现 | `ContextCompressor` | `BuiltinMemoryProvider`（不存在！） |
| Session 边界 | `on_session_start/end` | `on_session_end` |
| 工具暴露 | `get_tool_schemas` | `get_tool_schemas` |

---

## 7. 与 BlueCortexCE 对比

| 维度 | Hermes ContextEngine | BlueCortexCE |
|------|---------------------|--------------|
| 压缩触发 | `should_compress`（可插拔） | `/api/compress` 手动触发 |
| 引擎选择 | config 驱动，多实现可替换 | 单一 `ContextCompressor` |
| 插件架构 | ABC + `plugins/context_engine/` | 无对应 |
| Token 追踪 | 引擎维护，`run_agent.py` 读取 | API 层自行追踪 |
| Preflight | `should_compress_preflight` | 无 |
| 模型切换 | `update_model` 回调 | 无 |
| Session 边界 | `on_session_end` | SessionService 管理 |

---

## 8. 可借鉴点

**高优先级**：
- BlueCortexCE 可考虑引入 `ContextEngine` 抽象，将压缩逻辑从硬编码改为可插拔实现
- 增加 `should_compress_preflight` 等效，在 API 调用前做廉价预检

**中优先级**：
- 增加 `update_model` 回调，当模型变更时重新计算 token 阈值
- 统一 Session 边界管理（`on_session_end` 语义对齐）

---

## 9. 相关文档

- ContextCompressor 完整算法：[`24-context-compressor-full-algorithm.md`](24-context-compressor-full-algorithm.md)
- MemoryProvider Hooks：[`06-memory-provider-hooks-inventory.md`](06-memory-provider-hooks-inventory.md)
