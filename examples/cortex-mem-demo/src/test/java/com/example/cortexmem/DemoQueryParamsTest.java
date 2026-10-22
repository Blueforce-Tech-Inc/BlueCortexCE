package com.example.cortexmem;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the query parameters every demo endpoint binds — and the README's
 * documented ones against it.
 *
 * <p>Why this exists. The endpoint <em>surface</em> is pinned by
 * {@code DemoEndpointSurfaceTest} (44 {@code (verb, path)} pairs), but the
 * <em>parameters</em> those endpoints accept were pinned by nothing. A query
 * parameter that the endpoint does not declare is not an error: Spring binds
 * only the names it knows and drops the rest without a word, so the reader's
 * value is silently ignored and the demo answers with its default.
 *
 * <p>That is not theoretical. Renaming one bound parameter — changing
 * {@code @RequestParam(required = false) String project} to carry
 * {@code name = "projectZZ"} — was measured live against a running demo:
 *
 * <pre>
 *   GET /demo/tool?path=marker.txt
 *     before: {"status":"tool executed","result":"MARKER-CONTENT-42\n"}
 *     after : {"status":"tool executed","result":"Error: .../hello.txt"}
 * </pre>
 *
 * The second answer is the <em>default</em> file, not the requested one: the
 * request succeeded (HTTP 200), the response said the tool ran, and the
 * caller's value was dropped without a word. The demo's own 53-test suite
 * stayed green through the same change.
 *
 * <p>What is pinned here, per endpoint:
 *
 * <ol>
 *   <li>the <em>complete</em> set of query parameters the endpoint binds —
 *       equality, not containment, so a parameter added to an endpoint fails
 *       here too;</li>
 *   <li>every query parameter the README documents for an endpoint must be
 *       one the endpoint actually binds — the documentation and the code are
 *       one artifact;</li>
 *   <li>a self-check proving the comparison can report a difference.</li>
 * </ol>
 *
 * <p>The names are read the way Spring reads them: the annotation's
 * {@code name}, else its {@code value}, else the compiled parameter name
 * (this module is compiled with {@code -parameters}, so the compiled name is
 * the real one).
 *
 * <p>The expected sets are written out by hand on purpose. Deriving them from
 * the controllers' own annotations would make the assertion tautological, and
 * a tautological assertion is worse than no assertion.
 */
class DemoQueryParamsTest {

    private static final Class<?>[] CONTROLLERS = {
            ChatController.class,
            ExperiencesController.class,
            ExtractionController.class,
            FeedbackController.class,
            IngestController.class,
            ManagementController.class,
            MemoryController.class,
            ObservationsController.class,
            ProjectsController.class,
            SearchController.class,
            SessionLifecycleController.class,
            ToolsController.class,
    };

