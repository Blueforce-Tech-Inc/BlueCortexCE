# Cortex Memory Demo

Spring Boot application demonstrating **cortex-mem-spring-integration**: project isolation, session lifecycle, and multiple capture types.

## Prerequisites

- **Java 21+**
- **Cortex CE backend** running on `http://localhost:37777`
- **OpenAI API key** (or compatible API)

## Quick Start

### 1. Build & Run

```bash
# Local integration (with startSession, recommended for development)
cd cortex-mem-spring-integration && mvn install -DskipTests
cd examples/cortex-mem-demo
mvn spring-boot:run -Plocal

# Or JitPack (requires push to trigger build)
mvn spring-boot:run
```

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
| `GET /chat?message=...&project=project-a` | Memory-augmented chat by project |
| `GET /demo/tool?path=...&project=project-a` | Tool call scoped to project |
| `GET /actuator/health` | Health check |

### 4. E2E Test

```bash
# Requires backend (37777) and demo (37778) running
./e2e/run-e2e.sh
```

## Features Demonstrated

1. **Project isolation** — Configure `demo.projects`, switch via `?project=`
2. **Programmatic project switch** — `CortexSessionContext.begin(sessionId, projectPath)` or request params
3. **Session lifecycle** — start → user-prompt → tool-use → session-end
4. **Multiple capture types** — user prompt, tool observation, session end
5. **@Tool auto-capture** — AOP interception; requires separate Bean to avoid self-invocation
6. **Memory retrieval** — experiences, ICL, quality; all support `project` param

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
