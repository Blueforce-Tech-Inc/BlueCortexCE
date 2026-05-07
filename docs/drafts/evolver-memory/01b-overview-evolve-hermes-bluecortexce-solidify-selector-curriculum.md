<!-- part 2/2: auto-split from 01-intro-toc-memory-through-curriculum.md (sections 6-11) -->
## 6. evolve.js — 核心进化循环

**文件**: `src/evolve.js` (完整源码)

### 6.1 主循环概览

Evolver 的核心进化循环：

```
1. 预检 (Preflight Checks)
   ├── 进程互斥 (防止重复运行)
   ├── 队列限流 (避免饥饿用户会话)
   ├── 系统负载检测
   └── 循环门控 (等待上一次 solidification 完成)

2. 信号提取 (Signal Extraction)
   ├── extractSignals() — 从会话日志/MEMORY.md 提取
   ├── 分析历史抑制信号
   └── Hub 任务自动认领

3. Hub 交互 (Optional)
   ├── Hub 搜索 (复用已有方案)
   ├── 任务获取与认领
   └── Hub 事件处理

4. 记忆图谱更新
   ├── recordOutcomeFromState() — 推断上轮结果
   ├── recordSignalSnapshot() — 记录当前信号
   ├── recordHypothesis() — 记录假设
   └── recordAttempt() — 记录行动

5. 基因选择 (Gene Selection)
   ├── getMemoryAdvice() — 从记忆图谱获取建议
   ├── selectGeneAndCapsule() — 选择基因和胶囊
   └── computeAdaptiveStrategyPolicy() — 计算策略

6. 突变构建
   ├── buildMutation() — 生成突变
   └── allowHighRisk 判定

7. 桥接执行 (Bridge Mode)
   └── sessions_spawn → Hand Agent 执行
```

### 6.2 预检机制

**文件**: `src/evolve.js:350-420`

```javascript
async function runPreflightChecks(bridgeEnabled, loopMode) {
  // 1. 进程互斥
  const psRace = execSync('ps aux | grep "evolver_hand_" | grep -v grep');
  if (_psRace.length > 0) return { abort: true };
  
  // 2. 队列限流
  const activeUserSessions = getRecentActiveSessionCount(10 * 60 * 1000);
  if (activeUserSessions > QUEUE_MAX) {
    await sleepMs(QUEUE_BACKOFF_MS);
    return { abort: true };
  }
  
  // 3. 系统负载
  if (sysLoad.load1m > LOAD_MAX) {
    await sleepMs(QUEUE_BACKOFF_MS);
    return { abort: true };
  }
  
  // 4. 循环门控
  if (bridgeEnabled && loopMode) {
    const st = readStateForSolidify();
    if (lastRun exists && !lastSolidify) {
      // 上次运行还没完成 solidification，等待
      return { abort: true };
    }
  }
}
```

### 6.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 进程互斥 | 防止重复运行 | **高优先级**: BlueCortexCE 应防止并发写入冲突 |
| 队列限流 | 避免饥饿用户会话 | **高优先级**: BlueCortexCE 的 cron 任务应尊重用户活跃期 |
| 系统负载检测 | load1m vs 核心数*0.9 | **中优先级**: 资源密集型操作应检测负载 |
| 循环门控 | 等待上一次完成 | **高优先级**: BlueCortexCE 的观察写入应有幂等保证 |

---

## 7. 与 Hermes 的架构对比

### 7.1 记忆存储对比

| 维度 | Evolver | Hermes |
|------|---------|--------|
| 核心存储 | JSONL append-only | SQLite + FTS5 |
| curated memory | MEMORY.md 平面文件 | MEMORY.md 平面文件 |
| 记忆图谱 | signal→hypothesis→attempt→outcome | Holographic HRR vectors |
| 叙事记忆 | narrativeMemory.md | 无 |
| 信号系统 | signals.js 多语言提取 | honcho recall modes |

### 7.2 关键差异

1. **Evolver 有显式因果链**: signal → hypothesis → attempt → outcome，追踪"为什么这样做"
2. **Hermes 有向量检索**: HRR + cosine similarity，支持语义搜索
3. **Evolver 有叙事记忆**: 人类可读的决策历史
4. **Evolver 有 Gene 概念**: 结构化的突变模板库
5. **Hermes 有多 Provider**: honcho/hindsight/mem0 可插拔

