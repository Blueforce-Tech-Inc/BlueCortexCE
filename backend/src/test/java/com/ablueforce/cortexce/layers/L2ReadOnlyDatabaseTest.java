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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — what a client sees when the database refuses writes.
 *
 * <p>The rehearsal database is writable, so the read-only state is reproduced with a
 * BEFORE INSERT trigger that raises SQLSTATE 25006 for one project path only. The
 * application sees exactly what a read-only volume or a read-only replica produces
 * (a {@code DataAccessException} whose PostgreSQL state is 25006), and every other
 * project in the same run stays writable.
 *
 * <p>Pinned here:
 * <ol>
 *   <li>The ingest request answers <b>503</b>, not 500, because the condition is
 *       temporary and retryable.</li>
 *   <li>The body carries {@code code=service_unavailable} and a message that names
 *       the cause ("read-only"), so a person reading logs or a client reading JSON
 *       can tell it apart from a crash.</li>
 *   <li>Reads keep working: a search in the same window still answers 200.</li>
 * </ol>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2ReadOnlyDatabaseTest extends HttpLayerTestSupport {

    private static final String READ_ONLY_PREFIX = "cortexce-l2-readonly-";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("a database that rejects writes answers 503 with a readable message, reads stay 200")
    void readOnlyDatabaseAnswersReadableServiceUnavailable() {
        String project = uniqueProject(READ_ONLY_PREFIX);
        String sessionId = uniqueSession("l2-readonly");

        installReadOnlyProbeTrigger();
        try {
            ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                    "session_id", sessionId,
                    "project_path", project)));
            assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());

            ResponseEntity<String> created = post("/api/ingest/observation", json(Map.of(
                    "content_session_id", sessionId,
                    "project_path", project,
                    "title", "l2-readonly-observation",
                    "narrative", "l2-readonly-observation narrative",
                    "type", "discovery",
                    "facts", List.of("l2-readonly-fact"))));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, created.getStatusCode(),
                    "a rejected write must not look like a crash: " + created.getBody());
            JsonNode body = json(created);
            assertEquals("service_unavailable", body.path("error").path("code").asText(),
                    created.getBody());
            String message = body.path("error").path("message").asText();
            assertTrue(message.contains("read-only"),
                    "the message must name the cause, got: " + message);
            assertEquals(message, body.path("message").asText(),
                    "the top-level message mirrors the error message");

            // The same window must still serve reads; a read-only database is not down.
            ResponseEntity<String> search = get("/api/search?project=" + project + "&query=readonly");
            assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());
            JsonNode searchBody = json(search);
            assertEquals(0, searchBody.path("count").asInt(),
                    "an empty project answers with no rows: " + search.getBody());
            assertTrue(searchBody.path("strategy").asText().matches("hybrid|tsvector"),
                    "reads keep using a normal strategy: " + search.getBody());
        } finally {
            dropReadOnlyProbeTrigger();
        }
    }

    private void installReadOnlyProbeTrigger() {
        jdbc.execute("""
            CREATE OR REPLACE FUNCTION l2_readonly_probe() RETURNS trigger AS $$
            BEGIN
              RAISE EXCEPTION 'cannot execute INSERT in a read-only transaction'
                USING ERRCODE = '25006';
            END $$ LANGUAGE plpgsql""");
        jdbc.execute("""
            CREATE TRIGGER l2_readonly_probe_trigger BEFORE INSERT ON mem_observations
            FOR EACH ROW WHEN (NEW.project_path LIKE '/tmp/cortexce-cortexce-l2-readonly-%')
            EXECUTE FUNCTION l2_readonly_probe()""");
    }

    private void dropReadOnlyProbeTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS l2_readonly_probe_trigger ON mem_observations");
        jdbc.execute("DROP FUNCTION IF EXISTS l2_readonly_probe()");
    }
}
