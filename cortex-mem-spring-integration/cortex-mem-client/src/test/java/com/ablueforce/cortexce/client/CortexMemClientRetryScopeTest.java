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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the Java SDK's <em>retry scope</em>: exactly which of the 25 public methods retry a
 * transient failure, how many requests each one makes, and whether the exhausted failure is
 * swallowed or propagated.
 *
 * <p>Why this needs pinning. The README documents the split in detail — 10 of the 25 public
 * methods retry, five of those swallow their errors once the attempts are exhausted, five
 * propagate, and the remaining 15 never retry — and the three other SDKs retry a visibly
 * smaller set (three methods each). That difference is a cross-SDK contract a caller has to
 * work around, yet nothing in this module asserted it.
 *
 * <p>Why the existing suite could not see it. Every existing case builds the client with
 * {@code retry.max-attempts = 1}. With one attempt allowed, a method that retries and a
 * method that does not send <em>exactly the same number of requests</em> — one. The
 * swallow-contract cases assert {@code requestCount == 1}, and that assertion holds for a
 * method that never retries at all. So the whole retry path was invisible: moving
 * {@code recordObservation} off {@code executeWithRetrySilent}, or adding a retry to
 * {@code search}, would leave every test in the module green.
 *
 * <p>What is asserted.
 * <ol>
 *   <li><b>The request count per method</b> — 3 for the ten retrying methods, 1 for the
 *       fifteen that do not. This is the retry scope itself.</li>
 *   <li><b>Swallow vs propagate</b> for the ten retrying methods, which is the second half
 *       of the documented split: the five capture/async calls swallow, the three mutations
 *       and the two extraction reads propagate.</li>
 *   <li><b>That the table covers every public method</b> — the expected method names are
 *       compared against {@link CortexMemClient}'s public methods by reflection, so a new
 *       public method cannot be added without a decision about its retry behaviour, and a
 *       row cannot be quietly dropped.</li>
 *   <li><b>That the harness can tell the two apart</b> — the same call is made once with one
 *       attempt allowed and once with three, so a suite-wide {@code max-attempts = 1} (or a
 *       broken request counter) cannot make the assertions above pass vacuously.</li>
 * </ol>
 */
class CortexMemClientRetryScopeTest {

    /** Attempts allowed in the scope assertions. */
    private static final int MAX_ATTEMPTS = 3;

    /** A retryable status: 503 is retried, 500 deliberately is not. */
    private static final int RETRYABLE_STATUS = 503;

