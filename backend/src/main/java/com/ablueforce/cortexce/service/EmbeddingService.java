package com.ablueforce.cortexce.service;

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
}
