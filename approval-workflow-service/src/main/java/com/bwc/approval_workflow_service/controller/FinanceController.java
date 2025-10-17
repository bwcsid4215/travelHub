package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final WorkflowClient workflowClient;

    public FinanceController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    private WorkflowOptions preOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("TRAVEL_TASK_QUEUE")
                .build();
    }

    @PostMapping("/{travelRequestId}/approve-overpriced")
    public ResponseEntity<String> approveOverpriced(@PathVariable String travelRequestId) {
        UUID id = UUID.fromString(travelRequestId);
        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, preOptions(workflowId));
        stub.financeDecision("APPROVE", "Approved by finance");
        return ResponseEntity.ok("Finance approved overpriced for " + id);
    }

    @PostMapping("/{travelRequestId}/reject-overpriced")
    public ResponseEntity<String> rejectOverpriced(@PathVariable String travelRequestId) {
        UUID id = UUID.fromString(travelRequestId);
        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, preOptions(workflowId));
        stub.financeDecision("REJECT", "Rejected by finance");
        return ResponseEntity.ok("Finance rejected overpriced for " + id);
    }

    @PostMapping("/{travelRequestId}/reimbursed")
    public ResponseEntity<String> reimbursed(@PathVariable String travelRequestId) {
        UUID id = UUID.fromString(travelRequestId);
        String postId = travelRequestId + ":post";
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(postId).setTaskQueue("TRAVEL_TASK_QUEUE").build());
        stub.financeReimbursed();
        return ResponseEntity.ok("Reimbursement completed for " + id);
    }
}
