# 60-evolution/18-three-new-memory-providers.md

# 三新增 Memory Provider 分析：ByteRover / Hindsight / OpenViking

> **来源**：Hermes Agent `plugins/memory/<byterover|hindsight|openviking>/__init__.py`
> **快照时间**：2026-04-19
> **定位**：60-evolution 子稿，补充 `14` 中"文档未详"的三项

---

## 1. ByteRover — 层级 Context Tree + CLI 驱动

### 1.1 定位与特性

| 维度 | 说明 |
|------|------|
| **架构** | 本地优先（Local-first），可选云同步 |
| **存储模型** | 层级 Context Tree（层次化知识树） |
| **检索策略** | 两级：模糊文本匹配 → LLM 驱动搜索 |
| **依赖** | `brv` CLI（npm 或 curl 安装） |
| **Profile 隔离** | `$HERMES_HOME/byterover/` |

### 1.2 核心实现细节

**BRV 路径解析**（线程安全缓存）：

```python
_brv_path_lock = threading.Lock()
_cached_brv_path: Optional[str] = None

def _resolve_brv_path() -> Optional[str]:
    # 1. 先查 PATH (shutil.which)
    # 2. 再查 well-known 路径：
    #    ~/.brv-cli/bin/brv
    #    /usr/local/bin/brv
    #    ~/.npm-global/bin/brv
```

**超时策略**（区分读写特征）：

| 操作 | 超时 | 原因 |
|------|------|------|
| `brv query` | 10s | 读，纯本地 |
| `brv curate` | 120s | 写，可能触发 LLM |

**去噪过滤**：

```python
_MIN_QUERY_LEN = 10
_MIN_OUTPUT_LEN = 20
```

### 1.3 工具接口

| 工具 | 行为 |
|------|------|
| `brv_query` | 搜索知识树；query < 10 字符或输出 < 20 字符则静默跳过 |
| `brv_curate` | 存储事实/决策/模式；含 LLM 处理，timeout 120s |
| `brv_status` | 查 CLI 版本、树统计、同步状态 |

### 1.4 借鉴价值

- **BRV 路径缓存**：避免每次调用重复搜索 PATH，线程安全
- **超时分级**：`query` vs `curate` 差异化超时，体现读写特征差异
- **去噪阈值**：最小长度过滤，避免噪音入库

---

## 2. Hindsight — 知识图谱 + 多策略检索 + 本地嵌入模式

### 2.1 定位与特性

| 维度 | 说明 |
|------|------|
| **核心能力** | 知识图谱、实体消解、多策略检索（语义+实体图+重排） |
| **三种运行模式** | `cloud`（API）/ `local_embedded`（本地 LLM）/ `local_external`（自托管） |
| **本地嵌入** | 自动启动后台 daemon，内置 PostgreSQL + LLM API |
| **Bank Mission** | 可设置 `bank_mission` / `bank_retain_mission` 影响提取和推理方向 |
| **内存模式** | `hybrid`（注入+工具）/ `context`（仅注入）/ `tools`（仅工具） |

### 2.2 异步架构（关键创新）

**独立事件循环线程**（避免 aiohttp session 泄漏）：

```python
_loop: asyncio.AbstractEventLoop | None = None
_loop_thread: threading.Thread | None = None

def _get_loop() -> asyncio.AbstractEventLoop:
    global _loop, _loop_thread
    with _loop_lock:
        if _loop is not None and _loop.is_running():
            return _loop
        _loop = asyncio.new_event_loop()
        def _run():
            asyncio.set_event_loop(_loop)
            _loop.run_forever()
        _loop_thread = threading.Thread(target=_run, daemon=True, name="hindsight-loop")
        _loop_thread.start()
        return _loop

def _run_sync(coro, timeout: float = 120.0):
    loop = _get_loop()
    future = asyncio.run_coroutine_threadsafe(coro, loop)
    return future.result(timeout=timeout)
```

**Session 级 Turn 累积**：

```python
self._session_turns: list[str] = []  # 累积全 session 的所有 turn
self._turn_counter = 0
self._retain_every_n_turns = 1  # 每 N 个 turn retain 一次
```

### 2.3 工具接口

| 工具 | 描述 |
|------|------|
| `hindsight_retain` | 存储信息，自动实体抽取并索引 |
| `hindsight_recall` | 多策略搜索：语义+实体图遍历+重排 |
| `hindsight_reflect` | 跨记忆 LLM 综合推理（比 recall 更深入） |

**Reflect vs Recall**：Reflect 是合成推理，Recall 是检索；这是 Hindsight 的差异化能力。

### 2.4 Prefetch 机制

```python
def queue_prefetch(self, query: str, *, session_id: str = "") -> None:
    # 异步预取，支持 "recall" 或 "reflect" 模式
    # prefetch 结果缓存到 self._prefetch_result
```

`memory_mode=hybrid` 时 prefetch 自动开启。

### 2.5 配置优先级

```
1. $HERMES_HOME/hindsight/config.json  (profile-scoped, 优先)
2. ~/.hindsight/config.json            (legacy, 共享)
3. 环境变量
```

### 2.6 借鉴价值

- **独立事件循环线程**：解决 aiohttp 在非 async 主线程创建 session 的泄漏问题
- **Reflect 工具**：超越纯检索的 LLM 综合能力，可合成跨记忆推理
- **Bank Mission**：通过使命描述影响提取和推理方向（CE 可借鉴用于系统 Prompt 工程）
- **多模态 Memory Mode**：`hybrid/context/tools` 三档可配置，CE 可借鉴分级注入策略

