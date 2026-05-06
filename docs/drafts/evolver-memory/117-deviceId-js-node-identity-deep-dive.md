# `deviceId.js` — 稳定设备标识符（209行）

**文件**: `src/gep/deviceId.js`（v1.47.0 本地源码）  
**分析**: PM Agent @ 2026-05-06  
**定位**: Evolver 节点身份识别基础设施

---

## 1. 核心职责

`deviceId.js` 为 Evolver 生成**跨重启/迁移/升级稳定的设备标识符**，用于：
- `getNodeId()` — 节点唯一身份
- `env_fingerprint` — 环境指纹（用于 Hub 协作去重）

---

## 2. 优先级链（7层降级）

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | `EVOMAP_DEVICE_ID` 环境变量 | 显式覆盖（容器环境推荐） |
| 2 | `~/.evomap/device_id` 文件 | 主持久化路径 |
| 3 | `<project>/.evomap_device_id` 文件 | 容器 fallback（项目目录挂载卷） |
| 4 | `/etc/machine-id` (Linux) | OS 级安装标识 |
| 5 | `IOPlatformUUID` (macOS) | 硬件级 UUID |
| 6 | Docker/OCI 容器 ID | `/proc/self/cgroup` 或 `/proc/self/mountinfo` |
| 7 | `hostname + MAC` | 网络接口 fallback |
| 8 | `random 128-bit hex` | 最后兜底 |

---

## 3. 容器检测（4种方法）

```javascript
function isContainer() {
  // 1. /.dockerenv
  // 2. /proc/1/cgroup 含 docker/kubepods/containerd/lxc/ecs
  // 3. /run/.containerenv (containerd)
  // 4. hostname 匹配 12-64 hex（Docker 默认）
}
```

---

## 4. ID 生成算法

```javascript
function generateDeviceId() {
  // 优先 /etc/machine-id（Linux）或 IOPlatformUUID（macOS）
  if (machineId) return sha256('evomap:' + machineId).slice(0, 32);
  // 其次 Docker 容器 ID（64-char hex，来自 cgroup/mountinfo）
  if (containerId) return sha256('evomap:container:' + containerId).slice(0, 32);
  // 网络接口 MAC（排序后去重）
  if (macs.length > 0) return sha256('evomap:' + hostname + '|' + macs.join(','));
  // 最后兜底随机
  return randomBytes(16).toString('hex');
}
```

关键设计：
- 所有 ID 都经过 SHA-256 并截断到 32 字符（保护原始硬件标识符隐私）
- `evomap:` 前缀防止哈希碰撞
- `evomap:container:` 前缀区分容器 vs 物理机

---

## 5. 持久化策略（双路径保活）

```
优先: ~/.evomap/device_id         (0o600 权限)
兜底: <project>/.evomap_device_id  (容器环境 $HOME 可能是临时的)
```

关键设计点：
- 目录创建 `0o700`（仅 owner 可读）
- 文件写入 `0o600`（仅 owner 可读写）
- 两路径均失败时仅打印 WARN 不崩溃

---

## 6. 缓存机制（进程级）

```javascript
let _cachedDeviceId = null;
function getDeviceId() {
  if (_cachedDeviceId) return _cachedDeviceId;  // 无锁进程内缓存
  // ... 生成逻辑
  _cachedDeviceId = generated;
  return _cachedDeviceId;
}
```

---

## 7. 容器特殊处理

容器内运行时（无 `EVOMAP_DEVICE_ID`）生成 ID 时会打印 NOTE：
> "running in a container without EVOMAP_DEVICE_ID... set EVOMAP_DEVICE_ID as an env var or mount a persistent volume at ~/.evomap/"

---

## 8. BlueCortexCE 借鉴

| 方面 | Evolver | CE 现状 | 建议 |
|------|---------|---------|------|
| 节点身份 | 7层降级 + 持久化 | 无 | P2: 实现 `NodeIdService` 多层 fallback |
| 容器支持 | EVOMAP_DEVICE_ID 环境变量 | 无 | P2: 支持 `CLAUDE_MEM_NODE_ID` env var |
| 隐私保护 | SHA-256 哈希原始标识符 | 无 | P1: 节点指纹不应存原始值 |
| 持久化 | ~/.evomap/ + 项目目录双路径 | 无 | P2: 支持容器 volume mount |
| 权限安全 | 0o600 / 0o700 | 无 | P1: 敏感文件权限控制 |

**具体 CE 提案**：

```java
// NodeIdService.java — 伪代码
public String getNodeId() {
  // 1. ENV var override
  if (envVarNotEmpty("CLAUDE_MEM_NODE_ID")) return envVar;
  // 2. Persisted file (~/.claude-mem/node_id)
  if (persistedFileExists()) return persistedId;
  // 3. Generate from machine-id / container-id
  String generated = sha256("claude-mem:" + hardwareId).substring(0, 32);
  persistToFile(generated);
  return generated;
}
```

---

## 9. 安全与隐私分析

**优点**：
- 原始硬件标识符（MAC、IOPlatformUUID）从不暴露，只存 SHA-256 哈希
- 容器 ID 也是哈希后才存储
- 权限严格（0o600/0o700）防止其他用户读取

**CE 可改进**：
- 目前无撤销机制（EVOMAP_DEVICE_ID 一旦设置就永久有效）
- 容器重建后 ID 变化（预期行为，但需用户知晓）

---

**关联文档**：
- Doc 101 §4: 核心架构模式（节点身份部分）
- Doc 78: Proxy LifecycleManager（envFingerprint 部分）
- Doc 96: forceUpdate.js Hub 心跳驱动版本迁移
