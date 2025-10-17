package com.bwc.approval_workflow_service.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.UUID;

@WorkflowInterface
public interface PreTravelWorkflow {

    @WorkflowMethod
    void start(UUID travelRequestId);

    @SignalMethod
    void managerAction(String decision, String comments);

    @SignalMethod
    void travelDeskResult(boolean booked, boolean overpriced, double estimatedCost);

    @SignalMethod
    void financeDecision(String decision, String comments);

    @SignalMethod
    void hrComplianceDone();

    @SignalMethod
    void travelDeskCheckResult(boolean withinBudget);

    @SignalMethod
    void travelDeskBookingDone();
}
