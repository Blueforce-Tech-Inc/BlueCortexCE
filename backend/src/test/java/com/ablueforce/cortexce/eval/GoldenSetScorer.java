package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure scoring logic for the golden-set evaluation.
 * <p>
 * No network, no Spring context: the evaluator feeds real model outcomes in,
 * and the offline unit test feeds synthetic ones. Keeping this separate is what
 * makes the scoring repeatable without spending provider calls.
 */
public final class GoldenSetScorer {

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

        public Outcome(GoldenSet.Sample sample, String raw, boolean skipped,
                       XmlParser.ParsedObservation observation, boolean callFailed,
                       String failure, long elapsedMs) {
            this.sample = sample;
            this.raw = raw;
            this.skipped = skipped;
            this.observation = observation;
            this.callFailed = callFailed;
            this.failure = failure;
            this.elapsedMs = elapsedMs;
        }
    }

    public static final class SampleScore {
        public String id;
        public String category;
        public String raw;
        public boolean expectedSkip;
        public boolean actualSkip;
        public boolean parsed;
        public boolean callFailed;
        public boolean contractPass;
        public boolean titleFullHit;
        public double titleCoverage;
        public boolean typeMatch;
        public double factsPrecision;
        public double factsRecall;
        public double conceptsPrecision;
        public double conceptsRecall;
        public long elapsedMs;
        public List<String> problems = new ArrayList<>();
    }

    public static final class Aggregate {
        public int total;
        public int callFailures;
        public int formatErrors;
        public int skipTp;
        public int skipFp;
        public int skipFn;
        public int skipTn;
        public double contractPassRate;
        public double skipAccuracy;
        public double titleFullHitRate;
        public double typeAccuracy;
        public double factsPrecision;
        public double factsRecall;
        public double conceptsPrecision;
        public double conceptsRecall;
        public List<SampleScore> scores = new ArrayList<>();
        public List<String> problems = new ArrayList<>();
    }

    public static Aggregate score(List<Outcome> outcomes) {
        Aggregate agg = new Aggregate();
        agg.total = outcomes.size();

        double titleHits = 0;
        int titleSamples = 0;
        int typedSamples = 0;
        int typeHits = 0;
        double factsP = 0;
        double factsR = 0;
        double conceptsP = 0;
        double conceptsR = 0;
        int fieldSamples = 0;

        for (Outcome outcome : outcomes) {
            GoldenSet.Sample sample = outcome.sample;
            boolean expectedSkip = !sample.expectObservation;
            boolean parsed = outcome.observation != null;

            SampleScore score = new SampleScore();
            score.id = sample.id;
            score.category = sample.category;
            score.raw = outcome.raw;
            score.expectedSkip = expectedSkip;
            score.actualSkip = outcome.skipped;
            score.parsed = parsed;
            score.callFailed = outcome.callFailed;
            score.elapsedMs = outcome.elapsedMs;

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
            if (outcome.callFailed) {
                agg.callFailures++;
                score.problems.add("call failed: " + outcome.failure);
            } else if (expectedSkip && !outcome.skipped) {
                score.problems.add(parsed
                        ? "expected <skip> but produced an observation"
                        : "expected <skip> but produced malformed output: " + outcome.failure);
            } else if (!expectedSkip && !parsed && !outcome.skipped) {
                agg.formatErrors++;
                score.problems.add("format error: " + outcome.failure);
            } else if (!expectedSkip && outcome.skipped) {
                score.problems.add("expected an observation but model skipped");
            }

            if (!expectedSkip && parsed) {
                XmlParser.ParsedObservation obs = outcome.observation;
                score.titleCoverage = coverage(sample.titleKeywords, obs.title);
                score.titleFullHit = !sample.titleKeywords.isEmpty()
                        && score.titleCoverage >= 1.0;
                titleHits += score.titleFullHit ? 1 : 0;
                titleSamples++;

                score.typeMatch = sample.expectedType != null
                        && sample.expectedType.equalsIgnoreCase(obs.type);
                typeHits += score.typeMatch ? 1 : 0;
                typedSamples++;

                score.factsRecall = recall(sample.factsKeywords, obs.facts);
                score.factsPrecision = precision(sample.factsKeywords, obs.facts);
                score.conceptsRecall = recall(sample.conceptsKeywords, obs.concepts);
                score.conceptsPrecision = precision(sample.conceptsKeywords, obs.concepts);

                factsP += score.factsPrecision;
                factsR += score.factsRecall;
                conceptsP += score.conceptsPrecision;
                conceptsR += score.conceptsRecall;
                fieldSamples++;

                if (!score.titleFullHit) {
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
        agg.titleFullHitRate = ratio(titleHits, titleSamples);
        agg.typeAccuracy = ratio(typeHits, typedSamples);
        agg.factsPrecision = fieldSamples == 0 ? 0 : factsP / fieldSamples;
        agg.factsRecall = fieldSamples == 0 ? 0 : factsR / fieldSamples;
        agg.conceptsPrecision = fieldSamples == 0 ? 0 : conceptsP / fieldSamples;
        agg.conceptsRecall = fieldSamples == 0 ? 0 : conceptsR / fieldSamples;
        return agg;
    }

    /** Fraction of expected keywords present (case-insensitive substring match). */
    public static double coverage(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        String haystack = normalize(actual);
        int hits = 0;
        for (String keyword : expected) {
            if (haystack.contains(normalize(keyword))) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    /** Expected-keyword recall over any actual item. */
    public static double recall(List<String> expected, List<String> actualItems) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        String haystack = normalize(String.join(" ", actualItems == null ? List.of() : actualItems));
        int hits = 0;
        for (String keyword : expected) {
            if (haystack.contains(normalize(keyword))) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    /** Fraction of actual items that mention at least one expected keyword. */
    public static double precision(List<String> expected, List<String> actualItems) {
        if (actualItems == null || actualItems.isEmpty()) {
            return expected == null || expected.isEmpty() ? 1.0 : 0.0;
        }
        int useful = 0;
        for (String item : actualItems) {
            String haystack = normalize(item);
            for (String keyword : expected) {
                if (haystack.contains(normalize(keyword))) {
                    useful++;
                    break;
                }
            }
        }
        return (double) useful / actualItems.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }
}
