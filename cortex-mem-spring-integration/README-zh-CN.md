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
| **173 个单元测试** | 覆盖客户端、Advisor、工具和自动配置各层（120 客户端 + 46 spring-ai + 7 starter） |

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
| `retry.max-attempts` | int | `3` | 捕获调用的重试次数 |
| `retry.backoff` | Duration | `500ms` | 重试间隔基数 |

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
- **优雅降级**：检索失败返回空列表；ICL 失败返回空提示。
- **条件 Bean**：Advisor、AOP 切面和健康检查指示器仅在其依赖（Spring AI、AOP、Actuator）在 classpath 上时注册。
- **Spring AI 1.1**：使用 `CallAdvisor` / `StreamAdvisor` 和 `ChatClientRequest`（非旧版 `CallAroundAdvisor`）。

## 相关链接

- [English version](./README.md)

## 许可证

与父项目 BlueCortexCE 相同。
