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
            .setBody("{\"prompt\":\"Relevant experiences...\",\"experienceCount\":\"4\"}")
            .addHeader("Content-Type", "application/json"));

        ICLPromptResult result = client.buildICLPrompt(ICLPromptRequest.builder()
            .task("fix bug")
            .project("/app")
            .build());

        assertThat(result.prompt()).isEqualTo("Relevant experiences...");
        assertThat(result.experienceCountAsInt()).isEqualTo(4);
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
        assertThat(result.experienceCountAsInt()).isEqualTo(0);
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
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThat(client.healthCheck()).isTrue();
    }

    @Test
    void healthCheck_returnsFalseWhenError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThat(client.healthCheck()).isFalse();
    }
}
