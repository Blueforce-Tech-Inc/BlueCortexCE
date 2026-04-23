# Evolver Prompt 工程深度剖析：Schema、质量门禁与截断策略

> **数据来源**：`src/gep/prompt.js`（`buildGepPrompt`、`SCHEMA_DEFINITIONS`、`truncateContext`）、`src/gep/solidify.js`（验证逻辑）。
> **最后更新**：2026-04-23
> **前置阅读**：[25 高级模式 §8](./25-advanced-patterns-prm-epigenetic-antipattern.md)（Prompt 工程架构总结 — 多层注入总览）、[24 Gene/Strategy 层](./24-gene-strategy-layer.md)。

---

## 1. 架构定位

[25 §8](./25-advanced-patterns-prm-epigenetic-antipattern.md) 已覆盖**多层上下文注入的总览**（12 层表格 + 截断策略 + CE 翻译）。

本文档补充以下**深度实现细节**：
- **严格 Schema 定义**（5 个强制 JSON 对象的完整结构）
- **敏感数据参数化**（6 条硬性规则）
- **技能创建质量门禁**（8 项检查）
- **截断策略的精确实现**（动态上限 + 硬编码兜底）
- **常见失败模式清单**（8 种协议违规）

---

## 2. 严格 Schema 定义 (`SCHEMA_DEFINITIONS`)

### 2.1 五对象模型

Evolver 要求 LLM **按固定顺序**输出 5 个独立 JSON 对象（非数组包裹）：

| 序号 | 对象类型 | 必须为第一项 | 核心字段 |
|------|---------|------------|---------|
| 0 | **Mutation** | ✅ | `id`, `category`, `trigger_signals`, `target`, `risk_level`, `rationale` |
| 1 | **PersonalityState** | | `rigor`, `creativity`, `verbosity`, `risk_tolerance`, `obedience` (0.0–1.0) |
| 2 | **EvolutionEvent** | | `schema_version`, `parent`, `intent`, `signals`, `genes_used`, `blast_radius`, `outcome` |
| 3 | **Gene** | | `id`（描述性命名）, `summary`, `category`, `signals_match`, `strategy`, `constraints`, `validation` |
| 4 | **Capsule** | | 仅成功时输出；`trigger`, `gene`, `summary`, `confidence`, `blast_radius` |

### 2.2 输出格式约束

```
❌ 错误：包裹在 ```json ... ``` 中
❌ 错误：包裹在单个数组 [...]
❌ 错误：前导/后置文本（"Here is the plan..."）
✅ 正确：5 个独立 JSON 对象，用换行分隔，无任何 markdown 包裹
```

### 2.3 Gene ID 命名规则

```
✅ gene_retry_on_timeout      （描述性）
✅ gene_log_rotation_weekly   （描述性）
❌ gene_1713849600000          （时间戳）
❌ gene_cursor_12345           （工具名+随机数）
❌ gene_a1b2c3                 （哈希）
```

**摘要要求**：`summary` 必须是清晰的人类可读句子，描述 Gene 做什么。

### 2.4 BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译 |
|-------------|---------|
| 严格输出 Schema | CE `generateContext` 输出已有 JSON Schema 验证，可增加字段约束 |
| Mutation 必须第一 | CE 不需要（旁路系统不做自主行动） |
| Gene 描述性 ID | CE Observation ID 命名已有 UUID 规范 |
| 5 对象分离输出 | CE 可将上下文产出分为：观察摘要 + 信号 + 建议 + 风险评估 |

---

## 3. 敏感数据参数化（6 条硬性规则）

Evolver 在技能创建时**强制执行**敏感数据替换，违反 = `FAILED`：

