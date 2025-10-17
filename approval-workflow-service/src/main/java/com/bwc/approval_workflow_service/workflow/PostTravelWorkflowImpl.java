package com.bwc.approval_workflow_service.workflow;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostTravelWorkflowImpl implements PostTravelWorkflow {

    private final TravelActivities activities = Workflow.newActivityStub(
            TravelActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .build()
    );

    private volatile boolean billsUploaded = false;
    private volatile boolean billsReviewed = false;
    private volatile boolean financeReimbursed = false;

    @Override
    public void startPost(UUID travelRequestId) {
        String trId = travelRequestId.toString();
        log.info("Post-travel workflow started for {}", trId);
        activities.emitWorkflowStatusEvent(trId, "POST_TRAVEL_STARTED");

        // Wait up to 7 days for bills upload
        Workflow.await(Duration.ofDays(7), () -> billsUploaded);

        if (!billsUploaded) {
            activities.emitWorkflowStatusEvent(trId, "LATE_BILL_UPLOAD");
            Workflow.await(() -> billsUploaded);
        }

        // Travel Desk reviews bills
        activities.emitWorkflowStatusEvent(trId, "TRAVEL_DESK_BILL_REVIEW");
        Workflow.await(() -> billsReviewed);

        // Once reviewed, go to finance
        activities.emitWorkflowStatusEvent(trId, "FINANCE_REIMBURSEMENT");
        Workflow.await(() -> financeReimbursed);

        activities.emitWorkflowStatusEvent(trId, "POST_TRAVEL_COMPLETED");
    }

    @Override
    public void billsUploaded(UUID uploadedBy) {
        this.billsUploaded = true;
        log.info("Signal received: bills uploaded by {}", uploadedBy);
    }

    @Override
    public void billsReviewedByTravelDesk() {
        this.billsReviewed = true;
        log.info("Signal received: bills reviewed by travel desk");
    }

    @Override
    public void financeReimbursed() {
        this.financeReimbursed = true;
        log.info("Signal received: finance reimbursed");
    }
}
