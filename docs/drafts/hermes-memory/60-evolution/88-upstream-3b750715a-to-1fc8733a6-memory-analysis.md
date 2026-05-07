# 上游增量分析 `3b750715a..origin/main`（48 commits）

**时间**：2026-05-06 04:57 CST  
**起点**：`3b750715a`（lazy session creation 回归修复，已由 doc 87 覆盖）  
**终点**：`origin/main` (`1fc8733a6`)  
**新增 commit**：48 个

---

## 概述

本次 48 个新 commit 扫描结果：**无核心记忆系统代码变更**。主要内容：

| 类别 | 数量 | 代表 commit |
|------|------|------------|
| Provider/平台支持扩展 | 7 | `9022804d7` 所有 33 provider 可插拔化 |
| 文档更新（无代码） | 28 | 各类文档修复/完善 |
| TUI/CLI 修复 | 5 | `794f48766` `/model` 别名移除 |
| Kanban 功能 | 3 | `f67063ba8` 通用诊断引擎 |
| 其他 | 5 | Voice (Doubao)、Telegram、Teams 等 |

---

## 记忆/上下文系统相关 commit

| Hash | 标题 | 记忆相关度 | 详情 |
|------|------|-----------|------|
| `72c33dfe9` | docs(agent): remove stale BuiltinMemoryProvider references from memory module docstrings | ⚪ Doc-only | 见下方分析 |
| `e4723f671` | docs(cron): add context_from chaining section | ⚪ Doc-only | cron context_from 文档补充，无代码变更 |

---

## `72c33dfe9` — 清理过期 BuiltinMemoryProvider 文档引用

### 背景

`BuiltinMemoryProvider` 类早已从代码库中移除，但 `memory_manager.py` 和 `memory_provider.py` 的 module-level docstring 中仍保留了其示例代码和描述，造成误导。

### 变更内容

**文件**：`agent/memory_manager.py` + `agent/memory_provider.py`

变更前（误导）：
```python
# memory_manager.py
self._memory_manager.add_provider(BuiltinMemoryProvider(...))  # ImportError!

# memory_provider.py
Built-in memory is always active as the first provider and cannot be removed.
```

变更后（准确）：
```python
# memory_manager.py
self._memory_manager.add_provider(plugin_provider)
# Only ONE external plugin provider allowed

# memory_provider.py
External providers (Honcho, Hindsight, Mem0, etc.) are registered
and managed via MemoryManager. Only one external provider runs at a time.
```

### CE 借鉴

此变更确认了 Hermes 当前的 memory provider 架构：**MemoryManager 只管理外部插件 provider，内置 memory（如 MEMORY.md/USER.md）通过独立的 BuiltinMemoryProvider 实现**。

CE 当前设计（`cortex-mem-spring-integration`）采用了类似的分离模式：
- **AgentSkill** 负责 skill 生命周期（类似 Hermes 的 `skills_hub.py`）
- **StructuredExtractionService** 负责结构化提取（类似 Hermes 的 `on_pre_compress`/`on_memory_write` hooks）
- **ContextService** 负责会话上下文管理

此变更对 CE 无直接 API 影响，但**强化了"provider 外部化"的设计原则**——CE 的记忆系统也应遵循可插拔 provider 架构。

---

## `e4723f671` — Cron context_from 链接文档

仅文档变更（`website/docs/user-guide/features/cron.md`），添加了 `context_from` 链接用法的说明。无代码变更，对记忆系统分析无实质影响。

---

## 结论

**本次 48 commits 中 0 个核心记忆系统代码变更**。主要工作是 provider 架构扩展（33 个 provider 全部可插拔化）和文档完善。

建议：
- ✅ 无需在 doc 88 创建新的详细分析章节（无实质代码变更）
- ✅ Backlog 同步即可
- ⚠️ Provider 全量可插拔化（`9022804d7`/`20a4f79ed`）值得关注：未来 Hermes 可能支持更多第三方 memory provider，CE 应提前设计 provider 注册接口的兼容性
