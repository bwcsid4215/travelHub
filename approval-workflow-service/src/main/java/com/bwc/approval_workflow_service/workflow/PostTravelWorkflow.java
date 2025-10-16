// src/main/java/com/bwc/approval_workflow_service/workflow/PostTravelWorkflow.java
package com.bwc.approval_workflow_service.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PostTravelWorkflow {

    @WorkflowMethod
    void start(String travelRequestId);

    // signals
    @SignalMethod void billsUploaded();              // called by employee action
    @SignalMethod void billsReviewedByTravelDesk();  // called by travel desk
    @SignalMethod void financeReimbursed();          // called by finance
}
