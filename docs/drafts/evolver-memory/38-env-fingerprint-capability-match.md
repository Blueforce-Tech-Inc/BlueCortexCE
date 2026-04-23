# EnvFingerprint + CapabilityMatch：环境指纹与任务能力匹配

**目标**：分析 EvoMap/evolver 中两个尚未在现有文档深入覆盖的子系统——
`envFingerprint`（环境指纹）和 `taskReceiver.capabilityMatch`（任务能力匹配），提炼对 BlueCortexCE 的借鉴思路。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/envFingerprint.js`、
`src/gep/taskReceiver.js`（offset 101–250）

---

## 1. envFingerprint：跨环境扩散成功率测量

### 1.1 设计动机

Evolver 的 Gene/Capsule 资产生成后，会在不同环境（设备、OS、容器、Node 版本）中扩散。
**GDI（Gene Diffusion Index）** 需要衡量：同一资产在"同类环境"中的成功率 vs "跨类环境"中的成功率。

这要求系统能够：
1. 捕获发布时的**运行环境指纹**
2. 后续遇到问题时，能判断是否因**环境差异**导致

### 1.2 捕获内容

```javascript
// src/gep/envFingerprint.js  captureEnvFingerprint()
return {
  device_id: getDeviceId(),           // 持久设备标识
  node_version: process.version,        // Node.js 版本
  platform: process.platform,          // darwin/linux/win32
  arch: process.arch,                  // arm64/x64
  os_release: os.release(),            // OS 内核版本
  hostname_hash: sha256(hostname)[:12], // 主机名哈希（不暴露原始值）
  evolver_version: pkgVersion,         // evolver 包版本
  client: pkgName || 'evolver',        // 客户端名称
  client_version: pkgVersion,          // 客户端版本
  region: EVOLVER_REGION[:5] | undefined,
  cwd_hash: sha256(cwd)[:12],          // 工作目录哈希
  container: isContainer(),            // 是否运行在容器中
  captured_at: ISO8601,
};
```

**隐私设计**：所有可能暴露部署环境细节的字段（hostname、cwd）均使用 SHA-256 哈希后取前 12 字符，而非原始值。

### 1.3 关键函数

#### `envFingerprintKey(fp)` → 环境类 key
将 `device_id + node_version + platform + arch + hostname + client + client_version` 拼接后 SHA-256 取前 16 字符，作为"环境类"标识。

```javascript
// 两个具有相同 key 的节点被认为是"同类环境"
function envFingerprintKey(fp) {
  const parts = [fp.device_id, fp.node_version, fp.platform,
                 fp.arch, fp.hostname, fp.client, fp.client_version].join('|');
  return sha256(parts).slice(0, 16);
}

