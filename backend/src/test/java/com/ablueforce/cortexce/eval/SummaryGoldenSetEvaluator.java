package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manual-only evaluation of the session-summary prompt ({@code prompts/summary.txt}).
 * Mirrors {@code GoldenSetEvaluator}'s safety rules: manual-only, no retry, fixture replay for CI.
 */
public final class SummaryGoldenSetEvaluator {

    private static final Pattern STATUS = Pattern.compile("\\b([45]\\d\\d)\\b");

    /**
     * Thresholds locked from the first measured baseline (2026-11-03, both providers).
     * The pre-baseline draft (A8/A10 = 90%) was a placeholder and was replaced by these
     * measured floors before any run was accepted; they must never be lowered afterwards.
     */
    static final double REQUEST_COVERAGE_FLOOR = 0.80;
    static final double REQUEST_SUPPORT_FLOOR = 0.75;
    static final double LEARNED_RECALL_FLOOR = 0.70;
    static final double LEARNED_SUPPORT_FLOOR = 0.60;

    private SummaryGoldenSetEvaluator() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureIn = blankToNull(env("EVAL_FIXTURE_IN"));
        if (fixtureIn == null) {
            if (!"1".equals(env("REAL_LLM_EVAL"))) {
                System.err.println("Refusing to call a real model: set REAL_LLM_EVAL=1 first.");
                System.exit(2);
            }
            if (env("CI") != null || "1".equals(env("GITHUB_ACTIONS"))) {
                System.err.println("Refusing to run inside CI: real-model evaluation is manual-only.");
                System.exit(2);
            }
        }

        String provider = envOr("EVAL_PROVIDER", "openai").toLowerCase(Locale.ROOT);
        String modelName;
        String baseUrl = null;
        String apiKey = null;
        if ("anthropic".equals(provider)) {
            modelName = envOr("ANTHROPIC_MODEL", "claude-sonnet-4-5");
            if (fixtureIn == null) {
                apiKey = requireEnv("ANTHROPIC_API_KEY");
                baseUrl = envOr("ANTHROPIC_BASE_URL", "https://api.anthropic.com");
            }
        } else {
            modelName = envOr("OPENAI_MODEL", "deepseek-chat");
            if (fixtureIn == null) {
                apiKey = requireEnv("OPENAI_API_KEY");
                baseUrl = envOr("OPENAI_BASE_URL", "https://api.deepseek.com");
            }
        }

        int limit = Integer.parseInt(envOr("EVAL_LIMIT", String.valueOf(Integer.MAX_VALUE)));
        int keyRepeat = Math.max(1, Integer.parseInt(envOr("EVAL_KEY_REPEAT", "3")));
        double temperature = Double.parseDouble(envOr("EVAL_TEMPERATURE", "0.0"));

        SummaryGoldenSet golden = loadGoldenSet();
        String promptDir = blankToNull(env("EVAL_PROMPT_DIR"));
        String userTemplate = readPrompt(promptDir, "summary.txt");

        List<SummaryGoldenSet.Sample> selected = golden.samples.subList(
                0, Math.min(golden.samples.size(), limit));
        List<SummaryGoldenSet.Sample> plan = new ArrayList<>();
        for (SummaryGoldenSet.Sample sample : selected) {
            int runs = sample.keySample ? keyRepeat : 1;
            for (int run = 0; run < runs; run++) {
                plan.add(sample);
            }
        }
        if (plan.isEmpty()) {
            System.err.println("No summary samples selected; nothing to evaluate.");
            System.exit(2);
        }

