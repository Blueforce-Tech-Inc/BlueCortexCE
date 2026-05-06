# v1.78.7–v1.78.10 版本差分 + 新增混淆模块 + sync dedup 测试

**doc**: 108  
**目标**: 记录 v1.78.7–v1.78.10 的版本增量、新增文件及新增混淆模块的状态确认。  
**最后更新**: 2026-05-06（cron 巡检）

---

## v1.78.7（`d2a6620`）新增内容

### 基因库重大更新

- **`assets/gep/genes.json`**: +201 条新基因（repair/optimize/innovate 三类，含完整 `strategy`/`constraints`/`validation` 结构）
- **`assets/gep/capsules.json`**: +4 条新胶囊

### 三个新增混淆模块

⚠️ 以下三个模块在 `origin/main` 中重度混淆（Hex-encoded packer，代码不可读）：

| 文件 | 大小（字符） | 功能推测 |
|------|-------------|---------|
| `src/gep/explore.js` | ~65KB | 推测与 arXiv 论文探索相关（`EVOLVER_EXPLORE_ENABLED` / `EVOLVER_EXPLORE_COOLDOWN_MS` / `EVOLVER_EXPLORE_ARXIV_CATEGORIES` / `EVOLVER_EXPLORE_STALE_DAYS` 等 env 变量名暗示） |
| `src/gep/shield.js` | ~65KB | 推测为安全防护模块（函数名 `isShielded` / `runWithShield` 等在混淆代码字符串中出现） |
| `src/gep/hubVerify.js` | ~25KB | 推测为 Hub 验证/完整性检查模块 |

**分析限制**: 这些文件使用了 JavaScript 混淆器（packer），所有字符串和函数名均为 hex-encoded 或 RC4 加密后的形式。无法通过静态分析还原其实现逻辑。

**`.integrity` 文件**: `src/gep/.integrity` 为二进制文件，记录所有 gep 模块的 hash 值。新模块均已加入完整性校验。

---

## v1.78.8（`2b3c046`）

- **全模块版本 bump**: 所有 `src/gep/*.js` 模块版本标签更新
- **无新功能**: 纯版本同步

---

## v1.78.9（`5304511`）

### 回归测试新增

- **`test/evolveSessionsDir.test.js`**: 170 行，#527 回归测试
  - 验证 `getAgentSessionsDir()` 正确尊重 `AGENT_SESSIONS_DIR` 环境变量覆盖
  - 修复 Windows/非标准 OpenClaw 静默失败问题
- **其余**: 全模块版本标签更新

---

## v1.78.10（`4468c9e`）

### CLI 改进

- **`index.js`**: +58 行（改进 `evolver` CLI 启动逻辑 / dotenv 加载 / 循环保护等）
- **`src/evolve.js`**: +2 行

### 新增回归测试

- **`test/sync-dedup.test.js`**: 192 行，CLI `node index.js sync` 去重逻辑端到端测试
  - **Failure Mode 1**: bundled default-seed 基因（无 `hub_asset_id`）不应静默跳过——必须报告 `id_collision` 让用户知晓
  - **Failure Mode 2**: `--force` 时本地条目被 Hub 副本覆盖并记录 `hub_asset_id`；后续无 `--force` 运行成为 no-op
  - 使用内存 HTTP mock 测试 `/a2a/assets/published-by-me` 接口
  - 15s 超时保护

### 全模块版本 bump

- 所有 31 个 `src/gep/*.js` 模块版本标签更新
- `src/gep/.integrity` 更新

---

## BlueCortexCE 行动项

无直接行动项（混淆代码不可分析 / 均为 EvoMap 内部运行时机制）。

**backlog 备注**: v1.78.7 新增的 `explore.js`（推测 arXiv 探索）与 BlueCortexCE 的"结构化提取"方向不同，无借鉴价值。`shield.js` 安全机制可作为未来安全加固的参考方向，但需等源码公开或通过其他渠道获取设计文档。

---

## 与 v1.78.9 Delta 对比（doc 94/103）

| 版本 | 主要内容 | 混淆模块 | 回归测试 |
|------|---------|---------|---------|
| v1.78.7 | +201 基因 / +4 胶囊 | 3个新增 | 无 |
| v1.78.8 | 全模块 bump | 无 | 无 |
| v1.78.9 | dotenv #526 修复 | 无 | `evolveSessionsDir.test.js` 170L |
| v1.78.10 | CLI 改进 | 无 | `sync-dedup.test.js` 192L |

**演进趋势**: v1.78.7 引入混淆模块（探索/安全/验证），v1.78.8–v1.78.10 聚焦测试覆盖（回归测试护栏）和 CLI 稳定性改进。
