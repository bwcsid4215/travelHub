package com.bwc.approval_workflow_service.config;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ZeebeHealthIndicator implements HealthIndicator {

    private final ZeebeClient client;

    public ZeebeHealthIndicator(ZeebeClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            client.newTopologyRequest().send().join();
            return Health.up().withDetail("zeebe", "connected").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("zeebe", "unreachable").build();
        }
    }
}
