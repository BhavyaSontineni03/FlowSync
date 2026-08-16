package com.expensemanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executors for non-critical side effects. The shared {@code taskExecutor}
 * is best-effort (activity logging, OCR kickoffs). Submit notifications use a
 * dedicated pool so cross-tenant approval alerts are not silently discarded
 * under load (DiscardOldestPolicy on the shared pool).
 *
 * <p>Neither pool uses AbortPolicy on the HTTP request path: CallerRunsPolicy
 * on the submit pool may slow the after-commit caller under saturation but
 * never surfaces as HTTP 400.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("async-");
        // Best-effort work only: drop oldest rather than AbortPolicy (HTTP 400).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated pool for {@code ExpenseSubmissionEventListener}: manager notify,
     * activity log, websocket. CallerRunsPolicy keeps security-relevant
     * notifications from being silently dropped when the queue is full.
     */
    @Bean(name = "submissionSideEffectsExecutor")
    public Executor submissionSideEffectsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("submit-fx-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
