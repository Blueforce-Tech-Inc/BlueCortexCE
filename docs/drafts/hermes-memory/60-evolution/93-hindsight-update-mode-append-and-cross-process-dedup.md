# 上游 Commit `3082fa082` — Hindsight `update_mode='append'` 跨进程去重

**Commit**: `3082fa0829e0df4ce682358481fb59275b31a46e`
**Date**: 2026-05-05 14:46:22 +0200
**Author**: nicoloboschi
**Branch**: `origin/main`
**起点**: `601e5f1d5`（last Hermes inspection）

---

## 📋 变更概览

| 文件 | 变更类型 | 重要性 |
|------|----------|--------|
| `plugins/memory/hindsight/__init__.py` | 新增 API 版本探测 + `update_mode='append'` 路由逻辑 | ⭐⭐ P1 |
| `tests/agent/test_memory_session_switch.py` | 新增 flush-on-switch 反向 session 解析测试 | — |
| `tests/plugins/memory/test_hindsight_provider.py` | 新增 `TestUpdateModeAppendCapability`（5 个用例） | — |

---

## 问题背景：跨进程文档重复

Hermes Agent 中，同一 session 可能在多个进程/provider 实例中运行（如多个 CLI 实例、HONGO/Spawn 等）。在 `3082fa082` 之前：

- 每个进程初始化时，为 session 生成 `f"{session_id}-{start_ts}"` 作为 `document_id`
- 不同进程的 retains 写入**不同** document（因为 `start_ts` 不同）
- 结果：同一 session 在 Hindsight 中产生 N 份文档，内容重复

### 旧行为

```
Process A (session=X, ts=1700) → document_id = "X-1700"
Process B (session=X, ts=1701) → document_id = "X-1701"   ← 不同 doc，内容重复
```

### 新行为（Hindsight ≥ 0.5.0）

```
Process A (session=X) → document_id = "X", update_mode='append'  ← 同一 doc，追加
Process B (session=X) → document_id = "X", update_mode='append'  ← 追加到同一 doc
```

---

## 核心实现

### 1. 版本探测机制（`_fetch_hindsight_api_version`）

```python
def _fetch_hindsight_api_version(api_url: str, api_key: str | None = None,
                                 timeout: float = 5.0) -> str | None:
    """GET <api_url>/version → {"version": "0.5.6", ...}"""
    url = api_url.rstrip("/") + "/version"
    req = urllib.request.Request(url)
    if api_key:
        req.add_header("Authorization", f"Bearer {api_key}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = resp.read().decode("utf-8", errors="replace")
        data = json.loads(payload)
    except Exception:
        return None
    version = data.get("version") or data.get("api_version")
    return str(version) if version else None
```

### 2. 缓存版 API 能力检查（`_check_api_supports_update_mode_append`）

```python
_append_capability_cache: Dict[str, bool] = {}
_append_capability_lock = threading.Lock()

def _check_api_supports_update_mode_append(api_url: str,
                                           api_key: str | None = None) -> bool:
    """每个 (process, api_url) 只探测一次，结果缓存"""
    if not api_url:
        return False
    with _append_capability_lock:
        if api_url in _append_capability_cache:
            return _append_capability_cache[api_url]
    version = _fetch_hindsight_api_version(api_url, api_key)
    supported = _meets_minimum_version(version, _MIN_VERSION_FOR_UPDATE_MODE_APPEND)
    with _append_capability_lock:
        cached = _append_capability_cache.get(api_url)
        if cached is None:
            _append_capability_cache[api_url] = supported
        else:
            supported = cached
    if not supported:
        logger.warning("Hindsight API at %s version %r < %s, "
                       "falling back to per-process document_id...",
                       api_url, version, _MIN_VERSION_FOR_UPDATE_MODE_APPEND)
    return supported
```

**设计亮点**：
- **进程级缓存**：`threading.Lock` + dict 缓存，每个 api_url 只发一次 HTTP 请求
- **向后兼容**：探测失败 → 降级回 per-process unique document_id，legacy Hindsight 继续正常工作

