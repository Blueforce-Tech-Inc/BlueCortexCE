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
│ 4. Call LLM with structured prompt (schema-injected or BeanOutputConverter) │
│ 5. Validate & store as ObservationEntity (extractedData)    │
└──────────────────────────────────────────────────────────────┘
```

**Architecture summary:**

- **5 Lifecycle Hooks** → SessionStart, UserPromptSubmit, PostToolUse, Summary, SessionEnd produce observations in PostgreSQL
- **ExtractionConfig** (YAML templates) → Define what to extract, which prompts to use, output schemas
- **StructuredExtractionService** → Generic engine that runs templates against observations
- **DeepRefine integration** → Extraction can run as the last step of `deepRefineProjectMemories()` (after refinement), or via manual trigger (`POST /api/extraction/run`). The periodic scheduled task (`app.memory.refine-schedule-interval-ms`, default: 5 minutes) runs quick refinement only — extraction must be triggered manually or integrated into your application's workflow.
- **Storage** → Results stored as `ObservationEntity` with `type=extracted_{template}` and `extractedData` JSONB column
- **Append-Only Extraction** → Subsequent extractions use an append-only approach: the LLM outputs only `add`/`remove`/`keep_hint` operations (no prior context in prompt), then the service merges with the full prior from DB. This prevents silent data loss from truncation while keeping token costs low. First extraction (no prior) uses full-state extraction.

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

Add template definitions to `application.yml` under `app.memory.extraction.templates`:

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

Extraction is triggered manually via API. To trigger:

```bash
curl -X POST "http://localhost:37777/api/extraction/run?projectPath=/my-project"
```

### Step 5: Query Results

```bash
# Get latest extraction result for a template
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# Get extraction history
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

## Configuration Reference

### application.yml Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.memory.extraction.enabled` | boolean | `false` | Enable structured extraction globally |
| `app.memory.extraction.initial-run-max-candidates` | int | `100` | Max candidates for first extraction run per template |
| `app.memory.extraction.max-observations-per-batch` | int | `20` | Max observations per LLM call batch |
| `app.memory.extraction.max-batches-per-template` | int | `10` | Max batches per template per run (safety limit) |

Templates are configured inline under `app.memory.extraction.templates` in `application.yml` (see format below).

### Template YAML Format

Each YAML file defines one or more extraction templates:

