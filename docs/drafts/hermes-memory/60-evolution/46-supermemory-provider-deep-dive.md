# Supermemory Provider 深度解析（2026-04-25）

**源码**：`plugins/memory/supermemory/__init__.py`（791 行）  
**与 `doc 09`（Supermemory Capture Lifecycle）的关系：本文聚焦 **Provider 内部架构**，doc 09 聚焦 **会话生命周期中的调用时序**；两者互补。

---

## 1. 核心架构概览

Supermemory Provider 采用**云端 API + 本地合成**架构：

| 组件 | 职责 |
|------|------|
| `_SupermemoryClient` | 封装 `supermemory` Python SDK（`documents.add` / `search.memories` / `profile()` / `ingest_conversation` HTTP） |
| `SupermemoryMemoryProvider` | MemoryProvider ABC 实现 + 4 工具（store/search/forget/profile）|
| `initialize()` | 初始化 client、container tag 解析、write gating |
| `prefetch()` | 主动召回 + profile 周期性拉取 |
| `sync_turn()` | 每轮对话增量捕获（非阻塞） |
| `on_session_end()` | 会话结束时批量摄入完整对话 |
| `on_memory_write()` | 处理 `add` action，写入显式记忆 |

**多容器隔离**（v2 新增）：primary container + custom containers 白名单，支持 `{identity}` 模板变量实现 per-agent 隔离。

---

## 2. 三类 Deduplication 机制（§2）

Supermemory 的召回结果分为三类，每类独立去重：

```python
def _deduplicate_recall(static_facts, dynamic_facts, search_results):
    seen = set()
    # 三类分开去重，防止跨类重复
```

| 类型 | 来源 | 说明 |
|------|------|------|
| **static** | `profile().static` | 持久用户画像（偏好、习惯） |
| **dynamic** | `profile().dynamic` | 近期限制上下文 |
| **search** | `search.memories()` | 语义搜索命中，按相似度排序 |

三类分别取前 `max_results` 合并展示，**相似度分数 × 时间戳**双维标注。

**CE 借鉴**：BlueCortexCE 的 Observation/Summary 混同展示时可参照此三分法，静态 facts（长期偏好）、动态 facts（当前上下文）、检索结果（语义相关）分层展示，避免异质信息混杂。

---

## 3. Trivial Message 过滤（§3）

```python
_TRIVIAL_RE = re.compile(
    r"^(ok|okay|thanks|thank you|got it|sure|yes|no|yep|nope|k|ty|thx|np)\.?$",
    re.IGNORECASE,
)

def _is_trivial_message(text: str) -> bool:
    return bool(_TRIVIAL_RE.match((text or "").strip()))
```

`sync_turn()` 在 `capture_mode=all` 时：
1. 最小长度检查（user ≥ 10 chars，assistant ≥ 10 chars）
2. trivial regex 过滤（一次性确认语）
3. 上下文标签剥离（`<supermemory-context>` / `<supermemory-containers>`）

**CE 借鉴**：Claude-Mem 在 Observation ingestion 时无 trivial 过滤，会将 "ok", "thanks" 等短确认语存入 DB，污染语义检索。建议在 `IngestionService` 的 `ingest` 路径增加：
- 最小内容长度阈值（建议 ≥ 15 字符）
- 确认语正则黑名单

---

## 4. Profile Frequency 节流（§4）

```python
DEFAULT_PROFILE_FREQUENCY = 50  # 每 50 轮拉取一次 profile

def prefetch(self, query: str, *, session_id: str = "") -> str:
    include_profile = self._turn_count <= 1 or \
                      (self._turn_count % self._profile_frequency == 0)
    context = _format_prefetch_context(
        static_facts=profile["static"] if include_profile else [],
        dynamic_facts=profile["dynamic"] if include_profile else [],
        search_results=profile["search_results"],
        max_results=self._max_recall_results,
    )
```

**设计意图**：不在每轮都拉取 profile，只在 turn 0（首轮）或每 `profile_frequency` 轮拉取 static/dynamic facts，减少 API 调用。

**CE 借鉴**：`ContextService.generateContext()` 目前每次请求都做 DB 查询 + embedding 生成，频繁拉取增加 LLM 调用成本。可引入类似 `profile_frequency` 的节流机制：
- turn 0：强制拉取完整 context
- 后续每 N 轮拉取一次完整 summary
- 中间轮次仅做 semantic search（按需）

---

## 5. Write Gating — Agent Context 感知（§5）

```python
def initialize(self, session_id: str, **kwargs) -> None:
    agent_context = kwargs.get("agent_context", "")
    self._write_enabled = agent_context not in ("cron", "flush", "subagent")
    self._active = bool(self._api_key)
```

**写入禁用场景**：
- `cron`：定时巡检任务，不应污染用户记忆
- `flush`：内存 flush 操作，本身是系统动作
- `subagent`：子代理，不应继承父代理的写入权限

**CE 借鉴**：Claude-Mem 的 `AgentService` 中，subagent session 的 observation 不应写入父 session 的 memory namespace。建议：
- `AgentService.spawnAgent()` 时传递 `isSubagent=true` flag
- Ingestion path 检查该 flag，拒绝 subagent 的 observation 写入

---

## 6. Multi-Container 架构（§6）

```python
# 容器 tag 白名单验证
def _resolve_tool_container_tag(self, args: dict) -> Optional[str]:
    if not self._enable_custom_containers:
        return None
    tag = _sanitize_tag(args.get("container_tag", ""))
    if sanitized not in self._allowed_containers:
        raise ValueError(f"Container tag '{sanitized}' is not allowed.")
    return sanitized

# 模板变量解析
raw_tag = env_tag or self._config["container_tag"]
identity = kwargs.get("agent_identity", "default")
self._container_tag = _sanitize_tag(raw_tag.replace("{identity}", identity))
```

