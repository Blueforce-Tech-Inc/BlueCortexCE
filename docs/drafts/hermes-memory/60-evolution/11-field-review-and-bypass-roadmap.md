# 现场复核与旁路型路线图（演进补篇）

> **本地数据源**: `/Users/yangjiefeng/Documents/NousResearch/hermes-agent/`（本机路径；以你检出版本为准）  
> **撰写日期**: 2026-04-19  
> **任务对齐**: Hermes 记忆分析 — 结合 BlueCortexCE **旁路型**定位持续迭代。

---

## 1. 本次复核范围（代码锚点）

以下路径在本地仓库中存在，可与本目录其他文档中的文件名、行号对照阅读（上游若重构需自行更新行号）：

| 主题 | 路径 |
|------|------|
| Provider 抽象与可选 Hook | `agent/memory_provider.py` |
| 多 Provider 编排 | `agent/memory_manager.py` |
| 会话与消息 / FTS / reasoning 列 | `hermes_state.py` |
| 内置 curated 文件记忆 | `tools/memory_tool.py` |
| Session 历史检索 + 摘要 | `tools/session_search_tool.py` |
| 压缩与工具结果裁剪 | `agent/context_compressor.py` |
| 辅助模型路由（session_search 等） | `agent/auxiliary_client.py` |
| 运行时装配（prefetch / sync / hooks） | `run_agent.py`（体量极大，建议配合符号检索） |

`memory_provider.py` 文件头文档字符串概括了 **initialize → prefetch / queue_prefetch → sync_turn** 的主线，以及 **on_pre_compress、on_memory_write、on_delegation** 等可选 Hook；与 [`06-memory-provider-hooks-inventory.md`](06-memory-provider-hooks-inventory.md) 等篇互为补充。

---

## 2. 架构立场再确认（旁路型「翻译」）

| Hermes 内置型事实 | 对 BlueCortexCE 的含意 |
|------------------|------------------------|
| 记忆与 Agent 同进程、同编排 | 我们不复制「同一进程内改写对话」；改为 **HTTP API + 消费方（Claude Code / OpenClaw）** 决定何时检索、如何注入。 |
| `prefetch` 写入 user message 侧上下文 | **思想可借**：把「非用户输入」标成背景；**落地**：在 proxy/SDK 侧拼 `<memory-context>` 类围栏，而不是在服务端强行改 Claude 的 system prompt 语义。 |
| `MemoryManager` 聚合多 Provider | **思想可借**：检索多路合并（向量 / 关键词 / 时间线）；**落地**：后端可保留单一 pgvector 主路径，以「策略对象」扩展第二路检索，而非照搬多插件进程模型。 |

---

## 3. 建议优先级（下一轮产品/工程）

下列顺序兼顾 **成本** 与 **对旁路用户的感知**，且每条均可独立验收。

1. **注入语义与安全**：对所有 ingest 路径做「注入特征 + 不可见字符」类校验（对齐 Hermes `memory_tool` / prompt 侧扫描思路）；输出侧统一围栏文案，避免模型把记忆当用户指令。  
2. **检索互补**：在现有向量检索外，评估 **关键词 / BM25 / pg_trgm** 一路（Hermes 的 FTS5 角色），用于命令、路径、错误码类查询。  
3. **Turn 级预取（异步）**：在 `PostToolUse` / 下一轮 `UserPrompt` 之间，用后台任务预热「下一跳可能用到的上下文」，主路径只读缓存（对齐 `queue_prefetch` / `prefetch` 的时序思想，而非在服务端跑完整 Agent）。  
4. **压缩前兜底**：在 session 结束或长会话截断前，触发一次「高价值 observation 写入」策略（对齐 flush / `on_session_end` 的**意图**；实现放在后端策略或 proxy 配置，而非假扮用户发消息）。  
5. **可观测性**：为检索、嵌入、摘要、外部 LLM 调用打点（延迟、失败率、截断次数），对应 Hermes 侧「辅助模型链 + 超时」的工程化目标，便于旁路部署排障。

---

## 4. 与 BlueCortexCE 仓库的衔接提示（不写实现细节）

- **入口对齐**：继续以 `IngestionController` 与 thin proxy 事件为「事实来源」，任何新能力先定义 **REST/MCP 契约**，再选 Java 服务内实现或异步 worker。  
- **避免范围蔓延**：Dialectic / Honcho 云端 Peer 模型是 **产品形态差异**，旁路型默认只做「记忆即服务」；若要做「合成回答」，显式立项为独立 API（`/api/context/generate` 一类），勿与 ingestion 混层。

---

## 5. 文档维护

见 [`../AGENT.md`](../AGENT.md) 与 [`../index.md`](../index.md)。
