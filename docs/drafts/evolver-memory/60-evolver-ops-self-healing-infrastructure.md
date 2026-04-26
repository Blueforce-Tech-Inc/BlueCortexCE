# 60 — Evolver Ops 自我修复基础设施深度分析

**目标**：分析 `src/ops/` 七大模块的协同机制，提炼"外部可触发 + 独立运行"的自愈基础设施设计。

**数据来源**：`/Users/yangjiefeng/Documents/EvoMap/evolver/src/ops/`

**最后更新**：2026-04-25

---

## §1 架构定位：外部可触发的 ops 层

### 1.1 模块概览

| 模块 | 文件 | 行数 | 职责 |
|------|------|------|------|
| `lifecycle` | `ops/lifecycle.js` | 181 | Daemon 进程管理（start/stop/restart/check） |
| `cleanup` | `ops/cleanup.js` | 80 | 旧 GEP artifacts 清理（MAX_AGE_MS + MAX_FILES 双策略） |
| `self_repair` | `ops/self_repair.js` | 72 | Git 异常自修复（rebase/merge abort、stale lock 删除、hard reset） |
| `skills_monitor` | `ops/skills_monitor.js` | 143 | 技能健康检查 + 自动修复（node_modules/SKILL.md） |
| `health_check` | `ops/health_check.js` | 113 | 系统级健康检查（disk/memory/process count/secrets） |
| `trigger` | `ops/trigger.js` | 33 | 即时唤醒信号文件（`evolver_wake.signal`） |
| `commentary` | `ops/commentary.js` | 60 | 人格化 cycle 评语生成（三种 persona） |

### 1.2 关键架构洞察

**大多数 ops 模块是"独立 CLI 脚本"而非内部服务**。它们通过 `node ops/<module>.js <action>` 直接从命令行调用，不在 `evolve.js` 主循环中 import。唯一例外是 `ops/innovation.js` 的 `generateInnovationIdeas()` 被 `gep/prompt.js` 直接引用（见 doc 58）。

**ops/index.js** 统一导出六模块（不含 `health_check` 和 `trigger`）：

```javascript
module.exports = {
    lifecycle: require('./lifecycle'),
    skillsMonitor: require('./skills_monitor'),
    cleanup: require('./cleanup'),
    trigger: require('./trigger'),
    commentary: require('./commentary'),
    selfRepair: require('./self_repair'),
};
```

这种设计使得 ops 模块既是**可独立调用的 CLI**，又是**可集成的 npm 模块**（dual-mode）。

---

## §2 lifecycle — Daemon 进程管理器

### 2.1 核心功能

`lifecycle.js` 提供 evolver daemon 的完整生命周期管理：

```
start   → 启动 detached 子进程，写入 PID 文件
stop    → SIGTERM → SIGKILL 优雅终止
restart → stop + 2s delay + start
status  → 返回运行 PIDs + 命令行
tailLog → tail -n 最后 N 行日志
check   → 健康检查（进程存在 + 日志沉默超时）→ 自动 restart
```

### 2.2 关键实现细节

**进程发现**（跨平台）：

```javascript
// macOS/Linux: ps -e -o pid,args 过滤含 node/index.js/--loop/evolver 的进程
function getRunningPids() {
    var out = execSync('ps -e -o pid,args', { encoding: 'utf8' });
    // 过滤条件：cmd includes 'node' && 'index.js' && '--loop'
    // && ('feishu-evolver-wrapper' || 'skills/evolver')
}
```

**Detached 启动**：

```javascript
var out = fs.openSync(LOG_FILE, 'a');
var err = fs.openSync(LOG_FILE, 'a');
var child = spawn('node', [script, '--loop'], {
    detached: true, stdio: ['ignore', out, err],
    cwd: WORKSPACE_ROOT, env: env
});
child.unref(); // 父进程退出后子进程继续运行
fs.writeFileSync(PID_FILE, String(child.pid));
```

**健康检查 + 自动重启**：

