package com.ablueforce.cortexce.autoconfigure;

import com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor;
import com.ablueforce.cortexce.ai.aspect.CortexToolAspect;
import com.ablueforce.cortexce.ai.observation.DefaultObservationCaptureService;
import com.ablueforce.cortexce.ai.observation.ObservationCaptureService;
import com.ablueforce.cortexce.ai.retrieval.DefaultMemoryRetrievalService;
import com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService;
import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.CortexMemClientImpl;
import com.ablueforce.cortexce.client.config.CortexMemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Cortex CE memory integration.
 * <p>
 * Activated when {@code cortex.mem.base-url} is set in application properties.
 * Provides:
 * <ul>
 *   <li>{@link CortexMemClient} — REST client for the memory backend</li>
 *   <li>{@link ObservationCaptureService} — capture service</li>
 *   <li>{@link MemoryRetrievalService} — retrieval service</li>
 *   <li>{@link CortexMemoryAdvisor} — Spring AI advisor (when Spring AI is on classpath)</li>
 *   <li>{@link CortexToolAspect} — AOP aspect for @Tool auto-capture (when AOP is on classpath)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cortex.mem", name = "base-url")
public class CortexMemAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CortexMemAutoConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "cortex.mem")
    @ConditionalOnMissingBean
    public CortexMemProperties cortexMemProperties() {
        return new CortexMemProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public CortexMemClient cortexMemClient(CortexMemProperties properties) {
        log.info("Configuring CortexMemClient → {}", properties.getBaseUrl());
        return new CortexMemClientImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "cortex.mem", name = "capture-enabled", matchIfMissing = true)
    public ObservationCaptureService observationCaptureService(CortexMemClient client) {
        log.info("Enabling Cortex memory capture service");
        return new DefaultObservationCaptureService(client);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "cortex.mem", name = "retrieval-enabled", matchIfMissing = true)
    public MemoryRetrievalService memoryRetrievalService(CortexMemClient client,
                                                          CortexMemProperties properties) {
        log.info("Enabling Cortex memory retrieval service");
        return new DefaultMemoryRetrievalService(client, properties.getDefaultExperienceCount());
    }

    /**
     * Spring AI Advisor — only activated when Spring AI is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.ai.chat.client.ChatClient")
    @ConditionalOnProperty(prefix = "cortex.mem", name = "retrieval-enabled", matchIfMissing = true)
    static class SpringAiAdvisorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CortexMemoryAdvisor cortexMemoryAdvisor(CortexMemClient client,
                                                        CortexMemProperties properties) {
            log.info("Configuring CortexMemoryAdvisor for project: {}", properties.getProjectPath());
            return CortexMemoryAdvisor.builder(client)
                .projectPath(properties.getProjectPath() != null ? properties.getProjectPath() : "")
                .maxExperiences(properties.getDefaultExperienceCount())
                .build();
        }

        private static final Logger log = LoggerFactory.getLogger(SpringAiAdvisorConfiguration.class);
    }

    /**
     * Actuator health indicator — only when Actuator is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class HealthIndicatorConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "cortexMemHealthIndicator")
        public CortexMemHealthIndicator cortexMemHealthIndicator(CortexMemClient client) {
            return new CortexMemHealthIndicator(client);
        }
    }

    /**
     * AOP Aspect — only activated when Spring AOP is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "cortex.mem", name = "capture-enabled", matchIfMissing = true)
    static class AopCaptureConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CortexToolAspect cortexToolAspect(ObservationCaptureService captureService) {
            log.info("Enabling Cortex @Tool auto-capture AOP aspect");
            return new CortexToolAspect(captureService);
        }

        private static final Logger log = LoggerFactory.getLogger(AopCaptureConfiguration.class);
    }
}
