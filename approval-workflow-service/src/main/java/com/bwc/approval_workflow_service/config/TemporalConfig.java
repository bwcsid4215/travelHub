// src/main/java/com/bwc/approval_workflow_service/config/TemporalConfig.java
package com.bwc.approval_workflow_service.config;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Bean
    public WorkflowClient workflowClient() {
        // Connects to localhost:7233
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        return WorkflowClient.newInstance(service);
    }
}
