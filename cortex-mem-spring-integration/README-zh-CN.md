> English version: [README.md](./README.md)

# Cortex Memory Spring Integration

**Cortex CE** 记忆系统的 Spring Boot / Spring AI 一体化集成库。无需大量代码修改，即可为 AI 代理添加持久化上下文和基于经验检索（ExpRAG）。

## 概述

Cortex CE 是一个记忆后端，用于存储代理观察结果、生成摘要并提供语义检索。本库使 Spring AI 应用能够：

- **捕获** — 将工具执行、用户提示和会话事件记录到记忆系统
- **检索** — 获取相关的历史经验用于上下文学习（ICL）
- **演进** — 触发记忆优化并提交质量反馈

## 特性

| 特性 | 说明 |
|------|------|
| **一行集成** | `@EnableCortexMem` + 配置属性 |
| **即发即忘捕获** | 非阻塞、容错的观察记录 |
| **Spring AI Advisor** | 自动将 ICL 上下文注入 ChatClient 调用 |
| **CortexMemoryTools** | 按需记忆检索工具（`searchMemories`、`getMemoryContext`）—— 可选启用 |
| **@Tool 自动捕获** | AOP 切面拦截 `@Tool` 方法并记录其执行 |
| **会话上下文** | 基于 ThreadLocal 的会话和项目作用域 |
| **健康检查指示器** | 用于监控记忆后端的 Actuator 集成 |
| **186 个单元测试** | 覆盖客户端、Advisor、工具和自动配置各层（133 客户端 + 46 spring-ai + 7 starter） |

## 环境要求

- **Java 21+**
- **Spring Boot 3.3.x**
- **Spring AI 1.1.x**（可选，用于 Advisor 集成）
- **Cortex CE 后端** 运行中（默认：`http://localhost:37777`）

## 安装

推荐使用 [JitPack](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE) 获取预构建产物。访问 JitPack 页面查看可用版本（发布标签、分支名或提交哈希）。

### Maven（JitPack）

在 `pom.xml` 中添加 JitPack 仓库和依赖：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- 完整集成：cortex-mem-starter（客户端 + Spring AI + 自动配置） -->
    <dependency>
        <groupId>com.github.Blueforce-Tech-Inc</groupId>
        <artifactId>BlueCortexCE</artifactId>
        <version>Tag</version>
    </dependency>
</dependencies>
```

将 `Tag` 替换为发布标签（如 `v1.0.0`）、分支名（如 `main`）或提交哈希。参见 [JitPack 构建历史](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE) 查看可用版本。使用示例参见 [examples/cortex-mem-demo](../examples/cortex-mem-demo)。

### Maven（本地构建）

从源码构建并安装：

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

然后添加本地依赖：

```xml
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle（Kotlin DSL）

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.Blueforce-Tech-Inc:BlueCortexCE:Tag")
}
```

将 `Tag` 替换为发布标签、分支名或提交哈希（参见 [JitPack](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE)）。

## 快速开始

### 第一步：添加依赖（上文）

### 第二步：配置

```yaml
# application.yml
cortex:
  mem:
    base-url: http://localhost:37777
    project-path: /path/to/your/project
```

### 第三步：启用

```java
@SpringBootApplication
@EnableCortexMem
public class MyAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }
}
```

### 第四步：使用 ChatClient（记忆增强）

当 `cortex-mem-starter` 在 classpath 中且使用 Spring AI 时，`CortexMemoryAdvisor` 会自动配置。注入并添加到 ChatClient：

```java
@RestController
class AiController {
    private final ChatClient chatClient;

    public AiController(ChatClient.Builder builder, CortexMemoryAdvisor advisor) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant.")
            .defaultAdvisors(advisor)
            .build();
    }

    @GetMapping("/chat")
    String chat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

每次请求自动**检索**相关经验（ICL 上下文注入）。用户提示的**自动捕获**仅在 `capture-user-prompt-enabled=true` 且会话 ID 可用时生效（见下文）。

**关于用户提示捕获**，通过以下任一方式提供会话 ID。否则检索功能正常但不会记录提示：

会话 ID 解析（与 Spring AI `ChatMemory.CONVERSATION_ID` 对齐）。当 `context-bridge-enabled=true`（默认）时，`CortexSessionContextBridgeAdvisor` 自动激活上下文：