### 7.3 Hermes 对 Evolver 的"借鉴"分析

根据 hermes-memory-analysis.md，Hermes 的 Holographic 机制与 Evolver 的 memoryGraph 有以下相似点：

| Evolver | Hermes (推测抄袭) |
|---------|------------------|
| signal → hypothesis → attempt → outcome | Observation → Memory → Holographic |
| computeSignalKey (归一化) | Entity extraction + normalization |
| getMemoryAdvice (基因推荐) | reason() (代数检索) |
| edgeExpectedSuccess (Laplace + 衰减) | Trust scoring (指数衰减) |

**⚠️ 注意**: Hermes 的实现可能已偏离 Evolver 的原始设计思想。本分析仅基于 evolver 代码库的实际内容。

---

## 8. BlueCortexCE 借鉴建议汇总

### 8.1 高优先级借鉴

| 借鉴点 | Evolver 做法 | BlueCortexCE 现状 | 具体建议 |
|--------|-------------|------------------|----------|
| 错误签名归一化 | errsig + stableHash 归一化 | 无 | 在 Observation 记录中实现类似的错误签名归一化 |
| 归因链追踪 | signal→hypothesis→attempt→outcome | observations 表只有单条记录 | 增加 `parent_id` 和 `cause_type` 字段支持归因 |
| 时间衰减权重 | 30天半衰期指数衰减 | 无时间衰减 | 在检索结果排序中加入时间衰减因子 |
| Laplace 平滑 | (succ+1)/(total+2) | 无 | 所有成功率统计使用 Laplace 平滑 |
| 叙事摘要格式 | Markdown + 策略链 + 结果 | 纯文本 summary | 改进 summary 格式，支持策略/结果结构化 |
| 多语言支持 | EN/ZH-CN/ZH-TW/JA 信号检测 | 无 | 实现多语言观察提取 |
| 历史抑制 | 3+ 次信号 suppress | 无 | 实现"已处理信号"的冷却机制 |

### 8.2 中优先级借鉴

| 借鉴点 | Evolver 做法 | 具体建议 |
|--------|-------------|----------|
| 低效路径抑制 | 连续失败+低成功率 → ban | 记录"无效检索模式"并降权 |
| 语义标签扩展 | expandSignals → problem/action/area | 实现观察的语义标签分类 |
| 固定上限裁剪 | 30 条 / 12000 chars | summaries 表实现自动裁剪 |
| 进程互斥 | 防止重复运行 | cron 任务使用文件锁 |

### 8.3 低优先级（架构差异大）

| Evolver 特性 | BlueCortexCE 评估 |
|-------------|-------------------|
| Gene/Capsule 进化系统 | 旁路型系统不需要突变模板 |
| Personality 自然选择 | 无需人格系统 |
| Hub 生态系统 | 暂无多 Agent 协作需求 |

---

## 9. solidify.js — 突变固化与验证流程（v0.2 新增）

**文件**: `src/gep/solidify.js` (1344 lines)

### 9.1 核心设计原则

solidify.js 是 Evolver 的**验证与固化层**，负责：
1. 对上轮突变进行多维度验证
2. 计算 PRM (Process Reward Model) 评分
3. 成功时创建 Capsule，失败时保留 FailedCapsule
4. 应用表观遗传标记（Epigenetic Marks）
5. 可选地自动发布到 Hub

### 9.2 Process Reward Model (PRM) — 多步评分

**文件**: `solidify.js:430-560` (`computeProcessScores`)

Evolver 不使用简单的成功/失败二元判断，而是评估**8个独立维度**：

```javascript
// Phase weights
const weights = {
  signal:           0.05,   // 信号质量
  selection:        0.10,  // 基因选择质量
  mutation:         0.05,  // 突变格式质量
  blast:            0.15,  // 变更范围控制
  constraint:       0.25,  // 约束合规性
  validation:       0.25,  // 验证通过率
  protocol:         0.10,  // 协议合规性
  canary:           0.05,  // Canary 健康检查
};
```

**各维度评分逻辑**：

