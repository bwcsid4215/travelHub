package com.bwc.approval_workflow_service.workflow;

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

    private final WebClient tasklistClient;                 // Base URL must point to Tasklist (e.g., http://localhost:7080)
    private final EmployeeServiceClient employeeServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_PAGE_SIZE = 100;

    // ===========================
    // Public: Get all tasks
    // ===========================
    public UserTasksResponse getUserTasksWithHistory(String employeeId, String role) {
        try {
            EmployeeProxyDTO employee = employeeServiceClient.getEmployee(UUID.fromString(employeeId));

            UserInfo userInfo = new UserInfo();
            userInfo.setEmployeeId(employeeId);
            userInfo.setFullName(employee.getFullName());
            userInfo.setDepartment(employee.getDepartment());
            userInfo.setRole(role);

            List<TaskDTO> pendingTasks = getPendingTasks(employeeId, role);
            List<TaskDTO> completedTasks = getCompletedTasks(employeeId, role);

            // (Optional) Enrich tasks with process variables (safe guarded)
            enrichTasksWithProcessVariables(pendingTasks);
            enrichTasksWithProcessVariables(completedTasks);

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

    // ===========================
    // Pending & Completed
    // ===========================
    private List<TaskDTO> getPendingTasks(String employeeId, String role) {
        // Manager: combine assignee + candidateGroup=managers
        if ("MANAGER".equalsIgnoreCase(role)) {
            return mergeAssigneeAndGroupTasks(employeeId, "managers", "CREATED");
        }
        // Others: just role-based group or assignee
        return searchTasks(buildTaskQuery(employeeId, role, "CREATED"));
    }

    private List<TaskDTO> getCompletedTasks(String employeeId, String role) {
        return searchTasks(buildTaskQuery(employeeId, role, "COMPLETED"));
    }

    private List<TaskDTO> mergeAssigneeAndGroupTasks(String employeeId, String group, String state) {
        List<TaskDTO> all = new ArrayList<>();
        Map<String, Object> assigneeQuery = Map.of(
                "assignee", employeeId,
                "state", state,
                "pageSize", DEFAULT_PAGE_SIZE
        );
        Map<String, Object> groupQuery = Map.of(
                "candidateGroup", group,
                "state", state,
                "pageSize", DEFAULT_PAGE_SIZE
        );

        List<TaskDTO> assigneeTasks = searchTasks(assigneeQuery);
        log.info("📋 Found {} direct tasks for assignee {}", assigneeTasks.size(), employeeId);
        all.addAll(assigneeTasks);

        List<TaskDTO> groupTasks = searchTasks(groupQuery);
        log.info("📋 Found {} group tasks for {}", groupTasks.size(), group);
        all.addAll(groupTasks);

        // Dedup by taskId
        return all.stream()
                .collect(Collectors.toMap(TaskDTO::getTaskId, t -> t, (a, b) -> a))
                .values()
                .stream()
                .toList();
    }

    private Map<String, Object> buildTaskQuery(String employeeId, String role, String state) {
        Map<String, Object> query = new HashMap<>();
        query.put("state", state);
        query.put("pageSize", DEFAULT_PAGE_SIZE);

        switch (role == null ? "" : role.toUpperCase()) {
            case "MANAGER" -> {
                query.put("assignee", employeeId);
                query.put("candidateGroup", "managers");
                log.info("🔍 Manager task query: assignee={}, candidateGroup=managers, state={}", employeeId, state);
            }
            case "FINANCE" -> {
                query.put("candidateGroup", "finance-approvers");
                log.info("🔍 Finance task query: candidateGroup=finance-approvers, state={}", state);
            }
            case "HR" -> {
                query.put("candidateGroup", "hr-compliance-team");
                log.info("🔍 HR task query: candidateGroup=hr-compliance-team, state={}", state);
            }
            case "TRAVEL_DESK" -> {
                query.put("candidateGroup", "travel-desk-team");
                log.info("🔍 Travel Desk task query: candidateGroup=travel-desk-team, state={}", state);
            }
            default -> {
                query.put("assignee", employeeId);
                log.info("🔍 Default task query: assignee={}, state={}", employeeId, state);
            }
        }
        return query;
    }

    // ===========================
    // GraphQL: Search Tasks
    // ===========================
    public List<TaskDTO> searchTasks(Map<String, Object> queryParams) {
        try {
            log.info("🔍 Task search query: {}", queryParams);

            // Build GraphQL variables
            Map<String, Object> gqlVars = new HashMap<>();
            Map<String, Object> taskQuery = new HashMap<>();

            // state
            String state = Objects.toString(queryParams.getOrDefault("state", "CREATED"), "CREATED");
            taskQuery.put("state", state);

            // pagination
            int pageSize = (int) queryParams.getOrDefault("pageSize", DEFAULT_PAGE_SIZE);
            taskQuery.put("pageSize", pageSize);

            // filters
            if (queryParams.containsKey("assignee")) {
                taskQuery.put("assignee", String.valueOf(queryParams.get("assignee")));
            }
            if (queryParams.containsKey("candidateGroup")) {
                taskQuery.put("candidateGroup", String.valueOf(queryParams.get("candidateGroup")));
            }
            if (queryParams.containsKey("processInstanceIds")) {
                taskQuery.put("processInstanceIds", queryParams.get("processInstanceIds"));
            }
            if (queryParams.containsKey("ids")) {
                taskQuery.put("ids", queryParams.get("ids"));
            }

            gqlVars.put("q", taskQuery);

            JsonNode response = graphql(QUERY_TASKS, gqlVars);
            if (response == null) {
                log.warn("⚠️ GraphQL tasks response is null");
                return Collections.emptyList();
            }

            JsonNode tasksNode = response.at("/data/tasks");
            if (tasksNode == null || !tasksNode.isArray()) {
                log.warn("⚠️ GraphQL tasks returned unexpected shape: {}", tasksNode);
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = objectMapper.convertValue(tasksNode, new TypeReference<>() {});
            log.info("✅ GraphQL task search found {} tasks", items.size());

            return items.stream()
                    .map(this::mapToTaskDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("❌ Task search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ===========================
    // Debug helper
    // ===========================
    public void debugTaskAssignment(String processInstanceId) {
        try {
            Map<String, Object> q = Map.of(
                    "processInstanceIds", List.of(processInstanceId),
                    "pageSize", 50
            );
            Map<String, Object> vars = Map.of("q", q);
            JsonNode resp = graphql(QUERY_TASKS, vars);

            JsonNode tasks = resp == null ? null : resp.at("/data/tasks");
            if (tasks != null && tasks.isArray()) {
                log.info("📋 All tasks for process instance {}:", processInstanceId);
                for (JsonNode t : tasks) {
                    log.info("   - Task: id={}, name={}, assignee={}, candidateGroup={}, state={}",
                            t.path("id").asText(null),
                            t.path("name").asText(null),
                            t.path("assignee").asText(null),
                            t.path("candidateGroup").asText(null),
                            t.path("state").asText(null));
                }
            }
        } catch (Exception e) {
            log.error("❌ Debug task assignment failed: {}", e.getMessage());
        }
    }

    // ===========================
    // Mapping
    // ===========================
    private TaskDTO mapToTaskDTO(Map<String, Object> data) {
        TaskDTO dto = new TaskDTO();

        String id = asString(data.get("id"));
        dto.setTaskId(id);
        dto.setTaskName(asString(data.get("name")));
        dto.setProcessInstanceId(asString(data.get("processInstanceId")));
        dto.setProcessDefinitionId(asString(data.get("processDefinitionId")));
        dto.setCreatedDate(parseDateTime(asString(data.get("creationDate"))));
        dto.setDueDate(parseDateTime(asString(data.get("dueDate"))));
        dto.setAssignee(asString(data.get("assignee")));
        dto.setCandidateGroup(asString(data.get("candidateGroup")));
        dto.setCurrentStep(asString(data.get("name")));
        dto.setProcessState(asString(data.get("state")));

        // variables can be either a list({name,value}) or a map depending on server
        dto.setVariables(extractVariables(data));

        // Status from state
        String state = asString(data.get("state"));
        dto.setStatus("CREATED".equalsIgnoreCase(state) ? "PENDING" : "COMPLETED");

        // Outcome inference
        dto.setOutcome(extractOutcomeFromVars(dto.getVariables()));

        return dto;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private LocalDateTime parseDateTime(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDateTime.parse(v, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractVariables(Map<String, Object> data) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            Object raw = data.get("variables");
            if (raw == null) return vars;

            if (raw instanceof List<?> list) {
                for (Object obj : list) {
                    if (obj instanceof Map<?, ?> m) {
                        String name = Objects.toString(m.get("name"), "");
                        Object value = m.get("value");
                        if (!name.isEmpty()) vars.put(name, value);
                    }
                }
            } else if (raw instanceof Map<?, ?> map) {
                // some gateways may already return a map
                for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                    vars.put(String.valueOf(e.getKey()), e.getValue());
                }
            } else {
                log.warn("⚠️ Unrecognized variables format: {}", raw.getClass());
            }
        } catch (Exception e) {
            log.warn("Failed to extract variables: {}", e.getMessage());
        }
        return vars;
    }

    private String extractOutcomeFromVars(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) return "UNKNOWN";
        if (vars.containsKey("managerApproved"))
            return Boolean.TRUE.equals(vars.get("managerApproved")) ? "APPROVED" : "REJECTED";
        if (vars.containsKey("financeApproved"))
            return Boolean.TRUE.equals(vars.get("financeApproved")) ? "APPROVED" : "REJECTED";
        if (vars.containsKey("hrCompliant"))
            return Boolean.TRUE.equals(vars.get("hrCompliant")) ? "COMPLIANT" : "NON_COMPLIANT";
        return "UNKNOWN";
    }

    // ===========================
    // Optional enrichment
    // ===========================
    private void enrichTasksWithProcessVariables(List<TaskDTO> tasks) {
        if (tasks == null || tasks.isEmpty()) return;

        tasks.forEach(task -> {
            String procId = task.getProcessInstanceId();
            if (procId == null || procId.isBlank()) {
                // Avoid the previous 400 problem by skipping null process ids
                log.debug("Skipping process variables fetch: task {} has null processInstanceId", task.getTaskId());
                return;
            }
            try {
                Map<String, Object> procVars = fetchVariablesForProcessGraphQL(procId);
                if (procVars.isEmpty()) return;

                if (task.getVariables() == null) task.setVariables(new LinkedHashMap<>());
                task.getVariables().putAll(procVars);

                Object empId = task.getVariables().get("employeeId");
                if (empId != null) {
                    try {
                        EmployeeProxyDTO employee = employeeServiceClient.getEmployee(UUID.fromString(String.valueOf(empId)));
                        task.setEmployeeProfile(employee);
                    } catch (Exception e) {
                        log.warn("Failed to fetch employee profile for {}: {}", empId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to enrich task {}: {}", task.getTaskId(), e.getMessage());
            }
        });
    }

    // ===========================
    // GraphQL: Fetch process variables
    // ===========================
    private Map<String, Object> fetchVariablesForProcessGraphQL(String processInstanceId) {
        try {
            Map<String, Object> q = Map.of(
                    "processInstanceId", processInstanceId,
                    "pageSize", DEFAULT_PAGE_SIZE
            );
            Map<String, Object> vars = Map.of("q", q);

            JsonNode resp = graphql(QUERY_VARIABLES, vars);
            JsonNode items = resp == null ? null : resp.at("/data/variables");
            if (items == null || !items.isArray()) return Collections.emptyMap();

            List<Map<String, Object>> list = objectMapper.convertValue(items, new TypeReference<>() {});
            return list.stream()
                    .filter(m -> m.get("name") != null)
                    .collect(Collectors.toMap(
                            m -> String.valueOf(m.get("name")),
                            m -> m.get("value"),
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            log.warn("Failed to fetch variables for process {}: {}", processInstanceId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ===========================
    // Task completion (GraphQL)
    // ===========================
    public void completeTaskWithAction(String taskId, TaskActionRequest action) {
        try {
            Map<String, Object> vars = new LinkedHashMap<>();
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
            Map<String, Object> variables = new HashMap<>();
            variables.put("taskId", taskId);
            variables.put("variables", vars == null ? Map.of() : vars);

            JsonNode response = graphql(MUTATION_COMPLETE_TASK, variables);
            if (response != null && response.has("errors")) {
                log.error("❌ Task completion failed: {}", response.get("errors"));
                throw new RuntimeException("GraphQL mutation failed");
            }
            log.info("✅ Completed task {} with variables: {}", taskId, vars);
        } catch (Exception e) {
            log.error("❌ Task completion failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to complete task: " + taskId, e);
        }
    }

    // ===========================
    // GraphQL helper
    // ===========================
    private JsonNode graphql(String query, Map<String, Object> variables) {
        try {
            JsonNode resp = tasklistClient.post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("query", query, "variables", variables == null ? Map.of() : variables))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));

            if (resp != null && resp.has("errors")) {
                log.warn("⚠️ GraphQL returned errors: {}", resp.get("errors"));
            }
            return resp;
        } catch (Exception e) {
            log.error("❌ GraphQL call failed: {}", e.getMessage(), e);
            return null;
        }
    }
    
	 // ===========================
	 // Manager lookup helpers
	 // ===========================
	 public String getManagerForEmployee(String empId) {
	     try {
	         var emp = employeeServiceClient.getEmployee(UUID.fromString(empId));
	         if (emp != null && emp.getManagerId() != null)
	             return emp.getManagerId().toString();
	         log.warn("⚠️ No manager found for employee {}", empId);
	     } catch (Exception e) {
	         log.error("❌ getManagerForEmployee failed for {}: {}", empId, e.getMessage());
	     }
	     return null;
	 }
	
	 public String getManagerForEmployee(String empId, int depth) {
	     try {
	         UUID currentId = UUID.fromString(empId);
	         int level = 0;
	         while (currentId != null && level < depth) {
	             var emp = employeeServiceClient.getEmployee(currentId);
	             if (emp == null || emp.getManagerId() == null) break;
	             currentId = emp.getManagerId();
	             level++;
	         }
	         return currentId != null ? currentId.toString() : null;
	     } catch (Exception e) {
	         log.error("❌ getManagerForEmployee(depth) failed: {}", e.getMessage());
	         return null;
	     }
	 }
	
	 
	//===========================
	//Fallback for legacy controller calls
	//===========================
	public Map<String, Object> fetchProcessVariables(String processInstanceId) {
	  if (processInstanceId == null || processInstanceId.isBlank()) {
	      log.warn("⚠️ fetchProcessVariables called with null/blank ID");
	      return Collections.emptyMap();
	  }
	
	  try {
	      return fetchVariablesForProcessGraphQL(processInstanceId);
	  } catch (Exception e) {
	      log.warn("Failed to fetch process variables for {}: {}", processInstanceId, e.getMessage());
	      return Collections.emptyMap();
	  }
	}
	
	
	//===========================
	//Simple Task details stub (for ManagerTaskController)
	//===========================
	public TaskDTO getTaskDetails(String taskId) {
	 try {
	     List<TaskDTO> tasks = searchTasks(Map.of("ids", List.of(taskId)));
	     if (!tasks.isEmpty()) {
	         TaskDTO dto = tasks.get(0);
	         enrichTasksWithProcessVariables(List.of(dto));
	         return dto;
	     }
	     throw new RuntimeException("Task not found for ID: " + taskId);
	 } catch (Exception e) {
	     log.error("❌ getTaskDetails failed for {}: {}", taskId, e.getMessage());
	     throw new RuntimeException(e);
	 }
	}


    // ===========================
    // GraphQL documents
    // ===========================
    // NOTE: Field names (state/assignee/candidateGroup/creationDate/variables{name,value})
    // match common Tasklist GraphQL shapes. Adjust if your schema differs.
    private static final String QUERY_TASKS = """
        query Tasks($q: TaskQueryInput!) {
          tasks(query: $q) {
            id
            name
            state
            assignee
            candidateGroup
            processInstanceId
            processDefinitionId
            creationDate
            dueDate
            variables { name value }
          }
        }
        """;

    private static final String QUERY_VARIABLES = """
        query Variables($q: VariableQueryInput!) {
          variables(query: $q) {
            name
            value
          }
        }
        """;

    private static final String MUTATION_COMPLETE_TASK = """
        mutation CompleteTask($taskId: ID!, $variables: JSON) {
          completeTask(taskId: $taskId, variables: $variables)
        }
        """;
}