1. **Spring AI 会话 ID** — 通过 `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))` 设置
2. **CortexSessionContext** — 用 `begin`/`end` 包装时的后备方案

```java
// 选项 A：Spring AI 会话 ID（与 MessageChatMemoryAdvisor 对齐）
chatClient.prompt()
    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
    .user(message)
    .call()
    .content();

// 选项 B：CortexSessionContext
CortexSessionContext.begin(sessionId, projectPath);
try {
    CortexSessionContext.incrementAndGetPromptNumber();
    return chatClient.prompt().user(message).call().content();
} finally { CortexSessionContext.end(); }
```

## 配置

所有属性都在 `cortex.mem` 下：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `base-url` | String | `http://localhost:37777` | Cortex CE 后端 URL |
| `project-path` | String | — | 项目路径（用于记忆隔离） |
| `connect-timeout` | Duration | `10s` | HTTP 连接超时 |
| `read-timeout` | Duration | `30s` | HTTP 读取超时 |
| `default-experience-count` | int | `4` | 每次检索的最大经验数 |
| `capture-enabled` | boolean | `true` | 启用 @Tool 观察捕获（CortexToolAspect） |
| `capture-user-prompt-enabled` | boolean | `true` | 启用用户提示自动捕获（CortexMemoryAdvisor）。与 capture-enabled 独立。 |
| `retrieval-enabled` | boolean | `true` | 启用记忆检索 |
| `memory-tools-enabled` | boolean | `false` | 创建 CortexMemoryTools bean。工具不会自动注入——需通过 `ChatClient.defaultTools(cortexMemoryTools)` 添加。 |
| `context-bridge-enabled` | boolean | `true` | 创建 CortexSessionContextBridgeAdvisor。当设置 CONVERSATION_ID 时，自动 begin/end CortexSessionContext，使 @Tool 捕获无需手动管理上下文。 |
| `retry.max-attempts` | int | `3` | 会重试的那 10 个方法的总**尝试**次数——范围见下方「重试范围」 |
| `retry.backoff` | Duration | `500ms` | 重试间隔基数（线性 `backoff × attempt`，±25% 抖动） |

### 环境变量

```bash
CORTEX_MEM_BASE_URL=http://localhost:37777
CORTEX_MEM_PROJECT_PATH=/my/project
CORTEX_MEM_CAPTURE_ENABLED=true
CORTEX_MEM_CAPTURE_USER_PROMPT_ENABLED=true
```

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                   Your Spring AI Application                      │
├──────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              Cortex Memory Integration Layer               │  │
│  │  ┌─────────────────────┐    ┌──────────────────────────┐  │  │
│  │  │ CortexToolAspect     │    │ CortexMemoryAdvisor        │  │  │
│  │  │ (@Tool capture)      │    │ (ICL + user-prompt cap)  │  │  │
│  │  └──────────┬──────────┘    └────────────┬─────────────┘  │  │
│  │             │                              │                │  │
│  │             ▼                              ▼                │  │
│  │  ┌──────────────────────────────────────────────────────┐ │  │
│  │  │              CortexMemClient (REST Client)            │ │  │
│  │  └─────────────────────────┬────────────────────────────┘ │  │
│  └────────────────────────────┼──────────────────────────────┘  │
│                                │ HTTP                             │
├────────────────────────────────┼──────────────────────────────────┤
│                    ChatClient  │                                  │
└────────────────────────────────┼──────────────────────────────────┘
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              Cortex CE Backend (Port 37777)                      │
│  Ingest API │ Memory API (ExpRAG, ICL) │ Refinement             │
└─────────────────────────────────────────────────────────────────┘
```

## 使用模式

### 1. 按需记忆工具（CortexMemoryTools）

当 `memory-tools-enabled=true` 时，会创建一个 `CortexMemoryTools` bean。将其添加到 ChatClient 以实现按需检索——由 AI 决定何时调用 `searchMemories` 或 `getMemoryContext`。

**不会自动注入**：默认情况下工具不会添加到 ChatClient。必须显式调用 `defaultTools(cortexMemoryTools)`。

```yaml
# application.yml
cortex:
  mem:
    memory-tools-enabled: true
