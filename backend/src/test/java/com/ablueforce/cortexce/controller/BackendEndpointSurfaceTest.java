package com.ablueforce.cortexce.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the backend's <em>REST surface</em>: the exact set of
 * {@code (HTTP method, path)} pairs the {@code @RestController} classes
 * publish.
 *
 * <p>Why this needs pinning. The backend is the only component in this repository whose
 * surface is described in two places that are maintained independently: the hand-written
 * {@code docs/API.md} (66 paths, 71 operations) and the OpenAPI document springdoc
 * generates from the annotations at runtime. Nothing in the build connects them. A
 * controller method that is renamed, moved or deleted changes what the server serves and
 * silently changes what the published API reference promises, and no existing test would
 * fail — the backend's sixteen test classes contained not a single
 * {@code "/api/..."} path literal before this one.
 *
 * <p>The same gap was closed for the Python demo (26 endpoints) and the Java demo
 * (44 endpoints) in earlier rounds, and for the Go and JavaScript demos in later ones;
 * the backend is the largest surface of the family and was the one still unguarded.
 *
 * <p><b>Scope.</b> This sweep sees the REST layer only, because it is built by
 * reflecting over {@code @*Mapping} annotations. The running server publishes
 * <em>more</em> than what is pinned here: the MCP transport and the Spring Boot
 * actuator are registered by the framework and springdoc publishes neither, so the
 * whole server answers 80 operations across 74 paths while this test pins 74 across
 * 68. The remaining six are pinned by {@link BackendTransportEndpointSurfaceTest},
 * and the running server is compared against both by
 * {@code scripts/check-api-doc-surface.py}.
 *
 * <p>What is asserted.
 * <ol>
 *   <li><b>Set equality</b>, not containment: one added, one removed or one renamed
 *       endpoint fails. A {@code containsAll} assertion would only catch removals.</li>
 *   <li><b>No duplicates</b> — two handlers normalising to the same
 *       {@code (method, path)} would make one of them unreachable.</li>
 *   <li><b>The sweep is not vacuous</b> — the controller package must still be
 *       scannable and the surface must still be the expected size, so that a
 *       broken scanner cannot report an empty surface and pass.</li>
 * </ol>
 *
 * <p>The expectations are read from the annotations by reflection — no Spring context,
 * no database, no running server — so the test runs offline and in milliseconds.
 */
class BackendEndpointSurfaceTest {

    private static final String CONTROLLER_PACKAGE = "com.ablueforce.cortexce.controller";

    /**
     * Every {@code (method, path)} the backend publishes, as served by the running
     * instance on port 37790 on 2026-10-21 and verified against the annotations by
     * this test's own discovery pass.
     */
    private static final List<String> EXPECTED_OPERATIONS = List.of(
            "DELETE /api/cursor/register/{projectName}",
            "DELETE /api/memory/observations/{id}",
            "DELETE /api/observation/{id}",
            "DELETE /api/sessions",
            "DELETE /api/sessions/{platformSource}/{contentSessionId}",
            "DELETE /api/summary/{id}",
            "GET /api/context/inject",
            "GET /api/context/preview",
            "GET /api/context/prior-messages",
            "GET /api/context/recent",
            "GET /api/context/timeline",
            "GET /api/cursor/projects",
            "GET /api/cursor/register/{projectName}",
            "GET /api/extraction/{templateName}/history",
            "GET /api/extraction/{templateName}/latest",
            "GET /api/health",
            "GET /api/logs",
            "GET /api/memory/quality-distribution",
            "GET /api/mode",
            "GET /api/mode/concepts",
            "GET /api/mode/concepts/valid",
            "GET /api/mode/types",
            "GET /api/mode/types/valid",
            "GET /api/mode/types/{typeId}/emoji",
            "GET /api/mode/types/{typeId}/validate",
            "GET /api/modes",
            "GET /api/observations",
            "GET /api/processing-status",
            "GET /api/projects",
            "GET /api/prompts",
            "GET /api/readiness",
            "GET /api/search",
            "GET /api/search/by-file",
            "GET /api/session",
            "GET /api/session/{sessionId}",
            "GET /api/sessions",
            "GET /api/settings",
            "GET /api/stats",
            "GET /api/summaries",
            "GET /api/test/all",
            "GET /api/test/embedding",
            "GET /api/test/llm",
            "GET /api/timeline",
            "GET /api/version",
            "GET /stream",
            "PATCH /api/memory/observations/{id}",
            "PATCH /api/session/user",
            "PATCH /api/session/{sessionId}/user",
            "POST /api/context/generate",
            "POST /api/context/semantic",
            "POST /api/cursor/context/{projectName}",
            "POST /api/cursor/context/{projectName}/custom",
            "POST /api/cursor/register",
            "POST /api/extraction/run",
            "POST /api/import",
            "POST /api/import/observations",
            "POST /api/import/prompts",
            "POST /api/import/sessions",
            "POST /api/import/summaries",
            "POST /api/ingest/observation",
            "POST /api/ingest/session-end",
            "POST /api/ingest/tool-use",
            "POST /api/ingest/user-prompt",
            "POST /api/logs/clear",
            "POST /api/memory/experiences",
            "POST /api/memory/feedback",
            "POST /api/memory/icl-prompt",
            "POST /api/memory/refine",
            "POST /api/modes",
            "POST /api/observations/batch",
            "POST /api/sdk-sessions/batch",
            "POST /api/session/start",
            "POST /api/settings",
            "PUT /api/mode"
    );

