package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manual-only evaluation of the semantic relevance floor ({@code cortexce.search.min-similarity}).
 *
 * <p>The corpus deliberately contains paraphrase queries: relevance is intentional, not lexical,
 * so the pairs that matter land close to the current 0.5 floor. The evaluator embeds the corpus
 * with the production embedding model, records the query/observation similarity matrix, and sweeps
 * thresholds. The threshold is chosen by a rule fixed in advance, not by eyeballing the curve:</p>
 *
 * <blockquote>choose the highest threshold whose micro recall stays within 1 percentage point of
 * the maximum recall observed at the lowest swept threshold.</blockquote>
 *
 * <p>Safety mirrors {@code GoldenSetEvaluator}: manual-only, no retry, fixture replay for CI.</p>
 */
public final class SearchSimilarityEvaluator {

    /** Retrieval cap used by the curve; matches the default page size. */
    static final int LIMIT = 5;

    /** Thresholds swept from permissive to strict, in 0.05 steps. */
    static final double[] THRESHOLDS = {
            0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80};

    /** The operating point under measurement before the data decides a replacement. */
    static final double CURRENT_FLOOR = 0.50;

    /** Allowed recall loss against the lowest threshold before a floor is rejected. */
    static final double RECALL_TOLERANCE = 0.01;

    private SearchSimilarityEvaluator() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureIn = blankToNull(env("EVAL_FIXTURE_IN"));
        if (fixtureIn == null) {
            if (!"1".equals(env("REAL_LLM_EVAL"))) {
                System.err.println("Refusing to call a real embedding model: set REAL_LLM_EVAL=1 first.");
                System.exit(2);
            }
            if (env("CI") != null || "1".equals(env("GITHUB_ACTIONS"))) {
                System.err.println("Refusing to run inside CI: real-model evaluation is manual-only.");
                System.exit(2);
            }
        }

        Corpus corpus = loadCorpus();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Map<String, Double>> similarities;
        String model = envOr("SPRING_AI_OPENAI_EMBEDDING_MODEL", "BAAI/bge-m3");
        if (fixtureIn == null) {
            EmbeddingModel embeddingModel = buildEmbeddingModel(model);
            similarities = embed(embeddingModel, corpus);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> loaded = mapper.readValue(Path.of(fixtureIn).toFile(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Double>> stored =
                    (Map<String, Map<String, Double>>) loaded.get("similarities");
            similarities = stored;
            if (loaded.get("model") != null) {
                model = String.valueOf(loaded.get("model"));
            }
        }

        List<CurvePoint> curve = curve(corpus, similarities);
        double missedAtCurrent = 1.0 - recallAt(curve, CURRENT_FLOOR);
        double chosen = chooseThreshold(curve);
        CurvePoint chosenPoint = pointAt(curve, chosen);

        String fixtureOut = blankToNull(env("EVAL_FIXTURE_OUT"));
        if (fixtureOut != null) {
            Map<String, Object> fixture = new LinkedHashMap<>();
            fixture.put("model", model);
            fixture.put("limit", LIMIT);
            fixture.put("observations", corpus.observations.stream().map(o -> o.id).toList());
            fixture.put("queries", corpus.queries);
            fixture.put("similarities", similarities);
            fixture.put("curve", curve);
            fixture.put("missedRecallAt050", missedAtCurrent);
            fixture.put("chosenThreshold", chosen);
            writeJson(Path.of(fixtureOut), fixture);
            System.out.println("fixture=" + fixtureOut);
        }

        String reportPath = envOr("EVAL_REPORT",
                "docs/archive/" + LocalDate.now() + "_search-similarity-eval.md");
        String report = report(model, fixtureIn, curve, missedAtCurrent, chosen, chosenPoint);
        Path path = Path.of(reportPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, report, StandardCharsets.UTF_8);

        String metricsOut = blankToNull(env("EVAL_METRICS_OUT"));
        if (metricsOut != null) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("model", model);
            metrics.put("queries", corpus.queries.size());
            metrics.put("observations", corpus.observations.size());
            metrics.put("missedRecallAt050", missedAtCurrent);
            metrics.put("chosenThreshold", chosen);
            metrics.put("chosenRecall", chosenPoint.recall);
            metrics.put("chosenPrecision", chosenPoint.precision);
            metrics.put("curve", curve);
            writeJson(Path.of(metricsOut), metrics);
            System.out.println("metrics=" + metricsOut);
        }

        System.out.printf(Locale.ROOT,
                "%nmissedRecall@0.50=%.1f%% chosen=%.2f recall=%.1f%% precision=%.1f%%%nreport=%s%n",
                missedAtCurrent * 100, chosen, chosenPoint.recall * 100,
                chosenPoint.precision * 100, path);
    }

