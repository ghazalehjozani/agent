package com.example.ai01.configuration;


import com.example.ai01.agent.AgentConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties
@Import({AgentConfiguration.class})
public class ApplicationConfiguration {
    @Bean
    public ExecutorService auditExecutorService() {
        return Executors.newCachedThreadPool();
    }

}