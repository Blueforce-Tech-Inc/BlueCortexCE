# Doc 43: 上游新提交 — on_memory_write Bridge 修复 + Per-User Memory Scoping

**版本**: v1
**时间**: 2026-04-25
**覆盖上游 commits**: `46f7b38b`, `c52e5931`, `8877688b`, `9d42aca2`

---

## §1 `46f7b38b fix: add on_memory_write bridge to sequential tool execution path (#10174)`

### 问题描述

`MemoryManager.on_memory_write` 是外部 Memory Provider（如 CE 的 MCP 集成、ClawMem、RetainDB、Supermemory 等）感知内置记忆写入的**唯一通道**。

然而，该桥接代码**仅存在于并发工具执行路径**（`_invoke_tool`），而**顺序执行路径**（`_execute_tool_calls_sequential`）完全缺失这段逻辑。

由于绝大多数记忆操作是单工具调用（顺序路径），这意味着外部 Memory Provider **静默丢失了几乎所有的记忆写入通知**。

### 代码修复

```python
# run_agent.py — sequential tool execution path (~line 7466)
# 在 memory_tool 调用返回后添加:

if self._memory_manager and function_args.get("action") in ("add", "replace"):
    try:
        self._memory_manager.on_memory_write(
            function_args.get("action", ""),
            target,
            function_args.get("content", ""),
        )
    except Exception:
        pass  # 外部 provider 失败不影响主流程
```

与并发路径的桥接代码完全一致，确保 add/replace 操作双向同步。

### 测试覆盖（新增 58 行）

```python
class TestOnMemoryWriteBridge:
    def test_on_memory_write_add(self): ...
    def test_on_memory_write_replace(self): ...
    def test_on_memory_write_remove_not_bridged(self):
        # 桥接故意跳过 remove — 仅 add/replace 通知外部 provider
        mgr.on_memory_write("remove", "memory", "old fact")
        assert p.memory_writes == [("remove", "memory", "old fact")]
    def test_on_memory_write_tolerates_provider_failure(self):
        # 单个 provider 失败不影响其他 provider 收到通知
```

**关键契约**：
- `action="add"` → 通知（镜像到外部）
- `action="replace"` → 通知（镜像到外部）
- `action="remove"` → 不通过桥接通知（但 `MemoryManager.on_memory_write` 本身仍会调用 provider，外部 provider 需自行处理 remove）

### 对 CE 的影响（最重要！）

CE 通过 MCP 协议或 SDK 集成作为 Hermes 的外部 Memory Provider 时，`on_memory_write` 是 CE 感知用户记忆变化的**主动推送通道**。

**修复前**：CE 通过 MCP 的 memory tool 写入 → Hermes 内部处理 → **CE 完全不知情**（顺序路径的 bridge 缺失）

**修复后**：CE 通过 MCP 的 memory tool 写入 → Hermes 内部处理 → **桥接代码通知 CE** → CE 可执行去重/聚合/元数据增强

这解决了 CE 作为外部 Provider 时的**被动盲区**问题，使得 Hermes ↔ CE 的记忆同步从"拉取轮询"升级为"主动推送通知"。

### 源码位置

| 文件 | 行号 | 含义 |
|------|------|------|
| `run_agent.py` | ~7466 | 顺序路径的 bridge 添加点 |
| `tests/agent/test_memory_provider.py` | ~736 | `TestOnMemoryWriteBridge` 回归测试套件 |

---

## §2 `c52e5931 fix: thread gateway user_id to memory plugins for per-user scoping`

### 问题描述

Gateway 模式下（多用户通过 Telegram/Discord/WhatsApp/Web 等平台接入），Memory Provider 使用静态标识符（如 `Mem0` 的 `"hermes-user"`、`Honcho` 的 `cfg.peer_name`），导致**所有 Gateway 用户共享同一个记忆桶**。

典型场景：用户 A 和用户 B 都通过 Telegram 接入 Hermes → 记忆互相污染。

### 修复方案

```
Gateway → user_id → AIAgent._user_id → MemoryManager.init_kwargs → MemoryProvider
```

**1. AIAgent 新增 `user_id` 参数**：

```python
# run_agent.py
def __init__(
    self,
    ...
    user_id: str = None,  # 新增
    ...
):
    self._user_id = user_id  # Platform user identifier (gateway sessions)
```

**2. MemoryManager 初始化时透传**：

```python
# run_agent.py ~1096
if self._user_id:
    _init_kwargs["user_id"] = self._user_id
```

**3. Gateway 入口透传**：

```python
# gateway/run.py
# primary path ~4544 和 background path ~6633:
AIAgent(
    ...
    user_id=source.user_id,  # 新增
    ...
)
```

**4. Provider 层消费**：

```python
# plugins/memory/mem0/__init__.py
self._user_id = kwargs.get("user_id") or self._config.get("user_id", "hermes-user")

# plugins/memory/honcho/__init__.py
if _gw_user_id:
    cfg.peer_name = _gw_user_id  # 覆盖默认 peer_name
```

