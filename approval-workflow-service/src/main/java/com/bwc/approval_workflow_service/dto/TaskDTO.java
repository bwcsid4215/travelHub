package com.bwc.approval_workflow_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {
    private String taskId;
    private String taskName;
    private String processInstanceId;
    private String processDefinitionId;
    private LocalDateTime createdDate;
    private LocalDateTime dueDate;
    private String assignee;
    private String candidateGroup;
    private Map<String, Object> variables;
    private String status; // PENDING, APPROVED, REJECTED
    private String outcome;
}