```

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             CortexMemoryAdvisor advisor,
                             CortexMemoryTools memoryTools) {
    return builder
        .defaultAdvisors(advisor)
        .defaultTools(memoryTools)  // 显式 opt-in
        .build();
}
```

可用工具：
- `searchMemories(task, count?)` — 搜索相关的过去经验
- `getMemoryContext(task)` — 获取 ICL 格式的记忆提示

### 2. 自动 @Tool 捕获（AOP）

当 `capture-enabled=true` 且 Spring AOP 在 classpath 上时，任何带 `@Tool` 注解的方法都会被拦截，其输入/输出会被发送到记忆后端。

**重要**： `@Tool` 方法必须在**单独的 `@Component`** bean 中。同类自调用（从同一类中调用 `this.readFile()`）会绕过 Spring AOP，不会被捕获。

```java
@Component
class MyTools {
    @Tool(description = "Read a file")
    public String readFile(String path) {
        return Files.readString(Path.of(path));
    }
}
```

确保会话上下文处于活跃状态：

```java
CortexSessionContext.begin(sessionId, projectPath);
try {
    // ... 使用工具运行代理
} finally {
    CortexSessionContext.end();
}
```

### 3. 手动捕获

```java
@Service
class MyAgentService {
    private final ObservationCaptureService captureService;

    public void recordToolUse(String toolName, Map<String, Object> input, Object output) {
        captureService.recordToolObservation(ObservationRequest.builder()
            .sessionId(CortexSessionContext.getSessionId())
            .projectPath(CortexSessionContext.getProjectPath())
            .toolName(toolName)
            .toolInput(input)
            .toolResponse(Map.of("result", output))
            .promptNumber(CortexSessionContext.getPromptNumber())
            .build());
    }

    public void onSessionEnd() {
        captureService.recordSessionEnd(SessionEndRequest.builder()
            .sessionId(CortexSessionContext.getSessionId())
            .projectPath(CortexSessionContext.getProjectPath())
            .build());
    }
}
```

### 4. 手动检索（不使用 Advisor）

```java
@Service
class MyAgentService {
    private final MemoryRetrievalService retrievalService;

    public String processWithMemory(String task) {
        List<Experience> experiences = retrievalService
            .retrieveExperiences(task, "/my/project", 4);

        String iclPrompt = retrievalService.buildICLPrompt(task, "/my/project");

        return chatClient.prompt()
            .system(s -> s.text(iclPrompt))
            .user(task)
            .call()
            .content();
    }
}
```

### 5. 直接客户端访问

```java
@Component
class CustomService {
    private final CortexMemClient client;

    public void triggerRefinement() {
        client.triggerRefinement("/my/project");
    }

    public QualityDistribution stats() {
        return client.getQualityDistribution("/my/project");
    }

    public void feedback(String observationId, String feedbackType) {
        client.submitFeedback(observationId, feedbackType, "Very helpful");
    }
}
```

### 空更新会被拒绝

`updateObservation(id, update)` 在 `update` 未设置任何字段时抛 `IllegalArgumentException`，且不会发出任何请求。Go、Python、JS 三家规则与消息完全相同：

```
at least one field must be provided for update
```

这一点很重要：不设置任何字段的 PATCH 在 wire 上是一次静默 no-op。若没有这道检查，调用方用用户输入拼出一个空更新后会看到调用成功，却无法得知其实什么都没写入。

### 必填参数在客户端校验

下表中的参数都必须非空白。客户端抛出 `IllegalArgumentException` 且不发出任何请求。Go、Python、JS 三家 SDK 强制的是完全相同的一组规则。

| 方法 | 必填参数 |
|------|----------|
| `startSession` | `request.sessionId()`、`request.projectPath()` |
| `updateSessionUserId` | `sessionId`、`userId` |
| `recordObservation` | `request.sessionId()`、`request.projectPath()`、`request.toolName()` |
| `recordSessionEnd` | `request.sessionId()`、`request.projectPath()` |
| `recordUserPrompt` | `request.sessionId()`、`request.projectPath()`、`request.promptText()` |
| `retrieveExperiences` | `request.task()` |
| `buildICLPrompt` | `request.task()` |
| `search` | `request.project()` |
| `getObservation` | `observationId` |
| `getObservationsByIds` | `ids`——非空、至多 100 个、元素不得为空 |
| `triggerRefinement` | `projectPath` |
| `submitFeedback` | `observationId`、`feedbackType` |
| `updateObservation` | `observationId`，外加至少一个待修改字段 |
| `deleteObservation` | `observationId` |
| `getQualityDistribution` | `projectPath` |
| `triggerExtraction` | `projectPath` |
| `getLatestExtraction` | `projectPath`、`templateName` |
| `getExtractionHistory` | `projectPath`、`templateName`；`limit` 不得为负 |

