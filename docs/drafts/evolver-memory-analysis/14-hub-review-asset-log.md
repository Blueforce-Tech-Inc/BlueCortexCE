# 14 — Hub 使用反馈系统与资产可观测性

## 14.1 整体定位

本章补充 EvoMap Hub 生态中的两个"闭环机制"：

1. **Hub Review**：`hubReview.js`——当节点复用 Hub 资产并完成 solidify 后，向 Hub 提交使用评价
2. **Asset Call Log**：`assetCallLog.js`——Append-only 审计日志，记录所有 Hub 资产交互

这两个模块共同构成 Hub 生态的**反馈闭环**（Marketplace Feedback Loop），使整个网络能够基于真实使用经验进行信任传播。

---

## 14.2 Hub Review：使用后评价提交

### 14.2.1 动机

Hub 上的 Gene/Capsule 资产依赖节点的**使用评价**来建立信任。当一个节点从 Hub 获取了某个 Gene 并成功将其应用于自己的代码库，这个节点的亲身经验对其他节点极具参考价值。

**关键设计**：Review 是**被动触发**的——只有在节点真正使用了 Hub 资产且 solidify 成功后才会提交。不做无用评价。

### 14.2.2 触发条件

```javascript
// submitHubReview 的前置过滤
if (sourceType !== 'reused' && sourceType !== 'reference') {
  return { submitted: false, reason: 'not_hub_sourced' };
}
// source_type == 'reused'：节点直接使用 Hub 资产
// source_type == 'reference'：Hub 资产作为参考注入 prompt
```

**触发时机**：`solidify()` 成功后，由外部调用 `submitHubReview()`，传入 outcome、gene、signals、blast 等上下文。

### 14.2.3 评分推导规则

```javascript
function _deriveRating(outcome, constraintCheck) {
  if (outcome && outcome.status === 'success') {
    // 成功时：按 score 分级
    // score >= 0.85 → 5 星（优秀）
    // 否则 → 4 星（良好）
    return score >= 0.85 ? 5 : 4;
  }
  // 失败时：按约束违反情况分级
  // 有 constraint violation → 1 星（很差）
  // 无 → 2 星（较差）
  return hasConstraintViolation ? 1 : 2;
}
```

**设计意图**：评分直接关联 outcome 的 status 和 score，使得 Hub 上的资产评分能够真实反映其有效性。5 星评价对应 score ≥ 0.85，这是一个相当高的门槛。

### 14.2.4 Review 去重机制

```javascript
// 审查历史存储在 hub_review_history.json
// 每个 assetId 只评价一次（防止重复刷分）
function _alreadyReviewed(assetId) {
  const history = _loadReviewHistory();
  return !!history[assetId];
}
```

- 存储路径：`{evolution_dir}/hub_review_history.json`
- 最多保留 500 个历史条目（超出时按时间淘汰最旧记录）
- 去重基于 assetId，同一资产多次使用只产生一次 Review

### 14.2.5 Review 内容构建

```javascript
function _buildReviewContent({ outcome, gene, signals, blast, sourceType }) {
  // 生成最多 2000 字符的 Review 正文，包含：
  // - Outcome status + score
  // - Reuse mode (reused / reference)
  // - Gene id + category
  // - Signal list (最多 6 个)
  // - Blast radius (files + lines)
  // - 简短成功/失败说明
}
```

### 14.2.6 非阻塞设计

```javascript
// Review 提交完全非阻塞：
// - 失败不影响 solidify 结果
// - 超时 10 秒后自动 abort
// - fetch 错误静默吞掉（console.log）
// - 结果记录到 assetCallLog（用于调试）
```

**对 Claude-Mem 的借鉴**：类似机制可用于"Session 成功总结自动发布到社区模板市场"——评价提交不影响主流程，但为整个生态贡献数据。

---

## 14.3 Asset Call Log：Hub 交互可观测性

### 14.3.1 设计目的

`assetCallLog.js` 为所有 Hub 资产交互提供**结构化审计日志**。与 memoryGraph 的通用事件不同，Asset Call Log 专门记录与 Hub 的每一次交互。

**日志文件**：`{evolution_dir}/asset_call_log.jsonl`（Append-only JSONL）

### 14.3.2 记录的行为类型

| action | 含义 |
|--------|------|
| `hub_search_hit` | Hub 搜索命中 |
| `hub_search_miss` | Hub 搜索未命中 |
| `hub_review_submitted` | 使用后评价提交成功 |
| `hub_review_rejected` | Hub 拒绝评价（e.g., 已存在） |
| `hub_review_failed` | 评价提交失败（非阻塞） |
| `asset_reuse` | 资产被复用 |
| `asset_reference` | 资产被引用 |
| `asset_publish` | 资产发布到 Hub |
| `asset_publish_skip` | 资产发布被跳过 |

### 14.3.3 日志记录结构

```javascript
{
  timestamp: "2026-05-07T12:00:00.000Z",
  run_id: "abc123",           // 可用于关联同一轮 evolution 的所有事件
  action: "hub_search_hit",
  asset_id: "gene_xxx",
  asset_type: "Gene",
  source_node_id: "node_xxx", // 资产来源节点
  chain_id: "chain_xxx",      // 资产链 ID
  score: 0.85,                // 匹配得分
  mode: "reference",          // 复用模式
  signals: ["log_error", "TypeError..."],
  via: "search_then_fetch",    // 命中路径
  extra: { ... }              // 额外上下文
}
```