| Phase | 评分规则 |
|-------|---------|
| **signal_quality** | 0.4 + signals.length × 0.1，上限 1.0 |
| **gene_selection** | gene_auto_ = 0.7，named gene = 0.9，无 = 0.3 |
| **mutation_quality** | 有 rationale+category = 0.8，低风险=0.9，高风险=0.6 |
| **blast_control** | 文件数 ≤ 50%maxFiles = 1.0，≤ 100% = 0.7，>100% = 0.2 |
| **constraint_compliance** | 1 - violationCount × 0.25，下限 0 |
| **validation_pass_rate** | 通过数/总数，无验证命令 = 0.5 penalty |
| **protocol_compliance** | 1 - protocolViolations.length × 0.3 |
| **canary_health** | canary 失败 = 0，否则 1.0 |

**Evolver 为什么这样做**：单一的成功/失败指标无法区分"好的一般"和"差的一般"。PRM 让进化系统能够理解**部分成功**——例如验证通过但 blast radius 超标的改进仍然有价值。

### 9.3 Canary Safety Check — 隔离进程验证

**文件**: `solidify.js:610-625`

```javascript
// 在 validation 之后、提交之前，运行 canary 检查
const canary = runCanaryCheck({ repoRoot, timeoutMs: 30000 });
if (!canary.ok && !canary.skipped) {
  constraintCheck.violations.push(
    `canary_failed: index.js cannot load in child process: ${canary.err}`
  );
  constraintCheck.ok = false;
}
```

**设计意图**：`gene.validation` 命令可能通过（只验证了特定模块），但 index.js 入口点可能已损坏。Canary 在**隔离子进程**中加载 index.js，确保整体可运行性。

**Evolver 为什么这样做**：基因验证是局部检查，canary 是全局冒烟测试。两者结合确保"验证通过"≠"系统可用"。

### 9.4 FailedCapsule — 失败信息的保全

**文件**: `solidify.js:840-880`

```javascript
// 在 rollback 之前，捕获失败突变的信息
if (!dryRun && !success) {
  const diffSnapshot = captureDiffSnapshot(repoRoot);
  if (diffSnapshot) {
    const failedCapsule = {
      type: 'Capsule',
      id: 'failed_' + buildCapsuleId(ts),
      outcome: { status: 'failed', score: score },
      gene: geneUsed && geneUsed.id ? geneUsed.id : null,
      trigger: signals.slice(0, 8),
      diff_snapshot: diffSnapshot,
      failure_reason: failureReason,
      learning_signals: softFailureLearningSignals,
      constraint_violations: constraintCheck.violations || [],
      env_fingerprint: envFp,
      blast_radius: { files: blast.files, lines: blast.lines },
    };
    appendFailedCapsule(failedCapsule);
  }
}
```

**关键点**：rollback 会清除工作区变更，但在 rollback **之前**先捕获 diff_snapshot。这样即使失败，失败的原因和上下文也被永久记录。

### 9.5 Epigenetic Marks — 环境感知的基因表达调控

**文件**: `solidify.js:245-340` (`applyEpigeneticMarks`, `buildEpigeneticMark`)

```javascript
// 环境上下文 = platform + arch + node_version
const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';

// 成功的环境 → 正向标记
if (outcomeStatus === 'success') {
  if (existingIdx >= 0) {
    cur.boost = Math.min(0.5, cur.boost + 0.05);  // 强化
  } else {
    gene.epigenetic_marks.push(buildEpigeneticMark(envContext, 0.1, 'success_in_environment'));
  }
}

// 失败的环境 → 负向标记
if (outcomeStatus === 'failed') {
  if (existingIdx >= 0) {
    cur.boost = Math.max(-0.5, cur.boost - 0.1);  // 抑制
  } else {
    gene.epigenetic_marks.push(buildEpigeneticMark(envContext, -0.1, 'failure_in_environment'));
  }
}

// 衰减：90天前的标记过期，最多保留10个
const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
gene.epigenetic_marks = gene.epigenetic_marks
  .filter(m => new Date(m.created_at).getTime() > cutoff)
  .slice(-10);
```

**选择算法中的表观遗传 boost**：

```javascript
// selector.js:getEpigeneticBoostLocal()
function getEpigeneticBoostLocal(gene, envFingerprint) {
  const envContext = [platform, arch, nodeVersion].join('/') || 'unknown';
  const mark = gene.epigenetic_marks.find(m => m.context === envContext);
  return mark ? Number(mark.boost) || 0 : 0;
}
```

**Evolver 为什么这样做**：
1. **跨环境泛化**：Linux 上成功的基因在 macOS 上可能失败
2. **无损调整**：表观遗传标记不改变基因本身，只调整表达强度
3. **生物类比**：DNA 甲基化——相同基因在不同环境下有不同表达

