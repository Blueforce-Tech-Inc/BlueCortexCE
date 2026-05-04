
## 30. 多模态记忆澄清（v4.0 新增）

> **本节为 v4.0 新增**，澄清 Hermes 是否支持图像/音频等多模态记忆。

### 30.1 结论：Hermes 没有多模态记忆存储

经过深入探索，`hermes-agent` 的记忆系统中**没有任何多模态记忆存储能力**：

| 模块 | 功能 | 是否记忆存储 |
|------|------|-------------|
| `tools/vision_tools.py` | 图像 URL 下载 + Base64 编码 + LLM 图像分析 | ❌ 否（仅分析，不存储） |
| `tools/transcription_tools.py` | 音频转录 | ❌ 否（仅转录，不存储） |
| `hermes_state.py` messages 表 | 存储消息内容 | ❌ 纯文本，不支持二进制 |
| MemoryStore (holographic) | fact 存储 | ❌ 纯文本 content 字段 |
| Honcho/Mem0 providers | 云端记忆 | ❌ API 调用，无多模态 |

### 30.2 Vision Tools 的实际用途

`vision_tools.py` 的典型使用场景：

```python
# vision_tools.py:25-28
result = await vision_analyze_tool(
    image_url="https://example.com/image.jpg",
    prompt="Describe this image in detail"
)
```

**特点**：
1. 接收图像 URL → 下载 → Base64 编码
2. 发送给 LLM（通过 auxiliary vision router）
3. 返回 LLM 的文本描述
4. **文本描述作为 tool result 存入 messages 表**（与其他 tool result 一样）
5. **原始图像不存储**

### 30.3 Hermes 的记忆全是文本

| 记忆类型 | 存储格式 |
|----------|----------|
| MEMORY.md / USER.md | 纯文本 |
| Session messages | 纯文本 content 字段 |
| Holographic facts | 纯文本 content + HRR 向量 |
| Honcho/Mem0 | 纯文本 content |

**没有任何图像、音频、视频的二进制存储**。

### 30.4 对 BlueCortexCE 的参考

**Hermes 的选择**是合理的——多模态记忆存储复杂度高（需要对象存储、缩略图、元数据管理），且实际价值有限：
- Agent 需要的"记忆"主要是文本形式的观察、决策、偏好
- 图像作为证据/参考时，存储 URL 或 Base64 更实用
- 音频转录后存储文本比存储音频更有价值（可搜索、可摘要）

**建议**：BlueCortexCE 同样不需要多模态记忆存储，保持纯文本方向正确。

---

## 下轮计划

已完成本轮任务（v4.5）：
- ✅ **Holographic memory_banks 澄清**：确认 `memory_banks` 在 `reason()` 中被使用（`retrieval.py:143`），作为代数检索的优化路径（bank unbinding → fact scoring）
- ✅ **Holographic `related()` 方法**：裸原子直接相似度（`retrieval.py:220`），与 `probe()` 的 role binding 形成互补
- ✅ **memory_banks rebuild triggers**：add_fact/add_alias/set_trust/rebuild_all 四个触发点（`store.py:183,294,316,533`）
- ✅ **BlueCortexCE vs Hermes Summary Template**：逐字段对比，发现 BlueCortexCE 缺少 7 个高优先级字段（Constraints、Active State、Blocked、Key Decisions、Relevant Files 等）
- ✅ **BlueCortexCE 矛盾检测工程方案**：SQL + pgvector 实现方案，entity extraction 两种方案对比
- ✅ **SessionSearch LLM fallback**：MAX_SUMMARY_CHARS=2000，输入保护 >4000 chars

下轮继续深入：
- **Hindsight Provider 深挖**：知识图谱构建 + 实体消歧的具体算法（`plugins/memory/hindsight/__init__.py` 883行）
- **Mem0 Provider**：`memory_types` 如何映射到 mem0 的存储 schema，以及 `rerank_memories` 端点的使用
- **RetainDB Agent Self-Model**：`seed_agent_identity` → `get_agent_model` 的往返流程，以及在 Hermes Agent 启动时的调用时机
- **BlueCortexCE Summary Template 改进**：设计增加 Constraints/Active State/Blocked/Key Decisions 等字段的新 prompt 模板

---

## 23. Multi-Session 隔离架构 + Agent Context 过滤机制（v3.8 新增）

> **本节分析**：Hermes 如何在单一进程内安全地管理多用户、多 profile、多 session 并发的记忆隔离  
> **代码来源**：`agent/memory_manager.py:274-365`（initialize + lifecycle hooks）、`plugins/memory/honcho/client.py:207-470`（session strategy）、`run_agent.py:1199-1215`（agent_identity 注入）  
> **架构差异说明**：Hermes 是单一进程多 session，BlueCortexCE 是独立服务多 session。本节重点在于**隔离思想**，而非直接搬套。

### 23.1 三层隔离机制总览

Hermes 的记忆隔离依赖三个正交维度：

| 隔离维度 | 实现机制 | 控制参数 |
|----------|----------|----------|
| **Profile 隔离** | 每个 profile 独立 `hermes_home` 目录 | `HERMES_HOME` 环境变量 |
| **Session 隔离** | 每个运行实例独立 `session_id` | `session_id` 参数（`{timestamp}_{hex}`） |
| **Agent Context 过滤** | Provider 自行决定是否参与 | `agent_context` kwarg（`primary/subagent/cron/flush`） |

**与 BlueCortexCE 对比**：BlueCortexCE 通过 PostgreSQL schema/database 隔离多用户，通过 session_id 隔离会话，无 agent_context 概念。

### 23.2 Profile 隔离：`hermes_home` + `agent_identity`

**文件**: `run_agent.py:1199-1215`

Hermes 使用 `hermes_home` 作为所有记忆文件的根目录，且在初始化时将 `agent_identity`（profile 名）注入到每个 provider：

```python
# run_agent.py:1199-1215
self._memory_manager.initialize_all(
    self.session_id,
    hermes_home=str(get_hermes_home()),
    platform=self.platform,
    agent_context="primary",
    # Profile identity for per-profile provider scoping
    if self._user_profile_enabled:
        from hermes_cli.profiles import get_active_profile_name
        _profile = get_active_profile_name()
        _init_kwargs["agent_identity"] = _profile
)
```

