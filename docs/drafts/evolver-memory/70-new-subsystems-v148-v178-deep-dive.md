# `70` v1.48–v1.78 新增子系统深度分析

**文件**: `docs/drafts/evolver-memory/70-new-subsystems-v148-v178-deep-dive.md`  
**目标**: 源码级分析 v1.48–v1.78 新增的 6+ 个子系统，提炼可借鉴设计思想  
**数据来源**: `git show origin/main:src/gep/` 各文件  
**最后更新**: 2026-05-03

---

## 概览：新增子系统清单

| 文件/目录 | 行数 | 职责 |
|-----------|------|------|
| `src/gep/skill2gep.js` | 645 | 逆向蒸馏：Skill.md → Gene + Capsule |
| `src/gep/selfPR.js` | 408 | 自动 PR 贡献公开仓库 |
| `src/gep/validator/` | ~900+ | 沙箱验证子系统 |
| `src/gep/portable.js` | 96 | GEPX 归档导出（gzip-tar） |
| `src/gep/claimNudge.js` | 121 | Hub 积分领取提醒 |
| `src/gep/mailboxTransport.js` | 82 | 邮箱传输适配器 |

---

## 1. `skill2gep.js` — 逆向蒸馏（645 行）

### 1.1 设计定位

`skill2gep.js` 是 `skillDistiller.js` 的**反向**操作：

```
skillDistiller.js : Capsule stream → Gene    (正向蒸馏：执行证据 → 策略)
skill2gep.js      : Skill.md + 1 run → Gene + Capsule (逆向：技能描述 + 真实运行 → 策略 + 证据)
```

核心契约（`SKILL.md` 解析 → `Gene` 合成 → `Capsule` 组装 → 双通道发布）：

```
parseSkillMd(skillMd)
  → synthesizeGene(parsed, execution)
      → validateSynthesizedGene(draft)  ← 复用 skillDistiller 的净化/验证逻辑
  → detectForgery(execution)           ← 防伪造：零 trace → 拒绝
  → assembleCapsule(gene, execution)    ← 验证覆盖检查：Gene.validation ⊆ execution.trace
  → assetStore.upsertGene / .appendCapsule
  → publishAssets(gene, capsule)       ← 双通道发布：Skill Store + GEP bundle
```

### 1.2 关键设计机制

#### SKILL.md 解析（`parseSkillMd`）

从 Markdown 提取结构化信息：

- **Frontmatter**: `name` / `description` 等元字段
- **Signal 提取**: description + trigger 区 → 分词 → 3–40字符、去数字、去重
- **Strategy 提取**: `workflow`/`strategy`/`steps` 等区段 → 编号/列表项 → 最多 10 步
- **Avoid 提取**: `avoid`/`pitfall`/`anti-pattern` 区段 → 最多 5 条
- **Validation 提取**: fenced code block（bash/shell）→ 最多 5 条
- **Preconditions 提取**: `prerequisite` 区段 → 最多 4 条

**设计亮点**: 纯文本解析 + 区段匹配，无 LLM 调用，轻量快速。

#### 防伪造检测（`detectForgery`）

三层门禁，拒绝"声称成功但无真实证据"的 Capsule：

```
empty_execution_trace     → status=success 但 trace=[]
zero_blast_radius_with_success → 文件/行数均为 0
no_exit_code_in_trace     → 无任何 exit code 记录
```

#### Capsule 验证覆盖（`assembleCapsule`）

**关键不变量**: `Gene.validation` 中的每条命令必须在 `execution.trace` 中存在（规范化后精确匹配）。缺失任何一条 → 拒绝发出 Capsule，仅保留 Gene。

这保证社区收到的 Capsule 的 `execution_trace` 覆盖了 Gene 作者承诺的所有验证路径。

#### 双通道发布（`publishAssets`）

```
Channel 1 (Skill Store): skillPublisher.publishSkillToHub(gene) → SKILL.md 发布
Channel 2 (GEP bundle):  a2a.buildPublishBundle({gene, capsule}) → /a2a/publish
```

两者互不阻塞；任一成功即视为发布成功。

#### 幂等状态（`seen` map）

基于 `skillHash + execHash` 的去重：同一 Skill 内容 + 同一执行指纹只蒸馏一次，避免重复上传。

