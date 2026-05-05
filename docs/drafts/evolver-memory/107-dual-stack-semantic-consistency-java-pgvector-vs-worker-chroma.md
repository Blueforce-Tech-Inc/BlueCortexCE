# `107` Java pgvector vs Worker Chroma：双栈语义一致性分析

**Doc**: 107 | **Status**: Draft | **Author**: PM Agent | **Date**: 2026-05-05
**Target Backlog Item**: `11-research-backlog.md` Item #22（唯一剩余未勾选项）

---

## 1. 问题定义

**Backlog 原文**：
> **Java（pgvector）与 Worker（Chroma）语义结果一致性**：同名路由、异存储；全 Java / 混合部署下的对齐、评测与文档。

两套系统都叫 `/api/context/semantic`（路由名相同），但：
- **Java 路径**：Hook → Worker → HTTP 转发到 Java → pgvector
- **Worker 路径**：Hook → Worker → ChromaMcpManager → ChromaDB

两者向量模型、过滤逻辑、排序算法均不同。同一 query 可能返回不同结果。

---

## 2. 架构差异对照

### 2.1 向量存储格式

| 维度 | Java（pgvector） | Worker（Chroma） |
|------|-----------------|-----------------|
| **字段** | `embedding_768` / `embedding_1024` / `embedding_1536` 三列，按模型选择 | 每个 field 一个向量文档：`obs_{id}_narrative`、`obs_{id}_fact_0` 等 |
| **存储单位** | ObservationEntity（整行） | 每个 field 的文本片段 |
| **去重** | 返回完整 ObservationEntity | 按 `sqlite_id` 去重，保留最佳 distance |
| **距离度量** | `<=>`（cosine distance，pgvector） | Chroma 内部（默认 L2 或 cosine，取决于部署） |

**关键差异**：Java 以 observation 为粒度；Chroma 以 observation 内每个字段为粒度。

### 2.2 Chroma 字段分片策略

```typescript
// ChromaSync.ts: 每个 field 单独存储一个向量文档
// Chroma document ID 格式
obs_{id}_narrative   // narrative 文本的向量
obs_{id}_fact_0      // fact 字段第0段
obs_{id}_fact_1      // fact 字段第1段
obs_{id}_text        // text 字段
obs_{id}_errorSig    // error_sig_norm 字段
```

Chroma 查询后，按 `sqlite_id` 去重，保留第一个（最佳排名）。

**问题**：如果一个 observation 的 `narrative` 文本匹配度高但 `error_sig` 更相关，去重时可能丢失后者。

### 2.3 过滤时机

| | Java（pgvector） | Worker（Chroma） |
|--|-----------------|-----------------|
| **时间过滤** | `WHERE created_at_epoch > :minEpoch`（90天） | `whereFilter` 支持任意字段过滤 |
| **source 过滤** | 应用层 post-filter | Chroma `where` 子句 |
| **type 过滤** | 应用层 post-filter | Chroma `where` 子句 |
| **concept 过滤** | 应用层 post-filter（`concepts` JSONB 数组包含检查） | Chroma `where` 子句 |
| **语义+过滤组合** | hybrid search（`embedding_1024 <=> cast(...) AND tsvector @@ plainto_tsquery(...) AND ...`） | Chroma `query_texts` + `where` 子句 |

Java 的 hybrid search（语义 + 全文）在 SQL 层组合；Chroma 在 query level 组合。

### 2.4 查询路径源码对照

**Java pgvector 语义查询**（`ObservationRepository.java` L120-170）：

```java
// 三维 embedding 列，按查询向量维度自动选择
@Query(value="""
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND embedding_1024 IS NOT NULL
    AND created_at_epoch > :minEpoch
    ORDER BY embedding_1024 <=> cast(:queryVector as vector)
    LIMIT :limit
    """, nativeQuery = true)
List<ObservationEntity> semanticSearch1024(
    @Param("project") String project,
    @Param("queryVector") String queryVector,
    @Param("minEpoch") long minEpoch,
    @Param("limit") int limit
);

// hybrid search（语义 + 全文 + 过滤）
@Query(value="""
    SELECT * FROM mem_observations
    WHERE project_path = :project
    AND embedding_1024 IS NOT NULL
    AND created_at_epoch > :minEpoch
    AND search_vector @@ plainto_tsquery('english', :query)
    ORDER BY (
        (embedding_1024 <=> cast(:queryVector as vector)) * 0.7 +
        ts_rank(search_vector, plainto_tsquery('english', :query)) * 0.3
    ) ASC
    LIMIT :limit
    """, nativeQuery = true)
```

**Worker Chroma 语义查询**（`ChromaSync.ts` L718-755）：

