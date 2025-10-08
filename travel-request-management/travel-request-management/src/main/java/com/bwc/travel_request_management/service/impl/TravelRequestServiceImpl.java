package com.bwc.travel_request_management.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bwc.travel_request_management.client.WorkflowServiceClient;
import com.bwc.travel_request_management.client.dto.CreateWorkflowRequest;
import com.bwc.travel_request_management.dto.TravelRequestDTO;
import com.bwc.travel_request_management.entity.TravelRequest;
import com.bwc.travel_request_management.exception.ResourceNotFoundException;
import com.bwc.travel_request_management.mapper.TravelRequestManualMapper;
import com.bwc.travel_request_management.repository.TravelRequestRepository;
import com.bwc.travel_request_management.service.TravelRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelRequestServiceImpl implements TravelRequestService {

    private final TravelRequestRepository repository;
    private final TravelRequestManualMapper mapper;
    private final WorkflowServiceClient workflowServiceClient;

    @Override
    @Transactional
    public TravelRequestDTO createRequest(TravelRequestDTO dto) {
        log.info("Creating new travel request for employee: {}", dto.getEmployeeId());

        if (hasOverlappingRequest(dto.getEmployeeId(), dto.getStartDate(), dto.getEndDate())) {
            throw new IllegalArgumentException("Employee already has a travel request for the specified dates");
        }

        TravelRequest entity = mapper.toEntity(dto);
        entity.setStatus("DRAFT");
        TravelRequest saved = repository.save(entity);

        // Trigger workflow only after transaction commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    CreateWorkflowRequest workflowRequest = CreateWorkflowRequest.builder()
                            .travelRequestId(saved.getTravelRequestId())
                            .employeeId(saved.getEmployeeId())
                            .projectId(saved.getProjectId())
                            .workflowType("PRE_TRAVEL") // required for workflow routing
                            .estimatedCost(saved.getEstimatedBudget())
                            .build();

                    workflowServiceClient.createWorkflow(workflowRequest);
                    log.info("✅ PRE_TRAVEL workflow initiated via API Gateway for request ID: {}", saved.getTravelRequestId());
                } catch (Exception e) {
                    log.error("❌ Failed to initiate workflow for request {}: {}", saved.getTravelRequestId(), e.getMessage(), e);
                }
            }
        });

        log.info("Travel request created successfully with ID: {}", saved.getTravelRequestId());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TravelRequestDTO getRequest(UUID id) {
        return repository.findById(id).map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Travel Request not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelRequestDTO> getAllRequests() {
        return repository.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TravelRequestDTO> getAllRequests(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelRequestDTO> getRequestsByEmployee(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TravelRequestDTO> getRequestsByEmployee(UUID employeeId, Pageable pageable) {
        return repository.findByEmployeeId(employeeId, pageable).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelRequestDTO> getRequestsByProject(UUID projectId) {
        return repository.findByProjectId(projectId).stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TravelRequestDTO> getRequestsByProject(UUID projectId, Pageable pageable) {
        return repository.findByProjectId(projectId, pageable).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelRequestDTO> getRequestsByEmployeeAndDateRange(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return repository.findByEmployeeAndDateRange(employeeId, startDate, endDate).stream()
                .map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TravelRequestDTO updateRequest(UUID id, TravelRequestDTO dto) {
        log.info("Updating travel request with ID: {}", id);
        TravelRequest existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel Request not found with id: " + id));

        if (hasOverlappingRequest(existing.getEmployeeId(), dto.getStartDate(), dto.getEndDate(), id)) {
            throw new IllegalArgumentException("Employee already has another travel request for the specified dates");
        }

        existing.setEmployeeId(dto.getEmployeeId());
        existing.setProjectId(dto.getProjectId());
        existing.setStartDate(dto.getStartDate());
        existing.setEndDate(dto.getEndDate());
        existing.setPurpose(dto.getPurpose());
        existing.setManagerPresent(dto.isManagerPresent());
        existing.setStatus("UPDATED");

        TravelRequest updated = repository.save(existing);
        log.info("Travel request updated successfully with ID: {}", updated.getTravelRequestId());
        return mapper.toDto(updated);
    }

    @Override
    @Transactional
    public TravelRequestDTO patchRequest(UUID id, TravelRequestDTO dto) {
        log.info("Patching travel request with ID: {}", id);
        TravelRequest existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel Request not found with id: " + id));

        if (dto.getEmployeeId() != null) existing.setEmployeeId(dto.getEmployeeId());
        if (dto.getProjectId() != null) existing.setProjectId(dto.getProjectId());
        if (dto.getStartDate() != null) existing.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) existing.setEndDate(dto.getEndDate());
        if (dto.getPurpose() != null) existing.setPurpose(dto.getPurpose());
        existing.setManagerPresent(dto.isManagerPresent());

        TravelRequest updated = repository.save(existing);
        log.info("Travel request patched successfully with ID: {}", updated.getTravelRequestId());
        return mapper.toDto(updated);
    }

    @Override
    @Transactional
    public void deleteRequest(UUID id) {
        log.info("Deleting travel request with ID: {}", id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Travel Request not found with id: " + id);
        }
        repository.deleteById(id);
        log.info("Travel request deleted successfully with ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverlappingRequest(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return repository.existsOverlappingRequest(employeeId, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverlappingRequest(UUID employeeId, LocalDate startDate, LocalDate endDate, UUID excludeId) {
        return repository.existsOverlappingRequestExcludingId(employeeId, startDate, endDate, excludeId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getRequestCountByEmployee(UUID employeeId) {
        return repository.countByEmployeeId(employeeId);
    }

    @Override
    @Transactional
    public void updateStatus(UUID id, String status) {
        TravelRequest request = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Travel Request not found with id: " + id));
        request.setStatus(status);
        repository.save(request);
        log.info("Travel request {} status updated to {}", id, status);
    }
}
