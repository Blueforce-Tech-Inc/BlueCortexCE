# RetainDB Provider 深度解析

**来源**：`plugins/memory/retaindb/__init__.py`（766 行，32799 字节）  
**性质**：外部云端 Provider，完整 MemoryProvider 接口实现  
**最后更新**：2026-04-25（基于 upstream `023b1bff`）

---

## 1. 架构定位

RetainDB 是一个**云端跨会话记忆 Provider**，通过 RetainDB Cloud API 实现持久化存储。与本地 Provider（Honcho、Supermemory）不同，RetainDB 强调：

- **云端集中**：记忆存储在 RetainDB 服务器，支持多 Agent/多设备共享
- **本地写缓冲**：SQLite write-behind queue 实现 crash-safe 异步写入
- **Dialectic 合成**：LLM 驱动的用户理解层（`ask_user` API）
- **Agent 自我模型**：SOUL.md 作为 Agent identity 的种子数据
- **共享文件存储**：5 个文件工具，通过 `rdb://` URI 跨 Agent 引用

---

## 2. 核心组件

### 2.1 `_Client` — HTTP API 客户端

负责所有 RetainDB Cloud API 调用。关键设计：

**双路由兜底**：多个 API 方法同时支持新旧两套路由，自动 fallback：
```python
def get_profile(self, user_id: str) -> dict:
    try:
        return self.request("GET", f"/v1/memory/profile/{quote(user_id, safe='')}", ...)
    except Exception:
        return self.request("GET", "/v1/memories", params={...})  # 旧路由兜底
```

**HTTP 头差异化**：不同 API 路径使用不同的认证头：
```python
def _headers(self, path: str) -> dict:
    token = self.api_key.replace("Bearer ", "").strip()
    h = {"Authorization": f"Bearer {token}", "Content-Type": "application/json", "x-sdk-runtime": "hermes-plugin"}
    if path.startswith(("/v1/memory", "/v1/context")):
        h["X-API-Key"] = token  # 双认证头
    return h
```

**API 端点清单**：

| 操作 | 端点 | 方法 |
|------|------|------|
| 语义搜索 | `/v1/memory/search` | POST |
| 用户 Profile | `/v1/memory/profile/{user_id}` | GET |
| 添加记忆 | `/v1/memory` | POST |
| 删除记忆 | `/v1/memory/{id}` | DELETE |
| Session 摄入 | `/v1/memory/ingest/session` | POST |
| 上下文查询 | `/v1/context/query` | POST |
| 用户问答 | `/v1/memory/profile/{user_id}/ask` | POST |
| Agent 模型获取 | `/v1/memory/agent/{agent_id}/model` | GET |
| Agent Identity 播种 | `/v1/memory/agent/{agent_id}/seed` | POST |
| 文件上传 | `/v1/files` | POST (multipart) |
| 文件列表 | `/v1/files` | GET |
| 文件读取 | `/v1/files/{id}/content` | GET (raw bytes) |
| 文件摄入 | `/v1/files/{id}/ingest` | POST |
| 文件删除 | `/v1/files/{id}` | DELETE |

---

## 3. SQLite Write-Behind Queue（`_WriteQueue`）

### 3.1 设计与实现

这是 RetainDB 最具工程价值的部分——一个**crash-safe 的异步写入队列**：

```python
class _WriteQueue:
    """SQLite-backed async write queue. Survives crashes — pending rows replay on startup."""
```

**三层持久化**：
1. **入队时落盘**：每次 `enqueue()` 同时写入 SQLite `pending` 表和内存队列
2. **后台线程消费**：独立 `daemon=True` 线程从队列消费，调用 API
3. **启动时重放**：进程重启后，从 SQLite 恢复未完成的 rows

### 3.2 线程安全

**Thread-local 连接缓存**：
```python
def _get_conn(self) -> sqlite3.Connection:
    conn = getattr(self._local, "conn", None)  # threading.local() 隔离
    if conn is None:
        conn = sqlite3.connect(str(self._db_path), timeout=30)
        conn.row_factory = sqlite3.Row
        self._local.conn = conn
    return conn
```

每个线程复用同一个 SQLite 连接，避免跨线程锁竞争。

### 3.3 错误处理与重试

```python
def _flush_row(self, row_id: int, user_id: str, session_id: str, messages: list) -> None:
    try:
        self._client.ingest_session(user_id, session_id, messages)
        conn.execute("DELETE FROM pending WHERE id = ?", (row_id,))  # 成功后删除
    except Exception as exc:
        logger.warning("RetainDB ingest failed (will retry): %s", exc)
        conn.execute("UPDATE pending SET last_error = ? WHERE id = ?", (str(exc), row_id))
        time.sleep(2)  # 退避后重试
```

