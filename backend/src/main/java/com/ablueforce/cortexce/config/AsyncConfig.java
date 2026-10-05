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
 *   <li>A thread pool for async tasks, with a rejection handler that runs the task
 *       in the caller's thread when the queue is full</li>
 *   <li>Exception handling for void methods, via
 *       {@link AsyncUncaughtExceptionHandler}</li>
 * </ul>
 *
 * <p><b>There is no per-task timeout.</b> An earlier version of this comment listed
 * "Timeout handling for async methods" as a capability of this class; it never
 * existed. A search of the whole backend for {@code setTimeout},
 * {@code TimeoutInterceptor} and {@code Future.get} returns nothing, so an async
 * task runs to completion however long it takes. The one duration configured here
 * is {@code await-termination-seconds}, which bounds how long shutdown waits for
 * running tasks — not how long a task may run. Adding a real timeout means picking
 * a policy (cancel, or let the thread finish and discard the result) and is a
 * design decision, not a config toggle. See P2-67.
 *
 * <p>The four {@code claudemem.async.*} properties below are read with
 * {@code @Value} defaults, but {@code application.yml} defines no
 * {@code claudemem.async} block, so the defaults are always what applies unless a
 * deployment supplies the variables externally.
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    // P2: Thread pool configuration. NOTE: no `claudemem.async` block exists in
    // application.yml, so these @Value defaults are what actually applies.
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
            log.warn("Task rejected from async executor (queue full). Running in caller thread for backpressure. " +
                    "Consider increasing claudemem.async.max-pool-size or claudemem.async.queue-capacity");
            try {
                r.run();
            } catch (Exception ex) {
                log.error("Rejected task execution failed in caller thread", ex);
            }
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
