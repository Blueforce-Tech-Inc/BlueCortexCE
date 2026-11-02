package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.service.ExtractionValidationService;
import com.ablueforce.cortexce.util.XmlParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure scoring for the session-summary golden set. No provider call, no Spring context.
 *
 * <p>Three signals:</p>
 * <ol>
 *   <li>{@code request} must name the request substance (keyword coverage + identifiers);</li>
 *   <li>{@code learned} must recall the technical anchors the session established;</li>
 *   <li>every clause in {@code request} and {@code learned} must be grounded in the recorded
 *       tool trace: any concrete anchor it names must exist verbatim, and a clause that
 *       names no anchor must pass the facts-support rule.</li>
 * </ol>
 */
public final class SummaryGoldenSetScorer {

    private static final Pattern CAMELCASE = Pattern.compile(
            "[A-Za-z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)+");
    /** A file path, URL, or file name with a known extension. */
    private static final Pattern PATH_OR_FILE = Pattern.compile(
            "https?://\\S+"
                + "|\\b\\w+(?:[.-]\\w+)*/[\\w./-]*\\b"
                + "|\\b\\w+\\.(?:java|ts|tsx|jsx?|py|go|rs|kt|json|yml|yaml|xml|sql|md|txt"
                + "|properties|toml|gradle|sh)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern CLAUSE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\n+");

    private SummaryGoldenSetScorer() {
    }

    public static final class Outcome {
        public final SummaryGoldenSet.Sample sample;
        public final String raw;
        public final XmlParser.ParsedSummary summary;
        public final boolean callFailed;
        public final String failure;
        public final long elapsedMs;
        public final String status;
        public final int runIndex;
        public final boolean formatError;
        public final String formatErrorReason;

        public Outcome(SummaryGoldenSet.Sample sample, String raw, XmlParser.ParsedSummary summary,
                       boolean callFailed, String failure, long elapsedMs, String status,
                       int runIndex, boolean formatError, String formatErrorReason) {
            this.sample = sample;
            this.raw = raw;
            this.summary = summary;
            this.callFailed = callFailed;
            this.failure = failure;
            this.elapsedMs = elapsedMs;
            this.status = status;
            this.runIndex = runIndex;
            this.formatError = formatError;
            this.formatErrorReason = formatErrorReason;
        }
    }

    public static final class SampleScore {
        public String id;
        public String category;
        public double requestCoverage;
        public boolean requestIdentifierHit;
        public double requestSupportRate;
        public double learnedRecall;
        public boolean learnedIdentifierHit;
        public double learnedSupportRate;
        public boolean formatError;
        public String formatErrorReason;
        public long elapsedMs;
        public String status;
        public List<String> problems = new ArrayList<>();
    }

    public static final class Aggregate {
        public int total;
        public int callFailures;
        public int formatErrors;
        public double requestCoverage;
        public double requestIdentifierHitRate;
        public double requestSupportRate;
        public double learnedRecall;
        public double learnedIdentifierHitRate;
        public double learnedSupportRate;
        public long p50Ms;
        public long p95Ms;
        public long maxMs;
        public Map<String, Integer> statusCounts = new LinkedHashMap<>();
        public List<SampleScore> scores = new ArrayList<>();
        public List<String> problems = new ArrayList<>();
    }

