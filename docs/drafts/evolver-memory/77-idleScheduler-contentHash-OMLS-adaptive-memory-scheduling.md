# 77 — IdleScheduler（OMLS自适应调度）+ Content-Addressable Asset（内容寻址）系统深度分析

**目标**：分析 EvoMap/evolver 中两个关键但未充分文档化的设计机制：
1. **IdleScheduler**（`idleScheduler.js`，157行）— OMLS 启发的用户空闲感知的自适应调度
2. **Content-Addressable Asset**（`contentHash.js`，65行）— 内容寻址哈希系统

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/idleScheduler.js` + `contentHash.js` 源码  
**最后更新**：2026-05-03

---

## §1 IdleScheduler — OMLS 启发的自适应记忆调度

### 1.1 设计背景

传统的定时任务（如 cron）以固定频率运行，不管用户是否在场。OMLS（Observe-Monitor-Learn-System）理念主张：**让 AI 系统感知用户的活动状态，在用户空闲时执行重计算任务（蒸馏、反思、深度分析），在用户繁忙时仅做轻量级信号收集**。

Evolver 的 IdleScheduler 实现了这一理念，作为主循环的调度顾问（advisor），不直接控制流程，而是提供强度建议。

### 1.2 核心接口

```javascript
// idleScheduler.js 导出
getSystemIdleSeconds()       // 获取系统级空闲秒数（跨平台）
determineIntensity(idle)     // idle → intensity level
getScheduleRecommendation()  // 主入口：返回完整调度建议对象
```

### 1.3 强度等级（4级）

| 等级 | 触发条件 | `sleep_multiplier` | `should_distill` | `should_reflect` | `should_deep_evolve` |
|------|---------|-------------------|-----------------|-----------------|---------------------|
| `signal_only` | 平台不支持或超时 | 3 | ✗ | ✗ | ✗ |
| `normal` | `idle < 300s` | 1 | ✗ | ✗ | ✗ |
| `aggressive` | `300s ≤ idle < 1800s` | 0.5 | ✓ | ✓ | ✗ |
| `deep` | `idle ≥ 1800s` (30min) | 0.25 | ✓ | ✓ | ✓ |

**关键洞察**：
- `sleep_multiplier` 作用于主循环的休眠时间：空闲时休眠更短，活跃时休眠更长
- `should_distill` / `should_reflect` 直接控制是否执行技能蒸馏和反思操作
- `should_deep_evolve` 预留，未来可能触发 RL 或微调

### 1.4 跨平台空闲检测

```javascript
// Windows: PowerShell + C# P/Invoke
GetLastInputInfo()  →  (Environment.TickCount - li.dwTime) / 1000

// macOS: ioreg
ioreg -c IOHIDSystem | grep HIDIdleTime → / 1e9

// Linux: xprintidle
xprintidle 2>/dev/null → ms / 1000

// 兜底: 所有平台超时返回 -1 → 降级为 'normal'
```

**设计特点**：
- 每种平台用原生 API，不依赖第三方库
- 10s（Windows）/ 5s（macOS/Linux）超时保护
- `-1` 表示平台不支持或调用失败，触发 `normal` 模式（不做激进假设）

### 1.5 状态持久化

```javascript
// 存储路径: evolution_dir/idle_schedule_state.json
{
  "last_check": "2026-05-03T22:00:00.000Z",
  "last_idle_seconds": 1847,
  "last_intensity": "aggressive"
}
```

**目的**：
- 记录历史用于调试和审计
- 原子写入（`.tmp` + `rename`）防止状态损坏
- 独立于主循环，支持外部监控工具读取

### 1.6 与主循环集成

`index.js` 主循环在每个周期调用 `getScheduleRecommendation()`，根据返回值调整：
- `sleep_multiplier` → 控制 `setTimeout` 休眠时长
- `should_distill` → 决定是否执行 OMLS 主动蒸馏
- `should_reflect` → 决定是否执行自省循环

### 1.7 BlueCortexCE 借鉴路径（P0 高优先级）

**当前状态**：BlueCortexCE 的 cron 任务以固定间隔（2小时）运行，不感知用户活动状态。

**借鉴方案**：

```java
// Proposal: IdleScheduler for BlueCortexCE (Java)
public class IdleScheduler {
    private static final int IDLE_THRESHOLD_SECONDS = 300;
    private static final int DEEP_IDLE_THRESHOLD_SECONDS = 1800;

    public enum Intensity {
        SIGNAL_ONLY,  // 用户活跃，仅收集信号
        NORMAL,       // 默认
        AGGRESSIVE,   // 空闲，可执行深度分析
        DEEP          // 深度空闲，可执行重量级操作
    }

    public ScheduleRecommendation getRecommendation() {
        int idleSeconds = getSystemIdleSeconds();
        Intensity intensity = determineIntensity(idleSeconds);
        // ...
    }

