# 结构化信息提取

[English](structured-extraction.md) | 中文

## 概述

Cortex CE 的**结构化信息提取**是一个通用的、提示词驱动的系统，能够自动从对话观测数据中提取结构化信息。它不是简单存储原始对话文本，而是识别并组织有意义的事实——用户偏好、过敏信息、重要日期、联系方式等——转化为可查询的结构化记录。

**为什么需要这个功能？** 传统的记忆系统原样存储观测数据，使得语义查询变得困难。当 AI 助手需要记住"用户的预算范围"或"哪个家庭成员对花生过敏"时，原始观测数据难以解析。结构化提取将非结构化的对话数据转换为定义良好的 JSON Schema，应用程序可以直接查询——`GET /api/extraction/user_preference/latest` 返回 `{preferences: [{category: "手机", value: "小米", sentiment: "positive"}]}`，而不是让用户自行解读"用户在对话中提到喜欢小米手机"。

核心设计原则是**配置优于代码**：提取什么由 YAML 模板的提示词和 Schema 定义，而非 Java 代码。添加新的提取类型只需修改 YAML 配置，无需改动代码。

## 工作原理

提取管道分为 5 个阶段：

```
┌──────────────────────────────────────────────────────────────┐
│ 提取管道（每个模板、每个用户）                                  │
├──────────────────────────────────────────────────────────────┤
│ 1. 查找候选观测数据（source-filter + 时间范围）                │
│ 2. 按用户分组（通过 SessionEntity → userId）                  │
│ 3. 构建提示词（template.prompt + 观测数据 + 先前结果）         │
│ 4. 通过 BeanOutputConverter 调用 LLM（Schema 强制输出）        │
│ 5. 验证并存储为 ObservationEntity（extracted_data）           │
└──────────────────────────────────────────────────────────────┘
```

**架构概要：**

- **5 个生命周期钩子** → SessionStart、UserPromptSubmit、PostToolUse、Summary、SessionEnd 产生观测数据并存入 PostgreSQL
- **ExtractionConfig**（YAML 模板）→ 定义提取什么、使用哪些提示词、输出 Schema
- **StructuredExtractionService** → 通用引擎，对观测数据运行模板
- **DeepRefine 集成** → 提取在 `deepRefineProjectMemories()` 的最后一步运行（精炼之后），或通过定时 Cron 触发（每天凌晨 2 点）
- **存储** → 结果存储为 `ObservationEntity`，`type=extracted_{template}`，`extracted_data` 为 JSONB 列
- **LLM 重新提取** → 每次运行包含先前提取结果作为上下文；LLM 通过语义理解产生完整的当前状态，处理更新、删除和冲突

## 快速开始

### 第 1 步：启用功能

在 `application.properties` 中添加，或设置环境变量：

```properties
app.memory.extraction.enabled=true
```

或通过环境变量：

```bash
EXTRACTION_ENABLED=true
```

### 第 2 步：配置模板

在模板目录（`app.memory.extraction.templates-dir`，默认：`config/extraction-templates/`）中创建 YAML 模板文件：

```yaml
# config/extraction-templates/user_preferences.yml
templates:
  - name: "user_preference"
    enabled: true
    template-class: "java.util.Map"
    session-id-pattern: "pref:{project}:{userId}"
    key-fields: ["category", "value"]
    description: "从对话中提取用户偏好"
    source-filter: ["user_statement", "manual"]
    prompt: |
      从以下对话中提取用户偏好。
      关注：喜欢/不喜欢的品牌、预算限制、风格偏好。
      返回所有发现的偏好，不仅限于一条。
    output-schema: |
      {
        "type": "object",
        "properties": {
          "preferences": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "category": {"type": "string"},
                "value": {"type": "string"},
                "sentiment": {"type": "string", "enum": ["positive", "negative", "neutral"]},
                "confidence": {"type": "number"}
              }
            }
          }
        }
      }
```

### 第 3 步：启动服务

```bash
cd backend
mvn clean install -DskipTests
java -jar target/cortex-ce-*.jar
```

