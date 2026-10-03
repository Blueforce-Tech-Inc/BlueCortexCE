> English version: [README.md](./README.md)

# Cortex CE Go SDK

[Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) 的 Go 客户端库 —— AI 助手的持久记忆系统。

## 特性

- **零强制依赖** —— 仅使用 Go 标准库
- **完整 API 覆盖** —— 25 个方法，涵盖会话、捕获、检索、管理、提取、版本、P1
- **框架集成** —— 可选的 Eino、LangChainGo、Genkit 模块
- **Wire 格式兼容** —— JSON 字段名与后端 API 完全一致
- **全面测试** —— 359 个测试，含 Wire 格式验证。根模块跑 299 个（core 232 + dto 67）；适配器与示例模块从各自目录运行时再增加 60 个（eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27）。为何单跑 `go test ./...` 只覆盖根模块，见[测试](#测试)。

## 安装

```bash
go get github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go
```

## 快速开始

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

func main() {
    client := cortexmem.NewClient(
        cortexmem.WithBaseURL("http://127.0.0.1:37777"),
    )
    defer client.Close()

    ctx := context.Background()

    // 启动会话
    resp, err := client.StartSession(ctx, dto.SessionStartRequest{
        SessionID:   "my-session-001",
        ProjectPath: "/my-project",
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Session: %s\n", resp.SessionID)

    // 记录观察
    err = client.RecordObservation(ctx, dto.ObservationRequest{
        ProjectPath:  "/my-project",
        SessionID:    resp.SessionID,
        ToolName:     "Read",
        ToolInput:    map[string]any{"file_path": "file.txt"},
        ToolResponse: map[string]any{"content": "file contents..."},
    })
    if err != nil {
        log.Fatal(err)
    }

    // 搜索记忆
    result, err := client.Search(ctx, dto.SearchRequest{
        Project: "/my-project",
        Query:   "file operations",
        Limit:   5,
    })
    if err != nil {
        log.Fatal(err)
    }
    fmt.Printf("Found %d results (strategy: %s)\n", result.Count, result.Strategy)
}
```

## API 覆盖

| 类别 | 方法 |
|------|------|
| 会话 | `StartSession`, `UpdateSessionUserId` |
| 捕获 | `RecordObservation`, `RecordSessionEnd`, `RecordUserPrompt` |
| 检索 | `RetrieveExperiences`, `BuildICLPrompt`, `Search`, `ListObservations`, `GetObservation`, `GetObservationsByIds` |
| 管理 | `TriggerRefinement`, `SubmitFeedback`, `UpdateObservation`, `DeleteObservation`, `GetQualityDistribution` |
| 健康 | `HealthCheck` |
| 提取 | `TriggerExtraction`, `GetLatestExtraction`, `GetExtractionHistory` |
| 版本 | `GetVersion` |
| P1 | `GetProjects`, `GetStats`, `GetModes`, `GetSettings` |
| 生命周期 | `Close`（释放 SDK 自建的连接池，见下文）、`String`（调试表示） |

## Option 模式

使用 Option 配置客户端行为：

```go
client := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://127.0.0.1:37777"),
    cortexmem.WithAPIKey("my-api-key"),
    cortexmem.WithTimeout(30*time.Second),       // 总请求超时（默认 30s）
    cortexmem.WithConnectTimeout(10*time.Second), // 连接超时（默认 10s）
    cortexmem.WithMaxRetries(5),
    cortexmem.WithRetryBackoff(500*time.Millisecond),
)
```

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `WithBaseURL` | `http://127.0.0.1:37777` | 后端基础 URL |
| `WithAPIKey` | *(无)* | Bearer Token 认证 |
| `WithTimeout` | `30s` | 总请求超时（与 Java SDK `readTimeout` 对齐） |
| `WithConnectTimeout` | `10s` | 连接超时（与 Java SDK `connectTimeout` 对齐） |
| `WithHTTPClient` | *(自动构建)* | 自定义 `http.Client`（覆盖超时选项；归调用方所有，见下文） |
| `WithMaxRetries` | `3` | Fire-and-forget 操作的总**尝试**次数（3 = 发 3 次请求，即首次之后的 2 次重试） |
| `WithRetryBackoff` | `500ms` | 基础重试退避（线性：`backoff × attempt`） |
| `WithLogger` | *(空操作)* | 自定义日志器（兼容 `*slog.Logger`） |

### HTTP 客户端归属

`WithHTTPClient` 传入的客户端归调用方所有。SDK 不会改写它，也不会关闭它：
`Close()` 只释放 SDK **自建**客户端的空闲连接——清空一个你仍在与自己业务共用的
连接池，会白白丢掉热连接，并让你的下一次调用被迫重新建连。

Go 的 `http.Client` 没有「已关闭」状态，因此无论是否调用 `Close()`，客户端都仍可
使用。差别只在于连接池是否保留，断言时请针对这一点，而不是断言下一次调用是否报错。

## 框架集成

> **各适配器的错误处理有意不同**：Eino 与 Genkit 的 Retriever 会先记录日志再把错误返回给调用方，
> 因为"空结果"与"没有相关记忆"无法区分；而 LangChainGo 的 `Memory` 会降级为空记忆串，
> 以免中断提示词链路，同时记录日志保证错误仍然可见。

### Eino

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/eino"
)

client := cortexmem.NewClient()
retriever := eino.NewRetriever(client, "/my-project",
    eino.WithRetrieverSource("tool_result"),
)
```

### LangChainGo

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/langchaingo"
)

client := cortexmem.NewClient()
memory := langchaingo.NewMemory(client, "/my-project")
```

### Genkit

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/genkit"
)