### 9.6 Auto-publish to Hub — 成功的自动共享

**文件**: `solidify.js:950-1080`

```javascript
// 成功 + eligible_to_broadcast + 公开可见 + 最低分数 → 自动发布
if (!dryRun && capsule && capsule.a2a && capsule.a2a.eligible_to_broadcast) {
  const autoPublish = String(process.env.EVOLVER_AUTO_PUBLISH || 'true').toLowerCase() !== 'false';
  // ...
  if (autoPublish && visibility === 'public' && sourceType !== 'reused' && score >= minPublishScore) {
    // 发布 Gene + Capsule bundle 到 Hub
    const msg = buildPublishBundle({ gene, capsule, event, chainId, modelName });
    httpTransportSend(msg, { hubUrl });
  }
}
```

**eligible_to_broadcast 的判定**：
```javascript
capsule.a2a = {
  eligible_to_broadcast:
    isBlastRadiusSafe(capsule.blast_radius) &&
    (capsule.outcome.score || 0) >= BROADCAST_SCORE_THRESHOLD &&
    (capsule.success_streak || 0) >= BROADCAST_SUCCESS_STREAK,
};
```

### 9.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| PRM 多步评分 | 8维度独立评分，权重加权 | **高优先级**: BlueCortexCE 的 observation 可以记录多维度质量指标，不只是"记录成功/失败" |
| Canary 冒烟测试 | 隔离子进程加载 index.js | **高优先级**: BlueCortexCE 的 API 可以在写入后做基本的"冒烟测试"（如 health check） |
| FailedCapsule 保存 | rollback 前捕获 diff_snapshot | **高优先级**: BlueCortexCE 的失败记录应保存完整的上下文信息 |
| Epigenetic Marks | 环境感知的基因表达调控 | **中优先级**: BlueCortexCE 的检索可以记录"特定环境下检索效果好/差" |
| Auto-publish | 成功胶囊自动共享 Hub | **低优先级**: BlueCortexCE 无 Hub 生态 |

---

## 10. selector.js — 基因和胶囊选择算法（v0.2 新增）

**文件**: `src/gep/selector.js` (417 lines)

### 10.1 多层评分体系

Evolver 的基因选择使用**三层评分叠加**：

```javascript
function scoreGene(gene, signals) {
  const patterns = gene.signals_match || [];
  const tagScore = scoreTagOverlap(gene, signals);  // 语义标签重叠
  let score = 0;
  for (const pat of patterns) {
    if (matchPatternToSignals(pat, signals)) score += 1;  // 精确匹配
  }
  const semanticScore = scoreGeneSemantic(gene, signals) * SEMANTIC_WEIGHT;  // 语义相似度
  return score + (tagScore * 0.6) + semanticScore;
}
```

**评分构成**：
- 精确信号匹配：每个匹配 +1 分
- 语义标签重叠：scoreTagOverlap × 0.6
- Bag-of-words 语义相似度：cosine similarity × 0.4

### 10.2 Bag-of-Words 语义相似度

**文件**: `selector.js:25-70`

```javascript
// 停用词过滤
const STOP_WORDS = new Set([
  'the', 'and', 'for', 'with', 'from', 'that', 'this', 'into', 'when',
  'are', 'was', 'has', 'had', 'not', 'but', 'its', 'can', 'will', ...
]);

function tokenize(text) {
  return String(text || '').toLowerCase()
    .replace(/[^a-z0-9_\-]+/g, ' ')
    .split(/\s+/)
    .filter(w => w.length >= 2 && !STOP_WORDS.has(w));
}

function cosineSimilarity(tfA, tfB) {
  // TF-IDF style cosine similarity
  var dotProduct = 0, normA = 0, normB = 0;
  keys.forEach(function(k) {
    dotProduct += (tfA[k]||0) * (tfB[k]||0);
    normA += (tfA[k]||0) ** 2;
    normB += (tfB[k]||0) ** 2;
  });
  return normA && normB ? dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)) : 0;
}
```

**Evolver 为什么这样做**：在无外部 embedding provider 时，用 bag-of-words 实现轻量语义匹配。无需 LLM 或向量数据库。

### 10.3 多语言别名匹配

**文件**: `selector.js:100-115`