capture 与检索类方法接收一个请求 record 并通过其访问器读取参数；管理与抽取类方法则
按位置传参。

**`retrieveExperiences` / `buildICLPrompt` 的空 `project` 是这张表里唯一值得
单独说明的缺口。** 表格本身准确——这两个方法都不校验 `project`，而 `search`
校验——但它记录的是「校验了什么」，不是「会发生什么」，而这里的差别很关键。
`POST /api/memory/experiences` 与 `POST /api/memory/icl-prompt` 会把该值直接
传入仓储查询，**没有任何跨全部项目的分支**，因此缺失或为空的项目匹配不到内容，
返回 `200` 加空结果，而**不是**报错。活体实测：省略 `project`、传 `""`、
传不存在的路径三者都返回 `200 []`，而真实项目路径才会返回经验。
这一点在本 SDK 尤其要紧：`CortexMemoryTools.searchMemories` 与
`getMemoryContext` 的项目来自 `CortexSessionContext`，取不到时会回落到配置的
`cortex.mem.project-path` 且**不打日志**（见下方 `StreamAdvisor` 一节）。
于是「项目未设置」与「该项目确实没有记忆」无法区分。

这些检查不是装饰。其中三个 capture 方法最能说明问题：`recordObservation` 是
fire-and-forget，会吞掉后端返回的一切——工具名为空时后端返回
`400 Missing required field: tool_name`，客户端记一条日志后正常返回，调用方于是认为
观测已记录，而服务器刚刚拒绝了它。项目路径为空则更隐蔽，因为后端**接受**它：记录会以
空项目路径入队，随后不出现在任何按项目过滤的查询里，全程没有任何错误提示。

`search` 属于同一类隐患：客户端总会发送 `project`，而 `GET /api/search?project=` 会
返回 `200` 加一个空结果集，因此漏传参数的调用方读到的是「没有匹配」而不是「你的调用
不合法」。

`listObservations` 与无参的 getter（`getStats`、`getProjects`、`getModes`、
`getSettings`、`getVersion`、`healthCheck`）没有必填参数；`getStats` 接受一个可选的
项目过滤条件。

这句说的是**必填参数**，不是**影响范围**——而在 `listObservations` 上两者指向相反
方向。`listObservations` 是唯一一个项目过滤会**放宽**而非**清空**的检索方法：省略 `project`
（传空串也一样，四家 SDK 都会把它转成省略），SDK 就不会发出 `project` 参数，
后端随即返回**该实例上全部项目**的观测。活体实测：省略 `project` 的
`GET /api/observations` 在一个 100 条的单页里返回了 **16 个不同项目**的数据。
而**直连 HTTP** 带上字面量 `?project=` 则是相反的情形、会返回空——因为仓储查询判的是
`IS NULL` 而非空串；绕过 SDK 直连前值得知道这一点。

因此它与上面那两个 ICL 端点恰好互为镜像：那里空项目**静默清空**结果，这里空项目
**静默放宽**结果。在多租户部署中，这是**跨租户数据外泄**而不是「少给了一条答案」，
而且没有任何客户端校验能拦住它——因为这个调用本身是合法的。

## Wire 格式

SDK 建模的是后端**真实的** wire 形态，而字段名并不总能提示这一点：

- **列表列以 JSON 编码的字符串到达。** `facts`、`concepts`、`files_read`、
  `files_modified` 是 JSONB 列，后端为 TypeScript WebUI 把它们序列化成**字符串**，
  因此线上的一条 observation 到达时是 `concepts: "[\"allergy\",\"peanut\"]"` 而不是
  JSON 数组。客户端两种形态都能解码，故 `ObservationResponse.concepts()` 始终是真正的
  `List<String>`。