**`get_hermes_home()` 实现**（`hermes_constants.py:11`）：

```python
def get_hermes_home() -> Path:
    hermes_home = os.getenv("HERMES_HOME")
    if not hermes_home:
        hermes_home = os.path.join(os.path.expanduser("~"), ".hermes")
    profile_home = os.path.join(hermes_home, "home")
    # 如果 profile_home 存在（profile 已激活），优先使用它
    if os.path.exists(profile_home):
        return Path(profile_home)
    return Path(hermes_home)
```

**影响**：每个 profile 的 `MEMORY.md`/`USER.md` 存在各自 `hermes_home/home/` 下，互不干扰。

**翻译：旁路型如何借鉴**：
- BlueCortexCE 使用 PostgreSQL database/schema 做多租户隔离
- `agent_identity` 对应 BlueCortexCE 的 `user_id` 维度
- 建议：BlueCortexCE 的 `/api/context/generate` 应接受 `user_id` 参数，确保跨用户隔离

### 23.3 Session 隔离：`session_id` 参数穿透

**文件**: `agent/memory_manager.py:166-205`

`MemoryManager` 的所有方法都接受 `session_id` 参数，并将其传递给每个 provider：

```python
# agent/memory_manager.py:166-205
def prefetch_all(self, query: str, *, session_id: str = "") -> str:
    """Collect prefetch context from all providers."""
    parts = []
    for provider in self._providers:
        try:
            result = provider.prefetch(query, session_id=session_id)
            # ...

def sync_all(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    """Sync a completed turn to all providers."""
    for provider in self._providers:
        try:
            provider.sync_turn(user_content, assistant_content, session_id=session_id)
```

**Honcho 的 session 策略**（`plugins/memory/honcho/client.py:207-470`）：

Honcho 支持四种 session 分裂策略，通过 `session_strategy` 配置：

```python
@dataclass
class HonchoClientConfig:
    session_strategy: str = "per-directory"  # default
```

| 策略 | 行为 | Honcho session name 来源 |
|------|------|--------------------------|
| `per-session` | 每次 Hermes 运行新建 Honcho session | Hermes `session_id`（`{timestamp}_{hex}`） |
| `per-repo` | 每个 git 仓库一个 Honcho session | git repo root 目录名 |
| `per-directory` | 每个工作目录一个 Honcho session（默认） | 目录 basename |
| `global` | 全局单一 session | workspace name |

```python
# plugins/memory/honcho/client.py:454-470
# per-session: inherit Hermes session_id (new Honcho session each run)
if self.session_strategy == "per-session" and session_id:
    return f"{self.peer_name}-{session_id}" if self.peer_name else session_id

# per-repo: one Honcho session per git repository
if self.session_strategy == "per-repo":
    base = self._git_repo_name(cwd) or Path(cwd).name
    return f"{self.peer_name}-{base}" if self.peer_name else base

# per-directory: one Honcho session per working directory (default)
if self.session_strategy in ("per-directory", "per-session"):
    base = Path(cwd).name
    return f"{self.peer_name}-{base}" if self.peer_name else base
```

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的 `session_id` 对应 Hermes 的 `session_id`
- "per-session" 策略 = BlueCortexCE 每个对话 session 独立的记忆空间
- "per-directory" 策略 = BlueCortexCE 可以通过 `workspace_id`（cwd hash）实现类似效果
- **高优先级建议**：BlueCortexCE `/api/session/start` 增加 `workspace_id` 或 `cwd` 参数，支持目录级别的记忆隔离

### 23.4 Agent Context 过滤：防止非主 session 污染记忆

**文件**: `plugins/memory/honcho/__init__.py:198-215`

Hermes 使用 `agent_context` kwarg 区分 Agent 的运行上下文，Provider 据此决定是否参与：

```python
# plugins/memory/honcho/__init__.py:198-215
def initialize(self, session_id: str, **kwargs) -> None:
    # ...
    agent_context = kwargs.get("agent_context", "")
    platform = kwargs.get("platform", "")

    # Port #4053: cron guard — skip all memory writes for cron/flush contexts
    if agent_context in ("cron", "flush") or platform == "cron":
        logger.debug("Honcho skipped: cron/flush context (agent_context=%s, platform=%s)",
                     agent_context, platform)
        self._cron_skipped = True
        return
    self._cron_skipped = False
```

**`agent_context` 取值含义**：

| 值 | 含义 | Honcho 行为 |
|----|------|------------|
| `primary` | 主 Agent 会话（正常用户交互） | ✅ 正常激活 |
| `subagent` | 子 Agent（delegate_task 派生） | ⚠️ 允许 prefetch，禁止 sync |
| `cron` | Cron 定时任务 | ❌ 完全跳过（`_cron_skipped = True`） |
| `flush` | Flush session（session 压缩后的新 session） | ❌ 完全跳过 |

```python
# honcho/__init__.py:327 - prefetch 过滤
def prefetch(self, query: str, *, session_id: str = "") -> str:
    if self._cron_skipped:
        return ""  # No auto-injection for cron

# honcho/__init__.py:579 - sync 过滤
def sync_turn(self, user_content: str, assistant_content: str, *, session_id: str = "") -> None:
    if self._cron_skipped:
        return  # No writes for cron/flush
```

**为什么需要这个机制**：
1. **Cron 不应写入用户记忆**：Cron agent 的 system prompt 是系统生成的，不应污染用户画像
2. **子 Agent 的记忆归属问题**：子 Agent 的工作成果应归属父 session，而非子 session 自己的记忆
3. **Flush session 是压缩产物**：压缩后的新 session ID 不应产生新的记忆条目

**run_agent.py 中的 `agent_context` 注入**（`run_agent.py:1204`）：

```python
self._memory_manager.initialize_all(
    self.session_id,
    agent_context="primary",  # 主 session
    # ...
)
```

子 Agent 和 cron job 会传入不同的 `agent_context` 值。

