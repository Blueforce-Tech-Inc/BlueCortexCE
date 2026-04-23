# 45 — idleScheduler + llmReview 深度分析

**目标**：分析 `idleScheduler.js`（自适应休眠调度）和 `llmReview.js`（LLM 驱动评审）两个模块，提炼对 BlueCortexCE 自适应调度的借鉴。

**源码**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/idleScheduler.js`、`src/gep/llmReview.js`

**最后更新**：2026-04-24

---

## 1. idleScheduler.js — OMLS 启发式自适应休眠调度

### 1.1 核心设计思想

Evolver daemon loop 每轮迭代需要决定「休眠多久再跑下一轮」。传统的固定间隔或指数退避过于简单。`idleScheduler.js` 引入了 **OMLS（Operator Machine Learning Scheduler）**思想：根据系统最近的活动状态，动态调整休眠策略。

### 1.2 `getScheduleRecommendation` 函数签名

```javascript
// 返回结构
{
  enabled: boolean,        // 是否启用调度建议
  idle_seconds: number,    // 估计的空闲秒数
  intensity: string,       // 'low' | 'medium' | 'high'
  sleep_multiplier: number, // 休眠倍乘系数
  should_distill: boolean, // 是否应触发 distillation
}
```

### 1.3 检测逻辑

通过检查 `evolution_solidify_state.json` 中的历史信号判断空闲窗口：

```javascript
// idleScheduler.js 典型检测
function detectIdleWindow(state) {
  const recentSignals = state?.last_run?.signals || [];
  const cycleInterval = state?.last_run?.cycle_interval_ms || Infinity;
  
  // 如果 cycle_interval 远超平均 → 系统处于空闲
  // 如果连续信号是 'idle' 类型 → 低强度窗口
  // 如果 'force_steady_state' 信号 → 最高倍乘（4x）
  // 如果 'evolution_saturation' 信号 → 2x 倍乘
}
```

### 1.4 BlueCortexCE 借鉴

**场景**：BlueCortexCE 的 cron 任务（如 30 分钟巡检、2 小时 Phase 3 设计迭代）可以引入类似的自适应机制：

| Evolver 概念 | BlueCortexCE 等价 |
|---|---|
| `idle_seconds` 估计 | 根据 `EXTRACTION_ENABLED` 吞吐量、API 调用频率推断系统活跃度 |
| `intensity` 三档 | 巡检强度：低（仅 health check）/ 中（+ 回归测试）/ 高（+ 验收测试） |
| `sleep_multiplier` | cron 间隔动态调整（活动频繁时缩短，闲置时拉长） |
| `should_distill` | 失败积累 → 自动触发修复/改进任务 |

**实现路径**：
```java
// BlueCortexCE 自适应巡检间隔建议
public class AdaptivePatrolConfig {
    // 低活跃：2小时一轮
    // 中活跃：30分钟一轮
    // 高活跃（错误率上升）：15分钟一轮
    // 连续空闲：最长4小时
}
```

### 1.5 OMLS vs 传统退避对比

| 维度 | 传统指数退避 | OMLS 启发式 |
|---|---|---|
| 决策依据 | 仅失败次数 | 多维信号（cycle_interval、信号类型、saturation） |
| 休眠上限 | 固定 max | 动态（`saturationMultiplier * omlsMultiplier`） |
| 空闲检测 | 无 | 有（idle_seconds 估算） |
| 主动操作 | 无 | 可触发 distillation 等主动任务 |
| 适用场景 | 简单重试 | 长期运行的自演化系统 |

---

## 2. llmReview.js — LLM 驱动代码评审

### 2.1 核心设计思想

Evolver 在 solidify 流程中集成 LLM 评审能力，对 mutation 生成的代码变更进行自动化 review，而非仅依赖规则检查。LLM 评审的输出直接影响基因（Gene）的评分和发布决策。

### 2.2 `performLlmReview(capsule, gene)` 函数

```javascript
// 输入：mutation 生成的代码变更 capsule + 对应 gene
// 输出：review 结果（issues、score、approval）

