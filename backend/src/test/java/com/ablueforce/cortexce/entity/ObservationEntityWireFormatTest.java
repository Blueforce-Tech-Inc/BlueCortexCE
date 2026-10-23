package com.ablueforce.cortexce.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wire format of {@link ObservationEntity} and — more importantly — the invariant that
 * the machine-readable contract advertises <em>exactly</em> the fields the entity serializes.
 *
 * <p>The entity is a JPA mapping class, so its JSON names come from two different places: the
 * explicit {@code @JsonProperty} annotations and the global
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE}. springdoc resolves schemas by
 * reflection with its own mapper and does <em>not</em> apply that naming strategy, so every
 * field without an explicit annotation used to be advertised under a camelCase name the runtime
 * never emits — while the snake_case name the runtime does emit was missing from the schema.
 * A client generated from the OpenAPI therefore read {@code null} for those fields and expected
 * fields that never arrive.
 */
class ObservationEntityWireFormatTest {

    /** Mirrors {@code spring.jackson.property-naming-strategy: SNAKE_CASE} in application.yml. */
    private static ObjectMapper appObjectMapper() {
        return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    private static Set<String> advertisedProperties(Class<?> type) {
        ResolvedSchema resolved = ModelConverters.getInstance().readAllAsResolvedSchema(type);
        Schema<?> root = resolved.schema;
        if (root == null || root.getProperties() == null || root.getProperties().isEmpty()) {
            root = resolved.referencedSchemas.values().iterator().next();
        }
        return root.getProperties().keySet();
    }

    private static ObservationEntity sample() {
        ObservationEntity entity = new ObservationEntity();
        entity.setContentSessionId("session-1");
        entity.setProjectPath("/tmp/project");
        entity.setType("feature");
        entity.setTitle("title");
        entity.setFacts(List.of("fact-a"));
        entity.setConcepts(List.of("concept-a"));
        entity.setFilesRead(List.of("src/Main.java"));
        entity.setFilesModified(List.of("src/Main.java"));
        entity.setContentHash("hash-1");
        entity.setDiscoveryTokens(7);
        entity.setEmbeddingModelId("bge-m3");
        entity.setPromptNumber(3);
        entity.setStepNumber(2);
        return entity;
    }

    @Test
    @DisplayName("the OpenAPI schema advertises exactly the fields the entity serializes")
    void advertisedSchemaMatchesTheWireFormat() throws Exception {
        String json = appObjectMapper().writeValueAsString(sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> wire = appObjectMapper().readValue(json, Map.class);

        Set<String> advertised = advertisedProperties(ObservationEntity.class);
        Set<String> actual = new LinkedHashSet<>(wire.keySet());

        Set<String> phantom = new LinkedHashSet<>(advertised);
        phantom.removeAll(actual);
        Set<String> missing = new LinkedHashSet<>(actual);
        missing.removeAll(advertised);

        assertEquals(Set.of(), phantom, "OpenAPI advertises fields the entity never serializes: " + phantom);
        assertEquals(Set.of(), missing, "OpenAPI is missing fields the entity does serialize: " + missing);
        assertEquals(actual, advertised,
                "OpenAPI property set must equal the serialized key set");
    }

    @Test
    @DisplayName("conversation_id and standalone_kind are ALWAYS present, even when null")
    void identityFieldsAreAlwaysPresent() throws Exception {
        String json = appObjectMapper().writeValueAsString(sample());
        // The raw text, not a deserialized map: a map would drop nulls and hide the bug
        // where a standalone row omits conversation_id instead of emitting null.
        assertTrue(json.contains("\"conversation_id\":null"),
            "observation without a conversation must emit conversation_id:null; got " + json);
        assertTrue(json.contains("\"standalone_kind\":null"),
            "conversation-owned rows must emit standalone_kind:null; got " + json);

        ObservationEntity standalone = sample();
        standalone.setConversationId(null);
        standalone.setStandaloneKind("manual");
        String standaloneJson = appObjectMapper().writeValueAsString(standalone);
        assertTrue(standaloneJson.contains("\"conversation_id\":null"));
        assertTrue(standaloneJson.contains("\"standalone_kind\":\"manual\""));
    }

    @Test
    @DisplayName("memory_scope_id never appears on the wire or in the OpenAPI schema")
    void memoryScopeIdIsNeverExposed() throws Exception {
        ObservationEntity entity = sample();
        entity.setMemoryScopeId(java.util.UUID.randomUUID());
        String json = appObjectMapper().writeValueAsString(entity);

        // The wire keeps project/project_path as the caller-facing scope selector; a local
        // database UUID must not leak, or clients would start persisting a per-deployment id.
        assertTrue(!json.contains("memory_scope_id"), "wire leaked memory_scope_id: " + json);
        assertTrue(!json.contains("memoryScopeId"), "wire leaked memoryScopeId: " + json);
        assertTrue(!advertisedProperties(ObservationEntity.class).contains("memory_scope_id"),
            "OpenAPI must not advertise memory_scope_id");
        assertTrue(json.contains("\"memory_scope_type\":null"),
            "scope kind is a stable, non-UUID export field");
    }

    @Test
    @DisplayName("files_read / files_modified stay JSON-encoded strings, not arrays")
    void jsonEncodedListFieldsRemainStrings() throws Exception {
        String json = appObjectMapper().writeValueAsString(sample());
        @SuppressWarnings("unchecked")
        Map<String, Object> wire = appObjectMapper().readValue(json, Map.class);

        // The entity exposes getFilesReadJson() for WebUI compatibility; that getter owns the
        // "files_read" key, so the value is a JSON-encoded string, not a JSON array.
        assertTrue(wire.get("files_read") instanceof String,
                "files_read must be a JSON-encoded string (was: " + wire.get("files_read") + ")");
        assertTrue(wire.get("files_modified") instanceof String,
                "files_modified must be a JSON-encoded string (was: " + wire.get("files_modified") + ")");
        assertTrue(wire.get("facts") instanceof String,
                "facts must be a JSON-encoded string (was: " + wire.get("facts") + ")");
        assertTrue(wire.get("concepts") instanceof String,
                "concepts must be a JSON-encoded string (was: " + wire.get("concepts") + ")");
    }
}
