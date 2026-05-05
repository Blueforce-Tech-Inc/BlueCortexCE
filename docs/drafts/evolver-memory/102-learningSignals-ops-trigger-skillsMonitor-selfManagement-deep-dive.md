# Doc 102 — `learningSignals` + `ops/trigger` + `ops/skillsMonitor` 主动自我管理三模块深度分析

> **角色**：深度分析 Evolver 的三个"主动自我管理"模块——信号扩展标签化（`learningSignals.js`）、立即唤醒触发（`ops/trigger.js`）、技能自愈监控（`ops/skillsMonitor.js`）。三者共同实现"观测→标签→自愈"闭环，是 Evolver **无需外部干预即可维持自身健康**的关键机制。
>
> **源码**：
> - `/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/learningSignals.js`（89行，v1.47）
> - `/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/trigger.js`（33行，v1.47）
> - `/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/skills_monitor.js`（143行，v2.0，v1.47）
>
> **前置阅读**：doc 21（信号分类学）、doc 37（信号→基因选择端到端）、doc 60（Ops 自我修复基础设施）
> **最后更新**：2026-05-05

---

## §1 概览：三模块在 Evolver 中的位置

```
Hook/Session 原始信号
    │
    ▼
[learningSignals.expandSignals()]        ← 信号扩展：将原始文本 → 结构化标签
    │                                    ← 问题分类（reliability/protocol/performance）
    │                                    ← 行动建议（repair/optimize/innovate）
    ▼                                    ← 领域标记（orchestration/memory/skills）
[memoryGraph.computeSignalKey()]
    │  (稳定化 key 用于跨周期匹配)
    ▼
[selector.selectGeneAndCapsule()]
    │  (用 expandSignals 打分的 tag overlap 辅助基因选择)
    │
    ▼ (可选) ───────────────────────────
    │
[ops/skillsMonitor.run()]               ← 技能健康检查 + 自动修复
    │                                    ← 检测：missing node_modules / empty node_modules
    │                                    ← 修复：npm install / 创建 SKILL.md stub
    │
[ops/trigger.send()]                    ← 立即唤醒信号（polling-wake 机制）
    │                                    ← 写 evolver_wake.signal 文件
    │                                    ← wrapper poll 到该文件时立即触发
    │
[ops/health_check.run()]                ← 磁盘/环境/进程健康检查
    │
[ops/cleanup.run()]                     ← GEP artifact 清理
```

**核心洞察**：Evolver 不仅有**进化记忆**（gene/mutation/signal），还有**自维护记忆**（skills监控、清理、生命周期管理）。这两条线共同实现"自主智能体"。

---

## §2 `learningSignals.js`（89行）：从原始信号到可操作标签

### 2.1 核心函数

| 函数 | 职责 | 输出 |
|------|------|------|
| `expandSignals(signals, extraText)` | 原始信号 → 结构化标签数组 | `string[]` 标签 |
| `geneTags(gene)` | 从 gene 对象提取标签 | `string[]` 标签 |
| `scoreTagOverlap(gene, signals)` | 计算 gene 与信号的标签重叠度 | `number`（0~N） |

### 2.2 `expandSignals()` 算法

**输入**：原始信号数组 + 可选补充文本  
**处理流程**：

```
Step 1: 提取原始信号 + 基类
  raw[i]                        → 直接加入 tags
  raw[i].split(':')[0]         → 若与原值不同，也加入 tags（如 errsig:xxx → errsig）

Step 2: 文本模式匹配（extraText 参与）
  文本.lowerCase() + raw.join(' ') 合并后正则匹配

Step 3: 按匹配结果打标签
```

**Step 2 标签注入规则**：

| 正则模式 | 添加的标签 | 语义 |
|----------|-----------|------|
| `error\|exception\|failed\|unstable\|log_error\|runtime\|429` | `problem:reliability`, `action:repair` | 可靠性问题 → 修复 |
| `protocol\|prompt\|audit\|gep\|schema\|drift` | `problem:protocol`, `action:optimize`, `area:prompt` | 协议问题 → 优化 |
| `perf\|performance\|bottleneck\|latency\|slow\|throughput` | `problem:performance`, `action:optimize` | 性能问题 → 优化 |
| `feature\|capability_gap\|user_feature_request\|external_opportunity` | `problem:capability`, `action:innovate` | 能力缺口 → 创新 |
| `stagnation\|plateau\|steady_state\|saturation\|empty_cycle_loop` | `problem:stagnation`, `action:innovate` | 停滞问题 → 创新 |
| `task\|worker\|heartbeat\|hub\|commitment\|assignment` | `area:orchestration` | 编排领域 |
| `memory\|narrative\|reflection` | `area:memory` | 记忆领域 |
| `skill\|dashboard` | `area:skills` | 技能领域 |
| `validation\|canary\|rollback\|constraint\|blast.*radius\|destructive` | `risk:validation` | 验证风险 |

