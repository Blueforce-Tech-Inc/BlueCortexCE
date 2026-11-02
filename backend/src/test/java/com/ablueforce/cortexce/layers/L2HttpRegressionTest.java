package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.dto.ApiResponses;
import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — HTTP regression layer.
 *
 * <p>Starts the real application ({@code webEnvironment = RANDOM_PORT}) and drives
 * every published REST operation over HTTP against the dedicated rehearsal
 * database. The model edge is the offline stand-in, so the layer needs no key and
 * no network.
 *
 * <p>What is pinned:
 * <ol>
 *   <li><b>Full surface coverage.</b> The case table is compared for set equality
 *       with the operations springdoc publishes from the running app. A new endpoint
 *       without a case, or a case for an endpoint that disappeared, fails.</li>
 *   <li><b>No server errors.</b> Every call must answer a documented status; a 5xx
 *       is a regression even on failure paths.</li>
 *   <li><b>Negative contracts.</b> Missing fields → 400, unknown session → 404,
 *       legacy/removed paths → 404, malformed UUID → 400, limit/offset clamped.</li>
 *   <li><b>Wire envelopes.</b> Pagination keeps {@code items}/{@code hasMore};
 *       session start keeps {@code updateFiles}; settings keep {@code CLAUDE_MEM_*}.</li>
 * </ol>
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
class L2HttpRegressionTest extends HttpLayerTestSupport {

    private static final String CURSOR_PROJECT = "l2-cursor-project";

