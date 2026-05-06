# `mutation.js` 核心深度分析

**doc**: 110  
**源码**: `src/gep/mutation.js`（204L，纯 JS，无外部依赖）  
**上下文**: doc 74（Curriculum + Mutation 闭环管线）已覆盖整体流程；本文专注 `mutation.js` 内部决策逻辑与安全机制。  
**最后更新**: 2026-05-06

---

## 1. 模块定位

`mutation.js` 是 GEP（Gene Evolution Protocol）的**突变构建引擎**，负责：
1. 从信号上下文推导突变类别（repair / optimize / innovate）
2. 构建符合 GEP Schema 的 Mutation 对象
3. 执行**两层安全门禁**（personality-driven）
4. 提供验证与规范化工具函数

**关键特性**：纯函数式设计，无状态，所有决策由输入信号+人格状态驱动。

---

## 2. 导出 API

| 导出 | 签名 | 职责 |
|------|------|------|
| `buildMutation` | `(opts) => Mutation` | 主构建函数 |
| `isValidMutation` | `(obj) => boolean` | 8-field 结构验证 |
| `normalizeMutation` | `(obj) => Mutation` | 幂等规范化 |
| `isHighRiskMutationAllowed` | `(personalityState) => boolean` | 人格门禁 |
| `isHighRiskPersonality` | `(p) => boolean` | 人格风险判断 |
| `hasOpportunitySignal` | `(signals) => boolean` | 机会信号检测 |
| `clamp01` | `(x) => number` | 边界裁剪 |

---

## 3. 信号分类决策树

### 3.1 `hasErrorishSignal(signals)`

```javascript
function hasErrorishSignal(signals) {
  const list = Array.isArray(signals) ? signals.map(s => String(s || '')) : [];
  if (list.includes('issue_already_resolved') || list.includes('openclaw_self_healed')) return false;
  if (list.includes('log_error')) return true;
  if (list.some(s => s.startsWith('errsig:') || s.startsWith('errsig_norm:'))) return true;
  return false;
}
```

**排除逻辑**：`issue_already_resolved` 和 `openclaw_self_healed` 属于"已自愈"信号，**不触发 repair**。

**触发条件**（三选一）：
- `log_error` 关键词命中
- `errsig:*` 原始错误签名
- `errsig_norm:*` 规范化错误签名

### 3.2 `hasOpportunitySignal(signals)`

```javascript
var OPPORTUNITY_SIGNALS = [
  'user_feature_request',
  'user_improvement_suggestion',
  'perf_bottleneck',
  'capability_gap',
  'stable_success_plateau',
  'external_opportunity',
  'issue_already_resolved',   // ← 也出现，与 repair 排除逻辑呼应
  'openclaw_self_healed',      // ← 同上
  'empty_cycle_loop_detected',
];
```

**9 个机会信号**（含 2 个双向信号——既是 repair 排除项也是机会信号）。支持前缀匹配（`signal:detail` 形式）。

### 3.3 `mutationCategoryFromContext({ signals, driftEnabled })`

决策顺序：

```
1. hasErrorishSignal → 'repair'
   ↓ false
2. driftEnabled → 'innovate'
   ↓ false  
3. hasOpportunitySignal → 'innovate'
   ↓ false
4. strategy.innovate ≥ 0.5 → 'innovate'
   ↓ false
5. → 'optimize'
```

**关键点**：机会信号优先于策略预设；策略预设作为最后兜底。

---

## 4. 突变对象结构

```javascript
{
  type: 'Mutation',
  id: `mut_${ts}`,                    // 时间戳 ID
  category: 'repair|optimize|innovate',
  trigger_signals: string[],          // 去重后的触发信号
  target: 'gene:XXX' | 'behavior:protocol',
  expected_effect: string,            // 自然语言预期效果
  risk_level: 'low|medium|high',
}
```

---

## 5. 两层安全门禁

### 5.1 第一层：Personality × Category 交叉

```javascript
const highRiskPersonality = isHighRiskPersonality(personalityState || null);
if (base.category === 'innovate' && highRiskPersonality) {
  base.category = 'optimize';
  base.expected_effect = 'safety downgrade: optimize under high-risk personality ...';
  base.risk_level = 'low';
  base.trigger_signals = uniqStrings([...base.trigger_signals,
    'safety:avoid_innovate_with_high_risk_personality']);
}
```

