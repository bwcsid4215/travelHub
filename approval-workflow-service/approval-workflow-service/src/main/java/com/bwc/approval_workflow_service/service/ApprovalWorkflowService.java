package com.bwc.approval_workflow_service.service;

import com.bwc.approval_workflow_service.dto.*;
import java.util.List;
import java.util.UUID;

public interface ApprovalWorkflowService {

    default ApprovalWorkflowDTO initiateWorkflow(UUID travelRequestId) {
        return initiateWorkflow(travelRequestId, "PRE_TRAVEL", null);
    }

    ApprovalWorkflowDTO initiateWorkflow(UUID travelRequestId, String workflowType, Double estimatedCost);

    ApprovalWorkflowDTO processApproval(ApprovalRequestDTO approvalRequest);

    ApprovalWorkflowDTO getWorkflowByRequestId(UUID travelRequestId);

    ApprovalWorkflowDTO getWorkflow(UUID workflowId);

    List<ApprovalWorkflowDTO> getPendingApprovals(String approverRole, UUID approverId);

    List<ApprovalWorkflowDTO> getPendingApprovalsByRole(String approverRole);

    List<ApprovalWorkflowDTO> getWorkflowsByStatus(String status);

    List<ApprovalActionDTO> getWorkflowHistory(UUID travelRequestId);

    ApprovalWorkflowDTO escalateWorkflow(UUID workflowId, String reason, UUID escalatedBy);

    ApprovalWorkflowDTO reassignWorkflow(UUID workflowId, String newApproverRole, UUID newApproverId);

    ApprovalWorkflowDTO updateWorkflowPriority(UUID workflowId, String priority);

    WorkflowMetricsDTO getWorkflowMetrics();

    List<ApprovalStatsDTO> getApprovalStatsByApprover(UUID approverId);

    void reloadWorkflowConfigurations();

    ApprovalWorkflowDTO markBookingUploaded(UUID workflowId, UUID uploadedBy);
    
    // Add the missing method
    ApprovalWorkflowDTO uploadBills(UUID workflowId, Double actualCost, UUID uploadedBy);
}