package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class TaskDebugController {
    
    private final EnhancedUserTaskService userTaskService;
    
    @GetMapping("/all-manager-tasks")
    public Map<String, Object> getAllManagerTasks() {
        try {
            // Get all CREATED tasks with candidate group "managers"
            Map<String, Object> query = new HashMap<>();
            query.put("state", "CREATED");
            query.put("candidateGroups", java.util.List.of("managers"));
            query.put("pageSize", 50);
            
            var tasks = userTaskService.searchTasks(query);
            
            return Map.of(
                "totalTasks", tasks.size(),
                "tasks", tasks.stream().map(task -> Map.of(
                    "taskId", task.getTaskId(),
                    "taskName", task.getTaskName(),
                    "assignee", task.getAssignee(),
                    "candidateGroup", task.getCandidateGroup(),
                    "processInstanceId", task.getProcessInstanceId()
                )).collect(java.util.stream.Collectors.toList())
            );
            
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
    
    @GetMapping("/process-variables/{processInstanceId}")
    public Map<String, Object> getProcessVariables(@PathVariable String processInstanceId) {
        try {
            Map<String, Object> variables = userTaskService.fetchProcessVariables(processInstanceId);
            Map<String, Object> result = new HashMap<>();
            result.put("processInstanceId", processInstanceId);
            result.put("variables", variables);
            result.put("managerId", variables.get("managerId"));
            return result;
        } catch (Exception e) {
            return Map.of(
                "error", e.getMessage(),
                "processInstanceId", processInstanceId
            );
        }
    }
    
    @GetMapping("/test-manager-tasks/{managerId}")
    public Map<String, Object> testManagerTasks(@PathVariable String managerId) {
        try {
            // Test 1: Search by assignee
            Map<String, Object> assigneeQuery = new HashMap<>();
            assigneeQuery.put("state", "CREATED");
            assigneeQuery.put("assignee", managerId);
            assigneeQuery.put("pageSize", 50);
            
            var assigneeTasks = userTaskService.searchTasks(assigneeQuery);
            
            // Test 2: Search by candidate group
            Map<String, Object> groupQuery = new HashMap<>();
            groupQuery.put("state", "CREATED");
            groupQuery.put("candidateGroups", java.util.List.of("managers"));
            groupQuery.put("pageSize", 50);
            
            var groupTasks = userTaskService.searchTasks(groupQuery);
            
            return Map.of(
                "managerId", managerId,
                "assigneeTasksCount", assigneeTasks.size(),
                "groupTasksCount", groupTasks.size(),
                "assigneeTasks", assigneeTasks.stream().map(t -> Map.of(
                    "id", t.getTaskId(),
                    "name", t.getTaskName(),
                    "assignee", t.getAssignee()
                )).collect(java.util.stream.Collectors.toList()),
                "groupTasks", groupTasks.stream().map(t -> Map.of(
                    "id", t.getTaskId(),
                    "name", t.getTaskName(),
                    "assignee", t.getAssignee()
                )).collect(java.util.stream.Collectors.toList())
            );
            
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}