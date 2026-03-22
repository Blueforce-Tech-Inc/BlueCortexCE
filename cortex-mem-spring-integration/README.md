# Cortex Memory Spring Integration

A drop-in Spring Boot / Spring AI integration library for the **Cortex CE** memory system. Adds persistent context and experience-based retrieval (ExpRAG) to your AI agents with minimal code changes.

## Overview

Cortex CE is a memory backend that stores agent observations, generates summaries, and provides semantic retrieval. This library enables Spring AI applications to:

- **Capture** — Record tool executions, user prompts, and session events into the memory system
- **Retrieve** — Fetch relevant historical experiences for In-Context Learning (ICL)
- **Evolve** — Trigger memory refinement and submit quality feedback

## Features

| Feature | Description |
|---------|-------------|
| **One-line integration** | `@EnableCortexMem` + configuration properties |
| **Fire-and-forget capture** | Non-blocking, failure-tolerant recording of observations |
| **Spring AI Advisor** | Automatic ICL context injection into ChatClient calls |
| **CortexMemoryTools** | On-demand memory retrieval tools (`searchMemories`, `getMemoryContext`) — opt-in |
| **@Tool auto-capture** | AOP aspect intercepts `@Tool` methods and records executions |
| **Session context** | ThreadLocal-based session and project scope |
| **Health indicator** | Actuator integration for monitoring the memory backend |

## Requirements

- **Java 21+**
- **Spring Boot 3.3.x**
- **Spring AI 1.1.x** (optional, for Advisor integration)
- **Cortex CE backend** running (default: `http://localhost:37777`)

## Installation

We recommend using [JitPack](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE) for pre-built artifacts. Visit the JitPack page to see available versions (release tags, branch names, or commit hashes).

### Maven (JitPack)

Add the JitPack repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- Full integration: cortex-mem-starter (client + Spring AI + auto-config) -->
    <dependency>
        <groupId>com.github.Blueforce-Tech-Inc</groupId>
        <artifactId>BlueCortexCE</artifactId>
        <version>Tag</version>
    </dependency>
</dependencies>
```

Replace `Tag` with a release tag (e.g. `v1.0.0`), branch name (e.g. `main`), or commit hash. See [JitPack build history](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE) for available versions. For a working example, see [examples/cortex-mem-demo](../examples/cortex-mem-demo).

### Maven (Local build)

To build and install from source:

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

Then add the local dependency:

```xml
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.Blueforce-Tech-Inc:BlueCortexCE:Tag")
}
```

Replace `Tag` with a release tag, branch name, or commit hash (see [JitPack](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE)).

## Quick Start

### Step 1: Add the dependency (above)

### Step 2: Configure

```yaml
# application.yml
cortex:
  mem:
    base-url: http://localhost:37777
    project-path: /path/to/your/project
```

### Step 3: Enable

```java
@SpringBootApplication
@EnableCortexMem
public class MyAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }
}
```

### Step 4: Use ChatClient (memory-augmented)

`CortexMemoryAdvisor` is auto-configured when you use `cortex-mem-starter` with Spring AI on the classpath. Inject it and add it to your ChatClient:

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

Each request automatically **retrieves** relevant experiences (ICL context injection). User prompts are **auto-captured** only when `capture-user-prompt-enabled=true` and a session ID is available (see below).

**For user prompt capture**, provide a session ID via either method. Without it, retrieval works but prompts are not recorded:

Session ID resolution (aligned with Spring AI `ChatMemory.CONVERSATION_ID`). When `context-bridge-enabled=true` (default), `CortexSessionContextBridgeAdvisor` auto activates context:

1. **Spring AI conversation ID** — set via `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))`
2. **CortexSessionContext** — fallback when wrapping with `begin`/`end`

```java
// Option A: Spring AI conversation ID (aligns with MessageChatMemoryAdvisor)
chatClient.prompt()
    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
    .user(message)
    .call()
    .content();