- **`refinedFromIds` 是普通 String，不是列表。** `mem_observations.refined_from_ids`
  是存放**逗号分隔** UUID（`"uuid-1,uuid-2"`）的 `TEXT` 列，不是 JSONB 列表——后端用
  `,` 拼接 ID 且从不 JSON 编码。因此它暴露为 `String refinedFromIds` 而非
  `List<String>`。
- **命名。** 当 wire 名与 Java 名不同时，record 组件用 `@JsonProperty` 标注：
  `content_session_id` → `sessionId`、`project` → `projectPath`、`extractedData` 为
  camelCase，其余大多是 snake_case。

Go、Python、Java 三家的解码方式一致；同一张表在各自语言风格下的版本见 Go 与 Python
SDK 的 README。

## 错误处理

SDK 的错误行为**刻意不统一**，因为两类调用方需要的东西不同。写 `try`/`catch` 之前，
先弄清某个方法属于哪一类。

**向外抛出**——这些方法抛 `RuntimeException`，且异常消息携带后端自己的
`{"error": "..."}` 文本，因此失败永远不会被误认为空结果：

| 方法 | 之所以抛出 |
|------|-----------|
| `listObservations` | 空页与「查询确实没有匹配」无法区分 |
| `getObservationsByIds` | 空列表与「这些 id 都不存在」无法区分 |
| `getProjects` | 空项目列表与「该后端还没有任何项目」无法区分 |
| `startSession`、`updateObservation`、`deleteObservation`、`submitFeedback` 等 | 调用方必须知道写入是否真的发生了 |

**优雅降级**——这些方法返回空值或全零结果并记录 warning，因为 Spring AI 集成在
`@Tool` 方法与自动配置的健康指示器中调用它们，记忆后端故障不能打断 agent 的对话轮次、
也不能拖垮整个应用：

| 方法 | 失败时返回 |
|------|-----------|
| `retrieveExperiences` | 空列表 |
| `buildICLPrompt` | `ICLPromptResult("", 0)` |
| `getQualityDistribution` | 全零 `QualityDistribution` |
| `healthCheck` | `false` |

**部分降级**——这些方法在返回值里**标记**了降级，调用方据此可判定：
`search` 与 `getStats` 附带 `"fell_back": true` 和 `"error"` 键，`getVersion` 报
`"unknown"`，`getSettings` 附带 `"error"` 键，`getModes` 返回与 `/api/modes` 相同的键
（`observation_types` / `observation_concepts` 为空）并附带 `"error"` 键。`healthCheck`
列在上表，是因为它返回的 `false` 本身就是信号。

```java
try {
    PagedObservationResponse page = client.listObservations(req);
    // page.items() 为空只可能是因为查询确实没有匹配
} catch (RuntimeException e) {
    // e.getMessage() 携带后端给出的原因，例如 "project is required"
    log.warn("memory listing failed", e);
}
```

## 模块

| 模块 | 说明 |
|------|------|
| **cortex-mem-client** | REST 客户端、DTO、配置属性。无 Spring AI 依赖。 |
| **cortex-mem-spring-ai** | Advisor、捕获/检索服务、AOP 切面。依赖 Spring AI 和 client。 |
| **cortex-mem-starter** | Spring Boot 自动配置、`@EnableCortexMem`、健康检查指示器。依赖上述两个模块。 |

## Phase 3：多用户与结构化提取

### userId 支持

创建带可选 `userId` 的会话以实现多用户记忆隔离：

```java
// 带 userId 的会话
Map<String, Object> result = client.startSession(SessionStartRequest.builder()
    .sessionId("conv-123")
    .projectPath("/my-project")
    .userId("alice")  // Phase 3：多用户标识符
    .build());

// 更新现有会话的 userId（延迟绑定）
client.updateSessionUserId("conv-123", "bob");
```

### 结构化提取查询

按模板名查询 LLM 提取的结构化数据：

```java
// 获取用户的最新提取结果
ExtractionResponse extraction = client.getLatestExtraction(
    "/my-project", "user_preference", "alice");
// 返回：ExtractionResponse { status: "ok", template: "user_preference",
//   sessionId: "abc123", extractedData: { preferences: [...] }, createdAt: 1234567890,
//   observationId: "uuid", message: null }
// 使用 extraction.isFound() 检查提取结果是否存在

// 获取提取历史（所有快照）
List<Map<String, Object>> history = client.getExtractionHistory(
    "/my-project", "user_preference", "alice", 10);

// 手动触发提取
client.triggerExtraction("/my-project");
```