**关键特性**：
1. `{identity}` 占位符支持 per-agent 隔离 container
2. 工具参数中可选 `container_tag` 指定目标容器
3. 未授权的 tag 直接抛 ValueError（安全边界）

**CE 借鉴**：`ObservationEntity` 的 namespace 设计可参照：
- agent identity → namespace 隔离
- container tag → memory_type 或 tags 多维分类
- tool call 时可选指定 namespace/tags

---

## 7. Entity Context — 可配置的提取提示词（§7）

```python
_DEFAULT_ENTITY_CONTEXT = (
    "User-assistant conversation. Format: [role: user]...[user:end] and "
    "[role: assistant]...[assistant:end].\n\n"
    "Only extract things useful in future conversations. Most messages are not worth remembering.\n\n"
    "Remember lasting personal facts, preferences, routines, tools, ongoing projects, "
    "working context, and explicit requests to remember something.\n\n"
    "Do not remember temporary intents, one-time tasks, assistant actions, "
    "implementation details, or in-progress status.\n\n"
    "When in doubt, store less."
)
```

`entity_context` 作为**可配置的系统提示词**发送给 Supermemory API，控制提取质量：
- 强调"只记持久信息"
- 明确负面清单（temporary intents、implementation details）
- "When in doubt, store less" — 宁少勿多

**CE 借鉴**：Phase 3 Structured Extraction 的 YAML template 中，`extraction_instructions` 字段可参照此设计，提供：
- 正面清单（要提取的字段及含义）
- 负面清单（不要提取的内容）
- 默认策略（保守提取 vs 积极提取，可配置）

---

## 8. Session-End 批量摄入（§8）

```python
def on_session_end(self, messages: List[Dict[str, Any]]) -> None:
    # 清洗：只保留 user/assistant，内容抽签，丢弃系统消息
    cleaned = []
    for message in messages:
        role = message.get("role")
        if role not in ("user", "assistant"):
            continue
        content = _clean_text_for_capture(str(message.get("content", "")))
        if content:
            cleaned.append({"role": role, "content": content})
    # 极短单消息直接丢弃
    if len(cleaned) == 1 and len(cleaned[0].get("content", "")) < 20:
        return
    # HTTP POST 批量摄入
    self._client.ingest_conversation(self._session_id, cleaned)
```

**与 `sync_turn` 的区别**：
- `sync_turn`：每轮增量写入（实时性）
- `on_session_end`：会话结束批量摄入完整上下文（完整性保障，防止中途丢失）

**CE 借鉴**：Claude-Mem 目前仅靠 `SummaryService.summary()` 做周期性压缩，没有会话结束时的批量摄入保障。若 agent 因崩溃中断，部分 turn 可能既无 summary 也无 observation。建议：
- `AgentService` 的 session end lifecycle hook 中调用 `flushPendingMessages()`
- 或在 `ContextService` 中增加 `finalizeSession(sessionId)` 接口

---

## 9. 工具 Schema 设计（§9）

| 工具 | 签名 | 说明 |
|------|------|------|
| `supermemory_store` | `content: str, metadata?: obj, container_tag?: str` | 显式存储记忆，自动推断 category |
| `supermemory_search` | `query: str, limit?: int, container_tag?: str` | 语义搜索，支持多容器 |
| `supermemory_forget` | `id?: str, query?: str, container_tag?: str` | 按 ID 或 query 模糊删除 |
| `supermemory_profile` | `query?: str, container_tag?: str` | 拉取用户 profile（static + dynamic） |

**设计亮点**：forget 支持 query 模糊匹配（取 top-1 结果删除），降低工具调用复杂度。

**CE 差距**：`ObservationService` 无等效的 bulk forget 或 profile API。Search service 只有 semantic search，无 profile/summary 聚合接口。

---

## 10. 线程模型（§10）

| 线程 | 类型 | 职责 | join 策略 |
|------|------|------|-----------|
| `_sync_thread` | daemon | 每轮增量 sync | join(timeout=2.0) 后重建 |
| `_write_thread` | non-daemon | on_memory_write 写入 | join(timeout=2.0) 后重建 |
| `_prefetch_thread` | 无独立线程 | prefetch 同步执行（query 简单） | 无 |

**注意**：`_write_thread` 是 `non-daemon`（`daemon=False`），确保 shutdown 时能完成写入。

**CE 借鉴**：当前 Claude-Mem 的 observation ingestion 是同步的（如 `IngestionService.ingest()`），高并发时可能阻塞 agent turn。建议：
- 使用 async task queue 而非同步写入
- shutdown hook 等待 pending writes 完成（非 daemon thread join）

---

## 11. CE 可执行借鉴优先级

| 优先级 | 借鉴项 | 工作量 | 难度 |
|--------|--------|--------|------|
| 🔴 高 | Trivial message 过滤（`sync_turn` 路径） | 低 | 低 |
| 🔴 高 | Write gating for subagent（`agent_context` 感知） | 中 | 低 |
| 🟡 中 | Profile frequency 节流 | 中 | 中 |
| 🟡 中 | Session-end 批量摄入保障 | 中 | 中 |
| 🟢 低 | Multi-container namespace 隔离 | 高 | 高 |
| 🟢 低 | Entity context 可配置提取提示词 | 中 | 中（Phase 3 已有） |

---

*文档创建：2026-04-25 16:50 CST；源码行数：791；CE 差距分析已覆盖。*
