# Go Client SDK 设计文档

> **版本**: v1.0 DRAFT
> **日期**: 2026-03-24
> **状态**: 待审批
> **作者**: Cortex CE Team

---

## 1. 设计原则

### 1.1 Go SDK 的核心决策

**Go SDK 保持独立，不依赖任何 AI 框架。以可选模块提供与各框架的集成。**

| 原则 | 说明 |
|------|------|
| **零强制依赖** | 核心包只依赖 Go 标准库 (`net/http`, `context`, `encoding/json`) |
| **地道 Go 风格** | context 支持、error 处理、Option 模式、`io.Reader` 等 |
| **可选集成层** | Eino、Genkit、LangChainGo 等以独立 module 提供 |
| **与 Java SDK 等价** | 覆盖 Java `cortex-mem-client` 的全部 API |

### 1.2 为什么不依赖 AI 框架

```
Go 生态现实：
├── Eino (CloudWeGo)    — 字节跳动背景，增长快
├── Genkit (Google)     — Google 背景，刚进入 Go
├── ADK-Go (Google)     — Agent 开发框架
├── LangChainGo         — 社区驱动，历史悠久
└── 无框架 (原生 API)   — 占比最大

结论：选择任何一个 = 排斥其他用户
```

Go 社区文化：**极度厌恶不必要的依赖**。一个"记忆系统 Client"强制引入整个 AI 框架，会直接劝退用户。

参考先例：OpenTelemetry SDK、数据库驱动 (sql.Open) 都是"核心零依赖 + 可选集成层"的结构。

### 1.3 与 Java SDK 的对比

| | Java SDK | Go SDK |
|--|---------|--------|
| 核心依赖 | Spring AI (合理) | **无框架依赖** (必须) |
| 框架集成 | 天然继承 Spring AI 生态 | 独立 module 提供各框架适配 |
| HTTP 客户端 | Spring RestClient | `net/http` + 可选自定义 |
| 目标用户 | Spring AI 开发者 | 所有 Go 开发者 |
| 配置方式 | Spring properties | Go Option 模式 |

---

## 2. 目录结构

```
cortex-mem-go/
├── go.mod                        # module github.com/abforce/cortex-ce/cortex-mem-go
├── client.go                     # Client 接口 + 实现
├── client_option.go              # Option 模式配置
├── client_test.go                # 单元测试
├── error.go                      # 错误类型定义
├── dto/
│   ├── experience.go             # Experience DTO
│   ├── experience_request.go     # ExperienceRequest
│   ├── icl_prompt.go             # ICLPromptRequest / ICLPromptResult
│   ├── observation.go            # ObservationRequest / ObservationUpdate
│   ├── quality.go                # QualityDistribution
│   ├── session.go                # SessionStartRequest / SessionEndRequest
│   └── user_prompt.go            # UserPromptRequest
├── eino/
│   ├── go.mod                    # module github.com/abforce/cortex-ce/cortex-mem-go/eino
│   ├── retriever.go              # 实现 Eino 的 Retriever 接口
│   └── retriever_test.go
├── genkit/
│   ├── go.mod                    # module github.com/abforce/cortex-ce/cortex-mem-go/genkit
│   ├── plugin.go                 # 实现 Genkit plugin 接口
│   └── plugin_test.go
├── langchaingo/
│   ├── go.mod                    # module github.com/abforce/cortex-ce/cortex-mem-go/langchaingo
│   ├── memory.go                 # 实现 LangChainGo memory 接口
│   └── memory_test.go
├── examples/
│   ├── basic/                    # 纯 SDK 使用（无框架）
│   │   ├── main.go
│   │   ├── session.go
│   │   ├── memory.go
│   │   └── go.mod
│   ├── eino/                     # Eino 框架集成
│   │   ├── main.go
│   │   ├── retriever.go
│   │   ├── chat.go
│   │   └── go.mod
│   ├── genkit/                   # Genkit 框架集成
│   │   ├── main.go
│   │   ├── flow.go
│   │   └── go.mod
│   ├── langchaingo/              # LangChainGo 集成
│   │   ├── main.go
│   │   ├── chain.go
│   │   └── go.mod
│   └── http-server/              # HTTP 服务示例
│       ├── main.go
│       ├── handler/
│       │   ├── chat.go
│       │   ├── memory.go
│       │   └── session.go
│       └── go.mod
├── README.md
└── CHANGELOG.md
```

---

## 3. 核心包 API 设计

### 3.1 Client 创建 — Option 模式

```go
// cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"

client, err := cortexmem.NewClient(
    cortexmem.WithBaseURL("http://localhost:37777"),
    cortexmem.WithTimeout(10*time.Second),
    cortexmem.WithRetry(3, 500*time.Millisecond),
    cortexmem.WithHTTPClient(customHTTPClient),   // 可选：自定义 http.Client
    cortexmem.WithLogger(customLogger),            // 可选：注入 logger
)
```

**Option 函数列表**：

| Option | 默认值 | 说明 |
|--------|--------|------|
| `WithBaseURL(url)` | `http://localhost:37777` | 服务端地址 |
| `WithTimeout(d)` | `10s` | 单次请求超时 |
| `WithRetry(attempts, backoff)` | `3, 500ms` | 重试策略 |
| `WithHTTPClient(c)` | `&http.Client{}` | 自定义 HTTP 客户端 |
| `WithLogger(l)` | `slog.Default()` | 日志注入 |
| `WithHeaders(h)` | `{}` | 额外请求头 |

### 3.2 Client 接口

