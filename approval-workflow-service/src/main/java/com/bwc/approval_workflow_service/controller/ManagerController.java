// src/main/java/com/bwc/approval_workflow_service/controller/ManagerController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final WorkflowClient workflowClient;

    public ManagerController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/approve")
    public ResponseEntity<String> approve(@PathVariable String travelRequestId,
                                          @RequestParam(required = false) String comments) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.managerAction("APPROVE", comments);
        return ResponseEntity.ok("Manager approved " + travelRequestId);
    }

    @PostMapping("/{travelRequestId}/reject")
    public ResponseEntity<String> reject(@PathVariable String travelRequestId,
                                         @RequestParam(required = false) String comments) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.managerAction("REJECT", comments);
        return ResponseEntity.ok("Manager rejected " + travelRequestId);
    }
}
