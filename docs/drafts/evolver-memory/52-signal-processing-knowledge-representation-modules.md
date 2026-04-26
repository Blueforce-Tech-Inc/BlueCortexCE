# Evolver 信号处理与知识表示模块深度分析

> **数据来源**：`src/gep/learningSignals.js`、`src/gep/narrativeMemory.js`、`src/gep/candidates.js`、`src/gep/paths.js`
> **最后更新**：2026-04-25
> **前置阅读**：[37 Signal Taxonomy + Gene Selection 端到端](./37-signal-taxonomy-gene-selection-end-to-end.md)（signal 生命周期 → 四因子叠加评分 → Mutation 决策链）、[21 Signal Taxonomy](./21-signal-taxonomy-and-gene-selection-memory.md)（expandSignals / Jaccard ≥ 0.34 / getMemoryAdvice / Laplace 平滑）

---

## 1. 架构定位：信号到知识的转换管道

Evolver 的记忆系统包含两条正交的知识表示轴：

```
原始事件/Signal
    ↓  [learningSignals.js]
结构化标签（problem/action/area/risk）
    ↓  [candidates.js → skillDistiller.js]
Capability Candidate（Five Questions Shape）
    ↓  [skillDistiller.js]
可执行技能资产（SKILL.md）

原始事件/Signal
    ↓  [narrativeMemory.js]
叙事性记忆（evolution_narrative.md）
    ↓  [evolve.js 主循环]
叙事性经验用于 future prompt 注入
```

**两条表示路径的对比**：

| 维度 | 标签路径（learningSignals） | 叙事路径（narrativeMemory） |
|------|------------------------------|----------------------------|
| 粒度 | 细粒度（tag 级别） | 粗粒度（entry 级别） |
| 用途 | gene selection 评分输入 | 人工可读经验记录 |
| 格式 | 结构化标签集合 | Markdown 叙事条目 |
| 衰减 | Laplace 平滑 + 半衰衰减 | 固定条目数（≤30 条）+ 大小上限（12KB） |
| 检索 | tag overlap scoring（`scoreTagOverlap`） | 末尾 8 条摘要加载 |

---

## 2. `learningSignals.js`：信号扩展与基因标签评分

### 2.1 模块职责

`learningSignals.js` 是信号处理的核心转换器，提供三个导出函数：

```javascript
expandSignals(signals, extraText)  // raw signals → structured tags
geneTags(gene)                       // gene object → tag set
scoreTagOverlap(gene, signals)       // gene-tags × signal-tags → overlap score
```

### 2.2 `expandSignals`：信号语义扩展

**输入**：原始信号数组 + 额外文本  
**输出**：去重后的结构化标签数组

**扩展策略**（三层叠加）：

```javascript
// Layer 1: 原始信号直接加入
add(tags, signal)

// Layer 2: 冒号前缀提取（降维）
const base = signal.split(':')[0]
if (base && base !== signal) add(tags, base)

// Layer 3: 文本模式匹配 → 问题/行为/区域/风险标签
if (/(error|exception|failed|unstable|log_error|runtime|429)/.test(text)) {
    add(tags, 'problem:reliability')
    add(tags, 'action:repair')
}
if (/(protocol|prompt|audit|gep|schema|drift)/.test(text)) {
    add(tags, 'problem:protocol')
    add(tags, 'action:optimize')
    add(tags, 'area:prompt')
}
if (/(perf|performance|bottleneck|latency|slow|throughput)/.test(text)) {
    add(tags, 'problem:performance')
    add(tags, 'action:optimize')
}
if (/(feature|capability_gap|user_feature_request|external_opportunity|stagnation recommendation)/.test(text)) {
    add(tags, 'problem:capability')
    add(tags, 'action:innovate')
}
if (/(stagnation|plateau|steady_state|saturation|empty_cycle_loop|loop_detected|recurring)/.test(text)) {
    add(tags, 'problem:stagnation')
    add(tags, 'action:innovate')
}
// area 标签
if (/(task|worker|heartbeat|hub|commitment|assignment|orchestration)/.test(text)) {
    add(tags, 'area:orchestration')
}
if (/(memory|narrative|reflection)/.test(text)) {
    add(tags, 'area:memory')
}
if (/(skill|dashboard)/.test(text)) {
    add(tags, 'area:skills')
}
if (/(validation|canary|rollback|constraint|blast radius|destructive)/.test(text)) {
    add(tags, 'risk:validation')
}
```

