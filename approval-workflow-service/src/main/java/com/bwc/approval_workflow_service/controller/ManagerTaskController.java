package com.bwc.approval_workflow_service.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.TaskDTO;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/manager/tasks")
@RequiredArgsConstructor
public class ManagerTaskController {

    private final EnhancedUserTaskService userTaskService;

    // ✅ Get all tasks (pending + completed) - YOUR EXISTING METHOD
    @GetMapping("/{employeeId}")
    public UserTasksResponse getManagerTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "MANAGER");
    }

    // ✅ Get detailed context for one specific task - YOUR EXISTING METHOD
    @GetMapping("/{taskId}/details")
    public TaskDTO getTaskDetails(@PathVariable String taskId) {
        return userTaskService.getTaskDetails(taskId);
    }

    // ✅ NEW: Test endpoint for fixed state filtering
    @GetMapping("/test-fixed-states/{employeeId}")
    public Map<String, Object> testFixedStates(@PathVariable String employeeId) {
        // Test the corrected structure
        Map<String, Object> results = new LinkedHashMap<>();
        
        String[] states = {"CREATED", "COMPLETED", "CANCELED"};
        for (String state : states) {
            Map<String, Object> query = userTaskService.buildEnhancedTaskQuery(employeeId, null, state);
            List<TaskDTO> tasks = userTaskService.searchTasks(query);
            results.put(state, Map.of(
                "count", tasks.size(),
                "states", tasks.stream().map(t -> t.getStatus()).distinct().collect(Collectors.toList())
            ));
        }
        
        return results;
    }
    
    
 // ✅ NEW: Test endpoint to complete a task
    @PostMapping("/test-complete/{taskId}")
    public String testCompleteTask(@PathVariable String taskId) {
        try {
            // Complete the task with approval
            TaskActionRequest action = new TaskActionRequest();
            action.setApproved(true);
            action.setComments("Test completion for COMPLETED state testing");
            
            userTaskService.completeTaskWithAction(taskId, action);
            
            return "✅ Task " + taskId + " completed successfully!";
        } catch (Exception e) {
            return "❌ Failed to complete task: " + e.getMessage();
        }
    }
    
 // ✅ NEW: Enhanced test endpoint with task details
    @GetMapping("/test-detailed-states/{employeeId}")
    public Map<String, Object> testDetailedStates(@PathVariable String employeeId) {
        Map<String, Object> results = new LinkedHashMap<>();
        
        String[] states = {"CREATED", "COMPLETED", "CANCELED"};
        for (String state : states) {
            Map<String, Object> query = userTaskService.buildEnhancedTaskQuery(employeeId, null, state);
            List<TaskDTO> tasks = userTaskService.searchTasks(query);
            
            results.put(state, Map.of(
                "count", tasks.size(),
                "taskIds", tasks.stream().map(TaskDTO::getTaskId).collect(Collectors.toList()),
                "states", tasks.stream().map(TaskDTO::getStatus).distinct().collect(Collectors.toList())
            ));
        }
        
        return results;
    }
    
 // ✅ NEW: Debug endpoint to see what's happening with task completion
    @GetMapping("/debug-task/{taskId}")
    public Map<String, Object> debugTask(@PathVariable String taskId) {
        try {
            TaskDTO task = userTaskService.getTaskDetails(taskId);
            return Map.of(
                "taskId", taskId,
                "status", task.getStatus(),
                "processState", task.getProcessState(),
                "completionDate", task.getCreatedDate(), // This should show completion date if completed
                "hasCompletionDate", task.getVariables() != null && task.getVariables().containsKey("completionDate")
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
    
    
    // ✅ Approve a task - YOUR EXISTING METHOD
    @PostMapping("/{taskId}/approve")
    public String approveTask(@PathVariable String taskId,
                              @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(true);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ Manager task approved successfully!";
    }

    // ✅ Reject a task - YOUR EXISTING METHOD
    @PostMapping("/{taskId}/reject")
    public String rejectTask(@PathVariable String taskId,
                             @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(false);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "❌ Manager task rejected!";
    }
    
 // ✅ NEW: Simple completion without variables
    @PostMapping("/test-simple-complete/{taskId}")
    public String testSimpleComplete(@PathVariable String taskId) {
        try {
            // Complete the task with empty variables first
            Map<String, Object> emptyVars = new HashMap<>();
            userTaskService.completeTask(taskId, emptyVars);
            
            return "✅ Task " + taskId + " completed successfully (no variables)!";
        } catch (Exception e) {
            return "❌ Failed to complete task: " + e.getMessage();
        }
    }
    
 // ✅ NEW: Debug endpoint to check task state before completion
    @GetMapping("/debug-task-completion/{taskId}")
    public Map<String, Object> debugTaskCompletion(@PathVariable String taskId) {
        try {
            TaskDTO task = userTaskService.getTaskDetails(taskId);
            return Map.of(
                "taskId", taskId,
                "status", task.getStatus(),
                "processState", task.getProcessState(),
                "assignee", task.getAssignee(),
                "candidateGroup", task.getCandidateGroup(),
                "createdDate", task.getCreatedDate(),
                "variables", task.getVariables() != null ? task.getVariables().keySet() : "none"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}