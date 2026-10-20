package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.dto.ApiRequests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the machine-readable contract of the two mode-switch endpoints.
 *
 * <p>Background: {@code PUT /api/mode} accepts {@code mode_id} while {@code POST /api/modes}
 * accepts {@code mode}. Both request bodies were declared as records named
 * {@code ModeSwitchRequest}, and springdoc resolves request-body schemas by <em>simple class
 * name</em> — so the two collapsed into one OpenAPI schema. The surviving schema advertised
 * {@code mode} (required) for {@code PUT /api/mode}, whose implementation only ever accepted
 * {@code mode_id}: a client generated from the OpenAPI sent the documented field and got 400.
 *
 * <p>These tests assert three things, all without a database:
 * <ol>
 *   <li>the two request records resolve to two <em>different</em> schemas (no name collision),</li>
 *   <li>each schema advertises exactly the one field its endpoint accepts,</li>
 *   <li>the advertised field name equals the name the serializer actually writes.</li>
 * </ol>
 */
class ModeControllerOpenApiContractTest {

    /** Mirrors {@code spring.jackson.property-naming-strategy: SNAKE_CASE} in application.yml. */
    private static ObjectMapper appObjectMapper() {
        return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    private static ResolvedSchema resolve(Class<?> type) {
        return ModelConverters.getInstance().readAllAsResolvedSchema(type);
    }

    /** The schema that carries the properties of {@code type}, wherever the converter put it. */
    private static Schema<?> schemaWithProperties(ResolvedSchema resolved) {
        Schema<?> root = resolved.schema;
        if (root != null && root.getProperties() != null && !root.getProperties().isEmpty()) {
            return root;
        }
        if (resolved.referencedSchemas.size() == 1) {
            return resolved.referencedSchemas.values().iterator().next();
        }
        return root;
    }

    private static Set<String> advertisedProperties(Class<?> type) {
        return schemaWithProperties(resolve(type)).getProperties().keySet();
    }

    private static Set<String> schemaNames(Class<?> type) {
        return resolve(type).referencedSchemas.keySet();
    }

    @Test
    @DisplayName("PUT /api/mode and POST /api/modes resolve to two distinct OpenAPI schemas")
    void modeSwitchSchemasDoNotCollide() {
        Set<String> putNames = schemaNames(ModeController.ActiveModeSwitchRequest.class);
        Set<String> postNames = schemaNames(ApiRequests.ModeSwitchRequest.class);

        assertFalse(putNames.isEmpty(), "PUT /api/mode must resolve to a named schema");
        assertFalse(postNames.isEmpty(), "POST /api/modes must resolve to a named schema");
        assertNotEquals(putNames, postNames,
                "the two mode-switch request bodies must not collapse into one schema name");
    }

    @Test
    @DisplayName("PUT /api/mode advertises mode_id, POST /api/modes advertises mode")
    void modeSwitchSchemasAdvertiseTheAcceptedFieldOnly() {
        assertEquals(Set.of("mode_id"), advertisedProperties(ModeController.ActiveModeSwitchRequest.class),
                "PUT /api/mode accepts mode_id only");
        assertEquals(Set.of("mode"), advertisedProperties(ApiRequests.ModeSwitchRequest.class),
                "POST /api/modes accepts mode only");
    }

    @Test
    @DisplayName("the advertised field name is the name the serializer actually writes")
    void advertisedNameMatchesTheWireName() throws Exception {
        ObjectMapper mapper = appObjectMapper();

        String putJson = mapper.writeValueAsString(new ModeController.ActiveModeSwitchRequest("code--zh"));
        @SuppressWarnings("unchecked")
        Map<String, Object> putMap = mapper.readValue(putJson, Map.class);
        assertEquals(Set.of("mode_id"), putMap.keySet(),
                "PUT /api/mode body must serialize to mode_id — the advertised name is the wire name");
        assertFalse(putMap.containsKey("mode"), "PUT /api/mode must not accept the other endpoint's field");

        String postJson = mapper.writeValueAsString(new ApiRequests.ModeSwitchRequest("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> postMap = mapper.readValue(postJson, Map.class);
        assertEquals(Set.of("mode"), postMap.keySet(),
                "POST /api/modes body must serialize to mode — the advertised name is the wire name");
        assertTrue(postMap.containsKey("mode"), "POST /api/modes must still accept mode");
    }

    private static io.swagger.v3.oas.annotations.responses.ApiResponse responseFor(String code)
            throws NoSuchMethodException {
        Method method = ModeController.class.getMethod("setActiveMode", ModeController.ActiveModeSwitchRequest.class);
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertNotNull(responses, "PUT /api/mode must declare its responses");
        for (io.swagger.v3.oas.annotations.responses.ApiResponse response : responses.value()) {
            if (code.equals(response.responseCode())) {
                return response;
            }
        }
        fail("PUT /api/mode declares no " + code + " response");
        return null;
    }

    @Test
    @DisplayName("the 400 response declares an empty body — the implementation returns an empty body")
    void badRequestResponseDeclaresAnEmptyBody() throws Exception {
        io.swagger.v3.oas.annotations.responses.ApiResponse bad = responseFor("400");
        assertTrue(bad.content().length > 0,
                "the 400 must declare an explicit (empty) body shape — without one springdoc falls back "
                        + "to the response type and advertises a JSON body the endpoint never sends");
        String example = bad.content()[0].schema().example();
        assertNotNull(example, "the 400 must declare its own example, not a schema reference");
        assertEquals("", example, "the 400 example must be empty — the implementation returns no body");
    }

    @Test
    @DisplayName("the response examples use the wire field names, not the Java field names")
    void responseExamplesUseWireFieldNames() throws Exception {
        io.swagger.v3.oas.annotations.responses.ApiResponse ok = responseFor("200");
        assertTrue(ok.content().length > 0, "the 200 response must declare its example");
        String example = ok.content()[0].schema().example();
        assertTrue(example.contains("\"mode_id\""),
                "the 200 example must use the wire name mode_id (was: " + example + ")");
        assertFalse(example.contains("modeId"),
                "the 200 example must not use the Java field name modeId (was: " + example + ")");
        assertFalse(example.contains("observationTypes") || example.contains("observationConcepts"),
                "the 200 example must use the wire names observation_types/observation_concepts");
    }
}
