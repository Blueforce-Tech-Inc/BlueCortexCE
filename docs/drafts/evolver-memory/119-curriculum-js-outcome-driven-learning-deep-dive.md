# `curriculum.js` Outcome-Driven Curriculum Learning Deep Dive

**Doc**: 119  
**源码**: `EvoMap/evolver/src/gep/curriculum.js` (163L, v1.47.0)  
**日期**: 2026-05-06  
**目标**: 深入分析 Evolver 的 curriculum learning 系统——如何从 outcomes 自动推导学习路径、识别能力缺口、生成课程信号。

---

## 1. 架构定位

`curriculum.js` 是 Evolver 的**课程学习引擎**，实现「能力边界识别 → 课程信号生成 → 进度追踪 → 难度升级」闭环。它是 doc 1（"Memory Through Curriculum"）标题的核心实现。

**核心职责**：
1. 从 `memory_graph.jsonl` 最后 200 条 outcome 事件聚合统计数据
2. 按成功率将 signal_key 分类为：已掌握（mastered）、困难（failing）、前沿（frontier）
3. 生成 `curriculum_target:gap:X` / `curriculum_target:frontier:Y` 信号注入进化
4. 追踪进度，每 5 次成功自动提升课程等级（level 1→5）

**状态文件**：`evolution/curriculum_state.json`

---

## 2. 核心算法

### 2.1 Outcome 聚合

```javascript
function aggregateOutcomes(memoryGraphPath) {
  // 读取 memory_graph.jsonl 最后 200 行
  // 按 signal_key 聚合：{ success, fail, total } 计数
}
```

**设计决策**：
- **仅看最近 200 条**：防止历史数据稀释当前能力评估
- **按 signal_key 聚合**：`signal_key` 作为能力单元的标识符（不是基因 ID）

### 2.2 边界识别（三分类）

```javascript
const MASTERY_THRESHOLD  = 0.8;   // ≥80% 成功率 → mastered
const MASTERY_MIN_ATTEMPTS = 3;   // ≥3 次尝试才计入
const FAILURE_THRESHOLD  = 0.3;   // ≤30% 成功率 → failing

identifyFrontier(outcomes) {
  // mastered: rate ≥ 0.8 AND total ≥ 3
  // failing:  rate ≤ 0.3 AND total ≥ 2
  // frontier: 0.3 < rate < 0.8 (或尝试不足)
  // frontier 按 |rate - 0.5| 升序排列（最不确定的排最前）
}
```

**前沿优先策略**：选择成功率最接近 50% 的技能——这些是最有「学习价值」的，既不是完全掌握也不是完全失败。

### 2.3 课程信号生成

```javascript
function generateCurriculumSignals(opts) {
  // 输入：capabilityGaps[], memoryGraphPath, personality{}
  // 输出：最多 MAX_CURRICULUM_SIGNALS=2 个课程信号

  // 优先级 1：capabilityGaps（来自 selector 的能力缺口）
  if (gap not in mastered) {
    signals.push('curriculum_target:gap:' + gapTarget)
  }

  // 优先级 2：frontier 最优项
  if (signals.length < 2 AND frontier.length > 0) {
    signals.push('curriculum_target:frontier:' + bestFrontier.key)
  }
}
```

**信号格式**：
- `curriculum_target:gap:{capability}` — 来自 selector 的能力缺口（来自 `candidates.js` 的 capability gap 分析）
- `curriculum_target:frontier:{signal_key}` — 当前最不确定的技能

### 2.4 进度追踪与升级

```javascript
function markCurriculumProgress(signal, outcome) {
  // 记录：completed[] 保留最近 50 条（环形缓冲）
  // 每 5 次成功 → level++（上限 level 5）
}

loadCurriculumState() {
  // { level: 1-5, current_targets: [...], completed: [...], updated_at }
}
```

**等级系统**：
| Level | 含义 |
|-------|------|
| 1 | 新手——聚焦基础技能 |
| 5 | 专家——聚焦前沿挑战 |

---

## 3. 与进化主循环的集成

