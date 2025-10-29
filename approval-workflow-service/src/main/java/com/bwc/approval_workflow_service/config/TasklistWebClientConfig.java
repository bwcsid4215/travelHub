package com.bwc.approval_workflow_service.config;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TasklistWebClientConfig {

    @Value("${services.tasklist.url:http://localhost:7080}")
    private String tasklistUrl;
    
    @Value("${services.tasklist.username:}")
    private String username;
    
    @Value("${services.tasklist.password:}")
    private String password;

    @Bean
    public WebClient tasklistWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(tasklistUrl);
        
        // Add basic auth if credentials provided
        if (!username.isBlank() && !password.isBlank()) {
            builder.defaultHeaders(headers -> {
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            });
        }
        
        return builder.build();
    }
}