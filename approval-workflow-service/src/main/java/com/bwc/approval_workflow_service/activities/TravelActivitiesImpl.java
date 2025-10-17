package com.bwc.approval_workflow_service.activities;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.bwc.approval_workflow_service.workflow.TravelActivities;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TravelActivitiesImpl implements TravelActivities {

    private final WebClient web = WebClient.create();

    @Override
    public void notifyManager(String managerId, String travelRequestId) {
        // placeholder - call notification service or email
        log.info("Activity: notifyManager {} for travelRequest {}", managerId, travelRequestId);
        // e.g. POST to notification service
    }

    @Override
    public boolean tryBookTickets(String travelRequestId) {
        // Simulate booking logic; call travel desk / booking adapter
        log.info("Activity: try booking tickets for {}", travelRequestId);
        // return true if booked, false if needs finance approval
        return true; // change as per real logic
    }

    @Override
    public void notifyHR(String travelRequestId) {
        log.info("Activity: notify HR for {}", travelRequestId);
    }

    @Override
    public void notifyFinance(String travelRequestId, String reason) {
        log.info("Activity: notify Finance for {} reason {}", travelRequestId, reason);
    }

    @Override
    public void emitWorkflowStatusEvent(String travelRequestId, String status) {
        log.info("Activity: emit workflow status [{}] for {}", status, travelRequestId);
        // publish to Kafka or update DB (placeholder)
    }
}