**Step 3：去重**：`unique()` 函数（`Set` + trim + filter Boolean）

### 2.3 `scoreTagOverlap()` 基因选择辅助

```javascript
function scoreTagOverlap(gene, signals) {
  const signalTags = expandSignals(signals, '');  // 展开信号标签
  const geneTagList = geneTags(gene);              // 提取 gene 标签
  // Jaccard 计数版：命中标签数 / gene 标签总数
  let hits = 0;
  for (const tag of geneTagList) {
    if (signalSet.has(tag)) hits++;
  }
  return hits;  // 后续在 selector.js 中参与基因选择评分
}
```

**用途**：在 `selector.js` 的 `selectGeneAndCapsule()` 中，`scoreTagOverlap` 与边缘成功率、Laplace 平滑分等共同决定基因排名。

### 2.4 BlueCortexCE 借鉴价值

**P1 - 信号扩展框架**：

```java
// CE 等价：ObservationService.expandSignals()
public List<String> expandSignals(String rawSignal, String extraContext) {
    List<String> tags = new ArrayList<>();
    tags.add(rawSignal);  // 原始信号直接入标签

    // 基类提取
    String base = rawSignal.contains(":")
        ? rawSignal.split(":")[0]
        : rawSignal;
    if (!base.equals(rawSignal)) tags.add(base);

    // 文本模式匹配
    String text = (rawSignal + " " + (extraContext != null ? extraContext : "")).toLowerCase();
    if (Pattern.compile("(error|exception|failed|unstable)").matcher(text).find()) {
        tags.add("problem:reliability");
        tags.add("action:repair");
    }
    if (Pattern.compile("(perf|performance|bottleneck|latency)").matcher(text).find()) {
        tags.add("problem:performance");
        tags.add("action:optimize");
    }
    // ... 更多规则
    return tags.stream().distinct().toList();
}
```

**应用场景**：
- `ObservationEntity.extractedData.signal_tags` = 扩展后的标签数组
- 写入 observation 时自动打标签（`type=error` → `action:repair`）
- 检索时支持按标签过滤（如"只看 repair 类观察"）

---

## §3 `ops/trigger.js`（33行）：立即唤醒信号机制

### 3.1 设计目标

传统的 cron 调度存在**最坏一个周期**的唤醒延迟。`trigger.js` 提供**即时唤醒**能力：任何模块检测到需要立即处理的事件时，向文件写入信号，wrapper 的 poll loop 检测到后**立即触发**Evolver 进程。

### 3.2 API 设计

```javascript
// 发送立即唤醒信号
trigger.send()       // → 写 WAKE 文件 → 返回 true/false

// 清除唤醒信号（处理完毕后）
trigger.clear()       // → 删除 WAKE 文件

// 查询是否有待处理信号
trigger.isPending()    // → 文件是否存在
```

**WAKE 文件路径**：`{WORKSPACE_ROOT}/memory/evolver_wake.signal`

### 3.3 使用模式

典型调用方（`ops/lifecycle.js` 或外部事件处理器）：
```javascript
// 检测到需要立即处理的事件后
if (urgentCondition) {
  trigger.send();  // 写 WAK 信号
}

// lifecycle manager 的 poll loop
if (trigger.isPending()) {
  trigger.clear();  // 清除信号
  // 立即执行 evolve 周期
}
```

### 3.4 设计亮点

| 特性 | 实现 |
|------|------|
| **无竞态** | 文件存在性检测天然幂等，多次 send 无害 |
| **进程无关** | 基于文件，不依赖共享内存或 IPC |
| **跨语言友好** | 文件 API 普遍可用，wrapper 可用任意语言实现 |
| **可组合** | 可叠加 cron 调度（周期轮询）+ 立即触发（事件驱动）|

### 3.5 BlueCortexCE 借鉴价值

**P2 - 等价的立即唤醒机制**：

```java
// CE 等价：CronTriggerService
public void sendWakeSignal() {
    Path wakeFile = memoryDir.resolve("ce_wake.signal");
    try {
        Files.writeString(wakeFile, "WAKE");
    } catch (IOException e) { /* log and ignore */ }
}

public boolean isPending() {
    return Files.exists(memoryDir.resolve("ce_wake.signal"));
}

public void clearWake() {
    try { Files.deleteIfExists(wakeFile); } catch (IOException e) { }
}
```