```javascript
// 多语言别名: "en_term|zh_term|ja_term" — 任意分支匹配即命中
if (p.includes('|') && !p.startsWith('/')) {
  const branches = p.split('|').map(b => b.trim().toLowerCase()).filter(Boolean);
  return branches.some(needle => sig.some(s => s.toLowerCase().includes(needle)));
}
```

**Evolver 为什么这样做**：Evolver 是国际化项目，用户使用多种语言。多语言别名让单一基因模式匹配多个语言的信号。

### 10.4 连续漂移强度

**文件**: `selector.js:450-490` (`computeDriftIntensity`)

```javascript
// 遗传漂移强度公式：1/sqrt(Ne)
// Ne = effective population size（活跃基因数量）
function computeDriftIntensity(opts) {
  const ne = effectivePopulationSize || genePoolSize || null;
  if (driftEnabled) {
    return ne && ne > 1 ? Math.min(1, 1 / Math.sqrt(ne) + 0.3) : 0.7;
  }
  if (ne != null && ne > 0) {
    // 小种群 = 强漂移，Ne=1 → intensity=1.0, Ne=25 → 0.2
    return Math.min(1, 1 / Math.sqrt(ne));
  }
  return 0;
}
```

**进化生物学背景**：在自然界，小种群更容易发生遗传漂移（随机因素主导），大种群由自然选择主导。Evolver 将这个原理形式化为连续函数。

### 10.5 多样性导向漂移

**文件**: `selector.js:510-560`

```javascript
if (driftIntensity > 0 && Math.random() < driftIntensity) {
  if (capabilityGaps.length > 0) {
    // 有 gap 数据：选择覆盖最多 gap 的基因
    const gapScores = filtered.map((entry, idx) => {
      const patterns = g.signals_match || [];
      let gapHits = 0;
      for (const gapSignal of capabilityGaps.slice(0, 5)) {
        if (patterns.some(p => matchPatternToSignals(p, [gapSignal]))) gapHits++;
      }
      return { idx, gapHits, baseScore: entry.score };
    });
    // 优先 gap 覆盖，次优先 base score
    gapScores.sort((a, b) => b.gapHits - a.gapHits || b.baseScore - a.baseScore);
    selectedIdx = gapScores[0].idx;
    driftMode = 'diversity_directed';
  }
}
```

**Evolver 为什么这样做**：普通的随机漂移（pure drift）探索效率低。多样性导向漂移优先探索**能力空白区**，提高探索效率。

### 10.6 Learning History Boost

**文件**: `selector.js:155-195`

```javascript
function scoreGeneLearning(gene, signals, envFingerprint) {
  let boost = 0;
  const history = gene.learning_history || [];
  
  // 近期历史（最后8条）
  for (const entry of history.slice(-8)) {
    if (entry.outcome === 'success') boost += 0.12;
    else if (entry.mode === 'hard') boost -= 0.22;
    else if (entry.mode === 'soft') boost -= 0.08;
  }
  
  // 表观遗传标记
  boost += getEpigeneticBoostLocal(gene, envFingerprint);
  
  // Anti-pattern 惩罚
  for (const anti of gene.anti_patterns.slice(-6)) {
    const overlap = anti.learning_signals.some(tag => signalTags.has(String(tag)));
    if (overlap) boost -= anti.mode === 'hard' ? 0.4 : 0.18;
  }
  
  return Math.max(-1.5, Math.min(1.5, boost));  // clamp
}
```

**Evolver 为什么这样做**：
- 近期成功 = 正向反馈信号
- 硬失败（hard）= 严重惩罚（不可恢复的错误）
- 软失败（soft）= 轻度惩罚（可能重试成功）
- Anti-pattern 重叠 = 避免重复已知失败路径

### 10.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 多层评分 | 精确+标签+语义三层叠加 | **高优先级**: BlueCortexCE 的检索可使用多维度评分（信号匹配+语义+时效性） |
| Bag-of-words 语义 | 无外部依赖的轻量语义匹配 | **中优先级**: 简单场景可不用 embedding provider |
| 多语言别名 | en\|zh\|ja 分支匹配 | **高优先级**: BlueCortexCE 的检索应支持多语言查询 |
| 连续漂移强度 | 1/sqrt(Ne) 公式 | **低优先级**: BlueCortexCE 无基因池概念 |
| Learning boost | 历史成功+失败+anti-pattern | **高优先级**: BlueCortexCE 的检索结果排序应考虑"历史使用效果" |
| 多样性导向 | 能力 gap 覆盖优先 | **中优先级**: BlueCortexCE 的推荐可以覆盖"低频但重要"的观察 |

