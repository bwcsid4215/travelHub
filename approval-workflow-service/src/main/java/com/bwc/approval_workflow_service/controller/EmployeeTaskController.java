package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employee/tasks")
@RequiredArgsConstructor
public class EmployeeTaskController {

    private final EnhancedUserTaskService userTaskService;

    @GetMapping("/{employeeId}")
    public UserTasksResponse getEmployeeTasks(@PathVariable String employeeId) {
        return userTaskService.getUserTasksWithHistory(employeeId, "EMPLOYEE");
    }

    @GetMapping("/{employeeId}/status")
    public String getRequestStatus(@PathVariable String employeeId) {
        UserTasksResponse response = userTaskService.getUserTasksWithHistory(employeeId, "EMPLOYEE");
        
        long pending = response.getPendingTasks().size();
        long completed = response.getCompletedTasks().size();
        
        return String.format("You have %d pending requests and %d completed requests", pending, completed);
    }
}