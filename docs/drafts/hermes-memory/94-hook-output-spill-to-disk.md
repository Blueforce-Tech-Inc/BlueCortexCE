# 94. Hook Output Spill-to-Disk 机制深度解析

**Commit**: `b6c53ef0b` (`feat(hooks): spill oversized hook-injected context to disk`)  
**来源**: Port from `openai/codex` PR #21069  
**日期**: 2026-05-05  
**优先级**: ⭐⭐⭐ P1（CE 注入面可借鉴）

---

## 一、问题背景

**核心矛盾**：Hook 系统（Shell Hooks + Python Plugin `pre_llm_call`）可以返回 `{"context": "..."}`，这段内容会被拼接到**当前轮次的用户消息**中，并在**会话的每一个后续 API 调用**中持续累加。

**危害链**：
```
Hook 误返回大文本（如 debug dump / 全文件内容）
  → 每轮 user message 都携带该内容
    → prompt cache prefix 失效（内容一变 cache 就废）
      → 每轮都重新编码完整 prefix
        → token 消耗爆炸式增长
```

**与 CE 的类比**：Claude-Mem 的 `/api/ingest` 端点、`/api/context/generate` 的 `updateFiles`、Hook 机制中的 `pre_llm_call` 注入，都有类似的「上下文膨胀」风险。

---

## 二、架构设计

### 2.1 核心文件

| 文件 | 职责 |
|------|------|
| `tools/hook_output_spill.py` | 核心逻辑：判断是否溢出、落盘、生成 preview |
| `run_agent.py` | 在 `pre_llm_call` 聚合点统一调用 spill |
| `agent/shell_hooks.py` | 将 `output_spill` 注册为 `hooks:` 下的 reserved sub-key |

### 2.2 配置项（`config.yaml`）

```yaml
hooks:
  output_spill:
    enabled: true          # default: true；设为 false 则禁用 spill，行为完全兼容旧版
    max_chars: 10000       # 超过此阈值则 spill；default: 10000
    preview_head: 500      # 预览头部字符数；default: 500
    preview_tail: 500       # 预览尾部字符数；default: 500
    directory: null        # 溢出文件存放目录；default: $HERMES_HOME/hook_outputs
```

**配置原则**：所有字段可选；缺少任何字段都 fallback 到默认值 → **行为在旧配置下完全不变**。

### 2.3 溢出路径

```
$HERMES_HOME/hook_outputs/
  <session_id>/
    <uuid1>.txt   ← 完整原始文本（UTF-8，末尾保证换行）
    <uuid2>.txt
```

- **session_id 做目录隔离**：新 session 不污染旧 session 的 spill 文件
- **Path Traversal 防护**：`session_id` 中的 `/`、`\`、`..` 会被替换为 `_`
- **文件名用 UUID**：避免冲突，不暴露内部信息

### 2.4 Preview 格式

```
[hook output truncated — 45,230 chars; full content saved to /path/to/spill/file.txt]
--- head ---
<前 500 字符>
--- tail ---
<后 500 字符>
```

**设计意图**：Model 可以通过 `read_file` 或 `terminal` 自行读取完整内容。

---

## 三、核心实现解析

### 3.1 `spill_if_oversized()` 主流程

```python
def spill_if_oversized(text, *, session_id, source, config):
    # 1. None / 非字符串 → 空安全处理
    # 2. enabled=false → 直接返回原文本（不变）
    # 3. len(text) <= max_chars → 直接返回原文本（不变）
    # 4. 超过阈值 → 写磁盘 → 返回 preview
    # 5. 写磁盘失败 → 仍返回 preview（带 "spill write failed" 提示）
    #    绝不抛出异常（never raises）
```

**Never Raises 不变量**：任何 I/O 错误（磁盘满、权限、目录不存在）都 catch 住，fallback 到 preview-only 字符串，保证模型仍然收到有界的上下文。

### 3.2 `get_spill_config()` 配置解析

- `max_chars`：正整数，≤0 → fallback
- `preview_head` / `preview_tail`：非负整数（允许 0，即 tail 可以为空）
- `enabled`：强转为 bool
- `directory`：非字符串 → None（使用默认路径）

### 3.3 Shell Hook 中的 Reserved Sub-Key 模式

```python
# agent/shell_hooks.py
for event_name, entries in hooks_cfg.items():
    if event_name in ("output_spill",):   # ← reserved，不触发 "unknown hook event" 警告
        continue
    if event_name not in VALID_HOOKS:
        logger.warning("unknown hook event %r ...", event_name, suggestion=suggestion)
```

**Pattern 价值**：在 YAML 的 `hooks:` section 下，允许有 `output_spill` 这样的「配置子节」而不触发 unknown event 警告。这是一种**嵌套配置 + 静默跳过**的模式，值得 CE 借鉴。

---

## 四、在 run_agent.py 中的集成点

```python
# pre_llm_call hook 聚合结果合并
_ctx_parts: list[str] = []
try:
    from tools.hook_output_spill import (
        get_spill_config as _spill_cfg,
        spill_if_oversized as _spill_if_oversized,
    )
    _spill_config_cached = _spill_cfg()   # 模块级缓存，只 load 一次
