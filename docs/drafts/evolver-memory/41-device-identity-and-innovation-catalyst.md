# 41 Device Identity + Innovation Catalyst

**目标**：分析 EvoMap/evolver 中 `deviceId.js`（设备标识系统）和 `innovation.js`（创新催化）的设计机制，提炼对 BlueCortexCE 的借鉴思路。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/gep/deviceId.js`、`/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/innovation.js`

**文件关系**：`deviceId.js` 是 `envFingerprint.js` 的基础设施（`envFingerprint.js` 的 `captureEnvFingerprint()` 调用 `getDeviceId()`）；`innovation.js` 是 `ops/` 套件之一。

**最后更新**：2026-04-24

---

## 1. deviceId.js：设备标识系统

### 1.1 职责定位

`deviceId.js` 的核心职责是**跨重启、跨升级、跨目录迁移的稳定设备标识**。它不是"随机 UUID"，而是一个**硬件指纹 + 持久化**的混合系统。

### 1.2 优先级链（7 层 fallback）

```
1. EVOMAP_DEVICE_ID env var         ← 显式覆盖（容器环境推荐）
2. ~/.evomap/device_id file         ← 主持久化路径（$HOME 存在时）
3. <project>/.evomap_device_id      ← 容器 fallback（$HOME 临时时）
4. /etc/machine-id                 ← Linux 系统级标识
5. IOPlatformUUID                  ← macOS 硬件 UUID
6. Docker/OCI container ID         ← 容器生命周期内稳定
7. hostname + MAC addresses        ← 网络接口 fallback
8. crypto.randomBytes(16)           ← 最终兜底（立即持久化）
```

**设计原则**：
- 每一层失败则自动回退到下一层，**永不崩溃**
- 每层都产出一个有效的 16–64 字符 hex 字符串
- 标识符最终被 SHA-256 哈希化（不直接暴露硬件信息）

### 1.3 容器检测（`isContainer()`）

```javascript
function isContainer() {
  // 1. /.dockerenv 文件
  if (fs.existsSync('/.dockerenv')) return true;

  // 2. /proc/self/cgroup 中检查 docker/kubepods/containerd/cri-o/lxc/ecs
  const cgroup = fs.readFileSync('/proc/self/cgroup', 'utf8');
  if (/docker|kubepods|containerd|cri-o|lxc|ecs/i.test(cgroup)) return true;

  // 3. /run/.containerenv (containerd OCI)
  if (fs.existsSync('/run/.containerenv')) return true;

  return false;
}
```

**支持场景**：Docker、Kubernetes、ECS、LXC、containerd OCI 等容器环境。

### 1.4 容器 ID 读取（3 种方法）

```javascript
// Method 1: /proc/self/cgroup (cgroup v1 + Docker)
// 匹配 64-char hex container ID

// Method 2: /proc/self/mountinfo (cgroup v2 / containerd)
// 同样匹配 64-char hex

// Method 3: hostname（Docker 默认以短 container ID 为 hostname）
// 如果 hostname 是 12–64 字符 hex，直接使用
```

### 1.5 持久化双路径策略

```javascript
// Primary: ~/.evomap/device_id（权限 0o600）
try {
  fs.mkdirSync(DEVICE_ID_DIR, { recursive: true, mode: 0o700 });
  fs.writeFileSync(DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });
} catch {}

// Fallback: <project>/.evomap_device_id
// 用于 $HOME 为临时卷的容器环境（项目目录挂载持久卷时）
try {
  fs.writeFileSync(LOCAL_DEVICE_ID_FILE, id, { encoding: 'utf8', mode: 0o600 });
} catch {}
```

**关键洞察**：这是一个**跨卷持久化**策略——主路径失败时用项目路径作为兜底，适合 Docker/Kubernetes 等 $HOME 易失的场景。

### 1.6 内存缓存（`_cachedDeviceId`）

```javascript
let _cachedDeviceId = null;

