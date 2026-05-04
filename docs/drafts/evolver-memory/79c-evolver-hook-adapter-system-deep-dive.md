# v79c Hook 适配系统深度分析

**版本**：v79c | **时间**：2026-05-04 | **模块**：hookAdapter.js · evolver-signal-detect.js · evolver-session-start.js · evolver-session-end.js

---

## 1. hookAdapter.js — 跨平台 Hook 管理框架

**文件**：`src/adapters/hookAdapter.js`（~240行）| **设计原则**：平台无关 / 原子写入 / 幂等安装卸载 / Marker 驱动清理

### 1.1 支持的平台

```js
const PLATFORMS = {
  cursor:        { name: 'Cursor',       configDir: '.cursor' },
  'claude-code': { name: 'Claude Code',  configDir: '.claude' },
  codex:         { name: 'Codex',        configDir: '.codex' },
  kiro:          { name: 'Kiro',         configDir: '.kiro' },
};
```

### 1.2 平台检测

```js
function detectPlatform(cwd) {
  const root = cwd || process.cwd();
  const home = os.homedir();
  // 1. 检查 cwd 下是否有 .cursor / .claude / .codex / .kiro
  for (const [id, meta] of Object.entries(PLATFORMS)) {
    if (fs.existsSync(path.join(root, meta.detector))) return id;
  }
  // 2. 检查 home 目录（适用于全局安装场景）
  for (const [id, meta] of Object.entries(PLATFORMS)) {
    if (fs.existsSync(path.join(home, meta.detector))) return id;
  }
  return null;
}
```

### 1.3 原子 JSON Merge

```js
function mergeJsonFile(filePath, patch, { markerKey = '_evolver_managed' } = {}) {
  // 1. 读取现有 JSON
  let existing = {};
  if (fs.existsSync(filePath)) {
    const raw = fs.readFileSync(filePath, 'utf8').trim();
    if (raw) existing = JSON.parse(raw);
  }
  // 2. 深度合并（array 不合并，覆盖）
  const merged = deepMerge(existing, patch);
  merged[markerKey] = true;  // 标记为 evolver 管理
  // 3. 原子写入：tmp → rename
  const tmp = filePath + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(merged, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, filePath);
  return merged;
}
```

**安全**：使用 `tmp + rename` 实现原子写入，防止写入一半（进程崩溃/断电）导致配置文件损坏。

### 1.4 Hook Script 复制

```js
function copyHookScripts(destDir, evolverRoot) {
  const scripts = [
    'evolver-session-start.js',
    'evolver-signal-detect.js',
    'evolver-session-end.js',
  ];
  fs.mkdirSync(destDir, { recursive: true });
  for (const name of scripts) {
    const dest = path.join(destDir, name);
    fs.copyFileSync(src, dest);
    try { fs.chmodSync(dest, 0o755); } catch { /* windows */ }
    copied.push(dest);
  }
  return copied;
}
```

### 1.5 Marker 驱动的 Hook 卸载

```js
function removeEvolverHooks(filePath, { markerKey = '_evolver_managed' } = {}) {
  // 1. 检查 marker 是否存在
  if (!data[markerKey]) return false;
  // 2. 过滤掉 evolver 相关 hooks（不退其他 hook）
  data.hooks[event] = data.hooks[event].filter(h => {
    const cmd = h.command || '';
    return !cmd.includes('evolver-session') && !cmd.includes('evolver-signal');
  });
  // 3. 删除 marker
  delete data[markerKey];
  // 4. 原子写回
}
```

**设计亮点**：使用 marker (`_evolver_managed`) 而非简单删除，只删除 evolver 自己的 hooks，保留其他工具添加的 hooks。

### 1.6 Section Append（用于 appendToFile）

```js
function appendSectionToFile(filePath, marker, content) {
  let existing = fs.readFileSync(filePath, 'utf8');
  // 已包含 marker → 幂等跳过
  if (existing.includes(marker)) return false;
  const separator = existing.length > 0 && !existing.endsWith('\n') ? '\n\n' : '\n';
  fs.writeFileSync(filePath, existing + separator + content + '\n', 'utf8');
  return true;
}
```

### 1.7 Unify setupHooks 接口

```js
async function setupHooks({ platform, cwd, force, uninstall, evolverRoot } = {}) {
  const platformId = platform || detectPlatform(cwd);
  const adapter = loadAdapter(platformId);  // cursor.js / claudeCode.js / codex.js / kiro.js
  if (uninstall) return adapter.uninstall({ configRoot, evolverRoot });
  return adapter.install({ configRoot, evolverRoot, force });
}
```

每个平台 adapter 实现自己的 `install()` 和 `uninstall()` 方法，hookAdapter 提供统一的发现和调度层。

### 1.8 CE 借鉴

