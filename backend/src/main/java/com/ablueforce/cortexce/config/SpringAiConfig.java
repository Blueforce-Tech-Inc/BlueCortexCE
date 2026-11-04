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
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);

    // The provider condition decides *registration*, not just behaviour: the
    // branch that was not selected must not produce a second ChatModel bean
    // definition. A bean method that returned null while its definition stayed
    // registered still made `getBean(ChatModel.class)` report two candidates
    // (NoUniqueBeanDefinitionException), so the switch lives here.
    @Bean("openAiChatModel")
    @ConditionalOnProperty(prefix = "claudemem.llm", name = "provider",
        havingValue = "openai", matchIfMissing = true)
    public ChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
            @Value("${claudemem.llm.provider:openai}") String provider,
            @Value("${claudemem.llm.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${claudemem.llm.read-timeout-ms:120000}") int readTimeoutMs,
            @Value("${claudemem.llm.retry-max-attempts:3}") int retryMaxAttempts,
            @Value("${claudemem.llm.retry-backoff-ms:2000}") long firstBackoffMs,
            @Value("${claudemem.llm.retry-max-backoff-ms:5000}") long maxBackoffMs) {

        log.debug("OpenAI ChatModel called: apiKey={}, baseUrl={}, model={}, provider={}", 
            apiKey != null ? "set" : "null", baseUrl, model, provider);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("spring.ai.openai.api-key property exists but is empty — ChatModel bean not created. "
                + "Set a valid API key or remove the property to suppress this warning.");
            return null;
        }

        if (!"openai".equals(provider)) {
            log.debug("OpenAI ChatModel skipped, provider is: {}", provider);
            return null;
        }

        log.info("Creating OpenAI-compatible ChatModel: baseUrl={}, model={}, connect={}ms, read={}ms, "
                + "attempts={}, backoff={}ms..{}ms",
            baseUrl, model, connectTimeoutMs, readTimeoutMs,
            retryMaxAttempts, firstBackoffMs, maxBackoffMs);

        try {
            // Without an explicit request factory the HTTP client has no read timeout, so a
            // provider that accepts the call and never answers holds a worker forever. The
            // 120s read bound is the long end of a legitimate reasoning answer; the 5s connect
            // bound turns a dead endpoint into a fast, classified failure. The JDK client is
            // used because its per-request read timeout is honoured while a response is still
            // being awaited; HttpURLConnection's timeout only covers reads after headers.
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build());
            requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

            OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();

            ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                // Spring AI's default template retries ten times with backoff capped at 3
                // minutes; one slow response could occupy a worker for ~180s. Three attempts
                // with 2s/5s spacing bounds that to a few seconds plus the read timeout.
                .retryTemplate(openAiRetryTemplate(retryMaxAttempts, firstBackoffMs, maxBackoffMs))
                .build();
            
            log.info("OpenAI ChatModel created successfully: {}", chatModel.getClass().getSimpleName());
            return chatModel;
        } catch (Exception e) {
            log.error(">>> Failed to create OpenAI ChatModel: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retry policy for OpenAI-compatible provider calls.
     *
     * <p>Retries only what a repeat can fix: {@link TransientAiException} (429/5xx from
     * Spring AI's own classifier), {@link ResourceAccessException} (the wrapper the REST
     * client puts around a read/connect timeout, whose cause is a
     * {@code SocketTimeoutException}) and {@code IOException} (connection resets).
     * A 401/403 is a {@code NonTransientAiException} and is not in the list, so bad
     * credentials fail once instead of three times.</p>
     *
     * <p>The backoff is exponential with multiplier 2.5 capped at {@code maxBackoffMs},
     * which for the production defaults (2000 / 5000) yields exactly 2s then 5s: with
     * three attempts there are only two waits. This is deliberately not Spring AI's
     * default (ten attempts, backoff capped at 3 minutes).</p>
     */
    static RetryTemplate openAiRetryTemplate(int maxAttempts, long firstBackoffMs, long maxBackoffMs) {
        return RetryTemplate.builder()
            .maxAttempts(maxAttempts)
            .customBackoff(openAiBackOff(firstBackoffMs, maxBackoffMs))
            .retryOn(TransientAiException.class)
            .retryOn(ResourceAccessException.class)
            .retryOn(IOException.class)
            .withListener(new RetryMetricsListener(maxAttempts))
            .build();
    }

    /**
     * Counts the retries the provider retry template schedules.
     *
     * <p>Spring Retry increments the context's retry count before calling this
     * listener, so {@code count < maxAttempts} is exactly "this failure will be
     * repeated" (the last failure is not a retry). The counter carries the same
     * classification the application uses for the final outcome, so retries and
     * failures can be correlated by {@code failure_kind}.</p>
     */
    static final class RetryMetricsListener implements RetryListener {

        private final int maxAttempts;

        RetryMetricsListener(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
        }

        @Override
        public <T, E extends Throwable> void onError(RetryContext context,
                                                     RetryCallback<T, E> callback,
                                                     Throwable throwable) {
            if (context.getRetryCount() < maxAttempts) {
                CortexMetrics.llmRetry(
                    com.ablueforce.cortexce.service.LlmService.classify(throwable).name());
            }
        }
    }

    /** The 2s / 5s sequence the retry template sleeps between provider attempts. */
    static ExponentialBackOffPolicy openAiBackOff(long firstBackoffMs, long maxBackoffMs) {
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(firstBackoffMs);
        backOff.setMultiplier(2.5);
        backOff.setMaxInterval(maxBackoffMs);
        return backOff;
    }

    @Bean("anthropicChatModel")
    @ConditionalOnProperty(prefix = "claudemem.llm", name = "provider",
        havingValue = "anthropic")
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

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("spring.ai.anthropic.api-key property exists but is empty — ChatModel bean not created. "
                + "Set ANTHROPIC_API_KEY or remove the property to suppress this warning.");
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
            @Value("${spring.ai.openai.embedding.options.dimensions:1024}") Integer dimensions,
            @Value("${spring.ai.openai.embedding.send-dimensions:false}") boolean sendDimensions,
            @Value("${claudemem.llm.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${claudemem.embedding.read-timeout-ms:15000}") int readTimeoutMs,
            @Value("${claudemem.embedding.retry-max-attempts:2}") int retryMaxAttempts,
            @Value("${claudemem.embedding.retry-backoff-ms:1000}") long firstBackoffMs,
            @Value("${claudemem.embedding.retry-max-backoff-ms:3000}") long maxBackoffMs) {

        log.info("Creating EmbeddingModel: baseUrl={}, model={}, dimensions={}, sendDimensions={}",
            baseUrl, model, dimensions, sendDimensions);

        // Embedding calls sit on the synchronous request path (observation ingest,
        // viewer search, context inject), so the ChatModel's read bound and retry
        // bound have to apply here too. With Spring AI's default template these
        // paths inherited ten attempts with backoff capped at three minutes, and a
        // measured outage held one observation write for over 200 s. Embeddings are
        // short calls, so the read bound is 15 s rather than the chat 120 s.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .embeddingsPath("/v1/embeddings")
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();

        // `dimensions` is an OpenAI-specific request field. Several
        // OpenAI-compatible providers reject it outright: SiliconFlow/BAAI
        // bge-m3 answers 400 code 20015 ("The parameter is invalid") when it is
        // present. Those models already produce the declared width natively, so
        // the field is only sent when explicitly requested (text-embedding-3-*
        // style truncation). The declared value is still validated against the
        // returned vector by callers/the smoke script.
        OpenAiEmbeddingOptions.Builder optionsBuilder = OpenAiEmbeddingOptions.builder()
            .model(model);
        if (sendDimensions) {
            optionsBuilder.dimensions(dimensions);
        }
        OpenAiEmbeddingOptions options = optionsBuilder.build();

        return new OpenAiEmbeddingModel(
            openAiApi,
            MetadataMode.EMBED,
            options,
            openAiRetryTemplate(retryMaxAttempts, firstBackoffMs, maxBackoffMs));
    }
}
