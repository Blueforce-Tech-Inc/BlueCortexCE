# 上游大批量新提交记忆系统分析（d35efb989 → 81cd67829，1718 commits，2026-05-05 新增）

**下次扫描起点**：`origin/main` `81cd67829`

## 概述

`d35efb989..origin/main` 共 **1718 个新提交**，涵盖 10+ 个记忆/上下文系统相关发现。本文档覆盖最重要的 10 个，其余归类简述。

**警告**：此批次包含 `refactor(restructure): git mv all source files into hermes_agent/ package`（commit `65ca3ba93`），意味着 `run_agent.py` 等核心文件已被移动到 `heres_agent/` 子包。后续快照行号将大幅变化。

## 提交清单

| Commit | 严重性 | 描述 |
|--------|--------|------|
| `46f7b38bb` | P0 CRITICAL | on_memory_write bridge **缺失于 sequential 路径**，外部 Provider 静默丢失全部单次调用写入 |
| `1aaeca55e` / `ff95ec1c5` | P0 MAJOR | ContextEngine ABC 新增（206行），ContextCompressor 继承重构，**插件化上下文引擎入口** |
| `51751cdaf` / `e7209789b` | P1 | ContextEngine 接入 run_agent.py — session lifecycle + tool dispatch + engine tool schemas |
| `fec7b2225` / `d0d57bcde` | P1 | ContextEngine ABC 健壮性修复 — config selection + plugin discovery + ABC completeness |
| `825bd8cff` | P1 | 新增 `on_session_finalize` + `on_session_reset` plugin hooks（CLI exit, /new, /reset） |
| `721e0b96c` | P1 | compression disabled 时 `finish_reason='length'` 触发 length eviction 防死循环 |
| `c52e59319` | P2 | Gateway user_id 穿透到 memory plugins，**修复多用户共享同一记忆 bucket 的 bug** |
| `97fb69b01` / `24b8fb59e` | P2 | tool result persistence 阈值可配置化（BudgetConfig dataclass） |
| `8c90c8114` | P2 | session_search FTS5 CJK 查询 bypass（用 LIKE 替代避免单字符切分） |
| `eeba720fc8` | P2 | token accounting fallback + reasoning-aware compression 修复 |
| `1e6285c53` | P2 | compression eval harness 新增（离线评测 ContextCompressor） |
| `3dfce7409` / `fe6ca8b20` / `9cbfa1309` | P2 | tool result persistence module + registry + per-tool thresholds |

---

## 1. P0 CRITICAL: on_memory_write Bridge 缺失于 Sequential Tool Execution Path

**Commit**: `46f7b38bb`（2026-04-15）
**文件**: `run_agent.py`（现 `hermes_agent/run_agent.py`）

### 问题描述

`on_memory_write` bridge 用于通知外部 memory provider（ClawMem、RetainDB、Supermemory 等）内置 memory tool 的写入操作。该 bridge 原本**仅存在于并发工具执行路径**（`_invoke_tool`），但 sequential 路径（`_execute_tool_calls_sequential`）——这是单次工具调用（最常见场景）的执行路径——**完全缺失该 bridge**。

**影响**：所有外部 memory provider 静默丢失了**每一次**单次调用 memory write，而单次调用占全部 memory 操作的绝大多数。

### 修复代码

```python
# Bridge: notify external memory provider of built-in memory writes
if self._memory_manager and function_args.get("action") in ("add", "replace"):
    try:
        self._memory_manager.on_memory_write(
            function_args.get("action", ""),
            target,
            function_args.get("content", ""),
        )
    except Exception:
        pass
```

此 block 加在 sequential 路径中 memory tool 调用返回之后，与 concurrent 路径中的 bridge 完全相同。

### CE 对照

CE 侧 `IngestionService.recordUserPrompt()` 在 ingest 路径中有 `scanContent()` 调用，但与 Hermes 这类"内置 memory tool → external provider bridge"架构完全不同。不过，CE 的 `submitFeedback` 反馈回路确实存在类似的"写入后通知"需求，可参考此模式确保 feedback 写入能触发下游 extractor。

---

## 2. P0 MAJOR: ContextEngine ABC — 上下文引擎插件化架构

**Commit**: `1aaeca55e` / `ff95ec1c5`（2026-04-06）
**文件**: `agent/context_engine.py`（206行新增）

### 架构设计

