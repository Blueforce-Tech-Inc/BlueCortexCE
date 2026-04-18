[← 返回索引](./index.md)

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