**设计思想**：
- **Layer 1+2**：保留原始信号的表达能力，同时提取共性前缀用于泛化匹配
- **Layer 3**：基于关键词模式注入结构化元标签，将"文本"升格为"语义类型"
- 四个标签维度：`problem:*`（需要解决的问题）、`action:*`（推荐行为）、`area:*`（涉及领域）、`risk:*`（风险标识）

**调用方覆盖**（5 个模块）：

| 调用方 | 用途 |
|--------|------|
| `selector.js:105` | `scoreTagOverlap` 为基因选择器提供 tag 维度的重叠评分 |
| `selector.js:142` | 构建信号 tag set，用于 Jaccard 相似度计算 |
| `candidates.js:68` | 扩展信号列表，为 Capability Candidate 生成 `tags` 字段 |
| `candidates.js:134` | 扩展失败胶囊触发信号 + 失败原因，生成 dominant problem 分类 |
| `policyCheck.js:498` | 扩展信号 + 验证违规 + 失败原因，生成 policy tag 集合 |
| `skillDistiller.js:621` | 扩展技能匹配的信号标签，用于生成技能 asset 的元标签 |

### 2.3 `geneTags`：基因对象 → 标签集合

```javascript
function geneTags(gene) {
    let inputs = []
    if (gene.category) inputs.push('action:' + String(gene.category).toLowerCase())
    if (Array.isArray(gene.signals_match)) inputs = inputs.concat(gene.signals_match)
    if (typeof gene.id === 'string') inputs.push(gene.id)
    if (typeof gene.summary === 'string') inputs.push(gene.summary)
    return expandSignals(inputs, '')
}
```

**设计思想**：从基因的多个字段联合抽取标签，而非仅依赖 `signals_match`。`category` 字段提供 action 标签，`id` 和 `summary` 提供语义上下文。这使得即使一个基因没有显式的 `signals_match`，也能通过其 id/summary 获得标签。

### 2.4 `scoreTagOverlap`：基因-信号 tag 重叠评分

```javascript
function scoreTagOverlap(gene, signals) {
    const signalTags = expandSignals(signals, '')
    const geneTagList = geneTags(gene)
    if (signalTags.length === 0 || geneTagList.length === 0) return 0
    const signalSet = new Set(signalTags)
    let hits = 0
    for (const tag of geneTagList) {
        if (signalSet.has(tag)) hits++
    }
    return hits
}
```

**评分语义**：命中标签数量（非归一化）。该分数直接输入 `selector.js` 的多因子评分模型（作为 `tag` 因子）。

**与 Jaccard 的关系**：Doc 37 描述的 Jaccard ≥ 0.34 阈值是 signal-key 级别的相似度；`scoreTagOverlap` 是 tag 级别的细粒度重叠评分。两者可以并行使用。

---

## 3. `candidates.js`：Capability Candidate 提取管线

### 3.1 三来源候选提取

`extractCapabilityCandidates()` 从三个来源并行提取候选技能：

