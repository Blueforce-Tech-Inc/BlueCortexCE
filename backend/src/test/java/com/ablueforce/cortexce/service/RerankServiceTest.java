package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rerank switch is default-off and must be safe to enable: on any provider
 * failure the caller keeps the fused order, and a successful call reorders only
 * the configured prefix, never dropping or duplicating a row.
 */
class RerankServiceTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Serve a fixed chat-completions body, or sleep first when {@code delayMillis > 0}. */
    private String startServer(String content, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String body = "{\"choices\":[{\"message\":{\"content\":" + quote(content) + "}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<ObservationEntity> rows(int count) {
        List<ObservationEntity> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ObservationEntity observation = new ObservationEntity();
            observation.setId(UUID.randomUUID());
            observation.setTitle("row-" + (i + 1));
            observation.setContent("content-" + (i + 1));
            data.add(observation);
        }
        return data;
    }

    @Test
    void reordersOnlyTheConfiguredPrefix() throws IOException {
        String baseUrl = startServer("[3,1,2]", 0);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "key", "test-model", true, 3, 2000, false);
        List<ObservationEntity> fused = rows(5);

        List<ObservationEntity> reranked = service.rerank("query", fused);

        assertThat(reranked).hasSize(5);
        assertThat(reranked.subList(0, 3)).extracting(ObservationEntity::getTitle)
            .containsExactly("row-3", "row-1", "row-2");
        assertThat(reranked.subList(3, 5)).extracting(ObservationEntity::getTitle)
            .containsExactly("row-4", "row-5");
        assertThat(calls).hasValue(1);
    }

    @Test
    void keepsFusedOrderWhenProviderTimesOut() throws IOException {
        String baseUrl = startServer("[3,1,2]", 500);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "key", "test-model", true, 3, 200, false);
        List<ObservationEntity> fused = rows(3);

        List<ObservationEntity> reranked = service.rerank("query", fused);

        assertThat(reranked).isSameAs(fused);
    }

    @Test
    void keepsFusedOrderWhenResponseIsNotARanking() throws IOException {
        String baseUrl = startServer("I cannot rank these.", 0);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "key", "test-model", true, 3, 2000, false);
        List<ObservationEntity> fused = rows(3);

        assertThat(service.rerank("query", fused)).isSameAs(fused);
    }

    @Test
    void disabledSwitchNeverCallsTheProvider() throws IOException {
        String baseUrl = startServer("[3,1,2]", 0);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "key", "test-model", false, 3, 2000, false);

        assertThat(service.rerank("query", rows(3))).hasSize(3);
        assertThat(calls).hasValue(0);
    }

    @Test
    void missingApiKeyNeverCallsTheProvider() throws IOException {
        String baseUrl = startServer("[3,1,2]", 0);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "", "test-model", true, 3, 2000, false);

        assertThat(service.rerank("query", rows(3))).hasSize(3);
        assertThat(calls).hasValue(0);
    }

    @Test
    void parseOrderFillsDroppedCandidatesInFusedOrder() throws IOException {
        String baseUrl = startServer("[2,2]", 0);
        RerankService service = new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
            "key", "test-model", true, 3, 2000, false);
        List<ObservationEntity> fused = rows(3);

        List<ObservationEntity> reranked = service.rerank("query", fused);

        assertThat(reranked).extracting(ObservationEntity::getTitle)
            .containsExactly("row-2", "row-1", "row-3");
    }

    @Test
    void searchAppliesRerankToHybridResults() throws IOException {
        String baseUrl = startServer("[2,1]", 0);
        ObservationRepository repository = mock(ObservationRepository.class);

        List<ObservationEntity> stored = rows(2);
        UUID first = stored.get(0).getId();
        UUID second = stored.get(1).getId();
        when(repository.hybridSearchScored(anyString(), anyString(), anyString(), anyLong(),
            anyDouble(), anyDouble(), anyDouble(), anyInt()))
            .thenReturn(List.of(new Object[]{first, 0.9, 0.5}, new Object[]{second, 0.8, 0.0}));
        when(repository.findAllById(any()))
            .thenAnswer(invocation -> {
                Iterable<UUID> ids = invocation.getArgument(0);
                List<ObservationEntity> loaded = new ArrayList<>();
                for (UUID id : ids) {
                    stored.stream().filter(row -> row.getId().equals(id)).findFirst()
                        .ifPresent(loaded::add);
                }
                return loaded;
            });
        SearchService searchService = new SearchService(repository,
            new RerankService(java.net.http.HttpClient.newHttpClient(), baseUrl,
                "key", "test-model", true, 2, 2000, false));

        SearchService.SearchResult result = searchService.search(
            new SearchService.SearchRequest("/repo", "query", new float[]{0.1f, 0.2f},
                null, null, null, null, null, 2, 0, null));

        assertThat(result.strategy()).isEqualTo("hybrid");
        assertThat(result.observations()).extracting(ObservationEntity::getTitle)
            .containsExactly("row-2", "row-1");
    }
}
