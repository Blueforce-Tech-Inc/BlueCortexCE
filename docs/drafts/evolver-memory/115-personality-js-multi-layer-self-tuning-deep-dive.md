# `personality.js` 多层自我调优系统深度分析

> **数据来源**：`src/gep/personality.js`（379行纯JS）
> **分析日期**：2026-05-06
> **前置阅读**：[44 Personality State Machine + Hub Search](./44-personality-state-machine-and-hub-search-caching.md)、[24 Gene/Strategy Layer](./24-gene-strategy-layer.md)、[62 核心设计模式](./62-evolver-core-design-patterns-and-ce-translation.md)

---

## 1. 架构定位

`personality.js` 实现了 EvoMap 的**人格自我调优系统**：通过记录历史 PersonalityState 的 outcome，对人格参数进行持续自适应调整。它是 **GEP 闭环的第五个维度**（Signal → Gene → Capsule → Outcome → Personality），确保进化策略不仅在基因层自适应，也在人格层自适应。

**与其他模块的关系**：

```
mutation.js ──→ proposeMutations() 使用 personality state 作为约束输入
                （rigor 高时禁止 high-risk innovation）
                
strategy.js ──→ 预设影响 mutation 类别的权重分布
                （steady-state 压制 innovate）

personality.js ←─ evolve.js 每轮调用 selectPersonalityForRun()
                ←─ solidify.js 成功后调用 updatePersonalityStats()

reflection.js ──→ suggested_mutations 注入到 personality 调整
```

---

## 2. PersonalityState 数据模型

### 2.1 五维状态空间

```javascript
{
  type: 'PersonalityState',
  rigor: 0.7,           // 协议严格性（0=随意, 1=完美主义）
  creativity: 0.35,      // 探索/创新倾向（0=保守, 1=激进）
  verbosity: 0.25,      // 输出详细程度（0=简洁, 1=冗长）
  risk_tolerance: 0.4,   // 风险承受度（0=规避, 1=大胆）
  obedience: 0.85,       // 对协议/规范的遵循度（0=叛逆, 1=服从）
}
```

### 2.2 默认保守状态

```javascript
function defaultPersonalityState() {
  return {
    rigor: 0.7,
    creativity: 0.35,     // 默认偏低（保守）
    verbosity: 0.25,
    risk_tolerance: 0.4,   // 默认偏低
    obedience: 0.85,       // 默认偏高（协议优先）
  };
}
```

**设计意图**：初始化时倾向保守、协议优先、安全低风险。

### 2.3 状态量化（人格键）

```javascript
function personalityKey(state) {
  const step = 0.1;  // 量化到 0.1 步长
  // rigor=0.73 → 0.7, creativity=0.35 → 0.3, ...
  return `rigor=0.7|creativity=0.3|verbosity=0.2|risk_tolerance=0.4|obedience=0.9`;
}
```

**作用**：将连续五维空间离散化到桶（bucket），便于统计学习和历史追踪。

---

## 3. 持久化模型

### 3.1 文件结构

```
evolution/personality_state.json
```

```json
{
  "version": 1,
  "current": { type, rigor, creativity, verbosity, risk_tolerance, obedience },
  "stats": {
    "rigor=0.7|creativity=0.3|...": { success: 5, fail: 2, avg_score: 0.72, n: 7 },
    ...
  },
  "history": [
    { at: "2026-05-06 01:00:00", key: "rigor=0.7|...", outcome: "success", score: 0.8, notes: null },
    ...
  ],
  "updated_at": "2026-05-06T01:00:00Z"
}
```

### 3.2 原子写入

```javascript
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, filePath);  // rename is atomic on POSIX
}
```

**重要**：使用 tmp+rename 实现原子写，防止读取时文件损坏。

### 3.3 历史裁剪

```javascript
model.history = model.history.slice(-120);  // 仅保留最近 120 条记录
```

---

## 4. 三层突变机制（核心创新）

`selectPersonalityForRun()` 是核心方法，按优先级依次应用三层突变：

### Layer 1: Natural Selection（自然选择）

**触发时机**：始终执行（除非 stats 不足）。

```javascript
// 从历史 stats 中找到历史上得分最高的人格配置
const best = chooseBestKnownPersonality(stats);  // 需要 ≥3 次记录

if (best) {
  const diffs = getParamDeltas(current, bestState)  // 当前 → 最佳的差值
    .filter(d => Math.abs(d.delta) >= 0.05);       // 忽略微小差值
  // 最多调整 2 个参数，每次最多 ±0.1
  for (const d of diffs.slice(0, 2)) {
    const clipped = Math.max(-0.1, Math.min(0.1, d.delta));
    apply({ param: d.param, delta: clipped, reason: 'natural_selection' });
  }
}
```

