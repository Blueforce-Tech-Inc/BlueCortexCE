# `80` v1.47 → v1.78.5 架构演进总览 + `config.js` 集中配置模式

**文件**: `docs/drafts/evolver-memory/80-architecture-evolution-v147-v178-config-centralization.md`  
**目标**: 记录 v1.47.0 → v1.78.5 的宏观架构演进（模块化解构 + 配置集中化），提炼可复用的设计原则  
**数据来源**: `git diff e72778e(v1.47.0)..v1.78.5` + `git show v1.78.5:src/config.js`  
**最后更新**: 2026-05-04

---

## ⚠️ 版本状态修正

| 项目 | 值 |
|------|----|
| 本地工作树 | `e72778e` = **v1.47.0**（`git describe --tags --always HEAD`） |
| 最新 tag | **v1.78.5**（`git tag \| sort -V \| tail -1`） |
| 代码状态 | v1.78.5 代码在 git 中，**未检出到工作树** |
| 文档历史 | 此前 docs 误将本地代码标为 v1.78.5；本文档按 v1.47.0 同步修正 |

**本文档分析基于 git v1.78.5 tag 源码**，在本地 v1.47.0 工作树差异已在 §1 标注。

---

## §1 宏观架构演进

### 1.1 净增减统计

```
v1.47.0 → v1.78.5 (31 个版本差距)
净增代码: +3,670 行
净删代码: -9,156 行
净变化:   -5,486 行（总体精简 60%）
```

### 1.2 核心模块精简（代码外置）

| 模块 | v1.47.0 行数 | v1.78.5 行数 | 净变化 | 说明 |
|------|------------|------------|--------|------|
| `solidify.js` | ~2,400 | ~1,055 | **-1,345** | 拆出 policyCheck/gitOps/llmReview/questionGen |
| `a2aProtocol.js` | ~2,000 | ~778 | **-1,222** | 拆出 transport/heartbeat/claimNudge |
| `memoryGraph.js` | ~1,500 | ~712 | **-788** | 拆出 event model/边权重/Laplace |
| `skillDistiller.js` | ~1,500 | ~265 | **-1,235** | 拆出 skill2gep.js（645L独立）|
| `selector.js` | ~1,200 | ~782 | **-418** | 表观遗传/漂移独立 |
| `policyCheck.js` | ~1,100 | ~549 | **-551** | 拆出 blast radius/compute |
| `prompt.js` | ~1,200 | ~617 | **~-600** | 模板/截断/Schema 独立 |
| `signals.js` | ~500 | ~804 | **+304** | 三层信号架构扩展（唯一增长）|

### 1.3 新增子系统

| 子系统 | 路径 | 规模 | 职责 |
|--------|------|------|------|
| **ATP** (Agent Transaction Protocol) | `src/atp/` | ~1,800L | Hub market 双向买卖 / task pickup / autoBuyer / autoDeliver |
| **Adapters** (平台适配) | `src/adapters/` | ~900L | cursor / claudeCode / codex / kiro / hookAdapter / 3 scripts |
| **Ops** (运维基础设施) | `src/ops/` | ~900L | health_check / lifecycle / cleanup / trigger / skills_monitor / commentary / innovation |
| **Validator** (沙箱验证) | `src/gep/validator/` | ~900L | sandboxExecutor / stakeBootstrap |
| **Config** (集中配置) | `src/config.js` | 215L | 全量 magic number + env override |

### 1.4 新增独立模块（`src/gep/`）

- `skill2gep.js` — 逆向蒸馏（SKILL.md → Gene）
- `selfPR.js` — 自动 PR 贡献
- `portable.js` — GEPX 归档导出
- `claimNudge.js` — 承诺提醒
- `mailboxTransport.js` — Proxy 邮箱传输
- `curriculum.js` — 课程边界探测（~163L）
- `executionTrace.js` — 执行轨迹三级脱敏
- `learningSignals.js` — 结构化信号扩展

### 1.5 演进模式总结

**模式：宏内核 → 微内核 + 插件体系**

```
v1.47: 单一职能大文件（evolve.js ~2500L）
         ↓ 重构
v1.78: 一个协调层（evolve.js ~300L）
     + 多个专用模块（src/gep/ 各司其职）
     + 平台适配层（src/adapters/）
     + 运维基础设施（src/ops/）
     + 事务协议（src/atp/）
     + 集中配置（src/config.js）
```

