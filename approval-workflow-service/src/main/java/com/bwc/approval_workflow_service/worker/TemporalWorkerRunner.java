// src/main/java/com/bwc/approval_workflow_service/worker/TemporalWorkerRunner.java
package com.bwc.approval_workflow_service.worker;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.bwc.approval_workflow_service.activities.TravelActivitiesImpl;
import com.bwc.approval_workflow_service.workflow.PostTravelWorkflowImpl;
import com.bwc.approval_workflow_service.workflow.PreTravelWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;


public class TemporalWorkerRunner implements CommandLineRunner {

    private final WorkflowClient client;
    private final TravelActivitiesImpl activities;

    public TemporalWorkerRunner(WorkflowClient client, TravelActivitiesImpl activities) {
        this.client = client;
        this.activities = activities;
    }

    @Override
    public void run(String... args) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker("TRAVEL_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(PreTravelWorkflowImpl.class, PostTravelWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        System.out.println("🧠 Temporal worker listening on TRAVEL_TASK_QUEUE …");
    }
}
