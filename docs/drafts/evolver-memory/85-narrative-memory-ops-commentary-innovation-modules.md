# Doc 85 — Narrative Memory + Ops Supporting Modules Deep Dive

**目标**：分析 `narrativeMemory.js`、`ops/commentary.js`、`ops/innovation.js`、`ops/skills_monitor.js` 四个被低估的支持模块，提炼其设计思想。

**源码**：
- `/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/narrativeMemory.js` (108 lines)
- `/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/commentary.js` (58 lines)
- `/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/innovation.js` (58 lines)
- `/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/skills_monitor.js` (128 lines)

**最后更新**：2026-05-04 | doc 85 v1

---

## §1 Narrative Memory (`narrativeMemory.js`)

### 1.1 定位与调用关系

Narrative Memory 是进化过程的"编年史"——每次 `solidify` 成功后调用 `recordNarrative` 追加一条 Markdown 条目。它不是观测数据，而是**决策日志**，用于：
1. 人类可读的进化历史记录
2. `prompt.js` 通过 `loadNarrativeSummary` 将最近 8 条注入 Prompt
3. 提供快速回溯：某次失败的基因、信号、策略是什么

**调用入口**：
- `solidify.js` L974: `recordNarrative({ gene, signals, mutation, outcome, blast, capsule })`
- `prompt.js` L231: `loadNarrativeSummary(3000)` → 注入 Prompt

### 1.2 双重裁剪机制

```javascript
const MAX_NARRATIVE_ENTRIES = 30;   // 条目数上限
const MAX_NARRATIVE_SIZE  = 12000;  // 字符数上限
```

`trimNarrative` 实现了两阶段裁剪：

| 阶段 | 触发条件 | 策略 |
|------|---------|------|
| **Entry 裁剪** | entries.length > 30 | `entries.shift()` 移除最旧条目 |
| **字符裁剪** | content.length > 12000 | 保留最近 entries，try keep 5 extra |

两阶段设计确保：文件永远不会超过 12KB；同时保留足够多的历史记录（最多 30 条）。

### 1.3 Entry 结构

每条 Narrative Entry 格式（Markdown）：

```markdown
### [2026-05-04 10:30] REPAIR - success
- Gene: gene_repair_distilled_xxx | Score: 0.85 | Scope: 3 files, 47 lines
- Signals: [log_error, area:memory]
- Why: Memory graph edge weight degraded after idle period
- Strategy:
  1. resetEdgeWeights()
  2. recomputeBaseline()
- Result: Edge weights restored, latency back to baseline
```

关键字段：
- `geneId`: 基因 ID 或 `(auto)` 表示自动生成基因
- `category.uppercase + status`: 快速过滤（REPAIR-FAILURE / OPTIMIZE-SUCCESS）
- `Signals[0:4]`: 最多 4 个信号
- `rationale`: 变异决策理由（200 char）
- `strategy[0:3]`: 前 3 个策略步骤
- `capsuleSummary`: 胶囊执行摘要（200 char）

### 1.4 `loadNarrativeSummary` 设计

```javascript
function loadNarrativeSummary(maxChars) {
  const limit = maxChars || 4000;  // default 4000, prompt.js 传 3000
  const entries = content.split(/(?=^### \[)/m);  // 按 Markdown H3 split
  const recent = entries.slice(-8);  // 只取最近 8 条
  let summary = recent.join('');
  if (summary.length > limit) {
    summary = summary.slice(-limit);  // 从末尾截断
    const firstEntry = summary.indexOf('### [');  // 确保截断点从 H3 开始
    if (firstEntry > 0) summary = summary.slice(firstEntry);
  }
  return summary.trim();
}
```

**设计亮点**：
1. **从末尾截断**：保留最新记录，不丢失近期上下文
2. **对齐截断点**：确保从完整的 H3 header 开始，避免 Markdown 截断
3. **双重限制**：entries ≤ 8 条 + chars ≤ limit，双重保险

### 1.5 与 BlueCortexCE 的对比

| 方面 | EvoMap narrativeMemory | BlueCortexCE 对应 |
|------|----------------------|-----------------|
| 存储格式 | Markdown 文件追加 | ObservationEntity (数据库) |
| 裁剪策略 | 条目数 + 字符数双重 | PostgreSQL LIMIT / TTL |
| 用途 | Prompt 注入 | ContextService → generateContext |
| 记录内容 | 基因+信号+策略+结果 | SessionEntity + UserPromptEntity |
| 失败记忆 | 同成功统一记录 | SummaryEntity 分离 |

