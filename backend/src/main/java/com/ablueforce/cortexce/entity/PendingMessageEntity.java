package com.ablueforce.cortexce.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "mem_pending_messages",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_tool_input",
            columnNames = {"content_session_id", "tool_name", "tool_input_hash"})
    })
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
}