function getDeviceId() {
  if (_cachedDeviceId) return _cachedDeviceId;
  // ... 7层 fallback 生成 ...
  _cachedDeviceId = generated;
  return _cachedDeviceId;
}
```

**单进程单例模式**：进程生命周期内只计算一次，后续调用直接返回缓存值。

### 1.7 与 envFingerprint 的关系

`deviceId.js` 是 `envFingerprint.js` 的基础设施：

```javascript
// envFingerprint.js captureEnvFingerprint() 中：
device_id: getDeviceId(),  // ← 来自 deviceId.js

// envFingerprintKey(fp) 中：
const parts = [fp.device_id, fp.node_version, fp.platform, ...];
// device_id 是 envFingerprintKey 的核心组成部分
```

### 1.8 BlueCortexCE 借鉴

| Evolver 机制 | CE 翻译 | 优先级 |
|---|---|---|
| 7 层 fallback 设备标识 | CE 为多实例部署设计 `runtime_env.instance_id`：优先读取 `CORTEX_INSTANCE_ID` env → 读取 `~/.cortex/instance_id` → 生成并持久化 | P1 |
| 容器检测 + container ID | CE 部署在 K8s/Docker 时，自动捕获容器 ID 作为实例标识的一部分 | P2 |
| 双路径持久化（`~/.evomap/` + 项目路径） | CE 同样需要考虑 `~/.cortexce/` 和项目路径的双重持久化（k8s PVC vs emptyDir） | P1 |
| 硬件指纹（machine-id / IOPlatformUUID） | CE 不需要硬件指纹，但 `instance_id` 需要在无 env var 时能稳定生成 | P1 |
| 进程级缓存 `_cachedDeviceId` | CE 可在 Spring Bean 初始化时计算一次 instance_id，后续调用直接返回 | P0（低代码） |

**具体落点建议**：

```java
// BlueCortexCE: RuntimeEnv.java 或配置类
@Bean
public String instanceId() {
    // 1. env var 优先
    String env = System.getenv("CORTEX_INSTANCE_ID");
    if (env != null && env.matches("^[a-f0-9]{16,64}$")) {
        return env;
    }
    // 2. 持久化文件（~/.cortexce/instance_id）
    // 3. 生成 + 持久化
    // 4. 缓存（单例）
}
```

---

## 2. innovation.js：创新催化

### 2.1 职责定位

`innovation.js` 的职责是**在检测到系统停滞（stagnation）时，自动生成具体的创新改进建议**。它是 Evolver 自我进化的"创意来源"之一。

### 2.2 核心算法

```javascript
function generateInnovationIdeas() {
    // Step 1: 列举所有技能，按类别计数
    const categories = {
        'feishu': skills.filter(s => s.startsWith('feishu-')).length,
        'dev': skills.filter(s => s.startsWith('git-') || ...).length,
        'media': skills.filter(s => s.includes('image') || ...).length,
        'security': skills.filter(s => s.includes('security') || ...).length,
        'automation': skills.filter(s => s.includes('auto-') || ...).length,
        'data': skills.filter(s => s.includes('db') || ...).length
    };

    // Step 2: 找出最弱的两个类别（under-represented areas）
    const sortedCats = Object.entries(categories).sort((a, b) => a[1] - b[1]);
    const weakAreas = sortedCats.slice(0, 2).map(c => c[0]);

    // Step 3: 根据弱领域生成具体改进建议
    if (weakAreas.includes('security')) {
        ideas.push("- Security: Implement a 'dependency-scanner' skill...");
        ideas.push("- Security: Create a 'permission-auditor'...");
    }
    // ...

    return ideas.slice(0, 3);  // 最多返回 3 条
}
```

### 2.3 设计特点

**特点 1：基于弱领域驱动**（类似"木桶原理"）——不是随机产生创意，而是**优先补齐最短板**。

**特点 2：技能类别化**（6 大类：feishu、dev、media、security、automation、data）——通过命名约定（`startsWith`、`includes`）自动分类。

**特点 3：输出约束**（最多 3 条）——防止建议过载，保持可操作性。

**特点 4：条件触发**（`skills.length > 50` 时触发优化建议）——只有在技能库足够丰富时才提优化建议，避免早期过度优化。

### 2.4 BlueCortexCE 借鉴

| Evolver 机制 | CE 翻译 | 场景 |
|---|---|---|
| 弱领域驱动创意 | CE 可定期扫描"未实现的功能"（如 `EXTRACTION_ENABLED` 的完整覆盖、`mode` 策略的动态调整） | 功能发现 |
| 技能类别化 | CE 可对 `ObservationEntity.type` 分布做统计，找出从未出现过的类型（如 `capability_gap`） | 观察类型覆盖 |
| 优化建议（技能数量 > 50） | CE 可在"检索成功率持续低于阈值"时，触发检索策略优化建议 | 自优化 |
| 最多 3 条输出 | CE 的 cron 报告应限制建议数量，聚焦最重要的一条 | 报告精简 |

**注意**：`innovation.js` 本身是一个非常轻量的模块（~60 行），它依赖的"停滞检测"信号来自 Evolver 主循环的 `signal` 系统（见 doc 37）。它的价值在于**把弱领域检测变成了可执行的建议**。

---

## 3. 交叉分析

### 3.1 deviceId + envFingerprint → CE 多实例部署

Evolver 的设备标识 → 环境指纹 → 同类判断链条，对 BlueCortexCE 的多实例/K8s 部署有直接参考价值：

```
Evolver:
  deviceId.js (7层 fallback 设备 ID)
    ↓ getDeviceId()
  envFingerprint.js (device_id + node_version + platform + arch + hostname)
    ↓ envFingerprintKey()
  memoryGraph.js (同 EnvClass 边聚合)
    ↓ isSameEnvClass()
  selector.js (同类成功率加权)