**翻译：旁路型如何借鉴**：
- BlueCortexCE 目前没有 `agent_context` 概念
- 对于 BlueCortexCE 的消费方（Claude Code/OpenClaw），**子进程/子 Agent 的记忆归属**是个问题
- **中优先级建议**：BlueCortexCE API 增加 `agent_context` 参数，支持：
  - `primary`（默认）：正常写入
  - `subagent`：只读 prefetch，禁止写入
  - `system`：系统级操作（如 cron health check），完全跳过

### 23.5 Prefetch 队列与 Cron 的协作机制

**文件**: `plugins/memory/honcho/__init__.py:477-495`

Honcho 在每次 `queue_prefetch` 时会检查 cron skip 状态：

```python
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    # B1: tools-only mode — no prefetch
    if self._recall_mode == "tools":
        return
    # B2: cron/flush — no background prefetch (would waste API calls)
    if self._cron_skipped:
        return
    # Fire background dialectic query
    self._dialectic_manager.fire_query(
        self._dialectic_session.session_key, query, self._current_turn
    )
```

**关键洞察**：Honcho 在 `queue_prefetch` 阶段就过滤了 cron，而非等到 `prefetch` 返回时再判断。这样做的好处是：cron 上下文中，`queue_prefetch` 是无操作空返回，不需要启动任何后台线程。

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的 `/api/memory/queue`（如果未来实现）应该在入口层就检查 `agent_context`
- 对于 `cron/system` 上下文，直接返回空，避免无意义的 API 调用和后台资源消耗

### 23.6 On-demand Session 初始化（Lazy Init）

**文件**: `plugins/memory/honcho/__init__.py:321-340`

Honcho 的一个特殊设计：**tools-only 模式下，session 初始化是延迟的**，直到第一次调用工具时才真正初始化：

```python
def _ensure_session_initialized(self) -> None:
    """Lazily initialize the Honcho session (for tools-only mode)."""
    if self._session_initialized:
        return
    if not self._manager:
        return

    # Resolve session from lazy init data
    session_id = (
        self._lazy_init_session_id or "hermes-default"
    )
    self._lazy_init_session_id = None
    # ... actual init ...
    self._session_initialized = True
```

这是因为 `tools-only` 模式下 `initialize()` 可能因为 `agent_context` 检查而被跳过，但工具调用时需要真实的 session。

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的 Session 可以在第一次实际使用时才创建（lazy session 初始化）
- 对于只 prefetch 不写入的场景，避免提前创建 session 开销

### 23.7 BlueCortexCE 借鉴建议汇总

| 发现 | Hermes 做法 | 优先级 | BlueCortexCE 行动 |
|------|-------------|--------|------------------|
| Profile 隔离 | `hermes_home` + `agent_identity` | 高 | 确认 `/api/session/start` 正确使用 `user_id` 隔离 |
| Session 策略 | `per-session/per-repo/per-directory/global` | 中 | `/api/session/start` 增加 `workspace_id` 参数支持目录级隔离 |
| Agent Context 过滤 | `cron/flush` 完全跳过写入 | 高 | API 增加 `agent_context` 参数（`primary/subagent/system`） |
| Lazy Session Init | tools-only 模式延迟初始化 | 低 | 考虑在 `/api/session/start` 增加 `lazy=true` 选项 |
| 多 Provider 协调 | MemoryManager 统一编排 | 中 | BlueCortexCE 的 Provider 模式可以借鉴（但优先级低，SDK 层面已有抽象） |

### 23.8 待进一步确认（v4.0 更新）

1. ✅ **子 Agent 的记忆归属** — **已澄清：on_delegation 为空实现**。所有 provider（Honcho/Holographic）的 `on_delegation` 均使用基类 no-op 默认实现。
2. ✅ **Honcho per-repo 策略** — **v4.0 待确认**：`_git_repo_name` 如何实现？在无 git 环境下是否退化到 per-directory？
3. ✅ **Flush session 的压缩归属** — 压缩后的新 session ID 是 `old_session_id` 的子 ID 还是独立 session？

---

## 31. Holographic HRR Vector Store — 完整实现分析（v4.1 新增）

### 31.1 架构定位

**文件**: `plugins/memory/holographic/store.py` + `holographic.py`

Holographic 是 Hermes 内置的**本地 SQLite 向量存储**，使用 **HRR (Holographic Reduced Representations)** 而非传统 embedding + 余弦相似度。

**核心洞察**：这是 Hermes 所有 provider 中**唯一使用 VSA (Vector Symbolic Architecture) 的实现**，而非基于 OpenAI/bedrock embedding 的 RAG 范式。

### 31.2 HRR 核心算法

**文件**: `plugins/memory/holographic/holographic.py:1-205`

**三大代数运算**：

| 运算 | 实现 | 数学含义 | 用途 |
|------|------|----------|------|
| `bind(a, b)` | `(a + b) % 2π` — 相位加法 | 圆周卷积 | 将两个概念绑定（如 fact + role） |
| `unbind(memory, key)` | `(memory - key) % 2π` — 相位减法 | 圆周相关 | 从记忆中解开（检索 bound value） |
| `bundle(*vectors)` | 复指数相量和的角度 | 叠加平均 | 合并多个概念（可存储 O(√dim) 个条目） |

**为什么用相位编码而非传统复数 HRR**：
```python
# holographic.py:8-12
"""Phase encoding is numerically stable, avoids the magnitude collapse of
traditional complex-number HRRs, and maps cleanly to cosine similarity."""
```

**原子向量生成**（确定性，跨平台）：
```python
# holographic.py:48-70 encode_atom()
# 使用 SHA-256 counter blocks，而非 numpy RNG
# 每个 block 16 个 uint16 → scale to [0, 2π)
```

### 31.3 Fact 编码结构

**文件**: `plugins/memory/holographic/holographic.py:112-127 encode_fact()`

```python
def encode_fact(content: str, entities: list[str], dim: int = 1024) -> "np.ndarray":
    # 结构：[content_bound_to_ROLE_CONTENT] + [entity_1_bound_to_ROLE_ENTITY] + ...
    role_content = encode_atom("__hrr_role_content__", dim)
    role_entity = encode_atom("__hrr_role_entity__", dim)
    # bind(content, ROLE_CONTENT) + bind(entity_1, ROLE_ENTITY) + ... → bundle
```

