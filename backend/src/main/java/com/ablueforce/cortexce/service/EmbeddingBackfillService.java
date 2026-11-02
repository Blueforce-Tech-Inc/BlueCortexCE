package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Repairs observations whose embedding attempt failed.
 *
 * <p>Without this sweep, a provider outage would permanently remove those rows from
 * semantic search: nothing else re-reads an observation after it is written. Rows are
 * repaired from the same text the write path would have used
 * ({@link EmbeddingService#buildObservationText}), so a repaired row is indistinguishable
 * from one embedded on first write.
 *
 * <p>The sweep can run while a worker is updating the same row, and a long batch can
 * overlap the next sweep. Both writes are therefore claims, not saves:
 * <ol>
 *   <li>before calling the provider, ask whether the row still needs a vector — a second
 *       sweep that lost the race does not spend a provider call;</li>
 *   <li>after receiving the vector, write only the vector columns, guarded by the same
 *       "still missing" predicate the backlog query uses. A concurrent writer wins by
 *       moving the row out of that state, and the loser gets zero affected rows instead
 *       of overwriting the row's body or a vector that is already stored.</li>
 * </ol>
 * Failure and skip outcomes update only {@code embedding_state}/{@code embedding_error}
 * under the same guard, so they are idempotent too.
 *
 * <p>Each row is repaired on its own; a failure on one row cannot roll back the batch.
 * Rows that already failed are retried by later sweeps; there is no attempt counter
 * because the sweep is bounded by the batch size and runs on a slow fixed delay.
 */
@Service
public class EmbeddingBackfillService {

    static final int DEFAULT_BATCH_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillService.class);

    private final ObservationRepository observationRepository;
    private final EmbeddingService embeddingService;
    private final boolean enabled;

    public EmbeddingBackfillService(ObservationRepository observationRepository,
                                    EmbeddingService embeddingService,
                                    @Value("${claudemem.embedding.backfill.enabled:true}") boolean enabled) {
        this.observationRepository = observationRepository;
        this.embeddingService = embeddingService;
        this.enabled = enabled;
    }

    @Scheduled(
        fixedDelayString = "${claudemem.embedding.backfill-interval-ms:300000}",
        initialDelayString = "${claudemem.embedding.backfill-interval-ms:300000}"
    )
    public void scheduledBackfill() {
        if (!enabled) {
            return;
        }
        int repaired = backfill(DEFAULT_BATCH_LIMIT);
        if (repaired > 0) {
            log.info("Repaired {} observation embeddings", repaired);
        }
    }

    /**
     * Re-embed up to {@code limit} rows that have no vector.
     *
     * @return number of rows that received a vector
     */
    public int backfill(int limit) {
        if (limit <= 0 || !embeddingService.isAvailable()) {
            return 0;
        }
        List<ObservationEntity> candidates = observationRepository.findMissingEmbeddings(limit);
        int repaired = 0;
        for (ObservationEntity observation : candidates) {
            if (repair(observation)) {
                repaired++;
            }
        }
        return repaired;
    }

    /**
     * Try to give one observation a vector, recording a visible state either way.
     *
     * <p>Safe to call twice or from two threads: the second caller observes a row that is
     * no longer "missing" and returns {@code false} without calling the provider again.
     *
     * @return true when this call wrote the vector
     */
    public boolean repair(ObservationEntity observation) {
        UUID id = observation == null ? null : observation.getId();
        if (id == null) {
            return false;
        }
        // Cheap pre-check: a concurrent sweep or a worker may have completed this row
        // between the backlog query and this call. Do not pay for an embedding we cannot write.
        if (observationRepository.countEmbeddingMissingById(id) == 0) {
            return false;
        }

        String text = EmbeddingService.buildObservationText(
            observation.getTitle(), observation.getContent(), observation.getFacts());
        if (text.isBlank()) {
            recordOutcome(observation, EmbeddingService.STATE_SKIPPED, null);
            return false;
        }
        try {
            float[] vector = embeddingService.embed(text);
            if (!embeddingService.applyVector(observation, vector)) {
                recordOutcome(observation, EmbeddingService.STATE_FAILED, "unsupported-dimension");
                return false;
            }
            int updated = applyVectorIfMissing(id, vector, observation.getEmbeddingModelId());
            if (updated == 0) {
                // Lost the race: another writer moved the row out of the missing state.
                // Its vector/body stands; this call must not claim a repair it did not make.
                return false;
            }
            return true;
        } catch (Exception e) {
            String reason = EmbeddingService.classifyFailure(e);
            boolean recorded = recordOutcome(observation, EmbeddingService.STATE_FAILED, reason);
            if (recorded) {
                log.warn("Embedding backfill failed for observation {}: {}", id, reason);
            }
            return false;
        }
    }

    /** Write only the vector column that matches the provider's dimension. */
    private int applyVectorIfMissing(UUID id, float[] vector, String model) {
        String literal = EmbeddingService.toVectorLiteral(vector);
        return switch (vector.length) {
            case 768 -> observationRepository.applyEmbedding768IfMissing(id, literal, model);
            case 1024 -> observationRepository.applyEmbedding1024IfMissing(id, literal, model);
            case 1536 -> observationRepository.applyEmbedding1536IfMissing(id, literal, model);
            default -> 0;
        };
    }

    /** Record a non-ready outcome under the same "still missing" guard as the vector write. */
    private boolean recordOutcome(ObservationEntity observation, String state, String error) {
        boolean updated = observationRepository.markEmbeddingOutcomeIfMissing(
            observation.getId(), state, error) > 0;
        if (updated) {
            observation.setEmbeddingState(state);
            observation.setEmbeddingError(error);
        }
        return updated;
    }
}