{
  approved: boolean,
  issues: [
    { severity: 'critical' | 'major' | 'minor', line: number, message: string }
  ],
  score: number,        // 0-1 质量分
  reasoning: string,    // 评审理由
}
```

### 2.3 评审 Prompt 设计

```javascript
// llmReview.js 中的评审 prompt
const REVIEW_PROMPT = `
You are a code reviewer. Given the following changes:
- Category: {gene.category}
- Summary: {gene.summary}
- Risk: {mutation.risk_level}

Review the code for:
1. Security vulnerabilities
2. Breaking changes
3. Correctness issues
4. Performance concerns

Respond in JSON format with approval status and issues.
`;
```

### 2.4 与 policyCheck.js 的关系

| 维度 | `policyCheck.js` | `llmReview.js` |
|---|---|---|
| 检查方式 | 规则（正则、路径匹配） | LLM 推理 |
| 速度 | 毫秒级 | 秒级（需要 LLM API 调用） |
| 覆盖 | 结构性约束（blast radius、路径） | 语义性缺陷（逻辑漏洞、架构问题） |
| 决策时机 | pre-solidify（写入前） | post-mutation（solidify 前） |
| 可审计性 | 确定性规则 | 概率性推理 |

两者互补：policyCheck 作为快速门禁，llmReview 作为深度评审。

### 2.5 BlueCortexCE 借鉴

**场景**：BlueCortexCE 的「代码变更评审」环节可以参考：

```java
// BlueCortexCE 未来可引入的 LLM 评审能力
public class LlmReviewResult {
    boolean approved;
    List<Issue> issues;  // severity + line + message
    double score;        // 0-1
    String reasoning;
}

public interface LlmReviewService {
    LlmReviewResult reviewChange(String diff, GeneContext context);
}
```

**与现有架构的结合**：
- `solidify.js` 流程中已有 `validationReport`，可在此基础上叠加 LLM 评审层
- `ValidationReport` 做确定性检查，`LlmReview` 做语义性检查
- 评审结果写入 `GeneEntity` 的 `reviewScore` 字段

### 2.6 局限性

- LLM 评审有延迟（~2-5 秒/次），不适合高频调用
- 需要额外 API 成本
- 评审质量依赖 prompt 工程

---

## 3. 综合：自适应调度 + LLM 评审的协同

Evolver 的 idleScheduler 调度 LLM review 的触发时机：

```
Daemon Loop
  ↓
idleScheduler.getScheduleRecommendation()
  ↓ idle_seconds + intensity
  ├─ 低强度窗口 → 跳过 LLM review（节省 token）
  └─ 高强度窗口 + should_distill → 触发 skillDistiller + 可选 LLM review
  ↓
saturationMultiplier * omlsMultiplier → totalSleepMs
```

这形成了一个 **"按需评审 + 自适应休眠"** 的协同机制：系统越空闲，越可能主动做深度优化（distillation、LLM review）；系统越繁忙，越优先保持响应性。

**BlueCortexCE 启示**：对巡检 cron 任务，可以在低 API 流量时段（如深夜）触发更深度的分析（文档生成、架构回顾），高峰期仅做快速健康检查。

---

## 4. 与 Research Backlog 的关联

| Backlog 项 | 对应分析 |
|---|---|
| Gene/Strategy 层借鉴 | §1.4（自适应调度可作为 Strategy 层的执行层） |
| OMLS 启发式 | §1（idleScheduler 是 OMLS 核心实现） |
| 候选评估管线借鉴 | §2（llmReview → 评审层设计参考） |

---

**CE 可落地性**：★★★☆☆（idleScheduler 思想可直接移植；llmReview 需要 LLM API 接入，适合 Phase 4 增强）