### 1.3 CE 借鉴路径（P0/P1/P2）

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P0** | `detectForgery` 防伪造模式 | ObservationEntity 的 `contentHash` 去重 + `outcome` 验证覆盖检查 |
| **P1** | `parseSkillMd` 轻量区段解析 | 将 SKILL.md / prompt 模板解析为结构化 Gene 的思路 |
| **P1** | 双通道幂等状态 | 防止重复摄入的 `contentHash` seen-map 机制 |
| **P2** | 验证覆盖不变量 | ValidationReport 中"声明的测试 ⊆ 实际执行的测试"映射到 CE |

---

## 2. `selfPR.js` — 自动贡献公开仓库（408 行）

### 2.1 设计定位

当 evolver 通过自进化（self-mutation）生成高质量变更时，`selfPR.js` 自动创建 GitHub PR 将变更推送到公开仓库。

**多门禁安全模型**（全部通过才发 PR）：

```
score ≥ SELF_PR_MIN_SCORE          (outcome.score 门槛)
streak ≥ SELF_PR_MIN_STREAK        (连续成功次数门槛)
category == 'optimize'             (仅 optimize 类型，不含 repair/innovate)
risk == 'low'                      (仅低风险)
files ∈ [1, SELF_PR_MAX_FILES]     (文件数限制)
lines ∈ [1, SELF_PR_MAX_LINES]     (行数限制)
changedFiles ⊆ public_non_obfuscated (仅非混淆文件)
¬cooldown                           (24h 冷却)
¬duplicate_diff                     (diff hash 去重)
¬leak_detected                     (全量泄露扫描)
```

### 2.2 关键设计机制

#### 公开文件过滤（`isPublicNonObfuscated`）

```
OBFUSCATED_FILES (Set): 30+ 个内部混淆文件（solidify/memoryGraph/selector 等）
PUBLIC_EXCLUDE_PREFIXES: ['docs/', 'memory/', 'dist-public/']
PUBLIC_INCLUDE_PREFIXES: ['src/', 'scripts/']
PUBLIC_INCLUDE_EXACT: ['index.js', 'package.json']
```

→ PR 只改源码，不改文档/记忆/混淆文件。

#### Diff 去重（`computeDiffHash`）

基于 `SHA256(file:content + ...)` 的 16 位哈希，存储最近 20 个 diff hash，防止相同变更重复 PR。

#### 泄露扫描（`fullLeakCheck`）

使用 `sanitize.fullLeakCheck` 对 diff 内容进行全量泄露检测，包括：
- API key / token 模式
- 凭证模式
- 路径泄露
- 环境变量泄露

#### PR 创建流程

```
forkCheck (gh repo view) → clone public repo → git checkout -b → copy changed files
→ git add/commit → git push → gh pr create --body-file → cleanup
```

使用 `gh` CLI 而非 GitHub API，原生认证（`gh auth`）。

### 2.3 CE 借鉴路径（P0/P1/P2）

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P0** | 多门禁安全模型 | 任何自动修改代码的操作（Future phase）需要：score + streak + risk + blast_radius 多重门禁 |
| **P1** | `fullLeakCheck` 泄露扫描 | 任何外部输出前的 `sanitize` 泄露扫描 |
| **P2** | diff hash 去重 | 防止重复的 observation/event 上报到 Hub |

---

## 3. `validator/` — 沙箱验证子系统（~900+ 行）

### 3.1 子系统结构

```
src/gep/validator/
├── index.js           274 行 — 验证引擎入口 + 独立守护进程
├── sandboxExecutor.js 399 行 — 隔离执行器
├── stakeBootstrap.js 347 行 — Stake 引导 + 退避重试
└── reporter.js      118 行 — 验证报告生成
```

### 3.2 安全模型（`sandboxExecutor.js`）

这是整个子系统中安全设计最密集的部分。

#### 两层可执行白名单

**第一层：可执行文件白名单**
```javascript
const ALLOWED_EXECUTABLES = new Set(['node']);
// 拒绝 node/npm/npx（v1.69+ GHSA-jxh8-jh77-xh6g 修复）
```
说明：`npm` 和 `npx` 因任意代码执行风险被移除。Hub 提供的 `validation_commands` 只能调用 `node`。

