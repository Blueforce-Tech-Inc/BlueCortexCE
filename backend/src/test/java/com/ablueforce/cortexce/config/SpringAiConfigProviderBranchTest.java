package com.ablueforce.cortexce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Offline proof that the provider switch only builds the bean it was asked for.
 *
 * <p>No network and no API key are involved: the constructors below only
 * assemble clients. This is the regression guard for the Anthropic-compatible
 * branch (MiniMax and similar endpoints) and for the OpenAI default staying
 * untouched.
 */
class SpringAiConfigProviderBranchTest {

    private static final String KEY = "offline-placeholder-key";
    private static final int CONNECT_MS = 5_000;
    private static final int READ_MS = 120_000;
    private static final int ATTEMPTS = 3;
    private static final long FIRST_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 5_000;

    private final SpringAiConfig config = new SpringAiConfig();

    @Test
    @DisplayName("openai provider builds the OpenAI-compatible model and nothing else")
    void openAiBranchIsSelectedOnlyForTheOpenAiProvider() {
        ChatModel model = config.openAiChatModel(
                KEY, "https://api.deepseek.com", "deepseek-chat", "openai",
                CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS);

        OpenAiChatModel openAi = assertInstanceOf(OpenAiChatModel.class, model);
        assertEquals("deepseek-chat", openAi.getDefaultOptions().getModel());

        assertNull(config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "anthropic",
                        CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS),
                "provider=anthropic must not also create the OpenAI model");
        assertNull(config.openAiChatModel("", "https://api.deepseek.com", "deepseek-chat", "openai",
                        CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS),
                "an empty key must suppress the OpenAI model instead of failing at call time");
    }

    @Test
    @DisplayName("the provider retry budget is three attempts, not Spring AI's ten")
    void retryBudgetStopsAfterThreeAttempts() {
        RetryTemplate template = SpringAiConfig.openAiRetryTemplate(3, 10, 20);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("read timed out");
        })).isInstanceOf(ResourceAccessException.class);

        assertEquals(3, calls.get(), "a timeout must cost three attempts and then surface");
    }

    @Test
    @DisplayName("a rejected credential is not retried")
    void nonTransientFailureIsNotRetried() {
        RetryTemplate template = SpringAiConfig.openAiRetryTemplate(3, 10, 20);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw new NonTransientAiException("401 unauthorized");
        })).isInstanceOf(NonTransientAiException.class);

        assertEquals(1, calls.get(), "a 401/403 must fail once instead of three times");
    }

    @Test
    @DisplayName("the waits between attempts are 2s then 5s")
    void backoffSequenceIsTwoThenFiveSeconds() {
        ExponentialBackOffPolicy backOff = SpringAiConfig.openAiBackOff(2_000, 5_000);
        List<Long> waited = new ArrayList<>();
        backOff.setSleeper(millis -> waited.add(millis));
        BackOffContext context = backOff.start(new RetryContextSupport(null));

        backOff.backOff(context);
        backOff.backOff(context);
        backOff.backOff(context);

        assertEquals(List.of(2_000L, 5_000L, 5_000L), waited, "the cap holds after the second wait");
    }

    @Test
    @DisplayName("a provider that never answers fails in bounded time instead of occupying the caller")
    void slowProviderIsBoundedByReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            requests.incrementAndGet();
            try {
                // Far longer than the read timeout under test: without a read bound the
                // caller would sit here for ten seconds per attempt, and Spring AI's
                // default template would keep going for ten attempts.
                Thread.sleep(10_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        try {
            ChatModel model = config.openAiChatModel(
                    KEY, "http://127.0.0.1:" + server.getAddress().getPort(), "slow-provider", "openai",
                    500, 800, 3, 100, 200);

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> model.call(new Prompt("ping")))
                    .isInstanceOf(ResourceAccessException.class);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            assertEquals(3, requests.get(), "each attempt must reach the provider exactly once");
            assertThat(elapsedMs)
                    .as("bounded by 3 * read-timeout + backoff, never by the provider's hang")
                    .isLessThan(6_000L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("anthropic provider builds the Anthropic-compatible model and nothing else")
    void anthropicBranchIsSelectedOnlyForTheAnthropicProvider() {
        ChatModel model = config.anthropicChatModel(
                KEY, "https://api.minimaxi.com/anthropic", "MiniMax-M3", 1024, "anthropic");

        AnthropicChatModel anthropic = assertInstanceOf(AnthropicChatModel.class, model);
        assertEquals("MiniMax-M3", anthropic.getDefaultOptions().getModel());

        assertNull(config.anthropicChatModel(
                KEY, "https://api.minimaxi.com/anthropic", "MiniMax-M3", 1024, "openai"),
                "provider=openai must not also create the Anthropic model");
    }

    @Test
    @DisplayName("switching providers never yields two chat models in the same context")
    void exactlyOneBranchIsActivePerProvider() {
        assertInstanceOf(OpenAiChatModel.class,
                config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "openai",
                        CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS));
        assertNull(config.anthropicChatModel(KEY, "https://api.anthropic.com", "claude-sonnet-4-5", 1024, "openai"));

        assertNull(config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "anthropic",
                CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS));
        assertInstanceOf(AnthropicChatModel.class,
                config.anthropicChatModel(KEY, "https://api.anthropic.com", "claude-sonnet-4-5", 1024, "anthropic"));
    }

    @Test
    @DisplayName("embedding stays on the OpenAI-compatible branch for either chat provider")
    void embeddingDoesNotFollowTheChatProvider() {
        // embeddingModel takes no provider argument at all: the chat switch
        // (openai | anthropic) must not change where embeddings come from.
        assertInstanceOf(OpenAiEmbeddingModel.class,
                config.embeddingModel(KEY, "https://api.siliconflow.cn", "BAAI/bge-m3", 1024, false,
                        CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS));
        assertInstanceOf(OpenAiEmbeddingModel.class,
                config.embeddingModel(KEY, "https://api.siliconflow.cn", "BAAI/bge-m3", 1024, true,
                        CONNECT_MS, READ_MS, ATTEMPTS, FIRST_BACKOFF_MS, MAX_BACKOFF_MS));
    }

    @Test
    @DisplayName("a real context exposes exactly one ChatModel per provider without an override error")
    void contextWiresExactlyOneChatModelForEachProvider() {
        // Both keys are present on purpose: the provider property alone must
        // decide which branch becomes a real bean, and the other branch must
        // register as a suppressed (null) bean rather than a second ChatModel.
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(SpringAiConfig.class)
                .withPropertyValues(
                        "spring.ai.openai.api-key=" + KEY,
                        "spring.ai.anthropic.api-key=" + KEY,
                        "spring.ai.openai.embedding.api-key=" + KEY);

        runner.withPropertyValues("claudemem.llm.provider=openai").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
            assertThat(context.getBeansOfType(AnthropicChatModel.class)).isEmpty();
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OpenAiEmbeddingModel.class);
        });

        runner.withPropertyValues("claudemem.llm.provider=anthropic").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(AnthropicChatModel.class);
            assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OpenAiEmbeddingModel.class);
        });
    }
}
