package com.ablueforce.cortexce.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables Cortex CE memory integration for the Spring Boot application.
 * <p>
 * When placed on a {@code @Configuration} or {@code @SpringBootApplication} class,
 * it auto-configures the memory client, capture service, retrieval service,
 * and optionally the Spring AI advisor.
 * <p>
 * Example:
 * <pre>{@code
 * @SpringBootApplication
 * @EnableCortexMem
 * public class MyAiApp {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyAiApp.class, args);
 *     }
 * }
 * }</pre>
 *
 * @see CortexMemAutoConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CortexMemAutoConfiguration.class)
public @interface EnableCortexMem {

    /**
     * Enable automatic capture of @Tool executions. Default true.
     */
    boolean captureEnabled() default true;

    /**
     * Enable automatic memory-enhanced retrieval (Advisor). Default true.
     */
    boolean retrievalEnabled() default true;
}
