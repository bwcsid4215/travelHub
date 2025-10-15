package com.bwc.approval_workflow_service.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelRequestProxyDTO {
    private UUID travelRequestId;
    private UUID employeeId;
    private UUID projectId;
    private UUID managerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String purpose;
    private Double estimatedBudget;
    private String travelDestination;
    private String origin;
}
