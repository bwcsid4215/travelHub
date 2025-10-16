// src/main/java/com/bwc/approval_workflow_service/controller/TravelDeskController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-desk")
public class TravelDeskController {

    private final WorkflowClient workflowClient;

    public TravelDeskController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    // result of policy/options check
    @PostMapping("/{travelRequestId}/check")
    public ResponseEntity<String> check(@PathVariable String travelRequestId,
                                        @RequestParam boolean withinBudget) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.travelDeskCheckResult(withinBudget);
        return ResponseEntity.ok("Travel desk check recorded for " + travelRequestId);
    }

    // booking finished (tickets uploaded etc.)
    @PostMapping("/{travelRequestId}/booking-done")
    public ResponseEntity<String> bookingDone(@PathVariable String travelRequestId) {
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, travelRequestId);
        stub.travelDeskBookingDone();
        return ResponseEntity.ok("Booking completed for " + travelRequestId);
    }

    // post-travel: bills reviewed by travel desk
    @PostMapping("/{travelRequestId}/bills-reviewed")
    public ResponseEntity<String> billsReviewed(@PathVariable String travelRequestId) {
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class, travelRequestId + ":post");
        // NOTE: we’ll use child workflow default id unless you want fixed id; see note below.
        stub.billsReviewedByTravelDesk();
        return ResponseEntity.ok("Bills reviewed for " + travelRequestId);
    }
}