**不删除失败行**：重试次数不限，pending 表作为"永久重试队列"。这对网络不稳定的移动设备场景尤其重要。

### 3.4 启动重放机制

```python
self._thread.start()
for row_id, user_id, session_id, msgs_json in self._pending_rows():
    self._q.put((row_id, user_id, session_id, json.loads(msgs_json)))
```

**关键细节**：重放发生在 `_thread.start()` 之后，意味着：
- 队列中已有正在处理的项 + 新重放的项，全部由同一 writer 线程串行消费
- 不会重复处理（row_id 唯一，API 幂等）

### 3.5 与其他 Provider 的 Queue 对比

| 特性 | RetainDB `_WriteQueue` | Honcho | Supermemory |
|------|------------------------|--------|-------------|
| 持久化 | SQLite（crash-safe） | 内存 | 内存 |
| 重放机制 | 启动时自动重放 | 无 | 无 |
| 重试策略 | 无限重试，sleep 2s | 无 | 无 |
| 线程模型 | 单 writer 线程 | 多线程 | 多线程 |
| 连接管理 | Thread-local | 全局 | 全局 |

---

## 4. Dialectic 合成（Dialectic Synthesis）

### 4.1 概念

Dialectic Synthesis 是 RetainDB 的**用户理解层**——不是简单检索，而是 LLM 推理用户意图和偏好模式：

```python
def ask_user(self, user_id: str, query: str, reasoning_level: str = "low") -> dict:
    return self.request("POST", f"/v1/memory/profile/{quote(user_id, safe='')}/ask", json_body={
        "query": query, "reasoning_level": reasoning_level,
    })
```

### 4.2 推理层级自适应

```python
@staticmethod
def _reasoning_level(query: str) -> str:
    n = len(query)
    if n < 120:
        return "low"
    if n < 400:
        return "medium"
    return "high"
```

Query 越复杂，API 使用更深的推理链（可能调用更强大的模型或增加 CoT 步数）。

### 4.3 Prefetch 集成

```python
def _prefetch_dialectic(self, query: str) -> None:
    try:
        result = self._client.ask_user(self._user_id, query, reasoning_level=self._reasoning_level(query))
        answer = str(result.get("answer") or "")
        if answer:
            with self._lock:
                self._dialectic_result = answer
    except Exception as exc:
        logger.debug("RetainDB dialectic prefetch failed: %s", exc)
```

**Prefetch 线程防累积**（见 §6）。

---

## 5. Agent 自我模型（SOUL.md Seeding）

### 5.1 初始化时播种

```python
def initialize(self, session_id: str, **kwargs) -> None:
    # ...
    soul_path = hermes_home_path / "SOUL.md"
    if soul_path.exists():
        soul_content = soul_path.read_text(encoding="utf-8", errors="replace").strip()
        if soul_content:
            threading.Thread(
                target=self._seed_soul,
                args=(soul_content,),
                name="retaindb-soul-seed",
                daemon=True,
            ).start()
```

### 5.2 播种 API

```python
def _seed_soul(self, content: str) -> None:
    try:
        self._client.seed_agent_identity(self._agent_id, content, source="soul_md")
    except Exception as exc:
        logger.debug("RetainDB soul seed failed: %s", exc)
```

### 5.3 消费 Agent Model

```python
def prefetch(self, query: str, *, session_id: str = "") -> str:
    # ...
    if agent_model and agent_model.get("memory_count", 0) > 0:
        model_lines = []
        if agent_model.get("persona"):
            model_lines.append(f"Persona: {agent_model['persona']}")
        if agent_model.get("persistent_instructions"):
            model_lines.append("Instructions:\n" + "\n".join(f"- {i}" for i in agent_model["persistent_instructions"]))
        if agent_model.get("working_style"):
            model_lines.append(f"Working style: {agent_model['working_style']}")
```

**CE 对照**：BlueCortexCE 使用 SOUL.md 作为 Agent persona 定义文件，但**没有等效的"将 SOUL.md 播种到外部记忆服务"的机制**。这是一个值得借鉴的跨系统 identity 同步设计。

---

## 6. Prefetch 线程管理

### 6.1 防累积机制（Critical！）

```python
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    # Wait for any still-running prefetch threads before spawning new ones.
    # Prevents thread accumulation if turns fire faster than prefetches complete.
    for t in self._prefetch_threads:
        t.join(timeout=2.0)
    threads = [
        threading.Thread(target=self._prefetch_context, ...),
        threading.Thread(target=self._prefetch_dialectic, ...),
        threading.Thread(target=self._prefetch_agent_model, ...),
    ]
    self._prefetch_threads = threads
    for t in threads:
        t.start()
```

