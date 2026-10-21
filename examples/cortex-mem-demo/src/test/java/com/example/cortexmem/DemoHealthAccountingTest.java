package com.example.cortexmem;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binds the demo README's health-endpoint accounting to the code.
 *
 * <p>Why this exists: the README's headline used to read <em>"Only two of the
 * seven health endpoints actually check the backend"</em> while the very same
 * paragraph went on to name the two real ones and "the other six" — and the
 * endpoint table above it listed eight rows. Two plus six is eight, so the
 * headline contradicted its own paragraph and its own table. The sentence
 * arrived that way in round 388, when the six static beacons were first
 * documented; it has been wrong since, and nothing could catch it, because the
 * health endpoints were pinned one by one ({@code StaticHealthEndpointsTest})
 * while the <em>count the README publishes</em> was pinned by nothing.
 *
 * <p>This is the same shape as the other coverage gaps this project keeps
 * finding: a family of behaviour whose <em>members</em> are asserted but whose
 * <em>accounting</em> is not. Renaming a path, adding a beacon or dropping one
 * changes the published number, and the number is what a reader copies.
 *
 * <p>Three-way equality, so none of the three can drift together:
 * <ol>
 *   <li>the code — read off the annotations, by handler-method name;</li>
 *   <li>the README — the endpoint table plus the headline's number;</li>
 *   <li>a hand-written declared set, so that a mistake shared by the first two
 *       is still caught.</li>
 * </ol>
 *
 * <p>The code side is derived by the demo's own naming convention: a handler
 * method called {@code health} answers with a constant and never asks the
 * backend (that is what {@code StaticHealthEndpointsTest} pins semantically,
 * one endpoint at a time), and {@code getMemoryHealth} is the one that really
 * consults the backend. {@code /actuator/health} is Spring Boot's own and
 * lives outside the controllers, so it is added explicitly — and it is exactly
 * why only <em>seven</em> of the eight health-ish paths have "health" in their
 * name, the eighth being {@code /demo/observations/status}.
 */
class DemoHealthAccountingTest {

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

    /** The handler name the static beacons use — a constant body, no backend call. */
    private static final String STATIC_HANDLER = "health";

    /** The handler name of the one demo endpoint that really asks the backend. */
    private static final String REAL_HANDLER = "getMemoryHealth";

    /** Spring Boot's own health endpoint — not a controller mapping, hence explicit. */
    private static final String ACTUATOR_HEALTH = "/actuator/health";

    /** A row of the README's endpoint table: `| `GET /path?query` | description |`. */
    private static final Pattern TABLE_ROW =
            Pattern.compile("^\\|\\s*`([A-Z]+)\\s+([^`?]+)(?:\\?[^`]*)?`\\s*\\|\\s*(.*)$");

    /** The headline: "Only two of the eight health endpoints actually check…". */
    private static final Pattern HEADLINE =
            Pattern.compile("Only two of the \\*{0,2}([a-z]+)\\*{0,2} health endpoints");

    private static final Map<String, Integer> NUMBER_WORDS = numberWords();

    private static Map<String, Integer> numberWords() {
        Map<String, Integer> words = new LinkedHashMap<>();
        String[] names = {"zero", "one", "two", "three", "four", "five", "six",
                "seven", "eight", "nine", "ten", "eleven", "twelve"};
        for (int i = 0; i < names.length; i++) {
            words.put(names[i], i);
        }
        return words;
    }

    @Test
    void shouldServeExactlyTheHealthEndpointsTheReadmePublishes() {
        Set<String> declared = new TreeSet<>(Arrays.asList(
                "GET " + ACTUATOR_HEALTH,
                "GET /demo/experiences/health",
                "GET /demo/extraction/health",
                "GET /demo/feedback/health",
                "GET /demo/manage/health",
                "GET /demo/observations/status",
                "GET /demo/search/health",
                "GET /memory/health"
        ));

        // 1. the code
        Set<String> fromCode = new TreeSet<>(declaredHealthEndpoints());
        assertEquals(declared, fromCode,
                "the health endpoints the code declares no longer match the declared set");

        // 2. the README's endpoint table
        Map<String, String> rows = readmeTableRows();
        Set<String> fromReadme = new TreeSet<>();
        for (Map.Entry<String, String> row : rows.entrySet()) {
            if (isHealthPath(row.getKey())) {
                fromReadme.add(row.getKey());
            }
        }
        assertEquals(declared, fromReadme,
                "the health endpoints the README lists no longer match the declared set");
    }

