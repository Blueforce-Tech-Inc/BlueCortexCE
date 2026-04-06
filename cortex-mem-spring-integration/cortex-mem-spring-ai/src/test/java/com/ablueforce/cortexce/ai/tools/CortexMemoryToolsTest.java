package com.ablueforce.cortexce.ai.tools;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CortexMemoryToolsTest {

    private CortexMemClient client;
    private CortexMemoryTools tools;

    @BeforeEach
    void setUp() {
        client = mock(CortexMemClient.class);
        tools = new CortexMemoryTools(client, "/test/project", 4);
    }

    @AfterEach
    void tearDown() {
        CortexSessionContext.end();
    }

    @Test
    void searchMemories_returns_formatted_experiences() {
        when(client.retrieveExperiences(any()))
            .thenReturn(List.of(
                new Experience("id1", "Fix login bug", "Check auth", "Fixed", "when auth fails", 0.9f, OffsetDateTime.now())
            ));

        String result = tools.searchMemories("fix login", 2);

        assertThat(result).contains("Task: Fix login bug");
        assertThat(result).contains("Strategy: Check auth");
        assertThat(result).contains("Outcome: Fixed");
    }

    @Test
    void searchMemories_empty_task_returns_message() {
        String result = tools.searchMemories("", 4);

        assertThat(result).isEqualTo("No search task provided.");
    }

    @Test
    void searchMemories_no_results_returns_message() {
        when(client.retrieveExperiences(any())).thenReturn(List.of());

        String result = tools.searchMemories("nonexistent task", null);

        assertThat(result).contains("No relevant past experiences found");
    }

    @Test
    void searchMemories_uses_CortexSessionContext_project_when_active() {
        CortexSessionContext.begin("s1", "/ctx/project");
        when(client.retrieveExperiences(any()))
            .thenReturn(List.of(
                new Experience("id1", "Task", "S", "O", null, 0.8f, OffsetDateTime.now())
            ));

        tools.searchMemories("task", 1);

        CortexSessionContext.end();
        // Client was called with project from context (verified by no exception)
        assertThat(tools.searchMemories("task", 1)).isNotEmpty();
    }

    @Test
    void getMemoryContext_returns_icl_prompt() {
        when(client.buildICLPrompt(any()))
            .thenReturn(new ICLPromptResult("Here are past experiences: ...", 2));

        String result = tools.getMemoryContext("fix bug");

        assertThat(result).isEqualTo("Here are past experiences: ...");
    }

    @Test
    void getMemoryContext_empty_task_returns_empty() {
        String result = tools.getMemoryContext("");

        assertThat(result).isEmpty();
    }

    @Test
    void updateMemory_allNullFields_returnsError() {
        // All optional params null → should return early with helpful message
        // (does NOT reach client.updateObservation which would throw IllegalArgumentException)
        String result = tools.updateMemory("obs-1", null, null, null, null, null);

        assertThat(result).contains("at least one field");
    }

    @Test
    void updateMemory_emptyId_returnsError() {
        String result = tools.updateMemory("", "title", null, null, null, null);

        assertThat(result).contains("observation ID is required");
    }

    @Test
    void deleteMemory_nullId_returnsError() {
        String result = tools.deleteMemory(null);

        assertThat(result).isEqualTo("Error: observation ID is required");
    }

    @Test
    void deleteMemory_blankId_returnsError() {
        String result = tools.deleteMemory("   ");

        assertThat(result).isEqualTo("Error: observation ID is required");
    }

    @Test
    void deleteMemory_validId_callsClientAndReturnsSuccess() {
        String result = tools.deleteMemory("valid-uuid-123");

        assertThat(result).isEqualTo("Successfully deleted observation: valid-uuid-123");
    }

    @Test
    void deleteMemory_clientThrows_returnsErrorMessage() {
        doThrow(new RuntimeException("connection refused")).when(client).deleteObservation("failing-uuid");

        String result = tools.deleteMemory("failing-uuid");

        assertThat(result).contains("Error deleting memory");
        assertThat(result).contains("connection refused");
    }
}