// Option B: CortexSessionContext
CortexSessionContext.begin(sessionId, projectPath);
try {
    CortexSessionContext.incrementAndGetPromptNumber();
    return chatClient.prompt().user(message).call().content();
} finally { CortexSessionContext.end(); }
```

## Configuration

All properties are under `cortex.mem`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `base-url` | String | `http://localhost:37777` | Cortex CE backend URL |
| `project-path` | String | — | Project path (for memory isolation) |
| `connect-timeout` | Duration | `10s` | HTTP connect timeout |
| `read-timeout` | Duration | `30s` | HTTP read timeout |
| `default-experience-count` | int | `4` | Max experiences per retrieval |
| `capture-enabled` | boolean | `true` | Enable @Tool observation capture (CortexToolAspect) |
| `capture-user-prompt-enabled` | boolean | `true` | Enable user prompt auto-capture (CortexMemoryAdvisor). Independent of capture-enabled. |
| `retrieval-enabled` | boolean | `true` | Enable memory retrieval |
| `memory-tools-enabled` | boolean | `false` | Create CortexMemoryTools bean. Tools are not auto-injected — add via `ChatClient.defaultTools(cortexMemoryTools)`. |
| `context-bridge-enabled` | boolean | `true` | Create CortexSessionContextBridgeAdvisor. When CONVERSATION_ID is set, auto begin/end CortexSessionContext so @Tool capture works without manual context. |
| `retry.max-attempts` | int | `3` | Retry attempts for capture calls |
| `retry.backoff` | Duration | `500ms` | Base backoff between retries |

### Environment Variables

```bash
CORTEX_MEM_BASE_URL=http://localhost:37777
CORTEX_MEM_PROJECT_PATH=/my/project
CORTEX_MEM_CAPTURE_ENABLED=true
CORTEX_MEM_CAPTURE_USER_PROMPT_ENABLED=true
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Your Spring AI Application                      │
├──────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              Cortex Memory Integration Layer               │  │
│  │  ┌─────────────────────┐    ┌──────────────────────────┐  │  │
│  │  │ CortexToolAspect     │    │ CortexMemoryAdvisor      │  │  │
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

## Usage Patterns

### 1. On-Demand Memory Tools (CortexMemoryTools)

When `memory-tools-enabled=true`, a `CortexMemoryTools` bean is created. Add it to your ChatClient for on-demand retrieval — the AI decides when to call `searchMemories` or `getMemoryContext`.

**Not auto-injected**: Tools are never added to ChatClient by default. You must explicitly call `defaultTools(cortexMemoryTools)`.

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
        .defaultTools(memoryTools)  // explicit opt-in
        .build();
}
```

Available tools:
- `searchMemories(task, count?)` — Search for relevant past experiences
- `getMemoryContext(task)` — Get ICL-formatted memory prompt

### 2. Automatic @Tool Capture (AOP)

When `capture-enabled=true` and Spring AOP is on the classpath, any `@Tool`-annotated method is intercepted and its input/output is sent to the memory backend.

**Important**: The `@Tool` method must be in a **separate `@Component`** bean. Self-invocation (calling `this.readFile()` from the same class) bypasses Spring AOP and will not be captured.

```java
@Component
class MyTools {
    @Tool(description = "Read a file")
    public String readFile(String path) {
        return Files.readString(Path.of(path));
    }
}
```

Ensure a session context is active:

```java
CortexSessionContext.begin(sessionId, projectPath);
try {
    // ... run agent with tools
} finally {
    CortexSessionContext.end();
}
```

### 3. Manual Capture

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

### 4. Manual Retrieval (Without Advisor)

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

### 4. Direct Client Access

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

## Modules

| Module | Description |
|--------|-------------|
| **cortex-mem-client** | REST client, DTOs, configuration properties. No Spring AI dependency. |
| **cortex-mem-spring-ai** | Advisor, capture/retrieval services, AOP aspect. Depends on Spring AI and client. |
| **cortex-mem-starter** | Spring Boot auto-configuration, `@EnableCortexMem`, health indicator. Depends on both above. |

## Phase 3: Multi-User & Structured Extraction

### userId Support

Create sessions with optional `userId` for multi-user memory isolation:

```java
// Session with userId
Map<String, Object> result = client.startSession(SessionStartRequest.builder()
    .sessionId("conv-123")
    .projectPath("/my-project")
    .userId("alice")  // Phase 3: multi-user identifier
    .build());

// Update userId on existing session (late binding)
client.updateSessionUserId("conv-123", "bob");
```

### Structured Extraction Query

Query LLM-extracted structured data by template name:

```java
// Get latest extraction for a user
Map<String, Object> extraction = client.getLatestExtraction(
    "/my-project", "user_preference", "alice");
// Returns: {"preferences": [{"category":"phone_brand","value":"小米","sentiment":"positive"}]}

// Get extraction history (all snapshots)
List<Map<String, Object>> history = client.getExtractionHistory(
    "/my-project", "user_preference", "alice", 10);
```