    @Test
    void shouldPublishTheSameNumberOfHealthEndpointsAsItServes() {
        Set<String> served = declaredHealthEndpoints();
        Map<String, String> rows = readmeTableRows();

        long listed = rows.keySet().stream().filter(DemoHealthAccountingTest::isHealthPath).count();
        assertEquals(served.size(), listed,
                "the README's endpoint table lists a different number of health endpoints "
                        + "than the demo serves");

        String readme = readReadme();
        Matcher headline = HEADLINE.matcher(readme);
        assertTrue(headline.find(),
                "the README no longer states how many health endpoints check the backend; "
                        + "the headline pattern is what this test reads the number from");
        String word = headline.group(1);
        Integer published = NUMBER_WORDS.get(word);
        assertTrue(published != null,
                "the README publishes an unrecognised number word: " + word);
        assertEquals(served.size(), published.intValue(),
                "the README says \"" + word + "\" health endpoints, but the demo serves "
                        + served.size());
    }

    @Test
    void shouldNameExactlyTheStaticHealthEndpointsTheDemoServes() {
        // The README's rows are keyed `VERB /path`, so the derived set has to use
        // the same shape — comparing a path against a `VERB /path` key fails on
        // the prefix, not on the endpoint.
        Set<String> staticPaths = new TreeSet<>();
        for (String path : healthPathsForHandler(STATIC_HANDLER)) {
            staticPaths.add("GET " + path);
        }
        assertTrue(!staticPaths.isEmpty(),
                "no controller declares a '" + STATIC_HANDLER + "' handler any more");

        Map<String, String> rows = readmeTableRows();
        Set<String> publishedStatic = new TreeSet<>();
        for (Map.Entry<String, String> row : rows.entrySet()) {
            if (row.getValue().contains("Static beacon")) {
                publishedStatic.add(row.getKey());
            }
        }
        assertEquals(staticPaths, publishedStatic,
                "the README does not mark exactly the static health beacons the demo serves");
    }

    // ------------------------------------------------------------------ code

    /** Every health-ish endpoint the demo serves, as `VERB /path`. */
    private static Set<String> declaredHealthEndpoints() {
        Set<String> out = new LinkedHashSet<>();
        for (String path : healthPathsForHandler(STATIC_HANDLER)) {
            out.add("GET " + path);
        }
        for (String path : healthPathsForHandler(REAL_HANDLER)) {
            out.add("GET " + path);
        }
        out.add("GET " + ACTUATOR_HEALTH);
        return out;
    }

    /** The mapped paths of every controller handler with the given method name. */
    private static List<String> healthPathsForHandler(String handlerName) {
        List<String> out = new ArrayList<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
            String base = (classMapping != null && classMapping.value().length > 0)
                    ? classMapping.value()[0] : "";
            for (Method method : controller.getDeclaredMethods()) {
                if (!method.getName().equals(handlerName)) {
                    continue;
                }
                String path = pathOf(method);
                if (path != null) {
                    out.add(base + path);
                }
            }
        }
        return out;
    }

    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return firstOrEmpty(get.value());
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return firstOrEmpty(post.value());
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) return firstOrEmpty(put.value());
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) return firstOrEmpty(patch.value());
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) return firstOrEmpty(delete.value());
        return null;
    }

    private static String firstOrEmpty(String[] values) {
        return (values != null && values.length > 0) ? values[0] : "";
    }

    // --------------------------------------------------------------- readme

    private static boolean isHealthPath(String verbAndPath) {
        String path = verbAndPath.substring(verbAndPath.indexOf(' ') + 1);
        return path.endsWith("/health") || path.endsWith("/status");
    }

    /** Every row of the README's endpoint table: `VERB /path` → the row's text. */
    private static Map<String, String> readmeTableRows() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : readReadme().split("\n")) {
            Matcher m = TABLE_ROW.matcher(line);
            if (m.matches()) {
                rows.put(m.group(1) + " " + m.group(2).trim(), m.group(3));
            }
        }
        assertTrue(!rows.isEmpty(), "no endpoint table rows were read from the README");
        return rows;
    }

    private static String readReadme() {
        try {
            return new String(Files.readAllBytes(readmePath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Locate the demo's own README. The demo module's base directory is the
     * working directory Maven runs the tests in, but walk up a few levels as a
     * fallback — and require a distinctive marker, so the repository root's
     * README (which also exists) can never be picked up by accident.
     */
    private static Path readmePath() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path candidate = dir.resolve("README.md");
            if (Files.isRegularFile(candidate)) {
                try {
                    String text = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
                    if (text.contains("/demo/observations/status")) {
                        return candidate;
                    }
                } catch (IOException ignored) {
                    // keep looking
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "the demo's README.md was not found from " + Paths.get("").toAbsolutePath());
    }
}
