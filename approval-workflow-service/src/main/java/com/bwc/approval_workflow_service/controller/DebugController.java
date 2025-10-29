package com.bwc.approval_workflow_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DebugController {
    
    private final EnhancedUserTaskService userTaskService;
    
    @GetMapping("/api/debug/tasks/{processInstanceId}")
    public String debugTasks(@PathVariable String processInstanceId) {
        userTaskService.debugTaskAssignment(processInstanceId);
        return "Debug completed - check logs";
    }
    
    @GetMapping("/api/debug/manager-test/{employeeId}")
    public String testManagerAssignment(@PathVariable String employeeId) {
        // Test direct manager lookup
        String managerId = userTaskService.getManagerForEmployee(employeeId);
        log.info("🔍 Manager for employee {}: {}", employeeId, managerId);
        
        // Test skip-level manager
        String skipLevelManager = userTaskService.getManagerForEmployee(employeeId, 1);
        log.info("🔍 Skip-level manager for employee {}: {}", employeeId, skipLevelManager);
        
        return String.format("Manager: %s, Skip-level: %s", managerId, skipLevelManager);
    }
}