### ICL with userId

Build ICL prompts scoped to a specific user's extracted data:

```java
ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("推荐手机")
    .project("/my-project")
    .userId("alice")  // Phase 3: user-scoped context
    .maxChars(2000)
    .build());
```

### Experiences with userId

```java
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("推荐手机")
        .project("/my-project")
        .userId("alice")  // Phase 3: user-filtered
        .count(4)
        .build());
```

## V14 Features

### Source Attribution

Track the origin of each observation with the `source` field:

```java
client.recordObservation(ObservationRequest.builder()
    .sessionId(sessionId)
    .projectPath(projectPath)
    .toolName("search")
    .toolInput(Map.of("query", "Spring AI memory"))
    .source("tool_result")  // V14: source attribution
    .build());
```

### Structured Data Extraction

Store structured key-value data with `extractedData`:

```java
client.recordObservation(ObservationRequest.builder()
    .sessionId(sessionId)
    .projectPath(projectPath)
    .toolName("user_preference")
    .source("user_statement")
    .extractedData(Map.of(  // V14: structured key-value data
        "price_range", "3000",
        "brands", List.of("sony", "bose"),
        "category", "headphones"
    ))
    .build());
```

### Adaptive Truncation (maxChars)

Control ICL prompt size based on your model's context window:

```java
// Configure based on your model's context window
// 128K models: 8000-12000 chars
// 32K models: 4000-6000 chars
// 8K models: 2000-3000 chars

ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("fix login bug")
    .project("/my-project")
    .maxChars(4000)  // V14: adaptive truncation
    .build());
```

### Source & Concept Filtering

Filter experiences by source or required concepts:

```java
// Filter by source attribution
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("fix bug")
        .project("/my-project")
        .source("llm_inference")  // V14: source filtering
        .build());

// Filter by required concepts
List<Experience> verified = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("best approach")
        .project("/my-project")
        .requiredConcepts(List.of("verified", "tested"))  // V14: concept filtering
        .build());
```

### Memory Management Tools

Update or delete memories when the AI uses CortexMemoryTools:

```java
// updateMemory tool - AI can correct errors or mark important memories
// deleteMemory tool - AI can remove outdated or irrelevant memories
```

These tools are available when `memory-tools-enabled=true` and added to ChatClient.

## Build & Example

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

For a full working example (Chat, Tools, Session lifecycle, E2E tests), see `examples/cortex-mem-demo` in this repository.

## Backend API Alignment

The client talks to these Cortex CE endpoints:

| Client Method | Backend Endpoint | V14 | Phase 3 |
|---------------|-----------------|-----|---------|
| `startSession()` | `POST /api/session/start` | | ✅ userId |
| `updateSessionUserId()` | `PATCH /api/session/{id}/user` | | ✅ NEW |
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
| `getLatestExtraction()` | `GET /api/extraction/{template}/latest` | | ✅ NEW |
| `getExtractionHistory()` | `GET /api/extraction/{template}/history` | | ✅ NEW |
| `healthCheck()` | `GET /actuator/health` | | |

## Common Pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| Tool calls not captured | `@Tool` invoked via self-invocation | Move `@Tool` to a separate `@Component` and inject it |
| User prompts not captured | No session ID provided | Use Option A (conversation ID) or Option B (CortexSessionContext) |
| No ICL context injected | Backend unreachable or `retrieval-enabled=false` | Check `base-url`, ensure backend is running |
| Advisor not registered | Spring AI not on classpath | Add `spring-ai-starter-model-openai` (or similar) |
| Memory tools not available | `memory-tools-enabled=false` or not added to ChatClient | Set `memory-tools-enabled: true` and call `defaultTools(cortexMemoryTools)` |

## Design Notes

- **Fire-and-forget capture**: Capture operations log failures but never throw, so the AI pipeline is never blocked.
- **Graceful degradation**: Retrieval failures return empty lists; ICL failures return an empty prompt.
- **Conditional beans**: Advisor, AOP aspect, and health indicator are registered only when their dependencies (Spring AI, AOP, Actuator) are on the classpath.
- **Spring AI 1.1**: Uses `CallAdvisor` / `StreamAdvisor` and `ChatClientRequest` (not legacy `CallAroundAdvisor`).

## License

Same as the parent BlueCortexCE project.
