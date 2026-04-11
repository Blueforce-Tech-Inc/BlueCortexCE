package com.ablueforce.cortexce.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a single observation record returned from the backend.
 * <p>
 * Field names match the backend wire format:
 * <ul>
 *   <li>Fields with {@code @JsonProperty} annotations use the specified wire name</li>
 *   <li>Other fields use the default Jackson SNAKE_CASE naming strategy</li>
 * </ul>
 * <p>
 * Wire format verified against {@code backend/src/main/java/com/ablueforce/cortexce/entity/ObservationEntity.java}.
 * <p>
 * This DTO is used by the client to deserialize observation responses from
 * {@code GET /api/observations}, {@code GET /api/memory/observations/{id}}, and search results.
 * It complements {@link ObservationUpdate} (PATCH request DTO) and {@link ObservationRequest} (POST request DTO).
 *
 * @param id observation UUID
 * @param sessionId parsed from wire field {@code content_session_id} (@JsonProperty override on entity)
 * @param projectPath parsed from wire field {@code project} (@JsonProperty override on entity)
 * @param type observation type (e.g., "tool-use", "user-prompt")
 * @param title optional title
 * @param subtitle optional subtitle
 * @param narrative observation body text (@JsonProperty override on entity)
 * @param facts list of facts extracted from this observation
 * @param concepts list of concepts associated with this observation
 * @param filesRead files read during this observation (SNAKE_CASE wire field)
 * @param filesModified files modified during this observation (SNAKE_CASE wire field)
 * @param qualityScore quality score assigned by the refinement process
 * @param feedbackType feedback type: SUCCESS/PARTIAL/FAILURE/UNKNOWN
 * @param feedbackUpdatedAt ISO-8601 timestamp of last feedback update
 * @param source source attribution (e.g., "claude-code", "manual")
 * @param extractedData structured data extracted by the LLM (camelCase @JsonProperty override)
 * @param promptNumber prompt number in the session
 * @param createdAt ISO-8601 creation timestamp
 * @param createdAtEpoch epoch milliseconds of creation
 * @param lastAccessedAt ISO-8601 timestamp of last access
 * @param accessCount how many times this observation was retrieved (SNAKE_CASE wire field)
 * @param refinedAt when this observation was last refined/extracted (SNAKE_CASE wire field, backend stores as ISO-8601 String)
 * @param refinedFromIds IDs of source observations this was refined from, comma-separated (SNAKE_CASE wire field)
 * @param userComment user-provided comment/annotation (SNAKE_CASE wire field)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObservationResponse(
    String id,

    @JsonProperty("content_session_id")
    String sessionId,

    @JsonProperty("project")
    String projectPath,

    String type,
    String title,
    String subtitle,

    @JsonProperty("narrative")
    String narrative,

    List<String> facts,
    List<String> concepts,

    @JsonProperty("files_read")
    List<String> filesRead,

    @JsonProperty("files_modified")
    List<String> filesModified,

    @JsonProperty("quality_score")
    Float qualityScore,

    @JsonProperty("feedback_type")
    String feedbackType,

    @JsonProperty("feedback_updated_at")
    String feedbackUpdatedAt,

    String source,

    @JsonProperty("extractedData")
    Map<String, Object> extractedData,

    @JsonProperty("prompt_number")
    Integer promptNumber,

    @JsonProperty("created_at")
    String createdAt,

    @JsonProperty("created_at_epoch")
    Long createdAtEpoch,

    @JsonProperty("last_accessed_at")
    String lastAccessedAt,

    @JsonProperty("access_count")
    Integer accessCount,

    @JsonProperty("refined_at")
    String refinedAt,

    @JsonProperty("refined_from_ids")
    String refinedFromIds,

    @JsonProperty("user_comment")
    String userComment
) {
    /**
     * Convenience constructor for backward compatibility.
     * Creates an ObservationResponse with only the core fields,
     * leaving the 4 backend-extended fields (accessCount, refinedAt, refinedFromIds, userComment) as null.
     */
    public ObservationResponse(
        String id, String sessionId, String projectPath, String type,
        String title, String subtitle, String narrative,
        List<String> facts, List<String> concepts,
        List<String> filesRead, List<String> filesModified,
        Float qualityScore, String feedbackType, String feedbackUpdatedAt,
        String source, Map<String, Object> extractedData,
        Integer promptNumber, String createdAt, Long createdAtEpoch, String lastAccessedAt
    ) {
        this(id, sessionId, projectPath, type, title, subtitle, narrative,
             facts, concepts, filesRead, filesModified, qualityScore,
             feedbackType, feedbackUpdatedAt, source, extractedData,
             promptNumber, createdAt, createdAtEpoch, lastAccessedAt,
             null, null, null, null);
    }

    /**
     * Alias for {@link #narrative()} — returns the observation body text.
     */
    public String content() {
        return narrative;
    }
}
