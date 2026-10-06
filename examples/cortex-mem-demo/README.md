# Cortex Memory Demo

Spring Boot application demonstrating **cortex-mem-spring-integration**: project isolation, session lifecycle, and multiple capture types.

## Prerequisites

- **Java 21+**
- **Cortex CE backend** running on `http://localhost:37777`
- **OpenAI API key** (or compatible API)

## Quick Start

### 1. Build & Run

```bash
# Build the SDK from this repository first — the demo defaults to the `local`
# profile, which resolves cortex-mem-starter:1.0.0-SNAPSHOT from ~/.m2.
cd cortex-mem-spring-integration && mvn install -DskipTests

cd examples/cortex-mem-demo
mvn spring-boot:run

# `-Plocal` is now the default, so it may be passed explicitly if you prefer
mvn spring-boot:run -Plocal
```

> **About the `jitpack` profile**: it pins a fixed upstream commit
> (`6aa5de459c`, 2026-03-18) that predates `SessionStartRequest` and
> `CortexMemClient.updateSessionUserId`, so the demo does not compile against it.
> It is kept as an opt-in (`mvn -Pjitpack …`) for dependency-resolution
> experiments only — do not use it to build or run the demo.

### 2. Configuration

`application.yml`: `cortex.mem.project-path` is the default project; `demo.projects` maps logical names to paths.

### 3. Endpoints

Demo runs on `http://localhost:37778`.

| Endpoint | Description |
|----------|-------------|
| `GET /demo/projects` | List configured projects |
| `POST /demo/session/start?project=project-a` | Start session |
| `POST /demo/session/lifecycle?project=project-a` | Full flow: start → prompt → tool → end |
| `GET /memory/experiences?task=...&project=project-a` | Retrieve experiences by project |
| `GET /memory/icl?task=...&project=project-a` | Build ICL prompt |
| `GET /memory/icl/truncated?task=...&project=project-a&maxChars=4000` | ICL with adaptive truncation (V14) |
| `GET /memory/experiences/filtered?task=...&source=...` | Experiences with source filtering (V14) |
| `GET /demo/extraction/latest?template=...&project=...&userId=...` | Latest extraction result for user (V15) |
| `GET /demo/extraction/history?template=...&project=...&userId=...&limit=10` | Extraction history for user (V15) |
| `POST /demo/extraction/run?project=...` | Trigger extraction (V15) |
| `GET /memory/health?project=project-a` | Memory health check |
| `GET /chat?message=...&project=project-a` | Memory-augmented chat by project |
| `GET /demo/tool?path=...&project=project-a` | Tool call scoped to project |
| `GET /actuator/health` | Health check |

> **`?path=` is confined to the project root, and the demo listens on loopback only.**
> `FileReadTool.readFile` resolves every path against `demo.file-read-root`
> (default: the working directory) and refuses anything that escapes it —
> a path outside the root is rejected **before** the read, with no content in
> the response. Both a lexical `../` / absolute-path check and a symlink check
> run, because a symlink placed inside the root can otherwise point out of it.
> `?project=` still scopes only the *memory capture*, never the file read.
> Separately, `application.yml` sets `server.address` to
> `${SERVER_ADDRESS:127.0.0.1}`, so the server binds `127.0.0.1:37778` rather
> than every interface; the backend does the same. Overridable by exporting
> `SERVER_ADDRESS` if you deliberately want the demo reachable.
> This was P1-2 — recorded across many rounds as "do not copy the endpoint, or
> run it on a shared network" — and was fixed in round 294 after the user
> authorised it. The confinement is pinned by `FileReadToolTest`.

### 4. E2E Test

```bash
# Requires backend (37777) and demo (37778) running
# V14 demo tests (4 tests)
./scripts/demo-v14-test.sh

# V15 demo tests (3 tests - extraction endpoints)
./scripts/demo-v15-test.sh

# Phase 3 acceptance tests (20 tests, all pass with EXTRACTION_ENABLED=true)
EXTRACTION_ENABLED=true ./scripts/phase3-acceptance-test.sh
```

## Features Demonstrated

1. **Project isolation** — Configure `demo.projects`, switch via `?project=`
2. **Programmatic project switch** — `CortexSessionContext.begin(sessionId, projectPath)` or request params
3. **Session lifecycle** — start → user-prompt → tool-use → session-end
4. **Multiple capture types** — user prompt, tool observation, session end
5. **@Tool auto-capture** — AOP interception; requires separate Bean to avoid self-invocation
6. **Memory retrieval** — experiences, ICL, quality; all support `project` param
7. **V14: Source attribution** — Track observation origin (`source` field)
8. **V14: Adaptive truncation** — Control ICL prompt size with `maxChars`
9. **V14: Source & concept filtering** — Filter experiences by source or concepts
10. **Phase 3: Multi-user support** — `userId` on sessions for user-scoped memory
11. **Phase 3: Structured extraction** — LLM-driven extraction of user preferences, health info, etc.

## Dependency (JitPack)

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.Blueforce-Tech-Inc</groupId>
  <artifactId>BlueCortexCE</artifactId>
  <version>6aa5de459c</version>
</dependency>
```

For multi-module projects, you may need `cortex-mem-starter` as artifactId:

```xml
<artifactId>cortex-mem-starter</artifactId>
```

See [jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE](https://jitpack.io/#Blueforce-Tech-Inc/BlueCortexCE) for available versions.
