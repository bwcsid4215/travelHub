package com.bwc.approval_workflow_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.bwc.approval_workflow_service.workflow.EnhancedWorkflowWorkers;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class ManagerDebugController {
    
    private final EnhancedWorkflowWorkers workflowWorkers;
    
    @GetMapping("/manager-assignment/{employeeId}")
    public String debugManagerAssignment(@PathVariable String employeeId) {
        workflowWorkers.debugManagerAssignment(employeeId);
        return "Check logs for manager assignment debug info";
    }
}