function isSameEnvClass(fpA, fpB) {
  return envFingerprintKey(fpA) === envFingerprintKey(fpB);
}
```

#### `isContainer()` 检测
通过 `/proc/1/cgroup` 是否包含 `docker`/`containerd` 字符串判断容器环境。

### 1.4 在资产中的嵌入

`captureEnvFingerprint()` 的结果被嵌入：
- **Capsules**（固化资产）
- **EvolutionEvents**（进化事件）
- **ValidationReports**（验证报告）

这使得后续可以分析：同一 Capsule 在同类环境 vs 跨类环境中的成功率的差异。

### 1.5 BlueCortexCE 借鉴

| 方面 | Evolver 做法 | CE 可借鉴 |
|------|-------------|-----------|
| **Observation 增强** | env fingerprint 嵌入资产 | 在 `ObservationEntity` 中新增 `env_fingerprint` JSON 列 |
| **运行时上下文注入** | 每次 generateContext 可携带 env | 作为 ICL context 的额外 metadata |
| **跨环境根因分析** | Capsule 失败按 env class 分组 | 当某类 session 失败率异常时，按 runtime metadata 分组 |
| **隐私保护** | hostname/cwd 做哈希而非明文 | CE 的工作区路径也可类似处理 |

---

## 2. taskReceiver capabilityMatch：任务能力匹配评分

### 2.1 问题背景

Hub（资产市场）分发任务给节点时，需要判断：**哪个节点最适合这个任务？**
`estimateCapabilityMatch` 计算"节点对任务的匹配度"（0.0–1.0）：

```
composite = overlapScore × 0.4 + successScore × 0.6
```

### 2.2 三步计算

#### Step 1：从 memory graph 聚合历史 outcomes

```javascript
// 遍历 outcome 事件，按 signal_key 分组
for (ev of memoryEvents) {
  if (ev.kind !== 'outcome') continue;
  const sigs = ev.signal.signals;
  const key = ev.signal.key;  // signal_key（stable hash）
  const status = ev.outcome.status;  // 'success' | 'failed'

  totalBySignalKey[key]++;
  if (status === 'success') successBySignalKey[key]++;
}
```

#### Step 2：计算两个 score

**overlapScore**（信号覆盖度）：
```javascript
// taskSignals 与节点历史中所有出现过的信号做 Jaccard
const allSigArr = Object.keys(allSignals);  // 节点曾处理过的所有信号
const overlapScore = jaccard(taskSignals, allSigArr);
```

**successScore**（历史成功率，按相似度加权）：
```javascript
// 对每个匹配的 signal_key，计算带 Laplace 平滑的成功率
for (sk in totalBySignalKey) {
  const skParts = sk.split('|').map(s => s.trim().toLowerCase());
  const sim = jaccard(taskSignals, skParts);
  if (sim < 0.15) continue;

  const total = totalBySignalKey[sk];
  const succ = successBySignalKey[sk] || 0;
  const rate = (succ + 1) / (total + 2);  // Laplace 平滑
  weightedSuccess += rate * sim;
  weightSum += sim;
}
const successScore = weightSum > 0 ? weightedSuccess / weightSum : 0.5;
```

#### Step 3：综合评分

```javascript
return Math.min(1, overlapScore * 0.4 + successScore * 0.6);
```

### 2.3 难度估算（本地 fallback）

当 Hub 不提供 `complexity_score` 时，用信号数量和标题词数估算：

```javascript
function localDifficultyEstimate(task) {
  const signalFactor = Math.min(signals.length / 8, 1);    // 信号越多越难
  const titleWords = task.title.split(/\s+/).length;
  const titleFactor = Math.min(titleWords / 15, 1);
  return Math.min(1, signalFactor * 0.6 + titleFactor * 0.4);
}
```

### 2.4 承诺截止时间估算

基于难度级别映射到时间窗口：

| 难度阈值 | 时长 |
|---------|------|
| ≤ 0.3 | 15 min |
| ≤ 0.5 | 30 min |
| ≤ 0.7 | 60 min |
| ≤ 1.0 | 120 min |

边界约束：`[5 min, 24h]`。若 `task.expires_at` 更早，则提前 1 分钟（但不低于 5min）。

### 2.5 任务评分（composite + factors）

```javascript
function scoreTask(task, capabilityMatch) {
  const bountyBoost = Math.log1p(bountyValue) / 10;  // 对数增长，避免 bounty 爆炸
  const completionBoost = (task.already_completed_by || 0) * 0.02;
  const roiScore = roi(capabilityMatch, difficulty, bountyValue);

  const weights = STRATEGY_WEIGHTS[TASK_STRATEGY];
  return {
    composite:
      roiScore * weights.roi +
      capabilityMatch * weights.capability +
      completionBoost * weights.completion +
      bountyBoost * weights.bounty,
    factors: { roiScore, capabilityMatch, completionBoost, bountyBoost }
  };
}
```

三种策略权重（`greedy / balanced / conservative`）决定 composite 分数的侧重。

### 2.6 BlueCortexCE 借鉴

| 方面 | Evolver 做法 | CE 可借鉴 |
|------|-------------|-----------|
| **Session-observation 匹配** | signal history → capability | 当新 session 的 signals 与历史相似时，推荐相同/相似的 observation 标签 |
| **成功率加权** | Laplace 平滑 + Jaccard 相似度 | 搜索结果排序可考虑"该类型查询的历史成功率" |
| **难度估算** | signal count / title words | 新 session 可用"工具调用数量 + prompt 长度"估算复杂度 |
| **冷启动** | 无历史时默认 0.5 | CE 新会话无历史时，默认匹配度 0.5 |
| **信号扩展** | Jaccard + 0.15 阈值过滤 | 语义搜索时用向量相似度阈值过滤低相关结果 |

---

## 3. 综合：两子系统对 CE 的启发

### 3.1 envFingerprint × CE

```
Evolver Asset (Capsule) = 内容 + env_fingerprint
BlueCortex Observation  = 内容 + [无 env 字段]  ← 差距
```

**建议**：在 `ObservationEntity` 中可选地存储 `runtime_env` JSON 字段：

```json
{
  "observation_id": "obs_xxx",
  "content": "...",
  "observation_type": "error_fix",
  "runtime_env": {
    "platform": "darwin",
    "arch": "arm64",
    "node_version": "v22.x",
    "container": false
  }
}
```

用途：
- **根因分析**：当某类错误集中发生在特定平台时快速发现
- **上下文丰富**：generateContext 时可选注入当前运行环境
- **Session 聚类**：相似环境 + 相似信号的 session 可视为"同类任务"

### 3.2 capabilityMatch × CE

Evolver 的 capabilityMatch 实质上是一个**基于历史 outcome 的贝叶斯估计**：

```
P(任务成功 | 信号历史) ≈ (成功次数 + 1) / (总次数 + 2)  ← Laplace
```

这对 BlueCortexCE 的**搜索排序**和**上下文推荐**有直接借鉴价值：
- 当用户发起新查询时，根据其 signals 与历史 session 的 Jaccard 相似度，
  从高可信度历史 observations 中优先召回
- 避免冷启动时全部召回导致质量下降

---

## 附录：关键代码位置

| 功能 | 文件 | 行号范围 |
|------|------|---------|
| `captureEnvFingerprint` | `src/gep/envFingerprint.js` | ~15–75 |
| `envFingerprintKey` | `src/gep/envFingerprint.js` | ~78–88 |
| `isSameEnvClass` | `src/gep/envFingerprint.js` | ~91–92 |
| `estimateCapabilityMatch` | `src/gep/taskReceiver.js` | ~120–200 |
| `localDifficultyEstimate` | `src/gep/taskReceiver.js` | ~205–215 |
| `estimateCommitmentDeadline` | `src/gep/taskReceiver.js` | ~220–265 |
| `scoreTask` + `STRATEGY_WEIGHTS` | `src/gep/taskReceiver.js` | ~270–340 |
