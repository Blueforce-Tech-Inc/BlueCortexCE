# Evolver 高级模式：PRM 评分、表观遗传、反模式与自省循环

> **角色**：补充 [`24-gene-strategy-layer.md`](./24-gene-strategy-layer.md) 和 [`23`](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md) 未覆盖的**高级记忆与学习模式**。  
> **数据来源**：`src/gep/solidify.js`（PRM、epigenetic、failed capsule、auto-publish）、`src/gep/reflection.js`（自省循环）、`src/gep/learningSignals.js`（信号标签化）、`src/gep/prompt.js`（反模式注入、lessons、principles）、`src/ops/innovation.js`（创新催化）。  
> **前置**：先读 [`18`](./18-evolver-local-source-memory-architecture-snapshot.md)（Memory Graph 基础）、[`24`](./24-gene-strategy-layer.md)（Gene/Strategy 层）。  
> **状态**：v1 初稿（2026-04-23）

---

## 1. Process Reward Model（PRM）— 多步骤评分

Evolver 的 outcome score 不是简单的 success/failed 二元判定，而是借鉴 **Process Reward Model** 思想，对进化周期的每个阶段独立评分后加权合成。

### 1.1 八阶段评分维度

| 阶段 | 字段 | 权重 | 评分逻辑 |
|------|------|------|----------|
| Signal 质量 | `signal_quality` | 0.05 | 有信号 ≥ 0.4，每条 +0.1，上限 1.0 |
| Gene 选择 | `gene_selection` | 0.10 | 匹配到 Gene = 0.7；非 auto Gene = 0.9；无匹配 = 0.3 |
| Mutation 质量 | `mutation_quality` | 0.05 | 有 rationale+category = 0.8；risk=low → 0.9；risk=high → 0.6 |
| Blast 控制 | `blast_control` | 0.15 | ≤50% max_files = 1.0；≤100% = 0.7；超出 = 0.2；估算偏差大则再 ×0.5 |
| 约束合规 | `constraint_compliance` | 0.25 | 无违规 = 1.0；每条违规 −0.25 |
| 验证通过率 | `validation_pass_rate` | 0.25 | 通过数/总数；无验证命令 = 0.5（惩罚） |
| 协议合规 | `protocol_compliance` | 0.10 | 无违规 = 1.0；每条违规 −0.3 |
| Canary 健康 | `canary_health` | 0.05 | 通过 = 1.0；失败 = 0 |

### 1.2 关键设计

```
composite = Σ(phase_score × weight)
score = clamp01(composite)   // 保证 [0, 1]
```

- **约束 + 验证** 合计占 50% 权重 — 安全性优先
- **Blast 控制** 占 15% — 鼓励小范围修改
- **验证无命令** 惩罚 0.5 — 强制定义验证步骤
- 估算偏差（estimate vs actual）会额外惩罚 blast 分数

### 1.3 BlueCortexCE 借鉴

| Evolver PRM 概念 | CE 翻译方案 |
|------------------|------------|
| `signal_quality` | 观察信号丰富度：信号条数 → 分数 |
| `constraint_compliance` | 写入约束合规：路径校验、大小限制 |
| `validation_pass_rate` | 回归测试通过率：`scripts/regression-test.sh` |
| `blast_control` | 上下文注入范围：注入条数 vs token 预算 |
| 加权合成 | `ObservationEntity.extractedData.process_scores` JSONB |

---

## 2. Epigenetic Marks — 环境适应印记

### 2.1 设计思想

不同于 mutation（改变 Gene 本体），epigenetic marks 是**环境特异性的表达调节器**，类比生物 DNA 甲基化：

- **成功** → 在当前环境上下文上增加 boost（+0.05），强化表达
- **失败** → 减少 boost（−0.1），抑制表达
- **衰减** → 90 天过期，最多保留 10 条

### 2.2 数据结构

```javascript
{
  context: "darwin/arm64/v22.14.0",   // 环境指纹拼接
  boost: 0.15,                        // [-0.5, +0.5]，正=强化，负=抑制
  reason: "reinforced_by_success",     // success_in_environment | suppressed_by_failure
  created_at: "2026-04-23T..."
}
```

### 2.3 选择器集成

在 `selector.js` 中，epigenetic boost 与 exact match、semantic overlap、learning signals 一起作为**多因子选择**的加法项：

```
final_score = base_match + semantic_overlap × 0.3 + epigenetic_boost + learning_bonus
```

