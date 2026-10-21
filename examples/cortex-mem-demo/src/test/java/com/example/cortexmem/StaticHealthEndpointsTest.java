package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the demo's six <em>static</em> health endpoints — the ones that answer
 * {@code {"status":"ok"}} without ever asking the backend.
 *
 * <p>These are the trap this demo already hit once. Round 183 found that
 * {@code GET /memory/health} used {@code retrieveExperiences} — a client method
 * that <em>degrades silently</em> — as its liveness signal, so the endpoint
 * answered 200 with {@code status: "ok"} while every memory call was failing.
 * That one endpoint was fixed to consult {@code healthCheck()} and now answers
 * 503 when the backend is down.
 *
 * <p>The other six were left as they were, and they have the same shape: the
 * body is a constant, so the answer is {@code status: "ok"} whatever the
 * backend is doing. Measured live against a demo pointed at a dead backend
 * port, all six answered <em>200</em> with {@code status: "ok"}, byte for byte
 * the same as they do against a live backend — while {@code /memory/health} and
 * {@code /actuator/health} answered 503 in the same process. A liveness probe
 * pointed at one of these six reports the demo healthy precisely when the
 * memory system behind it is unreachable.
 *
 * <p>So the behaviour is pinned here deliberately, in the shape it actually
 * has: constant 200, constant {@code status: "ok"}, and <em>zero</em> calls to
 * the backend client. If a future change makes one of them consult the
 * backend, this test turns red — which is the point. It forces the change to
 * be made on purpose and, with it, the README's description of which health
 * endpoint is the real one.
 */
class StaticHealthEndpointsTest {

    private final CortexMemClient client = mock(CortexMemClient.class);

    private void assertStaticHealth(Supplier<ResponseEntity<Map<String, Object>>> call, String endpoint) {
        ResponseEntity<Map<String, Object>> response = call.get();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ok", response.getBody().get("status"));
        assertEquals(endpoint, response.getBody().get("endpoint"));
        // The whole point: the answer does not come from the backend.
        verifyNoInteractions(client);
    }

    @Test
    void shouldAnswerExperiencesHealthStatically() {
        assertStaticHealth(() -> new ExperiencesController(client).health(), "experiences");
    }

    @Test
    void shouldAnswerExtractionHealthStatically() {
        assertStaticHealth(() -> new ExtractionController(client).health(), "extraction");
    }

    @Test
    void shouldAnswerFeedbackHealthStatically() {
        assertStaticHealth(() -> new FeedbackController(client).health(), "feedback");
    }

    @Test
    void shouldAnswerManagementHealthStatically() {
        assertStaticHealth(() -> new ManagementController(client).health(), "manage");
    }

    @Test
    void shouldAnswerObservationsStatusStatically() {
        assertStaticHealth(() -> new ObservationsController(client).health(), "observations");
    }

    @Test
    void shouldAnswerSearchHealthStatically() {
        assertStaticHealth(() -> new SearchController(client).health(), "search");
    }

    @Test
    void shouldStillReachTheBackendFromTheRealHealthEndpoint() {
        // The positive control for verifyNoInteractions above: the same mock
        // DOES register a call when the endpoint really consults the backend,
        // so "no interactions" above means "did not ask", not "mock was inert".
        when(client.healthCheck()).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = new MemoryController(
                mock(com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService.class),
                client,
                new DemoProperties()).getMemoryHealth("/some/project");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("unavailable", response.getBody().get("status"));
        verify(client).healthCheck();
    }
}
