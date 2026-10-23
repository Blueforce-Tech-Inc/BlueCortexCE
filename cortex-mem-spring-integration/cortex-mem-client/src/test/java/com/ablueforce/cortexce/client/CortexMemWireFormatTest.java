package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ObservationRequest;
import com.ablueforce.cortexce.client.dto.ObservationResponse;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.SessionStartRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the outbound wire contract of the Java SDK: <em>the exact set of keys</em> every
 * request body carries.
 *
 * <p>Background: the SDK builds its request bodies by hand, one map literal per request
 * DTO ({@code toWireFormat()}), plus a few raw maps in the client itself. The existing
 * suite only ever asserted that a body <em>contains</em> some value or some key name —
 * for example {@code contains("\"source\":\"manual\"")} or {@code doesNotContain("maxChars")}.
 * No test anywhere asserted the complete key set of any outbound body.
 *
 * <p>That leaves the whole outbound contract unpinned. Renaming a key inside one
 * {@code toWireFormat()} map — or adding a field to the backend's request DTO — changes
 * what goes on the wire, and the suite stays green while the backend silently ignores
 * the renamed field. The key sets asserted here were checked against the backend's own
 * request records ({@code backend/.../dto/ApiRequests.java}) while writing this file;
 * every key the SDK sends is a key the backend declares.
 *
 * <p>The second family pins what the reference documentation publishes about the wire:
 * the names it enumerates must still be the names the SDK uses, and the family names it
 * compares itself with must still be the families that really behave alike.
 */
class CortexMemWireFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private CortexMemClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        CortexMemProperties props = new CortexMemProperties();
        props.setBaseUrl(baseUrl);
        props.getRetry().setMaxAttempts(1);
        props.getRetry().setBackoff(Duration.ZERO);
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        client = new CortexMemClientImpl(props, builder);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("every outbound request body carries exactly the published key set")
    void everyOutboundBodyHasExactlyThePublishedKeySet() throws Exception {
        Map<String, Set<String>> actual = new LinkedHashMap<>();

        actual.put("POST /api/session/start", keysOf(() ->
                client.startSession(SessionStartRequest.builder()
                    .sessionId("s1").projectPath("/proj").userId("u1")
                    .platformSource("codex").build()), "{}"));

        actual.put("POST /api/ingest/tool-use", keysOf(() ->
                client.recordObservation(ObservationRequest.builder()
                    .sessionId("s1").projectPath("/proj").toolName("Read")
                    .toolInput("{}").toolResponse("{}").promptNumber(3)
                    .source("manual").extractedData(Map.of("pref", "dark"))
                    .platformSource("codex").build()), ""));

        actual.put("POST /api/ingest/session-end", keysOf(() ->
                client.recordSessionEnd(SessionEndRequest.builder()
                    .sessionId("s1").projectPath("/proj").lastAssistantMessage("done")
                    .platformSource("codex").build()), ""));

        actual.put("POST /api/ingest/user-prompt", keysOf(() ->
                client.recordUserPrompt(UserPromptRequest.builder()
                    .sessionId("s1").projectPath("/proj").promptText("hello").promptNumber(2)
                    .platformSource("codex").build()), "{}"));

        actual.put("POST /api/memory/experiences", keysOf(() ->
                client.retrieveExperiences(ExperienceRequest.builder()
                    .task("t").project("/proj").count(5).source("manual")
                    .requiredConcepts(List.of("a")).userId("u1").build()), "[]"));

        actual.put("POST /api/memory/icl-prompt", keysOf(() ->
                client.buildICLPrompt(ICLPromptRequest.builder()
                    .task("t").project("/proj").maxChars(8000).userId("u1").build()),
                "{\"prompt\":\"ok\",\"experienceCount\":0}"));

        actual.put("POST /api/memory/feedback", keysOf(() ->
                client.submitFeedback("obs-1", "SUCCESS", "helpful"), ""));

        actual.put("PATCH /api/memory/observations/obs-1", keysOf(() ->
                client.updateObservation("obs-1", ObservationUpdate.builder()
                    .title("t").subtitle("s").content("c")
                    .facts(List.of("f")).concepts(List.of("x"))
                    .source("manual").extractedData(Map.of("k", "v")).build()), ""));

        actual.put("PATCH /api/session/user", keysOf(() ->
                client.updateSessionUserId("sess-1", "u1"), "{}"));

        actual.put("POST /api/observations/batch", keysOf(() ->
                client.getObservationsByIds(List.of("id1")), "{\"observations\":[]}"));

        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("POST /api/session/start",
            Set.of("session_id", "project_path", "user_id", "platform_source"));
        expected.put("POST /api/ingest/tool-use",
            Set.of("session_id", "tool_name", "cwd", "tool_input", "tool_response",
                   "prompt_number", "source", "extractedData", "platform_source"));
        expected.put("POST /api/ingest/session-end",
            Set.of("session_id", "cwd", "last_assistant_message", "platform_source"));
        expected.put("POST /api/ingest/user-prompt",
            Set.of("session_id", "prompt_text", "cwd", "prompt_number", "platform_source"));
        expected.put("POST /api/memory/experiences",
            Set.of("task", "project", "count", "source", "requiredConcepts", "userId"));
        expected.put("POST /api/memory/icl-prompt",
            Set.of("task", "project", "maxChars", "userId"));
        expected.put("POST /api/memory/feedback",
            Set.of("observationId", "feedbackType", "comment"));
        expected.put("PATCH /api/memory/observations/obs-1",
            Set.of("title", "subtitle", "content", "facts", "concepts", "source", "extractedData"));
        expected.put("PATCH /api/session/user",
            Set.of("user_id"));
        expected.put("POST /api/observations/batch",
            Set.of("ids"));

        // Positive control: the sweep must actually have reached every endpoint that
        // sends a body. A sweep that silently reached nothing would report an empty
        // map and pass for the wrong reason.
        assertThat(actual).hasSize(10);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("the narrative variant of a partial update swaps one key, not adds one")
    void partialUpdateNarrativeVariantCarriesTheSameKeyCount() throws Exception {
        Set<String> withNarrative = keysOf(() ->
                client.updateObservation("obs-1", ObservationUpdate.builder()
                    .title("t").subtitle("s").narrative("n")
                    .facts(List.of("f")).concepts(List.of("x"))
                    .source("manual").extractedData(Map.of("k", "v")).build()), "");

        assertThat(withNarrative).containsExactlyInAnyOrder(
            "title", "subtitle", "narrative", "facts", "concepts", "source", "extractedData");
        assertThat(withNarrative).doesNotContain("content");
    }

    @Test
    @DisplayName("the published references still name the wire names the SDK uses")
    void publishedReferencesNameTheWireNamesTheSdkUses() throws Exception {
        Map<String, String> published = new LinkedHashMap<>();
        published.put("sessionId", "content_session_id");
        published.put("projectPath", "project");
        published.put("extractedData", "extractedData");
        published.put("filesRead", "files_read");
        published.put("filesModified", "files_modified");
        published.put("refinedFromIds", "refined_from_ids");

        // The wire name the SDK actually binds each Java name to. Jackson reads the
        // annotation off the record's accessor, not off the record component — the
        // annotation is not @RecordComponent-targeted, so it never lands there.
        Map<String, String> actual = new LinkedHashMap<>();
        for (var component : ObservationResponse.class.getRecordComponents()) {
            String wire = component.getName();
            var accessor = ObservationResponse.class.getMethod(component.getName());
            var annotation = accessor.getAnnotation(
                com.fasterxml.jackson.annotation.JsonProperty.class);
            if (annotation != null) {
                wire = annotation.value();
            }
            actual.put(component.getName(), wire);
        }

        assertThat(actual).containsAllEntriesOf(published);

        // Every name the reference publishes must still be there, in both languages.
        for (String file : List.of("README.md", "README-zh-CN.md")) {
            String text = Files.readString(sdkRoot().resolve(file));
            String section = wireFormatSection(text);
            for (Map.Entry<String, String> entry : published.entrySet()) {
                assertThat(section)
                    .as("%s must publish the wire name of %s", file, entry.getKey())
                    .contains(entry.getValue());
            }
        }
    }

    @Test
    @DisplayName("the refinedFromIds bullet names the SDKs that expose it as a list")
    void refinedFromIdsBulletNamesTheSdkFamiliesThatSplitIt() throws Exception {
        for (String file : List.of("README.md", "README-zh-CN.md")) {
            String text = Files.readString(sdkRoot().resolve(file));
            String bullet = bulletMentioning(text, "refinedFromIds");

            assertThat(bullet)
                .as("%s must name the SDKs that expose refined_from_ids as a list", file)
                .contains("Go", "Python", "JS");
        }
    }

    // ------------------------------------------------------------- helpers

    private record WireCall(String method, String path, Set<String> keys) {}

    private Set<String> keysOf(Runnable call, String responseBody) throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));
        call.run();
        RecordedRequest req = server.takeRequest();
        Map<String, Object> body = MAPPER.readValue(req.getBody().readUtf8(),
            new TypeReference<Map<String, Object>>() {});
        return new LinkedHashSet<>(body.keySet());
    }

    /** The wire-format section of a reference document (the heading is localised). */
    private static String wireFormatSection(String text) {
        int start = text.indexOf("## Wire Format");
        if (start < 0) {
            start = text.indexOf("## Wire 格式");
        }
        assertThat(start).as("the reference must have a wire-format section").isGreaterThan(-1);
        int end = text.indexOf("\n## ", start + 1);
        return end < 0 ? text.substring(start) : text.substring(start, end);
    }

    /** The whole bullet (or paragraph) that mentions the given token. */
    private static String bulletMentioning(String text, String token) {
        String[] lines = text.split("\n");
        int hit = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(token)) {
                hit = i;
                break;
            }
        }
        assertThat(hit).as("the reference must mention %s", token).isGreaterThan(-1);

        // Walk back to the bullet marker the token belongs to ...
        int start = hit;
        while (start > 0 && !lines[start].startsWith("- ") && !lines[start].startsWith("* ")) {
            start--;
        }
        // ... and forward over its continuation lines. A bullet ends at the next
        // bullet, heading or paragraph — that is, at the next line that is neither
        // blank nor indented.
        int end = hit;
        while (end + 1 < lines.length) {
            String next = lines[end + 1];
            if (next.isBlank() || !Character.isWhitespace(next.charAt(0))) {
                break;
            }
            end++;
        }
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, end + 1));
    }

    /** The Java SDK root, i.e. the directory holding README.md and cortex-mem-client. */
    private static Path sdkRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.exists(dir.resolve("README.md"))
                    && Files.isDirectory(dir.resolve("cortex-mem-client"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new AssertionError(
            "cannot locate the Java SDK root from " + Paths.get("").toAbsolutePath());
    }
}
