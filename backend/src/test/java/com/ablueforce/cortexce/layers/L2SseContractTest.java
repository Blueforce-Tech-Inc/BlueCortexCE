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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    // ------------------------------------------------------------------
    // Input limits. These live in this class on purpose: they need the same live
    // context, and a separate @SpringBootTest class with its own DynamicPropertySource
    // would open another Hikari pool against the shared test container (max_connections
    // 100), which is already close to its limit.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a body over 2 MiB answers 413 payload_too_large; a normal body still succeeds")
    void oversizedBodyIsRefusedWithTheStandardEnvelope() {
        String project = uniqueProject("payload-limit");
        String session = uniqueSession("payload-limit");

        ResponseEntity<String> normal = post("/api/ingest/observation", json(Map.of(
            "content_session_id", session,
            "project_path", project,
            "title", "small observation",
            "narrative", "short body well under the 2 MiB default")));
        assertEquals(HttpStatus.OK, normal.getStatusCode(), normal.getBody());

        ResponseEntity<String> oversized = post("/api/ingest/observation", json(Map.of(
            "content_session_id", session,
            "project_path", project,
            "title", "oversized observation",
            "narrative", "x".repeat(2_200_000))));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, oversized.getStatusCode(), oversized.getBody());
        JsonNode body = json(oversized);
        assertEquals("payload_too_large", body.path("error").path("code").asText(), body.toString());
        assertTrue(body.path("error").path("message").asText().contains("2097152"), body.toString());
        assertEquals(2_097_152, body.path("error").path("details").path("max_bytes").asInt(),
            body.toString());

        // The service keeps serving normal requests after the refusal.
        assertEquals(HttpStatus.OK, get("/api/health").getStatusCode());
    }

    @Test
    @DisplayName("a burst over the 200-token bucket answers 429 rate_limited with Retry-After")
    void burstBeyondCapacityAnswers429WithRetryAfter() throws Exception {
        int requests = 600;
        int threads = 50;
        String project = uniqueProject("rate-limit");
        List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String session = uniqueSession("rate-limit") + "-" + i;
            calls.add(() -> post("/api/session/start", json(Map.of(
                "session_id", session, "project_path", project))));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int limited = 0;
        try {
            List<Future<ResponseEntity<String>>> futures = pool.invokeAll(calls);
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
                if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    limited++;
                    JsonNode body = json(response);
                    assertEquals("rate_limited", body.path("error").path("code").asText(),
                        body.toString());
                    String retryAfter = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                    assertTrue(retryAfter != null && retryAfter.matches("[1-9][0-9]*"),
                        "Retry-After=" + retryAfter);
                } else {
                    assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(limited > 0, "a 600-request burst must exceed the 200-token bucket");

        // Reads are not limited.
        assertEquals(HttpStatus.OK, get("/api/health").getStatusCode());

        // Refill is 100 tokens/s; a short pause restores capacity so later tests in this
        // shared context do not start against an empty bucket.
        Thread.sleep(3_000);
    }
}
