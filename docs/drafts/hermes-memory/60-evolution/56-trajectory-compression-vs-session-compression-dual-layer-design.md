# Trajectory Compression vs Session Compression — 双层压缩架构分析

> **文档状态**: v1.0 (2026-05-04 新增)
> **数据来源**: `trajectory_compressor.py` (65305 bytes) + `agent/trajectory.py` + `agent/insights.py`
> **前置阅读**: [`24-context-compressor-full-algorithm.md`](24-context-compressor-full-algorithm.md)（Session 级 ContextCompressor 完整算法解析）
> **关联**: [`48-flush-memories-removal-and-background-review-architecture.md`](48-flush-memories-removal-and-background-review-architecture.md)（Background Review 接管 flush_memories）

---

## 1. 核心发现：双层压缩架构

Hermes Agent 存在**两个独立的压缩系统**，服务于完全不同的目的：

| 维度 | Session 级 ContextCompressor | Trajectory 级 trajectory_compressor.py |
|------|------------------------------|--------------------------------------|
| **目的** | 保持活跃会话在模型 context limit 内 | 将完成的对话压缩为 RL 训练数据 |
| **触发时机** | 实时：每轮对话后检查是否超过 threshold | 离线：轨迹文件后处理 |
| **位置** | `run_agent.py` 主循环（~1000 行） | 独立脚本 `trajectory_compressor.py` |
| **目标** | 当前活跃 `messages[]` 列表 | JSONL 文件中的历史轨迹 |
| **压缩粒度** | 按 message 边界（保留工具调用对完整性） | 按 turn 边界（保护首尾 turn） |
| **摘要模型** | AuxiliaryClient（实时，成本敏感） | OpenRouter Gemini 3 Flash（固定配置） |
| **输出** | 更新 in-memory `messages[]` | 输出 `_compressed.jsonl` 文件 |
| **并发** | 单线程（嵌入主循环） | 多线程 + semaphore（`num_workers=4`, `max_concurrent=50`） |
| **目标 token** | `threshold_tokens`（context_length × 50%） | `target_max_tokens=15250`（固定） |

**设计意图**：Session 压缩是"在线"操作，不可避免；Trajectory 压缩是"离线"操作，用于训练数据准备，不影响用户感知延迟。

---

## 2. TrajectoryCompressor 完整算法解析

### 2.1 压缩策略（4 步）

```
原始轨迹:
[SYSTEM, HUMAN, GPT, TOOL_CALL, TOOL_RESULT, GPT, TOOL_CALL, TOOL_RESULT, ..., GPT, TOOL_RESULT, GPT, HUMAN?]

保护区域:
- protect_first_system: SYSTEM
- protect_first_human: HUMAN
- protect_first_gpt: GPT (第二个消息)
- protect_first_tool: TOOL_CALL + TOOL_RESULT (第一个工具调用对)
- protect_last_n_turns: 最后 4 个 turn

压缩区域: 中间的所有 turn
```

### 2.2 压缩目标配置

```python
@dataclass
class CompressionConfig:
    target_max_tokens: int = 15250      # 目标：压缩到 15K tokens 以内
    summary_target_tokens: int = 750     # 摘要部分上限 750 tokens
    protect_first_system: bool = True    # 保留 system prompt
    protect_first_human: bool = True     # 保留首个 user message
    protect_first_gpt: bool = True       # 保留首个 assistant response
    protect_first_tool: bool = True     # 保留首个 tool call + result
    protect_last_n_turns: int = 4       # 保留最后 4 个 turn
    summarization_model: str = "google/gemini-3-flash-preview"  # 固定摘要模型
    temperature: float = 0.3             # 低温度确保摘要一致性
```

### 2.3 摘要 Prompt 模板

```python
# 来自 trajectory_compressor.py 的摘要模板
SUMMARY_TEMPLATE = """You are a trajectory summarizer.
Compress this agent trajectory into ~{summary_target_tokens} tokens.
Focus on:
- Final actions and outcomes
- Key decisions
- Important tool calls that led to results
- Any errors or retries

Format: Summary paragraph followed by tool call list.
"""
```

### 2.4 压缩流程

```python
def compress_trajectory(self, trajectory: List[Dict], config: CompressionConfig):
    # Step 1: 计算 token 数
    original_tokens = self._count_tokens(trajectory)

    # Step 2: 如果已在目标内，跳过
    if original_tokens <= config.target_max_tokens:
        return trajectory, "skipped"

    # Step 3: 定位保护区域和压缩区域
    protected, middle, last = self._split_trajectory(trajectory, config)

    # Step 4: 压缩 middle turn
    if middle:
        summary = self._summarize_turns(middle, config)
        compressed_middle = [HUMAN_SUMMARY_MSG(summary)]
    else:
        compressed_middle = []

    # Step 5: 合并
    result = protected + compressed_middle + last

    # Step 6: 验证
    final_tokens = self._count_tokens(result)
    return result, "compressed" if final_tokens < original_tokens else "failed"
```

