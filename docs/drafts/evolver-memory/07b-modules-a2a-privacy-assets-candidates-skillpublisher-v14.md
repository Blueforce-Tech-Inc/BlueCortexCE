<!-- part 2/2: auto-split from 07-idle-through-skillpublisher-v14.md (sections 71-75) -->
## 71. privacyClient.js — 隐私计算与密封执行（v1.3 新增）

**文件**: `src/gep/privacyClient.js` (216 lines)

### 71.1 设计背景

privacyClient.js 实现 **隐私计算协议**——当某个任务需要处理敏感数据（如代码审计、日志分析）但又希望借助 Hub 的知识时，可以使用"密封执行"（Sealed Execution）模式：
1. 数据在本地加密后上传到 Hub
2. Hub 在加密 blob 上执行密封工具（不知道明文内容）
3. 结果返回本地后解密
4. Hub 始终不知道原始数据内容

### 71.2 核心流程

```
本地                    Hub                     本地
 |                       |                       |
 |-- submitPrivacyTask --&gt;|                       |
 |                       |                       |
 |&lt;-- taskId ----------|                       |
 |                       |                       |
 |-- uploadEncryptedBlob -| (blobId)             |
 |                       |                       |
 |            (Hub 对 blob 执行 sealed tool)      |
 |                       |                       |
 |-- executeSealedTool -|&gt;|                       |
 |                       |&lt;-- result -----------|
 |                       |                       |
 |-- getPrivacyResult --&gt;| (encrypted)          |
 |&lt;-- encrypted_result --|                       |
 |                       |                       |
(decrypt locally)        |                       |
 V                       |                       |
 plaintext result        |                       |
```

### 71.3 加密 blob 上传（uploadEncryptedBlob）

**文件**: `privacyClient.js:50-85`

```javascript
async function uploadEncryptedBlob(plaintext, opts) {
  const key = generateKey();          // 本地生成 AES-256-GCM 密钥
  const parts = encrypt(plaintext, key);  // 加密
  const packed = pack(parts);         // IV + authTag + ciphertext 打包
  
  const res = await fetch(privacyUrl('/blob/upload'), {
    body: JSON.stringify({
      data_base64: packed.toString('base64'),
      encryption: 'aes-256-gcm',
    }),
  });
  
  return {
    blobId: resp.blob_id,  // Hub 返回 blob ID
    key,                    // 本地保留密钥（关键！）
    iv: parts.iv,
    authTag: parts.authTag,
  };
}
```

**关键设计**：密钥 `key` **永远不发送**给 Hub。只有 Hub 收到加密数据，但无法解密。

### 71.4 密封工具执行（executeSealedTool）

**文件**: `privacyClient.js:90-115`

```javascript
async function executeSealedTool(opts) {
  // Hub 对加密 blob 执行 tool，不解密内容
  const res = await fetch(privacyUrl('/tool/execute'), {
    body: JSON.stringify({
      toolId: opts.toolId,   // 工具 ID（明文，Hub 可见）
      blobId: opts.blobId,  // 加密 blob ID（Hub 可见）
    }),
  });
  
  return { resultKey, resultHash, error };
}
```

**Evolver 为什么这样做**：`toolId` 是明文的（Hub 需要知道运行什么工具），`blobId` 指向加密数据（Hub 看不到内容）。这相当于"盲计算"——Hub 知道你在做什么工具，但不知道处理的是什么数据。

### 71.5 结果解密（getPrivacyResult）

**文件**: `privacyClient.js:130-155`

```javascript
async function getPrivacyResult(taskId, key) {
  const res = await fetch(privacyUrl(`/result/${taskId}`));
  const data = await res.json();
  
  // encrypted_result_base64 是 Hub 返回的加密结果
  const packed = Buffer.from(data.encrypted_result_base64, 'base64');
  const parts = unpack(packed);
  
  // 本地用本地密钥解密——Hub 不知道结果
  const plaintext = decrypt(parts.ciphertext, key, parts.iv, parts.authTag);
  return { plaintext, resultHash: data.result_hash };
}
```

### 71.6 隐私参数解析（parsePrivacyParams）

