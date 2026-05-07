# 上游分析：b93643c8f → 87b113c2e（87 commits，2026-05-06）

> **扫描起点**：`b93643c8f`（上次 v11 分析终点）
> **扫描终点**：`origin/main` `87b113c2e`
> **新提交总数**：87（2026-05-05 全天累积）
> **记忆/上下文系统相关**：8 个
> **下次扫描起点**：`origin/main` `87b113c2e`

---

## 概览

| 分类 | 数量 | 备注 |
|------|------|------|
| 总新提交 | 87 | 含大量非记忆相关（i18n/Telegram/Teams/Discord/Kanban/Dashboard） |
| 记忆系统相关 | **8** | reasoning metadata / session_id / provider / Honcho / JSONL |
| 严重 P0 | 0 | |
| 重要 P1 | 2 | 有效 session_id 追踪 / reasoning 跨轮泄漏防护 |
| 次要 P2 | 5 | Honcho 语义搜索 / JSONL append 锁 / ACP 原子历史 / Aux provider / Session-Key |
| 低优先级 P3 | 1 | ACP reasoning metadata 测试 |

---

## P1: 压缩后有效 Session ID 追踪（#16938）

**Commit**: `7f735b4db`（2026-04-28） + `314361733`（2026-05-05，测试）

**问题**：上下文压缩将 agent session_id 旋转到新的 child session，但 API Server 响应头 `X-Hermes-Session-Id` 仍然返回旧的 parent session_id。外部客户端持续发送旧 session_id，加载未压缩的 parent 历史而非压缩后的 continuation。

**修复**：
- `_run_agent()` 在结果字典中包含有效 session_id（`result["session_id"] = getattr(agent, "session_id", session_id)`）
- API Server 响应头使用 `result.get("session_id", session_id)` 而非原始 session_id
- 压缩旋转后外部客户端可正确追踪到 child session

**关键代码**（`gateway/platforms/api_server.py`）：
```python
response_headers = {
    "X-Hermes-Session-Id": result.get("session_id", session_id),
}
# result["session_id"] = getattr(agent, "session_id", session_id)
```

**CE 借鉴**：BlueCortexCE 的 `/api/session/start` 应在压缩触发 session rotation 后返回新的 effective session ID。当前 `POST /api/session/start` 在压缩后不会更新 session_id 引用，导致后续 `/api/context/generate` 可能加载旧历史。

**Issue**: #16938

---

## P1: 防止过期 Reasoning 跨轮泄漏（#17055）

**Commit**: `efe1cb00c`（2026-05-05）

**问题**：`run_conversation()` 中的 reasoning-box 提取循环向后遍历整个消息历史，寻找任何具有非空 `reasoning` 字段的 assistant 消息。当当前轮次没有生成 reasoning（如 provider 返回 `reasoning_content=null` 的简单响应），循环越过当前轮次，显示来自之前轮次的 reasoning——过时文本被误认为属于当前回复。

**修复**：在启动当前轮次的 user 消息处停止遍历，而非遍历整个历史。
```python
# 修复前：向后遍历整个消息历史
last_reasoning = None
for msg in reversed(messages):
    if msg.get("role") == "assistant" and msg.get("reasoning"):
        last_reasoning = msg["reasoning"]
        break

# 修复后：在 turn boundary 停止
for msg in reversed(messages):
    if msg.get("role") == "user":
        break  # turn boundary — 不跨越到之前的轮次
    if msg.get("role") == "assistant" and msg.get("reasoning"):
        last_reasoning = msg["reasoning"]
        break
```

**设计洞察**：
- 同一轮次内，reasoning 可能出现在 tool-call 步骤（如 Claude thinking、DeepSeek v4、Codex Responses），final-answer 步骤为 `reasoning=None`
- 因此向后遍历时需找到同轮次最近的 non-empty reasoning，而非仅最后一条
- 空字符串 `reasoning_content=''` 被视为 missing（而非空字符串）

**测试覆盖**（`tests/cli/test_reasoning_command.py`）：4 个场景
- reasoning 存在的简单轮次
- 无 reasoning 的简单轮次
- reasoning 位于 tool-call 步骤的 tool-calling 轮次
- prior turn 有 reasoning 但当前 turn 没有（stale-display bug 场景）
- reasoning 同时出现在两个步骤（latest wins）
- 空字符串 reasoning 视为 missing

**CE 借鉴**：
1. CE 的 `SummaryService.renderTimeline()` 若输出 reasoning content，应在轮次边界处停止提取
2. StructuredExtractionService 的 `ConversationObservation` 若包含 reasoning 字段，应记录 turn_id 而非全局索引
3. Phase 3 迭代压缩时，reasoning content 跨轮泄漏会导致错误的上下文注入

