# Structured Information Extraction

[中文](structured-extraction-zh-CN.md) | English

## Overview

Cortex CE's **Structured Information Extraction** is a generic, prompt-driven system that automatically extracts structured data from conversation observations. Instead of storing raw conversation text, it identifies and organizes meaningful facts — user preferences, allergy information, important dates, contact details, and more — into queryable structured records.

**Why does it exist?** Traditional memory systems store observations as-is, making semantic queries difficult. When an AI assistant needs to remember "the user's budget range" or "which family member is allergic to peanuts", raw observations are hard to parse. Structured extraction transforms unstructured conversation data into well-defined JSON schemas that applications can query directly — `GET /api/extraction/user_preference/latest` returns `{preferences: [{category: "手机", value: "小米", sentiment: "positive"}]}` instead of "用户在对话中提到喜欢小米手机".

The core design principle is **configuration over code**: what to extract is defined by YAML template prompts and schemas, not by Java code. Adding a new extraction type is a YAML change, not a code change.

## How It Works

The extraction pipeline operates in 5 stages:

```
┌──────────────────────────────────────────────────────────────┐
│ Extraction Pipeline (per template per user)                  │
├──────────────────────────────────────────────────────────────┤
│ 1. Find candidate observations (source-filter + time range)  │
│ 2. Group by user (via SessionEntity → userId)                │
│ 3. Build prompt (template.prompt + observations + prior)     │
│ 4. Call LLM via BeanOutputConverter (schema-enforced output)  │
│ 5. Validate & store as ObservationEntity (extracted_data)    │
└──────────────────────────────────────────────────────────────┘
```

**Architecture summary:**

- **5 Lifecycle Hooks** → SessionStart, UserPromptSubmit, PostToolUse, Summary, SessionEnd produce observations in PostgreSQL
- **ExtractionConfig** (YAML templates) → Define what to extract, which prompts to use, output schemas
- **StructuredExtractionService** → Generic engine that runs templates against observations
- **DeepRefine integration** → Extraction runs as the last step of `deepRefineProjectMemories()` (after refinement), or via scheduled cron (daily at 2am)
- **Storage** → Results stored as `ObservationEntity` with `type=extracted_{template}` and `extracted_data` JSONB column
- **LLM Re-extraction** → Each run includes prior extraction as context; the LLM produces a complete current state, handling updates, removals, and conflicts semantically

## Quick Start

### Step 1: Enable the Feature

Add to `application.properties` or environment variables:

```properties
app.memory.extraction.enabled=true
```

Or via environment variable:

```bash
EXTRACTION_ENABLED=true
```

### Step 2: Configure a Template

Create a YAML template file in the templates directory (`app.memory.extraction.templates-dir`, default: `config/extraction-templates/`):

```yaml
# config/extraction-templates/user_preferences.yml
templates:
  - name: "user_preference"
    enabled: true
    template-class: "java.util.Map"
    session-id-pattern: "pref:{project}:{userId}"
    key-fields: ["category", "value"]
    description: "Extract user preferences from conversations"
    source-filter: ["user_statement", "manual"]
    prompt: |
      From the following conversation, extract user preferences.
      Look for: brands they like/dislike, budget constraints, style preferences.
      Return ALL preferences found, not just one.
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

### Step 3: Start the Service

```bash
cd backend
mvn clean install -DskipTests
java -jar target/cortex-ce-*.jar
```

Or with Docker:

```bash
docker compose up -d
```

### Step 4: Trigger Extraction

Extraction runs automatically during deep refinement (daily at 2am by default). To trigger manually:

```bash
curl -X POST http://localhost:37777/api/extraction/run \
  -H "Content-Type: application/json" \
  -d '{"projectPath": "/my-project"}'
```

### Step 5: Query Results

```bash
# Get latest extraction result for a template
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# Get extraction history
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

## Configuration Reference

