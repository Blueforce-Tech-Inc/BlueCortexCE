# Spring AI 集成 Cortex CE 记忆系统 - 规划文档

> **版本**: 1.0  
> **创建日期**: 2026-03-18  
> **状态**: 规划中 (待审批)

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [当前记忆系统 API 概览](#2-当前记忆系统-api-概览)
3. [Spring AI 编程模型](#3-spring-ai-编程模型)
4. [集成方案设计](#4-集成方案设计)
   - [4.1 架构设计](#41-架构设计)
   - [4.2 捕获记忆组件](#42-捕获记忆组件)
     - [4.2.1 设计理念](#421-设计理念)
     - [4.2.2 核心组件: ObservationCaptureService](#422-核心组件-observationscaptureservice)
     - [4.2.3 DTO 模型](#423-dto-模型)
     - [4.2.4 自动捕获拦截器](#424-自动捕获拦截器)
     - [4.2.5 使用示例](#425-使用示例)
     - [4.2.6 ⭐ 最简配置 (一行代码捕获)](#426--最简配置-一行代码捕获记忆)
   - [4.3 检索记忆组件](#43-检索记忆组件)
     - [4.3.1 设计理念](#431-设计理念)
     - [4.3.2 核心组件: MemoryRetrievalService](#432-核心组件-memoryretrievalservice)
     - [4.3.3 Experience 数据模型](#433-experience-数据模型)
     - [4.3.4 Spring AI 集成: CortexMemoryAdvisor](#434-spring-ai-集成-cortexmemoryadvisor)
     - [4.3.5 使用示例](#435-使用示例)
     - [4.3.6 ⭐ 最简配置 (一行代码检索)](#436--最简配置-一行代码启用记忆增强)
5. [实现计划](#5-实现计划)
6. [示例代码](#6-示例代码)
7. [配置说明](#7-配置说明)
8. [风险与注意事项](#8-风险与注意事项)

---

## 1. 背景与目标

### 1.1 问题陈述

当前 Cortex CE 记忆系统主要通过 CLI Hook 集成 (Claude Code, Cursor, OpenClaw)，但对于使用 **Spring AI** 构建智能体的 Java 开发者，缺乏编程方式的无缝集成支持。

### 1.2 目标

为 Java 开发者提供 **最小化代码集成** 的解决方案，使得：

1. **快速集成**: 开发者只需添加依赖和少量配置即可集成
2. **完整功能**: 支持记忆存储、检索、演化等核心功能
3. **低侵入性**: 不影响现有智能体的架构设计
4. **Spring 风格**: 遵循 Spring 生态的设计原则

### 1.3 目标用户

- 使用 Spring AI 构建 AI 智能体的 Java 开发者
- 希望为现有 Spring 应用添加记忆功能的开发者
- 构建企业级 AI 应用的团队

---

## 2. 当前记忆系统 API 概览

### 2.1 核心 API 端点

| 端点 | 方法 | 用途 | 分类 |
|------|------|------|------|
| `/api/ingest/tool-use` | POST | 记录工具使用 (观察) | 捕获 |
| `/api/ingest/session-end` | POST | 会话结束处理 | 捕获 |
| `/api/ingest/user-prompt` | POST | 记录用户提示 | 捕获 |
| `/api/memory/experiences` | POST | 检索经验 (ExpRAG) | 检索 |
| `/api/memory/icl-prompt` | POST | 构建 ICL 提示 | 检索 |
| `/api/memory/refine` | POST | 触发记忆精炼 | 演化 |
| `/api/memory/quality-distribution` | GET | 获取质量分布 | 检索 |
| `/api/health` | GET | 健康检查 | 系统 |

### 2.2 API 详情 (捕获类)

#### 2.2.1 记录观察 (Tool Use)

```http
POST /api/ingest/tool-use
Content-Type: application/json

{
  "session_id": "content-session-id",
  "tool_name": "Edit|Write|Read|Bash",
  "tool_input": {...},
  "tool_response": {...},
  "cwd": "/path/to/project"
}
```

**响应**: `{"status": "accepted"}`

#### 2.2.2 会话结束 (Session End)

```http
POST /api/ingest/session-end
Content-Type: application/json

{
  "session_id": "content-session-id",
  "last_assistant_message": "...",
  "cwd": "/path/to/project"
}
```

**响应**: `{"status": "ok"}`

### 2.3 API 详情 (检索类)

#### 2.3.1 检索经验 (ExpRAG)

**端点**: `POST /api/memory/experiences`

```http
POST /api/memory/experiences
Content-Type: application/json

{
  "task": "current task description",
  "project": "/path/to/project",
  "count": 4
}
```

**注意**: 请求体使用 `task` (非 `query`) 字段。

**响应**:
```json
[
  {
    "task": "Task description",
    "strategy": "What strategy was used", 
    "outcome": "What was the outcome",
    "qualityScore": 0.85,
    "timestamp": "2026-01-01T00:00:00Z"
  }
]
```

#### 2.3.2 构建 ICL 提示

```http
POST /api/memory/icl-prompt
Content-Type: application/json

{
  "task": "current task description",
  "project": "/path/to/project"
}
```

**响应**:
```json
{
  "prompt": "Relevant historical experiences:\n\n### Experience 1\n**Task**: ...\n...",
  "experienceCount": 4
}
```

#### 2.3.3 触发记忆精炼

```http
POST /api/memory/refine?project=/path/to/project
```

**响应**:
```json
{
  "status": "triggered",
  "project": "/path/to/project",
  "message": "Memory refinement event has been published"
}
```

---

## 3. Spring AI 编程模型

### 3.1 Spring AI 核心概念

根据 Spring AI 官方文档，核心概念包括：

1. **ChatClient**: 用于与 AI 模型交互的流式 API
2. **ChatModel**: AI 模型抽象 (OpenAI, Anthropic, etc.)
3. **Prompt**: 用户输入的封装
4. **Message**: 对话消息 (UserMessage, SystemMessage)
5. **Advisor**: 拦截器模式，用于增强功能 (RAG, Memory 等)
6. **VectorStore**: 向量存储抽象

### 3.2 ChatClient 使用方式

```java
// 注入 ChatClient.Builder
@RestController
class MyController {
    private final ChatClient chatClient;

    public MyController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/ai")
    String generation(String userInput) {
        return this.chatClient.prompt()
            .user(userInput)
            .call()
            .content();
    }
}
```

### 3.3 RAG (检索增强生成)

Spring AI 提供 `QuestionAnswerAdvisor` 用于 RAG：

```java
// 配合 VectorStore 使用
chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .user(userText)
    .call()
    .content();
```

### 3.4 Chat Memory

Spring AI 提供对话历史管理：

```java
// 使用 MessageWindowChatMemory
chatClient.prompt()
    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .user(userText)
    .call()
    .content();
```

---

## 4. 集成方案设计

### 4.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Spring AI Agent                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Memory Integration Layer               │    │
│  │  ┌─────────────────┐      ┌─────────────────────┐ │    │
│  │  │ ObservationCapture │      │  MemoryRetrieval   │ │    │
│  │  │   (捕获记忆)      │      │    (检索记忆)       │ │    │
│  │  └────────┬────────┘      └──────────┬──────────┘ │    │
│  │           │                            │            │    │
│  │           ▼                            ▼            │    │
│  │  ┌─────────────────────────────────────────────┐   │    │
│  │  │           CortexMemClient (REST Client)      │   │    │
│  │  └────────────────────┬────────────────────────┘   │    │
│  │                       │                             │    │
│  └───────────────────────┼─────────────────────────────┘    │
│                          │ HTTP                              │
│  ┌───────────────────────┴─────────────────────────────┐    │
│  │               ChatClient (Spring AI)                 │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│              Cortex CE Backend (Port 37777)                  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │   Ingest    │  │    ReMem     │  │   Refinement     │   │
│  │    API      │  │     API      │  │     (演化)       │   │
│  │  (捕获)     │  │   (检索)     │  │                  │   │
│  └─────────────┘  └──────────────┘  └──────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

---

### 4.2 捕获记忆组件

负责将智能体的"经历"持久化到记忆系统。

#### 4.2.1 设计理念

**捕获什么**：
1. **工具执行结果** - 智能体使用工具 (Edit, Write, Read, Bash) 的结果
2. **会话上下文** - 会话开始/结束、用户提示
3. **任务结果** - 任务完成情况

**为什么需要**：
- 没有捕获就没有记忆
- 后续检索依赖于已捕获的经验

#### 4.2.2 核心组件: ObservationCaptureService

```java
/**
 * 记忆捕获服务 - 负责将智能体的行为记录到记忆系统
 */
public interface ObservationCaptureService {
    
    /**
     * 记录工具执行观察
     * 在工具执行后调用，记录工具输入、输出和结果
     */
    void recordToolObservation(ToolObservation observation);
    
    /**
     * 记录会话结束
     * 在会话结束时调用，触发摘要生成和经验归档
     */
    void recordSessionEnd(SessionEndRequest request);
    
    /**
     * 记录用户提示
     * 记录用户的原始输入
     */
    void recordUserPrompt(UserPromptRequest request);
}
```

#### 4.2.3 DTO 模型

```java
// 工具观察请求
public record ToolObservation(
    String sessionId,          // 会话 ID
    String projectPath,       // 项目路径 (记忆隔离)
    String toolName,          // 工具名称 (Edit/Write/Read/Bash)
    Map<String, Object> toolInput,    // 工具输入
    Map<String, Object> toolResponse, // 工具输出
    int promptNumber          // 当前是第几个 prompt
) {}

// 会话结束请求
public record SessionEndRequest(
    String sessionId,
    String projectPath,
    String lastAssistantMessage  // 可选: 最后assistant消息
) {}

// 用户提示请求
public record UserPromptRequest(
    String sessionId,
    String projectPath,
    String promptText,
    int promptNumber
) {}
```

#### 4.2.4 自动捕获拦截器

**核心问题**: Spring AI 的工具执行是通过 `FunctionCallback` 机制实现的，不是传统的事件机制。

**实现方案**: 使用 **AOP 切面** 拦截工具执行，自动记录前后状态。

---

##### 方案一: AOP 切面拦截 (推荐)

```java
/**
 * 工具执行切面 - 通过 AOP 自动拦截工具执行并记录观察
 * 
 * 原理: 切面拦截所有 @Tool 注解的方法，在执行前后自动记录
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CortexToolAspect {
    
    private final ObservationCaptureService captureService;
    
    public CortexToolAspect(ObservationCaptureService captureService) {
        this.captureService = captureService;
    }
    
    /**
     * 拦截所有被 @Tool 注解的方法
     */
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object interceptToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        String toolName = toolAnnotation.name();
        
        // 1. 记录执行前状态
        Object[] args = joinPoint.getArgs();
        Map<String, Object> toolInput = toMap(args, method.getParameters());
        
        // 2. 执行工具
        Object result = joinPoint.proceed();
        
        // 3. 执行后自动捕获 (关键!)
        captureService.recordToolObservation(ToolObservation.builder()
            .sessionId(getCurrentSessionContext())
            .projectPath(getCurrentProjectPath())
            .toolName(toolName)
            .toolInput(toolInput)
            .toolResponse(toMap(result))
            .promptNumber(getPromptCount())
            .build());
        
        return result;
    }
}
```

---

##### 方案二: 包装 ChatClient

```java
/**
 * 带记忆的 ChatClient - 包装原生 ChatClient，自动捕获工具执行
 */
@Component
class CortexChatClient {
    
    private final ChatClient delegate;
    private final ObservationCaptureService captureService;
    
    public CortexChatClient(ChatClient delegate,
                           ObservationCaptureService captureService) {
        this.delegate = delegate;
        this.captureService = captureService;
    }
    
    /**
     * 调用 AI 并自动捕获工具执行
     */
    public PromptResult chat(Prompt prompt) {
        // 1. 设置工具回调包装器
        FunctionCallbackWrapper wrapper = new FunctionCallbackWrapper() {
            @Override
            public Object apply(String toolName, Map<String, Object> input, 
                              FunctionCallbackResult result) {
                // 自动捕获工具执行结果
                captureService.recordToolObservation(ToolObservation.builder()
                    .sessionId(getCurrentSessionContext())
                    .projectPath(getCurrentProjectPath())
                    .toolName(toolName)
                    .toolInput(input)
                    .toolResponse(toMap(result.getResult()))
                    .promptNumber(getPromptCount())
                    .build());
                return result.getResult();
            }
        };
        
        // 2. 调用 AI
        return delegate.prompt(prompt)
            .toolCallbackWrapper(wrapper)  // 注入包装器
            .call();
    }
}
```

---

##### 方案三: 事件驱动 (适用于自定义事件)

如果你的智能体框架本身会发布事件，可以监听这些事件：

```java
@Component
class AgentEventListener {
    private final ObservationCaptureService captureService;
    
    @EventListener
    public void onToolUse(ToolUseEvent event) {
        // 框架发布的事件，直接捕获
        captureService.recordToolObservation(ToolObservation.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .toolName(event.getToolName())
            .toolInput(event.getInput())
            .toolResponse(event.getOutput())
            .promptNumber(event.getPromptNumber())
            .build());
    }
    
    @EventListener
    public void onSessionEnd(SessionEndEvent event) {
        captureService.recordSessionEnd(SessionEndRequest.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .lastAssistantMessage(event.getLastMessage())
            .build());
    }
}
```

---

##### 自动捕获机制对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **AOP 切面** | 无侵入，统一拦截 | 需要 AOP 依赖 | 通用场景 |
| **包装 ChatClient** | 完全可控 | 需要替换 bean | 细粒度控制 |
| **事件监听** | 解耦最好 | 依赖框架事件 | 自定义框架 |
        captureService.recordToolObservation(ToolObservation.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .toolName(event.getToolName())
            .toolInput(event.getInput())
            .toolResponse(event.getOutput())
            .promptNumber(event.getPromptNumber())
            .build());
    }
}
```

#### 4.2.5 使用示例

**方式一: 手动捕获 (推荐)**

```java
@Service
class MyAiAgent {
    private final ObservationCaptureService captureService;
    private final ChatClient chatClient;
    
    public MyAiAgent(ObservationCaptureService captureService,
                     ChatClient.Builder builder) {
        this.captureService = captureService;
        this.chatClient = builder.build();
    }
    
    public String executeTask(String task, String toolName, 
                             Map<String, Object> toolInput) {
        // 1. 执行工具
        Object result = executeTool(toolName, toolInput);
        
        // 2. 捕获观察 (关键!)
        captureService.recordToolObservation(ToolObservation.builder()
            .sessionId(getCurrentSessionId())
            .projectPath(getProjectPath())
            .toolName(toolName)
            .toolInput(toolInput)
            .toolResponse(toMap(result))
            .promptNumber(getPromptCount())
            .build());
        
        // 3. 继续处理...
        return processResult(result);
    }
    
    public void onSessionEnd() {
        captureService.recordSessionEnd(SessionEndRequest.builder()
            .sessionId(getCurrentSessionId())
            .projectPath(getProjectPath())
            .build());
    }
}
```

**方式二: 事件驱动自动捕获**

```java
@Component
class AgentEventListener {
    private final ObservationCaptureService captureService;
    
    public AgentEventListener(ObservationCaptureService captureService) {
        this.captureService = captureService;
    }
    
    @EventListener
    public void onToolUse(ToolUseEvent event) {
        // 自动捕获 - 无需手动调用
        captureService.recordToolObservation(ToolObservation.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .toolName(event.getToolName())
            .toolInput(event.getInput())
            .toolResponse(event.getOutput())
            .promptNumber(event.getPromptNumber())
            .build());
    }
    
    @EventListener
    public void onSessionEnd(SessionEndEvent event) {
        captureService.recordSessionEnd(SessionEndRequest.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .lastAssistantMessage(event.getLastMessage())
            .build());
    }
}
```

#### ⭐ 4.2.6 最简配置 (一行代码捕获记忆)

**目标**: 最小化代码改动，一行启用自动捕获

```java
@SpringBootApplication
@EnableCortexMem(captureEnabled = true)  // ← 只需这一行！
public class MyAiApp {
    public static void main(String[] args) {
        SpringApplication.run(MyAiApp.class, args);
    }
}
```

```yaml
# application.yml
cortex:
  mem:
    base-url: http://localhost:37777
    project-path: /my/project
```

**效果**: 启用后，所有工具执行和会话结束事件会自动捕获到记忆系统，无需修改任何业务代码。

---

### 4.3 检索记忆组件

负责从记忆系统中检索相关经验，为智能体提供上下文增强。

#### 4.3.1 设计理念

**检索什么**：
1. **相关经验** - 与当前任务相关的历史经验
2. **ICL 提示** - 构建好的上下文提示 (含历史经验)
3. **质量分布** - 记忆库质量统计

**检索策略**：
- **ExpRAG** - 基于质量评分的经验检索
- 优先返回高质量经验
- 支持自定义经验数量

#### 4.3.2 核心组件: MemoryRetrievalService

```java
/**
 * 记忆检索服务 - 负责从记忆系统检索相关经验
 */
public interface MemoryRetrievalService {
    
    /**
     * 检索相关经验 (ExpRAG)
     * 基于当前任务描述，检索高质量历史经验
     *
     * @param currentTask 当前任务描述
     * @param projectPath 项目路径
     * @param count 检索数量
     * @return 相关经验列表
     */
    List<Experience> retrieveExperiences(String currentTask, 
                                       String projectPath, 
                                       int count);
    
    /**
     * 构建 ICL (In-Context Learning) 提示
     * 将检索到的经验格式化为 prompt
     *
     * @param currentTask 当前任务
     * @param projectPath 项目路径
     * @return 格式化后的 ICL 提示
     */
    String buildICLPrompt(String currentTask, String projectPath);
    
    /**
     * 获取记忆质量分布
     *
     * @param projectPath 项目路径
     * @return 质量分布统计
     */
    Map<String, Long> getQualityDistribution(String projectPath);
}
```

#### 4.3.3 Experience 数据模型

```java
/**
 * 经验记录 - 从记忆系统检索的历史经验
 */
public record Experience(
    String task,           // 任务描述
    String strategy,       // 使用的策略
    String outcome,        // 执行结果
    Float qualityScore,   // 质量分数 (0-1)
    Instant timestamp     // 时间戳
) {}

/**
 * ICL 提示构建结果
 */
public record ICLPromptResult(
    String prompt,         // 格式化后的提示
    int experienceCount   // 包含的经验数量
) {}
```

#### 4.3.4 Spring AI 集成: CortexMemoryAdvisor

自定义 Advisor，无缝集成到 Spring AI ChatClient：

```java
/**
 * Cortex Memory Advisor
 * 
 * 自动将记忆检索结果注入到 AI 对话上下文中
 * 
 * 使用方式:
 * chatClient.prompt()
 *     .advisors(CortexMemoryAdvisor.builder(client).build())
 *     .user("...")
 *     .call()
 */
public class CortexMemoryAdvisor implements Advisor {
    
    private final CortexMemClient cortexClient;
    private final String projectPath;
    private final int maxExperiences;
    
    // 构造器
    public static Builder builder(CortexMemClient client) {
        return new Builder(client);
    }
    
    public static class Builder {
        private CortexMemClient client;
        private String projectPath;
        private int maxExperiences = 4;
        
        public Builder projectPath(String path) {
            this.projectPath = path;
            return this;
        }
        
        public Builder maxExperiences(int count) {
            this.maxExperiences = count;
            return this;
        }
        
        public CortexMemoryAdvisor build() {
            return new CortexMemoryAdvisor(client, projectPath, maxExperiences);
        }
    }
    
    // 实现 Advisor 接口
    @Override
    public AdvisedRequest advise(AdvisedRequest request) {
        // 1. 获取用户输入
        String userText = request.userText();
        
        // 2. 检索相关经验
        List<Experience> experiences = cortexClient
            .retrieveExperiences(ExperienceRequest.builder()
                .task(userText)
                .project(projectPath)
                .count(maxExperiences)
                .build());
        
        // 3. 构建 ICL 提示
        String iclPrompt = cortexClient.buildICLPrompt(
            ICLPromptRequest.builder()
                .task(userText)
                .project(projectPath)
                .build());
        
        // 4. 注入到 System Prompt
        return request.mutate()
            .systemText(iclPrompt)
            .build();
    }
}
```

#### 4.3.5 使用示例

**方式一: 使用 Advisor (推荐 - 无缝集成)**

```java
@Configuration
class AiConfig {
    @Bean
    public CortexMemoryAdvisor cortexMemoryAdvisor(CortexMemClient client) {
        return CortexMemoryAdvisor.builder(client)
            .projectPath("/my/project")
            .maxExperiences(4)
            .build();
    }
}

@RestController
class AiController {
    private final ChatClient chatClient;
    
    public AiController(ChatClient.Builder builder,
                        @Qualifier("cortexMemoryAdvisor") 
                        CortexMemoryAdvisor advisor) {
        this.chatClient = builder
            .defaultSystem("You are a helpful coding assistant.")
            .build()
            .mutate()
            .advisors(advisor)
            .build();
    }
    
    @GetMapping("/chat")
    String chat(@RequestParam String message) {
        // 自动检索相关经验并注入上下文
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

**方式二: 手动检索 (细粒度控制)**

```java
@Service
class MyAgentService {
    private final MemoryRetrievalService retrievalService;
    private final ChatClient chatClient;
    
    public MyAgentService(MemoryRetrievalService retrievalService,
                         ChatClient.Builder builder) {
        this.retrievalService = retrievalService;
        this.chatClient = builder.build();
    }
    
    public String processTask(String task) {
        // 1. 手动检索相关经验
        List<Experience> experiences = retrievalService
            .retrieveExperiences(task, "/my/project", 4);
        
        // 2. 构建增强提示
        String iclPrompt = retrievalService
            .buildICLPrompt(task, "/my/project");
        
        // 3. 调用 AI (使用自定义系统提示)
        return chatClient.prompt()
            .system(s -> s.text(iclPrompt))
            .user(task)
            .call()
            .content();
    }
}
```

#### ⭐ 4.3.6 最简配置 (一行代码启用记忆增强)

**目标**: 最少代码，让 AI 自动拥有"记忆上下文"能力

```java
@SpringBootApplication
@EnableCortexMem(retrievalEnabled = true)  // ← 只需这一行！
public class MyAiApp {
    public static void main(String[] args) {
        SpringApplication.run(MyAiApp.class, args);
    }
}
```

```yaml
# application.yml - 同样只需要配置地址
cortex:
  mem:
    base-url: http://localhost:37777
    project-path: /my/project
    default-experience-count: 4
```

**效果**: 启用后，ChatClient 的每次调用会自动检索相关经验并注入到 System Prompt，无需修改任何业务代码。

| 对比 | 最简配置 | 完整配置 |
|------|---------|---------|
| 代码改动 | 1 行注解 | 10+ 行配置 |
| 适用场景 | 快速原型 / MVP | 生产级定制 |
| 灵活性 | 固定策略 | 完全可控 |

---

### 4.4 统一的客户端接口

为了简化使用，提供统一的客户端接口：

```java
/**
 * Cortex Memory 客户端 - 统一的记忆系统接口
 * 
 * 封装所有记忆操作 (捕获 + 检索)
 */
public interface CortexMemClient {
    
    // ==================== 捕获相关 ====================
    
    /**
     * 记录工具观察
     */
    void recordObservation(ObservationRequest request);
    
    /**
     * 记录会话结束
     */
    void recordSessionEnd(SessionEndRequest request);
    
    /**
     * 记录用户提示
     */
    void recordUserPrompt(UserPromptRequest request);
    
    // ==================== 检索相关 ====================
    
    /**
     * 检索相关经验
     */
    List<Experience> retrieveExperiences(ExperienceRequest request);
    
    /**
     * 构建 ICL 提示
     */
    String buildICLPrompt(ICLPromptRequest request);
    
    // ==================== 管理相关 ====================
    
    /**
     * 触发记忆精炼
     */
    void triggerRefinement(String projectPath);
    
    /**
     * 获取质量分布
     */
    Map<String, Long> getQualityDistribution(String projectPath);
    
    /**
     * 健康检查
     */
    boolean healthCheck();
}
```

---

## 5. 实现计划

### 5.1 模块划分

```
cortex-mem-spring-integration/
├── cortex-mem-client/          # REST 客户端
│   ├── src/main/java/.../client/
│   │   ├── CortexMemClient.java
│   │   ├── CortexMemClientImpl.java
│   │   ├── dto/
│   │   │   ├── ObservationRequest.java
│   │   │   ├── ExperienceRequest.java
│   │   │   └── Experience.java
│   │   └── config/
│   │       └── CortexMemProperties.java
│   └── pom.xml
│
├── cortex-mem-spring-ai/       # Spring AI 集成
│   ├── src/main/java/.../ai/
│   │   ├── observation/
│   │   │   └── ObservationCaptureService.java
│   │   ├── retrieval/
│   │   │   └── MemoryRetrievalService.java
│   │   └── advisor/
│   │       └── CortexMemoryAdvisor.java
│   └── pom.xml
│
└── cortex-mem-starter/         # Spring Boot Starter
    ├── src/main/java/.../
    │   └── autoconfigure/
    │       └── CortexMemAutoConfiguration.java
    └── pom.xml
```

### 5.2 实现阶段

#### Phase 1: 基础客户端 (预计 2 天)

- [ ] 设计 DTO 模型类
- [ ] 实现 CortexMemClient 接口 (REST Client)
- [ ] 使用 Spring REST Client (同步编程，更简单)
- [ ] 添加重试和超时配置
- [ ] 单元测试

#### Phase 2: Spring AI 集成服务 (预计 3 天)

- [ ] 实现 ObservationCaptureService (捕获)
- [ ] 实现 MemoryRetrievalService (检索)
- [ ] 实现 CortexMemoryAdvisor (Spring AI 集成)
- [ ] 单元测试

#### Phase 3: Spring Boot Starter (预计 2 天)

- [ ] 定义配置属性类
- [ ] 实现自动配置
- [ ] 健康检查集成
- [ ] 配置属性验证

#### Phase 4: 文档与发布 (预计 1 天)

- [ ] 编写使用文档
- [ ] 示例代码
- [ ] Maven Central 发布配置

---

## 6. 示例代码

### 6.1 快速集成 (最小代码)

```java
// 1. 添加依赖后，只需配置
@SpringBootApplication
@EnableCortexMem  // 启用记忆系统
public class MyAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }
}
```

```yaml
# 2. application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
  cortex:
    mem:
      base-url: http://localhost:37777
      project-path: /path/to/your/project
```

```java
// 3. 使用 ChatClient (自动带记忆)
@RestController
class AiController {
    private final ChatClient chatClient;

    public AiController(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant")
            .build();
    }

    @GetMapping("/chat")
    String chat(@RequestParam String message) {
        // 自动检索相关经验并注入上下文
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

### 6.2 完整集成 (细粒度控制)

```java
@Configuration
class CortexMemConfig {
    @Bean
    public CortexMemClient cortexMemClient(CortexMemProperties props) {
        return new CortexMemClientImpl(props);
    }

    @Bean
    public CortexMemoryAdvisor cortexMemoryAdvisor(CortexMemClient client) {
        return CortexMemoryAdvisor.builder(client)
            .projectPath("/my/project")
            .maxExperiences(4)
            .build();
    }
}
```

```java
@Service
class MyAiAgent {
    private final ChatClient chatClient;
    private final CortexMemClient cortexMemClient;

    public MyAiAgent(ChatClient.Builder builder,
                     CortexMemClient cortexMemClient) {
        this.chatClient = builder.build();
        this.cortexMemClient = cortexMemClient;
    }

    public String process(String userInput, String toolName, 
                          Map<String, Object> toolInput) {
        // 1. 捕获: 记录观察到记忆系统
        cortexMemClient.recordObservation(ObservationRequest.builder()
            .sessionId(getSessionId())
            .toolName(toolName)
            .toolInput(toolInput)
            .projectPath(getProjectPath())
            .build());

        // 2. 检索: 获取相关经验
        List<Experience> experiences = cortexMemClient
            .retrieveExperiences(ExperienceRequest.builder()
                .task(userInput)
                .project(getProjectPath())
                .count(4)
                .build());

        // 3. 构建增强提示
        String iclPrompt = cortexMemClient.buildICLPrompt(
            ICLPromptRequest.builder()
                .task(userInput)
                .project(getProjectPath())
                .build());

        // 4. 调用 AI
        return chatClient.prompt()
            .system(iclPrompt)
            .user(userInput)
            .call()
            .content();
    }

    public void onSessionEnd() {
        cortexMemClient.recordSessionEnd(SessionEndRequest.builder()
            .sessionId(getSessionId())
            .projectPath(getProjectPath())
            .build());
    }
}
```

---

## 7. 配置说明

### 7.1 配置属性

```yaml
cortex:
  mem:
    # 必需: Cortex CE 后端地址
    base-url: http://localhost:37777
    
    # 必需: 项目路径 (用于隔离记忆)
    project-path: /path/to/project
    
    # 可选: 连接超时 (默认 10s)
    connect-timeout: 10s
    
    # 可选: 读取超时 (默认 30s)
    read-timeout: 30s
    
    # 可选: 重试次数 (默认 3)
    retry:
      max-attempts: 3
      
    # 可选: 启用自动记忆精炼 (默认 true)
    auto-refine-enabled: true
    
    # 可选: 每次请求检索的经验数量 (默认 4)
    default-experience-count: 4
```

### 7.2 环境变量

```bash
# 基础配置
CORTEX_MEM_BASE_URL=http://localhost:37777
CORTEX_MEM_PROJECT_PATH=/path/to/project

# 认证 (如果需要)
CORTEX_MEM_API_KEY=your-api-key
```

### 7.3 Docker 部署

```yaml
# docker-compose.yml
services:
  my-ai-agent:
    image: my-ai-agent:latest
    environment:
      CORTEX_MEM_BASE_URL: http://cortex-ce:37777
      CORTEX_MEM_PROJECT_PATH: /app/projects/my-project
      SPRING_AI_OPENAI_API_KEY: ${OPENAI_API_KEY}

  cortex-ce:
    image: ghcr.io/blueforce-tech-inc/bluecortexce/cortex-ce:main
    ports:
      - "37777:37777"
    environment:
      SPRING_AI_OPENAI_API_KEY: ${OPENAI_API_KEY}
      # 关闭自演化以节省 token
      MEMORY_REFINE_ENABLED: false
```

---

## 8. 风险与注意事项

### 8.1 风险评估

| 风险 | 级别 | 缓解措施 |
|------|------|----------|
| API 兼容性变更 | 中 | 保持向后兼容版本号策略 |
| 网络延迟影响响应时间 | 中 | 异步处理 + 缓存 |
| Token 消耗增加 | 高 | 提供开关控制 + 监控 |
| 内存泄漏 | 低 | 定期清理 + 监控 |

### 8.2 注意事项

1. **会话管理**: 确保正确管理 session_id，支持多会话隔离
2. **错误处理**: 网络失败时应有降级策略
3. **性能**: 记忆检索是同步的，考虑异步处理
4. **Token 成本**: 每次请求都会检索经验，注意成本控制
5. **数据安全**: 敏感信息需要考虑脱敏

### 8.3 监控建议

```java
// 集成 Micrometer 监控
@Timed("cortex.mem.experience.retrieve")
public List<Experience> retrieveExperiences(...) {
    // ...
}

@Timed("cortex.mem.observation.record")
public void recordObservation(...) {
    // ...
}
```

---

## 附录

### A. 捕获 → 检索 工作流

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  工具执行   │────▶│  捕获观察   │────▶│  存储到DB   │
└─────────────┘     └─────────────┘     └─────────────┘
                                               │
                                               ▼ (异步)
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  构建响应   │◀────│  检索经验   │◀────│  质量评分   │
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │
       │                   ▼
       │            ┌─────────────┐
       │            │  记忆精炼   │
       │            │  (可选)     │
       │            └─────────────┘
       ▼
┌─────────────┐
│  返回给用户 │
└─────────────┘
```

### B. 版本规划

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0.0 | 2026-Q2 | 初始版本 - 核心功能 |
| 1.1.0 | 2026-Q3 | 增强监控 + 性能优化 |
| 1.2.0 | 2026-Q4 | MCP 协议支持 |

---

**文档状态**: 🟡 规划中 - 待审批后实施

**下次检查**: 规划审批后