    // 实现空闲检测（macOS/Linux）
    private int getSystemIdleSeconds() {
        // macOS: ioreg -c IOHIDSystem | grep HIDIdleTime
        // Linux: xprintidle
        // Java: JNA 调用 native 方法
    }
}
```

**应用场景**：
- **用户在场时**：cron 任务仅做轻量级健康检查（`/api/health`），不执行向量搜索或复杂推理
- **用户空闲时（会议/离开）**：执行重量级操作——批量会话摘要、记忆精炼、模式分析
- **深夜/深度空闲**：执行数据迁移、归档、长期记忆压缩

---

## §2 Content-Addressable Asset System

### 2.1 设计目的

`contentHash.js` 提供：
1. **去重**：相同内容 → 相同 ID，避免重复存储
2. **篡改检测**：验证 `asset_id` 是否与内容匹配
3. **跨节点一致性**：SHA-256 是确定性的，任意节点计算结果相同

### 2.2 核心 API

```javascript
// 规范化序列化（确定性 JSON）
canonicalize(obj)
// → '{"key1":1,"key2":[1,2,3],"key3":"str"}'  // keys 排序，数字/布尔转字符串

// 计算内容 ID
computeAssetId(obj, excludeFields)
// → "sha256:<64-char-hex>"  // 排除 asset_id 自身

// 验证完整性
verifyAssetId(obj)
// → true/false
```

### 2.3 Canonicalization 算法

```javascript
// 递归遍历，规则：
// - null/undefined → "null"
// - boolean → "true"/"false"
// - number（非有限）→ "null"
// - string → JSON.stringify（加引号）
// - Array → [...].map(canonicalize).join(',')
// - Object → keys.sort().map(k=JSON.stringify(k)+':'+canonicalize(v)).join(',')
```

**示例**：
```javascript
canonicalize({b: 2, a: 1})  // '{"a":1,"b":2}'  (keys 排序)
canonicalize([3, 1, 2])      // '[3,1,2]'        (数组不排序)
canonicalize({x: NaN})       // '{"x":null}'     (NaN → null)
```

### 2.4 在 Gene/Capsule 中的应用

```javascript
// assetStore.js 使用 computeAssetId 为每个 Gene/Capsule 生成 asset_id
const assetId = computeAssetId(gene, ['asset_id']);
const geneWithId = { ...gene, asset_id: assetId };

// 验证
if (!verifyAssetId(geneWithId)) {
    throw new Error('Gene tampered or serialization changed');
}
```

**存储结构**（`assets/gep/genes.jsonl`）：
```json
{"type":"Gene","id":"gene_gep_repair_from_errors","asset_id":"sha256:abc123...","version":1,...}
```

### 2.5 Schema Version 管理

```javascript
const SCHEMA_VERSION = '1.6.0';
// 约定：
// - MAJOR bump: breaking changes（需要迁移）
// - MINOR bump: additive fields（向后兼容）
```

### 2.6 BlueCortexCE 借鉴路径（P2 中优先级）

**当前状态**：BlueCortexCE 使用数据库自增 ID 或 UUID 作为实体标识，内容变化时 ID 不变，无法感知内容变化。

**借鉴方案**：

```java
// Proposal: Content-addressable ID for BlueCortexCE entities
public class ContentHashUtil {
    // Canonical JSON serialization (sorted keys, NaN→null, etc.)
    public static String canonicalize(Object obj) { ... }

    // SHA-256 content ID
    public static String computeContentId(Object obj, String... excludeFields) {
        String canonical = canonicalize(obj);
        return "sha256:" + sha256(canonical);
    }

    // Verify integrity
    public static boolean verifyContentId(HasContentId entity) {
        String computed = computeContentId(entity, "contentId");
        return computed.equals(entity.getContentId());
    }
}

// 应用场景：
// 1. ObservationEntity deduplication
// 2. Gene/Capsule（如果 CE 引入）内容指纹
// 3. 跨节点事件一致性验证
```

---

## §3 两者联系：Idle + Content-Addressable

IdleScheduler 控制**何时**执行重计算，Content-Addressable 保证**什么**被存储是幂等的。两者共同支撑"按需执行+去重存储"的设计哲学。

**典型场景**：
1. 用户空闲 45min → IdleScheduler 返回 `aggressive`
2. Evolver 执行 `skillDistiller` 蒸馏新 Capsule
3. `computeAssetId` 为 Capsule 生成确定性 ID
4. 如果相同输入已蒸馏过（相同 asset_id），跳过重复写入
5. 结果写入 `assets/gep/capsules.jsonl` + `memory_graph.jsonl`

---

## §4 附录：完整导出接口对照

| 模块 | 导出 | 行数 | 职责 |
|------|------|------|------|
| `idleScheduler.js` | `getScheduleRecommendation()` | 157 | 系统空闲感知调度 |
| `idleScheduler.js` | `getSystemIdleSeconds()` | - | 跨平台空闲检测 |
| `contentHash.js` | `canonicalize()` | 65 | 确定性 JSON 序列化 |
| `contentHash.js` | `computeAssetId()` | - | SHA-256 内容寻址 |
| `contentHash.js` | `verifyAssetId()` | - | 篡改检测 |
| `contentHash.js` | `SCHEMA_VERSION` | - | 1.6.0 |

---

## §5 变更历史

| 版本 | 日期 | 内容 |
|------|------|------|
| 77-v1 | 2026-05-03 | 初始创建：IdleScheduler + ContentHash 深度分析 |

---

**CE P0 借鉴**：IdleScheduler 自适应调度 → BlueCortexCE cron 任务强度分级  
**CE P2 借鉴**：Content-Addressable → ObservationEntity 去重 / 内容指纹
