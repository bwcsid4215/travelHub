package com.bwc.travel_request_management.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bwc.travel_request_management.service.TravelRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowStatusConsumer {

    private final TravelRequestService travelRequestService;

    @KafkaListener(topics = "workflow-status-events", groupId = "travel-request-group")
    public void consumeWorkflowStatus(WorkflowStatusEvent event) {
        log.info("📥 Received workflow status event: {} - {}", event.getTravelRequestId(), event.getStatus());
        travelRequestService.updateStatus(event.getTravelRequestId(), event.getStatus());
    }
}
