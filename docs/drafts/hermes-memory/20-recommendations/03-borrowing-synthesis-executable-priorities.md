# Hermes → BlueCortexCE：借鉴综述与可验收优先级

> **目的**：把 Hermes 内置型设计「翻译」成旁路型、可独立验收的工程项；与 [`02-bluecortexce-recommendations.md`](02-bluecortexce-recommendations.md) 的细表互补，偏**行动顺序**与**边界**。  
> **上游参考**（本机）：`/Users/yangjiefeng/Documents/NousResearch/hermes-agent/`  
> **日期**：2026-04-19

---

## 1. 读代码时的「文档漂移」提醒

`agent/memory_manager.py`、`agent/memory_provider.py` 的模块文档仍按「`BuiltinMemoryProvider` 先入队、再挂一个外部 Provider」叙述；**当前实现里**，`MEMORY.md` / `USER.md` 由 `tools/memory_tool.py` 的 `MemoryStore` 管理，`run_agent.py` 直接持有 `_memory_store`，外部插件才走 `MemoryManager`。  
**结论**：借鉴时以 **调用链与类定义** 为准，模块头注释代表产品意图，可能与目录树不完全同步。

---

## 2. 高杠杆率借鉴（建议优先做）

下列项与旁路架构冲突小、用户感知强，且可与现有 API 分层对齐。

| 优先级 | Hermes 思想 | 旁路型落地 | 验收 idea |
|--------|-------------|------------|-----------|
| P0 | 预取上下文必须**围栏**且**消毒**（避免模型把记忆当用户话、防止 fence 逃逸） | 在 proxy/SDK 或响应拼装层：`sanitize` 掉用户/检索结果里的 `</memory-context>` 片段，再包一层统一 fence + system note | 单测：恶意字符串不能打断 fence；行为与 `agent/memory_manager.py` 中 `sanitize_context` / `build_memory_context_block` 一致 |
| P0 | 写入与 prompt 侧 **injection / 不可见字符** 扫描 | 所有 ingest 与可注入模板路径走同一套扫描器 | 与安全基线对齐，见 `02` 第 10.5 节 |
| P1 | **关键词检索**与向量检索互补（FTS5 / BM25 角色） | PostgreSQL：`pg_trgm` / 全文检索 / 混合排序，与 pgvector 并行一路 | 路径名、命令、错误码类 query 召回明显提升即可 |
| P1 | **辅助 LLM 任务**（摘要、session 搜索）做 **fallback 链 + per-task 模型** | `/api/context/generate` 等与主聊天模型解耦；便宜模型失败切换备用供应商 | 与 `02` 第 14 节 AuxiliaryClient 对照 |
| P2 | **Turn 末异步预热**下一跳可能用到的上下文 | Worker 或网关：在工具调用后、下一轮用户消息前填充缓存；主路径只读缓存 | 对齐 `queue_prefetch` 的时序思想，不在服务端跑完整 Agent |

更细的代码锚点与表格仍见 **`02`** 与 **`40-context-compression`**；路线图级排序与 **`60-evolution/11-field-review-and-bypass-roadmap.md`** 一致时可只维护一处，另一处用链接引用。

---

## 3. 谨慎借鉴或不要照搬

| 领域 | 原因 | 旁路型替代思路 |
|------|------|----------------|
| Honcho Dialectic 全链路 | 依赖云端 Peer 与后端合成，产品形态不同 | 显式 API：`/api/context/generate` 做「问题 → 综合背景」，限频、限长、可选模型档位 |
| 进程内 ContextCompressor 整段复制 | 与宿主对话状态强耦合 | 只借 **阶段思想**（保护 head/tail、工具结果裁剪、anti-thrashing），压缩触发放在客户端或独立服务 |
| 多插件 MemoryProvider 并存 | Hermes 限制「最多一个外部」以防 tool schema 爆炸 | 旁路可提供统一检索策略对象，内部多路合并，对外仍是一套 REST/MCP |

---

## 4. 与现有章节的交叉引用

- **本仓库注入点与 `/api/context` 端点表**：[`04-ce-injection-and-context-api-surface.md`](04-ce-injection-and-context-api-surface.md)
- **上下文安全缺口盘点（对照 Hermes）**：[`05-ce-context-security-gap-inventory.md`](05-ce-context-security-gap-inventory.md) · 接力 [`../11-research-backlog.md`](../11-research-backlog.md)
- **内置记忆有界 + 冻结快照**：`60-evolution/08-builtin-memory-tool-bounded-snapshot.md`  
- **Hooks 全量**：`60-evolution/06-memory-provider-hooks-inventory.md`  
- **Supermemory 捕获生命周期**：`60-evolution/09-supermemory-capture-lifecycle.md`  
- **矛盾检测 / 全息路线**（若做第二存储引擎再展开）：`60-evolution/07-*`、`10-*`

---

## 5. 下一步研究（可选）

- 对照 `tests/agent/test_memory_provider.py` 中 `TestMemoryContextFencing`，为 BlueCortexCE 侧围栏逻辑列等价测试用例名。  
- 跟踪上游是否引入真实的 `BuiltinMemoryProvider` 类；若有，更新 `02` 第 10.7 节「架构分离」表述以免误导。
