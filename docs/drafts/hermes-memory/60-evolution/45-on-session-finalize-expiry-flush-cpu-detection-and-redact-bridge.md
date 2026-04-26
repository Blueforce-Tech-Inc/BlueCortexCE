# 上游新提交分析（2026-04-25）：on_session_finalize Expiry Flush + Hindsight CPU Detection + Redact Config Bridge

**编号**：45  
**日期**：2026-04-25  
**上游范围**：`e69526be..HEAD`（~1645 commits）中 memory 相关新增  
**覆盖**：doc 32 / 35 / 38 / 39 / 43 已覆盖的 17 个 memory commit 之外的新发现

---

## §1 `on_session_finalize` Expiry Flush 调用（`260ae621`）

### 1.1 背景：已有的 Session Finalize 调用点

此前 `on_session_finalize` 在三个 session boundary 场景被触发：

| 调用位置 | 触发时机 | 文件行 |
|----------|----------|--------|
| `gateway/run.py:5077` | `/new` 或 `/reset` 时销毁旧 session | `GatewayRunner._handle_new_command` / `_handle_reset_command` |
| `gateway/run.py:2340` | CLI shutdown / gateway stop 时所有 running session | `GatewayRunner.stop()` |
| `hermes_cli/plugins.py:71` | CLI `/new` 时 | CLI plugin hook |

### 1.2 新增调用点：Expiry Flush

```python
# gateway/run.py:2366（_session_expiry_watcher 内）
for key, entry in _expired_entries:
    try:
        await self._async_flush_memories(entry.session_id, key)
        try:
            from hermes_cli.plugins import invoke_hook as _invoke_hook
            _parts = key.split(":")
            _platform = _parts[2] if len(_parts) > 2 else ""
            _invoke_hook(
                "on_session_finalize",
                session_id=entry.session_id,
                platform=_platform,
            )
        except Exception:
            pass
```

**触发时机**：`_session_expiry_watcher` 扫出 `idle_expired` 的 session entry 后，在 `_async_flush_memories` 之后立即调用 `on_session_finalize`。

### 1.3 Hook 接口

```python
"on_session_finalize": {"session_id": "test-session", "platform": "cli"}
```

| 参数 | 说明 |
|------|------|
| `session_id` | 过期 session 的 ID |
| `platform` | 从 session key 解析（格式 `uuid:platform:...`），无则空字符串 |

### 1.4 与 `/resume` 的关系（重要缺口）

**重要**：`on_session_finalize` 在 expiry flush 时触发，但 `/resume` 加载的是一个**被压缩后的新 session**（child session）。老 session 触发 finalize 时，child session 已经被创建。`/resume` 本身**不会**触发 `on_session_finalize`——只有当被 resume 的 parent session 最终被 expiry watcher 清理时才会触发。

这意味着 Provider 的 `on_session_finalize` hook 在 idle 清理场景下会收到 `session_id`（过期老 session）和 `platform`（从 key 解析），但**没有**携带 compression artifact 或 resume metadata 的路径信息。

### 1.5 Provider 现有实现状态

```bash
grep -r "on_session_finalize" plugins/memory/*/__init__.py
# 结果：无 Provider 实现 on_session_finalize
```

所有现有 Provider（Honcho、Mem0、RetainDB、Hindsight、OpenViking、ByteRover）均未实现该 hook。这意味着 expiry flush 场景下的资源清理（如 Hindsight 的 event loop graceful shutdown、RetainDB 的 pending writes flush）目前依赖其他触发路径（如 `on_memory_write` 或 Provider 自身的 periodic flush）。

### 1.6 与 CE 的关系

CE 无 equivalent hook。CE 的 session 清理路径：
- Session end → `sessionService.endSession()` → DB flush → 无 hook 回调
- 无 idle expiry watcher（依赖外部进程管理或 DB TTL）

**可执行借鉴**：
- **短期**：为 CE 的 `SessionService` 添加 `onSessionFinalize(sessionId, platform, reason)` hook interface，供 Provider 实现清理逻辑
- **中期**：添加 idle session expiry watcher，对长时间无活动的 session 触发 finalize + DB snapshot

---

## §2 Hindsight Local Runtime CPU 检测（`df55660e`）

### 2.1 问题背景

在旧 CPU（不支持某些 SIMD 指令集）上，Hindsight 本地嵌入模式的底层依赖（主要是 NumPy）可能在 import 时抛出 `RuntimeError`。此前 Hermes 无检测，导致反复尝试启动一个确定会失败的本地内存后端。

### 2.2 解决方案

```python
def _check_local_runtime() -> tuple[bool, str | None]:
    """Return whether local embedded Hindsight imports cleanly."""
    try:
        importlib.import_module("hindsight")
        importlib.import_module("hindsight_embed.daemon_embed_manager")
        return True, None
    except Exception as exc:
        return False, str(exc)
```