**文件**: `privacyClient.js:185-215`

```javascript
function parsePrivacyParams(body) {
  // 从 task body 中提取 [PRIVACY_PARAMS] 块
  // [PRIVACY_PARAMS]
  // tool_id: my_tool
  // blob_ids: blob1,blob2
  // [/PRIVACY_PARAMS]
  
  const block = body.substring(start + 16, end).trim();
  // 解析 key: value 行
  // 返回 { toolId, blobIds[] }
}
```

**Evolver 为什么这样做**：Hub 下发的任务 body 中可以包含隐私计算指令，Evolver 解析后执行对应的密封工具。

### 71.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 本地密钥生成 | key 从不离开客户端 | **高优先级**: BlueCortexCE 的隐私计算必须确保密钥不泄露 | 高 |
| 密封执行 | Hub 看到 toolId 但看不到 blob 内容 | **高优先级**: BlueCortexCE 处理敏感数据时可用类似模式 | 中 |
| 加密 blob 上传 | AES-256-GCM + pack(IV+authTag+ciphertext) | **中优先级**: BlueCortexCE 的隐私数据存储可参考此格式 | 中 |
| 结果本地解密 | Hub 返回加密结果，客户端解密 | **高优先级**: BlueCortexCE 的"云端处理"结果应在本地解密 | 高 |
| 隐私参数块 | [PRIVACY_PARAMS] 标签格式 | **低优先级**: BlueCortexCE 的任务描述格式可支持隐私标记 | 低 |

---

## 72. assets.js — 资产格式统一抽象（v1.3 新增）

**文件**: `src/gep/assets.js` (36 lines)

### 72.1 资产预览格式化（formatAssetPreview）

**文件**: `assets.js:8-35`

```javascript
function formatAssetPreview(preview) {
  if (!preview) return '(none)';
  if (typeof preview === 'string') {
    try {
      const parsed = JSON.parse(preview);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return JSON.stringify(parsed, null, 2);
      }
      return preview;
    } catch { return preview; }
  }
  return JSON.stringify(preview, null, 2);
}
```

**Evolver 为什么这样做**：资产预览可能是字符串（JSON 字符串）、数组或对象。统一格式化逻辑让 prompt 注入时的输出保持一致。

### 72.2 资产规范化（normalizeAsset）

**文件**: `assets.js:37-45`

```javascript
function normalizeAsset(asset) {
  if (!asset || typeof asset !== 'object') return asset;
  if (!asset.schema_version) asset.schema_version = SCHEMA_VERSION;
  if (!asset.asset_id) {
    try { asset.asset_id = computeAssetId(asset); } catch {}
  }
  return asset;
}
```

**Evolver 为什么这样做**：发布到 Hub 之前，自动补全 `schema_version` 和 `asset_id`——避免因缺失字段被 Hub 拒绝。

### 72.3 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 资产规范化 | 写入前补全 schema_version + asset_id | **高优先级**: BlueCortexCE 的任何写入前应自动补全元数据 | 高 |
| 预览格式化 | 字符串/数组/对象统一 JSON 格式化 | **中优先级**: BlueCortexCE 的 API 响应格式化应统一处理不同类型 | 中 |

---

## 73. candidates.js — 能力候选提取算法（v1.4 新增）

**文件**: `src/gep/candidates.js` (225 lines)

### 73.1 核心设计思想

Evolver 的 `candidates.js` 实现了**从失败和成功经验中自动发现可复用的能力模式**的算法。它从三个来源提取能力候选：

| 来源 | 触发条件 | 候选类型 |
|------|----------|---------|
| **Transcript 工具调用** | 同一工具调用 ≥3 次 | `CapabilityCandidate` |
| **Signal 模式** | 特定 signal 出现时 | `CapabilityCandidate` |
| **Failed Capsules** | 同类失败 ≥2 次 | `CapabilityCandidate` |

### 73.2 工具调用频率提取（extractToolCalls）

