// src/main/java/com/bwc/approval_workflow_service/workflow/TravelActivities.java
package com.bwc.approval_workflow_service.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface TravelActivities {

    // notifications
    @ActivityMethod void notifyUser(String requestId, String subject, String message);

    // PRE_TRAVEL steps (use your services under the hood)
    @ActivityMethod void recordManagerDecision(String requestId, String action, String comments);
    @ActivityMethod boolean travelDeskPolicyCheck(String requestId); // true = within budget, false = overpriced
    @ActivityMethod boolean financeApproveOverpriced(String requestId);
    @ActivityMethod void doTravelDeskBooking(String requestId);
    @ActivityMethod void doHRCompliance(String requestId);

    // POST_TRAVEL steps
    @ActivityMethod void markLateBill(String requestId);
    @ActivityMethod void reviewBillsByTravelDesk(String requestId);
    @ActivityMethod void processFinanceReimbursement(String requestId);
}
