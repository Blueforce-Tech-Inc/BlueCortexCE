package com.ablueforce.cortexce.eval;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A6 action classes, derived from the tool trace instead of hand-picked verbs.
 *
 * <p>The rule table was fixed before any round-6 answer was scored and may not be tuned per
 * sample. Precedence (first match wins):</p>
 * <ol>
 *   <li>{@code diagnose} — the tool is read-only (Read/Grep/Glob/LS) or a Shell call whose
 *       output reports no change (clean tree, unchanged lockfile, no reformatting, passing
 *       checks).</li>
 *   <li>{@code document} — the changed path is a documentation artifact: {@code *.md}, anything
 *       under {@code docs/}, or README/CHANGELOG/CONTRIBUTING/SECURITY.</li>
 *   <li>{@code secure} — the diff or its reported outcome mentions authorization, roles,
 *       permissions, credentials, passwords, secrets/API keys, IAM, redaction/sanitizing, XXE,
 *       DOCTYPE, CSRF, injection, path traversal, HttpOnly/SameSite, CORS, encryption or rate
 *       limiting/429.</li>
 *   <li>{@code remove} — the edit replaces a non-empty value with an empty one.</li>
 *   <li>{@code create} — a {@code Write} that reports a written/created file, or an {@code Edit}
 *       to {@code src/test} whose outcome starts with “Added …”.</li>
 *   <li>{@code modify} — every other edit to existing code, configuration or deployment.</li>
 * </ol>
 *
 * <p>A sample whose trace matches none of these is {@link #UNCLASSIFIABLE} and is dropped from
 * the A6 denominator; the golden-set data test fails if more than 10% of samples land there.</p>
 */
public final class GoldenSetActionRules {

    public static final Set<String> ACTIONS =
            Set.of("create", "modify", "remove", "secure", "document", "diagnose");

    public static final String UNCLASSIFIABLE = "unclassifiable";

    private static final Pattern DOC_PATH = Pattern.compile(
            "(?i)(\\.md$|(^|/)docs?/|(^|/)(README|CHANGELOG|CONTRIBUTING|SECURITY)(\\.|$))");

    private static final Pattern SECURITY = Pattern.compile(
            "(?i)(authoriz|role_admin|permission|credential|password|secret|api[ -]?key|\\biam\\b"
                    + "|redact|sanitiz|xxe|doctype|csrf|injection|traversal|httponly|samesite|cors"
                    + "|encrypt|cipher|rate limit|\\b429\\b)");

    private static final Pattern NO_CHANGE = Pattern.compile(
            "(?i)(no change|no edits|no documented change|read-only|0 files changed|unchanged"
                    + "|no matches|no file change|no source change|without a change|no errors"
                    + "|working tree clean|no files reformatted|package-lock.json unchanged)");

    private static final Pattern FILE_CREATED = Pattern.compile("(?i)file (written|created)");
    private static final Pattern EMPTY_REPLACEMENT = Pattern.compile("new_string=\\\"\\\"");
    private static final Pattern ADDED_OUTCOME = Pattern.compile("(?i)^added ");
    private static final Pattern FILE_PATH = Pattern.compile("file_path=([^\\s]+)");
    private static final Pattern WORD = Pattern.compile("[a-z0-9+]+");

    /** Leading-verb → action class. Only the contract verbs are listed. */
    private static final Map<String, String> ACTION_BY_VERB = Map.ofEntries(
            Map.entry("add", "create"),
            Map.entry("fix", "modify"), Map.entry("change", "modify"),
            Map.entry("refactor", "modify"), Map.entry("convert", "modify"),
            Map.entry("migrate", "modify"), Map.entry("set", "modify"),
            Map.entry("enable", "modify"),
            Map.entry("remove", "remove"),
            Map.entry("harden", "secure"), Map.entry("restrict", "secure"),
            Map.entry("redact", "secure"),
            Map.entry("document", "document"), Map.entry("record", "document"));

    private GoldenSetActionRules() {
    }

    /** Derive the action class from the recorded trace alone. */
    public static String derive(GoldenSet.Sample sample) {
        String tool = sample.toolName == null ? "" : sample.toolName;
        String input = sample.toolInput == null ? "" : sample.toolInput;
        String output = sample.toolOutput == null ? "" : sample.toolOutput;
        String path = path(input);

        if (Set.of("Read", "Grep", "Glob", "LS").contains(tool)) {
            return "diagnose";
        }
        if ("Shell".equals(tool) && NO_CHANGE.matcher(output).find()) {
            return "diagnose";
        }
        if (DOC_PATH.matcher(path).find()) {
            return "document";
        }
        if (SECURITY.matcher(input).find() || SECURITY.matcher(output).find()) {
            return "secure";
        }
        if (EMPTY_REPLACEMENT.matcher(input).find()) {
            return "remove";
        }
        if ("Write".equals(tool) && FILE_CREATED.matcher(output).find()) {
            return "create";
        }
        if (path.contains("src/test") && ADDED_OUTCOME.matcher(output).find()) {
            return "create";
        }
        return "modify";
    }

    /** Action class of the title's leading verb, or {@code null} when it is not a contract verb. */
    public static String actionForTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        Matcher matcher = WORD.matcher(title.toLowerCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        String lemma = GoldenSetScorer.verbLemma(matcher.group());
        return lemma == null ? null : ACTION_BY_VERB.get(lemma);
    }

    static String path(String input) {
        Matcher matcher = FILE_PATH.matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }
}