**关键设计**：使用 role binding 使得代数检索成为可能：
```
unbind(fact_vector, bind(entity_name, ROLE_ENTITY)) ≈ content_vector
```

这实现了**无需向量索引的实体→内容检索**。

### 31.4 SNR 容量控制

**文件**: `plugins/memory/holographic/holographic.py:176-193 snr_estimate()`

```python
snr = sqrt(dim / n_items)  # dim=1024, n_items > 256 时 SNR < 2.0
```

当 `n_items > dim/4` 时，检索准确率开始下降。Holographic 在添加 fact 时会检查 SNR，接近容量时记录 warning。

**这意味着**：单个 memory bank 的容量上限约为 `dim/4 ≈ 256` 个 fact。

### 31.5 SQLite Schema + HRR Bank

**文件**: `plugins/memory/holographic/store.py:18-75 _SCHEMA`

```sql
-- facts 表：每个 fact 存储 HRR 向量
CREATE TABLE facts (
    fact_id INTEGER PRIMARY KEY,
    content TEXT UNIQUE,           -- 去重
    category TEXT,
    trust_score REAL DEFAULT 0.5,  -- 信任分
    retrieval_count INTEGER,       -- 检索次数
    helpful_count INTEGER,          -- positive feedback 计数
    hrr_vector BLOB,               -- HRR 向量
    ...
);

-- memory_banks：每个 category 一个 bundled 向量
CREATE TABLE memory_banks (
    bank_name TEXT UNIQUE,         -- "cat:{category}"
    vector BLOB,                    -- bundle(*all_fact_vectors)
    fact_count INTEGER,
    dim INTEGER,
);

-- FTS5 虚拟表用于 keyword search
CREATE VIRTUAL TABLE facts_fts USING fts5(content, tags, content=facts);
```

**FTS5 触发器**保证 `INSERT/UPDATE/DELETE` on `facts` 自动同步到 `facts_fts`。

### 31.6 Trust 反馈机制（不对称调整）

**文件**: `plugins/memory/holographic/store.py:349-390 record_feedback()`

```python
_HELPFUL_DELTA   =  0.05   # helpful=True
_UNHELPFUL_DELTA = -0.10   # helpful=False（更严厉）
_TRUST_MIN       =  0.0
_TRUST_MAX       =  1.0
```

**不对称设计的原因**：
- 惩罚力度大于奖励（-0.10 vs +0.05）：避免错误记忆快速累积
- 有害记忆需要更多 positive feedback 才能恢复

**搜索时 trust 作为乘数**：
```python
# store.py:187-237 search_facts()
final_score = fts_rank * (1 + trust_score)  # trust 范围 [0,1]
```

### 31.7 Entity 提取算法

**文件**: `plugins/memory/holographic/store.py:394-458 _extract_entities()`

正则规则（按优先级）：
1. 大写多词短语：`"John Doe"`
2. 双引号词：`"Python"`
3. 单引号词：`'pytest'`
4. AKA 模式：`"Guido aka BDFL"` → 两个 entity

**Entity 去重 + 链接**：
```python
entity_id = _resolve_entity(name)     # 查找或创建 entity
_link_fact_entity(fact_id, entity_id)  # M:N 链接表
```

### 31.8 Category Bank Rebuild 机制

**文件**: `plugins/memory/holographic/store.py:494-530 _rebuild_bank()`

每当 `add_fact` 时：
1. 计算新 fact 的 HRR 向量
2. 从 DB 取出该 category 所有 fact 向量
3. `bundle(*vectors)` 生成 category-level bank vector
4. `INSERT ON CONFLICT DO UPDATE` 写入 `memory_banks`

**用途**：category bank vector 可用于"该类别整体相关度"的代数检索。

### 31.9 Hybrid Retrieval Pipeline

**文件**: `plugins/memory/holographic/retrieval.py:43-130 FactRetriever.search()`

```python
# Stage 1: FTS5 候选检索（limit*3 个）
candidates = _fts_candidates(query, category, min_trust, limit * 3)

# Stage 2: Jaccard 重排
jaccard = |query_tokens ∩ fact_tokens| / |query_tokens ∪ fact_tokens|

# Stage 3: 综合评分
# final_score = (fts_weight * fts_rank) + (jaccard_weight * jaccard) + (hrr_weight * hrr_similarity)
# 可选 temporal_decay: 0.5^(age_days / half_life)
```

**权重分配**（默认）：FTS=0.4, Jaccard=0.3, HRR=0.3

### 31.10 翻译：旁路型如何借鉴

| 发现 | Hermes 做法 | 架构差异 | BlueCortexCE 可借鉴 |
|------|-------------|----------|-------------------|
| HRR 绑定检索 | `bind/unbind` 代数操作 | Hermes 内置可直接调用 Python | **低优先级** — 需要 Agent 直接集成，旁路型难以暴露 VSA 能力 |
| SNR 容量控制 | `sqrt(dim/n_items)` 预警 | Hermes 本地计算 | **中优先级** — BlueCortexCE 可对单个 session 的 observation 数量做容量预警 |
| Trust 反馈 | `+0.05/-0.10` 不对称调整 | Hermes 直接修改 DB | **高优先级** — BlueCortexCE 可实现 `/api/feedback` 端点，让 Agent 反馈记忆质量 |
| FTS5 + HRR 混合 | FTS 候选 + Jaccard/HRR 重排 | Hermes 完整实现 | **高优先级** — BlueCortexCE 可在 pgvector 检索基础上增加 Jaccard 重排层 |
| Entity 提取 | 简单正则规则 | Hermes 内容理解 | **中优先级** — BlueCortexCE 的 observation 可选带 entity 标签，增强检索精度 |
| Category Bank | bundle 所有 fact vectors | Hermes 本地管理 | **低优先级** — 旁路型没有"当前 category"上下文 |

---

## 32. Memory Provider 全景对比（v4.1 新增）

### 32.1 七大 Provider 一览

