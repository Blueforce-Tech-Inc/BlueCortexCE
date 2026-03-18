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

Session ID resolution (aligned with Spring AI `ChatMemory.CONVERSATION_ID`):

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

### 1. Automatic @Tool Capture (AOP)

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

### 2. Manual Capture

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

### 3. Manual Retrieval (Without Advisor)

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

## Build & Example

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

For a full working example (Chat, Tools, Session lifecycle, E2E tests), see `examples/cortex-mem-demo` in this repository.

## Backend API Alignment

The client talks to these Cortex CE endpoints:

| Client Method | Backend Endpoint |
|---------------|------------------|
| `startSession()` | `POST /api/session/start` |
| `recordObservation()` | `POST /api/ingest/tool-use` |
| `recordSessionEnd()` | `POST /api/ingest/session-end` |
| `recordUserPrompt()` | `POST /api/ingest/user-prompt` |
| `retrieveExperiences()` | `POST /api/memory/experiences` |
| `buildICLPrompt()` | `POST /api/memory/icl-prompt` |
| `triggerRefinement()` | `POST /api/memory/refine` |
| `submitFeedback()` | `POST /api/memory/feedback` |
| `getQualityDistribution()` | `GET /api/memory/quality-distribution` |
| `healthCheck()` | `GET /actuator/health` |

## Common Pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| Tool calls not captured | `@Tool` invoked via self-invocation | Move `@Tool` to a separate `@Component` and inject it |
| User prompts not captured | No session ID provided | Use Option A (conversation ID) or Option B (CortexSessionContext) |
| No ICL context injected | Backend unreachable or `retrieval-enabled=false` | Check `base-url`, ensure backend is running |
| Advisor not registered | Spring AI not on classpath | Add `spring-ai-starter-model-openai` (or similar) |

## Design Notes

- **Fire-and-forget capture**: Capture operations log failures but never throw, so the AI pipeline is never blocked.
- **Graceful degradation**: Retrieval failures return empty lists; ICL failures return an empty prompt.
- **Conditional beans**: Advisor, AOP aspect, and health indicator are registered only when their dependencies (Spring AI, AOP, Actuator) are on the classpath.
- **Spring AI 1.1**: Uses `CallAdvisor` / `StreamAdvisor` and `ChatClientRequest` (not legacy `CallAroundAdvisor`).

## License

Same as the parent BlueCortexCE project.
