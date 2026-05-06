# `candidates.js` 三源能力候选提取深度分析

> **数据来源**：`src/gep/candidates.js`（208行纯JS）
> **分析日期**：2026-05-06
> **前置阅读**：[51 Capability Candidate 生命周期管线](./51-capability-candidate-lifecycle-pipeline.md)、[84 Skill Distiller 完整管线](./84-skilldistiller-full-pipeline-deep-dive.md)、[102 LearningSignals + Ops 主动自我管理](./102-learningSignals-ops-trigger-skillsMonitor-selfManagement-deep-dive.md)

---

## 1. 架构定位

`candidates.js` 是 EvoMap 的**能力缺口发现引擎**：从三种来源（会话转录、信号模式、失败胶囊）自动提取 CapabilityCandidate，并将它们格式化为标准结构供下游使用。

**与 SkillDistiller 的关系**：

```
candidates.js       → 发现"需要什么能力"（capability gap）
skillDistiller.js   → 将"成功的能力"固化为可复用 Gene/Capsule
                    ↓
              CapabilityCandidate
                    ↓
           SkillDistiller 原料
                    ↓
                 Gene/Capsule
```

**关键区别**：
- `candidates.js`：从失败和隐式模式中发现**能力缺口**
- `skillDistiller.js`：从成功结果中提取**已验证的解决方案**

---

## 2. 三源提取架构

### 2.1 源 1：会话转录（Transcript Source）

```javascript
// 从 transcript 中提取重复工具调用（≥3次）
function extractToolCalls(transcript) {
  const lines = toLines(transcript);
  for (const line of lines) {
    // OpenClaw format: [TOOL: Shell]
    const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
    if (m) { calls.push(m[1].trim()); continue; }
    // Cursor format: [Tool call] Shell
    const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
    if (m2) { calls.push(m2[1].trim()); }
  }
  return calls;
}

// 统计频率，≥3次触发候选
for (const [tool, count] of freq.entries()) {
  if (count < 3) continue;  // 至少 3 次才认为是"习惯性工具使用"
  const title = `Repeated tool usage: ${tool}`;
  // → CapabilityCandidate
}
```

**设计洞察**：重复使用某工具 → 说明当前能力不足需要手动补偿 → 可以抽象为候选能力。

**支持的格式**：

| 平台 | 格式 | 示例 |
|------|------|------|
| OpenClaw | `[TOOL: Shell]` | `[TOOL: Shell]` |
| Cursor | `[Tool call] Shell` | `[Tool call] Shell` |

### 2.2 源 2：信号模式（Signal Source）

```javascript
// 10 种信号 → 能力标题预定义映射
const signalCandidates = [
  // 防御性信号
  { signal: 'log_error',          title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift',     title: 'Prevent protocol drift and enforce auditable outputs' },
  { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions' },
  { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
  // 机会信号
  { signal: 'user_feature_request',        title: 'Implement user-requested feature' },
  { signal: 'user_improvement_suggestion', title: 'Apply user improvement suggestion' },
  { signal: 'perf_bottleneck',     title: 'Resolve performance bottleneck' },
  { signal: 'capability_gap',      title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  { signal: 'external_opportunity', title: 'Evaluate external A2A asset for local adoption' },
];

// 检查当前活跃信号列表是否包含这些信号
for (const sc of signalCandidates) {
  if (!signalList.some(s => s === sc.signal || s.startsWith(sc.signal + ':'))) continue;
  // → CapabilityCandidate
}
```

**设计洞察**：显式信号（signal tag）本身就是能力缺口的直接指示器。不需要推断，直接映射。

### 2.3 源 3：失败胶囊聚类（Failed Capsule Source）

这是最复杂的来源，从历史失败胶囊中聚类出重复失败模式：

