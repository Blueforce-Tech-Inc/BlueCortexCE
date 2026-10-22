package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Memory Scope: the long-lived range in which memories are retained, searched and grouped.
 *
 * <p>A scope is an ownership and query partition, never an authorisation or tenant boundary.
 * First release has two kinds:
 *
 * <ul>
 *   <li>{@code project} — keyed by the exact, uncanonicalised {@code project_path} string, so
 *       {@code "/repo/app"} and {@code "/repo/./app"} stay distinct exactly as the existing
 *       read paths already treated them.</li>
 *   <li>{@code manual_default} — one fixed scope for manual memories saved without a project.</li>
 * </ul>
 *
 * <p>Neither this entity nor its id is ever serialised: the public API keeps
 * {@code project}/{@code project_path} as the caller-facing selector, because a
 * deployment-local UUID is not a stable cross-install identity.
 */
@Entity
@Table(name = "mem_memory_scopes")
@JsonIgnoreType
public class MemoryScopeEntity {

    @Id
    @Column(columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    /**
     * Exact comparison key. Declared so JPQL can compare it, but the database column carries
     * {@code COLLATE "C"}: equality is byte-exact regardless of the instance locale.
     */
    @Column(name = "scope_key", nullable = false, columnDefinition = "text")
    private String scopeKey;

    @Column(name = "display_name", columnDefinition = "text")
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}