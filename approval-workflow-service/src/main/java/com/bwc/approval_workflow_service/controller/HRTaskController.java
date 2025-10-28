package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/tasks")
@RequiredArgsConstructor
public class HRTaskController {

    private final EnhancedUserTaskService userTaskService;

    @GetMapping("/{employeeId}")
    public UserTasksResponse getHRTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "HR");
    }

    @PostMapping("/{taskId}/review")
    public String reviewHRTask(@PathVariable String taskId, 
                             @RequestBody TaskActionRequest actionRequest) {
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ HR compliance review completed!";
    }
}