**问题场景**：如果 Agent turn 频率高于 prefetch 完成速度（如快速连续用户输入），旧 prefetch 线程会累积，导致资源泄漏。

**解决方案**：在启动新 prefetch 前，等待旧线程最多 2 秒。超时则强制继续（防止死锁）。

### 6.2 三路并发 Prefetch

| Prefetch 线程 | 目标 | 耗时估算 |
|--------------|------|---------|
| `retaindb-ctx` | 上下文 + Profile overlay | API 调用 ~1-2s |
| `retaindb-dialectic` | 用户理解推理 | API 调用 ~2-5s（LLM） |
| `retaindb-agent-model` | Agent 自我模型 | API 调用 ~1s |

三路并发，总延迟 = max(各路) 而非 sum(各路)。

---

## 7. Context Overlay 去重格式化

### 7.1 `_build_overlay()` 算法

```python
def _build_overlay(profile: dict, query_result: dict, local_entries: list[str] | None = None) -> str:
    def _compact(s: str) -> str:
        return re.sub(r"\s+", " ", str(s or "")).strip()[:320]

    def _norm(s: str) -> str:
        return re.sub(r"[^a-z0-9 ]", "", _compact(s).lower())

    seen: list[str] = [_norm(e) for e in (local_entries or []) if _norm(e)]
    profile_items, query_items = [], []
    for m in list((profile or {}).get("memories") or [])[:5]:
        c = _compact((m or {}).get("content") or "")
        n = _norm(c)
        if c and n not in seen:
            seen.append(n)
            profile_items.append(c)

    for r in list((query_result or {}).get("results") or [])[:5]:
        c = _compact((r or {}).get("content") or "")
        n = _norm(c)
        if c and n not in seen:
            seen.append(n)
            query_items.append(c)
```

**去重逻辑**：将每条记忆规范化（去除标点、转小写、取前 320 字符）后用 `seen` 集合去重。

**输出格式**：
```
[RetainDB Context]
Profile:
- <fact 1>
- <fact 2>
Relevant memories:
- <memory 1>
- <memory 2>
```

### 7.2 Profile + Query 双重来源

- **Profile**：`get_profile()` 获取用户的稳定长期偏好（top 5）
- **Query**：`query_context()` 获取当前任务相关的即时记忆（top 5）
- 两者合并去重后输出，确保既有时长期偏好又有即时上下文

---

## 8. 共享文件存储（5 个工具）

### 8.1 文件工具集

| 工具 | 功能 | 特殊处理 |
|------|------|---------|
| `retaindb_upload_file` | 上传本地文件到 RetainDB store | multipart 上传，返回 `rdb://` URI |
| `retaindb_list_files` | 列举文件 | 前缀过滤 |
| `retaindb_read_file` | 按 file_id 读取文件内容 | 文本文件最大 32KB，二进制文件返回 URI |
| `retaindb_ingest_file` | 将文件内容摄入为记忆 | LLM 提取文本块并向量化 |
| `retaindb_delete_file` | 删除文件 | 按 file_id 删除 |

### 8.2 `rdb://` URI 方案

文件上传后返回 `rdb://` URI，可被其他 Agent 引用：
```python
return {"file_id": file_id, "rdb_uri": file_info.get("rdb_uri"), "name": file_info.get("name"), "content": text}
```

**CE 对照**：BlueCortexCE 没有等效的跨 Agent 文件共享 URI 方案。

### 8.3 二进制文件处理

```python
if not (mime.startswith("text/") or ...endswith((".txt", ".md", ".json", ...))):
    return {"note": "Binary file — use retaindb_ingest_file to extract text into memory."}
```

文本 vs 二进制分流处理，二进制强制走 `ingest_file` 提取。

---

## 9. `memory_type` 枚举

```python
"memory_type": {
    "type": "string",
    "enum": ["factual", "preference", "goal", "instruction", "event", "opinion"],
    "description": "Category (default: factual).",
}
```

**6 类记忆类型**：

| 类型 | 语义 | 使用场景 |
|------|------|---------|
| `factual` | 客观事实 | 一般知识/事件记录 |
| `preference` | 用户偏好 | `on_memory_write` 中 target="user" 自动映射 |
| `goal` | 目标/意图 | 用户声明的目标 |
| `instruction` | 指令/规则 | 用户给的指示 |
| `event` | 事件 | 发生的事件记录 |
| `opinion` | 观点 | 用户表达的看法 |

**CE 对照**：BlueCortexCE 使用 ObservationType 枚举（`USER_PREFERENCE`/`ALLERGY`/`GOAL` 等），语义上与 RetainDB `memory_type` 相似但更偏向医疗/健康场景（来自 Phase 3 设计）。

