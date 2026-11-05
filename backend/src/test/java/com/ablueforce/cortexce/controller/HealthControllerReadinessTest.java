package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.OperationalStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Readiness must make a failing queue auditable, not only a big one.
 *
 * <p>The depth check alone cannot distinguish "slow" from "broken": rows can sit in
 * {@code failed} forever and the embedding backlog can grow silently. These fields are
 * additive; the pre-existing names ({@code queueDepth}, {@code queueStatus}) must not
 * change because the WebUI reads them.
 */
class HealthControllerReadinessTest {

    @Test
    @DisplayName("readiness exposes depth, failed counts, recent failures and embedding backlog")
    void readinessExposesQueueHealth() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        AgentService agentService = mock(AgentService.class);
        when(agentService.getQueueDepth()).thenReturn(3L);
        OperationalStatusService status = mock(OperationalStatusService.class);
        when(status.queueStatus())
            .thenReturn(new OperationalStatusService.QueueStatus(5L, 2L, 4L));

        HealthController controller = new HealthController(dataSource, agentService, status);
        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "ready");
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) response.getBody().get("checks");
        assertThat(checks).containsEntry("database", "ready");
        assertThat(checks).containsEntry("queueDepth", 3L);
        assertThat(checks).containsEntry("queueStatus", "ready");
        assertThat(checks).containsEntry("queueFailed", 5L);
        assertThat(checks).containsEntry("queueFailedLast24h", 2L);
        assertThat(checks).containsEntry("embeddingBacklog", 4L);
    }

    @Test
    @DisplayName("an unreachable database still yields 503 and the additive metrics")
    void unreachableDatabaseYields503() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        AgentService agentService = mock(AgentService.class);
        when(agentService.getQueueDepth()).thenReturn(0L);
        OperationalStatusService status = mock(OperationalStatusService.class);
        when(status.queueStatus())
            .thenReturn(new OperationalStatusService.QueueStatus(0L, 0L, 0L));

        ResponseEntity<Map<String, Object>> response =
            new HealthController(dataSource, agentService, status).readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("status", "not_ready");
    }
}