    @Test
    @DisplayName("the published (method, path) surface is exactly the pinned set")
    void exposedSurfaceIsPinned() {
        Set<String> discovered = new TreeSet<>(discoverOperations());
        Set<String> expected = new TreeSet<>(EXPECTED_OPERATIONS);

        assertEquals(expected, discovered,
                "the backend's exposed endpoint surface changed. "
                        + "Adding, removing or renaming an endpoint changes what the server serves "
                        + "and what docs/API.md promises, and nothing else in the build would notice. "
                        + "Update EXPECTED_OPERATIONS and docs/API.md (and its Chinese counterpart) together.");
    }

    @Test
    @DisplayName("no two handlers publish the same (method, path)")
    void noDuplicateOperations() {
        List<String> discovered = discoverOperations();
        Set<String> distinct = new TreeSet<>(discovered);
        assertEquals(distinct.size(), discovered.size(),
                "two handlers normalise to the same (method, path); one of them is unreachable: "
                        + distinct);
    }

    @Test
    @DisplayName("the discovery pass itself is not vacuous")
    void discoveryPassIsNotVacuous() {
        List<Class<?>> controllers = controllers();
        assertTrue(controllers.size() >= 14,
                "the controller package must be scannable, found " + controllers.size() + " classes");
        assertEquals(74, EXPECTED_OPERATIONS.size(),
                "the pinned surface is expected to hold 74 operations");
        assertEquals(68, expectedPathCount(),
                "the pinned surface is expected to span 68 distinct paths");
    }

    private int expectedPathCount() {
        Set<String> paths = new TreeSet<>();
        for (String op : EXPECTED_OPERATIONS) {
            paths.add(op.substring(op.indexOf(' ') + 1));
        }
        return paths.size();
    }

    /** Every {@code (method, path)} pair declared by the controllers, as a sorted list. */
    private static List<String> discoverOperations() {
        List<String> found = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            String prefix = classPrefix(controller);
            for (Method method : controller.getDeclaredMethods()) {
                for (String verb : verbsOf(method)) {
                    for (String suffix : pathsOf(method)) {
                        found.add(verb + " " + normalise(prefix + suffix));
                    }
                }
            }
        }
        found.sort(String::compareTo);
        return found;
    }

    private static String classPrefix(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return "";
        }
        return mapping.value()[0];
    }

    private static List<String> verbsOf(Method method) {
        List<String> verbs = new ArrayList<>();
        if (method.isAnnotationPresent(GetMapping.class)) {
            verbs.add("GET");
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            verbs.add("POST");
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            verbs.add("PUT");
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            verbs.add("DELETE");
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            verbs.add("PATCH");
        }
        return verbs;
    }

    private static List<String> pathsOf(Method method) {
        String[] values = null;
        if (method.isAnnotationPresent(GetMapping.class)) {
            values = method.getAnnotation(GetMapping.class).value();
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            values = method.getAnnotation(PostMapping.class).value();
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            values = method.getAnnotation(PutMapping.class).value();
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            values = method.getAnnotation(DeleteMapping.class).value();
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            values = method.getAnnotation(PatchMapping.class).value();
        }
        if (values == null || values.length == 0) {
            return List.of("");
        }
        return List.of(values);
    }

    private static String normalise(String path) {
        String p = path.replaceAll("/{2,}", "/");
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return classes;
    }
}