### 带 userId 的 ICL

构建限定于特定用户提取数据的 ICL 提示：

```java
ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("推荐手机")
    .project("/my-project")
    .userId("alice")  // Phase 3：用户作用域上下文
    .maxChars(2000)
    .build());
```

### 带 userId 的 Experiences

```java
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("推荐手机")
        .project("/my-project")
        .userId("alice")  // Phase 3：用户过滤
        .count(4)
        .build());
```

## V14 功能

### 来源归属

使用 `source` 字段跟踪每个观察结果的来源：

```java
client.recordObservation(ObservationRequest.builder()
    .sessionId(sessionId)
    .projectPath(projectPath)
    .toolName("search")
    .toolInput(Map.of("query", "Spring AI memory"))
    .source("tool_result")  // V14：来源归属
    .build());
```

### 结构化数据提取

使用 `extractedData` 存储结构化键值数据：

```java
client.recordObservation(ObservationRequest.builder()
    .sessionId(sessionId)
    .projectPath(projectPath)
    .toolName("user_preference")
    .source("user_statement")
    .extractedData(Map.of(  // V14：结构化键值数据
        "price_range", "3000",
        "brands", List.of("sony", "bose"),
        "category", "headphones"
    ))
    .build());
```

### 自适应截断（maxChars）

根据模型的上下文窗口大小控制 ICL 提示大小：

```java
// 根据模型的上下文窗口配置
// 128K 模型：8000-12000 字符
// 32K 模型：4000-6000 字符
// 8K 模型：2000-3000 字符

ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("fix login bug")
    .project("/my-project")
    .maxChars(4000)  // V14：自适应截断
    .build());
```

### 来源和概念过滤

按来源或必需概念过滤经验：

```java
// 按来源归属过滤
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("fix bug")
        .project("/my-project")
        .source("llm_inference")  // V14：来源过滤
        .build());

// 按必需概念过滤
List<Experience> verified = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("best approach")
        .project("/my-project")
        .requiredConcepts(List.of("verified", "tested"))  // V14：概念过滤
        .build());
```

### 记忆管理工具

当 AI 使用 CortexMemoryTools 时更新或删除记忆：

```java
// updateMemory 工具 - AI 可以纠正错误或标记重要记忆
// deleteMemory 工具 - AI 可以删除过时或不相关的记忆
```

这些工具在 `memory-tools-enabled=true` 且添加到 ChatClient 时可用。

## 构建与示例

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

完整工作示例（聊天、工具、会话生命周期、E2E 测试）参见本仓库中的 `examples/cortex-mem-demo`。

## 后端 API 对齐

客户端调用以下 Cortex CE 端点：

| 客户端方法 | 后端端点 | V14 | Phase 3 |
|------------|---------|-----|---------|
| `startSession()` | `POST /api/session/start` | | ✅ userId |
| `updateSessionUserId()` | `PATCH /api/session/{id}/user` | | ✅ 新增 |
| `recordObservation()` | `POST /api/ingest/tool-use` | ✅ source, extractedData | |
| `recordSessionEnd()` | `POST /api/ingest/session-end` | | |
| `recordUserPrompt()` | `POST /api/ingest/user-prompt` | | |
| `retrieveExperiences()` | `POST /api/memory/experiences` | ✅ source, requiredConcepts | ✅ userId |
| `buildICLPrompt()` | `POST /api/memory/icl-prompt` | ✅ maxChars | ✅ userId |
| `triggerRefinement()` | `POST /api/memory/refine` | | |
| `submitFeedback()` | `POST /api/memory/feedback` | | |
| `updateObservation()` | `PATCH /api/memory/observations/{id}` | ✅ V14 | |
| `deleteObservation()` | `DELETE /api/memory/observations/{id}` | ✅ V14 | |
| `getQualityDistribution()` | `GET /api/memory/quality-distribution` | | |
| `getLatestExtraction()` | `GET /api/extraction/{template}/latest` | | ✅ 新增 |
| `getExtractionHistory()` | `GET /api/extraction/{template}/history` | | ✅ 新增 |
| `triggerExtraction()` | `POST /api/extraction/run` | | ✅ 新增 |
| `healthCheck()` | `GET /api/health` | | |
| `search()` | `GET /api/search` | ✅ source | |
| `listObservations()` | `GET /api/observations` | | |
| `getObservation()` | `POST /api/observations/batch` | | |
| `getObservationsByIds()` | `POST /api/observations/batch` | | |
| `getVersion()` | `GET /api/version` | | |
| `getProjects()` | `GET /api/projects` | | |
| `getStats()` | `GET /api/stats` | | |
| `getModes()` | `GET /api/modes` | | |
| `getSettings()` | `GET /api/settings` | | |