        List<SummaryGoldenSetScorer.Outcome> outcomes = new ArrayList<>();
        List<Map<String, Object>> recorded = new ArrayList<>();
        Map<String, Integer> runIndexes = new LinkedHashMap<>();
        long startedAt = System.nanoTime();
        ChatModel model = null;
        if (fixtureIn == null) {
            model = buildModel(provider, apiKey, baseUrl, modelName, temperature);
            System.out.printf(Locale.ROOT,
                    "Evaluating %d summary run(s) (%d samples, key repeat %d) with %s (%s)%n",
                    plan.size(), selected.size(), keyRepeat, modelName, provider);
        } else {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> loaded = mapper.readValue(Path.of(fixtureIn).toFile(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) loaded.get("entries");
            for (Map<String, Object> entry : entries) {
                String id = String.valueOf(entry.get("id"));
                SummaryGoldenSet.Sample sample = find(golden, id);
                String raw = entry.get("raw") == null ? null : String.valueOf(entry.get("raw"));
                String error = entry.get("error") == null ? null : String.valueOf(entry.get("error"));
                String status = entry.get("status") == null ? null : String.valueOf(entry.get("status"));
                long elapsed = ((Number) entry.getOrDefault("elapsedMs", 0)).longValue();
                int runIndex = ((Number) entry.getOrDefault("runIndex", 0)).intValue();
                outcomes.add(toOutcome(sample, raw, error, status, elapsed, runIndex));
            }
        }

        if (fixtureIn == null) {
            for (int i = 0; i < plan.size(); i++) {
                SummaryGoldenSet.Sample sample = plan.get(i);
                int runIndex = runIndexes.merge(sample.id, 1, Integer::sum) - 1;
                String system = "You are a memory observer agent. Summarize the following session "
                        + "observations:\n\n" + SummaryGoldenSet.digest(sample);
                String user = userTemplate.replace("{{lastAssistantMessage}}",
                        sample.lastAssistantMessage == null || sample.lastAssistantMessage.isBlank()
                                ? "(not available)" : sample.lastAssistantMessage);
                long started = System.nanoTime();
                try {
                    ChatResponse response = model.call(new Prompt(
                            List.of(new SystemMessage(system), new UserMessage(user))));
                    String raw = response.getResult().getOutput().getText();
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    outcomes.add(toOutcome(sample, raw, null, "200", elapsedMs, runIndex));
                    recorded.add(entry(sample, raw, null, "200", elapsedMs, runIndex));
                    System.out.printf(Locale.ROOT, "  [%d/%d] %-32s run %d %5d ms%n",
                            i + 1, plan.size(), sample.id, runIndex, elapsedMs);
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                    String status = statusOf(error);
                    outcomes.add(toOutcome(sample, null, error, status, elapsedMs, runIndex));
                    recorded.add(entry(sample, null, error, status, elapsedMs, runIndex));
                    System.out.printf(Locale.ROOT, "  [%d/%d] %-32s run %d CALL FAILED (%s): %s%n",
                            i + 1, plan.size(), sample.id, runIndex, status, e.getMessage());
                }
            }
        }

        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        SummaryGoldenSetScorer.Aggregate aggregate = SummaryGoldenSetScorer.score(outcomes);

        String fixtureOut = blankToNull(env("EVAL_FIXTURE_OUT"));
        if (fixtureOut != null) {
            Map<String, Object> fixture = new LinkedHashMap<>();
            fixture.put("provider", provider);
            fixture.put("model", modelName);
            fixture.put("promptDir", promptDir == null ? "current" : promptDir);
            fixture.put("entries", recorded);
            Path path = Path.of(fixtureOut);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), fixture);
            System.out.println("fixture=" + path);
        }

        String reportPath = envOr("EVAL_REPORT",
                "docs/archive/" + LocalDate.now() + "_summary-quality-eval.md");
        String report = report(provider, modelName, fixtureIn, aggregate, durationMs);
        Path path = Path.of(reportPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, report, StandardCharsets.UTF_8);

        String metricsOut = blankToNull(env("EVAL_METRICS_OUT"));
        if (metricsOut != null) {
            Path metricsPath = Path.of(metricsOut);
            if (metricsPath.getParent() != null) {
                Files.createDirectories(metricsPath.getParent());
            }
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(metricsPath.toFile(), metrics(aggregate, durationMs));
            System.out.println("metrics=" + metricsPath);
        }

        System.out.printf(Locale.ROOT,
                "%nrequestCov=%s requestSupport=%s requestIds=%s "
                        + "learnedRecall=%s learnedSupport=%s learnedIds=%s formatErrors=%d%nreport=%s%n",
                pct(aggregate.requestCoverage), pct(aggregate.requestSupportRate),
                pct(aggregate.requestIdentifierHitRate), pct(aggregate.learnedRecall),
                pct(aggregate.learnedSupportRate), pct(aggregate.learnedIdentifierHitRate),
                aggregate.formatErrors, path);
    }

    static SummaryGoldenSet loadGoldenSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = SummaryGoldenSetEvaluator.class
                .getResourceAsStream("/eval/golden-summary.json")) {
            if (in == null) {
                throw new IllegalStateException("missing /eval/golden-summary.json");
            }
            return mapper.readValue(in, SummaryGoldenSet.class);
        }
    }

    static SummaryGoldenSet.Sample find(SummaryGoldenSet golden, String id) {
        return golden.samples.stream().filter(sample -> sample.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("unknown summary sample " + id));
    }

    static SummaryGoldenSetScorer.Outcome toOutcome(SummaryGoldenSet.Sample sample, String raw,
                                                    String error, String status, long elapsedMs,
                                                    int runIndex) {
        if (raw == null) {
            return new SummaryGoldenSetScorer.Outcome(sample, null, null, true,
                    error == null ? status : error, elapsedMs, status, runIndex, false, null);
        }
        try {
            XmlParser.ParsedSummary parsed = XmlParser.parseSummary(raw);
            return new SummaryGoldenSetScorer.Outcome(sample, raw, parsed, false, null,
                    elapsedMs, status, runIndex, false, null);
        } catch (Exception e) {
            return new SummaryGoldenSetScorer.Outcome(sample, raw, null, false, null,
                    elapsedMs, status, runIndex, true, e.getMessage());
        }
    }

    private static Map<String, Object> entry(SummaryGoldenSet.Sample sample, String raw,
                                             String error, String status, long elapsedMs,
                                             int runIndex) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", sample.id);
        entry.put("runIndex", runIndex);
        entry.put("status", status);
        entry.put("elapsedMs", elapsedMs);
        if (raw != null) {
            entry.put("raw", raw);
        }
        if (error != null) {
            entry.put("error", error);
        }
        return entry;
    }

    private static Map<String, Object> metrics(SummaryGoldenSetScorer.Aggregate agg,
                                               long durationMs) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("durationMs", durationMs);
        metrics.put("total", agg.total);
        metrics.put("callFailures", agg.callFailures);
        metrics.put("formatErrors", agg.formatErrors);
        metrics.put("requestCoverage", agg.requestCoverage);
        metrics.put("requestIdentifierHitRate", agg.requestIdentifierHitRate);
        metrics.put("requestSupportRate", agg.requestSupportRate);
        metrics.put("learnedRecall", agg.learnedRecall);
        metrics.put("learnedIdentifierHitRate", agg.learnedIdentifierHitRate);
        metrics.put("learnedSupportRate", agg.learnedSupportRate);
        metrics.put("p50Ms", agg.p50Ms);
        metrics.put("p95Ms", agg.p95Ms);
        metrics.put("maxMs", agg.maxMs);
        return metrics;
    }

    private static String report(String provider, String modelName, String fixtureIn,
                                 SummaryGoldenSetScorer.Aggregate agg, long durationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Session-summary quality evaluation\n\n");
        sb.append("- Provider: `").append(provider).append("`\n");
        sb.append("- Model: `").append(modelName).append("`\n");
        sb.append("- Date: ").append(LocalDate.now()).append("\n");
        sb.append("- Input: ").append(fixtureIn == null ? "live provider calls" : "fixture replay")
                .append("\n");
        sb.append("- Duration: ").append(durationMs).append(" ms\n\n");

        sb.append("## Headline\n\n");
        sb.append("| metric | value |\n|---|---|\n");
        row(sb, "samples", String.valueOf(agg.total));
        row(sb, "call failures", String.valueOf(agg.callFailures));
        row(sb, "format errors", String.valueOf(agg.formatErrors));
        row(sb, "request keyword coverage", pct(agg.requestCoverage));
        row(sb, "request identifier hit rate", pct(agg.requestIdentifierHitRate));
        row(sb, "request trace-support rate", pct(agg.requestSupportRate));
        row(sb, "learned recall", pct(agg.learnedRecall));
        row(sb, "learned identifier hit rate", pct(agg.learnedIdentifierHitRate));
        row(sb, "learned trace-support rate", pct(agg.learnedSupportRate));
        row(sb, "p50 / p95 / max ms", agg.p50Ms + " / " + agg.p95Ms + " / " + agg.maxMs);
        sb.append('\n');

        sb.append("## Gates\n\n");
        gate(sb, "A7", "request keyword coverage", ">= " + pct(REQUEST_COVERAGE_FLOOR),
                pct(agg.requestCoverage), agg.requestCoverage >= REQUEST_COVERAGE_FLOOR);
        gate(sb, "A8", "request trace support", ">= " + pct(REQUEST_SUPPORT_FLOOR),
                pct(agg.requestSupportRate), agg.requestSupportRate >= REQUEST_SUPPORT_FLOOR);
        gate(sb, "A9", "learned recall", ">= " + pct(LEARNED_RECALL_FLOOR),
                pct(agg.learnedRecall), agg.learnedRecall >= LEARNED_RECALL_FLOOR);
        gate(sb, "A10", "learned trace support", ">= " + pct(LEARNED_SUPPORT_FLOOR),
                pct(agg.learnedSupportRate), agg.learnedSupportRate >= LEARNED_SUPPORT_FLOOR);
        sb.append('\n');

        sb.append("## Per sample\n\n");
        sb.append("| id | category | req cov | req ids | req support | learned recall | learned ids | learned support | ms |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (SummaryGoldenSetScorer.SampleScore score : agg.scores) {
            sb.append("| ").append(score.id)
                    .append(" | ").append(score.category)
                    .append(" | ").append(score.formatError ? "FAIL" : pct(score.requestCoverage))
                    .append(" | ").append(score.requestIdentifierHit ? "hit" : "MISS")
                    .append(" | ").append(score.formatError ? "-" : pct(score.requestSupportRate))
                    .append(" | ").append(score.formatError ? "-" : pct(score.learnedRecall))
                    .append(" | ").append(score.learnedIdentifierHit ? "hit" : "MISS")
                    .append(" | ").append(score.formatError ? "-" : pct(score.learnedSupportRate))
                    .append(" | ").append(score.elapsedMs).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Problems\n\n");
        if (agg.problems.isEmpty()) {
            sb.append("None.\n");
        } else {
            agg.problems.forEach(problem -> sb.append("- ").append(problem).append('\n'));
        }
        sb.append('\n');

        sb.append("## Method and limitations\n\n");
        sb.append("- The system prompt is the production observation digest; the user prompt is the")
                .append(" production `prompts/summary.txt` with `{{lastAssistantMessage}}` filled.\n");
        sb.append("- `request` coverage and identifiers measure whether the summary names what the")
                .append(" session was about; `learned` recall measures whether it kept the technical")
                .append(" anchors.\n");
        sb.append("- Trace support splits `request`/`learned` into clauses. A clause is grounded when")
                .append(" every concrete anchor it names (CamelCase identifier, path/URL, number) occurs")
                .append(" verbatim in the recorded tool trace; a clause that names no anchor must pass the")
                .append(" facts-support rule (>= half of its significant tokens present). Paraphrasing a")
                .append(" traced change is allowed; an invented object or number is not.")
                .append(" `request` may additionally use the last assistant message as evidence.\n");
        sb.append("- The golden set is hand-authored for this prompt, not a sample of production traffic.\n");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String name, String value) {
        sb.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static void gate(StringBuilder sb, String id, String name, String threshold,
                             String measured, boolean pass) {
        sb.append("| ").append(id).append(" | ").append(name).append(" | ").append(threshold)
                .append(" | ").append(measured).append(" | ").append(pass ? "PASS" : "FAIL")
                .append(" |\n");
    }

    private static ChatModel buildModel(String provider, String apiKey, String baseUrl,
                                        String modelName, double temperature) {
        if ("anthropic".equals(provider)) {
            AnthropicApi anthropicApi = AnthropicApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
            return AnthropicChatModel.builder()
                    .anthropicApi(anthropicApi)
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .maxTokens(2000)
                            .build())
                    .build();
        }
        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(2000)
                        .build())
                .build();
    }

    private static String readPrompt(String dir, String name) throws Exception {
        String path = (dir == null ? "backend/src/main/resources/prompts" : dir) + "/" + name;
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String statusOf(String error) {
        if (error == null) {
            return null;
        }
        Matcher matcher = STATUS.matcher(error);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String requireEnv(String name) {
        String value = env(name);
        if (value == null || value.isBlank()) {
            System.err.println(name + " is required for a real-model summary evaluation.");
            System.exit(2);
        }
        return value;
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
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }
}
