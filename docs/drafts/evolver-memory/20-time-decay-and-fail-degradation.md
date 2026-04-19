# 排序增强专题：时间衰减与重复失败降权

> **目标**：将 Evolver `memoryGraph.js` 的 `decayWeight` + `edgeExpectedSuccess` 机制翻译为 BlueCortexCE 的检索排序增强建议。  
> **数据来源**：`EvoMap/evolver/src/gep/memoryGraph.js` §1（`decayWeight`、`edgeExpectedSuccess`）及 `09` §3.2。  
> **前置**：先读 [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) §3.2 与 §4 P0/P1。  
> **状态**：未实现（backlog item）

---

## 1. Evolver 实现摘要

### 1.1 `decayWeight`：指数半衰衰减

```javascript
function decayWeight(updatedAtIso, halfLifeDays) {
  const hl = Number(halfLifeDays);
  if (!Number.isFinite(hl) || hl <= 0) return 1;
  const t = Date.parse(updatedAtIso);
  if (!Number.isFinite(t)) return 1;
  const ageDays = (Date.now() - t) / (1000 * 60 * 60 * 24);
  if (!Number.isFinite(ageDays) || ageDays <= 0) return 1;
  // Exponential half-life decay: weight = 0.5^(age/hl)
  return Math.pow(0.5, ageDays / hl);
}
```

- **公式**：`weight = 0.5^(ageDays / halfLifeDays)`
- **半衰期默认**：边用 30 天，基因用 45 天
- **边界**：无法解析时间戳时返回 1（全权重）

### 1.2 `edgeExpectedSuccess`：Laplace 平滑 + 衰减加权

```javascript
function edgeExpectedSuccess(edge, opts) {
  const e = edge || { success: 0, fail: 0, last_ts: null };
  const succ = Number(e.success) || 0;
  const fail = Number(e.fail) || 0;
  const total = succ + fail;
  const p = (succ + 1) / (total + 2);          // Laplace smoothing
  const halfLifeDays = opts?.half_life_days ?? 30;
  const w = decayWeight(e.last_ts || '', halfLifeDays);
  return { p, w, total, value: p * w };        // value = p * w
}
```

- **Laplace 平滑**：`p = (success + 1) / (total + 2)`，避免 0/1 的极端概率
- **最终得分**：`value = p * decay_weight`
- **用途**：`getMemoryAdvice` 用 `value` 排序候选基因

### 1.3 重复失败降权模式

Evolver 在以下几处体现「重复失败降权」：

| 机制 | 代码位置 | 效果 |
|------|----------|------|
| `aggregateEdges` 按 `(signalKey, geneId)` 聚合 | `memoryGraph.js:188` | 同 signal+gene 的多次失败累加，`total` 增大拉低 `p` |
| `edgeExpectedSuccess` 中 `total = success + fail` | `memoryGraph.js:241` | Laplace 平滑使高频失败基因的 `p` 趋近 0.33 |
| `inferOutcomeEnhanced` 中 `prevHadError` vs `currentHasError` | `memoryGraph.js:504` | 错误信号链驱动基因选择 |
| `plateau/recurring/steady_state` 标签检测 | `learningSignals.js:43` | 特定信号词触发降权或重试冷却 |

### 1.4 半衰期参数差异

| 场景 | 半衰期 | 说明 |
|------|--------|------|
| 边（signal→gene） | 30 天 | 较快衰减，适应技能时效性 |
| 基因全局结果 | 45 天 | 较慢衰减，基因能力相对稳定 |
| 置信边构建 | `halfLifeDays` 参数传入 | 调用方指定（默认 30） |

---

## 2. BlueCortexCE 当前状态

### 2.1 现有排序机制

CE `SearchService` 当前排序逻辑（基于 `backend/src/main/java/com/ablueforce/cortexce/service/SearchService.java`）：

1. **向量相似度**：`embedding` 列的余弦距离
2. **时间过滤**：`minEpoch` 硬下界
3. **结果上限**：`maxResults` 硬上限

**缺少**：
- 时间衰减（越老的观察 relevance 不变）
- 重复失败模式降权
- 质量分（基于 `outcome` 或反馈的动态权重）

### 2.2 缺口对照

| 功能 | Evolver | CE | 差距 |
|------|---------|-----|------|
| 时间衰减 | `decayWeight(halfLifeDays=30)` | 无 | **缺失** |
| 重复失败降权 | `p = (succ+1)/(total+2)` | 无 | **缺失** |
| Laplace 平滑 | 有 | 无 | **缺失** |
| 半衰期可配置 | 边 30d / 基因 45d | 无 | **缺失** |
| 质量分排序 | `value = p * w` | 仅向量距离 | **差距** |

### 2.3 相关现有字段

CE `mem_observations` 表中可用于排序增强的现有字段：

| 字段 | 类型 | 可用于 |
|------|------|--------|
| `created_epoch` | BIGINT | 时间衰减计算 |
| `observation_type` | VARCHAR | 按类型差异化半衰（如 `error` 更短） |
| `quality_score` | DOUBLE（预留） | 直接加权 |
| `extracted_data` | JSONB | 可存储 `fail_count`、`success_count` 等 |
| `content_hash` | VARCHAR | 去重 + 重复检测 |

