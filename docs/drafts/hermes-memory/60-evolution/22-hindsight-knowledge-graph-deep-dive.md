# Hindsight 知识图谱深度解析 — v7.1

> **来源**: [Hindsight Official Docs](https://hindsight.vectorize.io/) + Hermes Agent `plugins/memory/hindsight/__init__.py` (883 lines)
> **快照时间**: 2026-04-23
> **前置**: `18-three-new-memory-providers.md` §2（Hindsight 摘要，约 80 行）
> **目的**: 深入解析 Hindsight 知识图谱架构，为 BlueCortexCE 提供可借鉴的记忆系统设计思想

---

## 1. 核心定位：为什么 Hindsight 不是 RAG

传统 RAG 是"语义相似片段检索"，Hindsight 声称是"结构化记忆 + 时序推理 + 实体理解 + 信念形成"。

| 维度 | 传统 RAG | Hindsight |
|------|----------|-----------|
| 检索策略 | 纯语义相似 | **语义 + BM25 + 知识图谱 + 时序** 四路并行 |
| 多跳推理 | 受限于 chunk 相似度 | **图遍历** 跨实体关系链 |
| 时序查询 | 关键词匹配（"spring"） | **时间解析 + 范围过滤** |
| 实体理解 | 无 | **实体消解 + 共现追踪** |
| 知识巩固 | 无状态 | **信念体系 + 观察更新** |
| Disposition | 无 | **3 维度气质（质疑/字面/共情）** |

### 1.1 典型失败案例 vs Hindsight

**多跳推理**:
```
存储:
- "Alice 是 Project Atlas 技术负责人"
- "Project Atlas 使用 Kubernetes"
- "Kubernetes 集群周二发生故障"

Query: "Alice 受最近故障影响了吗？"
RAG → 只返回 Alice 相关事实（"故障"与 Alice 无语义相似）
Hindsight → Alice → Project Atlas → Kubernetes → outage（图遍历链路）
```

**时序查询**:
```
存储:
- March: "Alice 开始微服务迁移"
- April: "Alice 完成 auth 服务"
- October: "Alice 专注于性能"

Query: "Alice 去年春天做了什么？"
RAG → 返回所有 Alice 事实（忽略日期）
Hindsight → 解析 "last spring" = March-May → 范围过滤
```

---

## 2. 四层记忆类型（Memory Types）

Hindsight 将记忆组织为层级结构，优先级从高到低：

### 2.1 Mental Models（用户策划摘要）

| 维度 | 说明 |
|------|------|
| **性质** | 用户/管理员预先创建的常询问题答案 |
| **优先级** | Reflect 循环中**最先检查**（最高优先） |
| **用途** | 常见问题的高质量固定答案 |
| **创建方式** | 用户通过 API 或 Reflect 结果保存 |

Mental Models 本质上是"已知问题的高分答案"，相当于 CE 的 `SummaryEntity` 但更结构化（预定义问题 + 预生成答案）。

### 2.2 Observations（观察/信念）

| 维度 | 说明 |
|------|------|
| **性质** | 从 raw facts **自动合并** 形成的信念 |
| **特征** | 有证据引用（含原文引用）、有证明计数 |
| **更新策略** | 新证据支持/否定/扩展时**更新而非覆盖**（历史保留） |
| **新鲜度趋势** | stable / strengthening / weakening / stale |
| **验证机制** | stale 状态下 Reflect 自动回退验证 raw facts |

**与 CE 的关键差异**:
- CE Summary 是覆盖式更新（`mergeAppendOnly` 解决了部分问题）
- Hindsight Observations 是累积式更新 + 趋势标记

**CE 借鉴**:
- 为 `SummaryEntity` 增加 `trend` 字段（stable/strengthening/weakening/stale）
- 每次 LLM re-summary 时，判断新旧证据关系

### 2.3 World Facts（世界事实）

| 维度 | 说明 |
|------|------|
| **性质** | 关于他人/地点/事物的客观事实 |
| **示例** | "Alice 在 Google 工作" |
| **触发** | Retain 操作自动提取 |

### 2.4 Experience Facts（经验事实）

| 维度 | 说明 |
|------|------|
| **性质** | Bank 自己执行的动作和交互 |
| **示例** | "我向 Alice 推荐了 Python" |
| **来源** | retain() 时 LLM 判定为"自身行为" |

---

## 3. TEMPR 四路并行检索

Recall 时，四种检索策略**同时执行**，结果通过 RRF（Reckprocal Rank Fusion）融合，再经 cross-encoder 重排。

### 3.1 语义搜索（Semantic）

- 基于 embedding 的向量相似度
- 处理概念匹配：`"Alice 的工作"` → `"Alice works as a software engineer"`
- 同义词扩展：`"meeting"` 匹配 `"conference"`, `"discussion"`, `"gathering"`

### 3.2 关键词搜索（BM25）

- 传统 BM25 算法
- 处理专有名词：`"Google"`, `"Alice Chen"`, `"MIT"`
- 处理技术术语：`"PostgreSQL"`, `"HNSW"`, `"TensorFlow"`
- 处理唯一标识符：URL、产品名、专有短语

**BM25 在 BM25 层面解决"语义搜不到专有词"的问题**，是 semantic search 的必要补充。

### 3.3 图遍历（Graph Traversal）

- 在知识图谱上**按实体关系跳步**
- 支持多跳：`Alice → Project Atlas → Kubernetes → outage`
- 通过**共现模式**发现间接关系

图遍历的独特价值：**即使 Alice 和她的 manager 从未同时出现，也能通过 shared projects/teams 找到关联**。

### 3.4 时序搜索（Temporal）

- 解析时间表达式：`"last spring"`, `"June 2024"`, `"before Alice joined Google"`
- 结合语义理解和时间过滤
- **双时间模型**：事件发生时间 + 记录时间（见 §5）

### 3.5 RRF 融合 + Cross-Encoder 重排

```
融合公式（RRF）:
score(item) = Σ 1/(k + rank_i(item))  for each strategy i
```

- 在多个策略中出现 → 排名更高（共识效应）
- rank > raw score（跨策略可比性）
- 最终 cross-encoder rerank：考虑 query-memory 交互

**CE 借鉴**:
- CE 目前只有向量语义搜索
- 可以补充：BM25 关键词索引（PostgreSQL GIN 索引天然支持）、时间过滤、图遍历（pgvector + 实体关系）

---

## 4. 实体识别与消解（Entity Recognition & Resolution）

### 4.1 自动实体提取

Retain 时自动识别并追踪实体：

| 实体类型 | 示例 |
|----------|------|
| 人物 | "Alice", "Dr. Smith", "Bob Chen" |
| 组织 | "Google", "MIT", "OpenAI" |
| 地点 | "Paris", "Central Park", "California" |
| 产品/概念 | "Python", "TensorFlow", "machine learning" |

### 4.2 实体消解（Entity Resolution）

同一实体的不同提及被统一：

```
"Alice" + "Alice Chen" + "Alice C." → 同一人物
"Bob" + "Robert Chen" → 同一人物（昵称消解）
```

### 4.3 共现消歧（Context-Aware Disambiguation）

如果新的 "Alice" 提及与 "Google" 和 "Stanford" 多次共现，则新 Alice 极可能是同一人。Hindsight 用**共现模式**消歧常见名字。

**CE 借鉴**:
- CE 目前ObservationEntity 有 `source/concepts` 字段，但无显式实体消解
- 可在 `ObservationService` 增加 entity resolution pass

### 4.4 Entity Labels（实体标签）

用户可定义受控词表作为 key:value 分类标签（如 `pedagogy:scaffolding`）。标签在 retain 时被提取为实体，自动在知识图谱中创建关联链接，同时可以写入 memory unit 的 tags 字段。

---

## 5. 双时间模型（Bitemporal Model）

Hindsight 追踪每个事实的两个时间维度：

### 5.1 事件时间（When It Happened）

- **事件型事实**：记录事件发生时间
  - `"Alice 2024 年 6 月结婚"` → 发生于 June 2024
- **一般事实**：无特定发生时间
  - `"Alice 偏好 Python"` → 持续偏好（ongoing）

### 5.2 记录时间（When You Learned It）

Hindsight 还追踪**何时将该事实告知系统**。

**为什么两者都重要？**

```
场景：2025 年 1 月，有人告诉你 "Alice 2024 年 6 月结婚了"
Query "Alice 2024 年做了什么？" → 历史查询生效（找到婚姻）
Query "Alice 最近发生了什么？" → 近期提及优先（recency ranking）
Query "Alice 结婚前发生了什么？" → 时序推理生效（before/after）
```

**CE 借鉴**:
- CE ObservationEntity 有 `createdAt`（记录时间）
- **缺少事件时间**：无法做"last spring"类时序查询
- 建议增加 `eventTime`（可选字段）用于时序推理

---

## 6. 知识图谱连接类型（4 种）

Retain 后，Hindsight 在知识图谱中自动创建四种连接：

### 6.1 实体连接（Entity Connections）

同一实体提及的所有事实互相链接。
→ 查询 "Alice 的一切" → 检索所有 Alice 相关事实

### 6.2 时序连接（Time-Based Connections）

时间接近的事实互相链接，越近的链接越强。
→ 查询 "那时还发生了什么？" → 找到上下文相关事件

### 6.3 语义连接（Meaning-Based Connections）

即使措辞不同，语义相似的事实互相链接。
→ 查询 "类似话题" → 找到主题相关信息

### 6.4 因果连接（Causal Connections）

因果关系被显式追踪。
→ 查询 "为什么会这样？" → 追踪推理链
```
Example: "Alice 感到疲惫" ← caused by ← "她每周工作 80 小时"
```

---

## 7. Observation 合并机制（Observation Consolidation）

Retain 后，Hindsight 后台自动将相关 facts 合并为 Observations。

### 7.1 去重（Deduplication）

重叠的事实被合并为单一持久观察，而非堆积为重复项。

### 7.2 证据追踪（Evidence Tracking）

每个 Observation 引用源 memories（含精确原文引用）+ 证明计数。

### 7.3 持续精化（Continuous Refinement）

- 新证据**支持/否定/扩展**现有 Observation 时，Observation 被更新（不是覆盖）
- 历史保留
- 每个 Observation 携带计算出的新鲜度趋势

### 7.4 与 CE 的对比

| 维度 | Hindsight Observations | CE SummaryEntity |
|------|------------------------|-----------------|
| 更新策略 | 累积式更新 + 趋势 | `mergeAppendOnly` 增量合并 |
| 证据追踪 | 引用 + 证明计数 | 无（只有 `observationsReferenced`） |
| 新鲜度趋势 | stable/strengthening/weakening/stale | 无 |
| 验证机制 | stale → 回退 raw facts | 无 |
| 来源追踪 | 精确原文引用 | chunk references |

---

## 8. Reflect — 反思推理引擎

Reflect 是 Hindsight 与其他记忆系统的核心差异：**不是检索，是推理**。

### 8.1 Agentic Loop（自主推理循环）

Reflect 在循环中运行，可访问的工具：

| 工具 | 用途 | 优先级 |
|------|------|--------|
| `search_mental_models` | 用户策划摘要 | 最高（最先检查） |
| `search_observations` | 合并知识 | 高 |
| `recall` | Raw facts（地面truth） | 降级 |
| `expand` | 获取记忆的更多上下文 | 按需 |
| `done` | 完成最终答案 | 就绪时 |

循环规则：
- **必须先收集证据**（guardrail，防止空回答）
- 最多 10 次迭代
- 引用验证 — 只能引用实际检索到的 ID

### 8.2 与 CE 的对比

CE 的 context generate 逻辑是**单程检索 + 组装**（Observation → Summary → ICL prompt），没有自主推理循环。

**Hindsight reflect = CE context generate + 自主多跳探索 + Disposition 过滤**

### 8.3 Disposition System（气质/态度系统）

Reflect 时应用的性格配置：

| 维度 | 低 (1) | 高 (5) |
|------|--------|--------|
| **Skepticism（质疑）** | 信任，接收信息为表面价值 | 质疑，怀疑和质疑主张 |
| **Literalism（字面）** | 灵活解读，读出言外之意 | 字面解读，按表面价值理解 |
| **Empathy（共情）** |  detachment，关注事实 | 共情，考虑情感上下文 |

### 8.4 Mission + Directives

| 配置项 | 性质 | 影响范围 |
|--------|------|----------|
| **Mission** | 自然语言身份描述 | Reflect 推理框架 |
| **Directives** | 必须遵守的硬规则 | Reflect 全部回答 |
| **Disposition Traits** | 软性性格特征 | Reflect 解读风格 |

示例：
```python
client.update_bank_config(
    "architect-bank",
    reflect_mission="You're a senior software architect — keep track of system designs, "
                   "technology decisions, and architectural patterns. Prefer simplicity over cutting-edge.",
    disposition_skepticism=4,  # 质疑新技术
    disposition_literalism=4,  # 关注具体规格
    disposition_empathy=2,      # 优先技术事实
)
```

---

## 9. 与 BlueCortexCE 的架构对照

| Hindsight 特性 | CE 当前实现 | 借鉴价值 | 实施难度 |
|----------------|-------------|----------|----------|
| TEMPR 四路检索 | 仅向量语义搜索 | 高（BM25 + 时序 + 图遍历） | 高 |
| 实体消解 | ObservationEntity 有概念，无显式消解 | 高 | 中 |
| Observation 合并 | SummaryEntity mergeAppendOnly | 中（趋势追踪/证据引用） | 中 |
| 双时间模型 | 仅有 createdAt | 高（eventTime 字段） | 低 |
| Mental Models | 无 | 中（预计算常询摘要） | 低 |
| Reflect Agentic Loop | 单程检索，无自主探索 | 高 | 高 |
| Disposition System | 无 | 中（AI persona 配置） | 中 |
| Causal Connections | 无 | 高（推理链追踪） | 高 |
| Entity Labels | 无 | 中（概念标签 → 图链接） | 低 |

---

## 10. 可执行借鉴项（按实施难度排序）

### 低难度（直接可做）

1. **eventTime 字段**：ObservationEntity 增加可选 eventTime 字段，SearchService 增加时间范围过滤
2. **Observation 趋势**：SummaryEntity 增加 trend 字段（stable/strengthening/weakening/stale）
3. **Entity Labels**：ObservationEntity 增加 labels 字段，支持 key:value 格式，影响检索

### 中难度（需设计）

4. **BM25 关键词索引**：利用 PostgreSQL GIN/PG_TRGM 索引支持关键词搜索
5. **Disposition System**：通过 system prompt 模板实现 persona 配置（ skeptic/emphatic/literal）
6. **Evidence Tracking**：SummaryEntity 增加 evidenceCount + sourceQuotes 字段

### 高难度（需架构重构）

7. **TEMPR 四路检索**：向量 + BM25 + 图遍历 + 时序 融合（需要重新设计 SearchService）
8. **Reflect Agentic Loop**：实现自主多跳探索（类似 Hindsight 的 agentic tool loop）
9. **Causal Connections**：抽取因果关系三元组，建立因果图谱

---

## 11. 文件关联

- 摘要：`docs/drafts/hermes-memory/60-evolution/18-three-new-memory-providers.md` §2
- Provider 注册：`docs/drafts/hermes-memory/60-evolution/14-multi-provider-plugin-discovery.md`
- 文档入口：`docs/drafts/hermes-memory/index.md`
- 全局导航：`docs/drafts/memory-research-hub.md`
- CE 借鉴总表：`docs/drafts/hermes-memory/20-recommendations/02-bluecortexce-recommendations.md`

## 12. 研究待跟进

- [ ] Hindsight `local_embedded` daemon 内部 PostgreSQL schema（实体表/关系表/observation 表）
- [ ] TEMPR fusion RRF 参数 k 的最优值（文档未披露）
- [ ] cross-encoder rerank 模型选择
- [ ] causal connection 抽取的 prompt 设计
- [ ] Hindsight 基准测试 LongMemEval 具体任务分类
