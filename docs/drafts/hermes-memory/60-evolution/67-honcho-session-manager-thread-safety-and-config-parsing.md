# Honcho Session Manager — 线程安全修复与配置解析健壮性（2026-05-05）

**上游 commit**：`ec4cb16a2`（2026-04-27）+ `bea2562fc`（2026-04-27）  
**文件**：`plugins/memory/honcho/session.py`（48251L）+ `plugins/memory/honcho/client.py`（26247L）  
**分析日期**：2026-05-05

---

## 1. 修复一：`_cache_lock` 读写分离导致的竞态条件

### 问题背景

`HonchoSessionManager` 使用双层缓存：

| 缓存 | 用途 | 锁 |
|------|------|-----|
| `_peers_cache: dict[str, Any]` | 缓存 peer 对象（honcho 对端） | `_cache_lock` |
| `_sessions_cache: dict[str, Any]` | 缓存 honcho session 对象 | `_cache_lock` |
| `_prefetch_cache: dict[str, Any]` | 异步预取缓存 | `_prefetch_cache_lock`（独立锁） |

**原始 bug**：`_get_peer()` 和 `_get_or_create_honcho_session()` 对两个主缓存的访问**没有加锁**，而同类中其他方法（如 `_update_session_id()`）正确使用了 `_cache_lock`。

### 原始代码（bug 版本）

```python
# session.py — _get_peer() 修复前
def _get_peer(self, peer_id: str) -> Peer:
    if peer_id in self._peers_cache:      # ❌ 无锁读
        return self._peers_cache[peer_id]  # ❌ 无锁读
    peer = self.honcho.peer(peer_id)       # 网络 I/O（耗时）
    self._peers_cache[peer_id] = peer       # ❌ 无锁写
    return peer

# session.py — _get_or_create_honcho_session() 修复前
def _get_or_create_honcho_session(self, session_id: str, ...) -> Tuple[Any, List]:
    if session_id in self._sessions_cache:  # ❌ 无锁读
        return self._sessions_cache[session_id], []
    session = self.honcho.session(session_id)  # 网络 I/O
    self._sessions_cache[session_id] = session  # ❌ 无锁写
    return session, []
```

### 修复后代码

```python
# session.py — _get_peer() 修复后
def _get_peer(self, peer_id: str) -> Peer:
    with self._cache_lock:           # ✅ 锁住读
        if peer_id in self._peers_cache:
            return self._peers_cache[peer_id]
    # I/O 在锁外，避免长时持有锁
    peer = self.honcho.peer(peer_id)
    with self._cache_lock:           # ✅ 锁住写
        self._peers_cache[peer_id] = peer
    return peer

# session.py — _get_or_create_honcho_session() 修复后
def _get_or_create_honcho_session(self, session_id: str, ...) -> Tuple[Any, List]:
    with self._cache_lock:           # ✅ 锁住读
        if session_id in self._sessions_cache:
            return self._sessions_cache[session_id], []
    session = self.honcho.session(session_id)  # I/O 在锁外
    with self._cache_lock:           # ✅ 锁住写
        self._sessions_cache[session_id] = session
    return session, []
```

### 设计思想：锁粒度与 I/O 分离原则

| 原则 | 说明 |
|------|------|
| **读-检查-写（check-then-act）必须原子化** | `if key in cache` + `return cache[key]` 不是原子操作，并发时会导致重复初始化或读到部分写入 |
| **I/O 操作不放锁内** | 网络调用（`honcho.peer()` / `honcho.session()`）耗时长，持有锁会导致其他线程阻塞在锁外等待 |
| **读写都用同一把锁** | 读时加锁防止读到部分写入状态；写时加锁防止与其他读写冲突 |
| **预取缓存用独立锁** | `_prefetch_cache_lock` 与 `_cache_lock` 分离，减少争用（prefetch 线程频繁访问） |

### CE 对照

**BlueCortexCE 的风险点**：CE 后端 `SearchService.java` 的 embedding 结果缓存（`ConcurrentHashMap`）理论上也可能存在类似 check-then-act 竞态，但 JDK `ConcurrentHashMap.get()` 本身是线程安全的（CAS），所以风险较低。真正需要关注的是：
- `TemplateService` 的模板缓存（非线程安全 `HashMap`，但只在 `@PostConstruct` 单线程初始化，无并发风险）
- `SessionService` 中 session 对象的懒加载模式

**可执行行动**：
1. 审计 CE 中所有 `if (!map.containsKey(k)) { map.put(k, compute()); }` 模式，替换为 `computeIfAbsent()` 或显式加锁
2. 对于涉及 I/O 的缓存填充（LLM 调用），确保 I/O 在锁外

---

## 2. 修复二：配置解析健壮性（`_parse_int_config`）

### 问题背景

