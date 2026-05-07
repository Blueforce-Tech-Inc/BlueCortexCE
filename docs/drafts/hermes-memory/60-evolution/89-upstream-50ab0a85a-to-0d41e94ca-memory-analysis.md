# 上游增量分析：`50ab0a85a` → `0d41e94ca`（7 commits，2026-05-06）

## 概述

本次增量 7 个新 commit，仅 **1 个记忆系统相关**。

| Hash | 类型 | 描述 | 记忆相关 |
|------|------|------|---------|
| `0d41e94ca` | feat(i18n) | French locale | ❌ |
| `ee8edd416` | chore | AUTHOR_MAP | ❌ |
| `3188e63b0` | fix | SSE batching / Open WebUI | ❌ |
| **`3082fa082`** | **feat** | **Hindsight append mode dedup** | **✅ P2** |
| `1efed6705` | chore | AUTHOR_MAP | ❌ |
| `56b479511` | guard | Kanban worker lifecycle | ❌ |
| `f0d278412` | feat | kanban.max_spawn | ❌ |
| `0b9cbc8b2` | test | Kanban metadata | ❌ |

---

## ⭐ P2 — Hindsight `update_mode='append'` 跨进程去重（`3082fa082`）

### 问题背景

Hindsight provider 在多进程并发写入同一 session 时，每个进程都创建独立 document，产生 **N 份重复文档**（#20115 的去重部分）。

### 解决方案

通过 `/version` API 探测 Hindsight server 版本（≥ 0.5.0 支持 `update_mode='append'`），自动选择：

| 场景 | document_id | update_mode | 效果 |
|------|-------------|-------------|------|
| API ≥ 0.5.0 | 稳定 `session_id` | `append` | 同 session 跨进程合并 |
| API < 0.5.0 / 探测失败 | `f"{session_id}-{start_ts}"`（per-process unique） | 无 | 降级到原有覆盖行为（#6654 resume fix） |

### 关键实现

**三缓存层版本探测 + 线程安全**（`hindsight/__init__.py` 新增 ~90 行）：

```python
# 模块级缓存：每个 api_url 只探测一次，全进程共享（thread-safe）
_append_capability_cache: Dict[str, bool] = {}
_append_capability_lock = threading.Lock()

def _fetch_hindsight_api_version(api_url, api_key, timeout=5.0) -> str | None:
    """GET /version，Hindsight 返回 {"version": "0.5.6", ...}"""
    url = api_url.rstrip("/") + "/version"
    # ... urllib.request.urlopen，失败返回 None

def _meets_minimum_version(actual, required) -> bool:
    from packaging.version import Version
    return Version(actual) >= Version(required)

def _check_api_supports_update_mode_append(api_url, api_key=None) -> bool:
    with _append_capability_lock:
        if api_url in _append_capability_cache:
            return _append_capability_cache[api_url]  # 缓存命中
    version = _fetch_hindsight_api_version(api_url, api_key)
    supported = _meets_minimum_version(version, "0.5.0")
    with _append_capability_lock:
        cached = _append_capability_cache.get(api_url)
        if cached is None:
            _append_capability_cache[api_url] = supported
        else:
            supported = cached  # 并发探测后以先写入者为准
    return supported

def _probe_url(self) -> str:
    """local_embedded 模式使用运行中 client 的动态端口，而非配置的 api_url"""
    if self._mode == "local_embedded" and self._client:
        url = getattr(self._client, "url", None)
        if url:
            return str(url)
    return self._api_url or ""

def _resolve_retain_target(self, fallback_document_id: str) -> tuple[str, str | None]:
    """返回 (document_id, update_mode) — 探测失败则降级到 per-process unique id"""
    if not self._session_id:
        return fallback_document_id, None
    if _check_api_supports_update_mode_append(self._probe_url(), self._api_key):
        return self._session_id, "append"
    return fallback_document_id, None
```

**调用点**：`retain()` 和 `on_session_switch()` 的 flush 前均调用 `_resolve_retain_target()` 获取 document_id，session switch flush 时针对**旧 session** 探测（因为 `_session_id` 已在 `on_session_switch()` 中被更新）。

**WARN 日志**（仅首次）：版本探测失败或 < 0.5.0 时输出升级建议。

### 降级保证

- API 版本探测失败 → WARN 日志提示升级
- 旧版 server → 仍走原有的 per-process unique document_id + overwrite 逻辑
- 已有文档不会被破坏（`#6654 resume-overwrite fix` 仍然有效）

### BlueCortexCE 借鉴

**场景**：如果 CE 的 `HindsightProvider` 将来支持多实例写入同一 session，需要类似的去重机制。

**CE 当前状态**：CE 的 Hindsight 集成通过 MCP 或 direct API，尚无并发写入场景。

**可迁移性**：中等 — CE 目前无多进程 Hindsight 写入场景，但 HindsightProvider 如果扩展为多 worker 并发写入，可参考此模式（API version 探测 + append 降级）。

---

## 非记忆系统变更摘要

| Commit | 文件 | 说明 |
|--------|------|------|
| `3188e63b0` | `api_server/` | SSE token batching + Open WebUI 性能优化（API 层） |
| `f0d278412` | `gateway/` | `kanban.max_spawn` 配置限制并发任务数 |
| `56b479511` | `gateway/` | Kanban worker lifecycle 按 run id 隔离 |
| `0d41e94ca` | `locales/` | 新增 French (fr) 国际化 |
