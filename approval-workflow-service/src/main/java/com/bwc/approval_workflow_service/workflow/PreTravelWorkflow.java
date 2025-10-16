// src/main/java/com/bwc/approval_workflow_service/workflow/PreTravelWorkflow.java
package com.bwc.approval_workflow_service.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PreTravelWorkflow {

    @WorkflowMethod
    void start(String travelRequestId);

    // signals from controllers (stakeholders)
    @SignalMethod void managerAction(String action, String comments); // APPROVE / REJECT
    @SignalMethod void travelDeskCheckResult(boolean withinBudget);   // true=OK, false=overpriced
    @SignalMethod void financeAction(String action);                  // APPROVE / REJECT (for overpriced)
    @SignalMethod void travelDeskBookingDone();                       // booking completed
    @SignalMethod void hrComplianceDone();                            // hr completed
}