---

## 10. Project 解析策略

```python
explicit = os.environ.get("RETAINDB_PROJECT")
if explicit:
    project = explicit
else:
    hermes_home = str(kwargs.get("hermes_home", ""))
    profile_name = os.path.basename(hermes_home) if hermes_home else ""
    project = f"hermes-{profile_name}" if (profile_name not in {"", ".hermes"}) else "default"
```

**优先级**：`RETAINDB_PROJECT` 环境变量 > `hermes-<profile_name>` > `"default"`

这意味着同一台机器上，不同 Hermes profile 使用不同的 RetainDB project，实现数据隔离。

---

## 11. `on_memory_write` Hook 实现

```python
def on_memory_write(self, action: str, target: str, content: str) -> None:
    """Mirror built-in memory writes to RetainDB."""
    if action != "add" or not content or not self._client:
        return
    try:
        memory_type = "preference" if target == "user" else "factual"
        self._client.add_memory(self._user_id, self._session_id, content, memory_type=memory_type)
    except Exception as exc:
        logger.debug("RetainDB memory mirror failed: %s", exc)
```

**仅处理 `add` action**：不支持 `delete` 等其他 action 的镜像。

**memory_type 映射**：Built-in memory 的 `target="user"` 映射为 RetainDB 的 `preference`，其他情况为 `factual`。

**CE 对照**：CE 没有等效的"内置记忆变更镜像到外部 Provider"的 hook 机制。CE 的 StructuredExtractionService 是主动写入，不是被动镜像。

---

## 12. 可执行借鉴（BlueCortexCE）

### 12.1 高优先级

**P1 — SOUL.md 播种机制**
CE 应在 AgentService 初始化时，将本地 SOUL.md 内容作为 initial observation 写入记忆服务（通过 `ObservationService`），确保 Agent 的自我认知跨 session 持久化。

**P1 — Write-Behind Queue 的 Crash-Safe 设计**
CE 的 SessionDB flush 机制可借鉴 RetainDB 的 SQLite pending table + 启动重放模式，确保服务崩溃后不丢失未 flush 的消息。

### 12.2 中优先级

**P2 — memory_type 语义枚举**
CE 的 ObservationType 枚举可参照 RetainDB 的 6 类设计，增加 `opinion`/`event` 等类型，使观察结果分类更丰富。

**P2 — 文件 URI 方案**
CE 的 FileService 可设计 `cbce://` URI 方案，支持跨 Agent 引用已上传文件，避免重复上传。

### 12.3 低优先级

**P3 — 推理层级自适应**
Dialectic synthesis 的 `reasoning_level` 自适应设计可作为 CE 未来 LLM 调用时模型选择的参考（简单 query 用小模型，复杂 query 用大模型）。

---

## 13. 与其他 Provider 特性对照

| 特性 | RetainDB | Honcho | Supermemory | Hindsight | OpenViking |
|------|-----------|--------|-------------|-----------|------------|
| 本地持久化 | SQLite queue | 内存 | 多容器 | PostgreSQL | 文件系统 |
| 云端 | ✅ 必需 | ❌ | ✅ 可选 | ✅ 可选 | ❌ |
| 文件存储 | ✅ 5 工具 | ❌ | ❌ | ❌ | ❌ |
| Dialectic/LLM 合成 | ✅ `ask_user` | ❌ | ❌ | ❌ | ❌ |
| SOUL.md seeding | ✅ | ❌ | ❌ | ❌ | ❌ |
| Crash-safe queue | ✅ SQLite | ❌ | ❌ | ❌ | ❌ |
| `on_memory_write` | ✅ 镜像写入 | ❌ | ❌ | ❌ | ❌ |
| memory_type 枚举 | ✅ 6 类 | ❌ | ❌ | ❌ | ❌ |

---

## 14. 技术风险

1. **API 依赖**：RetainDB 完全依赖外部云服务，网络不可达时 `ask_user` / `search` 等均失败（但 `queue_prefetch` 有 graceful degradation）
2. **无限重试**：pending 表失败行永不删除，长期网络故障可能导致 pending 表膨胀
3. **双路由兜底**：旧 API 路由可能在 RetainDB 服务端随时废弃，存在隐性技术债务
4. **Agent ID 命名**：`_agent_id` 默认 "hermes"，多实例部署时可能冲突

---

**文档版本**：v1.0  
**对应 upstream**：`023b1bff`（2026-04-24）  
**下一步**：可考虑分析 RetainDB 与 Honcho 的 Prefetch 机制对比（doc 07 §5 已部分覆盖）
