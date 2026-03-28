package com.ablueforce.cortexce.client;

import com.ablueforce.cortexce.client.config.CortexMemProperties;
import com.ablueforce.cortexce.client.dto.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CortexMemClientImpl using MockWebServer.
 */
class CortexMemClientImplTest {

    private MockWebServer server;
    private CortexMemClient client;
    private CortexMemProperties props;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        props = new CortexMemProperties();
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
    void recordObservation_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.recordObservation(ObservationRequest.builder()
            .sessionId("s1")
            .projectPath("/proj")
            .toolName("Read")
            .toolInput("{}")
            .toolResponse("{}")
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/ingest/tool-use");
        assertThat(req.getBody().readUtf8()).contains("s1", "/proj", "Read");
    }

    @Test
    void recordObservation_withV14Fields_sendsSourceAndExtractedData() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.recordObservation(ObservationRequest.builder()
            .sessionId("s1")
            .projectPath("/proj")
            .toolName("Write")
            .source("manual")
            .extractedData(Map.of("pref", "dark"))
            .build());

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"source\":\"manual\"");
        assertThat(body).contains("\"extractedData\"");
        assertThat(body).contains("\"pref\":\"dark\"");
    }

    @Test
    void updateObservation_emptyUpdate_throws() {
        // Empty update (all null fields) should be rejected before making HTTP call
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.updateObservation("obs-1", ObservationUpdate.EMPTY)
        );
    }

    @Test
    void updateObservation_withExtractedData_sendsCorrectBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.updateObservation("obs-1", ObservationUpdate.builder()
            .source("user_statement")
            .extractedData(Map.of("theme", "light", "lang", "en"))
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PATCH");
        assertThat(req.getPath()).isEqualTo("/api/memory/observations/obs-1");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"source\":\"user_statement\"");
        assertThat(body).contains("\"theme\":\"light\"");
        assertThat(body).contains("\"lang\":\"en\"");
    }

    @Test
    void recordSessionEnd_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.recordSessionEnd(SessionEndRequest.builder()
            .sessionId("s2")
            .projectPath("/app")
            .lastAssistantMessage("Done")
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/ingest/session-end");
        assertThat(req.getBody().readUtf8()).contains("s2", "/app", "Done");
    }

    @Test
    void recordUserPrompt_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.recordUserPrompt(UserPromptRequest.builder()
            .sessionId("s3")
            .projectPath("/x")
            .promptText("hello")
            .promptNumber(1)
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/ingest/user-prompt");
        assertThat(req.getBody().readUtf8()).contains("s3", "hello");
    }

    @Test
    void startSession_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"session_db_id\":\"uuid-123\",\"prompt_number\":1}")
            .addHeader("Content-Type", "application/json"));

        var result = client.startSession(SessionStartRequest.builder()
            .sessionId("s0")
            .projectPath("/my/project")
            .build());

        assertThat(result).containsKey("session_db_id");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/session/start");
        assertThat(req.getBody().readUtf8()).contains("s0", "/my/project");
    }

    @Test
    void retrieveExperiences_returnsList() throws Exception {
        String json = """
            [
              {"id":"a","task":"t1","strategy":"s1","outcome":"o1","reuseCondition":"","qualityScore":0.9,"createdAt":"2026-01-01T00:00:00Z"},
              {"id":"b","task":"t2","strategy":"s2","outcome":"o2","reuseCondition":"","qualityScore":0.8,"createdAt":"2026-01-02T00:00:00Z"}
            ]""";
        server.enqueue(new MockResponse()
            .setBody(json)
            .addHeader("Content-Type", "application/json"));

        List<Experience> result = client.retrieveExperiences(ExperienceRequest.builder()
            .task("current task")
            .project("/proj")
            .count(4)
            .build());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("a");
        assertThat(result.get(0).task()).isEqualTo("t1");
        assertThat(result.get(0).qualityScore()).isEqualTo(0.9f);
        assertThat(result.get(1).id()).isEqualTo("b");
    }

    @Test
    void retrieveExperiences_onFailure_returnsEmptyList() {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<Experience> result = client.retrieveExperiences(ExperienceRequest.builder()
            .task("x")
            .project("/p")
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void buildICLPrompt_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"prompt\":\"Relevant experiences...\",\"experienceCount\":4}")
            .addHeader("Content-Type", "application/json"));

        ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
            .task("fix bug")
            .project("/app")
            .build());

        assertThat(result.prompt()).isEqualTo("Relevant experiences...");
        assertThat(result.experienceCount()).isEqualTo(4);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/memory/icl-prompt");
    }

    @Test
    void buildICLPrompt_onFailure_returnsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(500));

        ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
            .task("x")
            .project("/p")
            .build());

        assertThat(result.prompt()).isEmpty();
        assertThat(result.experienceCount()).isEqualTo(0);
    }

    @Test
    void buildICLPrompt_defaultMaxChars_omitsFromRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"prompt\":\"ok\",\"experienceCount\":2}")
            .addHeader("Content-Type", "application/json"));

        // Builder default: maxChars is null → should NOT appear in request body
        client.buildICLPrompt(ICLPromptRequest.builder()
            .task("fix bug")
            .project("/app")
            .build());

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertThat(body).contains("fix bug");
        assertThat(body).doesNotContain("maxChars");
    }

    @Test
    void buildICLPrompt_explicitMaxChars_includedInRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"prompt\":\"ok\",\"experienceCount\":2}")
            .addHeader("Content-Type", "application/json"));

        client.buildICLPrompt(ICLPromptRequest.builder()
            .task("fix bug")
            .project("/app")
            .maxChars(8000)
            .build());

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertThat(body).contains("maxChars");
        assertThat(body).contains("8000");
    }

    @Test
    void triggerRefinement_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.triggerRefinement("/my/project");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).startsWith("/api/memory/refine");
        assertThat(req.getPath()).contains("project=");
        assertThat(req.getPath()).contains("my/project");
    }

    @Test
    void submitFeedback_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.submitFeedback("obs-uuid", "SUCCESS", "helpful");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/memory/feedback");
        assertThat(req.getBody().readUtf8()).contains("obs-uuid", "SUCCESS", "helpful");
    }

    @Test
    void getQualityDistribution_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"project\":\"/p\",\"high\":10,\"medium\":5,\"low\":3,\"unknown\":2}")
            .addHeader("Content-Type", "application/json"));

        QualityDistribution result = client.getQualityDistribution("/p");

        assertThat(result.project()).isEqualTo("/p");
        assertThat(result.high()).isEqualTo(10);
        assertThat(result.medium()).isEqualTo(5);
        assertThat(result.low()).isEqualTo(3);
        assertThat(result.unknown()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(20);
    }

    @Test
    void getQualityDistribution_onFailure_returnsFallback() {
        server.enqueue(new MockResponse().setResponseCode(500));

        QualityDistribution result = client.getQualityDistribution("/p");

        assertThat(result.project()).isEqualTo("/p");
        assertThat(result.high()).isZero();
        assertThat(result.total()).isZero();
    }

    @Test
    void healthCheck_returnsTrueWhenOk() {
        server.enqueue(new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .addHeader("Content-Type", "application/json"));
        assertThat(client.healthCheck()).isTrue();
    }

    @Test
    void healthCheck_returnsFalseWhenError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThat(client.healthCheck()).isFalse();
    }

    @Test
    void healthCheck_returnsFalseWhenDegraded() {
        server.enqueue(new MockResponse()
            .setBody("{\"status\":\"degraded\"}")
            .addHeader("Content-Type", "application/json"));
        assertThat(client.healthCheck()).isFalse();
    }

    @Test
    void healthCheck_returnsFalseWhenNullBody() {
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.healthCheck()).isFalse();
    }

    @Test
    void apiKey_sendsAuthorizationHeader() throws Exception {
        // Create a new client with apiKey set
        var authProps = new CortexMemProperties();
        authProps.setBaseUrl(server.url("").toString().replaceAll("/$", ""));
        authProps.setApiKey("test-api-key-123");
        authProps.getRetry().setMaxAttempts(1);
        authProps.getRetry().setBackoff(Duration.ZERO);
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(authProps.getBaseUrl())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        var authClient = new CortexMemClientImpl(authProps, builder);

        server.enqueue(new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .addHeader("Content-Type", "application/json"));

        authClient.healthCheck();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-api-key-123");
    }

    @Test
    void noApiKey_omitsAuthorizationHeader() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .addHeader("Content-Type", "application/json"));

        client.healthCheck();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("Authorization")).isNull();
    }

    // ==================== P0 API Tests ====================

    @Test
    void search_sendsCorrectParams() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"observations\":[],\"strategy\":\"hybrid\",\"count\":0}")
            .addHeader("Content-Type", "application/json"));

        client.search(SearchRequest.builder()
            .project("/proj")
            .query("test")
            .source("manual")
            .limit(10)
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).startsWith("/api/search");
        assertThat(req.getPath()).contains("project=");
        assertThat(req.getPath()).contains("query=test");
        assertThat(req.getPath()).contains("source=manual");
    }

    @Test
    void listObservations_sendsCorrectParams() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"items\":[],\"total\":0,\"hasMore\":false}")
            .addHeader("Content-Type", "application/json"));

        client.listObservations(ObservationsRequest.builder()
            .project("/proj")
            .limit(20)
            .offset(0)
            .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).startsWith("/api/observations?");
        assertThat(req.getPath()).contains("project=");
    }

    @Test
    void getObservationsByIds_sendsCorrectBody() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));

        client.getObservationsByIds(List.of("id1", "id2"));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/observations/batch");
        assertThat(req.getBody().readUtf8()).contains("id1", "id2");
    }

    @Test
    void listObservations_emptyProject_omitsParam() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"observations\":[],\"total\":0,\"hasMore\":false}")
            .addHeader("Content-Type", "application/json"));

        // project is optional — null should omit the project query param
        client.listObservations(ObservationsRequest.builder().build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).startsWith("/api/observations");
        assertThat(req.getPath()).doesNotContain("project=");
    }

    @Test
    void getObservationsByIds_emptyIds_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.getObservationsByIds(List.of()));
    }

    @Test
    void getObservationsByIds_exceedsBatchLimit_throws() {
        List<String> tooMany = java.util.stream.IntStream.range(0, 101)
            .mapToObj(i -> "id-" + i)
            .toList();
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.getObservationsByIds(tooMany));
    }

    @Test
    void getObservationsByIds_emptyStringInList_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.getObservationsByIds(List.of("id-1", "", "id-3")));
    }

    @Test
    void getObservationsByIds_nullInList_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.getObservationsByIds(java.util.Arrays.asList("id-1", null, "id-3")));
    }

    @Test
    void getObservationsByIds_whitespaceOnlyInList_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> client.getObservationsByIds(List.of("id-1", "   ", "id-3")));
    }

    // ==================== P1 Management API Tests ====================

    @Test
    void getVersion_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"version\":\"1.0.0\",\"buildTime\":\"2026-01-01\"}")
            .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = client.getVersion();

        assertThat(result).containsKey("version");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/version");
    }

    @Test
    void getProjects_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"projects\":[\"/proj-a\",\"/proj-b\"]}")
            .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = client.getProjects();

        assertThat(result).containsKey("projects");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/projects");
    }

    @Test
    void getStats_withProject_sendsQueryParam() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"totalObservations\":100}")
            .addHeader("Content-Type", "application/json"));

        client.getStats("/my-project");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).startsWith("/api/stats");
        assertThat(req.getPath()).contains("project=");
    }

    @Test
    void getStats_nullProject_omitsParam() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"totalObservations\":0}")
            .addHeader("Content-Type", "application/json"));

        client.getStats(null);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/stats");
    }

    @Test
    void getModes_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"modes\":[{\"name\":\"default\"}]}")
            .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = client.getModes();

        assertThat(result).containsKey("modes");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/modes");
    }

    @Test
    void getSettings_returnsResult() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"embeddingModel\":\"text-embedding-3-small\"}")
            .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = client.getSettings();

        assertThat(result).containsKey("embeddingModel");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/settings");
    }

    // ==================== Extraction API Tests ====================

    @Test
    void getLatestExtraction_sendsCorrectPath() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"result\":\"data\"}")
            .addHeader("Content-Type", "application/json"));

        client.getLatestExtraction("/proj", "user-preferences", "alice");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).contains("/api/extraction/user-preferences/latest");
        assertThat(req.getPath()).contains("projectPath=");
        assertThat(req.getPath()).contains("userId=alice");
        // templateName should NOT be duplicated as query param
        assertThat(req.getPath()).doesNotContain("templateName=");
    }

    @Test
    void getExtractionHistory_sendsCorrectParams() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("[{\"result\":\"v1\"}]")
            .addHeader("Content-Type", "application/json"));

        client.getExtractionHistory("/proj", "user-preferences", "alice", 10);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains("/api/extraction/user-preferences/history");
        assertThat(req.getPath()).contains("limit=10");
        assertThat(req.getPath()).contains("userId=alice");
    }

    @Test
    void getLatestExtraction_nullUserId_omitsParam() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        client.getLatestExtraction("/proj", "user-preferences", null);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).doesNotContain("userId=");
    }

    // ==================== Observation Management Tests ====================
    @Test
    void deleteObservation_sendsDelete() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.deleteObservation("obs-1");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).isEqualTo("/api/memory/observations/obs-1");
    }

    @Test
    void updateSessionUserId_sendsPatch() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = client.updateSessionUserId("sess-1", "user-42");

        assertThat(result).containsKey("status");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PATCH");
        assertThat(req.getPath()).isEqualTo("/api/session/sess-1/user");
        assertThat(req.getBody().readUtf8()).contains("user-42");
    }

    @Test
    void triggerExtraction_sendsCorrectRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.triggerExtraction("/my/project");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).startsWith("/api/extraction/run");
        assertThat(req.getPath()).contains("projectPath=");
        assertThat(req.getPath()).contains("/my/project");
    }

    // ==================== Null Body Handling Tests ====================

    @Test
    void startSession_nullBody_throwsIllegalState() {
        // Server returns 200 with empty body → deserialized as null
        server.enqueue(new MockResponse().setResponseCode(200));

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> client.startSession(SessionStartRequest.builder()
                .sessionId("s1")
                .projectPath("/proj")
                .build()));
    }

    @Test
    void updateSessionUserId_nullBody_throwsIllegalState() {
        server.enqueue(new MockResponse().setResponseCode(200));

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> client.updateSessionUserId("sess-1", "user-1"));
    }

    // ==================== Error Propagation Tests ====================

    @Test
    void startSession_serverError_propagatesException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> client.startSession(SessionStartRequest.builder()
                .sessionId("s1")
                .projectPath("/proj")
                .build()));
    }

    @Test
    void updateSessionUserId_serverError_propagatesException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> client.updateSessionUserId("sess-1", "user-1"));
    }
}