**设计原则**：
1. **单一职责**：每个文件 ≤ 1,000 行（大多数 ≤ 500 行）
2. **可插拔**：新功能加新目录，不修改核心文件
3. **配置外置**：所有 magic number 集中到 `config.js`，env override 无需改代码
4. **平台解耦**：`src/adapters/` 隔离各 AI 平台差异

---

## §2 `src/config.js` 集中配置模式（215行）

**文件**: `src/config.js`（v1.78.5）  
**定位**: 所有运行时 magic number 的单一真实源

### 2.1 Env Override 模式

```javascript
function envInt(key, fallback) {
  const v = process.env[key];
  if (v === undefined || v === '') return fallback;
  const n = parseInt(v, 10);
  return isNaN(n) ? fallback : n;
}
function envFloat(key, fallback) { /* 同理 */ }
function envStr(key, fallback) { /* 同理 */ }
```

所有常量均通过 `envXXX` 包装，**生产部署无需改代码**，只需设置环境变量。

### 2.2 配置分组（5 大类）

| 类别 | 典型参数 | 说明 |
|------|---------|------|
| **Network & A2A** | `HELLO_TIMEOUT_MS=15000` / `HEARTBEAT_INTERVAL_MS=360000` | Hub 连接、A2A 协议 |
| **Solidify & Validation** | `VALIDATION_TIMEOUT_MS=180000` / `CANARY_TIMEOUT_MS=30000` | 验证沙箱、canary 检查 |
| **Evolution Loop** | `REPAIR_LOOP_THRESHOLD=3` | 进化策略阈值 |
| **Gene Suppression** | `GENE_BAN_PER_KEY_ATTEMPTS=4` / `GENE_BAN_BEST_THRESHOLD=0.15` | 基因抑制/Ban |
| **Ops** | `MAX_SILENCE_MS=30min` / `CLEANUP_MAX_AGE_MS=24h` | 运维容限 |
| **Self-PR** | `SELF_PR_MIN_SCORE=0.85` / `SELF_PR_COOLDOWN_MS=24h` | 自动 PR 门槛 |
| **Leak Check** | `LEAK_CHECK_MODE=strict` | 隐私扫描强度 |

### 2.3 关键常量速查

```javascript
// Network
HELLO_TIMEOUT_MS        = 15000    // Hub 注册超时
HEARTBEAT_INTERVAL_MS   = 360000   // 6min 心跳间隔
EVENT_POLL_TIMEOUT_MS   = 60000    // Hub 事件轮询超时
HUB_SEARCH_TIMEOUT_MS    = 8000     // Hub 搜索超时

// Solidify
VALIDATION_TIMEOUT_MS    = 180000   // 3min 验证超时
CANARY_TIMEOUT_MS        = 30000    // 30s canary 验证
CAPSULE_CONTENT_MAX_CHARS= 8000     // Capsule 最大字数
MIN_PUBLISH_SCORE       = 0.78     // 发布最低评分

// Evolution
REPAIR_LOOP_THRESHOLD    = 3        // repair 连续次数门禁

// Gene
GENE_BAN_PER_KEY_ATTEMPTS= 4        // 同一 key 失败 4 次 Ban
GENE_BAN_BEST_THRESHOLD  = 0.15    // best < 0.15 强制 Ban
GENE_EPIGENETIC_HARD_BOOST = -0.3  // 表观遗传硬 boost 上限

// Memory/Graph
PROMPT_MAX_CHARS         = 24000    // Prompt 上限
TARGET_BYTES             = 120000   // 上下文目标 token 预算
PER_FILE_BYTES           = 20000    // 单文件 token 预算
DORMANT_TTL_MS           = 3600000  // 1h dormant 恢复

// Self-PR
SELF_PR_MIN_SCORE       = 0.85     // 自动 PR 最低评分
SELF_PR_MIN_STREAK      = 3        // 连续成功次数
SELF_PR_MAX_FILES       = 3        // PR 最大文件数
SELF_PR_MAX_LINES       = 100       // PR 最大行数
SELF_PR_COOLDOWN_MS     = 86400000  // 24h PR 冷却期
```

### 2.4 CE 借鉴路径

**P0 — 必须借鉴**（BlueCortexCE 当前无集中配置）：

