package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowStartController {

    private final WorkflowClient workflowClient;

    public WorkflowStartController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/pre-travel/{travelRequestId}/start")
    public ResponseEntity<String> startPreTravel(@PathVariable String travelRequestId) {
        UUID requestUUID = UUID.fromString(travelRequestId);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("TRAVEL_TASK_QUEUE")
                .setWorkflowId(requestUUID + ":pre")
                .build();

        PreTravelWorkflow wf = workflowClient.newWorkflowStub(PreTravelWorkflow.class, options);
        WorkflowClient.start(() -> wf.start(requestUUID));

        return ResponseEntity.ok("✅ PRE_TRAVEL started for " + travelRequestId);
    }
}
