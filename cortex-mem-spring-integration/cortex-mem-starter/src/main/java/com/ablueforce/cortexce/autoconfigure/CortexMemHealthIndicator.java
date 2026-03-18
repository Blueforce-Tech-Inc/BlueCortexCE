package com.ablueforce.cortexce.autoconfigure;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator health indicator for the Cortex CE backend.
 * <p>
 * Reports UP when the backend's /actuator/health responds successfully.
 * Registered as "cortexMem" in the health endpoint.
 */
public class CortexMemHealthIndicator implements HealthIndicator {

    private final CortexMemClient client;

    public CortexMemHealthIndicator(CortexMemClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            if (client.healthCheck()) {
                return Health.up()
                    .withDetail("service", "Cortex CE Memory Backend")
                    .build();
            }
            return Health.down()
                .withDetail("service", "Cortex CE Memory Backend")
                .withDetail("reason", "Health check returned false")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("service", "Cortex CE Memory Backend")
                .withException(e)
                .build();
        }
    }
}