或使用 Docker：

```bash
docker compose up -d
```

### 第 4 步：触发提取

提取会在深度精炼时自动运行（默认每天凌晨 2 点）。手动触发：

```bash
curl -X POST http://localhost:37777/api/extraction/run \
  -H "Content-Type: application/json" \
  -d '{"projectPath": "/my-project"}'
```

### 第 5 步：查询结果

```bash
# 获取模板的最新提取结果
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# 获取提取历史
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

## 配置参考

### application.properties 设置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `app.memory.extraction.enabled` | boolean | `false` | 全局启用结构化提取 |
| `app.memory.extraction.templates-dir` | String | `config/extraction-templates/` | YAML 模板文件目录 |
| `app.memory.extraction.schedule` | String | `0 0 2 * * ?` | 定时提取的 Cron 表达式 |
| `app.memory.extraction.batch-size` | int | `20` | 每次 LLM 调用处理的观测数据数量 |
| `app.memory.extraction.max-tokens-per-call` | int | `8000` | 每次提取 LLM 调用的最大 Token 数 |
| `app.memory.extraction.max-prior-chars` | int | `3000` | 先前提取上下文的最大字符数 |
| `app.memory.extraction.initial-run-max-candidates` | int | `500` | 每个模板首次提取的候选数据上限 |
| `app.memory.extraction.cost-control.dry-run` | boolean | `false` | 记录提取意图但不调用 LLM |
| `app.memory.extraction.cost-control.max-calls-per-run` | int | `10` | 每次提取运行的最大 LLM 调用次数 |

### 模板 YAML 格式

每个 YAML 文件定义一个或多个提取模板：

```yaml
templates:
  - name: "template_name"              # 必填。唯一标识符，存储为 type="extracted_{name}"
    enabled: true                       # 可选。默认：true。模板级别开关。
    template-class: "java.util.Map"     # 必填。输出类："java.util.Map"（灵活）或 POJO 类名。
    session-id-pattern: "pref:{project}:{userId}"  # 可选。结果存储位置。变量：{project}、{userId}。null = 继承源会话。
    key-fields: ["field1", "field2"]    # 可选。去重键字段。
    description: "人类可读的描述"        # 可选。
    trigger-keywords: ["keyword1"]      # 可选。未来关键词触发提取使用。
    source-filter: ["user_statement"]   # 必填。考虑哪些观测数据来源。
    prompt: |                           # 必填。LLM 提取调用的系统提示词。
      从对话中提取结构化信息。
      返回与输出 Schema 匹配的结果。
    output-schema: |                    # Map 模板必填。POJO 模板从 Java 类自动推导。
      {"type": "object", "properties": {...}}
```

### 输出类选项

| template-class | 使用场景 | Schema 来源 | 类型安全 |
|----------------|----------|-------------|----------|
| `java.util.Map` | 灵活，任意 Schema | YAML 中的 `output-schema` | 无（需要后处理） |
| `com.example.AllergyInfo` | 稳定、明确的 Schema | 从 Java 类自动推导 | 完全编译时安全 |

### 模板示例

**过敏信息：**

```yaml
templates:
  - name: "allergy_info"
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual", "llm_inference"]
    key-fields: ["person", "allergens"]
    prompt: |
      从对话中提取过敏和饮食信息：
      - 谁过敏（person）
      - 过敏原（allergens）
      - 如有提及，严重程度
    output-schema: |
      {
        "type": "object",
        "properties": {
          "person": {"type": "string"},
          "allergens": {"type": "array", "items": {"type": "string"}},
          "severity": {"type": "string"}
        }
      }
```

**重要日期：**

```yaml
templates:
  - name: "important_dates"
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual"]
    key-fields: ["date", "occasion"]
    prompt: |
      提取提到的重要日期：生日、纪念日、事件。
      包括：日期、场合、涉及人员。
    output-schema: |
      {
        "type": "object",
        "properties": {
          "dates": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "date": {"type": "string"},
                "occasion": {"type": "string"},
                "person": {"type": "string"}
              }
            }
          }
        }
      }
