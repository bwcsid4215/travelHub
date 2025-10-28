package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/tasks")
@RequiredArgsConstructor
public class FinanceTaskController {

    private final EnhancedUserTaskService userTaskService;

    @GetMapping("/{employeeId}")
    public UserTasksResponse getFinanceTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "FINANCE");
    }

    @PostMapping("/{taskId}/process")
    public String processFinanceTask(@PathVariable String taskId, 
                                   @RequestBody TaskActionRequest actionRequest) {
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ Finance task processed successfully!";
    }
}