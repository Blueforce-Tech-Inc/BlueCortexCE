package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L4 — end-to-end layer.
 *
 * <p>One real user path, executed against a freshly started application on its
 * own port and the dedicated rehearsal database:
 * session start → user prompt → tool-use (observation) → search → context
 * inject → session end → summary. Every wait is condition-based polling of the
 * database or the event stream, so a slow worker reports a real failure instead
 * of being hidden by a fixed sleep.
 *
 * <p>The model edge is the offline stand-in imported below, so this layer never
 * needs a key or the network. There is deliberately no real-provider variant
 * here: spending a provider key belongs in a separately triggered manual smoke,
 * not in a gate that CI runs on every push.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "claudemem.sse.timeout-ms=30000",
                // Wiring test: the offline stand-in's fixed facts do not quote the tool trace,
                // so the trace-grounding guard (unit-tested separately) is off in this layer.
                "claudemem.extraction.validation.enabled=false",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L4EndToEndTest extends HttpLayerTestSupport {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("session start through summary leaves the documented terminal state")
    void fullUserPathReachesSummary() throws Exception {
        String project = uniqueProject("l4");
        String sessionId = uniqueSession("l4");

        try (SseCollector collector = new SseCollector()) {
            collector.start();
            collector.awaitType("initial_load", Duration.ofSeconds(10));

            // 1. SessionStart hook.
            ResponseEntity<String> start = post("/api/session/start", json(Map.of(
                    "session_id", sessionId, "project_path", project)));
            assertEquals(HttpStatus.OK, start.getStatusCode(), start.getBody());
            UUID conversationId = UUID.fromString(json(start).path("session_db_id").asText());

            // 2. UserPromptSubmit hook.
            ResponseEntity<String> prompt = post("/api/ingest/user-prompt", json(Map.of(
                    "session_id", sessionId,
                    "prompt_text", "l4 end to end prompt",
                    "cwd", project,
                    "prompt_number", 1)));
            assertEquals(HttpStatus.OK, prompt.getStatusCode(), prompt.getBody());
            collector.awaitTypeContaining("new_prompt", "l4 end to end prompt",
                    Duration.ofSeconds(10));

            // 3. PostToolUse hook → the async worker extracts an observation.
            ResponseEntity<String> toolUse = post("/api/ingest/tool-use", json(Map.of(
                    "session_id", sessionId,
                    "cwd", project,
                    "tool_name", "Bash",
                    "tool_input", Map.of("command", "printf l4"),
                    "tool_response", Map.of("stdout", "l4"))));
            assertEquals(HttpStatus.OK, toolUse.getStatusCode(), toolUse.getBody());
            assertEquals("accepted", json(toolUse).path("status").asText());

            ObservationEntity observation = awaitValue("observation row persisted",
                    Duration.ofSeconds(30), () -> observationRepository
                            .findByConversationIdOrderByCreatedAtEpochAsc(conversationId)
                            .stream()
                            .filter(o -> OfflineAiTestModels.OBSERVATION_TITLE.equals(o.getTitle()))
                            .findFirst().orElse(null));
            assertTrue(observation.getContent()
                    .contains(OfflineAiTestModels.OBSERVATION_NARRATIVE));

            // 4. Retrieval branch: query + embedding → hybrid, no fallback.
            ResponseEntity<String> search = get("/api/search?project=" + project
                    + "&query=offline deterministic observation");
            assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());
            JsonNode searchBody = json(search);
            assertEquals("hybrid", searchBody.path("strategy").asText(), searchBody.toString());
            assertFalse(searchBody.path("fell_back").asBoolean());
            assertTrue(searchBody.path("observations").toString()
                    .contains(OfflineAiTestModels.OBSERVATION_TITLE));

            // 5. Context injection (proxy contract) and the recent feed must see
            //    the observation that was just written.
            ResponseEntity<String> inject = get("/api/context/inject?projects=" + project);
            assertEquals(HttpStatus.OK, inject.getStatusCode(), inject.getBody());
            assertTrue(json(inject).path("updateFiles").isArray());
            ResponseEntity<String> listed = get("/api/observations?project=" + project
                    + "&limit=5");
            assertEquals(HttpStatus.OK, listed.getStatusCode(), listed.getBody());
            assertTrue(json(listed).path("items").toString()
                    .contains(OfflineAiTestModels.OBSERVATION_TITLE));

            // 6. SessionEnd hook → durable summary job → summary row.
            ResponseEntity<String> end = post("/api/ingest/session-end", json(Map.of(
                    "session_id", sessionId,
                    "last_assistant_message", "l4 last assistant message")));
            assertEquals(HttpStatus.OK, end.getStatusCode(), end.getBody());

            SummaryEntity summary = awaitValue("summary row persisted",
                    Duration.ofSeconds(30), () -> summaryRepository
                            .findByConversationId(conversationId).stream()
                            .findFirst().orElse(null));
            assertTrue(summary.getLearned() != null
                    && summary.getLearned().contains(OfflineAiTestModels.SUMMARY_LEARNED),
                    "summary payload must come from the model answer: " + summary.getLearned());
            collector.awaitType("new_summary", Duration.ofSeconds(10));
        }

        // 7. Terminal database state. The summary row is persisted before the
        //    session flips from "summarizing" to "completed", so wait for the
        //    terminal status instead of sampling it once.
        SessionEntity session = awaitValue("session reaches completed",
                Duration.ofSeconds(30), () -> sessionRepository
                        .findByPlatformSourceAndContentSessionId("claude", sessionId)
                        .filter(s -> "completed".equals(s.getStatus()))
                        .orElse(null));
        assertEquals("completed", session.getStatus());
        assertEquals(1, observationRepository
                .findByConversationIdOrderByCreatedAtEpochAsc(session.getId()).size());
        assertEquals(1, userPromptRepository.countByConversationId(session.getId()));
        assertEquals(1, summaryRepository.findByConversationId(session.getId()).size());
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM mem_pending_messages
                 WHERE session_db_id = ? AND message_type = 'observation'
                """, Integer.class, session.getId()) >= 1);
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM mem_pending_messages
                 WHERE session_db_id = ? AND message_type = 'observation'
                   AND status IN ('pending', 'processing')
                """, Integer.class, session.getId()),
                "no queued observation job may be left behind");
    }
}