### 3.1 调用点

`generateCurriculumSignals` 在 `evolve.js` 主循环的 **signal 生成阶段** 被调用，其输出与其他信号（learningSignals、idleSignals）合并后注入 GEP prompt。

### 3.2 能力缺口来源

`capabilityGaps` 参数来自 `candidates.js` 的 Three-Source 能力候选提取（Doc 116）：
- Transcript 频率分析（≥3 次重复）
- Failed capsule ban 聚类（≥2 次失败）
- Five-Questions Shape 分析

这意味着 **curriculum 学习直接依赖于 candidates 的能力发现结果**。

### 3.3 反馈回路

```
memory_graph outcome events
    ↓ (aggregateOutcomes)
frontier identification
    ↓ (generateCurriculumSignals)
curriculum signals → GEP prompt
    ↓ (evolve cycle)
new outcomes → memory_graph
    ↓ (markCurriculumProgress)
level up / curriculum_state.json
```

---

## 4. 安全与边界保护

| 边界 | 保护机制 |
|------|----------|
| 状态文件写入 | `writeJsonAtomic` — `.tmp + rename` 原子写 |
| 空值 | `readJsonSafe` 带 fallback 默认状态 |
| 数组越界 | `slice(0, MAX)` 硬截断 |
| JSON 解析失败 | `try/catch` 静默 fallback |
| Level 范围 | `Math.max(1, Math.min(5, ...))` clamp |
| completed 长度 | 环形缓冲，保留最近 50 条 |

---

## 5. BlueCortexCE 行动项

### P3（长期）：观察驱动的课程学习

**核心借鉴**：Evolver 不需要预定义课程——它从真实 outcomes 自动推导学习路径。

**CE 映射提案**：

```sql
-- ObservationEntity 新增字段
ALTER TABLE observations ADD COLUMN outcome_status VARCHAR(16); -- 'success'/'failed'/'unknown'
ALTER TABLE observations ADD COLUMN signal_key VARCHAR(256);       -- 与 Evolver signal_key 对齐
ALTER TABLE observations ADD COLUMN cycle_run_id VARCHAR(64);       -- 关联进化周期
```

```java
// CurriculumService.java（新服务）
public class CurriculumService {
    // 1. 按 signal_key 聚合最近 N 条 outcome
    // 2. 分类：mastered(≥80%, ≥3次) / failing(≤30%) / frontier(中间)
    // 3. 生成 CurriculumSignal → 注入上下文

    // 对应 ObservationEntity 字段
    // outcomeStatus, signalKey, cycleRunId
}
```

**课程信号注入**（P3）：
```java
// ObservationEntity.signalTags 新增 "curriculum_target:gap:{type}"
// 或独立的 CurriculumSignal 注入策略
```

### 对比表

| 特性 | Evolver `curriculum.js` | CE 提案 |
|------|------------------------|---------|
| 学习单元 | `signal_key`（从 outcome 聚合） | `ObservationEntity.signalKey` |
| 缺口来源 | `candidates.js` Three-Source | 失败 Observation 高频 pattern |
| 前沿识别 | `|rate - 0.5|` 最小优先 | 同上（CE 版） |
| 进度追踪 | `curriculum_state.json`（level 1-5） | `CurriculumStateEntity` |
| 反馈回路 | memory_graph → curriculum signal → evolve | Observation → CurriculumSignal → Context |
| 原子写入 | `.tmp + rename` | DB transaction |

---

## 6. 关键设计思想

1. **无需预定义课程**：从真实 outcome 数据自动发现「还没掌握」和「正在学习」的技能
2. **前沿优先（Frontier-First）**：最接近 50% 成功率的技能最有学习价值——完全掌握无需重复，过于困难不适合强制学习
3. **能力缺口外部注入**：`capabilityGaps` 来自 selector 的独立分析，curriculum 只消费不生产
4. **非侵入式集成**：不修改 memory_graph schema，通过追加 `outcome` 事件类型隐式记录
5. **进度持久化**：level 升级驱动后续信号生成的难度调整，形成自适应学习曲线
