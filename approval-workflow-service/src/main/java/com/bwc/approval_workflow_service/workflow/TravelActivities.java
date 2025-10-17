// src/main/java/com/bwc/approval_workflow_service/workflow/TravelActivities.java
package com.bwc.approval_workflow_service.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface TravelActivities {

    @ActivityMethod
    void notifyManager(String managerId, String travelRequestId);

    @ActivityMethod
    boolean tryBookTickets(String travelRequestId);

    @ActivityMethod
    void notifyHR(String travelRequestId);

    @ActivityMethod
    void notifyFinance(String travelRequestId, String reason);

    @ActivityMethod
    void emitWorkflowStatusEvent(String travelRequestId, String status);
}
