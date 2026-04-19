# Hermes Agent 上游记忆架构 — 现场快照（源码锚点）

> **角色**：一次**可复现**的「读代码」结论，服务 **BlueCortexCE 借鉴**；不替代 `00-overview` 长文目录，也不重复 [`02`](../20-recommendations/02-bluecortexce-recommendations.md) 全表。  
> **上游根路径**（本机）：`/Users/yangjiefeng/Documents/NousResearch/hermes-agent`  
> **快照日期**：2026-04-19  
> **CE 落地交叉**：注入/会话首跳 [`04`](../20-recommendations/04-ce-injection-and-context-api-surface.md) · 安全缺口 [`05`](../20-recommendations/05-ce-context-security-gap-inventory.md) · 总导航 [`../../memory-research-hub.md`](../../memory-research-hub.md)

---

## 1. 三层分工（概念）

| 层 | 模块 | 职责（一句话） |
|----|------|----------------|
| **编排** | `agent/memory_manager.py` **`MemoryManager`** | 注册 **多个** `MemoryProvider`；聚合 **system prompt 块**、**prefetch**、**sync_turn**、**工具路由**；内置 **围栏** `sanitize_context` / `build_memory_context_block`。 |
| **插件契约** | `agent/memory_provider.py` **`MemoryProvider`（ABC）** | 定义 **`initialize` / `prefetch` / `sync_turn` / `get_tool_schemas` / `handle_tool_call`** 等；可选 **`on_session_end` / `on_pre_compress` / `on_delegation`** 等钩子。 |
| **内置文件记忆** | `tools/memory_tool.py` **`MemoryStore`** | **`MEMORY.md` / `USER.md`** 持久化；**会话内冻结 system 快照** + **工具侧活状态**；**写入前** `_scan_memory_content` 轻量安全扫描。 |

**约束（源码注释）**：**至多一个外部（非 builtin）Provider**，避免工具 schema 膨胀与后端冲突；builtin 始终存在且不可移除。

---

## 2. 关键路径与文件

| 主题 | 路径（相对上游根） |
|------|---------------------|
| 编排 + 围栏工具函数 | `agent/memory_manager.py`（`sanitize_context`、`build_memory_context_block`、`MemoryManager`） |
| Provider 抽象与生命周期 | `agent/memory_provider.py` |
| 内置 Memory 工具与存储 | `tools/memory_tool.py`（`MemoryStore`、`_scan_memory_content`、`get_memory_tool` 等） |
| 委托时与记忆联动（示例） | `tools/delegate_tool.py`（`parent_agent._memory_manager.on_delegation`） |
| CLI / 安装辅助 | `hermes_cli/memory_setup.py` |

---

## 3. 内置 `MemoryStore`（可借鉴点）

- **存储位置**：`get_hermes_home() / "memories"` → `MEMORY.md`、`USER.md`；条目分隔 **`§`**（section sign）。  
- **双状态**：**`_system_prompt_snapshot`** 在 `load_from_disk()` 时固定，**保 prefix cache**；**`memory_entries` / `user_entries`** 为工具可见的活数据，**即时落盘**。  
- **并发**：`**_file_lock**` 使用侧车 `.lock` 文件 + `fcntl` / Windows `msvcrt`。  
- **写入扫描**：`_MEMORY_THREAT_PATTERNS`（注入/外泄/敏感路径等）+ **不可见 Unicode 子集**；命中则 **Blocked** 字符串返回（与 CE [`05`](../20-recommendations/05-ce-context-security-gap-inventory.md) 对照用）。

---

## 4. `MemoryManager` 行为摘要（与旁路 CE 对照）

- **`prefetch_all`**：逐 Provider 调 `prefetch`，**单 Provider 失败不阻塞** 其他；结果拼接。  
- **`sync_all`**：回合结束后 `sync_turn`；**记录 warning，不整轮失败**。  
- **`handle_tool_call`**：按 **`_tool_to_provider`** 路由；未知工具走 `tool_error`。  
- **与 CE 差异**：Hermes 记忆主要在 **同一 Python 进程**内；CE 为 **Hook / Worker / Java** 多进程与双存储——对照时勿假设「单一 `MemoryManager`」即可覆盖 CE trace（见 [`../../evolver-memory/15-runtime-integration-surfaces.md`](../../evolver-memory/15-runtime-integration-surfaces.md)）。

---

## 5. 下一步（接力）

1. **主循环接线**：继续读 [`13-run-agent-memory-wiring-snapshot.md`](13-run-agent-memory-wiring-snapshot.md)（`_memory_store` ∥ `_memory_manager`、`prefetch`/`sync`、工具分发）。  
2. 将本快照与 [`06-memory-provider-hooks-inventory.md`](06-memory-provider-hooks-inventory.md) **钩子清单**交叉核对：是否有新增 `MemoryProvider` 方法未写入清单。  
3. 上游版本升级后：重跑「文件存在性 + 类名」核对，更新 **快照日期** 或拆 **`12b-...`** 增量稿（仍 ≤50KB）。  
4. **可勾选队列**：[`../11-research-backlog.md`](../11-research-backlog.md)「上游 hermes-agent 同步」。

---

## 6. 相关阅读（本目录）

| 若关心… | 打开 |
|---------|------|
| Prefetch / `<memory-context>` 生命周期 | [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](../40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md) |
| 内置 Memory Tool 边界快照 | [`08-builtin-memory-tool-bounded-snapshot.md`](08-builtin-memory-tool-bounded-snapshot.md) |
| Provider / Hooks 盘点 | [`06-memory-provider-hooks-inventory.md`](06-memory-provider-hooks-inventory.md) |
