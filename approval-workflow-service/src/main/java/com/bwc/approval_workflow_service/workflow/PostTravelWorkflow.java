package com.bwc.approval_workflow_service.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.UUID;

@WorkflowInterface
public interface PostTravelWorkflow {

    @WorkflowMethod
    void startPost(UUID travelRequestId);

    @SignalMethod
    void billsUploaded(UUID uploadedBy);

    @SignalMethod
    void billsReviewedByTravelDesk();

    @SignalMethod
    void financeReimbursed();
}
