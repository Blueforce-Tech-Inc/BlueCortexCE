# `strategy.js` 进化策略预设深度分析

**doc**: 111  
**源码**: `src/gep/strategy.js`（131L，纯 JS）  
**上下文**: doc 24（Gene/Strategy 层）已覆盖策略层设计概述；doc 74（Curriculum + Mutation 闭环）已覆盖策略在管线中的位置；本文专注 `strategy.js` 内部机制。  
**最后更新**: 2026-05-06

---

## 1. 模块定位

`strategy.js` 是 GEP 的**策略预设引擎**，负责：
1. 管理 7 种预设立场（repair / optimize / innovate 的权重分配）
2. 根据周期数和饱和信号自动切换策略
3. 提供 `resolveStrategy()` 作为策略查询的统一入口

---

## 2. 7 种策略预设

### 2.1 参数结构

| 字段 | 含义 |
|------|------|
| `repair` | repair 意图的目标权重 |
| `optimize` | optimize 意图的目标权重 |
| `innovate` | innovate 意图的目标权重 |
| `repairLoopThreshold` | 过去 8 周期中 repair 占比阈值，超过则强制创新 |
| `label` | 注入 GEP Prompt 的人类可读名称 |
| `description` | 策略描述 |

### 2.2 预设详情

| 策略 | repair | optimize | innovate | repairLoopThreshold | 典型场景 |
|------|--------|----------|----------|---------------------|---------|
| `balanced` | 0.20 | 0.30 | 0.50 | 0.50 | 正常运营 |
| `innovate` | 0.05 | 0.15 | 0.80 | 0.30 | 系统稳定，最大化探索 |
| `harden` | 0.40 | 0.40 | 0.20 | 0.70 | 大变更后，关注稳定性 |
| `repair-only` | 0.80 | 0.20 | 0.00 | 1.00 | 紧急情况，只修不改 |
| `early-stabilize` | 0.60 | 0.25 | 0.15 | 0.80 | 初始阶段，先修后创 |
| `steady-state` | 0.60 | 0.30 | 0.10 | 0.90 | 进化饱和，维持现状 |
| `auto` | — | — | — | — | 自适应（等价 balanced） |

**设计观察**：
- `innovate + repair = 1.0`（innovate=1.0 时 repair=0，repair-only=1.0 时 innovate=0）
- `repairLoopThreshold` 与 `repair` 权重正相关（repair 越重，阈值越高才触发强制创新）
- `early-stabilize` 的 repairLoopThreshold=0.80 意味着即使 80% 是 repair 仍可继续，不强制创新

---

## 3. 策略解析流程

```javascript
function resolveStrategy(opts) {
  // Step 1: 读取 EVOLVE_STRATEGY 环境变量
  var name = String(process.env.EVOLVE_STRATEGY || 'balanced').toLowerCase().trim();

  // Step 2: FORCE_INNOVATION 向后兼容
  if (!process.env.EVOLVE_STRATEGY) {
    var fi = String(process.env.FORCE_INNOVATION || process.env.EVOLVE_FORCE_INNOVATION || '').toLowerCase();
    if (fi === 'true') {
      name = 'innovate';
      forceInnovation = true;
    }
  }

  // Step 3: 自动检测（仅当未显式设置时生效）
  var isDefault = !process.env.EVOLVE_STRATEGY || name === 'balanced' || name === 'auto';
  if (isDefault && !forceInnovation) {
    // 3a: 周期数检测（cycle 1-5 → early-stabilize）
    var cycleCount = _readCycleCount();
    if (cycleCount > 0 && cycleCount <= 5) {
      name = 'early-stabilize';
    }
    // 3b: 饱和信号检测（force_steady_state / evolution_saturation → steady-state）
    if (signals.indexOf('force_steady_state') !== -1) {
      name = 'steady-state';
    } else if (signals.indexOf('evolution_saturation') !== -1) {
      name = 'steady-state';
    }
  }

  // Step 4: 解析策略对象
  var strategy = STRATEGIES[name] || STRATEGIES['balanced'];
  strategy.name = name;  // ← 附加 name 字段
  return strategy;
}
```

**关键点**：
- `FORCE_INNOVATION` 优先于自动检测（紧急场景）
- 自动检测仅在**默认策略**（未设置 EVOLVER_STRATEGY 或设为 balanced/auto）时生效
- cycleCount ≤ 5 → `early-stabilize`（"先修后创"原则）
- `force_steady_state` 优先于 `evolution_saturation`（前者是强制信号）

---

## 4. 周期数读取路径

```javascript
function _readCycleCount() {
  // 候选路径（按优先级）
  var localPath  = path.resolve(__dirname, '..', '..', 'memory', 'evolution_state.json');
  var workspacePath = path.resolve(__dirname, '..', '..', '..', '..', 'memory', 'evolution', 'evolution_state.json');
  // 返回第一个存在的文件，否则 0
}
```

**两个路径**：
1. `evolver/memory/evolution_state.json`（skill 本地）
2. `workspace/memory/evolution/evolution_state.json`（规范路径，evolve.js 使用）

---

## 5. 策略与 mutation.js 的联动

`mutation.js` 的 `mutationCategoryFromContext()` 调用 `strategy.resolveStrategy()`：

```javascript
// mutation.js § mutationCategoryFromContext
if (hasOpportunitySignal(signals)) return 'innovate';
try {
  var strategy = require('./strategy').resolveStrategy();
  if (strategy && typeof strategy.innovate === 'number' && strategy.innovate >= 0.5) return 'innovate';
} catch (_) {}
return 'optimize';
```

**联动条件**：`strategy.innovate ≥ 0.5` 时，即使无机会信号也触发 innovate。

---

## 6. 与 doc 24（Gene/Strategy 层）的分工

doc 24 覆盖了：
- Strategy presets 与 CE "观察注入策略"的对应关系
- 多因子 Gene selector 与 SearchService 增强
- Mutation safety 与观察风险分级

**本文专注**：strategy.js 本身的解析机制、7 种预设参数、自动检测算法、向后兼容性。

---

## 7. BlueCortexCE 借鉴

### P2: 策略驱动的观察注入

CE 的 `ModeService` 可以参考策略预设思想：
- 定义"注入策略"预设（如 `conservative` / `balanced` / `exploratory`）
- 根据 cycle count（会话轮次）自动切换到 `early-stabilize`
- 根据饱和信号（高重复观察）切换到 `steady-state`

### P3: 周期感知的模式

CE `AgentService` 可维护 `sessionCycleCount`：
- 周期 ≤ 3：注入 `early-stabilize` 策略（多 repair 类观察）
- 周期 > 10 + 重复高：`steady-state` 策略（降低创新尝试）
- 环境变量 `EVOLVE_STRATEGY` 映射为 CE 的 `INJECTION_MODE` 环境变量

### P3: 策略感知搜索排序

`SearchService` 可以引入策略感知的排序权重：
- `steady-state` 策略下，增加历史成功观察的权重（利用已知有效方案）
- `innovate` 策略下，增加新颖观察的权重（探索未知）