```javascript
// 来源 1：转录本中高频工具调用（≥3 次）
const toolCalls = extractToolCalls(recentSessionTranscript)
const freq = countFreq(toolCalls)
for (const [tool, count] of freq.entries()) {
    if (count < 3) continue
    candidates.push({
        type: 'CapabilityCandidate',
        id: `cand_${stableHash(title)}`,
        title: `Repeated tool usage: ${tool}`,
        source: 'transcript',
        signals: signalList,
        tags: expandedTags,  // ← 使用 learningSignals 扩展
        shape: buildFiveQuestionsShape({ title, signals, evidence }),
    })
}

// 来源 2：信号映射为候选（10 种预定义信号 → 候选标题）
const signalCandidates = [
    { signal: 'log_error', title: 'Repair recurring runtime errors' },
    { signal: 'protocol_drift', title: 'Prevent protocol drift and enforce auditable outputs' },
    { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions' },
    { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
    { signal: 'user_feature_request', title: 'Implement user-requested feature' },
    { signal: 'perf_bottleneck', title: 'Resolve performance bottleneck' },
    // ...
]
// 仅当对应信号存在时才生成候选

// 来源 3：失败胶囊聚类（≥2 次同类失败 → 一个聚合候选）
failedCapsules
    .filter(fc => fc.outcome?.status !== 'success')
    .groupBy(failureTags)  // problem:*/area:*/risk:* 标签分组
    .filter(group => group.count >= 2)
    .map(group => ({
        type: 'CapabilityCandidate',
        source: 'failed_capsules',
        title: 'Learn from recurring failed evolution paths',
        // 标题根据 dominant problem 动态生成
    }))
```

### 3.2 Five Questions Shape

每个候选携带一个标准化的 shape（受 CourseEra Five Questions 启发）：

```javascript
{
    title: '...',                    // 候选标题
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: 'Signals: signal1, signal2, ...',
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: '...',                 // 原始证据（truncate 240 chars）
}
```

**设计思想**：将"发现一个问题"转化为"一个有边界条件的技能需求描述"。`invariants` 和 `failure_points` 字段使 skillDistiller 能够生成更有针对性的 SKILL.md。

### 3.3 失败胶囊聚类算法

```javascript
// Step 1: 为每个失败胶囊扩展标签
const failureTags = expandSignals(
    (fc.trigger || []).concat(signalList),
    reason  // ← 使用失败原因作为 extraText 增强扩展
).filter(t =>
    t.startsWith('problem:') ||
    t.startsWith('risk:') ||
    t.startsWith('area:') ||
    t.startsWith('action:')
)

// Step 2: 确定 dominant problem（优先级数组）
const problemPriority = [
    'problem:performance',
    'problem:protocol',
    'problem:reliability',
    'problem:stagnation',
    'problem:capability',
]

// Step 3: 按 dominant problem 分组，≥2 次同类失败 → 一个聚合候选
```

**关键洞察**：不是每个失败都生成候选，而是将"同类失败聚类"后才生成——这避免了噪声触发，聚焦真正需要系统性解决的重复失败模式。

---

## 4. `narrativeMemory.js`：叙事性经验记忆

### 4.1 模块职责

将每次进化循环的关键决策记录为 Markdown 条目，存入 `evolution_narrative.md`。这是 Evolver 的"经验笔记本"——非结构化但人类可读。

### 4.2 记录格式

```markdown
### [2026-04-25 05:30] REPAIR - success
- Gene: gene_abc123 | Score: 0.82 | Scope: 3 files, +47/-12 lines
- Signals: [error:file_not_found, recurring:3x, session_timeout]
- Why: File path resolution fails on Windows due to backslash handling
- Strategy:
  1. Add path.normalize() wrapper
  2. Add Windows-specific test case
- Result: Session initialization成功率从 71% 提升至 89%
```

### 4.3 有界保留策略（bounded retention）

```javascript
const MAX_NARRATIVE_ENTRIES = 30   // 最大条目数
const MAX_NARRATIVE_SIZE  = 12000  // 最大文件大小（字节）

function trimNarrative(content) {
    // 优先保留最近条目（保留 header + 最近 entries）
    const entries = content.split(/(?=^### \[)/m)
    while (entries.length > MAX_NARRATIVE_ENTRIES) {
        entries.shift()  // 丢弃最老条目
    }
    let result = header + entries.join('')
    // 如果仍超限，额外裁剪至最近 5 条
    if (result.length > MAX_NARRATIVE_SIZE) {
        result = header + entries.slice(-5).join('')
    }
    return result
}
```

