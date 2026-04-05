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
│ 4. 调用 LLM（Schema 注入提示词或 BeanOutputConverter）          │
│ 5. 验证并存储为 ObservationEntity（extractedData）            │
└──────────────────────────────────────────────────────────────┘
```

**架构概要：**

- **5 个生命周期钩子** → SessionStart、UserPromptSubmit、PostToolUse、Summary、SessionEnd 产生观测数据并存入 PostgreSQL
- **ExtractionConfig**（YAML 模板）→ 定义提取什么、使用哪些提示词、输出 Schema
- **StructuredExtractionService** → 通用引擎，对观测数据运行模板
- **DeepRefine 集成** → 提取可在 `deepRefineProjectMemories()` 的最后一步运行（精炼之后），或通过手动触发（`POST /api/extraction/run`）。定时任务（`app.memory.refine-schedule-interval-ms`，默认：5 分钟）仅运行快速精炼——提取需手动触发或集成到应用工作流中。
- **存储** → 结果存储为 `ObservationEntity`，`type=extracted_{template}`，`extractedData` 为 JSONB 列
- **追加式提取（Append-Only）** → 后续提取采用追加式方式：LLM 仅输出 `add`/`remove`/`keep_hint` 操作（提示词中不包含先前上下文），然后服务端与数据库中的完整先前数据合并。这避免了截断导致的静默数据丢失，同时降低了 Token 成本。首次提取（无先前数据）使用全量状态提取。

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

在 `application.yml` 的 `app.memory.extraction.templates` 下添加模板定义：

```yaml
# config/extraction-templates/user_preferences.yml
templates:
  - name: "user_preference"
    enabled: true
    template-class: "java.util.Map"
    session-id-pattern: "pref:{project}:{userId}"
    key-fields: ["category", "value"]
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

通过 API 手动触发提取：

```bash
curl -X POST "http://localhost:37777/api/extraction/run?projectPath=/my-project"
```

### 第 5 步：查询结果

```bash
# 获取模板的最新提取结果
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# 获取提取历史
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

## 配置参考

### application.yml 设置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `app.memory.extraction.enabled` | boolean | `false` | 全局启用结构化提取 |
| `app.memory.extraction.initial-run-max-candidates` | int | `100` | 每个模板首次提取的候选数据上限 |
| `app.memory.extraction.max-observations-per-batch` | int | `20` | 每次 LLM 调用处理的最大观测数据数量 |
| `app.memory.extraction.max-batches-per-template` | int | `10` | 每次提取运行每个模板的最大批次数（安全限制） |

模板通过 `app.memory.extraction.templates` 在 `application.yml` 中内联配置（见下方格式说明）。

### 模板 YAML 格式

每个 YAML 文件定义一个或多个提取模板：

```yaml
templates:
  - name: "template_name"              # 必填。唯一标识符，存储为 type="extracted_{name}"
    enabled: true                       # 可选。默认：true。模板级别开关。
    template-class: "java.util.Map"     # 必填。输出类："java.util.Map"（灵活）或 POJO 类名。
    session-id-pattern: "pref:{project}:{userId}"  # 可选。结果存储位置。变量：{project}、{userId}。null = 继承源会话。
    key-fields: ["field1", "field2"]    # 可选。去重键字段。
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

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 要运行提取的绝对项目路径 |

**示例：**

```bash
curl -X POST "http://localhost:37777/api/extraction/run?projectPath=/my-project"
```

**响应（200）：**

```json
{
  "status": "ok",
  "projectPath": "/my-project",
  "message": "Extraction completed"
}
```

### GET /api/extraction/{templateName}/latest

获取模板的最新提取结果。

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 项目路径 |
| `userId` | 否 | 按用户 ID 过滤 |

**响应（200，有数据）：**

