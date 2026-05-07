# Doc 105: Shell Hooks 插件扩展架构深度解析

> **文件**: `agent/shell_hooks.py`（836 行）
> **分析日期**: 2026-05-07
> **上游版本**: `hermes-agent` origin/main `49c3c2e0d`

## 1. 概述

`shell_hooks.py` 是 Hermes Agent 的**Shell 脚本插件扩展系统**——允许用户用任意 Shell 脚本拦截和处理 Hermes 生命周期中的关键事件（pre_tool_call、post_llm_call 等），实现零代码侵入的功能扩展。

**设计目标**：
- 让用户无需修改 Hermes 源码即可自定义行为
- 与 Hermes 原生 Python 插件系统共存（通过 `PluginManager.invoke_hook`）
- 安全性：subprocess 隔离 + 同意书机制 + shell=False 防注入

## 2. 架构全景

```
cli-config.yaml
  └── hooks:
        pre_tool_call:
          - command: "/usr/local/bin/my-gate.sh"
            matcher: "terminal"
            timeout: 30
        pre_llm_call:
          - command: "/usr/local/bin/inject-context.sh"
```

```
register_from_config(cfg)
  ├── _parse_hooks_block()        # 解析 YAML → List[ShellHookSpec]
  ├── _is_allowlisted()           # 查 ~/.hermes/shell-hooks-allowlist.json
  ├── _prompt_and_record()        # 首次 TTY 征求同意
  └── manager._hooks[event].append(_make_callback(spec))
                                        │
invoke_hook(event, **kwargs) ──────────┘
  │
  └── Shell callback (spec.event 匹配)
        ├── _serialize_payload()   # kwargs → JSON stdin
        ├── _spawn(argv, stdin_json)  # subprocess.run(shell=False)
        ├── _parse_response()       # JSON stdout → {action, context}
        └── 返回 action/block/message
```

**关键设计原则**：
1. **零侵入**：Shell hooks 注册到同一个 `PluginManager`，与 Python 插件平等竞争
2. **幂等注册**：同一 `(event, matcher, command)` 三元组重复注册无效果，CLI 和 Gateway 可同时调用
3. **同意书先行**：首次使用时需用户同意（TTY prompt 或 `--accept-hooks` flag）
4. **Fail-open**：Hook 执行失败不影响主流程（log + return None）

## 3. ShellHookSpec 数据模型

```python
@dataclass
class ShellHookSpec:
    event: str                  # VALID_HOOKS 中的事件名
    command: str                # shell 命令路径（不支持管道，需包装为脚本）
    matcher: Optional[str]      # 正则表达式，仅 pre/post_tool_call 生效
    timeout: int                # 超时秒数（默认 60，最大 300）
    compiled_matcher: re.Pattern  # 预编译匹配器
```

**matcher 语义**（仅对 tool_call 事件有意义）：
- `None` → 事件触发时总是执行
- 正则字符串 → `re.fullmatch(tool_name)` 为真才执行

## 4. VALID_HOOKS 事件模型

`hermes_cli/plugins.py` 定义了 17 个合法事件：

| 事件 | 触发时机 | 可拦截返回值 |
|------|----------|--------------|
| `pre_tool_call` | 工具调用前 | `{action: "block", message: "..."}` 阻止执行 |
| `post_tool_call` | 工具调用后 | 无阻塞效果（observer） |
| `pre_llm_call` | LLM 调用前 | `{context: "..."}` 注入额外 context |
| `post_llm_call` | LLM 调用后 | 无阻塞效果（observer） |
| `pre_api_request` | 外向 HTTP 请求前 | 未使用 |
| `post_api_request` | 外向 HTTP 请求后 | 未使用 |
| `on_session_start` | Session 启动完成时 | 无（observer） |
| `on_session_end` | Session 结束时 | 无（observer） |
| `on_session_finalize` | Session 最终化时 | 无（observer） |
| `on_session_reset` | Session 重置时 | 无（observer） |
| `subagent_stop` | 子 Agent 停止时 | 无（observer） |
| `pre_gateway_dispatch` | Gateway 收到消息时 | `{action: "skip"/"rewrite"/"allow"}` |
| `pre_approval_request` | 危险命令审批前 | 无（observer） |
| `post_approval_response` | 危险命令审批后 | 无（observer） |
| `transform_terminal_output` | 终端输出转换 | 未使用 |
| `transform_tool_result` | 工具结果转换 | 未使用 |

## 5. Wire Protocol（JSON 协议）

### stdin（Hermes → Shell 脚本）

```json
{
  "hook_event_name": "pre_tool_call",
  "tool_name":       "terminal",
  "tool_input":      {"command": "rm -rf /"},
  "session_id":      "sess_abc123",
  "cwd":             "/home/user/project",
  "extra":           {...}
}
```