### application.properties Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.memory.extraction.enabled` | boolean | `false` | Enable structured extraction globally |
| `app.memory.extraction.templates-dir` | String | `config/extraction-templates/` | Directory for YAML template files |
| `app.memory.extraction.schedule` | String | `0 0 2 * * ?` | Cron schedule for periodic extraction |
| `app.memory.extraction.batch-size` | int | `20` | Observations per LLM call batch |
| `app.memory.extraction.max-tokens-per-call` | int | `8000` | Max tokens per extraction LLM call |
| `app.memory.extraction.max-prior-chars` | int | `3000` | Max characters for prior extraction context |
| `app.memory.extraction.initial-run-max-candidates` | int | `500` | Cap for first extraction run per template |
| `app.memory.extraction.cost-control.dry-run` | boolean | `false` | Log extraction intent without calling LLM |
| `app.memory.extraction.cost-control.max-calls-per-run` | int | `10` | Max LLM calls per extraction run |

### Template YAML Format

Each YAML file defines one or more extraction templates:

```yaml
templates:
  - name: "template_name"              # Required. Unique identifier, stored as type="extracted_{name}"
    enabled: true                       # Optional. Default: true. Per-template enable/disable.
    template-class: "java.util.Map"     # Required. Output class: "java.util.Map" (flexible) or a POJO class name.
    session-id-pattern: "pref:{project}:{userId}"  # Optional. Where to store results. Variables: {project}, {userId}. Null = inherit source session.
    key-fields: ["field1", "field2"]    # Optional. Fields used for deduplication.
    description: "Human-readable description"  # Optional.
    trigger-keywords: ["keyword1"]      # Optional. Keywords for future keyword-triggered extraction.
    source-filter: ["user_statement"]   # Required. Which observation sources to consider.
    prompt: |                           # Required. System prompt for the LLM extraction call.
      Extract structured information from the conversation.
      Return results matching the output schema.
    output-schema: |                    # Required for Map templates. Auto-derived from class for POJO templates.
      {"type": "object", "properties": {...}}
```

### Output Class Options

| template-class | Use Case | Schema Source | Type Safety |
|----------------|----------|---------------|-------------|
| `java.util.Map` | Flexible, any schema | `output-schema` in YAML | None (post-processing needed) |
| `com.example.AllergyInfo` | Stable, well-defined schema | Auto-derived from Java class | Full compile-time safety |

### Template Examples

**Allergy Information (POJO):**

```yaml
templates:
  - name: "allergy_info"
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual", "llm_inference"]
    key-fields: ["person", "allergens"]
    prompt: |
      Extract allergy and dietary information from the conversation:
      - Who has the allergy (person)
      - What allergens
      - Severity if mentioned
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

**Important Dates:**

```yaml
templates:
  - name: "important_dates"
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual"]
    key-fields: ["date", "occasion"]
    prompt: |
      Extract important dates mentioned: birthdays, anniversaries, events.
      Include: date, occasion, who's involved.
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

## API Reference

### POST /api/extraction/run

Manually trigger extraction for a project. Runs all enabled templates.

**Request:**

```json
{
  "projectPath": "/my-project",
  "templateName": "user_preference",    // Optional. Run specific template only.
  "userId": "alice"                     // Optional. Run for specific user only.
}
```

**Response (200):**

```json
{
  "status": "completed",
  "templatesRun": ["user_preference", "allergy_info"],
  "extractionsCreated": 3,
  "durationMs": 4521
}
```

### GET /api/extraction/{templateName}/latest

Get the most recent extraction result for a template.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Project path |
| `userId` | No | Filter by user ID |

**Response (200):**

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

Get extraction history (all snapshots) for a template.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Project path |
| `userId` | No | Filter by user ID |
| `limit` | No | Max results (default: 10) |

**Response (200):**

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

Search extractions by field value.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Project path |
| `fieldPath` | Yes | JSON path in extracted data (e.g., `allergens`) |
| `value` | Yes | Value to search for |

**Example:**

```bash
# Find who is allergic to peanuts
curl "http://localhost:37777/api/extraction/allergy_info/search?projectPath=/my-project&fieldPath=allergens&value=花生"
```

## Scenarios

### Scenario 1: User Preferences Extraction

A user tells their AI assistant:

> "我不喜欢苹果手机" → "我更喜欢小米" → "预算3000-4000"

**Configuration:**

