package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.TaskDTO;
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

    // ✅ Get all tasks (pending + completed)
    @GetMapping("/{employeeId}")
    public UserTasksResponse getManagerTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "MANAGER");
    }

    // ✅ Get detailed context for one specific task
    @GetMapping("/{taskId}/details")
    public TaskDTO getTaskDetails(@PathVariable String taskId) {
        return userTaskService.getTaskDetails(taskId);
    }

    // ✅ Approve a task
    @PostMapping("/{taskId}/approve")
    public String approveTask(@PathVariable String taskId,
                              @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(true);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ Manager task approved successfully!";
    }

    // ✅ Reject a task
    @PostMapping("/{taskId}/reject")
    public String rejectTask(@PathVariable String taskId,
                             @RequestBody TaskActionRequest actionRequest) {
        actionRequest.setApproved(false);
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "❌ Manager task rejected!";
    }
}