**设计思想**：不是完全切换到历史最佳，而是**小步靠近**（最多2参数×±0.1），防止过拟合到单一历史配置。

**与生物进化的类比**：自然选择 = 适者生存 → 统计上更成功的人格配置获得更高的"后代权重"。

### Layer 2: Triggered Mutation（触发式突变）

**触发条件**（任一满足）：

| 条件 | 调整方向 | 理由 |
|------|---------|------|
| `driftEnabled=true` | creativity +0.1, risk_tolerance -0.05 | 漂移启用时提升探索但控制风险 |
| `protocol_drift` 信号 | obedience +0.1, rigor +0.05 | 协议漂移时强化服从性 |
| `log_error` 或 `errsig:*` 信号 | rigor +0.1, risk_tolerance -0.1 | 错误时提升严谨、降低风险 |
| `hasOpportunitySignal()` | creativity +0.1, risk_tolerance +0.05 | 机会信号时提升创造力和风险承受 |
| 其他（默认 plateau 模式） | creativity +0.05, verbosity -0.05 | 突破局部最优，增加探索 |

**特殊处理**：

```javascript
// 如果 obedience 已经 ≥0.95，不再提升，改为提升 creativity
if (s.obedience >= 0.95) {
  muts[idx] = { param: 'creativity', delta: +0.05 };
}
```

**设计思想**：Rule-based triggered response，基于当前信号上下文做即时调整。

### Layer 3: Reflection-Driven Mutation（反思驱动突变）

**触发条件**：`reflection.js` 中 `suggested_mutations` 非空。

```javascript
const recent = loadRecentReflections(1);
if (recent.length > 0 && recent[0].suggested_mutations) {
  // 最多追加 (4 - 已应用数) 条 reflection 驱动的突变
  const refMuts = recent[0].suggested_mutations
    .slice(0, 4 - totalApplied)
    .map(m => ({
      param: m.param,
      delta: Math.max(-0.1, Math.min(0.1, Number(m.delta))),
      reason: m.reason
    }));
  apply(refMuts);
}
```

**设计思想**：利用 LLM 生成的战略复盘建议（reflection）作为人格调整的高层输入。

### 总上限：每轮最多 4 个参数变更

```javascript
let totalApplied = naturalSelectionApplied.length + triggeredApplied.length;
// reflection 只能在还有"空间"时追加
if (totalApplied < 4) { /* apply reflection mutations */ }
```

---

## 5. 人格评分算法

### 5.1 `personalityScore()` 公式

```javascript
function personalityScore(statsEntry) {
  const succ = Number(e.success) || 0;
  const fail = Number(e.fail) || 0;
  const total = succ + fail;
  const p = (succ + 1) / (total + 2);         // Laplace 平滑成功率
  const sampleWeight = Math.min(1, total / 8); // 小样本惩罚
  const q = avg == null ? 0.5 : clamp01(avg);   // 质量因子
  return p * 0.75 + q * 0.25 * sampleWeight;    // 加权组合
}
```

**解读**：
- 成功率权重 75%，质量评分权重 25%
- 样本量 < 8 时，quality 因子被稀释（防止小样本过拟合）
- Laplace 平滑 (α=1) 防止零成功率的零概率问题

### 5.2 `chooseBestKnownPersonality()` 选择最佳历史配置

```javascript
function chooseBestKnownPersonality(statsByKey) {
  let best = null;
  for (const [k, entry] of Object.entries(stats)) {
    const total = (Number(e.success) || 0) + (Number(e.fail) || 0);
    if (total < 3) continue;  // 至少 3 次观测才参与选择
    const sc = personalityScore(e);
    if (!best || sc > best.score) best = { key: k, score: sc, entry: e };
  }
  return best;  // 返回 { key, score, entry }
}
```

**设计巧思**：选择历史最佳配置而不是当前配置，确保有足够统计意义才切换。

---

## 6. Stats 更新与历史记录

### 6.1 `updatePersonalityStats()`

```javascript
function updatePersonalityStats({ personalityState, outcome, score, notes }) {
  const key = personalityKey(st);  // 量化后的桶 key
  // 更新 stats[key]
  if (outcome === 'success') cur.success++;
  else if (outcome === 'failed') cur.fail++;
  // 增量均值更新 avg_score
  cur.avg_score = prev + (sc - prev) / n;
  // 追加历史记录
  model.history.push({ at, key, outcome, score, notes });
  savePersonalityModel(model);
}
```

### 6.2 增量均值算法

```javascript
// prev_avg + (new - prev) / n 避免重新求和
cur.avg_score = prev + (sc - prev) / n;
```

**意义**：O(1) 时间复杂度更新均值，适合高频调用。

---

## 7. 突变约束

### 7.1 每次突变量上限