**有界保留设计原则**：
- **条目数上限（30）**：避免无限增长
- **文件大小上限（12KB）**：防止文件系统压力
- **双重保障**：条目数超 → 裁剪最老；文件大小超 → 进一步压缩到 5 条
- **FIFO 驱逐**：总是丢弃最老条目，保留最新经验

### 4.4 摘要加载策略

```javascript
function loadNarrativeSummary(maxChars = 4000) {
    const entries = content.split(/(?=^### \[)/m)
    const recent = entries.slice(-8)  // 固定取最近 8 条
    let summary = recent.join('')
    if (summary.length > limit) {
        summary = summary.slice(-limit)  // 从尾部截断
        const firstEntry = summary.indexOf('### [')
        if (firstEntry > 0) summary = summary.slice(firstEntry)
    }
    return summary.trim()
}
```

**设计决策**：固定取最近 8 条，而非动态计算。这比"按 token 预算"更简单，比"取最近的 N%"更可预测。`maxChars = 4000` 隐含了 token 预算控制。

### 4.5 与 memoryGraph.js 的关系

| 维度 | narrativeMemory | memoryGraph |
|------|-----------------|-------------|
| 存储格式 | Markdown 文件 | JSON 文件 |
| 粒度 | 每轮进化一个 entry | 每条边一个对象 |
| 用途 | 人类可读经验总结 | 程序化边权重查询 |
| 衰减 | 无（条目直接丢弃） | Laplace 平滑 + 半衰衰减 |
| 检索 | 线性加载（最近 8 条） | 向量相似度搜索 |

两者互补：narrativeMemory 记录"完整叙事"，memoryGraph 维护"量化关系"。

---

## 5. `paths.js`：路径管理与 Session Scope 隔离

### 5.1 核心路径函数

```javascript
getRepoRoot()         // EVOLVER_REPO_ROOT 或向上查找 .git
getWorkspaceRoot()    // OPENCLAW_WORKSPACE 或 repoRoot/workspace
getMemoryDir()        // MEMORY_DIR 或 workspace/memory
getEvolutionDir()     // memory/evolution（含 scope 子目录）
getGepAssetsDir()    // repo/assets/gep（含 scope 子目录）
getSkillsDir()       // SKILLS_DIR 或 workspace/skills
getSessionScope()     // EVOLVER_SESSION_SCOPE 环境变量
```

### 5.2 Session Scope 隔离

```javascript
function getSessionScope() {
    const raw = String(process.env.EVOLVER_SESSION_SCOPE || '').trim()
    if (!raw) return null
    // 白名单字符（防路径遍历）
    const safe = raw.replace(/[^a-zA-Z0-9_\-\.]/g, '_').slice(0, 128)
    if (!safe || /^\.{1,2}$/.test(safe) || /\.\./.test(safe)) return null
    return safe
}

function getEvolutionDir() {
    const baseDir = process.env.EVOLUTION_DIR || path.join(getMemoryDir(), 'evolution')
    const scope = getSessionScope()
    if (scope) {
        return path.join(baseDir, 'scopes', scope)
    }
    return baseDir
}
```

**Session Scope 设计**：
- **隔离粒度**：进程级别（通过 `EVOLVER_SESSION_SCOPE` 环境变量）
- **典型值**：Discord channel ID、项目名称等
- **安全**：输入白名单校验（仅允许 alphanumeric/dash/underscore/dot），防路径遍历
- **向后兼容**：无 `EVOLVER_SESSION_SCOPE` 时退化为全局路径
- **覆盖范围**：evolution dir + gep assets dir（`getGepAssetsDir()` 也使用相同 scope 逻辑）

**与 BlueCortexCE 的对比**：

| 维度 | Evolver Session Scope | BlueCortexCE Session Scope |
|------|-----------------------|---------------------------|
| 隔离机制 | 文件系统子目录 | `session_id` 列 + SQL WHERE |
| 上下文传播 | 环境变量 | 数据库 session_id |
| 跨会话共享 | 显式 disabled | 显式 opt-in |
| 粒度 | 进程级别 | 记录级别 |

