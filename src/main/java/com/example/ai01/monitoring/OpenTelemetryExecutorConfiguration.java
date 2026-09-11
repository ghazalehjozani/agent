package com.example.ai01.monitoring;

import io.opentelemetry.context.Context;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class OpenTelemetryExecutorConfiguration {

    @Bean(name = "auditExecutor", destroyMethod = "shutdown")
    public ExecutorService auditExecutor() {
        ExecutorService executor =
                Executors.newFixedThreadPool(3);

        return Context.taskWrapping(executor);
    }
    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        ExecutorService executor =
                Executors.newFixedThreadPool(3);

        return Context.taskWrapping(executor);
    }

    @Bean(name = "embeddingExecutor", destroyMethod = "shutdown")
    public ExecutorService embeddingExecutor() {
        ExecutorService executor =
                Executors.newFixedThreadPool(8);

        return Context.taskWrapping(executor);
    }

    @Bean(name = "matchingExecutor", destroyMethod = "shutdown")
    public ExecutorService matchingExecutor() {
        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        return Context.taskWrapping(executor);
    }

    @Bean(name = "ruleExtractionExecutor", destroyMethod = "shutdown")
    public ExecutorService ruleExtractionExecutor() {
        ForkJoinPool executor =
                new ForkJoinPool(3);

        return Context.taskWrapping(executor);
    }

}