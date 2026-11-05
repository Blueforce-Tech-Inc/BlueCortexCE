package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.ablueforce.cortexce.service.LlmService;
import com.ablueforce.cortexce.service.ProviderCircuitBreaker;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — the Prometheus scrape surface.
 *
 * <p>Boots the real application and reads {@code /actuator/prometheus} the way a
 * scraper does. Two things are pinned here that a JSON snapshot cannot prove:</p>
 * <ol>
 *   <li>every operational meter exists <em>with the labels an operator needs</em>
 *       before any traffic arrives (queue status, provider, failure kind, path);</li>
 *   <li>the values actually move: the pending gauge follows a queue row inserted and
 *       transitioned directly in the shared database, and one search raises the hybrid
 *       request counter.</li>
 * </ol>
 * <p>The queue assertion deliberately does <em>not</em> go through
 * {@code POST /api/ingest/tool-use}. That route publishes a {@code PendingMessageEvent}
 * whose {@code @Async} listener consumes the row immediately, so a scrape issued right
 * after the 200 response races the worker: it observed {@code pending=1} locally and
 * {@code pending=0} on the slower CI runner (run 41769617418), which is the flake this
 * test used to have. Disabling the scheduler does not help because the listener is
 * event-driven, not scheduled. Inserting the row through the repository tests the
 * documented gauge contract — "read from the shared database at scrape time" — without
 * depending on how fast the worker wins.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.pending-message.enabled=false",
                "claudemem.embedding.backfill.enabled=false",
                "app.memory.refine-enabled=false",
                "claudemem.sse.timeout-ms=30000",
                // The test harness contributes a defaults-level "export disabled"
                // property; name the exporter explicitly so the scrape surface is
                // exercised exactly as production configures it.
                "management.prometheus.metrics.export.enabled=true",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2MetricsEndpointTest extends HttpLayerTestSupport {

    @Autowired
    private ProviderCircuitBreaker circuitBreaker;

    @AfterEach
    void closeCircuitAfterEachTest() {
        // The breaker is a singleton in the shared context; leaving it OPEN would
        // fast-fail every later test class in this JVM.
        circuitBreaker.reset();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("the scrape exposes queue, worker, model, embedding and retrieval series with labels")
    void scrapeExposesEveryOperationalSeries() {
        String body = scrape();

        assertTrue(body.contains("cortexce_queue_depth{status=\"pending\"}"),
                "pending queue depth gauge is missing");
        assertTrue(body.contains("cortexce_queue_depth{status=\"processing\"}"),
                "processing queue depth gauge is missing");
        assertTrue(body.contains("cortexce_queue_depth{status=\"failed\"}"),
                "failed queue depth gauge is missing");
        assertTrue(body.contains("cortexce_queue_stale"),
                "stale queue gauge is missing");
        assertTrue(body.contains("cortexce_embedding_backlog"),
                "embedding backlog gauge is missing");
        assertEquals(1.0, sample(body, "cortexce_vector_indexes_valid", null),
                "the HNSW validity gauge must report 1 when every index is usable");

        assertTrue(body.contains("cortexce_worker_processing_seconds_count{provider=\"openai\"}"),
                "worker duration histogram is missing its provider-labelled count");
        assertTrue(body.contains("cortexce_worker_processing_seconds_bucket"),
                "worker duration histogram buckets are missing");

        assertTrue(body.contains("cortexce_llm_calls_total"),
                "LLM call counter is missing");
        assertTrue(body.contains("failure_kind=\"TIMEOUT\""),
                "LLM failure counter must be pre-registered by failure kind");
        assertTrue(body.contains("outcome=\"success\""),
                "LLM success counter is missing");
        assertTrue(body.contains("cortexce_llm_retries_total"),
                "LLM retry counter is missing");

        assertTrue(body.contains("cortexce_search_requests_total{path=\"hybrid\"}"),
                "hybrid retrieval counter is missing");
        assertTrue(body.contains("cortexce_search_requests_total{path=\"lexical\"}"),
                "lexical retrieval counter is missing");
        assertTrue(body.contains("cortexce_search_duration_seconds_count{path=\"hybrid\"}"),
                "retrieval latency histogram is missing its path-labelled count");
    }

    @Test
    @DisplayName("the provider circuit state is scrapeable and follows the live breaker")
    void circuitStateTracksTheBreaker() {
        circuitBreaker.reset();
        assertEquals(1.0, sample(scrape(), "cortexce_llm_circuit_state", "state=\"closed\""),
                "an idle circuit must report closed=1");
        assertEquals(0.0, sample(scrape(), "cortexce_llm_circuit_state", "state=\"open\""),
                "an idle circuit must report open=0");

        for (int i = 0; i < 3; i++) {
            circuitBreaker.onOutcome(LlmService.FailureKind.TIMEOUT);
        }
        assertEquals(ProviderCircuitBreaker.State.OPEN, circuitBreaker.state(),
                "three unreachable outcomes must open the circuit");

        assertEquals(1.0, sample(scrape(), "cortexce_llm_circuit_state", "state=\"open\""),
                "an open circuit must report open=1 so an alert can fire");
        assertEquals(0.0, sample(scrape(), "cortexce_llm_circuit_state", "state=\"closed\""),
                "an open circuit must report closed=0");
    }

    @Test
    @DisplayName("the actuator surface stays closed to internals")
    void internalsAreNotExposed() {
        assertEquals(HttpStatus.NOT_FOUND, get("/actuator/env").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, get("/actuator/beans").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, get("/actuator/configprops").getStatusCode());
    }

    @Test
    @DisplayName("the queue gauge reads the shared queue row by row and one search moves the retrieval counter")
    void valuesChangeWithOneIngestAndOneSearch() {
        String project = uniqueProject("metrics");
        String sessionId = uniqueSession("metrics");

        double pendingBefore = sample(scrape(), "cortexce_queue_depth", "status=\"pending\"");
        double processingBefore = sample(scrape(), "cortexce_queue_depth", "status=\"processing\"");
        double hybridSearchesBefore = sample(scrape(),
                "cortexce_search_requests_total", "path=\"hybrid\"");
        assertTrue(Double.isFinite(pendingBefore), "pending gauge must be readable");
        assertTrue(Double.isFinite(processingBefore), "processing gauge must be readable");
        assertTrue(Double.isFinite(hybridSearchesBefore), "hybrid counter must be readable");

        ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                "session_id", sessionId,
                "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        JsonNode startBody = json(start);
        assertTrue(!startBody.path("session_db_id").asText().isBlank());

        PendingMessageEntity queued = new PendingMessageEntity();
        queued.setSessionDbId(UUID.fromString(startBody.path("session_db_id").asText()));
        queued.setContentSessionId(sessionId);
        queued.setMessageType("observation");
        queued.setToolName("Read");
        queued.setToolInput("{\"file_path\":\"" + project + "/README.md\"}");
        queued.setToolInputHash(UUID.randomUUID().toString().replace("-", ""));
        queued.setToolResponse("{\"content\":\"metrics scrape seed\"}");
        queued.setCwd(project);
        queued.setStatus("pending");
        queued.setRetryCount(0);
        queued.setCreatedAtEpoch(Instant.now().toEpochMilli());
        pendingMessageRepository.saveAndFlush(queued);

        double pendingAfterInsert = sample(scrape(), "cortexce_queue_depth", "status=\"pending\"");
        assertEquals(pendingBefore + 1.0, pendingAfterInsert,
                "one pending row must raise the pending gauge by exactly one");

        queued.setStatus("processing");
        queued.setStartedProcessingAtEpoch(Instant.now().toEpochMilli());
        pendingMessageRepository.saveAndFlush(queued);

        assertEquals(pendingBefore, sample(scrape(), "cortexce_queue_depth", "status=\"pending\""),
                "the gauge must stop counting the row once it leaves pending");
        assertEquals(processingBefore + 1.0,
                sample(scrape(), "cortexce_queue_depth", "status=\"processing\""),
                "the same row must be counted under processing instead");

        ResponseEntity<String> search = get("/api/search?project=" + project + "&query=metrics");
        assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());

        double hybridSearchesAfter = sample(scrape(),
                "cortexce_search_requests_total", "path=\"hybrid\"");
        assertEquals(hybridSearchesBefore + 1.0, hybridSearchesAfter,
                "one hybrid search must increment exactly one request counter");

        double workerSamples = sample(scrape(),
                "cortexce_worker_processing_seconds_count", "provider=\"openai\"");
        assertTrue(Double.isFinite(workerSamples) && workerSamples >= 0.0,
                "worker histogram count must stay readable even with the scheduler disabled");

        // The rehearsal database is shared by every layer, so leave no processing row
        // behind for another class's stale-recovery or readiness assertion to trip over.
        pendingMessageRepository.delete(queued);
        pendingMessageRepository.flush();
    }

    private String scrape() {
        ResponseEntity<String> response = get("/actuator/prometheus");
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return response.getBody() == null ? "" : response.getBody();
    }

    /**
     * Value of the first sample whose name matches and that contains every tag fragment.
     * Returns NaN when the series is absent, so a missing meter fails loudly in the caller.
     */
    private static double sample(String body, String metricName, String tagFragment) {
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(metricName + "{") && !trimmed.startsWith(metricName + " ")) {
                continue;
            }
            if (tagFragment != null && !trimmed.contains(tagFragment)) {
                continue;
            }
            int space = trimmed.lastIndexOf(' ');
            try {
                return Double.parseDouble(trimmed.substring(space + 1));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }
}