```json
{
  "status": "ok",
  "template": "user_preference",
  "sessionId": "pref:abc123:alice",
  "extractedData": {
    "preferences": [
      {
        "category": "手机品牌",
        "value": "小米",
        "sentiment": "positive",
        "confidence": 0.95
      }
    ]
  },
  "createdAt": 1742639400000,
  "observationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**响应（200，无数据）：**

```json
{
  "status": "not_found",
  "template": "user_preference",
  "message": "No extraction found"
}
```

### GET /api/extraction/{templateName}/history

获取模板的提取历史（所有快照）。

**查询参数：**

| 参数 | 必填 | 说明 |
|------|------|------|
| `projectPath` | 是 | 项目路径 |
| `userId` | 否 | 按用户 ID 过滤 |
| `limit` | 否 | 最大结果数（默认：10，最大：100） |

**响应（200）：** JSON 数组，包含提取记录：

```json
[
  {
    "sessionId": "pref:abc123:alice",
    "extractedData": {
      "preferences": [{"category": "手机品牌", "value": "小米"}]
    },
    "createdAt": 1742639400000,
    "observationId": "550e8400-e29b-41d4-a716-446655440000"
  },
  {
    "sessionId": "pref:abc123:alice",
    "extractedData": {
      "preferences": [{"category": "手机品牌", "value": "苹果"}]
    },
    "createdAt": 1742553000000,
    "observationId": "660e8400-e29b-41d4-a716-446655440001"
  }
]
```

## 智能体如何利用提取结果

提取结果存储为 `ObservationEntity`（`type=extracted_{template}`, `extractedData` 为 JSONB）。这个设计意味着提取数据**自然融入**整个观测数据生态系统——不仅仅是独立的 API 响应。

### 消费路径总览

```
                              ┌─────────────────────┐
                              │  结构化信息提取服务    │
                              └──────────┬──────────┘
                                         │
                              ┌──────────▼──────────┐
                              │ ObservationEntity     │
                              │ type=extracted_{name} │
                              │ extractedData=JSONB   │
                              │ embedding=向量        │
                              └──────────┬──────────┘
                                         │
                 ┌───────────┬───────────┼───────────┬───────────┐
                 │           │           │           │           │
            ┌────▼────┐ ┌───▼────┐ ┌────▼────┐ ┌────▼────┐ ┌───▼────┐
            │ 直接API │ │ 搜索   │ │ 经验    │ │ ICL     │ │ 上下文 │
            │ 查询    │ │ 发现   │ │ RAG     │ │ 提示词  │ │ 注入   │
            └─────────┘ └────────┘ └─────────┘ └─────────┘ └────────┘
```

### 路径 1：直接 API 查询

最明确的方式——按模板名和用户 ID 查询提取结果。

```bash
# 获取最新提取结果
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# 获取提取历史
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

**适用场景**：明确知道需要哪个模板、哪个用户。最适合应用层功能，如"显示用户偏好"或"检查过敏信息"。

**SDK 示例（Java）**：
```java
Map<String, Object> prefs = client.getLatestExtraction("/project", "user_preference", "alice");
// 返回: {preferences: [{category: "手机品牌", value: "小米", sentiment: "positive"}]}
```

### 路径 2：搜索发现

因为提取结果存储为带向量的观测数据，它们**自动可被发现**——通过语义搜索和关键词搜索。

```bash
# 语义搜索可能命中提取观测数据
curl "http://localhost:37777/api/search?project=/my-project&query=用户手机偏好&limit=5"
```

搜索结果可能包含 `extracted_user_preference` 类型的观测数据，以及普通观测数据。`extractedData` 字段包含结构化 JSON。

**适用场景**：智能体不知道该查哪个模板——它只是自然地搜索相关信息。这是"发现"路径。

**示例流程**：
1. 用户问："Alice 喜欢什么手机？"
2. 智能体搜索：`query="Alice 手机 偏好" project="/family-project"`
3. 搜索返回：`type=extracted_user_preference` 的观测数据，`extractedData={preferences: [{category: "手机品牌", value: "小米"}]}`
4. 智能体使用结构化数据回答

