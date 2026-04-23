# Honcho / Holographic — 高级专题（续）

> **来源拆分**：本文件从 [`04-honcho-four-tools-routing.md`](04-honcho-four-tools-routing.md) 末尾拆分而来（原文 §24–§29 已迁入本文）。  
> **体量**：约 25KB ≪ 50KB 上限。  
> **总览索引**：[`hermes-memory/index.md`](../index.md)

---

## 24. on_delegation Hook — 子 Agent 记忆归属的架构性未完成（v3.9 新增）

> **文件**: `agent/memory_provider.py:175-183`（接口定义），`tools/delegate_tool.py:795-815`（调用点），`agent/memory_manager.py:319-332`（路由）
> **本节为 v3.9 新增**，分析 Hermes 的 `on_delegation` hook 机制及其当前实现状态。

### 24.1 接口定义

```python
# agent/memory_provider.py:175-183
def on_delegation(self, task: str, result: str, *,
                  child_session_id: str = "", **kwargs) -> None:
    """Called on the PARENT agent when a subagent completes.

    The parent's memory provider gets the task+result pair as an
    observation of what was delegated and what came back. The subagent
    itself has no provider session (skip_memory=True).

    task: the delegation prompt
    result: the subagent's final response
    child_session_id: the subagent's session_id
    """
```

**设计意图**：当 `delegate_task` 工具派生子 Agent 完成任务后，父 Agent 的记忆系统应自动记录：
1. **Task**：派发的目标是什么
2. **Result**：子 Agent 返回的结果摘要
3. **Child session ID**：子 Agent 的 session ID（用于溯源）

### 24.2 调用链

```
delegate_tool.py:800
  └── parent_agent._memory_manager.on_delegation(task, result, child_session_id)
        └── memory_manager.py:324
              └── for provider in self._providers:
                    └── provider.on_delegation(task, result, child_session_id=...)
```

```python
# tools/delegate_tool.py:795-815
if parent_agent and hasattr(parent_agent, '_memory_manager') and parent_agent._memory_manager:
    for entry in results:
        try:
            _task_goal = task_list[entry["task_index"]]["goal"] if entry["task_index"] < len(task_list) else ""
            parent_agent._memory_manager.on_delegation(
                task=_task_goal,
                result=entry.get("summary", "") or "",
                child_session_id=getattr(children[entry["task_index"]][2], "session_id", "") if entry["task_index"] < len(children) else "",
            )
        except Exception:
            pass
```

### 24.3 当前状态：所有 Provider 均未实现

**检查结果**：

| Provider | on_delegation 实现 | 代码位置 |
|----------|-------------------|----------|
| Honcho | ❌ 基类 no-op | 无 `def on_delegation` |
| Holographic | ❌ 基类 no-op | 无 `def on_delegation` |
| Mem0 | ❌ 基类 no-op | 无 `def on_delegation` |
| Honcho Hindsight | ❌ 基类 no-op | 无 `def on_delegation` |

所有 provider 直接继承 `MemoryProvider` 基类，使用默认 no-op 实现。

**影响**：
- **子 Agent 的工作成果不会自动记录到父 session 的记忆**
- 父 Agent 不知道子 Agent 完成了什么工作
- `delegate_task` 的结果只在工具返回值中可见，不进入长期记忆

### 24.4 与 BlueCortexCE 对比

| 维度 | Hermes on_delegation | BlueCortexCE |
|------|---------------------|--------------|
| 设计意图 | 父 session 记录子 Agent 工作成果 | 无对应机制 |
| 实现状态 | 接口存在，但所有 provider 未实现 | N/A |
| 触发时机 | 子 Agent 完成后立即调用 | N/A |
| 传递内容 | task goal + result summary + child_session_id | N/A |
| 实际效果 | **空操作** | N/A |

### 24.5 翻译：旁路型如何借鉴

**Hermes 做法**：在父 Agent 调用 `on_delegation` hook，期望 provider 将子任务结果写入父 session 的记忆。

