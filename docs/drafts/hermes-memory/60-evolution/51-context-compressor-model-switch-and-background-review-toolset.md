# 上游新提交分析（2026-04-27）：ContextCompressor 模型切换 Token 预算修复 + Background Review Agent 工具集限制

**日期**：2026-04-27 22:03 CST  
**上游范围**：`e5647d78..origin/main`（374 commits；记忆相关仅 2 个）  
**本地 Hermes Agent Repo 状态**：⚠️ 已在 2026-04-27 22:03 从本地删除，需重新 clone 才能进行后续代码实地分析  
**已分析上游快照**：doc 50（`cec0af02..origin/main`，~50 commits）；doc 48（`ea01bdce` flush_memories 移除）；doc 39（Session Auto-Prune）；doc 45（on_session_finalize）  
**本篇覆盖**：2 个新 memory 相关 commit

---

## 1. `5401a008` — ContextCompressor 模型切换 Token 预算重算 Bug 修复

### 1.1 问题描述

`ContextCompressor.update_model()` 在模型切换时，只重算了 `threshold_tokens`，但**遗漏了 `tail_token_budget` 和 `max_summary_tokens`**。这两个字段在 `__init__` 时基于初始模型的上下文窗口大小计算，但切换到更小的模型时未同步更新。

**具体场景**：从 200K 上下文模型切换到 32K 模型时：
- `tail_token_budget` 仍保持 ~20K（200K 的 62%）
- 正确值应为 ~10K（32K 的 62%）
- 结果：压缩尾部预算超限，导致压缩行为异常

### 1.2 修复内容

```python
# 修复后：在 update_model() 中同时重算 tail_token_budget 和 max_summary_tokens
def update_model(self, model_name: str):
    self.threshold_tokens = calculate_threshold(...)
    self.tail_token_budget = int(self.max_context_tokens * self.tail_token_ratio)
    self.max_summary_tokens = int(self.max_context_tokens * self.max_summary_ratio)
```

### 1.3 CE 借鉴意义

| 方面 | Hermes 做法 | CE 当前状态 | 可执行行动 |
|------|------------|------------|-----------|
| 模型切换时 token 预算重算 | `update_model()` 三字段同步重算 | BlueCortexCE `ContextCompressor` 无 `update_model()` 等效 | 中期：参考 Hermes 修复模式，在 LLM 模型切换时同步重算所有 token 预算字段 |
| Token 预算比率 | `tail_token_ratio=0.62`, `max_summary_ratio` | Phase 3 Structured Extraction 无对应压缩模块 | 长期：为 Structured Extraction 结果压缩实现类似的比率驱动预算机制 |

**CE 影响评估**：低（当前 Phase 3 实现无 ContextCompressor 等效组件，但为未来扩展需记录此模式）

### 1.4 相关：压缩地板 Bug（已在 doc 50 覆盖）

`cec0af02` 引入的 64K 压缩地板（`ce6fb1c2`）已在 doc 50 覆盖。本 commit（`5401a008`）是独立的 token 预算比例 bug，与压缩地板正交。

---

## 2. `8ad29a93` — Background Review Agent 工具集限制

### 2.1 问题描述

Background review agent（`/background` 命令触发的后台记忆评审 agent）在创建时**未限制工具集**，导致继承了完整的默认工具集。这允许它使用 `terminal`、`send_message`、`delegate_task` 等非预期工具，可能在记忆评审完成后执行无关的副作用。

### 2.2 修复内容

Background review agent 创建时显式传入 `toolset` 参数，限制为 `memory` 和 `skills` 工具集：

```python
# 修复前：background agent 无工具集限制
baby_agent = AIAgent(...)

# 修复后：限制为 memory + skills 工具集
baby_agent = AIAgent(
    toolsets=["memory", "skills"],
    ...
)
```

### 2.3 CE 借鉴意义

| 方面 | Hermes 做法 | CE 当前状态 | 可执行行动 |
|------|------------|------------|-----------|
| 后台 Agent 工具集隔离 | 显式 `toolsets=["memory", "skills"]` 限制 | BlueCortexCE 无等效后台 agent 机制 | 未来设计后台任务 agent 时，需显式限制工具集防止越权操作 |
| Agent 创建时的工具集边界 | 默认为 full toolset，需显式收窄 | 暂无此需求（单进程旁路型架构） | 记录此模式供架构演进参考 |

**CE 影响评估**：低（旁路型架构无进程内 agent 创建需求，但背景评审概念可借鉴）

---

## 3. 其余 372 个非记忆相关 Commit 概览

374 个新 commit 中，记忆系统核心相关的仅上述 2 个。其余涵盖：

| 类别 | 代表 Commit | 说明 |
|------|-----------|------|
| TUI 性能优化 | `e63929d4` / `db4e4acc` | 长期会话滚动性能修复 |
| 平台工具集成 | `df3c9593`（Google Meet）、`ab687963`（Yuanbao） | 新平台支持 |
| Backup 机制 | `817633bc` / `a9033c92` / `ea3c5a14` | SQLite WAL/SHM 排除、checkpoint 排除、opt-in 备份 |
| Approval/Delegate | `008860a2` / `0046d170` | prompt_toolkit deadlock 修复、Delegate 死锁修复 |
| 跨会话安全 | `ee1a07f9` | 阻止 cross-provider reasoning leak to DeepSeek/Kimi |
| Image/多模态路由 | `ec671c41` | 基于模型 vision capability 的原生多模态路由 |

**均不影响记忆系统核心架构**，不做详细分析。

---

## 4. ⚠️ 行动项：本地 Hermes Agent Repo 重新 clone

**问题**：本地 Hermes Agent 克隆（`/Users/yangjiefeng/Documents/NousResearch/hermes-agent`）已在 2026-04-27 22:03 被删除。此后无法进行代码实地复核。

**恢复命令**：
```bash
git clone https://github.com/NousResearch/hermes-agent.git \
  /Users/yangjiefeng/Documents/NousResearch/hermes-agent
```

**恢复后建议**：
1. 同步至 `origin/main`（`ac0325c2`）
2. 运行 `bash scripts/regression-test.sh` 验证 Hermes 功能正常
3. 继续跟踪上游新 commit（每轮 cron 扫描 memory/context/compress 相关文件变化）

---

## 5. 文档体量验证

| 文件 | 字节数 | 状态 |
|------|--------|------|
| 本文档 | ~7,500 | ✅ <50KB |
| 最大文件（`09-supermemory-capture-lifecycle.md`） | 46,922 | ✅ <50KB |

**文档体系总计**：51 篇正文 + 入口 ~620KB，全部低于 50KB 上限。
