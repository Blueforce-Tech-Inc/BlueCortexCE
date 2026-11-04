package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — SSE wire contract for {@code GET /stream}.
 *
 * <p>The five event types are published in {@code docs/API.md}. This test pins the names
 * and the payload fields that the Viewer reads, so a silent rename or a dropped field
 * fails here instead of breaking the WebUI at runtime.
 *
 * <p>Four types are produced through real application paths: {@code initial_load} and
 * {@code processing_status} on connect, {@code new_prompt} and {@code new_observation}
 * through ingestion. {@code new_summary} is produced by services that require an LLM
 * round trip; the test drives the broadcaster with the same payload shape those services
 * send, which still pins the event name and envelope the transport must carry.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "claudemem.sse.timeout-ms=30000",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2SseContractTest extends HttpLayerTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private SSEBroadcaster broadcaster;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("the five documented SSE event types carry their documented payload fields")
    void documentedEventTypesAndFields() throws Exception {
        String project = uniqueProject("sse");
        String session = uniqueSession("sse");

        try (SseCollector sse = new SseCollector()) {
            sse.start();

            JsonNode initial = sse.awaitType("initial_load", WAIT);
            assertTrue(initial.path("projects").isArray(), initial.toString());
            assertTrue(initial.path("sources").isArray(), initial.toString());
            assertTrue(initial.path("projectsBySource").isObject(), initial.toString());
            assertTrue(initial.path("timestamp").isNumber(), initial.toString());

            JsonNode processing = sse.awaitType("processing_status", WAIT);
            assertTrue(processing.path("isProcessing").isBoolean(), processing.toString());
            assertTrue(processing.path("queueDepth").isNumber(), processing.toString());

            assertEquals(HttpStatus.OK, post("/api/session/start", json(Map.of(
                "session_id", session, "project_path", project))).getStatusCode());
            assertEquals(HttpStatus.OK, post("/api/ingest/user-prompt", json(Map.of(
                "session_id", session, "prompt_text", "sse-contract-prompt",
                "cwd", project, "prompt_number", 1))).getStatusCode());

            JsonNode prompt = sse.awaitType("new_prompt", WAIT);
            assertTrue(prompt.path("prompt").isObject(), prompt.toString());
            assertTrue(prompt.path("prompt").path("id").isTextual(), prompt.toString());
            assertTrue(prompt.path("prompt").path("prompt_text").isTextual(), prompt.toString());

            assertEquals(HttpStatus.OK, post("/api/ingest/observation", json(Map.of(
                "content_session_id", session, "project_path", project,
                "title", "sse-contract-observation", "narrative", "narrative",
                "type", "discovery", "facts", List.of("sse-fact")))).getStatusCode());

            JsonNode observation = sse.awaitType("new_observation", WAIT);
            assertTrue(observation.path("observation").isObject(), observation.toString());
            assertTrue(observation.path("observation").path("id").isTextual(), observation.toString());
            assertTrue(observation.path("observation").path("title").isTextual(), observation.toString());

            broadcaster.broadcast(Map.of(
                "type", "new_summary",
                "summary", Map.of("id", "sse-summary", "project", project)), "new_summary");

            JsonNode summary = sse.awaitType("new_summary", WAIT);
            assertTrue(summary.path("summary").isObject(), summary.toString());
        }
    }
}
