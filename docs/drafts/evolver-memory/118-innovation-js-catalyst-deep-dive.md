# `innovation.js` — 创新催化剂（67行）

**文件**: `src/ops/innovation.js`（v1.47.0 本地源码）  
**分析**: PM Agent @ 2026-05-06  
**定位**: Evolver 停滞检测后的创新构想生成

---

## 1. 核心职责

当系统检测到**停滞（stagnation）**信号时，`innovation.js` 分析现有技能分布，识别薄弱领域，生成具体创新构想建议。

---

## 2. 技能分类体系

```javascript
const categories = {
  'feishu':      skills.filter(s => s.startsWith('feishu-')),
  'dev':         skills.filter(s => s.startsWith('git-') || s.startsWith('code-') || s.includes('lint') || s.includes('test')),
  'media':       skills.filter(s => s.includes('image') || s.includes('video') || s.includes('music') || s.includes('voice')),
  'security':    skills.filter(s => s.includes('security') || s.includes('audit') || s.includes('guard')),
  'automation':  skills.filter(s => s.includes('auto-') || s.includes('scheduler') || s.includes('cron')),
  'data':        skills.filter(s => s.includes('db') || s.includes('store') || s.includes('cache') || s.includes('index'))
};
```

---

## 3. 创新生成逻辑

**策略一：填补空白**
- 找出技能数量最少的 2 个领域
- 根据领域生成具体构想（安全/媒体/开发/自动化/数据各不同）

**策略二：优化现有**
- 技能数量 > 50 时，建议去重/合并相似技能

**策略三：元级改进**
- 始终建议添加"性能指标仪表盘"（meta）

---

## 4. 典型输出示例

```javascript
// 安全技能最弱时：
[
  "- Security: Implement a 'dependency-scanner' skill to check for vulnerable packages.",
  "- Security: Create a 'permission-auditor' to review tool usage patterns."
]

// 媒体技能最弱时：
[
  "- Media: Add a 'meme-generator' skill for social engagement.",
  "- Media: Create a 'video-summarizer' using ffmpeg keyframes."
]

// 技能过多时（优化）：
[
  "- Optimization: Identify and deprecate unused skills.",
  "- Optimization: Merge similar skills (e.g., 'git-sync' and 'git-doctor')."
]

// 元级（始终包含）：
[
  "- Meta: Enhance the Evolver's self-reflection by adding a 'performance-metric' dashboard."
]
```

---

## 5. 架构特点

| 特点 | 说明 |
|------|------|
| 被动触发 | 由外部检测到 stagnation 时调用，非主动轮询 |
| 基于技能目录 | 读取 `getSkillsDir()` 实时分析 |
| 启发式 | 简单规则，无 LLM 调用 |
| 有界输出 | `ideas.slice(0, 3)` 最多返回 3 条 |
| 无副作用 | 纯读操作，不修改任何文件 |

---

## 6. 与其他模块的关系

```
stagnation signal
    ↓
innovation.js → generateInnovationIdeas()
    ↓
suggested mutations for reflection.js or solidification
```

---

## 7. BlueCortexCE 借鉴

| 方面 | Evolver | CE 现状 | 建议 |
|------|---------|---------|------|
| 停滞检测 | `signals.js` 的 stagnation 信号 | 无 | P2: 实现停滞信号检测 |
| 创新构想 | 启发式基于技能分布 | 无 | P3: 可选（需 LLM 调用） |
| 技能分布分析 | 6 类技能前缀/后缀匹配 | 无 | P3: 参考性 |
| 元级建议 | 性能仪表盘建议 | 无 | P3: 参考性 |

**核心价值**：提供了一种**零 LLM 成本**的创新发现机制——通过分析现有能力分布的空白来驱动探索方向。

**注意**：此模块设计较为简单，真实创新构想生成可能需要 LLM 驱动的 `curriculum.js` 或 `reflection.js` 配合。
