package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.ApprovalRequestDTO;
import com.bwc.approval_workflow_service.dto.ApprovalWorkflowDTO;
import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-desk/approvals")
@RequiredArgsConstructor
@Tag(name = "Travel Desk Approvals", description = "Travel Desk approval workflows")
public class TravelDeskApprovalController {

    private final ApprovalWorkflowService workflowService;

    @Operation(summary = "Get pending Travel Desk approvals")
    @GetMapping("/pending")
    @PreAuthorize("hasRole('TRAVEL_DESK')")
    public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals() {
        return ResponseEntity.ok(workflowService.getPendingApprovalsByRole("TRAVEL_DESK"));
    }

    @Operation(summary = "Process Travel Desk approval")
    @PostMapping("/{workflowId}/action")
    @PreAuthorize("hasRole('TRAVEL_DESK')")
    public ResponseEntity<ApprovalWorkflowDTO> takeTravelDeskAction(
            @PathVariable UUID workflowId,
            @RequestBody ApprovalRequestDTO approvalRequest,
            HttpServletRequest request) {
        
        String travelDeskIdHeader = request.getHeader("X-User-Id");
        UUID travelDeskId = travelDeskIdHeader != null ? UUID.fromString(travelDeskIdHeader) : null;
        
        approvalRequest.setWorkflowId(workflowId);
        approvalRequest.setApproverRole("TRAVEL_DESK");
        approvalRequest.setApproverId(travelDeskId);
        
        return ResponseEntity.ok(workflowService.processApproval(approvalRequest));
    }

    @Operation(summary = "Get Travel Desk approval statistics")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('TRAVEL_DESK')")
    public ResponseEntity<?> getApprovalStats(HttpServletRequest request) {
        String travelDeskIdHeader = request.getHeader("X-User-Id");
        UUID travelDeskId = travelDeskIdHeader != null ? UUID.fromString(travelDeskIdHeader) : null;
        return ResponseEntity.ok(workflowService.getApprovalStatsByApprover(travelDeskId));
    }
}