**Issue**: #17055

---

## P2: Honcho 语义搜索开启（user_message as search_query）

**Commit**: `0a7cc85ea`（2026-04-28）

**问题**：`get_prefetch_context` 接受 `user_message` 参数但故意丢弃，理由是避免在服务器访问日志中暴露对话内容。但 Honcho 已通过 `saveMessages` 持久化每条消息，此理由不一致。

**影响**：
- 无 `search_query` 时，Honcho 返回完整的 peer representation（所有 observations、deductive/inductive layers、peer card）
- 当 `contextTokens` 设置时，最有用的部分（peer card、dialectic conclusions）被原始 observations 填满预算而截断

**修复**：将 `user_message` 作为 `search_query` 传递给 `_fetch_peer_context`，启用 Honcho 语义检索返回与当前 session 主题相关的结论，减少注入噪声，提高冷启动上下文质量。

**CE 借鉴**：BlueCortexCE 的 `TimelineService.renderTimeline()` 或 `ContextService.generateContext()` 在预取时，可将首条 user message 作为语义搜索查询，而非加载全部历史 observation（类似 Honcho 的 semantic retrieval vs. full dump 对比）。

---

## P2: ACP 原子性 Session 历史重写

**Commit**: `5795b3be4`（2026-05-05）

**问题**：ACP 的 `save_session()` 执行非原子性的 `clear_messages()` + `append_message()` 循环。如果任何消息在循环中途抛出异常（如格式错误的 tool_call），DELETE 已提交，持久化对话历史丢失。

**修复**：`SessionDB.replace_messages()` 将 DELETE + bulk INSERT 包装在单个 `BEGIN IMMEDIATE` 事务中，任何异常时回滚，坏消息不再破坏已持久化的历史。

```python
# 修复前
for msg in messages:
    self.delete_message(msg_id)
    self.insert_message(msg)

# 修复后
SessionDB.replace_messages()  # BEGIN IMMEDIATE + DELETE + bulk INSERT
```

**CE 借鉴**：BlueCortexCE 的 SessionService 在批量更新 messages 时（如 session import/replay），应使用事务包装，防止部分更新导致历史丢失。

---

## P2: JSONL Transcript Append 序列化修复

**Commit**: `ecc909de3`（2026-04-22）

**问题**：并发写入同一 transcript JSONL 文件时，`open(path, "a")` 缺乏同步，两个写操作可能交错导致 JSONL 行损坏。

**修复**：JSONL append 操作现在在现有 `_lock` 下序列化。
```python
# 修复前
with open(transcript_path, "a", encoding="utf-8") as f:
    f.write(json.dumps(message, ensure_ascii=False) + "\n")

# 修复后
with self._lock:
    with open(transcript_path, "a", encoding="utf-8") as f:
        f.write(json.dumps(message, ensure_ascii=False) + "\n")
```

**CE 借鉴**：若 BlueCortexCE 实现 transcript 持久化（SessionEntity messages JSONL），append 操作必须在锁下序列化。

---

## P2: Aux Provider 修复避免假压缩警告

**Commit**: `c46bc9294`（2026-05-05，Issue #12977）

**问题**：当主模型为 Bedrock 时，无条件传递 `self.provider` 给 `get_model_context_length()` 用于 aux 模型，导致 Bedrock 静态表硬拦截（step 1b）为非 Bedrock 模型返回 `BEDROCK_DEFAULT_CONTEXT_LENGTH=128K`，而非模型的真实 context window，触发每次 session 的假压缩警告。

**修复**：当 `_aux_cfg_provider` 明确设置时传递它，仅在 aux provider 为 unset 或 "auto" 时回退到 `self.provider`。
```python
# 修复前
provider=getattr(self, "provider", "")

# 修复后
provider=(
    _aux_cfg_provider
    if _aux_cfg_provider and _aux_cfg_provider != "auto"
    else getattr(self, "provider", "")
)
```

**CE 借鉴**：StructuredExtractionService 的 compression/auxiliary 模型若支持多 provider，应确保 context length lookup 使用对应 provider 的解析路径，避免错误的 context window 估算。

**Related Issues**: #12977, #13807, #17460

---

## P2: X-Hermes-Session-Key 长期记忆作用域（#20199）

**Commit**: `fe8560fc1`（2026-05-05）