```go
// Client is the unified interface for the Cortex CE memory system.
type Client interface {
    // ==================== Capture ====================

    // StartSession starts or resumes a session. POST /api/session/start
    StartSession(ctx context.Context, req dto.SessionStartRequest) (*dto.SessionStartResponse, error)

    // RecordObservation records a tool-use observation. POST /api/ingest/tool-use
    RecordObservation(ctx context.Context, req dto.ObservationRequest) error

    // RecordSessionEnd signals session end. POST /api/ingest/session-end
    RecordSessionEnd(ctx context.Context, req dto.SessionEndRequest) error

    // RecordUserPrompt records a user prompt. POST /api/ingest/user-prompt
    RecordUserPrompt(ctx context.Context, req dto.UserPromptRequest) error

    // ==================== Retrieval ====================

    // RetrieveExperiences retrieves relevant experiences. POST /api/memory/experiences
    RetrieveExperiences(ctx context.Context, req dto.ExperienceRequest) ([]dto.Experience, error)

    // BuildICLPrompt builds an ICL prompt from historical experiences. POST /api/memory/icl-prompt
    BuildICLPrompt(ctx context.Context, req dto.ICLPromptRequest) (*dto.ICLPromptResult, error)

    // ==================== Management ====================

    // TriggerRefinement triggers memory refinement. POST /api/memory/refine
    TriggerRefinement(ctx context.Context, projectPath string) error

    // SubmitFeedback submits feedback for an observation. POST /api/memory/feedback
    SubmitFeedback(ctx context.Context, observationID, feedbackType, comment string) error

    // UpdateObservation updates an existing observation. PATCH /api/memory/observations/{id}
    UpdateObservation(ctx context.Context, observationID string, update dto.ObservationUpdate) error

    // DeleteObservation deletes an observation. DELETE /api/memory/observations/{id}
    DeleteObservation(ctx context.Context, observationID string) error

    // GetQualityDistribution gets memory quality distribution. GET /api/memory/quality-distribution
    GetQualityDistribution(ctx context.Context, projectPath string) (*dto.QualityDistribution, error)

    // HealthCheck checks if the backend is healthy. GET /api/health
    HealthCheck(ctx context.Context) error

    // ==================== Extraction (Phase 3) ====================

    // GetLatestExtraction gets latest extraction result. GET /api/extraction/{template}/latest
    GetLatestExtraction(ctx context.Context, projectPath, templateName, userID string) (map[string]any, error)

    // GetExtractionHistory gets extraction history. GET /api/extraction/{template}/history
    GetExtractionHistory(ctx context.Context, projectPath, templateName, userID string, limit int) ([]map[string]any, error)

    // UpdateSessionUserId updates session userId. PATCH /api/session/{sessionId}/user
    UpdateSessionUserId(ctx context.Context, sessionID, userID string) (map[string]any, error)

    // Close releases any resources held by the client.
    Close() error
}
```

### 3.3 DTO 设计

#### Experience

```go
// dto/experience.go
package dto

import "time"

// Experience represents a retrieved memory from the Cortex CE system.
type Experience struct {
    ID              string    `json:"id"`
    Task            string    `json:"task"`
    Strategy        string    `json:"strategy"`
    Outcome         string    `json:"outcome"`
    ReuseCondition  string    `json:"reuseCondition"`
    QualityScore    float32   `json:"qualityScore"`
    CreatedAt       time.Time `json:"createdAt"`
}
```

#### ExperienceRequest — Functional Options

```go
// dto/experience_request.go
package dto

// ExperienceRequest is a request for retrieving relevant experiences.
type ExperienceRequest struct {
    Task             string   `json:"task"`
    Project          string   `json:"project"`
    Count            int      `json:"count,omitempty"`
    Source           string   `json:"source,omitempty"`
    RequiredConcepts []string `json:"requiredConcepts,omitempty"`
    UserID           string   `json:"userId,omitempty"`
}

// NewExperienceRequest creates an ExperienceRequest with required fields.
func NewExperienceRequest(task, project string, opts ...ExperienceRequestOption) ExperienceRequest {
    r := ExperienceRequest{Task: task, Project: project, Count: 4}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type ExperienceRequestOption func(*ExperienceRequest)

func WithCount(n int) ExperienceRequestOption {
    return func(r *ExperienceRequest) { r.Count = n }
}

func WithSource(source string) ExperienceRequestOption {
    return func(r *ExperienceRequest) { r.Source = source }
}

func WithRequiredConcepts(concepts ...string) ExperienceRequestOption {
    return func(r *ExperienceRequest) { r.RequiredConcepts = concepts }
}

func WithUserID(userID string) ExperienceRequestOption {
    return func(r *ExperienceRequest) { r.UserID = userID }
}
```

#### SessionStartRequest / SessionEndRequest

```go
// dto/session.go
package dto

// SessionStartRequest is a request to start or resume a session.
type SessionStartRequest struct {
    SessionID   string `json:"session_id"`
    ProjectPath string `json:"project_path"`
    UserID      string `json:"user_id,omitempty"`
}

// SessionStartResponse is the response from starting a session.
type SessionStartResponse struct {
    SessionDBID  string `json:"session_db_id"`
    SessionID    string `json:"session_id"`
    Context      string `json:"context,omitempty"`
    PromptNumber int    `json:"prompt_number"`
}

// SessionEndRequest is a request to end a session.
type SessionEndRequest struct {
    SessionID           string `json:"session_id"`
    ProjectPath         string `json:"cwd"`
    LastAssistantMessage string `json:"last_assistant_message,omitempty"`
}

// NewSessionStartRequest creates a SessionStartRequest.
func NewSessionStartRequest(sessionID, projectPath string, opts ...SessionStartOption) SessionStartRequest {
    r := SessionStartRequest{SessionID: sessionID, ProjectPath: projectPath}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type SessionStartOption func(*SessionStartRequest)

func WithSessionUserID(userID string) SessionStartOption {
    return func(r *SessionStartRequest) { r.UserID = userID }
}
```

