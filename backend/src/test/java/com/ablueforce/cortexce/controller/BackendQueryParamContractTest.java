package com.ablueforce.cortexce.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the parameter names every controller endpoint binds.
 *
 * <p>Why this exists: Spring binds a query parameter by the name declared in
 * {@code @RequestParam}, or — for a bare {@code @RequestParam} — by the
 * <em>compiled parameter name</em>. Nothing in the suite guarded either, so a
 * rename of one annotation argument or one Java parameter silently changed
 * the published contract: the live OpenAPI spec published a different name,
 * the API reference still documented the old one, and the whole backend suite
 * stayed green. Measured before this test: renaming one bound name in six
 * controllers left the suite at 192/192 green while the live spec answered
 * with the mutated names.
 *
 * <p>The expected names below are written out by hand. They were read off the
 * controller sources and cross-checked against the live OpenAPI spec; deriving
 * them from the very code under test would make the assertion tautological.
 */
class BackendQueryParamContractTest {

    /** Query parameter names, per {@code "VERB /path"}. */
    private static final Map<String, List<String>> EXPECTED_QUERY = Map.ofEntries(
            Map.entry("GET /api/context/inject", List.of("projects")),
            Map.entry("GET /api/context/recent", List.of("project", "limit")),
            Map.entry("GET /api/context/timeline",
                    List.of("anchor", "depth_before", "depth_after", "project")),
            Map.entry("GET /api/context/preview",
                    List.of("project", "observationTypes", "concepts", "includeObservations",
                            "includeSummaries", "maxObservations", "maxSummaries", "sessionCount",
                            "fullCount", "platformSource")),
            Map.entry("GET /api/context/prior-messages", List.of("project", "currentSessionId")),
            Map.entry("GET /api/extraction/{templateName}/latest", List.of("projectPath", "userId")),
            Map.entry("GET /api/extraction/{templateName}/history",
                    List.of("projectPath", "userId", "limit")),
            Map.entry("POST /api/extraction/run", List.of("projectPath")),
            Map.entry("GET /api/logs", List.of("lines")),
            Map.entry("GET /api/memory/quality-distribution", List.of("project")),
            Map.entry("POST /api/memory/refine", List.of("project")),
            Map.entry("GET /api/search",
                    List.of("project", "query", "type", "concept", "source", "limit", "offset", "orderBy")),
            // conversationId is the internal UUID and takes precedence over the external
            // pair; standaloneKind only applies to the sentinel/standalone branch.
            Map.entry("GET /api/observations",
                    List.of("project", "platformSource", "contentSessionId", "conversationId",
                            "standaloneKind", "offset", "limit")),
            Map.entry("GET /api/summaries",
                    List.of("project", "platformSource", "contentSessionId", "conversationId",
                            "offset", "limit")),
            Map.entry("GET /api/prompts",
                    List.of("project", "platformSource", "contentSessionId", "conversationId",
                            "offset", "limit")),
            Map.entry("GET /api/stats", List.of("project")),
            Map.entry("GET /api/timeline",
                    List.of("project", "startEpoch", "endEpoch", "anchorId", "depthBefore",
                            "depthAfter", "query")),
            Map.entry("GET /api/search/by-file",
                    List.of("project", "filePath", "isFolder", "limit", "debug")),
            Map.entry("GET /api/sessions",
                    List.of("project", "platformSource", "offset", "limit")));

    /** Path variable names, per {@code "VERB /path"}. */
    private static final Map<String, List<String>> EXPECTED_PATH_VARS = Map.ofEntries(
            Map.entry("DELETE /api/cursor/register/{projectName}", List.of("projectName")),
            Map.entry("POST /api/cursor/context/{projectName}", List.of("projectName")),
            Map.entry("POST /api/cursor/context/{projectName}/custom", List.of("projectName")),
            Map.entry("GET /api/cursor/register/{projectName}", List.of("projectName")),
            Map.entry("GET /api/extraction/{templateName}/latest", List.of("templateName")),
            Map.entry("GET /api/extraction/{templateName}/history", List.of("templateName")),
            Map.entry("PATCH /api/memory/observations/{id}", List.of("id")),
            Map.entry("DELETE /api/memory/observations/{id}", List.of("id")),
            Map.entry("GET /api/mode/types/{typeId}/emoji", List.of("typeId")),
            Map.entry("GET /api/mode/types/{typeId}/validate", List.of("typeId")),
            Map.entry("GET /api/session/{sessionId}", List.of("sessionId")),
            Map.entry("PATCH /api/session/{sessionId}/user", List.of("sessionId")),
            Map.entry("DELETE /api/sessions/{platformSource}/{contentSessionId}",
                    List.of("platformSource", "contentSessionId")),
            Map.entry("DELETE /api/summary/{id}", List.of("id")),
            Map.entry("DELETE /api/observation/{id}", List.of("id")));