```yaml
templates:
  - name: "user_preference"
    template-class: "java.util.Map"
    session-id-pattern: "pref:{project}:{userId}"
    source-filter: ["user_statement"]
    prompt: |
      Extract user preferences from the conversation.
      Look for: brands they like/dislike, budget, style.
    output-schema: |
      {"type": "object", "properties": {"preferences": {"type": "array", "items": {"type": "object", "properties": {
        "category": {"type": "string"}, "value": {"type": "string"}, "sentiment": {"type": "string", "enum": ["positive", "negative", "neutral"]}, "confidence": {"type": "number"}
      }}}}}
```

**Runtime behavior:**
1. Observations are captured via hooks (source = `user_statement`)
2. Extraction runs (scheduled or manual trigger)
3. LLM receives observations + template prompt
4. Result stored in session `pref:/my-project:alice`

**Query result:**

```json
{
  "preferences": [
    {"category": "手机品牌(排斥)", "value": "苹果", "sentiment": "negative", "confidence": 0.95},
    {"category": "手机品牌(偏好)", "value": "小米", "sentiment": "positive", "confidence": 0.90},
    {"category": "预算", "value": "3000-4000", "sentiment": "neutral", "confidence": 0.85}
  ]
}
```

### Scenario 2: Multi-user Isolation

Family "Zhang" has 4 members. Each member uses the system independently.

**How it works:**
- Each user has a different `userId` (e.g., `alice`, `bob`, `charlie`, `diana`)
- Extraction state is tracked per user — Alice's preferences don't affect Bob's
- Results are stored in user-scoped sessions (`pref:/project:alice`, `pref:/project:bob`)

```java
// Java SDK
client.startSession(SessionStartRequest.builder()
    .sessionId("conv-123")
    .projectPath("/family-project")
    .userId("alice")  // Multi-user identifier
    .build());

// Query Alice's preferences specifically
Map<String, Object> extraction = client.getLatestExtraction(
    "/family-project", "user_preference", "alice");
```

### Scenario 3: Re-extraction and Conflict Handling

User preferences evolve over time:

```
2025-01: "I love Sony headphones"
2025-06: "Actually, Bose noise cancellation is better"
2026-01: "I don't like Sony anymore"
```

**How LLM re-extraction handles this:**

Each extraction includes the **prior result as context**. The LLM produces a complete current state, deciding what to keep or remove based on semantic understanding:

```
Run 1: LLM output → [{category: "耳机", value: "Sony", sentiment: "positive"}]

Run 2 (prior + "Bose也不错"): 
  LLM output → [{category: "耳机", value: "Sony", sentiment: "positive"}, 
                 {category: "耳机", value: "Bose", sentiment: "positive"}]

Run 3 (prior + "不喜欢Sony了"):
  LLM output → [{category: "耳机", value: "Bose", sentiment: "positive"}]
  removed: [{category: "耳机", value: "Sony", reason: "用户说不再喜欢"}]
```

**Key points:**
- Old extractions are preserved as history (timestamp distinguishes current vs historical)
- No programmatic merge logic — LLM handles semantics
- Optional `removed` field tracks what was dropped and why

### Scenario 4: Custom Template (Allergies)

Define a completely custom extraction type:

```yaml
templates:
  - name: "allergy_info"
    enabled: true
    template-class: "java.util.Map"
    source-filter: ["user_statement", "manual"]
    key-fields: ["person", "allergens"]
    prompt: |
      Extract allergy and dietary information from the conversation.
      Look for: who is allergic, what allergens, severity.
      Be precise — medical information must be accurate.
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
                "source": {"type": "string", "description": "Origin: observation ID or 'prior'"}
              }
            }
          }
        }
      }
```

User says: "孩子对花生过敏，很严重"

**Extraction result:**

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

## Advanced Topics

### How Templates Map to Backend Schemas

Templates are YAML configuration loaded at startup. The `StructuredExtractionService` resolves each template:

1. **`template-class: "java.util.Map"`** → Uses `BeanOutputConverter<Map>`, schema from `output-schema` YAML field
2. **`template-class: "com.example.MyPojo"`** → Uses `BeanOutputConverter<MyPojo>`, schema auto-derived from Java class via `getJsonSchema()`

For Map templates, the `output-schema` is injected into the system prompt as format instructions. For POJO templates, `BeanOutputConverter` handles this automatically.

