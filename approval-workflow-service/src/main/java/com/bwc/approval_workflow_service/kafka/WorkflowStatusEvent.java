package com.bwc.approval_workflow_service.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowStatusEvent {
    private UUID travelRequestId;
    private String status;
    private String comments;
}
