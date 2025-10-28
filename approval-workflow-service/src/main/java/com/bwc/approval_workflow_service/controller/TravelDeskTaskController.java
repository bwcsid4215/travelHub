package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskActionRequest;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-desk/tasks")
@RequiredArgsConstructor
public class TravelDeskTaskController {

    private final EnhancedUserTaskService userTaskService;

    @GetMapping("/{employeeId}")
    public UserTasksResponse getTravelDeskTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "TRAVEL_DESK");
    }

    @PostMapping("/{taskId}/process")
    public String processTravelDeskTask(@PathVariable String taskId, 
                                      @RequestBody TaskActionRequest actionRequest) {
        userTaskService.completeTaskWithAction(taskId, actionRequest);
        return "✅ Travel desk task processed successfully!";
    }
}