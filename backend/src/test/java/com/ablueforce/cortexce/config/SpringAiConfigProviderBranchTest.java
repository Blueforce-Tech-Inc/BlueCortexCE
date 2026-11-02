package com.ablueforce.cortexce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
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

    private final SpringAiConfig config = new SpringAiConfig();

    @Test
    @DisplayName("openai provider builds the OpenAI-compatible model and nothing else")
    void openAiBranchIsSelectedOnlyForTheOpenAiProvider() {
        ChatModel model = config.openAiChatModel(
                KEY, "https://api.deepseek.com", "deepseek-chat", "openai");

        OpenAiChatModel openAi = assertInstanceOf(OpenAiChatModel.class, model);
        assertEquals("deepseek-chat", openAi.getDefaultOptions().getModel());

        assertNull(config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "anthropic"),
                "provider=anthropic must not also create the OpenAI model");
        assertNull(config.openAiChatModel("", "https://api.deepseek.com", "deepseek-chat", "openai"),
                "an empty key must suppress the OpenAI model instead of failing at call time");
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
                config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "openai"));
        assertNull(config.anthropicChatModel(KEY, "https://api.anthropic.com", "claude-sonnet-4-5", 1024, "openai"));

        assertNull(config.openAiChatModel(KEY, "https://api.deepseek.com", "deepseek-chat", "anthropic"));
        assertInstanceOf(AnthropicChatModel.class,
                config.anthropicChatModel(KEY, "https://api.anthropic.com", "claude-sonnet-4-5", 1024, "anthropic"));
    }

    @Test
    @DisplayName("embedding stays on the OpenAI-compatible branch for either chat provider")
    void embeddingDoesNotFollowTheChatProvider() {
        // embeddingModel takes no provider argument at all: the chat switch
        // (openai | anthropic) must not change where embeddings come from.
        assertInstanceOf(OpenAiEmbeddingModel.class,
                config.embeddingModel(KEY, "https://api.siliconflow.cn", "BAAI/bge-m3", 1024, false));
        assertInstanceOf(OpenAiEmbeddingModel.class,
                config.embeddingModel(KEY, "https://api.siliconflow.cn", "BAAI/bge-m3", 1024, true));
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
