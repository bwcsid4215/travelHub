package com.bwc.approval_workflow_service.kafka;

import com.bwc.approval_workflow_service.dto.TravelRequestProxyDTO;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TravelRequestConsumer {

    private final WorkflowClient workflowClient;

    @KafkaListener(topics = "travel-request-events", groupId = "workflow-group")
    public void consumeTravelRequest(TravelRequestProxyDTO travel) {
        log.info("📥 Received travel request event: {}", travel);
        String workflowId = travel.getTravelRequestId().toString() + ":pre";

        PreTravelWorkflow stub = workflowClient.newWorkflowStub(
                PreTravelWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("TRAVEL_TASK_QUEUE")
                        .setWorkflowId(workflowId)
                        .build());

        // Start non-blocking
        try {
            WorkflowClient.start(() -> stub.start(travel.getTravelRequestId()));
            log.info("✅ Workflow initiated for TravelRequest ID: {}", travel.getTravelRequestId());
        } catch (Exception e) {
            log.error("Failed to start workflow for {}: {}", travel.getTravelRequestId(), e.getMessage(), e);
        }
    }
}
