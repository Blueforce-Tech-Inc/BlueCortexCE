package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — concurrency correctness with two clients on one project.
 *
 * <p>Two sessions run against the same project and write an <em>identical</em>
 * observation at the same moment. The project shares one memory scope, so the
 * pre-existing content-hash dedup used to suppress the second write and hand
 * session B the row owned by session A. The assertions pin the intended model:
 * identity is per conversation, retrieval sees both writes, and the project-level
 * context injection contains the shared content.
 *
 * <p>A second group fires ten identical direct observations into one session at
 * once and expects a single row: the writer serialises on the conversation row
 * lock, so the duplicate check is trustworthy.
 *
 * <p>A third group does the same for ten concurrent identical tool-use hooks:
 * queue admission deduplicates under the same lock and leaves one pending row,
 * which produces exactly one observation when processed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.rate-limit.max-requests=200",
                "claudemem.sse.timeout-ms=30000",
                "logging.level.com.ablueforce=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2ConcurrentSessionIsolationTest extends HttpLayerTestSupport {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("two sessions writing identical content stay isolated, both searchable, and injected")
    void crossSessionIsolationRetrievalAndInjection() throws Exception {
        String project = uniqueProject("l2-concurrency");
        String sessionA = uniqueSession("l2-concurrency-a");
        String sessionB = uniqueSession("l2-concurrency-b");
        startSession(sessionA, project);
        startSession(sessionB, project);

        String token = "crosstoken" + UUID.randomUUID().toString().replace("-", "");
        String title = "shared observation " + token;
        String narrative = "shared narrative " + token;

        List<Supplier<ResponseEntity<String>>> calls = List.of(
                () -> post("/api/ingest/observation",
                        json(observation(sessionA, project, title, narrative))),
                () -> post("/api/ingest/observation",
                        json(observation(sessionB, project, title, narrative))));
        List<ResponseEntity<String>> responses = fireConcurrently(calls);
        for (ResponseEntity<String> response : responses) {
            assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        }

        JsonNode idA = json(responses.get(0)).path("id");
        JsonNode idB = json(responses.get(1)).path("id");
        assertNotEquals(idA.asText(), idB.asText(),
                "the second session received the first session's observation instead of its own row");

        JsonNode listA = json(get("/api/observations?project=" + project
                + "&platformSource=claude&contentSessionId=" + sessionA + "&limit=100"));
        JsonNode listB = json(get("/api/observations?project=" + project
                + "&platformSource=claude&contentSessionId=" + sessionB + "&limit=100"));
        assertEquals(1, listA.path("items").size(), "session A list: " + listA);
        assertEquals(1, listB.path("items").size(), "session B list: " + listB);
        assertEquals(idA.asText(), listA.path("items").get(0).path("id").asText());
        assertEquals(idB.asText(), listB.path("items").get(0).path("id").asText());

        ResponseEntity<String> searchResponse = get("/api/search?project=" + project
                + "&query=" + token + "&limit=20");
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode(), searchResponse.getBody());
        JsonNode observations = json(searchResponse).path("observations");
        assertTrue(observations.isArray(), "search response: " + searchResponse.getBody());
        Set<String> ids = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (JsonNode item : observations) {
            ids.add(item.path("id").asText());
            sessions.add(item.path("content_session_id").asText());
        }
        assertEquals(Set.of(idA.asText(), idB.asText()), ids,
                "search must return both concurrent writes: " + observations);
        assertEquals(Set.of(sessionA, sessionB), sessions,
                "search results must stay attributed to their own session: " + observations);

        ResponseEntity<String> injectResponse = get("/api/context/inject?projects=" + project);
        assertEquals(HttpStatus.OK, injectResponse.getStatusCode(), injectResponse.getBody());
        JsonNode inject = json(injectResponse);
        assertTrue(inject.path("context").asText().contains(token),
                "project-scoped injection must contain both writes: " + inject);
    }

    @Test
    @DisplayName("ten simultaneous identical observations in one session produce one row")
    void tenConcurrentIdenticalObservationsProduceOneRow() throws Exception {
        String project = uniqueProject("l2-concurrency");
        String session = uniqueSession("l2-concurrency-single");
        startSession(session, project);

        String title = "single " + UUID.randomUUID();
        String narrative = "single narrative";

        List<Supplier<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> post("/api/ingest/observation",
                    json(observation(session, project, title, narrative))));
        }
        List<ResponseEntity<String>> responses = fireConcurrently(calls);
        Set<String> ids = new HashSet<>();
        for (ResponseEntity<String> response : responses) {
            assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
            ids.add(json(response).path("id").asText());
        }
        assertEquals(1, ids.size(), "ten identical writes returned " + ids.size() + " distinct rows: " + ids);

        int rows = jdbc.queryForObject("""
                SELECT count(*) FROM mem_observations
                WHERE content_session_id = ? AND platform_source = 'claude'
                """, Integer.class, session);
        assertEquals(1, rows, "expected exactly one stored observation for " + session);
    }

    @Test
    @DisplayName("ten simultaneous identical tool-use hooks enqueue one job and produce one observation")
    void tenConcurrentIdenticalToolUsesEnqueueOneJob() throws Exception {
        String project = uniqueProject("l2-concurrency");
        String session = uniqueSession("l2-concurrency-tool");
        startSession(session, project);

        String toolInput = "echo concurrency fact-one fact-two";
        List<Supplier<ResponseEntity<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> post("/api/ingest/tool-use", json(java.util.Map.of(
                    "session_id", session,
                    "cwd", project,
                    "platform_source", "claude",
                    "tool_name", "Bash",
                    "tool_input", toolInput,
                    "tool_response", "fact-one fact-two output"))));
        }
        for (ResponseEntity<String> response : fireConcurrently(calls)) {
            assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        }

        String conversationId = jdbc.queryForObject("""
                SELECT id::text FROM mem_sessions
                WHERE content_session_id = ? AND platform_source = 'claude'
                """, String.class, session);
        List<String> pending = jdbc.queryForList("""
                SELECT id::text FROM mem_pending_messages
                WHERE session_db_id = ?::uuid AND message_type = 'observation'
                """, String.class, conversationId);
        assertEquals(1, pending.size(),
                "ten identical hooks must leave exactly one admitted job, found " + pending);

        awaitUntil("the admitted job to produce one observation", java.time.Duration.ofSeconds(30), () -> {
            Integer count = jdbc.queryForObject("""
                    SELECT count(*) FROM mem_observations WHERE conversation_id = ?::uuid
                    """, Integer.class, conversationId);
            return count != null && count == 1;
        });

        int observations = jdbc.queryForObject("""
                SELECT count(*) FROM mem_observations WHERE conversation_id = ?::uuid
                """, Integer.class, conversationId);
        assertEquals(1, observations, "the single admitted job must produce one observation");
    }

    // ------------------------------------------------------------ helpers

    private void startSession(String session, String project) {
        ResponseEntity<String> response = post("/api/session/start", json(java.util.Map.of(
                "session_id", session,
                "project_path", project,
                "platform_source", "claude")));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
    }

    private static java.util.Map<String, Object> observation(String session, String project,
                                                             String title, String narrative) {
        return java.util.Map.of(
                "content_session_id", session,
                "project_path", project,
                "platform_source", "claude",
                "title", title,
                "narrative", narrative,
                "type", "discovery",
                "concepts", List.of("how-it-works"),
                "facts", List.of("concurrent fact"));
    }


    /** Fires every call at once and returns the answers in call order. */
    private static List<ResponseEntity<String>> fireConcurrently(
            List<Supplier<ResponseEntity<String>>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size(), r -> {
            Thread thread = new Thread(r, "concurrent-client");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (Supplier<ResponseEntity<String>> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.get();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "clients did not all start");
            go.countDown();
            List<ResponseEntity<String>> answers = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                answers.add(future.get(60, TimeUnit.SECONDS));
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