1. **创建 `backend/src/main/resources/config.java`** 或 Spring `@ConfigurationProperties` 类，将当前散落在各 Service 的 magic number 集中
2. **Env override 支持**：参考 `envInt`/`envFloat`/`envStr` 模式，Java 用 `@Value("${key:fallback}")` 或 `Environment.getProperty(key, type, defaultValue)`
3. **分组组织**：按 Network / Solidify / Evolution / Ops / Security 分组，便于审计

**具体缺口**（当前硬编码在代码中）：
- `SearchService` 的 `DEFAULT_LIMIT=20`、向量维度
- `EmbeddingService` 的 batch size、timeout
- `ObservationService` 的半衰期常数
- `RateLimitService` 的窗口大小

**示例借鉴**：
```java
// BlueCortexCE - config properties
@ConfigurationProperties(prefix = "cortex.memory")
public class MemoryConfig {
    private int defaultSearchLimit = 20;
    private int vectorDimension = 1536;
    private int embeddingBatchSize = 100;
    private long dormantTtlMs = 3600000;
    // env override: CORTEX_MEMORY_DEFAULT_SEARCH_LIMIT=50
}
```

---

## §3 关键差异：v1.47.0 本地代码 vs v1.78.5 分析源码

| 特性 | 本地 v1.47.0 | v1.78.5 |
|------|------------|---------|
| `src/config.js` | ❌ 不存在（分散在各模块） | ✅ 215L 集中配置 |
| `src/adapters/` | ❌ 不存在 | ✅ ~900L 平台适配 |
| `src/atp/` | ❌ 不存在 | ✅ ~1,800L ATP |
| `src/ops/` | ⚠️ 简陋（分散） | ✅ ~900L 完整运维 |
| `src/gep/validator/` | ❌ 不存在 | ✅ ~900L 沙箱 |
| `signals.js` | ~500L 单层 regex | ~804L 三层架构 |
| `solidify.js` | ~2,400L | ~1,055L |
| `evolve.js` | ~2,500L | ~300L |

---

## §4 设计原则提炼

### 4.1 宏内核 → 微内核演进模式

**问题**：单文件随功能增长线性膨胀，最终难以维护（v1.47 `evolve.js` ~2,500L）

**解法**：
1. 识别模块边界（按职责：验证、传输、持久化、平台适配、运维）
2. 每轮版本迭代从大文件中提取 ≤ 3 个模块（避免大爆炸重构）
3. 用协调层（evolve.js ~300L）替代具体逻辑

### 4.2 配置即代码

**问题**：magic number 散落 20+ 文件，生产调优需要改源码

**解法**：
```javascript
// 每个常量 = 一行 config.js 导出 + env override
const HELLO_TIMEOUT_MS = envInt('EVOLVER_HELLO_TIMEOUT_MS', 15000);
```
优点：
- **可发现性**：审计所有可调参数只需查看 `config.js`
- **可测试性**：Jest 可用 `process.env` 注入测试值
- **可回滚**：env 变更可版本控制（与代码分离）

### 4.3 平台适配器模式

**问题**：cursor/codex/claude-code/kiro 各有不同 transcript 格式、hook 接口

**解法**：`src/adapters/` 为每个平台维护独立 adapter，核心逻辑零平台知识：

```
src/adapters/
  cursor.js       — readCursorTranscripts / writeEvolverHook
  claudeCode.js   — tool_input.* 嵌套解析
  codex.js        — Codex 特有格式
  kiro.js         — Kiro 特有格式
  hookAdapter.js  — 统一 setupHooks / removeEvolverHooks
  scripts/
    evolver-signal-detect.js   — 7类 signal 检测
    evolver-session-start.js   — 历史注入
    evolver-session-end.js     — git diff + Hub API
```

---

## §5 CE 行动项

| 优先级 | 行动 | 关联 |
|--------|------|------|
| **P0** | 创建集中配置类 `MemoryConfig.java`，收拢 magic number | 本 doc §2 |
| **P0** | 确认本地工作树升级到 v1.78.5 的计划（git checkout v1.78.5） | 本 doc §1 |
| **P1** | 梳理当前 `evolve.js` 的大小，若 >1,500L 考虑模块拆分 | v1.78 演进模式 |
| **P1** | 评估 Java 侧 adapter 模式（cursor vs claude-code vs codex 差异处理） | 本 doc §4.3 |
| **P2** | 运维配置化（health_check threshold / cleanup age 可 env 配置） | `src/ops/` 模式 |
