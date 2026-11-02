package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manual-only golden-set evaluation against a real OpenAI-compatible provider.
 * <p>
 * This class is a {@code main} entry point, not a JUnit test, so Surefire never
 * picks it up and CI never makes real provider calls. It refuses to run when
 * {@code CI} is set, requires {@code REAL_LLM_EVAL=1}, and hard-caps the number
 * of model calls through {@code EVAL_MAX_CALLS} (default 20).
 * <p>
 * Run it through {@code scripts/eval-model-quality.sh}; the wrapper loads
 * {@code backend/.env} and builds the test classpath.
 */
public final class GoldenSetEvaluator {

    private GoldenSetEvaluator() {
    }

    public static void main(String[] args) throws Exception {
        if (env("REAL_LLM_EVAL") == null || !"1".equals(env("REAL_LLM_EVAL"))) {
            System.err.println("Refusing to call a real model: set REAL_LLM_EVAL=1 first.");
            System.exit(2);
        }
        if (env("CI") != null || "1".equals(env("GITHUB_ACTIONS"))) {
            System.err.println("Refusing to run inside CI: real-model evaluation is manual-only.");
            System.exit(2);
        }

        String apiKey = requireEnv("OPENAI_API_KEY");
        String baseUrl = envOr("OPENAI_BASE_URL", "https://api.deepseek.com");
        String modelName = envOr("OPENAI_MODEL", "deepseek-chat");
        int maxCalls = Integer.parseInt(envOr("EVAL_MAX_CALLS", "20"));
        int limit = Integer.parseInt(envOr("EVAL_LIMIT", String.valueOf(Integer.MAX_VALUE)));
        double temperature = Double.parseDouble(envOr("EVAL_TEMPERATURE", "0.0"));

        GoldenSet golden = loadGoldenSet();
        String systemPrompt = resource("prompts/init.txt");
        String observationTemplate = resource("prompts/observation.txt");

        int planned = Math.min(Math.min(golden.samples.size(), limit), maxCalls);
        if (planned < 1) {
            System.err.println("No samples selected; nothing to evaluate.");
            System.exit(2);
        }

        ChatModel model = buildModel(apiKey, baseUrl, modelName, temperature);
        System.out.printf(Locale.ROOT,
                "Evaluating %d sample(s) with %s at %s (cap=%d, temperature=%.1f)%n",
                planned, modelName, baseUrl, maxCalls, temperature);

        List<GoldenSetScorer.Outcome> outcomes = new ArrayList<>();
        int calls = 0;
        int infraFailures = 0;
        for (int i = 0; i < planned; i++) {
            GoldenSet.Sample sample = golden.samples.get(i);
            String userPrompt = fillTemplate(observationTemplate, sample);
            long started = System.nanoTime();
            calls++;
            try {
                ChatResponse response = model.call(new Prompt(
                        List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
                String raw = response.getResult().getOutput().getText();
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                outcomes.add(parse(sample, raw, elapsedMs));
                System.out.printf(Locale.ROOT, "  [%d/%d] %-28s %5d ms%n", i + 1, planned, sample.id, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                infraFailures++;
                outcomes.add(new GoldenSetScorer.Outcome(sample, null, false, null, true,
                        e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs));
                System.out.printf(Locale.ROOT, "  [%d/%d] %-28s CALL FAILED: %s%n",
                        i + 1, planned, sample.id, e.getMessage());
            }
        }

        GoldenSetScorer.Aggregate aggregate = GoldenSetScorer.score(outcomes);
        String reportPath = envOr("EVAL_REPORT",
                "docs/archive/" + LocalDate.now() + "_model-output-quality-eval.md");
        String report = renderReport(modelName, baseUrl, temperature, calls, maxCalls, aggregate);
        Path path = Path.of(reportPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, report, StandardCharsets.UTF_8);

        printSummary(aggregate, calls, maxCalls, path);
        if (infraFailures > 0) {
            System.err.println("Evaluation could not complete: " + infraFailures + " call(s) failed.");
            System.exit(1);
        }
    }

    private static GoldenSetScorer.Outcome parse(GoldenSet.Sample sample, String raw, long elapsedMs) {
        String skip = XmlParser.extractTag(raw, "skip");
        if (skip != null) {
            return new GoldenSetScorer.Outcome(sample, raw, true, null, false, null, elapsedMs);
        }
        try {
            XmlParser.ParsedObservation observation = XmlParser.parseObservation(raw);
            return new GoldenSetScorer.Outcome(sample, raw, false, observation, false, null, elapsedMs);
        } catch (Exception e) {
            return new GoldenSetScorer.Outcome(sample, raw, false, null, false,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs);
        }
    }

    private static ChatModel buildModel(String apiKey, String baseUrl, String modelName, double temperature) {
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private static String fillTemplate(String template, GoldenSet.Sample sample) {
        return template
                .replace("{{toolName}}", sample.toolName == null ? "" : sample.toolName)
                .replace("{{occurredAt}}", sample.occurredAt == null ? "" : sample.occurredAt)
                .replace("{{cwd}}", sample.cwd == null ? "" : sample.cwd)
                .replace("{{toolInput}}", truncate(sample.toolInput, 4000))
                .replace("{{toolOutput}}", truncate(sample.toolOutput, 4000));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "... [truncated]";
    }

    private static GoldenSet loadGoldenSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = GoldenSetEvaluator.class.getResourceAsStream("/eval/golden-tool-use.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource /eval/golden-tool-use.json");
            }
            return mapper.readValue(in, GoldenSet.class);
        }
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = GoldenSetEvaluator.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String renderReport(String modelName, String baseUrl, double temperature,
                                       int calls, int maxCalls, GoldenSetScorer.Aggregate agg) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Model output quality evaluation\n\n");
        sb.append("- Date: ").append(LocalDate.now()).append('\n');
        sb.append("- Provider: OpenAI-compatible, model `").append(modelName).append("`, base URL `")
                .append(baseUrl).append("`\n");
        sb.append("- Samples: ").append(agg.total).append("; real calls used: ").append(calls)
                .append(" (cap ").append(maxCalls).append("); temperature: ").append(temperature).append('\n');
        sb.append("- Parser: production `XmlParser` on the raw answer; skip detection via `<skip>`.\n\n");

        sb.append("## Aggregate\n\n");
        sb.append("| metric | value |\n|---|---|\n");
        row(sb, "contract pass rate (observation parsed or skip when expected)", pct(agg.contractPassRate));
        row(sb, "skip accuracy", pct(agg.skipAccuracy));
        row(sb, "  skip confusion (TP/FP/FN/TN)",
                agg.skipTp + "/" + agg.skipFp + "/" + agg.skipFn + "/" + agg.skipTn);
        row(sb, "format errors", String.valueOf(agg.formatErrors));
        row(sb, "call failures", String.valueOf(agg.callFailures));
        row(sb, "title full-hit rate", pct(agg.titleFullHitRate));
        row(sb, "type accuracy", pct(agg.typeAccuracy));
        row(sb, "facts precision / recall", pct(agg.factsPrecision) + " / " + pct(agg.factsRecall));
        row(sb, "concepts precision / recall", pct(agg.conceptsPrecision) + " / " + pct(agg.conceptsRecall));
        sb.append('\n');

        sb.append("## Per sample\n\n");
        sb.append("| id | category | expected | actual | contract | title | type | facts P/R | concepts P/R | ms |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (GoldenSetScorer.SampleScore score : agg.scores) {
            sb.append("| ").append(score.id)
                    .append(" | ").append(score.category)
                    .append(" | ").append(score.expectedSkip ? "skip" : "observation")
                    .append(" | ").append(score.actualSkip ? "skip" : (score.parsed ? "observation" : (score.callFailed ? "call-failed" : "malformed")))
                    .append(" | ").append(score.contractPass ? "pass" : "FAIL")
                    .append(" | ").append(score.parsed ? pct(score.titleCoverage) : "-")
                    .append(" | ").append(score.parsed ? (score.typeMatch ? "match" : "MISS") : "-")
                    .append(" | ").append(score.parsed ? pct(score.factsPrecision) + "/" + pct(score.factsRecall) : "-")
                    .append(" | ").append(score.parsed ? pct(score.conceptsPrecision) + "/" + pct(score.conceptsRecall) : "-")
                    .append(" | ").append(score.elapsedMs).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Problems\n\n");
        if (agg.problems.isEmpty()) {
            sb.append("None: every sample met its contract and field expectations.\n\n");
        } else {
            for (String problem : agg.problems) {
                sb.append("- ").append(problem).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Raw answers behind problems\n\n");
        boolean any = false;
        for (GoldenSetScorer.SampleScore score : agg.scores) {
            if (score.problems.isEmpty()) {
                continue;
            }
            any = true;
            sb.append("### ").append(score.id).append("\n\n```text\n")
                    .append(excerpt(score.raw))
                    .append("\n```\n\n");
        }
        if (!any) {
            sb.append("None.\n\n");
        }

        sb.append("## Method and limitations\n\n");
        sb.append("- The prompt is the production `prompts/init.txt` system message plus `prompts/observation.txt`")
                .append(" filled with each sample's tool name, cwd, input and output.\n");
        sb.append("- Each sample costs exactly one chat call; embeddings are not exercised, so this measures")
                .append(" extraction quality only, not retrieval quality.\n");
        sb.append("- `facts`/`concepts` precision counts actual items that mention any expected keyword;")
                .append(" recall counts expected keywords found in the concatenated list. Keyword matching is")
                .append(" case-insensitive substring matching, so paraphrase misses count as errors.\n");
        sb.append("- The golden set is small (12 samples) and authored by hand; numbers describe this set,")
                .append(" not the full distribution of real tool-use traffic.\n");
        sb.append("- Real calls are manual-only: `scripts/eval-model-quality.sh` refuses to run under CI")
                .append(" and enforces the `EVAL_MAX_CALLS` cap.\n");
        return sb.toString();
    }

    private static String excerpt(String raw) {
        if (raw == null) {
            return "(no raw answer: the call failed before returning)";
        }
        String collapsed = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return collapsed.length() <= 400 ? collapsed : collapsed.substring(0, 400) + "... [truncated]";
    }

    private static void printSummary(GoldenSetScorer.Aggregate agg, int calls, int maxCalls, Path path) {
        System.out.printf(Locale.ROOT, "%ncontract=%s skipAccuracy=%s formatErrors=%d titleFull=%s type=%s factsP/R=%s/%s conceptsP/R=%s/%s calls=%d/%d%nreport=%s%n",
                pct(agg.contractPassRate), pct(agg.skipAccuracy), agg.formatErrors,
                pct(agg.titleFullHitRate), pct(agg.typeAccuracy),
                pct(agg.factsPrecision), pct(agg.factsRecall),
                pct(agg.conceptsPrecision), pct(agg.conceptsRecall),
                calls, maxCalls, path);
        if (!agg.problems.isEmpty()) {
            System.out.println("problems:");
            agg.problems.forEach(p -> System.out.println("  - " + p));
        }
    }

    private static void row(StringBuilder sb, String key, String value) {
        sb.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }
}