**高风险人格定义**（满足任一）：
- `rigor < 0.5`（低严谨度）
- `risk_tolerance > 0.6`（高风险偏好）

**效果**：`innovate` → `optimize`，附加安全信号标记。

### 5.2 第二层：High-Risk 突变的人格授权

```javascript
if (base.risk_level === 'high' && !isHighRiskMutationAllowed(personalityState || null)) {
  base.risk_level = 'medium';
  base.trigger_signals = uniqStrings([...base.trigger_signals,
    'safety:downgrade_high_risk']);
}
```

**High-Risk 突变授权条件**（同时满足）：
- `rigor ≥ 0.6`（高严谨度）
- `risk_tolerance ≤ 0.5`（低风险偏好）

**效果**：无授权则 `high` → `medium`，附加 `safety:downgrade_high_risk` 信号。

### 5.3 安全信号汇总

| 安全信号 | 触发条件 |
|---------|---------|
| `safety:avoid_innovate_with_high_risk_personality` | innovate + 高风险人格 |
| `safety:downgrade_high_risk` | 高风险突变未获人格授权 |

---

## 6. 风险等级默认值

| Category | 默认 risk_level | allowHighRisk 升级 |
|---------|----------------|-------------------|
| repair | low | - |
| optimize | low | - |
| innovate | **medium** | → high（需人格授权） |

---

## 7. 验证与规范化

### `isValidMutation(obj)` — 8-field 门禁

```javascript
function isValidMutation(obj) {
  if (!obj || typeof obj !== 'object') return false;
  if (obj.type !== 'Mutation') return false;
  if (!obj.id || typeof obj.id !== 'string') return false;
  if (!obj.category || !['repair','optimize','innovate'].includes(String(obj.category))) return false;
  if (!Array.isArray(obj.trigger_signals)) return false;
  if (!obj.target || typeof obj.target !== 'string') return false;
  if (!obj.expected_effect || typeof obj.expected_effect !== 'string') return false;
  if (!obj.risk_level || !['low','medium','high'].includes(String(obj.risk_level))) return false;
  return true;
}
```

**注意**：不检查 `trigger_signals` 内容，只检查是否为数组。

### `normalizeMutation(obj)` — 幂等规范化

- 缺失字段填充默认值
- `category` 非有效值 → `'optimize'`
- `risk_level` 非有效值 → `'low'`
- `trigger_signals` → `uniqStrings()` 去重

---

## 8. BlueCortexCE 借鉴

### P1: 观察风险分级

CE 当前观察类型缺少 `risk_level` 语义。可以参考：
- `ObservationEntity` 增加 `riskLevel` 字段（low/medium/high）
- 高风险观察（如大量失败、大文件修改）→ `high`
- 由 `AgentService.saveObservation` 在写入时基于信号自动推断

### P1: Safety Signal 机制

CE 的 `ObservationEntity.signalTags` 可以增加两类安全相关标签：
- `safety:downgrade` — 自动降级记录
- `safety:high_risk_blocked` — 被阻止的高风险操作

### P2: 人格驱动的策略调整

CE 的 `ModeService` 可以参考 `isHighRiskPersonality` 逻辑：
- `rigor` 映射为观察置信度阈值
- `risk_tolerance` 映射为注入激进度（innovate vs steady）

---

## 9. 与 doc 74（Curriculum + Mutation 闭环）的关系

doc 74 描述了 mutation 在 GEP 管线中的位置（`curriculum.js` → `mutation.js` → `solidify.js`）。本文补充了 `mutation.js` 内部的决策逻辑。

**doc 74 已覆盖**：
- Mutation 在 GEP 管线中的输入来源（curriculum 输出）
- 5层决策树（信号→类别→风险→目标→预期效果）

**本文新增**：
- `hasErrorishSignal` / `hasOpportunitySignal` 精确逻辑
- 两层安全门禁源码
- `isValidMutation` / `normalizeMutation` 工具函数
- Safety Signal 机制
- BlueCortexCE 具体借鉴方案
