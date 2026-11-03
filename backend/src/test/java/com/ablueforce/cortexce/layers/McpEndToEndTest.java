package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP end-to-end: a real MCP client completes the SSE handshake against the
 * running server, lists the tool catalogue, and calls every tool through
 * JSON-RPC. Nothing is invoked through the Spring bean directly, so a broken
 * transport, schema or argument binding fails here.
 *
 * <p>Seeding uses the documented REST hooks with the offline model stand-in,
 * exactly like {@link L4EndToEndTest}; the client half is the real MCP SDK.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "claudemem.sse.timeout-ms=30000",
                "claudemem.extraction.validation.enabled=false",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN",
                "logging.level.io.modelcontextprotocol=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class McpEndToEndTest extends HttpLayerTestSupport {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("real MCP client handshake, tool catalogue and every tool call")
    void everyToolAnswersThroughTheProtocol() throws Exception {
        String project = uniqueProject("mcp");
        String sessionId = uniqueSession("mcp");

        Map<String, Object> startBody = new LinkedHashMap<>();
        startBody.put("session_id", sessionId);
        startBody.put("project_path", project);
        ResponseEntity<String> start = post("/api/session/start", json(startBody));
        assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
        UUID conversationId = UUID.fromString(json(start).path("session_db_id").asText());

        Map<String, Object> promptBody = new LinkedHashMap<>();
        promptBody.put("session_id", sessionId);
        promptBody.put("prompt_text", "mcp end to end prompt");
        promptBody.put("cwd", project);
        promptBody.put("prompt_number", 1);
        post("/api/ingest/user-prompt", json(promptBody)).getBody();

        Map<String, Object> toolUseBody = new LinkedHashMap<>();
        toolUseBody.put("session_id", sessionId);
        toolUseBody.put("cwd", project);
        toolUseBody.put("tool_name", "Bash");
        toolUseBody.put("tool_input", Map.of("command", "printf mcp"));
        toolUseBody.put("tool_response", Map.of("stdout", "mcp"));
        ResponseEntity<String> ingress = post("/api/ingest/tool-use", json(toolUseBody));
        assertEquals(HttpStatus.OK, ingress.getStatusCode(), ingress.getBody());

        var observation = awaitValue("observation row persisted", Duration.ofSeconds(30),
                () -> observationRepository
                        .findByConversationIdOrderByCreatedAtEpochAsc(conversationId).stream()
                        .filter(o -> OfflineAiTestModels.OBSERVATION_TITLE.equals(o.getTitle()))
                        .findFirst().orElse(null));

        ResponseEntity<String> end = post("/api/ingest/session-end", json(Map.of(
                "session_id", sessionId,
                "last_assistant_message", "mcp last assistant message")));
        assertEquals(HttpStatus.OK, end.getStatusCode(), end.getBody());
        awaitValue("summary row persisted", Duration.ofSeconds(30),
                () -> summaryRepository.findByConversationId(conversationId).stream()
                        .findFirst().orElse(null));

        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder("http://localhost:" + port)
                .sseEndpoint("/sse")
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build()) {
            client.initialize();
            assertTrue(client.isInitialized(), "the SSE handshake must complete");
            assertThat(client.getServerInfo().name()).isEqualTo("claude-mem-mcp-server");

            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name)
                    .contains("search", "timeline", "get_observations", "save_memory", "recent");
            McpSchema.Tool searchTool = tools.stream()
                    .filter(tool -> "search".equals(tool.name()))
                    .findFirst().orElseThrow();
            assertThat(searchTool.inputSchema().required()).contains("project");

            String searchText = text(call(client, "search", Map.of(
                    "project", project,
                    "query", "offline deterministic observation",
                    "limit", 5)));
            assertThat(searchText).contains(OfflineAiTestModels.OBSERVATION_TITLE);

            String timelineText = text(call(client, "timeline", Map.of(
                    "project", project,
                    "anchorId", observation.getId().toString(),
                    "depthBefore", 2,
                    "depthAfter", 2)));
            assertThat(timelineText).contains(OfflineAiTestModels.OBSERVATION_TITLE);

            String detailText = text(call(client, "get_observations", Map.of(
                    "ids", List.of(observation.getId().toString()),
                    "project", project)));
            assertThat(detailText).contains(OfflineAiTestModels.OBSERVATION_TITLE)
                    .contains(OfflineAiTestModels.OBSERVATION_NARRATIVE);

            String saveText = text(call(client, "save_memory", Map.of(
                    "text", "MCP end-to-end saved memory about the rehearsal database",
                    "title", "MCP end-to-end manual memory",
                    "project", project)));
            assertThat(saveText).isNotBlank();

            var saved = awaitValue("saved memory is persisted", Duration.ofSeconds(10),
                    () -> observationRepository
                            .findByProjectPathOrderByCreatedAtEpochDesc(project).stream()
                            .filter(o -> "MCP end-to-end manual memory".equals(o.getTitle()))
                            .findFirst().orElse(null));
            assertThat(saved.getSource()).isEqualTo("manual");
            assertThat(saved.getEmbeddingState()).isNotEqualTo("failed");

            String savedSearch = text(call(client, "search", Map.of(
                    "project", project, "query", "saved memory rehearsal database")));
            assertThat(savedSearch).contains("MCP end-to-end manual memory");

            String recentText = text(call(client, "recent", Map.of("project", project, "limit", 3)));
            assertThat(recentText).contains(OfflineAiTestModels.SUMMARY_LEARNED);
        }
    }

    private McpSchema.CallToolResult call(McpSyncClient client, String name,
                                          Map<String, Object> arguments) {
        McpSchema.CallToolResult result =
                client.callTool(new McpSchema.CallToolRequest(name, arguments));
        assertThat(result.isError()).as(name + " must not answer with an MCP error").isFalse();
        assertThat(result.content()).as(name + " must return content").isNotEmpty();
        return result;
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }
}