### 2.4 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| epigenetic marks | `ObservationEntity.extractedData.env_boost`：记录观察在特定环境下的有效性 |
| 环境指纹 | CE 部署环境：Java 版本、DB 类型（pgvector vs Chroma）、OS |
| 表达调节 | 检索排序时，同环境的观察获得 `env_boost` 加分 |
| 衰减机制 | 复用 CE 已有的 `time_decay_score`，90 天自然衰减 |

---

## 3. Failed Capsules 与 Anti-Pattern Zone

### 3.1 失败固化（Failed Capsule）

失败的进化不会被丢弃，而是以 `FailedCapsule` 形式保存：

```javascript
{
  type: 'Capsule',
  id: 'failed_capsule_<timestamp>',
  outcome: { status: 'failed', score: 0.23 },
  gene: 'gene_auto_xxx',
  trigger: ['log_error', 'errsig:xxx'],
  diff_snapshot: '...',               // 失败时的 diff 快照
  failure_reason: 'constraint_violated: ...',
  learning_signals: ['problem:reliability', 'action:repair'],
  constraint_violations: [...],
}
```

**关键**：失败后先捕获 diff snapshot，再 rollback — 保留失败证据。

### 3.2 Anti-Pattern Zone（提示词注入）

在 `prompt.js` 的 `buildAntiPatternZone()` 中，将历史失败注入到提示词：

```
Context [Anti-Pattern Zone] (AVOID these failed approaches):
  1. Gene: gene_auto_xxx | Signals: [log_error, errsig:abc]
     Failure: constraint_violated: forbidden_path
     Diff (first 500 chars): ...
```

**匹配逻辑**：当前信号与失败 Capsule 的 trigger 有 ≥ 40% 重叠才注入 — 避免噪声。

### 3.3 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| Failed Capsule | `ObservationEntity` 中 `type=error` 且 `extractedData.outcome=failed` 的记录 |
| Anti-Pattern Zone | `generateContext` 输出中，同类错误的失败上下文优先展示 |
| 40% 信号重叠 | Jaccard 相似度 ≥ 0.34（复用 CE 已有的 `computeSignalKey` 逻辑） |
| diff snapshot | `extractedData.diff_hash` 或 `error_sig_norm` 用于去重 |

---

## 4. Lessons Block 与 Principles Block

### 4.1 Lessons Block（跨 Agent 经验）

从 EvoMap Hub 拉取的其他 Agent 的经验教训，分正负两类注入提示词：

```
Context [Lessons from Ecosystem] (Cross-agent learned experience):
  Strategies that WORKED:
    - [timeout-retry] Adding exponential backoff reduced 429 errors by 80% (from: node_abc)
  Pitfalls to AVOID:
    - [bulk-delete] Mass file deletion caused cascade failures (from: node_def)
```

**筛选逻辑**：信号重叠匹配，正负各最多 3 条。

### 4.2 Principles Block（进化原则）

从 `evolution_principles.md` 文件读取的指导原则，注入提示词：

```
Context [Evolution Principles] (Guiding directives -- align your actions):
  1. Prefer reversible changes over destructive ones
  2. Always validate before solidify
  ...
```

### 4.3 Constitutional Ethics（宪法伦理）

硬编码在提示词中的不可违反规则：

1. **人类福祉优先** — 不创建有害工具
2. **碳硅共生** — 服务双方利益
3. **透明性** — 所有操作可审计
4. **公平性** — 不创建垄断策略
5. **安全性** — 不绕过安全机制

违反 = `FAILED + rollback`，理由 `ethics_violation: <原则>`。

### 4.4 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| Lessons Block | `SearchService` 检索结果中注入"同类问题的成功/失败历史" |
| Principles Block | `docs/guidelines/` 下的开发原则，在 `generateContext` 中按需注入 |
| Constitutional Ethics | 写入约束：路径白名单、大小限制、敏感数据检测 |

---

## 5. Innovation Catalyst — 停滞检测与强制创新

### 5.1 停滞信号

当以下信号出现时，触发创新催化：

- `evolution_stagnation_detected`
- `stable_success_plateau`
- `repair_loop_detected`
- `force_innovation_after_repair_loop`
- `empty_cycle_loop_detected`
- `evolution_saturation`

### 5.2 创新想法生成

`ops/innovation.js` 分析已安装 skills 的类别分布，找出弱势领域：

```javascript
const categories = {
  'feishu': skills.filter(s => s.startsWith('feishu-')).length,
  'dev': skills.filter(s => s.startsWith('git-') || ...).length,
  'media': ...,
  'security': ...,
  'automation': ...,
  'data': ...,
};
// 取最弱 2 个类别，生成具体创新建议
```

### 5.3 强制创新指令

停滞检测后，提示词注入 CRITICAL STAGNATION DIRECTIVE：