**字段约定**：
- `extra`：捕获 kwargs 中非核心字段（如 `model`、`messages` 等事件特定参数）
- `cwd`：自动注入当前工作目录，供脚本中相对路径解析使用

### stdout（Shell 脚本 → Hermes）

**pre_tool_call 阻止指令**（两种格式均支持）：
```json
{"decision": "block", "reason": "Forbidden command"}   // Claude-Code 兼容格式
{"action": "block", "message": "Forbidden command"}    // Hermes 标准格式
```

**pre_llm_call 上下文注入**：
```json
{"context": "Today is Friday, user is in Tokyo timezone"}
```

**无操作**：
```json
{}   // 空对象 → 无任何行为
"任何非 JSON 或非对象输出"  → 忽略
```

**Exit code 语义**：
- `0`：正常执行（即使返回 block 指令也允许脚本通过 exit 0 报告信息）
- `非 0`：记录 warning 但仍尝试解析 stdout

## 6. Subprocess 执行模型

```python
proc = subprocess.run(
    argv,                        # shlex.split(os.path.expanduser(command))
    input=stdin_json,
    capture_output=True,
    timeout=spec.timeout,        # 默认 60s，最大 300s
    text=True,
    shell=False,                 # ← 关键安全设计：防 shell 注入
)
```

**安全特性**：
1. `shell=False`：不使用 shell 解析器， argv[0] 必须是真实可执行文件路径
2. `os.path.expanduser`：支持 `~/` 路径展开
3. `shlex.split`：标准化空格/引号处理
4. **不做 shell 注入**：命令链（`;`, `|`, `&&`）需包装到脚本中

**超时控制**：
- `timeout < 1` → 回退为 DEFAULT_TIMEOUT_SECONDS (60)
- `timeout > MAX_TIMEOUT_SECONDS (300)` → 自动 clamp 到 300
- 超时后 `subprocess.TimeoutExpired` 被捕获，记录 warning 但不 crash

**错误处理**：
- `FileNotFoundError`：命令不存在 → log warning，callback 返回 None
- `PermissionError`：不可执行 → 同上
- 其他异常：防御性捕获，返回 error dict

## 7. 同意书与 Allowlist 机制

### Allowlist 文件

路径：`~/.hermes/shell-hooks-allowlist.json`

```json
{
  "approvals": [
    {
      "event": "pre_tool_call",
      "command": "/usr/local/bin/my-gate.sh",
      "approved_at": "2026-04-01T12:00:00Z",
      "source": "cli_prompt"
    }
  ]
}
```

### 同意决策流程

```
register_from_config()
  │
  ├─ 已 allowlisted？→ 直接注册
  │
  └─ 未 allowlisted？
        │
        ├─ accept_hooks=True？→ 自动批准（CLI --accept-hooks / HERMES_ACCEPT_HOOKS=1）
        │
        └─ accept_hooks=False？
              │
              ├─ TTY 可用？→ 显示征求提示，等待用户 Y/n
              │
              └─ 非 TTY？→ log warning，跳过注册（下次仍会提示）
```

### 跨进程竞争处理

```
文件锁：~/.hermes/shell-hooks-allowlist.json.lock（fcntl.flock）
- POSIX：fcntl.flock(LOCK_EX) 保护读-改-写
- 非 POSIX（Windows）：threading.Lock（进程内保护）
- atomic_replace() 原子替换写入
```

## 8. 核心场景示例

### 场景 1：工具门禁（pre_tool_call + block）

```yaml
# ~/.hermes/cli-config.yaml
hooks:
  pre_tool_call:
    - command: "/usr/local/bin/confirm-dangerous.sh"
      matcher: "terminal"
      timeout: 30
```

```bash
#!/bin/bash
# /usr/local/bin/confirm-dangerous.sh
read -r tool_name
read -r tool_input

if echo "$tool_input" | grep -qE "rm\s+-rf\s+/|drop\s+database"; then
  echo '{"action": "block", "message": "Dangerous command blocked by policy"}'
  exit 1
fi
exit 0
```

### 场景 2：动态上下文注入（pre_llm_call）

```yaml
hooks:
  pre_llm_call:
    - command: "/usr/local/bin/inject-time.sh"
```

```bash
#!/bin/bash
echo "Today is $(date '+%Y-%m-%d %A')"
```

stdout → Hermes 注入为 LLM call 的额外 context。

### 场景 3：工具结果脱敏（transform_tool_result）

注：当前 Hermes 代码中此事件触发点未完全实现，仅预留了扩展位。

## 9. 与 Python 插件的关系

```
PluginManager._hooks[event] 是个 list
Python 插件注册 → list.append(py_callback)
Shell 钩子注册 → list.append(shell_callback)
invoke_hook() → 顺序调用所有 callback，直到某个返回非 None
```

