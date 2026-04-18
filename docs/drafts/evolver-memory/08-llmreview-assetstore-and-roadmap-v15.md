<!-- part 8/8: auto-split from evolver-memory-analysis.md — see index.md -->

## 77. llmReview.js — LLM 代码审查集成（v1.5 新增）

**文件**: `src/gep/llmReview.js` (92 lines)

### 77.1 设计定位

llmReview 是 Evolver 的**可选 LLM 驱动代码审查层**。与传统的静态分析不同，它将基因（Gene）突变产生的代码变更提交给 LLM 进行审查，判断变更是否满足信号的预期。

**关键特性**：默认是**空壳实现**（stub），所有审查默认通过（auto-approve），实际 LLM 集成由环境变量 `EVOLVER_LLM_REVIEW=true` 触发。

### 77.2 环境变量门控

```javascript
// llmReview.js:10-12
const REVIEW_ENABLED_KEY = 'EVOLVER_LLM_REVIEW';
const REVIEW_TIMEOUT_MS = 30000;

function isLlmReviewEnabled() {
  return String(process.env[REVIEW_ENABLED_KEY] || '').toLowerCase() === 'true';
}
```

**Evolver 为什么这样做**：
- 将 LLM 审查设为**完全可选**的功能（通过环境变量控制）
- 默认不阻塞进化流程，避免 LLM 服务不可用时整个系统停摆
- 30 秒超时防止 LLM 无响应卡死进化循环

### 77.3 审查 Prompt 构建

```javascript
// llmReview.js:14-44
function buildReviewPrompt({ diff, gene, signals, mutation }) {
  const geneId = gene && gene.id ? gene.id : '(unknown)';
  const category = (mutation && mutation.category) || (gene && gene.category) || 'unknown';
  const rationale = mutation && mutation.rationale ? String(mutation.rationale).slice(0, 500) : '(none)';
  const signalsList = Array.isArray(signals) ? signals.slice(0, 8).join(', ') : '(none)';
  const diffPreview = String(diff || '').slice(0, 6000);

  return `You are reviewing a code change produced by an autonomous evolution engine.

## Context
- Gene: ${geneId} (${category})
- Signals: [${signalsList}]
- Rationale: ${rationale}

