# Shell Hooks 外部脚本桥接 — `agent/shell_hooks.py` 深度解析

**编号**: #75
**文件**: `agent/shell_hooks.py`（836 行，~26KB）
**日期**: 2026-05-05
**撰写人**: PM Agent

---

## 0. 概述

`shell_hooks.py`（836 行）是 Hermes Agent v0.12+ 新增的架构组件，允许用户将**任意 shell 脚本**注册为 hook 回调，零修改现有 hook 调用点。

**与记忆系统的关系**：通过 hook 机制（`on_memory_write`/`on_delegation`/`on_session_end` 等），shell 脚本可以订阅记忆系统事件并触发外部动作（如向外部服务发通知、写入外部 DB、同步到第三方记忆系统）。

**设计原则**：
- Python 插件 hooks 和 shell hooks **自然组合**：均通过 `invoke_hook()` 调度
- Python 插件先注册（`discover_and_load()`），Python 赢
- `shell=False` subprocess 执行，无 shell 注入风险
- 首次使用需用户同意（allowlist 持久化）
- CLI 和 Gateway 均可安全调用（幂等注册）

---

## 1. 核心架构

### 1.1 注册模型

```
config.yaml hooks: 块
        ↓
register_from_config(cfg)
        ↓
_parse_hooks_block() → List[ShellHookSpec]
        ↓
manager._hooks[event].append(_make_callback(spec))
        ↓
invoke_hook(event, **kwargs)  ← 现有 hook 调用点，零修改
        ↓
_shell_callback(**kwargs) → subprocess.run() → JSON stdin/stdout
```

### 1.2 ShellHookSpec 数据结构

```python
@dataclass
class ShellHookSpec:
    event: str                              # hook 事件名
    command: str                             # 脚本路径（可带参数）
    matcher: Optional[str] = None            # 仅 pre/post_tool_call 支持 regex 过滤
    timeout: int = 60                        # 默认 60s，上限 300s
    compiled_matcher: Optional[re.Pattern]    # 预编译 matcher
```

### 1.3 Wire Protocol

**stdin（JSON，piped to script）**：

```json
{
    "hook_event_name": "on_memory_write",
    "tool_name": null,
    "tool_input": null,
    "session_id": "sess_abc123",
    "cwd": "/home/user/project",
    "extra": {
        "memory_type": "observation",
        "content": "..."
    }
}
```

**stdout（JSON，脚本返回值）**：

```python
# pre_tool_call 阻止（两种格式均支持）
{"action": "block", "message": "Forbidden"}
{"decision": "block", "reason": "Forbidden"}  # Claude-Code 兼容格式

# pre_llm_call 注入上下文
{"context": "Today is Friday"}

# 其他事件/空输出 = no-op
{}
```

---

## 2. 安全模型

### 2.1 三层安全防护

| 层级 | 机制 | 说明 |
|------|------|------|
| L1 | `shell=False` subprocess | `shlex.split()` 解析，`os.path.expanduser()` 展开 `~`，无 shell 注入 |
| L2 | 首次使用同意（TTY prompt） | 未 allowlisted 的脚本首次执行时阻塞并提示用户 |
| L3 | Allowlist 持久化（`~/.hermes/shell-hooks-allowlist.json`） | 批准后持久化，`fcntl.flock` 跨进程防竞态 |

### 2.2 绕过方式（合规场景）

```bash
# 方式 1：CLI flag
hermes --accept-hooks

# 方式 2：环境变量
HERMES_ACCEPT_HOOKS=1

# 方式 3：config.yaml
hooks_auto_accept: true
```

### 2.3 超时控制

- 默认：60 秒
- 最大：300 秒
- 超时后记录 warning，不阻塞主流程

---

## 3. 与记忆系统 Hook 的集成

### 3.1 支持的记忆相关事件

所有 plugin hook 事件均可通过 shell hook 响应，包括：

| 事件 | 触发时机 | shell hook 用途示例 |
|------|---------|------------------|
| `on_memory_write` | 每次记忆写入 | 同步到外部 DB、触发外部索引 |
| `on_session_end` | session 真正结束时 | 导出 session 历史到文件 |
| `on_delegation` | 委托子代理时 | 记录委托决策日志 |
| `on_pre_compress` | 压缩前 | 自定义预过滤逻辑 |
| `on_turn_start` | 每轮开始 | 触发外部偏好查询 |
| `pre_tool_call` | 工具调用前 | 记忆工具的访问控制（matcher 支持） |

### 3.2 stdin extra 字段示例（`on_memory_write`）