**第二层：Node 标志黑名单**
```javascript
const BLOCKED_NODE_FLAGS = new Set([
  '-e', '--eval', '-p', '--print', '-i', '--interactive',
  '-r', '--require', '--loader', '--experimental-loader',
  '--import', '--env-file',
]);
```
→ 即使是 `node`，也只允许 `node <script-file> [args]` 形式。

#### 命令解析器（`parseCommand`）

手写 tokeniser：支持单/双引号，拒绝所有 shell 元字符（`|` `&` `;` `>` `<` `` ` `` `$`）。

→ 命令字符串直接转换为 `{executable, args}`，不经过 shell 展开。

#### 沙箱环境（`buildSandboxEnv`）

```javascript
HOME=os.tmpdir()
TMPDIR=os.tmpdir()
PATH=process.env.PATH  // 仅保留 node 可解析
NODE_ENV='sandbox'
EVOLVER_SANDBOX='1'
```

所有凭证文件（`~/.npmrc`/`~/.ssh/`/`~/.aws/`）指向空目录而非真实 home。

#### 隔离目录

```
os.tmpdir()/evolver-validator/task_<timestamp>_<random>/
```

每任务一个 fresh 目录，执行后即删除（`cleanupDir`）。

#### 超时控制

- 单命令：默认 60s，硬上限 120s
- 批次：默认 180s
- 超时 → `SIGKILL` → `timedOut: true`

#### 输出截断

stdout/stderr 各截断至 4000 字符，防止日志注射。

### 3.3 Stake 引导与退避重试（`stakeBootstrap.js`）

#### 三层 Hub URL 解析

```
1. process.env.EVOLVER_VALIDATOR_ENABLED  (用户逃生出口，最高优先级)
2. readFeatureFlag('validator_enabled')    (Hub 邮箱持久化标志)
3. 默认: true                             (v1.69+ 默认开启)
```

#### 故障分类与差异化退避

| 故障类型 | HTTP 状态 | 退避策略 |
|----------|-----------|----------|
| 临时故障（网络/5xx） | 非 400/402/403/404 | 5min → 15min → 60min → 4h（cap） |
| 积分不足（402） | 402 | 60min → 4h（cap）+ 提示积分说明 |
| 永久故障 | 400/403/404 | `disabledUntilRestart: true` |

#### 磁盘持久化状态（v1.69.11+）

```
~/.evomap/validator_stake_state.json
{
  nextAttemptAt,       // 下次尝试时间
  transientFailures,   // 临时故障计数
  fundsFailures,       // 积分故障计数
  lastSuccessAt,       // 上次成功时间
}
```

关键设计：`disabledUntilRestart` **不**持久化 → 进程重启后自动重试（让 operator 修复后自然恢复）。

### 3.4 独立守护进程模式

Validator 角色运行独立的定时器（默认 60s 间隔，首次 30s 后），不依赖主循环的 idle gating，避免在宿主繁忙时被压制。

### 3.5 CE 借鉴路径（P0/P1/P2）

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P0** | 两层可执行白名单 + 标志黑名单 | 沙箱验证 / 外部工具调用的安全模型 |
| **P0** | 磁盘持久化退避状态 | 防止重试风暴的指数退避 + 磁盘 checkpoint |
| **P1** | 隔离 tmpdir + 凭证剥离 env | 敏感环境隔离 |
| **P1** | 差异化退避（临时 vs 积分 vs 永久） | Hub API 调用失败的不同处理策略 |
| **P2** | 独立守护进程 | OpenClaw 的独立 cron/巡检进程设计 |

---

## 4. `portable.js` — GEPX 归档导出（96 行）

### 4.1 设计定位

将本地 GEP 资产（Gene/Capsule/事件/记忆图）打包为自描述的 gzip-tar 归档，供离线迁移/备份/点对点传输。

### 4.2 归档结构

```
output.gepx (gzip-tar)
├── genes/
│   ├── genes.json      (基因索引)
│   └── genes.jsonl     (基因事件)
├── capsules/
│   ├── capsules.json   (胶囊索引)
│   └── capsules.jsonl  (胶囊事件)
├── events/
│   └── events.jsonl    (通用事件)
├── memory/
│   └── memory_graph.jsonl  (记忆图)
└── manifest.json       (元数据 + SHA256 校验和)
```

### 4.3 manifest 结构

```json
{
  "gep_version": "1.0.0",
  "created_at": "ISO8601",
  "agent_id": null,
  "agent_name": "unknown",
  "statistics": {
    "total_events": N,
    "total_genes": N,
    "total_capsules": N,
    "memory_graph_entries": N
  },
  "source": { "platform": "evolver", "component": "sync" },
  "checksums": ["sha256  path/to/file", ...]
}
```

### 4.4 CE 借鉴路径

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P1** | 自描述归档格式 + SHA256 校验 | Observation/Summary 导出 + 完整性校验 |
| **P2** | 轻量备份/迁移管道 | Session 数据导出/导入机制 |

---

## 5. `claimNudge.js`（121 行）+ `mailboxTransport.js`（82 行）

### 5.1 `claimNudge.js`

节点积分领取提醒：检查 Hub 账户余额，当积分超过阈值时提醒用户领取/认领节点。

关键机制：
- Hub API 查询账户余额
- 本地去重（已提醒过的不重复提醒）
- 积分阈值可配置

### 5.2 `mailboxTransport.js`

Hub Mailbox 传输适配器：封装与 Hub Mailbox 端点的通信（HTTP + 签名头）。

关键机制：
- `buildHubHeaders()`: NodeId + 签名
- `fetch` 封装 + 超时控制
- 错误处理标准化

### 5.3 CE 借鉴路径

| 优先级 | 借鉴点 | CE 映射 |
|--------|--------|---------|
| **P2** | Mailbox transport 封装模式 | Hub 客户端通信的标准化封装 |

---

## 6. 总体架构启示

### 6.1 核心设计原则

1. **反向蒸馏的对称性**: `skillDistiller`（Capsule→Gene）和 `skill2gep`（Skill→Gene+Capsule）形成对称，前者从执行提炼策略，后者从技能描述合成策略。两者共用 `validateSynthesizedGene`。

2. **零信任 Hub**: `sandboxExecutor` 不信任 Hub 提供的命令，即使 Hub 签名有效也做白名单检查 → 防御供应链攻击（GHSA-jxh8-jh77-xh6g）。

3. **安全默认值**: Validator 默认开启（`EVOLVER_VALIDATOR_ENABLED` 默认 `true`），但可通过环境变量一键关闭。

4. **渐进式退避**: 所有外部 API 调用（Hub stake、PR 创建）均有差异化退避，积分不足比临时故障退避更久。

5. **幂等发布**: 所有发布操作（Skill Store + GEP bundle）互不阻塞，失败隔离，双通道冗余。

### 6.2 CE 近期可直接借鉴

| 优先级 | 机制 | 文件 |
|--------|------|------|
| **P0** | 磁盘持久化的指数退避 + 会话级禁用标志 | 任何 Hub API 客户端 |
| **P0** | 双层可执行白名单 + 标志黑名单 | MCP 工具执行沙箱 |
| **P1** | `detectForgery` 模式（零证据拒绝） | ObservationEntity 验证覆盖检查 |
| **P1** | 多门禁安全模型（score + streak + risk + blast） | 自动代码修改的门禁设计 |
| **P2** | GEPX 归档格式 | Session/observation 导出 |

---

## 附录：关键源码行号速查

| 模块 | 函数/常量 | 核心行 |
|------|-----------|--------|
| skill2gep.js | `parseSkillMd` | L75–L152 |
| skill2gep.js | `detectForgery` | L165–L180 |
| skill2gep.js | `assembleCapsule` | L193–L250 |
| skill2gep.js | `runOnSkillInvocation` | L265–L400 |
| selfPR.js | `maybeCreatePR` (门禁链) | L145–L200 |
| selfPR.js | `isPublicNonObfuscated` | L45–L65 |
| selfPR.js | `fullLeakCheck(diffContent)` | L225 |
| validator/index.js | `isValidatorEnabled` (三层解析) | L35–L55 |
| validator/index.js | `startValidatorDaemon` | L150–L175 |
| sandboxExecutor.js | `parseCommand` | L80–L115 |
| sandboxExecutor.js | `ALLOWED_EXECUTABLES` | L35 |
| sandboxExecutor.js | `BLOCKED_NODE_FLAGS` | L45 |
| sandboxExecutor.js | `buildSandboxEnv` | L160–L180 |
| stakeBootstrap.js | `ensureValidatorStake` | L180–L260 |
| stakeBootstrap.js | `_loadStateFromDisk` | L105–L145 |
| stakeBootstrap.js | `classifyFailure` | L165–L175 |
