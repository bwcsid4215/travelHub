// src/main/java/com/bwc/approval_workflow_service/controller/PostTravelController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post-travel")
public class PostTravelController {

    private final WorkflowClient workflowClient;

    public PostTravelController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{travelRequestId}/bills-uploaded")
    public ResponseEntity<String> billsUploaded(@PathVariable String travelRequestId) {
        PostTravelWorkflow stub = workflowClient.newWorkflowStub(PostTravelWorkflow.class, travelRequestId + ":post");
        stub.billsUploaded();
        return ResponseEntity.ok("Bills uploaded for " + travelRequestId);
    }
}