    @Test
    @DisplayName("every endpoint binds exactly the query parameter names it documents")
    void everyEndpointBindsExactlyTheExpectedQueryParameters() {
        Map<String, Set<String>> actual = queryNamesByEndpoint();
        assertFalse(actual.isEmpty(), "no endpoint with query parameters was found - the scan is broken");

        for (Map.Entry<String, Set<String>> e : actual.entrySet()) {
            List<String> expected = EXPECTED_QUERY.get(e.getKey());
            assertTrue(expected != null,
                    () -> "endpoint " + e.getKey() + " is not in the expected table; it binds "
                            + e.getValue());
            assertEquals(new TreeSet<>(expected), new TreeSet<>(e.getValue()),
                    () -> "query parameter names of " + e.getKey() + " drifted");
        }
        // every expected entry must exist on a real endpoint as well
        for (String key : EXPECTED_QUERY.keySet()) {
            assertTrue(actual.containsKey(key),
                    () -> "expected endpoint " + key + " does not exist any more");
        }
    }

    @Test
    @DisplayName("every endpoint binds exactly the path variable names it documents")
    void everyEndpointBindsExactlyTheExpectedPathVariables() {
        Map<String, Set<String>> actual = pathVariablesByEndpoint();
        assertFalse(actual.isEmpty(), "no endpoint with path variables was found - the scan is broken");

        for (Map.Entry<String, Set<String>> e : actual.entrySet()) {
            List<String> expected = EXPECTED_PATH_VARS.get(e.getKey());
            assertTrue(expected != null,
                    () -> "endpoint " + e.getKey() + " is not in the expected path-variable table; it binds "
                            + e.getValue());
            assertEquals(new TreeSet<>(expected), new TreeSet<>(e.getValue()),
                    () -> "path variable names of " + e.getKey() + " drifted");
        }
        for (String key : EXPECTED_PATH_VARS.keySet()) {
            assertTrue(actual.containsKey(key),
                    () -> "expected endpoint " + key + " does not exist any more");
        }
    }

    @Test
    @DisplayName("the expected table covers every endpoint that binds a parameter")
    void noEndpointWithParametersIsLeftUnpinned() {
        Map<String, Set<String>> query = queryNamesByEndpoint();
        Map<String, Set<String>> pathVars = pathVariablesByEndpoint();

        for (String key : query.keySet()) {
            assertTrue(EXPECTED_QUERY.containsKey(key),
                    () -> "endpoint " + key + " binds query parameters but is not pinned");
        }
        for (String key : pathVars.keySet()) {
            assertTrue(EXPECTED_PATH_VARS.containsKey(key),
                    () -> "endpoint " + key + " binds path variables but is not pinned");
        }
        // the table must not claim endpoints that do not exist
        for (String key : EXPECTED_QUERY.keySet()) {
            assertTrue(query.containsKey(key), () -> "pinned endpoint " + key + " no longer binds query parameters");
        }
        for (String key : EXPECTED_PATH_VARS.keySet()) {
            assertTrue(pathVars.containsKey(key), () -> "pinned endpoint " + key + " no longer binds path variables");
        }
    }

    @Test
    @DisplayName("the expected table is complete and the comparison can actually fail")
    void theExpectedTableIsCompleteAndTheComparisonCanFail() {
        // A coverage floor: the number of endpoints and of bound names is part of
        // the contract, and it must not silently shrink to zero.
        Map<String, Set<String>> query = queryNamesByEndpoint();
        Map<String, Set<String>> pathVars = pathVariablesByEndpoint();
        assertTrue(query.size() >= 19,
                () -> "only " + query.size() + " endpoints with query parameters were found");
        assertTrue(pathVars.size() >= 15,
                () -> "only " + pathVars.size() + " endpoints with path variables were found");

        int slots = query.values().stream().mapToInt(Set::size).sum()
                + pathVars.values().stream().mapToInt(Set::size).sum();
        assertTrue(slots >= 80, () -> "only " + slots + " bound parameter names were found");

        // Falsifiability: a deliberately wrong expectation must NOT compare equal.
        // Without this, a comparison that always returns "equal" would make the
        // three tests above pass on any code at all.
        Set<String> real = new LinkedHashSet<>(List.of("project", "limit"));
        Set<String> sameButReordered = new LinkedHashSet<>(List.of("limit", "project"));
        Set<String> wrong = new LinkedHashSet<>(List.of("project", "offset"));
        assertEquals(new TreeSet<>(real), new TreeSet<>(sameButReordered),
                "the comparison must ignore ordering");
        assertNotEquals(new TreeSet<>(real), new TreeSet<>(wrong),
                "the comparison must detect a renamed parameter");
    }

