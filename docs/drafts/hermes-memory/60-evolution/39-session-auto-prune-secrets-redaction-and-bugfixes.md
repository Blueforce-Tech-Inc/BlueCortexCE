# 39 — Session Auto-Prune + Secrets Redaction + Bug Fixes（2026-04-25）

**覆盖**：`b8663813` · `3368814a` · `c0385873` · `a9a4416c`

**本地 HEAD**：`e69526be` → `c61547c0`（~1586 commits）

---

## §1 Session Auto-Prune + VACUUM at Startup（`b8663813`）

### 1.1 问题背景

`state.db`（SessionDB）无限累积：
- 每条 session、message、tool call 均永久写入
- FTS5 索引条目只增不减
- 重度用户（gateway + cron）报告：384MB / 982 sessions / 68K messages
- 手动 `hermes sessions prune --older-than 7` + VACUUM 后降至 43MB
- **关键**：PRUNE + VACUUM 从未在任何地方自动触发，用户无感知地等待爆炸

### 1.2 核心机制：三层防护设计

#### 1.2.1 配置块（`sessions:` in config.yaml）

```yaml
sessions:
  auto_prune: false        # 默认 opt-in（Session history powers session_search recall）
  retention_days: 90        # 保留天数
  vacuum_after_prune: true  # 仅在 prune 实际删除行时执行 VACUUM
  min_interval_hours: 24   # 跨 Hermes 进程共享（通过 state_meta 表）
```

#### 1.2.2 `state_meta` 幂等表

```sql
CREATE TABLE state_meta (key TEXT PRIMARY KEY, value TEXT);
```

新表追踪 `last_auto_prune_at` 等 key/value，确保：
- 所有 Hermes 进程（CLI + Gateway）共享同一 `HERMES_HOME` 时，真正**只执行一次**
- 第二次调用在 `min_interval_hours` 内直接跳过
- 不依赖进程间锁或文件锁

#### 1.2.3 `maybe_auto_prune_and_vacuum()` 幂等实现

```python
def maybe_auto_prune_and_vacuum(
    self,
    retention_days: int = 90,
    min_interval_hours: int = 24,
    vacuum: bool = True,
) -> None:
    """Idempotent: skips if called within min_interval_hours."""
    last_run = self._get_meta("last_auto_prune_at")
    if last_run and (time.time() - float(last_run) < min_interval_hours * 3600):
        return  # 在间隔内，跳过
    deleted = self._prune_sessions_older_than(retention_days)
    if deleted > 0 and vacuum:
        self.vacuum()  # 仅在有实际删除时才 VACUUM
    self._set_meta("last_auto_prune_at", str(time.time()))
```

**为什么 VACUUM 很重要**：SQLite DELETE 后文件大小**不收缩**，释放页进入 freelist 供后续 INSERT 重用。**不 VACUUM 的 DB 永远膨胀**。

#### 1.2.4 两处调用点

| 调用点 | 位置 | 行为 |
|--------|------|------|
| CLI init | `cli.py:1979`（`_run_state_db_auto_maintenance`） | 启动时调用，失败不阻止交互 |
| GatewayRunner init | `gateway/run.py:710` | 启动时调用，失败不阻止服务 |

两者共享同一 helper `_run_state_db_auto_maintenance`，读取 `config.yaml` 的 `sessions:` 块，配置不存在时使用内联默认值。

### 1.3 关键设计决策

**为什么 `auto_prune` 默认 False**：
> "Session history powers session_search recall across past conversations, so silently pruning on startup could surprise users."

→ 工具已就绪，用户主动开启。避免非预期丢失导致 session_search 结果减少。

**VACUUM 仅在删除行后执行**：避免频繁 I/O 开销，紧凑 DB 不受惩罚。

**Never raises**：所有异常被 `try/except` 吞掉，仅 `logger.debug()` 记录。维护失败不能阻止启动。

### 1.4 CE 差距 + 可执行借鉴

| 方面 | Hermes | BlueCortexCE | 行动 |
|------|--------|-------------|------|
| Session 数据自动清理 | `maybe_auto_prune_and_vacuum()`（opt-in） | **缺失** | 在 SessionService 添加类似机制 |
| 长期运行 DB 膨胀 | 自动 VACUUM | 无 | 研究 PostgreSQL AUTOVACUUM 是否足够 |
| session_search 历史保留 | 依赖 SessionDB 留存 | 无 session_search | 参考 Hermes FTS5 实现 |
| 跨进程协调（仅一次执行） | `state_meta` 共享表 | N/A | N/A |

**CE 当前清理方式**：Docker volume 重建或手动 SQL。**建议**：新增 `POST /api/admin/prune-sessions` 端点 + 配置项 `session.autoPrune` + 启动时检查。

---

## §2 Secrets Redaction in Context Compaction（`3368814a`）

### 2.1 问题背景

ContextCompactor 将消息内容 + tool call arguments 发送给 auxiliary summarizer model。
如果 message content 或 tool args 包含 API keys / tokens / passwords，这些 secrets 会：
1. 被写入 compression summary（持久化到 `_previous_summary`）
2. 随同每次 compaction 流转
3. 在 auxiliary model 的 context 中暴露

### 2.2 三层防御体系

#### Layer 1：Input Redaction（`_serialize_for_summary`）

```python
def _serialize_for_summary(self, turns: List[Dict]) -> str:
    for msg in turns:
        content = redact_sensitive_text(msg.get("content") or "")
        # ...
        if role == "tool":
            if isinstance(tc, dict):
                fn = tc.get("function", {})
                args = redact_sensitive_text(fn.get("arguments", ""))
```

#### Layer 2：Prompt Instructions

