// src/main/java/com/bwc/approval_workflow_service/controller/HRController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr")
public class HRController {

    private final WorkflowClient workflowClient;

    public HRController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/compliance-done")
    public ResponseEntity<String> complianceDone(@PathVariable String travelRequestId) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.hrComplianceDone();
        return ResponseEntity.ok("HR compliance completed for " + travelRequestId);
    }
}
