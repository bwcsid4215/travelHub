// src/main/java/com/bwc/approval_workflow_service/controller/WorkflowStartController.java
package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowStartController {

    private final WorkflowClient workflowClient;

    public WorkflowStartController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/pre-travel/{travelRequestId}/start")
    public ResponseEntity<String> startPreTravel(@PathVariable String travelRequestId) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("TRAVEL_TASK_QUEUE")
                .setWorkflowId(travelRequestId) // important: make it addressable by requestId
                .build();

        PreTravelWorkflow wf = workflowClient.newWorkflowStub(PreTravelWorkflow.class, options);
        WorkflowClient.start(wf::start, travelRequestId);

        return ResponseEntity.ok("✅ PRE_TRAVEL started for " + travelRequestId);
    }
}
