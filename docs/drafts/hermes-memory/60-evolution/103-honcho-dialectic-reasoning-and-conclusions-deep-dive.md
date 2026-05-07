# Honcho Dialectic Reasoning + Conclusions System 深度解析

> **分析目标**：Hermes Agent `plugins/memory/honcho/` 中尚未被独立深度文档化的两大子系统：
> 1. **Dialectic Multi-Pass Reasoning** — 多轮 LLM 推理合成用户模型
> 2. **Conclusions + AI Identity Seeding** — 结构化事实写入与 AI 自描述导入
>
> **对应上游 commit**：Honcho plugin（最后更新 `plugins/memory/honcho/__init__.py` 1253 行，`session.py` 1255 行）
> **文档大小**：~14KB

---

## 1. 系统定位

Honcho Memory Provider 是 Hermes Agent 的外部记忆 Provider，核心功能分三层：

| 层 | 组件 | 作用 |
|----|------|------|
| Base Context | `get_prefetch_context()` → `representation` + `card` | 快速结构化快照，无 LLM 推理 |
| Dialectic Supplement | `_run_dialectic_depth()` 多轮 LLM 调用 | 深度推理合成，消耗更高 |
| Tool Surface | `honcho_profile` / `honcho_search` / `honcho_conclude` | Agent 主动调用工具访问记忆 |

**本文重点**：Dialectic 层 + Conclusions 写入机制。

---

## 2. Dialectic Multi-Pass Reasoning

### 2.1 核心设计思想

Dialectic（辩证）层通过**多轮 LLM 调用**对用户进行深度推理，不同于简单的 semantic search：

- **Semantic Search**（`search_context`）：直接返回原始片段，模型自己合成
- **Dialectic**（`dialectic_query`）：LLM 主动推理，跨多轮自我修正，最终输出**合成结论**

Dialectic 调用链路：
```
Agent query
  └─> HonchoSessionManager.dialectic_query(session_key, query, reasoning_level, peer)
        └─> _PeerProxy.chat(query, reasoning_level)  [Honcho 后端 LLM]
```

### 2.2 多轮推理（Multi-Pass Depth）

`_run_dialectic_depth()` 方法实现最多 3 轮推理（`dialecticDepth` 配置项）：

```python
def _run_dialectic_depth(self, query: str) -> str:
    is_cold = not self._base_context_cache  # 冷启动：无历史上下文
    results: list[str] = []

    for i in range(self._dialectic_depth):
        if i == 0:
            prompt = self._build_dialectic_prompt(0, results, is_cold)
        else:
            # 提前退出：前轮已有足够信号
            if results and self._signal_sufficient(results[-1]):
                break
            prompt = self._build_dialectic_prompt(i, results, is_cold)

        level = self._resolve_pass_level(i, query=query)
        result = self._manager.dialectic_query(
            self._session_key, prompt,
            reasoning_level=level, peer="user",
        )
        results.append(result or "")

    # 返回最后一个非空结果
    for r in reversed(results):
        if r and r.strip():
            return r
    return ""
```

**提前退出（Early Bailout）**：如果某轮返回了"强信号"（结构化输出），跳过后续轮次，节省 tokens。

### 2.3 各轮 Prompt 设计

| Pass | 触发条件 | Prompt 目标 | Cold Start | Warm Session |
|------|----------|-------------|-----------|--------------|
| **Pass 0** | 始终 | 初始用户画像 | "Who is this person? preferences, goals, working style?" | "Given session so far, what's most relevant to current conversation?" |
| **Pass 1** | depth ≥ 2 | 自我审计 / 填补空白 | N/A | "What gaps remain in your understanding?" |
| **Pass 2** | depth = 3 | 矛盾调和 / 最终综合 | N/A | "Do these assessments cohere? Reconcile contradictions." |

**Pass 0 冷/热区分**：
- `is_cold = not self._base_context_cache`：首次调用时无 base context，LLM 需生成通用画像
- 后续调用有 session 历史，LLM 聚焦当前会话相关上下文

**Pass 1 审计逻辑**：输入 Pass 0 的结果，要求 LLM 主动识别"认知空白"并填补。

**Pass 2 调和逻辑**：输入 Pass 0 和 Pass 1 的结果，检查一致性，输出最终综合结论。

### 2.4 Signal Sufficiency（信号充足性）

`_signal_sufficient()` 决定是否提前退出：

```python
@staticmethod
def _signal_sufficient(result: str) -> bool:
    if not result or len(result.strip()) < 100:
        return False
    # 有结构化输出（section headers、bullets、numbered lists）= 强信号
    if "\n" in result and (
        "##" in result
        or "•" in result
        or re.search(r"^[*-] ", result, re.MULTILINE)
        or re.search(r"^\s*\d+\. ", result, re.MULTILINE)
    ):
        return True
    return len(result.strip()) > 300  # 长文本也有信号
```