**功能**：API Server 集成（Open WebUI、自定义 Web UI）现在可通过 `X-Hermes-Session-Key` header 传递稳定的 per-channel 标识符，独立于 transcript-scoped `X-Hermes-Session-Id` 来作用域化长期记忆（如 Honcho）。

**架构**：
- 对应 native gateway 的 `session_key / session_id` 分裂：一个 per-assistant channel 的稳定 key，多个在 `/new` 时轮换的独立 transcript
- `_create_agent` 和 `_run_agent` 接受 `gateway_session_key` 并传递给 `AIAgent(gateway_session_key=...)`
- Honcho memory provider 已支持（`plugins/memory/honcho/client.py` 的 `resolve_session_name`）
- 新 helper `_parse_session_key_header`：API-key 认证门槛、控制字符清理、256 字符长度上限

**API 端点**：所有三个 agent 端点（`/v1/chat/completions`、`/v1/responses`、`/v1/runs`）均支持
**Feature Detection**：`/v1/capabilities` 通告 `session_key_header`

**CE 借鉴**：BlueCortexCE 若实现多用户/多 channel 场景，需要类似的双层作用域：
- `session_id`（transcript-scoped，对应 X-Hermes-Session-Id）
- `user_id` 或 `channel_key`（long-term memory scoped，对应 X-Hermes-Session-Key）

---

## P3: ACP Reasoning Metadata 持久化测试

**Commit**: `e8e914737`（2026-04-21）+ `e4e0090b5`（2026-04-21）

**内容**：ACP adapter 的 `save_session()` 添加测试覆盖，确保 assistant reasoning metadata 在 session 持久化中保留。

```python
# tests/acp/test_session.py
def test_save_session_preserves_reasoning_metadata():
    # 验证 reasoning 字段在 encode/decode roundtrip 中保留
```

**CE 借鉴**：BlueCortexCE 的 `ObservationEntity` 若存储 reasoning 元数据（如 `reasoning_content`），应在 persistence layer 测试中验证 encode/decode roundtrip 完整性。

---

## 非记忆相关（重要但不属于记忆系统）

| Commit | 内容 | 备注 |
|--------|------|------|
| `de9238d37` | feat(kanban): hallucination gate + recovery UX | 卡片认领机制 |
| `7de3c86c5` | feat(i18n): display.language 静态消息翻译 (zh/ja/de/es) | 本地化 |
| `b7bd17710` | docs(AGENTS.md): curator/cron/delegation/toolsets | 架构文档 |
| `8ebb81fd7` | fix(cli): sanitize bracketed paste markers | CLI 健壮性 |

---

## 文档架构规范自检

- 入口 `hermes-memory-analysis.md`：1553 字节 ✅
- `hermes-memory/60-evolution/` 正文：86 篇（新增 doc 86），最大 46922 字节（`09`），全部低于 50KB ✅
- `index-reading-order.md`：45,026 字节（逼近 50KB 暂不追加条目）✅
- 新增 doc 86（~7,200 字节）✅

---

## 下游可落地事项（Priority Matrix）

| Priority | Item | Source | CE Action |
|----------|------|--------|-----------|
| P1 | 压缩后 effective session_id 追踪 | `7f735b4db` | `ContextService` / `SessionService` 在压缩触发 rotation 后返回新 effective session ID |
| P1 | Reasoning 跨轮泄漏防护 | `efe1cb00c` | `SummaryService` / `TimelineService` 在提取 reasoning 时设置 turn boundary guard |
| P2 | ACP 原子性 session 历史重写 | `5795b3be4` | SessionService 批量更新使用事务包装 |
| P2 | JSONL append 序列化 | `ecc909de3` | Transcript persistence 的 append 操作必须在锁下执行 |
| P2 | Honcho 语义搜索开启 | `0a7cc85ea` | `ContextService.generateContext()` 预取时使用 user message 作为语义搜索查询 |
| P2 | Aux provider context length | `c46bc9294` | StructuredExtractionService 多 provider 时使用对应 provider 的 context length lookup |
| P2 | X-Hermes-Session-Key 双层作用域 | `fe8560fc1` | CE 多用户/多 channel 时实现 session_id + user_id 双层作用域 |
| P3 | ACP reasoning metadata roundtrip | `e8e914737` | ObservationEntity reasoning 字段的 persistence 测试 |

---

## 更新记录

- 2026-05-06 02:04：v12 完成，分析 `b93643c8f..origin/main`（87 commits），8 个记忆/上下文系统相关发现，新增 doc 86
- 2026-05-05 19:52：v11 完成，`13a7cbcd6..b93643c8f`（23 commits），4 个记忆相关发现，新增 doc 84 + doc 85