```

## API 参考

### POST /api/extraction/run

手动触发项目的提取。运行所有已启用的模板。

**请求：**

```json
{
  "projectPath": "/my-project",
  "templateName": "user_preference",    // 可选。仅运行指定模板。
  "userId": "alice"                     // 可选。仅对指定用户运行。
}
```

**响应（200）：**

```json
{
  "status": "completed",
  "templatesRun": ["user_preference", "allergy_info"],
  "extractionsCreated": 3,
  "durationMs": 4521
}
```

### GET /api/extraction/{templateName}/latest

获取模板的最新提取结果。

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 项目路径 |
| `userId` | 否 | 按用户 ID 过滤 |

**响应（200）：**

```json
{
  "templateName": "user_preference",
  "userId": "alice",
  "extractedAt": "2026-03-22T10:30:00Z",
  "data": {
    "preferences": [
      {
        "category": "手机品牌",
        "value": "小米",
        "sentiment": "positive",
        "confidence": 0.95
      },
      {
        "category": "预算",
        "value": "3000-4000",
        "sentiment": "neutral",
        "confidence": 0.88
      }
    ]
  }
}
```

### GET /api/extraction/{templateName}/history

获取模板的提取历史（所有快照）。

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 项目路径 |
| `userId` | 否 | 按用户 ID 过滤 |
| `limit` | 否 | 最大结果数（默认：10） |

**响应（200）：**

```json
{
  "templateName": "user_preference",
  "userId": "alice",
  "history": [
    {
      "extractedAt": "2026-03-22T10:30:00Z",
      "data": {"preferences": [{"category": "手机品牌", "value": "小米"}]}
    },
    {
      "extractedAt": "2026-03-21T10:30:00Z",
      "data": {"preferences": [{"category": "手机品牌", "value": "苹果"}]}
    }
  ]
}
```

### GET /api/extraction/{templateName}/search

按字段值搜索提取结果。

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 项目路径 |
| `fieldPath` | 是 | 提取数据中的 JSON 路径（如 `allergens`） |
| `value` | 是 | 要搜索的值 |

**示例：**

```bash
# 查找谁对花生过敏
curl "http://localhost:37777/api/extraction/allergy_info/search?projectPath=/my-project&fieldPath=allergens&value=花生"
```

## 使用场景

### 场景 1：用户偏好提取

用户告诉 AI 助手：

> "我不喜欢苹果手机" → "我更喜欢小米" → "预算3000-4000"

**配置：**

```yaml
templates:
  - name: "user_preference"
    template-class: "java.util.Map"
    session-id-pattern: "pref:{project}:{userId}"
    source-filter: ["user_statement"]
    prompt: |
      从对话中提取用户偏好。
      关注：喜欢/不喜欢的品牌、预算、风格。
    output-schema: |
      {"type": "object", "properties": {"preferences": {"type": "array", "items": {"type": "object", "properties": {
        "category": {"type": "string"}, "value": {"type": "string"}, "sentiment": {"type": "string", "enum": ["positive", "negative", "neutral"]}, "confidence": {"type": "number"}
      }}}}}
```

**运行时行为：**
1. 观测数据通过钩子捕获（source = `user_statement`）
2. 提取运行（定时或手动触发）
3. LLM 接收观测数据 + 模板提示词
4. 结果存储在会话 `pref:/my-project:alice` 中

**查询结果：**

```json
{
  "preferences": [
    {"category": "手机品牌(排斥)", "value": "苹果", "sentiment": "negative", "confidence": 0.95},
    {"category": "手机品牌(偏好)", "value": "小米", "sentiment": "positive", "confidence": 0.90},
    {"category": "预算", "value": "3000-4000", "sentiment": "neutral", "confidence": 0.85}
  ]
}
```

### 场景 2：多用户隔离

张家有 4 位家庭成员。每位成员独立使用系统。

**工作方式：**
- 每个用户有不同的 `userId`（如 `alice`、`bob`、`charlie`、`diana`）
- 提取状态按用户跟踪——Alice 的偏好不影响 Bob 的
- 结果存储在用户范围的会话中（`pref:/project:alice`、`pref:/project:bob`）

```java
// Java SDK
client.startSession(SessionStartRequest.builder()
    .sessionId("conv-123")
    .projectPath("/family-project")
    .userId("alice")  // 多用户标识符
    .build());

