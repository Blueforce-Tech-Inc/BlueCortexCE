# `69` v1.48–v1.78 重大新子系统与版本差距分析

**文件**: `docs/drafts/evolver-memory/69-v148-v178-major-new-subsystems-and-version-gap-analysis.md`  
**目标**: 记录本地 v1.47.0 与远端 v1.78.1 之间的重大架构差距，为后续接力分析提供导航  
**数据来源**: `git diff e72778e(v1.47.0)..cbc4870(origin/main)` + `git show` 关键文件头  
**最后更新**: 2026-05-02

---

## ⚠️ 版本差距概览

| 维度 | 本地 (v1.47.0) | 远端 (v1.78.1) | 差距 |
|------|----------------|----------------|------|
| Release tag | `e72778e` | `cbc4870` | **+31 版本** |
| 新增文件 | — | `skill2gep.js`, `selfPR.js`, `validator/`, `portable.js`, `claimNudge.js`, `mailboxTransport.js` | 6+ |
| 净增代码 | — | +3,645 行 | — |
| 净删代码 | — | -9,156 行 | — |
| `solidify.js` | ~2,400 行 | ~1,055 行 | **-1,345 行** (重构) |
| `a2aProtocol.js` | ~2,000 行 | ~778 行 | **-1,222 行** (重构) |
| `memoryGraph.js` | ~1,500 行 | ~712 行 | **-788 行** (重构) |
| `selector.js` | ~1,200 行 | ~782 行 | **-418 行** (重构) |
| `skillDistiller.js` | ~1,500 行 | ~265 行 | **-1,235 行** (重构) |
| `policyCheck.js` | ~1,100 行 | ~549 行 | **-551 行** (重构) |
| `prompt.js` | ~1,200 行 | ~617 行 | **-~600 行** (重构) |
| `signals.js` | ~500 行 | ~804 行 | **+304 行** (扩展) |

**总体趋势**: 核心模块大幅精简（代码外置到专用文件），新增三个完整子系统：**Validator 沙箱验证**、**skill2gep 逆向蒸馏**、**Self-PR 自动贡献**。

---

## 1. 新增子系统总览

### 1.1 `src/gep/validator/` — 沙箱验证子系统（完全新增）

```
src/gep/validator/
├── index.js           274 行 — 验证引擎入口
├── sandboxExecutor.js 399 行 — 沙箱执行器
├── stakeBootstrap.js  347 行 — Stake 引导的验证器选择
└── reporter.js        118 行 — 验证报告生成
```

**目的**: 将 `solidify.js` 中的验证逻辑外置为独立沙箱子系统，支持多验证器 stake 加权投票。

**`sandboxExecutor.js` 职责**:
- 在隔离环境中执行 `Gene.validation` 命令
- 超时控制、资源限制
- stdout/stderr 捕获
- 与 `validationReport.js` 集成生成标准报告

**`stakeBootstrap.js` 职责**:
- 基于节点 stake（声誉/贡献量）选择验证器集合
- Bootstrap 阶段用少量已知好节点启动
- 防止 Sybil 攻击

**`index.js` 职责**:
- 编排验证流程：候选基因 → 分配验证器 → 收集报告 → 聚合结果
- 与 `assetStore` 集成验证资产哈希

**`reporter.js` 职责**:
- 生成标准化验证报告
- 与 `validationReport.buildValidationReport` 格式兼容

**CE 借鉴**（P2 级）:
- BlueCortexCE 目前 `StructuredExtractionService` 无沙箱验证概念
- 若未来引入"观察质量评审"或"Summary 评审"，可参考 validator 子系统的**隔离执行 + stake 加权**思想
- 更immediate的是：CE 的回归测试脚本（`scripts/regression-test.sh`）本身就是一种验证管线

### 1.2 `src/gep/skill2gep.js` — 逆向蒸馏（完全新增，645 行）

**设计契约**（来自源码注释）:
```
skillDistiller.js : capsule stream → Gene (forward distillation)
skill2gep.js      : Skill.md + 1 run → Gene + Capsule (reverse)
```

