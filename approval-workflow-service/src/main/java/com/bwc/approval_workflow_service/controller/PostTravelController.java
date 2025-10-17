package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/post-travel")
public class PostTravelController {

    private final WorkflowClient workflowClient;

    public PostTravelController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/bills-uploaded")
    public ResponseEntity<String> billsUploaded(@PathVariable String travelRequestId,
                                                @RequestParam UUID uploadedBy) {
        UUID id = UUID.fromString(travelRequestId);
        String postId = id + ":post";
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(postId).setTaskQueue("TRAVEL_TASK_QUEUE").build());
        stub.billsUploaded(uploadedBy);
        return ResponseEntity.ok("Bills uploaded for " + id);
    }

    @PostMapping("/{travelRequestId}/bills-reviewed")
    public ResponseEntity<String> billsReviewed(@PathVariable String travelRequestId) {
        UUID id = UUID.fromString(travelRequestId);
        String postId = id + ":post";
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(postId).setTaskQueue("TRAVEL_TASK_QUEUE").build());
        stub.billsReviewedByTravelDesk();
        return ResponseEntity.ok("Bills reviewed for " + id);
    }
}