**设计原理**：
- 100 chars 是最低门槛，排除空响应或敷衍回复
- 结构化输出（`##`、`•`、`- `）= LLM 认真分析了内容
- 无结构但超过 300 chars = 可能包含实质内容

### 2.5 Reasoning Level（推理深度）

每轮可配置不同推理深度（`dialecticReasoningLevel` 配置项）：

| Level | 含义 |
|-------|------|
| `minimal` | 最快、最便宜、泛化推理 |
| `low` | 基础推理 |
| `medium` | 中等深度 |
| `high` | 深度分析 |
| `max` | 最深度推理 |

**Proportional Levels（多轮时自动降级）**：

```python
_PROPORTIONAL_LEVELS: dict[tuple[int, int], str] = {
    (1, 0): "base",           # depth=1: 单轮用基础级别
    (2, 0): "minimal",        # depth=2: 首轮轻量
    (2, 1): "base",           # depth=2: 次轮基础
    (3, 0): "minimal",        # depth=3: 首轮轻量
    (3, 1): "medium",        # depth=3: 次轮中等
    (3, 2): "base",           # depth=3: 第三轮基础（最终综合）
}
```

**推理启发式**（`_apply_reasoning_heuristic`）：
- 查询 ≥ 400 chars → 在基础级别上 +2 档
- 查询 ≥ 120 chars → 在基础级别上 +1 档
- 上限为 `reasoning_level_cap`

### 2.6 Cadence + Empty Streak Backoff

Dialectic 不每轮调用，而是按 `dialecticCadence`（默认 1，即每轮调用）控制频率：

```python
effective = self._effective_cadence()
if (self._turn_count - self._last_dialectic_turn) < effective:
    return  # 跳过
```

**Empty Streak Backoff**：当 dialectic 连续返回空结果时，临时扩大 cadence：

```python
def _effective_cadence(self) -> int:
    if self._dialectic_empty_streak <= 0:
        return self._dialectic_cadence
    widened = self._dialectic_cadence + self._dialectic_empty_streak
    ceiling = self._dialectic_cadence * self._BACKOFF_MAX  # 8x 上限
    return min(widened, ceiling)
```

**原理**：Honcho 后端可能暂时不可用（网络、API 限流），连续空响应后逐步降低调用频率，避免无效重试。

### 2.7 First Turn 特殊处理

第一轮用户消息有特殊处理（`_is_trivial_prompt` 检查）：

```python
if self._is_trivial_prompt(query):
    return ""  # "yes", "ok", "thanks" 等不触发 context injection
```

```python
@classmethod
def _is_trivial_prompt(cls, text: str) -> bool:
    if not text:
        return True
    stripped = text.strip()
    if stripped.startswith("/"):
        return True  # slash commands
    return bool(cls._TRIVIAL_PROMPT_RE.match(stripped))
```

- One-word 肯定回复不触发记忆注入
- 节省 tokens，避免用户模型上下文污染简单回复

---

## 3. Conclusions System（结构化事实写入）

### 3.1 概念

**Conclusion**：Agent 主动写回 Honcho 的结构化事实，关于某个 peer（用户或 AI）。

这些 facts 会被 Honcho 的 reasoning model 吸收，影响后续的 `representation` 和 `card` 生成。

### 3.2 API

```python
def create_conclusion(
    self, session_key: str, content: str, peer: str = "user"
) -> bool:
    """写入一条关于目标 peer 的结论"""
```

**写入逻辑**：
```python
# 确定目标 peer
target_peer_id = self._resolve_peer_id(session, peer)

# AI 观察模式：AI peer 的 conclusions_of(目标)
if self._ai_observe_others:
    assistant_peer = self._get_or_create_peer(session.assistant_peer_id)
    conclusions_scope = assistant_peer.conclusions_of(target_peer_id)
else:
    # 自己写给自己
    target_peer = self._get_or_create_peer(target_peer_id)
    conclusions_scope = target_peer.conclusions_of(target_peer_id)

conclusions_scope.create([{
    "content": content.strip(),
    "session_id": session.honcho_session_id,
}])
```

**设计亮点**：
- `ai_observe_others` 模式：AI 可以观察用户，形成"AI 对用户的理解"
- 普通模式：peer 自我反思，写入对自己的理解
- 每个 conclusion 关联 `session_id`，可追溯来源会话

### 3.3 删除 Conclusion

```python
def delete_conclusion(
    self, session_key: str, conclusion_id: str, peer: str = "user"
) -> bool:
    """删除一条 conclusion"""
```

### 3.4 CE 借鉴价值

**当前 CE 缺失**：BlueCortexCE 的 Observation 是 Agent 被动记录，没有主动"纠正/补充"机制。

**可借鉴设计**：
1. Agent 可通过 tool call 主动写入"校正结论"
2. 结论按 session 归档，可追溯来源
3. Conclusions 最终被 LLM reasoning model 吸收，影响 future context

