package com.bwc.approval_workflow_service;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.bwc.approval_workflow_service")
@EnableFeignClients(basePackages = "com.bwc.approval_workflow_service.client")
// Keep the same file name; BPMN process id is handled inside the XML
@Deployment(resources = "classpath:bpmn/travel_approval_process.bpmn")
public class ApprovalWorkflowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalWorkflowServiceApplication.class, args);
    }
}