```javascript
// candidates.js:28-40
function extractToolCalls(transcript) {
  const lines = toLines(transcript);
  const calls = [];
  for (const line of lines) {
    // OpenClaw format: [TOOL: Shell]
    const m = line.match(/\[TOOL:\s*([^\]]+)\]/i);
    if (m && m[1]) { calls.push(m[1].trim()); continue; }
    // Cursor transcript format: [Tool call] Shell
    const m2 = line.match(/\[Tool call\]\s+(\S+)/i);
    if (m2 && m2[1]) calls.push(m2[1].trim());
  }
  return calls;
}
```

**Evolver 为什么这样做**：从 session transcript 中提取工具调用模式，识别"重复使用的工具"作为能力候选。频率 ≥3 才触发（避免噪声）。

### 73.3 Five Questions Shape 模板

每个候选都转换为**五问模板**，用于指导后续的 Gene 生成：

```javascript
// candidates.js:48-60
function buildFiveQuestionsShape({ title, signals, evidence }) {
  return {
    title: String(title || '').slice(0, 120),
    input: 'Recent session transcript + memory snippets + user instructions',
    output: 'A safe, auditable evolution patch guided by GEP assets',
    invariants: 'Protocol order, small reversible patches, validation, append-only events',
    params: `Signals: ${Array.isArray(signals) ? signals.join(', ') : ''}`.trim(),
    failure_points: 'Missing signals, over-broad changes, skipped validation, missing knowledge solidification',
    evidence: clip(evidence, 240),
  };
}
```

**五问**：
1. **Input** — 什么输入触发了这个能力？
2. **Output** — 期望的输出是什么？
3. **Invariants** — 必须保持不变的条件是什么？
4. **Params** — 与哪些 signals 相关？
5. **Failure Points** — 常见的失败点是什么？

### 73.4 Signal 驱动的候选生成

```javascript
// candidates.js:75-98
const signalCandidates = [
  // Defensive signals
  { signal: 'log_error', title: 'Repair recurring runtime errors' },
  { signal: 'protocol_drift', title: 'Prevent protocol drift and enforce auditable outputs' },
  { signal: 'windows_shell_incompatible', title: 'Avoid platform-specific shell assumptions (Windows compatibility)' },
  { signal: 'session_logs_missing', title: 'Harden session log detection and fallback behavior' },
  // Opportunity signals (innovation)
  { signal: 'user_feature_request', title: 'Implement user-requested feature' },
  { signal: 'user_improvement_suggestion', title: 'Apply user improvement suggestion' },
  { signal: 'perf_bottleneck', title: 'Resolve performance bottleneck' },
  { signal: 'capability_gap', title: 'Fill capability gap' },
  { signal: 'stable_success_plateau', title: 'Explore new strategies during stability plateau' },
  { signal: 'external_opportunity', title: 'Evaluate external A2A asset for local adoption' },
];
```

**Evolver 为什么这样做**：将 signal 模式直接映射为候选能力——当检测到特定 signal 时，自动生成对应的能力候选，驱动进化循环。

### 73.5 Failed Capsules 分组聚合

```javascript
// candidates.js:103-145
var groups = {};
var problemPriority = [
  'problem:performance',
  'problem:protocol',
  'problem:reliability',
  'problem:stagnation',
  'problem:capability',
];
for (var i = 0; i < failedCapsules.length; i++) {
  var fc = failedCapsules[i];
  if (!fc || fc.outcome && fc.outcome.status === 'success') continue;
  var reason = String(fc.failure_reason || '').trim();
  var failureTags = expandSignals((fc.trigger || []).concat(signalList), reason)
    .filter(function (t) {
      return t.indexOf('problem:') === 0 || t.indexOf('risk:') === 0 ||
             t.indexOf('area:') === 0 || t.indexOf('action:') === 0;
    });
  if (failureTags.length === 0) continue;
  var dominantProblem = null;
  for (var p = 0; p < problemPriority.length; p++) {
    if (failureTags.indexOf(problemPriority[p]) !== -1) {
      dominantProblem = problemPriority[p];
      break;
    }
  }
  // ...
}
```

**Evolver 为什么这样做**：将相似失败模式的 Capsule 聚合分组，识别"反复失败的进化路径"作为学习机会。同一问题类型出现 ≥2 次才生成候选。

