# v1.47 Session Source 架构：四模式路由与递归 Transcript 发现

> **来源**：`EvoMap/evolver` commit `e72778e` (v1.47.0, 2026-04-07)
> **变更规模**：12 files, +550/-1398 lines（含 vibe 删除）
> **最后更新**：2026-04-25

---

## 1. 背景

v1.47 之前，`readRealSessionLog()` 是单一函数，既读 OpenClaw JSONL 又读 Cursor 兼容格式，逻辑纠缠。v1.47 重构为：

- **专用函数分离**：`readOpenClawSessions()` + `readCursorTranscripts()` 各司其职
- **会话源路由**：`SESSION_SOURCE` 环境变量控制来源优先级
- **递归发现**：`collectTranscriptFiles()` 替代手工指定路径，支持 Cursor 嵌套目录
- **vibe 删除**：937 行 vibe/coaching 测试文件整块删除，feature 已不存在

---

## 2. SESSION_SOURCE 四模式路由

**常量定义**（`src/evolve.js:100`）：

```javascript
const SESSION_SOURCE = (process.env.EVOLVER_SESSION_SOURCE || 'auto').toLowerCase();
```

### 2.1 路由矩阵

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `auto`（默认） | OpenClaw 优先 → Cursor 回退 | 通用场景，向后兼容 |
| `cursor` | 仅 Cursor/Codex/Manus | 纯 IDE 场景（Cursor/Codex/Manus） |
| `openclaw` | 仅 OpenClaw sessions | 隔离 OpenClaw 环境 |
| `merge` | OpenClaw + Cursor 拼接 | 多运行时并行使用 |

**路由实现**（`src/evolve.js:430–470`，`readRealSessionLog()`）：

```javascript
function readRealSessionLog() {
  if (SESSION_SOURCE === 'cursor') {
    const content = readCursorTranscripts();
    if (content) return content;
    return '[NO SESSION LOGS FOUND]';
  }

  if (SESSION_SOURCE === 'openclaw') {
    const content = readOpenClawSessions();
    if (content) return content;
    return '[NO SESSION LOGS FOUND]';
  }

  if (SESSION_SOURCE === 'merge') {
    const ocContent = readOpenClawSessions();
    const cursorContent = readCursorTranscripts();
    if (ocContent && cursorContent) {
      return ocContent + '\n\n' + cursorContent;
    }
    return ocContent || cursorContent || '[NO SESSION LOGS FOUND]';
  }

  // 'auto' (default): OpenClaw primary, Cursor fallback
  const ocContent = readOpenClawSessions();
  if (ocContent) return ocContent;

  const cursorContent = readCursorTranscripts();
  if (cursorContent) {
    console.log('[SessionFallback] Using Cursor agent-transcripts as session source.');
    return cursorContent;
  }
  return '[NO SESSION LOGS FOUND]';
}
```

### 2.2 关键设计决策

1. **短路返回**：`merge` 模式只要任一来源有内容就返回，不会因某一来源空而放弃另一来源
2. **显式回退**：`auto` 模式的 Cursor 回退会打日志，便于排查
3. **隔离优先**：`cursor` / `openclaw` 模式完全跳过另一来源，避免污染

---

## 3. readOpenClawSessions 新函数

**定位**：从 OpenClaw agents 会话目录读取结构化 JSONL。

**关键参数**：

```javascript
const AGENT_SESSIONS_DIR = path.join(os.homedir(), `.openclaw/agents/${AGENT_NAME}/sessions`);
const ACTIVE_WINDOW_MS = 24 * 60 * 60 * 1000;  // 24小时活跃窗口
const TARGET_BYTES = 120000;                    // 总目标大小
const PER_SESSION_BYTES = 20000;               // 每会话上限
const MAX_SESSIONS = 6;
```

**关键设计**：

### 3.1 SessionScope 过滤

```javascript
const sessionScope = getSessionScope();  // 从 paths.js 获取

let nonEvolverFiles = files.filter(f => !f.name.startsWith('evolver_hand_'));

if (sessionScope && nonEvolverFiles.length > 0) {
  const scopeLower = sessionScope.toLowerCase();
  const scopedFiles = nonEvolverFiles.filter(f => f.name.toLowerCase().includes(scopeLower));
  if (scopedFiles.length > 0) {
    nonEvolverFiles = scopedFiles;
    console.log(`[SessionScope] Filtered to ${scopedFiles.length} session(s) matching scope "${sessionScope}".`);
  }
}
```

- 排除 `evolver_hand_` 前缀的会话文件（防止自我观测干扰）
- 若 `sessionScope` 存在，按 scope 关键词过滤文件
- 无匹配时回退到全部非 evolver 文件

### 3.2 字节预算分配

```javascript
for (let i = 0; i < maxSessions && totalBytes < TARGET_BYTES; i++) {
  const bytesLeft = TARGET_BYTES - totalBytes;
  const readSize = Math.min(PER_SESSION_BYTES, bytesLeft);
  const raw = readRecentLog(path.join(AGENT_SESSIONS_DIR, f.name), readSize);
  const formatted = formatSessionLog(raw);
  if (formatted.trim()) {
    sections.push(`--- SESSION (${f.name}) ---\n${formatted}`);
    totalBytes += formatted.length;
  }
}
```