---

## 3. 翻译方案（CE 检索排序增强）

### 3.1 时间衰减（`time_decay_score`）

在 SQL 层增加一个可选项，或在 Java 排序逻辑中计算：

```sql
-- PostgreSQL 表达式（可作为排序加分项）
-- half_life_days = 30
POWER(0.5, (EXTRACT(EPOCH FROM (NOW() - to_timestamp(o.created_epoch / 1000))) / (30 * 24 * 3600))) AS time_decay_score
```

或 Java 实现：

```java
double decayWeight(long createdEpochMs, int halfLifeDays) {
    if (createdEpochMs <= 0) return 1.0;
    double ageDays = (System.currentTimeMillis() - createdEpochMs) / (1000.0 * 60 * 60 * 24);
    if (ageDays <= 0) return 1.0;
    return Math.pow(0.5, ageDays / halfLifeDays);
}
```

**半衰期默认值**：建议 observation 表用 **14–30 天**（比 Evolver 的基因短，因 CE 是旁路记忆、上下文更频繁）。

### 3.2 重复失败降权（`fail_penalty`）

通过 `extracted_data` 中的 `error_signature` 或 `fail_count` 字段实现：

```java
double failPenalty(ObservationEntity obs) {
    Integer failCount = extractFailCount(obs.getExtractedData());
    if (failCount == null || failCount == 0) return 1.0;
    // 非线性降权：fail 越多惩罚越重，但有下限
    double penalty = 1.0 / (1.0 + Math.log1p(failCount));
    return Math.max(penalty, 0.1); // 最低 10% 权重
}
```

### 3.3 综合排序公式

最终得分（可作为 SQL `ORDER BY` 或内存二次排序）：

```
final_score = cosine_similarity * time_decay_score * fail_penalty
```

或带质量分的扩展：

```
final_score = cosine_similarity * time_decay_score * fail_penalty * quality_multiplier
```

其中 `quality_multiplier` 来自 `observation_type` 或 `extracted_data.quality_score`。

### 3.4 与 `minEpoch` 的关系

- **`minEpoch`**：硬下界（完全不展示太老的观察）
- **`time_decay_score`**：软降权（越老排名越低，但仍可展示）

建议保留 `minEpoch` 作为安全边界，叠加 `time_decay_score` 作为排序增强。

---

## 4. 观察类型差异化半衰

不同 observation type 应有不同的半衰期：

| Type | 建议半衰期 | 理由 |
|------|-----------|------|
| `error` | 7–14 天 | 错误模式变化快，老错误可能已修复 |
| `insight` | 30–60 天 | 有价值的洞见更持久 |
| `decision` | 21–30 天 | 决策上下文有一定有效期 |
| `preference` | 60–90 天 | 用户偏好相对稳定 |
| `default` | 14–30 天 | 通用默认 |

实现方式：在 SQL 查询或 Java 排序逻辑中按 `observation_type` 查表取 `half_life_days`。

---

## 5. 实施路径

| 阶段 | 动作 | 影响文件 |
|------|------|----------|
| **P0.1** | 在 `SearchService` 增加 `time_decay_score` SQL 表达式或 Java 计算 | `SearchService.java` |
| **P0.2** | 按 `observation_type` 配置差异化半衰期 | `SearchService.java` 或配置 |
| **P0.3** | 将 `time_decay_score` 纳入 `ORDER BY` 排序公式 | `SearchService.java` |
| **P1.1** | `extracted_data` 中写入 `fail_count`（来自反馈或 `error_signature` 去重） | `ObservationService.java` 或 ingestion 链 |
| **P1.2** | 在排序中叠加 `fail_penalty` | `SearchService.java` |
| **P2** | 添加 `quality_score` 字段并作为排序因子 | schema migration + `SearchService` |

---

## 6. 与现有 backlog 的关系

- **本文件**：聚焦排序增强的 Evolver 机制与 CE 翻译方案。
- **Backlog**（[`11-research-backlog.md`](./11-research-backlog.md)）中的勾选槽：
  - [ ] **时间半衰 / 重复失败降权**：对应本文 P0.1–P1.2。
  - [ ] **错误类观察 `extracted_data` 约定**：为 `fail_count` 提供数据基础（P1.1）。

---

## 7. 深入阅读

| 主题 | 入口 |
|------|------|
| Evolver `decayWeight` 完整上下文 | `EvoMap/evolver/src/gep/memoryGraph.js:168–175` |
| `edgeExpectedSuccess` + Laplace | `EvoMap/evolver/src/gep/memoryGraph.js:236–247` |
| CE `SearchService` 当前实现 | `backend/src/main/java/com/ablueforce/cortexce/service/SearchService.java` |
| 方面对照（优先级 / 反模式） | [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) §4 P0/P1 |
| CE 实现锚点 | [`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md) |
