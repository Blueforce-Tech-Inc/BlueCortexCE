# Holographic 三元存储系统深度解析

**来源**：`plugins/memory/holographic/`（`holographic.py` + `store.py` + `retrieval.py`）  
**日期**：2026-05-05  
**性质**：源码深度分析 + CE 对照

---

## 1. 概述

Holographic 是 Hermes Agent 内置的**本地向量存储 Provider**，无需外部服务。采用 **HRR（相位编码）** 而非浮点 embedding，存储在 SQLite 中。

三个核心模块：
- `holographic.py`：相位向量数学（bind/unbind/bundle/相似度）
- `store.py`：SQLite 持久化 + FTS5 全文索引 + 实体消解
- `retrieval.py`：混合召回（BM25 + HRR + trust weighting）

---

## 2. HRR 相位编码原理

### 核心思想

HRR 用**固定维度相位向量**表示概念。通过三角运算实现绑定/解绑/合并：

```python
def bind(a, b):       # 循环卷积 = 相位相加
    return (a + b) % _TWO_PI

def unbind(memory, key):  # 循环相关 = 相位相减
    return (memory - key) % _TWO_PI

def bundle(*vectors):     # 叠加 = 复指数的圆形均值
    complex_sum = np.sum([np.exp(1j * v) for v in vectors], axis=0)
    return np.angle(complex_sum) % _TWO_PI
```

### 跨平台可复现性

使用 SHA-256 而非 numpy RNG 生成原子向量：

```python
def encode_atom(word: str, dim: int = 1024) -> np.ndarray:
    values_per_block = 16  # SHA-256 = 32 bytes = 16 uint16
    blocks_needed = math.ceil(dim / values_per_block)
    uint16_values: list[int] = []
    for i in range(blocks_needed):
        digest = hashlib.sha256(f"{word}:{i}".encode()).digest()
        uint16_values.extend(struct.unpack("<16H", digest))
    phases = np.array(uint16_values[:dim], dtype=np.float64) * (_TWO_PI / 65536.0)
    return phases
```

结果：相同 word 在任何平台/进程生成完全相同的向量。

### SNR 容量估计

```python
def snr_estimate(dim: int, n_items: int) -> float:
    if n_items <= 0:
        return float("inf")
    snr = math.sqrt(dim / n_items)
    if snr < 2.0:
        logger.warning("HRR storage near capacity: SNR=%.2f (dim=%d, n_items=%d)", ...)
    return snr
```

SNR < 2.0 时检索精度开始下降，对应 `n_items > dim / 4`。

---

## 3. SQLite 三表结构

### `facts` 表（核心存储）

```sql
CREATE TABLE facts (
    fact_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    content         TEXT NOT NULL UNIQUE,
    category        TEXT DEFAULT 'general',
    tags            TEXT DEFAULT '',
    trust_score     REAL DEFAULT 0.5,      -- 0.0-1.0 信任分
    retrieval_count INTEGER DEFAULT 0,       -- 被检索次数
    helpful_count   INTEGER DEFAULT 0,       -- 用户标记 helpful
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    hrr_vector      BLOB                    -- 相位向量（dim/8 bytes）
);
```

### `entities` 表

```sql
CREATE TABLE entities (
    entity_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    entity_type TEXT DEFAULT 'unknown',
    aliases     TEXT DEFAULT '',
    created_at  TIMESTAMP
);
```

### `fact_entities` 关联表

```sql
CREATE TABLE fact_entities (
    fact_id   INTEGER REFERENCES facts(fact_id),
    entity_id INTEGER REFERENCES entities(entity_id),
    PRIMARY KEY (fact_id, entity_id)
);
```

### FTS5 虚拟表 + 触发器