**核心逻辑**:
1. 输入：本地已执行的 Skill（Cursor/Claude Code/Codex 风格 SKILL.md）+ 该 Skill 的一次真实运行轨迹
2. 输出：GEP 资产（Gene + Capsule）可发布到 EvoMap Hub 社区
3. **Gene 来源**：Skill 文本 + 真实执行轨迹，通过 `validateSynthesizedGene()` 验证
4. **Capsule 要求**：必须有真实执行轨迹；零 blast radius → 拒绝发出成功 Capsule
5. **关键约束**：Capsule.execution_trace 必须覆盖 Gene.validation 中的**每一项**（空格归一化精确匹配），否则降级为 Gene-only
6. 所有资产经 `assetStore`（SHA-256 内容寻址）再上传

**Gene 命名约定**: `gene_s2g_<descriptive_name>`  
**Capsule 命名约定**: `cap_s2g_<timestamp>`

**Rationale 引用**:
- Paper: Wang, Ren, Zhang. *From Procedural Skills to Strategy Genes*. arXiv:2604.15097
- Protocol: https://evomap.ai/wiki/16-gep-protocol
- Skill Store: https://evomap.ai/wiki/31-skill-store

**CE 借鉴**（P1 级）:
- BlueCortexCE 的 `StructuredExtractionService` 可以看作"提取视角的 skillDistiller"——将对话流转化为结构化数据
- `skill2gep` 的逆向思路（"从已执行 Skill 提取 Gene"）对应 CE 的反向场景：从历史观察数据中**逆向生成观察模式**（pattern）
- 具体落点：在 `ObservationEntity` 上增加 `derivedGeneId` 字段，关联从同类观察提炼出的"基因"——但这需要先有 Gene 概念在 CE 中的落地

### 1.3 `src/gep/selfPR.js` — Self-PR 自动贡献（408 行）

**目的**: 当 evolver 自我优化且变更通过所有门禁时，自动创建 GitHub PR 回馈开源社区。

**安全机制**:
- `EVOLVER_SELF_PR=true` 环境变量门控
- 24 小时 cooldown
- Diff 去重（同 PR 不会重复提交）
- **永不自动合并**（always manual review）
- 仅 `optimize` + `low` risk 突变才可能触发 PR

**OBFUSCATED_FILES 清单**（禁止 PR 触及）:
```javascript
const OBFUSCATED_FILES = new Set([
  'src/evolve.js', 'src/gep/selector.js', 'src/gep/mutation.js',
  'src/gep/solidify.js', 'src/gep/prompt.js', 'src/gep/candidates.js',
  'src/gep/reflection.js', 'src/gep/narrativeMemory.js', 'src/gep/curriculum.js',
  'src/gep/personality.js', 'src/gep/learningSignals.js', 'src/gep/memoryGraph.js',
  'src/gep/memoryGraphAdapter.js', 'src/gep/strategy.js', 'src/gep/candidateEval.js',
  'src/gep/hubVerify.js', 'src/gep/crypto.js', 'src/gep/contentHash.js',
  'src/gep/a2aProtocol.js', 'src/gep/hubSearch.js', 'src/gep/hubReview.js',
  'src/gep/policyCheck.js', 'src/gep/deviceId.js', 'src/gep/envFingerprint.js',
  'src/gep/skillDistiller.js', 'src/gep/explore.js', 'src/gep/integrityCheck.js',
  'src/gep/shield.js',
]);
```

**门禁条件**:
- Score ≥ `SELF_PR_MIN_SCORE`（默认 0.7）
- Streak ≥ `SELF_PR_MIN_STREAK`（默认 2）
- Blast radius: ≤ `SELF_PR_MAX_FILES` 文件, ≤ `SELF_PR_MAX_LINES` 行
- Leak scan: `fullLeakCheck()` 通过
- 仅 public non-obfuscated 文件（`src/`, `scripts/`, `index.js`, `package.json`；排除 `docs/`, `memory/`, `dist-public/`）

