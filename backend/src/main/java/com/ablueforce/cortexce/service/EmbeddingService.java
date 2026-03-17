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

    public EmbeddingService(List<EmbeddingModel> embeddingModels) {
        // Find first available embedding model (from auto-configuration)
        this.embeddingModel = embeddingModels.stream()
            .findFirst();

        if (embeddingModel.isEmpty()) {
            log.warn("No EmbeddingModel configured - semantic search disabled");
        } else {
            log.info("EmbeddingService initialized with: {}", embeddingModel.get().getClass().getSimpleName());
        }
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
