package com.bwc.approval_workflow_service.workflow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.bwc.approval_workflow_service.client.EmployeeServiceClient;
import com.bwc.approval_workflow_service.dto.EmployeeProxyDTO;
import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.TaskDTO;
import com.bwc.approval_workflow_service.dto.UserInfo;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedUserTaskService {

    private final WebClient tasklistClient;
    private final EmployeeServiceClient employeeServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_PAGE_SIZE = 100;

    // === Public: Get all tasks ===
    public UserTasksResponse getUserTasksWithHistory(String employeeId, String role) {
        try {
            long startTime = System.currentTimeMillis();
            
            EmployeeProxyDTO employee = getCachedEmployee(employeeId);
            if (employee == null) {
                throw new RuntimeException("Employee not found: " + employeeId);
            }

            UserInfo userInfo = new UserInfo();
            userInfo.setEmployeeId(employeeId);
            userInfo.setFullName(employee.getFullName());
            userInfo.setDepartment(employee.getDepartment());
            userInfo.setRole(role);

            // Get tasks
            List<TaskDTO> pendingTasks = getPendingTasks(employeeId, role);
            List<TaskDTO> completedTasks = getCompletedTasks(employeeId, role);

            // Use optimized enrichment
            enrichTasksWithProcessVariablesOptimized(pendingTasks);
            enrichTasksWithProcessVariablesOptimized(completedTasks);

            UserTasksResponse response = new UserTasksResponse();
            response.setPendingTasks(pendingTasks);
            response.setCompletedTasks(completedTasks);
            response.setUserInfo(userInfo);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Fetched {} pending, {} completed tasks in {}ms", 
                    pendingTasks.size(), completedTasks.size(), duration);

            return response;

        } catch (Exception e) {
            log.error("❌ Failed to fetch tasks for employee {}: {}", employeeId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user tasks", e);
        }
    }

    private List<TaskDTO> getPendingTasks(String employeeId, String role) {
        return mergeAssigneeAndGroupTasks(employeeId, "managers", "CREATED");
    }

    private List<TaskDTO> mergeAssigneeAndGroupTasks(String employeeId, String group, String state) {
        List<TaskDTO> all = new ArrayList<>();

        // First, get tasks by assignee
        Map<String, Object> assigneeQuery = buildEnhancedTaskQuery(employeeId, null, state);
        List<TaskDTO> assigneeTasks = searchTasks(assigneeQuery);
        log.info("📋 Found {} direct tasks for assignee {}", assigneeTasks.size(), employeeId);
        all.addAll(assigneeTasks);

        // Then, get tasks by candidate group
        Map<String, Object> groupQuery = buildEnhancedTaskQuery(null, group, state);
        List<TaskDTO> groupTasks = searchTasks(groupQuery);
        log.info("📋 Found {} group tasks for {}", groupTasks.size(), group);
        all.addAll(groupTasks);

        // Remove duplicates
        return all.stream()
                .collect(Collectors.toMap(TaskDTO::getTaskId, t -> t, (a, b) -> a))
                .values().stream()
                .toList();
    }

 // ✅ FIXED: Correct request structure for Camunda 8.8 Tasklist API
    public Map<String, Object> buildEnhancedTaskQuery(String assignee, String candidateGroup, String state) {
        Map<String, Object> query = new HashMap<>();
        
        // ✅ FIXED: State goes at TOP LEVEL, not under filter
        query.put("state", state);
        query.put("pageSize", DEFAULT_PAGE_SIZE);
        
        // ✅ FIXED: Assignee and candidateGroup also go at top level
        if (assignee != null && !assignee.isBlank()) {
            query.put("assignee", assignee);
        }
        
        if (candidateGroup != null && !candidateGroup.isBlank()) {
            query.put("candidateGroup", candidateGroup);
        }
        
        // ✅ FIXED: Include variables at top level
     // ✅ FIXED: Include variables at top level
        List<Map<String, Object>> includeVariables = new ArrayList<>();
        includeVariables.add(Map.of("name", "employeeId", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "managerId", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "travelRequestId", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "requestedAmount", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "origin", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "destination", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "travelPurpose", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "projectId", "alwaysReturnFullValue", false));
        includeVariables.add(Map.of("name", "managerPresent", "alwaysReturnFullValue", false));

        // ✅ NEW: include manager approval variables
        includeVariables.add(Map.of("name", "approved", "alwaysReturnFullValue", true));
        includeVariables.add(Map.of("name", "comments", "alwaysReturnFullValue", true));
        includeVariables.add(Map.of("name", "approvalDate", "alwaysReturnFullValue", true));
        includeVariables.add(Map.of("name", "approvedBy", "alwaysReturnFullValue", true));
        includeVariables.add(Map.of("name", "remarks", "alwaysReturnFullValue", true));

        query.put("includeVariables", includeVariables);

        
        return query;
    }

    private List<TaskDTO> getCompletedTasks(String employeeId, String role) {
        // Get completed tasks by assignee
        Map<String, Object> assigneeQuery = buildEnhancedTaskQuery(employeeId, null, "COMPLETED");
        List<TaskDTO> assigneeCompleted = searchTasks(assigneeQuery);
        
        // Get completed tasks by candidate group
        Map<String, Object> groupQuery = buildEnhancedTaskQuery(null, "managers", "COMPLETED");
        List<TaskDTO> groupCompleted = searchTasks(groupQuery);
        
        List<TaskDTO> allCompleted = new ArrayList<>();
        allCompleted.addAll(assigneeCompleted);
        allCompleted.addAll(groupCompleted);
        
        log.info("📋 Found {} completed tasks", allCompleted.size());
        
        // ✅ REMOVED: No client-side filtering needed - API handles it now
        
        // Remove duplicates and sort by creation date (newest first)
        return allCompleted.stream()
                .collect(Collectors.toMap(TaskDTO::getTaskId, t -> t, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(TaskDTO::getCreatedDate, 
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

 // ✅ FIXED: Use queryParams directly as request body
    public List<TaskDTO> searchTasks(Map<String, Object> queryParams) {
        try {
            log.info("🔍 Task search query: {}", queryParams);

            // ✅ FIXED: Use queryParams directly as the request body
            Map<String, Object> requestBody = new HashMap<>(queryParams);
            
            // Ensure pageSize is set
            if (!requestBody.containsKey("pageSize")) {
                requestBody.put("pageSize", DEFAULT_PAGE_SIZE);
            }

            log.debug("📤 Sending Tasklist body: {}", requestBody);

            JsonNode resp = tasklistClient.post()
                    .uri("/v1/tasks/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)  // ✅ Send the corrected structure
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(error -> {
                                        log.error("❌ Tasklist API error: {}", error);
                                        return new RuntimeException("Tasklist API error: " + error);
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            // ... rest of your existing method remains the same

            if (resp == null || resp.isNull()) {
                log.warn("⚠️ Task search returned null or empty response");
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = new ArrayList<>();

            if (resp.isArray()) {
                items = objectMapper.convertValue(resp, new TypeReference<>() {});
                log.info("✅ Tasklist returned array with {} items", items.size());
            } else if (resp.has("items")) {
                items = objectMapper.convertValue(resp.get("items"), new TypeReference<>() {});
                log.info("✅ Tasklist returned object wrapper with {} items", items.size());
            } else {
                log.warn("⚠️ Tasklist returned unexpected JSON: {}", resp);
                return Collections.emptyList();
            }

            return items.stream().map(this::mapToTaskDTO).collect(Collectors.toList());

        } catch (WebClientResponseException e) {
            log.error("❌ Task search failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("❌ Task search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

 // ✅ FIXED: Correct state mapping with proper validation
    private TaskDTO mapToTaskDTO(Map<String, Object> data) {
        TaskDTO dto = new TaskDTO();
        
        // Basic task information
        dto.setTaskId(firstNonNullString(data.get("id"), data.get("taskId")));
        dto.setTaskName((String) data.get("name"));
        dto.setProcessInstanceId(firstNonNullString(data.get("processInstanceId"), data.get("processInstanceKey")));
        dto.setProcessDefinitionId(firstNonNullString(data.get("processDefinitionId"), data.get("processDefinitionKey")));
        
        dto.setCreatedDate(parseDateTimeSafe(firstNonNullString(data.get("creationDate"), data.get("createdDate"))));
        dto.setDueDate(parseDateTimeSafe((String) data.get("dueDate")));
        
        dto.setAssignee((String) data.get("assignee"));
        
        // ✅ FIXED: Extract candidate group from response
        if (data.containsKey("candidateGroups") && data.get("candidateGroups") instanceof List) {
            List<String> groups = (List<String>) data.get("candidateGroups");
            if (groups != null && !groups.isEmpty()) {
                dto.setCandidateGroup(groups.get(0)); // Take first candidate group
            }
        }
        
        // ✅ FIXED: Extract variables from the correct location
        Map<String, Object> taskVariables = extractVariablesFromTask(data);
        dto.setVariables(taskVariables);
        
        // ✅ FIXED: PROPER STATE MAPPING - This is the key fix!
        String taskState = (String) data.get("taskState");
        String normalizedState = taskState == null ? "UNKNOWN" : taskState.toUpperCase(Locale.ROOT);
        
        // Validate state against known values
        if (!Arrays.asList("CREATED", "COMPLETED", "CANCELED").contains(normalizedState)) {
            log.warn("⚠️ Unexpected task state '{}' for task {}, defaulting to CREATED", normalizedState, dto.getTaskId());
            normalizedState = "CREATED";
        }
        
        // Set both status and processState to the same value for consistency
        dto.setStatus(normalizedState);
        dto.setProcessState(normalizedState);
        
        // Log completion for debugging
        if ("COMPLETED".equals(normalizedState)) {
            String completionDate = firstNonNullString(data.get("completionDate"));
            log.debug("✅ Found COMPLETED task {} with completion date: {}", dto.getTaskId(), completionDate);
        }
        
        dto.setOutcome(extractOutcome(data));
        dto.setCurrentStep((String) data.get("name"));
        
        return dto;
    }

    // Helper method for flexible field mapping
    private static String firstNonNullString(Object... objs) {
        for (Object o : objs) {
            if (o != null) {
                String str = o.toString();
                if (!str.isBlank()) {
                    return str;
                }
            }
        }
        return null;
    }

    private LocalDateTime parseDateTimeSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String clean = value.strip();
            if (clean.startsWith("\"") && clean.endsWith("\"")) {
                clean = clean.substring(1, clean.length() - 1);
            }

            // Normalize offsets without colon: +0000 -> +00:00
            String normalized = clean.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");

            // Try parse as OffsetDateTime (preferred) with timezone conversion
            try {
                OffsetDateTime odt = OffsetDateTime.parse(normalized);
                return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {}

            // Fallback: try ISO_LOCAL
            try {
                return LocalDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException ignored) {}

            log.warn("Failed to parse date '{}' with any format", clean);
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse date '{}': {}", value, e.getMessage());
            return null;
        }
    }

    // ✅ FIXED: Improved variable extraction
    private Map<String, Object> extractVariablesFromTask(Map<String, Object> data) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            // ✅ FIXED: Extract from "variables" array in Tasklist API response
            if (data.containsKey("variables") && data.get("variables") != null) {
                Object raw = data.get("variables");
                vars.putAll(extractVariablesFromRaw(raw));
            }

        } catch (Exception e) {
            log.warn("Failed to extract vars from task: {}", e.getMessage());
        }
        return vars;
    }

    private Map<String, Object> extractVariablesFromRaw(Object raw) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            if (raw instanceof List<?> list) {
                for (Object obj : list) {
                    if (obj instanceof Map<?, ?> map) {
                        String name = Objects.toString(map.get("name"), "");
                        Object value = map.get("value");
                        value = cleanVariableValue(value);
                        if (!name.isEmpty()) vars.put(name, value);
                    }
                }
            } else if (raw instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object value = entry.getValue();
                    value = cleanVariableValue(value);
                    vars.put(entry.getKey().toString(), value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract from raw data: {}", e.getMessage());
        }
        return vars;
    }

    private Object cleanVariableValue(Object value) {
        if (value instanceof String stringValue) {
            // Remove surrounding quotes if present
            if (stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
                return stringValue.substring(1, stringValue.length() - 1);
            }
            // Handle JSON-encoded values
            if (stringValue.startsWith("\"{") && stringValue.endsWith("}\"")) {
                return stringValue.substring(1, stringValue.length() - 1);
            }
        }
        return value;
    }

    private String extractOutcome(Map<String, Object> data) {
        Map<String, Object> vars = extractVariablesFromTask(data);
        if (vars.containsKey("managerApproved"))
            return Boolean.TRUE.equals(vars.get("managerApproved")) ? "APPROVED" : "REJECTED";
        if (vars.containsKey("financeApproved"))
            return Boolean.TRUE.equals(vars.get("financeApproved")) ? "APPROVED" : "REJECTED";
        if (vars.containsKey("hrCompliant"))
            return Boolean.TRUE.equals(vars.get("hrCompliant")) ? "COMPLIANT" : "NON_COMPLIANT";
        return "UNKNOWN";
    }

    public Map<String, Object> fetchProcessVariables(String processInstanceId) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("processInstanceKey", processInstanceId);
            requestBody.put("pageSize", 100);
            
            JsonNode resp = tasklistClient.post()
                    .uri("/v1/variables/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(5));

            if (resp != null && resp.has("items")) {
                List<Map<String, Object>> items = objectMapper.convertValue(
                        resp.get("items"), new TypeReference<>() {});
                
                Map<String, Object> cleanedVariables = new HashMap<>();
                for (Map<String, Object> item : items) {
                    String name = (String) item.get("name");
                    Object value = item.get("value");
                    value = cleanVariableValue(value);
                    if (name != null && !name.isBlank()) {
                        cleanedVariables.put(name, value);
                    }
                }
                
                log.debug("✅ Fetched {} variables for process {}", cleanedVariables.size(), processInstanceId);
                return cleanedVariables;
            } else {
                log.warn("⚠️ No variables found for process {}", processInstanceId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch variables for process {}: {}", processInstanceId, e.getMessage());
        }
        return new HashMap<>();
    }

    // === Task Completion ===
    public void completeTaskWithAction(String taskId, TaskActionRequest action) {
        try {
            Map<String, Object> vars = new HashMap<>();
            if (action.getApproved() != null) vars.put("approved", action.getApproved());
            if (action.getComments() != null) vars.put("comments", action.getComments());
            if (action.getAdditionalVariables() != null) vars.putAll(action.getAdditionalVariables());
            completeTask(taskId, vars);
        } catch (Exception e) {
            log.error("❌ completeTaskWithAction failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }


    
    public void completeTask(String taskId, Map<String, Object> vars) {
        try {
            // ✅ FIXED: Variables should be sent as an array, not an object
            List<Map<String, Object>> variablesList = new ArrayList<>();
            
            if (!vars.isEmpty()) {
                for (Map.Entry<String, Object> entry : vars.entrySet()) {
                    Map<String, Object> variableMap = new HashMap<>();
                    variableMap.put("name", entry.getKey());
                    variableMap.put("value", entry.getValue());
                    // Optionally, you can add type information if needed
                    // variableMap.put("type", getVariableType(entry.getValue()));
                    variablesList.add(variableMap);
                }
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("variables", variablesList);

            log.debug("📤 Sending completion request for task {}: {}", taskId, requestBody);

            JsonNode response = tasklistClient.patch()
                    .uri("/v1/tasks/{taskId}/complete", taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(error -> {
                                        log.error("❌ Task completion API error: Status {} - {}", 
                                                clientResponse.statusCode(), error);
                                        return new RuntimeException("Task completion failed: " + error);
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            
            log.info("✅ Completed task {} with variables: {}", taskId, vars);
        } catch (Exception e) {
            log.error("❌ Task completion failed for {}: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Failed to complete task: " + taskId, e);
        }
    }

    // Optional helper method to determine variable type
    private String getVariableType(Object value) {
        if (value == null) return "Null";
        if (value instanceof String) return "String";
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof Integer) return "Integer";
        if (value instanceof Long) return "Long";
        if (value instanceof Double) return "Double";
        if (value instanceof java.util.Date) return "Date";
        return "Object";
    }



    // === Manager lookup ===
    public String getManagerForEmployee(String empId) {
        try {
            var e = employeeServiceClient.getEmployee(UUID.fromString(empId));
            return e.getManagerId() != null ? e.getManagerId().toString() : null;
        } catch (Exception ex) {
            log.error("❌ getManagerForEmployee failed: {}", ex.getMessage());
            return null;
        }
    }

    public String getManagerForEmployee(String empId, int depth) {
        try {
            UUID current = UUID.fromString(empId);
            int level = 0;
            while (current != null && level < depth) {
                var e = employeeServiceClient.getEmployee(current);
                if (e.getManagerId() != null) return e.getManagerId().toString();
                current = e.getManagerId();
                level++;
            }
        } catch (Exception e) {
            log.error("❌ getManagerForEmployee(depth) failed: {}", e.getMessage());
        }
        return null;
    }

    // === Task Details with Progress Bar ===
    public TaskDTO getTaskDetails(String taskId) {
        try {
            log.debug("🔍 Fetching task details for ID: {}", taskId);

            JsonNode response = tasklistClient.get()
                    .uri("/v1/tasks/{taskId}", taskId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            if (response == null || response.isNull()) {
                throw new RuntimeException("Task not found: " + taskId);
            }

            Map<String, Object> taskData = objectMapper.convertValue(response, new TypeReference<>() {});
            TaskDTO dto = mapToTaskDTO(taskData);
            enrichTaskDetails(dto);

            log.info("✅ Successfully retrieved task details for {}", taskId);
            return dto;
        } catch (WebClientResponseException.NotFound e) {
            throw new RuntimeException("Task not found: " + taskId, e);
        } catch (Exception e) {
            log.error("❌ getTaskDetails failed for {}: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Failed to get task details: " + taskId, e);
        }
    }

    private void enrichTaskDetails(TaskDTO dto) {
        try {
            Map<String, Object> meta = fetchProcessMetadata(dto.getProcessInstanceId());
            if (meta != null) {
                dto.setProcessState((String) meta.get("state"));
                dto.setProcessStartTime((LocalDateTime) meta.get("startTime"));
                dto.setNextStepName((String) meta.get("nextStep"));
                dto.setAllSteps((List<String>) meta.get("allSteps"));
                dto.setProgressPercent(calculateProgress(dto.getCurrentStep(), dto.getAllSteps()));
                dto.setProgressSteps(generateProgressSteps(dto.getCurrentStep(), dto.getAllSteps()));
            }

            // Ensure variables are populated
            if (dto.getVariables() == null || dto.getVariables().isEmpty()) {
                Map<String, Object> vars = fetchProcessVariables(dto.getProcessInstanceId());
                dto.setVariables(vars);
            }

            // Ensure employee profile is loaded
            if (dto.getEmployeeProfile() == null && dto.getVariables().containsKey("employeeId")) {
                Object empId = dto.getVariables().get("employeeId");
                if (empId != null) {
                    try {
                        String cleanEmpId = empId.toString();
                        if (cleanEmpId.startsWith("\"") && cleanEmpId.endsWith("\"")) {
                            cleanEmpId = cleanEmpId.substring(1, cleanEmpId.length() - 1);
                        }
                        dto.setEmployeeProfile(employeeServiceClient.getEmployee(UUID.fromString(cleanEmpId)));
                    } catch (Exception e) {
                        log.warn("Failed to fetch employee profile for task details: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich task details: {}", e.getMessage());
        }
    }

    private double calculateProgress(String currentStep, List<String> steps) {
        if (steps == null || steps.isEmpty() || currentStep == null) return 0.0;
        int idx = steps.indexOf(currentStep);
        if (idx == -1) return 0.0;
        return ((idx + 1) * 100.0) / steps.size();
    }

    private List<Map<String, Object>> generateProgressSteps(String currentStep, List<String> steps) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (steps == null || steps.isEmpty()) return list;

        for (String step : steps) {
            String status;
            String color;

            if (step.equals(currentStep)) {
                status = "CURRENT";
                color = "yellow";
            } else if (steps.indexOf(step) < steps.indexOf(currentStep)) {
                status = "COMPLETED";
                color = "green";
            } else {
                status = "PENDING";
                color = "gray";
            }

            list.add(Map.of(
                    "name", step,
                    "status", status,
                    "color", color
            ));
        }

        return list;
    }
    
 // ✅ UPDATED: Better diagnostic method
    public Map<String, Object> testTasklistApiStates(String employeeId) {
        // First, debug the raw responses
        debugTaskResponse(employeeId);
        
        Map<String, Object> results = new LinkedHashMap<>();
        String[] statesToTest = {"CREATED", "COMPLETED", "CANCELED"};
        
        for (String state : statesToTest) {
            Map<String, Object> query = buildEnhancedTaskQuery(employeeId, null, state);
            List<TaskDTO> tasks = searchTasks(query);
            
            Map<String, Object> stateResult = new HashMap<>();
            stateResult.put("count", tasks.size());
            stateResult.put("sampleTasks", tasks.stream()
                    .limit(3)
                    .map(t -> Map.of(
                        "taskId", t.getTaskId(),
                        "taskName", t.getTaskName(),
                        "status", t.getStatus(),
                        "processState", t.getProcessState(),
                        "completionDate", t.getCreatedDate(),
                        "rawVariables", t.getVariables() != null ? t.getVariables().keySet() : "none"
                    ))
                    .collect(Collectors.toList()));
            
            results.put(state, stateResult);
        }
        
        return results;
    }
    
 // ✅ NEW: Debug method to see raw API responses
    public void debugTaskResponse(String employeeId) {
        String[] states = {"CREATED", "COMPLETED", "CANCELED"};
        
        for (String state : states) {
            Map<String, Object> query = buildEnhancedTaskQuery(employeeId, null, state);
            log.info("🔍 === DEBUG SEARCH for state: {} ===", state);
            
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("filter", query.get("filter"));
                requestBody.put("pageSize", 2); // Just get a couple for debugging
                requestBody.put("includeVariables", query.get("includeVariables"));

                JsonNode resp = tasklistClient.post()
                        .uri("/v1/tasks/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(Duration.ofSeconds(10));

                if (resp != null && resp.isArray()) {
                    log.info("📊 Raw API response for {}: {}", state, resp.toPrettyString());
                    
                    // Parse and log individual task states
                    List<Map<String, Object>> items = objectMapper.convertValue(resp, new TypeReference<>() {});
                    for (Map<String, Object> item : items) {
                        String taskId = (String) item.get("id");
                        String rawTaskState = (String) item.get("taskState");
                        log.info("   📝 Task {} -> raw taskState: {}", taskId, rawTaskState);
                    }
                }
            } catch (Exception e) {
                log.error("❌ Debug failed for state {}: {}", state, e.getMessage());
            }
        }
    }
    private Map<String, Object> fetchProcessMetadata(String procId) {
        try {
            return Map.of(
                    "state", "ACTIVE",
                    "startTime", LocalDateTime.now().minusHours(1),
                    "nextStep", "Next Approval Step",
                    "allSteps", List.of("Manager Approval", "Finance Review", "HR Compliance", "Completion")
            );
        } catch (Exception e) {
            log.warn("⚠️ fetchProcessMetadata failed: {}", e.getMessage());
            return null;
        }
    }
    
    private final Map<String, EmployeeProxyDTO> employeeCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> variablesCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private final Map<String, Instant> cacheTimestamps = new ConcurrentHashMap<>();
    
    private EmployeeProxyDTO getCachedEmployee(String employeeId) {
        String cacheKey = "employee:" + employeeId;
        Instant timestamp = cacheTimestamps.get(cacheKey);
        
        if (timestamp != null && Instant.now().isBefore(timestamp.plus(CACHE_TTL))) {
            return employeeCache.get(cacheKey);
        }
        
        try {
            EmployeeProxyDTO employee = employeeServiceClient.getEmployee(UUID.fromString(employeeId));
            employeeCache.put(cacheKey, employee);
            cacheTimestamps.put(cacheKey, Instant.now());
            log.debug("✅ Cached employee profile: {}", employeeId);
            return employee;
        } catch (Exception e) {
            log.warn("❌ Failed to fetch employee {}: {}", employeeId, e.getMessage());
            return null;
        }
    }
    
    private Map<String, Object> getCachedVariables(String processInstanceId) {
        String cacheKey = "variables:" + processInstanceId;
        Instant timestamp = cacheTimestamps.get(cacheKey);
        
        if (timestamp != null && Instant.now().isBefore(timestamp.plus(CACHE_TTL))) {
            return variablesCache.get(cacheKey);
        }
        
        Map<String, Object> variables = fetchProcessVariables(processInstanceId);
        variablesCache.put(cacheKey, variables);
        cacheTimestamps.put(cacheKey, Instant.now());
        return variables;
    }
    
    private void enrichTasksWithProcessVariablesOptimized(List<TaskDTO> tasks) {
        if (tasks.isEmpty()) return;
        
        // Step 1: Batch fetch variables for all unique process instances
        Map<String, Map<String, Object>> processVariables = new HashMap<>();
        Set<String> uniqueProcessIds = tasks.stream()
                .map(TaskDTO::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        log.info("🔄 Batch fetching variables for {} process instances", uniqueProcessIds.size());
        
        uniqueProcessIds.parallelStream().forEach(processId -> {
            try {
                Map<String, Object> vars = getCachedVariables(processId);
                processVariables.put(processId, vars);
            } catch (Exception e) {
                log.warn("Failed to fetch variables for process {}: {}", processId, e.getMessage());
                processVariables.put(processId, new HashMap<>());
            }
        });
        
        // Step 2: Collect unique employee IDs
        Set<String> employeeIds = new HashSet<>();
        tasks.forEach(task -> {
            Map<String, Object> vars = processVariables.get(task.getProcessInstanceId());
            if (vars != null && vars.containsKey("employeeId")) {
                String empId = cleanVariableValue(vars.get("employeeId")).toString();
                if (empId != null && !empId.isBlank()) {
                    employeeIds.add(empId);
                }
            }
        });
        
        // Step 3: Batch fetch employee profiles
        Map<String, EmployeeProxyDTO> employeeProfiles = new HashMap<>();
        employeeIds.forEach(empId -> {
            try {
                EmployeeProxyDTO employee = getCachedEmployee(empId);
                if (employee != null) {
                    employeeProfiles.put(empId, employee);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch employee profile for {}: {}", empId, e.getMessage());
            }
        });
        
        // Step 4: Enrich tasks
        tasks.forEach(task -> {
            try {
                Map<String, Object> vars = processVariables.get(task.getProcessInstanceId());
                if (vars != null && !vars.isEmpty()) {
                    // Merge variables
                    if (task.getVariables() == null || task.getVariables().isEmpty()) {
                        task.setVariables(new HashMap<>(vars));
                    } else {
                        Map<String, Object> merged = new HashMap<>(task.getVariables());
                        vars.forEach(merged::putIfAbsent);
                        task.setVariables(merged);
                    }
                    
                    // Set employee profile
                    if (vars.containsKey("employeeId")) {
                        String empId = cleanVariableValue(vars.get("employeeId")).toString();
                        EmployeeProxyDTO employee = employeeProfiles.get(empId);
                        if (employee != null) {
                            task.setEmployeeProfile(employee);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to enrich task {}: {}", task.getTaskId(), e.getMessage());
            }
        });
        
        log.info("✅ Batch enriched {} tasks with variables and profiles", tasks.size());
    }
}