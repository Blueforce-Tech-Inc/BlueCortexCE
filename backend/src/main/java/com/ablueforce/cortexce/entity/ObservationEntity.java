package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mem_observations")
public class ObservationEntity {

    private static final Logger log = LoggerFactory.getLogger(ObservationEntity.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // P2: Embedding dimension constants for maintainability
    public static final int EMBEDDING_DIMENSION_768 = 768;
    public static final int EMBEDDING_DIMENSION_1024 = 1024;
    public static final int EMBEDDING_DIMENSION_1536 = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Session identifier — FK to {@code mem_sessions.content_session_id} (Claude Code session id).
     */
    @Column(name = "content_session_id", nullable = false)
    @JsonProperty("content_session_id")
    private String contentSessionId;

    @Column(name = "project_path", nullable = false)
    @JsonProperty("project")
    private String projectPath;

    @Column(name = "type", nullable = false)
    @JsonProperty("type")
    private String type;

    @Column(name = "title")
    @JsonProperty("title")
    private String title;

    @Column(name = "subtitle")
    @JsonProperty("subtitle")
    private String subtitle;

    @Column(name = "content")
    @JsonProperty("narrative")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts", columnDefinition = "jsonb")
    private List<String> facts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "concepts", columnDefinition = "jsonb")
    private List<String> concepts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files_read", columnDefinition = "jsonb")
    private List<String> filesRead;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files_modified", columnDefinition = "jsonb")
    private List<String> filesModified;

    @Column(name = "content_hash")
    @JsonProperty("contentHash")
    private String contentHash;

    @Column(name = "discovery_tokens")
    @JsonProperty("discoveryTokens")
    private Integer discoveryTokens = 0;

    @Column(name = "prompt_number")
    @JsonProperty("prompt_number")
    private Integer promptNumber;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(name = "embedding_768", columnDefinition = "vector(768)")
    @JsonProperty("embedding768")
    private float[] embedding768;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(name = "embedding_1024", columnDefinition = "vector(1024)")
    @JsonProperty("embedding1024")
    private float[] embedding1024;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding_1536", columnDefinition = "vector(1536)")
    @JsonProperty("embedding1536")
    private float[] embedding1536;

    @Column(name = "embedding_model_id")
    @JsonProperty("embeddingModelId")
    private String embeddingModelId;

    // search_vector is GENERATED ALWAYS, read-only from JPA perspective
    // No mapping needed - handled by PostgreSQL

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_at_epoch", nullable = false)
    @JsonProperty("created_at_epoch")
    private Long createdAtEpoch;

    // ===== Quality Score Fields (V11) =====

    /**
     * Quality score [0, 1] - higher is better.
     * Computed from feedback type, reasoning trace, output, and tool usage count.
     * See QualityScorer service for calculation logic.
     */
    @Column(name = "quality_score")
    @JsonProperty("quality_score")
    private Float qualityScore;

    /**
     * Feedback type: SUCCESS/PARTIAL/FAILURE/UNKNOWN.
     * Used for quality assessment and memory refinement decisions.
     */
    @Column(name = "feedback_type")
    @JsonProperty("feedback_type")
    private String feedbackType;

    /**
     * Last accessed timestamp for recency scoring.
     * Updated每次检索时.
     */
    @Column(name = "last_accessed_at")
    @JsonProperty("last_accessed_at")
    private OffsetDateTime lastAccessedAt;

    /**
     * Number of times this memory was retrieved.
     * Used in retrieval scoring (access frequency weight).
     */
    @Column(name = "access_count")
    @JsonProperty("access_count")
    private Integer accessCount = 0;

    /**
     * Last refinement timestamp.
     * Used for cooldown detection (7 days before next refine).
     */
    @Column(name = "refined_at")
    @JsonProperty("refined_at")
    private OffsetDateTime refinedAt;

    /**
     * Comma-separated IDs of merged observations.
     * Used for traceability when memories are consolidated.
     */
    @Column(name = "refined_from_ids")
    @JsonProperty("refined_from_ids")
    private String refinedFromIds;

