# 52. Session Teardown Fix · Cross-Provider Reasoning Leak · Hindsight Setup · Transcript Filesystem（2026-04-27）

**上游扫描**：`cec0af02..origin/main`（267 commits）
**记忆相关**：4 个（其余 263 个非记忆）
**vh8.9 → v9.0**

---

## §1 Gateway/CLI Session Teardown — 向 `shutdown_memory_provider` 传递真实对话历史

**Commit**: `500774e3`（Gateway）+ `a59a98b1`（CLI sibling）

**问题根因**：

`_cleanup_agent_resources` 调用 `agent.shutdown_memory_provider()` 时**未传任何参数**，导致所有 Memory Provider 的 `on_session_end` hook 收到 `messages=[]`（空列表）。

Holographic 和 Hindsight 等 Provider 有 **early-return guard** 对空输入直接返回，导致：

> "抱歉，找不到相關的對話記錄"（找不到相关对话记录）
> 
> — 用户在 **Gateway 重启 / Session Reset / Idle Expiry** 之后的第一个 Turn 必然触发此错误。

**修复方案**：

传递 `agent._session_messages`（Agent 每个 Turn 通过 `_persist_session` 维护的真实对话历史副本）：

```python
# run_agent.py — _cleanup_agent_resources
agent.shutdown_memory_provider(agent._session_messages)
```

- 向后兼容：`shutdown_memory_provider(messages: list = None)` 的签名已有 `messages=None` 默认值，当属性不存在或非 list 时回退到空列表（MagicMock 测试桩兼容）
- `skip_memory=True` 的临时 Agent（memory flush / hygiene auto-compress / `/compress`）内部是 no-op，不受影响

CLI 侧（`a59a98b1`）：之前读取 `getattr(agent, 'conversation_history', None)` — 但 `AIAgent` 无此属性，所以 CLI teardown 路径同样总是收到 `[]`。改为使用同 Gateway 一致的 `agent._session_messages`。

**源码位置**：

| 文件 | 改动 |
|------|------|
| `run_agent.py` `_cleanup_agent_resources` | 传递 `agent._session_messages` |
| `cli.py` exit cleanup | 同上，`isinstance` guard |
| `tests/gateway/test_gateway_shutdown_memory_messages.py` | 4 cases（Gateway 套件）|
| `tests/cli/test_cli_shutdown_memory_messages.py` | 4 cases（CLI 套件）|

**CE 可执行借鉴**：

CE 的 `ContextService.onSessionEnd()` 或等价 hook 应确保：
1. **传递真实对话历史**（而非空列表）给所有 Memory Provider
2. 检查各 Provider 的 early-return guard — 空输入时是否应该跳过还是有 fallback
3. Session reset / idle expiry / 服务重启路径均需要覆盖

**与 doc 19（Gateway Session Expiry Watcher）关系**：

doc 19 记录了 `memory_flushed` 持久化和 proactive flush 机制，但未覆盖 `on_session_end` 调用时传空消息的 bug。本发现补充了 Session Expiry → Provider teardown 链路的完整性。

---

## §2 Cross-Provider Reasoning Leak — DeepSeek/Kimi HTTP 400 Fix

**Commit**: `ee1a07f9`（fix(agent): block cross-provider reasoning leak to DeepSeek/Kimi #15748）

**问题根因**：

当 Session 内 Provider 切换时（如 MiniMax → DeepSeek），源端 Assistant Turn 携带的 `reasoning` 字段由前一个 Provider 写入，但**没有** `reasoning_content` key。

`_copy_reasoning_content_for_api` 会将这个**跨 Provider 的** `reasoning` 提升为 outbound DeepSeek 请求的 `reasoning_content`，导致：
- **信息泄露**：跨 Provider 的 Chain-of-Thought 被泄露到新 Provider
- **HTTP 400**：DeepSeek 自己的 `_build_assistant_message` 在 tool-call turns 时强制将 `reasoning_content=''`，所以这种 shape（reasoning 存在、reasoning_content 缺失、tool_calls 存在）**从同 Provider DeepSeek 历史中永远无法到达** — 只能是 prior provider 污染的结果

**修复方案**：

在检测到上述 shape 时，填充 `''`（空字符串）而非 promote 外来 `reasoning`：

```python
# 修复后
if foreign_reasoning_without_reasoning_content_and_with_tool_calls:
    reasoning_content = ''  # 而非 promote foreign reasoning
```

- 同 Provider 内的正常 `reasoning` promote 行为不变
- DeepSeek 之外的 Provider 不受此问题影响（无强制空字符串 pin）

**CE 可执行借鉴**：

若 CE 在多 Model/Provider 切换时存在类似的 reasoning content 传递逻辑，需要防止跨 Provider 的 intermediate reasoning state 泄露。

---

## §3 Hindsight Setup Config Preservation

**Commit**: `64a497bf`（fix(hindsight): preserve setup config on blank input）

**问题**：用户重新运行 `hermes memory --setup hindsight` 时，交互式提示全部重置，用户需要重新输入所有已配置的值。

**修复**：加载磁盘现有配置，预填充交互式提示默认值：

| 字段 | 预填充来源 |
|------|-----------|
| Mode | `existing_config.get("mode")` |
| LLM Provider | `existing_config.get("llm_provider")` |
| LLM Base URL | `existing_config.get("llm_base_url")` |
| LLM Model | `existing_config.get("llm_model")` |

用户直接回车即保留现有值；输入新值则覆盖。

**源码**：`plugins/memory/hindsight/__init__.py`（setup flow，约 80 行 diff）

**CE 可执行借鉴**：

CE Structured Extraction 配置重新初始化时，应从 `application.properties` / 环境变量 / 上次成功配置中预填充，而非每次清空。

---

## §4 Session Transcript Filesystem Cleanup

**Commit**: `3b60abb6`（fix(sessions): delete on-disk transcript files during prune and delete #3015）

**问题**：`delete_session()` 和 `prune_sessions()` 仅删除 SQLite 记录，`.json` / `.jsonl` / `request_dump_*.json` 遗留磁盘，无限增长（观察到约 **27MB/天**）。

**修复**：

```python
@staticmethod
def _remove_session_files(session_id, sessions_dir):
    """Clean up .json, .jsonl, request_dump_{session_id}_*.json"""
    # best-effort, OSError silenced
```

- `delete_session(sessions_dir)` — 删除指定 session 及其 children 的磁盘文件
- `prune_sessions(sessions_dir)` — 删除所有被 prune session 的磁盘文件
- 文件清理为 best-effort，不阻塞 DB 操作
- 向后兼容：`sessions_dir=None` 保留原有行为

**CE 可执行借鉴**：

CE 若有 Session transcript 磁盘文件，也应纳入 prune 逻辑，防止磁盘泄漏。

---

## §5 文档体量验证

```bash
$ wc -c 52-*.md  # 新增
9516  docs/drafts/hermes-memory/60-evolution/52-session-teardown-fix-cross-provider-reasoning-and-filesystem-cleanup.md
```

远低于 50KB 上限。