```python
class ContextEngine(ABC):
    """Base class all context engines must implement."""
    
    @property
    @abstractmethod
    def name(self) -> str:
        """Short identifier (e.g. 'compressor', 'lcm')."""
    
    # Token state (MUST be maintained by engines)
    last_prompt_tokens: int = 0
    last_completion_tokens: int = 0
    last_total_tokens: int = 0
    threshold_tokens: int = 0
    context_length: int = 0
    compression_count: int = 0
    
    # Compaction parameters
    threshold_percent: float = 0.75
    protect_first_n: int = 3
    
    # Abstract methods
    @abstractmethod
    def on_session_start(self, session_id, ...): ...
    @abstractmethod
    def update_from_response(self, response, ...): ...
    @abstractmethod
    def should_compress(self, messages) -> bool: ...
    @abstractmethod
    def compress(self, messages, focus_topic=None): ...
    @abstractmethod
    def on_session_end(self): ...
```

**生命周期**：
1. `on_session_start()` — conversation begins
2. `update_from_response()` — after each API response
3. `should_compress()` — checked after each turn
4. `compress()` — when `should_compress()` returns True
5. `on_session_end()` — real session boundaries (CLI exit, /reset, gateway expiry)

### 插件化机制

选择通过 `context.engine` config key 驱动（`config.yaml`），默认 `"compressor"`。第三方引擎（如 LCM — Lossless Context Management）可通过插件系统或 `plugins/context_engine/<name>/` 目录替换内置 compressor。

### CE 对照

CE 的 `ContextService` 是单一实现，无插件化架构。`StructuredExtractionService` 作为独立 service 但非 context engine 模式。此设计理念与 CE Phase 3 的 template-driven extraction 有一定相似性（均为配置驱动、可替换的组件），但 ContextEngine 的可插拔设计更彻底。CE 的 compression/mode 系统如需演进，可参考此 ABC 契约。

---

## 3. P1: ContextEngine 接入 run_agent.py

**Commit**: `51751cdaf` / `e7209789b`（2026-04-06）

### 功能

- 在 compressor init 后将 engine tool schemas 注入 agent tool surface
- 调用 `on_session_start()` 并传入 session_id、hermes_home、platform、model
- 将 engine tool calls（`lcm_grep` 等）分派到 regular tool handler **之前**
- 55/55 tests pass

### 意义

这意味着 context engine 不仅仅是压缩器，还可以暴露自己的工具供 agent 调用。这是 LCM（Lossless Context Management）类引擎的关键能力——它们可能提供 `lcm_grep`、`lcm_recall` 等自定义工具。

---

## 4. P1: on_session_finalize + on_session_reset Plugin Hooks

**Commit**: `825bd8cff`（2026-04-08）
**文件**: `cli.py`（+26行）+ `hermes_cli/plugins.py`（+2行）

### 行为

| Hook | 触发时机 |
|------|----------|
| `on_session_finalize` | CLI exit（/quit, Ctrl-C）+ /new 或 /reset **之前** |
| `on_session_reset` | /new 或 /reset **之后**新 session 创建时 |

`on_session_finalize` 给 plugins 机会 flush 或 cleanup（例如将 buffered writes 持久化、关闭数据库连接）。`on_session_reset` 让 plugins 初始化 per-session 状态。

### CE 对照

CE 侧 session 生命周期有 `on_summary_generated` hook（`ContextService.generateContext()` 中的 summary post-processing），但无等效的 `session_start/session_end` 边界 hooks。CE 的 SessionEntity 生命周期由 API 层管理，不透传到外部插件系统。

---

## 5. P1: Length Eviction When No Compression

**Commit**: `721e0b96c`（2026-04-16）
**文件**: `run_agent.py`

### 行为

当 `compression_enabled=False`（用户禁用 compression）且 `finish_reason='length'`（模型达到输出 token 上限）时：

```python
if not self.compression_enabled:
    return {
        "final_response": None,
        "messages": messages,
        "api_calls": api_call_count,
        "completed": False,
        "partial": True,
        "error": "Response truncated due to output length limit",
    }
```

此前代码会 fall through 到 compression 逻辑，但 compression 已禁用，导致不可预期的行为。

---

## 6. P2: Gateway user_id Threaded to Memory Plugins — Per-User Memory Scoping

**Commit**: `c52e59319`（2026-04-07）

### Bug 描述