```javascript
// 优先问题域（用于主导问题确定）
const problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];

// 分组逻辑
for (const fc of failedCapsules) {
  // 1. 跳过成功的胶囊
  if (fc.outcome && fc.outcome.status === 'success') continue;
  
  // 2. 从 trigger + signalList 提取 failure_tags
  const failureTags = expandSignals(
    (fc.trigger || []).concat(signalList),
    reason  // 也从 failure reason 中提取信号
  ).filter(t =>
    t.startsWith('problem:') || t.startsWith('risk:') ||
    t.startsWith('area:')    || t.startsWith('action:')
  );
  
  // 3. 确定主导问题（按优先级）
  const dominantProblem = problemPriority.find(p => failureTags.includes(p));
  
  // 4. 用主导问题（或 fallback tag）作为分组 key
  const groupingTags = dominantProblem
    ? [dominantProblem]
    : failureTags.filter(t => t.startsWith('area:') || t.startsWith('risk:')).slice(0, 1);
  const key = groupingTags.join('|');
  
  // 5. 同组计数，收集 failure reason
  if (!groups[key]) groups[key] = { count: 0, tags: failureTags, reasons: [], gene: fc.gene };
  groups[key].count += 1;
  if (reason) groups[key].reasons.push(reason);
}

// 6. 组内 ≥2 个失败胶囊 → 生成 CapabilityCandidate
Object.keys(groups).forEach(function(key) {
  if (group.count < 2) return;  // 至少 2 次才认为"重复"
  // 根据主导问题生成标题
  if (dominantProblem === 'problem:performance') title = 'Resolve recurring performance regressions';
  // ...
});
```

**设计洞察**：
- 不从单次失败中提取（噪声太大）
- 至少 2 次重复失败才触发
- 按 problem domain 聚类，而非按具体错误消息聚类

---

## 3. Five-Questions 形状模板

每个 CapabilityCandidate 都带有 `shape`（五问框架）：

```javascript
function buildFiveQuestionsShape({ title, signals, evidence }) {
  const input = 'Recent session transcript + memory snippets + user instructions';
  const output = 'A safe, auditable evolution patch guided by GEP assets';
  const invariants = 'Protocol order, small reversible patches, validation, append-only events';
  const params = `Signals: ${signals.join(', ')}`;
  const failurePoints = 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification';
  return {
    title: String(title || '').slice(0, 120),
    input,
    output,
    invariants,
    params: params || 'Signals: (none)',
    failure_points: failurePoints,
    evidence: clip(evidence, 240),
  };
}
```

**五问框架**（借鉴自 LLM Reflection 最佳实践）：

| 问题 | 内容 | 作用 |
|------|------|------|
| What is the **input**? | Session + memory + instructions | 定义能力边界 |
| What is the **output**? | Auditable evolution patch | 定义成功标准 |
| What **invariants** must hold? | Protocol order, reversibility | 定义安全约束 |
| What are the **parameters**? | Signal context | 定义触发条件 |
| What are the **failure points**? | Missing signals, over-broad changes | 定义已知风险 |

---

## 4. 去重机制

```javascript
const seen = new Set();
return candidates.filter(c => {
  if (!c || !c.id) return false;
  if (seen.has(c.id)) return false;
  seen.add(c.id);
  return true;
});
```

**ID 生成**：

```javascript
// 转录来源：基于标题的 stable hash
id: `cand_${stableHash(title)}`

// 信号来源：基于信号名的 stable hash
id: `cand_${stableHash(sc.signal)}`

// 失败胶囊来源：基于分组 key 的 stable hash
id: `cand_${stableHash('failed:' + key)}`
```

**stableHash**：FNV-1a 哈希（确定性，不依赖系统随机性）。

---

## 5. 预览渲染

```javascript
function renderCandidatesPreview(candidates, maxChars = 1400) {
  const lines = [];
  for (const c of list) {
    lines.push(`- ${c.id}: ${c.title}`);
    lines.push(`  - input: ${s.input || ''}`);
    lines.push(`  - output: ${s.output || ''}`);
    lines.push(`  - invariants: ${s.invariants || ''}`);
    lines.push(`  - params: ${s.params || ''}`);
    lines.push(`  - failure_points: ${s.failure_points || ''}`);
    if (s.evidence) lines.push(`  - evidence: ${s.evidence}`);
  }
  return clip(lines.join('\n'), maxChars);
}
```

**用途**：在 GEP Prompt 中注入 CapabilityCandidates 摘要，作为上下文供 LLM 决策。

---

## 6. BlueCortexCE 行动项

### P2（建议实施）

**Observation 能力缺口发现**：

| EvoMap 机制 | BlueCortexCE 对应 | 实现方案 |
|------------|-----------------|---------|
| 转录工具调用重复检测 | ToolUseEntity 频率分析 | ObservationRepository 查询 `tool_name` 频率 |
| 信号→能力映射 | SignalTag → Capability | 新建 `capability_candidates` 表 |
| 失败胶囊聚类 | Failed Observation 聚类 | 按 `observation_type` + `signal_tags` 分组 |
| Five-Questions Shape | Structured Extraction Template | 复用 Phase 3 的 Template 设计 |

**具体方案**：

