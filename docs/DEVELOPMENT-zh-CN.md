# Cortex 社区版开发指南

本指南提供 Cortex CE 开发环境的设置和工作指南。

## 目录

- [前置条件](#前置条件)
- [开发环境设置](#开发环境设置)
  - [安装 Java 17+](#安装-java-17)
  - [安装 Maven](#安装-maven)
  - [安装 PostgreSQL](#安装-postgresql)
  - [安装 Node.js（用于 Thin Proxy）](#安装-nodejs用于-thin-proxy)
- [IDE 配置](#ide-配置)
  - [IntelliJ IDEA（推荐）](#intellij-idea推荐)
  - [VS Code](#vs-code)
- [项目结构](#项目结构)
- [构建项目](#构建项目)
- [运行应用程序](#运行应用程序)
- [编码规范](#编码规范)
  - [Java 约定](#java-约定)
  - [Spring Boot 最佳实践](#spring-boot-最佳实践)
  - [代码格式化](#代码格式化)
- [调试](#调试)
  - [远程调试](#远程调试)
  - [日志](#日志)
  - [数据库调试](#数据库调试)
- [测试](#测试)
  - [运行测试](#运行测试)
  - [编写测试](#编写测试)
  - [测试覆盖率](#测试覆盖率)
- [常见问题](#常见问题)
- [性能优化](#性能优化)
- [开发工作流](#开发工作流)

---

## 前置条件

| 软件 | 版本 | 必需 | 用途 |
|----------|---------|----------|---------|
| JDK | 17+ | 是 | Java 运行时 |
| Maven | 3.8+ | 是 | 构建工具 |
| PostgreSQL | 16+ | 是 | 数据库 |
| pgvector | 0.8+ | 是 | 向量扩展 |
| Node.js | 18+ | 可选 | Thin Proxy |
| Git | 2.x | 是 | 版本控制 |
| Docker | 最新 | 可选 | 容器运行时 |

---

## 开发环境设置

### 安装 Java 17+

#### macOS（Homebrew）

```bash
# 安装 OpenJDK 21（推荐）
brew install openjdk@21

# 创建符号链接
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
     /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 验证安装
java -version
# openjdk version "21.0.x"

# 设置 JAVA_HOME（添加到 ~/.zshrc 或 ~/.bashrc）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

#### Linux（Ubuntu/Debian）

```bash
# 安装 OpenJDK 21
sudo apt update
sudo apt install openjdk-21-jdk

# 验证
java -version

# 设置 JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

#### Windows

```powershell
# 使用 Scoop
scoop install openjdk21

# 或从以下地址下载：
# https://adoptium.net/temurin/releases/?version=21

# 设置 JAVA_HOME 环境变量
# 系统属性 → 环境变量 → 新建
# 变量名: JAVA_HOME
# 变量值: C:\Program Files\Eclipse Adoptium\jdk-21.x.x
```

### 安装 Maven

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
# 或使用项目自带的 Maven wrapper (./mvnw)
```

### 安装 PostgreSQL

#### macOS

```bash
# 安装 PostgreSQL 16 和 pgvector
brew install postgresql@16
brew install pgvector

# 启动 PostgreSQL
brew services start postgresql@16

# 创建数据库
createdb cortexce_dev

# 启用 pgvector 扩展
psql -d cortexce_dev -c "CREATE EXTENSION vector;"

# 验证扩展
psql -d cortexce_dev -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

#### Linux

```bash
# 添加 PostgreSQL 仓库
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
wget -qO- https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo tee /etc/apt/trusted.gpg.d/pgdg.asc &>/dev/null

# 安装
sudo apt update
sudo apt install postgresql-16 postgresql-16-pgvector

# 启动服务
sudo systemctl start postgresql
sudo systemctl enable postgresql

# 创建数据库
sudo -u postgres createdb cortexce_dev
sudo -u postgres psql -d cortexce_dev -c "CREATE EXTENSION vector;"
```

#### 使用 Docker（替代方案）

```bash
# 运行带 pgvector 的 PostgreSQL
docker run -d \
  --name cortexce-postgres \
  -e POSTGRES_DB=cortexce_dev \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# 等待启动
sleep 5

# 启用扩展
docker exec -it cortexce-postgres psql -U postgres -d cortexce_dev -c "CREATE EXTENSION vector;"
```

### 安装 Node.js（用于 Thin Proxy）

```bash
# macOS/Linux（使用 nvm）
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.zshrc  # 或 ~/.bashrc
nvm install 18
nvm use 18

# 验证
node -v  # v18.x.x
npm -v   # 9.x.x

# 安装 proxy 依赖
cd proxy
npm install
```

---

## IDE 配置

### IntelliJ IDEA（推荐）

#### 安装

1. 下载 [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/)（免费）或 Ultimate（付费）
2. 通过 Homebrew 安装：`brew install --cask intellij-idea-ce`

#### 初始配置

1. **打开项目**
   ```
   File → Open → 选择 java/claude-mem-java 目录
   ```

2. **配置 JDK**
   ```
   File → Project Structure → Project
   SDK: 21 (java version "21.0.x")
   Language level: 21 - Record patterns, pattern matching for switch
   ```

3. **启用注解处理**
   ```
   Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   ✓ Enable annotation processing
   Obtain processors from the project classpath
   ```

4. **安装插件**
   - Lombok（必需）
   - Spring Boot Assistant
   - JPA Buddy（可选，用于实体设计）
   - Database Navigator（可选）

5. **代码风格配置**
   ```
   Settings → Editor → Code Style → Java

   Tab size: 2
   Indent: 2
   Continuation indent: 4
   Line length: 120
   ```

   导入代码风格文件（如有）：
   ```
   Settings → Editor → Code Style → Scheme → Import Scheme → IntelliJ IDEA code style XML
   ```

6. **配置运行配置**

   创建新的 Spring Boot 运行配置：
   ```
   Run → Edit Configurations → + → Spring Boot

   Main class: com.ablueforce.cortexce.CortexCeApplication
   Environment variables: 从 .env 文件加载

   或使用 classpath 模块：
   Module: claude-mem-java
   ```

#### 常用 IntelliJ 快捷键

| 操作 | macOS | Windows/Linux |
|--------|-------|---------------|
| 查找操作 | `Cmd+Shift+A` | `Ctrl+Shift+A` |
| 跳转到类 | `Cmd+O` | `Ctrl+N` |
| 跳转到文件 | `Cmd+Shift+O` | `Ctrl+Shift+N` |
| 查找用法 | `Alt+F7` | `Alt+F7` |
| 重命名 | `Shift+F6` | `Shift+F6` |
| 快速修复 | `Alt+Enter` | `Alt+Enter` |
| 格式化代码 | `Cmd+Option+L` | `Ctrl+Alt+L` |
| 运行测试 | `Ctrl+Shift+R` | `Ctrl+Shift+F10` |

### VS Code

#### 安装

```bash
brew install --cask visual-studio-code
```

#### 必需扩展

```bash
# 通过命令行安装
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-spring-boot
code --install-extension vscjava.vscode-spring-initializr
code --install-extension vscjava.vscode-spring-boot-dashboard
code --install-extension GabrielBB.vscode-lombok
```

#### settings.json 配置

在项目根目录创建 `.vscode/settings.json`：

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

#### launch.json 配置

创建 `.vscode/launch.json`：

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

## 项目结构

```
java/
├── claude-mem-java/                    # 主 Spring Boot 应用
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ablueforce/cortexce/
│   │   │   │   │
│   │   │   │   ├── CortexCeApplication.java   # 主入口
│   │   │   │   │
│   │   │   │   ├── config/             # 配置类
│   │   │   │   │   ├── AsyncConfig.java        # @EnableAsync 配置
│   │   │   │   │   ├── SpringAiConfig.java     # LLM/Embedding beans
│   │   │   │   │   ├── WebConfig.java          # CORS、过滤器
│   │   │   │   │   └── QueueHealthIndicator.java
│   │   │   │   │
│   │   │   │   ├── controller/         # REST 控制器
│   │   │   │   │   ├── IngestionController.java   # Hook 事件
│   │   │   │   │   ├── ViewerController.java      # WebUI API
│   │   │   │   │   ├── ContextController.java     # 上下文检索
│   │   │   │   │   ├── StreamController.java      # SSE 流
│   │   │   │   │   ├── LogsController.java        # 日志 API
│   │   │   │   │   └── TestController.java        # 调试端点
│   │   │   │   │
│   │   │   │   ├── service/            # 业务逻辑
│   │   │   │   │   ├── AgentService.java         # 核心编排
│   │   │   │   │   ├── LlmService.java           # 聊天完成
│   │   │   │   │   ├── EmbeddingService.java     # 向量嵌入
│   │   │   │   │   ├── SearchService.java        # 语义搜索
│   │   │   │   │   ├── ContextService.java       # 上下文检索
│   │   │   │   │   ├── TimelineService.java      # 时间线组装
│   │   │   │   │   ├── ClaudeMdService.java      # CLAUDE.md 生成
│   │   │   │   │   ├── TokenService.java         # Token 计数
│   │   │   │   │   └── RateLimitService.java     # 限流
│   │   │   │   │
│   │   │   │   ├── repository/         # 数据访问
│   │   │   │   │   ├── SessionRepository.java
│   │   │   │   │   ├── ObservationRepository.java
│   │   │   │   │   ├── SummaryRepository.java
│   │   │   │   │   └── UserPromptRepository.java
│   │   │   │   │
│   │   │   │   ├── entity/              # JPA 实体
│   │   │   │   │   ├── SessionEntity.java
│   │   │   │   │   ├── ObservationEntity.java
│   │   │   │   │   ├── SummaryEntity.java
│   │   │   │   │   └── UserPromptEntity.java
│   │   │   │   │
│   │   │   │   ├── dto/                 # 数据传输对象
│   │   │   │   │   ├── ObservationDto.java
│   │   │   │   │   ├── SearchRequestDto.java
│   │   │   │   │   └── ContextResponseDto.java
│   │   │   │   │
│   │   │   │   ├── exception/           # 自定义异常
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   └── ValidationException.java
│   │   │   │   │
│   │   │   │   └── util/                # 工具类
│   │   │   │       ├── XmlParser.java
│   │   │   │       └── VectorValidator.java
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.yml      # 主配置
│   │   │       ├── application-dev.yml  # 开发环境配置
│   │   │       ├── application-prod.yml # 生产环境配置
│   │   │       │
│   │   │       ├── db/migration/        # Flyway 迁移
│   │   │       │   ├── V1__init_schema.sql
│   │   │       │   ├── V2__multi_dimension_embeddings.sql
│   │   │       │   └── ...
│   │   │       │
│   │   │       └── prompts/             # LLM 提示词模板
│   │   │           ├── init.txt         # 系统提示词
│   │   │           ├── observation.txt  # 观察提示词
│   │   │           └── summary.txt      # 摘要提示词
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
│   ├── pom.xml                          # Maven 配置
│   └── .env                             # 环境变量（git 忽略）
│
├── proxy/                               # Thin Proxy (Node.js)
│   ├── wrapper.js                       # CLI 入口
│   ├── proxy.js                         # HTTP 服务器（可选）
│   ├── package.json
│   └── CLAUDE-CODE-INTEGRATION.md
│
├── scripts/                             # 实用脚本
│   ├── regression-test.sh               # API 测试
│   ├── thin-proxy-test.sh               # Proxy 测试
│   └── webui-integration-test.sh        # WebUI 测试
│
├── docs/                                # 文档
│   ├── ARCHITECTURE.md
│   ├── API.md
│   └── DEVELOPMENT.md
│
├── README.md                            # 英文
├── README-zh-CN.md                      # 中文
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
└── LICENSE
```

### 关键目录说明

| 目录 | 用途 |
|-----------|---------|
| `config/` | Spring 配置、Bean 定义 |
| `controller/` | REST API 端点、HTTP 处理 |
| `service/` | 业务逻辑、编排 |
| `repository/` | 数据库访问、JPA 仓库 |
| `entity/` | 数据库实体、ORM 映射 |
| `dto/` | API 请求/响应对象 |
| `util/` | 辅助类、解析器、验证器 |
| `db/migration/` | Flyway SQL 迁移 |
| `prompts/` | LLM 提示词模板 |

---

## 构建项目

### 使用 Maven Wrapper（推荐）

```bash
cd java/claude-mem-java

# 清理并编译
./mvnw clean compile

# 编译跳过测试（更快）
./mvnw compile -DskipTests

# 打包（创建 JAR）
./mvnw clean package

# 打包跳过测试
./mvnw clean package -DskipTests

# 安装到本地 Maven 仓库
./mvnw clean install
```

### 使用系统 Maven

```bash
mvn clean package -DskipTests
```

### 构建 profiles

```bash
# 开发构建
./mvnw clean package -DskipTests -Pdev

# 生产构建（带优化）
./mvnw clean package -Pprod
```

### 常见构建问题

#### 内存不足

```bash
# 增加 Maven 内存
export MAVEN_OPTS="-Xmx2048m"
./mvnw clean package
```

#### 依赖下载问题

```bash
# 强制更新依赖
./mvnw clean package -U

# 清理本地缓存
rm -rf ~/.m2/repository
./mvnw clean package
```

---

## 运行应用程序

### 配置

1. **创建 .env 文件**

```bash
cd java/claude-mem-java
cp .env.example .env
```

2. **编辑 .env**

```properties
# 数据库
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_URL=jdbc:postgresql://localhost:5432/cortexce_dev

# LLM (DeepSeek - OpenAI 兼容)
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_MODEL=deepseek-chat

# Embedding (SiliconFlow)
SPRING_AI_OPENAI_EMBEDDING_API_KEY=sk-xxx
SPRING_AI_OPENAI_EMBEDDING_MODEL=BAAI/bge-m3
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=1024
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1/embeddings
```

### 运行命令

```bash
# 加载环境并运行
cd java/claude-mem-java
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
java -jar target/cortexce-*.jar

# 使用 dev profile 运行
java -jar target/cortexce-*.jar --spring.profiles.active=dev

# 指定端口运行
java -jar target/cortexce-*.jar --server.port=8080

# 带调试端口运行
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/cortexce-*.jar
```

### 使用 Maven Spring Boot 插件

```bash
# 直接运行（无需 JAR）
./mvnw spring-boot:run

# 使用 profile 运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 带环境变量运行
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
./mvnw spring-boot:run
```

### 验证应用程序

```bash
# 健康检查
curl http://localhost:37777/actuator/health

# 预期响应
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 编码规范

### Java 约定

#### 命名约定

| 类型 | 约定 | 示例 |
|------|------------|---------|
| 类 | PascalCase | `ObservationService` |
| 接口 | PascalCase | `SearchRepository` |
| 方法 | camelCase | `findById()` |
| 变量 | camelCase | `observationList` |
| 常量 | SCREAMING_SNAKE | `MAX_RETRY_COUNT` |
| 包 | 小写 | `com.ablueforce.cortexce.service` |

#### 类组织

```java
package com.ablueforce.cortexce.service;

// 1. 导入（按字母顺序排列）
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ablueforce.cortexce.entity.Observation;
import com.ablueforce.cortexce.repository.ObservationRepository;

// 2. 类级 Javadoc
/**
 * 管理观察的服务。
 *
 * <p>提供 CRUD 操作和语义搜索功能。
 *
 * @author Your Name
 * @since 1.0.0
 */
@Service
public class ObservationService {

    // 3. 常量（放在最前面）
    private static final int MAX_RESULTS = 100;
    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    // 4. 实例变量（依赖注入）
    private final ObservationRepository repository;
    private final EmbeddingService embeddingService;

    // 5. 构造方法
    public ObservationService(ObservationRepository repository,
                              EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    // 6. 公共方法（逻辑分组）
    public List<Observation> findAll() { ... }

    public Optional<Observation> findById(String id) { ... }

    // 7. 私有方法
    private void validate(Observation observation) { ... }
}
```

#### 使用 Record 作为 DTO

```java
// 好 - 不可变、简洁
public record ObservationDto(
    String id,
    String content,
    List<String> facts,
    List<String> concepts,
    float[] embedding
) {
    // 紧凑构造方法用于验证
    public ObservationDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(content, "content must not be null");
        facts = List.copyOf(facts);  // 防御性拷贝
    }
}

// 避免 - 可变、冗长
public class ObservationDto {
    private String id;
    private String content;
    // getters, setters...
}
```

#### 优先使用不可变性

```java
// 好 - 不可变
public class Observation {
    private final String id;
    private final String content;
    private final Instant createdAt;

    public Observation(String id, String content) {
        this.id = id;
        this.content = content;
        this.createdAt = Instant.now();
    }

    // 只有 getters，没有 setters
    public String getId() { return id; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
```

#### 使用 Optional 处理可空返回值

```java
// 好 - 显式空值处理
public Optional<Observation> findById(String id) {
    return repository.findById(id);
}

// 调用方
service.findById(id)
    .map(Observation::getContent)
    .orElse("Not found");

// 避免 - 隐藏的空值
public Observation findById(String id) {
    return repository.findById(id);  // 可能返回 null！
}
```

### Spring Boot 最佳实践

#### 构造方法注入

```java
// 好 - 构造方法注入（必需依赖）
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
    private ObservationRepository repository;  // 隐藏依赖
}
```

#### 事务管理

```java
@Service
public class ObservationService {

    // 查询用只读事务
    @Transactional(readOnly = true)
    public List<Observation> findByProject(String projectPath) {
        return repository.findByProjectPath(projectPath);
    }

    // 修改用写事务
    @Transactional
    public Observation save(Observation observation) {
        return repository.save(observation);
    }

    // 避免长事务
    @Transactional
    public void processLargeDataset() {
        // 坏：一个事务处理百万条记录
    }
}
```

#### 异步处理

```java
@Service
public class AgentService {

    // 异步fire-and-forget
    @Async
    public void processToolUseAsync(ToolUseEvent event) {
        // 在虚拟线程上运行
        String response = llmService.chatCompletion(prompt);
        // ... 处理并保存
    }

    // 带结果的异步
    @Async
    public CompletableFuture<Observation> createObservationAsync(String content) {
        Observation obs = process(content);
        return CompletableFuture.completedFuture(obs);
    }
}
```

#### 异常处理

```java
// 自定义异常
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// 全局异常处理器
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

### 代码格式化

#### IntelliJ 格式化器设置

创建 `.idea/codeStyles/Project.xml`：

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

#### 使用 Spotless（可选）

添加到 `pom.xml`：

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

运行：
```bash
./mvnw spotless:check   # 检查格式化
./mvnw spotless:apply   # 应用格式化
```

---

## 调试

### 远程调试

#### 启用调试端口

```bash
# 带调试代理运行
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/cortexce-*.jar
```

#### 从 IntelliJ 连接

1. Run → Edit Configurations → + → Remote JVM Debug
2. Host: `localhost`
3. Port: `5005`
4. 点击 Debug

#### 从 VS Code 连接

`.vscode/launch.json` 配置已包含 attach 配置。

### 日志

#### 日志级别

```yaml
# application.yml
logging:
  level:
    root: INFO
    com.ablueforce.cortexce: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

#### 使用 SLF4J

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

#### 使用 MDC 结构化日志

```java
import org.slf4j.MDC;

public void processEvent(String sessionId) {
    MDC.put("sessionId", sessionId);
    try {
        log.info("Processing event");
        // ... 业务逻辑
    } finally {
        MDC.clear();
    }
}
```

### 数据库调试

#### 启用 SQL 日志

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

#### 查询分析

```sql
-- 启用查询计时
\timing on

-- 解释查询计划
EXPLAIN ANALYZE
SELECT * FROM mem_observations
ORDER BY embedding_1024 <=> '[0.1, 0.2, ...]'::vector
LIMIT 10;

-- 检查索引使用
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'mem_observations';
```

#### 数据库控制台

```bash
# 连接数据库
psql -d cortexce_dev

# 常用查询
\dt                           -- 列出表
\d mem_observations           -- 表结构
\di                           -- 列出索引
SELECT COUNT(*) FROM mem_observations;  -- 行数
```

---

## 测试

### 运行测试

```bash
# 运行所有测试
./mvnw test

# 运行特定测试类
./mvnw test -Dtest=SearchServiceTest

# 运行特定测试方法
./mvnw test -Dtest=SearchServiceTest#testSemanticSearch

# 带覆盖率运行
./mvnw test jacoco:report

# 查看覆盖率报告
open target/site/jacoco/index.html
```

### 编写测试

#### 单元测试示例

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

#### 集成测试示例

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

        // 验证数据库
        assertThat(repository.count()).isEqualTo(1);
    }
}
```

### 测试覆盖率

#### JaCoCo 配置

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

## 常见问题

### 问题：应用程序无法启动 - 端口已被占用

```bash
# 查找占用端口的进程
lsof -i :37777

# 终止进程
kill -9 <PID>

# 或使用一行命令
lsof -ti:37777 | xargs kill -9
```

### 问题：数据库连接失败

```bash
# 检查 PostgreSQL 是否运行
pg_isready

# 检查连接
psql -h localhost -U postgres -d cortexce_dev -c "SELECT 1"

# 重置密码
psql -U postgres -c "ALTER USER postgres PASSWORD 'new_password';"
```

### 问题：Flyway 迁移失败

```bash
# 检查迁移状态
psql -d cortexce_dev -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"

# 修复（如需要）
./mvnw flyway:repair

# 手动修复
psql -d cortexce_dev -c "DELETE FROM flyway_schema_history WHERE success = false;"
```

### 问题：OutOfMemoryError

```bash
# 增加 JVM 堆内存
java -Xmx2g -jar target/cortexce-*.jar

# 或在环境中设置
export JAVA_OPTS="-Xmx2g -Xms1g"
```

### 问题：Lombok 在 IDE 中不工作

1. 安装 Lombok 插件
2. 启用注解处理
3. 重启 IDE
4. 重新构建项目

```bash
# IntelliJ
File → Settings → Plugins → 搜索 "Lombok" → 安装
File → Settings → Build → Compiler → Annotation Processors → Enable
```

### 问题：测试失败 "No qualifying bean"

```java
// 缺少 @SpringBootTest 或 @ExtendWith
@SpringBootTest
class MyTest { ... }

// 缺少 mock
@MockBean
private MyService myService;
```

---

## 性能优化

### 数据库优化

#### 索引调优

```sql
-- 检查缺失索引
SELECT
    schemaname, tablename,
    attname, n_distinct, correlation
FROM pg_stats
WHERE tablename = 'mem_observations';

-- 为常用查询添加索引
CREATE INDEX idx_observations_project_created
ON mem_observations(project_path, created_at DESC);

-- 分析查询性能
EXPLAIN ANALYZE
SELECT * FROM mem_observations
WHERE project_path = '/my/project'
ORDER BY created_at DESC
LIMIT 100;
```

#### 连接池

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

### JVM 调优

```bash
# 生产环境 JVM 选项
java \
  -Xms1g -Xmx2g \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxGCPauseMillis=10 \
  -jar target/cortexce-*.jar
```

### 缓存

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

// 使用
@Cacheable(value = "observations", key = "#id")
public Observation findById(String id) {
    return repository.findById(id).orElseThrow();
}
```

### 异步处理

```java
// 为 I/O 密集型操作使用虚拟线程
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

## 开发工作流

### 日常开发

```bash
# 1. 拉取最新更改
git pull upstream main

# 2. 创建功能分支
git checkout -b feat/my-feature

# 3. 进行更改并测试
./mvnw test

# 4. 运行应用程序
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
./mvnw spring-boot:run

# 5. 提交更改
git add .
git commit -m "feat: add my feature"

# 6. 推送并创建 PR
git push origin feat/my-feature
```

### 提交 PR 前

- [ ] 代码无错误编译
- [ ] 所有测试通过
- [ ] 保持代码覆盖率
- [ ] 更新文档
- [ ] 提交信息遵循规范
- [ ] PR 描述完整

---

**英文版本**: [DEVELOPMENT.md](DEVELOPMENT.md)

---

*开发指南版本 1.0*
*最后更新：2026年*