`HonchoClient.from_global_config()` 直接用 `int()` 解析三个配置字段：

```python
# 修复前 — 任意一个字段格式错误会导致整个 Provider 初始化失败
dialectic_max_chars=int(
    host_block.get("dialecticMaxChars") or raw.get("dialecticMaxChars") or 600
)
message_max_chars=int(
    host_block.get("messageMaxChars") or raw.get("messageMaxChars") or 25000
)
dialectic_max_input_chars=int(
    host_block.get("dialecticMaxInputChars") or raw.get("dialecticMaxInputChars") or 10000
)
```

**问题**：如果 `honcho.json` 中这些字段被设置为空字符串 `""`、非数字字符串 `"abc"` 或 `null`，`int()` 会抛出 `ValueError`，**整个 Provider 初始化中止**，导致用户完全无法使用 Honcho memory。

### 修复：`safe_get_int` 模式

```python
# client.py
def _parse_int_config(host_val, root_val, default: int) -> int:
    """Parse an integer config: host wins, then root, then default."""
    for val in (host_val, root_val):
        if val is not None:
            try:
                return int(val)
            except (ValueError, TypeError):
                pass  # fallback to next source
    return default
```

调用处：

```python
# 修复后 — 任意一个来源失败，自动降级到默认值
dialectic_max_chars=_parse_int_config(
    host_block.get("dialecticMaxChars"),
    raw.get("dialecticMaxChars"),
    default=600,
)
message_max_chars=_parse_int_config(
    host_block.get("messageMaxChars"),
    raw.get("messageMaxChars"),
    default=25000,
)
dialectic_max_input_chars=_parse_int_config(
    host_block.get("dialecticMaxInputChars"),
    raw.get("dialecticMaxInputChars"),
    default=10000,
)
```

### 设计思想：Config 安全三原则

| 原则 | 说明 |
|------|------|
| **Try-First-Fallback 模式** | 每个配置源（host > root > default）依次尝试，任何一步失败自动降级 |
| **错误隔离** | 单个字段解析错误不影响其他字段，也不中断 Provider 初始化 |
| **参数化默认值** | 默认值显式传入而非硬编码，便于测试和配置 |

**CE 对照**：CE `application.properties` 中的数字配置（如 `server.port`、`spring.datasource.hikari.maximum-pool-size`）由 Spring Boot 自动做类型转换，Spring 本身有 robust 的解析逻辑。但自定义的 YAML/JSON 配置解析（如 Phase 3 的模板配置 schema）应参考此模式：

```java
// CE 参考实现（伪代码）
private int parseIntConfig(String hostVal, String rootVal, int defaultVal) {
    for (String val : List.of(hostVal, rootVal)) {
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                // ignore and try next source
            }
        }
    }
    return defaultVal;
}
```

---

## 3. Honcho 缓存架构全览

### 缓存层次

```
HonchoSessionManager
├── _peers_cache (dict)     ← _cache_lock 保护
│   └── Peer 对象缓存（按 peer_id）
├── _sessions_cache (dict)  ← _cache_lock 保护
│   └── HonchoSession 对象缓存（按 session_id）
├── _prefetch_cache (dict)  ← _prefetch_cache_lock 保护
│   └── 异步预取上下文缓存
└── _async_thread           ← 后台预取线程（daemon）
```

### 预取线程机制（`_run_prefetch_in_background`）

```python
# session.py:577
t = threading.Thread(target=_run, name="honcho-context-prefetch", daemon=True)
# daemon=True：进程退出时自动终止，不阻塞 shutdown
self._async_thread = threading.Thread(...)
```

预取在 `on_memory_write` 或 `queue_prefetch` hook 中被调用，向 honcho 服务异步请求上下文内容。

---

## 4. 可执行借鉴清单

| 优先级 | 行动项 | 对应 CE 组件 |
|--------|--------|------------|
| **高** | 审计 CE 中 `computeIfAbsent` 模式是否被正确使用（`ConcurrentHashMap`） | `EmbeddingService` 缓存 |
| **中** | 为 Phase 3 模板配置的 YAML 解析添加 Try-First-Fallback 模式 | `TemplateService` |
| **低** | 在 `SearchService` 中为 I/O 操作（LLM 调用结果）设计短期内存缓存时，确保锁粒度合理（不在锁内做网络调用） | `SearchService` |

---

## 5. 版本信息

- 本分析基于 `origin/main`（commit `0ce1b9fe2`）的 `session.py` + `client.py`
- Honcho Provider 完整 `__init__.py`（54KB）仍待深入分析（见 [`AGENT.md`](AGENT.md) 体量预警）
- 相关 upstream 分析： Honcho Holographic Extended Memory Provider 已有部分覆盖（`50-honcho-holographic-deep/`）