---

## 3. OpenViking — 文件系统层级 + Viking URI + Tiered Context + AtExit 安全网

### 3.1 定位与特性

| 维度 | 说明 |
|------|------|
| **底层** | Volcengine (ByteDance) Context Database |
| **存储模型** | 文件系统风格知识层级 + `viking://` URI |
| **上下文分级** | L0 (~100 tokens) / L1 (~2k) / L2 (full) |
| **自动提取** | Session commit 时 6 类自动记忆抽取 |
| **资源摄入** | 支持 URL / docs / code 摄入 |
| **HTTP 客户端** | 纯 httpx，不依赖 SDK |

### 3.2 AtExit 安全网（关键设计）

**进程退出时自动 commit 未决 session**：

```python
_last_active_provider: Optional["OpenVikingMemoryProvider"] = None

def _atexit_commit_sessions():
    global _last_active_provider
    provider = _last_active_provider
    if provider is None:
        return
    _last_active_provider = None
    try:
        provider.on_session_end([])  # 强制 commit
    except Exception:
        pass  # best-effort

atexit.register(_atexit_commit_sessions)
```

**目的**：防止 gateway crash / SIGKILL / 异常导致 `shutdown_memory_provider` 未被调用时 session 数据丢失。

### 3.3 工具接口

| 工具 | 描述 |
|------|------|
| `viking_search` | 语义搜索，支持 fast/deep/auto 模式 |
| `viking_read` | 按 `viking://` URI 读内容（L0 abstract / L1 overview / L2 full） |
| `viking_browse` | 文件系统风格导航（list / tree / stat） |
| `viking_remember` | 存储事实，session commit 时触发抽取 |
| `viking_add_resource` | 摄入 URL/docs/code 到知识库 |

**Viking URI 示例**：

```
viking://memory/session-123/overview
viking://memory/session-123/full
viking://abstract
viking://overview
```

### 3.4 Tiered Context 设计

| 层级 | Token 预算 | 用途 |
|------|-----------|------|
| L0 | ~100 | 高层抽象摘要 |
| L1 | ~2k | 概要视图 |
| L2 | 无限制 | 完整内容 |

**检索时按需加载对应层级**，避免上下文爆栈。

### 3.5 借鉴价值

- **AtExit 安全网**：关键数据保护模式，CE 可借鉴用于 Session flush 保障
- **Tiered Context**：按层级渐进加载上下文，CE 的 Summary 分级可对应参考
- **Viking URI 抽象**：将记忆组织为虚拟文件系统，CE 可借鉴 URI 抽象做记忆寻址
- **纯 HTTP 客户端**：不依赖 SDK，减少外部依赖风险

---

## 4. 三者横向对照

| 维度 | ByteRover | Hindsight | OpenViking |
|------|-----------|-----------|------------|
| **存储模型** | 层级 Context Tree | 知识图谱 + 向量 | 文件系统层级 + URI |
| **检索策略** | 模糊+LLM 驱动 | 语义+实体图+重排 | fast/deep/auto 分级 |
| **本地优先** | ✅ 本地优先 | ✅ 本地嵌入 | ❌ 需服务端 |
| **LLM 推理** | curate 时调用 | recall/reflect 均调用 | 添加资源时调用 |
| **特殊能力** | CLI 驱动 | Reflect 综合推理 | Tiered URI + AtExit |
| **退出保护** | 无 | 无 | ✅ atexit commit |
| **多模态注入** | 工具模式 | hybrid/context/tools | 工具+注入 |

---

## 5. 与 CE 的对照

| 维度 | Hermes | BlueCortexCE |
|------|--------|--------------|
| 层级上下文 | OpenViking L0/L1/L2 | Summary 分级（概念相似） |
| 退出保护 | OpenViking atexit | 无（Session flush 依赖正常流程） |
| 知识图谱 | Hindsight | 无（实体消解未实现） |
| 外部 CLI | ByteRover (brv) | 无（依赖 HTTP API） |
| 综合推理 | Hindsight reflect | 无（LLM 综合能力未独立暴露） |

---

## 6. 更新 `14` Provider 清单

将 `14-multi-provider-plugin-discovery.md` 第 5 节 Provider 清单更新：

| Provider | 定位 | 关键特性 |
|----------|------|----------|
| honcho | 本地 Honcho 云 API | 4 tool schema，profile/search/context/remember |
| supermemory | Supermemory.ai 云 API | 语义长期记忆，实体抽取 |
| mem0 | Mem0 云 API | 轻量占位 |
| holographic | 本地 SQLite（HRR） | fact_store + fact_feedback；信任评分，HRR |
| retaindb | RetainDB 云 API | 写缓冲队列，dialectic 合成 |
| openviking | Volcengine DB | 文件系统层级，viking:// URI，L0/L1/L2 tiered context，**atexit 安全网** |
| byterover | ByteRover CLI | 层级 Context Tree，模糊+LLM 检索，**超时分级** |
| hindsight | 知识图谱 | 实体消解，多策略检索，**Reflect 综合推理**，**本地嵌入 daemon**，**Bank Mission** |

---

## 7. 待跟进

- [ ] `hindsight` 的 `local_embedded` daemon 自动管理细节（启动/停止/5min 超时）
- [ ] `openviking` 的 6 类自动记忆抽取具体类别
- [ ] `byterover` 的云同步机制（`BRV_API_KEY` 可选）
- [ ] 三者与 `run_agent` 的 `prefetch_all` / `sync_all` 集成方式