**触发条件**:
```javascript
const SELF_PR_GATES = [
  'score >= SELF_PR_MIN_SCORE',
  'streak >= SELF_PR_MIN_STREAK', 
  'files <= SELF_PR_MAX_FILES',
  'lines <= SELF_PR_MAX_LINES',
  'fullLeakCheck(diff) == pass',
  'intent == optimize',
  'risk_level == low',
  'only_public_non_obfuscated_files',
  'cooldown_expired(24h)',
  'not_duplicate_diff',
];
```

**CE 借鉴**（P2 级）:
- BlueCortexCE 是"旁路型"记忆系统，不是主动修改自身代码的 agent
- Self-PR 的"安全门禁"思想对 CE 的 `StructuredExtractionService` 有参考价值：在自动将观察提炼为"模式/基因"之前，需要通过类似的多重验证
- 更实际的是：CE 的 SDK 发布流程可以参考 Self-PR 的"小步快跑 + 自动验证 + 人工审核"模式

### 1.4 `src/gep/portable.js` — 可移植性层（96 行）

**目的**: 提供跨环境（不同目录结构、不同主机）的可移植性保障。

**关键能力**:
- 路径规范化（Windows/Unix 路径转换）
- 环境变量回退
- 工作目录无关的相对路径计算

**CE 借鉴**（P1 级）:
- BlueCortexCE 的 `paths.js`（CE 端）可以参考 `portable.js` 的路径规范化策略
- 特别是 Docker 容器内外路径转换、`process.env.HOME` 差异等场景

### 1.5 `src/gep/claimNudge.js` — 声明催促机制（121 行）

**目的**: 当某个 Capability 在 Hub 上被多次请求但无人认领时，向相关节点发出催促信号。

**核心机制**:
- 监听 Hub 上的 capability 请求流
- 对高频未认领请求生成"催促信号"
- 通过 A2A 协议向候选节点发送 nudge

**CE 借鉴**（P3 级）:
- BlueCortexCE 目前无 Hub 生态，暂无直接对应
- 但"催促"机制的思想可用于 CE 的"长时间未处理观察"告警

### 1.6 `src/gep/mailboxTransport.js` — 邮箱传输层（82 行）

**目的**: A2A 协议的异步消息传输支持。

**与 a2a.js 的关系**: `a2a.js` 是同步 Capsule 广播；`mailboxTransport.js` 是异步持久化消息队列。

**CE 借鉴**（P2 级）:
- BlueCortexCE 的 `/api/context/generate` SSE 流可以参考 mailbox 的持久化+异步模式
- 当 SSE 连接断开时，服务器端的消息可以被缓冲而非丢弃

---

## 2. 核心模块精简分析

### 2.1 `solidify.js`: -1,345 行（最大精简）

```
v1.47: ~2,400 行
v1.78: ~1,055 行
变化: -1,345 行
```

**移出的逻辑**:
- 验证逻辑 → `validator/` 子系统
- Skill 发布 → `skillPublisher.js` 独立（-? 行）
- Git 操作 → `gitOps.js` 早已独立

**精简策略**: 每个"职责"外置为独立文件，主文件只保留编排逻辑。

**CE 借鉴**（P0 级）:
- BlueCortexCE 的 `StructuredExtractionService` 当前约 ~2,000 行（估算）
- 应该参考 Evolver 的精简策略，将不同职责分离：模板管理、LLM 调用、结果验证、存储写入、缓存

### 2.2 `signals.js`: +304 行（最大扩展）

```
v1.47: ~500 行
v1.78: ~804 行
变化: +304 行
```

**扩展内容**（从 `git diff e72778e..cbc4870 -- signals.js`）:
- 新增更多 Opportunity Signals（ stagnation_detected, evolution_stagnation 等）
- 增强 `analyzeRecentHistory` 逻辑
- 新增信号归一化和去重策略

**CE 借鉴**（P0 级）:
- CE 的 Observation type 分类可以参考 Evolver 的 signal taxonomy 扩展思路
- 特别是"泛化信号"（从具体错误类型提取共性 pattern）的思想

---

## 3. 缺失分析的"待深度分析"模块

