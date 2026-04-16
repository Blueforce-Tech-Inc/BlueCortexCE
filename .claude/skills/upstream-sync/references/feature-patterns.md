# Upstream Feature Reference

Detailed patterns for implementing upstream features in BlueCortexCE.

## Platform Source (V18)

### Database Migration
```sql
ALTER TABLE mem_sessions ADD COLUMN IF NOT EXISTS platform_source VARCHAR(50) DEFAULT 'claude';
CREATE INDEX IF NOT EXISTS idx_sessions_platform_source ON mem_sessions(platform_source);
-- Repeat for mem_observations, mem_summaries, mem_user_prompts
```

### Entity Field
```java
@Column(name = "platform_source")
@JsonProperty("platform_source")
private String platformSource = "claude";
```

### Repository Query
```java
@Query("SELECT DISTINCT s.platformSource FROM SessionEntity s WHERE s.platformSource IS NOT NULL ORDER BY s.platformSource")
List<String> findAllPlatformSources();

@Query("SELECT s.platformSource, s.projectPath FROM SessionEntity s WHERE s.platformSource IS NOT NULL GROUP BY s.platformSource, s.projectPath ORDER BY s.platformSource, s.projectPath")
List<Object[]> findProjectsByPlatformSource();
```

### Controller Parameter
```java
@RequestParam(required = false) String platformSource
```

---

## Observation Feedback (V17)

### Database Migration
```sql
CREATE TABLE IF NOT EXISTS observation_feedback (
    id BIGSERIAL PRIMARY KEY,
    observation_id UUID NOT NULL,
    signal_type VARCHAR(50) NOT NULL,
    session_db_id UUID,
    created_at_epoch BIGINT NOT NULL,
    metadata TEXT,
    CONSTRAINT fk_feedback_observation FOREIGN KEY (observation_id) REFERENCES mem_observations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_feedback_observation ON observation_feedback(observation_id);

ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS generated_by_model VARCHAR(100);
ALTER TABLE mem_observations ADD COLUMN IF NOT EXISTS relevance_count INTEGER DEFAULT 0;
```

### Entity
```java
@Entity
@Table(name = "observation_feedback")
public class ObservationFeedbackEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_id", nullable = false)
    private ObservationEntity observation;

    @Column(name = "signal_type", nullable = false, length = 50)
    @JsonProperty("signal_type")
    private String signalType;

    @Column(name = "session_db_id")
    @JsonProperty("session_db_id")
    private UUID sessionDbId;

    @Column(name = "created_at_epoch", nullable = false)
    @JsonProperty("created_at_epoch")
    private Long createdAtEpoch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "text")
    @JsonProperty("metadata")
    private String metadata;

    // Constants
    public static final String SIGNAL_SEMANTIC_INJECT = "semantic_inject";
    public static final String SIGNAL_SEARCH_HIT = "search_hit";
    public static final String SIGNAL_EXPLICIT_RETRIEVAL = "explicit_retrieval";
}
```

### ObservationEntity Fields
```java
@Column(name = "generated_by_model")
@JsonProperty("generated_by_model")
private String generatedByModel;

@Column(name = "relevance_count")
@JsonProperty("relevance_count")
private Integer relevanceCount = 0;
```

---

## Session Age Guard

### AgentService Constants and Methods
```java
private static final long MAX_SESSION_AGE_MS = 4 * 60 * 60 * 1000; // 4 hours

private boolean isSessionTooOld(SessionEntity session) {
    if (session == null || session.getStartedAtEpoch() == null) {
        return false;
    }
    long sessionAgeMs = Instant.now().toEpochMilli() - session.getStartedAtEpoch();
    return sessionAgeMs > MAX_SESSION_AGE_MS;
}

private void markSessionExpired(SessionEntity session, String reason) {
    if (session == null) return;
    log.warn("Session exceeded max age limit - marking as expired: session_id={}, reason={}, age_hours={}",
        session.getContentSessionId(), reason,
        (Instant.now().toEpochMilli() - session.getStartedAtEpoch()) / 3600000.0);
    session.setStatus("expired");
    sessionRepository.save(session);
}
```

### Guard Usage
```java
// In processToolUseAsync() after session resolution
if (session != null && isSessionTooOld(session)) {
    log.warn("Session too old for new work - skipping: session_id={}", contentSessionId);
    markSessionExpired(session, "tool-use");
    return;
}
```

---

## Semantic Context Endpoint

### ContextController Endpoint
```java
@PostMapping(value = "/semantic", produces = MediaType.APPLICATION_JSON_VALUE)
public Map<String, Object> semanticContext(@RequestBody Map<String, Object> body) {
    String query = (String) body.get("q");
    String project = (String) body.get("project");
    int limit = 5;

    if (body.get("limit") instanceof Number) {
        limit = Math.min(Math.max(((Number) body.get("limit")).intValue(), 1), 20);
    }

    // Validation: query must be at least 20 chars
    if (query == null || query.length() < 20) {
        return Map.of("context", "", "count", 0);
    }

    if (project == null || project.isBlank()) {
        project = System.getProperty("user.dir");
    }

    try {
        if (!embeddingService.isAvailable()) {
            return Map.of("context", "", "count", 0);
        }

        float[] queryVector = embeddingService.embed(query);
        SearchService.SearchRequest searchRequest = new SearchService.SearchRequest(
            project, query, queryVector, null, null, null, null, null, limit, 0, null
        );
        SearchService.SearchResult result = searchService.search(searchRequest);
        List<ObservationEntity> observations = result.observations();

        if (observations.isEmpty()) {
            return Map.of("context", "", "count", 0);
        }

        StringBuilder context = new StringBuilder();
        context.append("## Relevant Past Work (semantic match)\n\n");

        for (ObservationEntity obs : observations) {
            String date = "";
            if (obs.getCreatedAtEpoch() != null) {
                date = Instant.ofEpochMilli(obs.getCreatedAtEpoch())
                    .atZone(ZoneId.systemDefault()).toLocalDate().toString();
            }
            context.append("### ").append(obs.getTitle() != null ? obs.getTitle() : "Observation");
            if (!date.isEmpty()) context.append(" (").append(date).append(")");
            context.append("\n\n");
            if (obs.getContent() != null && !obs.getContent().isBlank()) {
                context.append(obs.getContent()).append("\n\n");
            }
        }

        return Map.of("context", context.toString().trim(), "count", observations.size());

    } catch (Exception e) {
        log.warn("Semantic context query failed: {}", e.getMessage());
        return Map.of("context", "", "count", 0);
    }
}
```

---

## SSE Initial Load (StreamController)

```java
List<String> projects = sessionRepository.findAllProjects();
List<String> sources = sessionRepository.findAllPlatformSources();

List<Object[]> projectsBySourceRaw = sessionRepository.findProjectsByPlatformSource();
Map<String, List<String>> projectsBySource = new HashMap<>();
for (Object[] row : projectsBySourceRaw) {
    String source = (String) row[0];
    String proj = (String) row[1];
    projectsBySource.computeIfAbsent(source, k -> new ArrayList<>()).add(proj);
}

emitter.send(SseEmitter.event()
    .data(Map.of(
        "type", "initial_load",
        "projects", projects,
        "sources", sources,
        "projectsBySource", projectsBySource,
        "timestamp", System.currentTimeMillis()
    )));
```