**旁路型如何借鉴**：
- **高优先级**：BlueCortexCE 需要思考：当消费方（Claude Code/OpenClaw）使用子进程/子 Agent 时，记忆归属于谁？
- **当前 BlueCortexCE 现状**：完全由消费方决定如何处理，旁路型系统不知道子任务的存在
- **借鉴意义**：如果 BlueCortexCE 要支持"子 Agent 记忆归属父 session"，需要：
  1. 消费方显式传递"父 session_id"和"子任务摘要"
  2. BlueCortexCE 提供 `/api/delegation` 或在 `sync_turn` 中支持 `parent_session_id` 参数
- **注意**：这是旁路型架构的优势——消费方可以自己决定如何处理子任务记忆，不需要等系统自动处理

---

## 25. on_memory_write 桥接机制 — 内置记忆与外部 Provider 的双向同步（v3.9 新增）

> **文件**: `run_agent.py:6968-6975`（调用点），`agent/memory_manager.py:303-318`（路由），`plugins/memory/honcho/__init__.py:611-630`（Honcho 实现），`plugins/memory/holographic/__init__.py:243-253`（Holographic 实现）
> **本节为 v3.9 新增**，分析 Hermes 如何将内置 `memory` 工具的写入同步到外部记忆 Provider。

### 25.1 背景：两套记忆系统的并存

Hermes 有两套并行的记忆系统：

| 系统 | 工具 | 存储位置 | 用途 |
|------|------|----------|------|
| **内置记忆** | `memory` 工具（add/replace/remove） | `hermes_state.py` SQLite | 始终开启，Agent 直接使用 |
| **外部 Provider** | Honcho/Holographic/Mem0 等 | 各 Provider 自有存储 | 可插拔，Provider 特定能力 |

**问题**：当 Agent 使用 `memory` 工具添加用户事实时，外部 Provider 如何知道并同步？

### 25.2 桥接机制：`on_memory_write` Hook

```python
# run_agent.py:6968-6975
elif function_name == "memory":
    target = function_args.get("target", "memory")
    from tools.memory_tool import memory_tool as _memory_tool
    result = _memory_tool(
        action=function_args.get("action"),
        target=target,
        content=function_args.get("content"),
        old_text=function_args.get("old_text"),
        store=self._memory_store,
    )
    # Bridge: notify external memory provider of built-in memory writes
    if self._memory_manager and function_args.get("action") in ("add", "replace"):
        try:
            self._memory_manager.on_memory_write(
                function_args.get("action", ""),
                target,
                function_args.get("content", ""),
            )
        except Exception:
            pass
```

**关键点**：当 `memory` 工具执行 `add` 或 `replace` 时，自动触发 `on_memory_write` 广播给所有 Provider。

### 25.3 MemoryManager 路由

```python
# agent/memory_manager.py:303-318
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Notify all providers of a built-in memory write."""
    for provider in self._providers:
        try:
            provider.on_memory_write(action, target, content)
        except Exception as e:
            logger.debug(
                "Memory provider '%s' on_memory_write failed: %s",
                provider.name, e,
            )
```

### 25.4 Honcho 的实现：镜像为 Conclusion

```python
# plugins/memory/honcho/__init__.py:611-625
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in user profile writes as Honcho conclusions."""
    if action != "add" or target != "user" or not content:
        return  # Only mirror "add user" actions
    if self._cron_skipped:
        return
    if not self._manager or not self._session_key:
        return

    def _write():
        try:
            self._manager.create_conclusion(self._session_key, content)
        except Exception as e:
            logger.debug("Honcho memory mirror failed: %s", e)

    t = threading.Thread(target=_write, daemon=True, name="honcho-memwrite")
    t.start()
```

**特点**：
1. **仅镜像 `add user`** — 其他 target/action 忽略
2. **异步写入** — 后台线程调用 `create_conclusion`
3. **用户事实 → Honcho conclusion** — 内置记忆中的用户偏好写入 Honcho 云端