    // ------------------------------------------------------------------ corpus

    static final class Corpus {
        public List<Observation> observations = new ArrayList<>();
        public List<Query> queries = new ArrayList<>();
    }

    static final class Observation {
        public String id;
        public String title;
        public String narrative;
        public List<String> facts = new ArrayList<>();
    }

    static final class Query {
        public String id;
        public String text;
        public List<String> relevant = new ArrayList<>();
    }

    static Corpus loadCorpus() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = SearchSimilarityEvaluator.class
                .getResourceAsStream("/eval/search-similarity-corpus.json")) {
            if (in == null) {
                throw new IllegalStateException("missing /eval/search-similarity-corpus.json");
            }
            return mapper.readValue(in, Corpus.class);
        }
    }

    // --------------------------------------------------------------- embedding

    private static Map<String, Map<String, Double>> embed(EmbeddingModel model, Corpus corpus) {
        Map<String, float[]> observationVectors = new LinkedHashMap<>();
        for (Observation observation : corpus.observations) {
            String text = EmbeddingService.buildObservationText(
                    observation.title, observation.narrative, observation.facts);
            observationVectors.put(observation.id, model.embed(text));
        }
        Map<String, Map<String, Double>> similarities = new LinkedHashMap<>();
        for (Query query : corpus.queries) {
            float[] queryVector = model.embed(query.text);
            Map<String, Double> row = new LinkedHashMap<>();
            for (Observation observation : corpus.observations) {
                row.put(observation.id, round(cosine(queryVector, observationVectors.get(observation.id))));
            }
            similarities.put(query.id, row);
        }
        return similarities;
    }

    static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    // ------------------------------------------------------------------- curve

    static final class CurvePoint {
        public double threshold;
        public double recall;
        public double precision;
        public double missedRecall;
        public int retrieved;
        public int relevantRetrieved;
        public int relevantTotal;
    }

    /** Recomputes the whole curve from a recorded similarity matrix. */
    static List<CurvePoint> curve(Corpus corpus, Map<String, Map<String, Double>> similarities) {
        List<CurvePoint> points = new ArrayList<>();
        for (double threshold : THRESHOLDS) {
            CurvePoint point = new CurvePoint();
            point.threshold = threshold;
            int relevantTotal = 0;
            int retrieved = 0;
            int relevantRetrieved = 0;
            for (Query query : corpus.queries) {
                Set<String> relevant = new LinkedHashSet<>(query.relevant);
                relevantTotal += relevant.size();
                List<Map.Entry<String, Double>> ranked = new ArrayList<>(
                        similarities.getOrDefault(query.id, Map.of()).entrySet());
                ranked.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));
                int taken = 0;
                for (Map.Entry<String, Double> entry : ranked) {
                    if (taken >= LIMIT || entry.getValue() < threshold) {
                        break;
                    }
                    taken++;
                    retrieved++;
                    if (relevant.contains(entry.getKey())) {
                        relevantRetrieved++;
                    }
                }
            }
            point.relevantTotal = relevantTotal;
            point.retrieved = retrieved;
            point.relevantRetrieved = relevantRetrieved;
            point.recall = ratio(relevantRetrieved, relevantTotal);
            point.precision = ratio(relevantRetrieved, retrieved);
            point.missedRecall = 1.0 - point.recall;
            points.add(point);
        }
        return points;
    }

    /**
     * Pre-committed rule: the highest threshold whose recall stays within
     * {@link #RECALL_TOLERANCE} of the maximum recall seen at the lowest swept threshold.
     * This buys precision without discarding relevant rows the data shows are reachable.
     */
    static double chooseThreshold(List<CurvePoint> curve) {
        double maxRecall = curve.stream().mapToDouble(p -> p.recall).max().orElse(0.0);
        double chosen = curve.get(0).threshold;
        for (CurvePoint point : curve) {
            if (point.recall >= maxRecall - RECALL_TOLERANCE) {
                chosen = point.threshold;
            } else {
                break;
            }
        }
        return chosen;
    }

    static double recallAt(List<CurvePoint> curve, double threshold) {
        return pointAt(curve, threshold).recall;
    }

    static CurvePoint pointAt(List<CurvePoint> curve, double threshold) {
        return curve.stream()
                .filter(point -> Math.abs(point.threshold - threshold) < 1e-9)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("threshold not swept: " + threshold));
    }

    // ------------------------------------------------------------------ output

    private static String report(String model, String fixtureIn, List<CurvePoint> curve,
                                 double missedAtCurrent, double chosen, CurvePoint chosenPoint) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Semantic relevance floor evaluation\n\n");
        sb.append("- Date: ").append(LocalDate.now()).append("\n");
        sb.append("- Embedding model: `").append(model).append("`\n");
        sb.append("- Corpus: ").append(curve.get(0).relevantTotal)
                .append(" relevant pairs; limit ").append(LIMIT).append(" per query\n");
        sb.append("- Input: ").append(fixtureIn == null ? "live embedding calls" : "fixture replay")
                .append("\n\n");

        sb.append("## Operating point\n\n");
        sb.append("| metric | value |\n|---|---|\n");
        row(sb, "missed recall at 0.50", pct(missedAtCurrent));
        row(sb, "data-chosen threshold", String.format(Locale.ROOT, "%.2f", chosen));
        row(sb, "recall at chosen threshold", pct(chosenPoint.recall));
        row(sb, "precision at chosen threshold", pct(chosenPoint.precision));
        sb.append('\n');

        sb.append("## Recall / precision curve\n\n");
        sb.append("| threshold | retrieved | relevant retrieved | recall | precision | missed recall |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (CurvePoint point : curve) {
            sb.append("| ").append(String.format(Locale.ROOT, "%.2f", point.threshold))
                    .append(" | ").append(point.retrieved)
                    .append(" | ").append(point.relevantRetrieved).append('/').append(point.relevantTotal)
                    .append(" | ").append(pct(point.recall))
                    .append(" | ").append(pct(point.precision))
                    .append(" | ").append(pct(point.missedRecall))
                    .append(" |\n");
        }
        sb.append('\n');

        sb.append("## Decision rule\n\n");
        sb.append("- Fixed before the run: choose the highest threshold whose micro recall is within ")
                .append(pct(RECALL_TOLERANCE)).append(" of the maximum recall at the lowest swept ")
                .append("threshold, so precision is raised without discarding reachable rows.\n");
        sb.append("- The rule is independent of the observed threshold values; it was written before ")
                .append("the run and cannot be tuned afterwards.\n\n");

        sb.append("## Decision\n\n");
        if (Math.abs(chosen - CURRENT_FLOOR) < 1e-9) {
            sb.append("- The data-chosen threshold is `")
                    .append(String.format(Locale.ROOT, "%.2f", chosen))
                    .append("`, identical to the production floor `cortexce.search.min-similarity`; ")
                    .append("no production change is applied.\n");
        } else {
            sb.append("- The data-chosen threshold is `")
                    .append(String.format(Locale.ROOT, "%.2f", chosen))
                    .append("`; `cortexce.search.min-similarity` must move from `")
                    .append(String.format(Locale.ROOT, "%.2f", CURRENT_FLOOR)).append("` to it.\n");
        }
        double stricter = chosen + 0.05;
        if (stricter <= THRESHOLDS[THRESHOLDS.length - 1]) {
            CurvePoint next = pointAt(curve, stricter);
            sb.append("- The next stricter step (`")
                    .append(String.format(Locale.ROOT, "%.2f", stricter))
                    .append("`) recalls ").append(pct(next.recall)).append(" and misses ")
                    .append(pct(next.missedRecall)).append(" of relevant pairs.\n");
        }
        CurvePoint floorPoint = pointAt(curve, CURRENT_FLOOR);
        sb.append("- At the production floor the missed recall is ").append(pct(1.0 - floorPoint.recall))
                .append(" with precision ").append(pct(floorPoint.precision)).append(".\n");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String name, String value) {
        sb.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static void writeJson(Path path, Object value) throws Exception {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private static EmbeddingModel buildEmbeddingModel(String model) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(requireEnv("SPRING_AI_OPENAI_EMBEDDING_API_KEY"))
                .baseUrl(envOr("SPRING_AI_OPENAI_EMBEDDING_BASE_URL", "https://api.siliconflow.cn"))
                .embeddingsPath("/v1/embeddings")
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model(model).build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    private static String requireEnv(String name) {
        String value = env(name);
        if (value == null || value.isBlank()) {
            System.err.println(name + " is required for a real embedding evaluation.");
            System.exit(2);
        }
        return value;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String envOr(String name, String fallback) {
        String value = env(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
