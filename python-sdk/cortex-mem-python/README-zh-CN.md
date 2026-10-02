> English version: [README.md](./README.md)

# Cortex CE Python SDK

[Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) 持久记忆系统的 Python SDK。

## 特性

- **零强制依赖** —— 仅需 `requests`
- **完整 API 覆盖** —— 25 个方法，涵盖会话、捕获、检索、管理、提取
- **389 个单元测试** —— 全面覆盖客户端、DTO 和 Demo 集成
- **Python 风格** —— dataclass、kwargs、上下文管理器
- **Wire 格式兼容** —— JSON 字段名与后端 API 完全一致
- **Fire-and-forget 捕获** —— 非阻塞的观察记录，内置重试机制

## 安装

```bash
pip install -e ./python-sdk/cortex-mem-python
```

## 快速开始

```python
from cortex_mem import CortexMemClient

with CortexMemClient(base_url="http://localhost:37777") as client:
    # 启动会话
    session = client.start_session("my-session", "/path/to/project")

    # 记录观察（fire-and-forget）
    client.record_observation(
        session_id=session.session_id,
        project_path="/path/to/project",
        tool_name="Read",
        tool_input={"file": "main.py"},
    )

    # 检索经验
    experiences = client.retrieve_experiences(
        task="How to handle errors?",
        project="/path/to/project",
        count=3,
    )

    # 构建 ICL prompt
    result = client.build_icl_prompt(
        task="How to handle errors?",
        project="/path/to/project",
    )

    # 结束会话
    client.record_session_end(session_id=session.session_id, project_path="/path/to/project")
```

## API 参考

### 会话

| 方法 | 说明 |
|------|------|
| `start_session(session_id, project_path, user_id=None)` | 启动或恢复会话 |
| `update_session_user_id(session_id, user_id)` | 更新会话用户 ID |

### 捕获（fire-and-forget）

| 方法 | 说明 |
|------|------|
| `record_observation(session_id, project_path, tool_name, **kwargs)` | 记录工具使用观察 |
| `record_session_end(session_id, project_path, ...)` | 信号会话结束 |
| `record_user_prompt(session_id, prompt_text, ...)` | 记录用户提示 |

### 检索

| 方法 | 说明 |
|------|------|
| `retrieve_experiences(task, project, **kwargs)` | 检索相关经验 |
| `build_icl_prompt(task, project, **kwargs)` | 构建 ICL prompt |
| `search(project, **kwargs)` | 语义搜索 |
| `list_observations(project, **kwargs)` | 分页列出观察 |
| `get_observation(observation_id)` | 通过 ID 获取单个观察 |
| `get_observations_by_ids(ids)` | 批量获取观察 |

### 管理

| 方法 | 说明 |
|------|------|
| `trigger_refinement(project_path)` | 触发记忆精炼 |
| `submit_feedback(observation_id, feedback_type, comment="")` | 提交反馈 |
| `update_observation(observation_id, update=None, **kwargs)` | 更新观察（支持 dataclass 或 kwargs） |
| `delete_observation(observation_id)` | 删除观察 |
| `get_quality_distribution(project_path)` | 获取质量分布 |

#### ObservationUpdate — 双模式支持

`update_observation` 方法支持两种调用方式：

```python
from cortex_mem import ObservationUpdate

# 方式 1：Dataclass（推荐 —— IDE 自动补全 + 类型检查）
update = ObservationUpdate(title="New Title", source="manual", extracted_data={"pref": "dark"})
client.update_observation("obs-123", update)

# 方式 2：Kwargs（便捷）
client.update_observation("obs-123", title="New Title", source="manual")

# 方式 3：两者结合（kwargs 覆盖 dataclass 字段）
update = ObservationUpdate(title="From Dataclass")
client.update_observation("obs-123", update, title="From Kwargs")
```

支持字段：`title`, `subtitle`, `content`, `narrative`, `facts`, `concepts`, `source`, `extracted_data`。
只有非 None 字段会被发送到后端（PATCH 语义）。

### 健康 / 提取 / 版本

