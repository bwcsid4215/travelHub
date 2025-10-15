package com.bwc.approval_workflow_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowStatusProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "workflow-status-events";

    public void sendWorkflowStatus(WorkflowStatusEvent event) {
        kafkaTemplate.send(TOPIC, event);
        log.info("📤 Sent workflow status event [{}]: {} - {}", TOPIC, event.getTravelRequestId(), event.getStatus());
    }
}