client := cortexmem.NewClient()
retriever := genkit.NewRetriever(client, "/my-project",
    genkit.WithRetrieverCount(20),
)
```

## 测试

**本 SDK 是 9 个独立的 Go module，而不是一个。** `eino/`、`genkit/`、
`langchaingo/` 以及 `examples/` 下的每个目录都有自己的 `go.mod`，因此在本目录
直接执行 `go test ./...` **只能覆盖根模块**——359 个测试里的 299 个。被跳过的
四个适配器与示例模块，恰恰是最容易随上游框架升级而失效的部分，所以请跑全部九个：

```bash
# 逐个 module 运行全部测试（遇到第一个失败即中止）
find . -name go.mod -exec dirname {} \; | sort | while read -r d; do
  (cd "$d" && go test ./... -count=1) || exit 1
done
```

2026-10-03 实测九个全绿：core 232 + dto 67 + eino 8 + genkit 13
+ langchaingo 12 + `examples/http-server` 27 = **359**。其余四个 `examples/`
模块没有测试文件，会输出 `[no test files]`。

```bash
# 覆盖率仅统计根模块（各适配器需各自加 -cover 单独运行）
go test -cover ./...
```

## 示例项目

参见 `examples/` 目录下的完整示例：
- `basic/` —— 纯 SDK 使用
- `eino/` —— Eino 集成
- `langchaingo/` —— LangChainGo 集成
- `genkit/` —— Genkit 集成
- `http-server/` —— HTTP 服务器示例

## 错误处理

```go
import "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"