### 测试覆盖（新增 289 行）

`tests/agent/test_memory_user_id.py` — 验证：
- Gateway 有 user_id → 传递到 provider ✓
- CLI 无 user_id → 使用默认标识符 ✓
- Honcho/Mem0 均正确覆盖 ✓

### 对 CE 的影响

CE 在 Gateway 模式下与 Hermes 集成时，**必须支持 per-user memory scoping**：

1. CE 应在自己的存储层按 `user_id` 隔离记忆（而非共享一个 bucket）
2. CE 的 MCP 集成应检查 `user_id` 参数并据此路由存储
3. CE 的上下文检索应在 `user_id` scope 内执行

**CE 现状**：CE 的 `UserProfileEntity` 提供了 per-user 隔离机制，但需要确保 MCP/SDK 集成层面也正确传递 `user_id`。

---

## §3 `8877688b fix(hindsight): preserve custom timeout on reconfig`

### 概要

Hindsight provider 的 `post_setup()` 在重新配置时从 `self._config` 读取 timeout，但 `self._config` 在 setup 阶段为 None。改为从 `.env` 读取。

```python
# plugins/memory/hindsight/__init__.py
# 修复前: self._config.timeout
# 修复后: os.environ.get("HINDSIGHT_TIMEOUT") 或默认值
```

---

## §4 `9d42aca2 fix(hindsight): preserve existing LLM key on blank local_embedded setup`

### 概要

当 `local_embedded` 配置为空/blank 时，不要覆盖已有的 LLM API key。结合 masked-key prompt UX 改进（HINDSIGHT_LLM_API_KEY 始终写入 .env，即使为空）。

这是一个配置韧性与 UX 的改进，防止 re-config 时意外丢失关键凭证。

---

## 5. 汇总对比表

| Commit | 主题 | CE 相关度 | 类型 |
|--------|------|----------|------|
| `46f7b38b` | on_memory_write 顺序路径桥接 | ⭐⭐⭐ **最高** | Bug 修复 |
| `c52e5931` | Per-user memory scoping | ⭐⭐⭐ **最高** | Bug 修复 |
| `8877688b` | Hindsight timeout 修复 | ⭐ 低 | Bug 修复 |
| `9d42aca2` | Hindsight LLM key 保留 | ⭐ 低 | Bug 修复 |

---

## 6. CE 可执行借鉴

### 6.1 立即（on_memory_write Bridge — 最高优先）

**问题**：CE 作为外部 Memory Provider，`on_memory_write` 在单工具调用场景下从未被触发。

**行动**：
1. 确认 CE 的 MCP Provider 实现（或 SDK 集成）已实现 `on_memory_write` 钩子
2. 如果 CE 有 MCP memory provider，检查其 `on_memory_write` 是否只依赖并发路径的 bridge（那么单工具调用完全不会触发）
3. 参考 doc 38 的分析，确保 CE 在"主动推送"模式下工作，而非被动轮询

### 6.2 中期（Per-User Scoping）

**问题**：CE 的 UserProfileEntity 虽然支持 per-user，但 MCP/SDK 层面可能未正确传递 user_id。

**行动**：
1. 检查 CE 的 MCP server 集成是否接收并传递 `user_id`
2. 检查 `ContextService` 或 `AgentService` 是否有 `userId` 隔离机制
3. 确保 `/api/memory/search` 和 `/api/memory/experiences` 在多用户场景下正确 scope

### 6.3 长期（Metadata 增强）

结合 doc 37（write origin metadata）和 doc 42（delegate tool memory interaction），CE 的记忆写入应携带完整 provenance：

```json
{
  "write_origin": "mcp_provider|memory_flush|background_review",
  "execution_context": "foreground|flush_memories|background_review",
  "session_id": "...",
  "parent_session_id": "...",
  "platform": "...",
  "tool_name": "memory"
}
```

---

## 7. 关键源码位置

| 文件 | 含义 |
|------|------|
| `run_agent.py` ~7466 | 顺序路径 on_memory_write bridge |
| `run_agent.py` ~528 | AIAgent.user_id 参数 |
| `run_agent.py` ~1096 | user_id 透传到 memory providers |
| `gateway/run.py` ~4544, ~6633 | Gateway 入口透传 user_id |
| `plugins/memory/mem0/__init__.py` | Mem0 per-user scoping |
| `plugins/memory/honcho/__init__.py` | Honcho per-user scoping |
| `tests/agent/test_memory_user_id.py` | Per-user routing 测试（289行）|
| `tests/agent/test_memory_provider.py` ~736 | on_memory_write bridge 回归测试 |

---

## 8. 版本历史

| 版本 | 时间 | 内容 |
|------|------|------|
| v1 | 2026-04-25 | 初始版本，覆盖 4 个上游新提交 |