#### ObservationRequest / ObservationUpdate

```go
// dto/observation.go
package dto

// ObservationRequest records a tool-use observation.
type ObservationRequest struct {
    SessionID     string         `json:"session_id"`
    ProjectPath   string         `json:"cwd"`
    ToolName      string         `json:"tool_name"`
    ToolInput     any            `json:"tool_input,omitempty"`
    ToolResponse  any            `json:"tool_response,omitempty"`
    PromptNumber  int            `json:"prompt_number,omitempty"`
    Source        string         `json:"source,omitempty"`
    ExtractedData map[string]any `json:"extractedData,omitempty"`
}

// ObservationUpdate updates an existing observation.
type ObservationUpdate struct {
    Title         string         `json:"title,omitempty"`
    Content       string         `json:"content,omitempty"`
    Facts         []string       `json:"facts,omitempty"`
    Concepts      []string       `json:"concepts,omitempty"`
    Source        string         `json:"source,omitempty"`
    ExtractedData map[string]any `json:"extractedData,omitempty"`
}

// NewObservationRequest creates an ObservationRequest.
func NewObservationRequest(sessionID, projectPath, toolName string, opts ...ObservationOption) ObservationRequest {
    r := ObservationRequest{SessionID: sessionID, ProjectPath: projectPath, ToolName: toolName}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type ObservationOption func(*ObservationRequest)

func WithToolInput(input any) ObservationOption {
    return func(r *ObservationRequest) { r.ToolInput = input }
}

func WithToolResponse(resp any) ObservationOption {
    return func(r *ObservationRequest) { r.ToolResponse = resp }
}

func WithObservationSource(source string) ObservationOption {
    return func(r *ObservationRequest) { r.Source = source }
}

func WithExtractedData(data map[string]any) ObservationOption {
    return func(r *ObservationRequest) { r.ExtractedData = data }
}

func WithPromptNumber(n int) ObservationOption {
    return func(r *ObservationRequest) { r.PromptNumber = n }
}
```

#### ICLPromptRequest / ICLPromptResult

```go
// dto/icl_prompt.go
package dto

// ICLPromptRequest is a request for building an ICL prompt.
type ICLPromptRequest struct {
    Task     string `json:"task"`
    Project  string `json:"project"`
    MaxChars int    `json:"maxChars,omitempty"`
    UserID   string `json:"userId,omitempty"`
}

// ICLPromptResult is the result from the ICL prompt builder.
type ICLPromptResult struct {
    Prompt          string `json:"prompt"`
    ExperienceCount string `json:"experienceCount"`
}

// ExperienceCountAsInt parses the experience count as an integer.
func (r ICLPromptResult) ExperienceCountAsInt() int {
    n, _ := strconv.Atoi(r.ExperienceCount)
    return n
}

// NewICLPromptRequest creates an ICLPromptRequest with default maxChars.
func NewICLPromptRequest(task, project string, opts ...ICLOption) ICLPromptRequest {
    r := ICLPromptRequest{Task: task, Project: project, MaxChars: 4000}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type ICLOption func(*ICLPromptRequest)

func WithMaxChars(n int) ICLOption {
    return func(r *ICLPromptRequest) { r.MaxChars = n }
}

func WithICLUserID(userID string) ICLOption {
    return func(r *ICLPromptRequest) { r.UserID = userID }
}
```

#### QualityDistribution

```go
// dto/quality.go
package dto

// QualityDistribution represents memory quality distribution.
type QualityDistribution struct {
    Project string `json:"project"`
    High    int64  `json:"high"`
    Medium  int64  `json:"medium"`
    Low     int64  `json:"low"`
    Unknown int64  `json:"unknown"`
}

// Total returns the total number of observations.
func (q QualityDistribution) Total() int64 {
    return q.High + q.Medium + q.Low + q.Unknown
}
```

### 3.4 错误处理

```go
// error.go
package cortexmem

import (
    "fmt"
    "net/http"
)

// APIError represents an error response from the Cortex CE backend.
type APIError struct {
    StatusCode int
    Message    string
    Body       string
}

func (e *APIError) Error() string {
    return fmt.Sprintf("cortex-ce: API error %d: %s", e.StatusCode, e.Message)
}

// IsNotFound returns true if the error is a 404.
func IsNotFound(err error) bool {
    var apiErr *APIError
    if errors.As(err, &apiErr) {
        return apiErr.StatusCode == http.StatusNotFound
    }
    return false
}

// IsConflict returns true if the error is a 409.
func IsConflict(err error) bool {
    var apiErr *APIError
    if errors.As(err, &apiErr) {
        return apiErr.StatusCode == http.StatusConflict
    }
    return false
}

// IsRateLimited returns true if the error is a 429.
func IsRateLimited(err error) bool {
    var apiErr *APIError
    if errors.As(err, &apiErr) {
        return apiErr.StatusCode == http.StatusTooManyRequests
    }
    return false
}
```

### 3.5 Logger 接口

```go
// logger.go
package cortexmem

// Logger is the logging interface. Compatible with *slog.Logger.
type Logger interface {
    Debug(msg string, args ...any)
    Info(msg string, args ...any)
    Warn(msg string, args ...any)
    Error(msg string, args ...any)
}
```

### 3.6 内部实现要点