```javascript
function checkHealth() {
    var pids = getRunningPids();
    if (pids.length === 0) return { healthy: false, reason: 'not_running' };
    if (fs.existsSync(LOG_FILE)) {
        var silenceMs = Date.now() - fs.statSync(LOG_FILE).mtimeMs;
        if (silenceMs > MAX_SILENCE_MS) {
            return { healthy: false, reason: 'stagnation',
                     silenceMinutes: Math.round(silenceMs / 60000) };
        }
    }
    return { healthy: true, pids: pids };
}
// CLI: node lifecycle.js check → 不健康则自动 restart
```

**配置项**（`config.js`）：
- `MAX_SILENCE_MS`：日志沉默超时（默认，未导出但代码引用）
- `EVOLVER_LOOP_SCRIPT` env：可指定自定义 loop 脚本路径

### 2.3 CE 借鉴

**BlueCortexCE 的等效需求**：
- Java Spring Boot service 管理（start/stop/restart/status）
- 健康检查通过 HTTP `/api/health` 替代日志沉默检测
- CE 的 cron 任务（`openclaw gateway`）已经是 daemon，无需额外生命周期管理
- 但 `lifecycle.js` 的"进程发现 + 自动重启"模式可用于 CE 巡检的告警触发

---

## §3 cleanup — GEP Artifacts 清理

### 3.1 清理策略（双保险）

**Phase 1：基于年龄**（保持 MIN_KEEP = 10 个最新文件）：

```javascript
var MAX_AGE_MS = config.CLEANUP_MAX_AGE_MS; // 默认 24h
var MIN_KEEP = config.CLEANUP_MIN_KEEP;     // 默认 10
// 删除超过 MAX_AGE_MS 的文件（从第 MIN_KEEP 开始计数）
for (var i = MIN_KEEP; i < files.length; i++) {
    if (now - files[i].mtime > MAX_AGE_MS) filesToDelete.push(files[i]);
}
```

**Phase 2：基于数量硬上限**（最多保留 MAX_FILES = 10 个）：

```javascript
var MAX_FILES = config.CLEANUP_MAX_FILES; // 默认 10
// 无论年龄，超过 MAX_FILES 的旧文件一律删除
if (remainingFiles.length > MAX_FILES) {
    var toDelete = remainingFiles.slice(MAX_FILES).map(f => f.path);
    deleted += safeBatchDelete(toDelete);
}
```

**目标文件**：`gep_prompt_*.json/txt`（进化过程产物）

### 3.2 安全设计

- **永远保留最新 10 个**：`MIN_KEEP = 10` 保证历史不因清理丢失
- **批量安全删除**：`safeBatchDelete` 内部 try-catch，单个失败不中断整批
- **仅处理特定模式**：`/^gep_prompt_.*\.(json|txt)$/` 避免误删其他文件
- **两阶段独立**：年龄清理失败不影响数量清理

### 3.3 CE 借鉴

**BlueCortexCE 对应需求**：
- 旧的 `gep_prompt_*.json/txt` 对应 CE 的**进化中间产物**（如 `ObservationEntity` 中的临时数据、JSONL 日志文件）
- CE 可实现类似的**双保险清理**：按时间（7天）+ 按数量（保留最近 1000 条 observation）
- Java 实现：`@Scheduled` cron 任务 + `EntityManager` 批量删除

---

## §4 self_repair — Git 自修复

### 4.1 修复序列（四层防线）

```javascript
function repair(gitRoot) {
    var repaired = [];

    // 1. Abort pending rebase（最常见）
    try { execSync('git rebase --abort', { cwd: root, stdio: 'ignore' });
          repaired.push('rebase_aborted'); } catch (e) {}

    // 2. Abort pending merge
    try { execSync('git merge --abort', { cwd: root, stdio: 'ignore' });
          repaired.push('merge_aborted'); } catch (e) {}

    // 3. Remove stale index.lock（> LOCK_MAX_AGE_MS 才删）
    var lockFile = path.join(root, '.git', 'index.lock');
    if (fs.existsSync(lockFile)) {
        var age = Date.now() - stat.mtimeMs;
        if (age > LOCK_MAX_AGE_MS) { fs.unlinkSync(lockFile); repaired.push('stale_lock_removed'); }
    }

    // 4. Hard reset to origin/main（最后手段，需 EVOLVE_GIT_RESET=true）
    if (process.env.EVOLVE_GIT_RESET === 'true') {
        execSync('git fetch origin main');
        execSync('git reset --hard origin/main');
        repaired.push('hard_reset_to_origin');
    } else {
        // Safe fetch（不修改本地状态）
        execSync('git fetch origin', { timeout: 30000 });
    }
    return repaired;
}
```

