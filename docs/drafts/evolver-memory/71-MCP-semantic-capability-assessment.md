# `71` MCP Semantic Capability 产品评估

**文件**: `docs/drafts/evolver-memory/71-MCP-semantic-capability-assessment.md`  
**目标**: 回答 backlog 问题「**MCP 是否暴露与 Hook 对齐的 `semantic` 能力**」  
**数据来源**: `ClaudeMemMcpTools.java` 源码 + doc 12 §3.2  
**结论**: ✅ 已回答  
**最后更新**: 2026-05-03

---

## 1. 结论摘要

**一句话**: MCP `search` 工具**底层语义能力与 Hook `semantic` 对齐**，但**产品形态不同**——前者是通用记忆检索，后者专用于 prompt 注入块。

---

## 2. Hook 的 `semantic` 能力

### 2.1 HTTP 端点

| 端点 | 实现 | 存储 |
|------|------|------|
| `POST /api/context/semantic` (Java) | `ContextController.semanticContext` → `EmbeddingService.embed` → `SearchService.search` (pgvector 混合) | PostgreSQL + pgvector |
| `POST /api/context/semantic` (Worker) | `SearchRoutes.handleSemanticContext` → `SearchManager` → Chroma | Bun Worker + Chroma |

### 2.2 行为

- **输入**: `{ "q": string, "project"?: string, "limit"?: number }`
- **输出**: `{ "context": string, "count": number }`
- **约束**: `q` 长度 < 20 字符 → 返回空
- **用途**: 在 `session-init` Hook 路径上，将语义检索结果拼入 prompt「注入块」

---

## 3. MCP 的 `search` 能力

### 3.1 源码证据（`ClaudeMemMcpTools.java` L84–L103）

```java
@McpTool(
    name = "search",
    description = "Step 1: Search memory. Returns index with IDs. Use query for semantic search or type/concept for filtering."
)
public Map<String, Object> search(
        String query, String project, Integer limit,
        String type, String concept, Integer offset, String orderBy) {

    float[] queryVector = null;
    if (query != null && !query.isBlank()) {
        queryVector = embeddingService.embed(query);  // ← 语义向量生成
    }

    SearchService.SearchResult result = searchService.search(
        new SearchService.SearchRequest(project, query, queryVector, ...)
    );
    // 返回 { observations, strategy, fell_back, count }
}
```

### 3.2 底层机制对比

| 维度 | Hook `semantic` | MCP `search` |
|------|-----------------|--------------|
| **向量生成** | `EmbeddingService.embed(q)` | `EmbeddingService.embed(query)` |
| **检索引擎** | `SearchService.search` (pgvector 混合) | `SearchService.search` (pgvector 混合) |
| **存储** | PostgreSQL + pgvector | PostgreSQL + pgvector |
| **输入约束** | `q` < 20 char → 空 | 无 20 char 约束 |
| **输出** | `{ context: string }` prompt 注入块 | `{ observations, strategy, fell_back }` 结构化检索结果 |

---

## 4. 核心差异

### 4.1 产品形态不同

| | Hook `semantic` | MCP `search` |
|--|-----------------|--------------|
| **目标** | prompt 注入块生成 | 通用记忆检索 |
| **返回内容** | 格式化字符串（拼入 system prompt） | 结构化 JSON（ID + 元数据） |
| **触发时机** | `session-init` Hook 路径自动 | 显式工具调用 |
| **工具名** | HTTP endpoint（无工具名） | MCP tool name=`search` |

### 4.2 为什么不是「同名工具」

- Hook `semantic` 是 **HTTP 端点**，在 Hook 链路上自动调用
- MCP `search` 是 **MCP 工具**，由 LLM 显式调用
- 两者**不做同一件事**：前者生成 prompt 注入块，后者返回检索结果

---

## 5. 安全评估

| 方面 | Hook `semantic` | MCP `search` |
|------|-----------------|--------------|
| **数据泄露风险** | 仅返回注入块（格式化文本） | 返回完整 `ObservationEntity` JSON，含 `content`/`type`/`projectPath` 等 |
| **鉴权** | 依赖 Hook 链路的 `workerHttpRequest` | 依赖 MCP 协议的 `mcp-server.ts` 鉴权 |
| **项目隔离** | `project` 参数过滤 | `project` 参数过滤 |

**结论**: MCP `search` 返回更丰富的结构化数据，潜在泄露面大于 Hook `semantic`。建议评估是否需要对 MCP 侧结果做 `content` 字段脱敏。

---

## 6. 产品建议

### 6.1 当前状态

- Hook `semantic` → prompt 注入（自动，无 LLM 控制）
- MCP `search` → 显式检索（LLM 调用，返回原始数据）

### 6.2 可选增强方向

1. **MCP semantic 工具**: 新增名为 `semantic` 的 MCP 工具，复用 Hook `semantic` 的注入块格式化逻辑，返回 `{ context, count }` 而非原始 `observations`
2. **MCP 脱敏层**: 对 `search` 返回的 `content` 字段做脱敏（对齐 `sanitize.js` 14 种 REDACT_PATTERNS）
3. **双工 MCP 端点**: 支持 MCP 工具返回「注入块」格式，供 LLM 自行决定是否拼入 prompt

---

## 附录：源码位置

| 文件 | 行号 | 内容 |
|------|------|------|
| `ClaudeMemMcpTools.java` | L84–L103 | `search` 工具实现 |
| `ClaudeMemMcpTools.java` | L50–L65 | `save_memory` 工具 |
| `ClaudeMemMcpTools.java` | L140–L175 | `timeline` 工具 |
| `ClaudeMemMcpTools.java` | L180–L225 | `get_observations` 工具 |
| `ClaudeMemMcpTools.java` | L230–L280 | `recent` 工具 |
| `doc 12 §3.2` | L76–L88 | MCP 工具分流失败评估 |