```
*** CRITICAL STAGNATION DIRECTIVE ***
You MUST choose INTENT: INNOVATE.
You MUST NOT choose repair or optimize unless critical blocking error.
```

### 5.4 连续失败感知

`historyBlock` 检查最近 8 个周期：如果连续 3+ 个 repair 使用同一 Gene，强制切换到 innovate。

### 5.5 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| 停滞检测 | `TimelineService` 检测"连续 N 条同类观察无新意"→ 触发告警 |
| 创新催化 | 告警时注入"建议探索新方向"的上下文 |
| 连续失败感知 | `SearchService` 增加"重复失败模式"检测，权重降级 |
| 强制创新 | CE 不需要（旁路系统不做自主行动），但可作为"检索结果去偏"启发 |

---

## 6. Learning Signals — 结构化标签扩展

### 6.1 `expandSignals()` 函数

将原始信号转换为结构化标签：

| 原始信号模式 | 生成标签 |
|-------------|---------|
| `error/exception/failed/log_error/429` | `problem:reliability`, `action:repair` |
| `protocol/prompt/audit/schema/drift` | `problem:protocol`, `action:optimize`, `area:prompt` |
| `perf/bottleneck/latency/slow` | `problem:performance`, `action:optimize` |
| `feature/capability_gap/user_feature_request` | `problem:capability`, `action:innovate` |
| `stagnation/plateau/empty_loop` | `problem:stagnation`, `action:innovate` |
| `task/worker/heartbeat/hub` | `area:orchestration` |
| `memory/narrative/reflection` | `area:memory` |
| `skill/dashboard` | `area:skills` |
| `validation/canary/rollback` | `risk:validation` |

### 6.2 Gene 标签化

`geneTags(gene)` 将 Gene 的 category、signals_match、id、summary 统一扩展为标签集。

### 6.3 重叠评分

`scoreTagOverlap(gene, signals)` 计算 Gene 标签与当前信号标签的交集大小。

### 6.4 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| `expandSignals` | `ObservationEntity.type` + `extractedData.tags` 的自动标签化 |
| `problem:*` 标签 | CE 观察类型的语义扩展：`error` → `problem:reliability` |
| `area:*` 标签 | CE 观察的作用域：`area:api`, `area:db`, `area:ui` |
| 标签重叠评分 | `SearchService` 检索时使用标签匹配作为排序因子 |

---

## 7. Adaptive Reflection — 自适应自省循环

### 7.1 动态间隔

`reflection.js` 根据最近 3 个周期的 outcome 动态调整自省间隔：

| 最近 3 个周期 | 间隔（周期数） |
|--------------|---------------|
| 全部成功 | 8（宽松，减少开销） |
| 全部失败 | 3（密集，尽快调整） |
| 混合 | 5（默认） |

### 7.2 冷却机制

自省日志文件 30 分钟冷却期 — 避免频繁自省。

### 7.3 自省上下文构建

`buildReflectionContext()` 组装 LLM 提示词：

- 最近 10 个周期的成功/失败统计
- Intent 和 Gene 使用分布
- 当前信号（截断 20 条）
- Memory Graph Advice（偏好/禁止 Gene）
- 叙事记忆摘要

### 7.4 自省输出

```json
{
  "insights": ["...", "..."],
  "strategy_adjustment": "increase creativity by 0.05",
  "priority_signals": ["log_error", "capability_gap"]
}
```

### 7.5 建议 Mutation

`buildSuggestedMutations()` 根据信号自动建议 personality 调整：

- 停滞信号 → `creativity +0.05`
- 错误信号 → `rigor +0.05`
- 能力缺口 → `risk_tolerance +0.05`

最多 2 条建议。

### 7.6 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| 动态间隔 | CE 不需要自主自省，但可作为"检索频率自适应"启发 |
| 冷却机制 | CE `SearchService` 缓存 TTL 可参考 |
| 自省上下文 | `generateContext` 输出结构可参考：统计 + 信号 + 建议 |
| 建议 Mutation | CE 可用于"观察质量反馈"：成功观察增强权重，失败观察降级 |

---

## 8. Prompt 工程架构总结

Evolver 的提示词是一个**多层上下文注入系统**，每层有明确职责：

