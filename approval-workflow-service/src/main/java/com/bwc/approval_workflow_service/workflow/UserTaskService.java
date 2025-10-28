package com.bwc.approval_workflow_service.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTaskService {

    private final WebClient tasklistClient;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> getUserTasks(String processInstanceId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("processInstanceId", processInstanceId);
            body.put("state", "CREATED");
            body.put("pageSize", DEFAULT_PAGE_SIZE);

            JsonNode resp = tasklistClient.post()
                    .uri("/v1/tasks/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            if (resp == null || !resp.has("items")) {
                log.info("📋 No tasks found for processInstanceId {}", processInstanceId);
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = objectMapper.convertValue(
                    resp.get("items"),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            log.info("📋 Found {} tasks for process instance {}", items.size(), processInstanceId);
            return items;

        } catch (Exception e) {
            log.error("❌ Failed to fetch tasks from Tasklist REST: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user tasks", e);
        }
    }

    public void completeTask(String taskId, Map<String, Object> variables) {
        try {
            Map<String, Object> body = Map.of("variables", variables);

            tasklistClient.post()
                    .uri("/v1/tasks/{id}/complete", taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            log.info("✅ Completed task {} with variables: {}", taskId, variables);
        } catch (Exception e) {
            log.error("❌ Failed to complete task {} via Tasklist REST: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Failed to complete task: " + taskId, e);
        }
    }
}
