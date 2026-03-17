# Cortex Community Edition Development Guide

This guide provides comprehensive instructions for setting up and working with the Cortex CE development environment.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Development Environment Setup](#development-environment-setup)
  - [Installing Java 17+](#installing-java-17)
  - [Installing Maven](#installing-maven)
  - [Installing PostgreSQL](#installing-postgresql)
  - [Installing Node.js (for Thin Proxy)](#installing-nodejs-for-thin-proxy)
- [IDE Configuration](#ide-configuration)
  - [IntelliJ IDEA (Recommended)](#intellij-idea-recommended)
  - [VS Code](#vs-code)
- [Project Structure](#project-structure)
- [Building the Project](#building-the-project)
- [Running the Application](#running-the-application)
- [Coding Standards](#coding-standards)
  - [Java Conventions](#java-conventions)
  - [Spring Boot Best Practices](#spring-boot-best-practices)
  - [Code Formatting](#code-formatting)
- [Debugging](#debugging)
  - [Remote Debugging](#remote-debugging)
  - [Logging](#logging)
  - [Database Debugging](#database-debugging)
- [Testing](#testing)
  - [Running Tests](#running-tests)
  - [Writing Tests](#writing-tests)
  - [Test Coverage](#test-coverage)
- [Common Issues](#common-issues)
- [Performance Optimization](#performance-optimization)
- [Development Workflow](#development-workflow)

---

## Prerequisites

| Software | Version | Required | Purpose |
|----------|---------|----------|---------|
| JDK | 17+ | Yes | Java runtime |
| Maven | 3.8+ | Yes | Build tool |
| PostgreSQL | 16+ | Yes | Database |
| pgvector | 0.8+ | Yes | Vector extension |
| Node.js | 18+ | Optional | Thin Proxy |
| Git | 2.x | Yes | Version control |
| Docker | Latest | Optional | Container runtime |

---

## Development Environment Setup

### Installing Java 17+

#### macOS (Homebrew)

```bash
# Install OpenJDK 21 (recommended)
brew install openjdk@21

# Create symlink
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
     /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Verify installation
java -version
# openjdk version "21.0.x"

# Set JAVA_HOME (add to ~/.zshrc or ~/.bashrc)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

#### Linux (Ubuntu/Debian)

```bash
# Install OpenJDK 21
sudo apt update
sudo apt install openjdk-21-jdk

# Verify
java -version

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

#### Windows

```powershell
# Using Scoop
scoop install openjdk21

# Or download from:
# https://adoptium.net/temurin/releases/?version=21

# Set JAVA_HOME environment variable
# System Properties → Environment Variables → New
# Variable: JAVA_HOME
# Value: C:\Program Files\Eclipse Adoptium\jdk-21.x.x
```

### Installing Maven

#### macOS

```bash
brew install maven
mvn -version
```

#### Linux

```bash
sudo apt install maven
mvn -version
```

#### Windows

```powershell
scoop install maven
# Or use the Maven wrapper (./mvnw) included in the project
```

### Installing PostgreSQL

#### macOS

```bash
# Install PostgreSQL 16 with pgvector
brew install postgresql@16
brew install pgvector

# Start PostgreSQL
brew services start postgresql@16

# Create database
createdb cortexce_dev

# Enable pgvector extension
psql -d cortexce_dev -c "CREATE EXTENSION vector;"

# Verify extension
psql -d cortexce_dev -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

#### Linux

```bash
# Add PostgreSQL repository
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget -qO- https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo tee /etc/apt/trusted.gpg.d/pgdg.asc &>/dev/null

# Install
sudo apt update
sudo apt install postgresql-16 postgresql-16-pgvector

# Start service
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database
sudo -u postgres createdb cortexce_dev
sudo -u postgres psql -d cortexce_dev -c "CREATE EXTENSION vector;"
```

#### Using Docker (Alternative)

```bash
# Run PostgreSQL with pgvector
docker run -d \
  --name cortexce-postgres \
  -e POSTGRES_DB=cortexce_dev \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# Wait for startup
sleep 5

# Enable extension
docker exec -it cortexce-postgres psql -U postgres -d cortexce_dev -c "CREATE EXTENSION vector;"
```

### Installing Node.js (for Thin Proxy)

```bash
# macOS/Linux (using nvm)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.zshrc  # or ~/.bashrc
nvm install 18
nvm use 18

# Verify
node -v  # v18.x.x
npm -v   # 9.x.x

# Install proxy dependencies
cd proxy
npm install
```

---

## IDE Configuration

### IntelliJ IDEA (Recommended)

#### Installation

1. Download [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/) (free) or Ultimate (paid)
2. Install via Homebrew: `brew install --cask intellij-idea-ce`

#### Initial Setup

1. **Open Project**
   ```
   File → Open → Select java/claude-mem-java directory
   ```

2. **Configure JDK**
   ```
   File → Project Structure → Project
   SDK: 21 (java version "21.0.x")
   Language level: 21 - Record patterns, pattern matching for switch
   ```

3. **Enable Annotation Processing**
   ```
   Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   ✓ Enable annotation processing
   Obtain processors from the project classpath
   ```

4. **Install Plugins**
   - Lombok (required)
   - Spring Boot Assistant
   - JPA Buddy (optional, for entity design)
   - Database Navigator (optional)

5. **Code Style Configuration**
   ```
   Settings → Editor → Code Style → Java

   Tab size: 2
   Indent: 2
   Continuation indent: 4
   Line length: 120
   ```

   Import the code style file (if provided):
   ```
   Settings → Editor → Code Style → Scheme → Import Scheme → IntelliJ IDEA code style XML
   ```

6. **Configure Run Configuration**

   Create a new Spring Boot run configuration:
   ```
   Run → Edit Configurations → + → Spring Boot

   Main class: com.ablueforce.cortexce.CortexCeApplication
   Environment variables: Load from .env file

   Or use classpath module:
   Module: claude-mem-java
   ```

#### Useful IntelliJ Shortcuts

| Action | macOS | Windows/Linux |
|--------|-------|---------------|
| Find Action | `Cmd+Shift+A` | `Ctrl+Shift+A` |
| Go to Class | `Cmd+O` | `Ctrl+N` |
| Go to File | `Cmd+Shift+O` | `Ctrl+Shift+N` |
| Find Usages | `Alt+F7` | `Alt+F7` |
| Rename | `Shift+F6` | `Shift+F6` |
| Quick Fix | `Alt+Enter` | `Alt+Enter` |
| Format Code | `Cmd+Option+L` | `Ctrl+Alt+L` |
| Run Tests | `Ctrl+Shift+R` | `Ctrl+Shift+F10` |

### VS Code

#### Installation

```bash
brew install --cask visual-studio-code
```

#### Required Extensions

```bash
# Install via command line
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-spring-boot
code --install-extension vscjava.vscode-spring-initializr
code --install-extension vscjava.vscode-spring-boot-dashboard
code --install-extension GabrielBB.vscode-lombok
```

#### settings.json Configuration

Create `.vscode/settings.json` in project root:

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "spring-boot.ls.problem-application-properties.enabled": true,
  "editor.formatOnSave": true,
  "editor.tabSize": 2,
  "java.format.settings.url": ".vscode/java-formatter.xml",
  "[java]": {
    "editor.defaultFormatter": "redhat.java"
  }
}
```

#### launch.json Configuration

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Cortex CE Application",
      "request": "launch",
      "mainClass": "com.ablueforce.cortexce.CortexCeApplication",
      "projectName": "claude-mem-java",
      "envFile": "${workspaceFolder}/claude-mem-java/.env"
    },
    {
      "type": "java",
      "name": "Debug (Attach)",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

---

## Project Structure

```
java/
├── claude-mem-java/                    # Main Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ablueforce/cortexce/
│   │   │   │   │
│   │   │   │   ├── CortexCeApplication.java   # Main entry point
│   │   │   │   │
│   │   │   │   ├── config/             # Configuration classes
│   │   │   │   │   ├── AsyncConfig.java        # @EnableAsync config
│   │   │   │   │   ├── SpringAiConfig.java     # LLM/Embedding beans
│   │   │   │   │   ├── WebConfig.java          # CORS, filters
│   │   │   │   │   └── QueueHealthIndicator.java
│   │   │   │   │
│   │   │   │   ├── controller/         # REST Controllers
│   │   │   │   │   ├── IngestionController.java   # Hook events
│   │   │   │   │   ├── ViewerController.java      # WebUI API
│   │   │   │   │   ├── ContextController.java     # Context retrieval
│   │   │   │   │   ├── StreamController.java      # SSE streaming
│   │   │   │   │   ├── LogsController.java        # Logs API
│   │   │   │   │   └── TestController.java        # Debug endpoints
│   │   │   │   │
│   │   │   │   ├── service/            # Business Logic
│   │   │   │   │   ├── AgentService.java         # Core orchestration
│   │   │   │   │   ├── LlmService.java           # Chat completion
│   │   │   │   │   ├── EmbeddingService.java     # Vector embeddings
│   │   │   │   │   ├── SearchService.java        # Semantic search
│   │   │   │   │   ├── ContextService.java       # Context retrieval
│   │   │   │   │   ├── TimelineService.java      # Timeline assembly
│   │   │   │   │   ├── ClaudeMdService.java      # CLAUDE.md generation
│   │   │   │   │   ├── TokenService.java         # Token counting
│   │   │   │   │   └── RateLimitService.java     # Rate limiting
│   │   │   │   │
│   │   │   │   ├── repository/         # Data Access
│   │   │   │   │   ├── SessionRepository.java
│   │   │   │   │   ├── ObservationRepository.java
│   │   │   │   │   ├── SummaryRepository.java
│   │   │   │   │   └── UserPromptRepository.java
│   │   │   │   │
│   │   │   │   ├── entity/              # JPA Entities
│   │   │   │   │   ├── SessionEntity.java
│   │   │   │   │   ├── ObservationEntity.java
│   │   │   │   │   ├── SummaryEntity.java
│   │   │   │   │   └── UserPromptEntity.java
│   │   │   │   │
│   │   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   │   ├── ObservationDto.java
│   │   │   │   │   ├── SearchRequestDto.java
│   │   │   │   │   └── ContextResponseDto.java
│   │   │   │   │
│   │   │   │   ├── exception/           # Custom Exceptions
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   └── ValidationException.java
│   │   │   │   │
│   │   │   │   └── util/                # Utility Classes
│   │   │   │       ├── XmlParser.java
│   │   │   │       └── VectorValidator.java
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.yml      # Main configuration
│   │   │       ├── application-dev.yml  # Dev profile
│   │   │       ├── application-prod.yml # Production profile
│   │   │       │
│   │   │       ├── db/migration/        # Flyway migrations
│   │   │       │   ├── V1__init_schema.sql
│   │   │       │   ├── V2__multi_dimension_embeddings.sql
│   │   │       │   └── ...
│   │   │       │
│   │   │       └── prompts/             # LLM Prompt Templates
│   │   │           ├── init.txt         # System prompt
│   │   │           ├── observation.txt  # Observation prompt
│   │   │           └── summary.txt      # Summary prompt
│   │   │
│   │   └── test/
│   │       └── java/com/ablueforce/cortexce/
│   │           ├── service/
│   │           │   └── SearchServiceTest.java
│   │           ├── controller/
│   │           │   └── ObservationControllerTest.java
│   │           └── integration/
│   │               └── ObservationIntegrationTest.java
│   │
│   ├── pom.xml                          # Maven configuration
│   └── .env                             # Environment variables (gitignored)
│
├── proxy/                               # Thin Proxy (Node.js)
│   ├── wrapper.js                       # CLI entry point
│   ├── proxy.js                         # HTTP server (optional)
│   ├── package.json
│   └── CLAUDE-CODE-INTEGRATION.md
│
├── scripts/                             # Utility scripts
│   ├── regression-test.sh               # API tests
│   ├── thin-proxy-test.sh               # Proxy tests
│   └── webui-integration-test.sh        # WebUI tests
│
├── docs/                                # Documentation
│   ├── ARCHITECTURE.md
│   ├── API.md
│   └── DEVELOPMENT.md
│
├── README.md                            # English
├── README-zh-CN.md                      # Chinese
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
└── LICENSE
```

### Key Directories Explained

| Directory | Purpose |
|-----------|---------|
| `config/` | Spring configuration, bean definitions |
| `controller/` | REST API endpoints, HTTP handling |
| `service/` | Business logic, orchestration |
| `repository/` | Database access, JPA repositories |
| `entity/` | Database entities, ORM mappings |
| `dto/` | API request/response objects |
| `util/` | Helper classes, parsers, validators |
| `db/migration/` | Flyway SQL migrations |
| `prompts/` | LLM prompt templates |

---

## Building the Project

### Using Maven Wrapper (Recommended)

```bash
cd java/claude-mem-java

# Clean and compile
./mvnw clean compile

# Compile without tests (faster)
./mvnw compile -DskipTests

# Package (creates JAR)
./mvnw clean package

# Package without tests
./mvnw clean package -DskipTests

# Install to local Maven repository
./mvnw clean install
```

### Using System Maven

```bash
mvn clean package -DskipTests
```

### Build Profiles

```bash
# Development build
./mvnw clean package -DskipTests -Pdev

# Production build (with optimization)
./mvnw clean package -Pprod
```

### Common Build Issues

#### Out of Memory

```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2048m"
./mvnw clean package
```

#### Dependency Download Issues

```bash
# Force update dependencies
./mvnw clean package -U

# Clear local cache
rm -rf ~/.m2/repository
./mvnw clean package
```

---

## Running the Application

### Configuration

1. **Create .env file**

```bash
cd java/claude-mem-java
cp .env.example .env
```

2. **Edit .env**

```properties
# Database
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_URL=jdbc:postgresql://localhost:5432/cortexce_dev

# LLM (DeepSeek - OpenAI compatible)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# Embedding (SiliconFlow)
SILICONFLOW_API_KEY=sk-xxx
SILICONFLOW_MODEL=BAAI/bge-m3
SILICONFLOW_DIMENSIONS=1024
SILICONFLOW_URL=https://api.siliconflow.cn/v1/embeddings
```

### Run Commands

```bash
# Load environment and run
cd java/claude-mem-java
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar target/cortexce-*.jar

# Run with dev profile
java -jar target/cortexce-*.jar --spring.profiles.active=dev

# Run with specific port
java -jar target/cortexce-*.jar --server.port=8080

# Run with debug port
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/cortexce-*.jar
```

### Using Maven Spring Boot Plugin

```bash
# Run directly (no JAR needed)
./mvnw spring-boot:run

# Run with profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run with environment variables
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
./mvnw spring-boot:run
```

### Verify Application

```bash
# Health check
curl http://localhost:37777/actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## Coding Standards

### Java Conventions

#### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Class | PascalCase | `ObservationService` |
| Interface | PascalCase | `SearchRepository` |
| Method | camelCase | `findById()` |
| Variable | camelCase | `observationList` |
| Constant | SCREAMING_SNAKE | `MAX_RETRY_COUNT` |
| Package | lowercase | `com.ablueforce.cortexce.service` |

#### Class Organization

```java
package com.ablueforce.cortexce.service;

// 1. Imports (ordered alphabetically)
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ablueforce.cortexce.entity.Observation;
import com.ablueforce.cortexce.repository.ObservationRepository;

// 2. Class-level Javadoc
/**
 * Service for managing observations.
 *
 * <p>Provides CRUD operations and semantic search capabilities.
 *
 * @author Your Name
 * @since 1.0.0
 */
@Service
public class ObservationService {

    // 3. Constants (first)
    private static final int MAX_RESULTS = 100;
    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    // 4. Instance variables (dependency injection)
    private final ObservationRepository repository;
    private final EmbeddingService embeddingService;

    // 5. Constructor
    public ObservationService(ObservationRepository repository,
                              EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    // 6. Public methods (grouped logically)
    public List<Observation> findAll() { ... }

    public Optional<Observation> findById(String id) { ... }

    // 7. Private methods
    private void validate(Observation observation) { ... }
}
```

#### Use Records for DTOs

```java
// Good - Immutable, concise
public record ObservationDto(
    String id,
    String content,
    List<String> facts,
    List<String> concepts,
    float[] embedding
) {
    // Compact constructor for validation
    public ObservationDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(content, "content must not be null");
        facts = List.copyOf(facts);  // Defensive copy
    }
}

// Avoid - Mutable, verbose
public class ObservationDto {
    private String id;
    private String content;
    // getters, setters...
}
```

#### Prefer Immutability

```java
// Good - Immutable
public class Observation {
    private final String id;
    private final String content;
    private final Instant createdAt;

    public Observation(String id, String content) {
        this.id = id;
        this.content = content;
        this.createdAt = Instant.now();
    }

    // Only getters, no setters
    public String getId() { return id; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
```

#### Use Optional for Nullable Returns

```java
// Good - Explicit null handling
public Optional<Observation> findById(String id) {
    return repository.findById(id);
}

// Caller
service.findById(id)
    .map(Observation::getContent)
    .orElse("Not found");

// Avoid - Hidden null
public Observation findById(String id) {
    return repository.findById(id);  // May return null!
}
```

### Spring Boot Best Practices

#### Constructor Injection

```java
// Good - Constructor injection (required dependencies)
@Service
public class SearchService {
    private final ObservationRepository repository;
    private final EmbeddingService embeddingService;

    public SearchService(ObservationRepository repository,
                         EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }
}

// Avoid - Field injection
@Service
public class SearchService {
    @Autowired
    private ObservationRepository repository;  // Hidden dependency
}
```

#### Transaction Management

```java
@Service
public class ObservationService {

    // Read-only transaction for queries
    @Transactional(readOnly = true)
    public List<Observation> findByProject(String projectPath) {
        return repository.findByProjectPath(projectPath);
    }

    // Write transaction for modifications
    @Transactional
    public Observation save(Observation observation) {
        return repository.save(observation);
    }

    // Avoid long transactions
    @Transactional
    public void processLargeDataset() {
        // Bad: One transaction for millions of records
    }
}
```

#### Async Processing

```java
@Service
public class AgentService {

    // Fire-and-forget async
    @Async
    public void processToolUseAsync(ToolUseEvent event) {
        // Runs on virtual thread
        String response = llmService.chatCompletion(prompt);
        // ... process and save
    }

    // Async with result
    @Async
    public CompletableFuture<Observation> createObservationAsync(String content) {
        Observation obs = process(content);
        return CompletableFuture.completedFuture(obs);
    }
}
```

#### Exception Handling

```java
// Custom exception
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// Global exception handler
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Internal server error"));
    }
}
```

### Code Formatting

#### IntelliJ Formatter Settings

Create `.idea/codeStyles/Project.xml`:

```xml
<code_scheme name="Project" version="173">
  <JavaCodeStyleSettings>
    <option name="CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND" value="999" />
    <option name="NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND" value="999" />
  </JavaCodeStyleSettings>
  <codeStyleSettings language="JAVA">
    <option name="RIGHT_MARGIN" value="120" />
    <indentOptions>
      <option name="INDENT_SIZE" value="2" />
      <option name="CONTINUATION_INDENT_SIZE" value="4" />
    </indentOptions>
  </codeStyleSettings>
</code_scheme>
```

#### Using Spotless (Optional)

Add to `pom.xml`:

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.40.0</version>
  <configuration>
    <java>
      <googleJavaFormat>
        <version>1.18.1</version>
        <style>AOSP</style>
      </googleJavaFormat>
    </java>
  </configuration>
</plugin>
```

Run:
```bash
./mvnw spotless:check   # Check formatting
./mvnw spotless:apply   # Apply formatting
```

---

## Debugging

### Remote Debugging

#### Enable Debug Port

```bash
# Run with debug agent
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/cortexce-*.jar
```

#### Connect from IntelliJ

1. Run → Edit Configurations → + → Remote JVM Debug
2. Host: `localhost`
3. Port: `5005`
4. Click Debug

#### Connect from VS Code

The `.vscode/launch.json` configuration already includes the attach config.

### Logging

#### Log Levels

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.ablueforce.cortexce: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

#### Using SLF4J

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ObservationService {
    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    public Observation save(Observation observation) {
        log.debug("Saving observation: {}", observation.getId());
        Observation saved = repository.save(observation);
        log.info("Observation saved: {} in {}ms", saved.getId(), duration);
        return saved;
    }
}
```

#### Structured Logging with MDC

```java
import org.slf4j.MDC;

public void processEvent(String sessionId) {
    MDC.put("sessionId", sessionId);
    try {
        log.info("Processing event");
        // ... business logic
    } finally {
        MDC.clear();
    }
}
```

### Database Debugging

#### Enable SQL Logging

```yaml
# application-dev.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

#### Query Analysis

```sql
-- Enable query timing
\timing on

-- Explain query plan
EXPLAIN ANALYZE
SELECT * FROM mem_observations
ORDER BY embedding_1024 <=> '[0.1, 0.2, ...]'::vector
LIMIT 10;

-- Check index usage
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'mem_observations';
```

#### Database Console

```bash
# Connect to database
psql -d cortexce_dev

# Useful queries
\dt                           -- List tables
\d mem_observations           -- Describe table
\di                           -- List indexes
SELECT COUNT(*) FROM mem_observations;  -- Row count
```

---

## Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=SearchServiceTest

# Run specific test method
./mvnw test -Dtest=SearchServiceTest#testSemanticSearch

# Run with coverage
./mvnw test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Writing Tests

#### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ObservationRepository repository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private SearchService searchService;

    @Test
    void shouldReturnObservations_whenQueryProvided() {
        // Arrange
        String query = "test query";
        float[] embedding = {0.1f, 0.2f, 0.3f};
        List<Observation> expected = List.of(
            new Observation("1", "content 1"),
            new Observation("2", "content 2")
        );

        when(embeddingService.embed(query)).thenReturn(embedding);
        when(repository.findByVectorSimilarity(embedding, 10)).thenReturn(expected);

        // Act
        List<Observation> result = searchService.semanticSearch(query, 10);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expected);

        // Verify
        verify(embeddingService).embed(query);
        verify(repository).findByVectorSimilarity(embedding, 10);
    }

    @Test
    void shouldThrowException_whenQueryIsNull() {
        assertThatThrownBy(() -> searchService.semanticSearch(null, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Query must not be null");
    }
}
```

#### Integration Test Example

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class ObservationControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "pgvector/pgvector:pg16"
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObservationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateObservation() throws Exception {
        String requestBody = """
            {
              "content": "Test observation",
              "projectPath": "/test/project"
            }
            """;

        mockMvc.perform(post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.content").value("Test observation"));

        // Verify in database
        assertThat(repository.count()).isEqualTo(1);
    }
}
```

### Test Coverage

#### JaCoCo Configuration

```xml
<!-- pom.xml -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.11</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <rules>
      <rule>
        <element>PACKAGE</element>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.70</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</plugin>
```

---

## Common Issues

### Issue: Application Won't Start - Port Already in Use

```bash
# Find process using port
lsof -i :37777

# Kill process
kill -9 <PID>

# Or use one-liner
lsof -ti:37777 | xargs kill -9
```

### Issue: Database Connection Failed

```bash
# Check PostgreSQL is running
pg_isready

# Check connection
psql -h localhost -U postgres -d cortexce_dev -c "SELECT 1"

# Reset password
psql -U postgres -c "ALTER USER postgres PASSWORD 'new_password';"
```

### Issue: Flyway Migration Failed

```bash
# Check migration status
psql -d cortexce_dev -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"

# Repair (if needed)
./mvnw flyway:repair

# Manual fix
psql -d cortexce_dev -c "DELETE FROM flyway_schema_history WHERE success = false;"
```

### Issue: OutOfMemoryError

```bash
# Increase JVM heap
java -Xmx2g -jar target/cortexce-*.jar

# Or set in environment
export JAVA_OPTS="-Xmx2g -Xms1g"
```

### Issue: Lombok Not Working in IDE

1. Install Lombok plugin
2. Enable annotation processing
3. Restart IDE
4. Rebuild project

```bash
# IntelliJ
File → Settings → Plugins → Search "Lombok" → Install
File → Settings → Build → Compiler → Annotation Processors → Enable
```

### Issue: Tests Failing with "No qualifying bean"

```java
// Missing @SpringBootTest or @ExtendWith
@SpringBootTest
class MyTest { ... }

// Missing mock
@MockBean
private MyService myService;
```

---

## Performance Optimization

### Database Optimization

#### Index Tuning

```sql
-- Check missing indexes
SELECT
    schemaname, tablename,
    attname, n_distinct, correlation
FROM pg_stats
WHERE tablename = 'mem_observations';

-- Add index for common queries
CREATE INDEX idx_observations_project_created
ON mem_observations(project_path, created_at DESC);

-- Analyze query performance
EXPLAIN ANALYZE
SELECT * FROM mem_observations
WHERE project_path = '/my/project'
ORDER BY created_at DESC
LIMIT 100;
```

#### Connection Pooling

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
```

### JVM Tuning

```bash
# Production JVM options
java \
  -Xms1g -Xmx2g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxGCPauseMillis=10 \
  -jar target/cortexce-*.jar
```

### Caching

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10)));
        return cacheManager;
    }
}

// Usage
@Cacheable(value = "observations", key = "#id")
public Observation findById(String id) {
    return repository.findById(id).orElseThrow();
}
```

### Async Processing

```java
// Use virtual threads for I/O-bound operations
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

---

## Development Workflow

### Daily Development

```bash
# 1. Pull latest changes
git pull upstream main

# 2. Create feature branch
git checkout -b feat/my-feature

# 3. Make changes and test
./mvnw test

# 4. Run application
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
./mvnw spring-boot:run

# 5. Commit changes
git add .
git commit -m "feat: add my feature"

# 6. Push and create PR
git push origin feat/my-feature
```

### Before Submitting PR

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Code coverage maintained
- [ ] Documentation updated
- [ ] Commit messages follow conventions
- [ ] PR description complete

---

*Development guide version 1.0*
*Last updated: 2026*
