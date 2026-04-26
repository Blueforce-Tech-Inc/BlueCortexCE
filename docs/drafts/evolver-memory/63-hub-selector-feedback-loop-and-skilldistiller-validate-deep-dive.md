# Doc 63: Hub-Selector 反馈闭环 + SkillDistiller validateSynthesizedGene 深度分析

> **来源**：`EvoMap/evolver/src/gep/a2aProtocol.js`（`_latestCapabilityGaps`/`_latestNoveltyHint` 解析）、`src/evolve.js`（heartbeat 解析 + selector 调用链）、`src/gep/selector.js`（diversity-directed drift）、`src/gep/skillDistiller.js`（`validateSynthesizedGene`）  
> **前置**：[`30`](./30-multifactor-gene-selection-continuous-drift.md)（多因子漂移机制）、[`46`](./46-hub-ecosystem-integration-taskreview-issue.md)（Hub Ecosystem）、[`51`](./51-capability-candidate-lifecycle-pipeline.md)（问题生成六策略）  
> **最后更新**：2026-04-25

---

## 1. Hub-Selector 反馈闭环（完整闭环链路）

### 1.1 架构总览

这是 Evolver 最核心的**跨网络反馈机制**——Hub 不仅是任务市场，更是**网络级智能协调器**，通过心跳将"全局知识状态"注入每个 Agent 的基因选择过程：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EvoMap Hub (网络级)                             │
│  ┌──────────────────┐  ┌──────────────────────┐  ┌───────────────────┐  │
│  │  Agent 目录注册    │  │  跨 Agent 能力图谱    │  │  全局新颖性检测     │  │
│  │  (sendHelloToHub)│  │  (capability_gaps[]) │  │  (novelty hint)   │  │
│  └────────┬─────────┘  └──────────┬───────────┘  └─────────┬─────────┘  │
└───────────┼───────────────────────┼────────────────────────┼─────────────┘
            │ hello response         │ heartbeat response      │
            ▼                        ▼                         ▼
    ┌───────────────┐    ┌──────────────────────┐  ┌─────────────────┐
    │ _nodeId 持久化  │    │ _latestCapabilityGaps│  │_latestNoveltyHint│
    │ _nodeSecret    │    │ (string[])            │  │(object)         │
    └───────────────┘    └──────────┬───────────┘  └────────┬────────┘
                                     │                        │
                    ┌────────────────┴────────────────────────┘
                    │ getCapabilityGaps() / getNoveltyHint()
                    ▼
    ┌──────────────────────────────────────────────────────────┐
    │                   EvoMap Agent (本地)                     │
    │  ┌─────────────────┐  ┌──────────────────────────────┐  │
    │  │ Curriculum       │  │  Gene Selection (selector.js)│  │
    │  │ generateCurriculum│  │  selectGeneAndCapsule()       │  │
    │  │ Signals          │  │  ┌──────────────────────────┐│  │
    │  │ (earlyCapGaps)   │  │  │ diversity_directed drift ││  │
    │  └─────────────────┘  │  │  capabilityGaps → 候选   ││  │
    │                        │  │  noveltyScore → 探索范围 ││  │
    │                        │  └──────────────────────────┘│  │
    │                        └──────────────────────────────┘  │
    └──────────────────────────────────────────────────────────┘
```

### 1.2 Step-by-Step 链路

#### Step 1: Agent 注册 / 心跳请求（Agent → Hub）

```javascript
// evolve.js — Hub 心跳调用
async function hubHeartbeat() {
  const result = await sendHelloToHub({
    nodeId: getNodeId(),
    protocol_version: PROTOCOL_VERSION,
    // ... 包含当前状态摘要
  });
  return result;
}
```

`sendHelloToHub` 同时承担**注册**和**心跳**双重职责（取决于是否已注册）。

#### Step 2: Hub 响应（Hub → Agent）

Hub 返回的 JSON 包含：

| 字段 | 类型 | 含义 |
|------|------|------|
| `novelty` | `object` | 全局新颖性检测结果 |
| `novelty.score` | `number` | Agent 与网络其他成员的相似度（低 = 太相似，需要探索） |
| `capability_gaps` | `string[]` | 网络中尚未被充分覆盖的能力领域 |
| `available_work` | `object[]` | 可认领的任务 |
| `overdue_tasks` | `object[]` | 逾期任务警告 |
| `skill_store` | `object` | 技能市场提示 |
| `circle_experience` | `object` | 进化圈体验数据 |
| `has_pending_events` | `boolean` | 是否有待拉取的事件 |

#### Step 3: 本地缓存解析（a2aProtocol.js）

```javascript
// a2aProtocol.js — heartbeat 响应解析
// _processHelloResponse() 中：
if (data.novelty && typeof data.novelty === 'object') {
  _latestNoveltyHint = data.novelty;         // 存储到模块变量
}
if (Array.isArray(data.capability_gaps) && data.capability_gaps.length > 0) {
  _latestCapabilityGaps = data.capability_gaps;  // 存储到模块变量
}
```

**关键设计**：`novelty` 和 `capability_gaps` 都存储在**模块级变量**（闭包内），不持久化到文件。Agent 重启后这些数据丢失，但心跳会立即重新获取。

#### Step 4a: Curriculum 信号注入（早期影响）

```javascript
// evolve.js — 在选择基因之前，先用 capabilityGaps 生成课程信号
var earlyCapGaps = [];
try {
  const { getCapabilityGaps } = require('./gep/a2aProtocol');
  earlyCapGaps = getCapabilityGaps() || [];
} catch (_) {}

