package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ObservationRequest;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
import com.ablueforce.cortexce.client.dto.ObservationsRequest;
import com.ablueforce.cortexce.client.dto.SearchRequest;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.SessionStartRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the client's <em>required-argument contract</em>: every guard that rejects
 * a blank argument before the request leaves the process, and the published
 * table of required arguments matches what the code actually enforces.
 *
 * <p>Why this needs pinning. The README publishes an eighteen-row table of
 * required arguments and states that the client "throws
 * {@code IllegalArgumentException} and sends no request" for every one of them.
 * Nothing in the build connected that promise to the code. A per-guard mutation
 * sweep of {@code CortexMemClientImpl} — one guard removed at a time, the whole
 * client-module suite re-run, the file restored byte for byte — found that most
 * of the guard set was enforced by no test at all, and that the published table
 * was never compared with the runtime.
 *
 * <p><b>Two layers of validation.</b> The SDK rejects a blank required argument
 * in one of two places, and the README's table does not say which:
 * <ul>
 *   <li>the <em>request DTO</em> — {@code SessionStartRequest.Builder.build()}
 *       and {@code SearchRequest}'s compact constructor fail fast with their own
 *       wording ("sessionId is required (set via sessionId())");</li>
 *   <li>the <em>client</em> — {@code requireNonBlank} and friends, with the
 *       wording "sessionId must not be null or blank".</li>
 * </ul>
 * The two layers produce different messages for the same caller mistake, so
 * both are pinned separately here. Where the DTO rejects first, the client's own
 * guard is unreachable through that constructor — for {@code search} it is
 * unreachable altogether, because {@code SearchRequest}'s compact constructor
 * rejects a blank project with the client's own wording.
 *
 * <p>What is asserted.
 * <ol>
 *   <li><b>One case per guard</b>: the exception type, the field named in the
 *       message, and the fact that <em>no request was sent</em>. The last part
 *       is what makes the guard meaningful — a client that rejected the call
 *       only after it had already sent it would satisfy the first two.</li>
 *   <li><b>The DTO layer is pinned too</b>, with its own wording.</li>
 *   <li><b>Positive controls</b>: with valid arguments the same methods really
 *       do send exactly one request, so "no request was sent" can never hold
 *       vacuously.</li>
 *   <li><b>The published table is bound to the runtime</b>: the set of methods
 *       that reject a blank argument is derived from behaviour, and must equal
 *       the row set of the table in both language versions.</li>
 * </ol>
 *
 * <p>The whole class is offline: the server is a MockWebServer, so no test can
 * reach the resident backend.
 */
class CortexMemClientRequiredArgumentsTest {

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
        props.setReadTimeout(Duration.ofSeconds(2));
        client = new CortexMemClientImpl(props, RestClient.builder().baseUrl(baseUrl));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ==================== configuration guards ====================

