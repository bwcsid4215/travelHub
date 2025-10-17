package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final WorkflowClient workflowClient;

    public ManagerController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/approve")
    public ResponseEntity<String> approve(@PathVariable UUID travelRequestId,
                                          @RequestParam(required = false) String comments) {

        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);
        stub.managerAction("APPROVE", comments);

        return ResponseEntity.ok("Manager approved travel request " + travelRequestId);
    }

    @PostMapping("/{travelRequestId}/reject")
    public ResponseEntity<String> reject(@PathVariable UUID travelRequestId,
                                         @RequestParam(required = false) String comments) {

        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);
        stub.managerAction("REJECT", comments);

        return ResponseEntity.ok("Manager rejected travel request " + travelRequestId);
    }
}