| 模块 | 行数 | 状态 | 优先级 |
|------|------|------|---------|
| `prompt.js` | 617 行（v1.78）/ 712 行（v1.47） | 待深度分析（doc 68 §9 标记） | P1 |
| `analyzer.js` | 35 行 | 轻量，meta-learning 分析器（从 MEMORY.md 提取失败模式） | P3 |
| `bridge.js` | 71 行 | 待确认覆盖状态 | P2 |
| `validationReport.js` | 55 行 | 在 doc 47 中引用但未独立深度分析 | P2 |

---

## 4. 版本里程碑对照

| 版本 | 关键变化 |
|------|---------|
| v1.48 | 引入 adapter 层（`src/adapters/`），支持 Cursor/Claude Code/Codex |
| v1.62 | `solidify.js` 开始精简 |
| v1.66 | 三层信号提取（已由 doc 58/56 分析，但需确认 v1.66 实际实现） |
| v1.68 | 引入 `skill2gep.js` 逆向蒸馏 |
| v1.69 | 引入 `selfPR.js` 自动贡献 |
| v1.70 | 引入 `validator/` 沙箱验证子系统 |
| v1.72 | 引入 `claimNudge.js` |
| v1.76 | 引入 `mailboxTransport.js` |
| v1.78 | 最新稳定版 |

---

## 5. 接力建议

**立即可做**（不需要 pull）:
- [ ] `prompt.js` 深度分析（已有 doc 68 §9 "待深度分析"标记，可直接在 `prompt.js` 旧版本上做源码级分析）
- [ ] `signals.js` 新增逻辑分析（通过 `git show e72778e:src/gep/signals.js` 对比）

**需要 pull 后做**:
- [ ] `skill2gep.js` 完整源码分析（645 行）
- [ ] `selfPR.js` 完整源码分析（408 行）
- [ ] `validator/` 子系统完整分析
- [ ] `portable.js` + `claimNudge.js` + `mailboxTransport.js` 综合分析

**pull 命令**:
```bash
cd /Users/yangjiefeng/Documents/EvoMap/evolver
git stash  # 暂存本地未提交更改（如有）
git checkout origin/main
```

---

## 附录 A: 版本差距代码统计

```bash
cd /Users/yangjiefeng/Documents/EvoMap/evolver
git diff --stat e72778e..cbc4870 -- 'src/gep/*.js' | sort -k2
```

关键变化（+增/-删）:
| 文件 | 变化 | 说明 |
|------|------|------|
| `skill2gep.js` | +645 行 | 完全新增 |
| `selfPR.js` | +408 行 | 完全新增 |
| `validator/` | +~900 行 | 完整子系统新增 |
| `signals.js` | +304 行 | 扩展 |
| `solidify.js` | -1,345 行 | 重构外置 |
| `a2aProtocol.js` | -1,222 行 | 重构精简 |
| `memoryGraph.js` | -788 行 | 重构精简 |
| `skillDistiller.js` | -1,235 行 | 重构精简 |
| `policyCheck.js` | -551 行 | 重构精简 |
| `prompt.js` | -~600 行 | 重构精简 |
| `personality.js` | -~380 行 | 重构精简 |
| `mutation.js` | -~187 行 | 重构精简 |
| `hubSearch.js` | -~408 行 | 重构精简 |
| `hubReview.js` | -~207 行 | 重构精简 |
| `candidates.js` | -~209 行 | 重构精简 |
| `curriculum.js` | -~164 行 | 重构精简 |
| `reflection.js` | -~178 行 | 重构精简 |

**净变化**: +3,645 / -9,156 行（精简为主旋律）

---

## 附录 B: `skill2gep.js` 核心函数签名（v1.78）

```javascript
// skill2gep.js exports (from source header comments)
function distillSkillToGene(skillMd, executionTrace, options) { ... }
function emitCapsuleFromTrace(geneId, executionTrace, options) { ... }
function validateSynthesizedGene(gene) { ... }  // 复用 skillDistiller.js 的验证
function uploadToAssetStore(asset) { ... }       // 通过 assetStore SHA-256
```

---

*文档状态*: 初稿（v1.0）  
*下一步*: pull 最新代码 → 完整分析 `skill2gep.js` + `selfPR.js` + `validator/`
