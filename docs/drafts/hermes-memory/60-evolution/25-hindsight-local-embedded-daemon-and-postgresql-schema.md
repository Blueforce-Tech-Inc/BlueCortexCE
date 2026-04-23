# Hindsight 本地嵌入 Daemon 与 PostgreSQL Schema 分析 — v7.2

> **来源**: [Hindsight 官方文档](https://hindsight.vectorize.io/developer/installation) + Hermes Agent `plugins/memory/hindsight/__init__.py` (883 lines) + `hermes_cli/memory_setup.py`
> **快照时间**: 2026-04-23
> **前置**: `18-three-new-memory-providers.md` §2（Hindsight 摘要）+ `22-hindsight-knowledge-graph-deep-dive.md`（知识图谱架构）
> **目的**: 深度解析 Hindsight 本地嵌入模式的 daemon 架构和 PostgreSQL schema，为 BlueCortexCE 提供可借鉴的本地持久化 + 向量存储设计思想

---

## 1. 概述：为什么需要本地嵌入模式

Hindsight 提供两种部署形态：

| 形态 | 说明 | 适用场景 |
|------|------|---------|
| **Cloud API** | 连接 `api.hindsight.vectorize.io` | 零基础设施，快速上手 |
| **本地嵌入** | Daemon 进程运行在本地，内置 PostgreSQL | 隐私敏感、低延迟、无外网访问 |

Hermes Agent 的 HindsightMemoryProvider 支持三种模式：

```python
# plugins/memory/hindsight/__init__.py §1
# Cloud API
HINDSIGHT_MODE=cloud  # 默认，连接官方 API

# 本地外部（连接已有的 Hindsight server）
HINDSIGHT_MODE=local_external  # → http://localhost:8888

# 本地嵌入（自动启动 daemon）
HINDSIGHT_MODE=local_embedded  # → hindsight-all 包，内置 PostgreSQL
```

本地嵌入模式的核心价值：
- **数据不出本地** — 所有 LLM 提取在本地完成
- **零外部依赖** — 不需要互联网访问
- **进程隔离** — Daemon 可被多个 Hermes 会话共享
- **自动生命周期管理** — 首次使用时启动，5 分钟空闲后自动停止

---

## 2. 包架构：`hindsight-all` vs `hindsight-api`

Hindsight 提供多个 pip 包：

| 包名 | 内容 | 用途 |
|------|------|------|
| `hindsight-api` | API Server (HTTP) | 部署为独立服务 |
| `hindsight-api-slim` | API Server（需要外部 embedding/reranking） | 资源受限环境 |
| `hindsight-all` | **完整嵌入包**（含 `HindsightEmbedded` + `HindsightServer`） | 本地嵌入模式 |
| `hindsight-all-slim` | 轻量版嵌入包 | 本地轻量模式 |
| `hindsight-client` (≥0.4.22) | HTTP 客户端库 | 连接 Cloud 或外部 API |

### 2.1 两个嵌入类

```python
# hindsight-all 提供两个嵌入类

# 方式 1: In-process（HindsightServer）
# 服务运行在应用进程的后台线程中
from hindsight import HindsightServer, HindsightClient

with HindsightServer(llm_provider="openai", llm_api_key="sk-xxx") as server:
    client = HindsightClient(base_url=server.url)
    results = client.recall(bank_id="alice", query="...")

# 方式 2: Managed subprocess（HindsightEmbedded）⭐ Hermes 使用这个
# 服务作为后台守护进程运行，可在多个 Python 进程/会话间共享
from hindsight import HindsightEmbedded

client = HindsightEmbedded(
    profile="hermes",           # 配置 profile 名称
    llm_provider="openai",
    llm_api_key="sk-xxx",
    llm_model="gpt-4o-mini"
)
# 首次调用时自动启动 daemon
results = client.recall(bank_id="alice", query="...")
```

Hermes 的 HindsightMemoryProvider 使用 `HindsightEmbedded`，因为：
1. Daemon 可跨会话共享（多个 Hermes CLI 调用共用同一个 daemon）
2. 进程隔离，崩溃不影响主进程
3. 空闲自动退出，不占用资源

---

## 3. Daemon 生命周期管理

### 3.1 `hindsight_embed.daemon_embed_manager`

```python
# plugins/memory/hindsight/__init__.py §570-618
import hindsight_embed.daemon_embed_manager as dem
from rich.console import Console

# 重定向 Rich 控制台输出到日志文件
dem.console = Console(file=open(log_path, "a"), force_terminal=False)

client = self._get_client()
client._ensure_started()  # 启动 daemon（如未运行）
```

Daemon 管理器负责：
- 启动/停止 daemon 子进程
- 检测 daemon 是否运行（`is_running(profile)`）
- 配置变更时热重启
- 空闲超时自动退出（5 分钟）

### 3.2 Profile 机制

Daemon 配置通过 **profile** 组织，存储在 `~/.hindsight/profiles/<profile>.env`：

```bash
# ~/.hindsight/profiles/hermes.env
HINDSIGHT_API_LLM_PROVIDER=openai
HINDSIGHT_API_LLM_API_KEY=sk-xxx
HINDSIGHT_API_LLM_MODEL=gpt-4o-mini
HINDSIGHT_API_LOG_LEVEL=info
# 可选：HINDSIGHT_API_LLM_BASE_URL=http://localhost:8080/v1
```

Hermes 的 HindsightMemoryProvider 在初始化时：
1. 检查当前 config 与 `profile.env` 中的保存值是否一致
2. 如不一致（LLM provider/model/key 变更），写入新的 `.env` 并重启 daemon
3. 这样确保 daemon 始终使用最新的 LLM 配置

### 3.3 Daemon 日志路径

| 类型 | 路径 | 说明 |
|------|------|------|
| **Hermes 插件启动日志** | `~/.hermes/logs/hindsight-embed.log` | 插件层面的 daemon 启动日志 |
| **Hindsight daemon 运行时日志** | `~/.hindsight/profiles/<profile>.log` | Hindsight 服务自身的运行日志 |

这意味着两层日志分离：插件层记录 "daemon 启动了/失败了"，daemon 层记录 "retain/recall 请求处理详情"。

### 3.4 启动触发时机

```python
# plugins/memory/hindsight/__init__.py §547-630
if self._mode == "local_embedded":
    def _start_daemon():
        # ... 异步启动 daemon
        client._ensure_started()

    t = threading.Thread(target=_start_daemon, daemon=True, name="hindsight-daemon-start")
    t.start()
    # 不等待完成，主线程继续
```

Daemon 启动是**异步非阻塞**的：主线程在后台线程中启动 daemon，立即返回。第一次实际使用（如 `retain()` / `recall()`）会等待 daemon 就绪。

---

## 4. PostgreSQL Schema 架构

### 4.1 数据库存储位置

| 模式 | 存储位置 | 说明 |
|------|---------|------|
| 嵌入（pg0） | `~/.hindsight/data/` | 完整 PostgreSQL 数据文件 |
| 外部 | `HINDSIGHT_API_DATABASE_URL` | 自建 PostgreSQL |

```bash
# 嵌入模式：数据目录
~/.hindsight/data/
```

### 4.2 Schema 设计（基于官方文档推断）

Hindsight 的数据库 schema 支持知识图谱 + 向量搜索：

#### 核心表（推断）

```
banks
├── id (PK)
├── name (unique)
├── mission (TEXT)          -- 身份描述
├── directives (TEXT[])     -- 硬规则
├── disposition (JSONB)     -- 气质参数 {skepticism, literalism, empathy}
├── retain_mission (TEXT)   -- 提取指导
├── created_at / updated_at

documents
├── id (PK)
├── bank_id (FK → banks.id)
├── document_id (TEXT, external)  -- caller-supplied idempotency key
├── content_hash (TEXT)    -- 用于增量检测
├── tags (TEXT[])
├── metadata (JSONB)
├── created_at / updated_at
├── retention_timestamp (TIMESTAMPTZ)  -- 事件发生时间
├── ingestion_timestamp     -- 收入系统时间

memories / facts
├── id (PK)
├── bank_id (FK → banks.id)
├── document_id (FK → documents.id)
├── fact_type (TEXT)        -- 'world' | 'experience'
├── content (TEXT)          -- 提取的事实文本
├── entities (JSONB)        -- [{name, type, canonical_id}]
├── when_occurred (TSTZRANGE)  -- 事件发生时间范围
├── when_learned (TIMESTAMPTZ) -- 收入系统时间
├── tags (TEXT[])
├── source_chunk (TEXT)     -- 原始 chunk 片段
├── embedding (vector(1536))  -- fact 内容向量
├── metadata (JSONB)
├── created_at / updated_at

observations
├── id (PK)
├── bank_id (FK → banks.id)
├── content (TEXT)          -- 综合后的观察
├── observation_type (TEXT) -- 'mental_model' | 'consolidated'
├── scope_tags (TEXT[])     -- 观察的标签范围
├── trend (TEXT)            -- 'stable' | 'strengthening' | 'weakening' | 'stale'
├── proof_count (INT)       -- 支持证据数
├── supporting_facts (JSONB) -- [{fact_id, quote, source_doc_id}]
├── freshness (TIMESTAMPTZ) -- 最新证据时间
├── created_at / updated_at

entities (知识图谱节点)
├── id (PK)
├── bank_id (FK → banks.id)
├── canonical_name (TEXT)
├── aliases (TEXT[])         -- 实体别名（消解用）
├── entity_type (TEXT)      -- 'PERSON' | 'ORG' | 'CONCEPT' | ...
├── embedding (vector(1536))
├── co_occurrence_graph (JSONB)  -- 共现关系
├── created_at / updated_at

entity_links (知识图谱边)
├── id (PK)
├── from_entity_id (FK → entities.id)
├── to_entity_id (FK → entities.id)
├── relation_type (TEXT)    -- 'co_occurrence' | 'causal' | 'temporal' | 'semantic'
├── weight (FLOAT)
├── source_fact_ids (UUID[]) -- 支持该链接的事实

tags
├── id (PK)
├── bank_id (FK → banks.id)
├── tag (TEXT, unique within bank)
├── memory_count (INT)      -- 聚合计数（避免 JOIN）
```

#### 向量索引配置

```sql
-- 默认: pgvector with HNSW
CREATE EXTENSION vector;

-- 可选: pgvectorscale with DiskANN（生产大规模）
CREATE EXTENSION vectorscale;

-- 可选: vchord（高维向量 + 内置 BM25）
CREATE EXTENSION vchord_bm25;
```

#### 全文搜索配置

```sql
-- 支持三种 text search backend
-- native: PostgreSQL 内置 tsvector + GIN
-- vchord: vchord_bm25（llmlingua2 分词）
-- pg_textsearch: Timescale BM25 + Block-Max WAND
```

### 4.3 多 Schema 支持

```bash
# 通过环境变量使用自定义 schema
HINDSIGHT_API_DATABASE_SCHEMA=hindsight
# 或完整连接字符串
HINDSIGHT_API_DATABASE_URL=postgresql://user:pass@host:5432/hindsight
```

支持自定义 schema，便于：
- 多租户隔离（每个租户独立 schema）
- 共享 PostgreSQL 实例但表隔离
- 平台部署（Supabase 等限制 public schema 的场景）

---

## 5. 连接池与并发配置

### 5.1 连接池参数

```bash
HINDSIGHT_API_DB_POOL_MIN_SIZE=5      # 默认最小连接
HINDSIGHT_API_DB_POOL_MAX_SIZE=100    # 默认最大连接
HINDSIGHT_API_DB_COMMAND_TIMEOUT=60    # SQL 命令超时（秒）
HINDSIGHT_API_DB_ACQUIRE_TIMEOUT=30    # 连接获取超时（秒）
```

**高并发场景**：每个 recall/think 并发操作使用 2-4 个连接。

### 5.2 Migration 策略

```bash
# 自动迁移（默认开启）
HINDSIGHT_API_RUN_MIGRATIONS_ON_STARTUP=true

# 手动迁移（通过 CLI）
hindsight-admin run-db-migration
hindsight-admin run-db-migration --schema tenant_acme

# 专用 migration 连接（绕过 PgBouncer 等连接池）
HINDSIGHT_API_MIGRATION_DATABASE_URL=postgresql://...
```

---

## 6. LLM Provider 支持（Daemon 内置提取）

### 6.1 支持的 Provider

| Provider | 配置方式 | 本地/远程 |
|----------|---------|---------|
| `openai` | API Key | 远程 |
| `anthropic` | API Key | 远程 |
| `gemini` | API Key | 远程 |
| `groq` | API Key | 远程（低延迟） |
| `openrouter` | API Key | 远程 |
| `minimax` | API Key | 远程 |
| `ollama` | Base URL (localhost) | 本地 |
| `lmstudio` | Base URL (localhost) | 本地 |
| `llamacpp` | 内置推理，无需外部服务 | 本地 |
| `openai_compatible` | Base URL + Key | 远程/本地 |
| `vertexai` | GCP Project | 远程 |
| `bedrock` | AWS | 远程 |
| `litellm` | 统一网关 | 远程 |
| `none` | 纯向量模式 | N/A |

### 6.2 Hermes 的 Provider 映射

```python
# plugins/memory/hindsight/__init__.py §586-588
# Map openai_compatible/openrouter → openai for the daemon
daemon_provider = "openai" if current_provider in ("openai_compatible", "openrouter") else current_provider
```

Hermes 支持 `openai_compatible` 端点（如本地 vLLM、llama.cpp），但 Hindsight daemon 内部统一映射到 `openai` wire format。

---

## 7. Docker 部署对比（本地嵌入的替代方案）

```bash
# 方式 1: Docker 单容器
docker run --rm -it -p 8888:8888 -p 9999:9999 \
  -e HINDSIGHT_API_LLM_API_KEY=$OPENAI_API_KEY \
  -v $HOME/.hindsight-docker:/home/hindsight/.pg0 \
  ghcr.io/vectorize-io/hindsight:latest

# 方式 2: pip 安装（bare metal）
pip install hindsight-api
export HINDSIGHT_API_LLM_PROVIDER=groq
export HINDSIGHT_API_LLM_API_KEY=gsk_xxx
hindsight-api

# 方式 3: 嵌入（Hermes 用的方式）
pip install hindsight-all
# HindsightEmbedded 自动管理 daemon
```

Docker 镜像规格：
- **Full**: ~9 GB (AMD64) / ~3.7 GB (ARM64)
- **Slim**: ~500 MB（需要外部 embedding/reranking 服务）

---

## 8. 对 BlueCortexCE 的借鉴意义

### 8.1 可借鉴的架构思想

#### ① 向量存储分层选择

| 场景 | Hindsight 推荐 | CE 可借鉴 |
|------|-------------|---------|
| 小规模（<1M 向量） | pgvector (HNSW) | ✅ 直接用 pgvector |
| 大规模生产（10M+） | pgvectorscale (DiskANN) | ✅ 考虑迁移到 pgvectorscale |
| 高维向量（3000+） | vchord | 特殊场景 |
| 简单部署 | 嵌入式 pg0 | ⚠️ CE 不需要，外部 PostgreSQL 足够 |

**行动项**：CE 当前使用 pgvector，可以评估 pgvectorscale 的 p95 延迟改进。

#### ② 进程隔离的 Daemon 模式

Hindsight 的 `HindsightEmbedded` 模式将记忆服务作为独立进程管理，这对 CE 的架构启示：

```
当前 CE: Java Backend (Spring Boot) 直接连接 PostgreSQL
         ↓
Hindsight 模式: Daemon 进程 ↔ PostgreSQL（独立生命周期）
         ↓
CE 可借鉴: 将某些重型 LLM 操作（extraction、embedding）
           拆分为独立 Worker 进程，通过消息队列交互
```

#### ③ Profile 配置隔离

CE 可以学习 Hindsight 的 profile 概念，为不同使用场景（dev/staging/prod）或不同用户群体维护独立的配置快照。

#### ④ 多租户 Schema 隔离

Hindsight 支持 `DATABASE_SCHEMA` 隔离，CE 可以考虑对多租户场景使用独立的 schema 而非独立数据库。

#### ⑤ 全文搜索的多 backend 支持

Hindsight 支持三种 BM25 backend（native/vchord/pg_textsearch），CE 的全文搜索也可以考虑 pgvector 的 `paradedb` 或 `pg_sparse` 扩展。

### 8.2 当前 CE 与 Hindsight 的能力对照

| 能力 | Hindsight | CE 当前 | 差距 |
|------|-----------|---------|------|
| 知识图谱实体消解 | ✅ 完整 | ❌ 无 | 需要实现 |
| Observation 合并 | ✅ 自动异步 | ❌ 无 | 需要实现 |
| 多路检索（TEMPR） | ✅ 4路并行 | ⚠️ 简单向量 | 增强检索 |
| 本地嵌入 Daemon | ✅ hindsight-all | ❌ 不需要 | N/A |
| 外部 LLM 提取 | ✅ 所有 provider | ✅ Spring AI | 相当 |
| 向量扩展选择 | ✅ pgvector/pgvectorscale/vchord | ⚠️ 仅 pgvector | 可升级 |
| 增量文档更新 | ✅ document_id upsert | ❌ | 需要实现 |

---

## 9. 总结

Hindsight 的本地嵌入模式是一个精心设计的工程系统：

**核心价值**：
1. **零外部依赖** — 通过 `hindsight-all` + 内置 pg0 实现离线运行
2. **智能进程管理** — Daemon 自动启动/停止，支持配置热更新
3. **灵活存储** — 支持嵌入式 pg0、External PostgreSQL、Docker 三种形态
4. **企业级向量选项** — pgvector/pgvectorscale/vchord 按需选择
5. **Schema 隔离** — 多租户通过 schema 分离，无需独立数据库

**对 CE 的直接行动项**：
1. 评估 `pgvectorscale` 对大规模向量场景的延迟改进
2. 考虑将重型 extraction 操作拆分为独立 Worker（类 `HindsightEmbedded` 模式）
3. 实现知识图谱实体消解能力（Observation 合并 + 实体链接）

---

**下期预告**：Evolver 端到端流程走查（从收到消息到生成回复的完整记忆管线）
