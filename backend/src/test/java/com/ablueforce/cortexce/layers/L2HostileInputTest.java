package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — hostile input surface, driven over real HTTP against the real application.
 *
 * <p>Three families are pinned:
 * <ol>
 *   <li><b>Path traversal</b> on the compiled Viewer bundle, the log API and unmatched
 *       static paths: {@code ../}, single- and double-percent-encoded dots, absolute
 *       paths and an encoded NUL suffix.</li>
 *   <li><b>Injection-shaped query text</b> for {@code GET /api/search}: SQL fragments,
 *       backslashes, NUL, a 100 KB value, emoji plus a combining mark, and an isolated
 *       surrogate encoded as CESU-8.</li>
 *   <li><b>Structural attacks</b> on JSON bodies: 1000-level nesting, a very large array,
 *       duplicate keys and wrong types.</li>
 * </ol>
 *
 * <p>Every case must answer a 4xx (in the shared error envelope when the response is
 * produced by Spring) or a safe 2xx — never a 5xx, never a stack trace, never an
 * internal file path. Traversal probes additionally assert that no file content leaked
 * into the body.
 *
 * <p>The rate limiter is switched off for this class: the hostile corpus is dozens of
 * requests from one address, and a 429 would mask the behaviour under test rather than
 * exercise it (429 has its own L2 coverage).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "cortexce.http.rate-limit.enabled=false",
                "claudemem.sse.timeout-ms=30000",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2HostileInputTest extends HttpLayerTestSupport {

    private static final HttpClient RAW = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Markers that must never appear in a response body. */
    private static final List<String> LEAK_MARKERS = List.of(
            "org.springframework", "java.lang.", "java.sql", "Caused by:",
            "\tat ", ".java:", "root:x:0:0", "spring.datasource");

    private String project;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @BeforeEach
    void seedSearchableObservation() {
        project = uniqueProject("l2-hostile");
        String sessionId = uniqueSession("l2-hostile");
        ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                "session_id", sessionId,
                "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        ResponseEntity<String> created = post("/api/ingest/observation", json(Map.of(
                "content_session_id", sessionId,
                "project_path", project,
                "title", "hostile-seed-observation",
                "narrative", "hostile-seed narrative",
                "type", "discovery",
                "facts", List.of("hostile-seed-fact"))));
        assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());
    }

    // ------------------------------------------------------------ path traversal

    @Test
    @DisplayName("traversal probes on the bundle, logs and static paths never leak a file or return 5xx")
    void traversalProbesStaySafe() throws Exception {
        // Baseline: the compiled bundle really is being served, otherwise the
        // traversal probes below could be "passing" only because nothing is mounted.
        HttpResponse<String> bundle = rawGet("/viewer-bundle.js");
        assertEquals(200, bundle.statusCode(), "the Viewer bundle must be served");
        assertTrue(bundle.body().length() > 1000, "the served bundle looks empty");

        List<String> probes = List.of(
                "/../application.yml",
                "/../../../../etc/passwd",
                "/%2e%2e/%2e%2e/etc/passwd",
                "/%2E%2E%2F%2E%2E%2Fetc%2Fpasswd",
                "/%252e%252e%252f%252e%252e%252fetc%252fpasswd",
                "/viewer-bundle.js/../../../../etc/passwd",
                "/viewer-bundle.js%00",
                "/api/logs/../../../../etc/passwd",
                "/api/logs/%2e%2e%2f%2e%2e%2fetc%2fpasswd",
                "//etc/passwd",
                "/%00viewer-bundle.js");

        for (String probe : probes) {
            HttpResponse<String> response = rawGet(probe);
            System.out.println("[J1][traversal] " + probe + " -> " + response.statusCode());
            assertSafe(probe, response.statusCode(), response.body());
            assertFalse(response.body().contains("root:x:0:0"), probe + " leaked /etc/passwd");
            assertFalse(response.body().contains("spring:\n"), probe + " leaked application.yml");
        }
    }

    // ------------------------------------------------------------ query injection

    @Test
    @DisplayName("injection-shaped search values answer without 500 and without leaking internals")
    void searchQueryValuesStayNon5xx() throws Exception {
        List<String> encodedQueries = List.of(
                "%27%3B+DROP+TABLE+observations%3B+--",     // '; DROP TABLE observations; --
                "%5C%5C%5C%5C",                              // reversed solidus ×4
                "%00",                                        // encoded NUL
                "%F0%9F%98%80%CC%81",                         // emoji + combining acute
                "%ED%A0%80",                                  // isolated high surrogate (CESU-8)
                "%ED%B0%80",                                  // isolated low surrogate (CESU-8)
                "%FF%FE",                                     // invalid UTF-8 bytes
                "**",                                         // tsquery operator characters
                "a".repeat(100_000));                         // oversized value

        for (String encoded : encodedQueries) {
            String label = encoded.length() > 40 ? "<100 KB value>" : encoded;
            HttpResponse<String> response = rawGet(
                    "/api/search?project=" + urlEncode(project) + "&query=" + encoded);
            int status = response.statusCode();
            System.out.println("[J1][search] " + label + " -> " + status);
            assertSafe("query=" + label, status, response.body());
            if (status == 200) {
                JsonNode body = JSON.readTree(response.body());
                assertTrue(body.path("observations").isArray(),
                        "query=" + label + " answered 200 without an observations array");
            }
        }
    }

    @Test
    @DisplayName("numeric query parameters with wrong shapes answer 400 in the envelope")
    void numericParameterTypeMismatchesAnswer400() throws Exception {
        for (String path : List.of(
                "/api/search?project=" + urlEncode(project) + "&limit=abc",
                "/api/search?project=" + urlEncode(project) + "&limit=99999999999999999999",
                "/api/search?project=" + urlEncode(project) + "&offset=" + urlEncode("1e3"),
                "/api/observations?project=" + urlEncode(project) + "&limit=abc",
                "/api/logs?lines=abc")) {
            HttpResponse<String> response = rawGet(path);
            assertSafe(path, response.statusCode(), response.body());
            assertEquals(400, response.statusCode(), path);
            assertEnvelope(path, response.body());
        }
    }

    // ------------------------------------------------------------ structural bodies

    @Test
    @DisplayName("1000-level nesting and a very large array are rejected or handled without 5xx")
    void deepNestingAndLargeArraysStaySafe() throws Exception {
        String deep = "[".repeat(1000) + "{}" + "]".repeat(1000);
        HttpResponse<String> nested = rawPost("/api/ingest/observation", "application/json", deep);
        assertSafe("1000-level nesting", nested.statusCode(), nested.body());
        assertTrue(nested.statusCode() < 500, nested.body());

        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < 60_000; i++) {
            if (i > 0) {
                facts.append(',');
            }
            facts.append("\"f").append(i).append("\"");
        }
        String large = "{\"content_session_id\":\"l2-hostile-large\","
                + "\"project_path\":" + JSON.writeValueAsString(project) + ","
                + "\"title\":\"large-array\","
                + "\"facts\":[" + facts + "]}";
        HttpResponse<String> bigArray = rawPost("/api/ingest/observation", "application/json", large);
        assertSafe("large array", bigArray.statusCode(), bigArray.body());
        assertTrue(bigArray.statusCode() < 500, bigArray.body());
    }

    @Test
    @DisplayName("duplicate keys and wrong field types answer 2xx or 400, never 5xx")
    void duplicateKeysAndWrongTypesStaySafe() throws Exception {
        String duplicateKeys = "{\"content_session_id\":\"a\",\"content_session_id\":\"b\","
                + "\"project_path\":" + JSON.writeValueAsString(project) + "}";
        HttpResponse<String> duplicates = rawPost("/api/ingest/observation",
                "application/json", duplicateKeys);
        assertSafe("duplicate keys", duplicates.statusCode(), duplicates.body());

        List<String> wrongTypes = List.of(
                "{\"content_session_id\":123,\"project_path\":"
                        + JSON.writeValueAsString(project) + "}",
                "{\"content_session_id\":[],\"project_path\":"
                        + JSON.writeValueAsString(project) + "}",
                "{\"content_session_id\":" + JSON.writeValueAsString(uniqueSession("l2-hostile"))
                        + ",\"project_path\":{\"nested\":true}}",
                "{\"content_session_id\":" + JSON.writeValueAsString(uniqueSession("l2-hostile"))
                        + ",\"project_path\":" + JSON.writeValueAsString(project)
                        + ",\"facts\":12345}",
                "{\"content_session_id\":" + JSON.writeValueAsString(uniqueSession("l2-hostile"))
                        + ",\"project_path\":" + JSON.writeValueAsString(project)
                        + ",\"promptNumber\":\"not-a-number\"}");
        for (String body : wrongTypes) {
            HttpResponse<String> response = rawPost("/api/ingest/observation",
                    "application/json", body);
            assertSafe(body, response.statusCode(), response.body());
            assertTrue(response.statusCode() == 200 || response.statusCode() == 400,
                    body + " answered " + response.statusCode() + ": " + trim(response.body()));
            if (response.statusCode() == 400) {
                assertEnvelope(body, response.body());
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private HttpResponse<String> rawGet(String rawPath) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + rawPath))
                .timeout(Duration.ofSeconds(30))
                .GET());
    }

    private HttpResponse<String> rawPost(String rawPath, String contentType, String body)
            throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + rawPath))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return RAW.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertSafe(String label, int status, String body) {
        assertTrue(status < 500, label + " returned " + status + ": " + trim(body));
        for (String marker : LEAK_MARKERS) {
            assertFalse(body.contains(marker),
                    label + " leaked internal detail \"" + marker + "\": " + trim(body));
        }
    }

    /** A Spring-produced JSON error must carry the shared envelope. */
    private static void assertEnvelope(String label, String body) throws Exception {
        JsonNode node = JSON.readTree(body);
        assertTrue(node.has("error"), label + " is missing the error object: " + trim(body));
        assertFalse(node.path("error").path("code").asText("").isBlank(),
                label + " is missing error.code: " + trim(body));
        assertFalse(node.path("message").asText("").isBlank(),
                label + " is missing the top-level message mirror: " + trim(body));
        assertFalse(node.path("error").path("message").asText("").isBlank(),
                label + " is missing error.message: " + trim(body));
    }

    private static String trim(String body) {
        if (body == null) {
            return "<null>";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