```yaml
templates:
  - name: "template_name"              # Required. Unique identifier, stored as type="extracted_{name}"
    enabled: true                       # Optional. Default: true. Per-template enable/disable.
    template-class: "java.util.Map"     # Required. Output class: "java.util.Map" (flexible) or a POJO class name.
    session-id-pattern: "pref:{project}:{userId}"  # Optional. Where to store results. Variables: {project}, {userId}. Null = inherit source session.
    key-fields: ["field1", "field2"]    # Optional. Fields used for deduplication.
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

**Allergy Information (Map):**

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

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Absolute project path to run extraction for |

**Example:**

```bash
curl -X POST "http://localhost:37777/api/extraction/run?projectPath=/my-project"
```

**Response (200):**

```json
{
  "status": "ok",
  "projectPath": "/my-project",
  "message": "Extraction completed"
}
```

### GET /api/extraction/{templateName}/latest

Get the most recent extraction result for a template.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Project path |
| `userId` | No | Filter by user ID |

**Response (200, found):**

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

**Response (200, not found):**

```json
{
  "status": "not_found",
  "template": "user_preference",
  "message": "No extraction found"
}
```

### GET /api/extraction/{templateName}/history

Get extraction history (all snapshots) for a template.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `projectPath` | Yes | Project path |
| `userId` | No | Filter by user ID |
| `limit` | No | Max results (default: 10, max: 100) |

**Response (200):** JSON array of extraction records:

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

## How Agents Consume Extraction Results

Extraction results are stored as `ObservationEntity` records (`type=extracted_{template}`, `extractedData` as JSONB). This design means extraction data naturally participates in the entire observation ecosystem — not just as standalone API responses.

### Consumption Path Overview

```
                              ┌─────────────────────┐
                              │  Structured          │
                              │  Extraction Service  │
                              └──────────┬──────────┘
                                         │
                              ┌──────────▼──────────┐
                              │ ObservationEntity     │
                              │ type=extracted_{name} │
                              │ extractedData=JSONB   │
                              │ embedding=vector      │
                              └──────────┬──────────┘
                                         │
                 ┌───────────┬───────────┼───────────┬───────────┐
                 │           │           │           │           │
            ┌────▼────┐ ┌───▼────┐ ┌────▼────┐ ┌────▼────┐ ┌───▼────┐
            │ Direct  │ │ Search │ │ Experi- │ │  ICL    │ │Context │
            │  API    │ │(Vector │ │  ence   │ │ Prompt  │ │ Inject │
            │ Query   │ │+Keyword│ │  (RAG)  │ │         │ │        │
            └─────────┘ └────────┘ └─────────┘ └─────────┘ └────────┘
```

### Path 1: Direct API Query

The most explicit way — query extraction results by template name and user ID.

```bash
# Get latest extraction
curl "http://localhost:37777/api/extraction/user_preference/latest?projectPath=/my-project&userId=alice"

# Get extraction history
curl "http://localhost:37777/api/extraction/user_preference/history?projectPath=/my-project&userId=alice&limit=10"
```

**When to use**: When you know exactly which template and user you need. Best for application-level features like "show user preferences" or "check allergy information."

**SDK example (Java)**:
```java
Map<String, Object> prefs = client.getLatestExtraction("/project", "user_preference", "alice");
// Returns: {preferences: [{category: "手机品牌", value: "小米", sentiment: "positive"}]}
```

### Path 2: Search Discovery

Because extraction results are stored as observations with embeddings, they are **automatically discoverable** through semantic and keyword search.

```bash
# Semantic search may find extraction observations
curl "http://localhost:37777/api/search?project=/my-project&query=用户手机偏好&limit=5"
```

The search results may include observations of type `extracted_user_preference` alongside regular observations. The `extractedData` field contains the structured JSON.

**When to use**: When the agent doesn't know which template to query — it just searches for relevant information naturally. This is the "discovery" path.

**Example flow**:
1. User asks: "What phone does Alice prefer?"
2. Agent searches: `query="Alice 手机 偏好" project="/family-project"`
3. Search returns: Observation with `type=extracted_user_preference`, `extractedData={preferences: [{category: "手机品牌", value: "小米"}]}`
4. Agent uses the structured data to answer

### Path 3: Experience RAG

The Experience RAG system (`POST /api/memory/experiences`) retrieves relevant past experiences from observations. Extraction results participate as observations, so they are included in experience retrieval when relevant.

```bash
curl -X POST "http://localhost:37777/api/memory/experiences" \
  -H 'Content-Type: application/json' \
  -d '{"task": "推荐适合Alice的手机", "project": "/family-project", "count": 4}'
```

The returned experiences may include extraction-derived observations, formatted as reusable experience cards with task/strategy/outcome structure.

**When to use**: When the agent needs "past lessons" about a task, and user preferences/extraction data are part of those lessons.

### Path 4: ICL Prompt Construction

The ICL (In-Context Learning) prompt endpoint builds a prompt from experiences:

```bash
curl -X POST "http://localhost:37777/api/memory/icl-prompt" \
  -H 'Content-Type: application/json' \
  -d '{"task": "推荐手机", "project": "/family-project", "userId": "alice", "maxChars": 2000}'
```

**How it works**: ICL → retrieves experiences → experiences search observations → extraction observations are included. The structured data from extractions enriches the ICL prompt with structured facts.

**When to use**: When injecting context into an LLM prompt for task completion. The extraction data provides structured "grounding" for the LLM.

### Path 5: Context Injection

The context generation endpoints (`/api/context/inject`, `/api/context/generate`) produce context from all project observations:

```bash
curl "http://localhost:37777/api/context/inject?projects=/my-project"
```

The generated context includes summaries and observations — extraction results are included as they are regular observations with `type=extracted_{name}`.

**When to use**: When building a context injection pipeline (e.g., Claude Code hooks). Extraction data automatically flows into the injected context.

### Choosing the Right Path

| Scenario | Recommended Path | Why |
|----------|-----------------|-----|
| "Show me Alice's preferences" | **Direct API** | Know the template and user |
| "What do we know about Alice?" | **Search** | Discovery — don't know what exists |
| "What worked last time for phone recommendations?" | **Experience RAG** | Need past lessons |
| "Build a prompt for recommending phones to Alice" | **ICL Prompt** | Need structured context for LLM |
| "Inject context for Alice's session" | **Context Injection** | Automatic pipeline integration |

### Key Architectural Insight

Storing extraction results as `ObservationEntity` is a deliberate design choice. It means:

- **No separate integration code** — extraction data automatically participates in search, experiences, ICL, and context injection
- **Consistent access patterns** — the same APIs that work for regular observations work for extraction results
- **Embedding-based discovery** — extraction results have embeddings, enabling semantic search
- **Append-only history** — every extraction run creates a new observation, preserving the full history
- **Append-only extraction** — subsequent runs use `add`/`remove`/`keep_hint` operations (no prior context in LLM prompt), preventing data loss from truncation while keeping token costs ~20% lower than full-prior approaches

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

**How append-only extraction handles this:**

The first extraction uses full-state extraction (LLM produces complete state). Subsequent extractions use the **append-only approach**: the LLM receives only new observations (no prior context) and outputs `add`/`remove`/`keep_hint` operations. The service then merges these with the full prior data from the database:

```
Run 1 (no prior, full-state): 
  LLM output → [{category: "耳机", value: "Sony", sentiment: "positive"}]

Run 2 (append-only, new obs "Bose也不错"): 
  LLM output → {add: [{category: "耳机", value: "Bose", sentiment: "positive"}], 
                 keep_hint: [{category: "耳机", value: "Sony"}]}
  Service merges → [{category: "耳机", value: "Sony"}, {category: "耳机", value: "Bose"}]

Run 3 (append-only, new obs "不喜欢Sony了"):
  LLM output → {remove: [{category: "耳机", value: "Sony"}]}
  Service merges → [{category: "耳机", value: "Bose"}]
```

**Key points:**
- **Append-only prevents data loss** — prior context is never truncated or passed to the LLM, so older items can't silently disappear
- **Lower token cost** — no prior context in prompt (~2000 tokens vs ~7000 for full-prior approach)
- Old extractions are preserved as history (timestamp distinguishes current vs historical)
- `keep_hint` ensures items mentioned positively are retained even if not explicitly re-stated

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

1. **`template-class: "java.util.Map"`** → Uses `llmService.chatCompletion()` with `output-schema` injected into the system prompt as format instructions, then parses JSON response manually
2. **`template-class: "com.example.MyPojo"`** → Uses `llmService.chatCompletionStructured()` with `BeanOutputConverter<MyPojo>`, schema auto-derived from Java class

For Map templates, the `output-schema` is appended to the system prompt as a JSON Schema block. The LLM is instructed to respond with valid JSON matching the schema, but there is no runtime schema enforcement — parsing relies on LLM compliance. For POJO templates, `BeanOutputConverter` provides stronger type safety.

**Storage mapping:**

| Template Field | ObservationEntity Field |
|----------------|------------------------|
| `name` | `type` = `"extracted_{name}"` |
| `source-filter` | Determines which observations are candidates |
| `session-id-pattern` | `contentSessionId` of the result observation |
| Output data | `extractedData` (JSONB column) |

### Cost Control and Rate Limiting

Extraction costs are managed through several mechanisms:

- **On-demand processing** — extraction runs when triggered via API, not per-observation
- **Initial run cap** — `initial-run-max-candidates` (default 100) limits first-run processing
- **Batch size** — observations are chunked into batches of `max-observations-per-batch` (default 20) per LLM call
- **Max batches** — `max-batches-per-template` (default 10) caps total LLM calls per run

### Privacy Considerations

- **Access control is application-layer responsibility** — the memory system stores and extracts; the caller decides who can query what
- **User isolation** — userId-based extraction ensures personal data is not cross-contaminated
- **Prompt injection prevention** — user content in observations is length-limited and included with source attribution to help the LLM distinguish user content from system instructions
- **Data retention** — old extractions are preserved as history; implement your own retention policies as needed

### Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| No extraction results | `extraction.enabled` is `false` | Set `app.memory.extraction.enabled=true` |
| Extraction runs but returns empty | No observations match `source-filter` | Check that observations have matching source values |
| Template not loaded | Templates not in application.yml | Verify templates are under `app.memory.extraction.templates` in config |
| LLM returns invalid JSON | Schema compliance relies on prompt + LLM | Enable retry logic; extraction retries up to 3 times on parse failure |
| Token cost growing | Too many observations per batch | Check `max-observations-per-batch` and `max-batches-per-template` settings |
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
| `triggerExtraction()` | `POST /api/extraction/run` | Query params: projectPath |

---

*For design details, see [Phase 3 Design](drafts/phase-3-design.md) and [Walkthrough](drafts/phase-3-design-walkthrough.md).*