**Storage mapping:**

| Template Field | ObservationEntity Field |
|----------------|------------------------|
| `name` | `type` = `"extracted_{name}"` |
| `source-filter` | Determines which observations are candidates |
| `session-id-pattern` | `contentSessionId` of the result observation |
| Output data | `extractedData` (JSONB column) |

### Cost Control and Rate Limiting

Extraction costs are managed through several mechanisms:

- **Scheduled batch** (not real-time) — extraction runs daily, not per-observation
- **Incremental processing** — only new observations since last extraction are processed
- **Initial run cap** — `initial-run-max-candidates` (default 500) limits first-run processing
- **Batch size** — observations are chunked into batches of `batch-size` (default 20) per LLM call
- **Max calls per run** — `max-calls-per-run` (default 10) caps total LLM calls
- **Dry run mode** — set `dry-run: true` to log extraction intent without calling LLM
- **Prior context cap** — `max-prior-chars` (default 3000) prevents token cost escalation from growing prior results

### Privacy Considerations

- **Access control is application-layer responsibility** — the memory system stores and extracts; the caller decides who can query what
- **User isolation** — userId-based extraction ensures personal data is not cross-contaminated
- **Prompt injection prevention** — user content in observations is sanitized before inclusion in LLM prompts (special tokens like `SYSTEM:` are escaped)
- **Data retention** — old extractions are preserved as history; implement your own retention policies as needed

### Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| No extraction results | `extraction.enabled` is `false` | Set `app.memory.extraction.enabled=true` |
| Extraction runs but returns empty | No observations match `source-filter` | Check that observations have matching source values |
| Template not loaded | YAML file not in templates directory | Verify `templates-dir` path and YAML syntax |
| LLM returns invalid JSON | Schema compliance relies on prompt + LLM | Enable retry logic; extraction retries up to 3 times on parse failure |
| Token cost growing | Prior extraction context is unbounded | Check `max-prior-chars` setting (default 3000) |
| Duplicate extractions | Race condition between scheduled and manual | Project-level locking handles this; ensure both use the same lock |

### Dead Letter Queue (DLQ)

Failed extractions are stored as `ObservationEntity` with `type=extraction_failed`. A scheduled retry task processes DLQ entries. Failed entries include error details in `extractedData` for debugging:

```json
{
  "template": "user_preference",
  "error": "LLM returned invalid JSON after 3 retries",
  "failedAt": "2026-03-22T02:00:00Z",
  "candidateCount": 15
}
```

## Integration with SDKs

### Java SDK

The [Cortex Memory Spring Integration](../cortex-mem-spring-integration/README.md) provides extraction APIs:

```java
// Get latest extraction for a user
Map<String, Object> extraction = client.getLatestExtraction(
    "/my-project", "user_preference", "alice");

// Get extraction history
List<Map<String, Object>> history = client.getExtractionHistory(
    "/my-project", "user_preference", "alice", 10);

// ICL prompt with userId (includes extracted data automatically)
ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
    .task("推荐手机")
    .project("/my-project")
    .userId("alice")
    .maxChars(2000)
    .build());

// Experiences with userId filtering
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
// Get latest extraction
extraction, err := client.GetLatestExtraction(ctx, &pb.ExtractionRequest{
    ProjectPath:  "/my-project",
    TemplateName: "user_preference",
    UserId:       "alice",
})

// Get extraction history
history, err := client.GetExtractionHistory(ctx, &pb.ExtractionHistoryRequest{
    ProjectPath:  "/my-project",
    TemplateName: "user_preference",
    UserId:       "alice",
    Limit:        10,
})
```

### Backend API Alignment

| SDK Method | Backend Endpoint | Notes |
|------------|-----------------|-------|
| `getLatestExtraction()` | `GET /api/extraction/{template}/latest` | Query params: projectPath, userId |
| `getExtractionHistory()` | `GET /api/extraction/{template}/history` | Query params: projectPath, userId, limit |
| `triggerExtraction()` | `POST /api/extraction/run` | Body: projectPath, templateName?, userId? |

---

*For design details, see [Phase 3 Design](drafts/phase-3-design.md) and [Walkthrough](drafts/phase-3-design-walkthrough.md).*
