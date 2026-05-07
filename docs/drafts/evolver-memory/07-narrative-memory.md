# 7. 叙事记忆

## 7.1 定位

NarrativeMemory（`narrativeMemory.js`）提供**人类可读的进化历史**，以 Markdown 格式存储，便于人工审查和调试。

## 7.2 格式模板

每次进化后追加一个 Markdown 条目：

```markdown
### [2026-05-07 03:15] REPAIR - success
- Gene: fix_null_pointer_v3 | Score: 0.85 | Scope: 3 files, 47 lines
- Signals: [log_error, errsig:TypeError: x is undefined]
- Why: Null pointer in user profile lookup causing session crashes
- Strategy:
  1. Add defensive null checks
  2. Validate API responses
  3. Add integration test coverage
- Result: Error count reduced from 5 to 0 in last hour
```

## 7.3 滚动裁剪策略

```js
const MAX_NARRATIVE_ENTRIES = 30;   // 最多保留 30 条
const MAX_NARRATIVE_SIZE = 12000;   // 最多 12KB

function trimNarrative(content) {
  // 1. 如果总大小 ≤ 12KB，不裁剪
  // 2. 否则，从头删除最旧条目直到 ≤ 30 条
  // 3. 如果仍 > 12KB，再从尾删除到保留最近 N-5 条
  // 4. 保留 header（文件开头的说明文本）
}
```

**关键**：header（文件顶部的说明文本）始终保留，只有 `### [timestamp]` 条目被裁剪。

## 7.4 加载摘要

```js
function loadNarrativeSummary(maxChars = 4000) {
  // 从最新 8 条条目中提取最近 4000 字符
  // 从 entry 边界开始截断，避免截断中间条目
  const entries = content.split(/(?=^### \[)/m);
  const recent = entries.slice(-8);
  // ...
}
```

## 7.5 与 MemoryGraph 的关系

| 维度 | MemoryGraph | NarrativeMemory |
|------|-------------|-----------------|
| 格式 | JSONL（机器可读） | Markdown（人类可读） |
| 内容 | 信号、基因、结果、结构化事件 | 基因、信号、变更范围、策略理由 |
| 用途 | 基因选择建议、置信计算 | 人工审查、上下文理解 |
| 大小 | 无硬限制（append-only） | 30 条 / 12KB 硬限制 |
| 粒度 | 每次动作一条事件 | 每次进化一个条目 |
