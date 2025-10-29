package com.bwc.approval_workflow_service.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class TasklistDebugController {
    
    private final WebClient tasklistClient;
    private final ObjectMapper objectMapper;
    
    @GetMapping("/tasklist/connectivity")
    public Map<String, Object> testTasklistConnectivity() {
        try {
            String graphqlQuery = "query { tasks(query: { pageSize: 5 }) { id name taskState assignee candidateGroup } }";
            
            JsonNode response = tasklistClient.post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("query", graphqlQuery))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            
            return Map.of(
                "status", response != null ? "SUCCESS" : "FAILED",
                "response", response != null ? response : "No response",
                "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    
    
}