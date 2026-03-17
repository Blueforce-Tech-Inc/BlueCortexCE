package com.ablueforce.cortexce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);

    @Bean("openAiChatModel")
    @ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
    public ChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
            @Value("${claudemem.llm.provider:openai}") String provider) {

        log.info("OpenAI ChatModel called: apiKey={}, baseUrl={}, model={}, provider={}", 
            apiKey != null ? "set" : "null", baseUrl, model, provider);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OpenAI API key not configured, skipping ChatModel");
            return null;
        }

        if (!"openai".equals(provider)) {
            log.debug("OpenAI ChatModel skipped, provider is: {}", provider);
            return null;
        }

        log.info("Creating OpenAI-compatible ChatModel: baseUrl={}, model={}", baseUrl, model);

        try {
            OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();

            ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
            
            log.info("OpenAI ChatModel created successfully: {}", chatModel.getClass().getSimpleName());
            return chatModel;
        } catch (Exception e) {
            log.error(">>> Failed to create OpenAI ChatModel: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean("anthropicChatModel")
    @ConditionalOnProperty(prefix = "spring.ai.anthropic", name = "api-key")
    public ChatModel anthropicChatModel(
            @Value("${spring.ai.anthropic.api-key:}") String apiKey,
            @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-5}") String model,
            @Value("${spring.ai.anthropic.chat.options.max-tokens:1024}") Integer maxTokens,
            @Value("${claudemem.llm.provider:openai}") String provider) {

        if (!"anthropic".equals(provider)) {
            log.debug("Anthropic ChatModel skipped, provider is: {}", provider);
            return null;
        }

        log.info("Creating Anthropic-compatible ChatModel: baseUrl={}, model={}, maxTokens={}", baseUrl, model, maxTokens);

        AnthropicApi anthropicApi = AnthropicApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .build();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .model(model)
            .maxTokens(maxTokens)
            .build();

        return AnthropicChatModel.builder()
            .anthropicApi(anthropicApi)
            .defaultOptions(options)
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(List<ChatModel> chatModels) {
        List<ChatModel> availableModels = chatModels.stream()
            .filter(model -> model != null)
            .toList();

        if (availableModels.isEmpty()) {
            log.warn("""
                No ChatModel configured. LLM features disabled.
                Configure spring.ai.openai.api-key or spring.ai.anthropic.api-key.
                Set claudemem.llm.provider to 'openai' or 'anthropic'.
                """);
            return ChatClient.create(invocation -> {
                throw new IllegalStateException(
                    "ChatClient not configured. Set spring.ai.openai.api-key or spring.ai.anthropic.api-key.");
            });
        }

        ChatModel chatModel = availableModels.get(0);
        log.info("Creating ChatClient with model: {}", chatModel.getClass().getSimpleName());
        return ChatClient.create(chatModel);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.openai.embedding", name = "api-key")
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.embedding.api-key:}") String apiKey,
            @Value("${spring.ai.openai.embedding.base-url:https://api.siliconflow.cn}") String baseUrl,
            @Value("${spring.ai.openai.embedding.options.model:BAAI/bge-m3}") String model,
            @Value("${spring.ai.openai.embedding.options.dimensions:1024}") Integer dimensions) {

        log.info("Creating EmbeddingModel: baseUrl={}, model={}, dimensions={}", baseUrl, model, dimensions);

        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .embeddingsPath("/v1/embeddings")
            .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
            .model(model)
            .dimensions(dimensions)
            .build();

        return new OpenAiEmbeddingModel(
            openAiApi,
            MetadataMode.EMBED,
            options,
            RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }
}
