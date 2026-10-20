package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the property that a <em>failing</em> LLM call must be visible to the
 * caller of {@code GET /api/test/llm}.
 *
 * <p>Why this test exists: {@link LlmService#chatCompletionWithUsage} swallows
 * every provider exception and degrades it to an empty string. That is fine for
 * the callers that want a degraded result, but a connectivity probe cannot tell
 * "the provider answered with nothing" from "the provider is returning 401".
 * Before the fix, an expired API key produced
 * {@code {"status":"success","message":"LLM (DeepSeek) is working!"}} with an
 * empty {@code response} — the repository's own
 * {@code scripts/test-llm-provider.sh} then printed "LLM test passed" and
 * exited 0 while the LLM was completely unusable.
 *
 * <p>The property pinned here is deliberately one that <em>can fail</em>:
 * a provider exception must reach the caller. A companion test pins the
 * opposite direction (a successful call must still succeed), so that a fix
 * which simply always throws cannot pass.
 */
class TestControllerLlmFailureTest {

    /**
     * Builds a ChatClient whose {@code call().chatResponse()} throws.
     */
    private static ChatClient failingChatClient(RuntimeException failure) {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenThrow(failure);

        ChatClient client = mock(ChatClient.class);
        when(client.prompt()).thenReturn(requestSpec);
        return client;
    }

    /**
     * Builds a ChatClient that answers with the given content.
     */
    private static ChatClient succeedingChatClient(String content) {
        ChatResponse chatResponse = new ChatResponse(List.of(
            new Generation(new AssistantMessage(content))));

        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);

        ChatClient client = mock(ChatClient.class);
        when(client.prompt()).thenReturn(requestSpec);
        return client;
    }

    @Test
    void providerFailure_isReportedAsErrorWith500() {
        ChatClient client = failingChatClient(
            new IllegalStateException("401 - Authentication Fails, Your api key is invalid"));
        LlmService llmService = new LlmService(List.of(client));
        TestController controller = new TestController(llmService, mock(EmbeddingService.class));

        ResponseEntity<Map<String, Object>> response = controller.testLlm();

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("error");
        assertThat(String.valueOf(response.getBody().get("message")))
            .contains("401")
            .contains("LLM (DeepSeek) failed");
        assertThat(response.getBody()).doesNotContainKey("response");
    }

    @Test
    void providerSuccess_isStillReportedAsSuccessWith200() {
        ChatClient client = succeedingChatClient("Hello from DeepSeek!");
        LlmService llmService = new LlmService(List.of(client));
        TestController controller = new TestController(llmService, mock(EmbeddingService.class));

        ResponseEntity<Map<String, Object>> response = controller.testLlm();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("success");
    }

    /**
     * The swallowing behaviour of {@code chatCompletion} must stay unchanged:
     * the other eight callers depend on getting an empty string rather than an
     * exception. This test pins that the fix did not leak into them.
     */
    @Test
    void chatCompletion_stillDegradesProviderFailureToEmptyString() {
        ChatClient client = failingChatClient(new IllegalStateException("401 - invalid key"));
        LlmService llmService = new LlmService(List.of(client));

        String content = llmService.chatCompletion("system", "user");

        assertThat(content).isEmpty();
    }
}
