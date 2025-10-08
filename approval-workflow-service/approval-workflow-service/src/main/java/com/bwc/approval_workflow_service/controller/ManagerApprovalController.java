package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.ApprovalRequestDTO;
import com.bwc.approval_workflow_service.dto.ApprovalWorkflowDTO;
import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerApprovalController {

    private final ApprovalWorkflowService workflowService;

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals(
            @RequestParam UUID managerId,
            @RequestParam(required = false) String priority) {

        List<ApprovalWorkflowDTO> approvals = workflowService.getPendingApprovals("MANAGER", managerId);

        if (priority != null) {
            approvals = approvals.stream()
                    .filter(a -> priority.equalsIgnoreCase(a.getPriority()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(approvals);
    }

    @PostMapping("/approvals/{workflowId}/action")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApprovalWorkflowDTO> takeManagerAction(
            @PathVariable UUID workflowId,
            @RequestBody ApprovalRequestDTO approvalRequest) {

        approvalRequest.setWorkflowId(workflowId);
        approvalRequest.setApproverRole("MANAGER");
        return ResponseEntity.ok(workflowService.processApproval(approvalRequest));
    }
}
