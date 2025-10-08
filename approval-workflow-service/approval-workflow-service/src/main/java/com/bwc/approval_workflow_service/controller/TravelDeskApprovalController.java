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
@RequestMapping("/api/travel-desk/approvals")
@RequiredArgsConstructor
@Tag(name = "Travel Desk Approvals", description = "Travel Desk approval workflows")
public class TravelDeskApprovalController {

    private final ApprovalWorkflowService workflowService;

    @Operation(summary = "Get pending Travel Desk approvals")
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals(@RequestParam UUID travelDeskId) {
        return ResponseEntity.ok(workflowService.getPendingApprovals("TRAVEL_DESK", travelDeskId));
    }

    @Operation(summary = "Get Travel Desk approval statistics")
    @GetMapping("/stats")
    public ResponseEntity<?> getApprovalStats(@RequestParam UUID travelDeskId) {
        return ResponseEntity.ok(workflowService.getApprovalStatsByApprover(travelDeskId));
    }
}
