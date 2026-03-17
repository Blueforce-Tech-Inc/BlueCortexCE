package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mem_user_prompts")
public class UserPromptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "content_session_id", nullable = false)
    @JsonProperty("content_session_id")
    private String contentSessionId;

    @Column(name = "project_path")
    @JsonProperty("project")
    private String projectPath;

    @Column(name = "prompt_number", nullable = false)
    @JsonProperty("prompt_number")
    private Integer promptNumber;

    @Column(name = "prompt_text", nullable = false, length = 100000)
    // P1: Add length constraint to prevent unbounded text storage
    @JsonProperty("prompt_text")
    private String promptText;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_at_epoch", nullable = false)
    @JsonProperty("created_at_epoch")
    private Long createdAtEpoch;

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getContentSessionId() { return contentSessionId; }
    public void setContentSessionId(String contentSessionId) { this.contentSessionId = contentSessionId; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public Integer getPromptNumber() { return promptNumber; }
    public void setPromptNumber(Integer promptNumber) { this.promptNumber = promptNumber; }

    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }
}
