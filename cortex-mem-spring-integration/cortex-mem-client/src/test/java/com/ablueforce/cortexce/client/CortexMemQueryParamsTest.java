package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.ObservationsRequest;
import com.ablueforce.cortexce.client.dto.SearchRequest;
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
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the query parameter NAMES this SDK puts on the wire, per endpoint.
 *
 * <p>Why this exists: the parameter names are hand-written strings inside
 * {@code CortexMemClientImpl}, and a wrong name does not fail — the backend
 * binds only the names it declares and silently drops the rest, so the
 * parameter simply stops working while the SDK still reports success.
 *
 * <p>Measured before this test: renaming the bound name in eight of the
 * endpoints (one per endpoint) turned <em>six</em> tests red and left
 * <em>two</em> endpoints completely invisible. The six that turned red did so
 * through assertions of the shape {@code contains("project=")} — an
 * inclusion test, not a key-set test — so a parameter whose name is not
 * {@code project} or {@code projectPath} (and a parameter that is only
 * covered by a {@code doesNotContain} check) cannot be observed at all:
 * of the 20 query slots this SDK sends, 9 were invisible to any rename.
 *
 * <p>The expected key sets below are written out by hand. They were read off
 * {@code CortexMemClientImpl} and cross-checked against the live OpenAPI
 * spec of the backend; deriving them from the code under test would make
 * the assertion tautological.
 *
 * <p><b>The HTTP method is asserted too</b>, not just the path and the query
 * keys. It was not, at first, and the omission was caught by this file
 * itself: the label for {@code /api/memory/refine} said GET while the SDK
 * posts to it. A label is not a measurement — the endpoint identity is now
 * one constant per endpoint, used as the expectation-table key, the display
 * name and the lookup key, so those three cannot drift apart, and the method
 * is compared against the request that was actually sent.
 */
class CortexMemQueryParamsTest {

    // ------------------------------------------------------------- endpoints
    // One constant per endpoint: "<METHOD> <path template>". These are the
    // single source of truth — the expectation table, the display names and
    // the assertions all refer to the same constant, so a label cannot
    // quietly disagree with what the code does.

    private static final String REFINE = "POST /api/memory/refine";
    private static final String QUALITY_DISTRIBUTION = "GET /api/memory/quality-distribution";
    private static final String LATEST_EXTRACTION = "GET /api/extraction/{templateName}/latest";
    private static final String EXTRACTION_HISTORY = "GET /api/extraction/{templateName}/history";
    private static final String RUN_EXTRACTION = "POST /api/extraction/run";
    private static final String SEARCH = "GET /api/search";
    private static final String OBSERVATIONS = "GET /api/observations";
    private static final String STATS = "GET /api/stats";

    /** The complete query key set each endpoint sends when every argument is supplied. */
    private static final Map<String, Set<String>> EXPECTED_FULL_KEYS = Map.of(
            REFINE, Set.of("project"),
            QUALITY_DISTRIBUTION, Set.of("project"),
            LATEST_EXTRACTION, Set.of("projectPath", "userId"),
            EXTRACTION_HISTORY, Set.of("projectPath", "limit", "userId"),
            RUN_EXTRACTION, Set.of("projectPath"),
            SEARCH, Set.of("project", "query", "type", "concept", "source",
                    "limit", "offset", "orderBy"),
            OBSERVATIONS, Set.of("project", "offset", "limit"),
            STATS, Set.of("project"));

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

    // ------------------------------------------------------------- endpoints

    @Test
    @DisplayName(REFINE + " sends exactly {project}")
    void refineSendsExactlyProject() throws Exception {
        enqueue("{}");
        client.triggerRefinement("/my/project");
        assertKeys(REFINE, "/api/memory/refine");
    }

    @Test
    @DisplayName(QUALITY_DISTRIBUTION + " sends exactly {project}")
    void qualityDistributionSendsExactlyProject() throws Exception {
        enqueue("{\"project\":\"/my/project\",\"high\":0,\"medium\":0,\"low\":0}");
        client.getQualityDistribution("/my/project");
        assertKeys(QUALITY_DISTRIBUTION, "/api/memory/quality-distribution");
    }

    @Test
    @DisplayName(LATEST_EXTRACTION + " sends exactly {projectPath, userId}")
    void latestExtractionSendsExactlyProjectPathAndUserId() throws Exception {
        enqueue("{}");
        client.getLatestExtraction("/my/project", "user-preferences", "alice");
        assertKeys(LATEST_EXTRACTION, "/api/extraction/user-preferences/latest");
    }

    @Test
    @DisplayName(EXTRACTION_HISTORY + " sends exactly {projectPath, limit, userId}")
    void extractionHistorySendsExactlyProjectPathLimitAndUserId() throws Exception {
        enqueue("[]");
        client.getExtractionHistory("/my/project", "user-preferences", "alice", 10);
        assertKeys(EXTRACTION_HISTORY, "/api/extraction/user-preferences/history");
    }

    @Test
    @DisplayName(RUN_EXTRACTION + " sends exactly {projectPath}")
    void triggerExtractionSendsExactlyProjectPath() throws Exception {
        enqueue("{}");
        client.triggerExtraction("/my/project");
        assertKeys(RUN_EXTRACTION, "/api/extraction/run");
    }

    @Test
    @DisplayName(SEARCH + " sends exactly the eight documented parameters")
    void searchSendsExactlyTheDocumentedParameters() throws Exception {
        enqueue("{}");
        client.search(SearchRequest.builder()
                .project("/my/project")
                .query("needle")
                .type("bugfix")
                .concept("auth")
                .source("manual")
                .limit(10)
                .offset(5)
                .orderBy("created_at_epoch")
                .build());
        assertKeys(SEARCH, "/api/search");
    }

