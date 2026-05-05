# `localStateAwareness.js` + `analyzer.js` 深度分析

**Doc #118** | cron 2026-05-06 07:53 | 来源：`src/gep/localStateAwareness.js` (244L) + `src/gep/analyzer.js`

---

## 1. `localStateAwareness.js`：自发现状态快照

### 核心函数

**`captureLocalState()`** 返回 5 个区块的状态报告：

```
[Node Identity]
- Node ID: xxx (REGISTERED)
- Node Secret: PRESENT

[Environment Config]
- Env configured: A2A_*, EVOLVER_*, GITHUB_TOKEN...
- .env file: EXISTS

[Evolution State]
- Evolution cycles completed: N
- Last run gene: gene_xxx
- Active task: xxx
- Personality: rigor=X creativity=Y risk_tolerance=Z

[Memory & Knowledge]
- Memory directory: EXISTS
- MEMORY.md: N bytes
- Memory graph: N bytes
- Evolution narrative: EXISTS

[Skills]
- Installed skills: N (at ...)
```

### 用途

**session-init Hook** 在每次会话启动时调用 `captureLocalState()`，将本地状态快照注入 prompt 上下文。这解决了 AI **不知道自己在哪个节点、什么状态、有什么技能**的问题。

### BlueCortexCE 借鉴价值：P2

CE 的 session-start Hook 可以类似地注入：
- 当前数据库状态（observation 数量、session 数量）
- 配置状态（semantic_inject 是否启用）
- 技能目录（installed skills）

---

## 2. `analyzer.js`：`analyzeFailures()` 自我修正分析器

### 核心逻辑

```javascript
function analyzeFailures() {
  // 从 MEMORY.md 提取 Fix 表格中的失败记录
  const failureRegex = /\|\s*\*\*F\d+\*\*\s*\|\s*Fix\s*\|\s*(.*?)\s*\|/g;
  // 返回 top 3 失败记录用于 prompt 上下文
  return { status: 'success', count: N, failures: [...] };
}
```

### 设计思想

**元学习模式**：不是让 AI 自己记住所有失败，而是：
1. 从 MEMORY.md 提取失败模式（结构化表格）
2. 将 top 3 注入下次 mutation 的 prompt
3. 使 AI 在生成 mutation 时主动避免已知失败模式

### BlueCortexCE 借鉴价值：P3

**当前 CE 没有此机制**。未来可以在 `generateContext` 时从 `ObservationEntity` 中提取最近的失败模式，注入 prompt 帮助 AI 避免重复犯错。

---

## 3. 两模块的关系

```
session-init Hook
    ↓
captureLocalState()  →  状态快照注入 prompt（Node/Skills/Evolution/Memory）
    ↓
analyzeFailures()     →  失败模式分析（meta-learning）
    ↓
solidify()           →  基因固化 + 失败信息记录
    ↓
recordNarrative()     →  进化叙事记录
```

---

## 总结

| 模块 | 行数 | 核心功能 | CE 借鉴优先级 |
|------|------|---------|------------|
| `localStateAwareness.js` | 244 | 状态自发现快照 | P2 |
| `analyzer.js` | ~60 | 失败模式提取 | P3 |

**无 P0/P1 行动项**——这两个模块是辅助性运营模块，核心验证/持久化逻辑已在 `solidify.js`（doc 117）中覆盖。
