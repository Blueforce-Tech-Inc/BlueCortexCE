# `106` `questionComposer.js` 深度分析与 BlueCortexCE 上下文生成 Pipeline 借鉴

**Doc**: 106 | **Status**: Draft | **Author**: PM Agent | **Date**: 2026-05-05
**Source**: `/Users/yangjiefeng/Documents/EvoMap/evolver/src/atp/questionComposer.js`
**前身**: doc 83 (ATP merchant-side) 提及但未深度分析

---

## 1. 模块定位与问题背景

### 1.1 旧版 autoBuyer 的问题

在 `questionComposer.js` 引入之前，`autoBuyer.js` 直接拼接信号生成买家问题：

```javascript
// 旧版 autoBuyer 的问题方式
const text = `Capability gap detected by evolver: ${signals.join(',')}`;
```

这种拼接方式有三个严重问题：

| 问题 | 影响 |
|------|------|
| 泄露 Evolver 内部术语（signals、cycle、mutation） | 商家端可见内部机制 |
| 语义不自然 | 商家难以理解真实需求 |
| 无上下文适配 | 模板固定，无法根据 capability 选择最优提问策略 |

### 1.2 questionComposer 的设计目标

`questionComposer.js` 解决的核心问题：**如何将结构化的 capability/signal 输入转换为自然、防御性（不泄露内部术语）的买家问题**。

---

## 2. 源码深度分析

### 2.1 核心数据：TEMPLATES Map

```javascript
const TEMPLATES = {
  code_evolution: [
    'I want to improve code quality on a small module. Please suggest one concrete, minimal patch I can apply, including the exact files, the change, and why it helps.',
    'I am iterating on a codebase and would like one high-leverage refactor suggestion. Be specific about the file, the current issue, and the proposed change.',
  ],
  performance: [...],
  debugging: [...],
  testing: [...],
  documentation: [...],
  refactoring: [...],
  security: [...],
  data_analysis: [...],
  architecture: [...],
  deployment: [...],
  general: [...],  // 兜底模板
};
```

**关键设计特点**：

- **每个 capability 2 个模板**（可选）：支持多样性，避免相同问题重复出现
- **模板刻意保持 <240 字符**：不浪费买家预算和商家时间
- **never 泄露 Evolver 内部术语**：无 signals/cycle/mutation 等词汇
- **自然语言买家口吻**：站在真实买家角度提问

### 2.2 确定性选择算法

```javascript
function _pickTemplate(key, hashSeed) {
  const list = TEMPLATES[key] || TEMPLATES.general;
  if (!list || list.length === 0) return null;
  // Deterministic pick from a seed so the same signals yield the same
  // question across runs (plays nicely with autoBuyer's dedup hash).
  const n = Math.abs(Number(hashSeed) || 0) % list.length;
  return list[n];
}

function _hashFor(parts) {
  const s = Array.isArray(parts) ? parts.join('|') : String(parts || '');
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) | 0;
  }
  return h;
}
```

**确定性选择的价值**：
- 相同输入 → 相同输出（幂等性）
- 与 autoBuyer 的 dedup hash 配合良好（相同 capability → 相同问题 → dedup 生效）
- 无随机性，服务端和客户端可独立验证

### 2.3 完整 compose 函数

```javascript
function compose(opts) {
  const capabilities = Array.isArray(opts && opts.capabilities) ? opts.capabilities : [];
  const signals = Array.isArray(opts && opts.signals) ? opts.signals : [];
  const maxLen = Number(opts && opts.maxLen) || DEFAULT_MAX_LEN;

  // 归一化 capability → template key
  const keys = capabilities.map(_normalize).filter(Boolean);
  const primary = keys.find(function (k) { return TEMPLATES[k]; }) || keys[0] || 'general';
  const tmplKey = TEMPLATES[primary] ? primary : 'general';

  // 基于 capability+signals 的确定性 seed
  const seed = _hashFor(keys.concat(signals.slice(0, 4)));
  const tmpl = _pickTemplate(tmplKey, seed);

  if (tmpl) return _clip(tmpl, maxLen);

  // Generic fallback when no template matches
  const capsText = capabilities.length ? capabilities.slice(0, 3).join(', ') : 'a common task';
  const fb = (opts && opts.fallback && String(opts.fallback).trim())
    || 'I would like help with ' + capsText + '. Please provide one concrete, actionable answer.';
  return _clip(fb, maxLen);
}
```

### 2.4 _normalize 规范化函数

```javascript
function _normalize(s) {
  return String(s || '').toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
}
```

- 转小写 → 替换非字母数字下划线为下划线 → 裁剪首尾下划线
- `code_evolution` ← `Code Evolution` / `codeEvolution` / `Code-Evolution`
- 保证 capability string 到 template key 的可靠映射

---

## 3. 设计模式总结

### 3.1 模板模式（Template Pattern）

将**变化的提问内容**与**选择逻辑**分离：

```
Input: capability + signals
        ↓
  normalize() → template key
        ↓
  hash-seeded pick → concrete template
        ↓
  clip() → final question
```

### 3.2 策略模式（Strategy Pattern）

每个 capability key 对应一个独立的模板数组，选择逻辑统一（hash 种子）。