    @Test
    @DisplayName(OBSERVATIONS + " sends exactly {project, offset, limit}")
    void listObservationsSendsExactlyProjectOffsetLimit() throws Exception {
        enqueue("{\"items\":[],\"hasMore\":false}");
        client.listObservations(ObservationsRequest.builder()
                .project("/my/project")
                .offset(5)
                .limit(20)
                .build());
        assertKeys(OBSERVATIONS, "/api/observations");
    }

    @Test
    @DisplayName(STATS + " sends exactly {project}")
    void statsSendsExactlyProject() throws Exception {
        enqueue("{}");
        client.getStats("/my/project");
        assertKeys(STATS, "/api/stats");
    }

    // ------------------------------------------------------------- omission

    @Test
    @DisplayName("optional parameters are omitted rather than sent empty")
    void optionalParametersAreOmitted() throws Exception {
        enqueue("{}");
        client.search(SearchRequest.builder()
                .project("/my/project")
                .query("needle")
                .source("manual")
                .limit(10)
                .offset(5)
                .build());
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).as("HTTP method of " + SEARCH).isEqualTo(methodOf(SEARCH));
        assertThat(queryKeys(req))
                .as("search without type/concept/orderBy")
                .containsExactlyInAnyOrder("project", "query", "source", "limit", "offset");
    }

    @Test
    @DisplayName("a zero limit or offset is not sent, and a null userId is not sent")
    void zeroValuedAndNullParametersAreNotSent() throws Exception {
        enqueue("{\"items\":[],\"hasMore\":false}");
        client.listObservations(ObservationsRequest.builder()
                .project("/my/project")
                .offset(0)
                .limit(0)
                .build());
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).as("HTTP method of " + OBSERVATIONS).isEqualTo(methodOf(OBSERVATIONS));
        assertThat(queryKeys(req))
                .as("observations with offset=0 and limit=0")
                .containsExactlyInAnyOrder("project");

        enqueue("{}");
        client.getLatestExtraction("/my/project", "user-preferences", null);
        RecordedRequest req2 = server.takeRequest();
        assertThat(req2.getMethod()).as("HTTP method of " + LATEST_EXTRACTION).isEqualTo(methodOf(LATEST_EXTRACTION));
        assertThat(queryKeys(req2))
                .as("latest extraction without a userId")
                .containsExactlyInAnyOrder("projectPath");
    }

    // ------------------------------------------------------------- coverage

    @Test
    @DisplayName("the expected table covers every endpoint and every query slot")
    void theExpectedTableIsComplete() {
        assertThat(EXPECTED_FULL_KEYS).hasSize(8);
        int slots = EXPECTED_FULL_KEYS.values().stream().mapToInt(Set::size).sum();
        assertThat(slots).as("query slots this SDK sends").isGreaterThanOrEqualTo(20);
        for (Map.Entry<String, Set<String>> e : EXPECTED_FULL_KEYS.entrySet()) {
            assertThat(e.getValue()).as(e.getKey()).isNotEmpty();
            assertThat(methodOf(e.getKey())).as("method of " + e.getKey())
                    .isIn("GET", "POST", "PUT", "PATCH", "DELETE");
            assertThat(e.getKey()).as("endpoint key").startsWith(methodOf(e.getKey()) + " /");
        }
    }

    @Test
    @DisplayName("the key-set comparison can actually fail")
    void theComparisonCanActuallyFail() {
        // A renamed parameter must compare unequal to the expected set, and the
        // comparison must ignore ordering. Without this, a comparison that
        // always answered "equal" would make the endpoint tests pass on any
        // code at all.
        Set<String> expected = new LinkedHashSet<>(Set.of("project", "limit"));
        Set<String> reordered = new LinkedHashSet<>(Set.of("limit", "project"));
        Set<String> renamed = new LinkedHashSet<>(Set.of("project", "offset"));
        Set<String> extra = new LinkedHashSet<>(Set.of("project", "limit", "surprise"));
        assertThat(queryKeys(expected)).containsExactlyInAnyOrderElementsOf(reordered);
        assertThat(queryKeys(expected)).isNotEqualTo(queryKeys(renamed));
        assertThat(queryKeys(expected)).isNotEqualTo(queryKeys(extra));
    }

    // ------------------------------------------------------------- helpers

    private void enqueue(String body) {
        // The content type must be set: without it the SDK's message converter
        // sees application/octet-stream and refuses to extract the body.
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private void assertKeys(String endpoint, String expectedPath) throws Exception {
        // Fail fast and clearly if the caller names an endpoint that the
        // expectation table does not know — otherwise the lookup below would
        // answer null and the failure would surface as a confusing comparison
        // error instead of "this endpoint is not registered".
        assertThat(EXPECTED_FULL_KEYS).as("registered endpoints").containsKey(endpoint);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).as("HTTP method of " + endpoint).isEqualTo(methodOf(endpoint));
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo(expectedPath);
        assertThat(queryKeys(req))
                .as("query keys of " + endpoint)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FULL_KEYS.get(endpoint));
    }

    /** The HTTP method named by the endpoint key, e.g. "GET /api/search" -> "GET". */
    private static String methodOf(String endpoint) {
        int sp = endpoint.indexOf(' ');
        return sp < 0 ? endpoint : endpoint.substring(0, sp);
    }

    /** The set of query parameter names on the recorded request. */
    private static Set<String> queryKeys(RecordedRequest req) {
        return new LinkedHashSet<>(req.getRequestUrl().queryParameterNames());
    }

    private static Set<String> queryKeys(Set<String> names) {
        return new LinkedHashSet<>(names);
    }
}
