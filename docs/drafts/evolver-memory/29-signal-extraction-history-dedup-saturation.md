# Signal 提取、历史去重与饱和降级

> **来源**：`EvoMap/evolver/src/gep/signals.js`（~440 行）  
> **补充**：[`21`](./21-signal-taxonomy-and-gene-selection-memory.md)（标签分类）、[`25`](./25-advanced-patterns-prm-epigenetic-antipattern.md)（停滞信号枚举）  
> **最后更新**：2026-04-23

---

## 1. `analyzeRecentHistory` — 历史状态感知器

`extractSignals` 在提取原始信号**之前**，先调用 `analyzeRecentHistory(recentEvents)` 构建一个**历史状态摘要**。这是去重和降级的决策基础。

### 1.1 输入：最近 10 个 EvolutionEvent

取 `recentEvents.slice(-10)`，基于此计算：

| 指标 | 计算方式 | 用途 |
|------|----------|------|
| `consecutiveRepairCount` | 从尾部倒数连续 `intent === 'repair'` 的次数 | 修复循环检测 |
| `signalFreq` | 最近 8 个事件中每个信号出现次数（`errsig:` 归一为 `errsig`） | 过度处理检测 |
| `geneFreq` | 最近 8 个事件中每个 gene 使用次数 | 失败循环时 ban 主导 gene |
| `emptyCycleCount` | 最近 8 个事件中 `blast_radius.files === 0` 的次数 | 空转检测 |
| `consecutiveEmptyCycles` | 从尾部倒数连续空转次数 | 饱和降级 |
| `consecutiveFailureCount` | 从尾部倒数连续 `outcome.status === 'failed'` 的次数 | 失败连击 |
| `recentFailureRatio` | 最近 8 个事件中失败占比 | 高失败率干预 |

### 1.2 关键设计：尾部连续 vs 窗口总计

- **`consecutive*` 指标**从尾部倒数——检测"当前正在发生的问题"
- **窗口指标**（`signalFreq`、`emptyCycleCount`）取最近 8 个——检测"近期模式"

这种双层设计允许区分：
- "最近 8 个里有 4 个空转但最近 2 个正常" → 不触发饱和
- "最近 8 个里有 4 个空转且最近 5 个连续空转" → 触发 `force_steady_state`

---

## 2. 信号去重与抑制

### 2.1 频率抑制（信号级）

```javascript
// 出现 ≥3 次 / 最近 8 个事件 → 抑制
if (entries[ei][1] >= 3) suppressedSignals.add(key);
```

归一化规则：
- `errsig:xxx` → `errsig`
- `recurring_errsig(xxx)` → `recurring_errsig`
- `user_feature_request:snippet` → `user_feature_request`

**如果所有信号都被抑制** → 注入 `evolution_stagnation_detected` + `stable_success_plateau`（强制创新）

### 2.2 连续修复强制创新

```
consecutiveRepairCount ≥ 3:
  1. 移除所有 repair 信号（log_error, errsig:*, recurring_errsig:*）
  2. 若无剩余信号 → repair_loop_detected + stable_success_plateau
  3. 追加 force_innovation_after_repair_loop
```

### 2.3 空转循环检测

```
emptyCycleCount ≥ 4（最近 8 个中）:
  → 移除 repair 信号
  → 注入 empty_cycle_loop_detected + stable_success_plateau

consecutiveEmptyCycles ≥ 3:
  → evolution_saturation

consecutiveEmptyCycles ≥ 5:
  → force_steady_state + evolution_saturation（优雅降级）
```

**关键引用**（源码注释）：
> "This directly addresses the Echo-MingXuan failure: Cycle #55 hit 'no committable code changes' and load spiked to 1.30 because there was no degradation strategy."

### 2.4 失败连击干预

```
consecutiveFailureCount ≥ 3:
  → consecutive_failure_streak_N

consecutiveFailureCount ≥ 5:
  → failure_loop_detected
  → ban_gene:{topGene}（使用频率最高的 gene 被 ban）

recentFailureRatio ≥ 0.75:
  → high_failure_ratio + force_innovation_after_repair_loop
```

---

## 3. 多语言信号提取

`extractSignals` 支持 4 种语言的功能需求和改进建议检测：

| 语言 | 功能需求关键词 | 改进建议关键词 |
|------|--------------|--------------|
| EN | "add/implement/create...feature" | "should be/could be better/improve" |
| ZH-CN | 加个/实现一下/想要一个/我想 | 改进一下/优化一下/简化/重构 |
| ZH-TW | 加個/實現一下/想要一個 | 改進一下/優化一下/簡化/重構 |
| JA | 追加/実装/作って/機能を/してほしい | 改善/最適化/簡素化/リファクタ |

每种语言提取一个 **≤200 字符的 snippet**，附加到信号上（`user_feature_request:snippet`），供 selector 和 prompt 使用。

---

## 4. 工具绕行检测

```javascript
const bypassPatterns = [
  /node\s+\S+\.m?js/,   // node script.js
  /npx\s+/,              // npx command
  /curl\s+.*api/i,       // curl ...api
  /python\s+\S+\.py/,    // python script.py
];
```

当 agent 用 shell/exec 执行临时脚本而非注册工具时，注入 `tool_bypass` 信号。这检测**工具完整性问题**。

另外，`exec` 工具使用计数会减去良性命令（`node *.js ensure`），避免误报。

---

## 5. 信号优先级清理

```
可操作信号 = 所有信号 - [user_missing, memory_missing, session_logs_missing, windows_shell_incompatible]

if (可操作信号.length > 0):
  移除所有纯信息性信号
```

这确保当存在真正需要处理的问题时，不会被信息性噪音干扰。

---

## 6. BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译方案 | 优先级 |
|-------------|-----------|--------|
| **信号频率抑制** | `TimelineService` 统计最近 N 条同类观察的频率，超过阈值则降权 | P1 |
| **连续修复检测** | 检测连续 N 条 `type=error` 观察 → 注入"建议探索新方向"上下文 | P0 |
| **空转饱和降级** | 检测连续 N 次无新内容的 session → 降低 context 生成频率或切换稳态模式 | P1 |
| **失败连击 ban** | 连续失败时降低对应提取模板的权重（而非完全禁用） | P1 |
| **多语言需求提取** | `StructuredExtractionService` 的提示词中增加多语言模式匹配 | P2 |
| **工具绕行检测** | 不适用（CE 无工具执行层） | — |

### 6.1 信号抑制的 CE 实现思路

```java
// 在 TimelineService 或新增 SignalAnalysisService 中：
public class SignalDeduplicationService {
    // 统计最近 windowSize 条观察中每种 type 的频率
    Map<String, Integer> typeFrequency = recentObservations.stream()
        .collect(groupingBy(ObservationEntity::getType, counting()));

    // 超过阈值的 type 降权
    Set<String> suppressedTypes = typeFrequency.entrySet().stream()
        .filter(e -> e.getValue() >= SUPPRESSION_THRESHOLD)
        .map(Map.Entry::getKey)
        .collect(toSet());
}
```
