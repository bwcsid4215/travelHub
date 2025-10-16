// src/main/java/com/bwc/approval_workflow_service/controller/FinanceController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final WorkflowClient workflowClient;

    public FinanceController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/approve-overpriced")
    public ResponseEntity<String> approveOverpriced(@PathVariable String travelRequestId) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.financeAction("APPROVE");
        return ResponseEntity.ok("Finance approved overpriced for " + travelRequestId);
    }

    @PostMapping("/{travelRequestId}/reject-overpriced")
    public ResponseEntity<String> rejectOverpriced(@PathVariable String travelRequestId) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.financeAction("REJECT");
        return ResponseEntity.ok("Finance rejected overpriced for " + travelRequestId);
    }

    @PostMapping("/{travelRequestId}/reimbursed")
    public ResponseEntity<String> reimbursed(@PathVariable String travelRequestId) {
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class, travelRequestId + ":post");
        stub.financeReimbursed();
        return ResponseEntity.ok("Reimbursement completed for " + travelRequestId);
    }
}