// 查询 Alice 的偏好
Map<String, Object> extraction = client.getLatestExtraction(
    "/family-project", "user_preference", "alice");
```

### 场景 3：重新提取与冲突处理

用户偏好随时间演变：

```
2025-01: "I love Sony headphones"
2025-06: "Actually, Bose noise cancellation is better"
2026-01: "I don't like Sony anymore"
```

**LLM 重新提取如何处理：**

每次提取包含**先前结果作为上下文**。LLM 通过语义理解产生完整的当前状态，决定保留或移除哪些内容：

```
运行 1: LLM 输出 → [{category: "耳机", value: "Sony", sentiment: "positive"}]

运行 2（先前结果 + "Bose也不错"）:
  LLM 输出 → [{category: "耳机", value: "Sony", sentiment: "positive"},
               {category: "耳机", value: "Bose", sentiment: "positive"}]

运行 3（先前结果 + "不喜欢Sony了"）:
  LLM 输出 → [{category: "耳机", value: "Bose", sentiment: "positive"}]
  removed: [{category: "耳机", value: "Sony", reason: "用户说不再喜欢"}]
```

**关键要点：**
- 旧提取结果作为历史保留（时间戳区分当前和历史）
- 无程序化合并逻辑——LLM 处理语义
- 可选 `removed` 字段跟踪被移除的内容及原因

### 场景 4：自定义模板（过敏信息）

定义完全自定义的提取类型：

```yaml
templates:
  - name: "allergy_info"
    enabled: true
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual"]
    key-fields: ["person", "allergens"]
    prompt: |
      从对话中提取过敏和饮食信息。
      关注：谁过敏、过敏原、严重程度。
      务必精确——医疗信息必须准确。
    output-schema: |
      {
        "type": "object",
        "properties": {
          "allergies": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "person": {"type": "string"},
                "allergens": {"type": "array", "items": {"type": "string"}},
                "severity": {"type": "string"},
                "source": {"type": "string", "description": "来源：观测 ID 或 'prior'"}
              }
            }
          }
        }
      }
