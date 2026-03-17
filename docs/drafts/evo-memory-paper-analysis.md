# Evo-Memory 论文深入解读与 Claude-Mem 改进方向

> **论文**: [Evo-Memory: Benchmarking LLM Agent Test-time Learning with Self-Evolving Memory](https://arxiv.org/abs/2511.20857)
> **作者**: Google DeepMind + UIUC
> **发布日期**: 2025年11月
> **文档创建**: 2026-03-12
> **HTML版本**: https://arxiv.org/html/2511.20857v1

---

## 目录

1. [论文核心问题与动机](#1-论文核心问题与动机)
2. [Evo-Memory 框架详解](#2-evo-memory-框架详解)
3. [ReMem: Think-Act-Refine 记忆架构](#3-remem-think-act-refine-记忆架构)
4. [关键实验发现](#4-关键实验发现)
5. [当前 Claude-Mem Java 版本的差距分析](#5-当前-claude-mem-java-版本的差距分析)
6. [改进方向与实施建议](#6-改进方向与实施建议)
7. [实施路线图](#7-实施路线图)
8. **[旁路架构的适配策略](#8-旁路架构的适配策略)** ⭐ 重要

---

## 1. 论文核心问题与动机

### 1.1 核心洞察：对话回忆 ≠ 经验复用

论文指出当前 LLM 记忆系统的一个关键缺陷：

| 对话回忆 (Conversational Recall) | 经验复用 (Experience Reuse) |
|----------------------------------|----------------------------|
| 检索过去的**事实** | 检索**推理策略** |
| 回忆"说了什么" | 学习"怎么做" |
| 静态查询 | 动态适应 |
| 例如：记住方程的解 | 例如：记住求解方法（公式） |

> **关键论点**: 当前大多数记忆系统只能"回忆"，无法真正"学习"。Agent 记住了对话内容，但无法将经验抽象为可复用的策略。

### 1.2 测试时演化 (Test-time Evolution)

论文提出核心概念：**测试时演化** — LLM 在部署阶段持续检索、整合、更新记忆。

```
传统视图: 训练 → 部署 (静态)
Evo-Memory 视图: 训练 → 部署 (持续演化)
```

**问题背景**:
- 真实世界的 Agent 面临连续任务流（客服、多轮工具调用、具身导航等）
- 当前系统"记不住"过去经历，每次都像重新开始
- 无法像人类一样"吃一堑，长一智"

---

## 2. Evo-Memory 框架详解

### 2.1 统一形式化：记忆增强智能体 (F, U, R, C)

论文将记忆增强 Agent 抽象为四元组：

| 组件 | 符号 | 职责 | 示例 |
|------|------|------|------|
| 基础模型 | **F** | 核心推理能力 | Claude, GPT, Gemini |
| 检索模块 | **R** | 从记忆库中找相关经验 | 向量相似度搜索 |
| 上下文构造 | **C** | 将检索内容拼入 prompt | 模板化 prompt 构建 |
| 更新模块 | **U** | 写入新经验并演化记忆 | 追加/压缩/重写 |

**统一循环**:

```
对于每个时间步 t:
  1. 接收输入 x_t
  2. 检索相关记忆: R_t = R(M_t, x_t)
  3. 构造上下文: C_t = C(x_t, R_t)
  4. 生成输出: ŷ_t = F(C_t)
  5. 构建新记忆条目: m_t = h(x_t, ŷ_t, feedback)
  6. 更新记忆状态: M_{t+1} = U(M_t, m_t)
```

### 2.2 流式任务流 (Streaming Task Streams)

**核心创新**: 将静态数据集重构为有序任务流。

```
原始数据集: {(x_1, y_1), (x_2, y_2), ..., (x_T, y_T)}  // 独立样本
任务流: τ = {(x_1, y_1) → (x_2, y_2) → ... → (x_T, y_T)}  // 有序依赖
```

**设计原则**: 前面的任务信息/策略对后续任务有帮助甚至必要。

**预测轨迹**:

```
(x_1, ŷ_1, M_1) → (x_2, ŷ_2, M_2) → ... → (x_T, ŷ_T, M_T)
```

### 2.3 ExpRAG: 经验检索与聚合基线

论文提供了一个简单但有效的基线方法：

```python
# 伪代码
def ExpRAG(x_t, M_t):
    # 1. 检索 top-k 相似经验
    R_t = TopK_Search(M_t, x_t, k=4)
    
    # 2. 基于 ICL 原则，用检索经验增强 prompt
    ŷ_t = LLM(x_t, R_t)
    
    # 3. 将当前经验追加到记忆库
    M_{t+1} = M_t ∪ {(x_t, ŷ_t, feedback)}
```

**与传统 RAG 的区别**:
- 传统 RAG：检索**静态文档**
- ExpRAG：检索**真实交互轨迹**（更接近 Agent 的自我成长场景）

---

## 3. ReMem: Think-Act-Refine 记忆架构

### 3.1 核心创新：记忆推理作为第一类操作

ReMem 扩展了 ReAct 范式，引入第三个核心操作：

```
ReAct: Think → Act → Think → Act → ...
ReMem: Think ↔ Act ↔ Refine (循环)
```

**三种操作**:

| 操作 | 职责 | 输出 |
|------|------|------|
| **Think** | 内部推理，分解任务 | reasoning_trace |
| **Act** | 执行环境操作或输出回答 | action_result |
| **Refine** | 对记忆进行元推理 | memory_edit_plan |

### 3.2 控制循环

```python
def ReMemAgent(x_t, env):
    state = {
        "query": x_t,
        "memory": M_t,
        "reasoning_trace": [],
        "step": 0
    }
    
    while not done:
        # 决策下一个操作
        action = controller(state)  # Think / Act / Refine
        
        if action == "THINK":
            state = Think(state)
        elif action == "REFINE":
            state = RefineMemory(state)
        elif action == "ACT":
            output, feedback = Act(state, env)
            WriteBackMemory(state, output, feedback)
            return output
```

### 3.3 Refine Memory: 记忆的主动管理

**关键创新**: 不是简单追加，而是"检索 + 剪枝 + 重组"。

```python
def RefineMemory(state):
    # 1. 检索与当前任务相关的候选记忆
    candidates = MemoryStore.retrieve(state.query, k=K_REFINE)
    
    # 2. 构造 meta-prompt，让 LLM 对记忆做"元推理"
    meta_prompt = build_refine_prompt(
        query=state.query,
        candidates=candidates
    )
    
    # 3. LLM 输出"记忆编辑计划"
    edit_plan = LLM(meta_prompt)
    # 可能包括:
    # - 要删除哪些条目（噪音/过时）
    # - 要合并/重写哪些条目（抽象为规则）
    # - 要新增哪些条目（总结）
    
    # 4. 应用编辑计划
    apply_edit_plan(edit_plan, MemoryStore)
```

### 3.4 WriteBackMemory: 经验结构化

```python
def WriteBackMemory(state, output, feedback):
    # 1. 组织经验文本（结构化）
    experience = {
        "input": state.query,
        "reasoning_trace": state.reasoning_trace,
        "action": output,
        "outcome": feedback,
        "quality_score": estimate_quality(feedback)
    }
    
    # 2. 计算嵌入向量
    experience["embedding"] = embed(experience)
    
    # 3. 写入记忆库
    MemoryStore.upsert(experience)
```

### 3.5 Refine 候选选择策略详解（关键设计决策）

> **核心问题**: 如何选择待精炼的记忆？涉及三个关键设计决策：
> 1. **候选筛选条件**: 仅按时间排序是否足够？
> 2. **已精炼记录处理**: 精炼过的记忆是否还能再次精炼？
> 3. **多主题处理**: 输入涉及多主题时，是否应该输出多个精炼后的记录？

#### 3.5.1 论文中的策略

**ReMem 的原始设计**（论文第 3.3 节）：

1. **候选选择**:
   ```python
   # 论文原文: "Refine performs meta-reasoning over memory, 
   # which exploiting useful experiences, pruning noise, 
   # and reorganizing M_t"
   candidates = MemoryStore.retrieve(state.query, k=K_REFINE)
   ```
   - **策略**: 基于与当前任务的**相似度检索**，而非仅按时间
   - **关键**: Refine 是在 Think-Act 循环中**同步**触发的，候选来自当前任务相关记忆

2. **Refine 操作的内容**（论文描述）:
   - **删除 (pruning noise)**: 删除低质量/过时记忆
   - **合并 (reorganizing)**: 将多条相关记忆合并为抽象规则
   - **重写 (exploiting useful)**: 增强有用经验的可复用性

3. **论文未明确说明的细节**:
   - 是否有 `refined_at` 标记？
   - 已精炼记忆是否可再次精炼？
   - 多主题输入的输出策略？

#### 3.5.2 论文实验的隐含答案

**RQ4: 失败经验的选择性利用**（第 4.2 节）:
> "基线方法在存储失败经验时性能下降，ReMem 通过**主动精炼**保持鲁棒性。**关键**: 学习成功经验 + 适当利用失败信息。"

**隐含设计**:
- ✅ **质量评估是关键**: 不是所有经验都值得保留
- ✅ **选择性精炼**: 根据反馈类型（成功/失败）决定精炼策略
- ⚠️ **论文未明确讨论重复精炼问题**

#### 3.5.3 Claude-Mem 旁路架构的适配策略

由于 Claude-Mem 是**旁路观察者**，无法实现 ReMem 的同步 Refine，我们需要：

**候选选择策略（findRefineCandidates）**:

```java
public List<ObservationEntity> findRefineCandidates(String projectPath) {
    // 策略: 多维度筛选，优先处理低质量/过时记忆
    
    List<ObservationEntity> candidates = new ArrayList<>();
    
    // 1. 删除候选: 质量分 < 0.3
    candidates.addAll(observationRepository
        .findByProjectPathAndQualityScoreLessThan(projectPath, 0.3f));
    
    // 2. 过时候选: 30天未访问且质量分 < 0.6
    candidates.addAll(observationRepository
        .findByProjectPathAndLastAccessedBefore(projectPath, 
            now().minusDays(30), 0.6f));
    
    // 3. 合并候选: 同一会话内相似度高的记忆
    candidates.addAll(findMergeCandidates(projectPath));
    
    // 4. 过滤已精炼且在冷却期内的记忆（7天内不重复精炼）
    return candidates.stream()
        .filter(o -> canRefine(o))
        .limit(REFINE_BATCH_SIZE)
        .collect(Collectors.toList());
}

private boolean canRefine(ObservationEntity obs) {
    if (obs.getRefinedAt() == null) return true; // 从未精炼
    // 允许 7 天后再次精炼
    return obs.getRefinedAt().isBefore(now().minusDays(7));
}
```

**关键设计决策**:

| 问题 | 论文策略 | Claude-Mem 适配 | 理由 |
|------|---------|----------------|------|
| **候选筛选** | 任务相似度检索 | 质量分 + 访问时间 + 精炼状态 | 旁路无法同步检索当前任务相关记忆 |
| **已精炼记录** | 未明确 | 允许再次精炼（7天后） | 记忆可能随时间过时，需要重新评估 |
| **多主题输出** | 未明确 | 输出多个精炼记录 | 保持记忆粒度适中 |

#### 3.5.4 推荐的 Refine 候选选择算法

```java
@Service
public class MemoryRefineService {
    
    private static final float DELETE_THRESHOLD = 0.3f;
    private static final int REFINE_BATCH_SIZE = 20;
    private static final int REFINED_COOLDOWN_DAYS = 7;
    
    /**
     * 查找需要精炼的候选记忆
     * 
     * 策略: 多维度筛选，优先处理低质量/过时记忆
     */
    public List<ObservationEntity> findRefineCandidates(String projectPath) {
        List<ObservationEntity> candidates = new ArrayList<>();
        
        // 1. 删除候选: 质量分 < 0.3
        candidates.addAll(observationRepository
            .findByProjectPathAndQualityScoreLessThan(projectPath, DELETE_THRESHOLD));
        
        // 2. 合并候选: 同一会话内相似度高的记忆
        //    (防止碎片化)
        candidates.addAll(findMergeCandidates(projectPath));
        
        // 3. 过时候选: 30天未访问且质量分 < 0.6
        candidates.addAll(observationRepository
            .findByProjectPathAndLastAccessedBefore(projectPath, 
                now().minusDays(30), 0.6f));
        
        // 4. 过滤已精炼且在冷却期内的记忆
        candidates = candidates.stream()
            .filter(o -> canRefine(o))
            .limit(REFINE_BATCH_SIZE)
            .collect(Collectors.toList());
        
        return candidates;
    }
    
    private boolean canRefine(ObservationEntity obs) {
        if (obs.getRefinedAt() == null) return true;
        // 允许 7 天后再次精炼
        return obs.getRefinedAt().isBefore(now().minusDays(REFINED_COOLDOWN_DAYS));
    }
    
    private List<ObservationEntity> findMergeCandidates(String projectPath) {
        // 查找同一会话内嵌入向量相似度 > 0.8 的记忆对
        // 交给 LLM 决定是否合并
        return observationRepository.findHighSimilarityPairs(projectPath, 0.8f);
    }
}
```

#### 3.5.5 多主题输入的输出策略

**问题**: 如果输入的候选记忆涉及多个主题（如 "调试" + "重构"），是否应该输出多个精炼后的记录？

**推荐策略**: **主题聚类 + 分别精炼**

```java
public RefineResult refineMemory(String projectPath) {
    List<ObservationEntity> candidates = findRefineCandidates(projectPath);
    
    // 1. 按主题聚类
    Map<String, List<ObservationEntity>> clusters = clusterByTopic(candidates);
    
    // 2. 对每个聚类分别调用 LLM
    List<RefinePlan> plans = new ArrayList<>();
    for (Map.Entry<String, List<ObservationEntity>> entry : clusters.entrySet()) {
        String topic = entry.getKey();
        List<ObservationEntity> clusterCandidates = entry.getValue();
        
        RefinePlan plan = llmRefine(topic, clusterCandidates);
        plans.add(plan);
    }
    
    // 3. 执行所有精炼计划
    for (RefinePlan plan : plans) {
        executeRefinePlan(plan);
    }
    
    return new RefineResult(plans);
}

private Map<String, List<ObservationEntity>> clusterByTopic(
        List<ObservationEntity> candidates) {
    // 简单策略: 按 concepts 字段聚类
    // 复杂策略: 使用嵌入向量 + KMeans
    return candidates.stream()
        .collect(Collectors.groupingBy(o -> 
            o.getConcepts().isEmpty() ? "general" : o.getConcepts().get(0)));
}
```

#### 3.5.6 总结: Claude-Mem 的 Refine 候选选择策略

| 维度 | 策略 | 实现要点 |
|------|------|---------|
| **候选筛选** | 多维度（质量 + 时间 + 精炼状态） | 不只按时间，考虑质量分和访问频率 |
| **已精炼处理** | 冷却期机制（7天） | 允许再次精炼，防止记忆永久固化 |
| **多主题输出** | 聚类 + 分别精炼 | 输出多个精炼记录，保持粒度适中 |
| **触发时机** | SessionEnd 异步 | 旁路架构约束，效果下次会话可见 |

---

## 4. 关键实验发现

### 4.1 主要结果

**多轮任务（AlfWorld, BabyAI, PDDL, ScienceWorld）**:

| 模型 | 方法 | 平均成功率 | 平均进度率 |
|------|------|-----------|-----------|
| Claude 3.7 Sonnet | Baseline | 0.24 | 0.52 |
| Claude 3.7 Sonnet | ReAct | 0.57 | 0.79 |
| Claude 3.7 Sonnet | ExpRAG | 0.63 | 0.82 |
| **Claude 3.7 Sonnet** | **ReMem** | **0.78** | **0.91** |

**单轮推理（AIME, GPQA, MMLU-Pro, ToolBench）**:

| 模型 | 方法 | 平均准确率 |
|------|------|-----------|
| Gemini 2.5 Flash | Baseline | 0.59 |
| Gemini 2.5 Flash | ExpRAG | 0.60 |
| **Gemini 2.5 Flash** | **ReMem** | **0.65** |

### 4.2 核心发现

#### RQ1: 自演化记忆持续优于静态方案

- **多轮任务增益显著**: ReMem 在多轮环境中达到 0.92/0.96 成功率
- **单轮任务增益适中**: 说明经验复用在长期任务中更有价值
- **小模型受益更大**: 测试时演化是增强轻量 LLM 的实用路径

#### RQ2: 任务相似度是关键因素

```
ReMem 增益 vs 任务相似度相关性:
- Gemini 2.5 Flash: r = 0.717
- Claude 3.7 Sonnet: r = 0.563
```

**结论**: 
- 高相似度任务集（如 PDDL, AlfWorld）收益最大
- 低相似度任务集（如 AIME-25, GPQA）收益有限
- 嵌入组织结构和语义重叠是驱动记忆演化的关键

#### RQ3: 步骤效率显著提升

| 环境 | History (步) | ReMem (步) | 改善 |
|------|-------------|-----------|------|
| AlfWorld | 22.6 | 11.5 | -49% |
| BabyAI | 15.2 | 8.7 | -43% |
| PDDL | 18.3 | 10.2 | -44% |

**结论**: 持续精炼不仅提升准确率，还使推理更高效。

#### RQ4: 失败经验需要选择性利用

- 基线方法在存储失败经验时性能下降
- ReMem 通过主动精炼保持鲁棒性
- **关键**: 学习成功经验 + 适当利用失败信息

#### RQ5: 累积性能持续改进

- ReMem 在长任务序列中实现更快适应和更稳定保持
- 证明持续反思在测试时学习中的价值

---

## 5. 当前 Claude-Mem Java 版本的差距分析

### 5.1 当前架构回顾

```
Claude-Mem Java 架构:
┌─────────────────────────────────────────────────────────────┐
│  Hook Events (SessionStart, UserPrompt, PostToolUse, etc.) │
│                           ↓                                  │
│  Thin Proxy (wrapper.js) → HTTP POST                        │
│                           ↓                                  │
│  Java Backend (Spring Boot)                                  │
│    ├── IngestionController (接收事件)                        │
│    ├── AgentService (LLM → XML解析 → 嵌入 → 存储)            │
│    ├── SearchService (向量 + 文本搜索)                        │
│    └── PostgreSQL + pgvector (存储)                          │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 与 Evo-Memory 框架的对比

| 维度 | Evo-Memory 框架 | 当前 Claude-Mem Java | 差距 |
|------|----------------|---------------------|------|
| **记忆抽象** | (F, U, R, C) 四元组 | 隐式存在，无显式抽象 | ⚠️ 缺乏统一形式化 |
| **更新模块 U** | 支持追加/压缩/重写/合并 | 仅支持追加 (Upsert) | ❌ 无记忆演化机制 |
| **检索模块 R** | 多策略检索 + 相似度过滤 | 向量相似度 + 项目过滤 | ✅ 基本满足 |
| **上下文构造 C** | 模板化 + 动态选择 | 静态模板 | ⚠️ 缺乏动态性 |
| **任务流** | 显式序列化 + 依赖建模 | 独立事件处理 | ❌ 无任务流视角 |
| **Refine 操作** | 主动记忆管理 | 无 | ❌ 关键缺失 |
| **经验复用** | 检索历史轨迹作为 ICL 样本 | 检索事实性内容 | ⚠️ 检索目标不同 |
| **质量评估** | quality_score + 反馈 | 无 | ❌ 缺乏质量机制 |

### 5.3 具体差距分析

#### 差距 1: 记忆是静态的，无演化能力

**当前行为**:
```java
// ObservationEntity 只是存储，永远不会被修改或合并
public class ObservationEntity {
    private String content;
    private float[] embedding;
    // 一旦写入，永不更新
}
```

**论文建议**: 记忆应该能被:
- **删除**: 过时/噪音/低质量
- **合并**: 多个具体案例 → 抽象规则
- **重写**: 更精确的表述

#### 差距 2: 无任务流视角

**当前行为**: 每个事件独立处理
```
event_1 → observation_1
event_2 → observation_2
event_3 → observation_3
```

**论文建议**: 任务应该有序列依赖
```
task_1 → experience_1 → M_2
task_2 (基于 M_2) → experience_2 → M_3
task_3 (基于 M_3) → experience_3 → M_4
```

#### 差距 3: 检索的是事实，不是策略

**当前 prompt 模板**:
```
检索到的内容:
- "用户修改了 .gitignore 文件"
- "添加了 V6 数据库迁移"
```

**论文建议的经验格式**:
```
检索到的策略:
- "当处理 Git 忽略规则时，应该检查是否会影响构建输出"
- "数据库迁移添加新列时，记得同时创建索引以优化查询"
```

#### 差距 4: 无质量评估机制

**当前**: 所有观察同等对待

**论文**: 每个记忆条目有 `quality_score`
- 成功任务的经验得分高
- 失败任务的经验可选择性保留
- 低质量记忆在 Refine 时被剪枝

---

## 6. 改进方向与实施建议

> **⚠️ 架构约束**: Claude-Mem 是旁路观察者架构，无法同步干预 Claude Code 的执行。所有改进都应遵循"**异步处理 + 延迟生效**"原则。详见 [第8节：旁路架构的适配策略](#8-旁路架构的适配策略)。

### 6.1 短期改进（可快速实施）

#### 6.1.1 添加记忆质量评分

> **⚠️ 重要**: 本功能由开关 `app.memory.refine-enabled` 控制。关闭时不影响现有功能，所有新增字段均可为空。

**数据库 Schema 扩展**:

```sql
-- V11__observation_quality.sql
-- 注意: 所有字段均可为空，不影响已有功能
-- 开关: app.memory.refine-enabled = false 时不执行精炼逻辑
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS quality_score FLOAT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(20);

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMP;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS access_count INT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_at TIMESTAMP;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_from_ids TEXT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS user_comment TEXT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_quality_score ON mem_observations(quality_score DESC);
CREATE INDEX IF NOT EXISTS idx_last_accessed ON mem_observations(last_accessed_at);
CREATE INDEX IF NOT EXISTS idx_refined_at ON mem_observations(refined_at);
```

**Java 实现**:

```java
public enum FeedbackType {
    SUCCESS,    // 任务成功完成
    PARTIAL,    // 部分成功
    FAILURE,    // 任务失败
    UNKNOWN     // 无反馈信息
}

public class QualityScorer {
    
    public float estimateQuality(FeedbackType feedback, 
                                  String reasoningTrace,
                                  String output,
                                  int toolUsageCount) {
        // 基础分数来自反馈类型
        float baseScore = switch (feedback) {
            case SUCCESS -> 0.75f;
            case PARTIAL -> 0.50f;
            case FAILURE -> 0.20f;
            case UNKNOWN -> 0.50f;
        };
        
        // 效率调整（工具使用次数）
        float efficiencyBonus = Math.max(0, 0.1f - (toolUsageCount - 3) * 0.02f);
        
        // 内容质量调整（基于长度和结构）
        float contentBonus = evaluateContentQuality(reasoningTrace, output);
        
        float finalScore = baseScore + efficiencyBonus + contentBonus;
        
        // LLM 自评（可选）
        if (enableLLMEvaluation && feedback == FeedbackType.SUCCESS) {
            float llmScore = llmEvaluate(output);
            finalScore = (finalScore * 0.7f) + (llmScore * 0.3f);
        }
        
        return Math.min(1.0f, Math.max(0.0f, finalScore));
    }
    
    // 辅助方法定义见附录 A.1
    private float evaluateContentQuality(String reasoningTrace, String output) { /* 见附录 A.1 */ }
    private float llmEvaluate(String output) { /* 见附录 A.1 */ }
}
```

**检索时考虑质量**:

```java
@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {
    
    @Query("""
        SELECT o FROM ObservationEntity o 
        WHERE o.projectPath = :project 
        AND o.qualityScore >= :minQuality
        ORDER BY cosine_similarity(o.embedding, :query) DESC
        LIMIT :limit
        """)
    List<ObservationEntity> searchWithQuality(
        @Param("project") String project,
        @Param("query") float[] query,
        @Param("minQuality") float minQuality,
        @Param("limit") int limit
    );
}
```

**检索评分公式（含时间衰减）**:

> **关键补充**: 简单的质量过滤不足以优化检索，应该使用加权评分综合考虑多个因素。

```java
package com.ablueforce.cortexce.service;

import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetrievalScoringService {

    private static final float QUALITY_WEIGHT = 0.5f;      // 质量分权重
    private static final float RECENCY_WEIGHT = 0.3f;      // 新鲜度权重
    private static final float ACCESS_WEIGHT = 0.2f;       // 访问频率权重

    private static final double TIME_DECAY_HALF_LIFE_DAYS = 30.0; // 30天后新鲜度衰减一半

    /**
     * 计算记忆条目的综合检索评分
     *
     * 公式: score = quality * Wq + recency * Wr + access * Wa
     *
     * @param qualityScore 质量分数 [0, 1]
     * @param createdAt    创建时间
     * @param accessCount  访问次数
     * @return 综合评分 [0, 1]
     */
    public float calculateRetrievalScore(float qualityScore,
                                        OffsetDateTime createdAt,
                                        int accessCount) {
        // 1. 质量分归一化
        float normalizedQuality = Math.max(0, Math.min(1, qualityScore));

        // 2. 时间衰减计算 (指数衰减)
        long daysSinceCreated = ChronoUnit.DAYS.between(createdAt, OffsetDateTime.now());
        double decayFactor = Math.pow(0.5, daysSinceCreated / TIME_DECAY_HALF_LIFE_DAYS);
        float recencyScore = (float) decayFactor;

        // 3. 访问次数归一化 (使用 log 避免极端值)
        float accessScore = (float) (Math.log(1 + accessCount) / Math.log(1 + 100)); // 假设100次为上限

        // 4. 加权计算
        float finalScore = normalizedQuality * QUALITY_WEIGHT
                         + recencyScore * RECENCY_WEIGHT
                         + accessScore * ACCESS_WEIGHT;

        return Math.max(0, Math.min(1, finalScore));
    }

    /**
     * 带评分的检索方法
     */
    public List<ScoredObservation> searchWithScoring(String projectPath,
                                                     String query,
                                                     int limit) {
        // 1. 向量检索获取候选
        List<ObservationEntity> candidates = searchService.searchByVector(
            query, projectPath, limit * 3 // 获取更多候选用于重排序
        );

        // 2. 计算每个候选的综合评分
        List<ScoredObservation> scored = candidates.stream()
            .map(obs -> {
                float score = calculateRetrievalScore(
                    obs.getQualityScore() != null ? obs.getQualityScore() : 0.5f,
                    obs.getCreatedAt(),
                    obs.getAccessCount() != null ? obs.getAccessCount() : 0
                );
                return new ScoredObservation(obs, score);
            })
            .sorted(Comparator.comparingDouble(ScoredObservation::getScore).reversed())
            .limit(limit)
            .toList();

        // 3. 更新访问次数
        updateAccessCounts(scored);

        return scored;
    }

    private void updateAccessCounts(List<ScoredObservation> scored) {
        for (ScoredObservation so : scored) {
            ObservationEntity obs = so.getObservation();
            if (obs.getAccessCount() == null) {
                obs.setAccessCount(1);
            } else {
                obs.setAccessCount(obs.getAccessCount() + 1);
            }
            obs.setLastAccessedAt(OffsetDateTime.now());
        }
        observationRepository.saveAll(
            scored.stream()
                .map(ScoredObservation::getObservation)
                .toList()
        );
    }

    @Data
    public static class ScoredObservation {
        private final ObservationEntity observation;
        private final float score;
    }
}
```

**评分公式可视化**:

```
评分随时间变化示例 (quality=0.8, accessCount=5):

时间(天)  新鲜度分  综合分
0         1.00      0.80
7         0.85      0.70
30        0.50      0.50
60        0.25      0.38
90        0.12      0.30
```

**检索策略对比**:

| 策略 | 公式 | 适用场景 |
|------|------|---------|
| 纯向量 | cosine_similarity | 语义相似度优先 |
| 质量过滤 | quality >= 0.6 | 过滤噪音，保留高质量 |
| 时间衰减 | quality * 0.7 + recency * 0.3 | 平衡质量与新鲜度 |
| 综合评分 | 质量 + 新鲜度 + 访问 | 最优平衡 |

**实施建议**:
- Phase 1: 先使用简单的质量过滤
- Phase 2: 升级为综合评分检索

#### 6.1.2 反馈获取机制（关键补充）

> **⚠️ 重要说明**: 质量评分需要反馈信息作为输入。在旁路架构下，有两种反馈来源：
> 1. **自动推断**：SessionEnd 时从可用信息推断
> 2. **人工审核**：通过 WebUI 用户事后审核给出

**旁路架构下反馈来源分析**:

| 来源 | 可用性 | 可靠性 | 说明 |
|------|--------|--------|------|
| **WebUI 人工审核** | ✅ 可用 | ⭐⭐⭐ 高 | 用户事后审核记忆，直接给出反馈（最高优先级） |
| last_assistant_message | ✅ 可用 | ⭐⭐ 中 | Claude 的最后回复，可能包含完成状态 |
| observation 数量 | ✅ 可用 | ⭐ 低 | 过少可能表示失败 |
| 会话持续时间 | ✅ 可用 | ⭐ 低 | 过短可能表示失败 |

**WebUI 用户反馈机制**（新增）:

Claude-Mem 自带 WebUI，用户可以事后审核记忆记录并给出反馈：

```java
package com.ablueforce.cortexce.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.OffsetDateTime;

// WebUI 反馈 API
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    /**
     * 用户对记忆进行事后审核反馈
     *
     * @param observationId 记忆 ID
     * @param feedbackType 反馈类型: success / partial / failure / unknown
     * @param comment 用户评论（可选）
     */
    @PostMapping("/observation/{id}")
    public ResponseEntity<Void> submitObservationFeedback(
            @PathVariable("id") Long observationId,
            @RequestParam FeedbackType feedbackType,
            @RequestParam(required = false) String comment) {

        observationRepository.findById(observationId).ifPresent(obs -> {
            obs.setFeedbackType(feedbackType.name().toLowerCase());
            obs.setUserComment(comment);
            obs.setFeedbackUpdatedAt(OffsetDateTime.now());
            observationRepository.save(obs);

            // 重新计算质量分数
            float newQuality = qualityScorer.recalculateWithFeedback(obs);
            obs.setQualityScore(newQuality);
            observationRepository.save(obs);

            log.info("User feedback applied to observation {}: {}",
                observationId, feedbackType);
        });

        return ResponseEntity.ok().build();
    }
}
```

**反馈优先级**:
1. **WebUI 人工反馈** > 自动推断（用户审核更准确）
2. 自动推断仅在无人工反馈时使用

**推荐反馈推断策略**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;

@Service
public class FeedbackInferenceService {

    /**
     * 从 SessionEnd 可用信息推断反馈类型
     *
     * @param lastAssistantMessage Claude 的最后回复
     * @param observationCount     Observation 数量
     * @param sessionDurationMs    会话持续时间（毫秒）
     * @return 推断的反馈类型
     */
    public FeedbackType inferFeedback(String lastAssistantMessage,
                                      int observationCount,
                                      long sessionDurationMs) {
        // 1. 从最后回复中解析成功/失败信号
        if (lastAssistantMessage != null) {
            String lowerMsg = lastAssistantMessage.toLowerCase();

            // 成功信号
            if (containsSuccessSignal(lowerMsg)) {
                return FeedbackType.SUCCESS;
            }

            // 失败/未完成信号
            if (containsFailureSignal(lowerMsg)) {
                return FeedbackType.FAILURE;
            }
        }

        // 2. 基于观察数量的启发式判断
        if (observationCount == 0) {
            return FeedbackType.FAILURE; // 无任何有效操作
        }
        if (observationCount < 3) {
            return FeedbackType.FAILURE; // 工具使用过少，可能受阻
        }

        // 3. 基于会话时长（过短可能表示失败）
        if (sessionDurationMs < 5000 && observationCount < 5) {
            return FeedbackType.FAILURE;
        }

        // 4. 默认返回 PARTIAL（有进展但可能未完成）
        return FeedbackType.PARTIAL;
    }

    private boolean containsSuccessSignal(String msg) {
        return msg.contains("完成") || msg.contains("解决")
            || msg.contains("completed") || msg.contains("finished")
            || msg.contains("done") || msg.contains("solved")
            || msg.contains("已解决") || msg.contains("成功了");
    }

    private boolean containsFailureSignal(String msg) {
        return msg.contains("无法") || msg.contains("失败")
            || msg.contains("failed") || msg.contains("cannot")
            || msg.contains("unable") || msg.contains("错误")
            || msg.contains("error") || msg.contains("无法完成");
    }
}
```

**集成到 SessionEnd**:

```java
// 在 AgentService.completeSessionAsync() 中添加
@Autowired
private FeedbackInferenceService feedbackInferenceService;

public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
    // ... 现有逻辑 ...

    // 1. 计算会话持续时间
    long sessionDurationMs = session.getCompletedAtEpoch() - session.getCreatedAtEpoch();

    // 2. 推断反馈类型
    FeedbackType feedback = feedbackInferenceService.inferFeedback(
        lastAssistantMessage,
        observations.size(),
        sessionDurationMs
    );

    // 3. 为每个 observation 分配反馈类型和质量分
    for (ObservationEntity obs : observations) {
        obs.setFeedbackType(feedback.name().toLowerCase());
        float quality = qualityScorer.estimateQuality(
            feedback,
            obs.getContent(),
            obs.getFacts(),
            observations.size()
        );
        obs.setQualityScore(quality);
    }
    observationRepository.saveAll(observations);

    // ... 继续生成 summary ...
}
```

**实施要点**:
- 反馈推断是"尽力而为"，不可能 100% 准确
- 建议在运行一段时间后，根据实际效果调整启发式规则
- UNKNOWN 类型应作为 fallback，主要用于冷启动阶段

#### 6.1.3 经验结构化模板

**当前**: 自由文本 observation

**改进**: 结构化经验模板

```java
public class ExperienceTemplate {
    
    public String buildExperienceText(
            String taskInput,
            String reasoningTrace,
            String action,
            String outcome,
            List<String> keyLearnings) {
        
        return """
            ## Task
            %s
            
            ## Reasoning Process
            %s
            
            ## Action Taken
            %s
            
            ## Outcome
            %s
            
            ## Key Learnings
            %s
            
            ## When to Reuse
            This experience is useful when: [auto-generated condition]
            """.formatted(
                taskInput,
                reasoningTrace,
                action,
                outcome,
                String.join("\n- ", keyLearnings)
            );
    }
}
```

**LLM Prompt 增强**:

```
从以下工具使用事件中提取经验，重点关注:
1. 采取了什么策略
2. 为什么这个策略有效/无效
3. 什么情况下可以复用这个策略

格式要求:
<observation>
<task>任务描述</task>
<strategy>采用的策略</strategy>
<outcome>结果</outcome>
<reuse_condition>复用条件</reuse_condition>
<key_learning>关键学习点</key_learning>
</observation>
```

#### 6.1.4 任务流追踪

**新增表**:

```sql
-- V10__task_streams.sql (V9 分配给 session_metrics)
CREATE TABLE mem_task_streams (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    project_path VARCHAR(512),
    task_sequence INT NOT NULL,  -- 任务在流中的序号
    task_input TEXT,
    task_output TEXT,
    parent_task_id BIGINT,       -- 依赖的前置任务
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_task_id) REFERENCES mem_task_streams(id)
);

CREATE INDEX idx_task_stream_session ON mem_task_streams(session_id, task_sequence);
```

**用途**:
- 跟踪同一会话中的任务序列
- 识别任务依赖关系
- 支持基于任务流的上下文检索

#### 6.1.5 精炼时机设计

> **核心设计**: 精炼触发分为两大类：**事件触发**（最实时）和**定时触发**（分高频和深度）

---

##### 一、事件触发（最实时）

| 事件 | 触发条件 | 精炼逻辑 |
|------|---------|---------|
| **SessionEnd** | 用户结束会话 | 处理当前会话内新产生的记忆：评估质量分、删除低质量、合并会话内相似记忆 |

```java
// AgentService.completeSessionAsync() 中
public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
    // 1. 现有逻辑：生成 Summary

    // 2. 触发即时精炼（当前会话，最实时）
    memoryRefineService.refineCurrentSession(session);
}
```

---

##### 二、定时触发（分高频和深度）

**为什么需要定时触发**:
- 事件触发只处理当前会话的新记忆
- 跨会话积累的低质量记忆需要定时清理
- 合并跨会话的相似记忆需要全局视角

| 层级 | 间隔 | 触发条件 | 精炼逻辑 | 资源 |
|------|------|---------|---------|------|
| **高频** | 15分钟 | 检测到积压 | 快速清理少量低质量记忆 + 轻量级精炼 | 低 |
| **深度** | 每天凌晨 | 定时 | 跨会话大规模合并 | 高 |

---

**精炼时机总结**:

| 触发方式 | 时机 | 间隔 | 实时性 |
|---------|------|------|-------|
| **事件触发** | SessionEnd | 会话结束 | ⭐⭐⭐ 最实时 |
| **定时-高频** | 检测到积压 | 15分钟 | ⭐⭐ 接近实时 |
| **定时-深度** | 每天 | 24小时 | 深度优化 |

---

#### 6.1.6 定时增量精炼任务

**两级精炼检测策略**（优化 LLM 调用效率）:

> **核心优化**: 不是每次定时任务都调用 LLM 精炼，而是先做快速检测，只在需要时才调用 LLM。

```java
package com.ablueforce.cortexce.service;

/**
 * 两级精炼检测策略
 *
 * 第一级（快速检测）: 不调用 LLM，通过编码的启发式规则判断
 *   - 质量分低于阈值？
 *   - 超过一定时间未访问？
 *   - 与其他记忆相似度过高（可合并）？
 *
 * 第二级（深度检测）: 如果第一级通过，调用 LLM 进行精炼
 *   - LLM 判断具体如何合并/重写/删除
 *   - 成本较高，但更智能
 */
@Service
public class RefineDetectionService {

    @Value("${app.memory.refine.quality-threshold:0.3}")
    private float qualityThreshold;

    @Value("${app.memory.refine.days-threshold:30}")
    private int daysThreshold;

    @Value("${app.memory.refine.similarity-threshold:0.85}")
    private float similarityThreshold;

    /**
     * 第一级检测：快速启发式检测（无需 LLM）
     *
     * @return true 表示需要进一步精炼处理
     */
    public boolean needsRefinement(String projectPath) {
        // 1. 检查是否有低质量记忆
        long lowQualityCount = observationRepository
            .countByProjectPathAndQualityScoreLessThan(projectPath, qualityThreshold);

        if (lowQualityCount > 10) {
            log.info("Project {} has {} low-quality observations, needs refinement",
                projectPath, lowQualityCount);
            return true;
        }

        // 2. 检查是否有超过阈值未访问的记忆
        long staleCount = observationRepository
            .countByProjectPathAndLastAccessedBefore(
                projectPath, OffsetDateTime.now().minusDays(daysThreshold));

        if (staleCount > 20) {
            log.info("Project {} has {} stale observations, needs refinement",
                projectPath, staleCount);
            return true;
        }

        // 3. 检查是否有可合并的相似记忆
        // (通过向量检索找出相似记忆对)
        long mergeableCount = estimateMergeableCount(projectPath);

        if (mergeableCount > 5) {
            log.info("Project {} has {} potentially mergeable observations",
                projectPath, mergeableCount);
            return true;
        }

        // 4. 检查是否有待精炼的记忆超过冷却期
        long overdueCount = observationRepository
            .countOverdueForRefine(projectPath, OffsetDateTime.now().minusDays(7));

        if (overdueCount > 15) {
            return true;
        }

        return false; // 不需要精炼
    }

    private long estimateMergeableCount(String projectPath) {
        // 简化实现：统计相似记忆对数量
        // 实际实现可通过采样向量计算
        return observationRepository
            .countSimilarPairs(projectPath, similarityThreshold);
    }
}
```

**定时任务设计**:

```java
package com.ablueforce.cortexce.scheduled;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.RefineDetectionService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRefineScheduledTask {

    private final MemoryRefineService memoryRefineService;
    private final RefineDetectionService refineDetectionService;

    /**
     * 高频增量精炼 - 每 15 分钟执行一次
     *
     * 目标: 尽可能接近 ReMem 的"持续演化"效果
     *
     * 策略:
     * 1. 第一级检测：快速启发式检查（无需 LLM）
     * 2. 如果需要，触发第二级：LLM 精炼（轻量级，每次最多5条）
     * 3. 不做跨会话深度合并
     *
     * 配合 SessionEnd 精炼:
     * - SessionEnd: 处理当前会话（最实时）
     * - 每15分钟: 清理积压（接近实时）
     * - 每天: 深度合并和规则提取
     */
    @Scheduled(fixedRateString = "${app.memory.refine.frequent-interval-ms:900000}")
    public void frequentRefine() {
        log.debug("Starting frequent memory refine (15min interval)");

        try {
            List<String> projects = projectService.getAllProjects();

            for (String project : projects) {
                // 只处理积压的低优先级记忆
                memoryRefineService.quickRefine(project, 5); // 最多5条
            }

        } catch (Exception e) {
            log.error("Frequent refine task failed", e);
        }
    }

    /**
     * 每天凌晨 4:00 执行深度精炼（相比普通精炼更激进地合并）
     * - 包括跨会话相似记忆合并
     * - 规则提取和抽象
     *
     * 设计理由: 深度精炼比普通精炼更消耗资源，
     * 但为了接近 ReMem 的持续演化效果，需要每天执行
     */
    @Scheduled(cron = "${app.memory.deep-refine-cron:0 0 4 * * ?}")
    public void scheduledDeepRefine() {
        log.info("Starting scheduled deep memory refine");

        try {
            List<String> projects = projectService.getAllProjects();

            for (String project : projects) {
                try {
                    memoryRefineService.deepRefineProjectMemories(project);
                } catch (Exception e) {
                    log.error("Failed to deep refine project: {}", project, e);
                }
            }

            log.info("Scheduled deep refine completed for {} projects", projects.size());

        } catch (Exception e) {
            log.error("Scheduled deep refine task failed", e);
        }
    }
}
```

**Spring Boot 启用定时任务配置**:

```java
// 在主应用类上添加 @EnableScheduling
@SpringBootApplication
@EnableScheduling
public class ClaudeMemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaudeMemApplication.class, args);
    }
}
```

**application.yml 配置**:

```yaml
app:
  memory:
    # 深度精炼 cron（每天凌晨4点）
    deep-refine-cron: "0 0 4 * * ?"
    # 高频精炼间隔（15分钟，快速清理积压 + 轻量级精炼）
    frequent-interval-ms: 900000
```

**精炼任务分层设计**（更接近 ReMem 实时效果）:

| 层级 | 触发时机 | 间隔 | 范围 | LLM调用 | 适用场景 |
|------|---------|------|------|---------|---------|
| **L1: SessionEnd** | 会话结束 | 实时 | 当前会话 | ✅ 是 | 每次会话后立即处理 |
| **L2: 高频** | 定时 | 15分钟 | 少量积压 + 轻量级精炼 | ✅ 是 | 接近实时清理 |
| **L3: 深度** | 定时 | 每天 | 全量+跨会话 | ✅ 是 | 规则提取和抽象 |

**为什么需要多层设计**:
- 旁路系统无法修改 Agent 循环，但可以尽可能接近实时效果
- L1 (SessionEnd) 是最实时的，每次会话结束立即处理
- L2 (高频) 每15分钟处理积压，进一步缩短延迟
- 多层设计避免单次任务过重，同时保证持续演化

**实施优先级**:
- Phase 1: 先实现 SessionEnd 精炼
- Phase 2: 添加定时精炼任务

### 6.2 中期改进（需要一定工程量）

#### 6.2.1 实现 Refine Memory 机制

**新增服务**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryRefineService {
    
    private static final int REFINE_BATCH_SIZE = 10;
    private static final float QUALITY_THRESHOLD = 0.3f;
    private static final int REFINED_COOLDOWN_DAYS = 7;
    
    private final ObservationRepository observationRepository;
    private final LlmService llmService;
    
    /**
     * 触发记忆精炼（可在 SessionEnd 或定时任务中调用）
     */
    @Async
    public void refineMemory(String projectPath) {
        // 1. 检索候选记忆（低质量 + 长时间未访问）
        List<ObservationEntity> candidates = findRefineCandidates(projectPath);
        
        if (candidates.isEmpty()) return;
        
        // 2. 构造元推理 prompt
        String metaPrompt = buildMetaPrompt(candidates);
        
        // 3. LLM 生成编辑计划
        String planJson = llmService.chatCompletion(
            "You are a memory management assistant. Output only valid JSON.",
            metaPrompt
        );
        RefinePlan plan = parseRefinePlan(planJson);
        
        // 4. 执行编辑计划
        executeRefinePlan(plan);
    }
    
    
    /**
     * 查找需要精炼的候选记忆
     * 
     * 策略: 多维度筛选（与3.5.4节保持一致）
     */
    private List<ObservationEntity> findRefineCandidates(String projectPath) {
        List<ObservationEntity> candidates = new ArrayList<>();
        
        // 1. 删除候选: 质量分 < 0.3
        candidates.addAll(observationRepository
            .findByProjectPathAndQualityScoreLessThan(projectPath, QUALITY_THRESHOLD));
        
        // 2. 合并候选: 同一会话内相似度高的记忆
        candidates.addAll(findMergeCandidates(projectPath));
        
        // 3. 过时候选: 30天未访问且质量分 < 0.6
        candidates.addAll(observationRepository
            .findByProjectPathAndLastAccessedBefore(projectPath, 
                OffsetDateTime.now().minusDays(30), 0.6f));
        
        // 4. 过滤已精炼且在冷却期内的记忆（7天内不重复精炼）
        return candidates.stream()
            .filter(o -> canRefine(o))
            .limit(REFINE_BATCH_SIZE)
            .collect(Collectors.toList());
    }
    
    private boolean canRefine(ObservationEntity obs) {
        if (obs.getRefinedAt() == null) return true; // 从未精炼
        // 允许 7 天后再次精炼
        return obs.getRefinedAt().isBefore(OffsetDateTime.now().minusDays(7));
    }
    
    private List<ObservationEntity> findMergeCandidates(String projectPath) {
        // 查找同一会话内嵌入向量相似度 > 0.8 的记忆对
        // 实现需要根据项目实际情况调整
        return observationRepository.findHighSimilarityPairs(projectPath, 0.8f);
    }
    
    private String buildMetaPrompt(List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是一些记忆条目，请评估它们的质量并决定如何处理：\n\n");
        
        for (int i = 0; i < candidates.size(); i++) {
            ObservationEntity obs = candidates.get(i);
            sb.append("[%d] ID: %s\n".formatted(i, obs.getId()));
            sb.append("内容: %s\n".formatted(obs.getContent()));
            sb.append("质量分: %.2f\n".formatted(obs.getQualityScore()));
            sb.append("创建时间: %s\n\n".formatted(obs.getCreatedAt()));
        }
        
        sb.append("""
            请输出编辑计划（JSON格式）:
            {
              "to_delete": ["id1", "id2"],
              "to_merge": [["id1", "id2"], ["id3", "id4"]],
              "to_rewrite": [{"id": "id", "new_content": "..."}]
            }
            """);
        
        return sb.toString();
    }
    
    private void executeRefinePlan(RefinePlan plan) {
        // 删除
        if (!plan.getToDelete().isEmpty()) {
            observationRepository.deleteAllById(plan.getToDelete());
            log.info("Deleted {} low-quality memories", plan.getToDelete().size());
        }
        
        // 合并
        for (List<UUID> ids : plan.getToMerge()) {
            ObservationEntity merged = mergeObservations(ids);
            observationRepository.save(merged);
        }
        
        // 重写
        for (RefinePlan.RewriteItem item : plan.getToRewrite()) {
            observationRepository.updateContent(item.getId(), item.getNewContent());
        }
    }
    
    private ObservationEntity mergeObservations(List<UUID> ids) {
        List<ObservationEntity> observations = observationRepository.findAllById(ids);
        
        // 构造合并 prompt
        String mergePrompt = buildMergePrompt(observations);
        String mergedContent = llmService.chatCompletion(
            "You are a memory consolidation assistant.",
            mergePrompt
        );
        
        // 创建合并后的 observation
        ObservationEntity merged = new ObservationEntity();
        merged.setContent(mergedContent);
        merged.setQualityScore(
            (float) observations.stream()
                .mapToDouble(ObservationEntity::getQualityScore)
                .average()
                .orElse(0.5)
        );
        // ... 其他字段
        
        // 删除原始 observations
        observationRepository.deleteAll(observations);
        
        return merged;
    }
}
```

**集成到 SessionEnd Hook**:

```java
@Service
public class AgentService {
    
    public void handleSessionEnd(String sessionId) {
        // ... 现有的 summary 生成逻辑
        
        // 触发记忆精炼
        String projectPath = session.getProjectPath();
        memoryRefineService.refineMemory(projectPath);
    }
}
```

#### 6.2.2 经验检索增强 (ExpRAG 风格)

**数据类定义**:

```java
package com.ablueforce.cortexce.model;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 结构化经验数据类
 */
@Data
public class Experience {
    private String taskId;
    private String task;
    private String strategy;
    private String outcome;
    private String reuseCondition;
    private float qualityScore;
    private OffsetDateTime createdAt;
}
```

**改进检索策略**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpRagService {
    
    private final SearchService searchService;
    
    /**
     * 检索相关经验用于 ICL
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath) {
        // 1. 向量相似度检索
        List<ObservationEntity> similar = searchService.searchByVector(
            currentTask, projectPath, 20
        );
        
        // 2. 过滤高质量经验
        List<ObservationEntity> highQuality = similar.stream()
            .filter(o -> o.getQualityScore() >= 0.6f)
            .limit(4)
            .toList();
        
        // 3. 转换为经验格式
        return highQuality.stream()
            .map(obs -> toExperience(obs))
            .toList();
    }
    
    /**
     * 将 ObservationEntity 转换为 Experience 对象
     * 
     * 从结构化 content 字段中提取 task/strategy/outcome 等信息
     */
    private Experience toExperience(ObservationEntity obs) {
        Experience exp = new Experience();
        exp.setTaskId(obs.getId().toString());
        exp.setQualityScore(obs.getQualityScore());
        exp.setCreatedAt(obs.getCreatedAt());
        
        // 从 content 中解析结构化内容
        String content = obs.getContent();
        exp.setTask(ExperienceTemplates.extractTag(content, "task"));
        exp.setStrategy(ExperienceTemplates.extractTag(content, "strategy"));
        exp.setOutcome(ExperienceTemplates.extractTag(content, "outcome"));
        exp.setReuseCondition(ExperienceTemplates.extractTag(content, "reuse_condition"));
        
        return exp;
    }
    
    /**
     * 构造 ICL Prompt
     */
    public String buildICLPrompt(String currentTask, List<Experience> experiences) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("以下是相关的历史经验，可以参考：\n\n");
        
        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            sb.append("### 经验 %d\n".formatted(i + 1));
            sb.append("**任务**: %s\n".formatted(exp.getTask()));
            sb.append("**策略**: %s\n".formatted(exp.getStrategy()));
            sb.append("**结果**: %s\n".formatted(exp.getOutcome()));
            sb.append("**复用条件**: %s\n\n".formatted(exp.getReuseCondition()));
        }
        
        sb.append("---\n\n");
        sb.append("现在处理当前任务：\n%s\n".formatted(currentTask));
        
        return sb.toString();
    }
}
```

#### 6.2.3 步骤效率追踪

**新增指标**:

```sql
-- V12__step_efficiency.sql (更高编号，预留 V9-V11 给核心功能)
ALTER TABLE mem_sessions 
ADD COLUMN total_steps INT DEFAULT 0;

ALTER TABLE mem_sessions
ADD COLUMN avg_steps_per_task FLOAT;
```

**用途**:
- 跟踪任务完成所需的步骤数
- 评估记忆系统对效率的影响
- 对比不同配置下的步骤效率

#### 6.2.4 上下文预计算与缓存策略

> **关键补充**: 在旁路架构下，可以通过预计算和缓存来减少 SessionStart 时的检索延迟，同时提升上下文质量。

**为什么需要缓存策略**:

| 问题 | 影响 | 解决方案 |
|------|------|---------|
| 每次 SessionStart 重新检索 | 延迟高 (~500ms) | 预计算 + 缓存 |
| 实时检索无法利用精炼结果 | 效果打折 | SessionEnd 时预生成 |
| 多项目并发检索 | 资源竞争 | 缓存分层 |

**缓存架构设计**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ContextPrecomputeService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalScoringService retrievalScoringService;
    private final SearchService searchService;

    // 缓存配置
    private static final String CACHE_KEY_PREFIX = "mem:context:";
    private static final Duration CACHE_TTL = Duration.ofHours(24); // 缓存24小时
    private static final int MAX_PRECOMPUTE_COUNT = 10; // 预计算 top-10 上下文

    /**
     * SessionEnd 时预计算并缓存下次会话上下文
     *
     * 触发时机: SessionEnd Hook
     * 缓存键: mem:context:{projectPath}
     */
    @Async
    public void precomputeContext(String projectPath) {
        try {
            log.debug("Precomputing context for project: {}", projectPath);

            // 1. 获取项目的高质量记忆
            List<ObservationEntity> highQualityMemories = searchService
                .searchWithQualityFilter(projectPath, 0.6f, 50);

            // 2. 使用综合评分检索 top-10
            List<RetrievalScoringService.ScoredObservation> topMemories =
                retrievalScoringService.searchWithScoring(projectPath, "", MAX_PRECOMPUTE_COUNT);

            // 3. 生成预计算上下文
            PrecomputedContext context = buildPrecomputedContext(topMemories);

            // 4. 序列化并缓存到 Redis
            String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);

            log.info("Context precomputed for project: {} ({} memories)",
                projectPath, topMemories.size());

        } catch (Exception e) {
            log.error("Failed to precompute context for project: {}", projectPath, e);
        }
    }

    /**
     * SessionStart 时快速获取预计算上下文
     *
     * @return 预计算上下文，如果缓存未命中则返回 null
     */
    public PrecomputedContext getPrecomputedContext(String projectPath) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
            String json = redisTemplate.opsForValue().get(cacheKey);

            if (json == null) {
                log.debug("Cache miss for project: {}", projectPath);
                return null;
            }

            return objectMapper.readValue(json, PrecomputedContext.class);

        } catch (Exception e) {
            log.warn("Failed to get precomputed context for project: {}", projectPath, e);
            return null;
        }
    }

    /**
     * 强制刷新缓存（在精炼后调用）
     */
    public void invalidateCache(String projectPath) {
        String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
        redisTemplate.delete(cacheKey);
        log.info("Cache invalidated for project: {}", projectPath);

        // 触发重新预计算
        precomputeContext(projectPath);
    }

    private PrecomputedContext buildPrecomputedContext(
            List<RetrievalScoringService.ScoredObservation> memories) {

        PrecomputedContext context = new PrecomputedContext();
        context.setComputedAt(OffsetDateTime.now());

        List<PrecomputedContext.MemoryEntry> entries = memories.stream()
            .map(scored -> {
                PrecomputedContext.MemoryEntry entry = new PrecomputedContext.MemoryEntry();
                entry.setId(scored.getObservation().getId().toString());
                entry.setTitle(scored.getObservation().getTitle());
                entry.setContent(scored.getObservation().getContent());
                entry.setScore(scored.getScore());
                entry.setQualityScore(scored.getObservation().getQualityScore());
                return entry;
            })
            .toList();

        context.setMemories(entries);

        // 生成摘要
        String summary = generateContextSummary(entries);
        context.setSummary(summary);

        return context;
    }

    private String generateContextSummary(List<PrecomputedContext.MemoryEntry> memories) {
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 关键经验\n\n");

        for (int i = 0; i < Math.min(3, memories.size()); i++) {
            PrecomputedContext.MemoryEntry m = memories.get(i);
            sb.append(String.format("- %s (质量: %.2f)\n",
                m.getTitle(), m.getQualityScore()));
        }

        return sb.toString();
    }

    @Data
    public static class PrecomputedContext {
        private OffsetDateTime computedAt;
        private List<MemoryEntry> memories;
        private String summary;

        @Data
        public static class MemoryEntry {
            private String id;
            private String title;
            private String content;
            private float score;
            private float qualityScore;
        }
    }
}
```

**缓存命中时的 SessionStart 流程优化**:

```
优化前 (无缓存):
  SessionStart → 向量检索 (500ms) → 构造上下文 → 注入
                                          总计: ~500ms

优化后 (有缓存):
  SessionStart → 获取缓存 (5ms) → 注入
                                    总计: ~5ms
```

**缓存失效策略**:

| 策略 | 触发条件 | TTL |
|------|---------|-----|
| 时间失效 | 缓存创建后 24 小时 | 24h |
| 精炼失效 | MemoryRefineService 执行后 | 立即刷新 |
| 主动失效 | 用户手动触发 | 立即刷新 |
| 容量失效 | Redis 内存不足 | LRU 淘汰 |

**Redis 配置 (application.yml)**:

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

app:
  memory:
    precompute:
      enabled: true
      cache-ttl-hours: 24
      max-memories: 10
```

**实施优先级**:
- Phase 2: 先实现基础缓存（本地 Caffeine Cache）
- Phase 3: 升级为 Redis 分布式缓存（支持多实例）

**与现有 ContextCacheService 的关系**:

现有 `ContextCacheService` 是内存缓存，存储 ContextService 的结果。新的 `ContextPrecomputeService` 是 Redis 缓存，存储预计算的高质量记忆。两者可以共存：
- `ContextCacheService`: 短期缓存（会话级别）
- `ContextPrecomputeContext`: 长期缓存（项目级别，可跨会话）

### 6.3 长期改进（架构级变更）

> **注意**: 完整的 ReMem 实现需要同步干预智能体执行流程，与 Claude-Mem 的旁路架构不兼容。以下仅供参考。

#### 6.3.1 完整的 ReMem 实现（需要智能体核心修改）

**架构扩展**（需要 Claude Code 原生支持）:

```
当前架构:
Hook → Ingestion → Observation（旁路，不影响执行）

理想的 ReMem 架构（需要核心集成）:
Agent Core → Think → Act → Refine（同步循环）
                ↑_______________|
                记忆直接影响推理
```

> **⚠️ 不适用于 Claude-Mem**: 此架构需要直接修改 Claude Code 的执行循环，与旁路架构冲突。

**替代方案**: 在 Java 后端实现"伪同步"效果

```java
/**
 * 旁路架构下的"伪 ReMem"实现
 * 
 * 原理：虽然无法同步干预执行，但可以：
 * 1. 在 SessionEnd 时预测下次可能需要的上下文
 * 2. 预先生成精炼后的记忆摘要
 * 3. 在下次 SessionStart 时快速注入
 */
@Service
public class PseudoReMemService {
    
    /**
     * SessionEnd 时预测下次可能需要的上下文
     */
    @Async
    public void prepareNextSession(String sessionId, String projectPath) {
        // 1. 分析本次会话的主题和上下文
        SessionAnalysis analysis = analyzeSession(sessionId);
        
        // 2. 精炼相关记忆
        List<ObservationEntity> refined = refineMemories(
            projectPath, 
            analysis.getTopicClusters()
        );
        
        // 3. 预生成上下文摘要
        String precomputedContext = generateContextSummary(refined);
        
        // 4. 缓存到 Redis / 内存
        contextCache.put(projectPath, precomputedContext);
    }
    
    /**
     * SessionStart 时快速注入预计算上下文
     */
    public String getPrecomputedContext(String projectPath) {
        return contextCache.get(projectPath);
    }
}
```

**新增控制器**（用于外部集成场景）:

```java
@RestController
@RequestMapping("/api/remem")
public class ReMemController {
    
    @PostMapping("/think")
    public ThinkResponse think(@RequestBody ThinkRequest request) {
        // 1. 检索相关记忆
        List<MemoryEntry> memories = memoryService.retrieve(
            request.getQuery(), 
            request.getContext()
        );
        
        // 2. 构造 Think prompt
        String prompt = promptBuilder.buildThinkPrompt(
            request.getQuery(),
            memories,
            request.getReasoningTrace()
        );
        
        // 3. LLM 生成思考
        String thought = llmService.generate(prompt);
        
        return new ThinkResponse(thought, memories);
    }
    
    @PostMapping("/act")
    public ActResponse act(@RequestBody ActRequest request) {
        // 执行动作并收集反馈
        ActionResult result = actionExecutor.execute(
            request.getAction(),
            request.getEnvironment()
        );
        
        // 写回记忆
        memoryService.writeBack(
            request.getQuery(),
            request.getReasoningTrace(),
            request.getAction(),
            result.getFeedback()
        );
        
        return new ActResponse(result);
    }
    
    @PostMapping("/refine")
    public RefineResponse refine(@RequestBody RefineRequest request) {
        RefinePlan plan = memoryRefineService.generateRefinePlan(
            request.getQuery(),
            request.getCandidates()
        );
        
        memoryRefineService.executeRefinePlan(plan);
        
        return new RefineResponse(plan);
    }
}
```

#### 6.3.2 多模态记忆

**扩展 Schema**:

```sql
-- V13__multimodal_memory.sql (更高编号，预留核心功能)
ALTER TABLE mem_observations 
ADD COLUMN memory_type VARCHAR(20) DEFAULT 'text';  -- 'text', 'code', 'trajectory', 'rule'

ALTER TABLE mem_observations
ADD COLUMN modality VARCHAR(20) DEFAULT 'text';  -- 'text', 'image', 'audio', 'code'

-- 记忆关联
CREATE TABLE mem_memory_relations (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    relation_type VARCHAR(50),  -- 'derives_from', 'abstracts', 'contradicts'
    confidence FLOAT DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 6.3.3 分布式记忆演化

**场景**: 多个 Agent 共享记忆库

```java
@Service
public class DistributedMemoryService {
    
    private final KafkaTemplate<String, MemoryEvent> kafkaTemplate;
    
    /**
     * 发布记忆更新事件
     */
    public void publishMemoryUpdate(MemoryEntry entry) {
        MemoryEvent event = new MemoryEvent(
            entry.getId(),
            entry.getProjectPath(),
            MemoryEvent.Type.UPDATED,
            entry.getContent()
        );
        
        kafkaTemplate.send("memory-updates", event);
    }
    
    /**
     * 消费其他 Agent 的记忆更新
     */
    @KafkaListener(topics = "memory-updates")
    public void onMemoryUpdate(MemoryEvent event) {
        // 合并到本地记忆库
        memoryMerger.merge(event);
    }
}
```

---

## 7. 实施路线图

### Phase 1: 基础增强（1-2 周）

> **架构约束**: 所有改动都在旁路 Java 后端，通过 SessionEnd/SessionStart 触发，不影响 Claude Code 执行。

| 任务 | 优先级 | 估计工时 | 依赖 | 旁路适配 |
|------|--------|---------|------|---------|
| 添加 quality_score 字段 | P0 | 4h | 无 | SessionEnd 时评估 |
| 实现质量评分逻辑 | P0 | 8h | quality_score | 异步评估，不影响响应 |
| 修改检索考虑质量 | P1 | 4h | quality_score | 在 SessionStart 时过滤 |
| 经验结构化模板 | P1 | 8h | 无 | LLM prompt 优化 |
| 任务流追踪表 | P2 | 4h | 无 | 数据库层面支持 |

**交付物**:
- V9 migration (session_metrics)
- V11 migration (quality_score)
- QualityScorer 服务
- ExperienceTemplate 工具类
- V10 migration (task_streams, 可选)

### Phase 2: 核心机制（2-3 周）

> **关键**: Refine 在 SessionEnd 异步触发，效果在下次 SessionStart 体现。

| 任务 | 优先级 | 估计工时 | 旁路实现方式 |
|------|--------|---------|-------------|
| MemoryRefineService 框架 | P0 | 16h | @Async 在 SessionEnd 后执行 |
| Refine prompt 模板 | P0 | 8h | LLM 后台调用 |
| 合并/删除/重写逻辑 | P1 | 12h | 数据库批量操作 |
| ExpRAG 检索增强 | P1 | 12h | SessionStart 时优先检索高质量经验 |
| SessionEnd 集成 Refine | P1 | 4h | Hook 触发异步任务 |

**交付物**:
- MemoryRefineService 完整实现
- ExpRagService
- 集成测试

### Phase 3: 高级功能（3-4 周）

> **注意**: ReMem Think/Act/Refine 同步循环无法在旁路架构中实现，此处仅提供 API 供外部集成场景使用。

| 任务 | 优先级 | 估计工时 | 适用场景 |
|------|--------|---------|---------|
| ReMem API（伪同步） | P2 | 24h | 外部 Agent 框架集成 |
| 步骤效率追踪 | P2 | 8h | 性能分析 |
| 多模态记忆支持 | P3 | 20h | 图像/代码记忆 |
| 分布式记忆（可选） | P3 | 16h | 多实例部署 |

**交付物**:
- ReMem Controller
- 性能对比报告

### Phase 4: 评估与优化（持续）

| 任务 | 周期 | 指标 | 旁路适配 |
|------|------|------|---------|
| 与 Evo-Memory benchmark 对比 | 每月 | 成功率、步骤效率 | 跨 Session 评估 |
| 记忆质量分布分析 | 每周 | 平均质量分、低质量比例 | 后台分析任务 |
| Refine 效果评估 | 每月 | 删除率、合并率、检索命中率 | SessionEnd 后统计 |

---

## 8. 旁路架构的适配策略

### 8.1 架构本质差异

**ReMem 的架构假设**（论文中）：

```
ReMem = 智能体核心循环的一部分

用户请求 → [Think → Act ↔ Refine] → 响应
              ↑_______________|
              记忆是同步参与的
```

ReMem 假设你能**直接修改智能体的执行流程**，Refine 是在任务执行过程中同步发生的。

**Claude-Mem 的架构现实**：

```
Claude-Mem = 旁路观察者

用户请求 → Claude Code → 响应
              ↓ Hook
          [事件] → Java 后端 → 异步处理 → 存储
                                      ↓
                              下次 Session 时注入上下文
```

我们是**被动观察者**，无法干预 Claude Code 的执行流程。

### 8.2 旁路适配的核心思路：异步演化 + 延迟生效

| 论文机制 | 旁路适配 | 触发时机 |
|---------|---------|---------|
| **Refine (同步)** | 后台精炼 (异步) | SessionEnd / 定时任务 |
| **Think 中检索记忆** | SessionStart 注入上下文 | 下次会话开始时 |
| **Act 后立即写回** | 异步写入队列 | PostToolUse Hook |

### 8.3 旁路架构的完整数据流

```
┌─────────────────────────────────────────────────────────────┐
│  Session N (正在进行)                                        │
│                                                              │
│  Claude Code ──Hook Events──→ Java Backend (异步)           │
│                                    │                         │
│                                    ├─→ 写入 observation      │
│                                    └─→ 写入 pending queue    │
└─────────────────────────────────────────────────────────────┘
                                     │
                                     ▼ (SessionEnd 触发)
┌─────────────────────────────────────────────────────────────┐
│  后台精炼 (异步)                                              │
│                                                              │
│  1. 生成 Summary                                             │
│  2. 评估 observation 质量分数                                 │
│  3. 触发 Memory Refine (删除低质量/合并相似)                  │
│  4. 更新 CLAUDE.md (可选)                                     │
└─────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────┐
│  Session N+1 (下次会话)                                       │
│                                                              │
│  SessionStart Hook                                           │
│       │                                                      │
│       ├─→ 检索高质量经验 (quality_score >= 0.6)               │
│       ├─→ 生成 Timeline / Summary                            │
│       └─→ 注入到 CLAUDE.md / stdout                          │
│                                                              │
│  Claude Code 现在可以"看到"上次精炼后的记忆                    │
└─────────────────────────────────────────────────────────────┘
```

### 8.4 旁路架构下的改进优先级调整

基于架构现实，我们对改进优先级进行调整：

| 优先级 | 改进方向 | 旁路适配策略 |
|--------|---------|-------------|
| **P0** | 质量评分 | SessionEnd 时异步评估，标记到 observation |
| **P1** | SessionEnd Refine | 在会话结束时触发后台精炼，而非任务执行中 |
| **P1** | 下次会话注入优化 | 在 SessionStart 时注入精炼后的高质量经验 |
| **P2** | 定时全量精炼 | 通过 cron 任务定期对整个项目记忆库进行精炼 |
| **P3** | 跨会话经验复用 | 在 timeline 生成时优先引用高质量经验 |

### 8.5 关键结论

> **对于旁路记忆系统，Refine 的效果是"延迟生效"的：当前会话无法受益，但下次会话可以。**

这意味着：
1. **不要期望当前会话的实时改进** - 我们无法同步干预 Claude Code 的推理
2. **专注于"下次会话"的价值** - 通过精炼让下次会话获得更好的上下文
3. **精炼的时机很重要** - SessionEnd 是最佳触发点，确保下次会话前完成

---

## 附录 A: 代码示例

### A.1 完整的 QualityScorer 实现

```java
package com.ablueforce.cortexce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class QualityScorer {
    
    private final LlmService llmService;
    private final boolean enableLLMEvaluation;
    
    public QualityScorer(LlmService llmService, 
                         @Value("${app.quality.llm-evaluation:false}") boolean enableLLMEvaluation) {
        this.llmService = llmService;
        this.enableLLMEvaluation = enableLLMEvaluation;
    }
    
    /**
     * 评估经验质量
     * 
     * @param feedback 任务反馈类型
     * @param reasoningTrace 推理过程
     * @param output 输出结果
     * @param toolUsageCount 工具使用次数（越少越好）
     * @return 质量分数 [0, 1]
     */
    public float estimateQuality(FeedbackType feedback,
                                  String reasoningTrace,
                                  String output,
                                  int toolUsageCount) {
        // 基础分数来自反馈类型
        float baseScore = switch (feedback) {
            case SUCCESS -> 0.75f;
            case PARTIAL -> 0.50f;
            case FAILURE -> 0.20f;
            case UNKNOWN -> 0.50f;
        };
        
        // 效率调整（工具使用次数）
        float efficiencyBonus = Math.max(0, 0.1f - (toolUsageCount - 3) * 0.02f);
        
        // 内容质量调整（基于长度和结构）
        float contentBonus = evaluateContentQuality(reasoningTrace, output);
        
        float finalScore = baseScore + efficiencyBonus + contentBonus;
        
        // LLM 自评（可选）
        if (enableLLMEvaluation && feedback == FeedbackType.SUCCESS) {
            float llmScore = llmEvaluate(output);
            finalScore = (finalScore * 0.7f) + (llmScore * 0.3f);
        }
        
        return Math.min(1.0f, Math.max(0.0f, finalScore));
    }
    
    private float evaluateContentQuality(String reasoningTrace, String output) {
        float score = 0.0f;
        
        // 推理过程有结构化内容加分
        if (reasoningTrace != null) {
            if (reasoningTrace.contains("<observation>")) score += 0.05f;
            if (reasoningTrace.contains("Facts:")) score += 0.03f;
            if (reasoningTrace.contains("Concepts:")) score += 0.03f;
        }
        
        // 输出长度适中加分
        if (output != null) {
            int len = output.length();
            if (len >= 100 && len <= 500) score += 0.05f;
        }
        
        return Math.min(0.15f, score);
    }
    
    private float llmEvaluate(String output) {
        String prompt = """
            评估以下经验的质量（0-1分）：
            
            %s
            
            评分标准：
            - 0.9-1.0: 非常有价值，通用性强
            - 0.7-0.9: 有价值，可复用
            - 0.5-0.7: 一般，特定场景有用
            - 0.3-0.5: 价值较低
            - 0.0-0.3: 噪音或错误信息
            
            只输出分数数字。
            """.formatted(output);
        
        try {
            String response = llmService.generate(prompt).trim();
            return Float.parseFloat(response);
        } catch (Exception e) {
            log.warn("LLM quality evaluation failed", e);
            return 0.5f;
        }
    }
}
```

### A.2 经验模板

```java
package com.ablueforce.cortexce.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExperienceTemplates {
    
    /**
     * ExpRAG 风格的经验格式
     */
    public static final String EXPRAG_TEMPLATE = """
        ## Previous Experience
        
        **Task**: {task}
        **Approach**: {strategy}
        **Result**: {outcome}
        **When to Reuse**: {reuse_condition}
        
        ---
        """;
    
    /**
     * ReMem Think prompt 模板
     */
    public static final String THINK_PROMPT = """
        You are solving a task. Here are some relevant experiences from memory:
        
        {memories}
        
        Current task: {query}
        
        Previous reasoning:
        {reasoning_trace}
        
        Continue your reasoning. Think about:
        1. What can you learn from the experiences above?
        2. What is the best approach for this task?
        3. What are the potential pitfalls?
        
        Output your reasoning:
        """;
    
    /**
     * Refine prompt 模板
     */
    public static final String REFINE_PROMPT = """
        You are managing a memory system. Here are some memory entries:
        
        {candidates}
        
        Evaluate these memories and decide:
        
        1. Which entries should be DELETED (low quality, outdated, or noisy)?
        2. Which entries should be MERGED (similar content, can be combined)?
        3. Which entries should be REWRITTEN (good content but poor phrasing)?
        
        Output JSON:
        {
          "to_delete": [id1, id2],
          "to_merge": [[id1, id2], [id3, id4]],
          "to_rewrite": [{"id": id, "new_content": "..."}]
        }
        """;
    
    /**
     * 经验格式化（用于 ICL）
     * 
     * 注意: ObservationEntity 的 content 字段存储结构化经验文本，
     * 包含 <task>/<strategy>/<outcome>/<key_learning> 等标签内容。
     * 此方法从 content 中解析提取这些信息。
     */
    public static String formatExperienceForICL(ObservationEntity obs) {
        String content = obs.getContent();
        
        return """
            ### Experience (ID: %s, Quality: %.2f)
            
            **Task**: %s
            **Strategy**: %s
            **Outcome**: %s
            **Reuse Condition**: %s
            
            **Key Learning**: %s
            """.formatted(
                obs.getId(),
                obs.getQualityScore(),
                extractTag(content, "task"),
                extractTag(content, "strategy"),
                extractTag(content, "outcome"),
                extractTag(content, "reuse_condition"),
                extractTag(content, "key_learning")
            );
    }
    
    /**
     * 从结构化 content 中提取指定标签的内容
     */
    private static String extractTag(String content, String tagName) {
        // 匹配 <tag>content</tag> 格式
        Pattern pattern = Pattern.compile(
            "<" + tagName + ">(.*?)</" + tagName + ">", 
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 回退: 查找 "Key Learning:" 格式
        if (tagName.equals("key_learning") && content.contains("Key Learning:")) {
            int start = content.indexOf("Key Learning:");
            String remaining = content.substring(start + "Key Learning:".length());
            return remaining.split("\n")[0].trim();
        }
        return "[未找到]";
    }
}
```

---

## 附录 B: 测试用例

### B.1 QualityScorer 测试

```java
@SpringBootTest
class QualityScorerTest {
    
    @Autowired
    private QualityScorer qualityScorer;
    
    @Test
    void testSuccessFeedback() {
        float score = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS,
            "Some reasoning",
            "Good output",
            3
        );
        
        assertThat(score).isGreaterThanOrEqualTo(0.7f);
        assertThat(score).isLessThanOrEqualTo(1.0f);
    }
    
    @Test
    void testFailureFeedback() {
        float score = qualityScorer.estimateQuality(
            FeedbackType.FAILURE,
            "Some reasoning",
            "Bad output",
            10
        );
        
        assertThat(score).isLessThan(0.5f);
    }
    
    @Test
    void testEfficiencyBonus() {
        float efficientScore = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS, "", "", 2
        );
        
        float inefficientScore = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS, "", "", 10
        );
        
        assertThat(efficientScore).isGreaterThan(inefficientScore);
    }
}
```

### B.2 MemoryRefineService 测试

```java
@SpringBootTest
class MemoryRefineServiceTest {
    
    @Autowired
    private MemoryRefineService refineService;
    
    @Autowired
    private ObservationRepository observationRepo;
    
    @Test
    void testRefineDeletesLowQuality() {
        // 创建低质量 observation
        ObservationEntity lowQuality = new ObservationEntity();
        lowQuality.setContent("Test content");
        lowQuality.setQualityScore(0.1f);
        lowQuality = observationRepo.save(lowQuality);
        
        // 执行 refine
        refineService.refineMemory("test-project");
        
        // 验证被删除
        assertThat(observationRepo.findById(lowQuality.getId())).isEmpty();
    }
    
    @Test
    void testRefineMergesSimilar() {
        // 创建相似 observations
        ObservationEntity obs1 = createObservation("Fix bug in file A", 0.7f);
        ObservationEntity obs2 = createObservation("Fix bug in file A", 0.7f);
        
        // 执行 refine
        refineService.refineMemory("test-project");
        
        // 验证合并
        List<ObservationEntity> remaining = observationRepo.findByContentContaining("Fix bug");
        assertThat(remaining).hasSize(1);
    }
}
```

---

## 附录 C: 配置示例

### C.1 application.yml 新增配置

```yaml
app:
  quality:
    # 是否启用 LLM 质量评估
    llm-evaluation: false

  memory:
    # 是否启用记忆精炼
    refine-enabled: true
    # Refine 触发阈值（记忆数量）
    refine-threshold: 100
    # 每次 refine 处理的最大数量
    refine-batch-size: 10
    # 质量分数阈值（低于此值会被精炼删除）
    quality-threshold: 0.3
    # 过期时间阈值（天）
    days-threshold: 30
    # 相似度阈值（用于合并检测）
    similarity-threshold: 0.85
    # 深度精炼 cron（每天凌晨4点）
    deep-refine-cron: "0 0 4 * * ?"
    # 高频精炼间隔（15分钟，快速清理积压 + 轻量级精炼）
    frequent-interval-ms: 900000

  exprag:
    # ExpRAG 检索数量
    retrieve-count: 4
    # 是否在 session-start 时注入经验
    inject-on-start: true
```

### C.2 Prompt 模板配置

```yaml
prompts:
  observation:
    path: classpath:prompts/observation.txt
    
  # 新增：经验提取 prompt
  experience:
    path: classpath:prompts/experience.txt
    template: |
      从以下工具使用事件中提取结构化经验：
      
      事件: {event}
      
      请输出：
      <experience>
      <task>任务描述</task>
      <strategy>采用的策略</strategy>
      <outcome>结果</outcome>
      <reuse_condition>什么情况下可以复用这个策略</reuse_condition>
      <key_learning>关键学习点</key_learning>
      </experience>
  
  # 新增：Refine prompt
  refine:
    path: classpath:prompts/refine.txt
    template: |
      评估以下记忆条目并决定如何处理...
```

## 附录 D: 数据模型变更（规划）

> **注意**: 以下 Migration 文件和 Java 类为规划内容，尚未实际创建。
> 本文档作为实现指引，具体执行时需按此规划创建相应文件。

### D.1 规划的 Migration 文件

| 文件 | 描述 | 状态 |
|------|------|------|
| `V8__add_observation_content_hash.sql` | 内容哈希去重 | ✅ 已存在 |
| `V9__session_metrics.sql` | 会话级别指标追踪 | 📋 规划中 |
| `V10__task_streams.sql` | 任务流追踪（可选） | 📋 规划中 |
| `V11__observation_quality.sql` | 观察质量评分和访问追踪 | 📋 规划中 |

### D.2 V9: Session 指标字段（规划）

**Migration 文件**: `V9__session_metrics.sql`（待创建）

新增字段:
- `total_observations` (INT) - 观察数量
- `avg_observation_quality` (FLOAT) - 平均质量
- `refine_completed` (BOOLEAN) - 是否完成精炼
- `refined_at` (TIMESTAMP) - 精炼时间
- `tool_call_count` (INT) - 工具调用次数

### D.3 V11: Observation 质量字段（规划）

**Migration 文件**: `V11__observation_quality.sql`（待创建）

> **开关控制**: `app.memory.refine-enabled = false` 时不执行精炼逻辑，所有新增字段均可为空。

新增字段（全部可为空，不影响现有功能）:
- `quality_score` (FLOAT) - 质量分数 [0,1]
- `feedback_type` (VARCHAR(20)) - 反馈类型
- `last_accessed_at` (TIMESTAMP) - 最后访问时间
- `access_count` (INT) - 访问次数
- `refined_at` (TIMESTAMP) - 精炼时间
- `refined_from_ids` (TEXT) - 合并来源（JSON数组）
- `user_comment` (TEXT) - WebUI 用户评论
- `feedback_updated_at` (TIMESTAMP) - 用户反馈更新时间

### D.4 V10: Task Streams 表（可选，规划）

**Migration 文件**: `V10__task_streams.sql`（待创建）

用于追踪任务依赖关系和执行顺序（可选功能）。

### D.5 Java 实体更新（规划）

**ObservationEntity 新增字段** (对应 V11):

```java
// === 新增质量与精炼相关字段 ===

@Column(name = "quality_score")
private Float qualityScore = 0.5f;

@Column(name = "feedback_type")
private String feedbackType = "unknown";

@Column(name = "last_accessed_at")
private OffsetDateTime lastAccessedAt;

@Column(name = "access_count")
private Integer accessCount = 0;

@Column(name = "refined_at")
private OffsetDateTime refinedAt;

@Column(name = "refined_from_ids")
private String refinedFromIds; // JSON array: ["id1", "id2"]

// === 原有字段（参考） ===
// id, session_id, project_path, content, embedding,
// content_hash, concepts, created_at, updated_at
```

**SessionEntity 新增字段** (对应 V9):

```java
@Column(name = "total_observations")
private Integer totalObservations = 0;

@Column(name = "avg_observation_quality")
private Float avgObservationQuality;

@Column(name = "refine_completed")
private Boolean refineCompleted = false;

@Column(name = "refined_at")
private OffsetDateTime refinedAt;

@Column(name = "tool_call_count")
private Integer toolCallCount = 0;
```

**注意**: `ObservationEntity` 的 `content` 字段存储的是结构化的经验文本，
包含 task/strategy/outcome 等信息。代码中如需访问这些结构化内容，
应通过解析 `content` 字段或调用辅助方法（如 `extractFromContent()`）获取，
而非直接访问不存在的 `getTaskInput()` 等方法。

### D.6 新增 Java 类

| 类 | 路径 | 描述 |
|-----|------|------|
| **FeedbackType** | `common/FeedbackType.java` | 反馈类型枚举（位于 `com.ablueforce.cortexce.common` 包） |
| **QualityScorer** | `service/QualityScorer.java` | 质量评分服务 |
| **Experience** | `model/Experience.java` | 结构化经验数据类 |
| **ExpRagService** | `service/ExpRagService.java` | 经验检索增强服务 |
| **MemoryRefineService** | `service/MemoryRefineService.java` | 记忆精炼服务 |

---

## 总结

Evo-Memory 论文为 LLM 记忆系统提供了重要的理论基础和实践指导：

### 核心启示

1. **记忆应该演化，而不是静态存储** - 通过 Refine 机制持续优化记忆质量
2. **经验复用比事实回忆更重要** - 检索策略而非仅仅检索事实
3. **质量评估是关键** - 并非所有经验都值得保留
4. **任务流视角** - 将独立事件组织为有意义的序列

### 旁路架构的关键约束

> **Claude-Mem 是旁路观察者，无法同步干预 Claude Code 的执行。**

这意味着：
- ✅ **可以做**: 异步质量评估、后台记忆精炼、下次会话注入优化上下文
- ❌ **不能做**: 同步 Refine、实时修改推理过程、当前会话即时改进

**核心策略**: **异步演化 + 延迟生效** — 当前会话的精炼效果在下次会话体现。

### 对 Claude-Mem 的建议优先级（适配旁路架构）

| 优先级 | 改进方向 | 旁路实现方式 | 预期收益 |
|--------|---------|-------------|---------|
| **P0** | 添加 quality_score | SessionEnd 异步评估 | 提升检索质量 |
| **P1** | 实现 Refine 机制 | SessionEnd 触发 + 下次 SessionStart 生效 | 减少噪音 |
| **P1** | ExpRAG 检索增强 | SessionStart 注入高质量经验 | 提升经验复用 |
| **P2** | 经验结构化模板 | LLM prompt 优化 | 提升可读性 |
| **P3** | ReMem API | 仅供外部集成 | 扩展性 |

### 预期效果

基于论文实验结果，实施这些改进后预期可以：
- **多轮任务成功率**: 提升 20-40%（从 0.5 → 0.7+）
- **步骤效率**: 减少 30-50% 步骤数
- **记忆质量**: 低质量记忆比例从 ~30% 降至 ~10%

### 架构对比总结

| 维度 | ReMem（论文） | Claude-Mem（旁路适配） |
|------|-------------|----------------------|
| Refine 时机 | 任务执行中同步 | SessionEnd 异步 |
| 效果生效 | 立即 | 下次会话 |
| 实现复杂度 | 需要核心集成 | 仅需后端改动 |
| 适用场景 | 可控 Agent | 旁路观察系统 |

---

*文档作者: Claude (Claude-Mem)*
*最后更新: 2026-03-13*