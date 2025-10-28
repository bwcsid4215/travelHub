package com.bwc.approval_workflow_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TasklistWebClientConfig {

    @Value("${services.tasklist.url:http://localhost:8082}")
    private String tasklistUrl;

    @Bean
    public WebClient tasklistWebClient() {
        return WebClient.builder()
                .baseUrl(tasklistUrl)
                .build();
    }
}
