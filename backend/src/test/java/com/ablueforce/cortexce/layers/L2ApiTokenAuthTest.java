package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.offline.OfflineAiTestModels;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2 — optional bearer-token gate, configured.
 *
 * <p>The token is set, so every write under {@code /api/**} and the operator-only
 * {@code GET /api/logs/**} read must present {@code Authorization: Bearer <token>}.
 * Missing and wrong credentials both answer {@code 401} with the standard envelope
 * ({@code error.code = unauthorized}) and a {@code WWW-Authenticate} challenge.
 * Ordinary reads stay open because the Viewer UI's {@code EventSource} cannot attach
 * headers.
 *
 * <p>The unconfigured case (no token → writes pass) is covered by every other L2
 * test in this suite, which runs against the default configuration.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "claudemem.stale-message.recovery-interval-ms=86400000",
                "app.memory.refine-enabled=false",
                "claudemem.max-retries=3",
                "claudemem.sse.timeout-ms=30000",
                "cortexce.security.api-token=l2-secret-token",
                "logging.level.com.ablueforce=WARN",
                "logging.level.org.springframework.web=WARN"
        })
@Import(OfflineAiTestModels.class)
@EnabledIfEnvironmentVariable(
        named = "MEMSCOPE_TEST_DB_URL",
        matches = "jdbc:postgresql://[^/]+/[^?]*_flyway_rehearsal(?:\\?.*)?")
class L2ApiTokenAuthTest extends HttpLayerTestSupport {

    private static final String TOKEN = "l2-secret-token";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEMSCOPE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("MEMSCOPE_TEST_DB_PASSWORD", "123456"));
    }

    @Test
    @DisplayName("a write without a token is refused with 401 and the unified error envelope")
    void writeWithoutTokenIsRefused() {
        ResponseEntity<String> response = exchangeWithAuth(null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getBody());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        JsonNode body = json(response);
        assertEquals("unauthorized", body.path("error").path("code").asText());
        assertEquals("Missing bearer token", body.path("error").path("message").asText());
        assertEquals("Missing bearer token", body.path("message").asText());
        assertTrue(body.path("error").path("details").isMissingNode());
    }

    @Test
    @DisplayName("a write with a wrong token is refused with 401")
    void writeWithWrongTokenIsRefused() {
        ResponseEntity<String> response = exchangeWithAuth("Bearer not-the-token", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getBody());
        assertEquals("unauthorized", json(response).path("error").path("code").asText());
    }

    @Test
    @DisplayName("a write with a malformed scheme is refused with 401")
    void writeWithMalformedSchemeIsRefused() {
        ResponseEntity<String> response = exchangeWithAuth("Basic " + TOKEN, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), response.getBody());
    }

    @Test
    @DisplayName("a write with the correct token passes the gate and reaches validation")
    void writeWithCorrectTokenPassesTheGate() {
        ResponseEntity<String> response = exchangeWithAuth("Bearer " + TOKEN,
            json(Map.of("tool_name", "Read")));
        assertFalse(response.getStatusCode() == HttpStatus.UNAUTHORIZED, response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
        JsonNode body = json(response);
        assertEquals("invalid_request", body.path("error").path("code").asText());
        assertEquals("Missing required field: session_id",
            body.path("error").path("message").asText());
    }

    @Test
    @DisplayName("the sensitive logs read needs a token while ordinary reads stay open")
    void sensitiveReadNeedsTokenButSearchStaysOpen() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/logs").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/logs/errors").getStatusCode());

        ResponseEntity<String> search = get("/api/search?project=/tmp/none");
        assertEquals(HttpStatus.OK, search.getStatusCode(), search.getBody());

        ResponseEntity<String> logsWithToken = exchange(HttpMethod.GET, "/api/logs", null,
            "Bearer " + TOKEN);
        assertFalse(logsWithToken.getStatusCode() == HttpStatus.UNAUTHORIZED, logsWithToken.getBody());
    }

    @Test
    @DisplayName("an unknown API route answers the unified 404 envelope")
    void unknownRouteAnswersUnifiedNotFound() {
        ResponseEntity<String> response = get("/api/definitely-not-a-route");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
        JsonNode body = json(response);
        assertEquals("not_found", body.path("error").path("code").asText());
        assertEquals("Resource not found", body.path("error").path("message").asText());
    }

    private ResponseEntity<String> exchangeWithAuth(String authorization, String body) {
        return exchange(HttpMethod.POST, "/api/ingest/tool-use", body, authorization);
    }

    private ResponseEntity<String> exchange(org.springframework.http.HttpMethod method, String path,
                                            String body, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return rest.exchange(path, method,
            new org.springframework.http.HttpEntity<>(body, headers), String.class);
    }
}