**P0**：原子写入（tmp + rename）→ BlueCortexCE 在修改任何 JSON 配置文件时，必须使用 `tmp + rename` 模式，防止文件损坏。参考 Java: `Files.write(tmpPath, ...) + Files.move(tmp, target, REPLACE_EXISTING)`。

**P0**：Marker 驱动清理 (`_evolver_managed`) → CE 在修改配置文件（如 OpenClaw settings.json）时，应添加 marker 标记，便于以后完全清理。清理时只删除带 marker 的条目。

**P1**：平台检测双重检查（cwd → home）→ CE 的 OpenClaw 插件在检测环境时，应同时检查本地配置和全局配置。

**P1**：幂等安装/卸载 → CE 的 setup 函数应设计为可重复执行（多次 `setupHooks()` 不重复添加），且 `uninstall` 能干净删除所有添加的内容。

---

## 2. evolver-signal-detect.js — 轻量信号检测 Hook

**文件**：`src/adapters/scripts/evolver-signal-detect.js`（~110行）| **设计原则**：Zero-dependency / Synchronous-fast / 1.5s timeout

### 2.1 7类信号

```js
const SIGNAL_KEYWORDS = {
  perf_bottleneck:       ['timeout', 'slow', 'latency', 'bottleneck', 'oom', 'out of memory', 'performance'],
  capability_gap:       ['not supported', 'unsupported', 'not implemented', 'missing feature', 'not available'],
  log_error:             ['error:', 'exception:', 'typeerror', 'referenceerror', 'syntaxerror', 'failed'],
  user_feature_request:  ['add feature', 'implement', 'new function', 'new module', 'please add'],
  recurring_error:       ['same error', 'still failing', 'not fixed', 'keeps failing', 'repeatedly'],
  deployment_issue:      ['deploy failed', 'build failed', 'ci failed', 'pipeline', 'rollback'],
  test_failure:          ['test failed', 'test failure', 'assertion', 'expect(', 'assert.'],
};
```

### 2.2 输入解析（多平台兼容）

```js
const input = JSON.parse(inputData);
// Claude Code's PostToolUse payload nests tool args under tool_input.
const ti = input.tool_input || {};          // Claude Code
const tr = input.tool_response || {};      // Codex
// 多路径取值（兼容不同平台格式）
const content = ti.content || ti.new_string || ti.file_content
  || input.content || input.file_content || input.diff || '';
```

### 2.3 超时保护

```js
setTimeout(() => {
  if (handled) return;
  handled = true;
  process.stdout.write(JSON.stringify({}));  // 无信号 → 空输出
  process.exit(0);
}, 1500);  // 1.5s safety timeout
```

**为什么需要**：Hook 执行时间过长会阻塞平台响应。Claude Code 等平台对 hook 有执行时间限制，超过会强制终止。

### 2.4 输出格式

```js
// 有信号时
process.stdout.write(JSON.stringify({
  additional_context: `[Evolution Signal] Detected: [${signals.join(', ')}] in ${filePath || 'edited file'}. Consider recording this outcome.`,
  additionalContext: ctx,  // 兼容 Claude Code（驼峰式）
}));

// 无信号时
process.stdout.write(JSON.stringify({}));
```

### 2.5 CE 借鉴

**P1**：轻量关键词信号检测（零依赖）→ BlueCortexCE 的 Hook 层可以类似地做轻量级信号检测，在 Hook 阶段就识别信号，不等待完整处理。当前 CE 依赖 backend 的 `/api/observations` 做信号检测，但 Hook 层的早期信号检测可以更快触发进化。

**P2**：1.5s 超时 + 空输出 → CE 的任何 Hook 脚本都应有超时保护，超时后输出空对象（而非无输出），避免阻塞平台。

**P2**：多路径输入解析 → CE 的 Hook 如果需要解析多种平台输入，应实现类似的兼容层（`tool_input` → `tool_input || input`）。

---

## 3. evolver-session-start.js — 历史记忆注入 Hook

**文件**：`src/adapters/scripts/evolver-session-start.js`（~140行）| **设计原则**：去重保护 / 平台差异化 / Minimal context

### 3.1 去重保护机制

```js
// 问题：Kiro 等平台每次 user message 都触发 sessionStart-equivalent（promptSubmit）
// 如果不加去重，每次用户消息都会重新注入历史记忆 → 大量重复

// 解决方案：基于 (platform, cwd) 的 TTL dedup
function shouldSkipInjection() {
  const dedupEnabled = String(process.env.EVOLVER_SESSION_START_DEDUP || '').toLowerCase() === '1';
  if (!dedupEnabled) return false;  // Cursor/Claude Code/Codex → 不启用

  const ttlMs = Number(process.env.EVOLVER_SESSION_START_DEDUP_TTL_MS) || (30 * 60 * 1000);
  const key = process.cwd();  // 按工作目录隔离
  const now = Date.now();
  const last = state[key];
  if (typeof last === 'number' && now - last < ttlMs) return true;  // skip
  state[key] = now;  // update timestamp
  return false;  // proceed
}
```