    /**
     * The complete query-parameter set of every endpoint, written out by hand.
     * An empty value means the endpoint binds no query parameter at all.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("DELETE /demo/observations/{id}", "");
        EXPECTED.put("GET /chat", "conversationId,message,project,useTools");
        EXPECTED.put("GET /demo/experiences", "count,project,requiredConcepts,source,task,userId");
        EXPECTED.put("GET /demo/experiences/health", "");
        EXPECTED.put("GET /demo/extraction/health", "");
        EXPECTED.put("GET /demo/extraction/history", "limit,project,template,userId");
        EXPECTED.put("GET /demo/extraction/latest", "project,template,userId");
        EXPECTED.put("GET /demo/feedback/health", "");
        EXPECTED.put("GET /demo/iclprompt", "maxChars,project,task,userId");
        EXPECTED.put("GET /demo/manage/health", "");
        EXPECTED.put("GET /demo/manage/modes", "");
        EXPECTED.put("GET /demo/manage/projects", "");
        EXPECTED.put("GET /demo/manage/quality", "project");
        EXPECTED.put("GET /demo/manage/settings", "");
        EXPECTED.put("GET /demo/manage/stats", "project");
        EXPECTED.put("GET /demo/manage/version", "");
        EXPECTED.put("GET /demo/observations", "limit,offset,project");
        EXPECTED.put("GET /demo/observations/status", "");
        EXPECTED.put("GET /demo/observations/{id}", "");
        EXPECTED.put("GET /demo/projects", "");
        EXPECTED.put("GET /demo/search",
                "concept,limit,observationType,offset,orderBy,project,query,source");
        EXPECTED.put("GET /demo/search/health", "");
        EXPECTED.put("GET /demo/session/tool", "path,projectPath,sessionId");
        EXPECTED.put("GET /demo/tool", "path,project");
        EXPECTED.put("GET /memory/experiences", "count,project,task");
        EXPECTED.put("GET /memory/experiences/filtered",
                "count,project,requiredConcepts,source,task");
        EXPECTED.put("GET /memory/health", "project");
        EXPECTED.put("GET /memory/icl", "project,task");
        EXPECTED.put("GET /memory/icl/truncated", "maxChars,project,task");
        EXPECTED.put("GET /memory/quality", "project");
        EXPECTED.put("PATCH /demo/observations/{id}", "");
        EXPECTED.put("PATCH /demo/session/user", "");
        EXPECTED.put("POST /demo/extraction/run", "project");
        EXPECTED.put("POST /demo/feedback", "");
        EXPECTED.put("POST /demo/ingest/prompt", "");
        EXPECTED.put("POST /demo/ingest/session-end", "");
        EXPECTED.put("POST /demo/manage/refine", "project");
        EXPECTED.put("POST /demo/observations/batch", "");
        EXPECTED.put("POST /demo/observations/create", "");
        EXPECTED.put("POST /demo/session/end", "lastMessage,projectPath,sessionId");
        EXPECTED.put("POST /demo/session/lifecycle", "project,prompt,toolPath");
        EXPECTED.put("POST /demo/session/prompt", "projectPath,prompt,promptNumber,sessionId");
        EXPECTED.put("POST /demo/session/start", "project");
        EXPECTED.put("POST /memory/refine", "project");
    }

    @Test
    void shouldBindExactlyTheExpectedQueryParameters() {
        Map<String, Set<String>> actual = boundQueryParameters();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            Set<String> bound = actual.get(e.getKey());
            if (bound == null) {
                problems.add(e.getKey() + ": no such endpoint in the demo");
                continue;
            }
            Set<String> expected = new TreeSet<>(Arrays.asList(e.getValue().split(",")));
            expected.remove("");
            if (!expected.equals(bound)) {
                problems.add(e.getKey() + ": expected " + expected + " but binds " + bound);
            }
        }
        // an endpoint that is not in the table would be an unpinning gap
        for (String endpoint : actual.keySet()) {
            if (!EXPECTED.containsKey(endpoint)) {
                problems.add(endpoint + ": not in the expected table");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void shouldBindEveryQueryParameterTheReadmeDocuments() {
        Map<String, Set<String>> actual = boundQueryParameters();
        List<String> problems = new ArrayList<>();
        int rowsRead = 0;
        for (String[] row : readmeRows()) {
            String verb = row[0];
            String path = row[1];
            List<String> documented = row[2] == null ? List.of() : Arrays.asList(row[2].split("&"));
            if (documented.isEmpty()) {
                continue;
            }
            rowsRead++;
            String endpoint = verb + " " + path;
            Set<String> bound = actual.get(endpoint);
            if (bound == null) {
                problems.add(endpoint + ": the README documents query parameters for an "
                        + "endpoint the demo does not serve");
                continue;
            }
            for (String param : documented) {
                String name = param.split("=")[0].trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (!bound.contains(name)) {
                    problems.add(endpoint + ": the README documents '" + name
                            + "' but the endpoint binds " + bound);
                }
            }
        }
        assertTrue(rowsRead > 0,
                "no README row with query parameters was read — the reader is broken, "
                        + "not the README");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theComparisonCanReportADifference() {
        Set<String> expected = new TreeSet<>(Arrays.asList("path", "project"));
        Set<String> actual = new TreeSet<>(Arrays.asList("path", "project"));

        assertNull(diff(expected, actual), "an identical set must not be reported");

        Set<String> missing = new TreeSet<>(Arrays.asList("path"));
        assertNotNull(diff(expected, missing), "a missing key must be reported");
        assertTrue(diff(expected, missing).contains("project"),
                "the report must name the missing key: " + diff(expected, missing));

        Set<String> extra = new TreeSet<>(Arrays.asList("path", "project", "surprise"));
        assertNotNull(diff(expected, extra), "an extra key must be reported");
        assertTrue(diff(expected, extra).contains("surprise"),
                "the report must name the extra key: " + diff(expected, extra));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The query parameters each endpoint binds, keyed by "VERB /path".
     */
    private static Map<String, Set<String>> boundQueryParameters() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Class<?> controller : CONTROLLERS) {
            String base = basePath(controller);
            for (Method method : controller.getDeclaredMethods()) {
                for (String verb : verbsOf(method)) {
                    String path = pathOf(method);
                    if (path == null) {
                        continue;
                    }
                    out.put(verb + " " + base + path, boundNames(method));
                }
            }
        }
        return out;
    }

    /**
     * The name Spring binds for each @RequestParam of one method.
     *
     * <p>Spring's order is: the annotation's {@code name}, else its
     * {@code value}, else the compiled parameter name.
     */
    private static Set<String> boundNames(Method method) {
        Set<String> names = new LinkedHashSet<>();
        for (Parameter parameter : method.getParameters()) {
            RequestParam annotation = parameter.getAnnotation(RequestParam.class);
            if (annotation == null) {
                continue;
            }
            String name = annotation.name();
            if (name == null || name.isEmpty()) {
                name = annotation.value();
            }
            if (name == null || name.isEmpty()) {
                name = parameter.getName();
            }
            names.add(name);
        }
        return names;
    }

    /**
     * The difference between two sets, or null when they are equal.
     */
    private static String diff(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new TreeSet<>(actual);
        unexpected.removeAll(expected);
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return null;
        }
        return "missing=" + missing + " unexpected=" + unexpected;
    }

    private static String basePath(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        return (mapping != null && mapping.value().length > 0) ? mapping.value()[0] : "";
    }

    /**
     * The method's own path, or null when the method is not a mapping.
     */
    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return firstOrEmpty(get.value());
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return firstOrEmpty(post.value());
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return firstOrEmpty(put.value());
        }
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) {
            return firstOrEmpty(patch.value());
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return firstOrEmpty(delete.value());
        }
        return null;
    }

    private static String firstOrEmpty(String[] values) {
        return values.length > 0 ? values[0] : "";
    }

    private static Set<String> verbsOf(Method method) {
        Set<String> verbs = new LinkedHashSet<>();
        if (method.getAnnotation(GetMapping.class) != null) {
            verbs.add("GET");
        }
        if (method.getAnnotation(PostMapping.class) != null) {
            verbs.add("POST");
        }
        if (method.getAnnotation(PutMapping.class) != null) {
            verbs.add("PUT");
        }
        if (method.getAnnotation(PatchMapping.class) != null) {
            verbs.add("PATCH");
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            verbs.add("DELETE");
        }
        return verbs;
    }

    /**
     * The README's endpoint rows: {verb, path, query string or null}.
     */
    private static List<String[]> readmeRows() {
        Path readme = readmePath();
        List<String[]> rows = new ArrayList<>();
        Pattern row = Pattern.compile(
                "^\\|\\s*`((?:GET|POST|PUT|PATCH|DELETE))\\s+([^`?]+)(?:\\?([^`]*))?`");
        try {
            for (String line : Files.readAllLines(readme)) {
                Matcher m = row.matcher(line);
                if (m.find()) {
                    rows.add(new String[]{m.group(1), m.group(2), m.group(3)});
                }
            }
        } catch (Exception e) {
            throw new AssertionError("cannot read the demo README at " + readme, e);
        }
        return rows;
    }

    /**
     * The demo's README, located next to the module this test belongs to.
     */
    private static Path readmePath() {
        Path classes;
        try {
            classes = Paths.get(DemoQueryParamsTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath());
        } catch (Exception e) {
            throw new AssertionError("cannot locate the demo module directory", e);
        }
        // .../target/test-classes/ -> .../target/ -> the module directory
        Path module = classes.getParent() == null ? classes : classes.getParent().getParent();
        Path readme = module.resolve("README.md");
        if (!Files.isRegularFile(readme)) {
            throw new AssertionError("cannot find the demo README at " + readme);
        }
        return readme;
    }
}