```sql
CREATE VIRTUAL TABLE facts_fts USING fts5(content, tags, content=facts, content_rowid=fact_id);

CREATE TRIGGER facts_ai AFTER INSERT ON facts BEGIN
    INSERT INTO facts_fts(rowid, content, tags) VALUES (new.fact_id, new.content, new.tags);
END;
CREATE TRIGGER facts_ad AFTER DELETE ON facts BEGIN
    INSERT INTO facts_fts(facts_fts, rowid, content, tags) VALUES ('delete', old.fact_id, old.content, old.tags);
END;
CREATE TRIGGER facts_au AFTER UPDATE ON facts BEGIN
    INSERT INTO facts_fts(facts_fts, rowid, content, tags) VALUES ('delete', old.fact_id, old.content, old.tags);
    INSERT INTO facts_fts(rowid, content, tags) VALUES (new.fact_id, new.content, new.tags);
END;
```

### `memory_banks` 表

按 bank_name 分组存储叠加向量（用于批量检索）：

```sql
CREATE TABLE memory_banks (
    bank_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_name  TEXT NOT NULL UNIQUE,
    vector     BLOB NOT NULL,
    dim        INTEGER NOT NULL,
    fact_count INTEGER DEFAULT 0,
    updated_at TIMESTAMP
);
```

---

## 4. Trust Scoring 机制

```python
_HELPFUL_DELTA   =  0.05   # 每标记一次 helpful +0.05
_UNHELPFUL_DELTA = -0.10   # 每标记一次 unhelpful -0.10
```

检索时 trust_score 作为乘法因子：最终分数 = 相关性 × trust_score

---

## 5. 混合检索管道（FactRetriever）

```python
def __init__(self, store, temporal_decay_half_life=0,
             fts_weight=0.4, jaccard_weight=0.3, hrr_weight=0.3, hrr_dim=1024):
```

三路加权召回：
1. **BM25/FTS5**（40%）：SQLite FTS5 全文搜索候选
2. **Jaccard 重排**（30%）：query tokens 与 fact tokens 的 Jaccard 相似度
3. **HRR**（30%）：query 的相位向量与 fact 的 hrr_vector 相似度

```python
def search(self, query, category=None, min_trust=0.3, limit=10):
    # Stage 1: FTS5 取 limit*3 候选
    candidates = self._fts_candidates(query, category, min_trust, limit * 3)
    # Stage 2: Jaccard 重排 + trust weighting + temporal decay
    for fact in candidates:
        jaccard = self._jaccard_similarity(query_tokens, all_tokens)
        final_score = jaccard * fact["trust_score"] * decay
    # Stage 3: 返回 top limit
    return sorted(scored, key=lambda x: x["score"], reverse=True)[:limit]
```

---

## 6. 与 BlueCortexCE 对照

| 方面 | Hermes Holographic | BlueCortexCE |
|------|-------------------|--------------|
| 向量存储 | SQLite BLOB（相位向量） | PostgreSQL + pgvector（浮点向量） |
| 全文搜索 | SQLite FTS5 + 触发器 | 全文索引（PostgreSQL tsvector） |
| 实体消解 | 独立 entities 表 | Observation entity extraction |
| 信任机制 | helpful/unhelpful 反馈 | 无 |
| 检索方式 | BM25 + Jaccard + HRR 三路混合 | pgvector 语义相似度（单一） |
| 可复现性 | SHA-256 确定性编码 | 依赖外部 embedding API |
| 无外部依赖 | ✅ 完全本地 | ❌ 需要 pgvector 扩展 |

---

## 7. 可执行借鉴

### 短期

1. **多路召回**：CE 的 `SearchService` 可以引入 BM25 作为第一路候选（加速 + 关键词匹配），再用 pgvector 重排
2. **Trust 反馈机制**：增加 `helpful_count` / `unhelpful_count` 字段，记录用户反馈，检索时加权

### 中期

3. **实体消解**：在 Observation 提取时建立 entity 表，支持按实体类型筛选
4. **Temporal Decay**：增加时间衰减，`created_at` 影响检索权重

### 长期

5. **本地 HRR**：考虑将 HRR 作为轻量级本地缓存（无 embedding API 依赖），与 pgvector 形成双层召回
