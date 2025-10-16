package com.bwc.approval_workflow_service.workflow.impl;

import com.bwc.approval_workflow_service.workflow.PostTravelWorkflow;
import com.bwc.approval_workflow_service.workflow.TravelActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class PostTravelWorkflowImpl implements PostTravelWorkflow {

    private String requestId;

    private boolean billsUploaded = false;
    private boolean reviewed = false;
    private boolean reimbursed = false;

    private final TravelActivities activities = Workflow.newActivityStub(
            TravelActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .build()
    );

    @Override
    public void start(String travelRequestId) {
        this.requestId = travelRequestId;
        log.info("🚀 POST_TRAVEL workflow started for {}", requestId);

        // Step 1️⃣ Ask employee to upload bills
        activities.notifyUser(requestId, "POST_TRAVEL Started", "Upload bills within 7 days of travel completion");

        // Wait up to 7 days for upload
        boolean uploadedOnTime = Workflow.await(Duration.ofDays(7), () -> billsUploaded);

        if (!uploadedOnTime) {
            log.warn("⚠️ Bills not uploaded within 7 days for {}", requestId);
            activities.markLateBill(requestId);
            activities.notifyUser(requestId, "Late Bill Upload", "Bills were not uploaded within 7 days");

            // Wait until uploaded (no deadline now)
            Workflow.await(() -> billsUploaded);
        }

        // Step 2️⃣ Travel Desk Review
        log.info("🧾 Awaiting travel desk review for {}", requestId);
        activities.notifyUser(requestId, "Travel Desk Bill Review", "Desk to review uploaded bills");
        Workflow.await(() -> reviewed);
        activities.reviewBillsByTravelDesk(requestId);

        // Step 3️⃣ Finance Reimbursement
        log.info("💰 Awaiting finance reimbursement for {}", requestId);
        activities.notifyUser(requestId, "Finance Reimbursement", "Processing reimbursement");
        Workflow.await(() -> reimbursed);
        activities.processFinanceReimbursement(requestId);

        // Step 4️⃣ Completion
        activities.notifyUser(requestId, "POST_TRAVEL Completed", "Reimbursement completed");
        log.info("✅ POST_TRAVEL workflow completed for {}", requestId);
    }

    // ===============================
    // 📥 Signal Handlers
    // ===============================
    @Override
    public void billsUploaded() {
        this.billsUploaded = true;
        log.info("📨 Bills uploaded signal received for {}", requestId);
    }

    @Override
    public void billsReviewedByTravelDesk() {
        this.reviewed = true;
        log.info("📨 Travel desk bills review done for {}", requestId);
    }

    @Override
    public void financeReimbursed() {
        this.reimbursed = true;
        log.info("📨 Finance reimbursement done for {}", requestId);
    }
}