### 25.5 Holographic 的实现：镜像为 Fact

```python
# plugins/memory/holographic/__init__.py:243-253
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in memory writes as facts."""
    if action == "add" and self._store and content:
        try:
            category = "user_pref" if target == "user" else "general"
            self._store.add_fact(content, category=category)
        except Exception as e:
            logger.debug("Holographic memory_write mirror failed: %s", e)
```

**特点**：
1. **镜像所有 `add` 操作**
2. **target=user → category="user_pref"**，其他 → category="general"
3. **同步写入**（无后台线程）

### 25.6 与 BlueCortexCE 对比

| 维度 | Hermes 两套记忆桥接 | BlueCortexCE |
|------|-------------------|--------------|
| 触发条件 | `memory` 工具 `add`/`replace` | N/A（单一系统） |
| 广播范围 | 所有注册 Provider | N/A |
| Honcho 行为 | 异步写入云端 conclusion | N/A |
| Holographic 行为 | 同步写入本地 fact | N/A |
| 过滤策略 | Honcho 仅 `add user`，Holographic 所有 `add` | N/A |

### 25.7 翻译：旁路型如何借鉴

**Hermes 的两套记忆系统**对应 BlueCortexCE 的情况：
- BlueCortexCE 是纯旁路型，**没有**内置记忆系统
- 但如果有消费方同时使用 BlueCortexCE 和自己的本地记忆，**桥接机制**的思想仍然有价值

**借鉴建议**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **低** | BlueCortexCE 增加"写入同步"能力 | 当 BlueCortexCE 记录 Observation 时，可选同步到消费方的本地记忆系统（如果消费方暴露了 API） |
| **中** | BlueCortexCE 增加 `on_memory_write` 类似 hook | 让消费方可以注册回调，当 BlueCortexCE 写入时触发消费方的处理逻辑 |
| **高** | BlueCortexCE 的 SDK（JS/Go/Python）应该实现"双向同步" | 消费方本地的用户偏好变化时，同步到 BlueCortexCE；BlueCortexCE 的记录也可以写回消费方（如果有对应 API） |

---

## 26. Holographic 遗忘机制 — 指数衰减 + Trust Scoring（v3.9 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:28-35`（初始化），`plugins/memory/holographic/retrieval.py:569-595`（`_temporal_decay`），`plugins/memory/holographic/retrieval.py:95-110`（评分时应用衰减）
> **本节为 v3.9 新增**，分析 Holographic Provider 的时序遗忘机制。

### 26.1 配置项

```python
# plugins/memory/holographic/__init__.py:15
temporal_decay_half_life: 0  # days, 0 = disabled
```

```python
# plugins/memory/holographic/retrieval.py:28-35
@dataclass
class FactRetrieverConfig:
    temporal_decay_half_life: int = 0,  # days, 0 = disabled

    self.half_life = temporal_decay_half_life
```

**`half_life=0`（默认）= 遗忘机制禁用**。用户需要显式配置才启用。

### 26.2 指数衰减算法

```python
# plugins/memory/holographic/retrieval.py:569-595
def _temporal_decay(self, timestamp_str: str | None) -> float:
    """Exponential decay: 0.5^(age_days / half_life_days).

    Returns 1.0 if decay is disabled or timestamp is missing.
    """
    if not self.half_life or not timestamp_str:
        return 1.0  # No decay

    try:
        ts = datetime.fromisoformat(timestamp_str.replace("Z", "+00:00"))
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)

        age_days = (datetime.now(timezone.utc) - ts).total_seconds() / 86400
        if age_days < 0:
            return 1.0

        return math.pow(0.5, age_days / self.half_life)
    except (ValueError, TypeError):
        return 1.0
```

**衰减公式**：`score *= 0.5^(age_days / half_life)`