### 2.5 批处理架构

```python
# 并发处理：多线程 + semaphore 控制并发
async def process_trajectories(self, trajectories, config):
    semaphore = asyncio.Semaphore(config.max_concurrent_requests)  # 50 并发

    async def process_one(traj):
        async with semaphore:
            return await self._compress_single(traj, config)

    tasks = [process_one(t) for t in trajectories]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    # 统计
    return self._compute_metrics(results)
```

**关键设计**：
- `max_concurrent_requests=50`：避免 API rate limit
- `per_trajectory_timeout=300s`：单个轨迹 5 分钟超时
- `skip_under_target=True`：已在目标内则跳过，不浪费 API 调用
- `save_over_limit=True`：即使压缩后仍超限，也保存（保留数据）

---

## 3. Trajectory.py — RL 训练数据持久化

```python
# agent/trajectory.py

def save_trajectory(trajectory: List[Dict[str, Any]], model: str,
                    completed: bool, filename: str = None):
    """Append a trajectory entry to a JSONL file.

    轨迹格式: ShareGPT 风格
    """
    if filename is None:
        filename = "trajectory_samples.jsonl" if completed else "failed_trajectories.jsonl"

    entry = {
        "conversations": trajectory,   # ShareGPT format message list
        "timestamp": datetime.now().isoformat(),
        "model": model,
        "completed": completed,
    }

    with open(filename, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")
```

**设计要点**：
- **JSONL 格式**：每行一个 JSON，便于 streaming 处理和 append
- **completed 分流**：成功轨迹写入 `trajectory_samples.jsonl`，失败写入 `failed_trajectories.jsonl`
- **ShareGPT 格式**：行业标准格式，便于导入 RL 框架（如 TRL、veRL）
- **追加写入**：`"a"` mode 支持实时追加，不阻塞主循环

**调用点**：`run_agent.py` 在对话完成或异常退出时调用 `save_trajectory()`

---

## 4. Insights Engine — 历史会话分析

```python
# agent/insights.py

class InsightsEngine:
    """从 SQLite SessionDB 分析历史数据，生成使用洞察"""

    def generate(self, days: int = 30) -> InsightsReport:
        """生成过去 N 天的使用报告"""
        sessions = self._query_sessions(days)
        return InsightsReport(
            token_breakdown=self._token_breakdown(sessions),
            cost_estimate=self._cost_estimate(sessions),
            tool_usage=self._tool_usage_patterns(sessions),
            model_distribution=self._model_distribution(sessions),
            activity_trends=self._activity_trends(sessions),
        )
```

**分析维度**：
1. **Token 消耗分解**：input / output / cache_read / cache_write / reasoning
2. **成本估算**：结合 `usage_pricing.py` 的定价表
3. **工具使用模式**：高频工具、使用成功率
4. **模型分布**：各模型使用占比
5. **活跃趋势**：每日/每周会话数

**数据库查询**：
```python
def _query_sessions(self, days: int) -> List[Dict]:
    cutoff = datetime.now() - timedelta(days=days)
    return self.db.query("""
        SELECT * FROM sessions
        WHERE started_at > ?
        ORDER BY started_at DESC
    """, cutoff)
```

---

## 5. 与 Session 级 ContextCompressor 的关键差异

### 5.1 压缩目标不同

| 方面 | ContextCompressor（Session 级） | TrajectoryCompressor（RL 级） |
|------|--------------------------------|-------------------------------|
| **目标受众** | 正在进行的对话（用户可见） | 训练数据（模型可见） |
| **延迟要求** | 实时（影响响应时间） | 离线（无延迟约束） |
| **摘要模型选择** | AuxiliaryClient（灵活配置） | 固定 Gemini 3 Flash |
| **摘要长度** | 自适应（`_SUMMARY_RATIO=0.20`） | 固定 750 tokens |
| **保护策略** | Token budget tail（~20K tokens） | Fixed turns（首尾固定数量） |
| **迭代压缩** | 支持（保留 `_previous_summary`） | 不支持（离线一次性） |
| **工具结果处理** | 3-pass pruning（dedup/summarize/truncate） | 简单保护（不处理工具结果） |
| **压缩失败处理** | Static fallback marker + continue | 保留原始数据 + save_over_limit |

### 5.2 摘要质量保证

**ContextCompressor** 采用 11 字段 structured template：
```
## Active Task / ## Goal / ## Constraints & Preferences / ## Completed Actions /
## Active State / ## In Progress / ## Blocked / ## Key Decisions /
## Resolved Questions / ## Pending User Asks / ## Relevant Files /
## Remaining Work / ## Critical Context
```

**TrajectoryCompressor** 采用自由格式摘要：
```
Summary paragraph + tool call list (~750 tokens)
```