### 73.6 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 工具调用频率提取 | transcript 中提取 `[TOOL: xxx]` 模式，≥3 次触发 | **高优先级**: BlueCortexCE 可从 session transcript 中提取重复行为模式 | 高 |
| 五问模板 | 候选转换为 input/output/invariants/params/failure_points | **高优先级**: BlueCortexCE Observation 可增加 structured template | 中 |
| Signal 驱动候选 | signal → capability candidate 自动映射 | **中优先级**: BlueCortexCE 可基于 signal 类型生成 structured extraction | 中 |
| Failed 聚合 | 失败 Capsule 按 problem type 分组，≥2 次触发 | **中优先级**: BlueCortexCE 的 `/api/sessions/{id}/failed` 可做类似聚合 | 低 |
| 确定性哈希 | `stableHash()` 用于去重 ID 生成 | **高优先级**: BlueCortexCE 的 entity ID 生成应使用确定性哈希 | 高 |

---

## 74. candidateEval.js — 候选预演构建与外部资产匹配（v1.4 新增）

**文件**: `src/gep/candidateEval.js` (107 lines)

### 74.1 buildCandidatePreviews 函数

`candidateEval.js` 的核心是 `buildCandidatePreviews` 函数，它：

1. 从当前 session 的 transcript 和 signals 生成新候选
2. 持久化候选到 `assetStore`
3. 读取最近的本地和外部候选
4. 构建供 GEP prompt 使用的预览文本

```javascript
// candidateEval.js:13-25
function buildCandidatePreviews({ signals, recentSessionTranscript }) {
  // Step 1: 提取新候选
  const newCandidates = extractCapabilityCandidates({
    recentSessionTranscript: recentSessionTranscript || '',
    signals,
    recentFailedCapsules: readRecentFailedCapsules(50),
  });
  // Step 2: 持久化
  for (const c of newCandidates) {
    try { appendCandidateJsonl(c); } catch (e) { ... }
  }
  // Step 3: 读取本地候选
  const recentCandidates = readRecentCandidates(20);
  const capabilityCandidatesPreview = renderCandidatesPreview(recentCandidates.slice(-8), 1600);
  // Step 4: 读取外部候选 + 信号匹配
  let externalCandidatesPreview = '(none)';
  // ...
}
```

### 74.2 外部 Gene 与 Capsule 的信号匹配

```javascript
// candidateEval.js:35-55
const matchedExternalGenes = genesOnly
  .map(g => {
    const pats = Array.isArray(g.signals_match) ? g.signals_match : [];
    const hit = pats.reduce((acc, p) => (matchPatternToSignals(p, signals) ? acc + 1 : acc), 0);
    return { gene: g, hit };
  })
  .filter(x => x.hit > 0)
  .sort((a, b) => b.hit - a.hit)
  .slice(0, 3)
  .map(x => x.gene);

const matchedExternalCapsules = capsulesOnly
  .map(c => {
    const triggers = Array.isArray(c.trigger) ? c.trigger : [];
    const score = triggers.reduce((acc, t) => (matchPatternToSignals(t, signals) ? acc + 1 : acc), 0);
    return { capsule: c, score };
  })
  .filter(x => x.score > 0)
  .sort((a, b) => b.score - a.score)
  .slice(0, 3)
  .map(x => x.capsule);
```

**Evolver 为什么这样做**：
- 从 Hub 同步的外部 Gene/Capsule，按当前 signals 匹配度排序
- 取 top-3 作为预演内容，让 GEP prompt 知道外部有什么可用资产
- 这是**联邦知识发现**的关键环节

### 74.3 预览格式化输出

