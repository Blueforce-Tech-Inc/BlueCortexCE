# Supermemory Multi-Container + Search Mode（2026-04-14 上游新增）

> **上游 commit**: `7b18eeee`（2026-04-07，PR #5933，by Teknium）  
> **文件**: `plugins/memory/supermemory/__init__.py`（791 行）  
> **CE 参照**：[`09-supermemory-capture-lifecycle.md`](09-supermemory-capture-lifecycle.md)（capture 生命周期）  
> **最后复核**：2026-04-23（对照上游 v5.2+ 源码）

---

## 1. 概述

2026-04-07 上游为 Supermemory Plugin 新增三项功能，构成**多容器+多模搜索**架构：

| 功能 | 配置项 | 说明 |
|------|--------|------|
| **多容器标签** | `container_tag` + `{identity}` 模板 | 按 Hermes Profile 隔离容器 |
| **多容器模式** | `enable_custom_container_tags` + `custom_containers[]` | 跨容器读写（白名单控制） |
| **搜索模式** | `search_mode`：`hybrid` / `memories` / `documents` | 控制 recall 时查哪类内容 |
| **环境变量覆盖** | `SUPERMEMORY_CONTAINER_TAG` | 优先级高于配置文件 |
| **自定义 ID** | `custom_id` on `documents.add` | 外部系统对接用 |

---

## 2. `{identity}` 模板 — Profile 作用域容器

### 动机

Hermes 支持多 Profile（如 `coder`、`reviewer`），之前所有 Profile 共用同一 `container_tag`，导致记忆混杂。引入模板变量后，每个 Profile 可有独立容器。

### 配置

```json
// ~/.hermes/supermemory.json
{
  "container_tag": "hermes-{identity}"
}
```

### 解析规则

- Profile `coder` → `container_tag = "hermes-coder"`
- Profile `reviewer` → `container_tag = "hermes-reviewer"`
- 默认 Profile（无名）→ `container_tag = "hermes-default"`
- 模板解析发生在 `initialize()`，而非 `get_config_schema()`（因为 Profile 信息在 setup 时未必已知）

### 实现要点

```python
# __init__.py:111
raw_tag = str(config.get("container_tag", _DEFAULT_CONTAINER_TAG)).strip()
# Keep raw — {identity} resolved in initialize() after profile known
config["container_tag"] = raw_tag
```

关键：**不**在 `get_config_schema()` 做 sanitize，延迟到 `initialize()`——因为 `{identity}` 依赖 Profile 名，而 Profile 在 setup wizard 完成之后才确定。

### 与 CE 对照

| 维度 | Hermes | BlueCortexCE |
|------|--------|-------------|
| 隔离单位 | Profile → Container | Session → 向量记录 |
| 隔离字段 | `container_tag` | `session_id` 在 metadata |
| 模板变量 | `{identity}` → Profile 名 | 不需要（N 个 Session = N 份记录） |
| 默认行为 | 所有 Profile 共享 `hermes` | 所有 Session 共享同一库 |

**CE 借鉴**：如果未来支持多 Workspace，可在 `session` 表加 `workspace_id` 字段，检索时用 `session.workspace_id = ?` 过滤——相当于 Hermes 的 `{identity}` 作用域。

---

## 3. Multi-Container 模式

### 适用场景

OpenClaw 风格的多 Workspace（每个 Workspace 有独立记忆空间）或团队知识共享（一个容器放项目 A，一个放项目 B）。

### 配置

```json
{
  "container_tag": "hermes",
  "enable_custom_container_tags": true,
  "custom_containers": ["project-alpha", "project-beta", "shared-knowledge"],
  "custom_container_instructions": "Use project-alpha for coding tasks, project-beta for research, and shared-knowledge for team-wide facts."
}
```

### 行为矩阵

| 操作 | 主容器 | 自定义容器 |
|------|--------|-----------|
| `supermemory_search` | ✅（默认） | ✅（显式传 `container_tag`） |
| `supermemory_store` | ✅（默认） | ✅（显式传 `container_tag`） |
| `supermemory_forget` | ✅ | ✅ |
| `supermemory_profile` | ✅ | ❌ |
| 自动 `sync_turn` | ✅（主容器） | ❌ |
| 自动 `prefetch` | ✅（主容器） | ❌ |
| Session ingest | ✅（主容器） | ❌ |
| `custom_container_instructions` 注入 | — | ✅（写入 system prompt） |

### 白名单验证

用户传 `container_tag` 时必须白名单校验：

```python
# __init__.py:520
if self._enable_custom_containers and self._custom_containers:
    allowed = [self._container_tag] + list(self._custom_containers)
    if container_tag not in allowed:
        raise ValueError(f"container_tag '{container_tag}' not in whitelist")
```

防止通过 tool call 注入任意容器名。

### 动态 Tool Schema

当 `enable_custom_container_tags=true` 时，`supermemory_search` 等工具的 schema 会添加可选的 `container_tag` 参数：

```python
# __init__.py:538-542
if self._enable_custom_containers and self._custom_containers:
    for tool_fn in [self.supermemory_search, self.supermemory_store, ...]:
        tool_fn._memory_schema_extra = {"container_tag": {"type": "string", "optional": True}}
```

### 与 CE 对照

| 维度 | Hermes | BlueCortexCE |
|------|--------|-------------|
| 多 Workspace | 多个 Container | 多个 Session 或 `workspace_id` 字段 |
| Workspace 指令 | `custom_container_instructions` → system prompt | 无等价机制 |
| 白名单控制 | `custom_containers[]` | N/A |
| 主 Workspace 隔离 | 自动操作（sync/prefetch）走主容器 | 目前所有 session 混合 |