```typescript
async queryChroma(
  query: string,
  limit: number,
  whereFilter?: Record<string, any>
): Promise<{ ids: number[]; distances: number[]; metadatas: any[] }> {
  await this.ensureCollectionExists();
  const results = await chromaMcp.callTool('chroma_query_documents', {
    collection_name: this.collectionName,
    query_texts: [query],
    n_results: limit,
    ...(whereFilter && { where: whereFilter }),
    include: ['documents', 'metadatas', 'distances']
  });
  // 去重：按 sqlite_id 保留第一个
  ...
}
```

---

## 3. 一致性失败的根因

### 3.1 五大差异点

| # | 差异点 | 影响 |
|---|--------|------|
| **D1** | **向量模型不同**：Java 用 `embedding_1024`（bge-m3），Chroma 用 ChromaMcpManager 指定的模型 | 同一 query 向量化后不同 → 检索结果不同 |
| **D2** | **混合策略不同**：Java 用 weighted hybrid（0.7×语义 + 0.3×全文），Chroma 用纯语义 | 结果排序不同 |
| **D3** | **去重策略不同**：Java 返回整行，Chroma 按 sqlite_id 保留第一个 doc | Chroma 可能丢失在非首选字段上匹配度高的 observation |
| **D4** | **时间窗口不同**：Java 硬编码 90 天，Chroma 的 `whereFilter` 可配置 | 超出 Java 90 天的老 observation 在 Chroma 仍可查 |
| **D5** | **异常处理不同**：Java pgvector 失败时 fallback 到 tsvector；Chroma 失败时直接抛错 | 一致性还取决于故障模式 |

### 3.2 典型不一致场景

**场景 1：embedding 模型差异**
- query: "数据库连接失败"
- Java 路径：用 bge-m3 1024 维 embedding
- Chroma 路径：用 ChromaMcpManager 配置的模型（可能是 openai 或本地模型）
- 结果：即使存储了同一批 observation，向量空间不同，top-k 结果不同

**场景 2：Chroma 多 doc 去重丢失**
- observation #123 有 5 个 field docs（narrative, fact_0, fact_1, text, errorSig）
- 用户 query "error pattern" 与 `errorSig` 字段最匹配，但 narrative 字段距离更近
- Chroma 去重后保留 narrative（距离更近的），丢弃 errorSig
- 结果：Java 能返回 observation #123（errorSig 匹配），Chroma 可能不返回

**场景 3：时间窗口差异**
- 100 天前的 observation 在 Java pgvector 中不可查（被 90 天过滤排除）
- Chroma 的 `whereFilter` 若未设置 `created_at_epoch` 过滤，该 observation 仍可被检索
- 结果：Chroma 能找到，Java 找不到

---

## 4. BlueCortexCE 当前状态

BlueCortexCE 目前是**纯 Java 架构**（无 Worker 层）：
- 所有 `/api/context/semantic` 直接走 Java pgvector
- `webui/` 子模块中的 ChromaSync 和 Worker 相关代码**未启用**

**当前情况**：BlueCortexCE 不存在 Java/Worker 不一致问题，因为 Worker 层（Chroma）不在部署路径中。

**但未来风险**：如果引入 `webui/` 的 Worker Hook 集成（session-init.ts 调用 Worker `/api/context/semantic`），就会面临一致性问题。

---

## 5. 行动项

| 优先级 | 行动项 | 说明 |
|--------|--------|------|
| **P0** | 确认 BlueCortexCE 实际部署路径 | 是否使用 Worker 层？若纯 Java，无一致性问题 |
| **P1** | 若使用 Worker：统一 embedding 模型 | ChromaMcpManager 的 embedding 模型必须与 Java 侧一致（bge-m3） |
| **P1** | 若使用 Worker：统一时间窗口 | Chroma `whereFilter` 必须设置与 Java 相同的 90 天 `created_at_epoch` 下限 |
| **P2** | 若使用 Worker：统一混合策略 | Chroma 应引入与 Java hybrid search 等效的 weighted scoring |
| **P2** | 跨栈一致性评测方案 | 相同 query 在双栈返回结果的 Jaccard 相似度；设定 ≥0.85 阈值 |
| **P3** | 考虑废弃 Chroma 层 | BlueCortexCE 作为纯 Java 系统，Worker（Chroma）层的价值需重新评估 |

---

## 6. 与 Doc 86 的关系

- Doc 86（`86-dual-stack-semantic-architecture.md`）：描述了双栈**架构差异**（两套独立向量系统）
- 本文（doc 107）：深入**一致性问题**的根因（D1-D5）和**实际影响**（3个典型场景）

两者互补，doc 86 是"是什么"，doc 107 是"不一致怎么办"。

---

## 7. Changelog

- 2026-05-05: 初始创建。基于 `11-research-backlog.md` Item #22 未勾选项，分析 Java pgvector 与 Worker Chroma 双栈语义一致性问题。识别 5 大根因差异点（D1-D5）和 3 个典型不一致场景。确认 BlueCortexCE 当前为纯 Java 架构无一致性问题，但未来引入 Worker 层时存在风险。
