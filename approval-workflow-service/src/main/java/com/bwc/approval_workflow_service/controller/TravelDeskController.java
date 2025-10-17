package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-desk")
public class TravelDeskController {

    private final WorkflowClient workflowClient;

    public TravelDeskController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/check")
    public ResponseEntity<String> check(@PathVariable String travelRequestId,
                                        @RequestParam boolean withinBudget) {
        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue("TRAVEL_TASK_QUEUE").build());
        stub.travelDeskCheckResult(withinBudget);
        return ResponseEntity.ok("Travel desk check recorded for " + travelRequestId);
    }

    @PostMapping("/{travelRequestId}/booking-done")
    public ResponseEntity<String> bookingDone(@PathVariable String travelRequestId) {
        String workflowId = travelRequestId + ":pre";
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue("TRAVEL_TASK_QUEUE").build());
        stub.travelDeskBookingDone();
        return ResponseEntity.ok("Booking completed for " + travelRequestId);
    }
}
