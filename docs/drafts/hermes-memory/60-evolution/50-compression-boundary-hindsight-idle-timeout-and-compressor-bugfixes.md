# 上游新提交分析（2026-04-26）：压缩边界信号 · Hindsight 空闲超时 · 压缩机鲁棒性

**scan 范围**：`cec0af02` → `origin/main`（约 50 commits；全部见 `git log cec0af02..origin/main --oneline`）

**文档版本**：v8.9 — 2026-04-27

---

## §1 Compression Boundary Signal — `on_session_start(boundary_reason="compression")`

**commit**: `e85b7525`（Tosko4，PR #13370）

### 问题背景

当 `_compress_context` 执行压缩分裂（rotation）时，会生成一个新的 `session_id`。外部 ContextEngine 插件（如 `hermes-lcm`）将此次分裂视为**全新的 `/new` 会话**，导致：
- `compression_count` 重置为 1
- `store_messages` 归零
- DAG 节点丢失

这在 `hermes-lcm#68` 中被报告为「压缩后 LCM 丢失连续性」。

### 修复机制

在 `run_agent.py` 的压缩分裂路径中，新增信号调用：

```python
# run_agent.py ~line 8173
try:
    _old_sid = locals().get("old_session_id")
    if _old_sid and hasattr(self.context_compressor, "on_session_start"):
        self.context_compressor.on_session_start(
            self.session_id or "",
            boundary_reason="compression",
            old_session_id=_old_sid,
        )
except Exception as _ce_err:
    logger.debug("context engine on_session_start (compression): %s", _ce_err)
```

### 关键设计点

| 维度 | 说明 |
|------|------|
| **信号名** | `on_session_start`（已有方法；新增 `boundary_reason` + `old_session_id` 参数） |
| **触发时机** | 压缩分裂后（`_compress_context` 成功创建新 session_id） |
| **Plugin 用途** | `hermes-lcm` 等外部 ContextEngine 用 `boundary_reason="compression"` 保留 DAG lineage |
| **内置兼容** | `ContextCompressor.on_session_start` 接受 `**kwargs` 并忽略 — 无行为变化 |
| **错误处理** | `try/except` + `logger.debug` — 不阻塞压缩流程 |

### CE 启示

CE 的 `StructuredExtractionService` 若未来支持**跨压缩分裂的提取状态连续性**（如「第 2 次压缩后继续从第 1 次的 checkpoint 提取」），可借鉴此信号机制。CE 当前无压缩分裂（Session 在 phase 3 中生命周期不同），但 `ContextService.on_session_start` 或类似 hook 可为此预留。

---

## §2 Hindsight Idle Timeout — 嵌入式 Daemon 空闲关闭

**commit**: `0ba6471d`（Wysie）

### 原有行为

Hindsight 嵌入式 Daemon（`HindsightEmbedded`）启动后永久运行，除非手动关闭。

### 新增配置

| 环境变量 / Config 键 | 默认值 | 说明 |
|------|------|------|
| `HINDSIGHT_IDLE_TIMEOUT` | `300`（秒） | 嵌入式 daemon 空闲超时；`0` 禁用关闭 |
| `config.json` → `idle_timeout` | 同上 | 等效配置项 |

### 新增文件改动（`plugins/memory/hindsight/__init__.py`）

- `HINDSIGHT_IDLE_TIMEOUT` env var 解析
- `_build_embedded_profile_env()` 测试辅助函数新增
- 测试覆盖：`_clean_env` 新增 `HINDSIGHT_IDLE_TIMEOUT` 清理

### CE 启示

CE 的 `HindsightLocalEmbeddingService`（若后续引入嵌入式模式）应考虑类似 idle timeout 机制，避免后台进程持续占用资源。Phase 3 中 embedding service 的生命周期管理可参考此设计。

---

## §3 Background Review Memory Provider Teardown

**commit**: `2d86e97a`（MRHwick）

### 问题

临时后台评审 Agent（`_spawn_background_review`）可能初始化基于 Hindsight 的内存客户端。原有代码直接调用 `close()`，**跳过 Provider teardown**，导致 aiohttp session 泄漏直到进程退出。

### 修复

```python
# run_agent.py ~line 3307
finally:
    if review_agent is not None:
        try:
            review_agent.shutdown_memory_provider()  # 新增：先关闭 Provider
        except Exception:
            pass
        try:
            review_agent.close()  # 再关闭 Agent
        except Exception:
            pass
```

### CE 启示

CE 若未来引入**后台评审 Agent**（如自动反思服务），需确保 `shutdown_memory_provider()` 在 `close()` 之前被调用，防止连接泄漏。这对长期运行的 Java 服务尤为重要（连接池耗尽风险）。

---

## §4 Hindsight Async Client Loop Attachment Fix

**commit**: `18beb69b`（maxims-oss）

### 问题

`HindsightEmbedded.close()` 调用同步客户端的 `close()`。当 Hermes 在共享 async event loop 上创建/使用该客户端时，从主线程关闭会触发：

```
RuntimeError: attached to a different loop
```

导致 `ClientSession` / `TCPConnector` 泄漏。

### 修复方案

未获取完整 diff（文件 `plugins/memory/hindsight/__init__.py` 改了 137 行）。核心思路：
- 检测 loop attachment 状态
- 在正确的 loop 上执行关闭
- 或使用 `call_soon` / `asyncio.get_event_loop()` 桥接

