package com.ablueforce.cortexce.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 — the optional bearer gate without a running server.
 *
 * Covers the four behaviours the contract promises: unconfigured passthrough,
 * missing token 401, wrong token 401, correct token passes — plus the scope
 * rule (writes and {@code /api/logs} are gated, ordinary reads are not).
 */
class ApiTokenAuthFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean passes(String configuredToken, String method, String path, String header)
            throws Exception {
        ApiTokenAuthFilter filter = new ApiTokenAuthFilter(configuredToken, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    private JsonNode unauthorizedBody(String configuredToken, String method, String path)
            throws Exception {
        ApiTokenAuthFilter filter = new ApiTokenAuthFilter(configuredToken, MAPPER);
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        assertNotNull(response.getHeader("WWW-Authenticate"));
        return MAPPER.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void unconfiguredTokenLetsEveryRequestThrough() throws Exception {
        assertTrue(passes("", "POST", "/api/ingest/tool-use", null));
        assertTrue(passes("", "GET", "/api/logs", null));
        assertTrue(passes("   ", "DELETE", "/api/sessions/abc", null));
    }

    @Test
    void missingTokenIsRefusedWith401AndEnvelope() throws Exception {
        assertTrue(!passes("secret", "POST", "/api/ingest/tool-use", null));
        JsonNode body = unauthorizedBody("secret", "POST", "/api/ingest/tool-use");
        assertEquals("unauthorized", body.path("error").path("code").asText());
        assertEquals("Missing bearer token", body.path("error").path("message").asText());
    }

    @Test
    void wrongTokenIsRefusedWith401() throws Exception {
        assertTrue(!passes("secret", "POST", "/api/ingest/tool-use", "Bearer wrong"));
        JsonNode body = unauthorizedBody("secret", "POST", "/api/ingest/tool-use");
        assertEquals("unauthorized", body.path("error").path("code").asText());
    }

    @Test
    void correctTokenPasses() throws Exception {
        assertTrue(passes("secret", "POST", "/api/ingest/tool-use", "Bearer secret"));
        assertTrue(passes("secret", "POST", "/api/ingest/tool-use", "bearer secret"));
        assertTrue(passes("secret", "DELETE", "/api/sessions/abc", "Bearer secret"));
    }

    @Test
    void logsAreGatedWhileOrdinaryReadsStayOpen() throws Exception {
        assertTrue(!passes("secret", "GET", "/api/logs", null));
        assertTrue(!passes("secret", "GET", "/api/logs/errors", null));
        assertTrue(passes("secret", "GET", "/api/search?project=/tmp/p", null));
        assertTrue(passes("secret", "GET", "/stream", null));
        assertTrue(passes("secret", "OPTIONS", "/api/ingest/tool-use", null));
    }
}
