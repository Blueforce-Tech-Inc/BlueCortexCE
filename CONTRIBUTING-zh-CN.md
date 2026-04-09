# 贡献指南 — Cortex 社区版

> English version: [CONTRIBUTING.md](./CONTRIBUTING.md)

感谢你考虑为 Cortex 社区版贡献代码！正是像你这样的贡献者让 Cortex CE 变得更好。

## 目录

- [行为准则](#行为准则)
- [入门指南](#入门指南)
  - [前置条件](#前置条件)
  - [开发环境配置](#开发环境配置)
  - [项目构建](#项目构建)
- [开发规范](#开发规范)
  - [项目结构](#项目结构)
  - [代码风格](#代码风格)
  - [Java 约定](#java-约定)
  - [Spring Boot 最佳实践](#spring-boot-最佳实践)
- [Git 提交规范](#git-提交规范)
  - [提交信息格式](#提交信息格式)
  - [提交类型](#提交类型)
- [Pull Request 流程](#pull-request-流程)
  - [创建 Pull Request](#创建-pull-request)
  - [PR 要求](#pr-要求)
  - [审核流程](#审核流程)
- [代码审核标准](#代码审核标准)
- [测试要求](#测试要求)
  - [单元测试](#单元测试)
  - [集成测试](#集成测试)
  - [测试覆盖率](#测试覆盖率)
- [问题报告](#问题报告)
- [功能请求](#功能请求)
- [社区](#社区)

---

## 行为准则

本项目和所有参与者均受 [参与者准则行为准则](CODE_OF_CONDUCT.md) 约束。参与即表示你同意遵守该准则。请向 [maintainers@email.com](mailto:maintainers@email.com) 报告不可接受的行为。

---

## 入门指南

### 前置条件

确保已安装以下软件：

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | Java 开发套件 |
| Maven | 3.8+ | 构建工具 |
| PostgreSQL | 16+ | 数据库 |
| pgvector | 0.8+ | 向量扩展 |
| Git | 2.x | 版本控制 |
| Docker | 最新版（可选） | 容器运行时 |

### 开发环境配置

1. **Fork 并克隆仓库**

```bash
# Fork GitHub 上的仓库后：
git clone https://github.com/YOUR_USERNAME/BlueCortexCE.git
cd BlueCortexCE
```

2. **配置 PostgreSQL 数据库**

```bash
# macOS (Homebrew)
brew install postgresql@16
brew services start postgresql@16

# 创建数据库
createdb claude_mem

# 启用 pgvector
psql -d claude_mem -c "CREATE EXTENSION vector;"
```

3. **配置环境变量**

在项目根目录创建 `.env` 文件：

```bash
cp .env.example .env
```

编辑 `.env`：

```properties
# 数据库（Spring Boot 属性名——本地开发和 Docker 均适用）
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

# LLM（聊天模型）
SPRING_AI_OPENAI_API_KEY=sk-xxx
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_CHAT_MODEL=deepseek-chat

# Embedding（向量模型）
SPRING_AI_OPENAI_EMBEDDING_API_KEY=sk-xxx
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024
```

4. **IDE 配置（推荐 IntelliJ IDEA）**

- 安装 Lombok 插件
- 启用注解处理
- 导入为 Maven 项目
- 将项目 SDK 设置为 Java 21+

### 项目构建

```bash
# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 打包（跳过测试以加快构建）
mvn clean package -DskipTests

# 运行应用
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar backend/target/cortex-ce-*.jar
```

---

## 开发规范

### 项目结构

```
cortexce/
├── backend/                         # Spring Boot 主应用 (Java 21)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ablueforce/cortexce/
│   │   │   │   ├── config/            # 配置类
│   │   │   │   ├── controller/        # REST 控制器
│   │   │   │   ├── service/           # 业务逻辑
│   │   │   │   ├── repository/        # 数据访问
│   │   │   │   ├── entity/            # JPA 实体
│   │   │   │   ├── dto/               # 数据传输对象
│   │   │   │   ├── common/            # 共享工具类和常量
│   │   │   │   ├── event/             # Spring 应用事件
│   │   │   │   ├── exception/         # 自定义异常
│   │   │   │   ├── logging/           # 日志工具
│   │   │   │   ├── mcp/               # MCP 工具集成
│   │   │   │   └── util/              # 工具类
│   │   │   └── resources/
│   │   │       ├── application.properties  # 应用配置
│   │   │       └── db/migration/      # Flyway 迁移脚本
│   │   └── test/                      # 测试代码
│   └── pom.xml                        # Maven 配置
├── proxy/                             # 精简代理 (Node.js)
├── openclaw-plugin/                   # OpenClaw 集成
├── scripts/                           # 工具脚本
└── docs/                              # 文档
```

### 代码风格

遵循标准 Java 约定及项目特定规则：

#### 基本规则

- **缩进**：2 空格（禁止 Tab）
- **行长度**：最多 120 字符
- **编码**：UTF-8
- **换行符**：LF（Unix 风格）

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `ObservationService` |
| 方法名 | camelCase | `getObservationById` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | 小写 | `com.ablueforce.cortexce.service` |
| 变量名 | camelCase | `observationList` |

#### 文件组织

```java
// 1. 包声明
package com.ablueforce.cortexce.service;

// 2. 导入语句（按顺序）
import java.util.List;                    // Java 标准库
import org.springframework.stereotype.Service;  // Spring
import com.ablueforce.cortexce.entity.Observation;  // 项目内

// 3. 类级 Javadoc
/**
 * Service for managing observations.
 *
 * @author Your Name
 * @since 1.0.0
 */
@Service
public class ObservationService {

    // 4. 常量
    private static final int MAX_RESULTS = 100;

    // 5. 实例变量（依赖注入）
    private final ObservationRepository repository;

    // 6. 构造方法
    public ObservationService(ObservationRepository repository) {
        this.repository = repository;
    }

    // 7. 公开方法
    public List<Observation> findAll() {
        // Implementation
    }

    // 8. 私有方法
    private void validateInput(String input) {
        // Implementation
    }
}
```

### Java 约定

#### 使用 Final 字段保证不可变性

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

// 尽量避免可变状态
```

#### 优先使用构造方法注入

```java
// Good - 构造方法注入（强制依赖）
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

// 避免 - 字段注入
@Service
public class SearchService {
    @Autowired
    private ObservationRepository repository;  // 不推荐
}
```

#### 使用 Optional 返回可空值

```java
public Optional<Observation> findById(String id) {
    return repository.findById(id);
}
```

#### 使用有意义的变量名

```java
// Good
List<Observation> recentObservations = repository.findByOrderByCreatedAtDesc();
int retryCount = 0;

// Bad
List<Observation> list = repository.findByOrderByCreatedAtDesc();
int i = 0;
```

### Spring Boot 最佳实践

#### Controller 层

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

#### Service 层

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

#### 异常处理

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

## Git 提交规范

### 提交信息格式

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<类型>(<范围>): <主题>

<正文>

<footer>
```

#### 结构

- **type**: 变更类型（必填）
- **scope**: 模块/组件（可选）
- **subject**: 简短描述（必填）
- **body**: 详细描述（可选）
- **footer**: Breaking changes、issue 引用（可选）

### 提交类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(search): add vector similarity search` |
| `fix` | Bug 修复 | `fix(embedding): handle null vectors correctly` |
| `docs` | 文档更新 | `docs(api): update OpenAPI documentation` |
| `style` | 代码格式（无逻辑变更） | `style: format code with 2-space indent` |
| `refactor` | 代码重构 | `refactor(service): extract search logic` |
| `perf` | 性能改进 | `perf(query): optimize vector index usage` |
| `test` | 添加/更新测试 | `test(search): add unit tests for SearchService` |
| `chore` | 维护任务 | `chore(deps): update Spring Boot to 3.3` |
| `ci` | CI/CD 变更 | `ci: add GitHub Actions workflow` |
| `revert` | 回滚提交 | `revert: undo feat(search) changes` |

### 示例

#### 简单提交

```
feat(api): add health check endpoint
```

#### 带正文的提交

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

## Pull Request 流程

### 创建 Pull Request

1. **创建功能分支**

```bash
# 更新 main 分支
git checkout main
git pull upstream main

# 创建功能分支
git checkout -b feat/your-feature-name
```

2. **进行变更**

```bash
# 进行变更，频繁提交且保证提交信息规范
git add .
git commit -m "feat(component): add new feature"

# 保持分支更新
git fetch upstream
git rebase upstream/main
```

3. **推送并创建 PR**

```bash
git push origin feat/your-feature-name
```

前往 GitHub 创建 Pull Request。

### PR 要求

提交前确保：

- [ ] 代码编译无错误
- [ ] 所有测试通过（`./mvnw test`）
- [ ] 测试覆盖率维持或提升
- [ ] 必要时更新文档
- [ ] 提交信息符合规范
- [ ] PR 描述清晰完整
- [ ] 关联相关 issue

### PR 描述模板

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

### 审核流程

1. **自动化检查**：CI 必须通过
2. **代码审核**：至少需要 1 人批准
3. **处理反馈**：回复所有评论
4. **合并提交**：如需要，将 squash 后合并
5. **合并**：维护者将合并你的 PR

---

## 代码审核标准

### 审核者

审核代码时检查：

#### 功能性
- [ ] 是否解决了所述问题？
- [ ] 边界情况是否已处理？
- [ ] 错误处理是否恰当？

#### 代码质量
- [ ] 是否符合项目规范？
- [ ] 是否可读且可维护？
- [ ] 是否有代码重复？
- [ ] 抽象是否恰当？

#### 测试
- [ ] 测试覆盖率是否充分？
- [ ] 测试是否有意义？
- [ ] 边界情况是否已覆盖？

#### 性能
- [ ] 是否有明显性能问题？
- [ ] 数据库查询是否优化？
- [ ] 是否有内存泄漏？

#### 安全性
- [ ] 是否有安全漏洞？
- [ ] 是否有输入验证？
- [ ] 敏感数据处理是否得当？

### 作者

收到反馈时：

- 对建设性批评保持开放态度
- 回复所有评论
- 如需澄清请提出
- 及时处理要求的修改
- 不同意时解释你的理由

---

## 测试要求

### 单元测试

- 所有新增 service 方法必须有单元测试
- 测试成功和失败场景
- 使用描述性的测试名称

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

### 集成测试

- 端到端测试 API 端点
- 使用 `@SpringBootTest` + testcontainers
- 每个测试后清理测试数据

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

### 测试覆盖率

- 新代码最低 70% 行覆盖率
- 关键路径应有 90%+ 覆盖率
- 使用 JaCoCo 生成覆盖率报告

```bash
# 生成覆盖率报告
mvn jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

---

## 问题报告

### Bug 报告

报告 bug 时包含：

1. **描述**：清晰的 bug 描述
2. **复现步骤**：详细步骤
3. **预期行为**：应该发生什么
4. **实际行为**：实际发生了什么
5. **环境**：操作系统、Java 版本、Spring Boot 版本
6. **日志**：相关错误消息/堆栈跟踪
7. **截图**：如有

使用以下模板：

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

## 功能请求

请求功能时包含：

1. **问题**：这解决了什么问题？
2. **方案**：建议的解决方案
3. **替代方案**：考虑过的其他方案
4. **影响**：谁将受益？

使用以下模板：

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

## 社区

### 获取帮助

- **GitHub Discussions**：问题和一般讨论
- **GitHub Issues**：bug 报告和功能请求
- **文档**：[GitHub Docs](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/docs)

### 关注更新

- Watch 仓库以获取发布通知
- Star 项目以示支持
- 关注贡献者以获取更新

---

## 致谢

贡献者将在以下位置获得认可：

- 重大贡献的发布说明
- GitHub 贡献者图表

感谢你对 Cortex 社区版的贡献！

---

*本贡献指南改编自开源最佳实践，将随项目发展而更新。*
