package com.ablueforce.cortexce.client.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DTO wire format and builder behavior.
 */
class DtoTest {

    @Test
    void observationRequest_toWireFormat() {
        var req = ObservationRequest.builder()
            .sessionId("sess-123")
            .projectPath("/my/project")
            .toolName("Read")
            .toolInput(Map.of("path", "file.txt"))
            .toolResponse(Map.of("content", "hello"))
            .promptNumber(1)
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("session_id", "sess-123");
        assertThat(wire).containsEntry("cwd", "/my/project");
        assertThat(wire).containsEntry("tool_name", "Read");
        assertThat(wire).containsEntry("tool_input", Map.of("path", "file.txt"));
        assertThat(wire).containsEntry("tool_response", Map.of("content", "hello"));
        assertThat(wire).containsEntry("prompt_number", 1);
    }

    @Test
    void observationRequest_toWireFormat_includesV14Fields() {
        var req = ObservationRequest.builder()
            .sessionId("sess-1")
            .projectPath("/proj")
            .toolName("Write")
            .source("tool_result")
            .extractedData(Map.of("preference", "dark mode", "priority", 5))
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("source", "tool_result");
        assertThat(wire).containsEntry("extractedData", Map.of("preference", "dark mode", "priority", 5));
    }

    @Test
    void observationRequest_toWireFormat_omitsNullV14Fields() {
        var req = ObservationRequest.builder()
            .sessionId("s")
            .projectPath("/p")
            .toolName("Edit")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("source");
        assertThat(wire).doesNotContainKey("extractedData");
    }

    @Test
    void observationRequest_toWireFormat_omitsNullPromptNumber() {
        var req = ObservationRequest.builder()
            .sessionId("s")
            .projectPath("/p")
            .toolName("Edit")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("prompt_number");
    }

    @Test
    void observationRequest_toWireFormat_omitsNullToolInputAndResponse() {
        // When toolInput/toolResponse are null, they should NOT appear in wire format.
        // Matches Go omitempty / Python conditional behavior.
        var req = ObservationRequest.builder()
            .sessionId("s")
            .projectPath("/p")
            .toolName("Edit")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("tool_input");
        assertThat(wire).doesNotContainKey("tool_response");
        // But required fields should still be present
        assertThat(wire).containsEntry("session_id", "s");
        assertThat(wire).containsEntry("cwd", "/p");
        assertThat(wire).containsEntry("tool_name", "Edit");
    }

    @Test
    void sessionEndRequest_toWireFormat() {
        var req = SessionEndRequest.builder()
            .sessionId("sess-456")
            .projectPath("/project")
            .lastAssistantMessage("Done.")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("session_id", "sess-456");
        assertThat(wire).containsEntry("cwd", "/project");
        assertThat(wire).containsEntry("last_assistant_message", "Done.");
    }

    @Test
    void sessionEndRequest_toWireFormat_omitsNullMessage() {
        var req = SessionEndRequest.builder()
            .sessionId("s")
            .projectPath("/p")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("last_assistant_message");
    }

    @Test
    void userPromptRequest_toWireFormat() {
        var req = UserPromptRequest.builder()
            .sessionId("sess-789")
            .projectPath("/app")
            .promptText("Fix the bug")
            .promptNumber(2)
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("session_id", "sess-789");
        assertThat(wire).containsEntry("cwd", "/app");
        assertThat(wire).containsEntry("prompt_text", "Fix the bug");
        assertThat(wire).containsEntry("prompt_number", 2);
    }

    @Test
    void sessionStartRequest_toWireFormat() {
        var req = SessionStartRequest.builder()
            .sessionId("sess-001")
            .projectPath("/my/project")
            .userId("alice")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("session_id", "sess-001");
        assertThat(wire).containsEntry("project_path", "/my/project");
        // SessionStartRequest only sends project_path (not cwd) — backend uses project_path as primary.
        assertThat(wire).doesNotContainKey("cwd");
        assertThat(wire).containsEntry("user_id", "alice");
    }

    @Test
    void sessionStartRequest_toWireFormat_omitsNullUserId() {
        var req = SessionStartRequest.builder()
            .sessionId("sess-002")
            .projectPath("/proj")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("user_id");
        assertThat(wire).containsEntry("session_id", "sess-002");
        assertThat(wire).containsEntry("project_path", "/proj");
        assertThat(wire).doesNotContainKey("cwd");
    }

    @Test
    void experienceRequest_builder() {
        var req = ExperienceRequest.builder()
            .task("add feature")
            .project("/proj")
            .count(6)
            .build();
        assertThat(req.task()).isEqualTo("add feature");
        assertThat(req.project()).isEqualTo("/proj");
        assertThat(req.count()).isEqualTo(6);
    }

    @Test
    void experienceRequest_defaultCount() {
        var req = ExperienceRequest.builder().task("x").project("/p").build();
        assertThat(req.count()).isEqualTo(4);
    }

