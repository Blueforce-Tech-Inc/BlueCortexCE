package com.ablueforce.cortexce.autoconfigure;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CortexMemHealthIndicatorTest {

    @Mock
    private CortexMemClient client;

    @Test
    void health_whenClientReturnsTrue_returnsUp() {
        when(client.healthCheck()).thenReturn(true);
        var indicator = new CortexMemHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "Cortex CE Memory Backend");
    }

    @Test
    void health_whenClientReturnsFalse_returnsDown() {
        when(client.healthCheck()).thenReturn(false);
        var indicator = new CortexMemHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "Health check returned false");
    }

    @Test
    void health_whenClientThrows_returnsDown() {
        when(client.healthCheck()).thenThrow(new RuntimeException("connection refused"));
        var indicator = new CortexMemHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
