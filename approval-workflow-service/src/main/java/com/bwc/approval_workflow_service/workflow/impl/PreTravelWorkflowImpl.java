package com.bwc.approval_workflow_service.workflow.impl;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.TravelActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class PreTravelWorkflowImpl implements PreTravelWorkflow {

    // ===============================
    // 🧠 State Variables
    // ===============================
    private String requestId;
    private boolean managerDone = false;
    private boolean managerApproved = false;

    private boolean deskChecked = false;
    private boolean withinBudget = true;

    private boolean financeDone = false;
    private boolean financeApproved = false;

    private boolean bookingDone = false;
    private boolean hrDone = false;

    // ===============================
    // ⚙️ Activity Stub
    // ===============================
    private final TravelActivities activities = Workflow.newActivityStub(
            TravelActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .build()
    );

    // ===============================
    // 🚀 Workflow Logic
    // ===============================
    @Override
    public void start(String travelRequestId) {
        this.requestId = travelRequestId;
        log.info("🚀 PRE_TRAVEL workflow started for {}", requestId);

        activities.notifyUser(requestId, "PRE_TRAVEL Started", "Manager approval required");

        // 1️⃣ MANAGER_APPROVAL
        log.info("🕒 Waiting for MANAGER_APPROVAL...");
        Workflow.await(() -> managerDone);
        activities.recordManagerDecision(requestId, managerApproved ? "APPROVE" : "REJECT", null);

        if (!managerApproved) {
            log.info("❌ Manager rejected request {}", requestId);
            activities.notifyUser(requestId, "PRE_TRAVEL Rejected", "Manager rejected the request");
            return;
        }

        // 2️⃣ TRAVEL_DESK_CHECK
        activities.notifyUser(requestId, "Travel Desk Check", "Validating selected options/policy");
        log.info("🕒 Waiting for TRAVEL_DESK_CHECK result...");
        Workflow.await(() -> deskChecked);

        if (!withinBudget) {
            // 3️⃣ FINANCE_APPROVAL (if overpriced)
            activities.notifyUser(requestId, "Finance Approval", "Overpriced — finance approval required");
            log.info("💰 Waiting for FINANCE_APPROVAL...");
            Workflow.await(() -> financeDone);

            if (!financeApproved) {
                log.info("❌ Finance rejected request {}", requestId);
                activities.notifyUser(requestId, "PRE_TRAVEL Rejected", "Finance rejected overpriced request");
                return;
            }
        }

        // 4️⃣ TRAVEL_DESK_BOOKING
        activities.notifyUser(requestId, "Travel Desk Booking", "Proceed to book tickets");
        log.info("🕒 Waiting for TRAVEL_DESK_BOOKING completion...");
        Workflow.await(() -> bookingDone);
        activities.doTravelDeskBooking(requestId);

        // 5️⃣ HR_COMPLIANCE
        activities.notifyUser(requestId, "HR Compliance", "HR to verify compliance");
        log.info("🕒 Waiting for HR_COMPLIANCE completion...");
        Workflow.await(() -> hrDone);
        activities.doHRCompliance(requestId);

        // ✅ All steps done
        activities.notifyUser(requestId, "PRE_TRAVEL Completed", "All steps finished");
        log.info("✅ PRE_TRAVEL workflow completed successfully for {}", requestId);

        // Continue to PostTravel phase
        PostTravelWorkflow next = Workflow.newChildWorkflowStub(PostTravelWorkflow.class);
        next.start(requestId);
    }

    // ===============================
    // 📥 Signal Handlers
    // ===============================
    @Override
    public void managerAction(String action, String comments) {
        managerApproved = "APPROVE".equalsIgnoreCase(action);
        managerDone = true;
        log.info("📨 Manager action received: {} | comments={}", action, comments);
    }

    @Override
    public void travelDeskCheckResult(boolean withinBudget) {
        this.withinBudget = withinBudget;
        this.deskChecked = true;
        log.info("📨 Travel desk check completed | withinBudget={}", withinBudget);
    }

    @Override
    public void financeAction(String action) {
        financeApproved = "APPROVE".equalsIgnoreCase(action);
        financeDone = true;
        log.info("📨 Finance action received: {}", action);
    }

    @Override
    public void travelDeskBookingDone() {
        bookingDone = true;
        log.info("📨 Travel desk booking completed");
    }

    @Override
    public void hrComplianceDone() {
        hrDone = true;
        log.info("📨 HR compliance completed");
    }
}