### 3. `_resolve_retain_target` — 动态路由

```python
def _resolve_retain_target(self, fallback_document_id: str) -> tuple[str, str | None]:
    """Returns (document_id, update_mode) based on API capability.
    
    Modern API (≥ 0.5.0): stable session-scoped doc_id + 'append'
    Legacy API: per-process unique fallback_document_id + None
    """
    if not self._session_id:
        return fallback_document_id, None
    if _check_api_supports_update_mode_append(self._probe_url(), self._api_key):
        return self._session_id, "append"   # ← 同一 session 复用 document_id
    return fallback_document_id, None       # ← 降级：进程唯一 ID
```

**调用点**：`sync_turn()` 和 `on_session_switch` flush path

### 4. `local_embedded` 模式的 probe URL 特殊处理

```python
def _probe_url(self) -> str:
    """For local_embedded, daemon runs on per-profile dynamic port.
    Prefer the running client's URL over configured _api_url."""
    if self._mode == "local_embedded" and self._client is not None:
        url = getattr(self._client, "url", None)
        if url:
            return str(url)
    return self._api_url or ""
```

---

## 版本要求

| 特性 | 最低版本 |
|------|----------|
| `/version` 端点 | Hindsight 任意版本 |
| `update_mode='append'` | **Hindsight ≥ 0.5.0** |

**探测常量**：`MIN_VERSION_FOR_UPDATE_MODE_APPEND = "0.5.0"`

---

## BlueCortexCE 借鉴

### 1. Claude-Mem 的跨进程写入去重

CE 的 `ObservationService`（Spring Boot 单进程）目前无此问题。但若未来：
- 引入多实例部署（多 JVM）
- 引入异步 worker 写入

则需要类似机制：session 级别的 stable document_id + append 模式。

### 2. 版本探测模式

CE 的 `StructuredExtractionService` 尚无版本协商机制。若未来支持多版本 LLM API（如 OpenAI + Anthropic），可参考此模式：

```python
# 伪代码：LLM API 能力探测
_capability_cache: Dict[str, set[str]] = {}

def check_llm_capability(provider: str, api_url: str) -> bool:
    if api_url in _capability_cache:
        return "structured_output" in _capability_cache[api_url]
    # probe...
    _capability_cache[api_url] = detected_caps
```

### 3. 线程安全单次探测

Hindsight 的 `threading.Lock + double-checked locking` 模式值得借鉴：

```python
# Pattern: 确保单次执行 + 线程安全
with _lock:
    if key in cache:
        return cache[key]   # Fast path: 已有
result = probe()            # 慢路径：探测
with _lock:
    if key not in cache:    # 再检查（防止并发）
        cache[key] = result
    else:
        result = cache[key]
return result
```

---

## 总结

| 编号 | 发现 | 级别 | CE 行动 |
|------|------|------|---------|
| 1 | 跨进程 session 文档重复：Hindsight 通过 API 版本探测 + `update_mode='append'` 解决 | P1 | CE 多实例部署时需考虑同款机制 |
| 2 | 版本探测用 double-checked locking 缓存，每进程每 URL 只探测一次 | P2 | 跨组件能力协商参考模式 |
| 3 | `local_embedded` 模式用运行中 client URL 而非配置的 URL 探测 | P2 | 网络发现参考 |

---

## CE 相关现有文档

- Hindsight 本地嵌入 Daemon: [`25-hindsight-local-embedded-daemon-and-postgresql-schema.md`](25-hindsight-local-embedded-daemon-and-postgresql-schema.md)
- Hindsight 知识图谱: [`22-hindsight-knowledge-graph-deep-dive.md`](22-hindsight-knowledge-graph-deep-dive.md)
- 多 Provider 发现: [`14-multi-provider-plugin-discovery.md`](14-multi-provider-plugin-discovery.md)
