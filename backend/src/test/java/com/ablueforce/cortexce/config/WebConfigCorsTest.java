package com.ablueforce.cortexce.config;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two CORS fixes from the review, which had no test:
 *
 * <ul>
 *   <li>P2-78 — {@code PATCH} was missing from {@code allowedMethods}, so a browser
 *       preflight for either of the two live PATCH endpoints answered 403 while every
 *       other method answered 200. The fix was client-invisible: it only worked for
 *       callers who configured CORS and used a browser.</li>
 *   <li>P2-80 — origins written the documented way ({@code "a, b"}) kept the leading
 *       space on every element after the first, so only the first origin ever matched.
 *       The registry stores what will be compared against the {@code Origin} header,
 *       so the assertion belongs at that level.</li>
 * </ul>
 */
class WebConfigCorsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, CorsConfiguration> registrations(WebConfig config) {
        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);
        // Spring 6.1 keeps a private List<CorsRegistration>; the protected
        // getCorsConfigurations() turns it into the path -> configuration map that
        // the handler mapping actually consults.
        return (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(
            registry, "getCorsConfigurations");
    }

    private static WebConfig configFor(String origins) {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config;
    }

    @Test
    @DisplayName("the API mapping allows PATCH so both live PATCH endpoints survive preflight")
    void apiMappingAllowsPatch() {
        CorsConfiguration api = registrations(configFor("https://a.example"))
            .get("/api/**");
        assertThat(api).isNotNull();
        assertThat(api.getAllowedMethods())
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    }

    @Test
    @DisplayName("a comma-separated origin list is trimmed, so every origin matches")
    void originsAreTrimmed() {
        CorsConfiguration api = registrations(
            configFor("https://a.example, https://b.example , ")).get("/api/**");
        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins())
            .containsExactly("https://a.example", "https://b.example");
        assertThat(api.getAllowedOrigins())
            .noneMatch(origin -> origin.startsWith(" ") || origin.endsWith(" "));
    }

    @Test
    @DisplayName("the SSE stream stays GET-only and shares the trimmed origin list")
    void streamMappingStaysGetOnly() {
        CorsConfiguration stream = registrations(
            configFor("https://a.example, https://b.example")).get("/stream");
        assertThat(stream).isNotNull();
        assertThat(stream.getAllowedMethods()).containsExactly("GET");
        assertThat(stream.getAllowedOrigins())
            .containsExactly("https://a.example", "https://b.example");
    }

    @Test
    @DisplayName("an empty or blank origin list registers no origins and no credentials")
    void emptyOriginsDenyEverything() {
        CorsConfiguration api = registrations(configFor("  ")).get("/api/**");
        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins()).isEmpty();
        assertThat(api.getAllowCredentials()).isFalse();
    }
}
