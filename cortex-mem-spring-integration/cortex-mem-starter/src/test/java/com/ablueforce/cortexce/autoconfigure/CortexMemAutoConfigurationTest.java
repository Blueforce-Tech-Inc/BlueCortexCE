package com.ablueforce.cortexce.autoconfigure;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.config.CortexMemProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that CortexMemAutoConfiguration registers beans when properties are set.
 */
class CortexMemAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CortexMemAutoConfiguration.class));

    @Test
    void whenBaseUrlSet_registersCortexMemClient() {
        contextRunner
            .withPropertyValues("cortex.mem.base-url=http://localhost:37777")
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(CortexMemClient.class);
                assertThat(ctx).hasSingleBean(CortexMemProperties.class);
            });
    }

    @Test
    void whenBaseUrlNotSet_doesNotRegisterBeans() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(CortexMemClient.class);
        });
    }

    @Test
    void whenBaseUrlSet_registersCaptureAndRetrievalServices() {
        contextRunner
            .withPropertyValues(
                "cortex.mem.base-url=http://localhost:37777",
                "cortex.mem.project-path=/test"
            )
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(com.ablueforce.cortexce.ai.observation.ObservationCaptureService.class);
                assertThat(ctx).hasSingleBean(com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService.class);
            });
    }

    @Test
    void whenCaptureDisabled_doesNotRegisterCaptureService() {
        contextRunner
            .withPropertyValues(
                "cortex.mem.base-url=http://localhost:37777",
                "cortex.mem.capture-enabled=false"
            )
            .run(ctx -> {
                assertThat(ctx).doesNotHaveBean(com.ablueforce.cortexce.ai.aspect.CortexToolAspect.class);
            });
    }
}
