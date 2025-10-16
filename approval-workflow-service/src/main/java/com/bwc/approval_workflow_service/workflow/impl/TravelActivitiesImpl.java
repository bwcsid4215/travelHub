// src/main/java/com/bwc/approval_workflow_service/workflow/impl/TravelActivitiesImpl.java
package com.bwc.approval_workflow_service.workflow.impl;

import com.bwc.approval_workflow_service.workflow.TravelActivities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TravelActivitiesImpl implements TravelActivities {

    @Override
    public void notifyUser(String requestId, String subject, String message) {
        log.info("📣 [{}] {} - {}", requestId, subject, message);
        // TODO: call your notification-service here
    }

    @Override
    public void recordManagerDecision(String requestId, String action, String comments) {
        log.info("👔 Manager {} for {} (comments: {})", action, requestId, comments);
        // TODO: persist decision / call approval service as needed
    }

    @Override
    public boolean travelDeskPolicyCheck(String requestId) {
        log.info("🧾 Travel Desk policy/option check for {}", requestId);
        // TODO: check against policy-service, employee level, etc.
        return true; // return false to route to FinanceApproval
    }

    @Override
    public boolean financeApproveOverpriced(String requestId) {
        log.info("💰 Finance approval (overpriced) for {}", requestId);
        // TODO: call finance service/approval
        return true;
    }

    @Override
    public void doTravelDeskBooking(String requestId) {
        log.info("✈️ Booking (flights/hotels/trains) for {}", requestId);
        // TODO: call booking adapters + upload tickets to MinIO
    }

    @Override
    public void doHRCompliance(String requestId) {
        log.info("📋 HR compliance processed for {}", requestId);
        // TODO: call HR compliance checks
    }

    @Override
    public void markLateBill(String requestId) {
        log.info("⏱️ Bills late for {}", requestId);
        // TODO: update DB status: LATE_BILL_UPLOAD
    }

    @Override
    public void reviewBillsByTravelDesk(String requestId) {
        log.info("🧾 Travel Desk reviewed bills for {}", requestId);
        // TODO: verify attachments, totals, policy
    }

    @Override
    public void processFinanceReimbursement(String requestId) {
        log.info("💸 Finance reimbursement processed for {}", requestId);
        // TODO: create payout and mark reimbursed
    }
}
