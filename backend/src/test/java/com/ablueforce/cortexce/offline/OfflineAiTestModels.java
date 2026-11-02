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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline stand-ins for the two model beans the production configuration would
 * otherwise fetch from a remote provider.
 *
 * <p>Scope: test sources only. Nothing here is on the production classpath, and
 * no production default is changed — {@code SpringAiConfig} still decides which
 * real model to create, and these beans only exist when a test imports this
 * class explicitly.</p>
 *
 * <p>Why fake beans instead of mocks of {@code LlmService} / {@code EmbeddingService}:
 * the chains under test (observation extraction, session summary, semantic
 * search) must exercise the real services, the real prompt templates and the
 * real XML parser. Only the network edge is replaced, so a regression in the
 * service wiring or the parser is still caught.</p>
 *
 * <p>Determinism: every response is a pure function of the prompt content and
 * every vector is a pure function of the embedded text. Nothing depends on
 * wall-clock time, randomness or ordering, so repeated runs produce identical
 * observations, summaries and vectors.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineAiTestModels {

    /** pgvector column targeted by the observation chain on this schema. */
    public static final int EMBEDDING_DIMENSIONS = 1024;

    /** Marker text the observation chain must persist; asserted by the test. */
    public static final String OBSERVATION_TITLE = "Offline deterministic observation";
    public static final String OBSERVATION_NARRATIVE =
        "The offline ChatModel stand-in produced this narrative without any network call.";
    public static final String SUMMARY_LEARNED =
        "The offline ChatModel stand-in produced this summary without any network call.";

    /** Fragment unique to the summary prompt; used to route the fake response. */
    public static final String SUMMARY_PROMPT_MARKER = "PROGRESS SUMMARY CHECKPOINT";
    /** Fragment unique to the observation prompt. */
    public static final String OBSERVATION_PROMPT_MARKER = "<observed_from_primary_session>";

    /** @Primary guarantees these win over any stray provider bean a developer may export. */
    @Bean
    @Primary
    public ChatModel offlineChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel offlineEmbeddingModel() {
        return new HashEmbeddingModel(EMBEDDING_DIMENSIONS);
    }

    /**
     * Routes a prompt to one of three fixed answers by its content.
     *
     * <p>An unknown prompt throws instead of returning a generic string: a new
     * LLM call site must fail loudly here rather than silently get an
     * unparseable answer and turn the chain test into a false negative.</p>
     */
    public static final class ScriptedChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            String contents = prompt.getContents();
            if (contents == null) {
                throw new IllegalStateException("offline ChatModel received an empty prompt");
            }
            String response;
            if (contents.contains(SUMMARY_PROMPT_MARKER)) {
                response = summaryResponse();
            } else if (contents.contains("quality_score")) {
                response = "{\"quality_score\":0.8,\"feedback_type\":\"SUCCESS\","
                    + "\"reasoning\":\"offline test double\"}";
            } else if (contents.contains("Respond with ONLY one word")) {
                response = "SUCCESS";
            } else if (contents.contains("Hello from DeepSeek")) {
                response = "Hello from DeepSeek!";
            } else if (contents.contains(OBSERVATION_PROMPT_MARKER)) {
                response = observationXml();
            } else {
                throw new IllegalStateException(
                    "offline ChatModel has no scripted answer for prompt: "
                        + abbreviate(contents));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }

        /** Fixed, parseable observation answer. Shared with the failure-path test. */
        public static String observationXml() {
            return "<observation>\n"
                + "  <type>discovery</type>\n"
                + "  <title>" + OBSERVATION_TITLE + "</title>\n"
                + "  <subtitle>deterministic offline stand-in</subtitle>\n"
                + "  <narrative>" + OBSERVATION_NARRATIVE + "</narrative>\n"
                + "  <facts><fact>fact-one</fact><fact>fact-two</fact></facts>\n"
                + "  <concepts><concept>offline-testing</concept></concepts>\n"
                + "  <files_read><file>OfflineAiTestModels.java</file></files_read>\n"
                + "  <files_modified></files_modified>\n"
                + "</observation>";
        }

        private static String summaryResponse() {
            return "<summary>\n"
                + "  <request>Offline deterministic summary</request>\n"
                + "  <investigated>Ran the observation and summary chains without network access.</investigated>\n"
                + "  <learned>" + SUMMARY_LEARNED + "</learned>\n"
                + "  <completed>The session summary row was persisted.</completed>\n"
                + "  <next_steps>Assert the durable job reached its terminal state.</next_steps>\n"
                + "  <notes>offline-test-double</notes>\n"
                + "</summary>";
        }

        private static String abbreviate(String text) {
            String singleLine = text.replaceAll("\\s+", " ").trim();
            return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
        }
    }

    /**
     * Produces a deterministic, non-zero vector for any text.
     *
     * <p>The vector is a hash expansion, not a semantic embedding: two texts
     * that are close in meaning are not close here. That is deliberate — the
     * test asserts the <em>path</em> taken (vector branch vs. full-text
     * fallback), never similarity quality, which no offline stand-in can
     * honestly claim.</p>
     */
    public static final class HashEmbeddingModel implements EmbeddingModel {

        private final int dimensions;

        public HashEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> instructions = request.getInstructions();
            List<Embedding> results = new ArrayList<>(instructions.size());
            for (int i = 0; i < instructions.size(); i++) {
                results.add(new Embedding(vectorFor(instructions.get(i), dimensions), i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(String text) {
            return vectorFor(text, dimensions);
        }

        @Override
        public float[] embed(Document document) {
            return vectorFor(document.getText(), dimensions);
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        /** Pure function of the text: same input, same vector, across JVMs and runs. */
        public static float[] vectorFor(String text, int dimensions) {
            String source = text == null ? "" : text;
            float[] vector = new float[dimensions];
            byte[] block = null;
            for (int i = 0; i < dimensions; i++) {
                if (i % 32 == 0) {
                    block = sha256(source + "#" + (i / 32));
                }
                int value = block[i % 32] & 0xFF;
                vector[i] = (value - 127.5f) / 127.5f;
            }
            if (vector[0] == 0.f) {
                vector[0] = 1.f;
            }
            return vector;
        }

        private static byte[] sha256(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return digest.digest(input.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
