package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.service.ExtractionValidationService;
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
 * Manual golden-set evaluation against a real provider. Not a JUnit test: Surefire never picks
 * it up, so CI never calls a provider.
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>{@code REAL_LLM_EVAL=1} is required unless a recorded fixture is replayed.</p>
 *   <li>Calls are unthrottled by default ({@code EVAL_MAX_CALLS=0}); a positive value caps them.</li>
 *   <li>Key samples are repeated {@code EVAL_KEY_REPEAT} times (default 3) for stability.</li>
 *   <li>A failed call is never retried.</li>
 *   <li>{@code EVAL_FIXTURE_OUT} records every answer for offline replay; {@code EVAL_FIXTURE_IN}
 *       replays one without touching the network.</li>
 *   <li>{@code EVAL_PROMPT_DIR} points at a prompt directory (used to replay the pre-change
 *       baseline prompt from {@code src/test/resources/eval/prompts-baseline}).</li>
 *   <li>{@code EVAL_VALIDATE=0} disables the deterministic guard in-memory; default is 1.</li>
 * </ul>
 */
public final class GoldenSetEvaluator {

    private static final Pattern STATUS = Pattern.compile("\\b([45]\\d\\d)\\b");

    /**
     * Round-9 single-attempt baselines on the hand-written set, measured before any
     * performance floor existed. The gate is p95 ≤ {@code PERFORMANCE_TOLERANCE × baseline};
     * it may only be tightened. Providers missing here have no baseline yet and receive no
     * verdict.
     */
    static final Map<String, Long> LATENCY_P95_BASELINE_MS = Map.of(
            "anthropic", 5648L,
            "openai", 33716L);
    static final Map<String, Long> TOKEN_P95_BASELINE = Map.of(
            "anthropic", 4048L,
            "openai", 5763L);
    static final double PERFORMANCE_TOLERANCE = 1.5;

    private GoldenSetEvaluator() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureIn = blankToNull(env("EVAL_FIXTURE_IN"));
        if (fixtureIn == null) {
            if (env("REAL_LLM_EVAL") == null || !"1".equals(env("REAL_LLM_EVAL"))) {
                System.err.println("Refusing to call a real model: set REAL_LLM_EVAL=1 first.");
                System.exit(2);
            }
            if (env("CI") != null || "1".equals(env("GITHUB_ACTIONS"))) {
                System.err.println("Refusing to run inside CI: real-model evaluation is manual-only.");
                System.exit(2);
            }
        }

        String provider = envOr("EVAL_PROVIDER", "openai").toLowerCase(Locale.ROOT);
        String phase = envOr("EVAL_PHASE", "current");
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

        int maxCalls = Integer.parseInt(envOr("EVAL_MAX_CALLS", "0")); // 0 = unlimited
        int limit = Integer.parseInt(envOr("EVAL_LIMIT", String.valueOf(Integer.MAX_VALUE)));
        int keyRepeat = Math.max(1, Integer.parseInt(envOr("EVAL_KEY_REPEAT", "3")));
        boolean repeatAll = "1".equals(envOr("EVAL_REPEAT_ALL", "0"));
        double temperature = Double.parseDouble(envOr("EVAL_TEMPERATURE", "0.0"));
        boolean validate = "1".equals(envOr("EVAL_VALIDATE", "1"));

        GoldenSet golden = loadGoldenSet();
        String goldenSet = envOr("EVAL_GOLDEN", "main");
        String promptDir = blankToNull(env("EVAL_PROMPT_DIR"));
        String systemPrompt = readPrompt(promptDir, "init.txt");
        String observationTemplate = readPrompt(promptDir, "observation.txt");

        List<GoldenSet.Sample> selected = golden.samples.subList(0, Math.min(golden.samples.size(), limit));
        List<GoldenSet.Sample> plan = new ArrayList<>();
        for (GoldenSet.Sample sample : selected) {
            int runs = (sample.keySample || repeatAll) ? keyRepeat : 1;
            for (int run = 0; run < runs; run++) {
                plan.add(sample);
            }
        }
        int planned = maxCalls > 0 ? Math.min(plan.size(), maxCalls) : plan.size();
        if (planned < 1) {
            System.err.println("No samples selected; nothing to evaluate.");
            System.exit(2);
        }

        List<GoldenSetScorer.Outcome> outcomes;
        int calls;
        long startedAt = System.nanoTime();
        Map<String, Object> fixture;
        ChatModel model = null;
        if (fixtureIn == null) {
            model = buildModel(provider, apiKey, baseUrl, modelName, temperature);
            System.out.printf(Locale.ROOT,
                    "Evaluating %d scenario run(s) (%d samples, key repeat %d) with %s (%s) at %s "
                            + "(cap=%s, validate=%s, temperature=%.1f, repeatAll=%s)%n",
                    planned, selected.size(), keyRepeat, modelName, provider, baseUrl,
                    maxCalls == 0 ? "none" : String.valueOf(maxCalls), validate, temperature,
                    repeatAll);
        } else {
            System.out.printf(Locale.ROOT,
                    "Replaying fixture %s for %s (%s), validate=%s%n",
                    fixtureIn, modelName, provider, validate);
        }

