package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.dto.ApiRequests.CursorRegisterRequest;
import com.ablueforce.cortexce.service.CursorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Cursor API contract that the reference documentation publishes, in both
 * directions: what the endpoints actually put on the wire, and what the published
 * OpenAPI example claims about those fields.
 *
 * <p>Background: {@code installedAt} is stored as a {@link String} — the registry
 * record declares {@code CursorProjectEntry(String workspacePath, String installedAt)}
 * and {@code CursorService#registerProject} fills it with {@code Instant.now().toString()},
 * i.e. an ISO-8601 string such as {@code "2026-03-18T17:50:11.192503Z"}. The published
 * example for both endpoints that carry the field rendered it as the epoch number
 * {@code 1709000000000} instead. A client that takes the published example at its word
 * parses the field as a number and fails, and the reference contradicted itself: one
 * section of the same file stated that the field <em>is</em> an ISO-8601 string and
 * <em>not</em> an epoch number, while a later example in the same section showed an
 * epoch number.
 *
 * <p>Two families of cases are here, and they are deliberately kept apart:
 * <ul>
 *   <li><b>Runtime cases</b> exercise the controller against a real {@link CursorService}
 *       backed by a temporary registry, and pin what the wire actually carries. They are
 *       the ground truth.</li>
 *   <li><b>Published-example cases</b> read the {@code @ApiResponse} annotations directly
 *       and compare the JSON kind of each published field with the JSON kind of the
 *       runtime component it stands for. The runtime kind is derived from the record
 *       component type, not written down here, so the comparison cannot drift away from
 *       the registry by editing this file.</li>
 * </ul>
 *
 * <p>The published-example cases are the ones that catch the defect this file was added
 * for. They are not vacuous: each asserts that the example exists, that it contains the
 * registry entry, and that it actually carries every registry field — so an example that
 * dropped the field, or one that was never published at all, fails rather than passing
 * quietly.
 */
class CursorApiContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Every {@code "installedAt": …} value published in a reference document. */
    private static final Pattern INSTALLED_AT =
            Pattern.compile("\"installedAt\"\\s*:\\s*(\"[^\"]*\"|[^,\\n}]+)");

    private CursorService cursorService;
    private CursorController controller;

    @BeforeEach
    void setUp(@TempDir Path dataDir) throws Exception {
        cursorService = new CursorService();
        var dataDirField = CursorService.class.getDeclaredField("dataDir");
        dataDirField.setAccessible(true);
        dataDirField.set(cursorService, dataDir.toString());

        // The context service is only reached by the context-update endpoints, which
        // this contract does not cover. Passing null keeps the unit under test free of
        // a Spring context and of a database.
        controller = new CursorController(cursorService, null);
    }

    // ---------------------------------------------------------------- runtime

    @Test
    @DisplayName("a registered project is listed with an ISO-8601 installedAt, and count matches the list length")
    void listedProjectCarriesIso8601InstalledAt() {
        cursorService.registerProject("demo-project", "/tmp/demo-project");

        ResponseEntity<Map<String, Object>> response = controller.getProjects();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) body.get("projects");
        assertEquals(1, projects.size());
        assertEquals(projects.size(), body.get("count"));

        Map<String, Object> entry = projects.get(0);
        assertEquals("demo-project", entry.get("projectName"));
        assertEquals("/tmp/demo-project", entry.get("workspacePath"));

        Object installedAt = entry.get("installedAt");
        assertInstanceOf(String.class, installedAt, "installedAt must reach the wire as a string");
        Instant.parse((String) installedAt);
    }

    @Test
    @DisplayName("checking a registered project returns the same ISO-8601 installedAt")
    void checkRegisteredCarriesIso8601InstalledAt() {
        cursorService.registerProject("demo-project", "/tmp/demo-project");

        ResponseEntity<Map<String, Object>> response = controller.checkRegistered("demo-project");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("registered"));
        assertEquals("/tmp/demo-project", body.get("workspacePath"));

        Object installedAt = body.get("installedAt");
        assertInstanceOf(String.class, installedAt, "installedAt must reach the wire as a string");
        Instant.parse((String) installedAt);
    }

    @Test
    @DisplayName("an unregistered project is reported with exactly the two published fields")
    void checkRegisteredForUnknownProjectHasTwoFields() {
        ResponseEntity<Map<String, Object>> response = controller.checkRegistered("never-registered");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(Boolean.FALSE, body.get("registered"));
        assertEquals(Set.of("registered", "projectName"), body.keySet());
    }

    @Test
    @DisplayName("registering without a project name is refused with the published error body")
    void registerWithoutProjectNameIsRefused() {
        ResponseEntity<Object> response =
                controller.registerProject(new CursorRegisterRequest("  ", "/tmp/demo-project"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        Map<String, Object> body = asMap(response.getBody());
        assertEquals(Boolean.FALSE, body.get("success"));
        assertEquals("projectName is required", body.get("error"));
    }

    @Test
    @DisplayName("registering without a workspace path is refused with the published error body")
    void registerWithoutWorkspacePathIsRefused() {
        ResponseEntity<Object> response =
                controller.registerProject(new CursorRegisterRequest("demo-project", "  "));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        Map<String, Object> body = asMap(response.getBody());
        assertEquals(Boolean.FALSE, body.get("success"));
        assertEquals("workspacePath is required", body.get("error"));
    }

    // -------------------------------------------------- published example

    @Test
    @DisplayName("the published projects example types every registry field the way the registry stores it")
    void publishedProjectsExampleMatchesRuntimeFieldTypes() throws Exception {
        JsonNode example = publishedExample("getProjects");
        assertTrue(example.has("projects"), "the published example must contain a projects array");

        JsonNode publishedEntry = example.get("projects").get(0);
        assertNotNull(publishedEntry, "the published example must contain one registry entry");

        assertRegistryFieldsMatchRuntime(publishedEntry, "getProjects");
    }

    @Test
    @DisplayName("the published check-registration example types every registry field the way the registry stores it")
    void publishedCheckRegisteredExampleMatchesRuntimeFieldTypes() throws Exception {
        JsonNode example = publishedExample("checkRegistered");
        assertTrue(example.has("registered"), "the published example must contain the registered flag");

        assertRegistryFieldsMatchRuntime(example, "checkRegistered");
    }

    /**
     * Compares every field of the registry record, as published in the example, with the
     * JSON kind the runtime component actually produces.
     */
    private void assertRegistryFieldsMatchRuntime(JsonNode publishedEntry, String methodName) {
        for (RecordComponent component : CursorService.CursorProjectEntry.class.getRecordComponents()) {
            JsonNode publishedValue = publishedEntry.get(component.getName());
            assertNotNull(publishedValue,
                    "the published example for " + methodName + " omits the registry field "
                            + component.getName());
            assertEquals(kindOfJavaType(component.getType()), jsonKind(publishedValue),
                    "the published example for " + methodName + " types " + component.getName()
                            + " as " + jsonKind(publishedValue) + " while the registry stores it as a "
                            + component.getType().getSimpleName());
        }
    }

    // -------------------------------------------------- published reference

    @Test
    @DisplayName("both published API references render installedAt as an ISO-8601 string")
    void publishedApiReferencesRenderInstalledAtAsIso8601String() throws Exception {
        for (String reference : new String[] {"docs/API.md", "docs/API-zh-CN.md"}) {
            Path file = repoRoot().resolve(reference);
            assertTrue(Files.exists(file), "the published reference must exist: " + file);

            List<String> values = installedAtValues(Files.readString(file));

            // Positive control: the scan must actually have reached the field. A scan
            // that found nothing would otherwise report "no violations" and pass for
            // the wrong reason.
            assertTrue(values.size() >= 2,
                    reference + " must publish installedAt at least twice (the projects list and the "
                            + "registration check), found " + values.size());

            for (String value : values) {
                assertTrue(value.startsWith("\"") && value.endsWith("\""),
                        reference + " publishes installedAt as " + value
                                + " — the registry stores an ISO-8601 string, not a bare number");
                Instant.parse(value.substring(1, value.length() - 1));
            }
        }
    }

    /**
     * Every value published for the {@code installedAt} key, in document order.
     */
    private static List<String> installedAtValues(String document) {
        Matcher matcher = INSTALLED_AT.matcher(document);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1).trim());
        }
        return values;
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("docs/API.md")) && Files.exists(dir.resolve("backend/pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new AssertionError(
                "cannot locate the repository root from " + Paths.get("").toAbsolutePath());
    }

    // ------------------------------------------------------------- helpers

    private JsonNode publishedExample(String methodName) throws Exception {
        Method method = null;
        for (Method candidate : CursorController.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                method = candidate;
                break;
            }
        }
        assertNotNull(method, "CursorController must declare " + methodName);

        String raw = null;
        ApiResponses container = method.getAnnotation(ApiResponses.class);
        if (container != null) {
            for (ApiResponse response : container.value()) {
                raw = firstExample(response.content());
                if (raw != null) {
                    break;
                }
            }
        }
        if (raw == null) {
            ApiResponse single = method.getAnnotation(ApiResponse.class);
            if (single != null) {
                raw = firstExample(single.content());
            }
        }

        assertNotNull(raw, "CursorController#" + methodName + " must publish a response example");
        JsonNode example = MAPPER.readTree(raw);
        assertTrue(example.isObject(), "the published example for " + methodName + " must be a JSON object");
        return example;
    }

    private static String firstExample(Content[] contents) {
        for (Content content : contents) {
            Schema schema = content.schema();
            if (schema != null && schema.example() != null && !schema.example().isBlank()) {
                return schema.example();
            }
        }
        return null;
    }

    private static Map<String, Object> asMap(Object body) {
        assertNotNull(body, "the response body must not be null");
        return MAPPER.convertValue(body, new TypeReference<Map<String, Object>>() {});
    }

    private static String jsonKind(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isArray()) {
            return "array";
        }
        return "object";
    }

    private static String kindOfJavaType(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class) {
            return "string";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Double.class || type == double.class
                || type == Float.class || type == float.class
                || type == BigDecimal.class) {
            return "number";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
