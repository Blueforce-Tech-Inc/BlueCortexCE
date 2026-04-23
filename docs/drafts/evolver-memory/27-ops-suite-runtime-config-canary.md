# Evolver 运行时基础设施：Ops 模块套件、集中配置与 Canary 安全网

> **数据来源**：`src/ops/`（6 个子模块）、`src/config.js`（集中配置）、`src/canary.js`（冒烟测试）。
> **最后更新**：2026-04-23
> **前置阅读**：[26 运行时编排](./26-runtime-orchestration-adaptive-policy-candidates.md)（自适应策略、候选评估）、[23 State+Event 双层](./23-evolver-state-event-dual-layer-and-self-awareness-loop.md)（自省循环）。

---

## 1. 架构定位

Evolver 的 `src/ops/` 是一个**非 Feishu 依赖**的可移植运维层，与 `src/gep/`（进化核心逻辑）严格分离：

```
src/
├── evolve.js          # 主循环（调用 gep/* + ops/*）
├── config.js          # ★ 集中配置（所有阈值、超时）
├── canary.js          # ★ 冒烟测试安全网
├── gep/               # 进化核心（memoryGraph, signals, selector, ...）
└── ops/               # 运维层（独立于 Feishu/业务逻辑）
    ├── index.js       # 统一导出
    ├── lifecycle.js   # 进程生命周期（start/stop/restart/health）
    ├── skills_monitor.js  # 技能健康检查 + 自动修复
    ├── cleanup.js     # GEP 工件清理（两阶段）
    ├── trigger.js     # 跨进程唤醒信号
    ├── self_repair.js # Git 自修复
    ├── health_check.js    # 系统资源健康检查
    ├── commentary.js  # （略）
    └── innovation.js  # 创新催化（已在 25/26 文档覆盖）
```

**设计原则**：
- **零外部依赖**：ops 模块不 import Feishu SDK、不调用外部 API
- **故障隔离**：ops 故障不影响进化核心逻辑
- **可观测性**：每个模块返回结构化 JSON，便于日志聚合

---

## 2. 集中配置模式 (`config.js`)

### 2.1 设计思想

所有运行时阈值集中在一个文件，支持环境变量覆盖：

```javascript
// 辅助函数：类型安全的 env 解析
function envInt(key, fallback) {
  const v = process.env[key];
  if (v === undefined || v === '') return fallback;
  const n = parseInt(v, 10);
  return isNaN(n) ? fallback : n;
}
// envFloat, envStr 同理
```

### 2.2 配置分组

| 分组 | 关键配置 | 默认值 | 用途 |
|------|---------|--------|------|
| **Network** | `HELLO_TIMEOUT_MS` | 15000 | A2A 握手超时 |
| | `HEARTBEAT_INTERVAL_MS` | 360000 (6min) | 心跳间隔 |
| | `HTTP_TRANSPORT_TIMEOUT_MS` | 15000 | HTTP 传输超时 |
| **Solidify** | `VALIDATION_TIMEOUT_MS` | 180000 (3min) | 验证超时 |
| | `CANARY_TIMEOUT_MS` | 30000 | Canary 超时 |
| | `CAPSULE_CONTENT_MAX_CHARS` | 8000 | Capsule 内容上限 |
| | `MIN_PUBLISH_SCORE` | 0.78 | 发布最低分数 |
| | `BROADCAST_SCORE_THRESHOLD` | 0.7 | 广播阈值 |
| **Evolution** | `REPAIR_LOOP_THRESHOLD` | 3 | 修复循环检测阈值 |
| | `MEMORY_FRAGMENT_MAX_CHARS` | 50000 | 记忆片段上限 |
| | `PROMPT_MAX_CHARS` | 24000 | 提示词上限 |
| | `MEMORY_GRAPH_READ_LIMIT` | 1000 | 记忆图读取上限 |
| | `NARRATIVE_SUMMARY_MAX_CHARS` | 3000 | 叙事摘要截断 |
| **Ops** | `MAX_SILENCE_MS` | 1800000 (30min) | 静默检测阈值 |
| | `CLEANUP_MAX_AGE_MS` | 86400000 (24h) | 清理最大年龄 |
| | `LOCK_MAX_AGE_MS` | 600000 (10min) | Git lock 最大年龄 |
| **Security** | `LEAK_CHECK_MODE` | `warn` | 泄露检测模式 |

