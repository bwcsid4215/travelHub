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

    // ✅ ROOT CAUSE FIX: implement the missing "validate-input" worker
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

            // Dynamically resolve manager
            String managerId = userTaskService.getManagerForEmployee(employeeId);
            if (managerId == null) {
                log.warn("⚠️ No manager found for {}, using default", employeeId);
                managerId = getDefaultManager();
            }

            String riskCategory = calculateRiskCategory(vars);
            String slaDeadline = LocalDateTime.now().plusDays(3).toString();

            client.newCompleteCommand(job)
                    .variables(Map.of(
                            "managerId", managerId,
                            "approvalGroup", "managers",
                            "processInstanceId", job.getProcessInstanceKey(),
                            "riskCategory", riskCategory,
                            "slaDeadline", slaDeadline
                    ))
                    .send()
                    .join();

            log.info("✅ initialize-travel-process | mgr={} risk={} sla={}", managerId, riskCategory, slaDeadline);
        } catch (Exception e) {
            log.error("❌ initialize-travel-process failed: {}", e.getMessage(), e);
            client.newFailCommand(job)
                    .retries(Math.max(0, job.getRetries() - 1))
                    .errorMessage(e.getMessage())
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
        // Integrate with booking system here...
    }

    @JobWorker(type = "notify-success", autoComplete = true)
    public void notifySuccess(ActivatedJob job) {
        log.info("✅ notify-success | {}", job.getVariablesAsMap());
    }

    @JobWorker(type = "notify-reject", autoComplete = true)
    public void notifyReject(ActivatedJob job) {
        log.info("❌ notify-reject | {}", job.getVariablesAsMap());
    }

    @JobWorker(type = "upload-attachments", autoComplete = true)
    public void uploadAttachments(ActivatedJob job) {
        log.info("📎 upload-attachments | {}", job.getVariablesAsMap());
    }

    private String getDefaultManager() {
        try {
            var managers = employeeServiceClient.getEmployeesByRole("MANAGER");
            if (!managers.isEmpty()) {
                return managers.get(0).getEmployeeId().toString();
            }
        } catch (Exception e) {
            log.error("Failed to get default manager: {}", e.getMessage());
        }
        return "default-manager";
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
    
    
    @JobWorker(type = "passthrough-risk", autoComplete = false)
    public void passthroughRisk(JobClient client, ActivatedJob job) {
        // read headers
        String bypass = Optional.ofNullable(job.getCustomHeaders().get("bypass")).orElse("false");
        String sla = Optional.ofNullable(job.getCustomHeaders().getOrDefault("sla", "PT30M")).orElse("PT30M");

        Map<String, Object> out = new HashMap<>();
        // set safe defaults so later steps won't choke even if truly bypassed
        out.put("riskLevel", "LOW");
        out.put("requiresEnhancedApproval", false);
        out.put("riskScore", 0);

        if ("true".equalsIgnoreCase(bypass)) {
            log.warn("⏭️ BYPASSING passthrough-risk | pi={} sla={} vars={}",
                     job.getProcessInstanceKey(), sla, job.getVariablesAsMap());
            client.newCompleteCommand(job).variables(out).send().join();
            return;
        }

        // If someday you want real logic here, do it and then complete:
        // ... compute riskLevel/riskScore/requiresEnhancedApproval ...
        client.newCompleteCommand(job).variables(out).send().join();
    }

}