| Provider | 类型 | 存储后端 | 向量方案 | 特殊能力 | 代码规模 |
|----------|------|----------|----------|----------|----------|
| **honcho** | SaaS API | Honcho Cloud | OpenAI embedding | Dialectic Q&A, Observation synthesis | ~800行 |
| **holographic** | 本地 | SQLite | HRR (VSA) | 代数检索，矛盾检测 | ~574行 |
| **mem0** | SaaS API | mem0 Cloud | mem0 proprietary | 记忆分层, Circuit breaker | ~371行 |
| **retaindb** | SaaS API | RetainDB Cloud | RetainDB API | 持久化写队列, Agent self-model | ~766行 |
| **supermemory** | SaaS API | Supermemory API | Supermemory API | Deduplication, Category detection | ~791行 |
| **openviking** | SaaS API | OpenViking API | OpenViking API | — | ~637行 |
| **byterover** | 本地 CLI | BRV (ByteRover) CLI | BRV CLI | CLI wrapper | ~383行 |

### 32.2 mem0 Circuit Breaker 实现

**文件**: `plugins/memory/mem0/__init__.py:168-200`

```python
_BREAKER_THRESHOLD = 5          # 连续失败次数阈值
_BREAKER_COOLDOWN_SECS = 300   # 5 分钟 cooldown

def _is_breaker_open(self) -> bool:
    if self._consecutive_failures < _BREAKER_THRESHOLD:
        return False
    if time.monotonic() >= self._breaker_open_until:
        # Cooldown 结束 → 重置并允许重试
        self._consecutive_failures = 0
        return False
    return True

def _record_failure(self):
    self._consecutive_failures += 1
    if self._consecutive_failures >= _BREAKER_THRESHOLD:
        self._breaker_open_until = time.monotonic() + _BREAKER_COOLDOWN_SECS
        logger.warning("Mem0 circuit breaker tripped...")

def _record_success(self):
    self._consecutive_failures = 0
```

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的外部 embedding 服务（OpenAI/Azure）调用应该增加 circuit breaker
- 连续失败 5 次后暂停 5 分钟，避免雪崩效应

### 32.3 RetainDB 持久化写队列

**文件**: `plugins/memory/retaindb/__init__.py:330-410 _WriteQueue`

**架构**：SQLite 持久化队列 + 后台线程消费

```python
class _WriteQueue:
    """Survives crashes — pending rows replay on startup."""

    def __init__(self, client, db_path):
        # 启动后台线程
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()
        # 恢复 crash 前的 pending rows
        for row_id, user_id, session_id, msgs_json in self._pending_rows():
            self._q.put((row_id, user_id, session_id, json.loads(msgs_json)))

    def enqueue(self, user_id, session_id, messages):
        # 1. 写入 SQLite pending 表（持久化）
        # 2. 放入内存队列
        conn.execute("INSERT INTO pending ...", (...))
        self._q.put((row_id, user_id, session_id, messages))

    def _flush_row(self, row_id, ...):
        try:
            self._client.ingest_session(user_id, session_id, messages)
            conn.execute("DELETE FROM pending WHERE id = ?", (row_id,))  # 成功后删除
        except Exception as exc:
            conn.execute("UPDATE pending SET last_error = ? WHERE id = ?", (str(exc), row_id))
            # 不删除，下次 loop 重试
```

**崩溃恢复机制**：
1. 每次 `enqueue` 先写 SQLite（持久化）
2. 后台线程从 SQLite 读取 pending rows 并重放
3. 成功后从 SQLite 删除
4. Crash 后重启，pending rows 自动恢复

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的 `/api/ingest` 可以增加 async 模式（立即返回，队列写入）
- SQLite pending 表保证 crash 不丢失待写入数据
- **高优先级建议**：为 BlueCortexCE 的 observation 写入增加可选的 async ingest 模式

### 32.4 Supermemory Deduplication

**文件**: `plugins/memory/supermemory/__init__.py:189-210 _deduplicate_recall()`

```python
def _deduplicate_recall(static_facts, dynamic_facts, search_results):
    seen = set()
    def _norm(s): return re.sub(r"[^a-z0-9 ]", "", s.lower())

    for facts_list in [static_facts, dynamic_facts, search_results]:
        for fact in facts_list:
            norm = _norm(fact.get("content", ""))
            if norm and norm not in seen:
                seen.add(norm)
                yield fact
```

**翻译：旁路型如何借鉴**：
- BlueCortexCE 的多 observation 合并时可以增加内容去重（基于 normalized string）
- 避免同一事实被多次 observation 稀释 trust score

### 32.5 Provider 特殊机制补充

**OpenViking atexit 安全网** (`openviking/__init__.py:43-63`)：
```python
_last_active_provider: Optional["OpenVikingMemoryProvider"] = None

def _atexit_commit_sessions():
    """Fire on_session_end for the last active provider on process exit."""
    global _last_active_provider
    provider = _last_active_provider
    if provider is None:
        return
    _last_active_provider = None
    try:
        provider.on_session_end([])  # 即使没调用 shutdown 也提交 pending sessions
    except Exception:
        pass

atexit.register(_atexit_commit_sessions)
```
**翻译**：BlueCortexCE 的 Session.commit() 可以注册 atexit handler，防止进程异常退出时 pending 数据丢失。

**ByteRover `on_pre_compress` Hook** (`byterover/__init__.py:282-310`)：
```python
def on_pre_compress(self, messages):
    # 提取即将被压缩的最后 10 条消息
    for msg in messages[-10:]:
        if role in ("user", "assistant"):
            parts.append(f"{role}: {content[:500]}")
    # 异步调用 brv curate 将压缩前的上下文写入记忆
    _run_brv(["curate", "--", f"[Pre-compression context]\n{combined}"], ...)
```
**翻译**：BlueCortexCE 的 `Summary` hook（在 SessionEnd 之前触发）可以在上下文压缩发生前，主动将关键信息提取为 summary，避免压缩丢失。

**Supermemory Category Detection** (`supermemory/__init__.py:158-168`)：
```python
def _detect_category(text):
    lowered = text.lower()
    if re.search(r"prefer|like|love|hate|want", lowered): return "preference"
    if re.search(r"decided|will use|going with", lowered): return "decision"
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered): return "fact"
    return "other"
```
**翻译**：BlueCortexCE 的 observation 可以增加 `category` 字段，基于关键词自动分类。