BlueCortexCE 翻译:
  CORTEX_INSTANCE_ID env / 持久化 instance_id
    ↓ runtime_env.instance_id
  SearchService (按 instance_id 聚合检索结果)
    ↓ 类似 envFingerprintKey
  多实例部署时，同一 K8s Pod 内实例共享检索上下文
```

### 3.2 innovation + stagnation signal → CE 功能发现

Evolver 的创新催化依赖 `signal` 系统检测停滞（见 doc 37）。BlueCortexCE 如果要实现类似功能，需要：

1. **观察类型覆盖率监控**：统计 `ObservationEntity.type` 分布
2. **检索成功率时序分析**：连续 N 天检索成功率 < X% → 触发策略建议
3. **弱领域识别**：从未出现过的 `type` 值 → 功能缺失标记

---

## 4. 附录：模块文件位置

| 模块 | 路径 | 行数 | 复杂度 |
|------|------|------|--------|
| `deviceId.js` | `src/gep/deviceId.js` | ~180 | 中等（多层 fallback） |
| `innovation.js` | `src/ops/innovation.js` | ~60 | 低（简单的分类+建议生成） |
| `envFingerprint.js` | `src/gep/envFingerprint.js` | ~130 | 中等（与 deviceId 配合） |
| `memoryGraph.js` | `src/gep/memoryGraph.js` | ~1100 | 高（核心存储） |

**相关已有文档**：
- [`38-env-fingerprint-capability-match.md`](./38-env-fingerprint-capability-match.md) — envFingerprint 完整分析（含 `captureEnvFingerprint`、`envFingerprintKey`、`isSameEnvClass`）
- [`37-signal-taxonomy-gene-selection-end-to-end.md`](./37-signal-taxonomy-gene-selection-end-to-end.md) — stagnation signal 触发机制
- [`26-runtime-orchestration-adaptive-policy-candidates.md`](./26-runtime-orchestration-adaptive-policy-candidates.md) — 创新催化.runtime 编排部分
