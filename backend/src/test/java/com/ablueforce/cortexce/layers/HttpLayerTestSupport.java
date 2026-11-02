package com.ablueforce.cortexce.layers;

import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Shared plumbing for the L2/L3/L4 HTTP layers.
 *
 * <p>Every layer starts the <em>real</em> application on a random port against the
 * dedicated rehearsal database and talks to it over HTTP. The only replacement is
 * the two model beans ({@code OfflineAiTestModels}), so no API key or outbound
 * network is needed and every assertion is deterministic.
 *
 * <p>Waiting is condition-based: {@link #awaitUntil} polls a predicate until it
 * becomes true or the deadline expires. There are no fixed sleeps that could hide
 * a timing-dependent flake.
 */
abstract class HttpLayerTestSupport {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired protected TestRestTemplate rest;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObservationRepository observationRepository;
    @Autowired protected SummaryRepository summaryRepository;
    @Autowired protected PendingMessageRepository pendingMessageRepository;
    @Autowired protected SessionRepository sessionRepository;
    @Autowired protected UserPromptRepository userPromptRepository;
    @LocalServerPort protected int port;

    @BeforeEach
    void configureHttpClient() {
        // SimpleClientHttpRequestFactory sits on HttpURLConnection, which rejects
        // PATCH. The JDK HttpClient factory supports every verb the API publishes.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        rest.getRestTemplate().setRequestFactory(factory);
    }

    protected static String uniqueProject(String layer) {
        return "/tmp/cortexce-" + layer + "-" + UUID.randomUUID();
    }

    protected static String uniqueSession(String layer) {
        return layer + "-" + UUID.randomUUID();
    }

    protected ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, MediaType.APPLICATION_JSON);
    }

    protected ResponseEntity<String> getPlain(String path) {
        return exchange(HttpMethod.GET, path, null, MediaType.TEXT_PLAIN);
    }

    protected ResponseEntity<String> post(String path, String jsonBody) {
        return exchange(HttpMethod.POST, path, jsonBody, MediaType.APPLICATION_JSON);
    }

    protected ResponseEntity<String> patch(String path, String jsonBody) {
        return exchange(HttpMethod.PATCH, path, jsonBody, MediaType.APPLICATION_JSON);
    }

    protected ResponseEntity<String> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, MediaType.APPLICATION_JSON);
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, String jsonBody,
                                              MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return rest.exchange(path, method, entity, String.class);
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("response is not JSON: " + response.getBody(), e);
        }
    }

    protected static String json(Map<String, ?> body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Serialises any request body shape, including arrays used by the import routes. */
    protected static String json(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Polls {@code condition} every 20 ms until it is true. Fails with the supplied
     * message when the deadline passes, so a stuck worker is reported as a real
     * failure instead of a silent timeout.
     */
    protected static void awaitUntil(String what, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for " + what, e);
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("timed out waiting for " + what);
        }
    }

    protected static <T> T awaitValue(String what, Duration timeout, Supplier<T> supplier) {
        T[] holder = (T[]) new Object[1];
        awaitUntil(what, timeout, () -> {
            holder[0] = supplier.get();
            return holder[0] != null;
        });
        return holder[0];
    }

    /**
     * Collects Server-Sent Events from {@code GET /stream} on a background reader.
     * The queue makes {@link #awaitData} condition-based, and {@link #close()}
     * terminates the otherwise infinite response.
     */
    protected final class SseCollector implements AutoCloseable {

        private final BlockingQueue<String> dataLines = new LinkedBlockingQueue<>();
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private InputStream stream;
        private Future<?> reader;

        void start() throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/stream"))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new AssertionError("GET /stream answered " + response.statusCode());
            }
            if (!response.headers().firstValue("Content-Type").orElse("")
                    .contains("text/event-stream")) {
                throw new AssertionError("GET /stream is not an SSE response: "
                        + response.headers().firstValue("Content-Type").orElse("missing"));
            }
            stream = response.body();
            reader = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "sse-reader");
                thread.setDaemon(true);
                return thread;
            }).submit(() -> {
                try (BufferedReader buffered = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = buffered.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            dataLines.offer(line.substring("data:".length()).trim());
                        }
                    }
                } catch (Exception ignored) {
                    // closed by the test
                }
            });
        }

        /** Waits for a JSON payload whose {@code type} equals {@code type}. */
        JsonNode awaitType(String type, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String data = dataLines.poll(20, TimeUnit.MILLISECONDS);
                if (data == null) {
                    continue;
                }
                JsonNode node = JSON.readTree(data);
                if (type.equals(node.path("type").asText())) {
                    return node;
                }
            }
            throw new AssertionError("no SSE event with type=" + type + " within " + timeout);
        }

        JsonNode awaitTypeContaining(String type, String needle, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String data = dataLines.poll(20, TimeUnit.MILLISECONDS);
                if (data == null) {
                    continue;
                }
                JsonNode node = JSON.readTree(data);
                if (type.equals(node.path("type").asText())
                        && data.contains(needle)) {
                    return node;
                }
            }
            throw new AssertionError("no SSE event with type=" + type
                    + " containing " + needle + " within " + timeout);
        }

        @Override
        public void close() {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
                // closing an already-terminated stream is fine
            }
            if (reader != null) {
                reader.cancel(true);
            }
        }
    }
}
