package com.bwc.approval_workflow_service.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bwc.approval_workflow_service.client.EmployeeServiceClient;
import com.bwc.approval_workflow_service.client.NotificationServiceClient;
import com.bwc.approval_workflow_service.client.PolicyServiceClient;
import com.bwc.approval_workflow_service.client.TravelRequestServiceClient;
import com.bwc.approval_workflow_service.dto.ApprovalActionDTO;
import com.bwc.approval_workflow_service.dto.ApprovalRequestDTO;
import com.bwc.approval_workflow_service.dto.ApprovalStatsDTO;
import com.bwc.approval_workflow_service.dto.ApprovalWorkflowDTO;
import com.bwc.approval_workflow_service.dto.EmployeeProxyDTO;
import com.bwc.approval_workflow_service.dto.NotificationRequestDTO;
import com.bwc.approval_workflow_service.dto.TravelRequestProxyDTO;
import com.bwc.approval_workflow_service.dto.WorkflowMetricsDTO;
import com.bwc.approval_workflow_service.entity.ApprovalAction;
import com.bwc.approval_workflow_service.entity.ApprovalWorkflow;
import com.bwc.approval_workflow_service.entity.WorkflowConfiguration;
import com.bwc.approval_workflow_service.exception.ResourceNotFoundException;
import com.bwc.approval_workflow_service.exception.WorkflowException;
import com.bwc.approval_workflow_service.kafka.WorkflowStatusEvent;
import com.bwc.approval_workflow_service.kafka.WorkflowStatusProducer;
import com.bwc.approval_workflow_service.mapper.ApprovalWorkflowMapper;
import com.bwc.approval_workflow_service.repository.ApprovalActionRepository;
import com.bwc.approval_workflow_service.repository.ApprovalWorkflowRepository;
import com.bwc.approval_workflow_service.repository.WorkflowConfigurationRepository;
import com.bwc.approval_workflow_service.service.ApprovalWorkflowService;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalActionRepository actionRepository;
    private final WorkflowConfigurationRepository configRepository;
    private final TravelRequestServiceClient travelRequestClient;
    private final PolicyServiceClient policyClient;
    private final EmployeeServiceClient employeeClient;
    private final NotificationServiceClient notificationClient;
    private final ApprovalWorkflowMapper mapper;
    private final WorkflowStatusProducer workflowStatusProducer;

    

    @Override
    @Transactional
    public ApprovalWorkflowDTO initiateWorkflow(UUID travelRequestId, String workflowType, Double estimatedCost) {
        // Fallback to fetch if only ID is provided (backward compatibility)
        TravelRequestProxyDTO travelRequest = fetchTravelRequestSafe(travelRequestId);
        return initiateWorkflow(travelRequest, workflowType, estimatedCost);
    }

    @Override
    @Transactional
    public ApprovalWorkflowDTO initiateWorkflow(TravelRequestProxyDTO travelRequest, String workflowType, Double estimatedCost) {
        UUID travelRequestId = travelRequest.getTravelRequestId();
        
        // ✅ Prevent duplicates for the same travel request + workflow type
        if (workflowRepository.findByTravelRequestIdAndWorkflowType(travelRequestId, workflowType).isPresent()) {
            throw new WorkflowException("Workflow already exists for travel request " + travelRequestId + " and type " + workflowType);
        }

        // No need to fetch travel request - we already have it!
        EmployeeProxyDTO employee = fetchEmployeeSafe(travelRequest.getEmployeeId());

        List<WorkflowConfiguration> configs = configRepository
                .findByWorkflowTypeAndIsActiveTrueOrderBySequenceOrder(workflowType);

        if (configs.isEmpty()) {
            throw new WorkflowException("No workflow configuration found for type: " + workflowType);
        }

        WorkflowConfiguration firstStep = configs.get(0);
        UUID approverId = determineApproverId(firstStep, travelRequest);

        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .travelRequestId(travelRequestId)
                .workflowType(workflowType)
                .currentStep(firstStep.getStepName())
                .currentApproverRole(firstStep.getApproverRole())
                .currentApproverId(approverId)
                .status("PENDING")
                .nextStep(getNextStep(configs, 0))
                .priority(calculatePriority(travelRequest, estimatedCost))
                .estimatedCost(estimatedCost)
                .dueDate(calculateDueDate(firstStep))
                .build();

        ApprovalWorkflow savedWorkflow = workflowRepository.save(workflow);

        // Record initial SUBMIT action
        actionRepository.save(ApprovalAction.builder()
                .workflowId(savedWorkflow.getWorkflowId())
                .travelRequestId(travelRequestId)
                .approverRole("SYSTEM")
                .approverId(travelRequest.getEmployeeId())
                .action("SUBMIT")
                .step("SUBMIT")
                .comments(workflowType + " workflow initiated")
                .actionTakenAt(LocalDateTime.now())
                .build());

        // Update status in Travel Request Service
        updateTravelRequestStatus(travelRequestId, "UNDER_REVIEW");

        // Send notification to first approver
        sendNewApprovalNotification(savedWorkflow, travelRequest, employee);

        log.info("✅ {} workflow initiated successfully for request {}", workflowType, travelRequestId);
        return mapper.toDto(savedWorkflow);
    }

    @Override
    @Transactional
    public ApprovalWorkflowDTO processApproval(ApprovalRequestDTO approvalRequest) {
        ApprovalWorkflow workflow = workflowRepository.findById(approvalRequest.getWorkflowId())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));

        if (!"PENDING".equalsIgnoreCase(workflow.getStatus())) {
            throw new WorkflowException("Workflow is not in pending state");
        }

        // 🚨 CRITICAL: Validate approver has the correct role for current step
        validateApproverAuthorization(workflow, approvalRequest);

        // 🚨 CRITICAL: For manager-specific steps, validate manager ID matches
        validateManagerAuthorization(workflow, approvalRequest);

        // Record the action
        actionRepository.save(ApprovalAction.builder()
                .workflowId(workflow.getWorkflowId())
                .travelRequestId(workflow.getTravelRequestId())
                .approverRole(approvalRequest.getApproverRole())
                .approverId(approvalRequest.getApproverId())
                .approverName(approvalRequest.getApproverName())
                .action(approvalRequest.getAction().toUpperCase())
                .step(workflow.getCurrentStep())
                .comments(approvalRequest.getComments())
                .escalationReason(approvalRequest.getEscalationReason())
                .isEscalated(approvalRequest.getEscalationReason() != null)
                .amountApproved(approvalRequest.getAmountApproved())
                .reimbursementAmount(approvalRequest.getReimbursementAmount())
                .actionTakenAt(LocalDateTime.now())
                .build());

        List<WorkflowConfiguration> configs = configRepository
                .findByWorkflowTypeAndIsActiveTrueOrderBySequenceOrder(workflow.getWorkflowType());

        String action = approvalRequest.getAction().toUpperCase();
        switch (action) {
            case "APPROVE" -> handleApprove(workflow, configs, approvalRequest);
            case "REJECT" -> handleReject(workflow, approvalRequest.getComments());
            case "RETURN" -> handleReturn(workflow, approvalRequest.getComments());
            case "ESCALATE" -> handleEscalate(workflow, approvalRequest.getEscalationReason());
            default -> throw new WorkflowException("Unknown action: " + action);
        }

        ApprovalWorkflow updatedWorkflow = workflowRepository.save(workflow);
        return mapper.toDto(updatedWorkflow);
    }

    /**
     * 🚨 SECURITY: Validate that the approver has the correct role for the current workflow step
     */
    private void validateApproverAuthorization(ApprovalWorkflow workflow, ApprovalRequestDTO approvalRequest) {
        String currentStepRole = workflow.getCurrentApproverRole();
        String approverRole = approvalRequest.getApproverRole();
        
        if (!currentStepRole.equals(approverRole)) {
            throw new WorkflowException(
                String.format("Approver with role %s cannot approve step requiring role %s", 
                             approverRole, currentStepRole)
            );
        }
        
        log.info("✅ Authorization validated: {} can approve {} step", 
                 approverRole, workflow.getCurrentStep());
    }

    /**
     * 🚨 SECURITY: Enhanced validation for manager approvals
     */
    private void validateManagerAuthorization(ApprovalWorkflow workflow, ApprovalRequestDTO approvalRequest) {
        if ("MANAGER".equals(workflow.getCurrentApproverRole())) {
            // For manager steps, verify the specific manager ID matches
            if (workflow.getCurrentApproverId() == null) {
                throw new WorkflowException("No manager assigned to this workflow step");
            }
            
            if (!workflow.getCurrentApproverId().equals(approvalRequest.getApproverId())) {
                throw new WorkflowException(
                    String.format("Manager %s cannot approve request assigned to manager %s", 
                                 approvalRequest.getApproverId(), workflow.getCurrentApproverId())
                );
            }
        }
    }

    private void handleApprove(ApprovalWorkflow workflow, List<WorkflowConfiguration> configs, 
                             ApprovalRequestDTO approvalRequest) {
        int currentIndex = findCurrentStepIndex(configs, workflow.getCurrentStep());
        
        // Handle overpriced marking for Travel Desk
        if ("TRAVEL_DESK_CHECK".equals(workflow.getCurrentStep()) && 
            Boolean.TRUE.equals(approvalRequest.getMarkOverpriced())) {
            workflow.setIsOverpriced(true);
            workflow.setOverpricedReason(approvalRequest.getOverpricedReason());
        }

        // Handle finance approval with amount
        if ("FINANCE_APPROVAL".equals(workflow.getCurrentStep()) && 
            approvalRequest.getAmountApproved() != null) {
            workflow.setEstimatedCost(approvalRequest.getAmountApproved());
        }

        // Check if this is the last step
        if (currentIndex >= configs.size() - 1) {
            completeWorkflow(workflow, "APPROVED");
            return;
        }

        // Handle conditional routing based on current step
        WorkflowConfiguration nextStep = determineNextStep(workflow, configs, currentIndex);
        
        workflow.setPreviousStep(workflow.getCurrentStep());
        workflow.setCurrentStep(nextStep.getStepName());
        workflow.setCurrentApproverRole(nextStep.getApproverRole());
        workflow.setCurrentApproverId(determineApproverId(nextStep, 
                fetchTravelRequestSafe(workflow.getTravelRequestId())));
        workflow.setNextStep(getNextStep(configs, configs.indexOf(nextStep)));
        workflow.setDueDate(calculateDueDate(nextStep));
        workflow.setStatus("PENDING");
        
        if ("SYSTEM".equalsIgnoreCase(workflow.getCurrentApproverRole()) &&
                "WORKFLOW_COMPLETE".equalsIgnoreCase(workflow.getCurrentStep())) {

                completeWorkflow(workflow, "COMPLETED");
                log.info("✅ Workflow {} auto-completed by system after final reimbursement step.",
                        workflow.getWorkflowId());
                return; // stop here to avoid sending further notifications
            }

        sendNextApprovalNotification(workflow);
    }

    private WorkflowConfiguration determineNextStep(ApprovalWorkflow workflow, 
                                                   List<WorkflowConfiguration> configs, 
                                                   int currentIndex) {
        String currentStep = workflow.getCurrentStep();
        
        // Pre-travel workflow logic
        if ("PRE_TRAVEL".equals(workflow.getWorkflowType())) {
            switch (currentStep) {
                case "MANAGER_APPROVAL":
                    return configs.stream()
                            .filter(c -> "TRAVEL_DESK_CHECK".equals(c.getStepName()))
                            .findFirst()
                            .orElse(configs.get(currentIndex + 1));
                
                case "TRAVEL_DESK_CHECK":
                    if (Boolean.TRUE.equals(workflow.getIsOverpriced())) {
                        // Overpriced - go to Finance
                        return configs.stream()
                                .filter(c -> "FINANCE_APPROVAL".equals(c.getStepName()))
                                .findFirst()
                                .orElse(configs.get(currentIndex + 1));
                    } else {
                        // Within limits - go to HR
                        return configs.stream()
                                .filter(c -> "HR_APPROVAL".equals(c.getStepName()))
                                .findFirst()
                                .orElse(configs.get(currentIndex + 1));
                    }
                
                case "FINANCE_APPROVAL":
                    // After finance, go back to Travel Desk for booking
                    return configs.stream()
                            .filter(c -> "TRAVEL_DESK_BOOKING".equals(c.getStepName()))
                            .findFirst()
                            .orElse(configs.get(currentIndex + 1));
                
                case "TRAVEL_DESK_BOOKING":
                    // After booking, go to HR for compliance
                    return configs.stream()
                            .filter(c -> "HR_COMPLIANCE".equals(c.getStepName()))
                            .findFirst()
                            .orElse(configs.get(currentIndex + 1));
                
                case "HR_COMPLIANCE":
                    // After HR compliance, go to Finance for final approval
                    return configs.stream()
                            .filter(c -> "FINANCE_FINAL".equals(c.getStepName()))
                            .findFirst()
                            .orElse(configs.get(currentIndex + 1));
            }
        }
        
        // Post-travel workflow logic
        if ("POST_TRAVEL".equals(workflow.getWorkflowType())) {
            switch (currentStep) {
                case "TRAVEL_DESK_BILL_REVIEW":
                    if (Boolean.TRUE.equals(workflow.getIsOverpriced())) {
                        // Overpriced bills - go to Finance for calculation
                        return configs.stream()
                                .filter(c -> "FINANCE_REIMBURSEMENT".equals(c.getStepName()))
                                .findFirst()
                                .orElse(configs.get(currentIndex + 1));
                    } else {
                        // Within limits - go to Finance directly
                        return configs.stream()
                                .filter(c -> "FINANCE_REIMBURSEMENT".equals(c.getStepName()))
                                .findFirst()
                                .orElse(configs.get(currentIndex + 1));
                    }
            }
        }
        
        // Default: move to next sequential step
        return configs.get(currentIndex + 1);
    }

    private void handleReject(ApprovalWorkflow workflow, String comments) {
        workflow.setStatus("REJECTED");
        workflow.setCompletedAt(LocalDateTime.now());
        updateTravelRequestStatus(workflow.getTravelRequestId(), "REJECTED");
        sendRejectionNotification(workflow, comments);
    }

    private void handleReturn(ApprovalWorkflow workflow, String comments) {
        workflow.setStatus("RETURNED");
        updateTravelRequestStatus(workflow.getTravelRequestId(), "RETURNED");
        sendReturnNotification(workflow, comments);
    }

    private void handleEscalate(ApprovalWorkflow workflow, String reason) {
        workflow.setStatus("ESCALATED");
        workflow.setPriority("HIGH");
        sendEscalationNotification(workflow, reason);
    }

    @Override
    @Transactional
    public ApprovalWorkflowDTO markBookingUploaded(UUID workflowId, UUID uploadedBy) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));

        if (!"TRAVEL_DESK_BOOKING".equals(workflow.getCurrentStep())) {
            throw new WorkflowException("Workflow is not in booking upload step");
        }

        // Record booking upload action
        actionRepository.save(ApprovalAction.builder()
                .workflowId(workflowId)
                .travelRequestId(workflow.getTravelRequestId())
                .approverRole("TRAVEL_DESK")
                .approverId(uploadedBy)
                .action("UPLOAD_BOOKING")
                .step("TRAVEL_DESK_BOOKING")
                .comments("Travel bookings uploaded")
                .actionTakenAt(LocalDateTime.now())
                .build());

        // Move to next step (HR compliance)
        List<WorkflowConfiguration> configs = configRepository
                .findByWorkflowTypeAndIsActiveTrueOrderBySequenceOrder(workflow.getWorkflowType());
        
        WorkflowConfiguration nextStep = configs.stream()
                .filter(c -> "HR_COMPLIANCE".equals(c.getStepName()))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("HR compliance step not found"));

        workflow.setPreviousStep(workflow.getCurrentStep());
        workflow.setCurrentStep(nextStep.getStepName());
        workflow.setCurrentApproverRole(nextStep.getApproverRole());
        workflow.setCurrentApproverId(determineApproverId(nextStep, 
                fetchTravelRequestSafe(workflow.getTravelRequestId())));
        workflow.setNextStep(getNextStep(configs, configs.indexOf(nextStep)));
        workflow.setDueDate(calculateDueDate(nextStep));

        sendNextApprovalNotification(workflow);

        return mapper.toDto(workflowRepository.save(workflow));
    }

    @Override
    @Transactional
    public ApprovalWorkflowDTO uploadBills(UUID workflowId, Double actualCost, UUID uploadedBy) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));

        if (!"POST_TRAVEL".equals(workflow.getWorkflowType())) {
            throw new WorkflowException("Only post-travel workflows can have bills uploaded");
        }

        workflow.setActualCost(actualCost);

        // Record bill upload action
        actionRepository.save(ApprovalAction.builder()
                .workflowId(workflowId)
                .travelRequestId(workflow.getTravelRequestId())
                .approverRole("EMPLOYEE")
                .approverId(uploadedBy)
                .action("UPLOAD_BILLS")
                .step("BILL_UPLOAD")
                .comments("Travel bills uploaded with actual cost: " + actualCost)
                .actionTakenAt(LocalDateTime.now())
                .build());

        // Move to Travel Desk for bill review
        List<WorkflowConfiguration> configs = configRepository
                .findByWorkflowTypeAndIsActiveTrueOrderBySequenceOrder(workflow.getWorkflowType());
        
        WorkflowConfiguration nextStep = configs.stream()
                .filter(c -> "TRAVEL_DESK_BILL_REVIEW".equals(c.getStepName()))
                .findFirst()
                .orElseThrow(() -> new WorkflowException("Travel Desk bill review step not found"));

        workflow.setPreviousStep(workflow.getCurrentStep());
        workflow.setCurrentStep(nextStep.getStepName());
        workflow.setCurrentApproverRole(nextStep.getApproverRole());
        workflow.setCurrentApproverId(determineApproverId(nextStep, 
                fetchTravelRequestSafe(workflow.getTravelRequestId())));
        workflow.setNextStep(getNextStep(configs, configs.indexOf(nextStep)));
        workflow.setDueDate(calculateDueDate(nextStep));
        workflow.setStatus("PENDING");

        // Update travel request with actual cost
        try {
            travelRequestClient.updateActualCost(workflow.getTravelRequestId(), actualCost);
        } catch (Exception e) {
            log.warn("Failed to update actual cost: {}", e.getMessage());
        }

        sendNextApprovalNotification(workflow);

        return mapper.toDto(workflowRepository.save(workflow));
    }

    private void completeWorkflow(ApprovalWorkflow workflow, String status) {
        workflow.setStatus(status);
        workflow.setCurrentStep("COMPLETED");
        workflow.setCompletedAt(LocalDateTime.now());

        String travelRequestStatus = "APPROVED".equals(status) ? "COMPLETED" : status;
        updateTravelRequestStatus(workflow.getTravelRequestId(), travelRequestStatus);

        // 🚀 Automatically start POST_TRAVEL workflow after PRE_TRAVEL completion
        if ("PRE_TRAVEL".equals(workflow.getWorkflowType()) && "APPROVED".equals(status)) {
            try {
                // Avoid creating duplicate POST_TRAVEL workflow if it already exists
                boolean postTravelExists = workflowRepository
                        .findByTravelRequestIdAndWorkflowType(workflow.getTravelRequestId(), "POST_TRAVEL")
                        .isPresent();

                if (!postTravelExists) {
                    // Use the optimized method by fetching travel request once
                    TravelRequestProxyDTO travelRequest = fetchTravelRequestSafe(workflow.getTravelRequestId());
                    initiateWorkflow(travelRequest, "POST_TRAVEL", workflow.getEstimatedCost());
                    log.info("✅ POST_TRAVEL workflow automatically initiated for request {}", workflow.getTravelRequestId());
                } else {
                    log.warn("⚠️ POST_TRAVEL workflow already exists for request {}", workflow.getTravelRequestId());
                }
            } catch (Exception e) {
                log.error("❌ Failed to auto-initiate POST_TRAVEL workflow: {}", e.getMessage());
            }
        }

        sendCompletionNotification(workflow);
        workflowStatusProducer.sendWorkflowStatus(
        	    new WorkflowStatusEvent(workflow.getTravelRequestId(), status, "Workflow " + status)
        	);

    }

    // Helper methods
    private TravelRequestProxyDTO fetchTravelRequestSafe(UUID id) {
        try {
            return travelRequestClient.getTravelRequest(id);
        } catch (FeignException e) {
            log.error("Failed to fetch travel request {}: {}", id, e.getMessage());
            throw new WorkflowException("Failed to retrieve travel request");
        }
    }

    private EmployeeProxyDTO fetchEmployeeSafe(UUID employeeId) {
        try {
            return employeeClient.getEmployee(employeeId);
        } catch (FeignException e) {
            log.warn("Failed to fetch employee {}: {}", employeeId, e.getMessage());
            return EmployeeProxyDTO.builder().employeeId(employeeId).build();
        }
    }

    /**
     * ✅ Determines the correct approver ID for the workflow step.
     * For MANAGER steps, always fetches the latest manager from EmployeeService
     * to ensure accurate assignment (avoids stale travelRequest.managerId values).
     */
    private UUID determineApproverId(WorkflowConfiguration step, TravelRequestProxyDTO travelRequest) {
        UUID employeeId = travelRequest.getEmployeeId();

        if ("MANAGER".equalsIgnoreCase(step.getApproverRole())) {
            try {
                // Always fetch from EmployeeService to ensure manager info is up-to-date
                EmployeeProxyDTO employee = employeeClient.getEmployee(employeeId);

                if (employee.getManagerId() != null) {
                    logApproverAssignment(
                            step.getStepName(),
                            "MANAGER",
                            employee.getManagerId(),
                            employee.getEmployeeId(),
                            "EmployeeService"
                    );
                    return employee.getManagerId();
                } else {
                    UUID fallbackId = getSystemAdminIdFallback();
                    logApproverAssignment(
                            step.getStepName(),
                            "MANAGER",
                            fallbackId,
                            employee.getEmployeeId(),
                            "Fallback: No manager found"
                    );
                    return fallbackId;
                }

            } catch (Exception e) {
                log.error("❌ [{}] Failed to fetch manager for employee {}: {}",
                        step.getStepName(), employeeId, e.getMessage());
                UUID fallbackId = getSystemAdminIdFallback();
                logApproverAssignment(step.getStepName(), "MANAGER", fallbackId, employeeId, "Exception fallback");
                return fallbackId;
            }
        }

        // For other roles (Finance, HR, etc.), no direct approver assigned here
        logApproverAssignment(step.getStepName(), step.getApproverRole(), null, employeeId, "Non-manager step");
        return null;
    }

    /**
     * 🧩 Optional Fallback: fetches System Admin’s ID for edge cases where manager isn’t found.
     */
    private UUID getSystemAdminIdFallback() {
        // Replace this UUID with your real admin’s ID if different
        return UUID.fromString("ff78684e-ed8d-4696-bccf-582ecf1ab900");
    }



    private String getNextStep(List<WorkflowConfiguration> configs, int currentIndex) {
        return currentIndex < configs.size() - 1 ? configs.get(currentIndex + 1).getStepName() : "COMPLETED";
    }

    private LocalDateTime calculateDueDate(WorkflowConfiguration step) {
        return step.getTimeLimitHours() != null ? 
                LocalDateTime.now().plusHours(step.getTimeLimitHours()) : 
                LocalDateTime.now().plusDays(3);
    }

    private String calculatePriority(TravelRequestProxyDTO travelRequest, Double estimatedCost) {
        if (estimatedCost != null && estimatedCost > 5000) return "HIGH";
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                travelRequest.getStartDate(), travelRequest.getEndDate());
        if (days > 14) return "HIGH";
        return "NORMAL";
    }

    private int findCurrentStepIndex(List<WorkflowConfiguration> configs, String currentStep) {
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getStepName().equals(currentStep)) {
                return i;
            }
        }
        throw new WorkflowException("Current step not found in configuration: " + currentStep);
    }

    private void updateTravelRequestStatus(UUID travelRequestId, String status) {
        try {
            travelRequestClient.updateRequestStatus(travelRequestId, status);
        } catch (Exception e) {
            log.warn("Failed to update travel request status: {}", e.getMessage());
        }
    }

    // Notification methods
    @Async
    void sendNewApprovalNotification(ApprovalWorkflow workflow, TravelRequestProxyDTO travelRequest, EmployeeProxyDTO employee) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .userId(workflow.getCurrentApproverId())
                    .subject("Approval Required: Travel Request")
                    .message("Travel request from " + employee.getFullName() + " requires your approval")
                    .notificationType("APPROVAL_REQUEST")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send notification: {}", e.getMessage());
        }
    }

    @Async
    void sendNextApprovalNotification(ApprovalWorkflow workflow) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .userId(workflow.getCurrentApproverId())
                    .subject("Action Required: Next Approval Step")
                    .message("Workflow requires your action at step: " + workflow.getCurrentStep())
                    .notificationType("APPROVAL_NEXT")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send notification: {}", e.getMessage());
        }
    }

    // Other methods from interface
    @Override
    @Transactional(readOnly = true)
    public ApprovalWorkflowDTO getWorkflowByRequestId(UUID travelRequestId) {
        return workflowRepository.findByTravelRequestId(travelRequestId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalWorkflowDTO getWorkflow(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> getPendingApprovals(String approverRole, UUID approverId) {
        List<ApprovalWorkflow> workflows;
        if (approverId != null) {
            workflows = workflowRepository.findByCurrentApproverIdAndStatus(approverId, "PENDING");
        } else {
            workflows = workflowRepository.findByCurrentApproverRoleAndStatus(approverRole, "PENDING");
        }
        return workflows.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> getPendingApprovalsByRole(String approverRole) {
        return workflowRepository.findByCurrentApproverRoleAndStatus(approverRole, "PENDING")
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalWorkflowDTO> getWorkflowsByStatus(String status) {
        return workflowRepository.findByStatus(status).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalActionDTO> getWorkflowHistory(UUID travelRequestId) {
        return actionRepository.findByTravelRequestIdOrderByCreatedAtDesc(travelRequestId)
                .stream().map(mapper::toActionDto).collect(Collectors.toList());
    }

    @Override
    public ApprovalWorkflowDTO escalateWorkflow(UUID workflowId, String reason, UUID escalatedBy) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
        workflow.setStatus("ESCALATED");
        workflow.setPriority("HIGH");
        workflowRepository.save(workflow);
        sendEscalationNotification(workflow, reason);
        return mapper.toDto(workflow);
    }

    @Override
    public ApprovalWorkflowDTO reassignWorkflow(UUID workflowId, String newApproverRole, UUID newApproverId) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
        workflow.setCurrentApproverRole(newApproverRole);
        workflow.setCurrentApproverId(newApproverId);
        workflowRepository.save(workflow);
        sendNextApprovalNotification(workflow);
        return mapper.toDto(workflow);
    }

    @Override
    public void reloadWorkflowConfigurations() {
        log.info("Workflow configurations reloaded");
    }

    @Override
    @Transactional
    public ApprovalWorkflowDTO updateWorkflowPriority(UUID workflowId, String priority) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
        workflow.setPriority(priority);
        ApprovalWorkflow updated = workflowRepository.save(workflow);
        return mapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowMetricsDTO getWorkflowMetrics() {
        long totalWorkflows = workflowRepository.count();
        long pendingWorkflows = workflowRepository.countByStatus("PENDING");
        long approvedWorkflows = workflowRepository.countByStatus("APPROVED");
        long rejectedWorkflows = workflowRepository.countByStatus("REJECTED");
        long escalatedWorkflows = workflowRepository.countByStatus("ESCALATED");

        double averageApprovalTime = calculateAverageApprovalTime();

        return WorkflowMetricsDTO.builder()
                .totalWorkflows(totalWorkflows)
                .pendingWorkflows(pendingWorkflows)
                .approvedWorkflows(approvedWorkflows)
                .rejectedWorkflows(rejectedWorkflows)
                .escalatedWorkflows(escalatedWorkflows)
                .averageApprovalTime(averageApprovalTime)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalStatsDTO> getApprovalStatsByApprover(UUID approverId) {
        List<ApprovalWorkflow> workflows = workflowRepository.findByCurrentApproverIdAndStatus(approverId, "PENDING");
        
        return List.of(ApprovalStatsDTO.builder()
                .approverId(approverId)
                .totalAssigned((long) workflows.size())
                .pending((long) workflows.size())
                .approved(0L)
                .rejected(0L)
                .averageProcessingTime(0.0)
                .build());
    }

    // Add missing notification methods implementation
    @Async
    void sendRejectionNotification(ApprovalWorkflow workflow, String comments) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .subject("Workflow Rejected")
                    .message("Workflow " + workflow.getWorkflowId() + " was rejected. Comments: " + comments)
                    .notificationType("WORKFLOW_REJECTED")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send rejection notification: {}", e.getMessage());
        }
    }

    @Async
    void sendReturnNotification(ApprovalWorkflow workflow, String comments) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .subject("Workflow Returned")
                    .message("Workflow " + workflow.getWorkflowId() + " returned for correction. Comments: " + comments)
                    .notificationType("WORKFLOW_RETURNED")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send return notification: {}", e.getMessage());
        }
    }

    @Async
    void sendEscalationNotification(ApprovalWorkflow workflow, String reason) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .subject("Workflow Escalated")
                    .message("Workflow " + workflow.getWorkflowId() + " escalated. Reason: " + reason)
                    .notificationType("WORKFLOW_ESCALATED")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send escalation notification: {}", e.getMessage());
        }
    }

    @Async
    void sendCompletionNotification(ApprovalWorkflow workflow) {
        try {
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .subject("Workflow Completed")
                    .message("Workflow " + workflow.getWorkflowId() + " has been completed")
                    .notificationType("WORKFLOW_COMPLETED")
                    .referenceId(workflow.getTravelRequestId())
                    .referenceType("TRAVEL_REQUEST")
                    .build();
            notificationClient.sendNotification(notification);
        } catch (Exception e) {
            log.warn("Failed to send completion notification: {}", e.getMessage());
        }
    }

    private double calculateAverageApprovalTime() {
        List<ApprovalWorkflow> completedWorkflows = workflowRepository.findByStatus("APPROVED");
        if (completedWorkflows.isEmpty()) {
            return 0.0;
        }
        
        double totalHours = completedWorkflows.stream()
                .mapToDouble(wf -> {
                    if (wf.getCreatedAt() != null && wf.getCompletedAt() != null) {
                        return java.time.Duration.between(wf.getCreatedAt(), wf.getCompletedAt()).toHours();
                    }
                    return 0.0;
                })
                .sum();
        
        return totalHours / completedWorkflows.size();
    }
    
    /**
     * 🪶 Utility: Logs and standardizes approver assignments for debugging and auditing.
     * Always use this when assigning next approvers.
     */
    private void logApproverAssignment(String stepName, String role, UUID approverId, UUID employeeId, String source) {
        if (approverId != null) {
            log.info("🧭 [{}] Assigned {} role to approver {} for employee {} (source: {})",
                    stepName, role, approverId, employeeId, source);
        } else {
            log.warn("⚠️ [{}] No approver ID found for role {} (employee: {}, source: {})",
                    stepName, role, employeeId, source);
        }
    }
    
    @Override
    @Transactional
    public void recordAction(UUID workflowId, String step, String action, String comments) {
        var workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + workflowId));

        ApprovalAction approvalAction = ApprovalAction.builder()
                .workflowId(workflowId)
                .travelRequestId(workflow.getTravelRequestId())
                .approverRole(workflow.getCurrentApproverRole())
                .approverId(workflow.getCurrentApproverId())
                .action(action.toUpperCase())
                .step(step)
                .comments(comments)
                .actionTakenAt(LocalDateTime.now())
                .build();

        actionRepository.save(approvalAction);
        log.info("📝 Recorded manual action [{}:{}] on workflow {}", step, action, workflowId);
    }

}