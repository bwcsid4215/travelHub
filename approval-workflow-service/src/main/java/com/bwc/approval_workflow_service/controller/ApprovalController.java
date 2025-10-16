package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workflow/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final WorkflowClient workflowClient;
    private final ApprovalWorkflowService approvalWorkflowService;

    @PostMapping("/{workflowId}/action")
    public ResponseEntity<String> takeAction(
            @PathVariable UUID workflowId,
            @RequestParam String step,
            @RequestParam String action,
            @RequestParam(required = false) String comments
    ) {
        // Create an untyped stub for the workflow
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId.toString());

        // Send signal to the workflow method "handleAction"
        stub.signal("handleAction", step, action, comments);

        // Record in DB (audit trail, optional)
        approvalWorkflowService.recordAction(workflowId, step, action, comments);

        return ResponseEntity.ok("✅ " + step + " " + action + " successfully recorded for " + workflowId);
    }
}