### 14.3.4 读取与聚合

```javascript
// 支持多种过滤和聚合
readCallLog({ run_id, action, last, since })
summarizeCallLog(opts)
// → { total_entries, unique_assets, unique_runs, by_action: {...} }
```

这使得：
- 调试时可以按 `run_id` 追溯某次 evolution 的完整 Hub 交互链
- 按 `action` 聚合统计 Hub 命中率
- 按 `since` 过滤近期趋势

### 14.3.5 对 Claude-Mem 的借鉴

Claude-Mem 当前的 Session 内记录是 Observation，但**跨 Session 的 Hub 风格交互记录**不存在。如果未来 Claude-Mem 引入"记忆市场"或"上下文模板分享"机制，类似 `assetCallLog` 的审计层将是必需品。

---

## 14.4 Skill Publisher：Gene → Hub Skill 发布管道

### 14.4.1 定位

`solidify.js` 成功后将高置信度 Gene 发布到 Hub Skill Store，使其他节点可以搜索并复用。`skillPublisher.js` 是这个发布管道的实现。

**核心入口**：`publishSkillToHub(gene, opts)` → HTTP PUT/POST 到 Hub

### 14.4.2 SKILL.md 生成（geneToSkillMd）

将 Gene 对象转换为**市场级 SKILL.md** 格式：

```javascript
function geneToSkillMd(gene) {
  // 1. 名称规范化（sanitizeSkillName）
  //    "gene_distilled_retry_with_backoff_v3_1746543210"
  //    → "retry-with-backoff"（去除时间戳、工具名等）
  // 2. 生成 YAML frontmatter + Markdown 正文
  //    - When to Use（触发信号）
  //    - Trigger Signals（原始信号列表）
  //    - Preconditions（前置条件）
  //    - Strategy（步骤，带动词提取 bold）
  //    - Constraints（限制）
  //    - Validation（验证命令）
  //    - Metadata（分类、schema版本、蒸馏来源）
}
```

**名称清理关键规则**：

```javascript
// 去除所有 10 位以上数字序列（时间戳）
name.replace(/-?\d{10,}-?/g, '-')

// 拒绝纯数字开头、工具名（cursor/vscode/claude等）
if (/^\d{8,}/.test(name)) return null;
if (/^(cursor|vscode|windsurf|copilot|cline|codex)[-]?\d*$/i.test(name)) return null;

// 太短的名称（<6个有效字符）也拒绝
if (name.replace(/[-]/g, '').length < 6) return null;
```

### 14.4.3 发布流程

```
publishSkillToHub(gene)
       │
       ▼
POST /a2a/skill/store/publish
  body: { sender_id, skill_id, content, category, tags }
       │
       ├── [201/200] → ok
       ├── [409 Conflict] → 自动调用 updateSkillOnHub (PUT)
       └── [其他错误] → { ok: false, error }
```

**幂等性**：409 时自动升级为更新操作（版本迭代），同一 gene 的多次发布不会产生重复 Skill。

### 14.4.4 Tag 清理

```javascript
// 发布前清理 tags：
// - 长度 < 3 的 tag 丢弃
// - 纯数字 tag 丢弃
// - 含 10 位以上数字（时间戳）的 tag 丢弃
tags = tags.filter(t =>
  String(t).trim().length >= 3 &&
  !/^\d+$/.test(t) &&
  !/\d{10,}/.test(t)
);
```

---

## 14.5 三模块协同：Hub 生态闭环

```
Solidify 成功
     │
     ├─► [hubReview.js] submitHubReview()
     │         ├─► Hub 收到评价 → 更新 asset reputation_score
     │         └─► 记录到 asset_call_log.jsonl
     │
     ├─► [skillPublisher.js] publishSkillToHub()
     │         ├─► Gene → SKILL.md 转换
     │         ├─► 发布到 Hub Skill Store
     │         └─► 其他节点可搜索/复用
     │
     └─► [assetCallLog.js] logAssetCall()
              ├─► 记录 hub_search_hit / hub_search_miss
              ├─► 记录 hub_review_submitted
              └─► 可按 run_id / since 聚合分析
```

**信任传播路径**：
1. Node A 使用 Hub Gene → submitHubReview(5星) → Hub reputation_score ↑
2. Node B 搜索相同类型 Gene → 看到高 reputation_score → 选择它
3. Node B 也成功使用 → 也提交 5 星评价 → reputation 进一步巩固

---

## 14.6 关键文件

| 文件 | 职责 |
|------|------|
| `src/gep/hubReview.js` | Hub 使用后评价提交（submitHubReview） |
| `src/gep/assetCallLog.js` | Append-only Hub 交互审计日志 |
| `src/gep/skillPublisher.js` | Gene → SKILL.md 转换 + Hub 发布 |

---

## 14.7 与 Claude-Mem 的对应

| EvoMap 模块 | Claude-Mem 可能的对应 |
|-------------|----------------------|
| Hub Review submission | 成功的 Session 总结 → 社区模板市场 |
| Asset Call Log | Session 级别的 API 调用记录 |
| Skill Publisher (gene→SKILL.md) | Observation → 可复用 Context Template |
| 去重机制（hub_review_history） | 已有 Observation 去重（最近 N 条） |
| reputation_score | 无直接对应（未来可考虑） |

---

_Next: （如需扩展，可继续补充）_
