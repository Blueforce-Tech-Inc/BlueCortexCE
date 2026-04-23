# Evolver 运行时编排：自适应策略、候选评估与自我修复

> **角色**：补充 [`24`](./24-gene-strategy-layer.md)（Gene/Strategy 层）和 [`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md)（高级模式）未覆盖的**运行时编排模式**。  
> **数据来源**：`src/evolve.js`（`computeAdaptiveStrategyPolicy`）、`src/gep/candidateEval.js`（候选提取与匹配）、`src/ops/self_repair.js`（Git 自修复）、`src/ops/innovation.js`（创新催化）、`src/gep/localStateAwareness.js`（自我感知）。  
> **前置**：先读 [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md)（主循环）、[`24`](./24-gene-strategy-layer.md)（Gene/Strategy）。  
> **状态**：v1 初稿（2026-04-23）

---

## 1. 自适应策略策略（`computeAdaptiveStrategyPolicy`）

Evolver 的核心创新之一：**每个进化周期动态计算执行策略**，而非使用静态配置。这是从 `strategy.js` 的静态 presets 到运行时自适应的关键跃迁。

### 1.1 多因子决策矩阵

```javascript
// evolve.js — computeAdaptiveStrategyPolicy(opts)
// 输入：recentEvents, selectedGene, signals
// 输出：{ name, label, forceInnovate, cautiousExecution, highRiskGene,
//         repairStreak, failureStreak, blastRadiusMaxFiles, directives[] }
```

| 因子 | 数据源 | 影响 |
|------|--------|------|
| **Repair streak** | `recentEvents[-8]` 连续 `intent=repair` 计数 | ≥3 → 强制创新 |
| **Failure streak** | `recentEvents[-8]` 连续 `outcome=failed` 计数 | ≥2 → 谨慎执行；≥3 → 强制创新 |
| **Anti-pattern overlap** | `selectedGene.anti_patterns` × 当前 `signals` 的 Jaccard 匹配 | hard≥1 或 soft≥2 且 0 成功 → 高风险标记 |
| **Learning history** | `selectedGene.learning_history[-6]` 成功率 | 结合 anti-pattern 评估风险 |
| **Stagnation signals** | `stable_success_plateau` / `evolution_saturation` / `empty_cycle_loop_detected` | 触发 `forceInnovate` |

### 1.2 Blast Radius 动态控制

```
base = selectedGene.constraints.max_files || 12

if (cautiousExecution)  → max(2, min(base, 6))   // 收窄到 2–6 文件
else if (forceInnovate) → max(3, min(base, 10))   // 适度放宽 3–10
```

**关键设计**：
- 谨慎模式下 blast radius **硬上限 6 文件**，即使 Gene 允许更多
- 强制创新模式下放宽但不超过 10，防止失控
- 每个周期的 `blastRadiusMaxFiles` 写入 solidify state，供后续审计

### 1.3 Directives 注入

策略输出的 `directives[]` 数组直接注入 GEP prompt，作为 LLM 的**软约束**：

```
"Base strategy: Hardening (After a big change. Focus on stability.)"
"Force strategy shift: prefer innovate over repeating repair/optimize."
"Selected gene is high risk for current signals; keep blast radius narrow."
"Target max files for this cycle: 6."
```

### 1.4 BlueCortexCE 借鉴

| Evolver 概念 | CE 翻译方案 |
|--------------|------------|
| Repair streak → 强制创新 | 连续 N 次"补充信息"注入失败 → 切换注入策略（时间线→语义） |
| Failure streak → 谨慎执行 | 连续写入失败 → 降低批量大小、增加校验 |
| Blast radius 动态控制 | 上下文注入 token 预算动态调整：错误多→缩减，稳定→放宽 |
| Anti-pattern overlap | 历史失败模式匹配：同类错误重复 → 抑制相似注入 |
| Directives 注入 | `generateContext` 时注入动态指令到 prompt 模板 |

---

## 2. 候选评估管线（`candidateEval.js`）

Evolver 的**能力发现机制**：从会话转录中自动提取重复模式，转化为可复用的能力候选。

### 2.1 三阶段管线

```
Session Transcript → extractToolCalls() → countFreq() → filter(count≥3)
                                                        ↓
                                              buildFiveQuestionsShape()
                                                        ↓
                                              appendCandidateJsonl()  [持久化]
                                                        ↓
                                              renderCandidatesPreview()  [LLM 上下文]
```

### 2.2 工具调用模式提取

```javascript
// 两种格式兼容：
// OpenClaw: [TOOL: Shell]
// Cursor:   [Tool call] Shell
function extractToolCalls(transcript) {
  // 返回工具名列表
}
```

**频率阈值**：同一工具调用 ≥3 次才生成候选，避免噪声。

### 2.3 Five Questions Shape

每个候选生成结构化的"五问"框架：

```javascript
{
  title: "Repeated tool usage: Shell",
  input: "Recent session transcript + memory snippets + user instructions",
  output: "A safe, auditable evolution patch guided by GEP assets",
  invariants: "Protocol order, small reversible patches, validation, append-only",
  params: "Signals: log_error, recurring_tool_usage",
  failure_points: "Missing signals, over-broad changes, skipped validation",
  evidence: "Observed 7 occurrences of tool call marker for Shell."
}
```

### 2.4 外部候选匹配

`candidateEval.js` 还处理来自 Hub 的外部候选（Gene 和 Capsule）：

```javascript
// External Gene: 按 signals_match 与当前 signals 的命中数排序
// External Capsule: 按 trigger 与当前 signals 的命中数排序
// 各取 top 3 注入 LLM 上下文
```

### 2.5 BlueCortexCE 借鉴

| Evolver 概念 | CE 翻译方案 |
|--------------|------------|
| 工具调用频率分析 | 观察类型频率统计：同一类型连续出现 → 标记为"高频模式" |
| Five Questions Shape | 观察质量模板：每条观察附带 input/output/invariants 元数据 |
| 外部候选匹配 | 跨会话模式复用：历史成功注入模式推荐给新会话 |
| Candidate JSONL 持久化 | `ObservationEntity.extractedData.candidate_pattern` JSONB |

---

## 3. Git 自修复（`self_repair.js`）

Evolver 的**容错机制**：在进化周期开始前自动修复 Git 状态异常。

### 3.1 修复步骤

```
1. git rebase --abort    → 修复中断的 rebase
2. git merge --abort     → 修复中断的 merge
3. 删除 stale index.lock → 清理过期锁文件（> LOCK_MAX_AGE_MS）
4. git fetch origin      → 安全同步远程
```

### 3.2 强制重置（受控）

```javascript
// 仅当 EVOLVE_GIT_RESET=true 时启用
if (process.env.EVOLVE_GIT_RESET === 'true') {
  git fetch origin main
  git reset --hard origin/main  // 最后手段
}
```

### 3.3 BlueCortexCE 借鉴

| Evolver 概念 | CE 翻译方案 |
|--------------|------------|
| Git 状态自检 | 写入前自检：数据库连接、表空间、事务状态 |
| Stale lock 清理 | 过期会话清理：`SessionEntity` 中超时未结束的会话 |
| 安全 fetch | 健康检查端点自修复：`/api/health` 检测异常后自动重启 |
| 受控 hard reset | 数据库迁移回滚：标记而非删除，保留审计日志 |

---

## 4. 创新催化器（`innovation.js`）

当系统检测到**停滞**时，自动生成创新建议。

### 4.1 技能分类分析

```javascript
const categories = {
  'feishu':      skills.filter(s => s.startsWith('feishu-')).length,
  'dev':         skills.filter(s => s.includes('lint') || s.includes('test')).length,
  'media':       skills.filter(s => s.includes('image') || s.includes('video')).length,
  'security':    skills.filter(s => s.includes('security') || s.includes('audit')).length,
  'automation':  skills.filter(s => s.includes('auto-') || s.includes('cron')).length,
  'data':        skills.filter(s => s.includes('db') || s.includes('store')).length
};
```

### 4.2 创新策略

1. **填补空白**：识别最薄弱的 2 个类别，生成针对性建议
2. **优化整合**：技能 >50 时建议合并/废弃冗余技能
3. **元层反思**：始终建议一个性能指标仪表板

### 4.3 BlueCortexCE 借鉴

| Evolver 概念 | CE 翻译方案 |
|--------------|------------|
| 技能分类分析 | 观察类型覆盖度分析：哪些类型从未出现 → 提示用户补充 |
| 薄弱类别发现 | 上下文注入盲区检测：时间线/语义/ICL 各占比 → 平衡注入 |
| 技能合并建议 | 重复观察合并：相似内容的观察 → 生成 summary |

---

## 5. 本地状态感知（`localStateAwareness.js`）

Evolver 的**自我模型**：在每次进化前捕获系统完整状态快照，注入 LLM 上下文。

### 5.1 五维状态捕获

```javascript
function captureLocalState() {
  return [
    '[Node Identity]',     // A2A 节点 ID、密钥状态
    '[Environment Config]',// 环境变量配置清单
    '[Evolution State]',   // 循环计数、人格参数、上次运行
    '[Memory & Knowledge]',// MEMORY.md 大小、图谱大小、叙事状态
    '[Skills]'             // 已安装技能数量
  ].join('\n');
}
```

### 5.2 关键设计

- **幂等读取**：所有文件读取都有 `_readFileSafe` / `_readJsonSafe` 包装，失败返回 null
- **环境变量审计**：列出已配置和缺失的 `A2A_ENV_KEYS`，帮助诊断配置问题
- **人格状态暴露**：`rigor=0.7 creativity=0.5 risk_tolerance=0.3` — 让 LLM 知道当前"性格"
- **路径集中管理**：通过 `captureLocalStatePaths()` 返回所有关键路径，便于调试

### 5.3 BlueCortexCE 借鉴

| Evolver 概念 | CE 翻译方案 |
|--------------|------------|
| Node Identity | 会话上下文：当前 session ID、user ID、channel |
| Evolution State | 注入统计：本次会话已注入条数、token 使用量、剩余预算 |
| Memory State | 存储状态：PostgreSQL 连接池、pgvector 索引健康 |
| Skills State | 能力清单：可用 MCP 工具列表、Hook 启用状态 |
| 状态快照注入上下文 | `/api/context/generate` 返回附加 `system_state` 元数据 |

---

## 6. 运行时编排全景

### 6.1 进化周期中的编排顺序

```
evolve.run()
  ├── 1. self_repair.repair()           → Git 自修复
  ├── 2. readMemorySnippet()            → 读取 MEMORY.md（含 session scope）
  ├── 3. localStateAwareness.capture()  → 捕获系统状态
  ├── 4. extractSignals()               → 提取信号
  ├── 5. shouldSkipHubCalls()           → 饱和检测（跳过 Hub）
  ├── 6. hubSearch()                    → EvoMap-First 搜索（问题信号→扩展搜索）
  ├── 7. getMemoryAdvice()              → 记忆图谱推理
  ├── 8. shouldReflect()                → 自省判断（自适应间隔）
  ├── 9. buildCandidatePreviews()       → 候选提取与匹配
  ├── 10. selectGeneAndCapsule()        → Gene 选择（多因子）
  ├── 11. computeAdaptiveStrategyPolicy() → 动态策略计算 ★
  ├── 12. selectPersonalityForRun()     → 人格选择
  ├── 13. buildMutation()               → 变异生成
  ├── 14. recordHypothesis()            → 假设记录
  ├── 15. recordAttempt()               → 尝试记录
  └── 16. writeStateForSolidify()       → 固化状态写入
```

### 6.2 模块依赖图

```
evolve.js (主循环)
  ├── gep/memoryGraph.js         ← 因果记忆图谱
  ├── gep/memoryGraphAdapter.js  ← 适配器（统一接口）
  ├── gep/signals.js             ← 信号提取
  ├── gep/learningSignals.js     ← 信号标签化（expandSignals）
  ├── gep/selector.js            ← Gene 选择器
  ├── gep/strategy.js            ← 静态策略 presets
  ├── gep/mutation.js            ← 变异生成
  ├── gep/reflection.js          ← 自省循环
  ├── gep/candidates.js          ← 能力候选提取
  ├── gep/candidateEval.js       ← 候选评估管线 ★
  ├── gep/localStateAwareness.js ← 自我感知 ★
  ├── gep/narrativeMemory.js     ← 叙事记忆
  ├── gep/curriculum.js          ← 课程学习
  ├── ops/self_repair.js         ← Git 自修复 ★
  ├── ops/innovation.js          ← 创新催化 ★
  └── ops/lifecycle.js           ← 生命周期管理
```

★ = 本文档新增覆盖

---

## 7. BlueCortexCE 综合借鉴建议

### 7.1 P0（立即可做）

| 建议 | 理由 | 实现路径 |
|------|------|----------|
| 上下文注入动态预算 | Evolver 的 blast radius 控制思想 | `ContextService.generateContext()` 增加 token 预算参数 |
| 写入前自检 | Evolver 的 self_repair 思想 | `AgentService.saveObservation()` 前检查连接/事务 |

### 7.2 P1（近期规划）

| 建议 | 理由 | 实现路径 |
|------|------|----------|
| 观察类型覆盖度分析 | Evolver 的 innovation.js 思想 | 新 API: `GET /api/stats/type-coverage` |
| 注入策略动态切换 | Evolver 的 adaptive strategy | `ContextService` 根据历史成功率切换时间线/语义 |
| 高频模式自动发现 | Evolver 的 candidateEval 思想 | `AgentService` 统计工具调用频率，标记候选 |

### 7.3 P2（长期研究）

| 建议 | 理由 | 实现路径 |
|------|------|----------|
| 系统状态快照注入 | Evolver 的 localStateAwareness | `/api/context/generate` 附加 `system_state` |
| 跨会话候选复用 | Evolver 的 external candidate matching | `SearchService` 增加"成功注入模式"索引 |

---

## 与其它文件的关系

| 文件 | 本文档补充什么 |
|------|---------------|
| [24](./24-gene-strategy-layer.md) | 静态 Strategy presets → 本文档补充**动态自适应** |
| [25](./25-advanced-patterns-prm-epigenetic-antipattern.md) | PRM 评分 / Epigenetic → 本文档补充**运行时编排** |
| [19](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) | 主循环顺序 → 本文档补充**编排细节** |
| [11](./11-research-backlog.md) | 新增候选评估和自适应策略的研究项 |