### 32.6 所有 Provider 的共同接口模式

所有 provider 都实现了 `MemoryProvider` 接口：
- `initialize(session_id)` — 初始化
- `system_prompt_block()` — 注入 system prompt
- `prefetch(query)` — 主动 prefetch（同步）
- `queue_prefetch(query)` — 异步 prefetch（后台）
- `sync_turn(user_content, assistant_content)` — turn 同步
- `on_delegation(...)` — 子 Agent 委托（多数为空实现）
- `on_memory_write(...)` — 记忆写入事件
- `on_pre_compress(messages)` — 压缩前 hook
- `get_tool_schemas()` — 提供工具 schema
- `handle_tool_call(...)` — 工具调用处理

**这与 BlueCortexCE 的 Hook 机制（5 lifecycle hooks）功能类似，但粒度更细。**

---

## 33. 待进一步确认（v4.1 更新）

### 33.1 待深挖

1. **HRR 在实际检索中的效果**：Holographic 的代数检索（`unbind`）在实际对话中的准确率如何？是否有 A/B 对比数据？
2. **Honcho Dialectic 的 LLM 调用成本**：每次 `queue_prefetch` 会触发一次 dialectic 查询，成本如何控制？
3. **memory_banks 的实际用途**：`cat:{category}` bank vector 在检索中是如何被使用的？目前 `_rebuild_bank` 生成了 bank，但 `search_facts` 没有使用它。
4. **openviking/byterover 实现细节**：这两个 provider 的代码还未深入分析（待下轮）。
5. ✅ **supermemory category detection**：`_detect_category()` 如何判断记忆类别？— **v4.2 已澄清**（见 34.4）

---

## 34. OpenViking 分层上下文加载 — Filesystem-Style URI Abstraction（v4.2 新增）

> **文件**: `plugins/memory/openviking/__init__.py`（637 行完整实现）
> **本节为 v4.2 新增**，分析 OpenViking Provider 的分层上下文加载机制和 `viking://` URI 文件系统抽象。

### 34.1 核心洞察：记忆的"懒加载"范式

OpenViking 提出了一个独特的记忆加载范式：**不一次性返回完整记忆内容，而是提供分层 detail level，让 Agent 按需获取不同粒度的信息**。

| Detail Level | Token 估算 | 用途 | 典型延迟 |
|-------------|-----------|------|----------|
| `abstract`（L0） | ~100 tokens | 快速判断相关性 | 极低 |
| `overview`（L1） | ~2K tokens | 理解关键要点 | 低 |
| `full`（L2） | 完整内容 | 需要深入细节时 | 高 |

**对比其他 Provider**：Honcho/Holographic/Mem0 都只返回"一个粒度"的内容，要么是 raw excerpt，要么是 LLM 合成结果。OpenViking 是**唯一一个提供可分级检索粒度**的 Provider。

### 34.2 `viking://` URI 文件系统抽象

**设计思想**：将记忆库组织为文件系统层级（类似 `file://`），每个记忆/资源都有一个 `viking://` URI：

```
viking://resources/docs/python-guide.md
viking://user/memories/preferences/2024-03-project-notes.txt
viking://skills/viking-search-usage.md
```

**浏览操作**（`viking_browse` 工具）：

```python
# _tool_browse() — openviking/__init__.py:564-586
BROWSE_SCHEMA = {
    "name": "viking_browse",
    "description": (
        "Browse the OpenViking knowledge store like a filesystem.\n"
        "  list — show directory contents\n"
        "  tree — show hierarchy\n"
        "  stat — show metadata for a URI"
    ),
}
```

**操作示例**：
- `viking_browse(action="tree", path="viking://")` — 显示整个知识库目录树
- `viking_browse(action="list", path="viking://user/memories/")` — 列出用户记忆目录内容
- `viking_browse(action="stat", path="viking://resources/docs/guide.md")` — 显示某条记忆的元数据

**与网页浏览的相似性**：这个设计就像让 Agent 使用 `ls`、`tree`、`stat` 命令浏览文件系统，**而不是一次性搜索全部内容**。

### 34.3 分层读取实现（`_tool_read`）

```python
# _tool_read() — openviking/__init__.py:536-558
def _tool_read(self, args: dict) -> str:
    level = args.get("level", "overview")
    if level == "abstract":
        resp = self._client.get("/api/v1/content/abstract", params={"uri": uri})
    elif level == "full":
        resp = self._client.get("/api/v1/content/read", params={"uri": uri})
    else:  # overview
        resp = self._client.get("/api/v1/content/overview", params={"uri": uri})

    # 超过 8000 chars 截断
    if len(content) > 8000:
        content = content[:8000] + "\n\n[... truncated, use a more specific URI or abstract level]"
```

**OpenViking 服务器负责**：
- `abstract` 端点：生成 ~100 token 的摘要
- `overview` 端点：生成 ~2K token 的关键点
- `read` 端点：返回完整原始内容

**客户端只需要根据需要调用不同的 API 端点**。

### 34.4 六类自动记忆提取

OpenViking 的 `on_session_end` 提交 session 时，服务器自动将对话内容提取为 6 类记忆：

> `on_session_end` docstring: "OpenViking automatically extracts 6 categories of memories: profile, preferences, entities, events, cases, and patterns." (`openviking/__init__.py:415-417`)

| 类别 | 含义 | 示例 |
|------|------|------|
| `profile` | 用户身份轮廓 | 职位、技能、背景 |
| `preferences` | 用户偏好 | 编码风格、工具选择 |
| `entities` | 提到的实体 | 项目名、人名、工具名 |
| `events` | 事件记录 | 完成的任务、达成的决策 |
| `cases` | 案例/问题 | 解决的 bug、遇到的问题 |
| `patterns` | 行为模式 | 反复出现的习惯 |

**这 6 类与 Holographic 的 `category` 字段类似，但 OpenViking 是服务器端自动分类，不需要用户指定。**

### 34.5 viking_remember — 显式记忆写入的延迟机制

