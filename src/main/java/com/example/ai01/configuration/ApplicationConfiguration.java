package com.example.ai01.configuration;

import com.example.ai01.agent.configuration.AgentConfiguration;
import com.example.ai01.monitoring.OpenTelemetryExecutorConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Configuration
@EnableConfigurationProperties
@Import({AgentConfiguration.class , OpenTelemetryExecutorConfiguration.class})
public class
ApplicationConfiguration {
}