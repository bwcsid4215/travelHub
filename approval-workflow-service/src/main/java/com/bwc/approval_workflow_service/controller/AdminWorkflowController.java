package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.dto.TaskDTO;
import com.bwc.approval_workflow_service.dto.UserTasksResponse;
import com.bwc.approval_workflow_service.workflow.EnhancedUserTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/workflow")
@RequiredArgsConstructor
@Slf4j
public class AdminWorkflowController {

    private final EnhancedUserTaskService userTaskService;

    // ✅ 1️⃣ — Organization Summary
    @GetMapping("/summary")
    public Map<String, Object> getWorkflowSummary() {
        try {
            List<String> roles = List.of("MANAGER", "FINANCE", "HR", "TRAVEL_DESK");
            Map<String, Object> summary = new LinkedHashMap<>();
            long totalPending = 0, totalCompleted = 0;

            for (String role : roles) {
                UserTasksResponse tasks = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", role);
                long pending = tasks.getPendingTasks().size();
                long completed = tasks.getCompletedTasks().size();
                totalPending += pending;
                totalCompleted += completed;

                summary.put(role, Map.of(
                        "pending", pending,
                        "completed", completed
                ));
            }

            summary.put("organizationTotals", Map.of(
                    "totalPending", totalPending,
                    "totalCompleted", totalCompleted,
                    "timestamp", LocalDateTime.now()
            ));

            return summary;
        } catch (Exception e) {
            log.error("❌ Summary failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ✅ 2️⃣ — Department Metrics
    @GetMapping("/metrics/{role}")
    public Map<String, Object> getDepartmentMetrics(@PathVariable String role) {
        try {
            UserTasksResponse tasks = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", role);
            long pending = tasks.getPendingTasks().size();
            long completed = tasks.getCompletedTasks().size();

            double avgApprovalTime = tasks.getCompletedTasks().stream()
                    .mapToDouble(this::calculateApprovalDuration)
                    .filter(v -> v > 0)
                    .average().orElse(0);

            long slaBreaches = tasks.getPendingTasks().stream()
                    .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now()))
                    .count();

            return Map.of(
                    "role", role,
                    "pending", pending,
                    "completed", completed,
                    "avgApprovalTimeMinutes", Math.round(avgApprovalTime),
                    "slaBreaches", slaBreaches,
                    "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("❌ Metrics failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ✅ 3️⃣ — Auto-Approval Analytics
    @GetMapping("/auto-approvals")
    public Map<String, Object> getAutoApprovals() {
        try {
            UserTasksResponse mgrTasks = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", "MANAGER");

            List<TaskDTO> autoApproved = mgrTasks.getCompletedTasks().stream()
                    .filter(t -> Boolean.parseBoolean(
                            String.valueOf(t.getVariables().getOrDefault("managerAutoApproved", false))
                    ))
                    .collect(Collectors.toList());

            double percent = mgrTasks.getCompletedTasks().isEmpty()
                    ? 0 : (autoApproved.size() * 100.0 / mgrTasks.getCompletedTasks().size());

            return Map.of(
                    "autoApprovedCount", autoApproved.size(),
                    "totalManagerTasks", mgrTasks.getCompletedTasks().size(),
                    "autoApprovalRatePercent", Math.round(percent * 10.0) / 10.0
            );
        } catch (Exception e) {
            log.error("❌ Auto approvals failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ✅ 4️⃣ — SLA Breach List
    @GetMapping("/sla/breaches")
    public Map<String, Object> getSLABreaches() {
        try {
            List<String> roles = List.of("MANAGER", "FINANCE", "HR", "TRAVEL_DESK");
            List<Map<String, Object>> breaches = new ArrayList<>();

            for (String role : roles) {
                UserTasksResponse tasks = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", role);
                tasks.getPendingTasks().stream()
                        .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now()))
                        .forEach(t -> breaches.add(Map.of(
                                "role", role,
                                "taskName", t.getTaskName(),
                                "dueDate", t.getDueDate(),
                                "employee", t.getEmployeeProfile() != null
                                        ? t.getEmployeeProfile().getFullName()
                                        : "Unknown"
                        )));
            }

            return Map.of("totalBreaches", breaches.size(), "details", breaches);
        } catch (Exception e) {
            log.error("❌ SLA breaches failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ✅ 5️⃣ — Top Pending Employees
    @GetMapping("/top/pending")
    public Map<String, Object> getTopPendingEmployees() {
        try {
            Map<String, Long> empCounts = new HashMap<>();
            for (String role : List.of("MANAGER", "FINANCE", "HR", "TRAVEL_DESK")) {
                UserTasksResponse t = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", role);
                for (TaskDTO task : t.getPendingTasks()) {
                    String name = Optional.ofNullable(task.getEmployeeProfile())
                            .map(e -> e.getFullName())
                            .orElse("Unknown");
                    empCounts.merge(name, 1L, Long::sum);
                }
            }

            var sorted = empCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .toList();

            return Map.of("topEmployees", sorted);
        } catch (Exception e) {
            log.error("❌ Top pending failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // ✅ 6️⃣ — Org-wide Trend (default 7 days)
    @GetMapping("/trends")
    public Map<String, Object> getWorkflowTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return buildTrendsForRoles(List.of("MANAGER", "FINANCE", "HR", "TRAVEL_DESK"), "organization", days, from, to);
    }

    // ✅ 7️⃣ — Department-specific Trend (supports from/to)
    @GetMapping("/trends/{role}")
    public Map<String, Object> getDepartmentTrend(
            @PathVariable String role,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return buildTrendsForRoles(List.of(role.toUpperCase()), role.toUpperCase(), days, from, to);
    }

    // ✅ 8️⃣ — CSV Export Endpoint
    @GetMapping("/export/trends")
    public ResponseEntity<byte[]> exportTrendsToCSV(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        String roleLabel = (role != null) ? role.toUpperCase() : "ORGANIZATION";
        Map<String, Object> trends = buildTrendsForRoles(
                (role != null) ? List.of(roleLabel) : List.of("MANAGER", "FINANCE", "HR", "TRAVEL_DESK"),
                roleLabel, days, from, to
        );

        @SuppressWarnings("unchecked")
        Collection<Map<String, Object>> trendData = (Collection<Map<String, Object>>) trends.get("trendData");

        StringBuilder csv = new StringBuilder();
        csv.append("Date,New Requests,Completed,SLA Breaches\n");
        trendData.forEach(d -> csv.append(
                String.format("%s,%s,%s,%s\n",
                        d.get("date"),
                        d.get("newRequests"),
                        d.get("completed"),
                        d.get("slaBreaches"))
        ));

        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workflow_trends_" + roleLabel + ".csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(data);
    }

    // === Core Trend Logic ===
    private Map<String, Object> buildTrendsForRoles(List<String> roles, String label, int days, String from, String to) {
        try {
            LocalDate endDate = (to != null) ? LocalDate.parse(to, DateTimeFormatter.ISO_DATE) : LocalDate.now();
            LocalDate startDate = (from != null) ? LocalDate.parse(from, DateTimeFormatter.ISO_DATE) : endDate.minusDays(days - 1);

            Map<LocalDate, Map<String, Object>> dailyStats = new LinkedHashMap<>();
            LocalDate current = startDate;

            while (!current.isAfter(endDate)) {
                final LocalDate currentDate = current;

                long newReq = 0, completed = 0, sla = 0;

                for (String role : roles) {
                    UserTasksResponse resp = userTaskService.getUserTasksWithHistory("00000000-0000-0000-0000-000000000000", role);

                    newReq += resp.getPendingTasks().stream()
                            .filter(t -> t.getCreatedDate() != null &&
                                    t.getCreatedDate().toLocalDate().equals(currentDate))
                            .count();

                    completed += resp.getCompletedTasks().stream()
                            .filter(t -> t.getCreatedDate() != null &&
                                    t.getCreatedDate().toLocalDate().equals(currentDate))
                            .count();

                    sla += resp.getPendingTasks().stream()
                            .filter(t -> t.getDueDate() != null &&
                                    t.getDueDate().isBefore(LocalDateTime.now()) &&
                                    t.getCreatedDate() != null &&
                                    t.getCreatedDate().toLocalDate().equals(currentDate))
                            .count();
                }

                dailyStats.put(currentDate, Map.of(
                        "date", currentDate,
                        "newRequests", newReq,
                        "completed", completed,
                        "slaBreaches", sla
                ));

                current = current.plusDays(1);
            }

            return Map.of(
                    "role", label,
                    "trendPeriod", String.format("%s to %s", startDate, endDate),
                    "trendData", dailyStats.values()
            );
        } catch (Exception e) {
            log.error("❌ Trend build failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    // Helper
    private double calculateApprovalDuration(TaskDTO task) {
        try {
            if (task.getCreatedDate() != null && task.getDueDate() != null)
                return Duration.between(task.getCreatedDate(), task.getDueDate()).toMinutes();
        } catch (Exception ignored) {}
        return 0;
    }
}
