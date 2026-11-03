package com.ablueforce.cortexce.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mechanical facts derived from the tool trace itself.
 *
 * <p>Type confusion clusters around domain/category crossings (security hardening that is
 * filed as a bug fix, docs-only edits filed as features). Asking the model to infer those
 * boundaries from prose invites guessing, so the boundary evidence is computed here from
 * the trace and handed to the model as observations. The block never carries a label: it
 * reports which kinds of files moved and which risk paths were touched, and the model still
 * applies the type checklist.</p>
 *
 * <p>Round 12 adds candidate artifacts and candidate identifiers. Titles lost the edited
 * artifact because the model had to find it itself in a long trace; the trace already
 * states which file the tool edited and how many lines each file changed, so that ranking
 * is handed over as an observation. The block still contains no title and no answer.</p>
 */
public final class TraceSignals {

    /** File extensions shared by every path pattern below (alternatives longest-first). */
    private static final String EXTENSION = "(?:java|kts|kt|groovy|scala|py|jsx|js|tsx|ts|go|rs"
            + "|rb|php|cpp|cc|c|hpp|h|cs|swift|sql|xml|yml|yaml|json|toml|ini|properties|gradle"
            + "|sh|ps1|proto|mdx|md|txt|adoc|rst|html|css|scss|vue|svelte)";

    /**
     * A path ends at a non-alphanumeric character. Without this boundary the alternation
     * "…|c|…" matches {@code Object.create} as a C file and "…|h|…" matches {@code index.h}
     * out of {@code index.html}, which silently misfiles a documentation file as source.
     */
    private static final String PATH_TOKEN = "[\\w./@\\-]+?\\." + EXTENSION + "(?![A-Za-z0-9])";

    private static final Pattern FILE_PATH = Pattern.compile(PATH_TOKEN);

    /**
     * A build file with no extension. Without this the edit stat of {@code backend/Dockerfile}
     * is never parsed, so the file ranking gives it zero changed lines and a documentation
     * file becomes the subject.
     */
    private static final String EXTENSIONLESS_TOKEN =
            "(?:[\\w.@\\-]+/)*(?:Dockerfile|Makefile|Jenkinsfile|Procfile|Gemfile"
                    + "|Vagrantfile|LICENSE|CODEOWNERS|OWNERS)(?![\\w.])";

    /** Tool result lines such as {@code Edit a/Foo.java: removed 3 line(s), added 5 line(s)}. */
    private static final Pattern EDIT_STAT = Pattern.compile(
            "\\b(?:Edit|Read|Write|Wrote)\\s+(" + PATH_TOKEN + "|" + EXTENSIONLESS_TOKEN
                    + ")\\s*:[^;\\n]*?"
                    + "removed\\s+(\\d+)\\s+line\\(s\\),\\s+added\\s+(\\d+)\\s+line\\(s\\)",
            Pattern.CASE_INSENSITIVE);

    /** Build and CI files that carry no extension, so the path pattern above misses them. */
    private static final Pattern EXTENSIONLESS_PATH = Pattern.compile(
            "(^|[\\s\"'=(])((?:[\\w.@\\-]+/)*)(Dockerfile|Makefile|Jenkinsfile|Procfile|Gemfile"
                    + "|Vagrantfile|LICENSE|CODEOWNERS|OWNERS)(?![\\w.])");

    private static final Pattern TEST_PATH = Pattern.compile(
            "(^|/)(src/test|test|tests|__tests__|spec|specs)(/|\\.)|Test[^/]*\\.(java|kt)$"
                    + "|Tests?\\.java$|_test\\.(py|go)$|\\.(test|spec)\\.(js|jsx|ts|tsx)$|Spec\\.(java|kt)$");

    private static final Pattern DOC_PATH = Pattern.compile(
            "\\.(md|mdx|txt|adoc|rst|html)$|(^|/)docs?/");

    private static final Pattern CONFIG_PATH = Pattern.compile(
            "application[^/]*\\.(yml|yaml|properties)$|docker-compose[^/]*\\.(yml|yaml)$"
                    + "|Dockerfile$|pom\\.xml$|build\\.gradle(\\.kts)?$|\\.toml$|\\.ini$|\\.env$"
                    + "|(^|/)config/|\\.(yml|yaml|properties)$");

    private static final Pattern SOURCE_PATH = Pattern.compile(
            "\\.(java|kt|kts|groovy|scala|py|js|jsx|ts|tsx|go|rs|rb|php|c|cc|cpp|h|hpp|cs|swift"
                    + "|vue|svelte|sql)$");