    public static Aggregate score(List<Outcome> outcomes) {
        Aggregate agg = new Aggregate();
        agg.total = outcomes.size();
        List<Long> latencies = new ArrayList<>();
        double reqCov = 0;
        double reqId = 0;
        double reqSupport = 0;
        double learnedRecall = 0;
        double learnedId = 0;
        double learnedSupport = 0;
        int scored = 0;

        for (Outcome outcome : outcomes) {
            SampleScore score = new SampleScore();
            score.id = outcome.sample.id;
            score.category = outcome.sample.category;
            score.elapsedMs = outcome.elapsedMs;
            score.status = outcome.status;
            score.formatError = outcome.formatError;
            score.formatErrorReason = outcome.formatErrorReason;
            if (outcome.status != null) {
                agg.statusCounts.merge(outcome.status, 1, Integer::sum);
            }
            if (outcome.callFailed) {
                agg.callFailures++;
            }
            if (!outcome.formatError && outcome.summary != null) {
                String request = nullToEmpty(outcome.summary.request);
                String learned = nullToEmpty(outcome.summary.learned);
                score.requestCoverage = GoldenSetScorer.coverage(
                        outcome.sample.requestKeywords, request);
                score.requestIdentifierHit = GoldenSetScorer.identifiersPresent(
                        outcome.sample.requestIdentifiers, request);
                score.requestSupportRate = supportRate(request,
                        nullToEmpty(outcome.sample.lastAssistantMessage) + "\n"
                                + nullToEmpty(outcome.sample.toolTrace));
                score.learnedRecall = GoldenSetScorer.recall(
                        outcome.sample.learnedKeywords, clauses(learned));
                score.learnedIdentifierHit = GoldenSetScorer.identifiersPresent(
                        outcome.sample.learnedIdentifiers, learned);
                score.learnedSupportRate = supportRate(learned,
                        nullToEmpty(outcome.sample.toolTrace));
                if (score.requestCoverage < 1.0) {
                    score.problems.add("request coverage " + pct(score.requestCoverage)
                            + " (expected " + outcome.sample.requestKeywords + ")");
                }
                if (!score.requestIdentifierHit) {
                    score.problems.add("request lost identifiers "
                            + outcome.sample.requestIdentifiers);
                }
                if (score.requestSupportRate < 1.0) {
                    score.problems.add("request not fully trace-grounded "
                            + pct(score.requestSupportRate));
                }
                if (score.learnedRecall < 1.0) {
                    score.problems.add("learned recall " + pct(score.learnedRecall)
                            + " (expected " + outcome.sample.learnedKeywords + ")");
                }
                if (!score.learnedIdentifierHit) {
                    score.problems.add("learned lost identifiers "
                            + outcome.sample.learnedIdentifiers);
                }
                if (score.learnedSupportRate < 1.0) {
                    score.problems.add("learned not fully trace-grounded "
                            + pct(score.learnedSupportRate));
                }
                reqCov += score.requestCoverage;
                reqId += score.requestIdentifierHit ? 1 : 0;
                reqSupport += score.requestSupportRate;
                learnedRecall += score.learnedRecall;
                learnedId += score.learnedIdentifierHit ? 1 : 0;
                learnedSupport += score.learnedSupportRate;
                scored++;
            } else {
                agg.formatErrors++;
                String reason = outcome.formatErrorReason == null ? "unparseable summary"
                        : outcome.formatErrorReason;
                score.problems.add("format error: " + reason);
            }
            agg.scores.add(score);
            latencies.add(outcome.elapsedMs);
            for (String problem : score.problems) {
                agg.problems.add(outcome.sample.id + ": " + problem);
            }
        }

        agg.requestCoverage = ratio(reqCov, scored);
        agg.requestIdentifierHitRate = ratio(reqId, scored);
        agg.requestSupportRate = ratio(reqSupport, scored);
        agg.learnedRecall = ratio(learnedRecall, scored);
        agg.learnedIdentifierHitRate = ratio(learnedId, scored);
        agg.learnedSupportRate = ratio(learnedSupport, scored);
        agg.p50Ms = percentile(latencies, 0.50);
        agg.p95Ms = percentile(latencies, 0.95);
        agg.maxMs = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        return agg;
    }

    /**
     * Fraction of clauses that are grounded in the evidence. A grounded clause must pass the
     * facts-support rule and must not introduce a CamelCase identifier that is absent from the
     * evidence.
     */
    public static double supportRate(String text, String evidence) {
        List<String> clauses = clauses(text);
        if (clauses.isEmpty()) {
            return 0.0;
        }
        int supported = 0;
        for (String clause : clauses) {
            if (grounded(clause, evidence)) {
                supported++;
            }
        }
        return (double) supported / clauses.size();
    }

    /**
     * A clause is grounded when it invents no concrete anchor and says something the
     * evidence supports.
     *
     * <p>Two shapes are accepted:</p>
     * <ol>
     *   <li>the clause names at least one concrete anchor — identifier, file/URL, or number —
     *       and every such anchor occurs verbatim in the evidence;</li>
     *   <li>the clause names no concrete anchor, in which case it must pass the facts-support
     *       rule (at least half of its significant tokens occur in the evidence).</li>
     * </ol>
     *
     * <p>This is deliberately anchor-based rather than token-overlap-based: a summary is
     * written from the trace, not copied out of it, so a correct paraphrase ("fixed the race
     * in UserCache") must not be rejected for lacking the word "concurrent". What must not
     * pass is an invented object, path or number.</p>
     */
    public static boolean grounded(String clause, String evidence) {
        if (clause == null || clause.isBlank() || evidence == null) {
            return false;
        }
        boolean hasAnchor = false;
        for (Pattern pattern : new Pattern[]{CAMELCASE, PATH_OR_FILE, NUMBER}) {
            Matcher matcher = pattern.matcher(clause);
            while (matcher.find()) {
                hasAnchor = true;
                if (!evidence.contains(matcher.group())) {
                    return false;
                }
            }
        }
        return hasAnchor || ExtractionValidationService.isSupported(clause, evidence);
    }

    static List<String> clauses(String text) {
        List<String> clauses = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return clauses;
        }
        for (String raw : CLAUSE_SPLIT.split(text.trim())) {
            String clause = raw.trim();
            if (!clause.isEmpty()) {
                clauses.add(clause);
            }
        }
        return clauses;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }
}