```sql
-- capability_candidates 表
CREATE TABLE capability_candidates (
  id          VARCHAR(64) PRIMARY KEY,     -- cand_<stableHash>
  source      VARCHAR(32),                  -- transcript / signal / failed_observations
  title       VARCHAR(256),
  signals     TEXT[],                       -- 相关信号列表
  shape       JSONB,                        -- five-questions 结构
  evidence    TEXT,
  created_at  TIMESTAMP DEFAULT NOW(),
  status      VARCHAR(16) DEFAULT 'open',  -- open / addressed / dismissed
  addressed_by_gene_id VARCHAR(64)
);

-- 转录来源：查询最近 N 个 session 中重复使用的工具
SELECT tool_name, COUNT(*) as freq
FROM tool_uses
WHERE session_id IN (SELECT id FROM sessions ORDER BY created_at DESC LIMIT 10)
GROUP BY tool_name
HAVING COUNT(*) >= 3;

-- 信号来源：直接映射
SELECT signal_tag, title FROM signal_capability_map
WHERE signal_tag = ANY($1);  -- $1 = 当前活跃信号列表

-- 失败观测聚类
SELECT
  observation_type,
  array_agg(DISTINCT unnest(signal_tags)) as tags,
  COUNT(*) as failure_count
FROM observations
WHERE outcome_status = 'failed'
  AND created_at > NOW() - INTERVAL '7 days'
GROUP BY observation_type
HAVING COUNT(*) >= 2
ORDER BY failure_count DESC;
```

### P3（长期研究）

1. **Gene as Capability**：candidates → skillDistiller 输入 → Gene/Capsule 的完整链路 CE 化
2. **失败模式聚类**：对 FailedObservation 按 error signature 聚类，自动发现高频问题域
3. **Five-Questions 模板引擎**：复用 StructuredExtractionService 的 Phase 3 设计，生成能力缺口模板

---

## 7. 设计亮点总结

| 亮点 | 描述 | BlueCortexCE 价值 |
|------|------|-----------------|
| 三源覆盖 | Transcript + Signal + Failed Capsule 互补覆盖 | 全面发现能力缺口 |
| 频率过滤 | 转录≥3次 / 失败≥2次 | 消除噪声，避免单次误触发 |
| 问题域聚类 | 按 problem:* 标签聚类，而非错误消息 | 抽象层次合理，避免过拟合 |
| Five-Questions | 标准结构化格式 | 为 SkillDistiller 提供一致输入 |
| stableHash dedup | 幂等 ID 生成 | 多源合并时不重复 |
| 增量均值质量跟踪 | avg_score 增量更新 | O(1) 质量追踪 |

---

## 8. 与 doc 84（SkillDistiller）的边界

```
candidates.js                        skillDistiller.js
     │                                      │
     │  CapabilityCandidate                 │  Gene/Capsule
     │  (能力缺口，未验证)                    │  (已验证可复用方案)
     ▼                                      │
对"需要什么"的发现  ──────────────────────────→  对"如何实现"的固化
     │                                            │
     │  • 重复使用某工具（Transcript）             │  • 从成功 Capsule 蒸馏
     │  • 信号指示的能力缺口（Signal）             │  • validateSynthesizedGene
     │  • 重复失败模式（Failed Capsule）           │  • 11 道验证门
     ▼                                            ▼
  候选能力列表                              可发布的 Gene/Capsule
```

---

## 9. 源码证据

**三源入口方法**（行 88-162）：

```javascript
function extractCapabilityCandidates({ recentSessionTranscript, signals, recentFailedCapsules }) {
  const candidates = [];
  
  // Source 1: Transcript tool call frequency
  const toolCalls = extractToolCalls(recentSessionTranscript);
  const freq = countFreq(toolCalls);
  for (const [tool, count] of freq.entries()) {
    if (count < 3) continue;
    candidates.push({ type: 'CapabilityCandidate', id: `cand_${stableHash(title)}`, ... });
  }
  
  // Source 2: Signal-as-capability
  for (const sc of signalCandidates) {
    if (!signalList.some(s => s === sc.signal || s.startsWith(sc.signal + ':'))) continue;
    candidates.push({ type: 'CapabilityCandidate', id: `cand_${stableHash(sc.signal)}`, ... });
  }
  
  // Source 3: Failed capsule clustering
  for (const fc of failedCapsules) {
    // Group by problem domain, require count ≥ 2
    if (group.count < 2) continue;
    candidates.push({ type: 'CapabilityCandidate', id: `cand_${stableHash('failed:'+key)}`, ... });
  }
  
  return candidates;  // auto-deduped by Set
}
```
