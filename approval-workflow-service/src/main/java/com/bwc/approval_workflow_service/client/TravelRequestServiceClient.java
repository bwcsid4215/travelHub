package com.bwc.approval_workflow_service.client;

import com.bwc.approval_workflow_service.dto.TravelRequestProxyDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@FeignClient(name = "travel-request-service", url = "${services.travel-request.url:http://localhost:8080}")
public interface TravelRequestServiceClient {
    
    @GetMapping("/api/travel-requests/{id}")
    TravelRequestProxyDTO getTravelRequest(@PathVariable("id") UUID id);
    
    @PostMapping("/api/travel-requests/{id}/status")
    void updateRequestStatus(@PathVariable("id") UUID id, @RequestParam String status);
    
    @PostMapping("/api/travel-requests/{id}/actual-cost")
    void updateActualCost(@PathVariable("id") UUID id, @RequestParam Double actualCost);
    
    // NEW: Proxy endpoint for optimized workflow initiation
    @GetMapping("/api/travel-requests/proxy/{id}")
    TravelRequestProxyDTO getTravelRequestProxy(@PathVariable("id") UUID id);
}