except Exception:
    _spill_if_oversized = None            # 导入失败 → 禁用 spill

for r in _pre_results:
    _piece = r.get("context") if isinstance(r, dict) else r
    if _spill_if_oversized is not None:
        _piece = _spill_if_oversized(
            _piece,
            session_id=self.session_id,    # ← 传入 session_id 做目录隔离
            source="plugin hook",
            config=_spill_config_cached,
        )
    _ctx_parts.append(_piece)

if _ctx_parts:
    _plugin_user_context = "\n\n".join(_ctx_parts)
```

**集成位置**：在 `pre_llm_call` hook 的结果聚合阶段，覆盖了：
1. Python plugin hooks（通过 `invoke_hook` 调用）
2. Shell hooks（也通过 `invoke_hook` 路径）

两个来源在同一个循环中被 spill 处理，**统一了处理边界**。

---

## 五、CE 落地借鉴

### 5.1 立即可落地（P1）

**场景**：CE 的 Hook/Injection 系统中，如果某个 observation 或 context 片段过大，可以在 proxy 层或 service 层做 spill-to-disk + preview。

**具体方案**：
1. 在 `proxy/wrapper.js` 或 backend service 中检测注入内容大小
2. 超过阈值 → 写 `$CORTEX_MEM_HOME/hook_outputs/<session_id>/<uuid>.txt`
3. 返回 preview（head + tail + 文件路径）
4. Model 可通过 `read_file` 工具读取完整内容

**与 Hermes 的区别**：CE 目前的注入不是通过 hook system，而是通过 `/api/context/generate` 的 `updateFiles` 机制和 ICL（In-Context Learning）。可以把这个 pattern 迁移到 context injection pipeline。

### 5.2 Config 设计（P1）

CE 的 Spring Boot 配置中可以考虑类似的嵌套配置：

```yaml
cortex-mem:
  injection:
    spill:
      enabled: true
      max-chars: 8000      # 低于 Hermes（10000），CE context 更紧凑
      preview-head: 300
      preview-tail: 300
```

### 5.3 Never Raises 不变量（P2）

CE 的文件写入 / 内容注入应该有类似的不变量：**任何存储失败都不应该导致 request 失败**。Fallback 到截断内容，让 model 仍然收到有界上下文。

### 5.4 Reserved Sub-Key 模式（P2）

CE 没有 shell hook，但在 YAML 配置解析中，这个「嵌套配置 + 静默跳过 reserved key」的模式可以用于：
- 在现有配置节下添加新的子配置，而不影响旧的 consumers
- 不需要版本号来判断字段是否有效

---

## 六、安全与运维

### 6.1 磁盘空间

- 每 session 一个目录，`/new` 后不会持续增长（除非同一个 session 内多次溢出）
- 没有自动清理机制（依赖 session 生命周期 + 手动清理）
- **CE 借鉴**：可以在 session finalization 时清理对应 spill 目录

### 6.2 Path Traversal 防护

Session ID 中的 `/`、`\`、`..` 全部替换为 `_`，防止逃逸到 `HERMES_HOME` 之外。

### 6.3 权限

使用 `Path.write_text(..., encoding="utf-8")`，依赖进程权限写 `$HERMES_HOME/hook_outputs/`。

---

## 七、与 CE Phase 3 Structured Extraction 的关系

Phase 3 的 `mergeAppendOnly` 机制已经在处理 observation 去重和历史信息保留。Hook Output Spill 机制提供了一个**容量护栏**——即使提取逻辑输出大量内容，也能通过 spill 避免撑爆 context window。

两者结合：
```
Large Observation 
  → [spill_if_oversized] 
    → preview (head+tail) + spill file path 
      → stored in DB as preview
  → [Phase 3 mergeAppendOnly] 
    → deduplicated into session memory
  → Model 可以 later read_file 完整内容
```

---

## 八、总结

| 维度 | 评估 |
|------|------|
| **架构价值** | ⭐⭐⭐ 把「可能无限增长的注入内容」变成「有界 preview + 可选磁盘访问」 |
| **CE 借鉴可行性** | ⭐⭐⭐ 高；proxy 层 / service 层均可落地 |
| **安全内建** | ⭐⭐⭐ never raises + path traversal 防护 |
| **配置优雅性** | ⭐⭐⭐ reserved sub-key 模式 + 行为兼容旧配置 |
| **测试覆盖** | 14 unit tests + 49 existing shell_hooks tests + 62 plugin tests |

**核心 Pattern**：**容量护栏（Capacity Guard）**——在内容聚合点加一个大小检测，超过阈值就 spill 到磁盘，同时返回有界 preview。这个 pattern 是 CE context injection 和 ICL 管理中缺失的一环。