```go
// client.go (实现)
package cortexmem

import (
    "context"
    "net/http"
    "time"
)

type client struct {
    baseURL    string
    httpClient *http.Client
    maxRetries int
    retryDelay time.Duration
    logger     Logger
}

// 确保 client 实现 Client 接口
var _ Client = (*client)(nil)

func NewClient(opts ...Option) (Client, error) {
    c := &client{
        baseURL:    "http://localhost:37777",
        httpClient: &http.Client{Timeout: 10 * time.Second},
        maxRetries: 3,
        retryDelay: 500 * time.Millisecond,
        logger:     slog.Default(),
    }
    for _, opt := range opts {
        opt(c)
    }
    return c, nil
}

// 每个方法的标准模式:
func (c *client) RecordObservation(ctx context.Context, req dto.ObservationRequest) error {
    return c.doWithRetry(ctx, "RecordObservation", func() error {
        return c.post(ctx, "/api/ingest/tool-use", req, nil)
    })
}

// Capture 操作使用 fire-and-forget 模式（与 Java SDK 一致）：
// - 内部有重试
// - 失败只记录日志，不向上传播
// - 除非是 context.Canceled

func (c *client) RecordObservation(ctx context.Context, req dto.ObservationRequest) error {
    return c.doFireAndForget(ctx, "RecordObservation", func() error {
        return c.post(ctx, "/api/ingest/tool-use", req, nil)
    })
}
```

---

## 4. 框架集成层

### 4.1 设计原则

每个集成层是一个**独立 Go module**，可以独立版本化和发布：

```
github.com/abforce/cortex-ce/cortex-mem-go           # v1.x.x (核心)
github.com/abforce/cortex-ce/cortex-mem-go/eino       # v1.x.x (独立版本)
github.com/abforce/cortex-ce/cortex-mem-go/genkit     # v1.x.x (独立版本)
github.com/abforce/cortex-ce/cortex-mem-go/langchaingo # v1.x.x (独立版本)
```

### 4.2 Eino 集成

```go
// eino/retriever.go
package eino

import (
    "context"
    cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/dto"
    "github.com/cloudwego/eino/schema"
)

// Retriever wraps the Cortex CE client as an Eino Retriever.
type Retriever struct {
    client  cortexmem.Client
    project string
    count   int
}

// NewRetriever creates a new Eino-compatible retriever.
func NewRetriever(client cortexmem.Client, project string, opts ...RetrieverOption) *Retriever {
    r := &Retriever{client: client, project: project, count: 4}
    for _, opt := range opts {
        opt(r)
    }
    return r
}

type RetrieverOption func(*Retriever)

func WithRetrieverCount(n int) RetrieverOption {
    return func(r *Retriever) { r.count = n }
}

// Retrieve implements eino's Retriever interface.
func (r *Retriever) Retrieve(ctx context.Context, query string) ([]*schema.Document, error) {
    experiences, err := r.client.RetrieveExperiences(ctx, dto.NewExperienceRequest(
        query, r.project, dto.WithCount(r.count),
    ))
    if err != nil {
        return nil, err
    }
    docs := make([]*schema.Document, 0, len(experiences))
    for _, exp := range experiences {
        docs = append(docs, &schema.Document{
            Content: exp.Strategy + "\n" + exp.Outcome,
            MetaData: map[string]any{
                "id":            exp.ID,
                "task":          exp.Task,
                "qualityScore":  exp.QualityScore,
                "reuseCondition": exp.ReuseCondition,
            },
        })
    }
    return docs, nil
}
```

### 4.3 LangChainGo 集成

```go
// langchaingo/memory.go
package langchaingo

import (
    "context"
    cortexmem "github.com/abforce/cortex-ce/cortex-mem-go"
    "github.com/abforce/cortex-ce/cortex-mem-go/dto"
    "github.com/tmc/langchaingo/memory"
    "github.com/tmc/langchaigo/schema"
)

// Memory implements langchaingo's memory.Memory interface.
type Memory struct {
    client  cortexmem.Client
    project string
    maxChars int
}

func NewMemory(client cortexmem.Client, project string, opts ...MemoryOption) *Memory {
    m := &Memory{client: client, project: project, maxChars: 4000}
    for _, opt := range opts {
        opt(m)
    }
    return m
}

type MemoryOption func(*Memory)

func WithMemoryMaxChars(n int) MemoryOption {
    return func(m *Memory) { m.maxChars = n }
}

// LoadMemoryVariables loads memory variables from Cortex CE.
func (m *Memory) LoadMemoryVariables(ctx context.Context, _ map[string]any) (map[string]any, error) {
    result, err := m.client.BuildICLPrompt(ctx, dto.NewICLPromptRequest(
        "", m.project, dto.WithMaxChars(m.maxChars),
    ))
    if err != nil {
        return map[string]any{"history": ""}, nil
    }
    return map[string]any{"history": result.Prompt}, nil
}

// SaveContext saves the context to Cortex CE.
func (m *Memory) SaveContext(ctx context.Context, inputValues, outputValues map[string]any) {
    // Defer to the session lifecycle — observations are recorded via RecordObservation
}

// Clear clears the memory.
func (m *Memory) Clear(_ context.Context) error {
    return nil
}

var _ memory.Memory = (*Memory)(nil)
```

### 4.4 Genkit 集成（预留）

```go
// genkit/plugin.go — 预留，待 Genkit Go API 稳定后实现
package genkit

// Plugin provides Cortex CE integration for Google's Genkit (Go).
// Status: placeholder — waiting for Genkit Go API stabilization.
type Plugin struct {
    // Will implement genkit.Retriever when API is finalized
}
```

---

## 5. 测试策略

### 5.1 单元测试

- 使用 `httptest.Server` 模拟后端响应
- 测试所有 Client 方法的成功和失败路径
- 测试重试逻辑
- 测试错误类型判断（IsNotFound, IsConflict, IsRateLimited）

### 5.2 集成测试

- 使用真实的 Cortex CE 后端（或 Docker 容器）
- 测试完整生命周期：start → record → retrieve → end
- 测试 Extraction API

### 5.3 集成层测试

- Eino: mock Eino 接口，验证 Retrieve → Document 转换
- LangChainGo: mock LangChainGo 接口

