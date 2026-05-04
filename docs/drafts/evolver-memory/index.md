# Evolver 记忆系统分析（目录入口）

**分析目标**：为 BlueCortexCE（旁路型记忆）提供可落地的借鉴思路。  
**数据来源**：`/path/to/EvoMap/evolver/` 与本仓库架构文档。  
**最后更新**：2026-05-05（**`96`** `forceUpdate.js` Hub 心跳驱动三通道强制更新（100行/degit+npm+manual/幂等+白名单保护/CE P3）✅；**`95`** a2aProtocol.js + a2a.js 双层深度 ✅；**`94`** v1.78.7–v1.78.9 版本差分 ✅；**`93`** directoryClient.js 深度 ✅；**`92`** `prompt.js` GEP Schema Enforcement + Token Budget ✅）

**完整导航**（详细 doc 编号表 + 按主题入口）：[`index-nav.md`](./index-nav.md)

**并列入口（Hermes / 论文线）**：[`../memory-research-hub.md`](../memory-research-hub.md)

---

### 架构规范

- **短入口**：本文仅保留 changelog 与链接入口；完整导航见 [`index-nav.md`](./index-nav.md)。
- **单文件上限**：本目录正文建议 **≤50KB**；超标则**新建方面文件或拆分**。
- **索引真源**：[`index-nav.md`](./index-nav.md) 为完整编号表；changelog 以本文 changelog 为准。

**例行自检**：`wc -c docs/drafts/evolver-memory/*.md` 任一分片若逼近 50KB 考虑拆分。

---

### 快速跳转

| 目标 | 打开 |
|------|------|
| 完整导航（按 doc 编号 + 按主题） | [`index-nav.md`](./index-nav.md) |
| 总 changelog | [`../evolver-memory-analysis.md`](../evolver-memory-analysis.md) |
| BlueCortexCE 方面对照 | [`09`](./09-aspect-bluecortex-bridge.md) |
| CE 实现锚点 / 缺口 | [`10`](./10-aspect-bluecortex-implementation-map.md) |
| 研究 backlog（可勾选） | [`11`](./11-research-backlog.md) |
| CE 记忆 HTTP / 数据平面 | [`12`](./12-bluecortex-api-memory-surface.md) |
| CE Java 摄入 / 写入链 | [`16`](./16-ingestion-write-path-sketch.md) |
| a2aProtocol.js + a2a.js 双层架构 | [`95`](./95-a2aProtocol-and-a2a-deep-dive.md) |
| GEP Prompt Schema Enforcement + Token Budget | [`92`](./92-prompt-js-schema-enforcement-and-token-budget.md) |
| `forceUpdate.js` Hub 心跳驱动三通道强制更新 | [`96`](./96-forceupdate-hub-heartbeat-driven-version-migration.md) |
| Hub Search-First（两阶段搜索+双层缓存） | [`89`](./89-hubsearch-two-phase-semantic-and-dual-cache-deep-dive.md) |
| ATP Heartbeat 旁路交付机制 | [`91`](./91-atp-heartbeatsignalshandler-deep-dive.md) |
| taskReceiver Worker Pool 原子操作 + Capability Match | [`88`](./88-taskreceiver-workerpool-privacy-capability-deep-dive.md) |
| Hub Ecosystem（taskReceiver + hubReview + issueReporter） | [`46`](./46-hub-ecosystem-integration-taskreview-issue.md) |
| 5 核心设计模式 + CE 翻译优先级 | [`62`](./62-evolver-core-design-patterns-and-ce-translation.md) |
| 记忆系统架构综合（8 大设计原则） | [`36`](./36-memory-architecture-synthesis.md) |
| **EvoMap/evolver 本地源码**（memoryGraph / 叙事 / 适配器） | [`18`](./18-evolver-local-source-memory-architecture-snapshot.md) |
| `evolve.js` 主循环 | [`19`](./19-evolver-evolve-loop-memory-ordering-and-outcome-inference.md) |

---

### 完整 doc 编号速查

详见 [`index-nav.md`](./index-nav.md)，核心 doc 编号：

- **01–08**：顺序分片（旧版）
- **09–17**：CE 方面 / API / Java 链路 / 快照
- **18–54**：EvoMap 本地架构 / 主循环 / Session / v1.46–v1.66
- **55–78**：v1.66–v1.78 新架构 / 信号 / A2A / Proxy / Hook
- **79–96**：基础设施深度（asset/contentHash / Ops / Hook / Config / ATP / Dual-Stack / Storage / Worker Pool / hubSearch / Heartbeat旁路 / Prompt Schema / A2A Protocol / ForceUpdate）