### 路径 3：经验 RAG

经验 RAG 系统（`POST /api/memory/experiences`）从观测数据中检索相关的历史经验。提取结果作为观测数据参与其中。

```bash
curl -X POST "http://localhost:37777/api/memory/experiences" \
  -H 'Content-Type: application/json' \
  -d '{"task": "推荐适合Alice的手机", "project": "/family-project", "count": 4}'
```

返回的经验中可能包含提取产生的观测数据，格式化为可复用的经验卡片（task/strategy/outcome 结构）。

**适用场景**：智能体需要关于某任务的"过去经验"，而用户偏好/提取数据是这些经验的一部分。

### 路径 4：ICL 提示词构建

ICL（In-Context Learning）提示词端点从经验构建提示词：

```bash
curl -X POST "http://localhost:37777/api/memory/icl-prompt" \
  -H 'Content-Type: application/json' \
  -d '{"task": "推荐手机", "project": "/family-project", "userId": "alice", "maxChars": 2000}'
```

**工作原理**：ICL → 检索经验 → 经验搜索观测数据 → 提取观测数据被包含。提取的结构化数据丰富了 ICL 提示词中的结构化事实。

**适用场景**：将上下文注入 LLM 提示词以完成任务。提取数据为 LLM 提供结构化的"事实依据"。

### 路径 5：上下文注入

上下文生成端点（`/api/context/inject`, `/api/context/generate`）从所有项目观测数据生成上下文：

```bash
curl "http://localhost:37777/api/context/inject?projects=/my-project"
```

生成的上下文包含摘要和观测数据——提取结果作为 `type=extracted_{name}` 的普通观测数据被自动包含。

**适用场景**：构建上下文注入管道（如 Claude Code 钩子）。提取数据自动流入注入的上下文。

### 如何选择合适的路径

| 场景 | 推荐路径 | 原因 |
|------|----------|------|
| "显示 Alice 的偏好" | **直接 API** | 明确知道模板和用户 |
| "我们对 Alice 了解什么？" | **搜索** | 发现——不知道存在什么信息 |
| "上次推荐手机什么策略有效？" | **经验 RAG** | 需要过去的经验教训 |
| "为 Alice 构建手机推荐提示词" | **ICL 提示词** | 需要为 LLM 准备结构化上下文 |
| "为 Alice 的会话注入上下文" | **上下文注入** | 自动管道集成 |

### 关键架构洞察

将提取结果存储为 `ObservationEntity` 是一个刻意的设计决策。这意味着：

- **无需单独的集成代码** — 提取数据自动参与搜索、经验、ICL 和上下文注入
- **一致的访问模式** — 处理普通观测数据的 API 同样适用于提取结果
- **基于向量的可发现性** — 提取结果有嵌入向量，支持语义搜索
- **仅追加的历史** — 每次提取运行创建新的观测数据，保留完整历史
- **追加式提取** — 后续运行使用 `add`/`remove`/`keep_hint` 操作（LLM 提示词中不包含先前上下文），防止截断导致的数据丢失，同时 Token 成本比完整先前方案低约 20%

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
2. 提取运行（通过 API 手动触发）
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

**追加式提取如何处理：**

首次提取使用全量状态提取（LLM 产生完整状态）。后续提取使用**追加式方式**：LLM 仅接收新观测数据（不包含先前上下文），输出 `add`/`remove`/`keep_hint` 操作。服务端将这些操作与数据库中的完整先前数据合并：

```
运行 1（无先前数据，全量状态）：
  LLM 输出 → [{category: "耳机", value: "Sony", sentiment: "positive"}]

运行 2（追加式，新观测 "Bose也不错"）：
  LLM 输出 → {add: [{category: "耳机", value: "Bose", sentiment: "positive"}],
               keep_hint: [{category: "耳机", value: "Sony"}]}
  服务端合并 → [{category: "耳机", value: "Sony"}, {category: "耳机", value: "Bose"}]

运行 3（追加式，新观测 "不喜欢Sony了"）：
  LLM 输出 → {remove: [{category: "耳机", value: "Sony"}]}
  服务端合并 → [{category: "耳机", value: "Bose"}]
```

