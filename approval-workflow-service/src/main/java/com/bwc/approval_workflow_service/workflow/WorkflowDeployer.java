package com.bwc.approval_workflow_service.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowDeployer {

    private final ZeebeClient zeebeClient;

    @PostConstruct
    public void deployProcess() {
        try {
            zeebeClient.newDeployResourceCommand()
                    .addResourceFromClasspath("bpmn/travel_approval_process.bpmn")
                    .send()
                    .join(10, java.util.concurrent.TimeUnit.SECONDS);
            log.info("✅ Travel Approval Workflow deployed successfully!");
        } catch (Exception e) {
            log.error("❌ Failed to deploy BPMN process: {}", e.getMessage(), e);
        }
    }
}