**平台差异**：
- Cursor / Claude Code / Codex → 真正的 sessionStart 事件 → 不启用 dedup
- Kiro → 每次 promptSubmit（每条用户消息）→ 启用 dedup（TTL 30min）

### 3.2 evolverRoot 解析（多重 fallback）

```js
function findEvolverRoot() {
  const candidates = [
    process.env.EVOLVER_ROOT,
    path.resolve(__dirname, '..', '..', '..'),  // 相对于 hook script 的位置
  ];
  for (const c of candidates) {
    if (c && fs.existsSync(path.join(c, 'package.json'))) {
      const pkg = JSON.parse(fs.readFileSync(...));
      if (pkg.name === '@evomap/evolver' || pkg.name === 'evolver') return c;
    }
  }
  // fallback: ~/.skills/evolver
  const homeSkills = path.join(os.homedir(), 'skills', 'evolver');
  if (fs.existsSync(path.join(homeSkills, 'package.json'))) return homeSkills;
  return null;
}
```

### 3.3 记忆格式化

```js
function formatOutcome(entry) {
  const status = entry.outcome ? entry.outcome.status : 'unknown';
  const score = entry.outcome && entry.outcome.score != null ? entry.outcome.score : '?';
  const signals = Array.isArray(entry.signals) ? entry.signals.slice(0, 3).join(', ') : '';
  const ts = entry.timestamp ? entry.timestamp.slice(0, 10) : '';
  const icon = status === 'success' ? '+' : status === 'failed' ? '-' : '?';
  return `[${icon}] ${ts} score=${score} signals=[${signals}] ${note}`.slice(0, 200);
}
```

输出示例：
```
[Evolution Memory] Recent 5 outcomes (3 success, 2 failed):
[+] 2026-05-03 score=0.85 signals=[stable_success_plateau] ...
[-] 2026-05-03 score=0.3 signals=[log_error] ...
...
Use successful approaches. Avoid repeating failed patterns.
```

### 3.4 CE 借鉴

**P1**：去重保护机制 → BlueCortexCE 的 Hook 层（`evolver-session-start` 类似物）需要考虑平台差异：某些平台（Kiro）会在每条消息时触发 hook，需要类似 TTL dedup。当前 CE 的 Hook 主要依赖 OpenClaw 的 session 概念（sessionStart/sessionEnd），如果 OpenClaw 不在 Kiro 上工作，则无需 dedup。

**P1**：历史记忆注入（最近 N 条 + 成功/失败统计）→ CE 可以实现类似的历史 summary 注入：在每次 `/api/session/start` 时，注入最近 N 次会话的 summary（而非每次都注入完整 history），减少 context 长度。

**P2**：`[+]` / `[-]` 视觉化结果 → CE 的 context 可以使用类似图标快速传达历史 outcome 的好坏。

---

## 4. evolver-session-end.js — Session 结束记录 Hook

**文件**：`src/adapters/scripts/evolver-session-end.js`（~180行）| **设计原则**：Git diff 信号提取 / 双路径记录 / 注入防护 / 10MB buffer

### 4.1 Git Diff 统计

```js
function getGitDiffStats() {
  const stat = execSync('git diff --stat HEAD~1 2>/dev/null || git diff --stat 2>/dev/null', { ... });
  const diffContent = execSync('git diff HEAD~1 --no-color 2>/dev/null || git diff --no-color', { ... });
  return {
    stat,          // e.g. "3 files changed, 50 insertions(+), 10 deletions(-)"
    summary: `${filesChanged}, +${insertions}/-${deletions}`,
    diffSnippet: diffContent.slice(0, 2000),  // 只取前 2000 字符用于信号检测
    hasChanges: stat.length > 0,
  };
}
```

### 4.2 注入信号检测

```js
function detectSignals(text) {
  // 7类信号（同 evolver-signal-detect.js）
  // 额外的：stable_success_plateau（无错误 + 无 feature request）
}
```

### 4.3 Outcome 判断

```js
const hasErrors = signals.includes('log_error') || signals.includes('test_failure');
const status = hasErrors ? 'failed' : 'success';
const score = hasErrors ? 0.3 : 0.8;

const outcome = {
  geneId: 'ad_hoc',
  signals,
  status,
  score,
  summary: `Session end: ${diffInfo.summary}. Signals: [${signals.join(', ')}]`,
};
```

### 4.4 双路径记录

```js
const hubOk = recordToHub(outcome);           // 优先：Hub API
const localOk = graphPath ? recordToLocal(graphPath, outcome) : false;  // fallback: local JSONL
```