| 年龄 / 半衰期 | 衰减系数 |
|--------------|---------|
| 0（刚写入） | 1.0 |
| half_life / 2 | ~0.71 |
| half_life | 0.5 |
| 2 × half_life | 0.25 |
| 3 × half_life | 0.125 |

### 26.3 检索时应用衰减

```python
# plugins/memory/holographic/retrieval.py:95-110
# Stage 2: Rerank with Jaccard + trust + optional decay
for fact in candidates:
    score = fact.get("base_score", 0.0)
    # ...
    if self.half_life > 0:
        score *= self._temporal_decay(fact.get("updated_at") or fact.get("created_at"))
    fact["score"] = score
    scored.append(fact)
```

**关键点**：衰减在**检索重排序阶段**应用，不影响原始存储。fact 本身永远不删除（除非用户手动操作）。

### 26.4 Trust Scoring 的协同

Holographic 的评分机制结合了多个维度：

```python
score = (
    jaccard_similarity
    * trust_score          # 来源可信度（0.0-1.0）
    * temporal_decay       # 时间衰减（0.0-1.0）
    * (1 + 0.1 * helpful_count)  # 正向反馈加成
)
```

**三者协同**：
- **Trust score**：事实来源的可信度（手动设置或自动推断）
- **Temporal decay**：随时间降低相关性
- **Helpful count**：用户确认/赞成的次数

### 26.5 与 BlueCortexCE 对比

| 维度 | Holographic 遗忘机制 | BlueCortexCE |
|------|---------------------|--------------|
| 遗忘策略 | 指数衰减（评分时应用，不删除数据） | ❌ 无遗忘机制 |
| 半衰期配置 | 用户可配置（天数） | ❌ 无 |
| Trust scoring | ✅ 有（来源可信度） | ❌ 无 |
| Helpful count | ✅ 有（用户确认） | ❌ 无 |
| 检索时衰减 | ✅ 在 reranking 时应用 | ❌ 无 |

### 26.6 翻译：旁路型如何借鉴

**Hermes 做法**：检索时动态应用衰减，不物理删除数据。fact 保留历史，但随着时间推移，在检索结果中排名下降。

**BlueCortexCE 现状**：所有 Observation/Summary 永久存储，无时间衰减机制。

**翻译：旁路型如何借鉴**：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | BlueCortexCE 增加 `temporal_decay` 配置项 | 类似 `temporal_decay_half_life`，检索时降低旧记录的分数 |
| **中** | BlueCortexCE 增加 trust/quality 字段 | Observation/Summary 增加来源质量评分（可由消费方提供） |
| **中** | BlueCortexCE 的检索排序考虑时间因素 | 最近的相关记忆排名更高（可配置） |
| **低** | BlueCortexCE 增加"确认/反对"机制 | 消费方可以标记某条记忆是有用还是无用，影响后续检索权重 |
| **高** | **不需要物理删除** | Hermes 的"软遗忘"（评分衰减）比物理删除更安全，BlueCortexCE 应采用同样策略 |

**关键借鉴点**：BlueCortexCE 应该在**检索 API**（`/api/memory/search`）层面实现衰减，而不是在存储层面删除数据。这样既保留历史，又让旧记忆自动"沉淀"到搜索结果底部。

---

## 27. Holographic 矛盾检测 — 实体重叠 + 内容相异度算法（v4.0 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:338-442`
> **本节为 v4.0 新增**，深度分析 Holographic 的 `contradict()` 方法——**自动化记忆卫生检测**，这是目前所有记忆系统中独一无二的特性。

### 27.1 核心洞察：什么是"矛盾"？

Hermes 对"矛盾"的定义非常精确：

> **两个事实矛盾 = 共享实体（相同主体）+ 内容向量差异大（不同声明）**

这个定义背后的直觉是：
- 如果两个事实都说"关于 X 的一些事情"，但内容向量差异很大（一个是正面评价，一个是负面），则可能是矛盾的
- 如果两个事实没有共享实体，它们可能是完全无关的声明，不构成矛盾

### 27.2 算法完整流程

