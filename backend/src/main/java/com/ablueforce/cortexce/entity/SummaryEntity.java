package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mem_summaries")
public class SummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Session identifier — FK to {@code mem_sessions.content_session_id}.
     */
    @Column(name = "content_session_id", nullable = false)
    @JsonProperty("session_id")
    private String contentSessionId;

    @Column(name = "project_path", nullable = false)
    @JsonProperty("project")
    private String projectPath;

    @Column(name = "request")
    @JsonProperty("request")
    private String request;

    @Column(name = "investigated")
    @JsonProperty("investigated")
    private String investigated;

    @Column(name = "learned")
    @JsonProperty("learned")
    private String learned;

    @Column(name = "completed")
    @JsonProperty("completed")
    private String completed;

    @Column(name = "next_steps")
    @JsonProperty("next_steps")
    private String nextSteps;

    @Column(name = "files_read")
    @JsonProperty("files_read")
    private String filesRead;

    @Column(name = "files_edited")
    @JsonProperty("files_edited")
    private String filesEdited;

    @Column(name = "notes")
    @JsonProperty("notes")
    private String notes;

    @Column(name = "prompt_number")
    @JsonProperty("prompt_number")
    private Integer promptNumber;

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

    public String getRequest() { return request; }
    public void setRequest(String request) { this.request = request; }

    public String getInvestigated() { return investigated; }
    public void setInvestigated(String investigated) { this.investigated = investigated; }

    public String getLearned() { return learned; }
    public void setLearned(String learned) { this.learned = learned; }

    public String getCompleted() { return completed; }
    public void setCompleted(String completed) { this.completed = completed; }

    public String getNextSteps() { return nextSteps; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }

    public String getFilesRead() { return filesRead; }
    public void setFilesRead(String filesRead) { this.filesRead = filesRead; }

    public String getFilesEdited() { return filesEdited; }
    public void setFilesEdited(String filesEdited) { this.filesEdited = filesEdited; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getPromptNumber() { return promptNumber; }
    public void setPromptNumber(Integer promptNumber) { this.promptNumber = promptNumber; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }
}
