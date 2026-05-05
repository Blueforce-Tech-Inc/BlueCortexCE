# InsightsEngine 会话分析引擎深度解析（2026-05-05 新增）

**源码**：`agent/insights.py`（930 行）+ `agent/usage_pricing.py`（721 行）  
**CLI 入口**：`hermes_cli/main.py:9945` → `cmd_insights()`  
**Gateway 入口**：`gateway/run.py:10647` → `_handle_insights_command()`  
**Discord 入口**：`platforms/discord.py:2645` → `slash_insights()`  
**依赖**：`agent/usage_pricing.py`（CanonicalUsage / CostStatus / BillingRoute）  
**灵感来源**：Claude Code `/insights` 命令  

---

## 1. 定位与设计目标

**InsightsEngine** 是 Hermes Agent 的**会话使用分析引擎**，直接从 SQLite SessionDB 查询历史数据，生成多维度使用洞察报告。核心价值：

1. **Token 消耗追踪**：input / output / cache_read / cache_write / reasoning 分项统计
2. **成本估算**：基于 `usage_pricing.py` 的官方定价表（支持 actual / estimated / included / unknown 四级状态）
3. **使用模式分析**：工具调用频率、Skill 使用量、活动规律（最繁忙日/小时、连续活跃天数）
4. **模型/平台分布**：多模型 vs 多平台场景下的使用占比
5. **Top Sessions**：最长/最活跃会话排行

**与记忆系统的关系**：InsightsEngine 分析的是**会话使用数据**，而非记忆内容本身。它是记忆系统的**下游消费者**——记忆系统管理的 session 数据（消息、token 使用量）构成了 Insights 的数据来源。

---

## 2. 核心 API

### 2.1 `InsightsEngine(db)`

构造函数接受 `SessionDB` 实例或原生 `sqlite3.Connection`。所有查询直接走 SQL，无网络依赖。

```python
from agent.insights import InsightsEngine
engine = InsightsEngine(db)          # SessionDB or sqlite3.Connection
report = engine.generate(days=30)    # 生成 30 天分析报告
```

### 2.2 `generate(days=30, source=None) -> Dict`

主入口方法，返回完整报告 JSON：

```python
{
  "days": 30,
  "empty": False,
  "overview": {          # 总览
    "total_sessions": N,
    "total_messages": N,
    "total_tool_calls": N,
    "total_tokens": N,
    "total_input_tokens": N,
    "total_output_tokens": N,
    "total_cache_read_tokens": N,
    "total_cache_write_tokens": N,
    "total_reasoning_tokens": N,
    "total_hours": N,           # 累计活跃时长（小时）
    "avg_session_duration": N,   # 秒
    "total_cost_usd": N,
  },
  "models": [           # 模型分布（降序）
    {"model": "gpt-4o", "sessions": N, "messages": N, "tool_calls": N,
     "total_tokens": N, "input_tokens": N, "output_tokens": N, "cost_usd": N},
    ...
  ],
  "platforms": [         # 平台分布
    {"platform": "cli", "sessions": N, "messages": N, "tool_calls": N, ...},
    {"platform": "telegram", "sessions": N, ...},
  ],
  "tools": [             # 工具使用排行
    {"tool": "terminal", "count": N, "percentage": N, "sessions": N},
    {"tool": "memory_tool", "count": N, "percentage": N, "sessions": N},
    ...
  ],
  "skills": {            # Skill 使用统计
    "top_skills": [{"skill": "skill-name", "view_count": N, "manage_count": N, "last_used_at": ts}, ...],
  },
  "activity": {          # 活动规律
    "busiest_day": {"day": "Monday", "count": N},
    "busiest_hour": {"hour": 14, "count": N},
    "active_days": N,
    "max_streak": N,
  },
  "top_sessions": [      # Top 5 最长会话
    {"session_id": "...", "title": "...", "messages": N, "tool_calls": N,
     "tokens": N, "hours": N, "platform": "...", "last_active": ts},
    ...
  ],
}
```

### 2.3 `format_terminal(report) -> str`

将报告渲染为富文本 Terminal 输出（带 Unicode 表格、进度条、`█` 字符柱状图）：

```
╔══════════════════════════════════════════════════════════════╗
║          Hermes Insights — Last 30 days                       ║
╠══════════════════════════════════════════════════════════════╣
║  Sessions: 47  |  Messages: 1,204  |  Tool calls: 892      ║
║  Tokens: 1.24M (in: 892K / out: 348K)  |  Cost: $3.47      ║
║  Active: 4.3h  |  Avg session: 5.5min                      ║
╠══════════════════════════════════════════════════════════════╣
║  Models                                                   ║
║  gpt-4o          ████████████████████  38 sessions          ║
║  claude-sonnet-4 ██████████████        27 sessions          ║
╚══════════════════════════════════════════════════════════════╝
```

### 2.4 `format_gateway(report) -> str`

将报告渲染为 Gateway/Messaging 格式（Markdown 风格，适合 Discord/Telegram 等平台消息）：