### 4.2 关键设计

- **分层恢复**：从安全（rebase abort）到危险（hard reset）递进
- **Guard flag**：`EVOLVE_GIT_RESET=true` 才允许 hard reset，防止意外破坏
- **Stale lock 年龄检测**：只删除过期的 lock，避免误删正在进行的操作
- **幂等执行**：每个修复步骤独立 try-catch，部分失败不影响其他步骤

### 4.3 CE 借鉴

**BlueCortexCE 的等效场景**：
- Git 操作在 CE 中主要通过 `RuntimeService` 的 Git hook 触发
- `self_repair.js` 的"分层恢复"模式可应用于 CE 的**数据库事务回滚**
- Java 等效设计：检测 PostgreSQL 连接状态 → 终止僵死连接 → 回滚未提交事务 → 重置连接池
- 配置驱动：`GIT_RESET_ENABLED` env 对应 CE 的 `DB_SAFE_RESET_ENABLED`

---

## §5 skills_monitor — 技能健康检查与自愈

### 5.1 检查项

| 问题 | 检测方式 | 自动修复 |
|------|----------|----------|
| `Missing node_modules` | `package.json` 有 `dependencies` 但 `node_modules/` 不存在 | `npm install --production` |
| `Empty node_modules` | `node_modules/` 存在但为空 | 同上 |
| `Invalid node_modules` | 目录存在但 readdir 失败 | 同上 |
| `Missing SKILL.md` | `package.json` 存在但 `SKILL.md` 缺失 | 生成 stub |
| `Invalid package.json` | JSON.parse 失败 | 不修复（需人工） |

### 5.2 自愈执行

```javascript
function autoHeal(skillName, issues) {
    for (var issue of issues) {
        if (issue === 'Missing node_modules...') {
            // 删除 package-lock.json 避免冲突
            execSync('npm install --production --no-audit --no-fund', {
                cwd: skillPath, stdio: 'ignore', timeout: 60000
            });
            healed.push(issue);
        } else if (issue === 'Missing SKILL.md') {
            fs.writeFileSync(path.join(skillPath, 'SKILL.md'),
                '# ' + skillName + '\n\n' + name.replace(/-/g, ' ') + ' skill.\n');
            healed.push(issue);
        }
    }
    return healed;
}
```

### 5.3 忽略列表

```javascript
const IGNORE_LIST = new Set([
    'common', 'clawhub', 'input-validator', 'proactive-agent', 'security-audit',
]);
// 用户可在 .skill_monitor_ignore 文件中追加
```

### 5.4 性能优化（v2.0）

**问题**：v1.0 对每个 skill 都要 `spawn('node', ['-c', entryPoint])` 做语法检查，触发性能瓶颈。

**v2.0 修复**：
- 移除同步 spawn 语法检查（信任运行时捕获）
- 只检查 `node_modules` 目录是否存在
- 只在 `node_modules` 为空时触发 `npm install`（而非每次都运行）

### 5.5 CE 借鉴

**BlueCortexCE 等效场景**：
- CE 没有 skill 概念，但有**观察类型注册表**（ObservationType enum）
- `skills_monitor` 的"检查 → 自动修复 → 残留问题上报"模式可迁移到：
  - 数据库 schema 完整性检查
  - pgvector extension 状态检查
  - API endpoint 可达性检查
- IGNORE_LIST 模式对应 CE 的**配置化排除**（如 `config.ignored_observation_types`）

---

## §6 health_check — 系统级健康检查

### 6.1 检查项（五类）

