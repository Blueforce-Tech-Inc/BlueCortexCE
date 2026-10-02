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

### 配置项

构造函数的全部参数，含 `client.py` 中实际生效的默认值与下限：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `base_url` | `http://127.0.0.1:37777` | 末尾斜杠会被去掉 |
| `timeout` | `30.0` | 单次请求超时，单位**秒**；下限 `0.1` |
| `max_retries` | `3` | 总**尝试**次数而非重试次数（3 = 发 3 次）；下限 `1` |
| `retry_backoff` | `0.5` | 退避基数，单位**秒**；下限 `0.1` |
| `api_key` | *(无)* | 每次请求以 `Authorization: Bearer <key>` 发送 |
| `session` | *(新建 `requests.Session`)* | 传入即借用，见上文 |

**单位与另外三家不同，移植配置值时需要换算**：这里的超时与退避用**秒**，
Go 与 Java 接受 `time.Duration` / Spring `Duration`，JS 接受毫秒。`500ms`
在这里是 `0.5` 而不是 `500`。`max_retries` 计的是尝试次数，与 Java 的
`retry.max-attempts` 以及 Go、JS 的同名项一致——四家默认值都是 3。

重试**只作用于 fire-and-forget 捕获**——`record_observation`、
`record_session_end`、`record_user_prompt`。3 次尝试的预算用于瞬时故障，
耗尽后错误被静默吞掉。其它任何方法都不重试，因此后端偶发抖动对其余 API
会在**第一次尝试**就直接暴露。Go 与 JS 同样只重试这三个；而 Java SDK 会重试
25 个方法中的 **10** 个，额外包括两个抽取读方法、三个变更类方法，以及
`trigger_refinement` / `trigger_extraction`。若要在 SDK 之间移植「避免重试」
的代码，这个差异需要留意。

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

### 畸形响应体

`APIError` 只用于 HTTP 状态码。响应体**存在但无法解析**时（以 200 状态返回的 HTML
错误页——反向代理或网关失败时正是这个形状）抛出的是 `CortexError`：

```python
from cortex_mem import CortexMemClient, APIError, CortexError

try:
    page = client.list_observations("myproject")
except APIError as e:
    print(f"backend returned {e.status_code}: {e.message}")
except CortexError as e:
    print(f"backend response was not usable: {e}")
```

这是刻意的。原先返回空结果，会让失败与「后端确实没有数据」无法区分：
`start_session` 会交回一个 `session_id` 为空的响应对象，而调用方接下来整个会话都会
用这个空 ID；每个读方法也都返回一个结构完整但内容为空的对象。Go 与 JS 对同样的输入
都是抛错。

**真正为空**的响应体（204 No Content，或零长度内容）不算错误，仍返回各方法文档化的
默认值。

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

### 空更新会被拒绝

`update_observation` 在更新未设置任何字段时抛 `ValidationError`，且不会发出任何请求。Go、Java、JS 三家规则与消息完全相同：

```
cortex-ce: validation error on update: at least one field must be provided for update
```

这一点很重要：不设置任何字段的 PATCH 在 wire 上是一次静默 no-op。若没有这道检查，调用方用用户输入拼出一个空更新后会看到调用正常返回，却无法得知其实什么都没写入。若要显式发送一个空的 JSONB 值，请以关键字参数传入 `extracted_data={}`——数据类形式 `ObservationUpdate(extracted_data={})` 会把它从 wire 中省略，因此仍属空更新。

### 必填参数在客户端校验

下表中的参数都必须非空。SDK 抛出 `ValidationError` 且不发出任何请求。Go、Java、JS 三家 SDK 强制的是完全相同的一组规则。

| 方法 | 必填参数 |
|------|----------|
| `start_session` | `session_id`、`project_path` |
| `update_session_user_id` | `session_id`、`user_id` |
| `record_observation` | `session_id`、`project_path`、`tool_name` |
| `record_session_end` | `session_id`、`project_path` |
| `record_user_prompt` | `session_id`、`prompt_text`、`project_path` |
| `retrieve_experiences` | `task` |
| `build_icl_prompt` | `task` |
| `search` | `project` |
| `get_observation` | `observation_id` |
| `get_observations_by_ids` | `ids`——非空、至多 100 个、元素不得为空 |
| `trigger_refinement` | `project_path` |
| `submit_feedback` | `observation_id`、`feedback_type` |
| `update_observation` | `observation_id`，外加至少一个待修改字段 |
| `delete_observation` | `observation_id` |
| `get_quality_distribution` | `project_path` |
| `trigger_extraction` | `project_path` |
| `get_latest_extraction` | `project_path`、`template_name` |
| `get_extraction_history` | `project_path`、`template_name`；`limit` 不得为负 |

`record_user_prompt` 的 `project_path` 必须以位置或关键字方式传入——它没有默认值，
因此漏传会在调用处直接抛 `TypeError`，而不是悄悄发出一个空 `cwd`。

这些检查不是装饰。其中三个 capture 方法最能说明问题：`record_observation` 是
fire-and-forget，会吞掉后端返回的一切——`tool_name` 为空时后端返回
`400 Missing required field: tool_name`，SDK 记一条日志后返回 `None`，调用方于是认为
观测已记录，而服务器刚刚拒绝了它。`project_path` 为空则更隐蔽，因为后端**接受**它：
记录会以空项目路径入队，随后不出现在任何按项目过滤的查询里，全程没有任何错误提示。

`search` 属于同一类隐患：SDK 总会发送 `project`，而 `GET /api/search?project=` 会
返回 `200` 加一个空结果集，因此漏传参数的调用方读到的是「没有匹配」而不是「你的调用
不合法」。

`retrieve_experiences` 与 `build_icl_prompt` 是上面那张必填表**唯一不完整**的地方，
而且隐患在**另一个**字段上：这两个签名的 `project` 默认值是 `""` 且都**不校验**，
而 `search` 反而校验 project。对这两个方法来说这个顺序恰好是反的——
`POST /api/memory/experiences` 与 `POST /api/memory/icl-prompt` 会把该值直接传入
仓储查询且没有跨全部项目的分支，因此缺失或为空的项目匹配不到内容，返回 `200` 加
空结果而不是报错。活体实测：省略 `project`、传 `""`、传不存在的路径三者都返回
`200 []`，而真实项目路径才会返回经验。这个默认值让
`retrieve_experiences(task="...")` 这种写法看起来像在全局检索——其实不是。

`list_observations` 与无参的 getter（`get_stats`、`get_projects`、`get_modes`、
`get_settings`、`get_version`、`health_check`）没有必填参数；`get_stats` 接受一个可选的
项目过滤条件。

这句说的是**必填参数**，不是**影响范围**——而在 `list_observations` 上两者指向相反
方向。`list_observations` 是唯一一个项目过滤会**放宽**而非**清空**的检索方法：省略 `project`
（传空串也一样，四家 SDK 都会把它转成省略），SDK 就不会发出 `project` 参数，
后端随即返回**该实例上全部项目**的观测。活体实测：省略 `project` 的
`GET /api/observations` 在一个 100 条的单页里返回了 **16 个不同项目**的数据。
而**直连 HTTP** 带上字面量 `?project=` 则是相反的情形、会返回空——因为仓储查询判的是
`IS NULL` 而非空串；绕过 SDK 直连前值得知道这一点。

因此它与上面那两个 ICL 端点恰好互为镜像：那里空项目**静默清空**结果，这里空项目
**静默放宽**结果。在多租户部署中，这是**跨租户数据外泄**而不是「少给了一条答案」，
而且没有任何客户端校验能拦住它——因为这个调用本身是合法的。

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
