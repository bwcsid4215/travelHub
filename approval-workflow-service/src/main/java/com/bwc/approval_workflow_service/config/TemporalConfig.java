package com.bwc.approval_workflow_service.config;

import com.bwc.approval_workflow_service.activities.TravelActivitiesImpl;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflowImpl;
import com.bwc.approval_workflow_service.workflow.PostTravelWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TemporalConfig {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        // Connects to Temporal service (defaults to localhost:7233)
        return WorkflowServiceStubs.newInstance();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs service) {
        return WorkflowClient.newInstance(service);
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public Worker registerWorker(WorkerFactory factory,
                                 TravelActivitiesImpl activitiesImpl) { // ✅ only this one autowired
        String taskQueue = "TRAVEL_TASK_QUEUE";

        Worker worker = factory.newWorker(taskQueue);

        // ✅ Register workflow implementation *classes* (not beans)
        worker.registerWorkflowImplementationTypes(
            PreTravelWorkflowImpl.class,
            PostTravelWorkflowImpl.class
        );

        // ✅ Register Spring-managed activity implementation
        worker.registerActivitiesImplementations(activitiesImpl);

        // ✅ Start the factory once everything is registered
        factory.start();

        return worker;
    }
}