| 检查 | 阈值 | 严重度 |
|------|------|--------|
| **Feishu 密钥** | `FEISHU_APP_ID/SECRET` 存在 | warning（非 critical，避免重启循环） |
| **可选密钥** | `CLAWHUB_TOKEN`/`OPENAI_API_KEY` | info |
| **Disk** | >90% → critical；>80% → warning | critical/warning |
| **Memory** | >95% → critical | critical |
| **进程数**（仅 Linux） | >2000 → warning | warning（检测 fork bomb） |

### 6.2 关键实现

**Disk 检测（跨平台）**：

```javascript
if (fs.statfsSync) {
    // Node 18+ statfs（macOS/Linux）
    const stats = fs.statfsSync(mount);
    return { pct: Math.round((used/total)*100), freeMb: Math.round(free/1024/1024) };
} else {
    // Fallback: df -P
    const out = execSync(`df -P "${mount}" | tail -1 | awk '{print $5, $4}'`);
    // pct = 100% 行数，freeMb = 可用块数/1024
}
```

**进程数缓存**（避免频繁 `/proc` 扫描）：

```javascript
if (!runHealthCheck._procCache || now - runHealthCheck._procCacheAt > 60000) {
    runHealthCheck._procCache = fs.readdirSync('/proc').filter(f => /^\d+$/.test(f)).length;
    runHealthCheck._procCacheAt = now;
}
```

### 6.3 总体状态计算

```javascript
if (criticalErrors > 0) status = 'error';
else if (warnings > 0) status = 'warning';
else status = 'ok';
```

### 6.4 CE 借鉴

**BlueCortexCE 等效需求**：
- **Disk**：CE 的 JSONL 日志写入、`ObservationEntity` 数据库膨胀 → 监控 `/var/lib/postgresql`
- **Memory**：Java Heap 监控通过 `ManagementFactory.getMemoryMXBean()` 更精确
- **Process count**：CE 无 fork bomb 风险（Java 进程数固定）
- **密钥检查**：CE 的 `application.properties` / `.env` 密钥存在性检查
- **返回格式**：`{ status, timestamp, checks[] }` 可直接映射到 CE 的 `HealthResponse`

---

## §7 trigger — 即时唤醒信号

### 7.1 三函数 API

```javascript
function send()  { fs.writeFileSync(WAKE_FILE, 'WAKE'); }  // 写入唤醒文件
function clear() { if (fs.existsSync(WAKE_FILE)) fs.unlinkSync(WAKE_FILE); }  // 清除
function isPending() { return fs.existsSync(WAKE_FILE); }   // 查询待处理
```

**WAKE_FILE** = `<workspace>/memory/evolver_wake.signal`

### 7.2 用途

`trigger.send()` 由外部事件（如 Feishu 消息、Webhook）调用，写入信号文件后，wrapper 的轮询循环立即唤醒 evolver 执行任务，而非等待下一个 sleep 周期。

### 7.3 CE 借鉴

**BlueCortexCE 等效**：
- Feishu 消息事件本身就是 CE 的"唤醒信号"（通过 OpenClaw channel plugin）
- `trigger.js` 的文件轮询模式在 CE 中对应**WebSocket/SSE 长连接**推送
- 但 `isPending()` 查询模式可借鉴：CE 的**后台巡检任务**可通过 DB flag 触发（如 `HEARTBEAT.md` 中的 `next_cron_run` 字段）

---

## §8 commentary — 人格化 Cycle 评语

### 8.1 三种人格

| Persona | Success 示例 | Failure 示例 |
|---------|-------------|-------------|
| `standard` | "Evolution complete. System improved." | "Cycle failed. Will retry." |
| `greentea` | "Did I do good? Praise me~" | "Oops... it is not my fault though~" |
| `maddog` | "TARGET ELIMINATED." | "FAILED. RETRYING." |

### 8.2 实现

```javascript
function getComment(options) {
    var persona = options.persona || 'standard';
    var success = options.success !== false;
    var pool = success ? PERSONAS[persona].success : PERSONAS[persona].failure;
    return pool[Math.floor(Math.random() * pool.length)];
}
```

### 8.3 CE 借鉴