### 5.4 CI 集成

```yaml
# .github/workflows/go.yml
name: Go SDK
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: '1.22' }
      - run: go test -v -race ./...
      - run: go vet ./...
```

---

## 6. 版本策略

| 包 | 版本 | 说明 |
|---|------|------|
| `cortex-mem-go` (核心) | v1.0.0 | 初始发布 |
| `cortex-mem-go/eino` | v1.0.0 | Eino Retriever |
| `cortex-mem-go/langchaingo` | v1.0.0 | LangChainGo Memory |
| `cortex-mem-go/genkit` | v0.1.0 | 预留（Genkit API 不稳定） |

**语义化版本**：主版本号变更 = 破坏性 API 变更。

---

## 7. 开发计划

### Phase 1: 核心包 (3-4 天)

| 任务 | 天数 | 产出 |
|------|------|------|
| 项目骨架 + go.mod | 0.5 | 项目结构 |
| DTO 包（所有数据类型） | 0.5 | dto/*.go |
| Client 接口 + Option 模式 | 0.5 | client.go, client_option.go |
| HTTP 实现（所有 API 方法） | 1.0 | client.go |
| 错误处理 + 重试逻辑 | 0.5 | error.go |
| 单元测试（httptest） | 1.0 | client_test.go |

### Phase 2: 集成层 (2-3 天)

| 任务 | 天数 | 产出 |
|------|------|------|
| Eino Retriever 集成 | 1.0 | eino/retriever.go |
| LangChainGo Memory 集成 | 1.0 | langchaingo/memory.go |
| Genkit 预留骨架 | 0.5 | genkit/plugin.go |

### Phase 3: Demo 项目 (2-3 天)

| 任务 | 天数 | 产出 |
|------|------|------|
| basic demo | 0.5 | examples/basic/ |
| eino demo | 0.5 | examples/eino/ |
| http-server demo | 1.0 | examples/http-server/ |
| langchaingo demo | 0.5 | examples/langchaingo/ |
| genkit demo | 0.5 | examples/genkit/ (预留) |

### Phase 4: 文档 (1 天)

| 任务 | 天数 | 产出 |
|------|------|------|
| README.md (核心包) | 0.5 | 使用文档 |
| README.md (各集成层) | 0.5 | 各集成层文档 |

### Phase 5: 发布 (0.5 天)

| 任务 | 天数 | 产出 |
|------|------|------|
| go.mod tidy + 最终测试 | 0.25 | 发布就绪 |
| Git tag v1.0.0 | 0.25 | 正式发布 |

**总计**: 9-12 天

---

## 8. 待讨论事项

1. **异步支持**: Go SDK 是否需要支持异步 Capture 操作（goroutine + channel）？
   - 建议：核心包默认同步，由调用方决定是否用 goroutine 包装

2. **Streaming 支持**: 未来 SSE 端点是否需要 SDK 支持？
   - 预留接口，Phase 2 实现

3. **配置文件支持**: 是否支持从 YAML/TOML 文件加载配置？
   - 建议：核心包只提供代码配置，配置文件由用户层处理

4. **指标收集**: 是否集成 OpenTelemetry metrics？
   - 建议：可选 middleware 模式，不强制

---

## 附录 A: API 端点映射

| SDK 方法 | HTTP 方法 | 端点 | Java SDK 等价 |
|----------|----------|------|--------------|
| StartSession | POST | /api/session/start | startSession() |
| RecordObservation | POST | /api/ingest/tool-use | recordObservation() |
| RecordSessionEnd | POST | /api/ingest/session-end | recordSessionEnd() |
| RecordUserPrompt | POST | /api/ingest/user-prompt | recordUserPrompt() |
| RetrieveExperiences | POST | /api/memory/experiences | retrieveExperiences() |
| BuildICLPrompt | POST | /api/memory/icl-prompt | buildICLPrompt() |
| TriggerRefinement | POST | /api/memory/refine | triggerRefinement() |
| SubmitFeedback | POST | /api/memory/feedback | submitFeedback() |
| UpdateObservation | PATCH | /api/memory/observations/{id} | updateObservation() |
| DeleteObservation | DELETE | /api/memory/observations/{id} | deleteObservation() |
| GetQualityDistribution | GET | /api/memory/quality-distribution | getQualityDistribution() |
| HealthCheck | GET | /api/health | healthCheck() |
| GetLatestExtraction | GET | /api/extraction/{template}/latest | getLatestExtraction() |
| GetExtractionHistory | GET | /api/extraction/{template}/history | getExtractionHistory() |
| UpdateSessionUserId | PATCH | /api/session/{sessionId}/user | updateSessionUserId() |

## 附录 B: Demo 项目规划

### Java SDK 参考

Java SDK 的使用示例位于：`examples/cortex-mem-demo`

```
examples/cortex-mem-demo/
├── src/main/java/com/example/cortexmem/
│   ├── CortexMemDemoApplication.java    # Spring Boot 启动类
│   ├── ChatController.java              # 聊天接口（集成 Spring AI）
│   ├── MemoryController.java            # 记忆操作示例
│   ├── SessionLifecycleController.java  # 会话生命周期示例
│   ├── ToolsController.java             # 工具调用 + 记忆捕获
│   ├── ProjectsController.java          # 项目管理
│   └── DemoProperties.java              # 配置属性
└── pom.xml                              # 依赖 cortex-mem-spring-ai
```

### Go SDK Demo 项目规划

Go SDK 需要开发**多个 demo 项目**，覆盖不同使用场景：

```
examples/
├── basic/                    # 纯 SDK 使用（无框架）
│   ├── main.go               # 入口
│   ├── session.go            # 会话生命周期
│   ├── memory.go             # 记忆检索 + ICL prompt
│   └── go.mod
│
├── eino/                     # Eino 框架集成
│   ├── main.go               # 入口
│   ├── retriever.go          # 使用 Eino Retriever
│   ├── chat.go               # Eino Chat + Memory
│   └── go.mod
│
├── genkit/                   # Genkit 框架集成
│   ├── main.go               # 入口
│   ├── flow.go               # Genkit Flow + Memory
│   └── go.mod
│
├── langchaingo/              # LangChainGo 集成
│   ├── main.go               # 入口
│   ├── chain.go              # LLM Chain + Memory
│   └── go.mod
│
└── http-server/              # HTTP 服务示例（类似 Java 的 cortex-mem-demo）
    ├── main.go               # HTTP 服务入口
    ├── handler/
    │   ├── chat.go           # /chat 端点
    │   ├── memory.go         # /memory/* 端点
    │   └── session.go        # /session/* 端点
    └── go.mod
```

### Demo 项目详细说明

| Demo | 目标用户 | 核心演示 | 对标 Java |
|------|---------|---------|-----------|
| **basic** | 所有 Go 开发者 | 纯 SDK 调用，无框架依赖 | — |
| **eino** | Eino 用户 | Retriever 接口集成 | cortex-mem-demo (Spring AI) |
| **genkit** | Genkit 用户 | Plugin 集成 | cortex-mem-demo (Spring AI) |
| **langchaingo** | LangChainGo 用户 | Memory 接口集成 | cortex-mem-demo (Spring AI) |
| **http-server** | Web 服务开发者 | 完整 HTTP API 暴露 | cortex-mem-demo (Spring Boot) |

### Demo 开发优先级

| 优先级 | Demo | 理由 |
|--------|------|------|
| P0 | basic | 所有用户必看，零门槛 |
| P1 | eino | 字节生态，增长快 |
| P1 | http-server | 完整功能展示，对标 Java |
| P2 | langchaingo | 社区稳定，用户基础大 |
| P2 | genkit | Google 背景，Go API 待稳定 |

## 附录 C: Go 惯例参考

**Option 模式**：标准库 `grpc.DialOption`, `http.Client.Transport`, `zap.Option`

**Context 传播**：所有 API 方法第一个参数必须是 `context.Context`

**Error 处理**：
- API 错误返回 `*APIError`，包含状态码和消息
- Capture 操作使用 fire-and-forget（与 Java SDK 一致）
- Retrieval 操作返回错误（调用方需要处理）

**依赖管理**：
- 核心包：`go.mod` 只依赖标准库
- 集成层：`go.mod` 独立，依赖核心包 + 对应框架

---

## 附录 D: 集成框架接口研究（迭代 1）

### Eino (CloudWeGo)

**状态**: 🔍 需要进一步研究

Eino 是字节跳动 CloudWeGo 团队推出的 Go LLM 应用框架。

**已知接口模式**:
- `Retriever` 接口：`Retrieve(ctx context.Context, query string) ([]*schema.Document, error)`
- `Document` 结构：`Content string`, `MetaData map[string]any`
- 支持自定义 Retriever 实现

**待确认**:
- [ ] Eino 的 `Retriever` 接口是否已稳定
- [ ] 是否有 `RetrieverOption` 模式
- [ ] `schema.Document` 的完整字段定义

### Genkit (Google)

**状态**: 🔍 需要进一步研究

Google 的 Genkit 框架已进入 Go 生态。

**已知接口模式**:
- 提供 `Retriever` 和 `Indexer` 接口
- Plugin 系统设计
- 与 Firebase 集成

**待确认**:
- [ ] Genkit Go 的 `Retriever` 接口签名
- [ ] Plugin 注册机制
- [ ] 是否支持自定义 metadata

### LangChainGo

**状态**: 🔍 需要进一步研究

LangChainGo 是 LangChain 的 Go 移植版本。

**已知接口模式**:
- `memory.Memory` 接口：`LoadMemoryVariables`, `SaveContext`, `Clear`
- 支持多种 Memory 实现（Buffer, Summary, Vector）
- 与 Chain/Agent 集成

**待确认**:
- [ ] `memory.Memory` 接口的完整签名
- [ ] 是否支持自定义 metadata
- [ ] 与 VectorStore 的集成方式

### 下一步行动

1. 阅读各框架的源码或文档，获取精确接口定义
2. 更新 Go SDK 设计文档中的集成层代码
3. 如果接口不明确，考虑使用适配器模式隔离变化


---

## 附录 E: Java SDK 与后端 API 差距分析（迭代 1）

### 发现

| 类别 | 后端端点数 | Java SDK 封装 | 差距 |
|------|-----------|--------------|------|
| 总计 | ~50+ | 15 | 35+ 未封装 |

### Java SDK 已封装（与 Go SDK 对齐）

1. ✅ `StartSession` → `POST /api/session/start`
2. ✅ `RecordObservation` → `POST /api/ingest/tool-use`
3. ✅ `RecordSessionEnd` → `POST /api/ingest/session-end`
4. ✅ `RecordUserPrompt` → `POST /api/ingest/user-prompt`
5. ✅ `RetrieveExperiences` → `POST /api/memory/experiences`
6. ✅ `BuildICLPrompt` → `POST /api/memory/icl-prompt`
7. ✅ `TriggerRefinement` → `POST /api/memory/refine`
8. ✅ `SubmitFeedback` → `POST /api/memory/feedback`
9. ✅ `UpdateObservation` → `PATCH /api/memory/observations/{id}`
10. ✅ `DeleteObservation` → `DELETE /api/memory/observations/{id}`
11. ✅ `GetQualityDistribution` → `GET /api/memory/quality-distribution`
12. ✅ `HealthCheck` → `GET /api/health`
13. ✅ `GetLatestExtraction` → `GET /api/extraction/{template}/latest`
14. ✅ `GetExtractionHistory` → `GET /api/extraction/{template}/history`
15. ✅ `UpdateSessionUserId` → `PATCH /api/session/{sessionId}/user`

### 后端有但 Java SDK 未封装的端点

| 端点 | 方法 | 说明 | Go SDK 是否需要封装 |
|------|------|------|-------------------|
| `/api/search` | GET | 语义搜索 | ✅ 需要 |
| `/api/observations` | GET | 获取 observation 列表 | ✅ 需要 |
| `/api/summaries` | GET | 获取摘要列表 | ✅ 需要 |
| `/api/prompts` | GET | 获取 prompt 列表 | ⚠️ 可选 |
| `/api/projects` | GET | 项目列表 | ✅ 需要 |
| `/api/stats` | GET | 统计信息 | ⚠️ 可选 |
| `/api/timeline` | GET | 时间线 | ⚠️ 可选 |
| `/api/modes` | GET/POST | 模式管理 | ⚠️ 可选 |
| `/api/settings` | GET/POST | 设置管理 | ⚠️ 可选 |
| `/api/context/{project}` | POST | 上下文注入 | ⚠️ 可选 |
| `/api/observations/batch` | POST | 批量获取 | ✅ 需要 |
| `/api/sdk-sessions/batch` | POST | 批量会话 | ⚠️ 可选 |
| `/api/register/{project}` | GET | 项目注册状态 | ⚠️ 可选 |
| `/api/register` | POST | 项目注册 | ⚠️ 可选 |
| `/api/inject` | GET | 上下文注入 | ⚠️ 可选 |
| `/api/preview` | GET | 预览 | ⚠️ 可选 |
| `/api/version` | GET | 版本信息 | ✅ 需要 |
| `/api/types` | GET | 类型列表 | ⚠️ 可选 |
| `/api/concepts` | GET | 概念列表 | ⚠️ 可选 |

### 结论

**Go SDK Phase 1 应该只封装核心功能**（与 Java SDK 对齐的 15 个方法），后续迭代再补充其他端点。

**原因**：
1. 核心功能已覆盖主要使用场景
2. 避免过度设计
3. 与 Java SDK 保持一致

### 待办

- [ ] 决定 Go SDK Phase 1 是否包含 Search API（重要功能）
- [ ] 决定是否需要 Observation 列表 API
- [ ] 决定是否需要 Projects API


---

## 附录 F: Search API 详细设计（迭代 2）

### 后端 Search API 分析

**端点**: `GET /api/search`

**参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| project | string | ✅ | - | 项目路径 |
| query | string | ❌ | null | 搜索文本 |
| type | string | ❌ | null | 类型过滤 |
| concept | string | ❌ | null | 概念过滤 |
| source | string | ❌ | null | 源过滤 (V14) |
| limit | int | ❌ | 20 | 结果数量 (最大100) |
| offset | int | ❌ | 0 | 偏移量 (V16) |
| orderBy | string | ❌ | null | 排序（未实现） |

**响应**:
```json
{
  "observations": [...],
  "strategy": "filter|hybrid|tsvector|none",
  "fell_back": true|false,
  "count": 5
}
```

### SearchRequest Go 设计

```go
// dto/search_request.go
package dto

// SearchRequest is a request for searching observations.
type SearchRequest struct {
    Project   string `json:"project"`
    Query     string `json:"query,omitempty"`
    Type      string `json:"type,omitempty"`
    Concept   string `json:"concept,omitempty"`
    Source    string `json:"source,omitempty"`
    Limit     int    `json:"limit,omitempty"`
    Offset    int    `json:"offset,omitempty"`
    OrderBy   string `json:"orderBy,omitempty"`
}

func NewSearchRequest(project string, opts ...SearchOption) SearchRequest {
    r := SearchRequest{Project: project, Limit: 20, Offset: 0}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type SearchOption func(*SearchRequest)

func WithSearchQuery(q string) SearchOption {
    return func(r *SearchRequest) { r.Query = q }
}

func WithSearchSource(s string) SearchOption {
    return func(r *SearchRequest) { r.Source = s }
}

func WithSearchType(t string) SearchOption {
    return func(r *SearchRequest) { r.Type = t }
}

func WithSearchConcept(c string) SearchOption {
    return func(r *SearchRequest) { r.Concept = c }
}

func WithSearchLimit(n int) SearchOption {
    return func(r *SearchRequest) { r.Limit = n }
}

func WithSearchOffset(n int) SearchOption {
    return func(r *SearchRequest) { r.Offset = n }
}
```

### SearchResult Go 设计

```go
// dto/search_result.go
package dto

// SearchResult is the result from the search API.
type SearchResult struct {
    Observations []*Observation `json:"observations"`
    Strategy     string         `json:"strategy"`
    FellBack    bool           `json:"fell_back"`
    Count       int            `json:"count"`
}

// Observation is a simplified observation DTO for search results.
type Observation struct {
    ID            string                 `json:"id"`
    SessionID     string                 `json:"sessionId"`
    ProjectPath   string                 `json:"projectPath"`
    Type         string                 `json:"type"`
    Title        string                 `json:"title,omitempty"`
    Content       string                 `json:"content"`
    Facts         []string               `json:"facts,omitempty"`
    Concepts      []string               `json:"concepts,omitempty"`
    QualityScore  float32                `json:"qualityScore,omitempty"`
    Source        string                 `json:"source,omitempty"`
    ExtractedData map[string]any         `json:"extractedData,omitempty"`
    CreatedAt     time.Time              `json:"createdAt"`
}
```

### Client 接口补充

```go
// 在 Client 接口中添加 Search 方法
type Client interface {
    // ... 其他方法 ...

    // Search performs a semantic search over observations.
    // GET /api/search
    Search(ctx context.Context, req dto.SearchRequest) (*dto.SearchResult, error)
}
```

### 待办

- [ ] 决定 Search API 是否加入 Go SDK Phase 1
- [ ] 决定是否需要 Observation 列表 API
- [ ] 完善 SearchResult 的 Observation 结构


---

## 附录 G: Observation 列表 API 设计（迭代 3）

### 后端 Observations API 分析

**端点**: `GET /api/observations`

**参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| project | string | ❌ | null | 项目过滤 |
| offset | int | ❌ | 0 | 偏移量 |
| limit | int | ❌ | 20 | 结果数量 (最大100) |

**响应**:
```json
{
  "items": [...],
  "hasMore": true,
  "total": 100,
  "offset": 0,
  "limit": 20
}
```

### PagedResponse Go 设计

```go
// dto/paged_response.go
package dto

// PagedResponse is a generic paginated response.
type PagedResponse[T any] struct {
    Items   []T   `json:"items"`
    HasMore bool  `json:"hasMore"`
    Total   int64 `json:"total,omitempty"`
    Offset  int   `json:"offset"`
    Limit   int   `json:"limit"`
}
```

### ObservationsRequest Go 设计

```go
// dto/observations_request.go
package dto

// ObservationsRequest is a request for listing observations.
type ObservationsRequest struct {
    Project string `json:"project,omitempty"`
    Offset  int    `json:"offset,omitempty"`
    Limit   int    `json:"limit,omitempty"`
}

func NewObservationsRequest(opts ...ObservationsOption) ObservationsRequest {
    r := ObservationsRequest{Limit: 20, Offset: 0}
    for _, opt := range opts {
        opt(&r)
    }
    return r
}

type ObservationsOption func(*ObservationsRequest)

func WithObservationsProject(p string) ObservationsOption {
    return func(r *ObservationsRequest) { r.Project = p }
}

func WithObservationsOffset(n int) ObservationsOption {
    return func(r *ObservationsRequest) { r.Offset = n }
}

func WithObservationsLimit(n int) ObservationsOption {
    return func(r *ObservationsRequest) { r.Limit = n }
}
```

### Client 接口补充

```go
// 在 Client 接口中添加 ListObservations 方法
type Client interface {
    // ... 其他方法 ...

    // ListObservations lists observations with pagination.
    // GET /api/observations
    ListObservations(ctx context.Context, req dto.ObservationsRequest) (*dto.PagedResponse[*dto.Observation], error)
}
```

### 待办

- [ ] 决定是否在 Go SDK Phase 1 包含 Search 和 ListObservations
- [ ] 完善 Session/Project 管理 API


---

## 附录 H: Go SDK Phase 1 vs Phase 2 决策（迭代 4）

### Phase 1: 与 Java SDK 完全对齐（必须）

**目标**: Go SDK Phase 1 只封装 Java SDK 已有的 15 个方法

| # | 方法 | 端点 | 优先级 |
|---|------|------|--------|
| 1 | StartSession | POST /api/session/start | P0 |
| 2 | RecordObservation | POST /api/ingest/tool-use | P0 |
| 3 | RecordSessionEnd | POST /api/ingest/session-end | P0 |
| 4 | RecordUserPrompt | POST /api/ingest/user-prompt | P0 |
| 5 | RetrieveExperiences | POST /api/memory/experiences | P0 |
| 6 | BuildICLPrompt | POST /api/memory/icl-prompt | P0 |
| 7 | TriggerRefinement | POST /api/memory/refine | P1 |
| 8 | SubmitFeedback | POST /api/memory/feedback | P1 |
| 9 | UpdateObservation | PATCH /api/memory/observations/{id} | P1 |
| 10 | DeleteObservation | DELETE /api/memory/observations/{id} | P1 |
| 11 | GetQualityDistribution | GET /api/memory/quality-distribution | P1 |
| 12 | HealthCheck | GET /api/health | P0 |
| 13 | GetLatestExtraction | GET /api/extraction/{template}/latest | P1 |
| 14 | GetExtractionHistory | GET /api/extraction/{template}/history | P1 |
| 15 | UpdateSessionUserId | PATCH /api/session/{sessionId}/user | P1 |

### Phase 2: 重要扩展（推荐）

| # | 方法 | 端点 | 理由 |
|---|------|------|------|
| 16 | Search | GET /api/search | 语义搜索是核心功能 |
| 17 | ListObservations | GET /api/observations | 分页列表，必备 |
| 18 | GetVersion | GET /api/version | 版本检查 |

### Phase 3: 高级功能（可选）

| # | 方法 | 端点 | 说明 |
|---|------|------|------|
| 19 | GetProjects | GET /api/projects | 项目管理 |
| 20 | GetSummaries | GET /api/summaries | 摘要列表 |
| 21 | GetModes | GET/POST /api/modes | 模式管理 |
| 22 | GetSettings | GET /api/settings | 设置管理 |
| 23 | ContextInject | POST /api/context/{project} | 上下文注入 |

### 决策建议

**Go SDK Phase 1**: 15 个方法（与 Java SDK 对齐）
**Go SDK Phase 2**: +3 个方法（Search, ListObservations, GetVersion）

### 更新的开发计划

| Phase | 天数 | 产出 |
|-------|------|------|
| Phase 1: 核心包（15 方法） | 3-4 | 核心功能 |
| Phase 2: 扩展（+3 方法） | 1 | Search, List, Version |
| Phase 3: Demo 项目 | 2-3 | 5 个 demo |
| Phase 4: 文档 | 1 | README |
| Phase 5: 发布 | 0.5 | v1.0.0 |

**总计**: 7.5-9.5 天（比原计划更聚焦）

