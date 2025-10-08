package com.bwc.travel_request_management.client;

import com.bwc.travel_request_management.client.dto.CreateWorkflowRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ✅ Feign client to call the Approval Workflow Service securely.
 * All internal requests will automatically include the internal gateway secret header.
 */
@FeignClient(
    name = "approval-workflow-service",
    url = "${services.workflow.url:http://localhost:8088}", // ✅ Use API Gateway (port 8088)
    configuration = com.bwc.travel_request_management.config.FeignConfig.class // attach header automatically
)
public interface WorkflowServiceClient {

    @PostMapping("/api/workflows/initiate")
    void createWorkflow(@RequestBody CreateWorkflowRequest request);
}
