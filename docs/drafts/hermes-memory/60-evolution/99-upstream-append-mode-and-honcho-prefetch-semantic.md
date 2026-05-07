# 上游新提交分析（2026-05-06）：Hindsight Append-Mode + Honcho Prefetch 语义搜索

**扫描范围**：`origin/main` 上游推进至 `946ef0ea1`，新分析 2 个记忆/上下文系统相关提交  
**下次扫描起点**：`origin/main` `946ef0ea1`

---

## 概述

| # | Commit | 作者 | 主题 | 优先级 |
|---|--------|------|------|--------|
| 1 | `3082fa082` | nicoloboschi | Hindsight `update_mode='append'` API 探测 + 跨进程去重 | ⭐⭐⭐ **P0** |
| 2 | `0a7cc85ea` | qxxaa | Honcho `get_prefetch_context` 启用 `user_message` 语义搜索 | ⭐⭐ **P1** |

---

## Commit 1: `3082fa082` — Hindsight Append-Mode 跨进程去重

### 摘要

**完全镜像 `hindsight-integrations/openclaw` 已落地的模式**：通过探测 Hindsight API `/version` 端点，自动判断服务器是否支持 `update_mode='append'`。支持时，同一 session 的跨进程 retains 合并到同一 document；不支持时，回退到 legacy per-process unique document_id。

### 核心机制

#### 1. 版本探测（`_fetch_hindsight_api_version`）

```python
def _fetch_hindsight_api_version(api_url: str, api_key: str | None = None,
                                 timeout: float = 5.0) -> str | None:
    url = api_url.rstrip("/") + "/version"
    # Bearer token auth
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = json.loads(resp.read().decode("utf-8", errors="replace"))
    return data.get("version")  # e.g., "0.5.6"
```

- **探测端点**：`GET <api_url>/version`
- **返回格式**：`{"version": "0.5.6", ...}`
- **失败处理**：任何异常（timeout/404/JSON 错误）→ `None` → 触发 legacy fallback
- **local_embedded 特殊处理**：probe URL 取自运行中 `client.url`（actual daemon port），而非配置的默认端口

#### 2. 版本比较（`_meets_minimum_version`）

```python
from packaging.version import Version
return Version(actual) >= Version(required)  # semver comparison
```

- **阈值**：`0.5.0`（Hindsight 引入 `update_mode='append'` 的版本）
- **容错**：version 为 None 或格式错误 → `False`（安全默认值）

#### 3. 跨进程缓存（`_check_api_supports_update_mode_append`）

```python
_append_capability_cache: Dict[str, bool] = {}
_append_capability_lock = threading.Lock()

def _check_api_supports_update_mode_append(api_url: str, ...) -> bool:
    with _append_capability_lock:
        if api_url in _append_capability_cache:
            return _append_capability_cache[api_url]  # DCL double-checked locking
    version = _fetch_hindsight_api_version(api_url, api_key)
    supported = _meets_minimum_version(version, _MIN_VERSION_FOR_UPDATE_MODE_APPEND)
    with _append_capability_lock:
        _append_capability_cache[api_url] = supported
    return supported
```

- **每个 API URL 只探测一次**（per-process）
- **Thread-safe**：使用 `threading.Lock` 保护缓存读写
- **Double-checked locking**：减少锁竞争

#### 4. 文档 ID 策略（`_resolve_retain_target`）

