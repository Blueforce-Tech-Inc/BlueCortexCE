# 98 — v1.78.9 Minor Subsystem Additions 深度分析

**目标**：分析 v1.78.9 新增的四个小型、干净的子系统模块。  
**数据来源**：`origin/main` v1.78.9 (`src/gep/featureFlags.js` · `src/proxy/extensions/dmHandler.js` · `src/proxy/extensions/skillUpdater.js` · `src/proxy/task/monitor.js`)  
**分析日期**：2026-05-05

---

## 1. `featureFlags.js` — Feature Flag 持久化层（114 行）

### 1.1 核心设计：三层覆盖语义

```
优先级（高→低）：
  1. LOCAL_FLAGS_FILE   ← 项目本地（优先级最高，"local override"）
  2. FLAGS_FILE         ← 用户 home 目录 ~/.evomap/feature_flags.json
  3. Code default       ← 调用方自行 fallback
```

**文件 Schema**：
```json
{
  "<flag_key>": {
    "value": any,
    "source": "hub_mailbox" | "manual" | "unknown",
    "updatedAt": "2026-05-05T05:00:00.000Z"
  }
}
```

**读路径**（懒加载 + 单次缓存）：
```js
let _cache = null;          // 模块级缓存
let _cacheLoaded = false;   // 只从磁盘读一次

function _loadFromDisk() {
  if (_cacheLoaded) return _cache;
  _cacheLoaded = true;
  _cache = _readFile(FLAGS_FILE) || _readFile(LOCAL_FLAGS_FILE) || {};
  return _cache;
}
```

**写路径**（先尝试 home 目录，失败 fallback 到本地文件）：
```js
function _writeToDisk(obj) {
  try {
    fs.mkdirSync(FLAGS_DIR, { recursive: true, mode: 0o700 });
    fs.writeFileSync(FLAGS_FILE, JSON.stringify(obj, null, 2), { mode: 0o600 });
    return true;
  } catch (_) {}
  // fallback: 本地项目目录
  fs.writeFileSync(LOCAL_FLAGS_FILE, JSON.stringify(obj, null, 2), { mode: 0o600 });
}
```

### 1.2 公开 API

| API | 签名 | 说明 |
|-----|------|------|
| `readFeatureFlag(key)` | `string → any\|undefined` | 读单个 flag，未定义返回 undefined |
| `writeFeatureFlag(key, value, source?)` | `(string, any, string?) → boolean` | 写 flag，自动注 `updatedAt` 和 `source` |
| `getAllFeatureFlags()` | `() → object` | 诊断用，返回深拷贝 |

### 1.3 安全与健壮性

- **文件权限**：`0o600`（文件）/ `0o700`（目录）——防止其他用户读取 flag 值
- **懒初始化**：FLAG_DIR 不存在时才创建
- **容错**：所有 fs 操作包在 try/catch 中，静默失败
- **Schema 校验**：读时检查 `entry && typeof entry === 'object'`，非对象视为未定义
- **空值保护**：`!key` 或非 string key 直接返回 undefined / false

### 1.4 ⚠️ 注释与实现的偏差

文件头注释声称"Local env (highest priority)"暗示支持 `process.env` 覆盖，但**实际代码并未检查任何环境变量**。这里的 "Local" 实际指 `LOCAL_FLAGS_FILE`（项目本地文件），是"本地文件覆盖"而非"环境变量覆盖"。

### 1.5 BlueCortexCE 借鉴价值

**P2（可选）**：`MemoryConfig.java` 可参考此三层模式：
1. **环境变量**（用户逃逸门）——最高优先级
2. **本地配置文件**（项目级覆盖）
3. **class 默认值**（代码默认）

当前 CE 的 `application.properties` + env override 实际上已经部分实现了这一点，但 flag 粒度的动态覆盖值得参考。

---

## 2. `dmHandler.js` — Direct Message 处理器（45 行）

### 2.1 设计：极简 Store Wrapper

```js
class DmHandler {
  send({ recipientNodeId, content, metadata })   // 发 DM
  poll({ limit })                               // 拉取收到的 DM
  ack(messageIds)                               // 确认已读
  list({ limit, offset })                       // 列表（支持分页）
}
```

底层委托给 `this.store`（即 `MailboxStore`），无任何自有状态，无缓存，无重试，无熔断。

### 2.2 特点

- **payload 结构**：`{ recipient_node_id, content, metadata, sent_at }`
- **priority 字段**：固定为 `'normal'`（可在调用处 override）
- **无验证返回**：send/ack/poll/list 均直接透传 store 返回值

### 2.3 BlueCortexCE 借鉴价值

**P3（装饰性参考）**：CE 目前无 DM 功能；如果未来需要 Peer-to-Peer 观察共享，可以参考此简单接口设计。

---

## 3. `skillUpdater.js` — 远程 Skill 更新处理器（64 行）

### 3.1 核心流程