| 方法 | 说明 |
|------|------|
| `health_check()` | 检查后端健康状态 |
| `trigger_extraction(project_path)` | 触发提取 |
| `get_latest_extraction(project_path, template_name, ...)` | 获取最新提取结果 |
| `get_extraction_history(project_path, template_name, ...)` | 获取提取历史 |
| `get_version()` | 获取后端版本 |
| `get_projects()` | 获取所有项目 |
| `get_stats(project_path="")` | 获取统计信息 |
| `get_modes()` | 获取模式设置 |
| `get_settings()` | 获取当前设置 |

### 生命周期

| 方法 | 说明 |
|------|------|
| `close()` | 标记客户端已关闭并释放其 HTTP 会话（借用他人会话时的行为见下） |

`close()` 之后，所有方法都会抛出 `CortexError("client is closed")`。客户端同时支持
上下文管理器，因此通常无需显式调用：

```python
with CortexMemClient(base_url="http://localhost:37777") as client:
    result = client.search(project="/my-project", query="auth", limit=5)
```

### 会话归属

`CortexMemClient(..., session=your_session)` 会借用你已有的 `requests.Session`，
这是与应用程序其余部分共享连接池的方式。由此产生两条行为，都是有意设计：

- **请求头逐次传递，绝不写入会话**。SDK 在每次调用时单独发送 `Accept`、
  `User-Agent` 和 `Authorization`，而不是去改 `session.headers`。借用的会话与
  你其它所有 HTTP 流量共享，若把 API key 写进去，就等于把它发往无关主机。你自己
  设置的会话头永远不会被覆盖；自建会话走同一条代码路径，因此两种情况下实际发出的
  请求逐字节一致。
- **`close()` 不会关闭借用的会话**。它只标记客户端已关闭，并且仅在会话由 SDK 自己
  创建时才释放连接池。是否关闭仍归你所有的会话，由你决定。

## 错误处理

```python
from cortex_mem import CortexMemClient, NotFoundError, RateLimitError, APIError

try:
    client.delete_observation("nonexistent")
except NotFoundError:
    print("Observation not found")
except RateLimitError:
    print("Rate limited, retry later")
except APIError as e:
    print(f"API error {e.status_code}: {e.message}")
```

### 响应体大小上限

本 SDK **没有**响应体大小上限。`requests` 会先把整个响应体缓冲下来，再由
`resp.json()` 解析，因此超大响应只受进程可用内存约束。

这是与 Go / JS SDK 的有意差异：后两者把上限设为 10 MiB 并抛出明确错误，而
Python SDK 没有这样做，因为 `requests` 未提供可移植的流式大小检查钩子。在检索或
批量列出大量 observation 时，请把 `limit` 控制得小一些。

## 设计原则

1. **零强制依赖** —— 仅需 `requests`
2. **Python 风格** —— dataclass、kwargs、上下文管理器
3. **与 Go/Java SDK 兼容** —— 覆盖全部 25 个 API 方法
4. **Fire-and-forget 捕获** —— 捕获操作内部重试并静默错误

## Wire 格式

SDK 自动处理 Wire 格式差异：

- `project_path` → 观察/会话结束端点中使用 `cwd`
- `project_path` → 会话启动端点中使用 `project_path`
- `extracted_data` → `extractedData` (camelCase)
- `required_concepts` → `requiredConcepts` (camelCase)
- JSON 编码的字符串列表字段（`facts`、`concepts`、`files_read`、
  `files_modified`）→ Python `list[str]`
- 逗号分隔字符串字段（`refined_from_ids`）→ Python `list[str]`

第一条值得展开说明，因为它不是推测：后端把这四个 JSONB 列序列化成**字符串**，
因此线上的一条 observation 到达时是 `concepts: '["allergy","peanut"]'`，
而不是 JSON 数组。SDK 两种形态都能解析，因此 `observation.concepts` 始终是真正的
列表。若字符串不是合法 JSON，则按逗号切分降级处理，与 JS SDK 一致。

`refined_from_ids` 是另一种情况，因此单独列出：它是 `TEXT` 列而**非** JSONB，
存放逗号分隔的 UUID（`"uuid-1,uuid-2"`）。它完全没有 JSON 这一层——后端用 `,`
拼接 ID——所以按逗号切分是唯一适用的路径。把它与上面四个归为一类会错误描述后端的行为。

详见[设计文档](../../docs/drafts/python-sdk-design.md)。

## 许可证

MIT
