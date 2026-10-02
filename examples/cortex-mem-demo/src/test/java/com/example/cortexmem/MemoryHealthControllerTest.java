package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.Experience;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round 183: GET /memory/health used the result of {@code retrieveExperiences} as
 * its liveness signal. That method degrades silently — on a backend outage it
 * logs and returns an empty list — so the endpoint answered 200 with
 * {@code status: "ok"} while every memory call was failing, verified live
 * against a demo pointed at a dead backend port. It now consults
 * {@code healthCheck()}, the one SDK method that reports failure.
 */
class MemoryHealthControllerTest {

    private final CortexMemClient client = mock(CortexMemClient.class);
    private final MemoryController controller = new MemoryController(
            mock(com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService.class),
            client,
            new DemoProperties());

    @Test
    void shouldReportUnavailableWhenBackendHealthCheckFails() {
        when(client.healthCheck()).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.getMemoryHealth("/some/project");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "unavailable");
    }

    @Test
    void shouldNotProbeRetrievalWhenBackendIsDown() {
        when(client.healthCheck()).thenReturn(false);

        controller.getMemoryHealth("/some/project");

        // The silent-degradation trap: retrieveExperiences cannot fail loudly, so
        // calling it here would have produced the "ok" this test is guarding against.
        verify(client, never()).retrieveExperiences(any(ExperienceRequest.class));
    }

    @Test
    void shouldReportOkWhenBackendIsHealthyAndRetrievalWorks() {
        when(client.healthCheck()).thenReturn(true);
        when(client.retrieveExperiences(any(ExperienceRequest.class)))
                // Experience is a record; this Mockito/JDK combination cannot mock
                // final types, so build a real one.
                .thenReturn(List.of(new Experience("id", "task", "strategy", "outcome",
                        "reuse", 0.9f, null)));

        ResponseEntity<Map<String, Object>> response = controller.getMemoryHealth("/some/project");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", "ok")
                .containsEntry("sample_retrieval", "working");
    }

    @Test
    void shouldReportOkButEmptyWhenBackendIsHealthyAndHasNoObservations() {
        when(client.healthCheck()).thenReturn(true);
        when(client.retrieveExperiences(any(ExperienceRequest.class))).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getMemoryHealth("/some/project");

        // An empty result from a reachable backend is a legitimate answer and must
        // stay distinct from the unreachable case above.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", "ok")
                .containsEntry("sample_retrieval", "empty");
    }
}
