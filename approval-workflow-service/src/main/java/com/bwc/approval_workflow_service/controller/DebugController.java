package com.bwc.approval_workflow_service.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bwc.approval_workflow_service.dto.TaskDTO;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {
    
    private final EnhancedUserTaskService taskService;
    
    @GetMapping("/tasks/{employeeId}")
    public Map<String, Object> debugTasks(@PathVariable String employeeId) {
        Map<String, Object> debugInfo = new LinkedHashMap<>();
        
        // Check pending tasks
        Map<String, Object> pendingQuery = Map.of(
            "assignee", employeeId,
            "state", "CREATED",
            "pageSize", 100
        );
        List<TaskDTO> pendingTasks = taskService.searchTasks(pendingQuery);
        debugInfo.put("pendingTasks", pendingTasks.stream()
                .map(t -> Map.of(
                    "taskId", t.getTaskId(),
                    "taskName", t.getTaskName(),
                    "status", t.getStatus(),
                    "processState", t.getProcessState(),
                    "assignee", t.getAssignee()
                ))
                .collect(Collectors.toList()));
        
        // Check completed tasks
        Map<String, Object> completedQuery = Map.of(
            "assignee", employeeId,
            "state", "COMPLETED", 
            "pageSize", 100
        );
        List<TaskDTO> completedTasks = taskService.searchTasks(completedQuery);
        debugInfo.put("completedTasksRaw", completedTasks.stream()
                .map(t -> Map.of(
                    "taskId", t.getTaskId(),
                    "taskName", t.getTaskName(), 
                    "status", t.getStatus(),
                    "processState", t.getProcessState(),
                    "assignee", t.getAssignee()
                ))
                .collect(Collectors.toList()));
        
        debugInfo.put("summary", Map.of(
            "pendingCount", pendingTasks.size(),
            "completedRawCount", completedTasks.size(),
            "employeeId", employeeId
        ));
        
        return debugInfo;
    }
    
    
}