var curriculumSignals = generateCurriculumSignals({
  capabilityGaps: earlyCapGaps,
  memoryGraphPath: memGraphPath,
  personality: {},
});

// 将课程信号注入当前 signals 列表
for (var ci = 0; ci < curriculumSignals.length; ci++) {
  if (!signals.includes(curriculumSignals[ci])) {
    signals.push(curriculumSignals[ci]);
  }
}
```

**效果**：Hub 报告的能力缺口通过课程系统转化为信号，注入 gene 选择流程。

#### Step 4b: 基因选择直接使用（晚期影响）

```javascript
// evolve.js — gene 选择时直接传入 Hub 数据
var heartbeatNovelty = null;
var heartbeatCapGaps = [];
try {
  const { getNoveltyHint, getCapabilityGaps } = require('./gep/a2aProtocol');
  heartbeatNovelty = getNoveltyHint();
  heartbeatCapGaps = getCapGaps() || [];
} catch (e) {}

const { selectedGene, capsuleCandidates, selector } = selectGeneAndCapsule({
  genes,
  capsules,
  signals,
  memoryAdvice,
  driftEnabled: IS_RANDOM_DRIFT,
  failedCapsules: recentFailedCapsules,
  capabilityGaps: heartbeatCapGaps,
  noveltyScore: heartbeatNovelty && Number.isFinite(heartbeatNovelty.score)
                  ? heartbeatNovelty.score : null,
});
```

### 1.3 `noveltyScore` 在选择器中的作用

`noveltyScore` 来自 `novelty.score`，代表 Hub 评估的**该 Agent 与整个网络成员的相似程度**：

```javascript
// selector.js — random_weighted drift 中
if (noveltyScore != null && noveltyScore < 0.3 && topN < filtered.length) {
  topN = Math.min(filtered.length, topN + 1);  // 扩大随机范围，增加探索
}
```

| noveltyScore 区间 | Agent 状态 | selector 行为 |
|-----------------|-----------|-------------|
| `< 0.3` | 与网络成员高度相似，缺乏多样性 | 扩大 topN +1，鼓励随机探索 |
| `0.3–0.7` | 中等多样性 | 正常漂移行为 |
| `> 0.7` | 高度独特，多样性充足 | 缩小探索范围，偏向利用 |

### 1.4 `capabilityGaps` 在选择器中的作用

`capabilityGaps` 是 Hub 报告的**全局能力缺口数组**（字符串列表，如 `["error_recovery", "file_parsing"]`）：

```javascript
// selector.js — diversity_directed drift 模式
if (driftIntensity > 0 && filtered.length > 1 && Math.random() < driftIntensity) {
  if (capabilityGaps.length > 0) {
    // 对每个候选基因，计算其覆盖的能力缺口数量
    const gapScores = filtered.map((entry, idx) => {
      const g = entry.gene;
      const patterns = Array.isArray(g.signals_match) ? g.signals_match : [];
      let gapHits = 0;
      for (let gi = 0; gi < capabilityGaps.length && gi < 5; gi++) {
        const gapSignal = capabilityGaps[gi];
        // 用 matchPatternToSignals 判断该基因的 signals_match 是否能覆盖该缺口
        if (typeof gapSignal === 'string' &&
            patterns.some(p => matchPatternToSignals(p, [gapSignal]))) {
          gapHits++;
        }
      }
      return { idx, gapHits, baseScore: entry.score };
    });

    const hasGapHits = gapScores.some(gs => gs.gapHits > 0);
    if (hasGapHits) {
      // 优先选择覆盖最多缺口的基因（drift 模式）
      gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
      selectedIdx = gapScores[0].idx;
      driftMode = 'diversity_directed';
    } else {
      // 无基因能覆盖缺口 → fallback 到 novelty 加权随机
      driftMode = 'random_weighted';
    }
  }
}
```

**三种漂移模式对比**：

| 模式 | 触发条件 | 行为 |
|------|---------|------|
| `diversity_directed` | `driftIntensity > 0` + `capabilityGaps.length > 0` + 有基因覆盖缺口 | 按 gap 覆盖度排序选最优 |
| `random_weighted` | `diversity_directed` 无法触发 + 有 `noveltyScore` | 在 top-N 随机（范围受 noveltyScore 调节） |
| `random` | 无 capabilityGaps | 纯 top-N 随机 |
| `memory_preferred` | `preferredGeneId` 有效 | memory graph 偏好基因优先 |
| `selection` | 以上都不触发 | 按评分排序（纯利用） |

### 1.5 完整调用时序图

```
Agent 启动
    │
    │ sendHelloToHub()
    ▼
