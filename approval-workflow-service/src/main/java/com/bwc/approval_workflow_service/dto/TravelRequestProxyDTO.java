package com.bwc.approval_workflow_service.dto;

import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelRequestProxyDTO {
    private UUID travelRequestId;
    private UUID employeeId;
    private UUID projectId;
    private UUID managerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String purpose;
    private Double estimatedBudget;
    private String origin;
    private String travelDestination;
}