    // ------------------------------------------------------------------ scan

    private static List<Class<?>> controllerClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.ablueforce.cortexce.controller")) {
            try {
                classes.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("controller class not found: " + bd.getBeanClassName(), e);
            }
        }
        return classes;
    }

    private static Map<String, Set<String>> queryNamesByEndpoint() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        forEachEndpoint(out, true);
        return out;
    }

    private static Map<String, Set<String>> pathVariablesByEndpoint() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        forEachEndpoint(out, false);
        return out;
    }

    private static void forEachEndpoint(Map<String, Set<String>> out, boolean query) {
        for (Class<?> c : controllerClasses()) {
            RequestMapping classMapping = c.getAnnotation(RequestMapping.class);
            String prefix = "";
            if (classMapping != null) {
                String[] v = classMapping.value();
                prefix = v.length > 0 ? v[0] : (classMapping.path().length > 0 ? classMapping.path()[0] : "");
            }
            for (Method m : c.getDeclaredMethods()) {
                String verb = verbOf(m);
                if (verb == null) {
                    continue;
                }
                String key = verb + " " + prefix + pathOf(m);
                Set<String> names = out.computeIfAbsent(key, k -> new LinkedHashSet<>());
                for (Parameter p : m.getParameters()) {
                    if (query) {
                        RequestParam rp = p.getAnnotation(RequestParam.class);
                        if (rp != null) {
                            names.add(!rp.name().isEmpty() ? rp.name()
                                    : (!rp.value().isEmpty() ? rp.value() : p.getName()));
                        }
                    } else {
                        PathVariable pv = p.getAnnotation(PathVariable.class);
                        if (pv != null) {
                            names.add(!pv.name().isEmpty() ? pv.name()
                                    : (!pv.value().isEmpty() ? pv.value() : p.getName()));
                        }
                    }
                }
            }
        }
        out.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static String verbOf(Method m) {
        if (m.getAnnotation(GetMapping.class) != null) return "GET";
        if (m.getAnnotation(PostMapping.class) != null) return "POST";
        if (m.getAnnotation(PutMapping.class) != null) return "PUT";
        if (m.getAnnotation(PatchMapping.class) != null) return "PATCH";
        if (m.getAnnotation(DeleteMapping.class) != null) return "DELETE";
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null && rm.method().length > 0) return rm.method()[0].name();
        return null;
    }

    private static String pathOf(Method m) {
        String[] v = null;
        if (m.getAnnotation(GetMapping.class) != null) {
            GetMapping g = m.getAnnotation(GetMapping.class);
            v = g.value().length > 0 ? g.value() : g.path();
        } else if (m.getAnnotation(PostMapping.class) != null) {
            PostMapping g = m.getAnnotation(PostMapping.class);
            v = g.value().length > 0 ? g.value() : g.path();
        } else if (m.getAnnotation(PutMapping.class) != null) {
            PutMapping g = m.getAnnotation(PutMapping.class);
            v = g.value().length > 0 ? g.value() : g.path();
        } else if (m.getAnnotation(PatchMapping.class) != null) {
            PatchMapping g = m.getAnnotation(PatchMapping.class);
            v = g.value().length > 0 ? g.value() : g.path();
        } else if (m.getAnnotation(DeleteMapping.class) != null) {
            DeleteMapping g = m.getAnnotation(DeleteMapping.class);
            v = g.value().length > 0 ? g.value() : g.path();
        } else {
            RequestMapping rm = m.getAnnotation(RequestMapping.class);
            v = rm == null ? null : (rm.value().length > 0 ? rm.value() : rm.path());
        }
        return v == null || v.length == 0 ? "" : v[0];
    }
}