---

## 4. AI Identity Seeding（AI 自描述导入）

### 4.1 设计目标

从外部来源（SOUL.md、导出的对话、任何结构化描述）导入 AI 的自我描述到 Honcho：

```python
def seed_ai_identity(
    self, session_key: str, content: str, source: str = "manual"
) -> bool:
```

### 4.2 实现

```python
wrapped = (
    f"<ai_identity_seed>\n"
    f"<source>{source}</source>\n"
    f"\n"
    f"{content.strip()}\n"
    f"</ai_identity_seed>"
)
honcho_session.add_messages([assistant_peer.message(wrapped)])
```

**设计亮点**：
- XML 标签包裹，提供结构化上下文
- `source` 元标签可追溯来源（`soul_md`、`export`、`manual`）
- 作为 `assistant_peer.message()` 写入，等同于 AI 的一条自述消息

### 4.3 CE 借鉴价值

BlueCortexCE 可类似地将 SOUL.md 内容注入到 AI 的自描述记忆中：

```
<ai_identity_seed>
<source>soul_md</source>

[SOUL.md 内容]
</ai_identity_seed>
```

使 AI 的身份定义随时间被 Honcho reasoning model 理解和运用。

---

## 5. 其他未深度覆盖的方法

### 5.1 `get_session_context`

```python
def get_session_context(self, session_key: str, peer: str = "user") -> dict[str, Any]:
    """返回 session 的完整状态（summary + representation + card）"""
```

返回结构：
```python
{
    "summary": "...",           # 会话摘要
    "representation": "...",    # Honcho 合成的用户画像
    "card": ["fact1", "fact2"], # 结构化事实列表
}
```

### 5.2 `search_context`

语义搜索接口（无 LLM 推理）：
```python
def search_context(
    self, session_key: str, query: str,
    max_tokens: int = 800, peer: str = "user"
) -> str:
    """返回与 query 相关的 raw excerpts"""
```

- 比 `dialectic_query` 更快、更便宜
- 适合事实性查询，模型自己合成
- `max_tokens` 控制输出长度

### 5.3 `get_ai_representation`

```python
def get_ai_representation(self, session_key: str) -> dict[str, str]:
    """获取 AI peer 的当前 Honcho representation"""
    # returns {"representation": "", "card": ""}
```

---

## 6. 完整调用图

```
Agent prompt
    │
    ├─> MemoryStore (MEMORY.md / USER.md)
    │       └─> format_for_system_prompt()
    │
    ├─> HonchoMemoryProvider.prefetch(query)
    │       │
    │       ├─> Layer 1: Base Context (同步首轮 / 缓存后续)
    │       │       └─> _manager.get_prefetch_context()
    │       │               └─> representation + card
    │       │
    │       └─> Layer 2: Dialectic Supplement (后台线程)
    │               └─> _run_dialectic_depth(query)
    │                       ├─> Pass 0: cold/warm → dialectic_query()
    │                       ├─> [Pass 1: gap audit] → dialectic_query()
    │                       └─> [Pass 2: reconciliation] → dialectic_query()
    │
    └─> Agent Tools (按需调用)
            ├─> honcho_profile() → get_peer_card()
            ├─> honcho_search() → search_context()
            ├─> honcho_context() → get_session_context()
            ├─> honcho_reasoning() → dialectic_query()
            └─> honcho_conclude() → create_conclusion()
```

---

## 7. 与 BlueCortexCE 的对照

| 维度 | Hermes Honcho | BlueCortexCE |
|------|--------------|---------------|
| 用户模型 | Honcho 后端 LLM dialectic reasoning | Observation + Summary 固定结构 |
| 推理深度 | configurable depth (1-3) + reasoning level | 单一 summary 提取 |
| 自适应退出 | signal sufficiency early bailout | 无 |
| 频率控制 | dialectic cadence + empty streak backoff | 无 |
| 用户校正 | create_conclusion 主动写入 | Observation 为单向记录 |
| AI 自描述 | seed_ai_identity 从 SOUL.md 导入 | 无（SOUL.md 仅注入 prompt） |
| 语义搜索 | Honcho backend semantic search | pgvector semantic search |

---

## 8. CE 可执行借鉴

### P2：多轮推理 Synthesis

CE 当前 Phase 3 的 Structured Extraction 是单次 LLM 调用。可设计多轮版本：

```
Observation 生成后
  └─> Pass 1: 识别缺失字段 → 补充询问用户
  └─> Pass 2: 矛盾检测 → 最终合成
```

### P3：用户校正机制

增加 `record_correction(sessionId, field, correctedValue)` 接口，允许用户主动纠正 AI 误解，存储在单独的 `CorrectionEntity` 中，影响后续 context 生成。

### P3：AI Identity Seeding

将 SOUL.md 内容通过 structured extraction 处理后，注入到 AI 的自描述 context 中，而非仅作为静态 system prompt。
