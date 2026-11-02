package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.mcp.ClaudeMemMcpTools;
import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L3 — acceptance layer.
 *
 * <p>Each assertion below is the executable form of a capability that the project
 * documents, so a documentation claim and its test cannot drift apart silently.
 * Sources are named per test:
 * <ul>
 *   <li>{@code docs/API.md} / {@code docs/API-zh-CN.md} — REST surface.</li>
 *   <li>{@code AGENTS.md} — lifecycle hooks and the WebUI hard contracts.</li>
 *   <li>{@code docs/ARCHITECTURE.md} — retrieval branches and MCP tools.</li>
 * </ul>
 *
 * <p>The proxy forwarding contract is not exercised here: it is a Node.js
 * component ({@code proxy/wrapper.js}) and is covered by {@code node --test}
 * under {@code proxy/}, which {@code scripts/verify-local.sh} runs as part of
 * this layer.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "claudemem.sse.timeout-ms=30000",
                // This layer proves hook → queue → observation wiring. The offline stand-in
                // answers with fixed facts that do not quote the tool trace, so the
                // trace-grounding guard (covered by ExtractionValidationServiceTest) is off here.
                "claudemem.extraction.validation.enabled=false",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L3AcceptanceTest extends HttpLayerTestSupport {

    @Autowired ClaudeMemMcpTools mcpTools;

    private String project;
    private String sessionId;
    private String sessionDbId;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @BeforeEach
    void startConversation() {
        project = uniqueProject("l3");
        sessionId = uniqueSession("l3");
        ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                "session_id", sessionId, "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        sessionDbId = json(start).path("session_db_id").asText();
        assertFalse(sessionDbId.isBlank());
    }

    @Test
    @DisplayName("lifecycle hooks reach every documented write stage")
    void hookLifecycleReachesEveryDocumentedWriteStage() throws Exception {
        // Hook 2 — UserPromptSubmit (AGENTS.md "5 Lifecycle Hooks"), API.md
        // POST /api/ingest/user-prompt. The event stream is the viewer's feed.
        try (SseCollector collector = new SseCollector()) {
            collector.start();
            collector.awaitType("initial_load", Duration.ofSeconds(10));
            collector.awaitType("processing_status", Duration.ofSeconds(10));

            ResponseEntity<String> prompt = post("/api/ingest/user-prompt", json(Map.of(
                    "session_id", sessionId,
                    "prompt_text", "l3-hook-user-prompt",
                    "cwd", project,
                    "prompt_number", 1)));
            assertEquals(HttpStatus.OK, prompt.getStatusCode(), prompt.getBody());
            collector.awaitTypeContaining("new_prompt", "l3-hook-user-prompt",
                    Duration.ofSeconds(10));
        }
        assertTrue(userPromptRepository
                .findByConversationIdOrderByPromptNumberAsc(java.util.UUID.fromString(sessionDbId))
                .stream().anyMatch(p -> "l3-hook-user-prompt".equals(p.getPromptText())),
                "UserPromptSubmit must persist the prompt before acknowledging");

        // Hook 3 — PostToolUse (AGENTS.md). tool-use is queue admission, not a
        // synchronous observation: the observation row appears through the async
        // worker, which is the documented pipeline.
        ResponseEntity<String> toolUse = post("/api/ingest/tool-use", json(Map.of(
                "session_id", sessionId,
                "cwd", project,
                "tool_name", "Read",
                "tool_input", Map.of("file_path", project + "/README.md"),
                "tool_response", Map.of("content", "l3 hook content"))));
        assertEquals(HttpStatus.OK, toolUse.getStatusCode(), toolUse.getBody());
        assertEquals("accepted", json(toolUse).path("status").asText());
        awaitUntil("PostToolUse observation is persisted",
                Duration.ofSeconds(30), () -> observationRepository
                        .findByConversationIdOrderByCreatedAtEpochAsc(
                                java.util.UUID.fromString(sessionDbId))
                        .stream()
                        .anyMatch(o -> OfflineAiTestModels.OBSERVATION_TITLE.equals(o.getTitle())));

        // Hook 4 — Summary (AGENTS.md), POST /api/ingest/session-end. The job row
        // is written before the event is published; that is the durable hand-off.
        ResponseEntity<String> end = post("/api/ingest/session-end", json(Map.of(
                "session_id", sessionId,
                "last_assistant_message", "l3 last assistant message")));
        assertEquals(HttpStatus.OK, end.getStatusCode(), end.getBody());
        awaitUntil("SessionEnd enqueues a summary job", Duration.ofSeconds(10),
                () -> jdbc.queryForObject("""
                        SELECT count(*) FROM mem_pending_messages
                         WHERE session_db_id = ? AND message_type = 'summarize'
                        """, Integer.class, java.util.UUID.fromString(sessionDbId)) >= 1);
    }

    @Test
    @DisplayName("ingest observation is a synchronous write with the documented shape")
    void ingestObservationIsSynchronous() {
        ResponseEntity<String> created = post("/api/ingest/observation", json(Map.of(
                "content_session_id", sessionId,
                "project_path", project,
                "title", "l3-acceptance-observation",
                "narrative", "l3 acceptance narrative",
                "type", "discovery",
                "facts", List.of("l3-fact"))));
        assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());
        JsonNode body = json(created);
        assertEquals("l3-acceptance-observation", body.path("title").asText());
        assertEquals("discovery", body.path("type").asText());
        assertTrue(observationRepository.findById(
                java.util.UUID.fromString(body.path("id").asText())).isPresent(),
                "the response id must address a persisted row");
    }

    @Test
    @DisplayName("retrieval reports the hybrid strategy when an embedding is available")
    void retrievalReportsHybridStrategy() {
        ResponseEntity<String> created = post("/api/ingest/observation", json(Map.of(
                "content_session_id", sessionId,
                "project_path", project,
                "title", "l3-hybrid-observation",
                "narrative", "l3 hybrid retrieval narrative about deterministic testing",
                "type", "discovery")));
        assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());

        // docs/ARCHITECTURE.md: path 2 = query + embedding → hybrid; fell_back
        // stays false. The offline EmbeddingModel makes this deterministic.
        ResponseEntity<String> search = get("/api/search?project=" + project
                + "&query=deterministic%20testing");
        assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());
        JsonNode body = json(search);
        assertEquals("hybrid", body.path("strategy").asText(), body.toString());
        assertFalse(body.path("fell_back").asBoolean(), body.toString());
        assertTrue(body.path("observations").isArray());
        assertTrue(body.path("observations").toString().contains("l3-hybrid-observation"));
    }

    @Test
    @DisplayName("context injection and session start keep the updateFiles contract")
    void contextInjectionKeepsUpdateFilesContract() {
        // AGENTS.md WebUI hard contract: proxy/proxy.js reads data.updateFiles.
        ResponseEntity<String> inject = get("/api/context/inject?projects=" + project);
        assertEquals(HttpStatus.OK, inject.getStatusCode(), inject.getBody());
        JsonNode body = json(inject);
        assertTrue(body.path("context").isTextual());
        assertTrue(body.path("updateFiles").isArray());
        assertFalse(body.has("update_files"));

        ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                "session_id", uniqueSession("l3-start"), "project_path", project)));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        assertTrue(json(start).path("updateFiles").isArray());
        assertFalse(json(start).has("update_files"));
    }

    @Test
    @DisplayName("settings and modes expose the documented field sets")
    void settingsAndModesExposeDocumentedFields() {
        // AGENTS.md: /api/settings is a WebUI hard contract on CLAUDE_MEM_* keys.
        ResponseEntity<String> settings = get("/api/settings");
        assertEquals(HttpStatus.OK, settings.getStatusCode(), settings.getBody());
        JsonNode settingsBody = json(settings);
        assertTrue(settingsBody.fieldNames().hasNext(), "settings must not be empty");
        assertTrue(streamFieldNames(settingsBody).anyMatch(name -> name.startsWith("CLAUDE_MEM_")),
                "settings must expose CLAUDE_MEM_* keys: " + settingsBody);
        assertFalse(settingsBody.path("modeName").asText().isBlank());

        // AGENTS.md notes observation_types/observation_concepts are safe snake_case
        // fields the WebUI reads from ModeManager, not from this response.
        ResponseEntity<String> modes = get("/api/modes");
        assertEquals(HttpStatus.OK, modes.getStatusCode(), modes.getBody());
        JsonNode firstMode = json(modes).isArray() ? json(modes).get(0) : json(modes);
        assertFalse(firstMode.path("id").asText().isBlank());
        assertTrue(firstMode.path("observation_types").isArray());
        assertTrue(firstMode.path("observation_concepts").isArray());

        ResponseEntity<String> putMode = exchange(org.springframework.http.HttpMethod.PUT,
                "/api/mode", json(Map.of("mode_id", "code")),
                org.springframework.http.MediaType.APPLICATION_JSON);
        assertEquals(HttpStatus.OK, putMode.getStatusCode(), putMode.getBody());
        assertEquals("code", json(putMode).path("mode_id").asText());
    }

    @Test
    @DisplayName("MCP exposes the documented tool catalog and one call succeeds")
    void mcpToolCatalogAndOneCall() {
        // docs/ARCHITECTURE.md documents the search/timeline/get_observations/
        // save_memory/recent MCP tools. The server runs in-process, so no external
        // process is needed to verify the catalog.
        Set<String> catalog = new TreeSet<>();
        for (Method method : ClaudeMemMcpTools.class.getDeclaredMethods()) {
            McpTool tool = method.getAnnotation(McpTool.class);
            if (tool != null) {
                catalog.add(tool.name());
            }
        }
        assertEquals(Set.of("search", "timeline", "get_observations", "save_memory", "recent"),
                catalog);

        ResponseEntity<String> created = post("/api/ingest/observation", json(Map.of(
                "content_session_id", sessionId,
                "project_path", project,
                "title", "l3-mcp-observation",
                "narrative", "l3 mcp tool call narrative",
                "type", "discovery")));
        assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());

        Map<String, Object> result = mcpTools.search(null, project, 5, null, null, null, null);
        assertTrue(result.containsKey("observations"));
        assertTrue(result.containsKey("strategy"));
        assertEquals(false, result.get("fell_back"));
        assertTrue(((List<?>) result.get("observations")).size() >= 1,
                "the MCP search call must see the observation written through REST");
    }

    private static java.util.stream.Stream<String> streamFieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.stream();
    }
}