### 2.3 BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译 |
|-------------|---------|
| `envInt/envFloat/envStr` 辅助函数 | CE `application.properties` + `@Value` 注入已实现类似效果 |
| 集中配置文件 | CE 可考虑将分散在 Service 中的魔法数字提取到 `CortexConfig.java` |
| 全配置 env override | CE `application.properties` 支持 `${ENV_VAR:default}` 语法 |
| 配置分组注释 | CE 可按 domain 分组（Network / Solidify / Evolution / Ops） |

**具体落点**：
- `VALIDATION_TIMEOUT_MS`（180s）→ CE `@Async` 任务超时配置
- `MEMORY_FRAGMENT_MAX_CHARS`（50000）→ CE `generateContext` 的 token 预算
- `MIN_PUBLISH_SCORE`（0.78）→ CE 观察质量阈值
- `LEAK_CHECK_MODE` → CE 敏感数据检测开关

---

## 3. Canary 安全网 (`canary.js`)

### 3.1 实现

```javascript
// 极简设计：在子进程中尝试 require 主模块
try {
  require('../index.js');
  process.exit(0);   // 安全
} catch (e) {
  process.stderr.write(String(e.message || e).slice(0, 500));
  process.exit(1);    // 不安全
}
```

### 3.2 在进化流程中的位置

```
进化周期 → 验证（validation） → Canary 冒烟测试 → Solidify 提交
                                  ↓
                            子进程加载 index.js
                            ↓
                        exit 0 → 继续
                        exit 1 → ROLLBACK + FAILED
```

**关键设计**：
- **子进程隔离**：Canary 在 fork 的子进程中运行，崩溃不影响主进程
- **超时保护**：`CANARY_TIMEOUT_MS`（默认 30s）防止死锁
- **全局冒烟**：不同于 Gene 验证（局部检查），Canary 测试**整个系统能否加载**
- **PRM 评分影响**：Canary 失败 → `canary_health = 0`（权重 0.05）

### 3.3 失败分类

```javascript
// solidify.js 中的决策逻辑
if (canary && !canary.ok && !canary.skipped) {
  return { mode: 'hard', reasonClass: 'canary', retryable: false };
}
// → 不可重试的硬失败，直接 ROLLBACK
```

### 3.4 BlueCortexCE 借鉴

| Evolver 模式 | CE 翻译 |
|-------------|---------|
| Canary 子进程冒烟 | CE 写入后 `POST /api/health` 检查服务是否正常 |
| Canary 超时保护 | CE health check 加 timeout（5s 足够） |
| Canary 影响 PRM | CE 可将 health check 结果纳入观察质量评分 |
| 全局加载测试 | CE 可在 `mvn compile` 后运行 `java -jar --validate` |

**CE 实施建议**：
```bash
# 写入后冒烟测试（类比 Canary）
curl -sf http://127.0.0.1:37777/api/health --max-time 5 || {
  echo "Canary failed: service unhealthy after write"
  # 触发回滚或告警
}
```

---

## 4. Ops 模块套件详解

### 4.1 Lifecycle Manager (`lifecycle.js`)

**职责**：Evolver 进程的 start / stop / restart / status / health check

**关键机制**：

```javascript
// 进程发现：通过 ps 命令查找运行中的 evolver loop
function getRunningPids() {
  // 过滤条件：node + index.js + --loop + (feishu-evolver-wrapper | skills/evolver)
  // 去重 + isPidRunning 验证
}

// 健康检查：文件静默检测
function checkHealth() {
  // 1. 进程是否存在
  // 2. 日志文件最后修改时间是否超过 MAX_SILENCE_MS (30min)
  // → stagnation 意味着进化循环卡死
}
```

**守护进程模式**：
- `spawn('node', [script, '--loop'], { detached: true })` — 脱离父进程
- PID 文件：`memory/evolver_loop.pid`
- 日志追加：`fs.openSync(LOG_FILE, 'a')`