---

## 11. curriculum.js — 渐进式学习课程（v0.2 新增）

**文件**: `src/gep/curriculum.js` (163 lines)

### 11.1 核心概念：Frontier 信号

Evolver 的 curriculum 系统基于信号空间的**三层划分**：

| 类别 | 定义 | 阈值 |
|------|------|------|
| **mastered** | 已掌握，成功率 ≥ 80%，至少3次尝试 | rate ≥ 0.8, total ≥ 3 |
| **failing** | 持续失败，成功率 ≤ 30%，至少2次尝试 | rate ≤ 0.3, total ≥ 2 |
| **frontier** | 中间地带，正在学习 | rate ∈ (0.3, 0.8) 或 total < 2 |

```javascript
function identifyFrontier(outcomes) {
  const mastered = [], failing = [], frontier = [];
  
  for (const [k, o] of Object.entries(outcomes)) {
    if (o.total < 2) continue;
    const rate = o.success / o.total;
    if (rate >= MASTERY_THRESHOLD && o.total >= MASTERY_MIN_ATTEMPTS) {
      mastered.push({ key: k, rate, total: o.total });
    } else if (rate <= FAILURE_THRESHOLD && o.total >= 2) {
      failing.push({ key: k, rate, total: o.total });
    } else {
      // 按与 0.5 的距离排序（越接近 0.5 越需要学习）
      frontier.push({ key: k, rate, total: o.total });
    }
  }
  
  frontier.sort((a, b) => Math.abs(a.rate - 0.5) - Math.abs(b.rate - 0.5));
  return { mastered, failing, frontier };
}
```

### 11.2 课程信号生成

**文件**: `curriculum.js:80-110`

```javascript
function generateCurriculumSignals(opts) {
  const { capabilityGaps, memoryGraphPath, personality } = opts;
  const signals = [];
  
  // 1. 优先处理 Hub capability gaps
  if (capabilityGaps.length > 0) {
    const gapTarget = capabilityGaps[0];
    const alreadyMastered = analysis.mastered.some(m => m.key.indexOf(gapTarget) >= 0);
    if (!alreadyMastered) {
      signals.push('curriculum_target:gap:' + gapTarget.slice(0, 60));
    }
  }
  
  // 2. 其次处理 frontier 信号（最需要学习的）
  if (signals.length < MAX_CURRICULUM_SIGNALS && analysis.frontier.length > 0) {
    const best = analysis.frontier[0];  // 最接近 0.5 的
    signals.push('curriculum_target:frontier:' + best.key.slice(0, 60));
  }
  
  return signals.slice(0, MAX_CURRICULUM_SIGNALS);  // 最多2个
}
```

### 11.3 结果聚合

**文件**: `curriculum.js:30-60`

```javascript
function aggregateOutcomes(memoryGraphPath) {
  const outcomes = {};
  const lines = fs.readFileSync(memoryGraphPath, 'utf8').trim().split('\n').slice(-200);
  
  for (const line of lines) {
    const ev = JSON.parse(line);
    if (ev.kind !== 'outcome') continue;
    const key = ev.signal_key || ev.key || '';
    if (!outcomes[key]) outcomes[key] = { success: 0, fail: 0, total: 0 };
    if (ev.outcome.status === 'success') outcomes[key].success++;
    else if (ev.outcome.status === 'failed') outcomes[key].fail++;
    outcomes[key].total++;
  }
  return outcomes;
}
```

**Evolver 为什么这样做**：从最近 200 个 outcome 事件聚合信号级别的成功率，这是 curriculum 学习的数据基础。

### 11.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 |
|------|-------------|---------------------|
| 三层信号分类 | mastered/failing/frontier | **高优先级**: BlueCortexCE 可实现检索效果的类似分类 |
| 课程信号 | gap + frontier 优先 | **中优先级**: BlueCortexCE 可优先推荐"低曝光但潜在高价值"的观察 |
| 结果聚合 | signal → success/fail/total | **高优先级**: BlueCortexCE 应聚合检索历史的成功率 |
| 渐进式难度 | frontier 最需要学习 | **中优先级**: BlueCortexCE 的推荐可按"难度/覆盖率"排序 |

---