```markdown
📊 **Hermes Insights** — Last 30 days

**Sessions:** 47 | **Messages:** 1,204 | **Tool calls:** 892
**Tokens:** 1.24M (in: 892K / out: 348K)
**Active time:** ~4.3h | **Avg session:** ~5.5min

**🤖 Models:**
  gpt-4o — 38 sessions, 1.24M tokens
  claude-sonnet-4 — 27 sessions, 892K tokens

**🔧 Top Tools:**
  terminal — 412 calls (46.2%)
  memory_tool — 89 calls (10.0%)
```

---

## 3. 数据查询层

### 3.1 SQL 查询策略

InsightsEngine 直接执行 SQL 查询 SessionDB，完全绕过了 MemoryProvider 体系：

```python
_GET_SESSIONS_WITH_SOURCE = """
SELECT session_id, title, platform, model,
       input_tokens, output_tokens, cache_read_tokens, cache_write_tokens,
       reasoning_tokens, message_count, tool_call_count,
       started_at, last_active, total_cost_usd, billing_provider
FROM sessions
WHERE started_at > ?
  AND source = ?
ORDER BY started_at DESC
"""
```

- `source` 参数支持按来源过滤（如 `cli` / `telegram` / `discord`）
- 时间截止：`time.time() - days * 86400`
- 支持 `billing_provider` 透传到成本估算

### 3.2 数据字段映射

| SessionDB 字段 | 用途 |
|---------------|------|
| `input_tokens / output_tokens` | Token 消耗统计 |
| `cache_read_tokens / cache_write_tokens` | Prompt Cache 成本优化分析 |
| `reasoning_tokens` | Extended Thinking 成本分析 |
| `tool_call_count` | 工具使用强度 |
| `message_count` | 对话长度分布 |
| `started_at / last_active` | 活动规律计算 |
| `total_cost_usd` | 实际计费成本（若平台支持） |
| `billing_provider` | 成本估算时确定 Provider |

---

## 4. 分析维度

### 4.1 Token 与成本估算

`usage_pricing.py` 提供了完整的定价体系：

```python
@dataclass(frozen=True)
class CanonicalUsage:
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_tokens: int = 0     # Prompt Cache 读
    cache_write_tokens: int = 0     # Prompt Cache 写
    reasoning_tokens: int = 0       # Extended Thinking
    request_count: int = 1

CostStatus = Literal["actual", "estimated", "included", "unknown"]
```

**成本估算流程**：

```python
result = estimate_usage_cost(
    model,
    CanonicalUsage(input=..., output=..., cache_read=..., cache_write=...),
    provider=billing_provider,
    base_url=billing_base_url,
)
# result.amount_usd — USD 金额
# result.status — "actual" | "estimated" | "included" | "unknown"
```

**分级状态**：
- `actual`：平台 API 返回真实成本（如 OpenRouter、OpenAI）
- `estimated`：基于官方定价表估算
- `included`：已含在订阅中（如 Claude 订阅用户）
- `unknown`：无法确定（如自定义 endpoint）

### 4.2 模型分布（`_compute_model_breakdown`）

按模型聚合会话：

```python
# 输出格式
{"model": "gpt-4o", "sessions": 38, "messages": 842, "tool_calls": 412,
 "total_tokens": 1_240_000, "input_tokens": 892_000, "output_tokens": 348_000,
 "cost_usd": 3.47}
```

### 4.3 平台分布（`_compute_platform_breakdown`）

支持多平台分析：`cli` / `telegram` / `discord` / `feishu` / `gateway` 等。

### 4.4 工具使用排行（`_compute_tool_breakdown`）

从 `tool_calls` 表聚合工具调用频率，计算百分比分布：

```python
{"tool": "terminal", "count": 412, "percentage": 46.2, "sessions": 38}
{"tool": "memory_tool", "count": 89, "percentage": 10.0, "sessions": 21}
```

### 4.5 Skill 使用统计（`_compute_skill_breakdown`）

从 `skill_events` 表聚合 Skill 事件：

```python
{"skill": "hyperframes", "view_count": 47, "manage_count": 12, "last_used_at": ts}
```

### 4.6 活动规律（`_compute_activity_patterns`）

- **最繁忙日/小时**：统计 `started_at` 按星期几和小时分布
- **连续活跃天数**：`max_streak` 计算最长连续使用天数
- **`█` 字符柱状图**：`_bar_chart(values, max_width=20)` 生成 ASCII 条形图

### 4.7 Top Sessions（`_compute_top_sessions`）

按 `last_active` 排序，取 Top 5 最长会话，包含标题、消息数、工具调用数、Token 量。

---

## 5. CLI / Gateway / Discord 三端集成

### 5.1 CLI 入口

```python
# hermes_cli/main.py:9933
insights_parser = subparsers.add_parser("insights", help="Show usage insights and analytics")
insights_parser.add_argument("--days", type=int, default=30, help="Number of days to analyze")
insights_parser.add_argument("--source", type=str, default=None, help="Filter by source (e.g. cli, telegram)")
# 使用 db = SessionDB（SessionDB.context_path()）
```

用户执行：`hermes insights --days 7 --source telegram`