**CE 借鉴**：为 CE 引入 `Workspace` 概念时，可参考：
1. 主要 context 来自当前 Session（= Hermes 主容器）
2. 可显式 cross-workspace 查询（需白名单）
3. Workspace 级别指令可注入 system prompt

---

## 4. Search Mode

### 三种模式

```python
_VALID_SEARCH_MODES = ("hybrid", "memories", "documents")
```

| 模式 | 查询范围 | SDK 参数 |
|------|----------|---------|
| `hybrid` | Profile facts + memories | `search_mode="hybrid"` |
| `memories` | 仅 memories（历史交互记忆） | `search_mode="memories"` |
| `documents` | 仅 documents（外部文档） | `search_mode="documents"` |

### 实现

```python
# __init__.py:291-297
def search_memories(self, query: str, ..., search_mode: Optional[str] = None):
    tag = container_tag or self._container_tag
    mode = search_mode or self._search_mode
    kwargs = {"q": query, "container_tag": tag, "limit": limit}
    if mode in _VALID_SEARCH_MODES:
        kwargs["search_mode"] = mode  # passed to Supermemory SDK
```

每种模式对应 Supermemory SDK 的不同 endpoint/参数组合，实现不同的索引范围。

### 与 CE 对照

| 模式 | Hermes | BlueCortexCE 等价 |
|------|--------|------------------|
| `hybrid` | Profile facts + memories 混合 | `/api/context/generate`（综合 context） |
| `memories` | 纯交互记忆 | `/api/context/semantic`（仅语义记忆） |
| `documents` | 纯外部文档 | 暂无（可作未来文档索引集成） |

**CE 借鉴**：CE 目前没有 `documents` 模式。如果要支持外部文档（RAG），可参考 `search_mode` 设计：
- `hybrid` = Session 记忆 + 文档向量检索混合
- `memories` = 仅 Session 记忆（现状）
- `documents` = 仅 RAG 文档检索

---

## 5. `SUPERMEMORY_CONTAINER_TAG` 环境变量

### 优先级

```
环境变量 SUPERMEMORY_CONTAINER_TAG > 配置文件 container_tag
```

```python
# __init__.py:490
env_tag = os.environ.get("SUPERMEMORY_CONTAINER_TAG", "").strip()
if env_tag:
    self._container_tag = env_tag
else:
    self._container_tag = self._config["container_tag"]
```

### 用途

适合容器化部署或 CI 环境——不需要改配置文件即可切换容器。

### 与 CE 对照

CE 通过 `SPRING_DATASOURCE_URL` 等环境变量配置服务层，但记忆容器（Session）无环境变量覆盖机制。

---

## 6. `custom_id` 支持（Documents API）

### 变化

`documents.add` 调用现在支持 `custom_id` 参数，允许外部系统（如 GitHub PR、Web URL）指定记忆 ID，实现去重或定向更新：

```python
# upstream __init__.py line ~316
kwargs["custom_id"] = custom_id  # passed through to SDK
```

### 与 CE 对照

CE 的 `ObservationEntity` 目前用数据库自增 ID。引入 `custom_id` 字段可支持幂等写入（如同一个 GitHub Issue 的多次讨论合并为一条记忆）。

---

## 7. 对 BlueCortexCE 的借鉴意义

### 7.1 Workspace/Profile 隔离（高优先级）

CE 长期缺少 Session 级别的 Workspace 隔离。可借鉴 `{identity}` 模板思想：

```java
// 概念：Session entity 加 workspace_id 字段
@Entity
public class SessionEntity {
    private String workspaceId;  // 相当于 Hermes 的 profile name
    // ...
}
```

检索时默认过滤当前 Session 的 Workspace，跨 Workspace 查询需显式声明。

### 7.2 多模搜索分层（中优先级）

CE 的 `/api/context/semantic` 目前是单一语义检索。如需支持 RAG 文档，可扩展为 `search_mode` 参数：

```
GET /api/context/generate?search_mode=memories   # 仅记忆
GET /api/context/generate?search_mode=documents   # 仅文档
GET /api/context/generate?search_mode=hybrid      # 记忆+文档
```

### 7.3 环境变量覆盖（低优先级）

CE 的 `application.properties` 目前需重启服务才生效。参考 `SUPERMEMORY_CONTAINER_TAG`，可为关键配置项（如 `DEFAULT_SESSION_ID`）添加环境变量覆盖层。

---

## 8. 上游快照

### 配置 schema（2026-04-14）

```python
# __init__.py:57-69
config_schema = {
    "api_key": {"secret": True, "env_var": "SUPERMEMORY_API_KEY"},
    "container_tag": _DEFAULT_CONTAINER_TAG,  # "hermes"
    "search_mode": _DEFAULT_SEARCH_MODE,      # "hybrid"
    "enable_custom_container_tags": False,
    "custom_containers": [],
    "custom_container_instructions": "",
}
```

### 新增导入

```python
import os  # for SUPERMEMORY_CONTAINER_TAG
from typing import Optional, List, Any
```

### 文件大小

上游 `__init__.py` 从约 580 行增至 791 行（+211 行），主要新增多容器逻辑和 search_mode 支持。

---

## 9. 待验证项

- [ ] Supermemory SDK 端的 `search_mode` 实现细节（需 SDK 源码）
- [ ] `local_external` mode 下的容器行为（目前只在 cloud/local_embedded 测试）
- [ ] 多容器并发写入时的冲突处理策略