检测在三个时机被调用：

| 时机 | 行为 |
|------|------|
| `is_available()`（mode in `local`/`local_embedded`） | 返回 `False`（graceful degrade） |
| `_get_client()`（mode == `local_embedded`） | 抛出 `RuntimeError`（上游已检测） |
| `initialize()`（mode == `local_embedded`） | 将 `self._mode = "disabled"` 并 early return |

### 2.3 与 CE 的关系

CE 的 Hindsight 集成（HindsightEmbedded / HindsightServer）如果未来支持本地嵌入模式，需要类似的运行时检测。CE 侧的 `HindsightService` 目前无此保护。

**可执行借鉴**：在 CE `HindsightService` 中添加 `checkRuntimeAvailability()` 方法，检测 CPU/依赖兼容性，在 `initialize()` 阶段graceful degrade 到 cloud 模式。

---

## §3 Redact Config Bridge Bug Fix（`0e235947`）

### 3.1 Bug 根因

`agent/redact.py` 在**模块导入时** snapshot `_REDACT_ENABLED`：

```python
# agent/redact.py（模块级）
_REDACT_ENABLED = os.environ.get("HERMES_REDACT_SECRETS", "true").lower() == "true"
```

但 `hermes_cli/main.py` 的调用顺序是：
1. `load_hermes_dotenv()` → 加载 `.env`
2. `setup_logging()` → **导入** `agent.redact`（此时 `_REDACT_ENABLED` 被 snapshot）
3. config bridge 运行（将 `config.yaml` → env var）

结果：用户在 `config.yaml` 中设置 `security.redact_secrets: false`，但因为 config bridge **在 `setup_logging()` 之后**才运行，`agent.redact` 已经 snapshot 了默认值 `true`。

### 3.2 修复方案

在 `hermes_cli/main.py` 的 `setup_logging()` **之前**添加 config.yaml 预读取：

```python
# hermes_cli/main.py:166（在 setup_logging 之前）
if "HERMES_REDACT_SECRETS" not in os.environ:
    _cfg = yaml.safe_load(open(get_hermes_home() / "config.yaml")) or {}
    _sec = _cfg.get("security", {})
    if isinstance(_sec, dict) and _sec.get("redact_secrets") is not None:
        os.environ["HERMES_REDACT_SECRETS"] = str(_sec["redact_secrets"]).lower()
```

优先级：`.env` > `config.yaml`（`.env` 存在则跳过 bridge）。

### 3.3 测试覆盖

新增 `tests/hermes_cli/test_redact_config_bridge.py`（151 行），通过 subprocess 隔离测试：
- `redact_secrets: false` in config.yaml → redaction disabled
- key absent → redaction enabled (default)
- `.env HERMES_REDACT_SECRETS=true` → overrides config.yaml

### 3.4 与 CE 的关系

CE 的 Secrets Redaction 机制：
- Backend `IngestionController.handleUserPrompt` 无 redaction 逻辑
- TS 层 `tag-stripping.ts` 仅做 tag stripping，不做 secrets redaction
- `extractedData` JSONB 字段无 redaction（已在 doc 39 中标记为安全缺口）

**可执行借鉴**：
- **CE 当前缺口**：如果用户在 config 中关闭 redaction，extractedData 中的 API key 等信息不会被清理
- 建议在 `StructuredExtractionService` 或 `IngestionController` 中添加 redaction layer，参考 Hermes 的 `sanitize_context()` 模式

---

## §4 全量 Memory Commit 覆盖状态（截至 HEAD `e5647d78`）

| Doc | 覆盖 Commit | 主题 |
|-----|-------------|------|
| 37 | `6a957a74` | Write Origin Metadata |
| 38 | `00c3d848`, `1e8254e5`, `b66644f0` | Interrupted Sync / Structured Content / Retain Metadata |
| 39 | `b8663813`, `3368814a`, `c0385873`, `a9a4416c` | Auto-Prune / Secrets Redaction / Summary Bug Fix |
| 40 | `19a3e2ce` | Gateway /resume Compression Continuation Chain |
| 41 | `tools/memory_tool.py` | Built-in MemoryTool & SessionSearchTool |
| 42 | Delegate tool | Delegate Tool Memory Interaction Model |
| 43 | `46f7b38b`, `c52e5931`, `8877688b`, `9d42aca2` | on_memory_write Bridge / Per-User Scoping |
| 44 | RetainDB provider | RetainDB 766 行深度解析 |
| **45（本文）** | `260ae621`, `25465fd8`, `df55660e`, `0e235947` | on_session_finalize Expiry Flush / Hindsight CPU Detection / Redact Bridge |

**无 memory 相关遗漏**：1645 commits 中其余 gateway/aux/provider/CLI 修复均为非 memory 领域（auth fallback / TUI / Matrix / MCP / type hints），已在 HEARTBEAT 中确认。
