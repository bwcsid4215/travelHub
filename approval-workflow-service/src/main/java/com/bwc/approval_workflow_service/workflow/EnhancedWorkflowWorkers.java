package com.bwc.approval_workflow_service.workflow;

import com.bwc.approval_workflow_service.client.EmployeeServiceClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnhancedWorkflowWorkers {

    private final EmployeeServiceClient employeeServiceClient;
    private final EnhancedUserTaskService userTaskService;

    @JobWorker(type = "validate-input", autoComplete = false)
    public void validateInput(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        try {
            log.info("🧪 validate-input started | vars={}", vars);

            List<String> errors = new ArrayList<>();

            String travelRequestId = (String) vars.get("travelRequestId");
            String employeeId = (String) vars.get("employeeId");
            String destination = (String) vars.get("destination");
            Object amountObj = vars.get("requestedAmount");

            if (travelRequestId == null || travelRequestId.isBlank()) errors.add("travelRequestId is required");
            if (employeeId == null || employeeId.isBlank()) errors.add("employeeId is required");
            if (destination == null || destination.isBlank()) errors.add("destination is required");

            Double requestedAmount = null;
            try {
                if (amountObj instanceof Number) {
                    requestedAmount = ((Number) amountObj).doubleValue();
                } else if (amountObj != null) {
                    requestedAmount = Double.parseDouble(amountObj.toString());
                }
            } catch (Exception ex) {
                errors.add("requestedAmount must be a number");
            }

            boolean isValid = errors.isEmpty();
            Map<String, Object> out = new HashMap<>();
            out.put("isValid", isValid);
            out.put("validationErrors", isValid ? null : String.join("; ", errors));
            out.put("validationTimestamp", LocalDateTime.now().toString());

            client.newCompleteCommand(job)
                    .variables(out)
                    .send()
                    .join();

            log.info("✅ validate-input completed | valid={} errors={}", isValid, out.get("validationErrors"));
        } catch (Exception e) {
            log.error("❌ validate-input failed: {}", e.getMessage(), e);
            client.newFailCommand(job)
                    .retries(Math.max(0, job.getRetries() - 1))
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    @JobWorker(type = "initialize-travel-process", autoComplete = false)
    public void initializeTravelProcess(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        try {
            String employeeId = (String) vars.get("employeeId");
            Boolean managerPresent = Optional.ofNullable((Boolean) vars.get("managerPresent")).orElse(true);

            log.info("🚀 initialize-travel-process started | employeeId={}, managerPresent={}", employeeId, managerPresent);

            String managerId = null;
            boolean managerAutoApproved = false;
            Map<String, Object> out = new HashMap<>();

            if (employeeId == null || employeeId.isBlank()) {
                log.error("❌ Employee ID is null or blank");
                throw new IllegalArgumentException("Employee ID is required");
            }

            // Try to find manager
            if (Boolean.TRUE.equals(managerPresent)) {
                // Normal: direct manager
                managerId = userTaskService.getManagerForEmployee(employeeId);
                log.info("🔍 Direct manager lookup result: {}", managerId);
                
                if (managerId == null) {
                    log.warn("⚠️ No direct manager found; trying skip-level manager");
                    managerId = userTaskService.getManagerForEmployee(employeeId, 1);
                    log.info("🔍 Skip-level manager lookup result: {}", managerId);
                }
            } else {
                // Manager not present → try one level up
                managerId = userTaskService.getManagerForEmployee(employeeId, 1);
                log.info("🔍 Skip-level manager (manager not present) lookup result: {}", managerId);
            }

            // If still no manager found, use fallback strategies
            if (managerId == null) {
                log.warn("⚠️ No manager found through normal channels; using fallback strategies");
                
                // Strategy 1: Try to get default manager
                managerId = getDefaultManager();
                log.info("🔍 Default manager lookup result: {}", managerId);
                
                // Strategy 2: If default manager also fails, auto-approve
                if (managerId == null) {
                    log.warn("🚨 No manager available at all; auto-approving request");
                    managerAutoApproved = true;
                    
                    out.put("managerAutoApproved", true);
                    out.put("managerAutoApprovedReason", "No managers available in the system");
                    out.put("managerApproved", true);
                    out.put("managerComments", "Auto-approved: No managers available for approval");
                    out.put("managerId", "system-auto-approval");

                    // Notification context
                    out.put("notifyAudience", "EMPLOYEE_FINANCE");
                    out.put("notifyTitle", "Auto-approved due to manager unavailability");
                    out.put("notifyMessage", "Your travel request was auto-approved because no manager was available to review.");
                    out.put("notifySeverity", "INFO");

                    out.put("approvalGroup", "managers");
                    out.put("processInstanceId", job.getProcessInstanceKey());
                    out.put("riskCategory", calculateRiskCategory(vars));
                    out.put("slaDeadline", LocalDateTime.now().plusDays(3).toString());

                    client.newCompleteCommand(job).variables(out).send().join();
                    log.info("✅ initialize-travel-process | Auto-approved due to no managers available");
                    return;
                }
            }

            // Normal flow: manager found
            String riskCategory = calculateRiskCategory(vars);
            String slaDeadline = LocalDateTime.now().plusDays(3).toString();

            out.put("managerId", managerId);
            out.put("approvalGroup", "managers");
            out.put("processInstanceId", job.getProcessInstanceKey());
            out.put("riskCategory", riskCategory);
            out.put("slaDeadline", slaDeadline);
            out.put("managerAutoApproved", managerAutoApproved);

            client.newCompleteCommand(job).variables(out).send().join();
            log.info("✅ initialize-travel-process | managerId={}, riskCategory={}, slaDeadline={}", 
                    managerId, riskCategory, slaDeadline);

        } catch (Exception e) {
            log.error("❌ initialize-travel-process failed: {}", e.getMessage(), e);
            // Provide fallback variables even on failure
            Map<String, Object> fallbackVars = new HashMap<>();
            fallbackVars.put("managerId", getDefaultManager());
            fallbackVars.put("approvalGroup", "managers");
            fallbackVars.put("riskCategory", "MEDIUM");
            fallbackVars.put("slaDeadline", LocalDateTime.now().plusDays(3).toString());
            fallbackVars.put("managerAutoApproved", false);
            
            client.newCompleteCommand(job)
                    .variables(fallbackVars)
                    .send()
                    .join();
        }
    }

    @JobWorker(type = "validate-policy", autoComplete = false)
    public void validatePolicy(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        try {
            String destination = (String) vars.get("destination");
            Double amount = number(vars.get("requestedAmount"));

            boolean withinPolicy = validateTravelPolicy(destination, amount);
            String policyViolations = withinPolicy ? null : "Amount exceeds policy limit for destination";

            client.newCompleteCommand(job)
                    .variables(Map.of(
                            "withinPolicy", withinPolicy,
                            "policyViolations", policyViolations
                    ))
                    .send()
                    .join();

            log.info("✅ validate-policy | dest={} amount={} withinPolicy={}", destination, amount, withinPolicy);
        } catch (Exception e) {
            log.error("❌ validate-policy failed: {}", e.getMessage(), e);
            client.newFailCommand(job)
                    .retries(Math.max(0, job.getRetries() - 1))
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    @JobWorker(type = "book-tickets", autoComplete = true)
    public void bookTickets(ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        log.info("🎫 book-tickets {} → {} | requestedAmount: {}", vars.get("origin"), vars.get("destination"), vars.get("requestedAmount"));
    }

    @JobWorker(type = "notify-success", autoComplete = true)
    public void notifySuccess(ActivatedJob job) {
        Map<String, Object> v = job.getVariablesAsMap();
        String title = Objects.toString(v.getOrDefault("notifyTitle", "Request approved"), "");
        String msg   = Objects.toString(v.getOrDefault("notifyMessage", "Your request was approved."), "");
        String who   = Objects.toString(v.getOrDefault("notifyAudience", "EMPLOYEE"), "");

        log.info("📣 notify-success | to={} | {} - {}", who, title, msg);
        log.info("✅ notify-success | vars={}", v);
    }

    @JobWorker(type = "notify-reject", autoComplete = true)
    public void notifyReject(ActivatedJob job) {
        log.info("❌ notify-reject | {}", job.getVariablesAsMap());
    }

    @JobWorker(type = "upload-attachments", autoComplete = true)
    public void uploadAttachments(ActivatedJob job) {
        log.info("📎 upload-attachments | {}", job.getVariablesAsMap());
    }

    @JobWorker(type = "passthrough-risk", autoComplete = false)
    public void passthroughRisk(JobClient client, ActivatedJob job) {
        String bypass = Optional.ofNullable(job.getCustomHeaders().get("bypass")).orElse("false");
        String sla = Optional.ofNullable(job.getCustomHeaders().getOrDefault("sla", "PT30M")).orElse("PT30M");

        Map<String, Object> out = new HashMap<>();
        out.put("riskLevel", "LOW");
        out.put("requiresEnhancedApproval", false);
        out.put("riskScore", 0);

        if ("true".equalsIgnoreCase(bypass)) {
            log.warn("⏭️ BYPASSING passthrough-risk | pi={} sla={} vars={}",
                     job.getProcessInstanceKey(), sla, job.getVariablesAsMap());
            client.newCompleteCommand(job).variables(out).send().join();
            return;
        }

        client.newCompleteCommand(job).variables(out).send().join();
    }

    // Enhanced manager lookup with better error handling
    private String getDefaultManager() {
        try {
            log.info("🔍 Looking for default manager...");
            var managers = employeeServiceClient.getEmployeesByRole("MANAGER");
            if (managers != null && !managers.isEmpty()) {
                String managerId = managers.get(0).getEmployeeId().toString();
                log.info("✅ Found default manager: {}", managerId);
                return managerId;
            } else {
                log.warn("⚠️ No managers found in employee service");
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get default manager: {}", e.getMessage());
            return null;
        }
    }

    private String calculateRiskCategory(Map<String, Object> vars) {
        String destination = (String) vars.get("destination");
        Double amount = number(vars.get("requestedAmount"));
        if (amount != null && amount > 10000) return "HIGH";
        if (destination != null && Arrays.asList("Conflict Zone", "High Risk").contains(destination)) return "HIGH";
        if (amount != null && amount > 5000) return "MEDIUM";
        return "LOW";
    }

    private boolean validateTravelPolicy(String destination, Double amount) {
        if (amount == null) return true;
        if (destination != null && Arrays.asList("Domestic", "Low Risk").contains(destination)) {
            return amount <= 5000;
        }
        return amount <= 10000;
    }

    private Double number(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }
    
 // Add this method to EnhancedWorkflowWorkers for testing
    public void debugManagerAssignment(String employeeId) {
        try {
            log.info("🔍 DEBUG: Testing manager assignment for employee: {}", employeeId);
            
            // Test direct manager
            String directManager = userTaskService.getManagerForEmployee(employeeId);
            log.info("🔍 DEBUG: Direct manager: {}", directManager);
            
            // Test skip-level manager
            String skipLevelManager = userTaskService.getManagerForEmployee(employeeId, 1);
            log.info("🔍 DEBUG: Skip-level manager: {}", skipLevelManager);
            
            // Test default manager
            String defaultManager = getDefaultManager();
            log.info("🔍 DEBUG: Default manager: {}", defaultManager);
            
        } catch (Exception e) {
            log.error("❌ DEBUG: Manager assignment test failed: {}", e.getMessage(), e);
        }
    }
    
    @JobWorker(type = "zeebe.user-task", autoComplete = false)
    public void debugUserTaskCreation(JobClient client, ActivatedJob job) {
        try {
            log.info("🎯 USER TASK CREATED: {}", job.getElementId());
            log.info("📋 User Task Variables: {}", job.getVariablesAsMap());
            log.info("🔧 Custom Headers: {}", job.getCustomHeaders());
            
            // Complete the job to let it proceed
            client.newCompleteCommand(job).send().join();
            
        } catch (Exception e) {
            log.error("❌ User task creation failed: {}", e.getMessage(), e);
            client.newFailCommand(job)
                    .retries(0)
                    .errorMessage("User task creation failed: " + e.getMessage())
                    .send()
                    .join();
        }
    }
}