**原因**：RL 训练数据需要多样性（自由格式更适合学习），Session 压缩需要结构化（确保关键信息不丢失）。

### 5.3 并发模型

```python
# ContextCompressor: 单线程（嵌入主循环）
def run_conversation(self):
    for turn in messages:
        if self.context_compressor.should_compress():
            messages = self.context_compressor.compress(messages)  # 阻塞

# TrajectoryCompressor: 多线程 + semaphore
semaphore = asyncio.Semaphore(50)
tasks = [process_one(t) for t in trajectories]
results = await asyncio.gather(*tasks)  # 并发
```

---

## 6. BlueCortexCE 借鉴建议

### 6.1 双层压缩架构的启发

**CE 当前状态**：CE 只有 Session 级压缩（`MemoryRefineService` 对应 ContextCompressor），没有 Trajectory 级压缩。

**建议**：CE 的 Phase 3 结构化提取结果（structured extraction）可以作为 RL 训练数据的 preparation step：

```python
# CE 可以新增的 trajectory 处理
class TrajectoryProcessor:
    """处理 Claude Code/Cursor 等完成的对话轨迹"""

    def process(self, session_id: str) -> TrajectoryEntry:
        # 1. 获取完整对话历史
        messages = self.session_repo.find_messages(session_id)

        # 2. 应用结构化提取结果（Phase 3）
        extractions = self.extraction_repo.find_latest(session_id)

        # 3. 格式化为 ShareGPT 格式
        entry = self._to_sharegpt_format(messages, extractions)

        # 4. 追加到 trajectory JSONL
        self._append_to_file(entry, "trajectory_samples.jsonl")
```

### 6.2 轨迹分段保护策略的借鉴

**Hermes 策略**：
- 保护首尾 turn（系统提示、首次交互、最后 N 个 action）
- 仅压缩中间区域

**CE 借鉴**：
- 在 `MemoryRefineService` 的摘要中，也可以采用类似策略
- 保护"关键决策点"（`## Key Decisions`）和"未完成任务"（`## Pending User Asks`）
- 压缩中间过程细节（工具调用日志等）

### 6.3 Insights Engine 的借鉴

**CE 可新增**：`GET /api/insights` 端点，分析记忆使用模式：

```json
{
  "period": "30d",
  "memory_stats": {
    "total_observations": 1523,
    "by_type": {
      "code_change": 423,
      "error_resolution": 89,
      "preference": 234
    },
    "extraction_quality": {
      "avg_confidence": 0.87,
      "low_confidence_count": 12
    }
  },
  "cost_breakdown": {
    "embedding_calls": 342,
    "embedding_cost_usd": 0.12
  },
  "session_trends": {
    "avg_session_length": 45,
    "peak_activity_hour": 14
  }
}
```

### 6.4 Trajectory 保存格式的标准化

**建议 CE 采用相同格式**：

```jsonl
{"conversations": [...], "timestamp": "...", "model": "...", "completed": true, "extractions": [...]}
```

**好处**：
- 与现有 RL 训练生态兼容（TRL、veRL 等支持 ShareGPT 格式）
- 便于批量处理和分析
- `completed` 标记支持区分成功/失败案例

---

## 7. 关键设计原则总结

| 原则 | Hermes 实现 | CE 借鉴 |
|------|------------|---------|
| **分离关注点** | Session 压缩（实时）和 Trajectory 压缩（离线）完全分离 | CE 的 refine（在线）和 extraction（离线）可以分离 |
| **保护首尾** | 固定保护 system/first/last turn | 摘要中保护 `Active Task` 和 `Remaining Work` 字段 |
| **自适应预算** | Token budget tail（~20K）保护最新上下文 | 可以设置"保护最近 N 条 observation" |
| **失败安全** | Trajectory 保留原始数据即使超限 | Extraction 失败时保留原始 messages |
| **批处理并发** | Semaphore 控制 50 并发 | CE 批量 ingestion 时也应控制并发 |
| **数据分离** | 成功/失败轨迹分文件存储 | Extraction 结果按 confidence 分级存储 |

---

## 8. 待进一步确认

1. **TrajectoryCompressor** 的压缩效果（压缩率、摘要质量）是否已在 RL 训练中验证？
2. **InsightsEngine** 是否已集成到 CLI `insights` 命令？
3. **ShareGPT 格式**是否是行业标准，还是 Hermes 自创？需要验证 TRL/veRL 的实际格式要求。

---

## 9. 关联文档

- [`24-context-compressor-full-algorithm.md`](24-context-compressor-full-algorithm.md) — Session 级 ContextCompressor 完整算法
- [`48-flush-memories-removal-and-background-review-architecture.md`](48-flush-memories-removal-and-background-review-architecture.md) — Background Review 架构
- [`49-compression-eval-harness-and-structured-extraction-quality.md`](49-compression-eval-harness-and-structured-extraction-quality.md) — 压缩质量评估框架
