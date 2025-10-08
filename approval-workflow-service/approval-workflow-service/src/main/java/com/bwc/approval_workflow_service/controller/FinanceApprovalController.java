package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.ApprovalWorkflowDTO;
import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/approvals")
@RequiredArgsConstructor
@Tag(name = "Finance Approvals", description = "Finance team approval workflows")
public class FinanceApprovalController {

    private final ApprovalWorkflowService workflowService;

    @Operation(summary = "Get pending approvals for Finance")
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals(@RequestParam UUID financeId) {
        return ResponseEntity.ok(workflowService.getPendingApprovals("FINANCE", financeId));
    }

    @Operation(summary = "Get Finance approval statistics")
    @GetMapping("/stats")
    public ResponseEntity<?> getApprovalStats(@RequestParam UUID financeId) {
        return ResponseEntity.ok(workflowService.getApprovalStatsByApprover(financeId));
    }
}
