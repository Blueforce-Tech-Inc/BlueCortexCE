package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.config.AppSettings;
import com.ablueforce.cortexce.dto.ApiResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the camelCase wire fields the WebUI submodule and the proxy read directly.
 *
 * <p>The backend serializes with {@code spring.jackson.property-naming-strategy:
 * SNAKE_CASE}, so every camelCase field only survives because of an explicit
 * {@code @JsonProperty}. Those fields are hard contracts with two consumers that
 * are not compiled or tested here:
 * <ul>
 *   <li>{@code hasMore} — {@code webui/src/ui/viewer/hooks/usePagination.ts};</li>
 *   <li>{@code updateFiles} — {@code proxy/proxy.js} and {@code proxy/wrapper.js};</li>
 *   <li>{@code CLAUDE_MEM_*} — {@code webui/src/ui/viewer/hooks/useSettings.ts}.</li>
 * </ul>
 *
 * <p>Until this test existed, renaming one of these fields to its snake_case form
 * left the whole backend suite green while silently breaking the viewer and the
 * proxy: nothing in this repository asserted the names the consumers actually
 * read. The expected names below are written out by hand, not derived from the
 * classes under test.
 */
class WebUiWireContractTest {

    /** Mirrors {@code spring.jackson.property-naming-strategy: SNAKE_CASE} in application.yml. */
    private static ObjectMapper appObjectMapper() {
        return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> wire(Object value) throws Exception {
        ObjectMapper mapper = appObjectMapper();
        return mapper.readValue(mapper.writeValueAsString(value), Map.class);
    }

    private static Method endpoint(String name) {
        for (Method method : ViewerController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return fail("ViewerController must still declare " + name);
    }

    /** True when the method returns {@code ResponseEntity<PagedResponse<...>>}. */
    private static boolean returnsPagedResponse(Method method) {
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType parameterized)) {
            return false;
        }
        if (!parameterized.getRawType().equals(ResponseEntity.class)) {
            return false;
        }
        Type body = parameterized.getActualTypeArguments()[0];
        return body instanceof ParameterizedType bodyType
            && bodyType.getRawType().equals(ViewerController.PagedResponse.class);
    }

    @Test
    @DisplayName("pagination endpoints keep the camelCase items/hasMore envelope")
    void paginationEnvelopeKeepsCamelCaseHasMore() throws Exception {
        Map<String, Object> json = wire(new ViewerController.PagedResponse<>(List.of(), true));

        assertEquals(Set.of("items", "hasMore"), json.keySet(),
            "the viewer reads data.items and data.hasMore; SNAKE_CASE must not rewrite them");
        assertTrue((Boolean) json.get("hasMore"));

        for (String endpoint : List.of("getObservations", "getSummaries", "getPrompts")) {
            assertTrue(returnsPagedResponse(endpoint(endpoint)),
                endpoint + " must return ResponseEntity<PagedResponse<...>>");
        }
    }

    @Test
    @DisplayName("session start and context inject keep the camelCase updateFiles field")
    void sessionAndContextResponsesKeepCamelCaseUpdateFiles() throws Exception {
        ApiResponses.StartSessionResponse start = new ApiResponses.StartSessionResponse(
            "session-1", "context", List.of(Map.of("path", "/tmp/CLAUDE.md", "content", "x")),
            "00000000-0000-0000-0000-000000000001", 1);

        Map<String, Object> sessionJson = wire(start);
        assertEquals(
            Set.of("session_id", "context", "updateFiles", "session_db_id", "prompt_number"),
            sessionJson.keySet());
        assertTrue(sessionJson.containsKey("updateFiles"));
        assertFalse(sessionJson.containsKey("update_files"),
            "proxy.js reads data.updateFiles; snake_case breaks CLAUDE.md updates");

        ApiResponses.ContextInjectResponse inject = new ApiResponses.ContextInjectResponse(
            "context", List.of(new ApiResponses.UpdateFileEntry("/tmp/CLAUDE.md", "x")));

        Map<String, Object> injectJson = wire(inject);
        assertEquals(Set.of("context", "updateFiles"), injectJson.keySet());
        assertFalse(injectJson.containsKey("update_files"));
    }

    @Test
    @DisplayName("GET /api/settings keeps the CLAUDE_MEM_* keys the viewer reads")
    void settingsSnapshotExposesTheKeysTheViewerReads() {
        // Hard-coded from webui/src/ui/viewer/hooks/useSettings.ts + types: the viewer
        // spreads the GET response into its settings state, so a renamed key silently
        // becomes a default value in the UI.
        Set<String> required = Set.of(
            "CLAUDE_MEM_BACKEND",
            "CLAUDE_MEM_MODE",
            "CLAUDE_MEM_PROVIDER",
            "CLAUDE_MEM_MODEL",
            "CLAUDE_MEM_LOG_LEVEL",
            "CLAUDE_MEM_CONTEXT_OBSERVATIONS",
            "CLAUDE_MEM_CONTEXT_FULL_COUNT",
            "CLAUDE_MEM_CONTEXT_FULL_FIELD",
            "CLAUDE_MEM_CONTEXT_SESSION_COUNT",
            "CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES",
            "CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS",
            "CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS",
            "CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS",
            "CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS",
            "CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT",
            "CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT",
            "CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY",
            "CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE",
            "CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED",
            "CLAUDE_MEM_EXCLUDED_PROJECTS",
            "CLAUDE_MEM_DATA_DIR"
        );

        Map<String, Object> snapshot = new AppSettings().toMap();

        assertTrue(snapshot.keySet().containsAll(required),
            "GET /api/settings is missing keys the viewer reads: " + required);
        for (String key : snapshot.keySet()) {
            assertTrue(key.startsWith("CLAUDE_MEM_"),
                "GET /api/settings must only expose CLAUDE_MEM_* keys, found: " + key);
        }
        // useContextPreview.ts calls `.trim()` on these, so JSON numbers/arrays here
        // throw "CLAUDE_MEM_CONTEXT_OBSERVATIONS?.trim is not a function" in the browser.
        for (String key : List.of(
                "CLAUDE_MEM_CONTEXT_OBSERVATIONS",
                "CLAUDE_MEM_CONTEXT_FULL_COUNT",
                "CLAUDE_MEM_CONTEXT_SESSION_COUNT",
                "CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES",
                "CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS")) {
            assertInstanceOf(String.class, snapshot.get(key), key + " must be a string for the viewer");
        }
    }
}
