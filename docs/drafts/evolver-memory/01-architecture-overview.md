# 1. 架构概览

## 1.1 系统定位

Evolver 是 EvoMap 网络的**自进化引擎**，核心使命：把零散 prompt 调整升级为**可审计、可复用、可协作**的进化资产。

**它不是代码修补器**，而是一个 prompt 生成器。每次进化周期：
1. 扫描 `memory/` 目录中的运行时日志、错误模式、信号
2. 从 `assets/gep/` 选择最佳匹配的 Gene 或 Capsule
3. 输出严格遵循 GEP 协议的 prompt，引导下一次进化
4. 记录可审计的 EvolutionEvent

## 1.2 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| **SignalExtractor** | `src/gep/signals.js` | 从日志/文本中提取信号，多语言支持 |
| **MemoryGraph** | `src/gep/memoryGraph.js` | append-only JSONL 事件存储与检索 |
| **MemoryGraphAdapter** | `src/gep/memoryGraphAdapter.js` | local/remote 双模式适配器 |
| **GeneSelector** | `src/gep/selector.js` | 模式匹配 + 语义相似度 + 表观遗传boost |
| **OutcomeTracker** | `src/gep/memoryGraph.js` | 推断结果 + 置信边 + 衰减 |
| **Reflection** | `src/gep/reflection.js` | 周期反思、自适应间隔 |
| **NarrativeMemory** | `src/gep/narrativeMemory.js` | 人类可读进化叙事 |
| **LearningSignals** | `src/gep/learningSignals.js` | 信号扩展为标签，用于评分 |
| **Mutation** | `src/gep/mutation.js` | 变异上下文分类 |
| **Personality** | `src/gep/personality.js` | 个性状态管理与变异风险控制 |
| **ExecutionTrace** | `src/gep/executionTrace.js` | 脱敏执行轨迹上报 |

## 1.3 文件布局

```
evolver/src/
├── gep/
│   ├── memoryGraph.js          # 核心：JSONL 存储 + 边聚合 + 建议
│   ├── memoryGraphAdapter.js   # 适配器：local + remote provider
│   ├── signals.js              # 信号提取 + 去重 + 优先级
│   ├── selector.js             # 基因选择：模式 + 语义 + 表观遗传
│   ├── reflection.js           # 反思：自适应间隔 + 上下文构建
│   ├── narrativeMemory.js       # 叙事记忆：人类可读历史
│   ├── learningSignals.js      # 信号扩展为标签
│   ├── mutation.js             # 变异上下文
│   ├── personality.js           # 个性状态
│   ├── executionTrace.js        # 脱敏执行轨迹
│   ├── envFingerprint.js        # 环境指纹
│   └── paths.js                # 目录路径解析（含 scope 隔离）
├── ops/
│   ├── lifecycle.js            # 进化周期管理
│   ├── health_check.js         # 健康检查
│   └── ...
├── adapters/
│   ├── hookAdapter.js          # 平台无关的 hook 安装
│   ├── claudeCode.js           # Claude Code 适配器
│   ├── cursor.js               # Cursor 适配器
│   ├── codex.js                # Codex 适配器
│   └── kiro.js                # Kiro 适配器
└── proxy/
    ├── lifecycle/manager.js    # Hub 生命周期管理
    ├── mailbox/store.js        # 邮箱消息持久化
    └── sync/{engine,inbound,outbound}.js  # 同步引擎
```

## 1.4 平台无关 Hook 机制

适配器层（`adapters/`）将 evolver 的会话生命周期 hooks 安装到各 AI 编码工具：

| 平台 | 检测标识 | 适配器 |
|------|----------|--------|
| Claude Code | `.claude` 目录 | `claudeCode.js` |
| Cursor | `.cursor` 目录 | `cursor.js` |
| Codex | `.codex` 目录 | `codex.js` |
| Kiro | `.kiro` 目录 | `kiro.js` |

安装脚本为 `scripts/evolver-session-*.js`，通过 `hookAdapter.js` 的平台自动检测机制统一管理。

## 1.5 离线优先设计

核心记忆功能**完全离线运行**，Hub 连接仅用于网络特性（技能共享、worker pool、排行榜）。

```js
// 适配器选择逻辑
function resolveAdapter() {
  const provider = (process.env.MEMORY_GRAPH_PROVIDER || 'local').toLowerCase().trim();
  if (provider === 'remote') return buildRemoteAdapter();
  return localAdapter; // 默认离线
}
```