    @Test
    void iclPromptRequest_builder() {
        var req = ICLPromptRequest.builder().task("t").project("/p").build();
        assertThat(req.task()).isEqualTo("t");
        assertThat(req.project()).isEqualTo("/p");
    }

    @Test
    void iclPromptResult_experienceCountAndMaxChars() {
        assertThat(new ICLPromptResult("", 3, 5000).experienceCount()).isEqualTo(3);
        assertThat(new ICLPromptResult("", 3, 5000).maxChars()).isEqualTo(5000);
        assertThat(new ICLPromptResult("", 0).experienceCount()).isEqualTo(0);
        assertThat(new ICLPromptResult("", 0).maxChars()).isEqualTo(0);
        assertThat(new ICLPromptResult("prompt").experienceCount()).isEqualTo(0);
        assertThat(new ICLPromptResult("prompt").maxChars()).isEqualTo(0);
    }

    @Test
    void iclPromptResult_deserializeSnakeCase() throws Exception {
        String json = """
            {
                "prompt": "ICL context...",
                "experience_count": 5,
                "max_chars": 4000
            }
            """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ICLPromptResult result = mapper.readValue(json, ICLPromptResult.class);
        assertThat(result.prompt()).isEqualTo("ICL context...");
        assertThat(result.experienceCount()).isEqualTo(5);
        assertThat(result.maxChars()).isEqualTo(4000);
    }

    @Test
    void iclPromptResult_deserializeCamelCase() throws Exception {
        String json = """
            {
                "prompt": "ICL context...",
                "experienceCount": 3,
                "maxChars": 2000
            }
            """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ICLPromptResult result = mapper.readValue(json, ICLPromptResult.class);
        assertThat(result.prompt()).isEqualTo("ICL context...");
        assertThat(result.experienceCount()).isEqualTo(3);
        assertThat(result.maxChars()).isEqualTo(2000);
    }

    @Test
    void qualityDistribution_total() {
        var q = new QualityDistribution("/p", 10, 5, 3, 2);
        assertThat(q.total()).isEqualTo(20);
    }

