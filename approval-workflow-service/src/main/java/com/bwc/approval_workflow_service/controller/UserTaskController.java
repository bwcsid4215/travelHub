package com.bwc.approval_workflow_service.controller;

import com.bwc.approval_workflow_service.workflow.UserTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class UserTaskController {

    private final UserTaskService userTaskService;

    @GetMapping("/{processInstanceId}")
    public List<Map<String, Object>> getTasks(@PathVariable String processInstanceId) {
        return userTaskService.getUserTasks(processInstanceId);
    }

    @PostMapping("/{taskId}/complete")
    public String completeTask(@PathVariable String taskId, @RequestBody Map<String, Object> variables) {
        userTaskService.completeTask(taskId, variables);
        return "✅ Task " + taskId + " completed successfully!";
    }
}