        List<Map<String, Object>> recorded = new ArrayList<>();
        outcomes = new ArrayList<>();
        calls = 0;
        Map<String, Integer> runIndexes = new LinkedHashMap<>();
        if (fixtureIn != null) {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> loaded = mapper.readValue(Path.of(fixtureIn).toFile(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) loaded.get("entries");
            for (Map<String, Object> entry : entries) {
                String id = String.valueOf(entry.get("id"));
                GoldenSet.Sample sample = find(golden, id);
                String raw = entry.get("raw") == null ? null : String.valueOf(entry.get("raw"));
                String error = entry.get("error") == null ? null : String.valueOf(entry.get("error"));
                String status = entry.get("status") == null ? null : String.valueOf(entry.get("status"));
                long elapsed = ((Number) entry.getOrDefault("elapsedMs", 0)).longValue();
                int runIndex = ((Number) entry.getOrDefault("runIndex", 0)).intValue();
                int promptTokens = ((Number) entry.getOrDefault("promptTokens", 0)).intValue();
                int completionTokens = ((Number) entry.getOrDefault("completionTokens", 0)).intValue();
                outcomes.add(toOutcome(sample, raw, error, status, elapsed, runIndex, validate,
                        promptTokens, completionTokens));
            }
        } else {
            int consistency = Math.max(1,
                    Integer.parseInt(envOr("EVAL_CONSISTENCY_SAMPLES", "1")));
            for (int i = 0; i < planned; i++) {
                GoldenSet.Sample sample = plan.get(i);
                int runIndex = runIndexes.merge(sample.id, 1, Integer::sum) - 1;
                String userPrompt = fillTemplate(observationTemplate, sample);
                long started = System.nanoTime();
                calls += consistency;
                try {
                    List<CallResult> results = callConcurrently(model, systemPrompt, userPrompt,
                            consistency);
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    String raw = majorityRaw(results);
                    if (raw == null) {
                        CallResult failure = results.stream()
                                .filter(result -> result.error() != null).findFirst().orElse(null);
                        throw new IllegalStateException(failure == null
                                ? "all attempts returned no content" : failure.error());
                    }
                    int promptTokens = results.stream().mapToInt(CallResult::promptTokens).sum();
                    int completionTokens = results.stream().mapToInt(CallResult::completionTokens).sum();
                    outcomes.add(toOutcome(sample, raw, null, "200", elapsedMs, runIndex, validate,
                            promptTokens, completionTokens));
                    recorded.add(entry(sample, raw, null, "200", elapsedMs, runIndex,
                            promptTokens, completionTokens));
                    System.out.printf(Locale.ROOT, "  [%d/%d] %-30s run %d %5d ms (x%d, %d tokens)%n",
                            i + 1, planned, sample.id, runIndex, elapsedMs, consistency,
                            promptTokens + completionTokens);
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                    String status = statusOf(error);
                    outcomes.add(new GoldenSetScorer.Outcome(sample, null, false, null, true,
                            error, elapsedMs, status, runIndex, false));
                    recorded.add(entry(sample, null, error, status, elapsedMs, runIndex, 0, 0));
                    System.out.printf(Locale.ROOT, "  [%d/%d] %-30s run %d CALL FAILED (%s): %s%n",
                            i + 1, planned, sample.id, runIndex, status, e.getMessage());
                }
            }
        }

        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        GoldenSetScorer.Aggregate aggregate = GoldenSetScorer.score(outcomes);

        String fixtureOut = blankToNull(env("EVAL_FIXTURE_OUT"));
        if (fixtureOut != null) {
            fixture = new LinkedHashMap<>();
            fixture.put("provider", provider);
            fixture.put("model", modelName);
            fixture.put("phase", phase);
            fixture.put("promptDir", promptDir == null ? "current" : promptDir);
            fixture.put("validate", validate);
            fixture.put("entries", recorded);
            Path fixturePath = Path.of(fixtureOut);
            if (fixturePath.getParent() != null) {
                Files.createDirectories(fixturePath.getParent());
            }
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(fixturePath.toFile(), fixture);
            System.out.println("fixture=" + fixturePath);
        }

        String reportPath = envOr("EVAL_REPORT",
                "docs/archive/" + LocalDate.now() + "_model-output-quality-eval.md");
        String report = "golden set: " + goldenSet + " (" + golden.samples.size() + " samples)\n\n"
                + renderReport(provider, modelName, baseUrl, promptDir, phase, temperature,
                calls, maxCalls, keyRepeat, durationMs, aggregate, "main".equalsIgnoreCase(goldenSet));
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
                    .writeValue(metricsPath.toFile(), metrics(aggregate, calls, durationMs));
            System.out.println("metrics=" + metricsPath);
        }

