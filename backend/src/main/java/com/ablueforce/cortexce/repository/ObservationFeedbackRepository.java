package com.ablueforce.cortexce.repository;

import com.ablueforce.cortexce.entity.ObservationFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for observation feedback tracking.
 *
 * Stores signals about how observations are used:
 * - semantic_inject: Observation was semantically matched and injected into context
 * - search_hit: Observation was returned in search results
 * - explicit_retrieval: Observation was explicitly retrieved via MCP or API
 *
 * This data supports future Thompson Sampling optimization for selecting
 * which observations to include in context windows.
 */
@Repository
public interface ObservationFeedbackRepository extends JpaRepository<ObservationFeedbackEntity, Long> {

    /**
     * Find all feedback for a specific observation.
     */
    List<ObservationFeedbackEntity> findByObservationId(UUID observationId);

    /**
     * Find all feedback for a specific observation, ordered by creation time.
     */
    @Query("""
        SELECT f FROM ObservationFeedbackEntity f
        WHERE f.observation.id = :observationId
        ORDER BY f.createdAtEpoch DESC
        """)
    List<ObservationFeedbackEntity> findByObservationIdOrderByCreatedAtDesc(
        @Param("observationId") UUID observationId
    );

    /**
     * Count feedback by signal type for a specific observation.
     */
    long countByObservationIdAndSignalType(UUID observationId, String signalType);

    /**
     * Find feedback by session.
     */
    List<ObservationFeedbackEntity> findBySessionDbId(UUID sessionDbId);

    /**
     * Find feedback by signal type.
     */
    List<ObservationFeedbackEntity> findBySignalType(String signalType);

    /**
     * Get feedback count by observation (for relevance scoring).
     */
    @Query("""
        SELECT f.observation.id, COUNT(f) as feedbackCount
        FROM ObservationFeedbackEntity f
        WHERE f.signalType = :signalType
        GROUP BY f.observation.id
        ORDER BY feedbackCount DESC
        """)
    List<Object[]> countBySignalTypeGroupedByObservation(
        @Param("signalType") String signalType
    );

    /**
     * Find recent feedback across all observations.
     */
    @Query("""
        SELECT f FROM ObservationFeedbackEntity f
        ORDER BY f.createdAtEpoch DESC
        LIMIT :limit
        """)
    List<ObservationFeedbackEntity> findRecentFeedback(
        @Param("limit") int limit
    );

    /**
     * Delete feedback for a specific observation.
     */
    void deleteByObservationId(UUID observationId);
}