## 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 工具调用未被捕获 | `@Tool` 通过自调用触发 | 将 `@Tool` 移到单独的 `@Component` 中并注入 |
| 用户提示未被捕获 | 未提供会话 ID | 使用选项 A（会话 ID）或选项 B（CortexSessionContext） |
| 无 ICL 上下文注入 | 后端不可达或 `retrieval-enabled=false` | 检查 `base-url`，确保后端运行中 |
| Advisor 未注册 | Spring AI 不在 classpath | 添加 `spring-ai-starter-model-openai`（或类似） |
| 记忆工具不可用 | `memory-tools-enabled=false` 或未添加到 ChatClient | 设置 `memory-tools-enabled: true` 并调用 `defaultTools(cortexMemoryTools)` |

## 设计笔记

- **即发即忘捕获**：捕获操作记录失败但不抛出异常，因此 AI 管道永不被阻塞。
- **优雅降级**：大多数读操作在后端失败时不抛异常，而是返回一个合成值。这是有意设计（记忆层不能拖垮 AI 管道），
  但代价是：**除非该方法标记了失败**，否则「后端不可达」和「确实没有数据」看起来完全一样。
  有三个读方法是例外，它们直接向上抛出——`listObservations`、`getObservationsByIds` 与
  `getProjects`——因为在这三个方法上，空结果与「查询确实没有匹配」无法区分。

  | 方法 | 后端失败时的行为 | 是否标记失败 |
  |------|-----------------|------------|
  | `retrieveExperiences` | 返回空列表 | 否 |
  | `buildICLPrompt` | 返回 prompt `""`、count `0` | 否 |
  | `getQualityDistribution` | 各项计数均为 `0` | 否 |
  | `healthCheck` | 返回 `false` | 是（`false` 对 `true`） |
  | `getVersion` | 返回 `{"service": "unknown", "version": "unknown"}` | 是 |
  | `search` | 返回 `{"observations": [], "strategy": "none", "fell_back": true, "count": 0, "error": "<message>"}` | 是 |
  | `getStats` | 返回 `{"error": "<message>", "fell_back": true}` | 是 |
  | `getSettings` | 返回 `{"settings": {}, "error": "<message>"}` | 是 |
  | `getModes` | 返回与 `/api/modes` 相同的键（`observation_types` / `observation_concepts` 为空）并附带 `"error"` 键 | 是 |

  前三行与「确实为空」无法区分，因此需要区分的调用方必须先用 `healthCheck()`（或
  `getVersion()` 并检查 `version` 是否为 `unknown`）再信任空结果。`getModes` 与
  `getProjects` 分开列出，是因为只有前者会降级。

  写入与变更类方法不降级，直接向上抛出：`startSession`、`updateSessionUserId`、`submitFeedback`、
  `updateObservation`、`deleteObservation`、`triggerRefinement`、`triggerExtraction`，
  以及捕获类调用 `recordObservation`、`recordUserPrompt`、`recordSessionEnd`。