**BlueCortexCE 等效**：
- CE 是旁路记忆系统，cycle 评语对应**会话摘要的语气风格**
- `PersonalityState`（五维：rigor/creativity/verbosity/risk_tolerance/obedience）可控制 SummaryEntity 的输出风格
- CE 的"人格化摘要"：`ContextService.generateSummary()` 可根据 `ModeService` 的人格参数调整语气

---

## §9 协同架构：ops 层作为外部自愈总线

### 9.1 模块间关系图

```
外部调用（cron / wrapper / Feishu webhook）
    │
    ├── lifecycle.js check  → 不健康 → restart
    │                           │
    │                           └── self_repair.js repair → Git 异常修复
    │
    ├── cleanup.js run      → 定期清理旧 artifacts
    ├── skills_monitor.js run → 检查技能健康 → autoHeal
    ├── trigger.js send      → 立即唤醒 daemon
    └── health_check.js      → 系统资源监控（不直接修复）
```

**commentary.js** 是纯输出模块（评语气），不参与自愈闭环。

### 9.2 外部调度模式

这些 ops 模块通过 **CLI 路由**被 `index.js` 的 6 命令系统调用（见 doc 53 §2）：

```javascript
// index.js 中的 ops 命令分发
// node evolver.js <command> → 各模块的 CLI 入口
```

### 9.3 与主循环的关系

**唯一例外**：`ops/innovation.js` 的 `generateInnovationIdeas()` 在 `gep/prompt.js` 中被直接引用。当 `evolve.js` 检测到停滞信号时，`prompt.js` 在 GEP prompt 中注入 innovation ideas。

**其余 ops 模块**：完全外部化，通过 daemon 外层（wrapper 或 cron）调用，不影响 `evolve.js` 主循环的单一职责。

---

## §10 CE 借鉴路径（综合）

### P0（立即可落地）

| Ops 模块 | CE 借鉴 | 实现方式 |
|---------|---------|---------|
| `health_check` | **服务健康检查** | Java `HealthIndicator` + `/api/health` 端点 |
| `cleanup` | **旧数据定期清理** | `@Scheduled` + JPA `deleteOldObservations()` |
| `skills_monitor` | **DB/pgvector 完整性检查** | Startup 时检查 `pgvector` extension + schema version |

### P1（需要设计）

| Ops 模块 | CE 借鉴 | 实现方式 |
|---------|---------|---------|
| `self_repair` | **DB 连接池自愈** | HikariCP listener 检测僵死连接 → 重置 |
| `lifecycle` | **Cron 任务自恢复** | 检测任务卡死 → kill + reschedule |
| `trigger` | **即时巡检触发** | DB flag `pending_health_check = true` → 下次轮询立即执行 |

### P2（架构探索）

| Ops 模块 | CE 借鉴 | 实现方式 |
|---------|---------|---------|
| `commentary` | **人格化摘要语气** | ModeService 人格参数 → SummaryEntity 语气控制 |
| `skills_monitor` 的 autoHeal | **自我配置修复** | 检测配置漂移 → 自动应用修复 |

---

## §11 关键设计原则总结

1. **Dual-mode**：每个模块既是独立 CLI 又是可导入 npm 模块
2. **幂等性**：`repair()` / `autoHeal()` 多次执行结果一致
3. **分层防御**：`self_repair` 四层递进，`cleanup` 双保险
4. **性能隔离**：健康检查的 `/proc` 扫描缓存 60s，避免频繁 I/O
5. **外部编排**：ops 模块不由主循环直接调用，保持单一职责
6. **自动优先**：`skills_monitor` 发现问题后自动修复，不只是告警
7. **Guard flag**：危险操作（hard reset）需 env 显式启用

---

**关联文档**：[`53` 主入口 Daemon Loop + CLI 架构](./53-main-daemon-loop-cli-architecture.md)；[`27` Ops 套件 + Canary](./27-ops-suite-runtime-config-canary.md)；[`25` 高级模式（Innovation Catalyst）](./25-advanced-patterns-prm-epigenetic-antipattern.md) §5
