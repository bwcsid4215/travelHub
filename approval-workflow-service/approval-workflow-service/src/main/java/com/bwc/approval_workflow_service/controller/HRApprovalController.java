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
@RequestMapping("/api/hr/approvals")
@RequiredArgsConstructor
@Tag(name = "HR Approvals", description = "HR approval workflows")
public class HRApprovalController {

    private final ApprovalWorkflowService workflowService;

    @Operation(summary = "Get pending HR approvals")
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals(@RequestParam UUID hrId) {
        return ResponseEntity.ok(workflowService.getPendingApprovals("HR", hrId));
    }

    @Operation(summary = "Get HR approval statistics")
    @GetMapping("/stats")
    public ResponseEntity<?> getApprovalStats(@RequestParam UUID hrId) {
        return ResponseEntity.ok(workflowService.getApprovalStatsByApprover(hrId));
    }
}