### 5.2 Gateway 入口

```python
# gateway/run.py:10647
async def _handle_insights_command(self, event: MessageEvent) -> str:
    """Handle /insights command"""
    # 解析 /insights 7 或 /insights --days 7
    # 在线程池运行：loop.run_in_executor(None, _run_insights)
    # 返回 format_gateway() 格式的 Markdown 字符串
```

### 5.3 Discord 入口

```python
# platforms/discord.py:2645
async def slash_insights(interaction: discord.Interaction, days: int = 7):
    # 使用 Discord 的原生 slash command
    # 同样调用 InsightsEngine.generate() + format_gateway()
```

三端共用同一个 `InsightsEngine.generate()`，差异化仅在输出格式化。

---

## 6. BlueCortexCE 借鉴

### 6.1 可迁移的功能设计

| Hermes 设计 | CE 当前状态 | 借鉴建议 | 实施难度 |
|-----------|------------|---------|---------|
| InsightsEngine SQL 直接查 SessionDB | CE SessionEntity 已有 token 字段 | 直接复用查询模式 | 低 |
| `CanonicalUsage` dataclass | CE ObservationEntity 无结构化 token 统计 | 新增 `TokenUsage` 字段或独立表 | 中 |
| 成本估算（`estimate_usage_cost`） | CE 完全无成本估算 | 接入 OpenAI API pricing 表 | 中 |
| `format_gateway()` Markdown 输出 | CE API 返回 JSON | 新增 `/api/insights` 端点返回结构化 JSON | 低 |
| Top Sessions 排行 | CE 无 Session 排行 | 新增 `/api/sessions/stats` 端点 | 低 |
| 活动规律分析（最繁忙日/小时） | CE 无 activity tracking | SessionEntity 增加 `last_active_hour` 字段 | 中 |
| 多平台分布 | CE 有 channel 字段 | 直接复用 channel 分组逻辑 | 低 |
| Skill 使用统计 | CE 无 skill events 表 | 新增 `SkillEventEntity` | 高 |
| ShareGPT JSONL 格式轨迹导出（`agent/trajectory.py`） | CE 无 RL 训练数据 pipeline | 参考 ShareGPT 格式做轨迹导出 | 中 |

### 6.2 短期可执行行动（< 1 天）

1. **新增 `/api/insights` REST 端点**：
   ```json
   GET /api/insights?days=30
   {
     "overview": { "total_sessions": N, "total_messages": N, "total_tokens": N },
     "models": [...],
     "platforms": [...],
     "tools": [...],
     "activity": {...}
   }
   ```
   直接复用 `ObservationEntity` / `SessionEntity` SQL 查询，零新表。

2. **新增 `TokenUsage` 字段到 `SessionEntity`**：记录 `input_tokens` / `output_tokens` / `cache_tokens`，为未来成本分析奠基。

### 6.3 中期可执行行动（< 1 周）

3. **接入 OpenAI API pricing 表**（参考 `usage_pricing.py`），在 `ObservationEntity` 记录 `estimated_cost_usd`。

4. **Top Sessions 排行 API**：`GET /api/sessions/top?limit=5&by=duration|messages|tokens`

5. **ShareGPT JSONL 轨迹导出**：参考 doc 56 `agent/trajectory.py` 分析，为 RL 训练数据 pipeline 奠基。

### 6.4 与 Phase 3 的关系

Phase 3 的 Structured Extraction 产物（用户偏好、过敏信息等）是**记忆内容**，而 InsightsEngine 分析的是**使用元数据**（token 量、成本、活动规律）。两者互补：

- Insights → **量化**记忆系统的使用价值（消耗多少 tokens、调用多少次 memory_tool）
- Structured Extraction → **质量**记忆内容（记忆了什么有价值的信息）

建议在 Phase 3 完成后，将 InsightsEngine 的量化指标与 Structured Extraction 的质量指标结合，形成完整的记忆系统价值评估体系。

---

## 7. 关键源码文件

| 文件 | 行数 | 核心职责 |
|------|------|---------|
| `agent/insights.py` | 930 | InsightsEngine 主体（分析 + 格式化） |
| `agent/usage_pricing.py` | 721 | CanonicalUsage dataclass / cost estimation / pricing tables |
| `agent/model_metadata.py` | — | 模型元数据查询（context length、pricing source） |
| `hermes_cli/main.py:9945` | — | CLI `insights` 子命令 |
| `gateway/run.py:10647` | — | Gateway `/insights` handler |
| `platforms/discord.py:2645` | — | Discord `/insights` slash command |

---

## 8. CE 当前差距

1. **无独立分析引擎**：CE 没有 `InsightsEngine` 等效组件，无法量化会话使用数据
2. **无成本估算**：SessionEntity 缺少 `input_tokens` / `output_tokens` 等计量字段
3. **无活动规律追踪**：无 Top Sessions / 活动分布 / Streak 等可观测性数据
4. **无 Skill Event 表**：无法追踪 Skill 使用频率和效果
5. **轨迹导出缺失**：无 RL 训练数据 pipeline（ShareGPT JSONL）