Hub 注册 / 心跳 ─────────────────────────────────────────┐
    │                                                   │
    │ ← { novelty: {score: 0.25},                       │
    │     capability_gaps: ["error_recovery", "..."] }  │
    ▼                                                   │
a2aProtocol._processHelloResponse()                      │
    │                                                   │
    ├─ _latestNoveltyHint = {score: 0.25}               │
    └─ _latestCapabilityGaps = ["error_recovery", ...]  │
    │                                                   │
    │ getCapabilityGaps() / getNoveltyHint() ────────────┘
    ▼
┌─────────────────────────────────────────────────────┐
│  evolve.js 主循环                                    │
│                                                      │
│  ① earlyCapGaps = getCapabilityGaps()               │
│     → generateCurriculumSignals(earlyCapGaps)        │
│     → 注入课程信号到 signals[]                       │
│                                                      │
│  ② heartbeatCapGaps  = getCapabilityGaps()          │
│     heartbeatNovelty  = getNoveltyHint()             │
│     → selectGeneAndCapsule(                          │
│           capabilityGaps: heartbeatCapGaps,           │
│           noveltyScore: novelty.score)               │
│       └─ computeDriftIntensity()                     │
│       └─ selectGene()                                │
│           └─ diversity_directed / random_weighted     │
└─────────────────────────────────────────────────────┘
```

---

## 2. `validateSynthesizedGene` 源码级深度（skillDistiller.js）

### 2.1 函数签名与职责

```javascript
// skillDistiller.js line 383
function validateSynthesizedGene(gene, existingGenes) { ... }
```

**职责**：验证 LLM 生成的合成基因（synthesized gene）是否满足**可接受性标准**，防止低质量/危险基因进入基因池。

### 2.2 验证清单（5 大类）

```javascript
function validateSynthesizedGene(gene, existingGenes) {
  const errors = [];

  // ── 1. 结构完整性 ─────────────────────────────────────
  if (!gene || typeof gene !== 'object') {
    errors.push('gene must be a non-null object'); return errors;
  }
  if (!gene.id || typeof gene.id !== 'string') errors.push('missing or invalid id');
  if (!Array.isArray(gene.signals_match)) errors.push('signals_match must be array');
  if (!Array.isArray(gene.strategy)) errors.push('strategy must be array');

  // ── 2. ID 唯一性 ──────────────────────────────────────
  const existingIds = new Set(existingGenes.map(g => g.id));
  if (gene.id && existingIds.has(gene.id)) {
    errors.push('duplicate gene id: ' + gene.id);
  }

  // ── 3. signals_match 去重 + 防覆盖 ───────────────────
  const newSet = new Set(gene.signals_match.map(s => String(s).toLowerCase()));
  for (const eg of existingGenes) {
    const egSet = new Set((eg.signals_match || []).map(s => String(s).toLowerCase()));
    const overlap = [...newSet].filter(s => egSet.has(s)).length;
    // 如果与现有基因完全相同 → 拒绝
    if (overlap === newSet.size && newSet.size > 0) {
      errors.push('identical signals_match to existing gene: ' + eg.id);
    }
    // 如果高度重叠（>70%）→ 警告
    if (overlap > newSet.size * 0.7 && newSet.size > 2) {
      errors.push('excessive overlap (' + overlap + '/' + newSet.size
                  + ') with existing gene: ' + eg.id);
    }
  }

  // ── 4. 安全性约束 ────────────────────────────────────
  if (!Array.isArray(gene.constraints)) {
    errors.push('constraints must be an array');
  }
  if (!gene.constraints.forbidden_paths) {
    errors.push('missing constraints.forbidden_paths');
  } else {
    // 必须包含 .git 和 node_modules 的保护
    if (!gene.constraints.forbidden_paths.some(p =>
        p === '.git' || p === 'node_modules')) {
      errors.push('constraints.forbidden_paths must include .git and node_modules');
    }
    // 检查危险 patterns
    for (const path of gene.constraints.forbidden_paths) {
      if (/rm\s+-rf|shutdown|reboot/i.test(path)) {
        errors.push('dangerous path in forbidden_paths: ' + path);
      }
    }
  }

  // ── 5. 验证命令白名单 ─────────────────────────────────
  // 保留的 validation commands 中，必须不含危险的 shell 操作
  if (Array.isArray(gene.validation)) {
    gene.validation = gene.validation.filter(cmd => {
      if (!cmd || typeof cmd !== 'string') return false;
      // 禁止 node -e / shell 操作符 / 管道到危险命令
      if (/node\s+-e|;\s*\w|&\s*\w|\|\s*grep\s+-[iel]|kill\s+\d/i.test(cmd)) {
        errors.push('dangerous validation command stripped: ' + cmd);
        return false;
      }
      return true;
    });
  }

  return errors;  // 空数组 = 验证通过
}
```

### 2.3 关键设计原则

| 设计 | 机制 | 目的 |
|------|------|------|
| **ID 唯一性** | 与现有基因池比对 | 防止重复基因 |
| **信号完全重叠检测** | `overlap === newSet.size` | 避免引入与现有基因完全等价的"假新基因" |
| **信号高重叠警告** | `> 70% overlap` | 发现近似冗余 |
| **`.git` / `node_modules` 必须保护** | 白名单检查 | 确保 Git 仓库和依赖安全 |
| **危险 path 过滤** | `rm -rf` 等 pattern | 防止恶意/破坏性基因 |
| **validation 命令净化** | 过滤 `node -e` / shell 操作符 / `kill` | 防止验证阶段注入攻击 |
| **返回错误列表而非布尔** | `errors[]` 数组 | 便于调试和报告所有问题 |

### 2.4 调用上下文

`validateSynthesizedGene` 在 `shouldDistill()` → `prepareDistillation()` → LLM 生成 → 验证链中被调用：

```javascript
// skillDistiller.js — prepareDistillation() 内
const validationErrors = validateSynthesizedGene(newGene, existingGenes);
if (validationErrors.length > 0) {
  console.warn('[SkillDistiller] Validation failed:', validationErrors);
  // 不写入 gene pool，但保留用于诊断
}
```

---

## 3. 设计原则提炼

### 3.1 网络级闭环反馈（Hub-Selector Loop）

**核心洞察**：Evolver 不仅将 Hub 用作任务市场，更将其作为**网络级大脑**——每个 Agent 通过心跳获得"全局视角"，从而在基因选择层面实现**网络协同进化**。

| 要素 | 说明 |
|------|------|
| **集中知识** | Hub 汇总所有 Agent 的能力图谱 |
| **分散执行** | 每个 Agent 独立做基因选择 |
| **信息注入** | 通过 `capability_gaps` 和 `novelty` 影响选择 |
| **自然选择** | 覆盖缺口的 Agent → 更高适用性 → Hub 更可能分发相关任务 |

**三重反馈**：
1. **信号层**：Hub 的 `capability_gaps` → Curriculum 信号 → signals 列表
2. **选择层**：Hub 的 `capability_gaps` → 直接影响基因选择（diversity_directed drift）
3. **探索层**：Hub 的 `novelty.score` → 调节随机探索范围

### 3.2 验证即门禁（Validation as Gate）

`synthesizedGene` 的验证不是"可选检查"，而是**强制门禁**——任何 LLM 生成的基因必须通过 5 大类检查才能进入池中。这体现了：
- **纵深防御**：结构 + 唯一性 + 去重 + 安全 + 命令白名单
- **fail-safe**：即使 LLM 被提示注入恶意内容，验证层会过滤
- **可诊断性**：返回错误列表而非布尔，允许记录和报告

### 3.3 能力缺口作为"进化方向信号"

传统的进化算法依赖**局部适应度**（自身 outcome 历史）指导探索方向。Evolver 额外引入了**全局能力缺口**（Hub 报告）作为方向信号，实现了一种"**有指引的探索**"——不是盲目随机漂移，而是向网络最需要的方向漂移。

---

## 4. BlueCortexCE 借鉴

### 4.1 网络级反馈机制（CE 无 Hub，但可借鉴思想）

CE 作为**旁路记忆系统**，虽然没有 Hub，但可以在**会话间**实现类似的"全局视角反馈"：

| Evolver Hub 机制 | CE 对应设计 | 优先级 |
|----------------|-----------|--------|
| Hub 汇总 `capability_gaps` | 所有会话汇聚的**观察类型分布** → 发现"长期未覆盖的类型" | P1 |
| Hub 报告 `novelty.score` | 会话间**embedding 相似度检测** → 发现"重复探索同一领域" | P1 |
| `noveltyScore < 0.3` → 扩大探索 | 当某类型覆盖过多时，搜索结果中引入更多"冷门类型"的历史观察 | P1 |
| `capabilityGaps` → Curriculum 信号 | 用户长期未查询的**概念领域** → 适当注入相关记忆提醒 | P2 |

### 4.2 旁路验证门禁

CE 的 StructuredExtractionService 生成的 `ObservationEntity.extractedData` 应类似 `validateSynthesizedGene`，在写入前进行**结构验证**：

```java
// CE: extractedData 验证
public List<String> validateExtractedData(JsonNode data, String type) {
    List<String> errors = new ArrayList<>();
    if (data == null) {
        errors.add("extractedData must be non-null");
        return errors;
    }
    // 1. 必需字段检查（按类型）
    // 2. 敏感字段脱敏验证（调用 sanitize 模块）
    // 3. embedding 完整性（如果提供）
    // 4. 向后兼容性（新增字段不破坏 WebUI）
    return errors;  // 空 = 通过
}
```

### 4.3 探索性上下文注入

当检测到"会话主题过于集中"时，可参考 `noveltyScore` 机制注入**多样性上下文**：

```java
// CE: 基于覆盖度分布的多样性注入
public List<ObservationEntity> getDiversifiedContext(
        SearchContext ctx, List<ObservationEntity> candidates) {
    
    Map<String, Long> typeCounts = candidates.stream()
        .collect(Collectors.groupingBy(ObservationEntity::getType, Collectors.counting()));
    
    long total = candidates.size();
    Set<String> overRepresented = typeCounts.entrySet().stream()
        .filter(e -> e.getValue() * 1.0 / total > 0.6)  // >60% 视为过度集中
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    
    if (overRepresented.isEmpty()) {
        return candidates;  // 分布正常，不干预
    }
    
    // 引入少量非 dominant 类型的观察（类似 novelty 加权随机）
    return candidates.stream()
        .sorted((a, b) -> {
            boolean aOver = overRepresented.contains(a.getType());
            boolean bOver = overRepresented.contains(b.getType());
            if (aOver == bOver) return 0;
            return aOver ? 1 : -1;  // over-represented 降权
        })
        .collect(Collectors.toList());
}
```

---

## 附录：关键源码位置

| 功能 | 文件 | 函数 |
|------|------|------|
| Hub 心跳 + novelty/capability_gaps 解析 | `src/gep/a2aProtocol.js` | `_processHelloResponse()` (~line 658) |
| `novelty` / `capability_gaps` 导出 | `src/gep/a2aProtocol.js` | `getNoveltyHint()` (line 734) / `getCapabilityGaps()` (line 738) |
| 模块级缓存变量 | `src/gep/a2aProtocol.js` | `_latestNoveltyHint` (line 447) / `_latestCapabilityGaps` (line 448) |
| Curriculum → signals 注入 | `src/evolve.js` | `generateCurriculumSignals()` 调用 (~line 1337) |
| Selector 直接使用 Hub 数据 | `src/evolve.js` | `selectGeneAndCapsule()` 调用 (~line 1744) |
| `noveltyScore` → 探索范围调节 | `src/gep/selector.js` | `selectGene()` (~line 281) |
| `capabilityGaps` → diversity_directed drift | `src/gep/selector.js` | `selectGene()` (~line 255–290) |
| `validateSynthesizedGene` | `src/gep/skillDistiller.js` | line 383 |
| `isDuplicate` (问题去重) | `src/gep/questionGenerator.js` | line 34 |
