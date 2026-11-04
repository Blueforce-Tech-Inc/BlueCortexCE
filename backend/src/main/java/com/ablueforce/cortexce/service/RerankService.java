package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional model rerank of the first few hybrid results.
 *
 * <p>Measured on 90 real queries (see {@code docs/archive/2026-11-04_rerank-real.md}):
 * reordering the production top five with LongCat and thinking disabled lifted hit@1 from
 * 0.833 to 0.867 (net +3 queries against a ±2 noise floor) at a sequential p95 of 3.7 s.
 * Reordering the top twenty with reasoning enabled bought one more query of hit@5 and
 * +0.010 MRR, but cost 33.5 s p95 and returned empty rankings on 2.2% of calls. The switch
 * therefore defaults off and reranks only {@code top-n}.
 *
 * <p>Every failure path — disabled, blank key, timeout, transport error, non-2xx, a body
 * without a parseable ranking — returns the caller's list unchanged, so enabling this can
 * only add latency, never lose a result.
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[[^\\[\\]]*]", Pattern.DOTALL);
    private static final int CANDIDATE_CHARS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final int topN;
    private final long timeoutMillis;
    private final boolean thinking;

    @Autowired
    public RerankService(
        @Value("${cortexce.search.rerank.enabled:false}") boolean enabled,
        @Value("${cortexce.search.rerank.top-n:5}") int topN,
        @Value("${cortexce.search.rerank.timeout-ms:8000}") long timeoutMillis,
        @Value("${cortexce.search.rerank.thinking:false}") boolean thinking,
        @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
        @Value("${spring.ai.openai.api-key:}") String apiKey,
        @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
            baseUrl, apiKey, model, enabled, topN, timeoutMillis, thinking);
    }

    RerankService(HttpClient client, String baseUrl, String apiKey, String model,
                  boolean enabled, int topN, long timeoutMillis, boolean thinking) {
        this.client = client;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.enabled = enabled;
        this.topN = topN;
        this.timeoutMillis = timeoutMillis;
        this.thinking = thinking;
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isBlank() && topN > 0;
    }

    /**
     * Reorder up to {@code top-n} leading results, keeping the tail untouched.
     * Returns the input list instance when disabled or on any failure.
     */
    public List<ObservationEntity> rerank(String query, List<ObservationEntity> results) {
        if (!isEnabled() || query == null || query.isBlank() || results.size() < 2) {
            return results;
        }
        int size = Math.min(topN, results.size());
        List<ObservationEntity> head = new ArrayList<>(results.subList(0, size));
        List<Integer> order = requestOrder(query, head);
        if (order == null) {
            return results;
        }
        List<ObservationEntity> merged = new ArrayList<>(results.size());
        for (int index : order) {
            merged.add(head.get(index - 1));
        }
        merged.addAll(results.subList(size, results.size()));
        return merged;
    }

    /**
     * Ask the provider for a ranking of {@code head}. Returns 1-based indices covering every
     * candidate exactly once, or null when the call failed.
     */
    private List<Integer> requestOrder(String query, List<ObservationEntity> head) {
        long started = System.nanoTime();
        try {
            String body = MAPPER.writeValueAsString(requestBody(query, head));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            if (response.statusCode() != 200) {
                log.warn("Rerank call failed with HTTP {} after {} ms; keeping fused order",
                    response.statusCode(), elapsedMillis);
                return null;
            }
            List<Integer> order = parseOrder(response.body(), head.size());
            if (order == null) {
                log.warn("Rerank response had no usable ranking after {} ms; keeping fused order",
                    elapsedMillis);
                return null;
            }
            log.debug("Rerank reordered {} rows in {} ms", head.size(), elapsedMillis);
            return order;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Rerank call failed ({}); keeping fused order", e.getClass().getSimpleName());
            return null;
        }
    }

    private Object requestBody(String query, List<ObservationEntity> head) {
        List<Object> messages = List.of(
            message("system", "你是记忆检索重排器。给定一条历史查询和候选记忆，按它们能帮助回答该查询的程度"
                + "从高到低排序。只输出一个 JSON 整数数组，按从高到低的候选编号排列，覆盖每个候选一次。"
                + "不要输出解释、标点或其它任何文字。"),
            message("user", "查询：\n" + query + "\n\n候选记忆：\n" + candidateLines(head)));
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("max_tokens", 600);
        if (!thinking) {
            body.put("chat_template_kwargs", java.util.Map.of("enable_thinking", false));
        }
        return body;
    }

    private static java.util.Map<String, Object> message(String role, String content) {
        return java.util.Map.of("role", role, "content", content);
    }

    private static String candidateLines(List<ObservationEntity> head) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < head.size(); i++) {
            ObservationEntity observation = head.get(i);
            String text = (observation.getTitle() == null ? "" : observation.getTitle()) + "\n"
                + (observation.getContent() == null ? "" : observation.getContent());
            if (text.length() > CANDIDATE_CHARS) {
                text = text.substring(0, CANDIDATE_CHARS);
            }
            builder.append('[').append(i + 1).append("] ").append(text);
            if (i + 1 < head.size()) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    static List<Integer> parseOrder(String responseBody, int size) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            Matcher matcher = JSON_ARRAY.matcher(content);
            if (!matcher.find()) {
                return null;
            }
            JsonNode array = MAPPER.readTree(matcher.group());
            if (!array.isArray()) {
                return null;
            }
            List<Integer> order = new ArrayList<>(size);
            for (JsonNode node : array) {
                int index = node.asInt(-1);
                if (index >= 1 && index <= size && !order.contains(index)) {
                    order.add(index);
                }
            }
            if (order.isEmpty()) {
                return null;
            }
            // A model that drops a candidate still yields a usable ranking: the missing rows
            // keep their fused order at the tail so every input row is returned exactly once.
            for (int index = 1; index <= size; index++) {
                if (!order.contains(index)) {
                    order.add(index);
                }
            }
            return order;
        } catch (Exception e) {
            return null;
        }
    }
}