**BlueCortexCE 借鉴**：
| 概念 | CE 翻译 |
|------|---------|
| PID 文件管理 | CE 使用 systemd / launchd 管理 Java 进程 |
| 日志静默检测 | CE cron 巡检已实现（检查 `/api/health` 最后响应时间） |
| 守护进程 detached 模式 | CE `nohup java -jar` 或 Docker 容器 |

### 4.2 Skills Monitor (`skills_monitor.js`)

**职责**：扫描已安装技能，检测并自动修复常见问题

**检测项**：
1. `Missing node_modules` → 自动 `npm install --production`
2. `Empty node_modules` → 同上
3. `Invalid package.json` → 报告（不可自动修复）
4. `Missing SKILL.md` → 自动创建 stub

**自动修复流程**：
```javascript
function autoHeal(skillName, issues) {
  // 1. 缺少 node_modules → npm install (timeout: 60s)
  // 2. 缺少 SKILL.md → 创建最小 stub
  // 返回已修复的 issue 列表
}
```

**忽略列表机制**：
- 硬编码：`common`, `clawhub`, `input-validator`, `proactive-agent`, `security-audit`
- 用户自定义：`.skill_monitor_ignore` 文件（每行一个 skill 名）

**性能优化**：
- 不做 `node -c` 语法检查（太慢），信任运行时加载
- `node_modules` 存在即视为 OK（不做深度检查）

**BlueCortexCE 借鉴**：
| 概念 | CE 翻译 |
|------|---------|
| 技能健康扫描 | CE 不需要（无技能概念），但"依赖检查"思路可借鉴 |
| 自动修复（npm install） | CE `mvn dependency:resolve` 自动修复依赖 |
| 忽略列表 | CE 可在 health check 中排除已知无害的告警 |
| SKILL.md stub 生成 | CE 可在创建新模块时自动生成 README.md 模板 |

### 4.3 Cleanup (`cleanup.js`)

**职责**：清理旧的 GEP 工件文件（`gep_prompt_*.json/txt`）

**两阶段清理**：

```
Phase 1: 年龄清理
├── 保留最近 MIN_KEEP (10) 个文件
├── 超过 MAX_AGE_MS (24h) 的文件删除
└── 按 mtime 降序排序

Phase 2: 数量上限
├── 重新扫描剩余文件
├── 超过 MAX_FILES (10) 的文件删除
└── 双重保险：年龄 + 数量
```

**安全设计**：
- `safeBatchDelete()`：逐个 try/catch，单个失败不影响整体
- 先排序后删除，确保保留最新文件
- Phase 1 和 Phase 2 独立执行

**BlueCortexCE 借鉴**：
| 概念 | CE 翻译 |
|------|---------|
| 两阶段清理 | CE 日志轮转：按时间 + 按大小 |
| MIN_KEEP 保底 | CE 清理策略：保留最近 N 天/条记录 |
| 安全批量删除 | CE 批量操作用事务保护 |

### 4.4 Trigger (`trigger.js`)

**职责**：跨进程唤醒信号（写信号文件 → wrapper 轮询检测）

```javascript
// 信号文件：memory/evolver_wake.signal
function send() { fs.writeFileSync(WAKE_FILE, 'WAKE'); }
function clear() { fs.unlinkSync(WAKE_FILE); }
function isPending() { return fs.existsSync(WAKE_FILE); }
```

**使用场景**：外部事件（如 Feishu 消息）需要立即触发进化循环，而非等待下一次定时轮询。

**BlueCortexCE 借鉴**：
| 概念 | CE 翻译 |
|------|---------|
| 信号文件唤醒 | CE 可用 JMS / Redis Pub-Sub 替代（更可靠） |
| 文件级 IPC | CE 用 HTTP webhook 更标准 |

### 4.5 Health Check (`health_check.js`)

**多维度系统健康检查**：

| 检查项 | 阈值 | 严重级别 |
|--------|------|---------|
| `FEISHU_APP_ID` 缺失 | - | warning（非 critical，防止重启循环） |
| `FEISHU_APP_SECRET` 缺失 | - | warning |
| `CLAWHUB_TOKEN` 缺失 | - | info |
| 磁盘使用率 > 90% | 90% | critical |
| 磁盘使用率 > 80% | 80% | warning |
| 内存使用率 > 95% | 95% | critical |
| 进程数 > 2000（仅 Linux） | 2000 | warning |

