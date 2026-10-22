package com.ablueforce.cortexce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Durable queue row for one unit of asynchronous work.
 *
 * <p>The JPA {@code @UniqueConstraint(uk_session_tool_input)} that used to live here was
 * removed deliberately. It was never backed by a database object of that name (V6 created
 * only a plain index on the bare external id), so it was fiction on the read side — but it
 * was live fiction the moment schema generation ever ran, and it keyed deduplication on
 * {@code content_session_id}, which stops being globally unique once two platforms may
 * share an external id. Deduplication is now enforced by the enqueue protocol under the
 * Conversation parent row lock, keyed on the internal {@code session_db_id}.
 */
@Entity
@Table(name = "mem_pending_messages")
public class PendingMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_db_id", nullable = false)
    private UUID sessionDbId;

    @Column(name = "content_session_id", nullable = false)
    private String contentSessionId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "tool_input")
    private String toolInput;

    @Column(name = "tool_input_hash", length = 64)
    // P1: Changed from Integer to String to store full SHA-256 hash (64 hex chars)
    // Prevents hash collision attacks with 4-byte truncation
    private String toolInputHash;

    @Column(name = "tool_response")
    private String toolResponse;

    @Column(name = "cwd")
    private String cwd;

    @Column(name = "last_user_message")
    private String lastUserMessage;

    @Column(name = "last_assistant_message")
    private String lastAssistantMessage;

    @Column(name = "prompt_number")
    private Integer promptNumber;

    /**
     * Durable job idempotency key. {@code session-summary-v1} marks the canonical
     * SessionEnd summary job; NULL for ordinary observation work and for legacy rows
     * inside the V20 compatibility window.
     */
    @Column(name = "job_key", length = 64)
    private String jobKey;

    /**
     * Immutable rendered LLM request for a summary job.
     *
     * <p>SENSITIVE: same handling class as observation bodies. It exists so a crash or
     * retry replays byte-identical input instead of re-rendering a template that may have
     * changed. It must never reach logs, the API, SSE or exports.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_input_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> summaryInputSnapshot;

    /** SHA-256 of the canonical snapshot serialisation; verified before every retry. */
    @Column(name = "summary_input_sha256")
    private byte[] summaryInputSha256;

    /**
     * Result pointer. Written in the same transaction as the Summary row so crash
     * recovery can finalise without calling the generator a second time.
     */
    @Column(name = "result_summary_id", columnDefinition = "uuid")
    private UUID resultSummaryId;

    /** Tombstone written when the keyed result is deleted; the terminal job row survives. */
    @Column(name = "result_deleted_at")
    private OffsetDateTime resultDeletedAt;

    @Column(name = "status", nullable = false)
    private String status = "pending";

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at_epoch", nullable = false)
    private Long createdAtEpoch;

    @Column(name = "started_processing_at_epoch")
    private Long startedProcessingAtEpoch;

    @Column(name = "completed_at_epoch")
    private Long completedAtEpoch;

    @Column(name = "failed_at_epoch")
    private Long failedAtEpoch;

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionDbId() { return sessionDbId; }
    public void setSessionDbId(UUID sessionDbId) { this.sessionDbId = sessionDbId; }

    public String getContentSessionId() { return contentSessionId; }
    public void setContentSessionId(String contentSessionId) { this.contentSessionId = contentSessionId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolInput() { return toolInput; }
    public void setToolInput(String toolInput) { this.toolInput = toolInput; }

    public String getToolInputHash() { return toolInputHash; }
    public void setToolInputHash(String toolInputHash) { this.toolInputHash = toolInputHash; }

    public String getToolResponse() { return toolResponse; }
    public void setToolResponse(String toolResponse) { this.toolResponse = toolResponse; }

    public String getCwd() { return cwd; }
    public void setCwd(String cwd) { this.cwd = cwd; }

    public String getLastUserMessage() { return lastUserMessage; }
    public void setLastUserMessage(String lastUserMessage) { this.lastUserMessage = lastUserMessage; }

    public String getLastAssistantMessage() { return lastAssistantMessage; }
    public void setLastAssistantMessage(String lastAssistantMessage) { this.lastAssistantMessage = lastAssistantMessage; }

    public Integer getPromptNumber() { return promptNumber; }
    public void setPromptNumber(Integer promptNumber) { this.promptNumber = promptNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }

    public Long getStartedProcessingAtEpoch() { return startedProcessingAtEpoch; }
    public void setStartedProcessingAtEpoch(Long startedProcessingAtEpoch) { this.startedProcessingAtEpoch = startedProcessingAtEpoch; }

    public Long getCompletedAtEpoch() { return completedAtEpoch; }
    public void setCompletedAtEpoch(Long completedAtEpoch) { this.completedAtEpoch = completedAtEpoch; }

    public Long getFailedAtEpoch() { return failedAtEpoch; }
    public void setFailedAtEpoch(Long failedAtEpoch) { this.failedAtEpoch = failedAtEpoch; }

    // V20 durable summary job accessors

    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }

    public Map<String, Object> getSummaryInputSnapshot() { return summaryInputSnapshot; }
    public void setSummaryInputSnapshot(Map<String, Object> summaryInputSnapshot) { this.summaryInputSnapshot = summaryInputSnapshot; }

    public byte[] getSummaryInputSha256() { return summaryInputSha256; }
    public void setSummaryInputSha256(byte[] summaryInputSha256) { this.summaryInputSha256 = summaryInputSha256; }

    public UUID getResultSummaryId() { return resultSummaryId; }
    public void setResultSummaryId(UUID resultSummaryId) { this.resultSummaryId = resultSummaryId; }

    public OffsetDateTime getResultDeletedAt() { return resultDeletedAt; }
    public void setResultDeletedAt(OffsetDateTime resultDeletedAt) { this.resultDeletedAt = resultDeletedAt; }
}
