package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApprovalController {

    private final WorkflowClient workflowClient;

    private String preId(UUID travelRequestId) {
        return travelRequestId.toString() + ":pre";
    }

    private String postId(UUID travelRequestId) {
        return travelRequestId.toString() + ":post";
    }

    // ✅ Manager decision
    @PostMapping("/pre/{travelRequestId}/manager")
    public ResponseEntity<String> managerAction(
            @PathVariable UUID travelRequestId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments) {

        String workflowId = preId(travelRequestId);
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);
        stub.managerAction(decision, comments);

        log.info("✅ Manager action ({}) signaled for workflow: {}", decision, workflowId);
        return ResponseEntity.ok("Manager action signaled for " + workflowId);
    }

    // ✅ Travel Desk result
    @PostMapping("/pre/{travelRequestId}/traveldesk")
    public ResponseEntity<String> travelDesk(
            @PathVariable UUID travelRequestId,
            @RequestParam boolean booked,
            @RequestParam(defaultValue = "false") boolean overpriced,
            @RequestParam(defaultValue = "0.0") double estimatedCost) {

        String workflowId = preId(travelRequestId);
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);
        stub.travelDeskResult(booked, overpriced, estimatedCost);

        log.info("✅ Travel desk result signaled for workflow: {}", workflowId);
        return ResponseEntity.ok("Travel desk result signaled");
    }

    // ✅ Finance approval
    @PostMapping("/pre/{travelRequestId}/finance")
    public ResponseEntity<String> financeAction(
            @PathVariable UUID travelRequestId,
            @RequestParam String decision,
            @RequestParam(required = false) String comments) {

        String workflowId = preId(travelRequestId);
        PreTravelWorkflow stub = workflowClient.newWorkflowStub(PreTravelWorkflow.class, workflowId);
        stub.financeDecision(decision, comments);

        log.info("✅ Finance action ({}) signaled for workflow: {}", decision, workflowId);
        return ResponseEntity.ok("Finance action signaled");
    }

    // ✅ Post travel bills uploaded
    @PostMapping("/post/{travelRequestId}/bills-uploaded")
    public ResponseEntity<String> billsUploaded(
            @PathVariable UUID travelRequestId,
            @RequestParam UUID uploadedBy) {

        String workflowId = postId(travelRequestId);
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class, workflowId);
        stub.billsUploaded(uploadedBy);

        log.info("✅ Bills uploaded signal sent for {}", workflowId);
        return ResponseEntity.ok("Bills uploaded signal sent");
    }
}
