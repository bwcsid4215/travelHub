package com.bwc.approval_workflow_service.controller;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import io.camunda.zeebe.client.ZeebeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class ProcessDebugController {
    
    private final ZeebeClient zeebeClient;
    private final WebClient tasklistClient;
    
    @GetMapping("/process-instances/{processInstanceKey}")
    public Map<String, Object> getProcessInstanceDetails(@PathVariable long processInstanceKey) {
        try {
            // Get process instance details
            var topology = zeebeClient.newTopologyRequest().send().join();
            
            // Get active elements
            var activatedJobs = zeebeClient.newActivateJobsCommand()
                    .jobType("user-task")
                    .maxJobsToActivate(10)
                    .workerName("debug-worker")
                    .timeout(Duration.ofSeconds(10))
                    .send()
                    .join();
            
            // Check user tasks in Tasklist
            Map<String, Object> taskQuery = Map.of(
                "processInstanceKey", processInstanceKey,
                "pageSize", 50
            );
            
            var taskResponse = tasklistClient.post()
                    .uri("/v1/tasks/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(taskQuery)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            
            return Map.of(
                "processInstanceKey", processInstanceKey,
                "topology", topology.toString(),
                "activatedJobs", activatedJobs.getJobs().size(),
                "tasklistResponse", taskResponse
            );
            
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
    
    @GetMapping("/check-bpmn-process")
    public Map<String, Object> checkBpmnProcess() {
        try {
            // Get all process definitions
            var processDefinitions = zeebeClient.newDeployResourceCommand()
                    .addResourceFromClasspath("bpmn/travel_approval_process.bpmn")
                    .send()
                    .join();
            
            return Map.of(
                "deployedProcesses", processDefinitions.getProcesses().stream()
                    .map(p -> Map.of(
                        "bpmnProcessId", p.getBpmnProcessId(),
                        "version", p.getVersion(),
                        "processDefinitionKey", p.getProcessDefinitionKey()
                    )).collect(Collectors.toList())
            );
            
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}