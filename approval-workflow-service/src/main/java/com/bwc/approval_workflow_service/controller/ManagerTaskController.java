package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/manager/tasks")
@RequiredArgsConstructor
public class ManagerTaskController {

    private final EnhancedUserTaskService userTaskService;

    @GetMapping("/{employeeId}")
    public UserTasksResponse getManagerTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "MANAGER");
    }

    @PostMapping("/{taskId}/approve")
    public String approveTask(@PathVariable String taskId, 
                            @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(true);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ Manager task approved successfully!";
    }

    @PostMapping("/{taskId}/reject")
    public String rejectTask(@PathVariable String taskId, 
                           @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(false);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "❌ Manager task rejected!";
    }
}