    /**
     * User comment from WebUI feedback.
     * Manual feedback override for quality assessment.
     */
    @Column(name = "user_comment")
    @JsonProperty("user_comment")
    private String userComment;

    /**
     * Timestamp when feedback was last updated.
     */
    @Column(name = "feedback_updated_at")
    @JsonProperty("feedback_updated_at")
    private OffsetDateTime feedbackUpdatedAt;

    // Getters and Setters for quality fields

    public Float getQualityScore() { return qualityScore; }
    public void setQualityScore(Float qualityScore) { this.qualityScore = qualityScore; }

    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }

    public OffsetDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(OffsetDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }

    public Integer getAccessCount() { return accessCount; }
    public void setAccessCount(Integer accessCount) { this.accessCount = accessCount; }

    public OffsetDateTime getRefinedAt() { return refinedAt; }
    public void setRefinedAt(OffsetDateTime refinedAt) { this.refinedAt = refinedAt; }

    public String getRefinedFromIds() { return refinedFromIds; }
    public void setRefinedFromIds(String refinedFromIds) { this.refinedFromIds = refinedFromIds; }

    public String getUserComment() { return userComment; }
    public void setUserComment(String userComment) { this.userComment = userComment; }

    public OffsetDateTime getFeedbackUpdatedAt() { return feedbackUpdatedAt; }
    public void setFeedbackUpdatedAt(OffsetDateTime feedbackUpdatedAt) { this.feedbackUpdatedAt = feedbackUpdatedAt; }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getContentSessionId() { return contentSessionId; }
    public void setContentSessionId(String contentSessionId) { this.contentSessionId = contentSessionId; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    // Custom getters that return JSON strings for WebUI compatibility
    // TypeScript WebUI expects: JSON.parse(observation.facts) etc.
    @JsonProperty("facts")
    public String getFactsJson() {
        if (facts == null) return null;
        try {
            return MAPPER.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize facts to JSON", e);
            return null;
        }
    }

    public List<String> getFacts() { return facts; }
    public void setFacts(List<String> facts) { this.facts = facts; }

    @JsonProperty("concepts")
    public String getConceptsJson() {
        if (concepts == null) return null;
        try {
            return MAPPER.writeValueAsString(concepts);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize concepts to JSON", e);
            return null;
        }
    }

    public List<String> getConcepts() { return concepts; }
    public void setConcepts(List<String> concepts) { this.concepts = concepts; }

    @JsonProperty("files_read")
    public String getFilesReadJson() {
        if (filesRead == null) return null;
        try {
            return MAPPER.writeValueAsString(filesRead);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize files_read to JSON", e);
            return null;
        }
    }

    public List<String> getFilesRead() { return filesRead; }
    public void setFilesRead(List<String> filesRead) { this.filesRead = filesRead; }

    @JsonProperty("files_modified")
    public String getFilesModifiedJson() {
        if (filesModified == null) return null;
        try {
            return MAPPER.writeValueAsString(filesModified);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize files_modified to JSON", e);
            return null;
        }
    }

    public List<String> getFilesModified() { return filesModified; }
    public void setFilesModified(List<String> filesModified) { this.filesModified = filesModified; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public Integer getDiscoveryTokens() { return discoveryTokens; }
    public void setDiscoveryTokens(Integer discoveryTokens) { this.discoveryTokens = discoveryTokens; }

    public Integer getPromptNumber() { return promptNumber; }
    public void setPromptNumber(Integer promptNumber) { this.promptNumber = promptNumber; }

    public float[] getEmbedding768() { return embedding768; }
    public void setEmbedding768(float[] embedding768) { this.embedding768 = embedding768; }

    public float[] getEmbedding1024() { return embedding1024; }
    public void setEmbedding1024(float[] embedding1024) { this.embedding1024 = embedding1024; }

    public float[] getEmbedding1536() { return embedding1536; }
    public void setEmbedding1536(float[] embedding1536) { this.embedding1536 = embedding1536; }

    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String embeddingModelId) { this.embeddingModelId = embeddingModelId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }
}
