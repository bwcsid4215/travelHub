package com.bwc.approval_workflow_service.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeProxyDTO {
    private UUID employeeId;
    private String fullName;
    private String email;
    private String department;
    private String level;
    private UUID managerId;
    private Set<String> roles;
    private Set<UUID> projectIds;
}
