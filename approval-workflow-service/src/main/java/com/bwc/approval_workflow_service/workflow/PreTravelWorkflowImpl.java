package com.bwc.approval_workflow_service.workflow;

import java.time.Duration;
import java.util.UUID;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreTravelWorkflowImpl implements PreTravelWorkflow {

    private final TravelActivities activities = Workflow.newActivityStub(
            TravelActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .build()
    );

    private volatile boolean managerDecisionReceived = false;
    private volatile String managerDecision = null;
    private volatile boolean travelDeskSignalReceived = false;
    private volatile boolean travelDeskBooked = false;
    private volatile boolean overpriced = false;
    private volatile boolean hrComplianceDone = false;
    private volatile boolean financeDecisionReceived = false;
    private volatile String financeDecision = null;

    @Override
    public void start(UUID travelRequestId) {
        String trId = travelRequestId.toString();
        log.info("Workflow started for travelRequestId: {}", trId);

        activities.emitWorkflowStatusEvent(trId, "UNDER_REVIEW");
        activities.notifyManager("managerId-placeholder", trId);

        Workflow.await(() -> managerDecisionReceived);

        if (!"APPROVE".equalsIgnoreCase(managerDecision)) {
            activities.emitWorkflowStatusEvent(trId, "REJECTED_BY_MANAGER");
            return;
        }

        activities.emitWorkflowStatusEvent(trId, "TRAVEL_DESK_CHECK");
        Promise<Void> timeout = Workflow.newTimer(Duration.ofHours(1));
        Workflow.await(() -> travelDeskSignalReceived || timeout.isCompleted());

        if (!travelDeskSignalReceived) {
            boolean booked = activities.tryBookTickets(trId);
            if (!booked) {
                activities.emitWorkflowStatusEvent(trId, "NEEDS_FINANCE_APPROVAL");
                Workflow.await(() -> financeDecisionReceived);
                if (!"APPROVE".equalsIgnoreCase(financeDecision)) {
                    activities.emitWorkflowStatusEvent(trId, "REJECTED_BY_FINANCE");
                    return;
                }
            } else {
                activities.emitWorkflowStatusEvent(trId, "TRAVEL_DESK_BOOKED");
            }
        } else if (overpriced) {
            activities.emitWorkflowStatusEvent(trId, "NEEDS_FINANCE_APPROVAL");
            Workflow.await(() -> financeDecisionReceived);
            if (!"APPROVE".equalsIgnoreCase(financeDecision)) {
                activities.emitWorkflowStatusEvent(trId, "REJECTED_BY_FINANCE");
                return;
            }
        }

        activities.emitWorkflowStatusEvent(trId, "HR_COMPLIANCE");
        Workflow.await(() -> hrComplianceDone);
        activities.notifyHR(trId);

        activities.emitWorkflowStatusEvent(trId, "PRE_TRAVEL_COMPLETED");

        // start PostTravel child workflow
        ChildWorkflowOptions opts = ChildWorkflowOptions.newBuilder()
                .setWorkflowId(trId + ":post")
                .setTaskQueue("TRAVEL_TASK_QUEUE")
                .build();

        PostTravelWorkflow post = Workflow.newChildWorkflowStub(PostTravelWorkflow.class, opts);
        Async.procedure(() -> post.startPost(travelRequestId));
    }

    // signals
    @Override
    public void managerAction(String decision, String comments) {
        this.managerDecision = decision;
        this.managerDecisionReceived = true;
    }

    @Override
    public void travelDeskResult(boolean booked, boolean overpriced, double estimatedCost) {
        this.travelDeskSignalReceived = true;
        this.travelDeskBooked = booked;
        this.overpriced = overpriced;
    }

    @Override
    public void financeDecision(String decision, String comments) {
        this.financeDecision = decision;
        this.financeDecisionReceived = true;
    }

    @Override
    public void hrComplianceDone() {
        this.hrComplianceDone = true;
    }

    @Override
    public void travelDeskCheckResult(boolean withinBudget) {
        this.travelDeskSignalReceived = true;
        this.overpriced = !withinBudget;
    }

    @Override
    public void travelDeskBookingDone() {
        this.travelDeskSignalReceived = true;
        this.travelDeskBooked = true;
    }
}