- **重试范围既不是「全部」也不只是「捕获」**：25 个 public 方法中有 10 个会重试，退避为线性
  （`retry.backoff × attempt`，±25% 抖动）。其中五个捕获/异步调用在耗尽尝试后会**静默吞掉**错误
  （`recordObservation`、`recordUserPrompt`、`recordSessionEnd`、`triggerRefinement`、
  `triggerExtraction`）；三个变更类方法重试后**向上抛出**（`submitFeedback`、
  `updateObservation`、`deleteObservation`）；而**唯二会重试的读方法**是
  `getLatestExtraction` 与 `getExtractionHistory`。其余 15 个方法完全不会重试——包括其他所有读方法：
  `search`、`listObservations`、`getObservation`、`getObservationsByIds`、`getProjects`、
  `getStats`、`getModes`、`getSettings`、`getVersion`、`healthCheck`、`getQualityDistribution`、
  `retrieveExperiences`、`buildICLPrompt`，以及 `startSession` 与 `updateSessionUserId`。
  也就是说，后端出现瞬时错误时，本 SDK 大部分 API 会在**第一次尝试**就把错误抛给你。
- 另外三家的重试**只覆盖捕获路径**，因此同一个配置项在那边管到的调用更少：这两个抽取读方法的
  重试是 Java 独有的。为它们写「避免重试」补丁时，只需在 Java 侧做保护。
- **自动路径不做按用户隔离**。若一个项目要服务多个用户，请注意：注入 Agent 的记忆是**项目级、
  而非用户级**。后端本身**是支持**限定的——`POST /api/memory/experiences` 带 `userId`
  会只返回该用户的经验，对其他人返回空列表（实测：alice 名下一条观测，alice 查到 1 条、
  bob 查到 0 条）——而 `ICLPromptRequest` / `ExperienceRequest` 都带 `userId` 字段，
  `DefaultMemoryRetrievalService` 也**已经实现并透传**。但 `CortexMemoryAdvisor` 与两个
  `@Tool` 读方法都是从 `CortexSessionContext` 构造请求的，而那个类里只有 `sessionId`、
  `projectPath` 和一个提示计数器——**没有 `userId` 字段，也没有任何接受它的 `begin()` 重载**。
  因此自动路径在结构上**无法**按用户限定，同一项目里的每个 Agent 都会拿到相同的 ICL
  上下文，其中包含**记在别人名下的偏好**。自行调用 `client.buildICLPrompt(...)` 并设置
  `userId` 是可行的——那是可用的绕行方式。已记录为 P2-13。
- **条件 Bean**：Advisor、AOP 切面和健康检查指示器仅在其依赖（Spring AI、AOP、Actuator）在 classpath 上时注册。
- **Spring AI 1.1**：使用 `CallAdvisor` / `StreamAdvisor` 和 `ChatClientRequest`（非旧版 `CallAroundAdvisor`）。
- **无响应体大小上限**：客户端使用 Spring 6 的 `RestClient`（底层为 `java.net.http.HttpClient`），它会一次性反序列化整个响应体，因此超大响应只受堆内存约束。Go 与 JS SDK 把上限设为 10 MiB 并抛出明确错误，本 SDK 没有这样做。在检索或批量列出大量 observation 时，请把 `limit` 控制得小一些。
- **流式（`StreamAdvisor`）不做会话传播**：`CortexSessionContext` 是普通 `ThreadLocal`，而流式模型调用运行在不同于 advisor 调用线程的线程上。三个后果：流式下 `@Tool` 自动捕获被**静默跳过**；调用线程上的会话上下文不会被释放（该线程池线程后续处理的请求可能被归到上一个会话）；以及两个 `@Tool` **读**方法——`searchMemories` 与 `getMemoryContext`——会回落到配置的 `cortex.mem.project-path`，而不是当前会话真实所属的项目，**且不打任何日志**。第三条值得展开，因为两种结果都不会自我暴露：若配置了 `project-path`，Agent 会被喂进**另一个项目**的记忆并当成当前对话的历史；若未配置，工具会发出空项目，后端返回 `200` 加空列表，工具于是报告「No relevant past experiences found」——与该项目确实没有历史**无法区分**。活体实测：`POST /api/memory/experiences` 传 `project: ""` 返回 `200 []`，同一请求传真实项目路径返回 5 条经验。这影响 `CortexSessionContextBridgeAdvisor` 与 `ChatClient.stream()` 的组合。若需要 `@Tool` 自动捕获，请改用同步的 `.call()`——它完全运行在调用线程上，不受影响。已记录为 [`docs/drafts/backend-review-findings.md`](../docs/drafts/backend-review-findings.md) 的 P1-1。

## 相关链接

- [English version](./README.md)

## 许可证

与父项目 BlueCortexCE 相同。