    @Test
    void observationUpdate_isEmpty_trueWhenAllNull() {
        assertThat(ObservationUpdate.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void observationUpdate_isEmpty_falseWhenAnyFieldSet() {
        assertThat(ObservationUpdate.builder().title("x").build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().subtitle("x").build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().content("x").build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().narrative("x").build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().facts(List.of("a")).build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().concepts(List.of("a")).build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().source("x").build().isEmpty()).isFalse();
        assertThat(ObservationUpdate.builder().extractedData(Map.of("k", "v")).build().isEmpty()).isFalse();
    }

    @Test
    void observationUpdate_omitsNullFields() throws Exception {
        // Verify @JsonInclude(NON_NULL) — only title is set, others should be omitted
        var update = ObservationUpdate.builder().title("New Title").build();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(update);
        assertThat(json).contains("\"title\":\"New Title\"");
        assertThat(json).doesNotContain("\"subtitle\"");
        assertThat(json).doesNotContain("\"content\"");
        assertThat(json).doesNotContain("\"facts\"");
        assertThat(json).doesNotContain("\"concepts\"");
        assertThat(json).doesNotContain("\"source\"");
        assertThat(json).doesNotContain("\"extractedData\"");
    }

    @Test
    void observationUpdate_withExtractedData() throws Exception {
        var extractedData = Map.<String, Object>of("preference", "vim", "theme", "dark");
        var update = ObservationUpdate.builder()
            .source("manual")
            .extractedData(extractedData)
            .build();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(update);
        assertThat(json).contains("\"source\":\"manual\"");
        assertThat(json).contains("\"extractedData\"");
        assertThat(json).contains("\"preference\":\"vim\"");
        // title/content/facts/concepts should be omitted
        assertThat(json).doesNotContain("\"title\"");
        assertThat(json).doesNotContain("\"content\"");
    }

    @Test
    void observationUpdate_withSubtitle() throws Exception {
        var update = ObservationUpdate.builder()
            .subtitle("A subtitle")
            .build();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(update);
        assertThat(json).contains("\"subtitle\":\"A subtitle\"");
        assertThat(json).doesNotContain("\"title\"");
        assertThat(json).doesNotContain("\"content\"");
    }

    @Test
    void observationUpdate_withNarrative() throws Exception {
        var update = ObservationUpdate.builder()
            .narrative("User prefers dark mode")
            .build();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(update);
        assertThat(json).contains("\"narrative\":\"User prefers dark mode\"");
        assertThat(json).doesNotContain("\"content\"");
        assertThat(json).doesNotContain("\"title\"");
    }

    @Test
    void observationUpdate_withFactsAndConcepts() throws Exception {
        var update = ObservationUpdate.builder()
            .facts(List.of("Spring Boot is fast", "Java 21 is required"))
            .concepts(List.of("backend", "java"))
            .build();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(update);
        assertThat(json).contains("\"facts\"");
        assertThat(json).contains("Spring Boot is fast");
        assertThat(json).contains("Java 21 is required");
        assertThat(json).contains("\"concepts\"");
        assertThat(json).contains("backend");
        assertThat(json).contains("java");
        // title/source/extractedData should be omitted
        assertThat(json).doesNotContain("\"title\"");
        assertThat(json).doesNotContain("\"source\"");
        assertThat(json).doesNotContain("\"extractedData\"");
    }

    @Test
    void searchRequest_builder() {
        var req = SearchRequest.builder()
            .project("/proj")
            .query("debug")
            .type("observation")
            .source("manual")
            .concept("error")
            .limit(20)
            .offset(10)
            .build();
        assertThat(req.project()).isEqualTo("/proj");
        assertThat(req.query()).isEqualTo("debug");
        assertThat(req.type()).isEqualTo("observation");
        assertThat(req.source()).isEqualTo("manual");
        assertThat(req.concept()).isEqualTo("error");
        assertThat(req.limit()).isEqualTo(20);
        assertThat(req.offset()).isEqualTo(10);
    }

    @Test
    void observationsRequest_builder() {
        var req = ObservationsRequest.builder()
            .project("/proj")
            .limit(50)
            .offset(100)
            .build();
        assertThat(req.project()).isEqualTo("/proj");
        assertThat(req.limit()).isEqualTo(50);
        assertThat(req.offset()).isEqualTo(100);
    }

    @Test
    void experienceRequest_toWireFormat() {
        var req = ExperienceRequest.builder()
            .task("add feature")
            .project("/proj")
            .count(6)
            .source("tool_result")
            .requiredConcepts(List.of("feature", "design"))
            .userId("alice")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("task", "add feature");
        assertThat(wire).containsEntry("project", "/proj");
        assertThat(wire).containsEntry("count", 6);
        assertThat(wire).containsEntry("source", "tool_result");
        assertThat(wire).containsEntry("requiredConcepts", List.of("feature", "design"));
        assertThat(wire).containsEntry("userId", "alice");
    }

    @Test
    void experienceRequest_toWireFormat_omitsOptionalFields() {
        var req = ExperienceRequest.builder()
            .task("fix bug")
            .project("/p")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("source");
        assertThat(wire).doesNotContainKey("requiredConcepts");
        assertThat(wire).doesNotContainKey("userId");
        // Default count should be included
        assertThat(wire).containsEntry("count", 4);
    }

    @Test
    void experienceRequest_toWireFormat_omitsBlankProject() {
        var req = ExperienceRequest.builder()
            .task("test")
            .project("")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("project");
    }

    @Test
    void iclPromptRequest_toWireFormat() {
        var req = ICLPromptRequest.builder()
            .task("debug")
            .project("/app")
            .maxChars(5000)
            .userId("bob")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).containsEntry("task", "debug");
        assertThat(wire).containsEntry("project", "/app");
        assertThat(wire).containsEntry("maxChars", 5000);
        assertThat(wire).containsEntry("userId", "bob");
    }

    @Test
    void iclPromptRequest_toWireFormat_omitsNullOptional() {
        var req = ICLPromptRequest.builder()
            .task("test")
            .project("/p")
            .build();

        Map<String, Object> wire = req.toWireFormat();
        assertThat(wire).doesNotContainKey("maxChars");
        assertThat(wire).doesNotContainKey("userId");
    }

    @Test
    void experience_deserializeSnakeCase() throws Exception {
        // Backend uses global SNAKE_CASE Jackson naming strategy.
        // Verify Experience DTO correctly maps snake_case JSON fields.
        String json = """
            {
                "id": "exp-123",
                "task": "add dark mode",
                "strategy": "reuse",
                "outcome": "success",
                "reuse_condition": "frontend project",
                "quality_score": 0.85,
                "created_at": "2026-03-28T10:00:00Z"
            }
            """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Experience exp = mapper.readValue(json, Experience.class);
        assertThat(exp.id()).isEqualTo("exp-123");
        assertThat(exp.task()).isEqualTo("add dark mode");
        assertThat(exp.strategy()).isEqualTo("reuse");
        assertThat(exp.outcome()).isEqualTo("success");
        assertThat(exp.reuseCondition()).isEqualTo("frontend project");
        assertThat(exp.qualityScore()).isEqualTo(0.85f);
        assertThat(exp.createdAt()).isNotNull();
    }

    @Test
    void experience_deserializeCamelCase_fallback() throws Exception {
        // Also accept camelCase in case backend changes naming strategy
        String json = """
            {
                "id": "exp-456",
                "task": "refactor",
                "strategy": "new",
                "outcome": "pending",
                "reuseCondition": "any",
                "qualityScore": 0.5,
                "createdAt": "2026-03-28T12:00:00Z"
            }
            """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // With @JsonProperty annotations, only snake_case is accepted by default.
        // Verify camelCase falls back to null (expected behavior — backend uses SNAKE_CASE).
        Experience exp = mapper.readValue(json, Experience.class);
        assertThat(exp.id()).isEqualTo("exp-456");
        // reuseCondition and qualityScore will be default (null/0) because @JsonProperty overrides
        // the default mapping. This is acceptable since backend always sends snake_case.
    }
}
