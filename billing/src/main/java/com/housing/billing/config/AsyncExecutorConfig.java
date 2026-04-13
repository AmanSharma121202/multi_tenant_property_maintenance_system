package com.housing.billing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncExecutorConfig {

    @Bean(name = "invoiceGenerationExecutor")
    public Executor invoiceGenerationExecutor(
            @Value("${app.async.invoice-generation.core-pool-size:4}") int corePoolSize,
            @Value("${app.async.invoice-generation.max-pool-size:8}") int maxPoolSize,
            @Value("${app.async.invoice-generation.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("invoice-gen-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }
}

