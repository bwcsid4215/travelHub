package com.bwc.approval_workflow_service.kafka;

import com.bwc.approval_workflow_service.dto.TravelRequestProxyDTO;
import io.camunda.zeebe.client.ZeebeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TravelRequestConsumer {

    private final ZeebeClient zeebeClient;

    // Read groupId and other props from application.yml (don’t hard-code here)
    @KafkaListener(
        topics = "travel-request-events",
        groupId = "${spring.kafka.consumer.group-id}"
        // ack-mode is MANUAL_IMMEDIATE via application.yml
    )
    public void consume(
            TravelRequestProxyDTO travel,
            Acknowledgment ack,
            ConsumerRecord<String, TravelRequestProxyDTO> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        UUID travelId = travel.getTravelRequestId();
        log.info("📥 Received travel request: {} | key={} p{}@{}",
                travelId, key, partition, offset);

        // --- Build Zeebe variables (camelCase aligned to BPMN v4) ---
        Map<String, Object> vars = new HashMap<>();
        vars.put("travelRequestId", travelId != null ? travelId.toString() : null);
        vars.put("employeeId", travel.getEmployeeId() != null ? travel.getEmployeeId().toString() : null);
        vars.put("managerId", travel.getManagerId() != null ? travel.getManagerId().toString() : null);
        vars.put("projectId", travel.getProjectId() != null ? travel.getProjectId().toString() : null);

        vars.put("requestedAmount", travel.getEstimatedBudget() != null ? travel.getEstimatedBudget() : 0.0);
        vars.put("origin", travel.getOrigin() != null ? travel.getOrigin() : "Unknown");
        vars.put("destination", travel.getTravelDestination() != null ? travel.getTravelDestination() : "Unknown");
        vars.put("travelPurpose", travel.getPurpose() != null ? travel.getPurpose() : "NA");

        try {
            // Start the workflow (idempotency tip: consider using a business key later)
            zeebeClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId("travel_approval_v4")
                    .latestVersion()
                    .variables(vars)
                    .send()
                    .join();

            // ✅ Commit offset only after success
            ack.acknowledge();
            log.info("✅ Workflow instance started & offset committed for {}", travelId);

        } catch (Exception e) {
            // ❌ No ack -> container will retry (per your error handling/DLQ config)
            log.error("❌ Failed to start workflow for {} (key={} p{}@{}). Will retry. Cause: {}",
                    travelId, key, partition, offset, e.toString(), e);
        }
    }
}