**CE 借鉴点**：
- Narrative 的 Markdown 条目格式值得学习——结构化、可读性强
- `loadNarrativeSummary` 的"末尾截断+对齐H3"技巧可用在 ContextService 的 summary 生成
- 双限制（条目数+字符数）防止文件无限膨胀

---

## §2 Commentary Generator (`ops/commentary.js`)

### 2.1 定位

进化循环结束后向用户报告的简短评语生成器。58 行纯函数，无外部依赖（无 fs、无网络）。

### 2.2 三人格系统

```javascript
var PERSONAS = {
    standard: {   // 默认，专业中性
        success: ['Evolution complete. System improved.', ...],
        failure: ['Cycle failed. Will retry.', ...],
    },
    greentea: {   // 傲娇少女风
        success: ['Did I do good? Praise me~', 'So efficient... unlike someone else~', ...],
        failure: ['Oops... it is not my fault though~', 'This is harder than it looks, okay?', ...],
    },
    maddog: {    // 极简军事风
        success: ['TARGET ELIMINATED.', 'Mission complete. Next.', ...],
        failure: ['FAILED. RETRYING.', 'Error. Will overcome.', ...],
    },
};
```

人格通过 `process.env.EVOLVER_PERSONA` 或启动参数选择。每次随机选一条。

### 2.3 API 设计

```javascript
function getComment({ persona, success, duration }) {
    var p = PERSONAS[persona] || PERSONAS.standard;
    var pool = success ? p.success : p.failure;
    var comment = pool[Math.floor(Math.random() * pool.length)];
    return comment;
}
```

**设计亮点**：
1. 纯函数，无副作用，无外部依赖
2. `duration` 参数存在但未使用（可能是为 future use 预留）
3. `success !== false` 保证 undefined 时默认 success
4. 可独立运行：`node commentary.js greentea true`

### 2.4 CE 借鉴

CE 没有评语系统。但人格系统的实现方式值得参考：
- 固定词池（而非 LLM 生成）→ 确定性、低延迟、无 API 费用
- 适合简单场景的快速反馈

---

## §3 Innovation Catalyst (`ops/innovation.js`)

### 3.1 定位

当系统检测到"停滞"（stagnation）时，Innovation Catalyst 分析当前 skill 组合的覆盖度，提出填补缺口的创意建议。它不是代码生成器，而是**创意提案生成器**。

### 3.2 能力缺口分析算法

```javascript
const categories = {
    'feishu': skills.filter(s => s.startsWith('feishu-')).length,
    'dev': skills.filter(s => s.startsWith('git-') || ...).length,
    'media': skills.filter(s => s.includes('image') || ...).length,
    'security': skills.filter(s => s.includes('security') || ...).length,
    'automation': skills.filter(s => s.includes('auto-') || ...).length,
    'data': skills.filter(s => s.includes('db') || ...).length,
};
const sortedCats = Object.entries(categories).sort((a, b) => a[1] - b[1]);
const weakAreas = sortedCats.slice(0, 2).map(c => c[0]);
```

选取技能数量最少的 2 个领域作为"弱势领域"。

### 3.3 创意生成策略

| 策略 | 触发条件 | 示例 |
|------|---------|------|
| **缺口填补** | weakAreas 含某领域 | security → dependency-scanner skill |
| **优化合并** | skills.length > 50 | 合并相似 git 技能 |
| **元级增强** | 始终 | performance-metric dashboard |

**输出上限**：`ideas.slice(0, 3)` — 只返回 top 3，避免信息过载。

### 3.4 设计特点

1. **轻量启发式**：无 LLM，基于文件系统扫描
2. **零外部依赖**：纯 Node.js fs + 字符串匹配
3. **快速失败**：try/catch 覆盖所有 I/O，失败时返回空数组
4. **真实技能缺口驱动**：不是随机创意，而是基于实际覆盖度分析

### 3.5 CE 借鉴

CE 没有 equivalent innovation 模块，但类似的思想可以用于：
- Skill 覆盖度分析 → 推荐新 Skill 安装
- 基于现有 ObservationEntity 的领域分析 → 发现能力空白

---

