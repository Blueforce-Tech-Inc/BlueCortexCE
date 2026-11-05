package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.service.SessionManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the batch-session endpoint's N+1 fix.
 *
 * <p>The endpoint used to call the single-id lookup once per requested id. These tests pin
 * two properties that the batching must not lose: a request issues exactly one service
 * call, and the response still follows the caller's input order (including duplicate ids,
 * which the old loop returned twice). The database-level equivalence between the batch
 * query and the single-row query is covered by {@code SessionBatchLookupDatabaseTest}.
 */
class ViewerControllerBatchSessionsTest {

    private final SessionManagementService sessions = mock(SessionManagementService.class);
    private final ViewerController controller = new ViewerController(
        null, null, null, null, null, null, sessions, null, null);

    private static SessionEntity session(String contentSessionId) {
        SessionEntity entity = new SessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setContentSessionId(contentSessionId);
        entity.setPlatformSource("claude");
        entity.setProjectPath("/work");
        entity.setStartedAtEpoch(1_000L);
        entity.setStatus("completed");
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> body(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody();
    }

    private static List<String> contentIds(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> (String) row.get("content_session_id")).toList();
    }

    @Test
    @DisplayName("legacy contentSessionIds batch: one lookup, input order preserved")
    void legacyBatchIssuesOneLookupAndPreservesOrder() {
        SessionEntity a = session("a");
        SessionEntity b = session("b");
        when(sessions.findLegacyClaudeBatch(any())).thenReturn(Map.of("a", a, "b", b));

        List<Map<String, Object>> rows = body(controller.batchGetSessions(
            Map.of("contentSessionIds", List.of("b", "a", "missing"))));

        assertThat(contentIds(rows)).containsExactly("b", "a");
        assertThat(rows).allSatisfy(row -> assertThat(row).containsKeys(
            "id", "content_session_id", "platform_source", "project", "status"));

        verify(sessions).findLegacyClaudeBatch(any());
        verify(sessions, never()).findLegacyClaude(anyString());
    }

    @Test
    @DisplayName("duplicate requested ids stay duplicated, matching the old per-id loop")
    void duplicateIdsAreRepeatedInTheResponse() {
        SessionEntity a = session("a");
        when(sessions.findLegacyClaudeBatch(any())).thenReturn(Map.of("a", a));

        List<Map<String, Object>> rows = body(controller.batchGetSessions(
            Map.of("contentSessionIds", List.of("a", "a"))));

        assertThat(contentIds(rows)).containsExactly("a", "a");
        assertThat(rows).allSatisfy(row -> assertThat(row.get("id")).isEqualTo(a.getId().toString()));
    }

    @Test
    @DisplayName("sessionRefs batch: one lookup per platform request, not one per ref")
    void refBatchIssuesOneLookupAndPreservesOrder() {
        ExternalSessionRef claudeRef = ExternalSessionRef.of("claude", "shared");
        ExternalSessionRef codexRef = ExternalSessionRef.of("codex", "shared");
        SessionEntity claude = session("shared");
        SessionEntity codex = session("shared");
        codex.setPlatformSource("codex");
        when(sessions.findBatch(any())).thenReturn(Map.of(claudeRef, claude, codexRef, codex));

        List<Map<String, Object>> rows = body(controller.batchGetSessions(
            Map.of("sessionRefs", List.of(
                Map.of("contentSessionId", "shared", "platformSource", "codex"),
                Map.of("contentSessionId", "shared")))));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("platform_source")).isEqualTo("codex");
        assertThat(rows.get(1).get("platform_source")).isEqualTo("claude");

        verify(sessions).findBatch(any());
        verify(sessions, never()).find(any());
    }
}