### CE 启示

CE 的 Java 服务中，若使用 async HTTP 客户端（WebClient / RestTemplate async），在多线程环境下关闭时需确保 loop/线程一致性。Java 中通常通过 `@Async` 线程池隔离解决。

---

## §5 ContextCompressor 鲁棒性三连击

### 5.1 Bare-String Guard — 多模态 Content List（`94346523`）

**问题**：`message["content"]` 可能是 `[{"type":"text","text":"..."}, "plain_string", ...]` 的混合列表。`.get("text", "")` 对 bare string 调用导致 `AttributeError`。

**修复**：
```python
# 修复前
text = p.get("text", "")

# 修复后
if not isinstance(p, dict):
    continue  # 或取 str(p)
text = p.get("text", "") if isinstance(p, dict) else ""
```

### 5.2 Bare-String Guard — Protect-Tail Boundary（`bda2dbc2`）

**问题**：`_calculate_protect_tail_boundary`（约 line 487）中存在**相同模式**的 `.get("text", "")` 调用，同样在 bare string 上崩溃。`80ae2621` 只修了 `_find_tail_cut_by_tokens`，遗漏了此处。

**修复**：应用相同 `isinstance(dict)` guard。

### 5.3 Token 估算 — Multimodal Block 计数（`cfc8befe`）

**问题**：`_find_tail_cut_by_tokens` 调用 `len(content)` 估算 token 数。当 content 是 `[text_block, image_url_block]` 多模态列表时，`len()` 返回 **block 数量**（如 2），而非字符数。500 字符的文本消息被计为 ~10 tokens 而非 ~135。

**修复**：
```python
# 修复前
token_est = len(content)

# 修复后：multimodal list 时用文字部分字符数估算
if isinstance(content, list):
    text_chars = sum(
        len(p.get("text", "")) if isinstance(p, dict) else len(str(p))
        for p in content
    )
    token_est = text_chars // 4  # 字符→token 近似
else:
    token_est = len(content)
```

### CE 启示

CE 的 `ContextCompressor`（`cortex-ce` 中的压缩逻辑）若有类似多模态 content 处理，需同样防御 bare-string 和 `len()` 误用。当前 CE 主要处理文本 JSON，暂无此风险，但建议在 `mergeAppendOnly` 等结构化操作中对数组元素类型做显式检查。

---

## §6 SessionSearchTool — 排除当前 Lineage Root（`dbe50155`）

**问题**：在「最近会话」模式搜索时，当前会话的 lineage root 被意外包含在结果中。

**修复**：`session_search_tool.py` 中新增 deterministic exclusion — 排除当前 lineage root。

```python
# tools/session_search_tool.py（+3/-1）
# 确保当前会话 lineage root 不出现在最近模式搜索结果中
```

### CE 启示

CE 的 `SearchService` 若实现「最近会话搜索」，需确保**当前 session 自身及其 lineage root 祖先**被排除，避免自引用混淆结果。

---

## §7 全部新 Commits 索引

| Commit | 文件 | 说明 |
|--------|------|------|
| `e85b7525` | `run_agent.py` | 压缩分裂时向 ContextEngine 发 `boundary_reason="compression"` 信号 |
| `0ba6471d` | `plugins/memory/hindsight/__init__.py` | 新增 `HINDSIGHT_IDLE_TIMEOUT` 嵌入式 daemon 空闲关闭 |
| `2d86e97a` | `run_agent.py` | `shutdown_memory_provider()` 在 `close()` 前调用 |
| `18beb69b` | `plugins/memory/hindsight/__init__.py` | 修复 Hindsight 嵌入式异步客户端 loop attachment 泄漏 |
| `94346523` | `agent/context_compressor.py` | 多模态 bare-string `.get()` guard |
| `bda2dbc2` | `agent/context_compressor.py` | Protect-tail boundary 同等 bare-string guard |
| `cfc8befe` | `agent/context_compressor.py` | Multimodal block token 估算改用字符和 |
| `dbe50155` | `tools/session_search_tool.py` | 最近模式搜索排除当前 lineage root |
| `64a497bf` | `plugins/memory/hindsight/__init__.py` | 修复空输入时保留 setup config |

---

## §8 与现有文档交叉索引

| 主题 | 关联已有文档 |
|------|------------|
| ContextEngine 插件架构 | [`27-context-engine-pluggable-architecture.md`](27-context-engine-pluggable-architecture.md)（ContextEngine ABC / `on_session_start` 签名） |
| Hindsight 嵌入式 Daemon | [`25-hindsight-local-embedded-daemon-and-postgresql-schema.md`](25-hindsight-local-embedded-daemon-and-postgresql-schema.md)（HindsightEmbedded 架构） |
| Background Review Agent | [`48-flush-memories-removal-and-background-review-architecture.md`](48-flush-memories-removal-and-background-review-architecture.md)（`_spawn_background_review` 架构） |
| ContextCompressor 完整算法 | [`24-context-compressor-full-algorithm.md`](24-context-compressor-full-algorithm.md)（四阶段压缩 / Tool Pruning / Protect-Tail） |
| Compression Eval Harness | [`49-compression-eval-harness-and-structured-extraction-quality.md`](49-compression-eval-harness-and-structured-extraction-quality.md)（Phase 3 质量评估借鉴） |
