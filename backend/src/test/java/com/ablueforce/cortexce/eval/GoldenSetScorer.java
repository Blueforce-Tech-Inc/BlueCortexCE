package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.service.ExtractionValidationService;
import com.ablueforce.cortexce.util.XmlParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure scoring logic for the golden-set evaluation.
 * <p>
 * No network, no Spring context: the evaluator feeds real model outcomes in,
 * and the offline unit test feeds synthetic ones. Keeping this separate is what
 * makes the scoring repeatable without spending provider calls.
 */
public final class GoldenSetScorer {

    /**
     * Keyword-coverage floor for a normalized title hit, fixed in the planning document before any
     * measurement (section 4: “keyword coverage ≥ 0.8”). With 3–4 declared keywords this means every
     * keyword must appear; it is deliberately not lowered when a run misses it.
     */
    static final double TITLE_COVERAGE_FLOOR = 0.8;

    /**
     * Small deterministic paraphrase table. Keys are canonical tokens; values are the canonical
     * token they fold into. Only morphology and very common synonyms are included — enough to stop
     * "fixed/fixes/fixing" or "concurrency/concurrent" from counting as different words, not enough
     * to hide a genuinely wrong title.
     */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("fixes", "fix"), Map.entry("fixed", "fix"), Map.entry("fixing", "fix"),
            Map.entry("repair", "fix"), Map.entry("repaired", "fix"), Map.entry("resolved", "fix"),
            Map.entry("adds", "add"), Map.entry("added", "add"), Map.entry("adding", "add"),
            Map.entry("creates", "add"), Map.entry("created", "add"), Map.entry("creating", "add"),
            Map.entry("refactors", "refactor"), Map.entry("refactored", "refactor"),
            Map.entry("refactoring", "refactor"),
            Map.entry("changes", "change"), Map.entry("changed", "change"), Map.entry("changing", "change"),
            Map.entry("enables", "enable"), Map.entry("enabled", "enable"), Map.entry("enabling", "enable"),
            Map.entry("restricts", "restrict"), Map.entry("restricted", "restrict"),
            Map.entry("restricting", "restrict"),
            Map.entry("converts", "convert"), Map.entry("converted", "convert"),
            Map.entry("converting", "convert"),
            Map.entry("documents", "document"), Map.entry("documented", "document"),
            Map.entry("documenting", "document"),
            Map.entry("updates", "update"), Map.entry("updated", "update"), Map.entry("updating", "update"),
            Map.entry("removes", "remove"), Map.entry("removed", "remove"), Map.entry("removing", "remove"),
            Map.entry("hardens", "harden"), Map.entry("hardened", "harden"), Map.entry("hardening", "harden"),
            Map.entry("records", "record"), Map.entry("recorded", "record"), Map.entry("recording", "record"),
            Map.entry("sets", "set"), Map.entry("setting", "set"),
            Map.entry("migrates", "migrate"), Map.entry("migrated", "migrate"),
            Map.entry("migrating", "migrate"),
            Map.entry("delegates", "delegate"), Map.entry("delegated", "delegate"),
            Map.entry("redacts", "redact"), Map.entry("redacted", "redact"),
            Map.entry("redaction", "redact"), Map.entry("masks", "redact"), Map.entry("masked", "redact"),
            Map.entry("concurrency", "concurrent"), Map.entry("concurrently", "concurrent"),
            Map.entry("races", "race"), Map.entry("racing", "race"),
            Map.entry("parameterized", "parameterized"), Map.entry("parameterised", "parameterized"),
            Map.entry("configuration", "config"), Map.entry("configured", "config"),
            Map.entry("deployed", "deploy"), Map.entry("deployment", "deploy"),
            Map.entry("deploying", "deploy"),
            Map.entry("tests", "test"), Map.entry("testing", "test"),
            Map.entry("paging", "pagination"),
            Map.entry("nullpointer", "nullpointer"), Map.entry("npe", "nullpointer"),
            Map.entry("timezone", "timezone"),
            Map.entry("db", "database"),
            Map.entry("docs", "doc"), Map.entry("documentation", "doc"));

    private GoldenSetScorer() {
    }

    /** One model answer, already parsed by the production {@link XmlParser}. */
    public static final class Outcome {
        public final GoldenSet.Sample sample;
        public final String raw;
        public final boolean skipped;
        public final XmlParser.ParsedObservation observation;
        public final boolean callFailed;
        public final String failure;
        public final long elapsedMs;
        public final String status;
        public final int runIndex;
        public final boolean validationRejected;

        public Outcome(GoldenSet.Sample sample, String raw, boolean skipped,
                       XmlParser.ParsedObservation observation, boolean callFailed,
                       String failure, long elapsedMs) {
            this(sample, raw, skipped, observation, callFailed, failure, elapsedMs, null, 0, false);
        }

        public Outcome(GoldenSet.Sample sample, String raw, boolean skipped,
                       XmlParser.ParsedObservation observation, boolean callFailed,
                       String failure, long elapsedMs, String status, int runIndex,
                       boolean validationRejected) {
            this.sample = sample;
            this.raw = raw;
            this.skipped = skipped;
            this.observation = observation;
            this.callFailed = callFailed;
            this.failure = failure;
            this.elapsedMs = elapsedMs;
            this.status = status;
            this.runIndex = runIndex;
            this.validationRejected = validationRejected;
        }
    }

    public static final class SampleScore {
        public String id;
        public String category;
        public String raw;
        public int runIndex;
        public boolean expectedSkip;
        public boolean actualSkip;
        public boolean parsed;
        public boolean callFailed;
        public boolean validationRejected;
        public boolean contractPass;
        public boolean titleExactHit;
        public boolean titleIdentifierHit;
        public boolean titleNormalizedHit;
        public double titleCoverage;
        public boolean typeMatch;
        public double factsPrecision;
        public double factsRecall;
        public double factsSupportRate;
        public double conceptsPrecision;
        public double conceptsRecall;
        public long elapsedMs;
        public String status;
        public List<String> problems = new ArrayList<>();
    }

    public static final class RunSummary {
        public int runIndex;
        public int samples;
        public double skipAccuracy;
        public double titleNormalizedHitRate;
        public double factsPrecision;
        public double factsRecall;
        public int formatErrors;
    }

    public static final class TypeSummary {
        public int samples;
        public int contractPass;
        public int typeMatch;
        public int titleNormalizedHit;
    }

    public static final class Aggregate {
        public int total;
        public int callFailures;
        public int formatErrors;
        public int validationRejections;
        public int skipTp;
        public int skipFp;
        public int skipFn;
        public int skipTn;
        public double contractPassRate;
        public double skipAccuracy;
        public double titleExactHitRate;
        public double titleNormalizedHitRate;
        public double titleIdentifierHitRate;
        public double typeAccuracy;
        public double factsPrecision;
        public double factsRecall;
        public double factsSupportRate;
        public double conceptsPrecision;
        public double conceptsRecall;
        public double repeatConsistency;
        public int repeatedSamples;
        public long p50Ms;
        public long p95Ms;
        public long maxMs;
        public Map<String, TypeSummary> perType = new LinkedHashMap<>();
        public Map<String, Integer> statusCounts = new LinkedHashMap<>();
        public List<RunSummary> runs = new ArrayList<>();
        public List<SampleScore> scores = new ArrayList<>();
        public List<String> problems = new ArrayList<>();
    }

    /**
     * Headline aggregate: the first run of every unique sample. Repeat runs are summarised
     * separately in {@link Aggregate#runs} and {@link Aggregate#repeatConsistency}.
     */
    public static Aggregate score(List<Outcome> outcomes) {
        List<Outcome> firstRuns = new ArrayList<>();
        Map<String, List<Outcome>> bySample = new LinkedHashMap<>();
        for (Outcome outcome : outcomes) {
            bySample.computeIfAbsent(outcome.sample.id, key -> new ArrayList<>()).add(outcome);
            if (outcome.runIndex == 0) {
                firstRuns.add(outcome);
            }
        }
        Aggregate agg = scoreRun(firstRuns, 0);
        agg.runs.addAll(runSummaries(bySample));
        agg.repeatConsistency = repeatConsistency(bySample);
        agg.repeatedSamples = (int) bySample.values().stream().filter(list -> list.size() > 1).count();
        return agg;
    }

    private static Aggregate scoreRun(List<Outcome> outcomes, int runIndex) {
        Aggregate agg = new Aggregate();
        agg.total = outcomes.size();

        double titleExactHits = 0;
        double titleNormalizedHits = 0;
        double titleIdentifierHits = 0;
        int titleSamples = 0;
        int typedSamples = 0;
        int typeHits = 0;
        double factsP = 0;
        double factsR = 0;
        double factsSupport = 0;
        double conceptsP = 0;
        double conceptsR = 0;
        int fieldSamples = 0;
        List<Long> latencies = new ArrayList<>();

        for (Outcome outcome : outcomes) {
            GoldenSet.Sample sample = outcome.sample;
            boolean expectedSkip = !sample.expectObservation;
            boolean parsed = outcome.observation != null;

            SampleScore score = new SampleScore();
            score.id = sample.id;
            score.category = sample.category;
            score.raw = outcome.raw;
            score.runIndex = outcome.runIndex;
            score.expectedSkip = expectedSkip;
            score.actualSkip = outcome.skipped;
            score.parsed = parsed;
            score.callFailed = outcome.callFailed;
            score.validationRejected = outcome.validationRejected;
            score.elapsedMs = outcome.elapsedMs;
            score.status = outcome.status;
            latencies.add(outcome.elapsedMs);
            if (outcome.status != null) {
                agg.statusCounts.merge(outcome.status, 1, Integer::sum);
            }

            TypeSummary type = agg.perType.computeIfAbsent(sample.category, key -> new TypeSummary());
            type.samples++;

            if (expectedSkip) {
                if (outcome.skipped) {
                    agg.skipTp++;
                } else {
                    agg.skipFn++;
                }
            } else {
                if (outcome.skipped) {
                    agg.skipFp++;
                } else {
                    agg.skipTn++;
                }
            }

            score.contractPass = expectedSkip ? outcome.skipped : parsed;
            if (score.contractPass) {
                type.contractPass++;
            }
            if (outcome.callFailed) {
                agg.callFailures++;
                score.problems.add("call failed: " + outcome.failure);
            } else if (expectedSkip && !outcome.skipped) {
                score.problems.add(parsed
                        ? "expected <skip> but produced an observation"
                        : "expected <skip> but produced malformed output: " + outcome.failure);
            } else if (!expectedSkip && !parsed && !outcome.skipped) {
                if (outcome.validationRejected) {
                    agg.validationRejections++;
                    score.problems.add("validation rejected the observation: " + outcome.failure);
                } else {
                    agg.formatErrors++;
                    score.problems.add("format error: " + outcome.failure);
                }
            } else if (!expectedSkip && outcome.skipped) {
                score.problems.add("expected an observation but model skipped");
            }

            if (!expectedSkip && parsed) {
                XmlParser.ParsedObservation obs = outcome.observation;
                score.titleCoverage = coverage(sample.titleKeywords, obs.title);
                score.titleExactHit = !sample.titleKeywords.isEmpty()
                        && coverageExact(sample.titleKeywords, obs.title) >= 1.0;
                score.titleIdentifierHit = identifiersPresent(sample.titleIdentifiers, obs.title);
                score.titleNormalizedHit = score.titleIdentifierHit
                        && (sample.titleKeywords.isEmpty() || score.titleCoverage >= TITLE_COVERAGE_FLOOR);
                titleExactHits += score.titleExactHit ? 1 : 0;
                titleNormalizedHits += score.titleNormalizedHit ? 1 : 0;
                titleIdentifierHits += score.titleIdentifierHit ? 1 : 0;
                titleSamples++;
                type.titleNormalizedHit += score.titleNormalizedHit ? 1 : 0;

                score.typeMatch = sample.expectedType != null
                        && sample.expectedType.equalsIgnoreCase(obs.type);
                type.typeMatch += score.typeMatch ? 1 : 0;
                typeHits += score.typeMatch ? 1 : 0;
                typedSamples++;

                score.factsRecall = recall(sample.factsKeywords, obs.facts);
                score.factsPrecision = precision(sample.factsKeywords, obs.facts);
                score.factsSupportRate = supportRate(obs.facts, sample);
                score.conceptsRecall = recall(sample.conceptsKeywords, obs.concepts);
                score.conceptsPrecision = precision(sample.conceptsKeywords, obs.concepts);

                factsP += score.factsPrecision;
                factsR += score.factsRecall;
                factsSupport += score.factsSupportRate;
                conceptsP += score.conceptsPrecision;
                conceptsR += score.conceptsRecall;
                fieldSamples++;

                if (!score.titleIdentifierHit) {
                    score.problems.add("title lost identifiers " + sample.titleIdentifiers
                            + " (got '" + obs.title + "')");
                } else if (!score.titleNormalizedHit) {
                    score.problems.add("title coverage " + pct(score.titleCoverage)
                            + " (expected keywords " + sample.titleKeywords + ")");
                }
                if (!score.typeMatch) {
                    score.problems.add("type '" + obs.type + "' != expected '"
                            + sample.expectedType + "'");
                }
                if (score.factsRecall < 1.0 && !sample.factsKeywords.isEmpty()) {
                    score.problems.add("facts recall " + pct(score.factsRecall)
                            + " (expected " + sample.factsKeywords + ", got " + obs.facts + ")");
                }
                if (score.conceptsRecall < 1.0 && !sample.conceptsKeywords.isEmpty()) {
                    score.problems.add("concepts recall " + pct(score.conceptsRecall)
                            + " (expected " + sample.conceptsKeywords + ", got " + obs.concepts + ")");
                }
            }

            agg.scores.add(score);
            if (!score.problems.isEmpty()) {
                for (String problem : score.problems) {
                    agg.problems.add(sample.id + ": " + problem);
                }
            }
        }

        agg.contractPassRate = ratio(agg.scores.stream().filter(s -> s.contractPass).count(), agg.total);
        agg.skipAccuracy = ratio(agg.skipTp + agg.skipTn, agg.total);
        agg.titleExactHitRate = ratio(titleExactHits, titleSamples);
        agg.titleNormalizedHitRate = ratio(titleNormalizedHits, titleSamples);
        agg.titleIdentifierHitRate = ratio(titleIdentifierHits, titleSamples);
        agg.typeAccuracy = ratio(typeHits, typedSamples);
        agg.factsPrecision = fieldSamples == 0 ? 0 : factsP / fieldSamples;
        agg.factsRecall = fieldSamples == 0 ? 0 : factsR / fieldSamples;
        agg.factsSupportRate = fieldSamples == 0 ? 0 : factsSupport / fieldSamples;
        agg.conceptsPrecision = fieldSamples == 0 ? 0 : conceptsP / fieldSamples;
        agg.conceptsRecall = fieldSamples == 0 ? 0 : conceptsR / fieldSamples;
        agg.p50Ms = percentile(latencies, 0.50);
        agg.p95Ms = percentile(latencies, 0.95);
        agg.maxMs = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        return agg;
    }

    private static List<RunSummary> runSummaries(Map<String, List<Outcome>> bySample) {
        Map<Integer, List<Outcome>> byRun = new LinkedHashMap<>();
        for (List<Outcome> outcomes : bySample.values()) {
            for (Outcome outcome : outcomes) {
                byRun.computeIfAbsent(outcome.runIndex, key -> new ArrayList<>()).add(outcome);
            }
        }
        List<RunSummary> summaries = new ArrayList<>();
        for (Map.Entry<Integer, List<Outcome>> entry : byRun.entrySet()) {
            Aggregate run = scoreRun(entry.getValue(), entry.getKey());
            RunSummary summary = new RunSummary();
            summary.runIndex = entry.getKey();
            summary.samples = run.total;
            summary.skipAccuracy = run.skipAccuracy;
            summary.titleNormalizedHitRate = run.titleNormalizedHitRate;
            summary.factsPrecision = run.factsPrecision;
            summary.factsRecall = run.factsRecall;
            summary.formatErrors = run.formatErrors;
            summaries.add(summary);
        }
        return summaries;
    }

    /** Share of repeated samples whose runs all agreed on skip-vs-observation. */
    private static double repeatConsistency(Map<String, List<Outcome>> bySample) {
        int repeated = 0;
        int consistent = 0;
        for (List<Outcome> outcomes : bySample.values()) {
            if (outcomes.size() < 2) {
                continue;
            }
            repeated++;
            boolean first = outcomes.get(0).skipped;
            boolean same = outcomes.stream().allMatch(outcome -> outcome.skipped == first);
            if (same) {
                consistent++;
            }
        }
        return ratio(consistent, repeated);
    }

    /** Fraction of expected keywords present after case/punctuation/CamelCase normalisation. */
    public static double coverage(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        String haystack = canonicalize(actual);
        int hits = 0;
        for (String keyword : expected) {
            if (containsKeyword(haystack, keyword)) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    /** Legacy metric: exact case-insensitive substring coverage, without normalization. */
    public static double coverageExact(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        String haystack = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String keyword : expected) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    public static boolean identifiersPresent(List<String> identifiers, String actual) {
        if (identifiers == null || identifiers.isEmpty()) {
            return true;
        }
        String haystack = actual == null ? "" : actual;
        for (String identifier : identifiers) {
            if (!haystack.contains(identifier)) {
                return false;
            }
        }
        return true;
    }

    /** Expected-keyword recall over any actual item. */
    public static double recall(List<String> expected, List<String> actualItems) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        String haystack = canonicalize(String.join(" ", actualItems == null ? List.of() : actualItems));
        int hits = 0;
        for (String keyword : expected) {
            if (containsKeyword(haystack, keyword)) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    /** Fraction of actual items that mention at least one expected keyword. */
    public static double precision(List<String> expected, List<String> actualItems) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        if (actualItems == null || actualItems.isEmpty()) {
            return 0.0;
        }
        int useful = 0;
        for (String item : actualItems) {
            String haystack = canonicalize(item);
            for (String keyword : expected) {
                if (containsKeyword(haystack, keyword)) {
                    useful++;
                    break;
                }
            }
        }
        return (double) useful / actualItems.size();
    }

    /** Fraction of produced facts that the deterministic guard would keep as trace-grounded. */
    public static double supportRate(List<String> facts, GoldenSet.Sample sample) {
        if (facts == null || facts.isEmpty()) {
            return 1.0;
        }
        String evidence = (sample.toolName == null ? "" : sample.toolName) + "\n"
                + (sample.toolInput == null ? "" : sample.toolInput) + "\n"
                + (sample.toolOutput == null ? "" : sample.toolOutput);
        int supported = 0;
        for (String fact : facts) {
            if (ExtractionValidationService.isSupported(fact, evidence)) {
                supported++;
            }
        }
        return (double) supported / facts.size();
    }

    /**
     * Lower-case, split CamelCase, strip punctuation, fold synonyms, collapse whitespace.
     * {@code "PaymentService"} and {@code "payment service"} meet here; {@code "payment"} alone
     * does not become {@code "payment service"}.
     */
    static String canonicalize(String value) {
        if (value == null) {
            return "";
        }
        // "10%" and "10 percent" describe the same change; normalize the symbol away so a
        // percentage keyword is not lost purely because the model wrote the other form.
        String spelled = value.replaceAll("(\\d+(?:\\.\\d+)?)\\s*%", "$1 percent");
        String spaced = spelled.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        List<String> tokens = new ArrayList<>();
        for (String raw : spaced.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            tokens.add(SYNONYMS.getOrDefault(raw, raw));
        }
        return String.join(" ", tokens);
    }

    /**
     * Keyword containment after canonicalization. Token boundaries are preferred; a compact
     * fallback (no spaces, at least 5 chars) lets {@code orderquery} meet {@code OrderQuery}
     * without letting {@code add} match {@code address}.
     */
    private static boolean containsKeyword(String canonicalHaystack, String keyword) {
        String canonicalKeyword = canonicalize(keyword);
        if (canonicalKeyword.isEmpty()) {
            return false;
        }
        if ((" " + canonicalHaystack + " ").contains(" " + canonicalKeyword + " ")) {
            return true;
        }
        String compactHaystack = canonicalHaystack.replace(" ", "");
        String compactKeyword = canonicalKeyword.replace(" ", "");
        return compactKeyword.length() >= 5 && compactHaystack.contains(compactKeyword);
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