result, err := client.Search(ctx, req)
if err != nil {
    if cortexmem.IsNotFound(err) {
        // 处理 404
    } else if cortexmem.IsBadRequest(err) {
        // 处理 400
    } else {
        // 处理其他错误
    }
}
```

### 响应体大小上限

SDK 不会缓冲超过 **10 MiB** 的响应体（`cortexmem.MaxResponseBytes`，即
`10 << 20`）。读取时的上限是 `MaxResponseBytes + 1` 字节，因此超限响应会以
明确的错误返回，而不是被静默截断后再解析失败：

```
cortex-ce: response body exceeds 10485760 byte limit (raise the page size or split the query)
```

内部 `doRequest` 会把服务端状态码与该错误一起返回，但公开方法只返回 `error` ——
因此超限的**成功**响应（`200`）表现为一个普通错误，既不是空结果也不是
`APIError`。请把检索调用的 `limit` 控制在上限以内，或缩小查询范围。

### 空更新会被拒绝

`UpdateObservation` 在更新未设置任何字段时返回 `ValidationError`，且不会发出任何请求。Java、Python、JS 三家规则与消息完全相同：

```
cortex-ce: ObservationUpdate validation error: at least one field must be provided for update
```

这一点很重要：不设置任何字段的 PATCH 在 wire 上是一次静默 no-op。若没有这道检查，调用方用用户输入拼出一个空更新后会看到调用成功，却无法得知其实什么都没写入。

### 必填参数在客户端校验

下表中的参数都必须非空。SDK 返回 `ValidationError` 且不发出任何请求。Java、Python、JS 三家 SDK 强制的是完全相同的一组规则。

| 方法 | 必填参数 |
|------|----------|
| `StartSession` | `req.SessionID`、`req.ProjectPath` |
| `UpdateSessionUserId` | `sessionID`、`userID` |
| `RecordObservation` | `req.SessionID`、`req.ProjectPath`、`req.ToolName` |
| `RecordSessionEnd` | `req.SessionID`、`req.ProjectPath` |
| `RecordUserPrompt` | `req.SessionID`、`req.PromptText`、`req.ProjectPath` |
| `RetrieveExperiences` | `req.Task` |
| `BuildICLPrompt` | `req.Task` |
| `Search` | `req.Project` |
| `GetObservation` | `id` |
| `GetObservationsByIds` | `ids`——非空、至多 100 个、元素不得为空 |
| `TriggerRefinement` | `projectPath` |
| `SubmitFeedback` | `observationID`、`feedbackType` |
| `UpdateObservation` | `observationID`，外加至少一个待修改字段 |
| `DeleteObservation` | `observationID` |
| `GetQualityDistribution` | `projectPath` |
| `TriggerExtraction` | `projectPath` |
| `GetLatestExtraction` | `projectPath`、`templateName` |
| `GetExtractionHistory` | `projectPath`、`templateName`；`limit` 不得为负 |

其中多数方法接收 `dto.*Request` 结构体，管理与抽取类方法则按位置传参。注意 `Search`
与 `RetrieveExperiences` 把限定范围的字段命名为 `Project` 而非 `ProjectPath`，因为在这
两个端点上它上 wire 的名字就是 `project`。

**`RetrieveExperiences` / `BuildICLPrompt` 的空 `Project` 是必填参数表里唯一值得
单独说明的缺口。** 上表是准确的——这两个方法都不校验 `project`，而 `Search` 校验——
但表格说的是「校验了什么」，不是「会发生什么」，而这两者在这里差别很大。
`POST /api/memory/experiences` 与 `POST /api/memory/icl-prompt` 会把该值直接传给
仓储查询，且**没有任何跨全部项目的分支**，因此缺失或为空的项目匹配不到内容，
返回 `200` 加空结果，而**不是**报错。活体实测：省略 `project`、传 `""`、
传不存在的路径，三者都返回 `200 []`，而真实项目路径才会返回经验。
SDK 自带的适配器也继承了这个陷阱——`eino` 与 `genkit` 的 retriever 以及
`langchaingo` 的 memory 都接收项目字符串，且**均不校验**。
因此「项目未设置」与「该项目确实没有记忆」无法区分，尽管客户端不会替你把关，
仍值得在调用处自行判断。四家 SDK 在这两个端点上都是如此。

这些检查不是装饰。其中三个 capture 方法最能说明问题：`RecordObservation` 是
fire-and-forget，会吞掉后端返回的一切——`ToolName` 为空时后端返回
`400 Missing required field: tool_name`，SDK 记一条日志后正常返回，调用方于是认为
观测已记录，而服务器刚刚拒绝了它。`ProjectPath` 为空则更隐蔽，因为后端**接受**它：
记录会以空项目路径入队，随后不出现在任何按项目过滤的查询里，全程没有任何错误提示。

`Search` 属于同一类隐患：SDK 总会发送 `project`，而 `GET /api/search?project=` 会
返回 `200` 加一个空结果集，因此漏传参数的调用方读到的是「没有匹配」而不是「你的调用
不合法」。

`ListObservations` 与无参的 getter（`GetStats`、`GetProjects`、`GetModes`、
`GetSettings`、`GetVersion`、`HealthCheck`）没有必填参数；`GetStats` 接受一个可选的
项目过滤条件。

这句说的是**必填参数**，不是**影响范围**——而在 `ListObservations` 上两者指向相反
方向。`ListObservations` 是唯一一个项目过滤会**放宽**而非**清空**的检索方法：省略 `project`
（传空串也一样，四家 SDK 都会把它转成省略），SDK 就不会发出 `project` 参数，
后端随即返回**该实例上全部项目**的观测。活体实测：省略 `project` 的
`GET /api/observations` 在一个 100 条的单页里返回了 **16 个不同项目**的数据。
而**直连 HTTP** 带上字面量 `?project=` 则是相反的情形、会返回空——因为仓储查询判的是
`IS NULL` 而非空串；绕过 SDK 直连前值得知道这一点。

因此它与上面那两个 ICL 端点恰好互为镜像：那里空项目**静默清空**结果，这里空项目
**静默放宽**结果。在多租户部署中，这是**跨租户数据外泄**而不是「少给了一条答案」，
而且没有任何客户端校验能拦住它——因为这个调用本身是合法的。

## Wire 格式

SDK 使用与后端 API 完全一致的 JSON 字段名：

- `session_id` (snake_case)
- `project_path` → 工具观察中使用 `cwd`
- `type` → 工具观察中使用 `tool_name`
- `requiredConcepts` (camelCase)
- `observationId` (camelCase)

**列表列的到达方式并不一致。** `Facts`、`Concepts`、`FilesRead`、`FilesModified` 是 JSONB 列，
后端为 WebUI 把它们序列化成 **JSON 编码的字符串**，因此线上的一条 observation 到达时是
`concepts: "[\"allergy\",\"peanut\"]"`，而不是 JSON 数组。`RefinedFromIds` 则不同：它是存放
逗号分隔 UUID（`"uuid-1,uuid-2"`）的 `TEXT` 列，没有任何 JSON 编码——后端用 `,` 拼接 ID。
`dto.StringList` 接受全部三种形态（真实数组、JSON 编码数组、逗号分隔串）且从不返回错误，
因此某个意外的列表列永远无法让承载它的 observation 解析失败。

详见 `dto/` 包中的完整 Wire 格式定义。

详见 [Go SDK 设计文档](../../docs/drafts/go-sdk-design.md)。

## 许可证

MIT