```python
# plugins/memory/holographic/retrieval.py:338-442
def contradict(self, category: str | None = None, threshold: float = 0.3, limit: int = 10):
    """
    1. 从 SQLite 获取所有有 HRR 向量的事实
    2. 对每对事实 (O(n²))：
       a. 提取共享实体（Jaccard overlap）
       b. 如果 overlap >= 0.3：
          - 计算 HRR 内容相似度
          - contradiction_score = entity_overlap * (1 - (content_sim + 1) / 2)
          - 如果 score >= threshold，标记为矛盾
    3. 按 contradiction_score 降序返回
    """
```

**关键参数**：
- `entity_overlap >= 0.3`（Jaccard）才进入矛盾判断——避免不相关实体的事实被误判
- `contradiction_score >= 0.3`（默认）才报告——可调整敏感度

### 27.3 矛盾分数计算公式

```
contradiction_score = entity_overlap * (1 - (content_similarity + 1) / 2)

其中：
- entity_overlap = |ents1 ∩ ents2| / |ents1 ∪ ents2|  (Jaccard, 0-1)
- content_similarity = HRR similarity between two fact vectors (-1 to 1)
- (content_similarity + 1) / 2 将 HRR 范围映射到 (0, 1)

所以：
- entity_overlap = 1.0（完全相同实体）+ content_similarity = -1.0（完全相反内容）
  → contradiction_score = 1.0 * (1 - 0/2) = 1.0（最高矛盾）
- entity_overlap = 1.0 + content_similarity = 1.0（完全相同内容）
  → contradiction_score = 1.0 * (1 - 1) = 0.0（无矛盾）
```

### 27.4 O(n²) 比较的防护机制

```python
# retrieval.py:370-378
_MAX_CONTRADICT_FACTS = 500
if len(rows) > _MAX_CONTRADICT_FACTS:
    rows = sorted(rows, key=lambda r: r["updated_at"] or r["created_at"], reverse=True)
    rows = rows[:_MAX_CONTRADICT_FACTS]
```

**保护**：当 facts 超过 500 条时，只比较最近更新的 500 条。500 facts → 最多 125,000 对比较，这是可接受的上限。

### 27.5 返回结构

```python
{
    "fact_a": {...},           # 第一个事实（去掉 hrr_vector）
    "fact_b": {...},           # 第二个事实（去掉 hrr_vector）
    "entity_overlap": 0.857,    # Jaccard 重叠度
    "content_similarity": -0.23,  # HRR 内容相似度
    "contradiction_score": 0.541,  # 最终矛盾分数
    "shared_entities": ["peppi", "backend"]  # 共享实体列表
}
```

### 27.6 工具 Schema 中的体现

```python
# plugins/memory/holographic/__init__.py:49-51
"• contradict — Memory hygiene: find facts making conflicting claims.\n"
```

用户可以通过 `memory` 工具调用 `contradict` action，主动检查记忆库中是否存在矛盾：

```python
# holographic.py:308-309
elif action == "contradict":
    results = retriever.contradict(category=category, threshold=threshold, limit=limit)
```

### 27.7 与 BlueCortexCE 对比

| 维度 | Holographic contradict | BlueCortexCE |
|------|----------------------|--------------|
| 矛盾定义 | 实体重叠 + 内容相异 | ❌ 无对应机制 |
| 算法 | O(n²) 两两比较 | ❌ 无 |
| 防护 | 500 条上限 | ❌ 无 |
| 调用方式 | `memory` 工具 + `contradict` action | ❌ 无 |
| 输出 | 矛盾对列表 + 分数 | ❌ 无 |

### 27.8 翻译：旁路型如何借鉴

**核心洞察**：这是目前所有记忆系统中**唯一**实现了自动化矛盾检测的。BlueCortexCE 完全缺少这个能力。

**实现方案（高优先级）**：

