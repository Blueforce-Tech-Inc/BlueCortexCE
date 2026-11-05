package com.ablueforce.cortexce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Executable layering rules, so a new shortcut fails a test instead of being
 * discovered in review.
 *
 * <p>Existing violations are baselined below with a reason. The baseline is an
 * allowlist, not a target: a new controller that talks to a repository, a new
 * service that reaches into a controller, an entity leaking out of a controller
 * signature, or a package that joins a dependency cycle all fail.
 *
 * <p>The one existing package cycle is intrinsic to Spring wiring rather than a
 * design mistake: {@code config} owns the beans, {@code service} injects
 * configuration properties, {@code repository} needs entities, and {@code util}
 * resolves configuration. Breaking it would require moving infrastructure types
 * into a new module; that is recorded here as the accepted baseline instead of
 * being silently ignored. See {@code docs/ARCHITECTURE.md}.
 */
class ArchitectureGuardTest {

    private static final String BASE_PACKAGE = "com.ablueforce.cortexce";

    /**
     * Controllers that read repositories directly. Each is a thin read
     * endpoint; adding a one-method service in front would move the same query
     * one layer down without adding behaviour, so the direct dependency is
     * accepted here and pinned by this baseline.
     */
    private static final Map<String, String> CONTROLLER_REPOSITORY_BASELINE = Map.of(
            "ViewerController.java", "viewer polling reads observations/sessions/summaries/prompts directly");

    /**
     * Methods that still return entity rows. The three viewer list endpoints
     * serialise entities directly because the bundled WebUI consumes that exact
     * JSON shape and the paging envelope is a hard contract; a parallel DTO
     * would duplicate every field and drift silently. The vector columns are
     * already excluded from serialisation on the entity itself. Moving these to
     * DTOs is a deliberate contract change, not a layering cleanup, so they are
     * pinned here with a reason instead of being changed in passing.
     */
    private static final Set<String> CONTROLLER_ENTITY_BASELINE = Set.of(
            "ViewerController#getObservations",
            "ViewerController#getSummaries",
            "ViewerController#getPrompts");

    /**
     * The only accepted strongly connected component of top-level packages.
     * A package outside this set that joins a cycle fails the test.
     */
    private static final Set<String> ACCEPTED_CYCLE_PACKAGES = Set.of(
            "config", "entity", "event", "repository", "service", "util");

    @Test
    void controllersDoNotImportRepositoriesOutsideTheBaseline() throws IOException {
        Map<String, String> offenders = new HashMap<>();
        for (Path file : javaFiles("controller")) {
            String content = Files.readString(file);
            if (content.contains("import " + BASE_PACKAGE + ".repository.")) {
                offenders.put(file.getFileName().toString(), "");
            }
        }

        Set<String> unknown = new HashSet<>(offenders.keySet());
        unknown.removeAll(CONTROLLER_REPOSITORY_BASELINE.keySet());
        assertThat(unknown)
                .as("controller -> repository imports not in the baselined set; "
                        + "route the call through a service or add a reasoned baseline entry")
                .isEmpty();

        // The baseline names must still exist. A stale entry hides nothing
        // today, but it makes the next reader trust a rule that is not there.
        Set<String> missing = new HashSet<>(CONTROLLER_REPOSITORY_BASELINE.keySet());
        missing.removeAll(offenders.keySet());
        assertThat(missing)
                .as("baselined controller no longer imports a repository; remove the stale entry")
                .isEmpty();
    }

