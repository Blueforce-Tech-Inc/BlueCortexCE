# 98 Tool Call Loop Guardrails + File Safety — 深度解析

> **Source**: `agent/tool_guardrails.py` (455 lines) + `agent/file_safety.py` (111 lines)  
> **Date**: 2026-05-07  
> **Hermes upstream**: `origin/main` (`946ef0ea1`, 2026-05-06)

---

## 0. 概述

Hermes Agent 的 `_tool_call_loop_guardrails` 和 `_file_safety` 是两个互补的**安全与可靠性**组件，均为纯函数式设计（无副作用，仅返回决策）：

| 组件 | 文件 | 行数 | 职责 |
|------|------|------|------|
| **ToolCallLoopGuardrails** | `tool_guardrails.py` | 455 | 检测并阻止工具调用循环（重试失败/无进展重复调用） |
| **FileSafety** | `file_safety.py` | 111 | 文件读写路径安全审查（敏感路径写入拦截 / Hermes 内部缓存读取拦截） |

两者共同构成 Hermes Agent 的**主动防御层**，在模型执行阶段而非后处理阶段介入。

---

## 1. Tool Call Loop Guardrails — 架构全解

### 1.1 核心设计理念

`ToolCallGuardrailController` 是**无副作用的状态机**：
- `before_call()` — 工具执行前检查（决定是否放行）
- `after_call()` — 工具执行后检查（更新计数，决定是否警告/阻止）
- `reset_for_turn()` — 每轮重置，避免跨轮状态污染

**关键约束**：hard stop 默认**关闭**，用户需在 `config.yaml` 显式启用。这是一种"安全默认值"（safe default）设计——默认只警告不阻止，避免阻断合法长操作。

### 1.2 工具分类白名单

```python
IDEMPOTENT_TOOL_NAMES = frozenset({
    "read_file", "search_files", "web_search", "web_extract",
    "session_search", "browser_snapshot", "browser_console",
    "mcp_filesystem_read_file", "mcp_filesystem_list_directory", ...
})

MUTATING_TOOL_NAMES = frozenset({
    "terminal", "execute_code", "write_file", "patch", "todo",
    "memory", "skill_manage", "browser_click", "browser_navigate",
    "send_message", "cronjob", "delegate_task", "process",
})
```

**设计意图**：
- **幂等工具**（读类）：允许检测"重复返回相同结果"的循环
- **变更工具**（写类）：不追踪无进展，因为写操作本身就有进展

### 1.3 三种检测模式

#### 模式 A：Exact Failure（完全重复失败）

```
same tool_name + same args (SHA256 hash) + failed result
```

- **触发条件**：精确相同的工具调用（参数也相同）连续失败 N 次
- **默认阈值**：警告 ≥2 次，阻止 ≥5 次
- **典型场景**：`read_file("/path")` 一直返回 "file not found" 但模型仍重复调用

```python
exact_count = self._exact_failure_counts.get(signature, 0) + 1
if exact_count >= self.config.exact_failure_warn_after:
    return ToolGuardrailDecision(action="warn", code="repeated_exact_failure_warning", ...)
if exact_count >= self.config.exact_failure_block_after:
    return ToolGuardrailDecision(action="block", code="repeated_exact_failure_block", ...)
```

#### 模式 B：Same-Tool Failure（同类工具重复失败）

```
same tool_name + any args + failed result
```

- **触发条件**：同一工具名（参数无关）连续失败
- **默认阈值**：警告 ≥3 次，阻止 ≥8 次
- **典型场景**：模型连续调用 8 次 `terminal` 全部失败（环境问题而非参数问题）

#### 模式 C：Idempotent No-Progress（幂等工具无进展）

```
same tool_name + same args + same result (hash) + not failed
```

- **触发条件**：幂等工具返回完全相同结果（未被标记为 failed）重复 N 次
- **默认阈值**：警告 ≥2 次，阻止 ≥5 次
- **典型场景**：`search_files("TODO")` 重复返回相同搜索结果，模型却不利用结果继续循环

**注意**：变更类工具不进入此检测（写操作本身就有进展意义）。

### 1.4 ToolCallSignature — 稳定化工具身份

```python
@dataclass(frozen=True)
class ToolCallSignature:
    tool_name: str
    args_hash: str  # SHA256 of sorted canonical JSON
```

**设计要点**：
- `frozen=True` 使其可哈希，用于 Dict key
- 参数标准化（`canonical_tool_args`）：排序键、JSON紧凑序列化、`default=str`（防止不可序列化对象崩溃）
- **不可逆**：只存 hash，不存原始参数（隐私+体积）

### 1.5 配置模型