```js
processSkillUpdate(message) {
  1. 检查 this.skillPath 是否配置 ← 跳过如未配置
  2. 提取 payload.content || payload.skill_content
  3. 确保目录存在 fs.mkdirSync(..., { recursive: true })
  4. 备份旧文件 this.skillPath → this.skillPath + '.bak'
  5. 写入新内容 fs.writeFileSync(..., 'utf8')
  6. 更新 store 状态 last_skill_update + skill_version
}

pollAndApply() {
  1. this.store.poll({ type: 'skill_update' })
  2. 对每条消息调用 processSkillUpdate
  3. ack 已处理的消息
  4. 返回 applied 计数
}
```

### 3.2 关键设计决策

| 决策 | 实现 |
|------|------|
| 原子写 | 无原子写（直接 writeFileSync） |
| 备份 | 保留 `.bak` 后缀备份（单份，不旋转） |
| 版本记录 | `last_skill_update` 时间戳 + `skill_version` 字符串 |
| 静默失败 | 未配置 path 时 warn 后返回 false，不抛异常 |
| 轮询模式 | `pollAndApply()` 需要被显式调用（非定时器） |

### 3.3 BlueCortexCE 借鉴价值

**P3（架构参考）**：CE 的 SKILL.md 管理（如 `skills_monitor.js` 自愈）未来可参考：
- 备份策略：写前备份 `.bak`
- 版本追踪：写入时间戳和版本号到 store 状态

---

## 4. `taskMonitor.js` — Task 统计与订阅监控（131 行）

### 4.1 核心数据结构

```js
this._stats = {
  tasks_received: 0,
  tasks_claimed: 0,
  tasks_completed: 0,
  tasks_failed: 0,
  last_claim_at: null,        // Unix ms
  last_complete_at: null,      // Unix ms
  avg_completion_ms: 0,
  _completion_times: [],        // 环形缓冲区，最多 100 条
};
```

### 4.2 关键设计

**环形缓冲区**（有界内存，防止内存泄漏）：
```js
this._stats._completion_times.push(duration);
if (this._stats._completion_times.length > 100) {
  this._stats._completion_times.shift();  // O(n) 裁剪，可优化为索引循环
}
```

**滑动平均**（每完成一次任务重新计算）：
```js
const sum = this._stats._completion_times.reduce((a, b) => a + b, 0);
this._stats.avg_completion_ms = Math.round(sum / this._stats._completion_times.length);
```

**订阅模型**（通过 Store 状态持久化 + 向 Hub 发消息）：
```js
subscribe(filters = []) {
  this.store.setState('task_subscription', JSON.stringify({
    enabled: true, filters,
    subscribed_at: new Date().toISOString(),
  }));
  return this.store.send({ type: 'task_subscribe', payload: { capability_filter: filters } });
}
```

**心跳元数据**（上报给 Hub）：
```js
getHeartbeatMeta() {
  return {
    task_subscription: this.subscribed,
    task_metrics: {
      pending: this.store.countPending({ direction: 'inbound' }),
      claimed: this._stats.tasks_claimed,
      completed: this._stats.tasks_completed,
      failed: this._stats.tasks_failed,
      avg_completion_ms: this._stats.avg_completion_ms,
    },
  };
}
```

**状态恢复**（重启后从 Store 恢复统计）：
```js
_restoreStats() {
  const raw = this.store.getState('task_monitor_stats');
  if (!raw) return;
  const saved = typeof raw === 'string' ? JSON.parse(raw) : raw;
  // ... 逐字段 restore，带 null 检查
}
```

### 4.3 BlueCortexCE 借鉴价值

**P1（推荐参考）**：
1. **有界环形缓冲区**：ObservationRepository 的 `_completion_times` 类似模式，防止统计数据结构无限增长
2. **心跳元数据**：`getHeartbeatMeta()` 提供了一个"可观测性快照"的设计范式——每次心跳上报聚合指标而非原始数据
3. **订阅模型 + 状态持久化**：`subscribe()` 同时写本地状态 + 通知 Hub，是"本地优先 + 远程同步"的好例子

---

## 5. 综合 CE 行动项

| 模块 | 优先级 | 行动项 |
|------|--------|--------|
| `taskMonitor.js` | **P1** | `getHeartbeatMeta()` 模式 → CE 观察统计心跳上报机制 |
| `taskMonitor.js` | **P1** | 环形缓冲区（有界） → CE 统计集合上限 |
| `featureFlags.js` | P2 | `MemoryConfig.java` 三层覆盖（env > 本地文件 > class default） |
| `featureFlags.js` | P3 | 修正注释偏差（"Local env" 实际指本地文件，非 process.env） |
| `dmHandler.js` | P3 | 装饰性参考——未来 P2P 观察共享接口设计 |
| `skillUpdater.js` | P3 | 备份策略 / 版本追踪 → CE 未来 SKILL.md 管理 |

---

## 6. 与现有 docs 的关系

| 已有覆盖 | 本文新增 |
|----------|---------|
| Doc 78: Proxy 子系统（MailboxStore / Sync / Routes） | 新增 `dmHandler` / `skillUpdater` / `taskMonitor` 三个 extension 深度 |
| Doc 80: `config.js` 集中配置 | `featureFlags.js` 提供另一种 flag 粒度的动态配置持久化 |
| Doc 88: taskReceiver Worker Pool | `taskMonitor` 在 Proxy 层提供任务级统计和订阅管理 |
