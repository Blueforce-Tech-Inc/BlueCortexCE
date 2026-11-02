package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Repairs observations whose embedding attempt failed.
 *
 * <p>Without this sweep, a provider outage would permanently remove those rows from
 * semantic search: nothing else re-reads an observation after it is written. Rows are
 * repaired from the same text the write path would have used
 * ({@link EmbeddingService#buildObservationText}), so a repaired row is indistinguishable
 * from one embedded on first write.
 *
 * <p>Each row is saved on its own; a failure on one row cannot roll back the batch.
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
     * @return true when a vector was applied
     */
    public boolean repair(ObservationEntity observation) {
        String text = EmbeddingService.buildObservationText(
            observation.getTitle(), observation.getContent(), observation.getFacts());
        if (text.isBlank()) {
            observation.setEmbeddingState(EmbeddingService.STATE_SKIPPED);
            observation.setEmbeddingError(null);
            observationRepository.save(observation);
            return false;
        }
        try {
            if (embeddingService.applyVector(observation, embeddingService.embed(text))) {
                observationRepository.save(observation);
                return true;
            }
            observation.setEmbeddingState(EmbeddingService.STATE_FAILED);
            observation.setEmbeddingError("unsupported-dimension");
        } catch (Exception e) {
            observation.setEmbeddingState(EmbeddingService.STATE_FAILED);
            observation.setEmbeddingError(EmbeddingService.classifyFailure(e));
            log.warn("Embedding backfill failed for observation {}: {}",
                observation.getId(), e.getClass().getSimpleName());
        }
        observationRepository.save(observation);
        return false;
    }
}