```json
{
    "hook_event_name": "on_memory_write",
    "session_id": "sess_abc123",
    "cwd": "/home/user/project",
    "extra": {
        "memory_type": "observation",
        "content": "用户偏好深色主题",
        "source": "extraction",
        "retain_tags": []
    }
}
```

---

## 4. 实现亮点

### 4.1 幂等注册

```python
_registered: Set[Tuple[str, Optional[str], str]] = set()

with _registered_lock:
    if key in _registered:
        continue  # CLI + Gateway 双重调用安全
    manager._hooks.setdefault(spec.event, []).append(_make_callback(spec))
    _registered.add(key)
```

### 4.2 非阻塞 first-use prompt

```python
# prompt 在锁外执行，避免线程阻塞
if not already_allowlisted:
    if not _prompt_and_record(spec.event, spec.command, accept_hooks=effective_accept):
        continue  # 用户拒绝则跳过

with _registered_lock:  # 重新检查 + 写入
    if key in _registered:
        continue
```

### 4.3 原子 allowlist 更新（跨进程安全）

```python
fd, tmp_path = tempfile.mkstemp(prefix="shell-hooks-allowlist.", suffix=".tmp")
with os.fdopen(fd, "w") as fh:
    fh.write(json.dumps(data, indent=2))
atomic_replace(tmp_path, p)  # 原子替换，原位写入
# 跨进程竞态：fcntl.flock 保护 read-modify-write
```

### 4.4 响应格式归一化

```python
# Claude-Code 兼容格式 → Hermes 内部格式
if data.get("decision") == "block":  # Claude-Code
    message = data.get("reason") or ""
    return {"action": "block", "message": message}
if data.get("action") == "block":    # Hermes 格式
    return {"action": "block", "message": data.get("message", "")}
```

---

## 5. BlueCortexCE 借鉴

### 5.1 CE 当前 Hook 状态

CE（BlueCortexCE）的 Hook 机制基于 Spring AI 的 `@Tool` annotation + MCP protocol，目前**没有外部脚本桥接能力**。

### 5.2 可执行借鉴

#### 借鉴 1：外部脚本 hook 桥接（中等优先级）

CE 可以提供类似的 shell hook 扩展机制，允许用户在配置文件中注册外部脚本响应记忆事件：

```yaml
# cortex-ce.yaml 潜在配置
hooks:
  on_memory_write:
    - command: /usr/local/bin/sync-memory-to-es.sh
      matcher: "observation|summary"   # 可选：按 memory_type 过滤
      timeout: 30
  on_session_end:
    - command: /usr/local/bin/export-session.sh
```

**实现路径**：
1. 在 `StructuredExtractionService` 的 `save()` 之后调用 `invokeExternalHook("on_memory_write", ...)`
2. 使用 `ProcessBuilder`（Java）执行外部脚本，timeout 控制
3. stdout JSON 解析同 `shell_hooks.py` 协议

#### 借鉴 2：allowlist 安全模型（高优先级）

CE 的外部脚本 hook 应借鉴：
- 首次使用 TTY 确认（CLI 场景）
- `~/.cortex-ce/shell-hooks-allowlist.json` 持久化
- `ProcessBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)` 避免信息泄露

#### 借鉴 3：Hook 响应归一化（低优先级）

CE 当前 MCP 工具返回格式已规范化，无需 Claude-Code 兼容层。

### 5.3 不适用的部分

- **matcher 机制**：CE 的记忆类型过滤应在服务层做，不适合散落到脚本层
- **subprocess timeout 回退**：CE 应使用 `CompletableFuture.get(timeout, TimeUnit.SECONDS)` 替代

---

## 6. 与 Provider Hooks 的对比

| 维度 | Provider Hooks（内存接口） | Shell Hooks（外部脚本） |
|------|--------------------------|---------------------|
| 运行环境 | 进程内，同步 | 独立 subprocess，异步 |
| 语言 | Python | 任意（shell/python/go/...） |
| 访问权限 | 直接操作内存对象 | 只能通过 JSON stdin 接收数据 |
| 失败影响 | 隔离（try/except） | 不影响主流程（超时仅 warning） |
| 适用场景 | Provider 内部逻辑 | 外部系统集成、审计日志 |

两者互补：Provider hooks 做内存内部操作，shell hooks 做外部系统集成。

---

## 7. 文件元信息

```
agent/shell_hooks.py
├── 836 行
├── 依赖：subprocess, shlex, fcntl, threading, pathlib, json
├── 无外部依赖（纯标准库）
└── 新增于：commit 3988c3c2（~2026-04-25）
```

**上游扫描**：commit `3988c3c2` 已在 doc 34 中提及，本次为源码级深度解析。