```

用户说："孩子对花生过敏，很严重"

**提取结果：**

```json
{
  "allergies": [
    {
      "person": "孩子",
      "allergens": ["花生"],
      "severity": "严重",
      "source": "prior"
    }
  ]
}
```

## 高级主题

### 模板如何映射到后端 Schema

模板是启动时加载的 YAML 配置。`StructuredExtractionService` 解析每个模板：

1. **`template-class: "java.util.Map"`** → 使用 `BeanOutputConverter<Map>`，Schema 来自 YAML 的 `output-schema` 字段
2. **`template-class: "com.example.MyPojo"`** → 使用 `BeanOutputConverter<MyPojo>`，Schema 通过 `getJsonSchema()` 从 Java 类自动推导

对于 Map 模板，`output-schema` 作为格式指令注入系统提示词。对于 POJO 模板，`BeanOutputConverter` 自动处理。

**存储映射：**

| 模板字段 | ObservationEntity 字段 |
|----------|------------------------|
| `name` | `type` = `"extracted_{name}"` |
| `source-filter` | 决定哪些观测数据为候选 |
| `session-id-pattern` | 结果观测的 `contentSessionId` |
| 输出数据 | `extractedData`（JSONB 列） |

### 成本控制与速率限制

提取成本通过以下机制管理：

- **定时批处理**（非实时）——提取每日运行，非逐条观测
- **增量处理**——仅处理上次提取以来的新观测数据
- **首次运行上限**——`initial-run-max-candidates`（默认 500）限制首次运行处理量
- **批处理大小**——观测数据按 `batch-size`（默认 20）分批进行 LLM 调用
- **最大调用次数**——`max-calls-per-run`（默认 10）限制每次提取的总 LLM 调用次数
- **空运行模式**——设置 `dry-run: true` 记录提取意图但不调用 LLM
- **先前上下文上限**——`max-prior-chars`（默认 3000）防止因先前结果增长导致的 Token 成本递增

### 隐私考量

- **访问控制是应用层职责**——记忆系统负责存储和提取，调用方决定谁能查询
- **用户隔离**——基于 userId 的提取确保个人数据不会交叉污染
- **提示词注入防护**——观测数据中的用户内容在纳入 LLM 提示词前会进行清理（转义 `SYSTEM:` 等特殊标记）
- **数据保留**——旧提取结果作为历史保留；请根据需要实现自己的保留策略

### 故障排除

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 无提取结果 | `extraction.enabled` 为 `false` | 设置 `app.memory.extraction.enabled=true` |
| 提取运行但返回空 | 无观测数据匹配 `source-filter` | 检查观测数据的 source 值是否匹配 |
| 模板未加载 | YAML 文件不在模板目录中 | 检查 `templates-dir` 路径和 YAML 语法 |
| LLM 返回无效 JSON | Schema 合规依赖提示词 + LLM | 启用重试逻辑；解析失败时最多重试 3 次 |
| Token 成本持续增长 | 先前提取上下文无限制 | 检查 `max-prior-chars` 设置（默认 3000） |
| 重复提取 | 定时任务和手动触发间的竞态条件 | 项目级锁处理此问题；确保两者使用相同的锁 |

### 死信队列（DLQ）

失败的提取存储为 `ObservationEntity`，`type=extraction_failed`。定时重试任务处理 DLQ 条目。失败条目在 `extractedData` 中包含错误详情用于调试：

```json
{
  "template": "user_preference",
  "error": "LLM 在 3 次重试后仍返回无效 JSON",
  "failedAt": "2026-03-22T02:00:00Z",
  "candidateCount": 15
}
```

## SDK 集成

### Java SDK

[Cortex Memory Spring Integration](../cortex-mem-spring-integration/README.md) 提供提取 API：

```java
// 获取用户的最新提取结果
Map<String, Object> extraction = client.getLatestExtraction(
    "/my-project", "user_preference", "alice");

// 获取提取历史
List<Map<String, Object>> history = client.getExtractionHistory(
    "/my-project", "user_preference", "alice", 10);

// 使用 userId 的 ICL 提示词（自动包含提取数据）
ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("推荐手机")
    .project("/my-project")
    .userId("alice")
    .maxChars(2000)
    .build());

// 使用 userId 过滤的经验查询
List<Experience> experiences = client.retrieveExperiences(
    ExperienceRequest.builder()
        .task("推荐手机")
        .project("/my-project")
        .userId("alice")
        .count(4)
        .build());
```

### Go SDK

```go
// 获取最新提取结果
extraction, err := client.GetLatestExtraction(ctx, &pb.ExtractionRequest{
    ProjectPath:  "/my-project",
    TemplateName: "user_preference",
    UserId:       "alice",
})

// 获取提取历史
history, err := client.GetExtractionHistory(ctx, &pb.ExtractionHistoryRequest{
    ProjectPath:  "/my-project",
    TemplateName: "user_preference",
    UserId:       "alice",
    Limit:        10,
})
```

### 后端 API 对照

| SDK 方法 | 后端端点 | 说明 |
|----------|----------|------|
| `getLatestExtraction()` | `GET /api/extraction/{template}/latest` | 查询参数：projectPath、userId |
| `getExtractionHistory()` | `GET /api/extraction/{template}/history` | 查询参数：projectPath、userId、limit |
| `triggerExtraction()` | `POST /api/extraction/run` | 请求体：projectPath、templateName?、userId? |

---

*设计详情请参阅 [Phase 3 设计文档](drafts/phase-3-design.md) 和 [场景分析](drafts/phase-3-design-walkthrough.md)。*