```javascript
// applyPersonalityMutations 内部
const clipped = Math.max(-0.2, Math.min(0.2, delta));  // 单次 ±0.2
```

### 7.2 每轮参数变更上限

```javascript
let count = 0;
for (const m of muts) {
  // ...
  count += 1;
  if (count >= 2) break;  // 最多变更 2 个参数
}
```

**两层约束**：单次突变量 ±0.2 + 每轮参数数 ≤ 2 → 防止单轮人格剧烈跳变。

---

## 8. 与 mutation.js 的联动

`personality.js` 的输出（personality_state）影响 `mutation.js` 的决策：

```javascript
// mutation.js 中的安全门禁 Layer 1
if (high_risk_personality) {
  // (rigor < 0.5 OR risk_tolerance > 0.6) → innovate downgrade
  newCategory = 'optimize';
  safetySignals.push('safety:avoid_innovate_with_high_risk_personality');
}
```

**双向联动**：
- Personality → Mutation：人格状态约束 mutation 类别选择
- Outcome → Personality：mutation 结果反馈到 personality stats

---

## 9. BlueCortexCE 行动项

### P2（建议实施）

**ModeService 人格状态引入**：

| BlueCortexCE 概念 | EvoMap 对应 | 实现方案 |
|------------------|-----------|---------|
| ModeService 模式切换 | `selectPersonalityForRun()` | 在 ModeService 中引入 5 维状态 |
| Session 结果反馈 | `updatePersonalityStats()` | ObservationEntity 增加 `mode_outcome` 字段 |
| 自然选择 | `chooseBestKnownPersonality()` | 基于历史最优 Mode 配置做小步调整 |
| 规则触发调整 | `proposeMutations()` | 错误信号 → rigor↑ risk↓; 机会信号 → creativity↑ |

**具体实现路径**：

```java
// ModeService.java 新增接口
public class PersonalityState {
    private double rigor;          // 0.0-1.0
    private double creativity;     // 0.0-1.0
    private double verbosity;      // 0.0-1.0
    private double riskTolerance;  // 0.0-1.0
    private double obedience;      // 0.0-1.0
}

// 在 generateContext 时注入 personality_state
// 策略：high_rigor → 强制 JSON schema enforcement
//       high_creativity → 宽松 observation 过滤
//       high_risk_tolerance → 允许更多实验性提取
```

### P3（长期研究）

1. **人格-注入策略映射表**：将 5 维状态映射到具体的 prompt 注入策略
2. **Mode 统计面板**：WebUI 增加 Mode 历史成功率统计
3. **Reflection 驱动的 Mode 调整**：从 SessionSummary 中提取 suggested_mode_mutations

---

## 10. 设计亮点总结

| 亮点 | 描述 | BlueCortexCE 价值 |
|------|------|-----------------|
| 三层突变优先级 | Natural Selection → Triggered → Reflection | 可复用到 Mode 调优 |
| 离散化桶设计 | 连续 5 维空间量化到 0.1 步长桶 | 统计学习的基础 |
| 小步快跑 | 每轮最多 2 参数 × ±0.2 | 防人格跳变，保持稳定性 |
| Laplace 平滑 | (succ+1)/(total+2) | 小样本零成功率防零概率 |
| 增量均值 | prev + (new-prev)/n O(1) 更新 | 高频 stats 更新无压力 |
| 三层联动 | Personality ↔ Mutation ↔ Outcome | 完整反馈闭环 |

---

## 11. 源码证据

**核心选择方法签名**（行 217-257）：

```javascript
// selectPersonalityForRun: 三层突变入口
function selectPersonalityForRun({ driftEnabled, signals, recentEvents } = {}) {
  const model = loadPersonalityModel();
  const base = normalizePersonalityState(model.current);
  
  // Layer 1: Natural Selection toward best known
  if (best && best.key) {
    const applied = applyPersonalityMutations(base, muts);
    model.current = applied.state;
    naturalSelectionApplied = applied.applied;
  }
  
  // Layer 2: Rule-based triggered mutation
  const trig = shouldTriggerPersonalityMutation({ driftEnabled, recentEvents });
  if (trig.ok) {
    const props = proposeMutations({ baseState: model.current, ... });
    const applied = applyPersonalityMutations(model.current, props);
    model.current = applied.state;
    triggeredApplied = applied.applied;
  }
  
  // Layer 3: Reflection-driven
  if (totalApplied < 4) {
    const recent = loadRecentReflections(1);
    // apply reflection suggested_mutations
  }
  
  return { personality_state, personality_mutations, model_meta };
}
```

**人格评分公式**（行 95-103）：

```javascript
const p = (succ + 1) / (total + 2);              // Laplace
const sampleWeight = Math.min(1, total / 8);      // 小样本惩罚
return p * 0.75 + avg_score * 0.25 * sampleWeight; // 加权组合
```
