# 10. 设计原则与 Cortex CE 借鉴

## 10.1 核心设计原则

### 1. Append-Only 事件溯源

**Evolver**：所有操作追加到 JSONL，状态由独立 state 文件快照管理。

**Cortex CE 现状**：当前是覆盖式写入（如 `recordUserPrompt` 更新同一条记录）。

**借鉴建议**：考虑引入 append-only 事件层，`ContextEntity` 保留完整历史，支持任意时间点回溯。

### 2. 离线优先 + 优雅降级

**Evolver**：Remote 写入先本地后异步同步，读取优先远程降级本地。

**借鉴建议**：Cortex CE 的 MCP/SDK 通信层可采用类似策略：本地先写，网络恢复后同步，不因网络问题阻塞主流程。

### 3. 读取时聚合（Aggregation-at-Read-Time）

**Evolver**：边不在写入时维护，每次 `getMemoryAdvice()` 重新聚合。

**优点**：旧数据自然过期（通过 decay weight），无需维护增量更新逻辑。

**借鉴建议**：Cortex CE 的 SessionStats 等聚合指标可考虑在读取时计算，而非维护预聚合字段。

### 4. 指数半衰期衰减

**Evolver**：置信边使用 `0.5^(ageDays / halfLifeDays)` 衰减。

**Cortex CE 借鉴**：可应用于 observation 的"新鲜度"权重，让近期交互优先级更高。

### 5. 多语言信号提取

**Evolver**：`extractSignals()` 支持 EN/ZH-CN/ZH-TW/JA 四种语言。

**Cortex CE 借鉴**：Structured Extraction 的 prompt 可考虑类似的多语言别名支持。

### 6. 作用域隔离

**Evolver**：通过 `EVOLVER_SESSION_SCOPE` 实现多租户/多项目隔离。

**Cortex CE 借鉴**：当前 session 隔离依赖 serviceId，可考虑引入更细粒度的 scope 概念。

### 7. 表观遗传 Boost

**Evolver**：基因可携带 `epigenetic_marks`，在特定环境（OS/arch/node version）下自动获得 boost。

**Cortex CE 借鉴**：Agent 的 mode/prompt 可携带环境条件，根据运行时环境调整行为。

## 10.2 与 Cortex CE 的关键差异

| 维度 | EvoMap/evolver | Cortex CE |
|------|----------------|-----------|
| **核心抽象** | Gene（进化单元） | Observation（记忆单元） |
| **存储格式** | JSONL 事件 | PostgreSQL + pgvector |
| **检索方式** | Jaccard 相似度 | 向量语义检索 |
| **衰减模型** | 指数半衰期（时间维度） | 无内置衰减 |
| **上下文管理** | Signal + Gene + Hypothesis | Session + Observation + Summary |
| **适配器模式** | Local/Remote 双模式 | SDK / MCP 双模式 |
| **反思机制** | 周期 LLM 反思 | 无内置反思 |
| **叙事记忆** | 有（Markdown） | 无 |

## 10.3 可直接借鉴的设计

1. **Signal → Observation 对应**：Evolver 的信号提取框架可复用于 Cortex CE 的 observation 类型分类
2. **Adapter Provider 模式**：`memoryGraphAdapter.js` 的 local/remote 分离是 SDK/MCP 架构的很好参考
3. **NarrativeMemory**：Cortex CE 缺少人类可读的历史视图，可引入类似 Markdown 叙事层
4. **Decay Weight**：对 observation relevance scoring 引入时间衰减
5. **高频抑制规则**：防止某类 signal 被反复处理（对应防止同类 observation 反复生成）

## 10.4 不适合借鉴的部分

- **基因选择**：Cortex CE 是记忆系统，不是进化引擎，Gene/Capsule 抽象不适用
- **协议漂移检测**：Evolver 特有，与 GEP 协议绑定
- **Blast Radius**：代码变更范围度量，记忆系统不需要
- **Hub/ATP 网络**：Cortex CE 当前是无中心设计