## §4 Skills Monitor + Auto-Heal (`ops/skills_monitor.js`)

### 4.1 定位

定期检查 `~/.claude/skills/` 下所有技能的可用性，自动修复简单问题（npm install / SKILL.md 生成）。零飞书依赖。

### 4.2 检测项

| 问题 | 检测方法 | 自动修复 |
|------|---------|---------|
| `Missing node_modules` | 检查 `node_modules/` 目录是否存在 | `npm install --production` |
| `Empty node_modules` | `readdirSync().length === 0` | `npm install` |
| `Invalid node_modules` | 目录访问异常 | `npm install` |
| `Invalid package.json` | JSON parse 失败 | ❌ 需人工 |
| `Missing SKILL.md` | 文件不存在 | 生成 stub 文件 |

### 4.3 性能优化（v2.0）

**关键注释揭示了一次历史性能问题**：
```javascript
// Optimization: Check for node_modules existence instead of spawning node
// Spawning node for every skill is too slow (perf_bottleneck).
// We assume if node_modules exists, it's likely okay.
// Or we can use a lighter check if we really suspect issues.
```

v1.0 曾经对每个技能 spawn Node.js 进程检查语法 → 太慢（perf_bottleneck）。v2.0 改为：
- `fs.statSync` 检查目录是否存在
- `fs.readdirSync` 检查 node_modules 是否为空
- 完全移除了同步 spawn

### 4.4 用户忽略列表

```javascript
const IGNORE_LIST = new Set(['common', 'clawhub', 'input-validator', ...]);
// 用户可通过 .skill_monitor_ignore 文件添加
```

### 4.5 Auto-Heal 流程

```javascript
function autoHeal(skillName, issues) {
    for (let i = 0; i < issues.length; i++) {
        if (issues[i].includes('node_modules')) {
            execSync('npm install --production --no-audit --no-fund', {
                cwd: skillPath, stdio: 'ignore', timeout: 60000
            });
        } else if (issues[i] === 'Missing SKILL.md') {
            fs.writeFileSync(path.join(skillPath, 'SKILL.md'),
                '# ' + skillName + '\n\n' + name.replace(/-/g, ' ') + ' skill.\n');
        }
    }
}
```

**设计亮点**：
1. **幂等性**：`npm install` 重复执行安全
2. **宽容失败**：npm install 失败只 warn，不阻断
3. **package-lock.json 清理**：删除避免冲突
4. **SKILL.md stub**：保留命名格式（`git-sync` → `git sync skill.`）

### 4.6 CE 借鉴

CE 的 equivalent 是 `OpenClaw skills` 系统。目前 OpenClaw 没有等效的 auto-heal。可以借鉴：
- Skill 目录存在性检查
- `node_modules` 安装状态检测
- SKILL.md stub 生成
- `.skill_monitor_ignore` 用户忽略机制

---

## §5 四个模块的横向对比

| 模块 | 职责 | 核心算法 | 外部依赖 | CE 等效 |
|------|------|---------|---------|---------|
| `narrativeMemory` | 进化决策日志 | 双重裁剪（条目+字符） | fs | ObservationEntity |
| `commentary` | 进化结果评语 | 固定词池随机 | 无 | 无 |
| `innovation` | 技能缺口创意 | 类别覆盖度排序 | fs | 无 |
| `skills_monitor` | 技能可用性自愈 | fs stat + exec npm | exec | 无（可借鉴） |

**共同主题**：
1. **轻量优先**：所有模块都是同步 fs 操作，无网络 I/O，无 LLM 调用
2. **幂等设计**：操作可重复执行而不破坏状态
3. **优雅降级**：失败不阻断主循环，只是报告或跳过
4. **文件即状态**：不依赖数据库，用文件系统管理状态

---

## §6 CE 行动项

| 优先级 | 行动项 | 来源模块 |
|--------|-------|---------|
| P2 | 评估在 ContextService 中增加"结构化 Evolution 历史摘要"注入到 context 的可行性 | `narrativeMemory` |
| P3 | 评估 OpenClaw Skills 的 auto-heal 能力（类似 skills_monitor） | `skills_monitor` |
| P3 | 评估在 SkillManager 中实现技能覆盖度分析 + 缺口推荐 | `innovation` |
| P3 | 评估固定词池评语系统在任务完成通知中的使用（替代 LLM 生成） | `commentary` |
