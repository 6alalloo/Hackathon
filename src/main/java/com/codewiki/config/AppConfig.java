package com.codewiki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppConfig {

    @Value("${huggingface.api.url}")
    private String huggingFaceApiUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(huggingFaceApiUrl)
                .build();
    }
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wiki-gen-");
        executor.initialize();
        return executor;
    }
}
