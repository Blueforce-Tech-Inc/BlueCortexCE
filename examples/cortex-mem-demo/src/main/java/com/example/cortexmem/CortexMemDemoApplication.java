package com.example.cortexmem;

import com.ablueforce.cortexce.autoconfigure.EnableCortexMem;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo application showcasing cortex-mem-spring-integration.
 *
 * <p>Features enabled:
 * <ul>
 *   <li>@EnableCortexMem — auto-configures client, capture, retrieval, advisor</li>
 *   <li>Memory-augmented ChatClient via CortexMemoryAdvisor</li>
 *   <li>@Tool auto-capture (when CortexSessionContext is active)</li>
 *   <li>Actuator health check for Cortex CE backend</li>
 * </ul>
 */
@SpringBootApplication
@EnableCortexMem
public class CortexMemDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CortexMemDemoApplication.class, args);
    }
}