Memory plugins（Mem0、Honcho）使用静态标识符（`'hermes-user'`、config `peerName`），这意味着**所有 gateway 用户共享同一个记忆 bucket**——用户 A 的记忆会被用户 B 看到。

### 修复

- `AIAgent.__init__` 增加 `user_id` 参数
- `gateway/run.py` 将 `source.user_id` 传给 `AIAgent`（primary + background 双路径）
- `Mem0 plugin`: 优先使用 kwargs `user_id` 而非 config 默认值
- `Honcho plugin`: 当存在 gateway `user_id` 时覆盖 `cfg.peer_name`
- CLI sessions（`user_id=None`）保留现有默认值

### CE 对照

CE 的 session 已按 `sessionId` 隔离，user scoping 在 observation/summary 层面通过 `userId` 字段实现。Hermes 此 bug fix 的根因与 CE 类似：记忆系统的 user identity 传递链路容易断裂。

---

## 7. P2: Tool Result Persistence Thresholds Configurable

**Commit**: `97fb69b01` / `24b8fb59e`（2026-04-07）
**文件**: `environments/agent_loop.py` / `hermes_base_env.py` 等

### 设计

引入 `BudgetConfig` dataclass，将硬编码常量集中化、可覆盖：

| 常量 | 原值 | 用途 |
|------|------|------|
| per-result limit | 50K chars | 单个 tool output 持久化阈值 |
| per-turn limit | 200K chars | 单轮所有 tool outputs 总 budget |
| preview limit | 2K chars | inline preview 长度 |

**优先级**: `pinned(read_file=inf)` > `env config overrides` > `registry per-tool` > `defaults`

CLI override: `--env.turn_budget_chars 80000`

### CE 对照

CE `submitFeedback` 中 tool result 截断逻辑是硬编码的 4096 chars，无分层 budget 机制。CE 应考虑类似的可配置 threshold 设计。

---

## 8. P2: FTS5 CJK Query Bypass in session_search

**Commit**: `8c90c8114`（2026-04-25）
**文件**: `hermes_state.py`（+16/-12行）

### 问题

FTS5 默认 tokenizer 将 CJK 字符切分为单个 token，导致 `"大别山项目"` 这样的多字符查询变成 4 个单字符 AND 查询，几乎没有结果。

### 修复

对 CJK 查询（通过字符范围检测）跳过 FTS5，改用 `LIKE` 做 substring 匹配，确保短语准确性。

---

## 9. P2: Token Accounting + Reasoning-Aware Compression

**Commit**: `eeba720fc8`

### 内容

- Token accounting fallback：当无法获取精确 token count 时有 graceful degrade 路径
- Reasoning-aware compression：压缩时考虑模型的 `reasoning` content（非 `reasoning_content`）

---

## 10. P2: Compression Eval Harness

**Commit**: `1e6285c53`（2026-04-24）

### 设计

完整的离线评测工具链，位于 `scripts/compression_eval/`：
1. 加载真实对话 fixture
2. 通过 `ContextCompressor.compress()` 处理
3. 向 compressor model 提问，从压缩状态中回答
4. judge model 对每个答案在 6 个维度 0-5 分评分

---

## 源码重构：hermes_agent/ 子包迁移

**Commit**: `65ca3ba93`

所有源码文件被迁移到 `hermes_agent/` 子包（`git mv`），意味着：
- `run_agent.py` → `hermes_agent/run_agent.py`
- `agent/context_compressor.py` → `hermes_agent/context_compressor.py`
- 所有 `import` 路径更新

**影响**：后续上游快照的行号将与历史文档无法直接对照，需重新建立行号映射。

---

## 文档状态

- 记忆系统文档上次更新：#68（`d35efb989`，Telegram topic mode）
- 本次新增：#69（`81cd67829`，1718 commits 记忆系统分析）
- 上游已同步至 `origin/main`（`81cd67829`）

## CE 可执行借鉴

| 优先级 | 借鉴项 | 对应来源 |
|--------|--------|----------|
| P0 | 检查 CE 是否有类似的"写入后通知 external consumer"链路缺失 | `46f7b38bb` |
| P1 | feedback 写入后触发下游 extractor 的 bridge 设计 | `46f7b38bb` |
| P1 | session boundary hooks（start/end/finalize/reset）设计 | `825bd8cff` |
| P2 | tool result persistence 分层可配置 threshold | `97fb69b01` |
| P2 | 确认 CE session 有正确的 user_id 穿透到记忆系统 | `c52e59319` |
