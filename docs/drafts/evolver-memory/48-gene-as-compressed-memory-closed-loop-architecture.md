# Gene as Compressed Memory: Evolver 闭环记忆架构综合分析

> **角色**：将分散在 `24`（Gene 层）、`36`（三层记忆综合）、`39`（内容寻址）、`47`（技能提炼）的设计线索**合成为一个连贯的架构洞察**——**Gene 是记忆的压缩可执行形式**。这是 evolver 与传统记忆系统的根本区别。
>
> **数据来源**：`src/gep/memoryGraph.js`、`memoryGraphAdapter.js`、`signals.js`、`selector.js`、`mutation.js`、`assetStore.js`、`solidify.js`、`curriculum.js`、`skillDistiller.js`、`candidates.js`。
>
> **最后更新**：2026-04-25

---

## 目录

- [§1 核心命题：Gene 是压缩的记忆](#s1-核心命题gene-是压缩的记忆)
- [§2 记忆 → 信号 → Gene 选择 → 行为 → Outcome 完整闭环](#s2-记忆--信号--gene-选择--行为--outcome-完整闭环)
- [§3 Gene 的结构：压缩了哪些记忆维度](#s3-gene-的结构压缩了哪些记忆维度)
- [§4 闭环如何关闭：outcome → 边权重 → 未来选择](#s4-闭环如何关闭outcome--边权重--未来选择)
- [§5 内容寻址：基因资产的去重与跨节点一致性](#s5-内容寻址基因资产的去重与跨节点一致性)
- [§6 技能提炼：Gene 的生成路径与质量门控](#s6-技能提炼gene-的生成路径与质量门控)
- [§7 Session Scope 隔离：多租户记忆边界](#s7-session-scope-隔离多租户记忆边界)
- [§8 BlueCortexCE 启示录](#s8-bluecortexce-启示录)

---

## §1 核心命题：Gene 是压缩的记忆

传统记忆系统（如 BlueCortexCE）将记忆存储为**被动数据**：

- Observation 实体：存储对话/动作的观察结果
- Summary 实体：压缩的会话摘要
- Prompt 实体：用户提示的快照

这些记忆被检索出来后，**由外部 agent 决定如何使用**。记忆本身不编码"如何做"。

**Evolver 的设计翻转了这一关系**：记忆被编码为**可直接执行的策略模板（Gene）**，在选择阶段就已经决定了"看到 X 信号 → 执行 Y 行为"。Gene 不是关于过去的描述，而是**对未来行为的压缩指令**。

```
传统记忆系统：
  记忆 = 被动数据 → 等待外部 agent 解读

Evolver Gene：
  记忆 = 压缩的行为指令 → 信号触发 → 直接可执行
```

---

## §2 记忆 → 信号 → Gene 选择 → 行为 → Outcome 完整闭环

```
┌─────────────────────────────────────────────────────────────────┐
│                    完整反馈环路 (closed feedback loop)             │
│                                                                 │
│   ┌──────────┐    extractSignals()                             │
│   │ Session  │──────────────► signals[] ──────────────────┐     │
│   │ Context  │         (多语言 / 错误签名 / 频率分析)          │     │
│   └──────────┘                                                │     │
│                                                               │     │
│   ┌──────────┐    expandSignals()       ┌──────────────────▼────┐│
│   │ Signals  │──────────────────────────►│  Gene Selector        ││
│   │ []       │    (problem:repair 等    │  - exact match (score)││
│   │          │     标签扩展)             │  - semantic (cosine) ││
│   └──────────┘                           │  - learning boost    ││
│                                         │  - anti-pattern ban  ││
│   ┌──────────┐  getMemoryAdvice()        │  - memory edge score││
│   │ Memory   │◄──────────────────────────│                      ││
│   │ Graph    │  (Jaccard≥0.34 历史边)    └──────────────────▲────┘│
│   │ Advice   │                                            │      │
│   └──────────┘                                            │      │
│                                                         │      │
│   ┌──────────────────────────────────────────────────────────────┐│
│   │  selected Gene ──► execute action ──► outcome inference     ││
│   └──────────────────────────────────────────────────────────────┘│
│                              │                                   │
│                              ▼                                   │
│   ┌──────────┐    recordOutcomeFromState()                     │
│   │ Outcome  │──────────────► memory_graph.jsonl (append)       │
│   │ event    │               更新 (signal_key, gene_id) 边权重   │
│   └──────────┘                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**闭环特性**：
1. **信号触发**：`extractSignals` 从 session context 生成信号数组
2. **记忆查询**：`getMemoryAdvice` 基于 `signal_key` 在 JSONL 历史中查 Jaccard≥0.34 的相似信号，获取 `(signal_key, gene_id)` 边权重
3. **Gene 选择**：多因子评分（exact match + semantic cosine + learning history + anti-pattern penalty）
4. **行为执行**：Gene 策略模板指导实际代码变更
5. **Outcome 记录**：推断成功/失败，追加 outcome 事件到 JSONL，更新边权重

**关键**：Outcome 的推断不是简单的二元判断，而是：
- `inferOutcomeEnhanced` 综合了 `log_error` 信号变化 + 错误计数 delta + scan 时间 delta
- Laplace 平滑避免 0/1 极端
- 半衰衰减（边 30 天，gene 先验 45 天）避免古老记忆主导

---

## §3 Gene 的结构：压缩了哪些记忆维度

一个 Gene 实体不是单一字段，而是一个**多维记忆压缩包**：

```javascript
{
  type: 'Gene',
  id: 'gene_gep_repair_from_errors',
  category: 'repair',                    // ← 行动类别（repair/optimize/innovate）
  signals_match: [                       // ← 触发条件：什么样的信号激活此 Gene
    '/error|exception|failed/i',
    'user_feature_request:...',
  ],
  summary: '...',                        // ← 人类可读摘要
  strategy: [                           // ← 核心：行为策略序列（可执行步骤）
    'Extract structured signals from logs...',
    'Select an existing Gene by signals match...',
    'Apply smallest reversible patch...',
  ],
  constraints: {                        // ← 约束边界
    max_files: 12,
    forbidden_paths: ['.git', 'node_modules'],
  },
  validation: [                         // ← 验证步骤
    'node scripts/validate-modules.js ...',
  ],
  epigenetic_marks: [                    // ← 环境适应记忆（跨环境差异化）
    { context: 'darwin/arm64/node20', boost: 0.15 },
  ],
  anti_patterns: [                       // ← 已知反模式（触发惩罚）
    { mode: 'hard', learning_signals: ['tool_bypass'] },
  ],
  learning_history: [                   // ← 学习历史（近期 outcome 加权）
    { outcome: 'success', mode: 'soft', ts: '...' },
  ],
}
```

**与 BlueCortexCE Observation 的对比**：

| 维度 | BlueCortexCE Observation | Evolver Gene |
|------|-------------------------|--------------|
| 触发条件 | observation_types（静态标签） | signals_match（动态模式匹配） |
| 行为指导 | 无（外部解读） | strategy 数组（直接可执行） |
| 约束 | 无 | constraints + validation |
| 历史学习 | 无内置 | learning_history + anti_patterns |
| 环境适应 | 无 | epigenetic_marks |

---

## §4 闭环如何关闭：outcome → 边权重 → 未来选择

### 4.1 边聚合（Edge Aggregation）

`memoryGraph.js` 的 `aggregateEdges` 从 JSONL 中聚合所有 `kind === 'outcome'` 事件：

```javascript
// 按 (signal_key, gene_id) 聚合
const k = `${signalKey}::${geneId}`;
const cur = map.get(k) || { success: 0, fail: 0, last_ts: null, last_score: null };
if (status === 'success') cur.success += 1;
else if (status === 'failed') cur.fail += 1;
```

**核心数据结构**：
```
signal_key_A  ──(gene_id_X, success=3, fail=1, p=0.67)──►  边
signal_key_A  ──(gene_id_Y, success=1, fail=4, p=0.33)──►  边
signal_key_B  ──(gene_id_X, success=5, fail=0, p=0.86)──►  边
```

### 4.2 置信度计算（Edge Expected Success）

```javascript
function edgeExpectedSuccess(edge, opts) {
  const succ = Number(edge.success) || 0;
  const fail = Number(edge.fail) || 0;
  const total = succ + fail;
  const p = (succ + 1) / (total + 2);  // Laplace 平滑
  const w = decayWeight(edge.last_ts || '', halfLifeDays);  // 指数半衰
  return { p, w, value: p * w };  // 置信度 = 平滑概率 × 衰减权重
}
```

- **Laplace 平滑** `(succ+1)/(total+2)`：避免 0 次尝试就产生 100% 或 0% 置信度
- **半衰衰减** `0.5^(age/halfLife)`：30 天前的数据权重约为 0.5，45 天前约为 0.3

### 4.3 Jaccard 相似信号键匹配

```javascript
function jaccard(aList, bList) {
  const aNorm = normalizeSignalsForMatching(aList);  // 含 errsig 规范化
  const a = new Set(aNorm.map(String));
  const b = new Set(bNorm.map(String));
  const inter = [...a].filter(x => b.has(x)).length;
  const union = a.size + b.size - inter;
  return union === 0 ? 0 : inter / union;
}
```

- `Jaccard ≥ 0.34` 才算相似（不是精确匹配）
- `errsig:<raw>` → `errsig_norm:<stableHash>`，使同类错误可跨次匹配

### 4.4 选择时的评分组合

```javascript
// getMemoryAdvice() 中的最终评分
const combined = info.best > 0
  ? info.best + info.prior * 0.12   // 信号边 + 基因全局先验(弱权重)
  : info.prior * 0.4;               // 仅基因先验时更弱

scoredGeneIds.push({ geneId, score: combined, attempts: info.attempts, prior: info.prior });

// 低效路径压制（非 drift 模式）
if (!driftEnabled && info.attempts >= 2 && info.best < 0.18) {
  bannedGeneIds.add(geneId);  // 两次以上尝试 + 置信度 < 0.18 → ban
}
```

**启示**：闭环的效果是**动态调整 Gene 的选择优先级**，而非静态排名。每次 cycle 的 outcome 都在更新边的置信度，使系统自然趋向"对当前信号类型更有效的 Gene"。

---

## §5 内容寻址：基因资产的去重与跨节点一致性

### 5.1 为什么需要内容寻址

Evolver 作为**多节点联邦系统**（多个 evolver 实例 + Hub 目录），需要：
- 相同 Gene 策略在所有节点上拥有**相同的资产 ID**
- 防止相同内容的资产被重复创建
- 支持资产的跨节点验证（内容是否被篡改）

### 5.2 Canonical JSON + SHA-256

```javascript
// contentHash.js
function computeAssetId(obj) {
  const canonical = canonicalize(obj);  // 键排序 + 字符串转义
  const hash = crypto.createHash('sha256').update(canonical, 'utf8').digest('hex');
  return `asset_${hash.slice(0, 16)}`;
}
```

**关键特性**：
- `canonicalize` 保证： `{a:1, b:2}` 和 `{b:2, a:1}` 产生相同的 canonical string
- SHA-256 输出 64 字符，取前 16 字符作为短 ID
- **幂等写入**：`assetStore.js` 的 `storeAsset` 在写入前检查 ID 是否已存在

### 5.3 资产存储结构

```
genes.json         ← 基因资产清单（含 asset ID）
capsules.json      ← 成功凝固的知识胶囊
candidates.json    ← 待评估的候选能力
evolution_events.jsonl  ← 不可变事件日志
```

**幂等写入语义**：
```javascript
// assetStore.js
function storeAsset(assets, newAsset) {
  const id = computeAssetId(newAsset);
  const existing = assets.find(a => a.id === id);
  if (existing) return { stored: false, id, existing };  // 已存在，跳过
  assets.push({ ...newAsset, id });
  return { stored: true, id };
}
```

### 5.4 BlueCortexCE 借鉴

BlueCortexCE 的 `ObservationEntity` 和 `SummaryEntity` 目前没有内容寻址。如果 BlueCortexCE 要支持：
- **跨节点去重**：相同内容的 observation 不重复存储
- **完整性验证**：内容是否被篡改
- **引用稳定性**：`assetId` 在任何节点上指向相同内容

建议：在 `ObservationEntity` 添加 `content_hash` 字段（SHA-256 of canonicalized observation content），在 ingest 时做幂等检查。

---

## §6 技能提炼：Gene 的自动生成路径与质量门控

### 6.1 候选发现（Candidates）

`candidates.js` 从 session transcript 中自动发现能力缺口：

```javascript
// extractCapabilityCandidates()
if (count >= 3) {  // 工具调用 ≥ 3 次
  const title = `Repeated tool usage: ${tool}`;
  const evidence = `Observed ${count} occurrences of tool call marker for ${tool}.`;
  candidates.push({ type: 'CapabilityCandidate', id, title, evidence, ... });
}
```

候选发现信号：
- **高工具使用**：`high_tool_usage:<tool>` (≥10 次调用)
- **重复工具使用**：`repeated_tool_usage:exec` (exec ≥5 次)
- **能力缺口**：`capability_gap`
- **用户功能请求**：`user_feature_request`

### 6.2 技能提炼管线（SkillDistiller → Gene）

```
CapabilityCandidate
       │
       ▼ (candidateEval: ROI 评分)
  passed? ──No──► rejected candidates
       │Yes
       ▼
  extractExecutionTrace()  ──► 三级脱敏（full / concise / minimal）
       │
       ▼
  Pattern Analysis (LLM)   ──► strategy + signals_match + constraints
       │
       ▼
  ValidationReport (content-hash) ──► git diff 验证
       │
       ▼
  geneTemplate ──► genes.json (幂等写入)
       │
       ▼
  SkillPublisher          ──► SKILL.md market format
```

**三级脱敏（ExecutionTrace）**：

| 等级 | 内容 | 用途 |
|------|------|------|
| `full` | 完整 transcript，含文件路径、变量名 | 深度分析 |
| `concise` | 移除个人身份信息、具体路径 | SkillDistiller 分析 |
| `minimal` | 仅保留行为模式（无上下文） | 发布到 Marketplace |

### 6.3 Curriculum：基于掌握的课程学习

`curriculum.js` 维护每个 Gene/Capsule 的掌握度状态：

```javascript
// 三区分类
const MASTERY_THRESHOLD = 0.8;   // ≥80% 成功率 = 已掌握
const FAILURE_THRESHOLD = 0.3;   // ≤30% 成功率 = 持续失败

// 课程信号生成
if (zone === 'mastery') {
  signals.push('stable_success_plateau');
} else if (zone === 'learning') {
  signals.push('curriculum_target');
} else if (zone === 'failure') {
  signals.push('capability_gap');
}
```

**关键**：Curriculum 信号直接影响 Gene Selector 的决策——处于 failure 区的 Gene 对应的 capability_gap 信号会触发"寻找新 Gene"的创新行为。

---

## §7 Session Scope 隔离：多租户记忆边界

### 7.1 设计动机

Evolver 可被多个项目/Discord 频道/用户同时使用。如果记忆不隔离：

- 项目 A 的 Gene/Capsule 污染项目 B 的选择
- 频道 A 的 signal×gene 边影响频道 B 的 outcome 推断
- 调试时无法分离不同上下文的演化历史

### 7.2 实现

```javascript
// paths.js
function getSessionScope() {
  const raw = String(process.env.EVOLVER_SESSION_SCOPE || '').trim();
  if (!raw) return null;
  // Sanitize: 防止路径遍历
  const safe = raw.replace(/[^a-zA-Z0-9_\-\.]/g, '_').slice(0, 128);
  return safe;
}

function getEvolutionDir() {
  const baseDir = process.env.EVOLUTION_DIR || path.join(getMemoryDir(), 'evolution');
  const scope = getSessionScope();
  if (scope) {
    return path.join(baseDir, 'scopes', scope);  // ← 隔离子目录
  }
  return baseDir;
}
```

**隔离的存储层次**：
```
memory/
└── evolution/
    └── scopes/
        ├── discord_channel_12345/
        │   ├── memory_graph.jsonl
        │   ├── memory_graph_state.json
        │   └── genes.json
        └── project_backend/
            ├── memory_graph.jsonl
            ├── memory_graph_state.json
            └── genes.json
```

### 7.3 隔离边界

| 资源 | 隔离范围 |
|------|---------|
| `memory_graph.jsonl` | Session scope |
| `memory_graph_state.json` | Session scope |
| `genes.json` | Session scope |
| `capsules.json` | Session scope |
| `evolution_narrative.md` | Session scope |

**不隔离**（全局）：
- Evolver 自身代码（`src/`）
- 配置文件（`assets/gep/` 下默认 preset）
- Hub 目录（跨 scope 的能力发现）

### 7.4 BlueCortexCE 借鉴

BlueCortexCE 目前**没有 session scope 隔离**——所有 session 共享同一张 `SessionEntity` 表。这意味着：
- `getMemoryAdvice` 的信号聚合会跨 session 混合
- Gene Pool 的 outcome 边会跨用户污染

建议：BlueCortexCE 添加 `scope_id` 字段到 `SessionEntity`，在信号聚合和 gene 选择时增加 scope 过滤条件。

---

## §8 BlueCortexCE 启示录

### 8.1 P0 优先级（立即可落地）

**1. 内容寻址 + 幂等写入**

BlueCortexCE 的 `ObservationEntity` 和 `SummaryEntity` 可以添加 `content_hash` 字段（canonical JSON + SHA-256），在 ingest 时检查重复。避免相同 observation 被重复存储，支持跨节点一致性验证。

**2. 错误签名规范化**

在 `ObservationService` 中，对包含错误信息的 observation 自动提取 `error_signature_normalized` 字段（路径→`<path>`，数字→`<n>`，stableHash）。通过该字段实现跨会话的错误模式聚合检索。

**3. Outcome 置信度计算**

BlueCortexCE 的"观察类型有效性"评分可引入 **Laplace 平滑 + 半衰衰减**：
```javascript
const p = (successCount + 1) / (totalCount + 2);
const w = Math.pow(0.5, ageDays / halfLifeDays);
const effectiveScore = p * w;
```

### 8.2 P1 优先级（需要架构调整）

**4. Gene 作为压缩行为策略**

BlueCortexCE 的 observation_types 可升级为**行为策略模板**：
- 不仅存储"这是什么观察"，还存储"遇到此类信号时推荐的行为"
- 在 `SearchService` 返回匹配结果时，附加 `recommended_action_strategy`
- 策略来自 LLM 对历史成功 outcome 的分析

**5. 反馈环路闭合**

BlueCortexCE 目前是"单向写入"系统——observation 写进去，但不会直接影响未来的 context 生成质量。

建议在 `ContextService` 中引入：
- 基于历史 outcome 置信度调整 observation 的注入权重
- 高置信度（多次成功）的 observation 在 context 中优先级更高
- 低置信度或长期未引用的 observation 权重衰减

**6. Session Scope 隔离**

为 BlueCortexCE 引入 `scope_id` 字段到 `SessionEntity`，在 `SearchService` 和 `ContextService` 的聚合逻辑中增加 scope 过滤。

### 8.3 P2 优先级（长期架构演进）

**7. 技能提炼管线**

BlueCortexCE 可以实现与 Evolver `SkillDistiller` 相似的机制：
- 从高频成功 observation 序列中提炼"最佳实践模式"
- 生成的策略模板可作为 `ObservationTypeMapper` 的新条目
- 通过 ValidationReport 确保提炼质量

**8. 适配器模式（Local/Romote）**

Evolver 的 `MemoryGraphAdapter` 展示了如何用适配器模式支持本地优先、远程增强的存储架构。BlueCortexCE 未来如要支持多节点联邦，可以参考：
- 本地 PostgreSQL + pgvector 作为 source of truth
- 可选的远程知识图谱服务作为读增强
- 写操作始终先写本地

---

## 附录：核心文件索引

| 文件 | 职责 |
|------|------|
| `memoryGraph.js` | Append-only JSONL 事件存储、边聚合、置信度计算 |
| `memoryGraphAdapter.js` | 适配器模式（local default / remote optional） |
| `signals.js` | 信号提取、多语言、频率抑制、饱和检测 |
| `learningSignals.js` | 信号→标签扩展、Gene 标签匹配 |
| `selector.js` | Gene 多因子选择（exact + semantic + learning + anti-pattern） |
| `mutation.js` | 基因变异、策略预设 |
| `assetStore.js` | 内容寻址资产存储、幂等写入 |
| `contentHash.js` | Canonical JSON + SHA-256 哈希 |
| `candidates.js` | 能力候选发现 |
| `skillDistiller.js` | 候选→Gene 提炼管线 |
| `skillPublisher.js` | SKILL.md 市场格式生成 |
| `curriculum.js` | 基于掌握的课程学习系统 |
| `solidify.js` | 验证报告、Canary 健康检查、知识凝固 |
| `narrativeMemory.js` | 人类可读的 Markdown 叙事记忆 |
| `reflection.js` | LLM 驱动的战略反思、自适应间隔 |

---

*文档状态：v1 定稿*
