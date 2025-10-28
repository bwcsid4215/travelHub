
package com.bwc.approval_workflow_service.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTasksResponse {
    private java.util.List<TaskDTO> pendingTasks;
    private java.util.List<TaskDTO> completedTasks;
    private UserInfo userInfo;
}