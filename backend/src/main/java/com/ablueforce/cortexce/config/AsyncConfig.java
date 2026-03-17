package com.ablueforce.cortexce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * P1: Async configuration for @Async methods.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Configurable thread pool for async tasks</li>
 *   <li>Timeout handling for async methods</li>
 *   <li>Exception handling for void methods</li>
 * </ul>
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    // P2: Thread pool configuration - values from application.yml with defaults
    @Value("${claudemem.async.core-pool-size:10}")
    private int corePoolSize;

    @Value("${claudemem.async.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${claudemem.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${claudemem.async.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("claude-mem-async-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Task rejected from async executor, queue is full. Consider increasing claudemem.async.max-pool-size or claudemem.async.queue-capacity");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * P1: Custom exception handler for async void methods.
     */
    private static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async method '{}' threw uncaught exception", method.getName(), ex);
            // P1: Alert on critical async failures
            if (isCriticalMethod(method.getName())) {
                log.error("Critical async method failed: {}", method.getName());
            }
        }

        private boolean isCriticalMethod(String methodName) {
            return "processToolUseAsync".equals(methodName) ||
                   "completeSessionAsync".equals(methodName);
        }
    }
}