**应用场景**：
- `StructuredExtractionService` 检测到高优先级模式（异常飙升）→ `sendWakeSignal()`
- Cron 巡检进程 poll `isPending()` → 立即执行 health check 而非等待下次 cron
- SessionEnd hook 检测到关键反馈 → 立即触发 context 重新生成

---

## §4 `ops/skillsMonitor.js`（143行）：技能自愈监控

### 4.1 设计目标

Evolver 的技能系统（`skills/` 目录）是插件化的，但插件可能因为手动删除 `node_modules`、缺少 `SKILL.md` 等原因失效。`skillsMonitor` 做**主动健康检查**并**自动修复**常见问题，无需人工干预。

### 4.2 检测问题类型

| 问题 | 检测方法 | 自动修复 |
|------|----------|----------|
| `Missing node_modules` | `package.json` 有 `dependencies` 但 `node_modules/` 不存在 | `npm install --production` |
| `Empty node_modules` | `node_modules/` 存在但目录为空 | 同上 |
| `Missing SKILL.md` | `package.json` 存在但无对应 `SKILL.md` | 创建 stub 文件 |
| `Invalid package.json` | `JSON.parse` 失败 | 无（跳过） |
| `Invalid node_modules` | `readdirSync` 失败 | 无（跳过） |

### 4.3 `checkSkill()` 算法

```javascript
function checkSkill(skillName) {
  const skillPath = path.join(SKILLS_DIR, skillName);
  const issues = [];

  // 1. 必须是目录
  if (!fs.statSync(skillPath).isDirectory()) return null;

  // 2. 检查 package.json
  const pkg = exists(pkgPath) ? JSON.parse(readFile) : null;
  mainFile = pkg?.main ?? 'index.js';

  // 3. dependencies → node_modules 存在性（轻量检测，不 spawn node）
  if (pkg?.dependencies) {
    const nmPath = path.join(skillPath, 'node_modules');
    if (!exists(nmPath)) {
      issues.push('Missing node_modules');
    } else if (readdirSync(nmPath).length === 0) {
      issues.push('Empty node_modules');  // 优化：避免 slow spawn
    }
  }

  // 4. 有 package.json 但无 SKILL.md
  if (pkg && !exists(path.join(skillPath, 'SKILL.md'))) {
    issues.push('Missing SKILL.md');
  }

  return issues.length > 0 ? { name: skillName, issues } : null;
}
```

**性能优化亮点**：注释明确说明 `"Spawning node for every skill is too slow (perf_bottleneck)"`，改用目录存在性/长度检测替代 `node -c` 语法检查。

### 4.4 `autoHeal()` 修复逻辑

```javascript
function autoHeal(skillName, issues) {
  const healed = [];

  for (const issue of issues) {
    if (issue === 'Missing node_modules...' || issue === 'Empty node_modules...') {
      // 删除 package-lock 避免冲突
      unlinkIfExists(path.join(skillPath, 'package-lock.json'));
      // npm install（production，60s timeout）
      execSync('npm install --production --no-audit --no-fund', {
        cwd: skillPath, stdio: 'ignore', timeout: 60000
      });
      healed.push(issue);
    }
    if (issue === 'Missing SKILL.md') {
      // 创建 stub
      const name = skillName.replace(/-/g, ' ');
      writeFileSync(path.join(skillPath, 'SKILL.md'),
        '# ' + skillName + '\n\n' + name + ' skill.\n');
      healed.push(issue);
    }
  }
  return healed;
}
```

### 4.5 忽略列表机制

```javascript
const IGNORE_LIST = new Set([
  'common', 'clawhub', 'input-validator',
  'proactive-agent', 'security-audit'
]);

// 支持用户自定义扩展
// .skill_monitor_ignore 文件，每行一个 skill 名
// （# 开头为注释）
```

**设计意图**：系统内置 skills 永远忽略；用户可通过配置文件扩展忽略范围。

### 4.6 BlueCortexCE 借鉴价值

**P2 - Skill/SDK 健康检查框架**：

