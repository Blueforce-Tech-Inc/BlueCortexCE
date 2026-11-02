package com.ablueforce.cortexce.offline;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-scope model doubles whose answer can be switched per test.
 *
 * <p>Why a separate configuration instead of {@link OfflineAiTestModels}: the happy-path
 * test needs one fixed script, while the failure-path test needs to throw a real
 * provider exception (401/403/429/timeout) or return unusable content. Both configure
 * the <em>same</em> production services, the same prompt templates and the same parsers;
 * only the network edge changes.</p>
 *
 * <p>The doubles are mutable on purpose. Tests run sequentially (Surefire's default),
 * and each test resets the answer in {@code @BeforeEach}, so the shared Spring context
 * never leaks one test's failure into the next. No sleep, no scheduler, no wall clock.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ControllableAiTestModels {

    @Bean
    @Primary
    public MutableChatModel controllableChatModel() {
        return new MutableChatModel();
    }

    @Bean
    @Primary
    public MutableEmbeddingModel controllableEmbeddingModel() {
        return new MutableEmbeddingModel(OfflineAiTestModels.EMBEDDING_DIMENSIONS);
    }

    /** Answers or throws whatever the current test installed. */
    public static final class MutableChatModel implements ChatModel {

        private volatile String answer = OfflineAiTestModels.ScriptedChatModel.observationXml();
        private volatile RuntimeException failure;

        public void respondWith(String content) {
            this.answer = content;
            this.failure = null;
        }

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            RuntimeException current = this.failure;
            if (current != null) {
                throw current;
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(this.answer))));
        }
    }

    /** Produces the deterministic hash vector unless the test installs a failure. */
    public static final class MutableEmbeddingModel implements EmbeddingModel {

        private final int dimensions;
        private volatile RuntimeException failure;

        public MutableEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        public void succeed() {
            this.failure = null;
        }

        public void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> instructions = request.getInstructions();
            List<Embedding> results = new ArrayList<>(instructions.size());
            for (int i = 0; i < instructions.size(); i++) {
                results.add(new Embedding(vectorFor(instructions.get(i)), i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(String text) {
            return vectorFor(text);
        }

        @Override
        public float[] embed(Document document) {
            return vectorFor(document.getText());
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        private float[] vectorFor(String text) {
            RuntimeException current = this.failure;
            if (current != null) {
                throw current;
            }
            return OfflineAiTestModels.HashEmbeddingModel.vectorFor(text, dimensions);
        }
    }
}
