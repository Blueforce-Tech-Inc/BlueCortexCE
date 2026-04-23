# Hermes `run_agent.py` — 记忆管线接线快照

> **角色**：说明 **主循环里** 内置文件记忆与 **外部 MemoryProvider 插件** 如何并联，供 CE 做「进程内 vs 多进程旁路」对照。  
> **上游**：`/Users/yangjiefeng/Documents/NousResearch/hermes-agent/run_agent.py`（行号以 **2026-04-19** 克隆为准，后续提交可能漂移）。  
> **配对**：模块级分工见 [`12-upstream-hermes-agent-memory-snapshot.md`](12-upstream-hermes-agent-memory-snapshot.md)。  
> **CE 交叉**：[`04`](../20-recommendations/04-ce-injection-and-context-api-surface.md) · [`../../evolver-memory/15-runtime-integration-surfaces.md`](../../evolver-memory/15-runtime-integration-surfaces.md)

---

## 1. 双线结构（与 `MemoryManager` 文档头注释的差异）

| 对象 | 类型 / 来源 | 在 `run_agent` 中的角色 |
|------|-------------|-------------------------|
| **`self._memory_store`** | `tools.memory_tool.MemoryStore` | **内置** `MEMORY.md` / `USER.md`：拼 **静态** system 块（**冻结快照**）、承载 **`memory` 工具** 的读写。 |
| **`self._memory_manager`** | `agent.memory_manager.MemoryManager` | **仅当** `config.yaml` 里 `memory.provider` 非空且插件 `load_memory_provider` 成功时创建；内部 **`add_provider` 的只有该插件**，**不是**「builtin + 插件」双注册。 |

**调研备注**：`memory_manager.py` / `memory_provider.py` 顶部文档仍写 **`BuiltinMemoryProvider` 始终注册**；当前仓库内 **未找到** `class BuiltinMemoryProvider` 实现，`run_agent` 将 **内置记忆** 完全放在 **`_memory_store` + `memory` 工具** 路径上。写稿或对照 CE 时以 **本文件与源码为准**，文档字符串可能滞后。

---

## 2. 初始化（`skip_memory` 为假时）

1. **`memory`** 配置段：`memory_enabled` / `user_profile_enabled` / 字符上限等。  
2. 若启用任一项：构造 **`MemoryStore`**，`load_from_disk()`。  
3. 读取 **`memory.provider`**：  
   - 可为空；若历史 **Honcho** 配置仍有效，可能 **自动迁移** 并写回 `provider: honcho`。  
4. **`MemoryManager()`** → **`load_memory_provider(name)`** → **`add_provider`**（仅插件）→ **`initialize_all`**（`session_id`、`platform`、`hermes_home`、`agent_context: primary`，以及可选 `user_id`、`agent_identity` 等）。  
5. 将 **`get_all_tool_schemas()`** 注入 **`self.tools`** 与 **`valid_tool_names`**。

---

## 3. System prompt 拼装顺序（节选）

在 **`_build_system_prompt`** 中（概念序）：

1. 基础 `system_message`（若有）。  
2. **`_memory_store.format_for_system_prompt("memory")`**（若 `memory_enabled`）。  
3. **`format_for_system_prompt("user")`**（若 `user_profile_enabled`）。  
4. **`_memory_manager.build_system_prompt()`**（外部 Provider 的 **静态** 说明块，与 prefetch 动态块分离）。

→ **内置文件块** 与 **插件静态块** 可 **同时存在**。

---

## 4. 每轮 API：prefetch 注入（仅 user 消息副本）

在 **`run_agent`** 主 tool 循环 **开始前**：

- **`prefetch_all(original_user_message)`** 一次，结果缓存为 **`_ext_prefetch_cache`**。  
- 注释明确：**避免每个 tool 迭代重复 prefetch**（成本与延迟）。  
- 构造 **`api_messages`** 时，对 **当前轮 user 消息** 做 **副本** `api_msg`：在 **`content`** 末尾拼接 **`build_memory_context_block(_ext_prefetch_cache)`**（`<memory-context>` 围栏），以及插件 **`pre_llm_call`** 注入；**不修改** 持久化的 `messages` 列表。

→ 与 CE **Hook 改 `additionalContext`**、**多进程** 路径 **形态不同**，但「**API 调用前临时拼接、不落库为用户原文**」可对齐产品讨论（见 [`40-context-compression/03-...`](../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)）。

---

## 5. 回合结束：`sync` 与下一拍 prefetch

在拿到 **`final_response`** 后（且未中断等条件满足时）：

- **`sync_all(original_user_message, final_response)`**  
- **`queue_prefetch_all(original_user_message)`**  

同样使用 **`original_user_message`**，避免 skill 等注入污染检索/query。

---

## 6. 工具分发：`memory` vs 插件工具

在统一 **`function_name`** 分发处：

- **`memory`**：调用 **`memory_tool(..., store=self._memory_store)`**；若动作为 **`add` / `replace`** 且存在 **`_memory_manager`**，则 **`on_memory_write`**，把内置写入 **镜像通知** 给外部 Provider（`MemoryManager` 内对 `name == "builtin"` 的 Provider 会 skip，当前 run_agent **通常无 builtin Provider**）。  
- 其它名字：若 **`_memory_manager.has_tool`**，则 **`handle_tool_call`**。

---

## 7. 上下文压缩前

- **`flush_memories`**（尽力在压缩前落盘/提示）。  
- **`_memory_manager.on_pre_compress(messages)`**：`MemoryManager` 会聚合各 Provider 的 **`on_pre_compress`** 返回值，但 **`run_agent` 当前调用未使用返回字符串**（仅副作用/日志类用途需查具体 Provider）。

---

## 8. 可借鉴点（CE / 旁路）

| Hermes 模式 | CE 侧可对齐的思考 |
|-------------|-------------------|
| **prefetch 每轮只算一次 + 缓存** | Worker/Java 多跳检索时的 **去重与预算**（避免 tool 环内重复 `semantic`）。 |
| **user 消息副本注入、不污染 session 持久化** | 与 **围栏 + 不把注入写回用户消息** 的安全叙事一致（对照 `04` / `05`）。 |
| **内置存储与插件 Provider 解耦、桥接 `on_memory_write`** | CE **SQLite 观察** 与 **Java Postgres** 若要做「镜像/索引」，类似 **单向通知** 钩子，但须重新定义真源（见 Evolver [`11`](../../evolver-memory/11-research-backlog.md)）。 |
| **文档与实现不一致** | 维护「**源码快照**」类短文（如 `12`/`13`）减少 Agent 误读 docstring。 |

---

## 9. 相关阅读

| 文档 | 用途 |
|------|------|
| [`12-upstream-hermes-agent-memory-snapshot.md`](12-upstream-hermes-agent-memory-snapshot.md) | `MemoryManager` / `MemoryStore` 类级分工 |
| [`03-memory-context-injection-and-prefetch-lifecycle.md`](../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md) | user 侧注入与 Prefetch 机制长文 |
| [`08-builtin-memory-tool-bounded-snapshot.md`](08-builtin-memory-tool-bounded-snapshot.md) | 内置工具边界 |
| [`19-gateway-session-expiry-watcher.md`](19-gateway-session-expiry-watcher.md) | Gateway 后台 session 过期 → 记忆主动 flush（`memory_flushed` 持久化、防覆盖注入） |