```java
// CE 等价：SkillHealthMonitor
public class SkillHealthMonitor {

    public SkillHealthReport checkSkill(String skillName) {
        List<String> issues = new ArrayList<>();
        Path skillPath = skillsDir.resolve(skillName);

        // 检测 node_modules（如果有 package.json）
        Path pkgJson = skillPath.resolve("package.json");
        if (Files.exists(pkgJson)) {
            Path nodeModules = skillPath.resolve("node_modules");
            if (!Files.exists(nodeModules)) {
                issues.add("Missing node_modules");
            } else if (isEmptyDir(nodeModules)) {
                issues.add("Empty node_modules");
            }
        }

        return new SkillHealthReport(skillName, issues);
    }

    public List<String> autoHeal(String skillName, List<String> issues) {
        List<String> healed = new ArrayList<>();
        Path skillPath = skillsDir.resolve(skillName);
        for (String issue : issues) {
            if (issue.startsWith("Missing node_modules")) {
                if (runNpmInstall(skillPath)) {
                    healed.add(issue);
                }
            }
        }
        return healed;
    }
}
```

**应用场景**：
- CE 启动时检查 SDK skills 健康状态
- Demo 环境部署时自动修复缺失依赖
- 与 `health_check.js` 配合形成完整的"进程+插件"健康体系

---

## §5 三模块联合闭环：自主智能体模式

### 5.1 完整自管理循环

```
[周期 N] ─────────────────────────────────────
  │
  ├─ 信号检测（Hook）
  │     ↓
  ├─ expandSignals() ─→ 问题分类 + 行动建议标签
  │     ↓
  ├─ memoryGraph.recordSignalSnapshot()
  │     ↓
  ├─ selector.scoreTagOverlap() ─→ 基因选择参考
  │     ↓
  ├─ [可能] trigger.send() ──→ 立即触发下一周期
  │
  ├─ [可选] skillsMonitor.run({ autoHeal: true })
  │     ├─ 检测技能问题（missing node_modules / SKILL.md）
  │     └─ 自动修复
  │
  └─ [定期] lifecycle manager 检查进程健康
        ├─ health_check（磁盘/环境）
        ├─ cleanup（old gep artifacts）
        └─ trigger.send()（如需唤醒）
```

### 5.2 自管理成熟度分级

| 级别 | 描述 | 示例 |
|------|------|------|
| **L0 无** | 纯人工维护 | 手动安装依赖、手动重启 |
| **L1 告警** | 检测问题但人工修复 | health_check 告警 |
| **L2 自愈** | 检测+自动修复常见问题 | skillsMonitor autoHeal |
| **L3 主动** | 预测性维护，未雨绸缪 | idleScheduler 预测性休眠 |
| **L4 进化** | 自主发现新策略并验证 | Evolver gene 进化 |

**Evolver 当前状态**：L2~L3（skillsMonitor 自愈 + idleScheduler 预测休眠）。  
**BlueCortexCE 当前状态**：L1（health check 只读告警，无自愈）。  
**差距**：CE 需要补 L2 自愈能力（SDK/技能安装失败自动重试）。

---

## §6 关键设计模式总结

| 模式 | 模块 | BlueCortexCE 落地 | 优先级 |
|------|------|------------------|--------|
| **信号扩展标签化** | `learningSignals` | `ObservationEntity.extractedData.signal_tags` 字段；自动打标签服务 | P1 |
| **标签重叠评分** | `learningSignals.scoreTagOverlap` | 检索时按标签 overlap + 成功率混合排序 | P2 |
| **文件信号立即唤醒** | `ops/trigger` | Cron 巡检 + 事件驱动双模式触发 | P2 |
| **技能健康自愈** | `ops/skillsMonitor` | SDK/Skill 启动时自动健康检查 + npm install 自修复 | P2 |
| **忽略列表 + 用户扩展** | `ops/skillsMonitor` | `CE_SKILL_MONITOR_IGNORE` 环境变量配置 | P3 |
| **性能优化：目录检测替代进程 spawn** | `ops/skillsMonitor` | CE 中避免频繁子进程调用，优先文件系统检测 | P3 |

---

## §7 相关文档

- 信号分类学：[`21-signal-taxonomy-and-gene-selection-memory.md`](./21-signal-taxonomy-and-gene-selection-memory.md)
- 信号→基因选择端到端：[`37-signal-taxonomy-gene-selection-end-to-end.md`](./37-signal-taxonomy-gene-selection-end-to-end.md)
- Ops 自我修复基础设施：[`60-evolver-ops-self-healing-infrastructure.md`](./60-evolver-ops-self-healing-infrastructure.md)
- Ops 基础设施模块：[`79b-evolver-ops-infrastructure-modules-deep-dive.md`](./79b-evolver-ops-infrastructure-modules-deep-dive.md)
- 自适应休眠调度（预测性维护）：[`77-idleScheduler-contentHash-OMLS-adaptive-memory-scheduling.md`](./77-idleScheduler-contentHash-OMLS-adaptive-memory-scheduling.md)
- 目录入口：[`index.md`](./index.md)
