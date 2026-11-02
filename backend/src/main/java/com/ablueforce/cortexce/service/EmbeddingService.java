package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Embedding service for generating vector embeddings.
 *
 * <p>Supports flexible combination of embedding models from different providers.
 * Uses any available EmbeddingModel bean (OpenAI-compatible, Anthropic, etc.)</p>
 */
@Service
public class EmbeddingService {

    /** A vector is stored; the row is complete. */
    public static final String STATE_READY = "ready";
    /** The provider call or dimension check failed; the backfill sweep retries it. */
    public static final String STATE_FAILED = "failed";
    /** There was no text to embed; the backfill sweep must not retry it. */
    public static final String STATE_SKIPPED = "skipped";

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final Optional<EmbeddingModel> embeddingModel;

    /**
     * Selects the first available EmbeddingModel from Spring's auto-configured beans.
     * If multiple models are present, selection depends on bean injection order
     * (typically determined by @Primary or declaration order).
     *
     * @param embeddingModels all configured EmbeddingModel beans (may be empty)
     */
    public EmbeddingService(List<EmbeddingModel> embeddingModels) {
        if (embeddingModels.isEmpty()) {
            this.embeddingModel = Optional.empty();
            log.warn("No EmbeddingModel configured - semantic search disabled");
            return;
        }

        if (embeddingModels.size() > 1) {
            log.info("Multiple EmbeddingModel beans found ({}), candidates: {}",
                embeddingModels.size(),
                embeddingModels.stream()
                    .map(m -> m.getClass().getSimpleName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
        }

        // Use first model (Spring injection order respects @Primary)
        this.embeddingModel = Optional.of(embeddingModels.get(0));
        log.info("EmbeddingService initialized with: {}", embeddingModel.get().getClass().getSimpleName());
    }

    public float[] embed(String text) {
        if (embeddingModel.isEmpty()) {
            throw new IllegalStateException("Embedding not configured. Set spring.ai.openai.embedding.api-key in application-dev.yml");
        }
        return embeddingModel.get().embed(text);
    }

    public boolean isAvailable() {
        return embeddingModel.isPresent();
    }

    public String getModel() {
        return embeddingModel.map(m -> m.getClass().getSimpleName()).orElse("unknown");
    }

    /**
     * Build the text that represents an observation in vector space.
     * Shared by the write path and the backfill sweep so a repaired row is embedded
     * from exactly the same input it would have used on first write.
     */
    public static String buildObservationText(String title, String narrative, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        // A whitespace-only title must not turn into the text ".", which would be sent to
        // the provider and recorded as a real attempt.
        if (title != null && !title.isBlank()) {
            sb.append(title.strip()).append(". ");
        }
        if (narrative != null && !narrative.isBlank()) {
            sb.append(narrative.strip()).append(" ");
        }
        if (facts != null && !facts.isEmpty()) {
            sb.append(facts.stream()
                .filter(fact -> fact != null && !fact.isBlank())
                .map(String::strip)
                .reduce((left, right) -> left + "; " + right)
                .orElse(""));
        }
        return sb.toString().trim();
    }

    /**
     * Store a model vector in the column that matches its dimension.
     *
     * @return true when the vector was applied; false when the dimension is unsupported,
     *         in which case the caller must record a visible failure instead of silently
     *         saving a row that looks embedded
     */
    public boolean applyVector(ObservationEntity observation, float[] vector) {
        if (vector == null || observation == null) {
            return false;
        }
        switch (vector.length) {
            case 768 -> observation.setEmbedding768(vector);
            case 1024 -> observation.setEmbedding1024(vector);
            case 1536 -> observation.setEmbedding1536(vector);
            default -> {
                log.warn("Unsupported embedding dimension: {} (supported: 768, 1024, 1536)", vector.length);
                return false;
            }
        }
        observation.setEmbeddingModelId(getModel());
        observation.setEmbeddingState(STATE_READY);
        observation.setEmbeddingError(null);
        return true;
    }

    /**
     * Short, non-sensitive classification of an embedding failure.
     * The exception message is deliberately not used: it can contain request bodies.
     */
    public static String classifyFailure(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String name = error.getClass().getSimpleName();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
}