| 规则 | 模式 | 替换为 |
|------|------|--------|
| 1. API Keys/Tokens | `sk-xxx`, `token_xxx` | `process.env.<SERVICE>_API_KEY` |
| 2. 本地路径 | `/home/<user>/`, `/Users/<user>/` | `path.join(process.env.HOME, ...)` |
| 3. 数据库连接串 | `mongodb://`, `postgres://` | `process.env.DATABASE_URL` |
| 4. 内部 IP/主机名 | `192.168.x.x`, `localhost:5432` | `process.env.<SERVICE>_HOST` |
| 5. 用户名 | 硬编码在路径/配置/注释中 | 泛型引用 |
| 6. 密码 | 硬编码密码 | `process.env.PASSWORD` |

### BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译 |
|-------------|---------|
| 6 条硬性规则 | CE 可在 `sanitize.js` 中实现类似的模式匹配 |
| 输出前自动替换 | CE `ObservationEntity` 写入前的脱敏 |
| 检测 → FAILED | CE 可将敏感数据检测失败标记为观察质量降级 |

**CE 实施建议**：
```java
// AgentService.saveObservation() 中增加
if (containsSensitiveData(observation.getContent())) {
  observation.setExtractedData("sanitized", true);
  observation.setContent(sanitize(observation.getContent()));
}
```

---

## 4. 技能创建质量门禁（8 项检查）

Evolver 在 `innovate` intent 创建新技能时，必须通过 8 项检查：

### 4.1 结构检查

```
skills/<name>/
├── index.js          ← 必须存在且可导出
├── SKILL.md          ← 必须存在
├── package.json      ← 必须存在（含 name + version）
├── scripts/          ← 可选
├── references/       ← 可选
└── assets/           ← 可选
```

- 空目录 = `FAILED`
- 缺少 `index.js` = `FAILED`
- 不创建不必要的文件（README.md, CHANGELOG.md 等）

### 4.2 命名规则

```
✅ http-retry-with-backoff   （2-6 个描述性单词，连字符分隔）
✅ log-file-rotation
✅ config-validator
❌ cursor-1773331925711      （工具名+时间戳）
❌ new-skill                 （无描述性）
❌ test-skill                （无描述性）
```

### 4.3 SKILL.md 前置元数据

```yaml
---
name: <skill-name>           # 必须符合命名规则
description: <完整句子，描述做什么和何时使用>  # 最少 20 字符
---
```

- `description` 是触发机制——决定了何时激活技能
- 泛型描述 = `FAILED`

### 4.4 简洁性

- SKILL.md 正文不超过 500 行
- 仅包含 Agent 不知道的信息
- 详细参考资料放入 `references/` 文件

### 4.5 导出验证

```bash
# 必须能成功导入并列出导出
node -e "const s = require('./skills/<name>'); console.log(Object.keys(s))"
```

### 4.6 敏感数据参数化

见 §3 的 6 条规则。

### 4.7 测试验证

```bash
# 创建后必须实际运行
node -e "require('./skills/<name>').main ? require('./skills/<name>').main() : console.log('ok')"
```

### 4.8 原子创建

- 单个周期内创建所有文件
- 不在一个周期创建目录、在下一个周期填充
- 失败周期的空目录自动清理

### BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| 结构检查 | CE Maven archetype 标准化模块结构 |
| 命名规则 | CE Java 包命名规范已有（`com.ablueforce.cortexce`） |
| 前置元数据 | CE 可在新模块 README.md 中强制包含项目描述 |
| 导出验证 | CE `mvn compile` + 单元测试 |
| 原子创建 | CE feature branch + PR 模式 |

---

## 5. 截断策略的精确实现

### 5.1 总体上限

```javascript
const maxChars = process.env.GEP_PROMPT_MAX_CHARS || 50000;
```

### 5.2 分层截断（按优先级）

| 层 | 截断策略 | 上限 |
|----|---------|------|
| **Execution Context** | 硬上限 | 20,000 字符 |
| **Capability Candidates** | 动态（有 Gene = 500，无 Gene = 2000） | 500/2000 |
| **Signals** | 数量上限 + 单条截断 | 50 条 × 200 字符/条 |
| **总体** | 优先截断 Execution Context | `GEP_PROMPT_MAX_CHARS` |