| API 版本 | `document_id` | `update_mode` | 行为 |
|----------|--------------|----------------|------|
| ≥ 0.5.0 | 稳定 `session_id` | `'append'` | 跨进程 retains 合并到同一 document |
| < 0.5.0 / probe 失败 | `f"{session_id}-{start_ts}"`（per-process unique） | 无 | 每次 process 重启 creates new document，resume-overwrite fix (#6654) 保持有效 |

#### 5. 告警机制

```python
if not supported:
    logger.warning(
        "Hindsight API at %s reports version %r, older than %s. "
        "Falling back to per-process document_id — retains across "
        "processes/sessions create separate documents instead of "
        "appending to a session-scoped one. Upgrade Hindsight to "
        ">= %s for cross-process deduplication."
    )
```

- **一次性 WARNING**：仅在首次发现旧版本时记录，避免日志刷屏
- **指导用户升级路径**

### 测试覆盖

5 个测试用例：
1. **Legacy fallback**：probe 失败 → 使用 `legacy-session-<ts>` doc_id，无 `update_mode`
2. **Modern stable+append**：live 0.5.6 daemon → 稳定 `modern-session` doc_id + `update_mode='append'`
3. **Per-URL cache**：同一 URL 多次调用只 probe 一次
4. **One-time warn**：旧版本只警告一次
5. **Flush-on-switch resolves against OLD session**：session 切换时 flush 仍针对旧 session doc

### E2E 验证

```
Legacy probe (unreachable host) → legacy-session-<ts> doc_id, no update_mode
Modern probe (live local_embedded 0.5.6 daemon) → modern-session doc_id + update_mode='append'
test_hermes_embedded_smoke.py passes (90s)
```

---

## Commit 2: `0a7cc85ea` — Honcho Prefetch 语义搜索

### 摘要

`get_prefetch_context` 之前丢弃 `user_message` 参数（理由是避免访问日志暴露对话内容）。该 commit 认为此理由不成立（Honcho 已通过 `saveMessages` 持久化完整消息），并将 `user_message` 作为 `search_query` 传递给 Honcho semantic retrieval，实现按当前 session 话题过滤上下文，减少冷启动噪音。

### 问题分析

**之前的行为**：
```python
user_ctx = self._fetch_peer_context(session.user_peer_id, target=session.user_peer_id)
# search_query 未传递
```

- Honcho 返回完整 peer representation（所有 observations + deductive/inductive layers + peer card）
- 按**插入顺序**返回
- 当 `contextTokens` 设置时，peer card 和 dialectic conclusions 被**截断**，因为原始 observations 填充了 token 预算

**修复后的行为**：
```python
user_ctx = self._fetch_peer_context(
    session.user_peer_id,
    search_query=user_message or None,  # 启用语义过滤
    target=session.user_peer_id
)
```

- Honcho semantic retrieval 返回**仅与当前 session 话题相关**的 conclusions
- 减少注入噪音，提高冷启动上下文质量

### 隐私考量反驳

commit message 明确反驳了之前的隐私理由：

> "This rationale is inconsistent: Honcho already persists every message in full via `saveMessages`. The content is already in the database. A search query in an access log adds negligible additional exposure, and is moot for self-hosted Honcho deployments where the operator owns the logs."

---

## BlueCortexCE 落地借鉴

### P0 借鉴：`ObservationService` API 版本兼容性探测

CE 应该参考此模式，在 `EmbeddingService` 或 `ObservationService` 中实现 API 能力探测：

```java
// CE 借鉴：API 能力探测模式
public class ApiCapabilityProbe {
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
    
    public boolean supportsAppendMode(String apiUrl) {
        // Double-checked locking with ConcurrentHashMap
        if (cache.containsKey(apiUrl)) {
            return cache.get(apiUrl);
        }
        locks.putIfAbsent(apiUrl, new ReentrantLock());
        Lock lock = locks.get(apiUrl);
        lock.lock();
        try {
            if (cache.containsKey(apiUrl)) {
                return cache.get(apiUrl);
            }
            String version = fetchVersion(apiUrl);
            boolean supported = meetsMinimumVersion(version, "0.5.0");
            cache.put(apiUrl, supported);
            return supported;
        } finally {
            lock.unlock();
        }
    }
}
```

**CE 具体场景**：
- PostgreSQL pgvector 版本探测（是否支持 `vector_cosine_distance` 等）
- Spring AI API 能力探测
- 向后兼容的 API 版本策略

### P1 借鉴：Prefetch 语义搜索

CE 的 `ContextService.getContextForSession()` 可以参考 Honcho 的 prefetch 语义搜索：

```java
// CE 借鉴：Prefetch 时使用 user message 作为 semantic search query
public ContextPrefetchResult prefetchContext(String sessionId, String userMessage) {
    // 使用 userMessage 作为 semantic search query
    // 返回与当前话题相关的 observations，而非全量插入顺序
    List<ObservationEntity> relevant = observationRepository
        .findRelevantBySession(sessionId, userMessage, maxTokens);
    return new ContextPrefetchResult(relevant);
}
```

### P1 借鉴：Hindsight 等效功能（CE 无 Hindsight Provider）

CE 的 `SearchService.search()` 目前是全量向量检索，没有 `update_mode='append'` 概念。但 Hindsight 的跨进程 deduplication 思想对 CE 有以下启发：

1. **Session-scoped document ID**：CE 的 `session_id` 作为 observation 的组织单位，本身已是 session-scoped
2. **跨请求合并**：如果 CE 的前端（如 WebUI）和后端都写入同一 session 的 observations，应确保不会产生重复
3. **API 版本探测**：CE 在初始化时探测 PostgreSQL 版本，确保使用兼容的 pgvector 特性

---

## 文件变更摘要

| 文件 | 变更 | 说明 |
|------|------|------|
| `plugins/memory/hindsight/__init__.py` | +151 行 | Append-mode 探测逻辑 + 版本常量 |
| `tests/plugins/memory/test_hindsight_provider.py` | +104 行 | `TestUpdateModeAppendCapability` 5 cases |
| `tests/agent/test_memory_session_switch.py` | +8 行 | bypass-init factory 扩展 |
| `plugins/memory/honcho/session.py` | ±7 行 | `get_prefetch_context` 连接 `user_message` → `search_query` |

---

**维护**：下次扫描起点 `origin/main` `946ef0ea1`（`fix(tui): bound virtual history offset searches`）