在 summarizer preamble、template Critical Context section、focus topic 中明确指令：
```
NEVER include API keys, tokens, passwords, secrets, credentials,
or connection strings in the summary — replace any that appear
with [REDACTED].
```

#### Layer 3：Output Redaction

```python
summary = redact_sensitive_text(content.strip())  # 摘要输出也 redact
self._previous_summary = summary  # 存入迭代更新链
```

### 2.3 `redact_sensitive_text()` 模式库

复用 `agent/redact.py` 的已有模式：
- `sk-*, ghp_*, key=VALUE` 格式
- URL 中的 access_token query params
- Form bodies 中的 password field
- Discord webhook/mention 中的 `@token` 格式

### 2.4 CE 差距 + 可执行借鉴

| 方面 | Hermes | BlueCortexCE | 行动 |
|------|--------|-------------|------|
| 摘要输入 redaction | ✅ `_serialize_for_summary` | ❌ 无 | 在 StructuredExtractionService 添加 input redaction |
| 摘要输出 redaction | ✅ `redact_sensitive_text()` | ❌ 无 | 输出前增加 `redact_sensitive_text()` |
| Prompt 指令禁止 secrets | ✅ 模板中明确 | ❌ 无 | 在 extraction prompt 中增加 |
| Tool args redaction | ✅ 单独处理 | ❌ 无 | structured output 中增加 |

**CE 当前现状**：`StructuredExtractionService` 生成的 JSON field values 可能包含 secrets（API keys, tokens）。**高优先级**：在 `extract()` 后对所有 string field values 应用 redaction。

---

## §3 Summary Model Fallback NameError Fix（`c0385873`）

### 3.1 问题根因

```python
# _generate_summary() 签名
def _generate_summary(turns_to_summarize, focus_topic): ...

# 错误递归调用（fallback 路径）
summary = _generate_summary(messages, summary_budget)  # ❌ 两参数类型不匹配
# → NameError: 'messages' is not in scope
```

### 3.2 修复

```python
# 正确递归调用
summary = _generate_summary(turns_to_summarize, focus_topic)  # ✅
```

### 3.3 影响分析

- **影响范围**：当 summary model 不可用（404/503/model_not_found）时，fallback 到主模型
- **之前**：NameError 导致 fallback 静默失败，context 无限增长
- **之后**：fallback 正常工作，context 压缩不因 auxiliary model 故障而中断

### 3.4 CE 差距

CE 的 `EmbeddingService` 无 fallback 机制，失败时返回空向量，无降级路径。对比 Hermes 7-Provider fallback chain，CE **单点故障风险更高**。

---

## §4 ContextEngine ABC Plugin Compatibility Fix（`a9a4416c`）

### 4.1 问题根因

手动 `/compress` gateway handler 直接访问 `tmp_agent.context_compressor._align_boundary_forward()` 和 `_find_tail_cut_by_tokens()`——这两个是 `ContextCompressor` 私有方法，不在 `ContextEngine` ABC 中。

当任何 **plugin ContextEngine**（如 LCM Engine）处于激活状态时，`context_compressor` 实际类型是 plugin 类，这些私有方法不存在 → `AttributeError` 崩溃。

### 4.2 修复方案

#### 4.2.1 ContextEngine ABC 新增方法

```python
class ContextEngine(ABC):
    @abstractmethod
    def compress(
        self,
        messages: List[Dict],
        summary_budget: int,
        focus_topic: str = None,
        ...
    ) -> List[Dict]: ...

    def has_content_to_compress(self, messages: List[Dict]) -> bool:
        """Safe default: always attempt compression."""
        return True  # ← 兼容所有 plugin
```

**关键**：默认返回 `True`（总是尝试压缩），保证 plugin engine 不因此方法缺失而跳过压缩。

#### 4.2.2 ContextCompressor Override

```python
class ContextCompressor(ContextEngine):
    def has_content_to_compress(self, messages: List[Dict]) -> bool:
        return self._align_boundary_forward(...)  # 保留原有精确逻辑
```

#### 4.2.3 Gateway `/compress` 重写

```python
# 之前：reach into private methods
# tmp_agent.context_compressor._align_boundary_forward(...)

# 之后：调用 ABC 方法
engine = tmp_agent.context_compressor
if hasattr(engine, 'has_content_to_compress'):
    if not engine.has_content_to_compress(messages_to_compress):
        return {"status": "nothing_to_compress"}
```

### 4.3 新增：`focus_topic` 参数进 ABC `compress()` 签名

Plugin engines 可以通过 `focus_topic` 做有针对性的压缩（ContextCompressor 用它控制 token 预算分配）。

### 4.4 CE 差距 + 可执行借鉴

| 方面 | Hermes | BlueCortexCE | 行动 |
|------|--------|-------------|------|
| Plugin 架构 | ContextEngine ABC + discover | ❌ 无 plugin | Phase 4 考虑 |
| Compress 前置检查 | `has_content_to_compress()` | ❌ 无 | 在 `/api/compress` 端点增加 |
| `focus_topic` 参数 | ABC + ContextCompressor | ❌ 无 | 暂无需求 |

---

## §5 总览：4 提交对 CE 的优先级

| 提交 | 类别 | CE 优先级 | 行动 |
|------|------|----------|------|
| `b8663813` Session Auto-Prune | 运维/DX | 中 | SessionService 添加自动清理 |
| `3368814a` Secrets Redaction | **安全** | **高** | StructuredExtractionService output redaction |
| `c0385873` Summary Fallback | Bug/鲁棒性 | 中 | LLM fallback 机制设计 |
| `a9a4416c` ContextEngine ABC | Plugin 架构 | 低（Phase 4） | 架构预留 |

**最高优先级**：`3368814a`（Secrets Redaction）——直接影响已存储的 extractedData JSONB 安全性。