        printSummary(aggregate, calls, durationMs, path);
        int callFailures = aggregate.callFailures;
        if (callFailures > 0) {
            System.err.println("Warning: " + callFailures + " call(s) failed; see the status distribution.");
        }
    }

    static GoldenSetScorer.Outcome toOutcome(GoldenSet.Sample sample, String raw, String error,
                                                     String status, long elapsedMs, int runIndex,
                                                     boolean validate) {
        return toOutcome(sample, raw, error, status, elapsedMs, runIndex, validate, 0, 0);
    }

    static GoldenSetScorer.Outcome toOutcome(GoldenSet.Sample sample, String raw, String error,
                                                     String status, long elapsedMs, int runIndex,
                                                     boolean validate, int promptTokens,
                                                     int completionTokens) {
        if (error != null || raw == null) {
            return new GoldenSetScorer.Outcome(sample, raw, false, null, true,
                    error == null ? "no raw answer" : error, elapsedMs, status, runIndex, false,
                    promptTokens, completionTokens);
        }
        String skip = XmlParser.extractTag(raw, "skip");
        if (skip != null) {
            return new GoldenSetScorer.Outcome(sample, raw, true, null, false, null,
                    elapsedMs, status, runIndex, false, promptTokens, completionTokens);
        }
        XmlParser.ParsedObservation observation;
        try {
            observation = XmlParser.parseObservation(raw);
        } catch (Exception e) {
            return new GoldenSetScorer.Outcome(sample, raw, false, null, false,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs, status,
                    runIndex, false, promptTokens, completionTokens);
        }
        if (validate) {
            ExtractionValidationService guard =
                    new ExtractionValidationService(null, true, 100, 5, 20);
            ExtractionValidationService.Result result = guard.validate(observation,
                    sample.toolName, sample.toolInput, sample.toolOutput, null);
            if (!result.accepted()) {
                return new GoldenSetScorer.Outcome(sample, raw, false, null, false,
                        result.reason(), elapsedMs, status, runIndex, true,
                        promptTokens, completionTokens);
            }
            observation.facts = result.facts();
        }
        return new GoldenSetScorer.Outcome(sample, raw, false, observation, false, null,
                elapsedMs, status, runIndex, false, promptTokens, completionTokens);
    }

    private static Map<String, Object> entry(GoldenSet.Sample sample, String raw, String error,
                                              String status, long elapsedMs, int runIndex,
                                              int promptTokens, int completionTokens) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", sample.id);
        entry.put("runIndex", runIndex);
        entry.put("status", status);
        entry.put("elapsedMs", elapsedMs);
        entry.put("promptTokens", promptTokens);
        entry.put("completionTokens", completionTokens);
        entry.put("raw", raw);
        if (error != null) {
            entry.put("error", error);
        }
        return entry;
    }

    static GoldenSet.Sample find(GoldenSet golden, String id) {
        return golden.samples.stream().filter(sample -> sample.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Fixture references unknown sample " + id));
    }

    private static String statusOf(String error) {
        Matcher matcher = STATUS.matcher(error == null ? "" : error);
        return matcher.find() ? matcher.group(1) : "exception";
    }

    /**
     * Output budget sent to both providers, so a truncation is never mistaken for a model
     * difference. It must stay above the longest answer plus any provider-side reasoning:
     * LongCat emits {@code reasoning_content} that counts against the same budget, and at
     * 1024 tokens its XML answer was cut off mid-element (finish_reason=length).
     */
    static final int MAX_OUTPUT_TOKENS = 4096;

    private static ChatModel buildModel(String provider, String apiKey, String baseUrl,
                                        String modelName, double temperature) {
        if ("anthropic".equals(provider)) {
            AnthropicApi api = AnthropicApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model(modelName)
                    .temperature(temperature)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .build();
            return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options).build();
        }
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxTokens(MAX_OUTPUT_TOKENS)
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

    static GoldenSet loadGoldenSet() throws Exception {
        return loadGoldenSet(envOr("EVAL_GOLDEN", "main"));
    }

    static GoldenSet loadGoldenSet(String which) throws Exception {
        String resource = "real".equalsIgnoreCase(which)
                ? "/eval/golden-tool-use-real.json"
                : "/eval/golden-tool-use.json";
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = GoldenSetEvaluator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            return mapper.readValue(in, GoldenSet.class);
        }
    }

    private static String readPrompt(String promptDir, String name) throws Exception {
        if (promptDir == null) {
            return resource("prompts/" + name);
        }
        Path path = Path.of(promptDir, name);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Missing prompt file " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = GoldenSetEvaluator.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String renderReport(String provider, String modelName, String baseUrl,
                                       String promptDir, String phase, double temperature,
                                       int calls, int maxCalls, int keyRepeat, long durationMs,
                                       GoldenSetScorer.Aggregate agg, boolean performanceGated) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Model output quality evaluation\n\n");
        sb.append("- Date: ").append(LocalDate.now()).append('\n');
        sb.append("- Phase: ").append(phase).append('\n');
        sb.append("- Provider: ").append(provider).append(", model `").append(modelName).append("`, base URL `")
                .append(baseUrl == null ? "replay" : baseUrl).append("`\n");
        sb.append("- Prompt source: ").append(promptDir == null
                ? "classpath `prompts/` (current production prompt)" : promptDir).append('\n');
        sb.append("- Samples: ").append(agg.total).append(" unique; runs scored: ").append(agg.runs.size())
                .append("; key repeat: ").append(keyRepeat).append("; real calls: ").append(calls)
                .append(" (cap ").append(maxCalls == 0 ? "none" : maxCalls).append("); duration: ")
                .append(durationMs).append(" ms; temperature: ").append(temperature).append('\n');
        sb.append("- Parser: production `XmlParser`; skip detection via `<skip>`.\n\n");

        sb.append("## Headline (first run of every sample)\n\n");
        sb.append("| metric | value |\n|---|---|\n");
        row(sb, "contract pass rate (observation parsed or skip when expected)", pct(agg.contractPassRate));
        row(sb, "skip accuracy", pct(agg.skipAccuracy));
        row(sb, "  skip confusion (TP/FP/FN/TN)",
                agg.skipTp + "/" + agg.skipFp + "/" + agg.skipFn + "/" + agg.skipTn);
        row(sb, "format errors", String.valueOf(agg.formatErrors));
        row(sb, "validation rejections", String.valueOf(agg.validationRejections));
        row(sb, "call failures", String.valueOf(agg.callFailures));
        row(sb, "title exact-hit rate (legacy)", pct(agg.titleExactHitRate));
        row(sb, "title normalized hit rate (legacy)", pct(agg.titleNormalizedHitRate));
        row(sb, "title action-class hit rate (A6)", pct(agg.titleActionHitRate)
                + " (" + agg.titleActionSamples + " scored, "
                + agg.titleActionUnclassifiable + " unclassifiable)");
        row(sb, "title verb-class hit rate (diagnostic, retired)", pct(agg.titleVerbClassHitRate));
        row(sb, "title subject hit rate (legacy, diagnostic)", pct(agg.titleSubjectHitRate));
        row(sb, "title trace-visible subject hit rate (A6b)", pct(agg.titleTraceSubjectHitRate)
                + " (" + agg.titleTraceSubjectSamples + " scored, "
                + agg.titleTraceSubjectUnreachable + " unreachable/excluded)");
        row(sb, "title contract hit rate (legacy definition, diagnostic)",
                pct(agg.titleContractHitRate));
        row(sb, "title trace contract hit rate (A6: action + reachable subject + identifiers)",
                pct(agg.titleTraceContractHitRate));
        row(sb, "title verb contract hit rate (verb + subject + identifiers, diagnostic)",
                pct(agg.titleVerbContractHitRate));
        row(sb, "title identifier hit rate", pct(agg.titleIdentifierHitRate));
        row(sb, "type accuracy", pct(agg.typeAccuracy));
        row(sb, "facts keyword precision (legacy) / recall",
                pct(agg.factsPrecision) + " / " + pct(agg.factsRecall));
        row(sb, "facts informative precision", pct(agg.factsInformativeRate));
        row(sb, "facts trace-support rate", pct(agg.factsSupportRate));
        row(sb, "concepts precision / recall", pct(agg.conceptsPrecision) + " / " + pct(agg.conceptsRecall));
        row(sb, "skip consistency across repeats", pct(agg.repeatConsistency)
                + " (" + agg.repeatedSamples + " repeated samples)");
        row(sb, "latency p50 / p95 / max (ms)",
                agg.p50Ms + " / " + agg.p95Ms + " / " + agg.maxMs);
        row(sb, "tokens p50 / p95 / max / mean per call",
                agg.p50Tokens + " / " + agg.p95Tokens + " / " + agg.maxTokens + " / " + agg.meanTokens);
        sb.append('\n');

        sb.append("## Acceptance gate (pre-committed in docs/drafts/extraction-quality-improvement-plan.md)\n\n");
        sb.append("| id | criterion | threshold | observed | status |\n|---|---|---|---|---|\n");
        gate(sb, "A1", "format errors", "= 0", String.valueOf(agg.formatErrors), agg.formatErrors == 0);
        gate(sb, "A2", "skip accuracy", ">= 95%", pct(agg.skipAccuracy), agg.skipAccuracy >= 0.95);
        // skipFp = a record-worthy sample was skipped (the failure A3 guards); skipFn = a
        // low-value sample was recorded (the failure A4 guards). The two counters were
        // reported under each other's labels in the v3 report, so pin them here.
        gate(sb, "A3", "must-record samples skipped", "= 0", String.valueOf(agg.skipFp), agg.skipFp == 0);
        gate(sb, "A4", "low-value samples recorded", "<= max(1, TP/20)", String.valueOf(agg.skipFn),
                agg.skipFn <= Math.max(1, agg.skipTp / 20));
        gate(sb, "A5", "facts informative precision", ">= 75%",
                pct(agg.factsInformativeRate), agg.factsInformativeRate >= 0.75);
        gate(sb, "A6a", "title action class (trace-derived)", ">= 80%",
                pct(agg.titleActionHitRate), agg.titleActionHitRate >= 0.80);
        gate(sb, "A6b", "title trace-visible subject", ">= 90%",
                pct(agg.titleTraceSubjectHitRate), agg.titleTraceSubjectHitRate >= 0.90);
        gate(sb, "A6c", "title identifiers", "= 100%",
                pct(agg.titleIdentifierHitRate), agg.titleIdentifierHitRate >= 1.0);
        gate(sb, "A6", "all three title dimensions together", "all pass",
                pct(agg.titleTraceContractHitRate),
                agg.titleActionHitRate >= 0.80 && agg.titleTraceSubjectHitRate >= 0.90
                        && agg.titleIdentifierHitRate >= 1.0);
        Long latencyBaseline = performanceGated ? LATENCY_P95_BASELINE_MS.get(provider) : null;
        Long tokenBaseline = performanceGated ? TOKEN_P95_BASELINE.get(provider) : null;
        if (latencyBaseline != null) {
            gate(sb, "A11", "p95 latency vs baseline", "<= 1.5x baseline",
                    agg.p95Ms + " ms (baseline " + latencyBaseline + " ms)",
                    agg.p95Ms <= latencyBaseline * PERFORMANCE_TOLERANCE);
        }
        if (tokenBaseline != null) {
            gate(sb, "A12", "p95 tokens vs baseline", "<= 1.5x baseline",
                    agg.p95Tokens + " tokens (baseline " + tokenBaseline + ")",
                    agg.p95Tokens <= tokenBaseline * PERFORMANCE_TOLERANCE);
        }
        sb.append('\n');

        if (!agg.statusCounts.isEmpty()) {
            sb.append("## Status distribution\n\n| status | calls |\n|---|---|\n");
            agg.statusCounts.forEach((status, count) ->
                    sb.append("| ").append(status).append(" | ").append(count).append(" |\n"));
            sb.append('\n');
        }

        sb.append("## Per run (single vs average vs worst)\n\n");
        sb.append("| run | samples | skip accuracy | title normalized | title contract | facts P | facts actionable | facts R | format errors |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        double avgSkip = 0, avgTitle = 0, avgContract = 0, avgFactsP = 0, avgActionable = 0, avgFactsR = 0;
        double worstSkip = 1, worstTitle = 1, worstContract = 1, worstFactsP = 1, worstActionable = 1, worstFactsR = 1;
        for (GoldenSetScorer.RunSummary run : agg.runs) {
            sb.append("| ").append(run.runIndex).append(" | ").append(run.samples)
                    .append(" | ").append(pct(run.skipAccuracy))
                    .append(" | ").append(pct(run.titleNormalizedHitRate))
                    .append(" | ").append(pct(run.titleContractHitRate))
                    .append(" | ").append(pct(run.factsPrecision))
                    .append(" | ").append(pct(run.factsInformativeRate))
                    .append(" | ").append(pct(run.factsRecall))
                    .append(" | ").append(run.formatErrors).append(" |\n");
            avgSkip += run.skipAccuracy;
            avgTitle += run.titleNormalizedHitRate;
            avgContract += run.titleContractHitRate;
            avgFactsP += run.factsPrecision;
            avgActionable += run.factsInformativeRate;
            avgFactsR += run.factsRecall;
            worstSkip = Math.min(worstSkip, run.skipAccuracy);
            worstTitle = Math.min(worstTitle, run.titleNormalizedHitRate);
            worstContract = Math.min(worstContract, run.titleContractHitRate);
            worstFactsP = Math.min(worstFactsP, run.factsPrecision);
            worstActionable = Math.min(worstActionable, run.factsInformativeRate);
            worstFactsR = Math.min(worstFactsR, run.factsRecall);
        }
        int runs = Math.max(1, agg.runs.size());
        sb.append("\n- average: skip ").append(pct(avgSkip / runs))
                .append(", title ").append(pct(avgTitle / runs))
                .append(", title contract ").append(pct(avgContract / runs))
                .append(", facts P/R ").append(pct(avgFactsP / runs)).append("/").append(pct(avgFactsR / runs))
                .append(", facts actionable ").append(pct(avgActionable / runs))
                .append("\n- worst run: skip ").append(pct(worstSkip))
                .append(", title ").append(pct(worstTitle))
                .append(", title contract ").append(pct(worstContract))
                .append(", facts P/R ").append(pct(worstFactsP)).append("/").append(pct(worstFactsR))
                .append(", facts actionable ").append(pct(worstActionable))
                .append("\n\n");

        sb.append("## Per type\n\n");
        sb.append("| category | samples | contract pass | type match | title normalized |\n");
        sb.append("|---|---|---|---|---|\n");
        agg.perType.forEach((category, type) -> sb.append("| ").append(category)
                .append(" | ").append(type.samples)
                .append(" | ").append(type.contractPass).append("/").append(type.samples)
                .append(" | ").append(type.typeMatch).append("/").append(type.samples)
                .append(" | ").append(type.titleNormalizedHit).append("/").append(type.samples)
                .append(" |\n"));
        sb.append('\n');

        if (!agg.variance.isEmpty()) {
            sb.append("## Run-to-run variance (repeated samples)\n\n");
            sb.append("| metric | min | max | mean | std dev |\n|---|---|---|---|---|\n");
            for (GoldenSetScorer.MetricVariance variance : agg.variance) {
                sb.append("| ").append(variance.metric)
                        .append(" | ").append(pct(variance.min))
                        .append(" | ").append(pct(variance.max))
                        .append(" | ").append(pct(variance.mean))
                        .append(" | ").append(pct(variance.stdDev))
                        .append(" |\n");
            }
            sb.append("\n## Inconsistent samples (type or action changed between runs)\n\n");
            if (agg.inconsistentSamples.isEmpty()) {
                sb.append("None: every repeated sample kept the same type and action class.\n\n");
            } else {
                sb.append(agg.inconsistentSamples.size()).append(" of ")
                        .append(agg.repeatedSamples).append(" repeated samples disagreed:\n\n");
                for (String sample : agg.inconsistentSamples) {
                    sb.append("- ").append(sample).append('\n');
                }
                sb.append('\n');
            }
        }

        if (!agg.unreachableSubjectSamples.isEmpty()) {
            sb.append("## Unreachable subject labels (excluded from A6b)\n\n");
            sb.append(agg.unreachableSubjectSamples.size())
                    .append(" sample(s) declare subject tokens the recorded trace never contains:\n\n");
            for (String entry : agg.unreachableSubjectSamples) {
                sb.append("- ").append(entry).append('\n');
            }
            sb.append('\n');
        }

        appendFailureClusters(sb, agg);

        sb.append("## Per sample\n\n");
        sb.append("| id | category | expected | actual | contract | title norm | title contract | title exact | type | facts P/R | facts actionable | concepts P/R | ms |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (GoldenSetScorer.SampleScore score : agg.scores) {
            String actual = score.actualSkip ? "skip"
                    : (score.parsed ? "observation"
                    : (score.callFailed ? "call-failed"
                    : (score.validationRejected ? "validation-rejected" : "malformed")));
            String titleNorm = score.parsed ? pct(score.titleCoverage) : "-";
            String titleContract = score.parsed
                    ? (score.titleTraceContractHit ? "hit"
                        : "MISS (" + (score.titleTraceSubjectScored
                            ? "subject " + (score.titleTraceSubjectHit ? "hit" : "MISS") + ", "
                            : "subject unreachable, ")
                        + "id " + (score.titleIdentifierHit ? "hit" : "MISS") + ", "
                        + "action " + (score.titleActionHit ? "hit" : "MISS") + ")")
                    : "-";
            String titleExact = score.parsed ? (score.titleExactHit ? "hit" : "miss") : "-";
            String type = score.parsed ? (score.typeMatch ? "match" : "MISS") : "-";
            String facts = score.parsed
                    ? pct(score.factsPrecision) + "/" + pct(score.factsRecall) : "-";
            String concepts = score.parsed
                    ? pct(score.conceptsPrecision) + "/" + pct(score.conceptsRecall) : "-";
            sb.append("| ").append(score.id)
                    .append(" | ").append(score.category)
                    .append(" | ").append(score.expectedSkip ? "skip" : "observation")
                    .append(" | ").append(actual)
                    .append(" | ").append(score.contractPass ? "pass" : "FAIL")
                    .append(" | ").append(titleNorm)
                    .append(" | ").append(titleContract)
                    .append(" | ").append(titleExact)
                    .append(" | ").append(type)
                    .append(" | ").append(facts)
                    .append(" | ").append(score.parsed ? pct(score.factsInformativeRate) : "-")
                    .append(" | ").append(concepts)
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
        sb.append("- The prompt is the production `prompts/init.txt` system message plus the filled")
                .append(" `prompts/observation.txt`; the baseline phase replays a checked-in prompt snapshot.\n");
        sb.append("- `EVAL_VALIDATE=1` applies the production deterministic guard in-memory (no duplicate")
                .append(" lookup), so the “after” numbers include the guard and the “before” numbers do not.\n");
        sb.append("- Title contract hit requires (1) the leading verb to belong to the declared action class,")
                .append(" (2) the declared subject tokens that actually occur in the trace to reach")
                .append(" coverage ≥ 0.8, and (3) every declared identifier to appear verbatim. Subject")
                .append(" tokens the trace never mentions are unreachable labels: they leave the A6b")
                .append(" denominator and are counted, while the legacy subject rate is reported alongside.\n");
        sb.append("- Facts informative precision (A5) counts produced facts that name a concrete object")
                .append(" (identifier, path, number, status code or quoted value), state an actionable outcome,")
                .append(" and are trace-grounded; legacy keyword precision is reported alongside.")
                .append(" Concepts recall is the primary concepts signal.\n");
        sb.append("- The golden set is hand-authored and describes this set, not all production traffic.\n");
        sb.append("- Real calls are manual-only and never retried; CI only replays fixtures.\n");
        return sb.toString();
    }

    /**
     * Round 10: classify every scored observation into the four failure clusters the review
     * asked for, with one line of evidence per sample. A sample can sit in several clusters —
     * a title can miss both the identifier and the action — so the shares do not sum to 100%.
     */
    private static void appendFailureClusters(StringBuilder sb,
                                              GoldenSetScorer.Aggregate agg) {
        List<GoldenSetScorer.SampleScore> scored = agg.scores.stream()
                .filter(score -> score.parsed && !score.expectedSkip && !score.actualSkip)
                .toList();
        sb.append("## Failure clusters (first run)\n\n");
        sb.append("| cluster | misses | share of scored (").append(scored.size()).append(") |\n");
        sb.append("|---|---|---|\n");
        clusterRow(sb, "identifier missing", scored,
                score -> !score.titleIdentifierHit);
        clusterRow(sb, "action class wrong", scored,
                score -> !score.titleActionHit);
        clusterRow(sb, "subject wrong", scored,
                score -> score.titleTraceSubjectScored && !score.titleTraceSubjectHit);
        clusterRow(sb, "type wrong", scored,
                score -> !score.typeMatch);
        sb.append('\n');
        clusterEvidence(sb, "identifier missing", scored,
                score -> !score.titleIdentifierHit,
                score -> "expected identifiers " + score.expectedIdentifiers
                        + ", title '" + score.title + "'");
        clusterEvidence(sb, "action class wrong", scored,
                score -> !score.titleActionHit,
                score -> "expected " + score.titleActionExpected + ", got '"
                        + score.titleActionActual + "' from title '" + score.title + "'");
        clusterEvidence(sb, "subject wrong", scored,
                score -> score.titleTraceSubjectScored && !score.titleTraceSubjectHit,
                score -> "expected subject tokens " + score.titleTraceSubject
                        + ", title '" + score.title + "'");
        clusterEvidence(sb, "type wrong", scored,
                score -> !score.typeMatch,
                score -> "expected '" + score.expectedType + "', got '" + score.actualType
                        + "' (title '" + score.title + "')");
    }

    private static void clusterRow(StringBuilder sb, String name,
                                   List<GoldenSetScorer.SampleScore> scored,
                                   java.util.function.Predicate<GoldenSetScorer.SampleScore> miss) {
        long misses = scored.stream().filter(miss).count();
        sb.append("| ").append(name).append(" | ").append(misses).append(" | ")
                .append(pct(scored.isEmpty() ? 0.0 : (double) misses / scored.size()))
                .append(" |\n");
    }

    private static void clusterEvidence(StringBuilder sb, String name,
                                        List<GoldenSetScorer.SampleScore> scored,
                                        java.util.function.Predicate<GoldenSetScorer.SampleScore> miss,
                                        java.util.function.Function<GoldenSetScorer.SampleScore, String> evidence) {
        List<GoldenSetScorer.SampleScore> misses = scored.stream().filter(miss).toList();
        if (misses.isEmpty()) {
            return;
        }
        sb.append("### ").append(name).append(" (").append(misses.size()).append(")\n\n");
        for (GoldenSetScorer.SampleScore score : misses) {
            sb.append("- ").append(score.id).append(" — ").append(evidence.apply(score)).append('\n');
        }
        sb.append('\n');
    }

    /** Result of one provider call, including failure, usage and latency. */
    private record CallResult(String raw, String error, int promptTokens,
                              int completionTokens, long elapsedMs) {
    }

    /**
     * Round 10: run the same trace {@code n} times concurrently, mirroring the production
     * majority vote. Wall-clock latency is the group's latency, not the sum, which is the
     * point of the change; token cost is the sum of all attempts and is reported as such.
     */
    private static List<CallResult> callConcurrently(ChatModel model, String systemPrompt,
                                                     String userPrompt, int n) throws Exception {
        // One estimator for both providers. Provider-reported prompt tokens are not
        // comparable: the Anthropic adapter reports input_tokens=1 while the real prompt
        // sits in cache_read_input_tokens (see docs/archive/2026-11-03_provider-request-alignment.md),
        // so a cross-provider cost table built from that field is meaningless.
        int promptTokens = estimateTokens(systemPrompt + "\n" + userPrompt);
        if (n <= 1) {
            long started = System.nanoTime();
            ChatResponse response = model.call(new Prompt(
                    List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            return List.of(resultOf(response, elapsedMs, promptTokens));
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors
                .newFixedThreadPool(n);
        try {
            List<java.util.concurrent.Future<CallResult>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < n; attempt++) {
                futures.add(pool.submit(() -> {
                    long started = System.nanoTime();
                    ChatResponse response = model.call(new Prompt(
                            List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt))));
                    return resultOf(response, (System.nanoTime() - started) / 1_000_000,
                            promptTokens);
                }));
            }
            List<CallResult> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static final com.knuddels.jtokkit.api.Encoding TOKEN_ENCODING =
            com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry()
                    .getEncodingForModel(com.knuddels.jtokkit.api.ModelType.GPT_4);

    static int estimateTokens(String text) {
        return text == null ? 0 : TOKEN_ENCODING.countTokens(text);
    }

    private static CallResult resultOf(ChatResponse response, long elapsedMs, int promptTokens) {
        String raw = response.getResult().getOutput().getText();
        int completionTokens = 0;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        }
        return new CallResult(raw, null, promptTokens, completionTokens, elapsedMs);
    }

    /** Majority winner over the concurrent attempts; order is attempt order for tie-breaks. */
    private static String majorityRaw(List<CallResult> results) {
        List<com.ablueforce.cortexce.service.ExtractionConsistency.Candidate> candidates =
                new ArrayList<>();
        for (CallResult result : results) {
            candidates.add(candidateOf(result.raw()));
        }
        int winner = com.ablueforce.cortexce.service.ExtractionConsistency.select(candidates);
        return winner < 0 ? null : results.get(winner).raw();
    }

    private static com.ablueforce.cortexce.service.ExtractionConsistency.Candidate candidateOf(
            String raw) {
        if (raw == null) {
            return new com.ablueforce.cortexce.service.ExtractionConsistency.Candidate(
                    false, false, null, null);
        }
        if (raw.contains("<skip>")) {
            return new com.ablueforce.cortexce.service.ExtractionConsistency.Candidate(
                    true, true, null, null);
        }
        try {
            var observation = XmlParser.parseObservation(raw);
            return new com.ablueforce.cortexce.service.ExtractionConsistency.Candidate(
                    false, true, observation.type, observation.title);
        } catch (RuntimeException e) {
            return new com.ablueforce.cortexce.service.ExtractionConsistency.Candidate(
                    false, false, null, null);
        }
    }

    private static Map<String, Object> metrics(GoldenSetScorer.Aggregate agg, int calls, long durationMs) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("calls", calls);
        metrics.put("durationMs", durationMs);
        metrics.put("total", agg.total);
        metrics.put("formatErrors", agg.formatErrors);
        metrics.put("validationRejections", agg.validationRejections);
        metrics.put("callFailures", agg.callFailures);
        metrics.put("skipAccuracy", agg.skipAccuracy);
        metrics.put("skipTp", agg.skipTp);
        metrics.put("skipFp", agg.skipFp);
        metrics.put("skipFn", agg.skipFn);
        metrics.put("skipTn", agg.skipTn);
        metrics.put("contractPassRate", agg.contractPassRate);
        metrics.put("titleExactHitRate", agg.titleExactHitRate);
        metrics.put("titleNormalizedHitRate", agg.titleNormalizedHitRate);
        metrics.put("titleIdentifierHitRate", agg.titleIdentifierHitRate);
        metrics.put("titleVerbClassHitRate", agg.titleVerbClassHitRate);
        metrics.put("titleActionHitRate", agg.titleActionHitRate);
        metrics.put("titleActionSamples", agg.titleActionSamples);
        metrics.put("titleActionUnclassifiable", agg.titleActionUnclassifiable);
        metrics.put("titleSubjectHitRate", agg.titleSubjectHitRate);
        metrics.put("titleTraceSubjectHitRate", agg.titleTraceSubjectHitRate);
        metrics.put("titleTraceSubjectSamples", agg.titleTraceSubjectSamples);
        metrics.put("titleTraceSubjectUnreachable", agg.titleTraceSubjectUnreachable);
        metrics.put("unreachableSubjectSamples", agg.unreachableSubjectSamples);
        metrics.put("titleContractHitRate", agg.titleContractHitRate);
        metrics.put("titleTraceContractHitRate", agg.titleTraceContractHitRate);
        metrics.put("titleVerbContractHitRate", agg.titleVerbContractHitRate);
        metrics.put("typeAccuracy", agg.typeAccuracy);
        metrics.put("factsPrecision", agg.factsPrecision);
        metrics.put("factsInformativeRate", agg.factsInformativeRate);
        metrics.put("factsRecall", agg.factsRecall);
        metrics.put("factsSupportRate", agg.factsSupportRate);
        metrics.put("conceptsPrecision", agg.conceptsPrecision);
        metrics.put("conceptsRecall", agg.conceptsRecall);
        metrics.put("repeatConsistency", agg.repeatConsistency);
        metrics.put("p50Ms", agg.p50Ms);
        metrics.put("p95Ms", agg.p95Ms);
        metrics.put("maxMs", agg.maxMs);
        metrics.put("meanTokens", agg.meanTokens);
        metrics.put("p50Tokens", agg.p50Tokens);
        metrics.put("p95Tokens", agg.p95Tokens);
        metrics.put("maxTokens", agg.maxTokens);
        metrics.put("inconsistentSamples", agg.inconsistentSamples);
        metrics.put("variance", agg.variance);
        metrics.put("statusCounts", agg.statusCounts);
        return metrics;
    }

    private static String excerpt(String raw) {
        if (raw == null) {
            return "(no raw answer: the call failed before returning)";
        }
        String collapsed = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return collapsed.length() <= 400 ? collapsed : collapsed.substring(0, 400) + "... [truncated]";
    }

    private static void printSummary(GoldenSetScorer.Aggregate agg, int calls, long durationMs, Path path) {
        System.out.printf(Locale.ROOT,
                "%ncontract=%s skip=%s (TP/FP/FN/TN=%d/%d/%d/%d) formatErrors=%d "
                        + "titleExact=%s titleNorm=%s titleAction=%s titleContract=%s type=%s "
                        + "factsP/R=%s/%s actionable=%s support=%s "
                        + "conceptsP/R=%s/%s calls=%d duration=%dms consistency=%s%nreport=%s%n",
                pct(agg.contractPassRate), pct(agg.skipAccuracy),
                agg.skipTp, agg.skipFp, agg.skipFn, agg.skipTn, agg.formatErrors,
                pct(agg.titleExactHitRate), pct(agg.titleNormalizedHitRate),
                pct(agg.titleActionHitRate), pct(agg.titleContractHitRate), pct(agg.typeAccuracy),
                pct(agg.factsPrecision), pct(agg.factsRecall), pct(agg.factsInformativeRate),
                pct(agg.factsSupportRate),
                pct(agg.conceptsPrecision), pct(agg.conceptsRecall), calls, durationMs,
                pct(agg.repeatConsistency), path);
        if (!agg.problems.isEmpty()) {
            System.out.println("problems:");
            agg.problems.forEach(p -> System.out.println("  - " + p));
        }
    }

    private static void row(StringBuilder sb, String key, String value) {
        sb.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static void gate(StringBuilder sb, String id, String criterion, String threshold,
                              String observed, boolean met) {
        sb.append("| ").append(id).append(" | ").append(criterion).append(" | ").append(threshold)
                .append(" | ").append(observed).append(" | ").append(met ? "PASS" : "**FAIL**").append(" |\n");
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }
}