**返回结构**：
```json
{
  "status": "ok|warning|error",
  "timestamp": "2026-04-23T...",
  "checks": [
    { "name": "env:FEISHU_APP_ID", "ok": true, "status": "present" },
    { "name": "disk_space", "ok": true, "status": "65% used" },
    ...
  ]
}
```

**设计亮点**：
- Secret 缺失降级为 warning（防止重启循环：缺 secret → 健康检查失败 → 重启 → 仍缺 → 无限循环）
- 进程数检查带 60s 缓存（`readdirSync('/proc')` 开销大）
- `statfsSync` 优先，`df` 命令回退（跨平台兼容）

**BlueCortexCE 借鉴**：
| 概念 | CE 翻译 |
|------|---------|
| 多维健康检查 | CE `/api/health` 已返回 DB 连接状态，可扩展 |
| Secret 缺失降级 | CE 可将非关键配置缺失标记为 warning 而非 error |
| 进程数监控 | CE JVM 线程数监控（`ThreadMXBean`） |
| 磁盘/内存检查 | CE 可通过 JMX 暴露系统指标 |

---

## 5. 整体设计模式总结

### 5.1 分层架构

```
┌─────────────────────────────────────────────┐
│                  evolve.js                   │  ← 主循环
├─────────────────────────────────────────────┤
│           src/gep/ (进化核心)                │  ← 业务逻辑
│  memoryGraph, signals, selector, mutation,  │
│  solidify, reflection, prompt, ...          │
├─────────────────────────────────────────────┤
│           src/ops/ (运维层)                  │  ← 基础设施
│  lifecycle, skills_monitor, cleanup,        │
│  trigger, self_repair, health_check,        │
│  innovation                                 │
├─────────────────────────────────────────────┤
│           src/config.js (配置层)             │  ← 横切关注点
│  集中阈值 + env override                     │
└─────────────────────────────────────────────┘
```

### 5.2 关键设计模式

| 模式 | 实现 | CE 可借鉴度 |
|------|------|-----------|
| **集中配置 + env override** | `config.js` | ⭐⭐ CE 已有 `@Value`，可增加配置分组 |
| **Canary 冒烟测试** | `canary.js` | ⭐⭐⭐ CE 写入后 health check |
| **自动修复** | `skills_monitor.js` | ⭐⭐ CE 依赖自动修复 |
| **两阶段清理** | `cleanup.js` | ⭐⭐ CE 日志/工件清理 |
| **信号文件 IPC** | `trigger.js` | ⭐ CE 用 HTTP/消息队列更标准 |
| **多维健康检查** | `health_check.js` | ⭐⭐⭐ CE `/api/health` 扩展 |
| **进程生命周期管理** | `lifecycle.js` | ⭐⭐ CE 用 systemd/Docker |

### 5.3 BlueCortexCE 综合借鉴建议

**P0（立即实施）**：
1. Canary 模式 → 写入后 `/api/health` 冒烟测试
2. 多维健康检查 → 扩展 `/api/health` 返回 DB/磁盘/内存状态

**P1（短期）**：
3. 集中配置 → 提取 `ContextService` 中的魔法数字到配置类
4. 两阶段清理 → 实现旧观察记录的自动清理策略

**P2（长期）**：
5. 自动修复 → 依赖问题自动检测与修复
6. 进程管理 → 更完善的进程生命周期管理

---

## 6. 与现有文档的关系

| 本文档 | 与现有文档的区别 |
|--------|----------------|
| [26 运行时编排](./26-runtime-orchestration-adaptive-policy-candidates.md) | 26 聚焦**自适应策略和候选评估**（业务逻辑），本文档聚焦**基础设施层**（ops/config/canary） |
| [25 高级模式](./25-advanced-patterns-prm-epigenetic-antipattern.md) | 25 聚焦**PRM 评分、Anti-Pattern、Prompt 工程**，本文档补充 ops 模块的**实现细节** |
| [01–08 分片](./index.md) | 01–08 按时间线覆盖各版本功能，本文档做**横切面专题分析** |
