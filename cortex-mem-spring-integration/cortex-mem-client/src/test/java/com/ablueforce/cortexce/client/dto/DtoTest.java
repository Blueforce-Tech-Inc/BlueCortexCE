package com.ablueforce.cortexce.client.dto;

import org.junit.jupiter.api.Test;

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
    void iclPromptResult_experienceCountAsInt() {
        assertThat(new ICLPromptResult("", "3").experienceCountAsInt()).isEqualTo(3);
        assertThat(new ICLPromptResult("", "0").experienceCountAsInt()).isEqualTo(0);
        assertThat(new ICLPromptResult("", "invalid").experienceCountAsInt()).isEqualTo(0);
    }

    @Test
    void qualityDistribution_total() {
        var q = new QualityDistribution("/p", 10, 5, 3, 2);
        assertThat(q.total()).isEqualTo(20);
    }
}