```javascript
// candidateEval.js:60-90
externalCandidatesPreview = `\`\`\`json\n${JSON.stringify(
  [
    ...matchedExternalGenes.map(g => ({
      type: g.type,
      id: g.id,
      category: g.category || null,
      signals_match: g.signals_match || [],
      a2a: g.a2a || null,
    })),
    ...matchedExternalCapsules.map(c => ({
      type: c.type,
      id: c.id,
      trigger: c.trigger,
      gene: c.gene,
      summary: c.summary,
      confidence: c.confidence,
      blast_radius: c.blast_radius || null,
      outcome: c.outcome || null,
      success_streak: c.success_streak || null,
      a2a: c.a2a || null,
    })),
  ],
  null, 2
)}\n\`\`\``;
```

**Evolver 为什么这样做**：输出 JSON 格式便于 LLM 解析，包含 type/id/trigger/summary/confidence 等关键字段。

### 74.4 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 外部资产信号匹配 | genes/capsules 按 signals 匹配度排序，取 top-3 | **高优先级**: BlueCortexCE 的 `/api/search` 可增加"信号匹配度"排序 | 高 |
| 联邦知识发现 | Hub 同步 + 本地匹配 | **中优先级**: BlueCortexCE 可实现多实例联邦搜索 | 中 |
| 预览格式化 JSON | JSON 输出便于 LLM 解析 | **中优先级**: BlueCortexCE 的 context 输出可增加结构化 JSON 块 | 中 |
| 候选持久化 | appendCandidateJsonl 异步写入 | **高优先级**: BlueCortexCE 的候选observation应有异步写入机制 | 高 |

---

## 75. skillPublisher.js — Gene 到 SKILL.md 格式转换与 Hub 发布（v1.4 新增）

**文件**: `src/gep/skillPublisher.js` (307 lines)

### 75.1 核心设计思想

`skillPublisher.js` 实现将 **Gene 资产转换为可发布的 SKILL.md 格式**并发布到 Hub 的完整流程。这是 Evolver 知识变现的核心环节：

```
Gene (内部资产) → SKILL.md (Hub 发布格式) → Hub (联邦知识市场)
```

### 75.2 Gene → SKILL.md 格式转换（geneToSkillMd）

```javascript
// skillPublisher.js:67-135
function geneToSkillMd(gene) {
  var name = sanitizeSkillName(gene.id) || deriveFallbackName(gene);
  var displayName = toTitleCase(name);
  var lines = [
    '---',
    'name: ' + displayName,
    'description: ' + desc,
    '---',
    '',
    '# ' + displayName,
    '',
    '## When to Use',
    '- When your project encounters: ' + gene.signals_match.slice(0, 4).map(...).join(', '),
    '',
    '## Trigger Signals',
    gene.signals_match.forEach(s => lines.push('- `' + s + '`')),
    '',
    '## Preconditions',
    gene.preconditions.forEach(p => lines.push('- ' + p)),
    '',
    '## Strategy',
    gene.strategy.map((step, i) => (i+1) + '. **' + extractStepVerb(step) + '** -- ' + stripLeadingVerb(step)),
    '',
    '## Constraints',
    // constraints.max_files, constraints.forbidden_paths
    '',
    '## Validation',
    gene.validation.map(cmd => '```bash\n' + cmd + '\n```'),
    '',
    '## Metadata',
    '- Category: `' + gene.category + '`',
    '- Schema version: `' + gene.schema_version + '`',
    '- Distilled from: ' + gene._distilled_meta.source_capsule_count + ' successful capsules',
  ];
  return lines.join('\n');
}
```

**SKILL.md 结构**：

| Section | 内容 |
|---------|------|
| Frontmatter | name, description (YAML) |
| When to Use | 触发条件（signals） |
| Trigger Signals | 信号列表 |
| Preconditions | 前置条件 |
| Strategy | 步骤列表（动词 bold 化） |
| Constraints | 约束（文件数限制、禁止路径） |
| Validation | 验证命令 |
| Metadata | 类别、版本、来源 |

### 75.3 技能名称清洗（sanitizeSkillName）

```javascript
// skillPublisher.js:13-28
function sanitizeSkillName(rawName) {
  var name = rawName.replace(/[\r\n]+/g, '-')
                     .replace(/^gene_distilled_/, '')
                     .replace(/^gene_/, '')
                     .replace(/_/g, '-');
  // Strip ALL embedded timestamps (10+ digit sequences)
  name = name.replace(/-?\d{10,}-?/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  // 过滤工具名和纯数字
  if (/^\d{8,}/.test(name) || /^(cursor|vscode|vim|emacs|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) {
    return null;
  }
  if (name.replace(/[-]/g, '').length < 6) return null;
  return name;
}
```

**Evolver 为什么这样做**：
- 去除 `gene_distilled_` 和 `gene_` 前缀
- 将下划线转为连字符（kebab-case）
- 去除嵌入的时间戳
- 过滤工具名和纯数字

### 75.4 动词提取（extractStepVerb）

```javascript
// skillPublisher.js:157-168
function extractStepVerb(step) {
  // Only match a capitalized verb at the very start
  var match = step.match(/^([A-Z][a-z]+)/);
  return match ? match[1] : '';
}

function stripLeadingVerb(step) {
  var verb = extractStepVerb(step);
  if (verb && step.startsWith(verb)) {
    var rest = step.slice(verb.length).replace(/^[\s:.\-]+/, '');
    return rest || step;
  }
  return step;
}
```

**Evolver 为什么这样做**：策略步骤格式为 "Verb -- rest"，展示时动词 bold 化，让格式更易读。

### 75.5 Hub 发布流程（publishSkillToHub）

```javascript
// skillPublisher.js:180-230
function publishSkillToHub(gene, opts) {
  var hubUrl = getHubUrl();
  if (!hubUrl) return Promise.resolve({ ok: false, error: 'no_hub_url' });

  var content = geneToSkillMd(geneCopy);
  var skillId = 'skill_' + derivedName.replace(/_?\d{10,}_?/g, '_').replace(/_+/g, '_');
  var body = {
    sender_id: nodeId,
    skill_id: skillId,
    content: content,
    category: opts.category || geneCopy.category || null,
    tags: tags,
  };

  var endpoint = hubUrl + '/a2a/skill/store/publish';
  return fetch(endpoint, {
    method: 'POST',
    headers: buildHubHeaders(),
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  })
    .then(function (res) {
      if (res.status === 201 || res.status === 200) {
        return { ok: true, result: result.data };
      }
      if (res.status === 409) {
        return updateSkillOnHub(nodeId, skillId, content, opts, gene); // 已存在则更新
      }
      return { ok: false, error: result.data?.error || 'publish_failed' };
    });
}
```

**Evolver 为什么这样做**：
- `409 Conflict` 时自动触发 `updateSkillOnHub`（版本迭代）
- 15s 超时防止 Hub 无响应阻塞
- `AbortSignal.timeout()` 现代 API

### 75.6 标签清洗（sanitizeSignalsMatch）

```javascript
// skillPublisher.js:196-203
var tags = opts.tags || geneCopy.signals_match || [];
tags = tags.filter(function (t) {
  var s = String(t || '').trim();
  return s.length >= 3 && !/^\d+$/.test(s) && !/\d{10,}/.test(s);
});
```

**Evolver 为什么这样做**：过滤纯数字和时间戳标签，防止 Hub 拒绝或排序异常。

### 75.7 BlueCortexCE 借鉴点

| 发现 | Evolver 做法 | 翻译：旁路型如何借鉴 | 优先级 |
|------|-------------|---------------------|--------|
| 技能格式化 | Gene → SKILL.md (frontmatter + sections) | **高优先级**: BlueCortexCE 可将 Observation 导出为 SKILL.md 格式 | 高 |
| 技能名称清洗 | kebab-case + 去除时间戳 | **高优先级**: BlueCortexCE 的 asset 名称应有规范化逻辑 | 高 |
| Hub 发布 | POST → 409 → PUT 自动版本更新 | **中优先级**: BlueCortexCE 的资产发布可参考此幂等模式 | 中 |
| Verb bold 化 | 策略步骤 "Verify -- installation" → "**Verify** -- installation" | **中优先级**: BlueCortexCE 的 structured output 可类似格式化 | 中 |
| 来源追溯 | `_distilled_meta.source_capsule_count` 标注成功 Capsule 数量 | **高优先级**: BlueCortexCE 的 Observation 应记录来源 session | 高 |

---