```python
@dataclass(frozen=True)
class ToolCallGuardrailConfig:
    warnings_enabled: bool = True    # 默认开
    hard_stop_enabled: bool = False  # 默认关（安全默认值）
    
    # 阈值均可通过 config.yaml 覆盖
    exact_failure_warn_after: int = 2
    exact_failure_block_after: int = 5
    same_tool_failure_warn_after: int = 3
    same_tool_failure_halt_after: int = 8
    no_progress_warn_after: int = 2
    no_progress_block_after: int = 5
    
    idempotent_tools: frozenset[str] = field(default_factory=lambda: IDEMPOTENT_TOOL_NAMES)
    mutating_tools: frozenset[str] = field(default_factory=lambda: MUTING_TOOL_NAMES)
```

**可组合性**：每个维度独立阈值，允许多细粒度配置（如"读类工具容忍度更高"）。

### 1.6 输出模式

Guardrail 决策有 4 种 `action`：

| Action | 含义 | 后续处理 |
|--------|------|----------|
| `allow` | 正常执行 | 无干预 |
| `warn` | 警告但不阻止 | 在结果后追加 `[Tool loop warning: ...]` |
| `block` | 阻止并终止本轮 | 返回 synthetic error JSON |
| `halt` | 同一工具路径彻底停用 | 返回 synthetic error JSON |

```python
def append_toolguard_guidance(result: str, decision: ToolGuardrailDecision) -> str:
    """Append runtime guidance to the current tool result content."""
    suffix = f"\n\n[{label}: {decision.code}; count={decision.count}; {decision.message}]"
    return (result or "") + suffix
```

---

## 2. File Safety — 路径安全双防线

### 2.1 写入拒绝（Write Denylist）

```python
def build_write_denied_paths(home: str) -> set[str]:
    return {
        "~/.ssh/authorized_keys", "~/.ssh/id_rsa", "~/.ssh/id_ed25519",
        "~/.bashrc", "~/.zshrc", "~/.profile",
        "~/.netrc", "~/.pgpass",
        "$HERMES_HOME/.env",
        "/etc/sudoers", "/etc/passwd", "/etc/shadow",
    }

def build_write_denied_prefixes(home: str) -> list[str]:
    return [
        "~/.ssh/", "~/.aws/", "~/.gnupg/", "~/.kube/",
        "/etc/sudoers.d/", "/etc/systemd/",
        "$HERMES_HOME/.env",
        "~/.docker/", "~/.azure/", "~/.config/gh/",
    ]
```

**设计要点**：
- **精确路径**（`authorized_keys`, `id_rsa`）vs **目录前缀**（`~/.ssh/` 整个目录）
- Hermes 内部路径（`$HERMES_HOME/.env`）单独处理
- 敏感系统文件（`/etc/shadow`）直接拒绝

### 2.2 Safe Root 隔离

```python
def is_write_denied(path: str) -> bool:
    safe_root = get_safe_write_root()  # HERMES_WRITE_SAFE_ROOT env
    if safe_root and not (resolved == safe_root or resolved.startswith(safe_root + os.sep)):
        return True
```

通过 `HERMES_WRITE_SAFE_ROOT` 环境变量，限制写操作只能在指定目录树下。

### 2.3 内部缓存读取拦截

```python
def get_read_block_error(path: str) -> Optional[str]:
    blocked_dirs = [
        "$HERMES_HOME/skills/.hub/index-cache",
        "$HERMES_HOME/skills/.hub",
    ]
    if resolved under blocked:
        return "Access denied: ... Use skills_list or skill_view tools instead."
```

**安全意图**：防止通过直接读文件绕过工具抽象，注入恶意提示词。

---

## 3. 集成点

### 3.1 Tool Guardrails 集成

在 `run_agent.py` 的工具调用循环中：

```python
# before call
decision = self._tool_call_guardrails.before_call(tool_name, tool_args)
if decision.should_halt:
    return toolguard_synthetic_result(decision)

# after call
decision = self._tool_call_guardrails.after_call(
    tool_name, tool_args, result_str, failed=has_error
)
if decision.action == "warn":
    result_str = append_toolguard_guidance(result_str, decision)
elif decision.should_halt:
    return toolguard_synthetic_result(decision)
```

### 3.2 File Safety 集成

在工具执行层（如 `write_file`, `terminal`）执行前：

```python
if is_write_denied(path):
    raise PermissionError(f"Write to {path} is denied by policy")
```

---

## 4. CE 落地借鉴

### 4.1 Tool Call Loop Guardrail — CE 适用性

**CE 当前状态**：无等效工具调用循环检测。

**CE 可借鉴场景**（按优先级）：

| 优先级 | CE 场景 | 借鉴方案 |
|--------|----------|----------|
| ⭐⭐ P1 | SDK 工具调用循环 | 在 `CortexMemClient` 或 proxy 层引入 `ToolCallGuardrailController`，防止 SDK 客户端重复调用同一工具陷入循环 |
| ⭐⭐ P1 | MCP 工具循环 | 在 `ClaudeMemMcpTools` 接入层增加循环检测，防止 MCP 客户端循环调用 |
| ⭐ P2 | Session search 循环 | `session_search` 返回空结果时模型可能重复调用，GuardrailController 可提前终止 |
| ⭐ P3 | Extraction 循环 | Structured extraction 连续失败时，给出警告而非无限重试 |