    private MockWebServer server;
    private CortexMemProperties props;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("").toString().replaceAll("/$", "");
        props = new CortexMemProperties();
        props.setBaseUrl(baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ---------------------------------------------------------------- the table

    /** A call that may throw. */
    @FunctionalInterface
    private interface Call {
        void run() throws Exception;
    }

    /**
     * One public method: its name, how many requests it makes against a retryable failure,
     * whether the exhausted failure is swallowed, and how to invoke it.
     */
    private record Case(String method, int expectedAttempts, boolean swallows,
                        Function<CortexMemClient, Call> invocation) {
        @Override
        public String toString() {
            return method;
        }
    }

    /**
     * The expected retry scope. The argument shapes are copied from
     * {@code CortexMemClientImplTest} rather than written from scratch, so a case here
     * cannot fail for an unrelated reason (a blank project path, a missing session id).
     */
    private static List<Case> cases() {
        String project = absoluteProjectPath();
        return List.of(
            // ---- the ten that retry -------------------------------------------------
            new Case("recordObservation", MAX_ATTEMPTS, true, c -> () -> c.recordObservation(
                    ObservationRequest.builder().sessionId("s1").projectPath(project).toolName("Read").build())),
            new Case("recordSessionEnd", MAX_ATTEMPTS, true, c -> () -> c.recordSessionEnd(
                    SessionEndRequest.builder().sessionId("s1").projectPath(project).build())),
            new Case("recordUserPrompt", MAX_ATTEMPTS, true, c -> () -> c.recordUserPrompt(
                    UserPromptRequest.builder().sessionId("s1").projectPath(project).promptText("hello").build())),
            new Case("triggerRefinement", MAX_ATTEMPTS, true, c -> () -> c.triggerRefinement(project)),
            new Case("triggerExtraction", MAX_ATTEMPTS, true, c -> () -> c.triggerExtraction(project)),
            new Case("submitFeedback", MAX_ATTEMPTS, false, c -> () -> c.submitFeedback("obs-uuid", "SUCCESS", "helpful")),
            new Case("updateObservation", MAX_ATTEMPTS, false, c -> () -> c.updateObservation("obs-1",
                    ObservationUpdate.builder().source("user_statement").build())),
            new Case("deleteObservation", MAX_ATTEMPTS, false, c -> () -> c.deleteObservation("obs-1")),
            new Case("getLatestExtraction", MAX_ATTEMPTS, false,
                    c -> () -> c.getLatestExtraction(project, "user-preferences", "alice")),
            new Case("getExtractionHistory", MAX_ATTEMPTS, false,
                    c -> () -> c.getExtractionHistory(project, "user-preferences", "alice", 10)),

            // ---- the fifteen that do not ---------------------------------------------
            new Case("startSession", 1, false, c -> () -> c.startSession(
                    SessionStartRequest.builder().sessionId("s0").projectPath(project).build())),
            new Case("updateSessionUserId", 1, false, c -> () -> c.updateSessionUserId("sess-1", "user-1")),
            new Case("retrieveExperiences", 1, false, c -> () -> c.retrieveExperiences(
                    ExperienceRequest.builder().task("current task").project(project).count(4).build())),
            new Case("buildICLPrompt", 1, false, c -> () -> c.buildICLPrompt(
                    ICLPromptRequest.builder().task("fix bug").project(project).build())),
            new Case("getQualityDistribution", 1, false, c -> () -> c.getQualityDistribution(project)),
            new Case("healthCheck", 1, false, c -> () -> c.healthCheck()),
            new Case("search", 1, false, c -> () -> c.search(
                    SearchRequest.builder().project(project).query("test").source("manual").limit(10).build())),
            new Case("listObservations", 1, false, c -> () -> c.listObservations(
                    ObservationsRequest.builder().project(project).limit(20).offset(0).build())),
            new Case("getObservation", 1, false, c -> () -> c.getObservation("obs-42")),
            new Case("getObservationsByIds", 1, false, c -> () -> c.getObservationsByIds(List.of("id1", "id2"))),
            new Case("getVersion", 1, false, c -> () -> c.getVersion()),
            new Case("getProjects", 1, false, c -> () -> c.getProjects()),
            new Case("getStats", 1, false, c -> () -> c.getStats(project)),
            new Case("getModes", 1, false, c -> () -> c.getModes()),
            new Case("getSettings", 1, false, c -> () -> c.getSettings())
        );
    }

    static Stream<Case> allCases() {
        return cases().stream();
    }

    static Stream<Case> retryingCases() {
        return cases().stream().filter(c -> c.expectedAttempts() > 1);
    }

    // ---------------------------------------------------------------- the assertions

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    void retryScope_isPinned(Case testCase) {
        CortexMemClient client = clientWith(MAX_ATTEMPTS);
        enqueueRetryableFailures(MAX_ATTEMPTS);

        // The error behaviour of a method is a separate axis (which errors are swallowed);
        // this case asserts the number of attempts, so any exception is simply absorbed.
        try {
            testCase.invocation().apply(client).run();
        } catch (Exception ignored) {
            // asserted separately, where it matters
        }

        assertThat(server.getRequestCount())
                .as("%s: expected %d request(s) against a %d, got %d",
                        testCase.method(), testCase.expectedAttempts(), RETRYABLE_STATUS, server.getRequestCount())
                .isEqualTo(testCase.expectedAttempts());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retryingCases")
    void retryingMethods_swallowOrPropagateTheExhaustedFailure(Case testCase) {
        CortexMemClient client = clientWith(MAX_ATTEMPTS);
        enqueueRetryableFailures(MAX_ATTEMPTS);

        if (testCase.swallows()) {
            assertThatCode(() -> testCase.invocation().apply(client).run())
                    .as("%s swallows the exhausted failure", testCase.method())
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> testCase.invocation().apply(client).run())
                    .as("%s propagates the exhausted failure", testCase.method())
                    .isNotNull();
        }
        assertThat(server.getRequestCount())
                .as("%s must have spent every attempt before giving up", testCase.method())
                .isEqualTo(MAX_ATTEMPTS);
    }

    /**
     * The table has to cover every public method of the client, otherwise a method could
     * retry (or stop retrying) without any assertion noticing.
     */
    @Test
    void caseTable_coversEveryPublicMethodOfTheClient() {
        Set<String> declared = Arrays.stream(CortexMemClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> tabulated = cases().stream()
                .map(Case::method)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertThat(tabulated)
                .as("every public method of CortexMemClient needs a retry-scope row")
                .isEqualTo(declared);
        assertThat(cases()).as("the pinned surface").hasSize(25);
        assertThat(retryingCases().count()).as("the number of methods that retry").isEqualTo(10);
        assertThat(cases().stream().filter(c -> c.expectedAttempts() == 1).count())
                .as("the number of methods that never retry").isEqualTo(15);
    }

    /**
     * Positive control: the same call, once with a single attempt allowed and once with
     * three. If the harness could not see the difference, the scope assertions above would
     * pass for the wrong reason — which is exactly how the gap this class closes survived
     * in the first place.
     */
    @Test
    void harness_canTellARetriedCallFromASingleAttempt() {
        CortexMemClient single = clientWith(1);
        enqueueRetryableFailures(MAX_ATTEMPTS);
        int before = server.getRequestCount();
        try {
            cases().stream()
                    .filter(c -> c.method().equals("recordObservation"))
                    .findFirst()
                    .orElseThrow()
                    .invocation().apply(single).run();
        } catch (Exception ignored) {
            // swallowed by the client under test; irrelevant to the request count
        }
        assertThat(server.getRequestCount() - before)
                .as("with one attempt allowed the client sends exactly one request")
                .isEqualTo(1);

        CortexMemClient triple = clientWith(MAX_ATTEMPTS);
        enqueueRetryableFailures(MAX_ATTEMPTS);
        before = server.getRequestCount();
        try {
            cases().stream()
                    .filter(c -> c.method().equals("recordObservation"))
                    .findFirst()
                    .orElseThrow()
                    .invocation().apply(triple).run();
        } catch (Exception ignored) {
            // same
        }
        assertThat(server.getRequestCount() - before)
                .as("with three attempts allowed the same call is sent three times")
                .isEqualTo(MAX_ATTEMPTS);
    }

    // ---------------------------------------------------------------- helpers

    private CortexMemClient clientWith(int maxAttempts) {
        props.getRetry().setMaxAttempts(maxAttempts);
        props.getRetry().setBackoff(Duration.ZERO);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return new CortexMemClientImpl(props, builder);
    }

    private void enqueueRetryableFailures(int count) {
        for (int i = 0; i < count; i++) {
            server.enqueue(new MockResponse().setResponseCode(RETRYABLE_STATUS));
        }
    }

    /**
     * An absolute path, so a case cannot fail the project-path validation on a platform
     * where "/proj" is not absolute. Copied from {@code CortexMemClientImplTest}.
     */
    private static String absoluteProjectPath() {
        return Paths.get("/proj").toAbsolutePath().toString();
    }
}
