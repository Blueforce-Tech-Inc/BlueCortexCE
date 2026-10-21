package com.ablueforce.cortexce.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the part of the backend's exposed surface that
 * {@link BackendEndpointSurfaceTest} cannot see: the operations that are
 * <em>not</em> declared by a {@code @RestController} method.
 *
 * <p>Why this needs pinning. The controller sweep discovers endpoints by
 * reflecting over the {@code @*Mapping} annotations, so it sees the REST layer
 * and nothing else. The running server publishes more than that: the MCP
 * transport and the Spring Boot actuator are registered by the framework, and
 * springdoc does not publish either. Measured 2026-10-21 against a running
 * instance, the REST layer is 71 operations across 66 paths and the whole
 * server answers <b>77</b> operations across <b>72</b> paths — the six
 * operations this test pins were outside the only existing surface check, and
 * outside every other test in the module.
 *
 * <p>What was at stake is not academic. The MCP endpoint is a user-facing
 * integration surface: {@code proxy/CLAUDE-CODE-INTEGRATION.md} and
 * {@code proxy/CURSOR-INTEGRATION.md} both instruct users to register
 * {@code http://127.0.0.1:37777/sse}. That path comes from
 * {@code spring.ai.mcp.server.sse-endpoint} in {@code application.yml}, and
 * nothing in the build was watching it: changing the configuration moved the
 * endpoint the integration guides tell users to use, and no test turned red.
 *
 * <p>What is asserted.
 * <ol>
 *   <li><b>The pinned surface is derived from the configuration</b>, not
 *       hand-copied next to it: the MCP transport follows
 *       {@code spring.ai.mcp.server.protocol} and the actuator exposure
 *       follows {@code management.endpoints.web.exposure.include}. Changing
 *       either one therefore turns this test red instead of leaving the
 *       expectation behind.</li>
 *   <li><b>Set equality</b>, not containment.</li>
 *   <li><b>The configuration really was read</b> — a broken reader must not
 *       report an empty, matching surface and pass.</li>
 * </ol>
 *
 * <p>The running server is compared against this surface by
 * {@code scripts/check-api-doc-surface.py}, which discovers the actuator
 * endpoints from the live {@code /actuator} links page and probes the MCP
 * transport; this test is the offline half of the same pair.
 */
class BackendTransportEndpointSurfaceTest {

    /**
     * Every operation the running server publishes outside the
     * {@code @RestController} layer, as served on 2026-10-21 and re-derived
     * from {@code application.yml} by the discovery pass below.
     */
    private static final List<String> EXPECTED_TRANSPORT_OPERATIONS = List.of(
            "GET /actuator",
            "GET /actuator/health",
            "GET /actuator/info",
            "GET /actuator/metrics",
            "GET /sse",
            "POST /mcp/message"
    );

    @Test
    @DisplayName("the transport surface is exactly what the configuration produces")
    void transportSurfaceIsPinned() {
        Set<String> derived = deriveFromConfiguration();

        assertEquals(new TreeSet<>(EXPECTED_TRANSPORT_OPERATIONS), derived,
                "the operations the server publishes outside the @RestController layer "
                        + "changed. The MCP transport and the actuator are registered by the "
                        + "framework, so no controller sweep can see them, and the MCP endpoint is "
                        + "a user-facing URL that the integration guides tell users to register. "
                        + "Update EXPECTED_TRANSPORT_OPERATIONS and docs/API.md (and its Chinese "
                        + "counterpart) together.");
    }

    @Test
    @DisplayName("the configuration really was read")
    void configurationIsRead() {
        Properties properties = applicationProperties();

        String protocol = properties.getProperty("spring.ai.mcp.server.protocol");
        assertNotNull(protocol, "spring.ai.mcp.server.protocol must be readable from application.yml");
        assertTrue(List.of("SSE", "STREAMABLE", "STATELESS").contains(protocol.trim().toUpperCase()),
                "unexpected MCP protocol: " + protocol);

        String sseEndpoint = properties.getProperty("spring.ai.mcp.server.sse-endpoint");
        String sseMessage = properties.getProperty("spring.ai.mcp.server.sse-message-endpoint");
        assertNotNull(sseEndpoint, "spring.ai.mcp.server.sse-endpoint must be readable");
        assertNotNull(sseMessage, "spring.ai.mcp.server.sse-message-endpoint must be readable");
        assertTrue(sseEndpoint.startsWith("/") && sseMessage.startsWith("/"),
                "the MCP endpoints must be absolute paths: " + sseEndpoint + ", " + sseMessage);

        String exposure = properties.getProperty("management.endpoints.web.exposure.include");
        assertNotNull(exposure, "management.endpoints.web.exposure.include must be readable");
        assertTrue(exposure.contains("health"),
                "the actuator exposure list should name the health endpoint: " + exposure);
    }

    @Test
    @DisplayName("the pinned transport surface is not vacuous")
    void pinnedSurfaceIsNotVacuous() {
        assertEquals(6, EXPECTED_TRANSPORT_OPERATIONS.size(),
                "the pinned transport surface is expected to hold 6 operations");
        assertEquals(6, distinctPathCount(),
                "the pinned transport surface is expected to span 6 distinct paths");
        assertEquals(6, deriveFromConfiguration().size(),
                "the configuration must still describe six transport operations");
    }

    private int distinctPathCount() {
        Set<String> paths = new TreeSet<>();
        for (String operation : EXPECTED_TRANSPORT_OPERATIONS) {
            paths.add(operation.substring(operation.indexOf(' ') + 1));
        }
        return paths.size();
    }

    /**
     * The transport surface the configuration produces.
     *
     * <p>The MCP transport depends on the configured protocol: the SSE
     * transport publishes an event stream plus a message endpoint, while the
     * streamable-HTTP and stateless transports publish a single POST endpoint.
     * The actuator publishes its links page (unless discovery is switched off)
     * plus one endpoint per name in the exposure list.
     */
    private Set<String> deriveFromConfiguration() {
        Properties properties = applicationProperties();
        Set<String> operations = new TreeSet<>();

        String protocol = String.valueOf(properties.getProperty("spring.ai.mcp.server.protocol")).trim();
        if ("SSE".equalsIgnoreCase(protocol)) {
            operations.add("GET " + properties.getProperty("spring.ai.mcp.server.sse-endpoint").trim());
            operations.add("POST " + properties.getProperty("spring.ai.mcp.server.sse-message-endpoint").trim());
        } else {
            operations.add("POST "
                    + properties.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint").trim());
        }

        String discovery = properties.getProperty("management.endpoints.web.discovery.enabled", "true").trim();
        if (Boolean.parseBoolean(discovery)) {
            operations.add("GET /actuator");
        }
        String exposure = properties.getProperty("management.endpoints.web.exposure.include", "health");
        for (String name : exposure.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                operations.add("GET /actuator/" + trimmed);
            }
        }
        return operations;
    }

    /** The backend's own {@code application.yml}, flattened into dotted keys. */
    private Properties applicationProperties() {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        Properties properties = new Properties();
        try {
            List<PropertySource<?>> sources =
                    loader.load("application", new ClassPathResource("application.yml"));
            for (PropertySource<?> source : sources) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                    throw new IllegalStateException(
                            "application.yml was loaded as a source that cannot be enumerated: "
                                    + source.getClass().getName());
                }
                for (String name : enumerable.getPropertyNames()) {
                    Object value = source.getProperty(name);
                    if (value != null) {
                        properties.setProperty(name, String.valueOf(value));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read application.yml from the classpath", e);
        }
        if (properties.isEmpty()) {
            throw new IllegalStateException(
                    "application.yml was found but carried no properties; the comparison below "
                            + "would be vacuous");
        }
        return properties;
    }
}