    private static final Pattern SECURITY_TEXT = Pattern.compile(
            "authenticat|authoriz|auth[^a-z]|login|token|password|passwd|secret|credential"
                    + "|crypto|encrypt|decrypt|hmac|hash|salt|sanitiz|redact|escape|csrf|xss"
                    + "|injection|permission|privilege|mask|cipher|certificate|tls",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PUBLIC_API = Pattern.compile(
            "\\+\\s*(public|protected)\\s+(static\\s+)?(final\\s+)?(class|interface|record|enum|void"
                    + "|synchronized|[A-Za-z_<][\\w<>,.?\\[\\] ]*\\s+[A-Za-z_]\\w*\\s*\\()"
                    + "|added\\s+(a\\s+)?public|new\\s+public\\s+", Pattern.CASE_INSENSITIVE);

    /**
     * Words the trace uses when a defect is being repaired. Type misses concentrate on
     * expected {@code bugfix} answered {@code feature}: the model has no observable that
     * says "something was broken", only prose it may read as "new behaviour".
     *
     * <p>Only the tool input and the removed side of a diff count. A defect word inside
     * newly added prose is prose about defects (a prompt rule that mentions "whose bug it
     * reproduces"), not evidence that this change repairs one; treating it as evidence made
     * the model answer {@code bugfix} on feature and change commits.</p>
     */
    private static final Pattern DEFECT_TEXT = Pattern.compile(
            "\\b(error[a-z]*|fail(?:ed|s|ure)?|fix(?:es|ed)?|bugs?|regressions?|crash(?:es|ed)?"
                    + "|broken|incorrect|wrong|invalid|exception|panic|corrupt(?:ed|ion)?"
                    + "|defect|hotfix)\\b", Pattern.CASE_INSENSITIVE);

    /** A {@code 'old' -> 'new'} snippet in an edit stat; group 1 is the removed text. */
    private static final Pattern EDIT_PAIR = Pattern.compile("'([^']*)'\\s*->\\s*'[^']*'");

    private static final Pattern RENAME_TEXT = Pattern.compile(
            "renamed|rename\\b|moved\\s+from|\\bR[0-9]{2,3}\\b.*\\b(renamed|=>)", Pattern.CASE_INSENSITIVE);

    /** Class, interface, record or enum declarations, including ones in a diff snippet. */
    private static final Pattern CLASS_DECL = Pattern.compile(
            "\\b(?:class|interface|record|enum)\\s+([A-Z][\\w$]*)");

    /** Declared methods and constructors: a visibility/modifier keyword then a name and "(". */
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(?:^|[\\s;{}(])(?:public|protected|private|static|final|abstract|synchronized"
                    + "|default|native)\\b[^;(){}\"]*?\\b([A-Za-z_$][\\w$]*)\\s*\\(");

    /** A configuration key at the start of a line: {@code server.port: 8080} or {@code key=value}. */
    private static final Pattern CONFIG_KEY = Pattern.compile(
            "(?:^|\\n)[ \\t]*([a-z][a-z0-9_.-]{2,})[ \\t]*[:=][ \\t]*\\S");

    /** A quoted absolute path, which is how an endpoint is written in code or docs. */
    private static final Pattern ENDPOINT = Pattern.compile(
            "[\"'`](/(?:[A-Za-z0-9_{}.-]+/)*[A-Za-z0-9_{}.-]+)[\"'`]");

    /** The observation validator rejects a title longer than this. */
    private static final int TITLE_MAX_CHARS = 100;

    private static final int MAX_ARTIFACTS = 8;
    private static final int MAX_IDENTIFIERS = 16;
    private static final int MAX_SYMBOLS = 6;
    private static final int MAX_CONFIG_KEYS = 4;
    private static final int MAX_ENDPOINTS = 4;

    /** Tool-input keys that look like configuration keys but carry no identifier. */
    private static final Set<String> META_KEYS = Set.of(
            "tool_name", "file_path", "read_files", "old_string", "new_string", "content",
            "command", "description", "pattern", "glob", "limit", "offset", "cwd",
            "occurred_at", "tool_input", "tool_output");

    /** One touched artifact, ranked by the number of changed lines the trace reports. */
    public record Candidate(String path, int changedLines) {
    }

    public record Signals(
            int files,
            int testFiles,
            int docFiles,
            int configFiles,
            int sourceFiles,
            boolean renameOrMove,
            boolean addsPublicApi,
            boolean securityRelated,
            boolean defectEvidence,
            List<Candidate> artifacts,
            List<String> identifiers) {

        public boolean configOnly() {
            return files > 0 && configFiles == files && sourceFiles == 0 && testFiles == 0;
        }

        public boolean docsOnly() {
            return files > 0 && docFiles == files;
        }

        public boolean testsOnly() {
            return files > 0 && testFiles == files && sourceFiles == 0 && docFiles == 0;
        }
    }

    private TraceSignals() {
    }

    public static Signals of(String toolInput, String toolOutput) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        collect(seen, toolInput);
        collect(seen, toolOutput);
        int tests = 0;
        int docs = 0;
        int configs = 0;
        int sources = 0;
        for (String path : seen.keySet()) {
            // Exclusive buckets: a test file is a test, not source, otherwise a test-only
            // change would never look test-only.
            if (TEST_PATH.matcher(path).find()) {
                tests++;
            } else if (DOC_PATH.matcher(path).find()) {
                docs++;
            } else if (CONFIG_PATH.matcher(path).find()) {
                configs++;
            } else if (SOURCE_PATH.matcher(path).find()) {
                sources++;
            }
        }
        String text = (toolInput == null ? "" : toolInput) + "\n" + (toolOutput == null ? "" : toolOutput);
        List<Candidate> artifacts = rank(seen, text);
        List<String> identifiers = identifiers(artifacts, text);
        return new Signals(seen.size(), tests, docs, configs, sources,
                RENAME_TEXT.matcher(text).find(),
                PUBLIC_API.matcher(text).find(),
                SECURITY_TEXT.matcher(text).find(),
                defectEvidence(toolInput, toolOutput),
                artifacts, identifiers);
    }

    /**
     * A defect word counts only in the tool input or in text that was removed. Newly added
     * prose that mentions a bug is about defects, not evidence of one being repaired: the
     * generated set contains a commit that adds the sentence "whose bug it reproduces", which
     * previously flipped {@code feature} and {@code change} commits to {@code bugfix}.
     */
    private static boolean defectEvidence(String toolInput, String toolOutput) {
        if (toolInput != null && DEFECT_TEXT.matcher(toolInput).find()) {
            return true;
        }
        String output = toolOutput == null ? "" : toolOutput;
        StringBuilder removed = new StringBuilder();
        Matcher pair = EDIT_PAIR.matcher(output);
        boolean sawPair = false;
        while (pair.find()) {
            removed.append(pair.group(1)).append('\n');
            sawPair = true;
        }
        // No quoted diff pair means the stat itself is the only evidence left.
        return DEFECT_TEXT.matcher(sawPair ? removed : output).find();
    }

    private static void collect(Map<String, Integer> paths, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = FILE_PATH.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.length() > 3 && !candidate.startsWith("http")) {
                paths.putIfAbsent(candidate, 0);
            }
        }
        Matcher bare = EXTENSIONLESS_PATH.matcher(text);
        while (bare.find()) {
            paths.putIfAbsent(bare.group(2) + bare.group(3), 0);
        }
    }

    /**
     * Orders the touched artifacts by the changed-line count in the trace. Equal counts keep
     * the edit-stat order: the generator ranks the same numbers with a stable sort over the
     * tool's own edit lines, so the first edited file stays first and the subject matches.
     */
    private static List<Candidate> rank(Map<String, Integer> seen, String text) {
        Map<String, Integer> changed = new LinkedHashMap<>();
        Matcher stat = EDIT_STAT.matcher(text);
        while (stat.find()) {
            int lines = Integer.parseInt(stat.group(2)) + Integer.parseInt(stat.group(3));
            changed.merge(stat.group(1), lines, Integer::sum);
        }
        List<Candidate> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : changed.entrySet()) {
            ranked.add(new Candidate(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (!changed.containsKey(entry.getKey())) {
                ranked.add(new Candidate(entry.getKey(), 0));
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.changedLines(), a.changedLines()));
        return ranked.size() <= MAX_ARTIFACTS ? ranked : ranked.subList(0, MAX_ARTIFACTS);
    }

    /**
     * Candidate strings the model may copy into the title: the rank-1 artifact's path and
     * basename (a {@code .java} suffix is dropped, matching how the golden set names an
     * identifier) plus the class, method, config-key and endpoint names that literally occur
     * in the trace. Supporting artifacts stay out of this list; they are already ranked in
     * {@code <candidate_artifacts>}, and repeating their basenames here only spends tokens on
     * names the title cannot use.
     */
    private static List<String> identifiers(List<Candidate> artifacts, String text) {
        Set<String> ids = new LinkedHashSet<>();
        if (!artifacts.isEmpty()) {
            String path = artifacts.get(0).path();
            ids.add(path);
            ids.add(identifierOf(path));
        }
        collectInto(ids, CLASS_DECL, text, MAX_SYMBOLS);
        collectInto(ids, METHOD_DECL, text, MAX_SYMBOLS);
        collectConfigKeys(ids, text);
        collectInto(ids, ENDPOINT, text, MAX_ENDPOINTS);
        return List.copyOf(ids);
    }

    private static void collectInto(Set<String> ids, Pattern pattern, String text, int limit) {
        Matcher matcher = pattern.matcher(text);
        int added = 0;
        while (matcher.find() && added < limit) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty() && ids.add(value)) {
                added++;
            }
        }
    }

    private static void collectConfigKeys(Set<String> ids, String text) {
        Matcher matcher = CONFIG_KEY.matcher(text);
        int added = 0;
        while (matcher.find() && added < MAX_CONFIG_KEYS) {
            String value = matcher.group(1).trim();
            if (!META_KEYS.contains(value) && ids.add(value)) {
                added++;
            }
        }
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** The golden-set convention: a Java basename loses its suffix, others keep the extension. */
    private static String identifierOf(String path) {
        String basename = basename(path);
        return basename.endsWith(".java")
                ? basename.substring(0, basename.length() - ".java".length())
                : basename;
    }

    /**
     * The block injected into the observation prompt. Only observations: counts, yes/no
     * facts, and the candidate names the trace literally contains. No type label and no
     * title is ever written here, so the model still has to do the classification and the
     * wording.
     */
    public static String hintBlock(String toolInput, String toolOutput) {
        Signals signals = of(toolInput, toolOutput);
        StringBuilder sb = new StringBuilder();
        sb.append("<mechanical_signals>\n");
        sb.append("  <note>observed facts from the trace, not a classification</note>\n");
        Candidate primary = signals.artifacts().isEmpty() ? null : signals.artifacts().get(0);
        if (primary != null) {
            // Stated first and repeated after the candidate lists: the title keeps losing the
            // edited artifact to a supporting file or a symbol, so the requirement is spelled
            // out in the same channel that supplies the candidates.
            sb.append("  <title_contract>The title MUST end with ")
                    .append(escape(identifierOf(primary.path())))
                    .append(", copied character for character from the trace; never substitute "
                            + "another file or generalize the name.</title_contract>\n");
        }
        sb.append("  <files_touched>").append(signals.files()).append("</files_touched>\n");
        sb.append("  <test_files>").append(signals.testFiles()).append("</test_files>\n");
        sb.append("  <doc_files>").append(signals.docFiles()).append("</doc_files>\n");
        sb.append("  <config_files>").append(signals.configFiles()).append("</config_files>\n");
        sb.append("  <source_files>").append(signals.sourceFiles()).append("</source_files>\n");
        yesNo(sb, "rename_or_move_only", signals.renameOrMove());
        yesNo(sb, "adds_public_api", signals.addsPublicApi());
        yesNo(sb, "security_related_text", signals.securityRelated());
        yesNo(sb, "defect_evidence", signals.defectEvidence());
        yesNo(sb, "config_only_change", signals.configOnly());
        yesNo(sb, "docs_only_change", signals.docsOnly());
        yesNo(sb, "tests_only_change", signals.testsOnly());
        if (primary != null) {
            // The trace already names the edited file and reports changed lines, so the
            // subject is an observation rather than something to search for. It is not a
            // classification: the model still writes the title, facts and concepts.
            sb.append("  <title_subject_candidate>").append(escape(primary.path()))
                    .append("</title_subject_candidate>\n");
            sb.append("  <candidate_artifacts>");
            int rank = 0;
            for (Candidate candidate : signals.artifacts()) {
                rank++;
                if (rank > 1) {
                    sb.append(", ");
                }
                sb.append(escape(candidate.path())).append(" [")
                        .append(candidate.changedLines()).append("]");
            }
            sb.append("</candidate_artifacts>\n");
        }
        if (!signals.identifiers().isEmpty()) {
            // One comma-separated line: per-identifier XML tags cost about fifteen
            // characters each and the list is read as a set of strings, not as a tree.
            sb.append("  <candidate_identifiers>");
            int index = 0;
            for (String identifier : signals.identifiers()) {
                if (index++ > 0) {
                    sb.append(", ");
                }
                sb.append(escape(identifier));
            }
            sb.append("</candidate_identifiers>\n");
        }
        if (primary != null) {
            String identifier = identifierOf(primary.path());
            // The validator rejects a title over 100 characters, and the required identifier
            // can take most of that budget. Naming the remaining characters turns "keep it
            // short" into arithmetic the model can apply, so a forced suffix does not turn
            // long filenames into rejected observations.
            int verbObjectBudget = Math.max(0, TITLE_MAX_CHARS - identifier.length());
            sb.append("  <title_identifier_required>").append(escape(identifier))
                    .append("</title_identifier_required>\n");
            sb.append("  <title_budget identifier_chars=\"").append(identifier.length())
                    .append("\" verb_object_chars=\"").append(verbObjectBudget).append("\"/>\n");
        }
        sb.append("</mechanical_signals>");
        return sb.toString();
    }

    private static void yesNo(StringBuilder sb, String name, boolean value) {
        sb.append("  <").append(name).append('>').append(value ? "yes" : "no")
                .append("</").append(name).append(">\n");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
