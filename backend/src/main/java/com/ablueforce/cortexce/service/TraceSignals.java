package com.ablueforce.cortexce.service;

import java.util.LinkedHashSet;
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
 */
public final class TraceSignals {

    /**
     * A path ends at a non-alphanumeric character. Without this boundary the alternation
     * "…|c|…" matches {@code Object.create} as a C file and "…|h|…" matches {@code index.h}
     * out of {@code index.html}, which silently misfiles a documentation file as source.
     */
    private static final Pattern FILE_PATH = Pattern.compile(
            "[\\w./@\\-]+?\\.(java|kts|kt|groovy|scala|py|jsx|js|tsx|ts|go|rs|rb|php|cpp|cc|c"
                    + "|hpp|h|cs|swift|sql|xml|yml|yaml|json|toml|ini|properties|gradle|sh|ps1"
                    + "|proto|mdx|md|txt|adoc|rst|html|css|scss|vue|svelte)(?![A-Za-z0-9])");

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
     */
    private static final Pattern DEFECT_TEXT = Pattern.compile(
            "\\b(error[a-z]*|fail(?:ed|s|ure)?|fix(?:es|ed)?|bugs?|regressions?|crash(?:es|ed)?"
                    + "|broken|incorrect|wrong|invalid|exception|panic|corrupt(?:ed|ion)?"
                    + "|defect|hotfix)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RENAME_TEXT = Pattern.compile(
            "renamed|rename\\b|moved\\s+from|\\bR[0-9]{2,3}\\b.*\\b(renamed|=>)", Pattern.CASE_INSENSITIVE);

    public record Signals(
            int files,
            int testFiles,
            int docFiles,
            int configFiles,
            int sourceFiles,
            boolean renameOrMove,
            boolean addsPublicApi,
            boolean securityRelated,
            boolean defectEvidence) {

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
        Set<String> paths = new LinkedHashSet<>();
        collect(paths, toolInput);
        collect(paths, toolOutput);
        int tests = 0;
        int docs = 0;
        int configs = 0;
        int sources = 0;
        for (String path : paths) {
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
        return new Signals(paths.size(), tests, docs, configs, sources,
                RENAME_TEXT.matcher(text).find(),
                PUBLIC_API.matcher(text).find(),
                SECURITY_TEXT.matcher(text).find(),
                DEFECT_TEXT.matcher(text).find());
    }

    private static void collect(Set<String> paths, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = FILE_PATH.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.length() > 3 && !candidate.startsWith("http")) {
                paths.add(candidate);
            }
        }
        Matcher bare = EXTENSIONLESS_PATH.matcher(text);
        while (bare.find()) {
            paths.add(bare.group(2) + bare.group(3));
        }
    }

    /**
     * The block injected into the observation prompt. Only counts and yes/no observations:
     * no type label is ever written here, so the model still has to apply the checklist.
     */
    public static String hintBlock(String toolInput, String toolOutput) {
        Signals signals = of(toolInput, toolOutput);
        StringBuilder sb = new StringBuilder();
        sb.append("<mechanical_signals>\n");
        sb.append("  <note>observed facts from the trace, not a classification</note>\n");
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
        sb.append("</mechanical_signals>");
        return sb.toString();
    }

    private static void yesNo(StringBuilder sb, String name, boolean value) {
        sb.append("  <").append(name).append('>').append(value ? "yes" : "no")
                .append("</").append(name).append(">\n");
    }
}