    private String project;
    private String sessionId;
    private String sessionDbId;
    private final List<String> observationIds = new ArrayList<>();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @BeforeEach
    void seedDeterministicData() {
        project = uniqueProject("l2");
        sessionId = uniqueSession("l2");
        observationIds.clear();

        ResponseEntity<String> start = post("/api/session/start", json(java.util.Map.of(
                "session_id", sessionId,
                "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        JsonNode startBody = json(start);
        sessionDbId = startBody.path("session_db_id").asText();
        assertFalse(sessionDbId.isBlank());

        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> created = post("/api/ingest/observation", json(java.util.Map.of(
                    "content_session_id", sessionId,
                    "project_path", project,
                    "title", "l2-seed-observation-" + i,
                    "narrative", "l2-seed-observation-" + i + " narrative",
                    "type", "discovery",
                    "facts", List.of("l2-fact-" + i))));
            assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());
            observationIds.add(json(created).path("id").asText());
        }

        for (int i = 1; i <= 2; i++) {
            ResponseEntity<String> prompt = post("/api/ingest/user-prompt", json(java.util.Map.of(
                    "session_id", sessionId,
                    "prompt_text", "l2-seed-prompt-" + i,
                    "cwd", project,
                    "prompt_number", i)));
            assertEquals(HttpStatus.OK, prompt.getStatusCode(), prompt.getBody());
        }
    }

    @Test
    @DisplayName("health, version and readiness describe the running process")
    void healthVersionAndReadinessDescribeTheRunningProcess() {
        ResponseEntity<String> health = get("/api/health");
        assertEquals(HttpStatus.OK, health.getStatusCode(), health.getBody());
        JsonNode body = json(health);
        assertEquals("ok", body.path("status").asText());
        assertEquals("claude-mem-java", body.path("service").asText());

        ResponseEntity<String> version = get("/api/version");
        assertEquals(HttpStatus.OK, version.getStatusCode(), version.getBody());
        JsonNode versionBody = json(version);
        // Read from the JAR manifest when packaged, "dev-SNAPSHOT" from classes.
        assertFalse(versionBody.path("version").asText().isBlank());
        assertEquals("claude-mem-java", versionBody.path("service").asText());
        assertFalse(versionBody.path("java").asText().isBlank());
        assertFalse(versionBody.path("springBoot").asText().isBlank());

        ResponseEntity<String> readiness = get("/api/readiness");
        assertEquals(HttpStatus.OK, readiness.getStatusCode(), readiness.getBody());
        assertEquals("ready", json(readiness).path("status").asText());
        assertEquals("ready", json(readiness).path("checks").path("database").asText());
    }

    @Test
    @DisplayName("session start, lookup, user update and delete follow the documented contract")
    void sessionLifecycleFollowsTheDocumentedContract() {
        String throwawayId = uniqueSession("l2-lifecycle");
        ResponseEntity<String> start = post("/api/session/start", json(java.util.Map.of(
                "session_id", throwawayId, "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        JsonNode started = json(start);
        assertEquals(throwawayId, started.path("session_id").asText());
        assertTrue(started.path("updateFiles").isArray(),
                "proxy.js reads data.updateFiles; it must be an array");
        assertFalse(started.has("update_files"));

        ResponseEntity<String> byPath = get("/api/session/" + throwawayId);
        assertEquals(HttpStatus.OK, byPath.getStatusCode(), byPath.getBody());
        assertEquals(throwawayId, json(byPath).path("content_session_id").asText());

        ResponseEntity<String> byQuery = get("/api/session?contentSessionId=" + throwawayId
                + "&platformSource=claude");
        assertEquals(HttpStatus.OK, byQuery.getStatusCode(), byQuery.getBody());

        ResponseEntity<String> patch = patch("/api/session/" + throwawayId + "/user",
                json(java.util.Map.of("user_id", "l2-user")));
        assertEquals(HttpStatus.OK, patch.getStatusCode(), patch.getBody());
        assertEquals("ok", json(patch).path("status").asText());

        ResponseEntity<String> deleteActive = delete("/api/sessions/claude/" + throwawayId);
        // Deleting a session that still accepts writes is refused on purpose:
        // silently dropping an active conversation would lose queued work.
        assertEquals(HttpStatus.CONFLICT, deleteActive.getStatusCode(), deleteActive.getBody());

        String completedId = uniqueSession("l2-completed");
        ResponseEntity<String> imported = post("/api/import/sessions", json(List.of(
                java.util.Map.of(
                        "content_session_id", completedId,
                        "project_path", project,
                        "status", "completed",
                        "started_at_epoch", System.currentTimeMillis()))));
        assertEquals(HttpStatus.OK, imported.getStatusCode(), imported.getBody());
        assertEquals(1, json(imported).path("imported").asInt(), imported.getBody());

        ResponseEntity<String> delete = delete("/api/sessions/claude/" + completedId);
        assertEquals(HttpStatus.NO_CONTENT, delete.getStatusCode(), delete.getBody());
        assertEquals(HttpStatus.NOT_FOUND, get("/api/session/" + completedId).getStatusCode());
    }

    @Test
    @DisplayName("all three ingestion write types persist and validate required fields")
    void ingestionWritesAndValidates() {
        ResponseEntity<String> missingSession = post("/api/ingest/tool-use",
                json(java.util.Map.of("tool_name", "Read")));
        assertEquals(HttpStatus.BAD_REQUEST, missingSession.getStatusCode());
        assertEquals("Missing required field: session_id",
                json(missingSession).path("error").asText());

        ResponseEntity<String> missingTool = post("/api/ingest/tool-use",
                json(java.util.Map.of("session_id", sessionId)));
        assertEquals(HttpStatus.BAD_REQUEST, missingTool.getStatusCode());

        ResponseEntity<String> toolUse = post("/api/ingest/tool-use", json(java.util.Map.of(
                "session_id", sessionId,
                "cwd", project,
                "tool_name", "Read",
                "tool_input", java.util.Map.of("file_path", project + "/README.md"),
                "tool_response", java.util.Map.of("content", "l2 seeded content"))));
        assertEquals(HttpStatus.OK, toolUse.getStatusCode(), toolUse.getBody());
        assertEquals("accepted", json(toolUse).path("status").asText());

        ResponseEntity<String> missingPromptSession = post("/api/ingest/user-prompt",
                json(java.util.Map.of("prompt_text", "no session")));
        assertEquals(HttpStatus.BAD_REQUEST, missingPromptSession.getStatusCode());

        ResponseEntity<String> missingObservationSession = post("/api/ingest/observation",
                json(java.util.Map.of("project_path", project)));
        assertEquals(HttpStatus.BAD_REQUEST, missingObservationSession.getStatusCode());
        ResponseEntity<String> missingProject = post("/api/ingest/observation",
                json(java.util.Map.of("content_session_id", sessionId)));
        assertEquals(HttpStatus.BAD_REQUEST, missingProject.getStatusCode());

        ResponseEntity<String> unknownEnd = post("/api/ingest/session-end",
                json(java.util.Map.of("session_id", "l2-unknown-session")));
        assertEquals(HttpStatus.NOT_FOUND, unknownEnd.getStatusCode());

        assertEquals(2, userPromptRepository
                .countByConversationId(java.util.UUID.fromString(sessionDbId)),
                "the two seeded user prompts must be visible");
    }

    @Test
    @DisplayName("pagination keeps items/hasMore and clamps limit and offset")
    void paginationEnvelopeClampsLimitsAndRejectsMalformedUuids() {
        ResponseEntity<String> firstPage = get("/api/observations?project=" + project
                + "&limit=2&offset=0");
        assertEquals(HttpStatus.OK, firstPage.getStatusCode(), firstPage.getBody());
        JsonNode first = json(firstPage);
        assertEquals(Set.of("items", "hasMore"), new HashSet<>(jsonFieldNames(first)),
                "the viewer reads data.items and data.hasMore");
        assertEquals(2, first.path("items").size());
        assertTrue(first.path("hasMore").asBoolean());

        ResponseEntity<String> secondPage = get("/api/observations?project=" + project
                + "&limit=2&offset=2");
        assertEquals(HttpStatus.OK, secondPage.getStatusCode(), secondPage.getBody());
        assertEquals(1, json(secondPage).path("items").size());
        assertFalse(json(secondPage).path("hasMore").asBoolean());

        ResponseEntity<String> clamped = get("/api/observations?project=" + project
                + "&limit=99999&offset=-5");
        assertEquals(HttpStatus.OK, clamped.getStatusCode(), clamped.getBody());
        JsonNode clampedPage = json(clamped);
        assertTrue(clampedPage.path("items").size() <= 100,
                "limit must be clamped to MAX_PAGE_SIZE");
        // offset=-5 has no echo field, so prove the clamp by requiring the same
        // first page as an explicit offset=0 request.
        ResponseEntity<String> reference = get("/api/observations?project=" + project
                + "&limit=100&offset=0");
        assertEquals(HttpStatus.OK, reference.getStatusCode(), reference.getBody());
        assertEquals(json(reference).path("items"), clampedPage.path("items"),
                "offset must be clamped to 0, not forwarded as -5");

        ResponseEntity<String> zeroLimit = get("/api/observations?project=" + project + "&limit=0");
        assertEquals(HttpStatus.OK, zeroLimit.getStatusCode(), zeroLimit.getBody());
        assertEquals(1, json(zeroLimit).path("items").size(), "limit must be clamped to at least 1");

        ResponseEntity<String> badUuid = get("/api/observations?conversationId=not-a-uuid");
        assertEquals(HttpStatus.BAD_REQUEST, badUuid.getStatusCode());

        ResponseEntity<String> badSentinel = get("/api/observations"
                + "?contentSessionId=manual-memories&standaloneKind=extraction");
        assertEquals(HttpStatus.BAD_REQUEST, badSentinel.getStatusCode());

        for (String path : List.of("/api/summaries?project=" + project,
                "/api/prompts?project=" + project)) {
            ResponseEntity<String> page = get(path);
            assertEquals(HttpStatus.OK, page.getStatusCode(), page.getBody());
            assertEquals(Set.of("items", "hasMore"),
                    new HashSet<>(jsonFieldNames(json(page))), path);
        }
    }

    @Test
    @DisplayName("legacy and unknown paths stay 404")
    void legacyAndUnknownPathsStay404() {
        for (String path : List.of("/api/does-not-exist", "/api/memory/search",
                "/api/memory/sessions", "/api/ingest")) {
            ResponseEntity<String> response = get(path);
            assertTrue(response.getStatusCode() == HttpStatus.NOT_FOUND
                            || response.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED,
                    path + " answered " + response.getStatusCode() + ": " + response.getBody());
        }
        ResponseEntity<String> betterKnown = post("/api/ingest", "{}");
        assertTrue(betterKnown.getStatusCode() == HttpStatus.NOT_FOUND
                        || betterKnown.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED,
                "POST /api/ingest must not silently dispatch: " + betterKnown.getStatusCode()
                        + " " + betterKnown.getBody());
    }

    @Test
    @DisplayName("search and context endpoints answer with documented shapes")
    void searchAndContextAnswerWithDocumentedShapes() {
        ResponseEntity<String> search = get("/api/search?project=" + project
                + "&query=l2-seed-observation-1");
        assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());
        JsonNode body = json(search);
        assertTrue(body.has("observations"));
        assertNotNull(body.path("strategy").asText(null));
        assertFalse(body.path("fell_back").asBoolean());

        ResponseEntity<String> inject = get("/api/context/inject?projects=" + project);
        assertEquals(HttpStatus.OK, inject.getStatusCode(), inject.getBody());
        JsonNode injected = json(inject);
        assertTrue(injected.path("context").isTextual());
        assertTrue(injected.path("updateFiles").isArray());
        assertFalse(injected.has("update_files"));

        ResponseEntity<String> semantic = post("/api/context/semantic",
                json(java.util.Map.of("q", "a query long enough to pass validation",
                        "project", project, "limit", 3)));
        assertEquals(HttpStatus.OK, semantic.getStatusCode(), semantic.getBody());
        assertTrue(json(semantic).has("count"));
    }

    @Test
    @DisplayName("every published operation has a case and answers without a server error")
    void everyPublishedOperationIsExercisedWithoutServerErrors() throws Exception {
        Set<String> published = publishedOperations();
        List<Case> cases = coverageCases();

        Set<String> declared = new TreeSet<>();
        for (Case one : cases) {
            declared.add(one.template());
        }
        assertEquals(published, declared,
                "the L2 case table and the running server's published operations disagree. "
                        + "Add a case for every new operation and delete cases for removed ones.");

        for (Case one : cases) {
            if (one.sse()) {
                assertSseReachable();
                continue;
            }
            ResponseEntity<String> response = exchange(one.method(), one.path(), one.body(),
                    org.springframework.http.MediaType.APPLICATION_JSON);
            int status = response.getStatusCode().value();
            assertTrue(one.allowed().contains(status),
                    one.template() + " answered " + status + " (allowed " + one.allowed()
                            + "): " + response.getBody());
            assertTrue(status < 500, one.template() + " produced a 5xx: " + response.getBody());
        }
    }

    // ---------------------------------------------------------------- coverage

    private Set<String> publishedOperations() throws Exception {
        ResponseEntity<String> response = get("/v3/api-docs");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "springdoc must be reachable");
        JsonNode document = json(response);
        Set<String> operations = new TreeSet<>();
        document.path("paths").fields().forEachRemaining(path -> {
            for (String verb : List.of("get", "post", "put", "delete", "patch")) {
                if (path.getValue().has(verb)) {
                    operations.add(verb.toUpperCase() + " " + path.getKey());
                }
            }
        });
        assertTrue(operations.size() >= 74,
                "only " + operations.size() + " operations were published; the scan is broken");
        return operations;
    }

    private List<Case> coverageCases() {
        String obsId = observationIds.get(0);
        String unknownId = java.util.UUID.randomUUID().toString();
        // State-changing operations get their own conversation so one case cannot
        // push another into a different lifecycle state (e.g. session-end closing
        // the session before a tool-use case runs).
        String completedSession = importCompletedSession(project);
        String toolUseSession = startThrowawaySession(project);
        String sessionEndSession = startThrowawaySession(project);
        String cursorDir = "/tmp/cortexce-l2-cursor-" + java.util.UUID.randomUUID();
        String q = "?project=" + project;

        return List.of(
                new Case("DELETE /api/cursor/register/{projectName}", HttpMethod.DELETE,
                        "/api/cursor/register/" + CURSOR_PROJECT, null, Set.of(200, 404)),
                new Case("DELETE /api/memory/observations/{id}", HttpMethod.DELETE,
                        "/api/memory/observations/" + unknownId, null, Set.of(200, 400, 404)),
                new Case("DELETE /api/observation/{id}", HttpMethod.DELETE,
                        "/api/observation/" + unknownId, null, Set.of(200, 404)),
                new Case("DELETE /api/sessions", HttpMethod.DELETE,
                        "/api/sessions?platformSource=claude&contentSessionId=" + completedSession,
                        null, Set.of(200, 204, 404)),
                new Case("DELETE /api/sessions/{platformSource}/{contentSessionId}", HttpMethod.DELETE,
                        "/api/sessions/claude/" + completedSession, null, Set.of(200, 204, 404)),
                new Case("DELETE /api/summary/{id}", HttpMethod.DELETE,
                        "/api/summary/" + unknownId, null, Set.of(200, 404)),
                new Case("GET /api/context/inject", HttpMethod.GET,
                        "/api/context/inject?projects=" + project, null, Set.of(200)),
                new Case("GET /api/context/preview", HttpMethod.GET,
                        "/api/context/preview?project=" + project, null, Set.of(200)),
                new Case("GET /api/context/prior-messages", HttpMethod.GET,
                        "/api/context/prior-messages?project=" + project, null, Set.of(200)),
                new Case("GET /api/context/recent", HttpMethod.GET,
                        "/api/context/recent?project=" + project + "&limit=3", null, Set.of(200)),
                new Case("GET /api/context/timeline", HttpMethod.GET,
                        "/api/context/timeline?anchor=" + unknownId + "&project=" + project,
                        null, Set.of(200, 400, 404)),
                new Case("GET /api/cursor/projects", HttpMethod.GET,
                        "/api/cursor/projects", null, Set.of(200)),
                new Case("GET /api/cursor/register/{projectName}", HttpMethod.GET,
                        "/api/cursor/register/" + CURSOR_PROJECT, null, Set.of(200, 404)),
                new Case("GET /api/extraction/{templateName}/history", HttpMethod.GET,
                        "/api/extraction/user_preference/history?projectPath=" + project,
                        null, Set.of(200)),
                new Case("GET /api/extraction/{templateName}/latest", HttpMethod.GET,
                        "/api/extraction/user_preference/latest?projectPath=" + project,
                        null, Set.of(200)),
                new Case("GET /api/health", HttpMethod.GET, "/api/health", null, Set.of(200)),
                new Case("GET /api/logs", HttpMethod.GET, "/api/logs?lines=10", null, Set.of(200)),
                new Case("GET /api/memory/quality-distribution", HttpMethod.GET,
                        "/api/memory/quality-distribution" + q, null, Set.of(200)),
                new Case("GET /api/mode", HttpMethod.GET, "/api/mode", null, Set.of(200)),
                new Case("GET /api/mode/concepts", HttpMethod.GET, "/api/mode/concepts", null, Set.of(200)),
                new Case("GET /api/mode/concepts/valid", HttpMethod.GET,
                        "/api/mode/concepts/valid", null, Set.of(200)),
                new Case("GET /api/mode/types", HttpMethod.GET, "/api/mode/types", null, Set.of(200)),
                new Case("GET /api/mode/types/valid", HttpMethod.GET,
                        "/api/mode/types/valid", null, Set.of(200)),
                new Case("GET /api/mode/types/{typeId}/emoji", HttpMethod.GET,
                        "/api/mode/types/discovery/emoji", null, Set.of(200, 404)),
                new Case("GET /api/mode/types/{typeId}/validate", HttpMethod.GET,
                        "/api/mode/types/discovery/validate", null, Set.of(200)),
                new Case("GET /api/modes", HttpMethod.GET, "/api/modes", null, Set.of(200)),
                new Case("GET /api/observations", HttpMethod.GET,
                        "/api/observations" + q + "&limit=2", null, Set.of(200)),
                new Case("GET /api/processing-status", HttpMethod.GET,
                        "/api/processing-status", null, Set.of(200)),
                new Case("GET /api/projects", HttpMethod.GET, "/api/projects", null, Set.of(200)),
                new Case("GET /api/prompts", HttpMethod.GET, "/api/prompts" + q, null, Set.of(200)),
                new Case("GET /api/readiness", HttpMethod.GET, "/api/readiness", null, Set.of(200)),
                new Case("GET /api/search", HttpMethod.GET, "/api/search" + q, null, Set.of(200)),
                new Case("GET /api/search/by-file", HttpMethod.GET,
                        "/api/search/by-file?project=" + project + "&filePath=/tmp/README.md",
                        null, Set.of(200, 400)),
                new Case("GET /api/session", HttpMethod.GET,
                        "/api/session?contentSessionId=" + sessionId + "&platformSource=claude",
                        null, Set.of(200)),
                new Case("GET /api/session/{sessionId}", HttpMethod.GET,
                        "/api/session/" + sessionId, null, Set.of(200)),
                new Case("GET /api/sessions", HttpMethod.GET,
                        "/api/sessions?project=" + project, null, Set.of(200)),
                new Case("GET /api/settings", HttpMethod.GET, "/api/settings", null, Set.of(200)),
                new Case("GET /api/stats", HttpMethod.GET, "/api/stats" + q, null, Set.of(200)),
                new Case("GET /api/summaries", HttpMethod.GET, "/api/summaries" + q, null, Set.of(200)),
                new Case("GET /api/test/all", HttpMethod.GET, "/api/test/all", null, Set.of(200)),
                new Case("GET /api/test/embedding", HttpMethod.GET,
                        "/api/test/embedding", null, Set.of(200)),
                new Case("GET /api/test/llm", HttpMethod.GET, "/api/test/llm", null, Set.of(200)),
                new Case("GET /api/timeline", HttpMethod.GET, "/api/timeline" + q, null, Set.of(200)),
                new Case("GET /api/version", HttpMethod.GET, "/api/version", null, Set.of(200)),
                new Case("GET /stream", HttpMethod.GET, "/stream", null, Set.of(200), true),
                new Case("PATCH /api/memory/observations/{id}", HttpMethod.PATCH,
                        "/api/memory/observations/" + obsId,
                        json(java.util.Map.of("title", "l2-patched-title")),
                        Set.of(200, 400, 404)),
                new Case("PATCH /api/session/user", HttpMethod.PATCH,
                        "/api/session/user?contentSessionId=" + sessionId + "&platformSource=claude",
                        json(java.util.Map.of("user_id", "l2-user")), Set.of(200)),
                new Case("PATCH /api/session/{sessionId}/user", HttpMethod.PATCH,
                        "/api/session/" + sessionId + "/user",
                        json(java.util.Map.of("user_id", "l2-user")), Set.of(200, 404)),
                new Case("POST /api/context/generate", HttpMethod.POST,
                        "/api/context/generate",
                        json(java.util.Map.of("project_path", project)), Set.of(200)),
                new Case("POST /api/context/semantic", HttpMethod.POST,
                        "/api/context/semantic",
                        json(java.util.Map.of("q", "a query long enough to pass validation",
                                "project", project, "limit", 3)), Set.of(200)),
                new Case("POST /api/cursor/context/{projectName}", HttpMethod.POST,
                        "/api/cursor/context/" + CURSOR_PROJECT, "{}", Set.of(200, 404)),
                new Case("POST /api/cursor/context/{projectName}/custom", HttpMethod.POST,
                        "/api/cursor/context/" + CURSOR_PROJECT + "/custom",
                        json(java.util.Map.of("context", "l2 cursor context")), Set.of(200, 404)),
                new Case("POST /api/cursor/register", HttpMethod.POST, "/api/cursor/register",
                        json(java.util.Map.of("projectName", CURSOR_PROJECT,
                                "workspacePath", cursorDir)), Set.of(200)),
                new Case("POST /api/extraction/run", HttpMethod.POST,
                        "/api/extraction/run?projectPath=" + project, null, Set.of(200, 400)),
                new Case("POST /api/import", HttpMethod.POST, "/api/import", "{}", Set.of(200)),
                new Case("POST /api/import/observations", HttpMethod.POST,
                        "/api/import/observations", "[]", Set.of(200)),
                new Case("POST /api/import/prompts", HttpMethod.POST,
                        "/api/import/prompts", "[]", Set.of(200)),
                new Case("POST /api/import/sessions", HttpMethod.POST,
                        "/api/import/sessions", "[]", Set.of(200)),
                new Case("POST /api/import/summaries", HttpMethod.POST,
                        "/api/import/summaries", "[]", Set.of(200)),
                new Case("POST /api/ingest/observation", HttpMethod.POST,
                        "/api/ingest/observation",
                        json(java.util.Map.of("content_session_id", sessionId,
                                "project_path", project, "title", "l2-coverage-observation",
                                "narrative", "l2-coverage narrative", "type", "discovery")),
                        Set.of(200, 400)),
                new Case("POST /api/ingest/session-end", HttpMethod.POST,
                        "/api/ingest/session-end",
                        json(java.util.Map.of("session_id", sessionEndSession,
                                "last_assistant_message", "l2 last message")),
                        Set.of(200, 409)),
                new Case("POST /api/ingest/tool-use", HttpMethod.POST,
                        "/api/ingest/tool-use",
                        json(java.util.Map.of("session_id", toolUseSession, "cwd", project,
                                "tool_name", "Bash", "tool_input",
                                java.util.Map.of("command", "echo l2"),
                                "tool_response", java.util.Map.of("stdout", "l2"))),
                        Set.of(200, 429)),
                new Case("POST /api/ingest/user-prompt", HttpMethod.POST,
                        "/api/ingest/user-prompt",
                        json(java.util.Map.of("session_id", sessionId,
                                "prompt_text", "l2-coverage-prompt", "cwd", project,
                                "prompt_number", 3)), Set.of(200)),
                new Case("POST /api/logs/clear", HttpMethod.POST, "/api/logs/clear", null, Set.of(200)),
                new Case("POST /api/memory/experiences", HttpMethod.POST,
                        "/api/memory/experiences",
                        json(java.util.Map.of("task", "l2 experience task",
                                "project", project, "count", 2,
                                "requiredConcepts", List.of())), Set.of(200, 400)),
                new Case("POST /api/memory/feedback", HttpMethod.POST,
                        "/api/memory/feedback",
                        json(java.util.Map.of("observationId", obsId,
                                "feedbackType", "SUCCESS", "comment", "l2 feedback")),
                        Set.of(200, 400, 404)),
                new Case("POST /api/memory/icl-prompt", HttpMethod.POST,
                        "/api/memory/icl-prompt",
                        json(java.util.Map.of("task", "l2 icl task", "project", project)),
                        Set.of(200, 400)),
                new Case("POST /api/memory/refine", HttpMethod.POST,
                        "/api/memory/refine?project=" + project, null, Set.of(200, 400)),
                new Case("POST /api/modes", HttpMethod.POST, "/api/modes",
                        json(java.util.Map.of("mode", "code")), Set.of(200, 400)),
                new Case("POST /api/observations/batch", HttpMethod.POST,
                        "/api/observations/batch",
                        json(java.util.Map.of("ids", List.of(obsId))), Set.of(200)),
                new Case("POST /api/sdk-sessions/batch", HttpMethod.POST,
                        "/api/sdk-sessions/batch",
                        json(java.util.Map.of("contentSessionIds", List.of(sessionId))), Set.of(200)),
                new Case("POST /api/session/start", HttpMethod.POST, "/api/session/start",
                        json(java.util.Map.of("session_id", uniqueSession("l2-case"),
                                "project_path", project)), Set.of(200)),
                new Case("POST /api/settings", HttpMethod.POST, "/api/settings",
                        json(java.util.Map.of("CLAUDE_MEM_MODE", "code")), Set.of(200)),
                new Case("PUT /api/mode", HttpMethod.PUT, "/api/mode",
                        json(java.util.Map.of("mode_id", "code")), Set.of(200, 400))
        );
    }

    private String startThrowawaySession(String project) {
        String id = uniqueSession("l2-case");
        ResponseEntity<String> response = post("/api/session/start",
                json(java.util.Map.of("session_id", id, "project_path", project)));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return id;
    }

    private String importCompletedSession(String project) {
        String id = uniqueSession("l2-completed");
        ResponseEntity<String> response = post("/api/import/sessions", json(List.of(
                java.util.Map.of(
                        "content_session_id", id,
                        "project_path", project,
                        "status", "completed",
                        "started_at_epoch", System.currentTimeMillis()))));
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertEquals(1, json(response).path("imported").asInt(), response.getBody());
        return id;
    }

    private void assertSseReachable() throws Exception {
        try (SseCollector collector = new SseCollector()) {
            collector.start();
            JsonNode initial = collector.awaitType("initial_load", Duration.ofSeconds(10));
            assertTrue(initial.path("projects").isArray());
            collector.awaitType("processing_status", Duration.ofSeconds(10));
        }
    }

    private static List<String> jsonFieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private record Case(String template, HttpMethod method, String path, String body,
                        Set<Integer> allowed, boolean sse) {
        Case(String template, HttpMethod method, String path, String body, Set<Integer> allowed) {
            this(template, method, path, body, allowed, false);
        }
    }
}