```python
# _tool_remember() — openviking/__init__.py:588-609
def _tool_remember(self, args: dict) -> str:
    # 将内容作为 session message 暂存
    # 服务器会在 session commit 时提取
    text = f"[Remember — {category}] {content}"
    self._client.post(f"/api/v1/sessions/{self._session_id}/messages", {
        "role": "user",
        "parts": [{"type": "text", "text": text}],
    })
    return json.dumps({
        "status": "stored",
        "message": "Memory recorded. Will be extracted and indexed on session commit.",
    })
```

**关键设计**：`viking_remember` **不直接写入记忆库**，而是将内容暂存为 session message，等待 `on_session_end` 的 commit 触发自动提取。这实现了：
- **批量提取**：多个 `viking_remember` 调用会在同一次 commit 中一起处理
- **服务器端分类**：内容类型由服务器自动判断
- **原子性**：如果 session commit 失败，所有暂存的 remember 都回滚

### 34.6 atexit 安全网

```python
# openviking/__init__.py:43-63
_last_active_provider: Optional["OpenVikingMemoryProvider"] = None

def _atexit_commit_sessions():
    global _last_active_provider
    provider = _last_active_provider
    if provider is None:
        return
    _last_active_provider = None
    try:
        provider.on_session_end([])  # 即使没调用 shutdown 也提交 pending sessions
    except Exception:
        pass

atexit.register(_atexit_commit_sessions)
```

**用途**：防止进程异常退出（SIGKILL、gateway crash）时 pending sessions 未 commit。

### 34.7 与 BlueCortexCE 对比

| 维度 | OpenViking 分层上下文 | BlueCortexCE |
|------|---------------------|--------------|
| 分层加载 | abstract/overview/full 三级 | ❌ 无（统一粒度） |
| URI 抽象 | `viking://` 文件系统式 URI | ❌ 无（flat API） |
| 自动分类 | 6 类自动提取 | ⚠️ Observation 有 type 字段，但无自动分类 |
| 延迟写入 | remember 暂存 → commit 时批量提取 | ❌ 直接写入 |
| atexit 安全网 | ✅ 有 | ❌ 无 |
| 资源索引 | `viking_add_resource` 支持 URL/doc/code | ❌ 无 |

### 34.8 翻译：旁路型如何借鉴

**核心差距**：OpenViking 的分层加载是**服务器端能力**，BlueCortexCE 作为旁路型服务，可以借鉴其**思想**。

**高优先级借鉴**：

1. **BlueCortexCE 增加分层检索 API**：
   ```
   GET /api/memory/search?query=X&level=abstract|overview|full
   ```
   - `abstract`：只返回 Observation 标题/类型（~100 tokens）
   - `overview`：返回 Observation 的摘要（~2K tokens）
   - `full`：返回完整 Observation 内容

2. **BlueCortexCE 增加资源索引 API**：
   ```
   POST /api/memory/resource?url=https://...
   ```
   让 Agent 可以主动索引外部文档（GitHub repo、网页等），服务器自动解析、摘要、存储

3. **BlueCortexCE 增加 atexit handler**：服务退出时确保 pending writes 被 flush

4. **Observation category 自动检测**：在 Observation 生成时，自动推断类别（参考 Supermemory 的正则方案）

---

## 35. Honcho _flush_session 机制 — Message Batching 与 Crash Recovery（v4.2 新增）

> **文件**: `plugins/memory/honcho/session.py:324-390`（`_flush_session` + `_async_writer_loop`）
> **本节为 v4.2 新增**，澄清 Honcho 内部 message batching 和 crash recovery 的实现细节。

### 35.1 `_flush_session` 的核心逻辑

```python
# session.py:324-360
def _flush_session(self, session: HonchoSession) -> bool:
    """Internal: write unsynced messages to Honcho synchronously."""
    if not session.messages:
        return True  # Nothing to sync

    # 1. 获取或创建 Honcho session
    user_peer = self._get_or_create_peer(session.user_peer_id)
    assistant_peer = self._get_or_create_peer(session.assistant_peer_id)
    honcho_session = self._sessions_cache.get(session.honcho_session_id)
    if not honcho_session:
        honcho_session, _ = self._get_or_create_honcho_session(...)

    # 2. 只同步未同步的消息
    new_messages = [m for m in session.messages if not m.get("_synced")]
    if not new_messages:
        return True

    # 3. 转换为 Honcho message 格式
    honcho_messages = []
    for msg in new_messages:
        peer = user_peer if msg["role"] == "user" else assistant_peer
        honcho_messages.append(peer.message(msg["content"]))

    # 4. 批量提交到 Honcho cloud
    try:
        honcho_session.add_messages(honcho_messages)
        for msg in new_messages:
            msg["_synced"] = True  # 标记已同步
        return True
    except Exception as e:
        for msg in new_messages:
            msg["_synced"] = False  # 保留未同步状态，重试时重新提交
        return False
```

**关键设计**：
- **`_synced` 标记**：每个 message 有 `_synced` 布尔标记，失败时不删除，只重置标记。这样 `_flush_session` 再次调用时会重新提交这些消息。
- **幂等性**：`honcho_session.add_messages()` 是追加操作，即使被调用两次也不会重复创建消息（Honcho 云端去重）。

### 35.2 Async Writer Loop 的重试机制

```python
# session.py:362-388
def _async_writer_loop(self) -> None:
    while True:
        try:
            item = self._async_queue.get(timeout=5)
            if item is _ASYNC_SHUTDOWN:
                break

            try:
                success = self._flush_session(item)
            except Exception as e:
                success = False

            if not success:
                # 失败 → sleep 2s → 重试一次
                _time.sleep(2)
                try:
                    retry_success = self._flush_session(item)
                except Exception as e2:
                    logger.error("Honcho async write retry failed, dropping batch: %s", e2)
                    continue  # 丢弃这批消息，跳过

            # 成功 → 继续处理下一项
        except queue.Empty:
            continue
```

**重试策略**：
1. **最多重试 1 次**（不是无限重试）
2. **重试间隔 2 秒**（给 Honcho 云端恢复时间）
3. **重试仍然失败 → 丢弃**（`continue`，不阻塞队列）
4. **同步异常 `_synced=False`**：确保重试时这些消息会被重新提交

