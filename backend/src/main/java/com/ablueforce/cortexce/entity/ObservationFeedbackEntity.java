package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for tracking observation usage feedback.
 *
 * This table stores signals about how observations are used:
 * - semantic_inject: Observation was semantically matched and injected into context
 * - search_hit: Observation was returned in search results
 * - explicit_retrieval: Observation was explicitly retrieved via MCP or API
 *
 * Future: This data will support Thompson Sampling optimization for selecting
 * which observations to include in context windows.
 */
@Entity
@Table(name = "observation_feedback")
public class ObservationFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The observation this feedback is about.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_id", nullable = false)
    @JsonProperty("observation_id")
    private ObservationEntity observation;

    /**
     * Type of usage signal (e.g., semantic_inject, search_hit, explicit_retrieval).
     */
    @Column(name = "signal_type", nullable = false, length = 50)
    @JsonProperty("signal_type")
    private String signalType;

    /**
     * Session that triggered this feedback (optional - may be null for some signal types).
     */
    @Column(name = "session_db_id")
    @JsonProperty("session_db_id")
    private UUID sessionDbId;

    /**
     * When this feedback was recorded.
     */
    @Column(name = "created_at")
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    /**
     * Epoch timestamp for efficient indexing and range queries.
     */
    @Column(name = "created_at_epoch", nullable = false)
    @JsonProperty("created_at_epoch")
    private Long createdAtEpoch;

    /**
     * JSON metadata about the usage context (e.g., query text, injection rank).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "text")
    @JsonProperty("metadata")
    private String metadata;

    // Signal type constants
    public static final String SIGNAL_SEMANTIC_INJECT = "semantic_inject";
    public static final String SIGNAL_SEARCH_HIT = "search_hit";
    public static final String SIGNAL_EXPLICIT_RETRIEVAL = "explicit_retrieval";

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ObservationEntity getObservation() { return observation; }
    public void setObservation(ObservationEntity observation) { this.observation = observation; }

    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }

    public UUID getSessionDbId() { return sessionDbId; }
    public void setSessionDbId(UUID sessionDbId) { this.sessionDbId = sessionDbId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
