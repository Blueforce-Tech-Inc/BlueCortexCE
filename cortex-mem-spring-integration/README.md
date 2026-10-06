> 中文版: [README-zh-CN.md](./README-zh-CN.md)

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
| **196 unit tests** | Comprehensive coverage across client, advisor, tools, and auto-config layers (143 client + 46 spring-ai + 7 starter) |

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

**A session ID alone is not enough for a captured prompt to be findable again — it also needs a project path.** The two are independent: the session id decides *whether* a prompt is recorded, the project path decides *which project* it is filed under, and every project-scoped query filters on the latter. `CortexSessionContext.begin(sessionId, projectPath)` supplies both. The conversation-id path supplies only the first, so it is paired with `cortex.mem.project-path` (or the builder's `.projectPath(...)`).

That property has **no default**. Left unset, the auto-configuration substitutes the empty string, which is not `null`, so it survives the null check in `UserPromptRequest.toWireFormat()` and is sent as `"cwd": ""`:

```text
POST /api/ingest/user-prompt  {"session_id":"s1","cwd":"","prompt_text":"..."}
  -> 200 {"status":"ok"},  row persisted with project_path = ''
```

The backend accepts this and answers `200`, so nothing looks wrong. But the row is retrievable only by querying with an empty project, which no caller does — in practice the prompt is recorded and never recalled. This is merely the rare spelling of a broader situation: clients that omit `cwd` entirely store `NULL` instead, and `NULL` is the most common value in a real database. **Set `project-path` if you rely on capture.** Retrieval via the ICL path is unaffected either way.

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
| `project-path` | String | — | Project path. Unset means the empty string, which is stored as `project_path = ''` and is then unretrievable by project — see the capture section above |
| `connect-timeout` | Duration | `10s` | HTTP connect timeout |
| `read-timeout` | Duration | `30s` | HTTP read timeout |
| `default-experience-count` | int | `4` | Max experiences per retrieval |
| `capture-enabled` | boolean | `true` | Enable @Tool observation capture (CortexToolAspect) |
| `capture-user-prompt-enabled` | boolean | `true` | Enable user prompt auto-capture (CortexMemoryAdvisor). Independent of capture-enabled. |
| `retrieval-enabled` | boolean | `true` | Enable memory retrieval |
| `memory-tools-enabled` | boolean | `false` | Create CortexMemoryTools bean. Tools are not auto-injected — add via `ChatClient.defaultTools(cortexMemoryTools)`. |
| `context-bridge-enabled` | boolean | `true` | Create CortexSessionContextBridgeAdvisor. When CONVERSATION_ID is set, auto begin/end CortexSessionContext so @Tool capture works without manual context. |
| `retry.max-attempts` | int | `3` | Total **attempts** for the 10 methods that retry — see the retry-scope note below |
| `retry.backoff` | Duration | `500ms` | Base backoff between retries (linear `backoff × attempt`, ±25% jitter) |

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

### 5. Direct Client Access

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

### Empty Updates Are Rejected

`updateObservation(id, update)` throws `IllegalArgumentException` when `update` sets
no field, and no request is sent. This is the same rule the Go, Python and JS SDKs
apply, with the same message:

```
at least one field must be provided for update
```

It matters because a PATCH that sets nothing is a silent no-op on the wire: without
the check, a caller who assembled an empty update from user input would see the call
succeed and could not tell that nothing was written.

### Required Arguments Are Checked Client-Side

Every argument below must be non-blank. The client throws `IllegalArgumentException`
and sends no request. The Go, Python and JS SDKs enforce exactly the same set.

| Method | Required arguments |
|--------|--------------------|
| `startSession` | `request.sessionId()`, `request.projectPath()` |
| `updateSessionUserId` | `sessionId`, `userId` |
| `recordObservation` | `request.sessionId()`, `request.projectPath()`, `request.toolName()` |
| `recordSessionEnd` | `request.sessionId()`, `request.projectPath()` |
| `recordUserPrompt` | `request.sessionId()`, `request.promptText()`, `request.projectPath()` |
| `retrieveExperiences` | `request.task()` |
| `buildICLPrompt` | `request.task()` |
| `search` | `request.project()` |
| `getObservation` | `observationId` |
| `getObservationsByIds` | `ids` — non-empty, at most 100, no blank element |
| `triggerRefinement` | `projectPath` |
| `submitFeedback` | `observationId`, `feedbackType` |
| `updateObservation` | `observationId`, plus at least one field to change |
| `deleteObservation` | `observationId` |
| `getQualityDistribution` | `projectPath` |
| `triggerExtraction` | `projectPath` |
| `getLatestExtraction` | `projectPath`, `templateName` |
| `getExtractionHistory` | `projectPath`, `templateName`; `limit` must not be negative |

The capture and retrieval methods take a request record and read their arguments
through its accessors; the management and extraction methods take them positionally.

**A blank `project` on `retrieveExperiences` / `buildICLPrompt` is the one gap in
this table worth knowing about.** The table is accurate — neither method
validates `project`, while `search` does — but it records what is *checked*, not
what *happens*, and the difference matters here. `POST /api/memory/experiences`
and `POST /api/memory/icl-prompt` pass the value straight into the repository
query with **no cross-project branch**, so a missing or empty project matches
nothing and returns `200` with an empty result rather than an error. Verified
live: omitting `project`, sending `""`, and sending a non-existent path all
return `200 []`, while a real path returns experiences. This matters more here
than in the other SDKs, because `CortexMemoryTools.searchMemories` and
`getMemoryContext` resolve their project from `CortexSessionContext` and fall
back to the configured `cortex.mem.project-path` **without logging** — see the
`StreamAdvisor` note below. An unset project is therefore indistinguishable
from "this project genuinely has no memories".

The checks are not decoration, and the capture methods are the clearest case.
`recordObservation` is fire-and-forget, so it swallows whatever the backend replies:
an empty tool name comes back as `400 Missing required field: tool_name`, the client
logs it and returns, and the caller concludes the observation was captured when the
server had just rejected it. An empty project path is quieter still, because the
backend *accepts* it — the record is queued against no project and then appears in no
project-scoped query, with no error anywhere.

**The backend's own tool-use deduplication is the third capture hazard, and the only
one this client cannot see coming.** `POST /api/ingest/tool-use` identifies an event by
`(content_session_id, tool_name, SHA-256(tool_input))` and skips any event whose triple
is already present in a non-`failed` state. `toolResponse` and `promptNumber` are on the
wire but **not in that key**. So if the same tool is called twice with the same input
and a *different* result — re-running a command, re-reading a file, any retry that is
not a byte-identical resend — and the first is still being processed, the second is
**silently discarded** and the caller still receives `200 {"status":"accepted"}`,
which is byte-identical to the response for an event that really was queued. Verified
live: with an in-flight row planted on the same triple, a tool-use carrying a brand-new
`tool_response` returned `200 accepted` and left the table unchanged.

Two properties make this worse rather than better. First, the window is exactly as long
as the first event takes to process, so the outcome is **timing-dependent** — the same
two calls can both land or have the second dropped, run to run. Second, the check and
the insert are not atomic, and the unique constraint that the backend's own code
assumes exists (`uk_session_tool_input`) is **not** created by any migration, so
concurrent identical calls are not backstopped either: eight identical concurrent
`recordObservation` calls produced eight rows. Note the flip side, which is why the
SDK's retry is safe: a byte-identical resend *is* absorbed, which is exactly the
idempotency you want. The key simply cannot tell a resend from a new result.
`toolName` is also compared as free-form client text, so `Read` and `read` bypass the
check completely. Tracked as P2-29.

`search` has the same shape of hazard: the client always sends `project`, and
`GET /api/search?project=` answers `200` with an empty result set, so a caller who
forgot the argument would read "no matches" rather than "your call was malformed".

`listObservations` and the argument-free getters (`getStats`, `getProjects`,
`getModes`, `getSettings`, `getVersion`, `healthCheck`) require nothing. `getStats`
takes an optional project filter.

That is a statement about required arguments, not about blast radius, and on
this one method the two point in opposite directions. `listObservations` is the
only retrieval method whose project filter **widens** instead of emptying: omit
`project` — and pass an empty string if you like, because all four SDKs convert
that to omission — and no `project` is sent at all, so the backend answers with
observations from **every project on the instance**. Verified live against a
populated backend: `GET /api/observations` with no `project` returned 16
distinct projects inside a single 100-item page. A *direct* HTTP call with a
literal `?project=` is the opposite case and returns nothing, because the
repository query tests `IS NULL` rather than blank — worth knowing before you
bypass the SDK.

So this is the mirror image of the two ICL endpoints above: there a blank
project silently empties the result, here it silently over-broadens it. In a
multi-tenant deployment that is cross-tenant exposure rather than a missing
answer, and no client-side check will catch it, because the call is
well-formed.

## Wire Format

The SDK models the backend's actual wire shapes, which are not always what the field
names suggest:

- **List columns arrive as JSON-encoded strings.** `facts`, `concepts`, `files_read` and
  `files_modified` are JSONB columns that the backend serializes as **strings** for the
  TypeScript WebUI, so a live observation carries `concepts: "[\"allergy\",\"peanut\"]"`
  rather than a JSON array. The client decodes both shapes, so
  `ObservationResponse.concepts()` is always a real `List<String>`.
- **`refinedFromIds` is a plain String, not a list.** `mem_observations.refined_from_ids`
  is a `TEXT` column holding **comma-separated** UUIDs (`"uuid-1,uuid-2"`), not a JSONB
  list — the backend joins the ids with `,` and never JSON-encodes them. It is therefore
  exposed as `String refinedFromIds`, not `List<String>`.
- **Naming.** Record components are annotated with `@JsonProperty` where the wire name
  differs from the Java name: `content_session_id` → `sessionId`, `project` →
  `projectPath`, `extractedData` is camelCase, and most others are snake_case.

Go, Python and Java all decode these the same way; see the Go and Python SDK READMEs for
the same table in their own idioms.

## Error Handling

The SDK's error behaviour is **deliberately not uniform**, because two different callers
need different things. Knowing which is which matters before you write a `try`/`catch`.

**Propagation** — these throw `RuntimeException` carrying the backend's own
`{"error": "..."}` text, so a failure is never confused with an empty result:

| Method | Why it propagates |
|--------|-------------------|
| `listObservations` | An empty page is indistinguishable from a query that matched nothing |
| `getObservationsByIds` | An empty list is indistinguishable from "none of those ids exist" |
| `getProjects` | An empty project list is indistinguishable from "this backend has none yet" |
| `startSession`, `updateObservation`, `deleteObservation`, `submitFeedback`, … | The caller must know the write happened |

**Graceful degradation** — these return an empty or zeroed result and log a warning,
because the Spring AI integration calls them from `@Tool` methods and from the
auto-configured health indicator, where a memory backend outage must not break the
agent's turn or take the application down:

| Method | Returns on failure |
|--------|--------------------|
| `retrieveExperiences` | empty list |
| `buildICLPrompt` | `ICLPromptResult("", 0)` |
| `getQualityDistribution` | all-zero `QualityDistribution` |
| `healthCheck` | `false` |

**Partial degradation** — these return a result that *marks* the fallback so a caller can
detect it: `search` and `getStats` add `"fell_back": true` plus an `"error"` key,
`getVersion` reports `"unknown"`, `getSettings` adds an `"error"` key, and `getModes`
returns the same keys `/api/modes` returns with empty `observation_types` /
`observation_concepts` plus an `"error"` key. `healthCheck` is in the table above because
its `false` is itself the signal.

```java
try {
    PagedObservationResponse page = client.listObservations(req);
    // page.items() is empty only because the query matched nothing
} catch (RuntimeException e) {
    // e.getMessage() carries the backend's reason, e.g. "project is required"
    log.warn("memory listing failed", e);
}
```

**The HTTP status code is not on the exception.** This is the one thing the table
above cannot tell you, and it is worth stating plainly: every failure arrives as a
plain `java.lang.RuntimeException`, and its message holds the backend's `error`
text but **not** the status. A 404 and a 500 produce the same exception type, so
code that needs to tell "no such observation" from "the backend is down" has to
walk the cause chain itself. Verified against a stub returning
`404 {"error":"Observation not found: abc"}`:

```
thrown   : java.lang.RuntimeException
message  : getObservationsByIds failed: Observation not found: abc
cause[0] : org.springframework.web.client.HttpClientErrorException$NotFound
           404 Not Found: "{"error":"Observation not found: abc"}"
```

```java
import org.springframework.web.client.RestClientResponseException;

static int statusOf(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
        if (t instanceof RestClientResponseException http) {
            return http.getStatusCode().value();
        }
    }
    return 0; // not an HTTP failure
}

try {
    client.getObservation(id);
} catch (RuntimeException e) {
    int status = statusOf(e);
    if (status == 404) { /* not found */ } else { /* 5xx, transport, ... */ }
}
```

This is a known asymmetry rather than an oversight you can code around for free:
the Go, Python and JS SDKs all raise a typed `APIError` carrying `statusCode`
(Go additionally unwraps 11 sentinel errors), and their clients never write this
loop. The Java demo carries its own copy of the helper above for exactly this
reason. Closing the gap means adding public exception types to this SDK, which is
an API addition rather than a fix, so it is tracked as P2-16 rather than changed
here.

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
ExtractionResponse extraction = client.getLatestExtraction(
    "/my-project", "user_preference", "alice");
// Returns: ExtractionResponse { status: "ok", template: "user_preference",
//   sessionId: "abc123", extractedData: { preferences: [...] }, createdAt: 1234567890,
//   observationId: "uuid", message: null }
// Use extraction.isFound() to check if extraction exists

// Get extraction history (all snapshots)
List<Map<String, Object>> history = client.getExtractionHistory(
    "/my-project", "user_preference", "alice", 10);

// Manually trigger extraction
client.triggerExtraction("/my-project");
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
| `updateSessionUserId()` | `PATCH /api/session/{sessionId}/user` | | ✅ NEW |
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
| `triggerExtraction()` | `POST /api/extraction/run` | | ✅ NEW |
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
- **Graceful degradation**: Most read operations do not throw on a backend failure — they
  return a synthesized value. This is deliberate (a memory layer must not break the AI
  pipeline), but it means "backend unreachable" and "no data" look identical *unless the
  method marks the failure*. Three reads are the exception and propagate instead —
  `listObservations`, `getObservationsByIds` and `getProjects` — because an empty result
  there is indistinguishable from a query that legitimately matched nothing.

  | Method | Behaviour on backend failure | Marks the failure? |
  |--------|-------------------------------|--------------------|
  | `retrieveExperiences` | returns empty list | no |
  | `buildICLPrompt` | returns prompt `""`, count `0` | no |
  | `getQualityDistribution` | returns all counts `0` | no |
  | `healthCheck` | returns `false` | yes (`false` vs `true`) |
  | `getVersion` | returns `{"service": "unknown", "version": "unknown"}` | yes |
  | `search` | returns `{"observations": [], "strategy": "none", "fell_back": true, "count": 0, "error": "<message>"}` | yes |
  | `getStats` | returns `{"error": "<message>", "fell_back": true}` | yes |
  | `getSettings` | returns `{"settings": {}, "error": "<message>"}` | yes |
  | `getModes` | returns the same keys `/api/modes` returns, with empty `observation_types` / `observation_concepts` plus an `"error"` key | yes |

  The first three rows are indistinguishable from a genuinely empty answer, so a caller
  that needs to tell them apart must call `healthCheck()` (or `getVersion()` and look for
  `version: "unknown"`) first. `getModes` is listed separately from `getProjects` because
  only the former degrades.

  Write and mutation methods propagate instead of degrading: `startSession`,
  `updateSessionUserId`, `submitFeedback`, `updateObservation`, `deleteObservation`,
  `triggerRefinement`, `triggerExtraction`, and the capture calls `recordObservation`,
  `recordUserPrompt`, `recordSessionEnd`.
- **Retry scope is neither "everything" nor "captures only"**: 10 of the 25 public methods
  retry, with linear backoff (`retry.backoff × attempt`, ±25% jitter). Five capture/async
  calls swallow their errors once the attempts are exhausted (`recordObservation`,
  `recordUserPrompt`, `recordSessionEnd`, `triggerRefinement`, `triggerExtraction`); three
  mutations propagate instead (`submitFeedback`, `updateObservation`, `deleteObservation`);
  and the only two **reads** that retry are `getLatestExtraction` and `getExtractionHistory`.
  The remaining 15 methods never retry — including every other read: `search`,
  `listObservations`, `getObservation`, `getObservationsByIds`, `getProjects`, `getStats`,
  `getModes`, `getSettings`, `getVersion`, `healthCheck`, `getQualityDistribution`,
  `retrieveExperiences`, `buildICLPrompt`, plus `startSession` and `updateSessionUserId`.
  A transient backend error therefore surfaces on the first attempt through most of the API.
  Retrying `recordObservation` is safe from the double-record point of view, because the
  backend's tool-use dedup absorbs a byte-identical resend — but the same key also
  absorbs a call whose *result* differs, so a retry is not what makes the capture path
  lossy here. See the deduplication hazard above.
- The other three SDKs retry **only** the capture path, so the same setting covers a
  smaller set of calls there: the two extraction reads retry on Java alone. A caller who
  ports a retry-avoidance workaround for those two methods needs a guard on Java only.
- **No per-user isolation on the automatic path.** If you run more than one user out of a single project, note that the memory injected into the agent is **project-wide, not user-scoped**. The backend does support scoping — `POST /api/memory/experiences` with a `userId` returns that user's experiences and an empty list for anyone else (verified: one observation under `alice` gives 1 for `alice`, 0 for `bob`) — and `ICLPromptRequest` / `ExperienceRequest` both carry `userId`, and `DefaultMemoryRetrievalService` already forwards it. But `CortexMemoryAdvisor` and the two `@Tool` read methods build their requests from `CortexSessionContext`, which holds only `sessionId`, `projectPath` and a prompt counter — **there is no `userId` field and no `begin()` overload that accepts one**. So the automatic path structurally cannot scope by user, and every agent in a project receives the same ICL context, including preferences recorded for someone else. Calling `client.buildICLPrompt(...)` yourself and setting `userId` does work; that is the escape hatch. Tracked as P2-13.
- **Conditional beans**: Advisor, AOP aspect, and health indicator are registered only when their dependencies (Spring AI, AOP, Actuator) are on the classpath.
- **Spring AI 1.1**: Uses `CallAdvisor` / `StreamAdvisor` and `ChatClientRequest` (not legacy `CallAroundAdvisor`).
- **No response size cap**: the client uses Spring 6's `RestClient` (backed by a `java.net.http.HttpClient`), which deserializes the whole body at once, so a very large response is bounded only by heap. The Go and JS SDKs cap at 10 MiB and raise an explicit error; this one does not. Keep `limit` modest when searching or listing large observation sets.
- **Streaming (`StreamAdvisor`) has no session propagation**: `CortexSessionContext` is a plain `ThreadLocal`, but a streaming model call runs on a different thread than the one that invoked the advisor. Three consequences: `@Tool` auto-capture is **silently skipped** during streaming; the invoking thread's session context is not released (a later request served by that same pooled thread can be attributed to the previous conversation); and the two `@Tool` **read** methods — `searchMemories` and `getMemoryContext` — fall back to the configured `cortex.mem.project-path` instead of the live conversation's project, **without logging anything**. That third one is worth spelling out because neither outcome announces itself. If `project-path` is set, the agent is handed memories from a *different* project and attributes them to this conversation. If it is unset, the tools send an empty project, the backend answers `200` with an empty list, and the tool reports "No relevant past experiences found" — indistinguishable from a project that genuinely has no history. Verified live: `POST /api/memory/experiences` with `project: ""` returns `200 []`, while the same request with a real project path returns 5 experiences. This affects `CortexSessionContextBridgeAdvisor` with `ChatClient.stream()`. If you need `@Tool` auto-capture, use the synchronous `.call()` — it runs entirely on the calling thread and is unaffected. Tracked as P1-1 in [`docs/drafts/backend-review-findings.md`](../docs/drafts/backend-review-findings.md).

## See Also

- [中文版](./README-zh-CN.md)

## License

Same as the parent BlueCortexCE project.
