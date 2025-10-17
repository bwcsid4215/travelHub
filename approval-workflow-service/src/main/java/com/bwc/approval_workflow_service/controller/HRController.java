package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HRController {

    private final WorkflowClient workflowClient;

    @PostMapping("/{travelRequestId}/compliance-done")
    public ResponseEntity<String> complianceDone(@PathVariable String travelRequestId) {
        try {
            String workflowId = travelRequestId + ":pre";
            
            // ✅ Correct way: connect to an existing workflow instance
            PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);

            // ✅ Send the signal
            stub.hrComplianceDone();

            System.out.println("✅ HR compliance completed for workflow: " + workflowId);
            return ResponseEntity.ok("HR compliance completed for " + travelRequestId);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("❌ Failed to signal HR compliance: " + e.getMessage());
        }
    }
}
