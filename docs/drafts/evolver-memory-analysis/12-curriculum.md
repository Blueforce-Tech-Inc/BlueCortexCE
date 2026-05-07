# 12 — 自适应课程学习系统（Curriculum）

## 12.1 整体定位

`curriculum.js` 实现了一个**自适应课程学习系统**，其核心理念来自教育学中的"最近发展区（Zone of Proximal Development）"理论：

> 学习应该在"已经掌握"和"完全不会"之间的**边界区域**进行——既不太简单导致停滞，也不太难导致放弃。

**在 EvoMap 中的体现**：不是所有信号都同等重要。当某个信号模式已经被"掌握"（高成功率）时，系统应该挑战更难的问题；当某个信号模式持续失败时，应该先回到基础。

```
信号 S 的成功率
    1.0 │██████████
        │         \
   0.8  │          ████████ ← mastered（达到 MASTERY_THRESHOLD=0.8）
        │                   \
   0.5  │  frontier         ████████ ← 课程焦点（最接近 0.5 的区域）
        │                   /
   0.3  │  failing         ████████ ← failing（低于 FAILURE_THRESHOLD=0.3）
    0.0 │████████████████████
        └─────────────────────────────────► 经验次数（≥ MASTERY_MIN_ATTEMPTS=3）
```

---

## 12.2 三区划分

```javascript
function identifyFrontier(outcomes) {
  const mastered = [];   // 成功率 ≥ 0.8 且 total ≥ 3
  const failing = [];    // 成功率 ≤ 0.3 且 total ≥ 2
  const frontier = [];  // 其余所有（学习区）

  for (const [key, o] of outcomes) {
    if (o.total < 2) continue;
    const rate = o.success / o.total;

    if (rate >= MASTERY_THRESHOLD && o.total >= MASTERY_MIN_ATTEMPTS) {
      mastered.push({ key, rate, total: o.total });
    } else if (rate <= FAILURE_THRESHOLD && o.total >= 2) {
      failing.push({ key, rate, total: o.total });
    } else {
      frontier.push({ key, rate, total: o.total });
    }
  }

  // frontier 按距 0.5 的距离排序（越近越优先）
  frontier.sort((a, b) => Math.abs(a.rate - 0.5) - Math.abs(b.rate - 0.5));

  return { mastered, failing, frontier };
}
```

---

## 12.3 课程信号生成

```javascript
function generateCurriculumSignals({ capabilityGaps, memoryGraphPath, personality }) {
  const signals = [];
  const MAX_CURRICULUM_SIGNALS = 2;

  // Step 1: 从 memory graph 聚合最近 200 条 outcome 事件
  const outcomes = aggregateOutcomes(memoryGraphPath);
  const analysis = identifyFrontier(outcomes);

  // Step 2: 优先处理能力缺口（如果有）
  if (capabilityGaps.length > 0) {
    const gapTarget = capabilityGaps[0];
    const alreadyMastered = analysis.mastered.some(m => m.key.includes(gapTarget));
    if (!alreadyMastered) {
      signals.push('curriculum_target:gap:' + gapTarget.slice(0, 60));
    }
  }

  // Step 3: 从 frontier 选一个最接近 0.5 的
  if (signals.length < MAX_CURRICULUM_SIGNALS && analysis.frontier.length > 0) {
    const best = analysis.frontier[0];
    if (!signals.some(s => s.includes(best.key))) {
      signals.push('curriculum_target:frontier:' + best.key.slice(0, 60));
    }
  }

  // Step 4: 保存课程状态
  if (signals.length > 0) {
    state.current_targets = signals;
    state.level = Math.max(1, Math.min(5, state.level));
    saveCurriculumState(state);
  }

  return signals;
}
```

**生成的信号格式**：
- `curriculum_target:gap:<gap_signal>` — 针对能力缺口
- `curriculum_target:frontier:<signal_key>` — 针对 frontier 区信号

---

## 12.4 进度追踪

```javascript
function markCurriculumProgress(signal, outcome) {
  const state = loadCurriculumState();

  state.completed.push({ signal, outcome, at: new Date().toISOString() });
  if (state.completed.length > 50) state.completed = state.completed.slice(-50);

  // 每 5 个成功 → 课程等级提升（最高 5 级）
  const successCount = state.completed.filter(c => c.outcome === 'success').length;
  if (successCount > 0 && successCount % 5 === 0 && state.level < 5) {
    state.level++;
  }

  saveCurriculumState(state);
}
```

---

## 12.5 课程状态持久化

```javascript
// curriculum_state.json
{
  "level": 2,           // 当前课程等级（1-5）
  "current_targets": [  // 当前聚焦的信号
    "curriculum_target:frontier:log_error|recurring_error"
  ],
  "completed": [        // 最近 50 次记录
    { "signal": "...", "outcome": "success", "at": "..." },
    ...
  ],
  "updated_at": "..."
}
```

---

## 12.6 与 Gene 选择器的集成

课程信号作为**额外信号输入**注入到基因选择流程：

```
selector.js 中的 selectGeneAndCapsule()
       │
       ├── 从 signals.js 获取正常信号
       ├── 从 curriculum.js 获取课程信号 ← 注入
       └── 合并后输入 getMemoryAdvice()
```

课程信号在 selector 中的作用：
- `curriculum_target:gap:X` → 强制选择能处理 X 的基因
- `curriculum_target:frontier:X` → 倾向于选择 X 相关的基因

---

## 12.7 与 Claude-Mem 的类比

| EvoMap Curriculum | Claude-Mem 对应机制 |
|------------------|-------------------|
| 三区划分（mastered/failing/frontier） | 无对应 |
| 自适应课程信号注入 | 无对应 |
| 难度分级（level 1-5） | 无对应 |
| 成功率统计（按 signal_key） | 无对应（Claude-Mem 按 session/observation 聚合） |
| 课程进度追踪（completed[]） | Session summary 历史 |
| 能力缺口检测（coverage_gaps） | 无对应 |

**本质区别**：Claude-Mem 是被动的记忆检索系统，没有"主动选择学习内容"的概念。EvoMap 的 Curriculum 是一个主动的学习控制器。

---

_Next: [13-safety-ops.md](./13-safety-ops.md) — 安全、并发与运维保障_