**优先级**：
- Python 插件先注册（`discover_and_load()` 在前）
- Shell hooks 后注册（`register_from_config()` 在后）
- 但 `invoke_hook()` 按注册顺序遍历，**无内置优先级机制**
- `PluginManager` 内部有 `_hook_aggregators` 支持聚合返回值，但 shell hooks 不使用此机制

## 10. BlueCortexCE 落地借鉴

### 10.1 Shell-based 扩展模式（P1）

Hermes 的 Shell Hooks 证明了一种"用户无需修改核心代码即可扩展行为"的架构可行性。对于 BlueCortexCE：

| 维度 | Hermes | BlueCortexCE 现状 | 借鉴建议 |
|------|--------|-------------------|----------|
| 事件类型 | 17 种生命周期事件 | 5 种 Lifecycle Hook | 扩展 Hook 类型 |
| 扩展语言 | Shell 脚本 | Java/Spring 直接实现 | 考虑脚本化扩展（Groovy/JS） |
| 同意机制 | allowlist 文件 | 无 | 参照 Hermes 安全模型 |
| 超时控制 | 60s/300s 两档 | 无 | Tool call 加入超时限制 |

### 10.2 MCP 层 GuardrailController 对照

Doc 98 中已记录 `ToolCallGuardrailController`，其三层检测（exact failure / same-tool failure / idempotent no-progress）与 Shell Hooks 的 `pre_tool_call` 拦截形成**双层防护**：

```
Hermes:
  Shell pre_tool_call (用户自定义, shell=False)  ─┐
  Python ToolCallGuardrailController (内置)      ─┼─ 双重 block 能力
  Tool actual execution                           ─┘

BlueCortexCE:
  MCP 层 GuardrailController (内置)              ─┐
  [缺少用户自定义 pre-tool 层]                   ─┘ ← 待补充
```

**P1 建议**：BlueCortexCE MCP 层支持用户自定义 pre-tool guard scripts（类似 Hermes Shell Hooks but in-process for better performance）。

### 10.3 同意书机制（Security，P1）

Hermes 的 allowlist + TTY prompt 机制确保用户明确知晓并同意每个 shell hook 的注册。BlueCortexCE 若引入类似扩展机制，应参照：

1. **首次使用征求同意**：不可静默注册
2. **Allowlist 持久化**：`~/.bluecortexce/shell-hooks-allowlist.json`
3. **非 TTY 跳过机制**：Gateway 环境下需 `accept_hooks=true` flag
4. **atomic_replace 写入**：防止进程崩溃导致 allowlist 损坏

### 10.4 Wire Protocol 设计（Medium）

Hermes Shell Hooks 的 JSON stdin/stdout 设计清晰且可扩展：
- stdin 包含 `hook_event_name` + 事件特定字段 + `cwd` + `extra`
- stdout 支持多种响应类型（block / context injection / no-op）

BlueCortexCE 若设计 SDK 级别的 hook extension，可参照此设计：
```json
// 假设未来 BlueCortexCE Shell Extension
{"event": "preobservation", "session_id": "...", "content": "..."}
{"action": "block", "reason": "contains PII"}
{"context": "enriched observation with metadata"}
```

## 11. 代码质量评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **线程安全** | ✅ 优秀 | `_registered_lock` + `_allowlist_write_lock` 双锁；POSIX `fcntl.flock` 处理跨进程竞争 |
| **内存管理** | ✅ 良好 | 无长期状态累积，subprocess 每事件独立生命周期 |
| **输入验证** | ✅ 优秀 | `shlex.split` 防注入；timeout 自动 clamp；matcher 编译失败 fallback 到 literal |
| **错误处理** | ✅ 优秀 | `FileNotFoundError`/`PermissionError`/`TimeoutExpired` 全覆盖；Fail-open 不影响主流程 |
| **安全设计** | ✅ 优秀 | `shell=False` 防注入；allowlist 持久化；atomic_replace 写入 |
| **可测试性** | ⚠️ 中等 | `reset_for_tests()` 支持测试隔离；但 `_spawn` 依赖 subprocess 真实执行 |

**发现的问题**：无 P0/P1/P2 级别 bug。

## 12. 相关文档

- Doc 98: [`98-tool-call-loop-guardrails-and-file-safety.md`](98-tool-call-loop-guardrails-and-file-safety.md)（GuardrailController 与 Shell Hooks 在 `pre_tool_call` 的互补关系）
- Doc 92: [`60-evolution/92-upstream-aa88dcc57-memory-analysis.md`](60-evolution/92-upstream-aa88dcc57-memory-analysis.md)（compressor 与 hook 交互）
- Doc 96: [`60-evolution/96-hermes-memory-architecture-synthesis-and-ce-roadmap.md`](60-evolution/96-hermes-memory-architecture-synthesis-and-ce-roadmap.md)（架构综合与 CE 路线图）
- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)