### 3.3 防御性编程（Defensive Design）

- **Never leak internals**：模板内容严格检查，无 evolver 内部词汇
- **Never return empty**：所有分支保证非空字符串
- **Graceful degradation**：未知 capability → `general` 兜底

### 3.4 确定性（Determinism）

- `_hashFor` 保证：相同输入 → 相同 seed → 相同模板 → 相同问题
- 关键属性：**幂等性 + 可重现性**
- 对于自动 buyer 的 dedup 机制至关重要

---

## 4. BlueCortexCE 借鉴分析

### 4.1 类比：Capability → Observation Type / Signal

| EvoMap questionComposer | BlueCortexCE 类比 |
|------------------------|-------------------|
| capability (`code_evolution`) | `ObservationEntity.type` / signal tag |
| signals (内部标签) | `ObservationEntity.signalTags` |
| 输出：买家的自然语言问题 | 输出：给 LLM 的结构化上下文 |

### 4.2 借鉴1：StructuredContext 的防御性模板

BlueCortexCE 的 `StructuredContext` 注入 prompt 时，可以借鉴 questionComposer 的思想：

```java
// CE 现状：Observation → prompt string 直接拼接
String context = observations.stream()
    .map(o -> "- " + o.getContent())
    .collect(Collectors.joining("\n"));

// 借鉴 questionComposer：先归一化，再选择模板，最后填充
Map<String, List<String>> OBSERVATION_TEMPLATES = Map.of(
    "error", List.of(
        "The system encountered an error: {content}. This suggests a {rootCause} issue.",
        "A failure occurred: {content}. Recommended action: {resolution}."
    ),
    "decision", List.of(
        "Decision made: {content}. Rationale: {reasoning}."
    ),
    "preference", List.of(
        "User preference detected: {content}. Confidence: {confidence}."
    )
);
```

**防御性原则**：
- 不同类型的观察使用不同语义模板
- 模板确保上下文自然且信息密度高
- 不泄露内部 entity 字段名（如 `observationId`、`contentHash`）

### 4.3 借鉴2：确定性上下文注入

`questionComposer` 的 hash-seeded 模板选择对 BlueCortexCE 的启发：

```java
// 当前问题：相同的 session + query，每次 generateContext 可能选择不同的 observation
// （SearchService 返回顺序不稳定）

// 解决思路：确定性选择
public static int deterministicPick(int seed, int size) {
    return Math.abs(seed) % size;
}

// 对于给定 session + query：
int seed = Objects.hash(sessionId, query.hashCode());
List<ObservationEntity> sorted = observations.stream()
    .sorted(Comparator.comparingInt(o -> 
        deterministicPick(seed + o.getId().hashCode(), Integer.MAX_VALUE)))
    .toList();
```

### 4.4 借鉴3：信号 → 结构化上下文模板映射

questionComposer 的 capability → TEMPLATES 映射对 CE 的 Structured Extraction 有直接参考价值：

```yaml
# questionComposer-style template registry for CE context injection
context_templates:
  code_review:
    priority: 10
    templates:
      - "Code review finding: {content}. Impact: {severity}."
      - "Review note: {content}. Confidence: {confidence}."
  
  error_pattern:
    priority: 20  # errors always shown
    templates:
      - "Known error pattern: {content}. This has occurred {count} times."
      - "Error: {content}. Normalized signature: {errorSigNorm}."
  
  preference:
    priority: 5
    templates:
      - "User preference: {content} (confidence: {confidence})."
```

### 4.5 借鉴4：maxLen 截断保护

`questionComposer` 的 `_clip` 函数保护商家预算。CE 的 `generateContext` 同样需要 token 预算保护：

```java
private String clip(String text, int maxChars) {
    if (text == null || text.length() <= maxChars) return text;
    // 在单词边界截断，避免破坏完整性
    int cutoff = text.lastIndexOf(' ', maxChars - 3);
    return text.substring(0, cutoff > 0 ? cutoff : maxChars - 3) + "...";
}
```

这与 doc 104 的 Token Budget 分析（`SEMANTIC_MAX_CHARS`）形成互补。

---

## 5. 具体行动项

| 优先级 | 行动项 | 对应文档 |
|--------|--------|---------|
| P2 | 在 `ContextService` 中引入基于 observation type 的模板化上下文输出 | doc 14 |
| P3 | 探索 `deterministicPick` 解决 SearchService 返回顺序不确定性 | doc 10 §3 |
| P2 | 参考 `_clip` 实现 `generateContext` 的字符级保护 | doc 104 |
| P3 | 设计 `context_templates.yaml` 配置化模板注册表 | doc 10 |

---

## 6. 与 doc 83 的关系

Doc 83 分析了 ATP merchant 端 pickup、autoBuyer、questionComposer 的整体协作流程。本 doc 专注 **questionComposer 源码级深度**（模板设计 / 确定性算法 / 防御性原则），以及 **BlueCortexCE 的 context pipeline 借鉴路径**。

---

## 7. Changelog

- 2026-05-05: 初始创建。分析 `questionComposer.js` 133 行源码，提炼 4 大设计模式（模板/策略/防御性/确定性），给出 CE 上下文生成 Pipeline 的 4 个具体借鉴方向。
