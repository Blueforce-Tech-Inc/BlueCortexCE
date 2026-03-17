# Contributing to Cortex Community Edition

First off, thank you for considering contributing to Cortex Community Edition! It's people like you that make Cortex CE such a great tool.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Environment Setup](#development-environment-setup)
  - [Building the Project](#building-the-project)
- [Development Guidelines](#development-guidelines)
  - [Project Structure](#project-structure)
  - [Code Style](#code-style)
  - [Java Conventions](#java-conventions)
  - [Spring Boot Best Practices](#spring-boot-best-practices)
- [Git Commit Guidelines](#git-commit-guidelines)
  - [Commit Message Format](#commit-message-format)
  - [Commit Types](#commit-types)
- [Pull Request Process](#pull-request-process)
  - [Creating a Pull Request](#creating-a-pull-request)
  - [PR Requirements](#pr-requirements)
  - [Review Process](#review-process)
- [Code Review Standards](#code-review-standards)
- [Testing Requirements](#testing-requirements)
  - [Unit Tests](#unit-tests)
  - [Integration Tests](#integration-tests)
  - [Test Coverage](#test-coverage)
- [Reporting Issues](#reporting-issues)
- [Feature Requests](#feature-requests)
- [Community](#community)

---

## Code of Conduct

This project and everyone participating in it is governed by the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [maintainers@email.com].

---

## Getting Started

### Prerequisites

Ensure you have the following installed:

| Software | Version | Purpose |
|----------|---------|---------|
| JDK | 17+ | Java Development Kit |
| Maven | 3.8+ | Build tool |
| PostgreSQL | 16+ | Database |
| pgvector | 0.8+ | Vector extension |
| Git | 2.x | Version control |
| Docker | Latest (optional) | Container runtime |

### Development Environment Setup

1. **Fork and Clone the Repository**

```bash
# Fork the repo on GitHub, then:
git clone https://github.com/YOUR_USERNAME/cortexce.git
cd cortexce/java
```

2. **Set Up PostgreSQL Database**

```bash
# macOS (Homebrew)
brew install postgresql@16
brew services start postgresql@16

# Create database
createdb cortexce_dev

# Enable pgvector
psql -d cortexce_dev -c "CREATE EXTENSION vector;"
```

3. **Configure Environment Variables**

Create a `.env` file in the project root:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:

```properties
# Database
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_URL=jdbc:postgresql://localhost:5432/cortexce_dev

# LLM API (choose one)
# Option 1: DeepSeek (OpenAI-compatible)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# Option 2: Anthropic-compatible
ANTHROPIC_API_KEY=sk-ant-xxx
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_MODEL=claude-sonnet-4-20250514

# Embedding Service
SILICONFLOW_API_KEY=sk-xxx
SILICONFLOW_MODEL=BAAI/bge-m3
SILICONFLOW_DIMENSIONS=1024
```

4. **IDE Setup (IntelliJ IDEA Recommended)**

- Install Lombok plugin
- Enable annotation processing
- Import as Maven project
- Set Project SDK to Java 17+

### Building the Project

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package (skip tests for faster build)
./mvnw clean package -DskipTests

# Run the application
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar claude-mem-java/target/cortexce-*.jar
```

---

## Development Guidelines

### Project Structure

```
java/
├── claude-mem-java/                    # Main Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ablueforce/cortexce/
│   │   │   │   ├── config/            # Configuration classes
│   │   │   │   ├── controller/        # REST controllers
│   │   │   │   ├── service/           # Business logic
│   │   │   │   ├── repository/        # Data access
│   │   │   │   ├── entity/            # JPA entities
│   │   │   │   ├── dto/               # Data transfer objects
│   │   │   │   ├── exception/         # Custom exceptions
│   │   │   │   └── util/              # Utility classes
│   │   │   └── resources/
│   │   │       ├── application.yml    # Application config
│   │   │       ├── db/migration/      # Flyway migrations
│   │   │       └── prompts/           # LLM prompt templates
│   │   └── test/                      # Test sources
│   └── pom.xml                        # Maven configuration
├── proxy/                             # Thin Proxy (Node.js)
├── scripts/                           # Utility scripts
└── docs/                              # Documentation
```

### Code Style

We follow standard Java conventions with some project-specific rules:

#### General Rules

- **Indentation**: 2 spaces (no tabs)
- **Line Length**: 120 characters max
- **Encoding**: UTF-8
- **End of Line**: LF (Unix style)

#### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `ObservationService` |
| Methods | camelCase | `getObservationById` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Packages | lowercase | `com.ablueforce.cortexce.service` |
| Variables | camelCase | `observationList` |

#### File Organization

```java
// 1. Package declaration
package com.ablueforce.cortexce.service;

// 2. Import statements (ordered)
import java.util.List;                    // Java standard
import org.springframework.stereotype.Service;  // Spring
import com.ablueforce.cortexce.entity.Observation;  // Project

// 3. Class-level Javadoc
/**
 * Service for managing observations.
 *
 * @author Your Name
 * @since 1.0.0
 */
@Service
public class ObservationService {

    // 4. Constants
    private static final int MAX_RESULTS = 100;

    // 5. Instance variables (dependency injection)
    private final ObservationRepository repository;

    // 6. Constructor
    public ObservationService(ObservationRepository repository) {
        this.repository = repository;
    }

    // 7. Public methods
    public List<Observation> findAll() {
        // Implementation
    }

    // 8. Private methods
    private void validateInput(String input) {
        // Implementation
    }
}
```

### Java Conventions

#### Use Final Fields for Immutability

```java
// Good
public class ObservationEntity {
    private final String id;
    private final String content;

    public ObservationEntity(String id, String content) {
        this.id = id;
        this.content = content;
    }
}

// Avoid mutable state where possible
```

#### Prefer Constructor Injection

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
    private ObservationRepository repository;  // Not recommended
}
```

#### Use Optional for Nullable Returns

```java
public Optional<Observation> findById(String id) {
    return repository.findById(id);
}
```

#### Use Meaningful Variable Names

```java
// Good
List<Observation> recentObservations = repository.findByOrderByCreatedAtDesc();
int retryCount = 0;

// Bad
List<Observation> list = repository.findByOrderByCreatedAtDesc();
int i = 0;
```

### Spring Boot Best Practices

#### Controller Layer

```java
@RestController
@RequestMapping("/api/observations")
public class ObservationController {

    private final ObservationService service;

    public ObservationController(ObservationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObservationDto> getById(@PathVariable String id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

#### Service Layer

```java
@Service
@Transactional
public class ObservationService {

    @Transactional(readOnly = true)
    public List<Observation> findByProject(String projectPath) {
        return repository.findByProjectPath(projectPath);
    }

    public Observation save(Observation observation) {
        return repository.save(observation);
    }
}
```

#### Exception Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }
}
```

---

## Git Commit Guidelines

### Commit Message Format

We follow [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Structure

- **type**: What kind of change (required)
- **scope**: What module/component (optional)
- **subject**: Brief description (required)
- **body**: Detailed description (optional)
- **footer**: Breaking changes, issue references (optional)

### Commit Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat(search): add vector similarity search` |
| `fix` | Bug fix | `fix(embedding): handle null vectors correctly` |
| `docs` | Documentation | `docs(api): update OpenAPI documentation` |
| `style` | Code style (no logic change) | `style: format code with 2-space indent` |
| `refactor` | Code refactoring | `refactor(service): extract search logic` |
| `perf` | Performance improvement | `perf(query): optimize vector index usage` |
| `test` | Adding/updating tests | `test(search): add unit tests for SearchService` |
| `chore` | Maintenance tasks | `chore(deps): update Spring Boot to 3.3` |
| `ci` | CI/CD changes | `ci: add GitHub Actions workflow` |
| `revert` | Revert previous commit | `revert: undo feat(search) changes` |

### Examples

#### Simple Commit

```
feat(api): add health check endpoint
```

#### Commit with Body

```
fix(embedding): resolve dimension mismatch error

When using bge-m3 model with 1024 dimensions, the system was
incorrectly attempting to store in the 768-dimension column.

This fix routes embeddings to the correct column based on the
configured dimension.

Closes #123
```

#### Breaking Change

```
refactor(api)!: rename search endpoint parameters

BREAKING CHANGE: The `q` parameter has been renamed to `query`.
Update your API calls accordingly.

Migration guide:
- Old: GET /api/search?q=test
- New: GET /api/search?query=test
```

---

## Pull Request Process

### Creating a Pull Request

1. **Create a Feature Branch**

```bash
# Update main branch
git checkout main
git pull upstream main

# Create feature branch
git checkout -b feat/your-feature-name
```

2. **Make Your Changes**

```bash
# Make changes, commit frequently with good messages
git add .
git commit -m "feat(component): add new feature"

# Keep your branch updated
git fetch upstream
git rebase upstream/main
```

3. **Push and Create PR**

```bash
git push origin feat/your-feature-name
```

Go to GitHub and create a Pull Request.

### PR Requirements

Before submitting, ensure:

- [ ] Code compiles without errors
- [ ] All tests pass (`./mvnw test`)
- [ ] Code coverage maintained or improved
- [ ] Documentation updated if needed
- [ ] Commit messages follow conventions
- [ ] PR description is clear and complete
- [ ] Linked to relevant issues

### PR Description Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing functionality to change)
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No new warnings introduced
- [ ] Tests pass locally

## Related Issues
Fixes #123
Related to #456

## Screenshots (if applicable)
```

### Review Process

1. **Automated Checks**: CI must pass
2. **Code Review**: At least 1 approval required
3. **Address Feedback**: Respond to all comments
4. **Squash Commits**: If requested, squash before merge
5. **Merge**: Maintainer will merge your PR

---

## Code Review Standards

### For Reviewers

When reviewing code, check:

#### Functionality
- [ ] Does it solve the stated problem?
- [ ] Edge cases handled?
- [ ] Error handling appropriate?

#### Code Quality
- [ ] Follows project conventions?
- [ ] Readable and maintainable?
- [ ] No code duplication?
- [ ] Appropriate abstractions?

#### Testing
- [ ] Adequate test coverage?
- [ ] Tests are meaningful?
- [ ] Edge cases tested?

#### Performance
- [ ] No obvious performance issues?
- [ ] Database queries optimized?
- [ ] No memory leaks?

#### Security
- [ ] No security vulnerabilities?
- [ ] Input validation present?
- [ ] Sensitive data handled properly?

### For Authors

When receiving feedback:

- Be open to constructive criticism
- Respond to all comments
- Ask for clarification if needed
- Make requested changes promptly
- Explain your reasoning when you disagree

---

## Testing Requirements

### Unit Tests

- All new service methods must have unit tests
- Test both success and failure scenarios
- Use descriptive test names

```java
@Test
void shouldReturnObservation_whenIdExists() {
    // Arrange
    String id = "test-id";
    Observation expected = new Observation(id, "content");
    when(repository.findById(id)).thenReturn(Optional.of(expected));

    // Act
    Optional<Observation> result = service.findById(id);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(id);
}

@Test
void shouldReturnEmpty_whenIdNotFound() {
    // Arrange
    String id = "non-existent";
    when(repository.findById(id)).thenReturn(Optional.empty());

    // Act
    Optional<Observation> result = service.findById(id);

    // Assert
    assertThat(result).isEmpty();
}
```

### Integration Tests

- Test API endpoints end-to-end
- Use `@SpringBootTest` with test containers
- Clean up test data after each test

```java
@SpringBootTest
@Testcontainers
class ObservationControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateObservation() throws Exception {
        mockMvc.perform(post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }
}
```

### Test Coverage

- Minimum 70% line coverage for new code
- Critical paths should have 90%+ coverage
- Use JaCoCo for coverage reporting

```bash
# Generate coverage report
./mvnw jacoco:report

# View report
open target/site/jacoco/index.html
```

---

## Reporting Issues

### Bug Reports

When reporting bugs, include:

1. **Description**: Clear description of the bug
2. **Steps to Reproduce**: Detailed steps
3. **Expected Behavior**: What should happen
4. **Actual Behavior**: What actually happens
5. **Environment**: OS, Java version, Spring Boot version
6. **Logs**: Relevant error messages/stack traces
7. **Screenshots**: If applicable

Use this template:

```markdown
## Bug Description
A clear description of what the bug is.

## Steps to Reproduce
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened.

## Environment
- OS: [e.g., macOS 14]
- Java: [e.g., OpenJDK 21]
- Spring Boot: [e.g., 3.3.0]
- PostgreSQL: [e.g., 16.2]

## Logs
```
Paste relevant logs here
```

## Additional Context
Any other context about the problem.
```

---

## Feature Requests

When requesting features, include:

1. **Problem**: What problem does this solve?
2. **Solution**: Proposed solution
3. **Alternatives**: Other solutions considered
4. **Impact**: Who benefits?

Use this template:

```markdown
## Problem Statement
A clear description of what problem this feature would solve.

## Proposed Solution
Describe the feature you'd like to see.

## Alternatives Considered
Any alternative solutions you've considered.

## Additional Context
Any other context, mockups, or examples.

## Would you be willing to submit a PR?
[ ] Yes, I'd like to contribute this feature
```

---

## Community

### Getting Help

- **GitHub Discussions**: For questions and general discussion
- **GitHub Issues**: For bug reports and feature requests
- **Documentation**: [https://docs.cortexce.ai](https://docs.cortexce.ai)

### Stay Updated

- Watch the repository for releases
- Star the project to show support
- Follow contributors for updates

---

## Recognition

Contributors are recognized in:

- `CONTRIBUTORS.md` file
- Release notes for significant contributions
- GitHub's contributor graph

Thank you for contributing to Cortex Community Edition!

---

*This contributing guide is adapted from open-source best practices and will evolve with the project.*
