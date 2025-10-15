package com.bwc.approval_workflow_service.kafka;

import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;
import com.bwc.approval_workflow_service.dto.TravelRequestProxyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelRequestConsumer {

    private final ApprovalWorkflowService workflowService;

    @KafkaListener(topics = "travel-request-events", groupId = "workflow-group")
    public void consumeTravelRequest(TravelRequestProxyDTO travelRequest) {
        log.info("📥 Received travel request event: {}", travelRequest);
        try {
            workflowService.initiateWorkflow(travelRequest, "PRE_TRAVEL", travelRequest.getEstimatedBudget());
            log.info("✅ Workflow initiated for TravelRequest ID: {}", travelRequest.getTravelRequestId());
        } catch (Exception e) {
            log.error("❌ Failed to initiate workflow: {}", e.getMessage(), e);
        }
    }
}