1. **Entity linking**：为 BlueCortexCE 的 Observation 增加实体提取能力
   - 可以用简单的正则（类似 Holographic）
   - 或用 LLM 在提取 Observation 时顺便提取实体列表

2. **矛盾检测 API**：`GET /api/memory/contradictions`
   - 实现类似公式：entity_overlap * (1 - content_similarity)
   - 限制最大比较对数（如 500 条 Observation）
   - 返回矛盾对列表

3. **触发时机**：
   - 用户主动查询（类似 Hermes 的 `contradict` tool）
   - 或在 Observation 写入时检查（如果新 Observation 与已有 Observation 有高实体重叠但内容相异）

**注意**：Holographic 的矛盾检测是纯本地的（SQLite + numpy），BlueCortexCE 使用 PostgreSQL + pgvector，需要用 SQL/pgvector 实现类似逻辑。

---

## 28. Holographic reason() — 多实体代数检索（v4.0 新增）

> **文件**: `plugins/memory/holographic/retrieval.py:260-337`
> **本节为 v4.0 新增**，分析 HRR 代数检索的核心能力——**多实体组合查询**。

### 28.1 核心洞察：什么是"代数检索"？

传统向量数据库只能做：**"找与 query 向量最相似的 K 个结果"**。

HRR 代数检索能做到：**"找同时与 [A, B, C] 都有结构关联的事实"**——这是传统 embedding 无法做到的。

### 28.2 算法核心

```python
# retrieval.py:260-337
def reason(self, entities: list[str], category: str | None = None, limit: int = 10):
    """
    1. 对每个 entity，计算 probe_key = bind(encode_atom(entity), role_entity)
    2. 对每个 fact：
       - 对每个 entity，从 fact_vec 中 unbinding 出 residual
       - 比较 residual 与 role_content 的相似度
       - 取所有 entity 相似度的 min（AND 语义）
    3. 只返回所有 entity 都"结构相关"的事实
    """
```

**关键设计**：
- `probe_key = bind(entity_vec, role_entity)` — 将实体绑定到"实体角色"
- `residual = unbinding(fact_vec, probe_key)` — 从事实中提取关于该实体的信号
- `min(entity_scores)` — 所有实体都必须有关联（AND 语义）

### 28.3 AND 语义 vs OR 语义

```python
# 注释原文
# A fact scores high only if ALL entities have structural presence
# (AND semantics via min, vs OR which would use mean/max).
```

**为什么用 min 而不是 mean/max？**
- `min`：所有实体都必须结构相关 → AND 语义
- `mean/max`：任一实体相关即可 → OR 语义

对于"找同时与 peppi 和 backend 都相关的事实"，必须用 AND 语义。

### 28.4 Fallback 机制

```python
# retrieval.py:264-268
if not hrr._HAS_NUMPY or not entities:
    # Fallback: search with all entities as keywords
    query = " ".join(entities)
    return self.search(query, category=category, limit=limit)
```

当 numpy 不可用时，退化为关键词搜索（将所有 entity 作为空格分隔的 query）。

### 28.5 与 BlueCortexCE 对比

| 维度 | Holographic reason() | BlueCortexCE |
|------|---------------------|--------------|
| 查询类型 | 多实体 AND 语义 | ❌ 无（只支持单 query） |
| 算法基础 | HRR 代数（bind/unbind） | pgvector 余弦相似度 |
| 语义 | AND（所有实体都相关） | OR（任一实体相关） |
| 实现难度 | 高（需要 HRR 代数） | 低（pgvector 不支持） |

### 28.6 翻译：旁路型如何借鉴

**现实评估**：HRR 代数检索在 BlueCortexCE 中**无法直接实现**（pgvector 不支持 bind/unbind 代数操作）。

**替代方案（中优先级）**：
1. **多实体查询 API**：`POST /api/memory/search` 接受 `entities: ["peppi", "backend"]`
2. **实现方式**：
   - 先分别搜索每个 entity 的相关 Observation
   - 取交集（AND 语义）或并集（OR 语义）
   - 这是工程上的近似，不是代数上的等价
