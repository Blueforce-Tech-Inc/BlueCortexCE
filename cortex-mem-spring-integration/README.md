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

### Maven

```xml
<!-- Full integration: Starter (client + Spring AI + auto-config) -->
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Client only (no Spring AI, no auto-config) -->
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> **Note**: Before using, run `mvn install` from the `cortex-mem-spring-integration` directory to install the libraries into your local Maven repository.

### Gradle (Kotlin DSL)

```kotlin
implementation("com.ablueforce.cortexce:cortex-mem-starter:1.0.0-SNAPSHOT")
```

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

```java
@RestController
class AiController {
    private final ChatClient chatClient;

    public AiController(ChatClient.Builder builder, CortexMemoryAdvisor advisor) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant.")
            .build()
            .mutate()
            .advisors(advisor)
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

Each request automatically retrieves relevant experiences from the memory backend and injects them into the system prompt as ICL context.

## Configuration

All properties are under `cortex.mem`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `base-url` | String | `http://localhost:37777` | Cortex CE backend URL |
| `project-path` | String | — | Project path (for memory isolation) |
| `connect-timeout` | Duration | `10s` | HTTP connect timeout |
| `read-timeout` | Duration | `30s` | HTTP read timeout |
| `default-experience-count` | int | `4` | Max experiences per retrieval |
| `capture-enabled` | boolean | `true` | Enable observation capture |
| `retrieval-enabled` | boolean | `true` | Enable memory retrieval |
| `retry.max-attempts` | int | `3` | Retry attempts for capture calls |
| `retry.backoff` | Duration | `500ms` | Base backoff between retries |

### Environment Variables

```bash
CORTEX_MEM_BASE_URL=http://localhost:37777
CORTEX_MEM_PROJECT_PATH=/my/project
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
│  │  │ (@Tool capture)      │    │ (ICL retrieval)          │  │  │
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

When capture is enabled and Spring AOP is on the classpath, any `@Tool`-annotated method is intercepted and its input/output is sent to the memory backend.

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

## Build

```bash
cd cortex-mem-spring-integration
mvn clean install -DskipTests
```

## Backend API Alignment

The client talks to these Cortex CE endpoints:

| Client Method | Backend Endpoint |
|---------------|------------------|
| `recordObservation()` | `POST /api/ingest/tool-use` |
| `recordSessionEnd()` | `POST /api/ingest/session-end` |
| `recordUserPrompt()` | `POST /api/ingest/user-prompt` |
| `retrieveExperiences()` | `POST /api/memory/experiences` |
| `buildICLPrompt()` | `POST /api/memory/icl-prompt` |
| `triggerRefinement()` | `POST /api/memory/refine` |
| `submitFeedback()` | `POST /api/memory/feedback` |
| `getQualityDistribution()` | `GET /api/memory/quality-distribution` |
| `healthCheck()` | `GET /actuator/health` |

## Design Notes

- **Fire-and-forget capture**: Capture operations log failures but never throw, so the AI pipeline is never blocked.
- **Graceful degradation**: Retrieval failures return empty lists; ICL failures return an empty prompt.
- **Conditional beans**: Advisor, AOP aspect, and health indicator are registered only when their dependencies (Spring AI, AOP, Actuator) are on the classpath.
- **Spring AI 1.1**: Uses `CallAdvisor` / `StreamAdvisor` and `ChatClientRequest` (not legacy `CallAroundAdvisor`).

## License

Same as the parent BlueCortexCE project.