**关键要点：**
- **追加式防止数据丢失**——先先前上下文不会被截断或传入 LLM，因此较早的条目不会静默消失
- **更低的 Token 成本**——提示词中不包含先前上下文（约 2000 tokens vs 完整先前方案约 7000 tokens）
- 旧提取结果作为历史保留（时间戳区分当前和历史）
- `keep_hint` 确保被正面提及的条目即使未重新声明也会被保留

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

1. **`template-class: "java.util.Map"`** → 使用 `llmService.chatCompletion()`，`output-schema` 作为格式指令附加到系统提示词中，然后手动解析 JSON 响应
2. **`template-class: "com.example.MyPojo"`** → 使用 `llmService.chatCompletionStructured()` 内部使用 `BeanOutputConverter<MyPojo>`，Schema 通过 Java 类自动推导

对于 Map 模板，`output-schema` 以 JSON Schema 形式附加到系统提示词。LLM 被指示返回符合 Schema 的 JSON，但没有运行时 Schema 强制——解析依赖于 LLM 的合规性。对于 POJO 模板，`BeanOutputConverter` 提供更强的类型安全。

**存储映射：**

| 模板字段 | ObservationEntity 字段 |
|----------|------------------------|
| `name` | `type` = `"extracted_{name}"` |
| `source-filter` | 决定哪些观测数据为候选 |
| `session-id-pattern` | 结果观测的 `contentSessionId` |
| 输出数据 | `extractedData`（JSONB 列） |

### 成本控制与速率限制

提取成本通过以下机制管理：

- **按需处理**——提取通过 API 触发运行，非逐条观测实时处理
- **首次运行上限**——`initial-run-max-candidates`（默认 100）限制首次运行处理量
- **批处理大小**——观测数据按 `max-observations-per-batch`（默认 20）分批进行 LLM 调用
- **最大批次数**——`max-batches-per-template`（默认 10）限制每次提取每个模板的总批次数

### 隐私考量

- **访问控制是应用层职责**——记忆系统负责存储和提取，调用方决定谁能查询
- **用户隔离**——基于 userId 的提取确保个人数据不会交叉污染
- **提示词注入防护**——观测数据中的用户内容带有来源标注，并限制长度，帮助 LLM 区分用户内容和系统指令
- **数据保留**——旧提取结果作为历史保留；请根据需要实现自己的保留策略

### 故障排除

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 无提取结果 | `extraction.enabled` 为 `false` | 设置 `app.memory.extraction.enabled=true` |
| 提取运行但返回空 | 无观测数据匹配 `source-filter` | 检查观测数据的 source 值是否匹配 |
| 模板未加载 | 模板未在 application.yml 中配置 | 检查 `app.memory.extraction.templates` 配置 |
| LLM 返回无效 JSON | Schema 合规依赖提示词 + LLM | 启用重试逻辑；解析失败时最多重试 3 次 |
| Token 成本持续增长 | 每批观测数据过多 | 检查 `max-observations-per-batch` 和 `max-batches-per-template` 设置 |
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
| `getLatestExtraction()` | `GET /api/extraction/{templateName}/latest` | 查询参数：projectPath、userId |
| `getExtractionHistory()` | `GET /api/extraction/{templateName}/history` | 查询参数：projectPath、userId、limit |
| `triggerExtraction()` | `POST /api/extraction/run` | 查询参数：projectPath |

---

*设计详情请参阅 [Phase 3 设计文档](drafts/phase-3-design.md) 和 [场景分析](drafts/phase-3-design-walkthrough.md)。*