**recordToHub** — curl argv-array 防注入：
```js
spawnSync('curl', [
  '-s', '-m', '8', '-X', 'POST',
  '-H', 'Content-Type: application/json',
  '-H', `Authorization: Bearer ${apiKey}`,
  '-d', payload,
  `${hubUrl}/a2a/evolution/record`,
], { shell: false });  // argv-array，不走 shell
```

**注意**：使用 `spawnSync` 而非 `execSync`，且 `shell: false`，避免 apiKey/payload 中的 shell 元字符被解释。

### 4.5 7秒超时

```js
setTimeout(() => {
  if (handled) return;
  handled = true;
  process.stdout.write(JSON.stringify({}));
  process.exit(0);
}, 7000);  // 7s timeout
```

Session end hook 允许更长的超时（7s > signal-detect 的 1.5s），因为需要执行 git diff。

### 4.6 10MB Buffer

```js
const MAX_EXEC_BUFFER = 10 * 1024 * 1024;  // 10MB
// 防止 git log/diff 在大型仓库上返回超大量输出导致 RangeError
```

### 4.7 CE 借鉴

**P0**：curl argv-array + `shell: false` → BlueCortexCE 在调用任何外部命令时，如果涉及敏感数据（API key、token），必须使用 argv-array 形式，禁止 shell 拼接字符串。

**P0**：10MB buffer 保护 → CE 在调用 git diff/log 等可能产生大量输出的命令时，应设置合理的 buffer 限制和超时。

**P1**：双路径记录（Hub优先 → local fallback）→ CE 可以实现类似的双路径：在 Hub 连接正常时同步到 Hub，断开时写入本地 JSONL，下次连接时同步。

**P1**：无 git changes → 无输出（`process.stdout.write({})`）→ CE 如果在 session end 检测到没有实质性变化，不应注入任何 context，避免浪费。

**P2**：outcome score 硬编码（0.3 / 0.8）→ CE 的 session-end outcome 评估可以更精细（参考 `inferOutcomeEnhanced` 的 baseline/current delta 机制）。

---

## 5. 总体设计模式总结

| 模块 | 模式 | CE 优先级 |
|------|------|---------|
| hookAdapter.js | 原子 JSON merge（tmp + rename） | P0 |
| hookAdapter.js | `_evolver_managed` marker 驱动清理 | P0 |
| hookAdapter.js | 平台双重检测（cwd → home） | P1 |
| hookAdapter.js | 幂等 install/uninstall | P1 |
| hookAdapter.js | 统一 setupHooks 接口 + adapter 模式 | P1 |
| signal-detect | 1.5s 超时保护 | P1 |
| signal-detect | 多路径输入解析 | P2 |
| session-start | 去重保护（TTL dedup，平台差异化） | P1 |
| session-start | evolverRoot 多重 fallback | P2 |
| session-start | Top-N 记忆格式化 + 成功/失败统计 | P2 |
| session-end | curl argv-array + `shell: false` | P0 |
| session-end | 10MB exec buffer | P0 |
| session-end | 7s 超时（session-end 可较长） | P1 |
| session-end | 双路径记录（Hub优先 → local fallback） | P1 |
| session-end | 无 git changes → 空输出（幂等） | P1 |

---

## 6. Hook 与 OpenClaw 的关系

```
Platform (Cursor/Claude Code/Kiro/Codex)
  └─ Hook System (evolver-session-start / evolver-signal-detect / evolver-session-end)
       ├─ evolver-session-start.js → 注入历史记忆 → Agent context
       ├─ evolver-signal-detect.js → 检测信号 → inject additional_context
       └─ evolver-session-end.js → 记录 outcome → Hub API / local memory_graph.jsonl
            ↓
       Evolver Core (evolve.js)
            ↓
       BlueCortexCE (via OpenClaw agent wrapper: proxy/wrapper.js)
```

**关键**：Evolver Hooks 与 BlueCortexCE 通过 OpenClaw 的 `sessions_spawn` 协议通信（参考 `bridge.js` 的 `renderSessionsSpawnCall`）。Evolver 通过 Hook 感知平台事件，通过 `sessions_spawn` 委托 OpenClaw agent 执行操作。

---

## 7. 下一步

- **并行建议**：分析 `cursor.js` / `claudeCode.js` / `codex.js` / `kiro.js` 四个平台 adapter 的具体 install/uninstall 实现
- **待确认**：Kiro adapter 中 dedup 是在 `EVOLVER_SESSION_START_DEDUP=1` 环境变量中硬编码，还是动态检测
- **对比**：`evolver-session-end.js` 的 `recordToHub` 与 BlueCortexCE 的 Hub API 客户端是否可共用 HTTP 客户端