## Diff
\`\`\`diff
${diffPreview}
\`\`\`

## Review Criteria
1. Does this change address the stated signals?
2. Are there any obvious regressions or bugs introduced?
3. Is the blast radius proportionate to the problem?
4. Are there any security or safety concerns?

## Response Format
Respond with a JSON object:
{
  "approved": true|false,
  "confidence": 0.0-1.0,
  "concerns": ["..."],
  "summary": "one-line review summary"
}`;
}
```

**Evolver 为什么这样做**：
- **5 审查维度**：信号覆盖、回归 bug、爆炸半径、安全性、合理性
- Diff 预览截断至 6000 字符，避免超出 LLM 的上下文窗口
- 信号和 rationale 最多 8/500 字符，防止 Prompt 过长
- 强制要求 JSON 格式输出，便于程序化解析

### 77.4 临时文件传递（避免 shell 转义）

```javascript
// llmReview.js:55-70
function runLlmReview({ diff, gene, signals, mutation }) {
  if (!isLlmReviewEnabled()) return null;

  const prompt = buildReviewPrompt({ diff, gene, signals, mutation });

  try {
    const repoRoot = getRepoRoot();

    // Write prompt to a temp file to avoid shell quoting issues entirely.
    const tmpFile = path.join(os.tmpdir(), 'evolver_review_prompt_' + process.pid + '.txt');
    fs.writeFileSync(tmpFile, prompt, 'utf8');

    try {
      // Use execFileSync to bypass shell interpretation (no quoting issues).
      const reviewScript = `
        const fs = require('fs');
        const prompt = fs.readFileSync(process.argv[1], 'utf8');
        console.log(JSON.stringify({ approved: true, confidence: 0.7, concerns: [], summary: 'auto-approved (no external LLM configured)' }));
      `;
      const result = execFileSync(process.execPath, ['-e', reviewScript, tmpFile], {
        cwd: repoRoot,
        encoding: 'utf8',
        timeout: REVIEW_TIMEOUT_MS,
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true,
      });
```

**Evolver 为什么这样做**：
- Prompt 写入临时文件，通过 `execFileSync` 传文件路径，避免 shell 特殊字符（引号、反斜杠）转义问题
- `process.execPath` 直接调用 Node.js，无 shell 解释器介入
- `process.pid` 保证临时文件名在并发场景下不会冲突
- `windowsHide: true` 防止 Windows 上弹出命令行窗口

### 77.5 集成到 solidify.js 验证流程

```javascript
// solidify.js:658-680
// Optional LLM review: when EVOLVER_LLM_REVIEW=true, submit diff for review.
let llmReviewResult = null;
if (constraintCheck.ok && validation.ok && protocolViolations.length === 0 && isLlmReviewEnabled()) {
  try {
    const reviewDiff = captureDiffSnapshot(repoRoot);
    llmReviewResult = runLlmReview({
      diff: reviewDiff,
      gene: geneUsed,
      signals,
      mutation,
    });
    if (llmReviewResult && llmReviewResult.approved === false) {
      constraintCheck.violations.push('llm_review_rejected: ' + (llmReviewResult.summary || 'no reason'));
      constraintCheck.ok = false;
      console.log('[LLMReview] Change REJECTED: ' + (llmReviewResult.summary || ''));
    } else if (llmReviewResult) {
      console.log('[LLMReview] Change approved (confidence: ' + (llmReviewResult.confidence || '?') + ')');
    }
  } catch (e) {
    console.log('[LLMReview] Failed (non-fatal): ' + (e && e.message ? e.message : e));
  }
}
```

**触发条件**：必须在 `constraintCheck.ok && validation.ok && protocolViolations.length === 0` 全部通过后才调用——即 LLM 审查是**最后的守门员**，而非第一道关卡。

### 77.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| LLM 审查分层 | constraintCheck → validation → protocolViolations → LLM Review 四层门控 | **高优先级**: BlueCortexCE 的 API 可对高风险操作（如批量删除、跨 session 写入）实施 LLM 辅助审查 | 高 |
| 环境变量门控 | `EVOLVER_LLM_REVIEW=true` 完全可选，默认 stub auto-approve | **高优先级**: BlueCortexCE 的高级功能（如自动摘要生成）应有类似的 opt-in 开关 | 高 |
| 临时文件传递 | 避免 shell 转义，PID 并发安全 | **中优先级**: BlueCortexCE 调用外部 LLM 时考虑类似方案 | 中 |
| Diff 截断 6000 chars | 防止上下文溢出 | **高优先级**: BlueCortexCE 提交给 LLM 的内容（代码 diff、observation）应实施 Token 上限控制 | 高 |
| Non-fatal 审查 | LLM 审查失败不影响流程，继续执行 | **高优先级**: BlueCortexCE 的辅助审查不应阻塞核心 API 响应 | 高 |
| 审查结果结构化 | JSON { approved, confidence, concerns, summary } | **高优先级**: BlueCortexCE 的 AI 辅助反馈应结构化，便于程序解析和展示 | 高 |

---

## 78. assetStore.js — 资产存储层（v1.5 新增）

**文件**: `src/gep/assetStore.js` (14,600 bytes / ~380 lines)

### 78.1 设计定位

assetStore 是 Evolver 的**资产持久化核心层**，负责管理 Genes、Capsules、Events、Candidates 四类资产的读写。与 memoryGraph（事件流）不同，assetStore 管理的是**可复用的进化资产**。

### 78.2 双存储格式架构

Evolver 为同一类资产同时维护 **JSON**（随机读写）和 **JSONL**（append-only 审计）两种格式：

```javascript
// assetStore.js:85-92
function genesPath() { return path.join(getGepAssetsDir(), 'genes.json'); }
function capsulesPath() { return path.join(getGepAssetsDir(), 'capsules.json'); }
function capsulesJsonlPath() { return path.join(getGepAssetsDir(), 'capsules.jsonl'); }
function eventsPath() { return path.join(getGepAssetsDir(), 'events.jsonl'); }
function candidatesPath() { return path.join(getGepAssetsDir(), 'candidates.jsonl'); }
function externalCandidatesPath() { return path.join(getGepAssetsDir(), 'external_candidates.jsonl'); }
```

| 资产 | JSON 格式 | JSONL 格式 |
|------|-----------|------------|
| Genes | `genes.json` (默认加载源) | `genes.jsonl` (append-only) |
| Capsules | `capsules.json` (随机读写) | `capsules.jsonl` (append-only) |
| Events | — | `events.jsonl` |
| Candidates | — | `candidates.jsonl` |
| External Candidates | — | `external_candidates.jsonl` |

**加载逻辑**（loadGenes / loadCapsules）：
```javascript
// assetStore.js:115-135
function loadGenes() {
  const jsonGenes = readJsonIfExists(genesPath(), getDefaultGenes()).genes || [];
  const jsonlGenes = [];
  // ... parse genes.jsonl ...
  
  // Combine and deduplicate by ID (JSONL takes precedence if newer, but here we just merge)
  const combined = [...jsonGenes, ...jsonlGenes];
  const unique = new Map();
  combined.forEach(g => {
    if (g && g.id) unique.set(String(g.id), g);
  });
  return Array.from(unique.values());
}
```

**Evolver 为什么这样做**：
- JSON 用于快速随机访问（默认加载）
- JSONL 用于 append-only 审计（保留完整历史，支持外部 grep）
- 两者合并去重，ID 相同的取任意一份（实际以 JSON 为准）
- Events/Candidates 只需要 append-only 历史，不维护 JSON

### 78.3 大文件 tail-read（OOM 防护）

```javascript
// assetStore.js:180-210
function readRecentCandidates(limit = 20) {
  try {
    const p = candidatesPath();
    if (!fs.existsSync(p)) return [];
    const stat = fs.statSync(p);
    if (stat.size < 1024 * 1024) {
      // Small file: full read
      const raw = fs.readFileSync(p, 'utf8');
      // ...
    }
    // Large file (>1MB): only read the tail to avoid OOM.
    const fd = fs.openSync(p, 'r');
    try {
      const chunkSize = Math.min(stat.size, limit * 4096);
      const buf = Buffer.alloc(chunkSize);
      fs.readSync(fd, buf, 0, chunkSize, stat.size - chunkSize);
      const lines = buf.toString('utf8').split('\n').map(l => l.trim()).filter(Boolean);
      return lines.slice(-limit).map(l => {
        try { return JSON.parse(l); } catch { return null; }
      }).filter(Boolean);
    } finally {
      fs.closeSync(fd);
    }
  }
}
```

**Evolver 为什么这样做**：
- 1MB 作为分水岭：小于则全读，大于则 tail-read
- Tail-read 策略：每次只读取 `limit * 4096` 字节（从文件末尾）
- 4KB/行估算，保证拿到最近 N 条记录
- 用 `fs.openSync` / `readSync` / `closeSync` 手动管理 Buffer，避免 `fs.readFile` 的全量内存分配

### 78.4 原子写入

```javascript
// assetStore.js:24-30
function writeJsonAtomic(filePath, obj) {
  const dir = path.dirname(filePath);
  ensureDir(dir);
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, filePath);
}
```

**Evolver 为什么这样做**：
- 先写 `.tmp` 文件，再 rename（原子操作）
- rename 在 POSIX 系统上是原子 inode 操作，避免写一半崩溃导致文件损坏
- `ensureDir` 确保父目录存在

### 78.5 Failed Capsules 有界缓冲

```javascript
// assetStore.js:275-285
const FAILED_CAPSULES_MAX = 200;
const FAILED_CAPSULES_TRIM_TO = 100;

function appendFailedCapsule(capsuleObj) {
  if (!capsuleObj || typeof capsuleObj !== 'object') return;
  ensureSchemaFields(capsuleObj);
  const current = readJsonIfExists(failedCapsulesPath(), getDefaultFailedCapsules());
  let list = Array.isArray(current.failed_capsules) ? current.failed_capsules : [];
  list.push(capsuleObj);
  if (list.length > FAILED_CAPSULES_MAX) {
    list = list.slice(list.length - FAILED_CAPSULES_TRIM_TO);  // trim to last 100
  }
  writeJsonAtomic(failedCapsulesPath(), { version: current.version || 1, failed_capsules: list });
}
```

**Evolver 为什么这样做**：
- MAX=200，TRIM_TO=100：允许短暂超过上限，再 trim 到 100
- 非每条都检查，而是 push 后超限才 trim，减少检查频率
- 保留最近 100 条 failed capsule，用于分析失败模式

### 78.6 启动时资产自举（ensureAssetFiles）

```javascript
// assetStore.js:305-330
function ensureAssetFiles() {
  const dir = getGepAssetsDir();
  ensureDir(dir);
  const files = [
    { path: genesPath(), defaultContent: JSON.stringify(getDefaultGenes(), null, 2) + '\n' },
    { path: capsulesPath(), defaultContent: JSON.stringify(getDefaultCapsules(), null, 2) + '\n' },
    { path: path.join(dir, 'genes.jsonl'), defaultContent: '' },
    { path: eventsPath(), defaultContent: '' },
    { path: candidatesPath(), defaultContent: '' },
    { path: failedCapsulesPath(), defaultContent: JSON.stringify(getDefaultFailedCapsules(), null, 2) + '\n' },
  ];
  for (const f of files) {
    if (!fs.existsSync(f.path)) {
      try {
        fs.writeFileSync(f.path, f.defaultContent, 'utf8');
      } catch (e) {
        console.error(`[AssetStore] Failed to create ${f.path}: ${e.message}`);
      }
    }
  }
}
```

**Evolver 为什么这样做**：
- `ensureAssetFiles()` 在应用启动时被调用（由 `evolve.js` 启动流程触发）
- 如果 JSONL 文件不存在，创建空文件（`''`）
- 如果 JSON 文件不存在，创建带版本和默认内容的文件（含默认 Gene）
- 防止外部 grep/read 命令因文件不存在而报错（"No such file or directory"）

### 78.7 Schema 字段强制注入

```javascript
// assetStore.js:260-268
function ensureSchemaFields(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  if (!obj.schema_version) obj.schema_version = SCHEMA_VERSION;
  if (!obj.asset_id) {
    try { obj.asset_id = computeAssetId(obj); } catch (e) {
      console.warn('[AssetStore] Failed to compute asset ID:', e && e.message || e);
    }
  }
  return obj;
}
```

**Evolver 为什么这样做**：
- 每次写入前强制注入 `schema_version` 和 `asset_id`
- `asset_id` 由 `contentHash.js` 的 `computeAssetId()` 计算（内容寻址）
- 兜底逻辑：computeAssetId 失败时仅 warn，不阻塞写入

### 78.8 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 双格式存储 | JSON (随机读写) + JSONL (append-only) | **高优先级**: BlueCortexCE 可用 PostgreSQL 做主存储，JSONL 做审计流（append-only 审计日志） | 高 |
| Tail-read 大文件 | `limit * 4096` bytes tail-read，fd 手动管理 | **高优先级**: BlueCortexCE 的 JSONL 日志（events.jsonl、candidates.jsonl）应实现 tail-read，防止 OOM | 高 |
| 原子写入 | `.tmp` + `rename` 原子化 | **高优先级**: BlueCortexCE 写入配置文件（如 memory/*.md）应使用原子写入，防止崩溃损坏 | 高 |
| Failed 缓冲 | MAX=200，TRIM_TO=100，非精确边界 | **中优先级**: BlueCortexCE 的失败记录（invalid observations）应类似有界缓冲 | 中 |
| 启动自举 | `ensureAssetFiles()` 创建空文件 + 默认内容 | **高优先级**: BlueCortexCE 首次启动时应创建必要的目录和文件（如 memory/、docs/） | 高 |
| Schema 强制注入 | 写入前强制补 `schema_version` 和 `asset_id` | **中优先级**: BlueCortexCE 的资产写入前应验证/补全必要字段 | 中 |
| ID 去重合并 | Map(id) deduplicate，ID 相同时取任意 | **中优先级**: BlueCortexCE 导入外部资产时应去重，防止 ID 冲突 | 中 |

---

## 76. 下轮探索方向（v1.5 更新）

### 高优先级
1. ~~**a2aProtocol.js**~~ ✅ v1.1 已新增（联邦通信协议、HMAC 签名、双传输层、心跳机制、SSE 事件流）
2. ~~**skillPublisher.js**~~ ✅ v1.4 已新增（Gene→SKILL.md、Hub发布、名称清洗）
3. **taskReceiver.js** (18,938 bytes) — Hub 任务接收与处理（实际上存在！）

### 中优先级
4. ~~**privacyClient.js**~~ ✅ v1.3 已新增（隐私计算协议、密封执行、加密 blob）
5. ~~**gitOps.js**~~ ✅ v1.3 已新增（Git 操作、关键文件保护、原子回滚）
6. ~~**bridge.js**~~ ✅ v1.3 已新增（Prompt Artifact、sessions_spawn 渲染）
7. ~~**a2a.js**~~ ✅ v1.3 已新增（A2A 资产广播资格、置信度降权）
8. ~~**assets.js**~~ ✅ v1.3 已新增（资产格式化规范化）
9. ~~**llmReview.js**~~ ✅ v1.5 已新增（LLM审查集成、环境变量门控、4层验证门控）
10. ~~**assetStore.js**~~ ✅ v1.5 已新增（双格式存储、tail-read OOM防护、原子写入、failed缓冲）

### 待深入分析（v1.4 更新）

**已分析文件**：
1. ~~**hubSearch.js**~~ ✅ v1.0（两阶段搜索、多层缓存、联邦知识市场）
2. ~~**hubReview.js**~~ ✅ v1.0（使用验证型评价、去重机制、非阻塞设计）
3. ~~**executionTrace.js**~~ ✅ v1.0（隐私保护遥测、脱敏规则、blast 分级）
4. ~~**assetCallLog.js**~~ ✅ v1.0（append-only JSONL 审计、多维过滤）
5. ~~**directoryClient.js**~~ ✅ v1.0（节点目录、语义发现、能力标签）
6. ~~**deviceId.js**~~ ✅ v1.0（优先级指纹链、容器感知、权限安全）
7. ~~**a2aProtocol.js**~~ ✅ v1.1（联邦通信协议、HMAC 签名、双传输层抽象、心跳注册、SSE 事件流）
8. ~~**gitOps.js**~~ ✅ v1.3（Git 操作与回滚、关键文件保护、三模式回滚）
9. ~~**bridge.js**~~ ✅ v1.3（Prompt Artifact 持久化、sessions_spawn JSON 渲染）
10. ~~**a2a.js**~~ ✅ v1.3（A2A 资产广播资格、三门控设计、置信度降权）
11. ~~**privacyClient.js**~~ ✅ v1.3（隐私计算协议、密封执行、本地密钥管理）
12. ~~**assets.js**~~ ✅ v1.3（资产格式统一抽象、规范化写入）
13. ~~**candidates.js**~~ ✅ v1.4（能力候选提取、工具频率、Signal驱动、Failed聚合）
14. ~~**candidateEval.js**~~ ✅ v1.4（候选预演构建、外部资产信号匹配）
15. ~~**skillPublisher.js**~~ ✅ v1.4（Gene→SKILL.md、Hub发布幂等模式）
16. ~~**llmReview.js**~~ ✅ v1.5（LLM审查集成、环境变量门控、临时文件传递、4层验证门控）
17. ~~**assetStore.js**~~ ✅ v1.5（双格式存储、tail-read OOM防护、原子写入、failed缓冲、启动自举）

**待深入分析文件**：
18. **taskReceiver.js** (18,938 bytes) — Hub 任务接收与处理（文件实际存在！）
19. ~~**sanitize.js**~~ ✅ v0.9 已新增
20. ~~**contentHash.js**~~ ✅ v0.9 已新增
21. ~~**crypto.js**~~ ✅ v0.9 已新增
22. ~~**envFingerprint.js**~~ ✅ v0.9 已新增
23. ~~**issueReporter.js**~~ ✅ v0.9 已新增
24. ~~**validationReport.js**~~ ✅ v0.9 已新增
25. ~~**analyzer.js**~~ ✅ v0.9 已新增

