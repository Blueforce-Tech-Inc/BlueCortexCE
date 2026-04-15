> English version: [README.md](./README.md)

# Cortex CE Go SDK

[Cortex CE](https://github.com/Blueforce-Tech-Inc/BlueCortexCE) 的 Go 客户端库 —— AI 助手的持久记忆系统。

## 特性

- **零强制依赖** —— 仅使用 Go 标准库
- **完整 API 覆盖** —— 25 个方法，涵盖会话、捕获、检索、管理、提取、版本
- **框架集成** —— 可选的 Eino、LangChainGo、Genkit 模块
- **Wire 格式兼容** —— JSON 字段名与后端 API 完全一致
- **全面测试** —— 280 个单元测试，含 Wire 格式验证（client 194 + dto 53 + genkit 13 + langchaingo 12 + eino 8；集成包需从各自目录运行）

## 安装

```bash
go get github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go
```

## 快速开始

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/dto"
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
| `WithHTTPClient` | *(自动构建)* | 自定义 `http.Client`（覆盖超时选项） |
| `WithMaxRetries` | `3` | Fire-and-forget 操作最大重试次数 |
| `WithRetryBackoff` | `500ms` | 基础重试退避（线性：`backoff × attempt`） |
| `WithLogger` | *(空操作)* | 自定义日志器（兼容 `*slog.Logger`） |

## 框架集成

### Eino

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/eino"
)

client := cortexmem.NewClient()
retriever := eino.NewRetriever(client, "/my-project",
    eino.WithRetrieverSource("tool_result"),
)
```

### LangChainGo

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/langchaingo"
)

client := cortexmem.NewClient()
memory := langchaingo.NewMemory(client, "/my-project")
```

### Genkit

```go
import (
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"
    "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go/genkit"
)

client := cortexmem.NewClient()
retriever := genkit.NewRetriever(client, "/my-project",
    genkit.WithRetrieverCount(20),
)
```

## 测试

```bash
# 运行所有测试
go test -v ./...

# 运行测试并查看覆盖率
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
import "github.com/Blueforce-Tech-Inc/BlueCortexCE/cortex-mem-go"

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

## Wire 格式

SDK 使用与后端 API 完全一致的 JSON 字段名：

- `session_id` (snake_case)
- `project_path` → 工具观察中使用 `cwd`
- `type` → 工具观察中使用 `tool_name`
- `requiredConcepts` (camelCase)
- `observationId` (camelCase)

详见 `dto/` 包中的完整 Wire 格式定义。

详见 [Go SDK 设计文档](../../docs/drafts/go-sdk-design.md)。

## 许可证

MIT