3. **注意**：这种实现的信息召回率可能低于真正的 HRR 代数检索

---

## 29. Holographic 实体提取算法（v4.0 新增）

> **文件**: `plugins/memory/holographic/store.py:391-428`
> **本节为 v4.0 新增**，分析 Holographic 的轻量级实体提取机制。

### 29.1 正则规则 vs LLM 提取

**关键发现**：Holographic 的实体提取**不使用 LLM**，而是纯正则规则。

这与 Hindsight 的"实体消歧"（基于 LLM + knowledge graph）形成对比。

### 29.2 四条正则规则

```python
# store.py:394-428
_RE_CAPITALIZED  = re.compile(r'\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b')
# 匹配：大写字母开头的多词短语
# 例："John Doe" → "John Doe"

_RE_DOUBLE_QUOTE = re.compile(r'"([^"]+)"')
# 匹配：双引号内的内容
# 例：'"Python"' → "Python"

_RE_SINGLE_QUOTE = re.compile(r"'([^']+)'")
# 匹配：单引号内的内容
# 例："'pytest'" → "pytest"

_RE_AKA          = re.compile(
    r'(\w+(?:\s+\w+)*)\s+(?:aka|also known as)\s+(\w+(?:\s+\w+)*)',
    re.IGNORECASE,
)
# 匹配：X aka Y 或 X also known as Y
# 例："Guido aka BDFL" → "Guido" 和 "BDFL"
```

### 29.3 去重策略

```python
# store.py:403-413
seen: set[str] = set()
candidates: list[str] = []

def _add(name: str) -> None:
    stripped = name.strip()
    if stripped and stripped.lower() not in seen:
        seen.add(stripped.lower())
        candidates.append(stripped)
```

**去重规则**：
- 大小写不敏感（`"John"` 和 `"john"` 被视为相同）
- 保留首次出现的原始大小写形式

### 29.4 Entity 解析（_resolve_entity）

```python
# store.py:429-458
def _resolve_entity(self, name: str) -> int:
    # 1. 精确匹配 name 字段
    row = self._conn.execute(
        "SELECT entity_id FROM entities WHERE name LIKE ?", (name,)
    ).fetchone()
    if row is not None:
        return int(row["entity_id"])

    # 2. 在 aliases 字段中搜索（逗号分隔的别名列表）
    alias_row = self._conn.execute(
        """
        SELECT entity_id FROM entities
        WHERE ',' || aliases || ',' LIKE '%,' || ? || ',%'
        """,
        (name,),
    ).fetchone()
    if alias_row is not None:
        return int(alias_row["entity_id"])

    # 3. 创建新 entity
    cur = self._conn.execute(
        "INSERT INTO entities (name) VALUES (?)", (name,)
    )
    self._conn.commit()
    return int(cur.lastrowid)
```

**别名支持**：存储为逗号分隔字符串，查询时用 `LIKE '%...%'` 匹配。

### 29.5 与 BlueCortexCE 对比

| 维度 | Holographic 实体提取 | BlueCortexCE |
|------|-------------------|--------------|
| 提取方式 | 纯正则（无 LLM） | 无实体提取 |
| 实体解析 | SQLite 本地解析 | N/A |
| 别名支持 | ✅ 有 | ❌ 无 |
| 大小写处理 | 去重时忽略大小写 | N/A |

### 29.6 翻译：旁路型如何借鉴

**低优先级（但有价值）**：
- BlueCortexCE 可以在 Observation 写入时增加实体提取
- 实现方式：
  1. LLM 提取（更准确，但有成本）：在 Observation prompt 中要求输出 `entities: ["entity1", "entity2"]`
  2. 正则提取（无成本，但有限）：类似 Holographic 的正则规则
- 实体字段可用于：
  - 矛盾检测（如上所述）
  - 多实体 AND 查询（如上所述）
  - 实体级别的记忆统计（"这个实体的记忆有多少条"）

---

