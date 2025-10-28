package com.bwc.approval_workflow_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private String employeeId;
    private String fullName;
    private String department;
    private String role;
}