---

## 6. 关键设计原则总结

### 6.1 信号升维（Signal Elevation）

原始信号（字符串）→ 结构化标签（problem/action/area/risk）→ 数值评分。这是一个持续的"语义浓缩"过程，每一层都比上一层更易于程序化处理。

### 6.2 有界存储（Bounded Storage）

所有存储都有明确的边界：
- narrativeMemory：30 条或 12KB
- Session scope path：128 字符
- Tag set：去重 + 截断

这使得系统行为可预测，避免"悄悄无限增长"。

### 6.3 标签作为跨模块契约

`learningSignals.expandSignals` 被 5 个不同模块调用，所有模块共享同一个标签词汇表（`problem:*`、`action:*`、`area:*`、`risk:*`）。这是一种隐式的 schema 契约，比显式 API 更轻量但同样有效。

### 6.4 失败聚类而非逐条记录

`candidates.js` 对失败胶囊按标签聚类（≥2 次同类失败才生成候选），而不是为每次失败都生成一条记录。这是一种"模式压缩"——将多个相似失败压缩为一个待解决的模式。

---

## 7. BlueCortexCE 借鉴路径

### P0（可直接借鉴）

1. **失败聚类模式**：BlueCortexCE `ObservationEntity` 可引入"同类观察聚合"逻辑。类似 `candidates.js` 的 `failedCapsules.groupBy(failureTags)`，对 `type=error` 的观察按 `extractedData.error_sig_norm` 分组，聚类阈值≥2。

2. **有界叙事记忆**：BlueCortexCE `SessionEntity` 或独立 `NarrativeEntity` 可引入 `narrativeMemory` 风格的有界保留策略（最大条目数 + 最大大小）。目前 CE 无此机制。

3. **信号标签扩展复用**：`learningSignals.js` 的 Layer 3 关键词模式可直接移植到 CE `AgentService.saveObservation()` 路径，为每个观察注入 `problem:*`/`action:*`/`area:*` 标签，存储在 `extractedData` JSONB 中。

### P1（需要适度适配）

4. **Five Questions Shape 启发**：Evolver 的 `buildFiveQuestionsShape` 将问题转化为有边界条件的技能需求描述。CE 的 Phase 3 Structured Extraction 可参考此格式设计"用户偏好模板"的 schema field。

5. **Session Scope 隔离**：CE 的 `session_id` 列已经实现了记录级隔离。`paths.js` 的进程级 scope 隔离思路可用于多租户场景（如不同 Discord server 的完全状态隔离）。

### P2（长期架构参考）

6. **标签作为跨模块隐式契约**：Evolver 的 5 个模块通过共享标签词汇表实现松耦合。CE 可以考虑在 `SearchService` / `ContextService` / `AgentService` 之间引入类似的标签契约（如 `capability:*`、`memory:*` 标签），使服务间集成更平滑。

---

## 附录：模块依赖图

```
learningSignals.js
  ↑ 被调用 by:
  ├── selector.js (scoreTagOverlap / expandSignals)
  ├── candidates.js (expandSignals → tags)
  ├── policyCheck.js (expandSignals → policy tags)
  └── skillDistiller.js (expandSignals → asset metadata)

candidates.js
  ↑ 被调用 by:
  └── (由 evolve.js 主循环调用)

narrativeMemory.js
  ↑ 被调用 by:
  └── evolve.js (recordNarrative after each cycle)
  ↓ 读取 by:
  └── evolve.js (loadNarrativeSummary for prompt injection)

paths.js
  ↑ 被调用 by:
  ├── (几乎所有模块)
  ├── memoryGraph.js (getEvolutionDir)
  ├── candidates.js (getEvolutionDir for state file)
  ├── narrativeMemory.js (getNarrativePath / getEvolutionDir)
  └── evolve.js (所有路径解析)
```