- 多会话轮询分配，每会话上限 20KB
- 总计上限 120KB
- 最多 6 个会话
- 按时间倒序取最新

### 3.3 与旧版差异

| 维度 | 旧版（v1.46） | 新版（v1.47） |
|------|--------------|--------------|
| 函数拆分 | `readRealSessionLog` 单一函数 | `readOpenClawSessions()` 独立 |
| evolver 文件过滤 | 无 | 排除 `evolver_hand_` 前缀 |
| SessionScope | 在 `formatSessionLog` 内 | 在 `readOpenClawSessions` 内 |
| 字节预算 | 单一文件读取 | 多会话轮询分配 |

---

## 4. collectTranscriptFiles 递归发现

**定位**：替代原手工路径指定，支持 Cursor 多层嵌套目录。

```javascript
function collectTranscriptFiles(dir, maxDepth) {
  const results = [];
  function walk(d, depth) {
    if (depth > maxDepth) return;
    let entries;
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const ent of entries) {
      if (ent.isFile() && (ent.name.endsWith('.jsonl') || ent.name.endsWith('.txt'))) {
        const fp = path.join(d, ent.name);
        try {
          const st = fs.statSync(fp);
          results.push({ path: fp, name: ent.name, time: st.mtime.getTime(), size: st.size });
        } catch { /* skip unreadable */ }
      } else if (ent.isDirectory() && ent.name !== 'subagents' && ent.name !== 'node_modules') {
        walk(path.join(d, ent.name), depth + 1);
      }
    }
  }
  walk(dir, 0);
  return results;
}
```

**设计要点**：

1. **递归深度限制**：maxDepth=3，防止无限遍历
2. **跳过目录**：`subagents/` 和 `node_modules/` 不进入
3. **文件过滤器**：`.jsonl` 和 `.txt` 两种格式
4. **元组返回**：`{path, name, time, size}` 便于后续排序和大小筛选
5. **容错**：任何异常跳过该条目，不中断整个 walk

---

## 5. readCursorTranscripts 更新

**位置**：`src/evolve.js:317`

```javascript
function readCursorTranscripts() {
  if (!CURSOR_TRANSCRIPTS_DIR) return '';

  // 递归收集 Cursor JSONL 文件（深度3）
  let files = collectTranscriptFiles(CURSOR_TRANSCRIPTS_DIR, 3)
    .filter(f => (now - f.time) < ACTIVE_WINDOW_MS)  // 7天活跃窗口
    .sort((a, b) => b.time - a.time);               // 最新优先

  // 字节预算同 OpenClaw：
  const TARGET_BYTES = 120000;
  const PER_FILE_BYTES = 20000;
  const MAX_FILES = 6;
}
```

**与 readOpenClawSessions 对比**：

| 维度 | OpenClaw | Cursor |
|------|----------|--------|
| 活跃窗口 | 24h | 7天 |
| 目录结构 | 平面（readdirSync） | 嵌套（递归 walk） |
| 跳过目录 | `evolver_hand_` 文件名 | `subagents/` 目录 |
| 环境变量 | `AGENT_NAME` 推导 | `EVOLVER_CURSOR_TRANSCRIPTS_DIR` |
| 文件格式 | OpenClaw JSONL | Cursor/Codex/Manus 多格式 |

---

## 6. v1.47 vibe Feature 删除

**删除文件**：`test/vibe_test.js`（937 行）

**删除内容**：
- `vibe` / `Vibe` 相关所有代码引用（grep 无结果）
- `getVibe`, `vibeScore`, `vibeCheck`, `vibeEval` 均不存在
- vibe/coaching 能力已从代码库完全移除

**影响**：
- 之前的 `09` BlueCortex bridge 中关于 vibe 的对照内容需要核实是否仍有效
- 如果 CE 侧有借鉴 vibe 的计划，需要重新评估

---

## 7. CE 借鉴路径

### P0（可直接落地）

1. **多源会话路由**：CE Java 侧可引入 `SESSION_SOURCE` 类似的环境变量控制，从多个会话目录/来源合并内容
2. **递归文件发现**：`collectTranscriptFiles` 的递归 walk + depth limit + 跳过规则的模式，可在 CE 的 transcript 读取中复用
3. **字节预算分配**：多会话轮询分配（每会话上限 + 总上限）在 CE 的 context window 管理中有直接借鉴价值

### P1（需设计评审）

4. **SessionScope 过滤**：`sessionScope` 关键词匹配文件名的模式，可用于 CE 的多租户 session 隔离
5. **merge 模式拼接**：多源内容拼接时序（OpenClaw → Cursor）可参考

### P2（长期参考）

6. **vibe 删除警示**：CE 若有类似"评分/点评"类 feature，应确保有持续维护意愿再引入

---

## 8. 与 doc 32 的关系

doc 32（`32-v146-147-multiagent-session-sse-swarm.md`）覆盖：
- ✅ 多 Agent JSONL 格式解析链（Claude Code / OpenClaw / Cursor / Codex / Manus）
- ✅ `formatSessionLog` 函数重写
- ❌ `SESSION_SOURCE` 四模式路由（新）
- ❌ `readOpenClawSessions` 独立函数（新）
- ❌ `collectTranscriptFiles` 递归发现（新）
- ❌ vibe 删除（架构变更）

本 doc 是 doc 32 的补充，聚焦 v1.47 session 架构重构。