### 5.3 智能截断函数

```javascript
function truncateContext(text, maxLength = 20000) {
  if (!text || text.length <= maxLength) return text || '';
  return text.slice(0, maxLength) + '\n...[TRUNCATED_EXECUTION_CONTEXT]...';
}
```

**关键设计**：
- 不在中间截断（可能破坏 JSON 结构）
- 添加明确的截断标记，让 LLM 知道数据不完整
- Execution Context 被优先截断（因为它是最大且最可变的）

### 5.4 超限时的降级逻辑

```javascript
if (basePrompt.length <= maxChars) return basePrompt;

// 找到 Execution Context 的位置
const executionContextIndex = basePrompt.indexOf("Context [Execution]:");
if (executionContextIndex > -1) {
  // 只截断 Execution Context，保留其他所有层
  const prefix = basePrompt.slice(0, executionContextIndex + 20);
  const currentExecution = basePrompt.slice(executionContextIndex + 20);
  const EXEC_CONTEXT_CAP = 20000;
  const allowedLength = Math.min(EXEC_CONTEXT_CAP, Math.max(0, maxChars - prefix.length - 100));
  return prefix + "\n" + currentExecution.slice(0, allowedLength) + "\n...[TRUNCATED]...";
}

// 最终兜底：直接截断整个提示词
return basePrompt.slice(0, maxChars) + "\n...[TRUNCATED]...";
```

### BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译 |
|-------------|---------|
| 分层截断 | CE `generateContext` 已有 token 预算，可增加分层策略 |
| Execution Context 优先截断 | CE 可优先截断最早的观察记录 |
| 动态上限（有/无 Gene） | CE 可根据是否有活跃任务调整上下文量 |
| 截断标记 | CE 可在截断处添加 `[TRUNCATED]` 标记 |

---

## 6. 常见失败模式清单

Evolver 明确列出 8 种常见协议违规（提示词中直接告知 LLM）：

| # | 失败模式 | 原因 |
|---|---------|------|
| 1 | Blast radius exceeded | 修改文件数超过限制 |
| 2 | Omitted Mutation object | 忘记输出第一个 JSON 对象 |
| 3 | Merged objects into one JSON | 将 5 个对象合并为一个 |
| 4 | Hallucinated "type": "Logic" | LLM 发明了不存在的类型 |
| 5 | "id": "mut_undefined" | Mutation ID 未正确生成 |
| 6 | Missing "trigger_signals" | 触发信号列表为空 |
| 7 | Unrunnable validation steps | 验证命令无法执行 |
| 8 | Markdown code blocks wrapping JSON | 用 ```json 包裹输出 |

### BlueCortexCE 借鉴

| 概念 | CE 翻译 |
|------|---------|
| 失败模式清单 | CE 可在 `generateContext` 提示词中加入"不要做什么"的指导 |
| 协议验证 | CE JSON Schema 验证已有，可增加业务规则验证 |
| 错误模式反馈 | CE 可将验证失败的原因反馈给 LLM 以改进 |

---

## 7. 与现有文档的关系

| 本文档 | 与现有文档的区别 |
|--------|----------------|
| [25 §8 Prompt 工程架构](./25-advanced-patterns-prm-epigenetic-antipattern.md) | 25 §8 是**总览**（12 层表格 + 截断策略概述），本文档是**深度实现**（Schema 定义、质量门禁、敏感数据规则、精确截断算法） |
| [24 Gene/Strategy 层](./24-gene-strategy-layer.md) | 24 聚焦 Gene 选择和 Mutation，本文档聚焦**提示词如何组织这些信息** |
| [26 运行时编排](./26-runtime-orchestration-adaptive-policy-candidates.md) | 26 聚焦自适应策略（输入），本文档聚焦提示词构建（输出） |