### 35.3 `flush_all()` — Session 结束时的同步 drain

```python
# session.py:424-442
def flush_all(self) -> None:
    """Flush all pending unsynced messages for all cached sessions."""
    # 1. 同步 flush 所有 session
    for session in list(self._cache.values()):
        try:
            self._flush_session(session)
        except Exception as e:
            logger.error("Honcho flush_all error for %s: %s", session.key, e)

    # 2. 同步 drain 异步队列（确保 session 结束时无遗漏）
    if self._async_queue is not None:
        while not self._async_queue.empty():
            try:
                item = self._async_queue.get_nowait()
                if item is not _ASYNC_SHUTDOWN:
                    self._flush_session(item)
            except queue.Empty:
                break
```

**关键设计**：`flush_all()` 不仅处理 `_cache` 中的 session，还**同步 drain** `_async_queue` 中的所有待处理项。这确保了：
- 所有 session 的 pending messages 都被 flush
- 异步队列中的消息不会因为"还未被 async writer 处理"而被遗漏

### 35.4 与 BlueCortexCE 对比

| 维度 | Honcho _flush_session | BlueCortexCE |
|------|----------------------|--------------|
| Message 标记 | `_synced` 布尔标记 | ❌ 无（写入即视为成功） |
| 失败处理 | 保留 `_synced=False`，下次重试 | ❌ 失败即丢弃 |
| 重试次数 | 最多 1 次 | ❌ 无重试 |
| 重试间隔 | 2 秒 | ❌ 无 |
| flush_all drain | 同步 drain async queue | ❌ 无 async queue |
| 幂等性 | Honcho cloud 端去重 | ❌ 可能重复写入 |

### 35.5 翻译：旁路型如何借鉴

**核心借鉴**：Honcho 的 message batching + `_synced` 标记机制是一个**比 BlueCortexCE 当前方案更可靠的写入模型**。

**高优先级建议**：

| 建议 | 说明 |
|------|------|
| BlueCortexCE 增加写入重试机制 | 写入失败后保留 pending 状态，下次 `sync_turn` 时重试 |
| BlueCortexCE 增加最多重试次数 | 避免无限重试，建议 2-3 次 |
| BlueCortexCE 增加重试间隔 | 指数退避（1s, 2s, 4s）比立即重试更合理 |
| BlueCortexCE 增加 async write queue | 类似 Honcho 的 `async` 模式，减少同步等待 |

**当前 BlueCortexCE 的问题**：`recordObservation` 等写入操作是"fire and forget"，如果写入失败，调用方可能不知道。这与 Honcho 的 `_flush_session` 形成了鲜明对比——Honcho 的消息在未收到云端确认前，始终保留重试机会。

---

## 36. Supermemory 轻量分类 — Regex-Based Memory Categorization（v4.2 新增）

> **文件**: `plugins/memory/supermemory/__init__.py:158-168`（`_detect_category`），`plugins/memory/supermemory/__init__.py:693`（使用点）
> **本节为 v4.2 新增**，分析 Supermemory 的轻量级记忆分类机制。

### 36.1 `_detect_category` 算法

```python
# supermemory/__init__.py:158-168
def _detect_category(text: str) -> str:
    lowered = text.lower()
    if re.search(r"prefer|like|love|hate|want", lowered):
        return "preference"
    if re.search(r"decided|will use|going with", lowered):
        return "decision"
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered):
        return "fact"
    return "other"
```

**分类规则（优先级顺序）**：

| 顺序 | 类别 | 关键词模式 | 含义 |
|------|------|-----------|------|
| 1 | `preference` | `prefer`\|`like`\|`love`\|`hate`\|`want` | 用户偏好 |
| 2 | `decision` | `decided`\|`will use`\|`going with` | 已达成决策 |
| 3 | `fact` | `\bis\b`\|`\bare\b`\|`\bhas\b`\|`\bhave\b` | 事实性陈述 |
| 4 | `other` | （默认） | 其他类型 |

**使用位置**（`__init__.py:693`）：
```python
# Supermemory 在接收外部 recall 结果时自动分类
metadata.setdefault("type", _detect_category(content))
```

### 36.2 设计权衡：Regex vs LLM

Supermemory 选择**纯正则**而非 LLM 做分类，背后的权衡：

| 方案 | 准确性 | 成本 | 延迟 | 适用场景 |
|------|--------|------|------|----------|
| Regex | 低~中（覆盖常见模式） | 零 | 极低 | 实时、大量、简单分类 |
| LLM | 高（理解语义） | 高 | 高 | 少量、复杂、需要理解 |

**Supermemory 的选择**：零成本 + 极低延迟，适合作为"快速初步分类"，后续可以有人工审核或 LLM 复核。

### 36.3 对比：Holographic 的 Category

Holographic 也支持 category，但需要**用户显式指定**：

```python
# holographic/store.py — add_fact
self._store.add_fact(content, category=category)  # category 由调用方传入
```

**Supermemory vs Holographic**：
- Supermemory：自动推断 category（无调用方负担）
- Holographic：调用方指定 category（更精确但需要主动）

### 36.4 翻译：旁路型如何借鉴

**建议**：BlueCortexCE 在 Observation 生成时，增加轻量级 category 推断：

```python
def _detect_observation_category(text: str) -> str:
    """Lightweight category detection for observations (no LLM)."""
    lowered = text.lower()
    # 偏好
    if re.search(r"\bprefer\b|\blike\b|\blove\b|\bhate\b|\bwant\b", lowered):
        return "preference"
    # 决策
    if re.search(r"\bdecided\b|\bwill use\b|\bgoing with\b", lowered):
        return "decision"
    # 问题/阻塞
    if re.search(r"\berror\b|\bfailed\b|\bblocked\b|\bissue\b", lowered):
        return "problem"
    # 事实
    if re.search(r"\bis\b|\bare\b|\bhas\b|\bhave\b", lowered):
        return "fact"
    return "observation"
```

**优先级**：中（属于"nice to have"，不是核心功能）

---