**CE 实施路径**：

```java
// CE 伪代码 — MCP 工具层
public class ToolCallGuardrailController {
    private final Map<String, Integer> exactFailureCounts = new ConcurrentHashMap<>();
    private final int WARN_AFTER = 2;
    private final int BLOCK_AFTER = 5;
    
    public GuardrailDecision beforeCall(String toolName, Map<String, Object> args) {
        String sig = hash(toolName, args);
        int count = exactFailureCounts.getOrDefault(sig, 0);
        if (count >= BLOCK_AFTER) {
            return GuardrailDecision.block("repeated_failure_block", toolName, count);
        }
        return GuardrailDecision.allow(toolName);
    }
    
    public GuardrailDecision afterCall(String toolName, Map<String, Object> args, boolean failed) {
        String sig = hash(toolName, args);
        if (failed) {
            int count = exactFailureCounts.merge(sig, 1, Integer::sum);
            if (count >= WARN_AFTER) {
                return GuardrailDecision.warn("repeated_failure_warning", toolName, count);
            }
        } else {
            exactFailureCounts.remove(sig);  // 成功则清除计数
        }
        return GuardrailDecision.allow(toolName);
    }
}
```

### 4.2 File Safety — CE 适用性

**CE 当前状态**：后端仅在 `ContextService` 有路径规范化（拒绝 `..` 逃逸），无文件写入安全层。

**CE 可借鉴场景**：

| 优先级 | CE 场景 | 借鉴方案 |
|--------|----------|----------|
| ⭐⭐ P0 | `write_file` 类工具 | 参考 Hermes `file_safety.py`，在 `ContextService` 或 `IngestionController` 对所有文件写入路径做 denylist 检查 |
| ⭐⭐ P1 | Template 路径注入 | 防止用户通过 `{{file:///etc/passwd}}` 注入路径读取敏感文件 |
| ⭐ P2 | WebUI 文件预览 | 对 `webui/` 内部文件路径做 `get_read_block_error()` 等效检查 |

**关键差异**：CE 是服务后端，不直接执行用户提供的文件写入操作。但其 `ContextService` 向量存储/文件读取路径（如 `backgroundFile`）需要同等防护。

### 4.3 三层防御模型总结

| 层次 | Hermes 组件 | CE 对应 |
|------|-------------|---------|
| **L1 入口过滤** | `preprocess_context_references` (injection scan) | `ContextSecurityService` 缺失 |
| **L2 执行防护** | `ToolCallGuardrailController` (loop prevention) | `ToolCallGuardrailController` 缺失 |
| **L3 写保护** | `file_safety.py` (path denylist) | `ContextService` 仅有路径规范化，无 denylist |
| **L4 泄露防护** | `sanitize_context` + `StreamingContextScrubber` | 无等效 |

**CE 优先级**：L1（L2 注入）> L4（fence）> L2（Guardrail）> L3（file safety，对 CE 影响较小）

---

## 5. 安全纵深设计亮点

### 5.1 安全默认值

`hard_stop_enabled: bool = False` — 默认只警告不阻止。这是一个重要的 UX+安全权衡：过度阻止会中断合法操作，但只警告可以让用户感知问题。

### 5.2 可组合阈值矩阵

每个检测维度独立配置（`exact_failure_*`, `same_tool_failure_*`, `no_progress_*`），允许细粒度调优而不影响其他维度。

### 5.3 失败分类标准化

`classify_tool_failure()` 与 CLI 的 `display._detect_tool_failure` 完全镜像，保证 guardrail 判断与用户可见标签一致，避免"用户看到 error 但 guardrail 认为成功"的分裂。

### 5.4 工具分类的声明式设计

幂等/变更工具分类是**声明式**的（`IDEMPOTENT_TOOL_NAMES`, `MUTATING_TOOL_NAMES` frozenset），新增工具只需更新 frozenset，无需改逻辑。

---

## 6. 版本历史

| 时间 | 变更 |
|------|------|
| 2026-04-24 | 初始文档（基于 `tool_guardrails.py` + `file_safety.py` 源码分析） |
| 2026-05-07 | 新增 upstream `aa88dcc57` 确认（memory authority 升级，非本组件） + CE 落地路径细化 |

## 7. CE 实施优先级

| 优先级 | 项目 | 工作量 | 说明 |
|--------|------|--------|------|
| ⭐⭐ P1 | MCPTools 层 GuardrailController | 中 | 防止 MCP 客户端工具循环 |
| ⭐⭐ P1 | `is_write_denied` 等效实现 | 小 | CE 后端文件路径 denylist |
| ⭐ P2 | SDK 层 GuardrailController | 中 | 防止客户端 SDK 工具循环 |
| ⭐ P3 | Configurable thresholds | 小 | 将阈值暴露为配置项 |