    @Test
    void servicesDoNotImportControllers() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles("service")) {
            String content = Files.readString(file);
            if (content.contains("import " + BASE_PACKAGE + ".controller.")) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertThat(offenders).as("services must not depend on controllers").isEmpty();
    }

    @Test
    void controllersDoNotReturnEntitiesOutsideTheBaseline() throws IOException {
        Pattern entityType = Pattern.compile(
                "\\b(ObservationEntity|SessionEntity|SummaryEntity|UserPromptEntity|"
                        + "PendingMessageEntity|MemoryScopeEntity)\\b");
        Pattern methodName = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*$");
        Set<String> offenders = new HashSet<>();
        for (Path file : javaFiles("controller")) {
            String simpleName = file.getFileName().toString().replace(".java", "");
            // Collapse whitespace so a signature split over several lines is still
            // one match.
            String content = Files.readString(file).replaceAll("\\s+", " ");
            Matcher matcher = Pattern.compile("\\bpublic\\s+([^;{]*?)\\(").matcher(content);
            while (matcher.find()) {
                String signature = matcher.group(1);
                if (!entityType.matcher(signature).find()) {
                    continue;
                }
                Matcher nameMatcher = methodName.matcher(signature);
                String method = nameMatcher.find() ? nameMatcher.group(1) : signature;
                offenders.add(simpleName + "#" + method);
            }
        }

        Set<String> unknown = new HashSet<>(offenders);
        unknown.removeAll(CONTROLLER_ENTITY_BASELINE);
        assertThat(unknown)
                .as("controllers must map entities into a DTO before returning them; "
                        + "baselined methods: " + CONTROLLER_ENTITY_BASELINE)
                .isEmpty();

        Set<String> missing = new HashSet<>(CONTROLLER_ENTITY_BASELINE);
        missing.removeAll(offenders);
        assertThat(missing)
                .as("baselined method no longer returns an entity; remove the stale entry")
                .isEmpty();
    }

    @Test
    void noNewPackageCycles() throws IOException {
        Map<String, Set<String>> graph = topLevelPackageGraph();
        Set<String> cyclicPackages = new HashSet<>();
        for (Set<String> component : stronglyConnectedComponents(graph)) {
            if (component.size() > 1) {
                cyclicPackages.addAll(component);
            }
        }

        Set<String> unknown = new HashSet<>(cyclicPackages);
        unknown.removeAll(ACCEPTED_CYCLE_PACKAGES);
        assertThat(unknown)
                .as("new package dependency cycle involving %s; accepted cycle: %s",
                        unknown, ACCEPTED_CYCLE_PACKAGES)
                .isEmpty();
    }

    // ------------------------------------------------------------------

    private static List<Path> javaFiles(String subPackage) throws IOException {
        Path root = sourceRoot().resolve(BASE_PACKAGE.replace('.', '/')).resolve(subPackage);
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path sourceRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path direct = workingDirectory.resolve("src/main/java");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = workingDirectory.resolve("backend/src/main/java");
        assertThat(Files.isDirectory(nested))
                .as("could not locate src/main/java from %s", workingDirectory)
                .isTrue();
        return nested;
    }

    private static Map<String, Set<String>> topLevelPackageGraph() throws IOException {
        Path root = sourceRoot().resolve(BASE_PACKAGE.replace('.', '/'));
        Map<String, Set<String>> graph = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                String packageName = packagePrefix(root, file);
                if (packageName.isEmpty()) {
                    continue;
                }
                graph.computeIfAbsent(packageName, ignored -> new LinkedHashSet<>());
                for (String dependency : importedTopLevelPackages(file)) {
                    if (!dependency.equals(packageName)) {
                        graph.get(packageName).add(dependency);
                        graph.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>());
                    }
                }
            }
        }
        return graph;
    }

    private static String packagePrefix(Path root, Path file) {
        Path relative = root.relativize(file.getParent());
        if (relative.getNameCount() == 0) {
            return "";
        }
        return relative.getName(0).toString();
    }

    private static Set<String> importedTopLevelPackages(Path file) throws IOException {
        Pattern importPattern = Pattern.compile(
                "^\\s*import\\s+(?:static\\s+)?" + Pattern.quote(BASE_PACKAGE) + "\\.([a-zA-Z0-9_]+)\\.");
        Set<String> dependencies = new HashSet<>();
        for (String line : Files.readAllLines(file)) {
            Matcher matcher = importPattern.matcher(line);
            if (matcher.find()) {
                dependencies.add(matcher.group(1));
            }
        }
        return dependencies;
    }

    /**
     * Tarjan's algorithm. The graph has a handful of nodes, so clarity beats a
     * library dependency here.
     */
    private static List<Set<String>> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        List<String> stack = new ArrayList<>();
        List<Set<String>> components = new ArrayList<>();
        int[] nextIndex = {0};

        for (String node : graph.keySet()) {
            if (!index.containsKey(node)) {
                visit(node, graph, index, lowLink, onStack, stack, components, nextIndex);
            }
        }
        return components;
    }

    private static void visit(String node, Map<String, Set<String>> graph, Map<String, Integer> index,
                              Map<String, Integer> lowLink, Set<String> onStack, List<String> stack,
                              List<Set<String>> components, int[] nextIndex) {
        index.put(node, nextIndex[0]);
        lowLink.put(node, nextIndex[0]);
        nextIndex[0]++;
        stack.add(node);
        onStack.add(node);

        for (String neighbour : graph.getOrDefault(node, Set.of())) {
            if (!index.containsKey(neighbour)) {
                visit(neighbour, graph, index, lowLink, onStack, stack, components, nextIndex);
                lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(neighbour)));
            } else if (onStack.contains(neighbour)) {
                lowLink.put(node, Math.min(lowLink.get(node), index.get(neighbour)));
            }
        }

        if (lowLink.get(node).equals(index.get(node))) {
            Set<String> component = new HashSet<>();
            String member;
            do {
                member = stack.remove(stack.size() - 1);
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            components.add(component);
        }
    }
}
