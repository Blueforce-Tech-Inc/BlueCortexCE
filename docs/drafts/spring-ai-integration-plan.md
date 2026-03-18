# Spring AI 集成 Cortex CE 记忆系统 - 规划文档

> **版本**: 1.1  
> **更新日期**: 2026-03-18  
> **状态**: ✅ Phase 1-3 已实施 (cortex-mem-spring-integration 三模块完成，已安装到本地 Maven 仓库)
>
> ---
>
> **✅ Spring AI API 验证状态** (2026-03-18 确认):
>
> 本文档中涉及的 Spring AI API 已通过官方文档验证：
>
> 1. ✅ `@Tool` 注解 - 包路径: `org.springframework.ai.tool.annotation.Tool`
> 2. ✅ `ToolCallback` 接口 - 存在
> 3. ✅ `MethodToolCallback` - 存在
> 4. ✅ `ToolCallbacks.from()` - 存在，用于从类实例创建工具回调
> 5. ✅ `tools()` 方法 (ChatClient) - 存在
> 6. ✅ AOP 拦截 `@Tool` 方法 - 可行 (Spring AI 支持)
>
> **参考**: [Spring AI Tool Calling 官方文档](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

## 目录

- [Spring AI 集成 Cortex CE 记忆系统 - 规划文档](#spring-ai-集成-cortex-ce-记忆系统---规划文档)
  - [目录](#目录)
  - [1. 背景与目标](#1-背景与目标)
    - [1.1 问题陈述](#11-问题陈述)
    - [1.2 目标](#12-目标)
    - [1.3 目标用户](#13-目标用户)
  - [2. 当前记忆系统 API 概览](#2-当前记忆系统-api-概览)
    - [2.1 核心 API 端点](#21-核心-api-端点)
    - [2.2 API 详情 (捕获类)](#22-api-详情-捕获类)
      - [2.2.1 记录观察 (Tool Use)](#221-记录观察-tool-use)
      - [2.2.2 会话结束 (Session End)](#222-会话结束-session-end)
    - [2.3 API 详情 (检索类)](#23-api-详情-检索类)
      - [2.3.1 检索经验 (ExpRAG)](#231-检索经验-exprag)
      - [2.3.2 构建 ICL 提示](#232-构建-icl-提示)
      - [2.3.3 触发记忆精炼](#233-触发记忆精炼)
      - [2.3.4 提交记忆质量反馈](#234-提交记忆质量反馈)
      - [2.3.5 获取质量分布](#235-获取质量分布)
  - [3. Spring AI 编程模型](#3-spring-ai-编程模型)
    - [3.1 Spring AI 核心概念](#31-spring-ai-核心概念)
    - [3.2 ChatClient 使用方式](#32-chatclient-使用方式)
    - [3.3 RAG (检索增强生成)](#33-rag-检索增强生成)
    - [3.4 Chat Memory](#34-chat-memory)
  - [4. 集成方案设计](#4-集成方案设计)
    - [4.1 架构设计](#41-架构设计)
    - [4.2 捕获记忆组件](#42-捕获记忆组件)
      - [4.2.1 设计理念](#421-设计理念)
      - [4.2.2 核心组件: ObservationCaptureService](#422-核心组件-observationcaptureservice)
      - [4.2.3 DTO 模型](#423-dto-模型)
      - [4.2.4 自动捕获拦截器](#424-自动捕获拦截器)
        - [方案一: AOP 切面拦截 (推荐)](#方案一-aop-切面拦截-推荐)
        - [方案二: 使用 Spring AI 原生回调机制](#方案二-使用-spring-ai-原生回调机制)
        - [方案三: 事件驱动 (仅当框架发布事件时可用)](#方案三-事件驱动-仅当框架发布事件时可用)
        - [自动捕获机制对比](#自动捕获机制对比)
      - [4.2.5 使用示例](#425-使用示例)
      - [⭐ 4.2.6 最简配置 (一行代码捕获记忆)](#-426-最简配置-一行代码捕获记忆)
    - [4.3 检索记忆组件](#43-检索记忆组件)
      - [4.3.1 设计理念](#431-设计理念)
      - [4.3.2 核心组件: MemoryRetrievalService](#432-核心组件-memoryretrievalservice)
      - [4.3.3 Experience 数据模型](#433-experience-数据模型)
      - [4.3.4 Spring AI 集成: CortexMemoryAdvisor](#434-spring-ai-集成-cortexmemoryadvisor)
      - [4.3.5 使用示例](#435-使用示例)
      - [⭐ 4.3.6 最简配置 (一行代码启用记忆增强)](#-436-最简配置-一行代码启用记忆增强)
      - [4.3.7 CortexMemoryTools：Advisor vs Tool 模式设计讨论 (2026-03-18)](#437-cortexmemorytoolsadvisor-vs-tool-模式设计讨论-2026-03-18)
      - [4.3.8 旁路型集成：VectorStoreChatMemoryAdvisor 的借鉴与改进方向](#438-旁路型集成vectorstorechatmemoryadvisor-的借鉴与改进方向-2026-03-18)
    - [4.4 统一的客户端接口](#44-统一的客户端接口)
  - [5. 实现计划](#5-实现计划)
    - [5.1 模块划分](#51-模块划分)
    - [5.2 实现阶段](#52-实现阶段)
      - [Phase 1: 基础客户端 ✅ 已完成](#phase-1-基础客户端--已完成)
      - [Phase 2: Spring AI 集成服务 ✅ 已完成](#phase-2-spring-ai-集成服务--已完成)
      - [Phase 3: Spring Boot Starter ✅ 已完成](#phase-3-spring-boot-starter--已完成)
      - [Phase 4: 文档与发布 (待做)](#phase-4-文档与发布-待做)
  - [6. 示例代码](#6-示例代码)
    - [6.1 快速集成 (最小代码)](#61-快速集成-最小代码)
    - [6.2 完整集成 (细粒度控制)](#62-完整集成-细粒度控制)
  - [7. 配置说明](#7-配置说明)
    - [7.1 配置属性](#71-配置属性)
    - [7.2 环境变量](#72-环境变量)
    - [7.3 Docker 部署](#73-docker-部署)
  - [8. 风险与注意事项](#8-风险与注意事项)
    - [8.1 风险评估](#81-风险评估)
    - [8.2 注意事项](#82-注意事项)
    - [8.3 监控建议](#83-监控建议)
  - [附录](#附录)
    - [A. 捕获 → 检索 工作流](#a-捕获--检索-工作流)
    - [B. 版本规划](#b-版本规划)

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
| `/api/session/start` | POST | 初始化/恢复会话 | 捕获 |
| `/api/ingest/tool-use` | POST | 记录工具使用 (观察) | 捕获 |
| `/api/ingest/session-end` | POST | 会话结束处理 | 捕获 |
| `/api/ingest/user-prompt` | POST | 记录用户提示 | 捕获 |
| `/api/memory/experiences` | POST | 检索经验 (ExpRAG) | 检索 |
| `/api/memory/icl-prompt` | POST | 构建 ICL 提示 | 检索 |
| `/api/memory/refine` | POST | 触发记忆精炼 | 演化 |
| `/api/memory/feedback` | POST | 提交记忆质量反馈 | 演化 |
| `/api/memory/quality-distribution` | GET | 获取质量分布 | 检索 |
| `/api/health` | GET | 健康检查 | 系统 |

> **⚠️ CORS 说明**: 后端默认关闭 CORS。如需浏览器前端直接调用，需配置 `claudemem.cors.allowed-origins` (多个域名用逗号分隔)。

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

#### 2.3.4 提交记忆质量反馈

```http
POST /api/memory/feedback
Content-Type: application/json

{
  "observationId": "uuid-of-observation",
  "feedbackType": "SUCCESS",
  "comment": "This was helpful for..."
}
```

**注意**: `feedbackType` 可取 `SUCCESS`、`FAILURE`、`USEFUL`、`NOT_USEFUL` 等。

**响应**:
```json
{
  "status": "accepted",
  "id": "feedback-id"
}
```

**用途**: 用户可以对检索到的经验进行评分，帮助系统学习和改进检索质量。

#### 2.3.5 获取质量分布

```http
GET /api/memory/quality-distribution?project=/path/to/project
```

**响应**（扁平结构，与 cortex-mem-client 的 QualityDistribution 对齐）:
```json
{
  "project": "/path/to/project",
  "high": 45,
  "medium": 30,
  "low": 25,
  "unknown": 0
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

Spring AI 提供 `QuestionAnswerAdvisor` 用于 RAG（静态文档检索增强）：

```java
// 配合 VectorStore 使用
chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .user(userText)
    .call()
    .content();
```

### 3.4 Chat Memory

Spring AI 提供多种对话记忆 Advisor：

- **MessageChatMemoryAdvisor**：从 ChatMemory 取回消息，按对话结构注入
- **VectorStoreChatMemoryAdvisor**：面向长程对话记忆的 RAG Advisor。**Before** 阶段：嵌入用户查询 → 相似检索 → 注入相关历史到 System 文本；**After** 阶段：自动将新轮次（user+assistant）转为 Document → embed → `vectorStore.add()` 持久化（`persistOnCompletion` 默认 true）。全程自动，无需手动存储。参见 [Spring AI 文档](https://docs.spring.io/spring-ai/reference/api/advisors.html)。

```java
// 示例：MessageChatMemoryAdvisor
chatClient.prompt()
    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
    .user(userText)
    .call()
    .content();
```

**重要区分**：Cortex CE 记忆系统与上述**不同**。VectorStoreChatMemoryAdvisor 的数据源是**对话轮次**；Cortex CE 的数据源是**智能体执行轨迹**（工具调用、观察、摘要），用于经验复用（ExpRAG/ICL），二者互补而非替代，详见 [4.3.7](#437-cortexmemorytoolsadvisor-vs-tool-模式设计讨论-2026-03-18)。

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
    void recordToolObservation(ObservationRequest observation);
    
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

以下与 `cortex-mem-client` 的 DTO 对齐（实现类为 `ObservationRequest`、`SessionEndRequest`、`UserPromptRequest`）：

```java
// 工具观察请求 (ObservationRequest)
public record ObservationRequest(
    String sessionId,          // 会话 ID
    String projectPath,        // 项目路径 (记忆隔离)
    String toolName,           // 工具名称 (Edit/Write/Read/Bash)
    Object toolInput,          // 工具输入
    Object toolResponse,       // 工具输出
    Integer promptNumber       // 当前是第几个 prompt
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

**核心问题**: Spring AI 的工具执行是通过 `FunctionCallback` 或 `@Tool` 注解实现的。

**实现方案**: 使用 **AOP 切面** 拦截工具方法执行。

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
     * 注: @Tool 注解来自 org.springframework.ai.tool.annotation.Tool
     */
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object interceptToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 需要导入: import org.springframework.ai.tool.annotation.Tool;
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        String toolName = toolAnnotation.name();
        
        // 1. 记录执行前状态
        Object[] args = joinPoint.getArgs();
        Map<String, Object> toolInput = toMap(args, method.getParameters());
        
        // 2. 执行工具
        Object result = joinPoint.proceed();
        
        // 3. 执行后自动捕获 (关键!)
        captureService.recordToolObservation(ObservationRequest.builder()
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

##### 方案二: 使用 Spring AI 原生回调机制

> ✅ **已验证**: Spring AI 提供 `ToolCallback` 接口和 `ToolCallbacks.from()` 方法。

```java
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;

/**
 * 带记忆的工具类 - 通过 Spring AI 的工具回调机制自动捕获
 * 
 * 继承原有工具类并注册到 ChatClient，即可自动捕获
 */
@Component
class CortexToolCallbacks extends DateTimeTools {
    
    private final ObservationCaptureService captureService;
    
    public CortexToolCallbacks(ObservationCaptureService captureService) {
        this.captureService = captureService;
    }
    
    // 重写工具方法，自动捕获执行结果
    @Override
    @Tool(description = "Get the current date and time")
    public String getCurrentDateTime() {
        String result = super.getCurrentDateTime();
        
        // 自动捕获工具执行结果
        captureService.recordToolObservation(ObservationRequest.builder()
            .sessionId(getCurrentSessionContext())
            .projectPath(getCurrentProjectPath())
            .toolName("getCurrentDateTime")
            .toolInput(Map.of())
            .toolResponse(Map.of("result", result))
            .promptNumber(getPromptCount())
            .build());
        
        return result;
    }
}

// 使用方式
ChatClient.create(chatModel)
    .tools(new CortexToolCallbacks(captureService))  // 传入带捕获的类
    .call();
    }
}
```

---

##### 方案三: 事件驱动 (仅当框架发布事件时可用)

> ⚠️ **注意**: 此方案要求你的智能体框架会发布事件。以下代码中的 `ToolUseEvent` 和 `SessionEndEvent` 是**假设的事件类**，你需要根据实际框架替换为真实的事件类型。

如果你的智能体框架本身会发布事件 (如自定义事件)，可以监听这些事件：

```java
@Component
class AgentEventListener {
    private final ObservationCaptureService captureService;
    
    // 假设的事件类 - 需要替换为框架实际的类
    @EventListener
    public void onToolUse(YourFrameworkToolUseEvent event) {
        // 框架发布的事件，直接捕获
        captureService.recordToolObservation(ObservationRequest.builder()
            .sessionId(event.getSessionId())
            .projectPath(event.getProjectPath())
            .toolName(event.getToolName())
            .toolInput(event.getInput())
            .toolResponse(event.getOutput())
            .promptNumber(event.getPromptNumber())
            .build());
    }
    
    // 假设的事件类 - 需要替换为框架实际的类
    @EventListener
    public void onSessionEnd(YourFrameworkSessionEndEvent event) {
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

| 方案 | 原理 | 优点 | 状态 |
|------|------|------|------|
| **AOP 切面** | 拦截 @Tool 方法 | 无侵入、统一拦截 | ✅ 可行 |
| **继承工具类 + 重写** | 重写工具方法 | 完全可控 | ✅ 可行 |
| **事件监听** | 监听框架事件 | 解耦最好 | ⚠️ 需框架支持 |

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
        captureService.recordToolObservation(ObservationRequest.builder()
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

#### ⭐ 4.2.6 最简配置 (一行代码捕获记忆)

> **📌 设计目标**: 以下配置为 Phase 3 Spring Boot Starter 实现后的使用方式。

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

**效果**: 启用后，`@Tool` 执行在 `CortexSessionContext` 激活时会自动捕获；会话结束 (session-end) 仍需显式调用 `recordSessionEnd`。参见 [4.3.8](#438-旁路型集成vectorstorechatmemoryadvisor-的借鉴与改进方向-2026-03-18)。

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
     * @return 质量分布统计 (QualityDistribution)
     */
    QualityDistribution getQualityDistribution(String projectPath);
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

自定义 Advisor，无缝集成到 Spring AI ChatClient。实现 `CallAdvisor` + `StreamAdvisor`（Spring AI 1.1）：

```java
/**
 * Cortex Memory Advisor
 * 
 * 自动将记忆检索结果注入到 AI 对话上下文中
 * 使用方式: chatClient.defaultAdvisors(cortexMemoryAdvisor).build()
 */
public class CortexMemoryAdvisor implements CallAdvisor, StreamAdvisor {
    
    private final CortexMemClient cortexClient;
    private final String projectPath;
    private final int maxExperiences;
    
    public static Builder builder(CortexMemClient client) {
        return new Builder(client);
    }
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest enriched = enrichRequest(request);  // 检索 + 注入 ICL
        return chain.nextCall(enriched);
    }
    
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest enriched = enrichRequest(request);
        return chain.nextStream(enriched);
    }
    
    // enrichRequest: 1) 提取 userText 2) 检索经验 3) buildICLPrompt 4) 注入 SystemMessage
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

> **📌 设计目标**: 以下配置为 Phase 3 Spring Boot Starter 实现后的使用方式。

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

**效果**: 启用后创建 CortexMemoryAdvisor Bean；需在 ChatClient 中通过 `defaultAdvisors(cortexMemoryAdvisor)` 显式添加后，每次调用才会自动检索并注入。参见 [6.1 快速集成](#61-快速集成-最小代码)。

| 对比 | 最简配置 | 完整配置 |
|------|---------|---------|
| 代码改动 | 1 行注解 | 10+ 行配置 |
| 适用场景 | 快速原型 / MVP | 生产级定制 |
| 灵活性 | 固定策略 | 完全可控 |

#### 4.3.7 CortexMemoryTools：Advisor vs Tool 模式设计讨论 (2026-03-18)

> **讨论背景**: 检索记忆有两种集成方式——Advisor 被动注入 vs Tool 按需调用。本节记录设计决策与默认行为。

**Advisor vs Tool 模式对比**

| 维度 | Advisor (CortexMemoryAdvisor) | Tool (CortexMemoryTools) |
|------|------------------------------|--------------------------|
| **触发时机** | 每次请求自动执行 | 仅当 AI 决定调用工具时执行 |
| **上下文注入** | 被动注入 ICL 到 System Prompt | AI 主动获取，按需填充上下文 |
| **Token 消耗** | 每次调用都会检索 + 注入 | 按需检索，更可控 |
| **适用场景** | 希望对话"自带记忆"的体验 | 希望 AI 自主决定何时查记忆 |
| **模式类比** | 类似 `QuestionAnswerAdvisor` 的被动注入（但数据源是经验，非文档） | Spring AI 的 `@Tool` 方法 |

**与 Spring AI 记忆组件的区别（非竞争关系）**

Cortex CE 记忆系统与 Spring AI 的 `VectorStoreChatMemoryAdvisor`、`QuestionAnswerAdvisor` 解决的是**不同问题**，可组合使用。

| 维度 | VectorStoreChatMemoryAdvisor | CortexMemoryAdvisor / Cortex CE |
|------|------------------------------|---------------------------------|
| **本质** | 聊天历史的 RAG — 对话消息作为知识库存入 VectorStore | 经验记忆的 ICL — 智能体执行轨迹作为经验检索 |
| **数据源** | 动态累积的对话轮次（user/assistant messages） | 工具执行记录、用户提示、会话摘要 |
| **存储/嵌入** | **自动**：After 阶段将新轮次转为 Document → embed → vectorStore.add()，无需额外代码 | **显式捕获**：需 AOP（CortexToolAspect）、API 调用等将观察写入后端 |
| **用途** | 长程语义对话记忆 — 如「用户曾提过 Lion 是猫」→ 查询「动物」时增强连贯性 | 经验复用 — 如「上次修 login bug 用 X 策略成功」→ 当前任务注入类似经验 |
| **检索对象** | 历史对话片段（chat history chunks） | 过去任务/策略/结果（experiences, observations） |
| **Spring AI 归类** | [RAG Advisors](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) 之一，与 QuestionAnswerAdvisor 并列 | 独立经验检索，非通用 RAG |

**结论**：`VectorStoreChatMemoryAdvisor` 面向**对话记忆**（chat history RAG）；Cortex CE 面向**智能体经验记忆**（experience ICL）。二者解决不同问题，**互补而非竞争**。可同时使用：前者负责对话语义检索，后者负责跨会话经验注入。

**决策：新增 CortexMemoryTools**

除 Advisor 外，增加 `CortexMemoryTools` 作为可选工具集，理由：

1. **按需检索**：AI 可自主决定何时搜索记忆，降低不必要的 Token 消耗
2. **灵活性**：用户可选择只用 Advisor、只用 Tools、或两者结合
3. **与 MCP 对齐**：Cortex CE MCP 提供 `search`、`timeline`、`get_observations` 等工具，Tool 模式与之语义一致

**默认注入行为（重要）**

| 问题 | 答案 |
|------|------|
| **CortexMemoryTools 默认会直接注入么？** | **不会**。Spring AI 的 Tool 不会自动加入 `ChatClient`。即使用户引入了 `cortex-mem-spring-ai`，Tools 也不会出现在 `defaultTools()` 中。 |
| **如果用户不想要呢？** | **无需做任何事**。只要不将 `CortexMemoryTools` 传入 `ChatClient.defaultTools(...)` 或 `prompt().tools(...)`，就不会被使用。 |

**Bean 创建策略（可选）**

可提供配置项 `cortex.mem.memory-tools-enabled` 控制是否创建 `CortexMemoryTools` Bean：

- **默认 `false`**：更保守，引入依赖后不会多出任何 Tools 相关 Bean
- **默认 `true`**：Bean 存在，用户只需 `defaultTools(cortexMemoryTools)` 即可使用；不使用则忽略即可

**推荐**：默认 `false`，显式启用后才有 Tools Bean；使用时需在 ChatClient 中显式添加，双重 opt-in 更清晰。

**使用示例（启用后）**

```java
// 1. application.yml
cortex:
  mem:
    memory-tools-enabled: true  # 启用 Tools Bean

// 2. ChatClient 显式添加
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             CortexMemoryTools memoryTools) {
    return builder
        .defaultTools(memoryTools)  // ← 显式添加，用户完全可控
        .build();
}
```

#### 4.3.8 旁路型集成：VectorStoreChatMemoryAdvisor 的借鉴与改进方向 (2026-03-18)

> **背景**：VectorStoreChatMemoryAdvisor 提供 Before（检索）+ After（自动持久化）一体化流程，全程自动、零样板代码。本节分析其对 Cortex CE 旁路型集成的借鉴价值及改进空间。

**VectorStoreChatMemoryAdvisor 的可借鉴点**

| 特性 | VectorStoreChatMemoryAdvisor | 对旁路型集成的启发 |
|------|------------------------------|---------------------|
| **一体化生命周期** | Before 检索 + After 持久化，同一 Advisor 完成 | 检索已集成在 Advisor；捕获分散在 AOP、Advisor、手动调用，可考虑更统一的入口 |
| **零 context 样板** | 仅需 `advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))`，无需 begin/end | 用户 prompt 已支持 conversationId；**@Tool 捕获**仍强制依赖 `CortexSessionContext.begin/end` |
| **persistOnCompletion** | 每次响应后自动 persist，默认 true | 我们的「persist」是发送到后端（异步 LLM 提取），语义不同；无直接等价物 |
| **单一入口** | 添加一个 Advisor 即可获得检索+存储 | 我们需 Advisor + AOP + 可选 session 生命周期调用，入口较多 |

**当前旁路型集成评估**

| 能力 | 状态 | 说明 |
|------|------|------|
| **检索** | ✅ 到位 | CortexMemoryAdvisor 自动注入 ICL；CortexMemoryTools 按需检索 |
| **User prompt 捕获** | ✅ 到位 | Advisor 内自动捕获，支持 conversationId 或 CortexSessionContext |
| **@Tool 捕获** | ⚠️ 有前置条件 | 需 `CortexSessionContext` 激活，否则 AOP 不记录；用户须手动 begin/end |
| **Session 生命周期** | ❌ 全手动 | startSession、recordSessionEnd 需显式调用 |
| **Assistant 响应** | ❌ 无每轮捕获 | 仅 session-end 时传 last_assistant_message，无 per-turn 存储 |

**改进方向**

| 改进项 | 价值 | 难度 | 状态 |
|--------|------|------|------|
| **CortexSessionContextBridgeAdvisor** | 高 | 低 | ✅ 已实施 (2026-03-18) |
| **Session 生命周期 Helper** | 中 | 低 | ⏳ 待实施 — Filter/注解，如首次请求自动 startSession、超时自动 recordSessionEnd；见 `cortex-mem-integration-capture-analysis.md` 4.3 |
| **persistOnCompletion 语义扩展** | 中 | 高 | ⏳ 待实施 — 若后端支持轻量「记录单轮」API，Advisor After 阶段可考虑自动上报 |
| **配置与文档** | 中 | 低 | ⏳ 待实施 — 明确「最小集成」与「完整集成」的差异 |

**结论**：**CortexSessionContextBridgeAdvisor** 已实施。当 `ChatMemory.CONVERSATION_ID` 存在时，Bridge 自动 `begin/end` CortexSessionContext，使 @Tool 捕获在纯 ChatClient 场景下无需手动 context。配置 `cortex.mem.context-bridge-enabled=true`（默认）启用；在 ChatClient 中将 Bridge 置于 CortexMemoryAdvisor 之前。

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
     * 初始化/恢复会话。POST /api/session/start
     */
    Map<String, Object> startSession(SessionStartRequest request);
    
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
    ICLPromptResult buildICLPrompt(ICLPromptRequest request);
    
    // ==================== 管理相关 ====================
    
    /**
     * 触发记忆精炼
     */
    void triggerRefinement(String projectPath);
    
    /**
     * 获取质量分布
     */
    QualityDistribution getQualityDistribution(String projectPath);
    
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
│   │   ├── advisor/
│   │   │   ├── CortexMemoryAdvisor.java
│   │   │   └── CortexSessionContextBridgeAdvisor.java
│   │   ├── aspect/
│   │   │   └── CortexToolAspect.java
│   │   └── tools/
│   │       └── CortexMemoryTools.java
│   └── pom.xml
│
└── cortex-mem-starter/         # Spring Boot Starter
    ├── src/main/java/.../
    │   └── autoconfigure/
    │       └── CortexMemAutoConfiguration.java
    └── pom.xml
```

### 5.2 实现阶段

#### Phase 1: 基础客户端 ✅ 已完成

- [x] 设计 DTO 模型类 (7 个 record)
- [x] 实现 CortexMemClient 接口 (REST Client)
- [x] 使用 Spring REST Client (同步编程，更简单)
- [x] 添加重试和超时配置
- [ ] 单元测试

#### Phase 2: Spring AI 集成服务 ✅ 已完成

- [x] 实现 ObservationCaptureService (捕获)
- [x] 实现 MemoryRetrievalService (检索)
- [x] 实现 CortexMemoryAdvisor (Spring AI 1.1 CallAdvisor + StreamAdvisor)
- [x] 实现 CortexToolAspect (AOP @Tool 自动捕获)
- [ ] 单元测试

#### Phase 3: Spring Boot Starter ✅ 已完成

- [x] 定义配置属性类 (CortexMemProperties)
- [x] 实现自动配置 (CortexMemAutoConfiguration)
- [x] 健康检查集成 (CortexMemHealthIndicator)
- [x] @EnableCortexMem 注解
- [ ] 配置属性验证

#### Phase 4: 文档与发布 (待做)

- [ ] 编写使用文档
- [ ] 示例代码
- [ ] Maven Central 发布配置

---

## 6. 示例代码

> **📌 说明**: 以下示例为 Phase 3 Spring Boot Starter 实现后的使用方式。

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
cortex:
  mem:
    base-url: http://localhost:37777
    project-path: /path/to/your/project

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

```java
// 3. 使用 ChatClient (自动带记忆)
@RestController
class AiController {
    private final ChatClient chatClient;

    // Bridge 可选 (context-bridge-enabled=true 时存在)：可使 @Tool 捕获在 conversationId 场景下生效
    public AiController(ChatClient.Builder builder,
                        @Autowired(required = false) CortexSessionContextBridgeAdvisor bridgeAdvisor,
                        CortexMemoryAdvisor cortexMemoryAdvisor) {
        var b = builder.defaultSystem("You are a helpful assistant");
        this.chatClient = (bridgeAdvisor != null
            ? b.defaultAdvisors(bridgeAdvisor, cortexMemoryAdvisor)
            : b.defaultAdvisors(cortexMemoryAdvisor))
            .build();
    }

    @GetMapping("/chat")
    String chat(@RequestParam String message, @RequestParam(required = false) String conversationId) {
        var spec = conversationId != null && !conversationId.isBlank()
            ? chatClient.prompt().advisors(s -> s.param(ChatMemory.CONVERSATION_ID, conversationId)).user(message)
            : chatClient.prompt().user(message);
        return spec.call().content();
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
        ICLPromptResult iclResult = cortexMemClient.buildICLPrompt(
            ICLPromptRequest.builder()
                .task(userInput)
                .project(getProjectPath())
                .build());

        // 4. 调用 AI
        return chatClient.prompt()
            .system(iclResult != null ? iclResult.prompt() : "")
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
    
    # 可选: 每次请求检索的经验数量 (默认 4)
    default-experience-count: 4
    
    # 可选: 是否创建 CortexMemoryTools Bean (默认 false，需显式启用)
    # 启用后需在 ChatClient 中调用 defaultTools(cortexMemoryTools) 才会生效
    memory-tools-enabled: false
    
    # 可选: 是否创建 CortexSessionContextBridgeAdvisor (默认 true)
    # 启用后，当 ChatMemory.CONVERSATION_ID 存在时自动 begin/end CortexSessionContext，使 @Tool 捕获在纯 ChatClient 场景下无需手动 context
    context-bridge-enabled: true
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

**文档状态**: 🟢 Phase 1-3 已实施 + CortexMemoryTools 已实现 — 2026-03-18

**实施进度**: 详见 `docs/drafts/spring-ai-integration-progress.md`

**CortexMemoryTools** (2026-03-18): `cortex-mem-spring-ai/tools/CortexMemoryTools.java` — `searchMemories`, `getMemoryContext`；配置 `memory-tools-enabled` 控制 Bean 创建；Demo 支持 `?useTools=true`。

**CortexSessionContextBridgeAdvisor** (2026-03-18): ✅ 已实施 — 当 `ChatMemory.CONVERSATION_ID` 存在时自动 begin/end CortexSessionContext，使 @Tool 捕获在纯 ChatClient 场景下无需手动 context；`cortex.mem.context-bridge-enabled=true`（默认）。

**改进方向**: 见 [4.3.8](#438-旁路型集成vectorstorechatmemoryadvisor-的借鉴与改进方向-2026-03-18) — Session 生命周期 Helper（中优）。

**下一步**: 单元测试覆盖 + 使用文档完善
