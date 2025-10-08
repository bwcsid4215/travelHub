package com.bwc.employee_service.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bwc.employee_management_service.entity.Employee;
import com.bwc.employee_management_service.repository.EmployeeRepository;
import com.bwc.employee_service.dto.EmployeeProxyDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeProxyController {

    private final EmployeeRepository employeeRepository;

    /**
     * Fetch lightweight employee data for inter-service use (e.g. Travel Request Service)
     */
    @GetMapping("/proxy/{id}")
    public EmployeeProxyDTO getProxy(@PathVariable UUID id) {
        log.info("🔍 Fetching employee proxy for ID: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with ID: " + id));

        UUID managerId = (employee.getManager() != null) ? employee.getManager().getEmployeeId() : null;

        Set<String> roleNames = employee.getRoles().stream()
                .map(role -> role.getRoleName()) // assuming Role entity has `roleName` field
                .collect(Collectors.toSet());

        return EmployeeProxyDTO.builder()
                .employeeId(employee.getEmployeeId())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .level(employee.getLevel())
                .managerId(managerId)
                .roles(roleNames)
                .build();
    }

    /**
     * Fetch all employees having a specific role (optional helper endpoint)
     */
    @GetMapping("/proxy/by-role")
    public List<EmployeeProxyDTO> getByRole(@RequestParam("role") String role) {
        log.info("🔍 Fetching employees by role: {}", role);

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(emp -> emp.getRoles().stream()
                        .anyMatch(r -> r.getRoleName().equalsIgnoreCase(role)))
                .toList();

        return employees.stream()
                .map(emp -> EmployeeProxyDTO.builder()
                        .employeeId(emp.getEmployeeId())
                        .fullName(emp.getFullName())
                        .email(emp.getEmail())
                        .department(emp.getDepartment())
                        .level(emp.getLevel())
                        .managerId(emp.getManager() != null ? emp.getManager().getEmployeeId() : null)
                        .roles(emp.getRoles().stream().map(r -> r.getRoleName()).collect(Collectors.toSet()))
                        .build())
                .toList();
    }
}