| 层 | 来源 | 注入内容 |
|----|------|---------|
| Schema | 硬编码 | 5 个强制 JSON 对象的严格结构 |
| Strategy | Gene | 当前 Gene 的策略步骤 |
| Strategy Policy | `strategy.js` | 自适应策略指令（forceInnovate / cautiousExecution） |
| Innovation | `innovation.js` | 停滞检测时的具体创新建议 |
| History | `solidify.js` | 最近 8 个周期的去重检查 |
| Anti-Pattern | `prompt.js` | 历史失败 Capsule（信号匹配 ≥ 40%） |
| Lessons | Hub API | 跨 Agent 成功/失败经验 |
| Narrative | `narrativeMemory.js` | 叙事记忆摘要（3000 字符截断） |
| Principles | 文件 | 进化原则文件 |
| Execution | 会话上下文 | MEMORY.md + USER.md + 当日日志 |
| Env Fingerprint | 运行时 | 平台/架构/Node 版本 |
| Ethics | 硬编码 | 5 条不可违反的宪法伦理 |

**截断策略**：
- 总提示词上限 `GEP_PROMPT_MAX_CHARS`（默认 50000）
- Execution Context 硬上限 20000 字符
- Capability Candidates 根据是否有 Gene 选择动态调整上限（有 Gene = 500，无 Gene = 2000）
- 信号最多 50 条，每条截断 200 字符

### BlueCortexCE 借鉴

| Evolver 层 | CE `generateContext` 对应 |
|-----------|--------------------------|
| Schema | 不需要（CE 不做自主行动） |
| Strategy | `extractedData.strategy` 或 `TemplateService` 模板 |
| Anti-Pattern | 同类错误的失败观察 |
| Lessons | `SearchService` 检索结果中的成功案例 |
| Narrative | `SummaryEntity` 摘要 |
| Execution | `ObservationEntity` + `UserPromptEntity` |
| 截断策略 | CE 已有 token 预算，可增加分层截断 |

---

## 9. A2A Auto-Publish — 成功与反模式共享

### 9.1 成功自动发布

成功 Capsule 满足以下条件自动发布到 Hub：

1. `EVOLVER_AUTO_PUBLISH != 'false'`（默认开启）
2. `EVOLVER_DEFAULT_VISIBILITY = 'public'`
3. `sourceType != 'reused'`（非直接复用）
4. `score >= MIN_PUBLISH_SCORE`
5. `blast_radius` 安全
6. `success_streak >= BROADCAST_SUCCESS_STREAK`

### 9.2 反模式发布

失败进化可选择性发布为反模式（`EVOLVER_PUBLISH_ANTI_PATTERNS=true`）：

- 仅约束违规或 canary 失败有资格（普通验证失败不算）
- 标记 `anti_pattern: true`
- 包含 `failure_reason` 和 `diff_snapshot`

### 9.3 泄露检测

发布前执行 `fullLeakCheck()`：

- `strict` 模式：发现泄露 → 阻止发布
- 其他模式：发现泄露 → 警告但继续（由 `sanitizePayload` 红化）

### 9.4 BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| 成功发布 | CE 不需要（旁路系统不做 Hub 广播），但成功案例的 `confidence` 评分可参考 |
| 反模式发布 | CE `type=error` 观察可作为"反模式"供后续检索 |
| 泄露检测 | CE 写入时的敏感数据检测（已在 `sanitize.js` 中） |

---

## 10. 与现有文档的关系

| 本文档 | 补充内容 |
|--------|---------|
| [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) | Memory Graph 基础（JSONL 事件、getMemoryAdvice、叙事） |
| [`21`](./21-signal-taxonomy-and-gene-selection-memory.md) | Signal Taxonomy 基础（expandSignals、Jaccard、getMemoryAdvice 链） |
| [`23`](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md) | State+Event 双层、自省循环基础 |
| [`24`](./24-gene-strategy-layer.md) | Gene Pool、Selector、Mutation、Strategy Presets |
| **本文档** | PRM 多步骤评分、Epigenetic Marks、Failed Capsules、Anti-Pattern Zone、Lessons/Principles Block、Innovation Catalyst、Adaptive Reflection、Prompt 工程架构、A2A Auto-Publish |

---

## 11. BlueCortexCE 综合借鉴优先级

| 优先级 | 概念 | CE 落点 | 工作量 |
|--------|------|---------|--------|
| **P0** | PRM 多步骤评分 | `extractedData.process_scores` JSONB | 中 |
| **P0** | Anti-Pattern Zone | `generateContext` 注入同类失败观察 | 中 |
| **P1** | Epigenetic Marks | `extractedData.env_boost` + 检索排序 | 中 |
| **P1** | Learning Signals 标签化 | `extractedData.tags` 自动标签化 | 小 |
| **P2** | Failed Capsules | `type=error` 观察的 diff 保存 | 小 |
| **P2** | Adaptive Reflection | CE 不需要自主自省，低优先 | - |
| **P2** | Innovation Catalyst | CE 不需要自主创新，低优先 | - |