    @Test
    @DisplayName("a blank base URL is rejected at construction")
    void blankBaseUrlIsRejected() {
        CortexMemProperties props = new CortexMemProperties();
        props.setBaseUrl("  ");
        assertThatThrownBy(() -> new CortexMemClientImpl(props, RestClient.builder()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("baseUrl must not be null or blank");
    }

    @Test
    @DisplayName("a negative connect timeout is rejected at construction")
    void negativeConnectTimeoutIsRejected() {
        CortexMemProperties props = new CortexMemProperties();
        props.setBaseUrl("http://127.0.0.1:1");
        props.setConnectTimeout(Duration.ofSeconds(-1));
        assertThatThrownBy(() -> new CortexMemClientImpl(props, RestClient.builder()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("connectTimeout must not be null or negative");
    }

    @Test
    @DisplayName("a read timeout beyond 24.8 days is rejected at construction")
    void oversizedReadTimeoutIsRejected() {
        CortexMemProperties props = new CortexMemProperties();
        props.setBaseUrl("http://127.0.0.1:1");
        props.setReadTimeout(Duration.ofMillis(Integer.MAX_VALUE + 1L));
        assertThatThrownBy(() -> new CortexMemClientImpl(props, RestClient.builder()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("readTimeout exceeds maximum supported value (24.8 days)");
    }

    // ==================== the DTO layer ====================
    // Two request records reject a blank required argument themselves, before
    // the client is ever called. Their wording differs from the client's.

    @Test
    @DisplayName("the SessionStartRequest builder rejects a blank sessionId")
    void sessionStartRequestBuilderRejectsBlankSessionId() {
        assertThatThrownBy(() -> SessionStartRequest.builder().sessionId("").projectPath("/proj").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("sessionId is required (set via sessionId())");
    }

    @Test
    @DisplayName("the SessionStartRequest builder rejects a blank projectPath")
    void sessionStartRequestBuilderRejectsBlankProjectPath() {
        assertThatThrownBy(() -> SessionStartRequest.builder().sessionId("s1").projectPath("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("projectPath is required (set via projectPath())");
    }

    @Test
    @DisplayName("the SearchRequest builder rejects a blank project")
    void searchRequestBuilderRejectsBlankProject() {
        assertThatThrownBy(() -> SearchRequest.builder().project("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("project is required (set via project())");
    }

    @Test
    @DisplayName("the SearchRequest record rejects a blank project with the client's own wording")
    void searchRequestRecordRejectsBlankProject() {
        // The compact constructor fails fast, so the client's own guard for
        // `project` can never be reached - and the two wordings are identical.
        assertThatThrownBy(() -> new SearchRequest("", null, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("project must not be null or blank");
    }

    // ==================== startSession ====================
    // The canonical constructor does not validate, so these reach the client.

    @Test
    @DisplayName("startSession rejects a null request")
    void startSessionRejectsNullRequest() {
        assertGuarded(() -> client.startSession(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("startSession rejects a blank sessionId")
    void startSessionRejectsBlankSessionId() {
        assertGuarded(() -> client.startSession(new SessionStartRequest("", "/proj", null)),
                IllegalArgumentException.class, "sessionId must not be null or blank");
    }

    @Test
    @DisplayName("startSession rejects a blank projectPath")
    void startSessionRejectsBlankProjectPath() {
        assertGuarded(() -> client.startSession(new SessionStartRequest("s1", "", null)),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    // ==================== recordObservation ====================

    @Test
    @DisplayName("recordObservation rejects a null request")
    void recordObservationRejectsNullRequest() {
        assertGuarded(() -> client.recordObservation(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("recordObservation rejects a blank sessionId")
    void recordObservationRejectsBlankSessionId() {
        assertGuarded(() -> client.recordObservation(ObservationRequest.builder()
                        .sessionId("").projectPath("/proj").toolName("Read").build()),
                IllegalArgumentException.class, "sessionId must not be null or blank");
    }

    @Test
    @DisplayName("recordObservation rejects a blank projectPath")
    void recordObservationRejectsBlankProjectPath() {
        assertGuarded(() -> client.recordObservation(ObservationRequest.builder()
                        .sessionId("s1").projectPath("").toolName("Read").build()),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("recordObservation rejects a relative projectPath (the Java-only rule)")
    void recordObservationRejectsRelativeProjectPath() {
        assertGuarded(() -> client.recordObservation(ObservationRequest.builder()
                        .sessionId("s1").projectPath("relative/path").toolName("Read").build()),
                IllegalArgumentException.class, "projectPath must be an absolute path");
    }

    @Test
    @DisplayName("recordObservation rejects a blank toolName")
    void recordObservationRejectsBlankToolName() {
        assertGuarded(() -> client.recordObservation(ObservationRequest.builder()
                        .sessionId("s1").projectPath("/proj").toolName("").build()),
                IllegalArgumentException.class, "toolName must not be null or blank");
    }

    // ==================== recordSessionEnd ====================

    @Test
    @DisplayName("recordSessionEnd rejects a null request")
    void recordSessionEndRejectsNullRequest() {
        assertGuarded(() -> client.recordSessionEnd(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("recordSessionEnd rejects a blank sessionId")
    void recordSessionEndRejectsBlankSessionId() {
        assertGuarded(() -> client.recordSessionEnd(
                        SessionEndRequest.builder().sessionId("").projectPath("/proj").build()),
                IllegalArgumentException.class, "sessionId must not be null or blank");
    }

    @Test
    @DisplayName("recordSessionEnd rejects a blank projectPath")
    void recordSessionEndRejectsBlankProjectPath() {
        assertGuarded(() -> client.recordSessionEnd(
                        SessionEndRequest.builder().sessionId("s1").projectPath("").build()),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("recordSessionEnd rejects a relative projectPath (the Java-only rule)")
    void recordSessionEndRejectsRelativeProjectPath() {
        assertGuarded(() -> client.recordSessionEnd(
                        SessionEndRequest.builder().sessionId("s1").projectPath("relative/path").build()),
                IllegalArgumentException.class, "projectPath must be an absolute path");
    }

    // ==================== recordUserPrompt ====================

    @Test
    @DisplayName("recordUserPrompt rejects a null request")
    void recordUserPromptRejectsNullRequest() {
        assertGuarded(() -> client.recordUserPrompt(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("recordUserPrompt rejects a blank sessionId")
    void recordUserPromptRejectsBlankSessionId() {
        assertGuarded(() -> client.recordUserPrompt(UserPromptRequest.builder()
                        .sessionId("").projectPath("/proj").promptText("hi").build()),
                IllegalArgumentException.class, "sessionId must not be null or blank");
    }

    @Test
    @DisplayName("recordUserPrompt rejects a blank promptText")
    void recordUserPromptRejectsBlankPromptText() {
        assertGuarded(() -> client.recordUserPrompt(UserPromptRequest.builder()
                        .sessionId("s1").projectPath("/proj").promptText("").build()),
                IllegalArgumentException.class, "promptText must not be null or blank");
    }

    @Test
    @DisplayName("recordUserPrompt rejects a blank projectPath")
    void recordUserPromptRejectsBlankProjectPath() {
        assertGuarded(() -> client.recordUserPrompt(UserPromptRequest.builder()
                        .sessionId("s1").projectPath("").promptText("hi").build()),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("recordUserPrompt rejects a relative projectPath (the Java-only rule)")
    void recordUserPromptRejectsRelativeProjectPath() {
        assertGuarded(() -> client.recordUserPrompt(UserPromptRequest.builder()
                        .sessionId("s1").projectPath("relative/path").promptText("hi").build()),
                IllegalArgumentException.class, "projectPath must be an absolute path");
    }

    // ==================== retrieveExperiences / buildICLPrompt ====================

    @Test
    @DisplayName("retrieveExperiences rejects a null request")
    void retrieveExperiencesRejectsNullRequest() {
        assertGuarded(() -> client.retrieveExperiences(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("retrieveExperiences rejects a blank task")
    void retrieveExperiencesRejectsBlankTask() {
        assertGuarded(() -> client.retrieveExperiences(ExperienceRequest.builder().task("").build()),
                IllegalArgumentException.class, "task must not be null or blank");
    }

    @Test
    @DisplayName("buildICLPrompt rejects a null request")
    void buildICLPromptRejectsNullRequest() {
        assertGuarded(() -> client.buildICLPrompt(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("buildICLPrompt rejects a blank task")
    void buildICLPromptRejectsBlankTask() {
        assertGuarded(() -> client.buildICLPrompt(ICLPromptRequest.builder().task("").build()),
                IllegalArgumentException.class, "task must not be null or blank");
    }

    // ==================== management and extraction ====================

    @Test
    @DisplayName("triggerRefinement rejects a blank projectPath")
    void triggerRefinementRejectsBlankProjectPath() {
        assertGuarded(() -> client.triggerRefinement(""),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("submitFeedback rejects a blank observationId")
    void submitFeedbackRejectsBlankObservationId() {
        assertGuarded(() -> client.submitFeedback("", "helpfulness", null),
                IllegalArgumentException.class, "observationId must not be null or blank");
    }

    @Test
    @DisplayName("submitFeedback rejects a blank feedbackType")
    void submitFeedbackRejectsBlankFeedbackType() {
        assertGuarded(() -> client.submitFeedback("obs-1", "", null),
                IllegalArgumentException.class, "feedbackType must not be null or blank");
    }

    @Test
    @DisplayName("getQualityDistribution rejects a blank projectPath")
    void getQualityDistributionRejectsBlankProjectPath() {
        assertGuarded(() -> client.getQualityDistribution(""),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("triggerExtraction rejects a blank projectPath")
    void triggerExtractionRejectsBlankProjectPath() {
        assertGuarded(() -> client.triggerExtraction(""),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("getLatestExtraction rejects a blank projectPath")
    void getLatestExtractionRejectsBlankProjectPath() {
        assertGuarded(() -> client.getLatestExtraction("", "tpl", null),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("getLatestExtraction rejects a blank templateName")
    void getLatestExtractionRejectsBlankTemplateName() {
        assertGuarded(() -> client.getLatestExtraction("/proj", "", null),
                IllegalArgumentException.class, "templateName must not be null or blank");
    }

    @Test
    @DisplayName("getExtractionHistory rejects a blank projectPath")
    void getExtractionHistoryRejectsBlankProjectPath() {
        assertGuarded(() -> client.getExtractionHistory("", "tpl", null, 10),
                IllegalArgumentException.class, "projectPath must not be null or blank");
    }

    @Test
    @DisplayName("getExtractionHistory rejects a blank templateName")
    void getExtractionHistoryRejectsBlankTemplateName() {
        assertGuarded(() -> client.getExtractionHistory("/proj", "", null, 10),
                IllegalArgumentException.class, "templateName must not be null or blank");
    }

    @Test
    @DisplayName("getExtractionHistory rejects a negative limit")
    void getExtractionHistoryRejectsNegativeLimit() {
        assertGuarded(() -> client.getExtractionHistory("/proj", "tpl", null, -1),
                IllegalArgumentException.class, "limit must not be negative");
    }

    @Test
    @DisplayName("updateSessionUserId rejects a blank sessionId")
    void updateSessionUserIdRejectsBlankSessionId() {
        assertGuarded(() -> client.updateSessionUserId("", "user-1"),
                IllegalArgumentException.class, "sessionId must not be null or blank");
    }

    @Test
    @DisplayName("updateSessionUserId rejects a blank userId")
    void updateSessionUserIdRejectsBlankUserId() {
        assertGuarded(() -> client.updateSessionUserId("s1", ""),
                IllegalArgumentException.class, "userId must not be null or blank");
    }

    // ==================== observations ====================

    @Test
    @DisplayName("updateObservation rejects a blank observationId")
    void updateObservationRejectsBlankObservationId() {
        assertGuarded(() -> client.updateObservation("", ObservationUpdate.builder().source("manual").build()),
                IllegalArgumentException.class, "observationId must not be null or blank");
    }

    @Test
    @DisplayName("updateObservation rejects a null update")
    void updateObservationRejectsNullUpdate() {
        assertGuarded(() -> client.updateObservation("obs-1", null),
                NullPointerException.class, "update must not be null");
    }

    @Test
    @DisplayName("updateObservation rejects an update that changes nothing")
    void updateObservationRejectsEmptyUpdate() {
        assertGuarded(() -> client.updateObservation("obs-1", ObservationUpdate.builder().build()),
                IllegalArgumentException.class, "at least one field must be provided for update");
    }

    @Test
    @DisplayName("deleteObservation rejects a blank observationId")
    void deleteObservationRejectsBlankObservationId() {
        assertGuarded(() -> client.deleteObservation(""),
                IllegalArgumentException.class, "observationId must not be null or blank");
    }

    @Test
    @DisplayName("getObservation rejects a blank observationId")
    void getObservationRejectsBlankObservationId() {
        assertGuarded(() -> client.getObservation(""),
                IllegalArgumentException.class, "observationId must not be null or blank");
    }

    @Test
    @DisplayName("getObservationsByIds rejects a null id list")
    void getObservationsByIdsRejectsNullList() {
        assertGuarded(() -> client.getObservationsByIds(null),
                NullPointerException.class, "ids must not be null");
    }

    @Test
    @DisplayName("getObservationsByIds rejects an empty id list")
    void getObservationsByIdsRejectsEmptyList() {
        assertGuarded(() -> client.getObservationsByIds(List.of()),
                IllegalArgumentException.class, "ids must not be empty");
    }

    @Test
    @DisplayName("getObservationsByIds rejects more than 100 ids")
    void getObservationsByIdsRejectsOversizedBatch() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add("obs-" + i);
        }
        assertGuarded(() -> client.getObservationsByIds(ids),
                IllegalArgumentException.class, "batch size exceeds maximum of 100");
    }

    @Test
    @DisplayName("getObservationsByIds rejects a blank element")
    void getObservationsByIdsRejectsBlankElement() {
        assertGuarded(() -> client.getObservationsByIds(List.of("obs-1", "  ")),
                IllegalArgumentException.class, "ids[1] is empty");
    }

    // ==================== search and listing ====================

    @Test
    @DisplayName("search rejects a null request")
    void searchRejectsNullRequest() {
        assertGuarded(() -> client.search(null),
                NullPointerException.class, "request must not be null");
    }

    @Test
    @DisplayName("search rejects a blank project before any request")
    void searchRejectsBlankProject() {
        assertGuarded(() -> client.search(new SearchRequest("", "q", null, null, null, null, null, null)),
                IllegalArgumentException.class, "project must not be null or blank");
    }

    @Test
    @DisplayName("listObservations rejects a null request")
    void listObservationsRejectsNullRequest() {
        assertGuarded(() -> client.listObservations(null),
                NullPointerException.class, "request must not be null");
    }

    // ==================== positive controls ====================
    // Without these, "no request was sent" could hold vacuously: a client that
    // never sent anything would satisfy every guard case above.

    @Test
    @DisplayName("startSession with valid arguments sends exactly one request")
    void startSessionWithValidArgumentsSendsOneRequest() throws Exception {
        server.enqueue(json("{}"));

        client.startSession(new SessionStartRequest("s1", "/proj", null));

        assertThat(server.getRequestCount()).isOne();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/session/start");
    }

    @Test
    @DisplayName("recordObservation with valid arguments sends exactly one request")
    void recordObservationWithValidArgumentsSendsOneRequest() throws Exception {
        server.enqueue(json("{}"));

        client.recordObservation(ObservationRequest.builder()
                .sessionId("s1").projectPath("/proj").toolName("Read").build());

        assertThat(server.getRequestCount()).isOne();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/ingest/tool-use");
    }

    @Test
    @DisplayName("updateObservation with a real change sends exactly one request")
    void updateObservationWithAChangeSendsOneRequest() throws Exception {
        server.enqueue(json("{}"));

        client.updateObservation("obs-1", ObservationUpdate.builder().source("manual").build());

        assertThat(server.getRequestCount()).isOne();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PATCH");
    }

    @Test
    @DisplayName("getObservationsByIds with valid ids sends exactly one request")
    void getObservationsByIdsWithValidIdsSendsOneRequest() throws Exception {
        server.enqueue(json("{}"));

        client.getObservationsByIds(List.of("obs-1", "obs-2"));

        assertThat(server.getRequestCount()).isOne();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/observations/batch");
    }

    @Test
    @DisplayName("search with a valid project sends exactly one request")
    void searchWithValidProjectSendsOneRequest() throws Exception {
        server.enqueue(json("{}"));

        client.search(new SearchRequest("/proj", "q", null, null, null, null, null, null));

        assertThat(server.getRequestCount()).isOne();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    // ==================== the published table is bound to the runtime ====================

    @Test
    @DisplayName("the published required-argument table lists exactly the methods that reject a blank argument")
    void publishedTableMatchesTheRuntime() throws IOException {
        Set<String> guarded = methodsRejectingBlankArguments();

        Set<String> publishedEn = publishedTable("README.md", "| Method | Required arguments |");
        Set<String> publishedZh = publishedTable("README-zh-CN.md", "| 方法 | 必填参数 |");

        assertThat(publishedEn)
                .as("the English table must list exactly the methods that reject a blank argument")
                .containsExactlyInAnyOrderElementsOf(guarded);
        assertThat(publishedZh)
                .as("the Chinese table must list exactly the methods that reject a blank argument")
                .containsExactlyInAnyOrderElementsOf(guarded);
        assertThat(publishedEn)
                .as("the table must not be empty - an empty comparison would pass vacuously")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the exposed API method count is pinned")
    void exposedApiMethodCountIsPinned() {
        assertThat(CortexMemClient.class.getDeclaredMethods())
                .as("the SDK's API method count (close and toString are not API methods)")
                .hasSize(26);
    }

    /**
     * The methods that reject a blank argument, derived from behaviour rather
     * than from a hand-written list: every API method is called with blank
     * arguments and is recorded as guarded only when it rejected the call
     * before any request was sent.
     */
    private Set<String> methodsRejectingBlankArguments() {
        // The methods that have no blank-argument guard really do reach the
        // server. Queue a generic response for each of them so the probe does
        // not spend the read timeout waiting for one; the queued bodies never
        // make an unguarded method look like a guarded one, because only an
        // IllegalArgumentException counts as "guarded".
        for (int i = 0; i < 12; i++) {
            server.enqueue(json("{}"));
        }
        Map<String, Call> probes = new LinkedHashMap<>();
        probes.put("startSession", () -> client.startSession(
                new SessionStartRequest("", "", null)));
        probes.put("recordObservation", () -> client.recordObservation(ObservationRequest.builder()
                .sessionId("").projectPath("").toolName("").build()));
        probes.put("recordSessionEnd", () -> client.recordSessionEnd(
                SessionEndRequest.builder().sessionId("").projectPath("").build()));
        probes.put("recordUserPrompt", () -> client.recordUserPrompt(UserPromptRequest.builder()
                .sessionId("").projectPath("").promptText("").build()));
        probes.put("retrieveExperiences", () -> client.retrieveExperiences(
                ExperienceRequest.builder().task("").build()));
        probes.put("buildICLPrompt", () -> client.buildICLPrompt(
                ICLPromptRequest.builder().task("").build()));
        probes.put("triggerRefinement", () -> client.triggerRefinement(""));
        probes.put("submitFeedback", () -> client.submitFeedback("", "", null));
        probes.put("updateObservation", () -> client.updateObservation("", ObservationUpdate.builder().build()));
        probes.put("deleteObservation", () -> client.deleteObservation(""));
        probes.put("getQualityDistribution", () -> client.getQualityDistribution(""));
        probes.put("triggerExtraction", () -> client.triggerExtraction(""));
        probes.put("getLatestExtraction", () -> client.getLatestExtraction("", "", null));
        probes.put("getExtractionHistory", () -> client.getExtractionHistory("", "", null, 0));
        probes.put("updateSessionUserId", () -> client.updateSessionUserId("", ""));
        probes.put("search", () -> client.search(new SearchRequest("", null, null, null, null, null, null, null)));
        probes.put("getObservation", () -> client.getObservation(""));
        probes.put("getObservationsByIds", () -> client.getObservationsByIds(List.of(" ")));
        // The remaining API methods have no blank-argument guard at all; they are
        // probed too, so that a guard appearing on one of them would show up as
        // a difference between the runtime and the published table.
        probes.put("listObservations", () -> client.listObservations(
                ObservationsRequest.builder().project("").build()));
        probes.put("healthCheck", () -> client.healthCheck());
        probes.put("getVersion", () -> client.getVersion());
        probes.put("getProjects", () -> client.getProjects());
        probes.put("getStats", () -> client.getStats(""));
        probes.put("getModes", () -> client.getModes());
        probes.put("getSettings", () -> client.getSettings());

        Set<String> guarded = new TreeSet<>();
        for (Map.Entry<String, Call> probe : probes.entrySet()) {
            int before = server.getRequestCount();
            try {
                probe.getValue().run();
            } catch (IllegalArgumentException e) {
                if (server.getRequestCount() == before) {
                    guarded.add(probe.getKey());
                }
            } catch (Exception ignored) {
                // Any other outcome (a rejected request, a returned default)
                // means the method does not guard against a blank argument.
            }
        }
        return guarded;
    }

    /** The method names listed in the published required-argument table. */
    private static Set<String> publishedTable(String fileName, String header) throws IOException {
        Path root = sdkRoot();
        List<String> lines = Files.readAllLines(root.resolve(fileName), StandardCharsets.UTF_8);
        Set<String> methods = new TreeSet<>();
        boolean inTable = false;
        for (String line : lines) {
            if (!inTable) {
                inTable = line.startsWith(header);
                continue;
            }
            if (!line.startsWith("|")) {
                break;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 2) {
                continue;
            }
            String first = columns[1].trim();
            if (first.isEmpty() || first.startsWith("-")) {
                continue;
            }
            methods.add(first.replace("`", ""));
        }
        return methods;
    }

    /** The SDK root, found by walking up from the working directory. */
    private static Path sdkRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("README.md"))
                    && Files.exists(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("cortex-mem-client"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "cannot locate the SDK root from " + System.getProperty("user.dir"));
    }

    /** A call that may throw anything. */
    @FunctionalInterface
    private interface Call {
        void run() throws Exception;
    }

    /** A JSON response the client can actually read. */
    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /**
     * Asserts that the call is rejected client-side, with the expected type and
     * message, and that nothing was sent.
     */
    private void assertGuarded(Call call, Class<? extends Throwable> expectedType, String messageFragment) {
        assertThatThrownBy(call::run)
                .isInstanceOf(expectedType)
                .hasMessageContaining(messageFragment);
        assertThat(server.getRequestCount())
                .as("a client-side guard must reject the call before any request is sent")
                .isZero();
    }
}
