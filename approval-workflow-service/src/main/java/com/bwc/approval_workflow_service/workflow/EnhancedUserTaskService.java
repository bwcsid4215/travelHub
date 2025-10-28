package com.bwc.approval_workflow_service.workflow;

import com.bwc.approval_workflow_service.client.EmployeeServiceClient;
import com.bwc.approval_workflow_service.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedUserTaskService {

    private final WebClient tasklistClient;
    private final EmployeeServiceClient employeeServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_PAGE_SIZE = 100;

    public UserTasksResponse getUserTasksWithHistory(String employeeId, String role) {
        try {
            // Get user info
            EmployeeProxyDTO employee = employeeServiceClient.getEmployee(UUID.fromString(employeeId));
            
            // Create UserInfo - using simple constructor
            UserInfo userInfo = new UserInfo();
            userInfo.setEmployeeId(employeeId);
            userInfo.setFullName(employee.getFullName());
            userInfo.setDepartment(employee.getDepartment());
            userInfo.setRole(role);

            // Get pending tasks
            List<TaskDTO> pendingTasks = getPendingTasks(employeeId, role);
            
            // Get completed tasks (history)
            List<TaskDTO> completedTasks = getCompletedTasks(employeeId, role);

            // Create response with our custom DTOs
            UserTasksResponse response = new UserTasksResponse();
            response.setPendingTasks(pendingTasks);
            response.setCompletedTasks(completedTasks);
            response.setUserInfo(userInfo);

            return response;

        } catch (Exception e) {
            log.error("❌ Failed to fetch tasks for employee {}: {}", employeeId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user tasks", e);
        }
    }

    private List<TaskDTO> getPendingTasks(String employeeId, String role) {
        Map<String, Object> query = buildTaskQuery(employeeId, role, "CREATED");
        return searchTasks(query);
    }

    private List<TaskDTO> getCompletedTasks(String employeeId, String role) {
        Map<String, Object> query = buildTaskQuery(employeeId, role, "COMPLETED");
        return searchTasks(query);
    }

    private Map<String, Object> buildTaskQuery(String employeeId, String role, String state) {
        Map<String, Object> query = new HashMap<>();
        query.put("state", state);
        query.put("pageSize", DEFAULT_PAGE_SIZE);

        // Role-based task assignment
        switch (role.toUpperCase()) {
            case "MANAGER":
                // Manager sees tasks assigned to them specifically
                query.put("assignee", employeeId);
                break;
            case "FINANCE":
                query.put("candidateGroups", Arrays.asList("finance-approvers"));
                break;
            case "HR":
                query.put("candidateGroups", Arrays.asList("hr-compliance-team"));
                break;
            case "TRAVEL_DESK":
                query.put("candidateGroups", Arrays.asList("travel-desk-team"));
                break;
            default:
                // Regular employees see tasks assigned to them
                query.put("assignee", employeeId);
        }

        return query;
    }

    private List<TaskDTO> searchTasks(Map<String, Object> queryParams) {
        try {
            JsonNode resp = tasklistClient.post()
                    .uri("/v1/tasks/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(queryParams)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            if (resp == null || !resp.has("items")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = objectMapper.convertValue(
                    resp.get("items"),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            return items.stream()
                    .map(this::mapToTaskDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Failed to search tasks: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private TaskDTO mapToTaskDTO(Map<String, Object> taskData) {
        TaskDTO taskDTO = new TaskDTO();
        taskDTO.setTaskId((String) taskData.get("id"));
        taskDTO.setTaskName((String) taskData.get("name"));
        taskDTO.setProcessInstanceId((String) taskData.get("processInstanceId"));
        taskDTO.setProcessDefinitionId((String) taskData.get("processDefinitionId"));
        taskDTO.setCreatedDate(parseDateTime((String) taskData.get("creationDate")));
        taskDTO.setDueDate(parseDateTime((String) taskData.get("dueDate")));
        taskDTO.setAssignee((String) taskData.get("assignee"));
        taskDTO.setCandidateGroup((String) taskData.get("candidateGroup"));
        taskDTO.setVariables(extractVariables(taskData));
        taskDTO.setStatus("CREATED".equals(taskData.get("state")) ? "PENDING" : "COMPLETED");
        taskDTO.setOutcome(extractOutcome(taskData));
        
        return taskDTO;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return null;
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractVariables(Map<String, Object> taskData) {
        try {
            if (taskData.containsKey("variables")) {
                return objectMapper.convertValue(taskData.get("variables"), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to extract variables from task: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    private String extractOutcome(Map<String, Object> taskData) {
        Map<String, Object> variables = extractVariables(taskData);
        if (variables.containsKey("managerApproved")) {
            return Boolean.TRUE.equals(variables.get("managerApproved")) ? "APPROVED" : "REJECTED";
        }
        if (variables.containsKey("financeApproved")) {
            return Boolean.TRUE.equals(variables.get("financeApproved")) ? "APPROVED" : "REJECTED";
        }
        if (variables.containsKey("hrCompliant")) {
            return Boolean.TRUE.equals(variables.get("hrCompliant")) ? "COMPLIANT" : "NON_COMPLIANT";
        }
        return "UNKNOWN";
    }

    public void completeTaskWithAction(String taskId, TaskActionRequest actionRequest) {
        try {
            Map<String, Object> variables = new HashMap<>();
            
            // Add action-specific variables
            if (actionRequest.getApproved() != null) {
                variables.put("approved", actionRequest.getApproved());
            }
            if (actionRequest.getComments() != null) {
                variables.put("comments", actionRequest.getComments());
            }
            
            // Add any additional variables
            if (actionRequest.getAdditionalVariables() != null) {
                variables.putAll(actionRequest.getAdditionalVariables());
            }

            completeTask(taskId, variables);

        } catch (Exception e) {
            log.error("❌ Failed to complete task {} with action: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Failed to complete task: " + taskId, e);
        }
    }

    public void completeTask(String taskId, Map<String, Object> variables) {
        try {
            Map<String, Object> body = Collections.singletonMap("variables", variables);

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

    // Get manager for an employee dynamically
    public String getManagerForEmployee(String employeeId) {
        try {
            EmployeeProxyDTO employee = employeeServiceClient.getEmployee(UUID.fromString(employeeId));
            return employee.getManagerId() != null ? employee.getManagerId().toString() : null;
        } catch (Exception e) {
            log.error("❌ Failed to get manager for employee {}: {}", employeeId, e.getMessage());
            return null;
        }
    }
    
    
    
}