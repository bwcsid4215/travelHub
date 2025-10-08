// employee-management-service/src/main/java/com/bwc/employee_management_service/mapper/EmployeeMapper.java
package com.bwc.employee_management_service.mapper;

import com.bwc.employee_management_service.dto.EmployeeRequest;
import com.bwc.employee_management_service.dto.EmployeeResponse;
import com.bwc.employee_management_service.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "employeeId", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "manager", ignore = true)
    @Mapping(target = "subordinates", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "dateJoined", expression = "java(new java.util.Date())")
    @Mapping(target = "isActive", constant = "true")
    Employee toEntity(EmployeeRequest request);

    @Mapping(target = "managerId", source = "manager.employeeId")
    @Mapping(target = "managerName", source = "manager.fullName")
    @Mapping(target = "roleIds", source = "roles", qualifiedByName = "mapRolesToIds")
    @Mapping(target = "projectIds", source = "projects", qualifiedByName = "mapProjectsToIds")
    EmployeeResponse toResponse(Employee employee);

    @Named("mapRolesToIds")
    default Set<UUID> mapRolesToIds(Set<com.bwc.employee_management_service.entity.Role> roles) {
        if (roles == null) return Collections.emptySet();
        return roles.stream()
                .map(com.bwc.employee_management_service.entity.Role::getRoleId)
                .collect(Collectors.toSet());
    }

    @Named("mapProjectsToIds")
    default Set<UUID> mapProjectsToIds(Set<com.bwc.employee_management_service.entity.Project> projects) {
        if (projects == null) return Collections.emptySet();
        return projects.stream()
                .map(com.bwc.employee_management_service.entity.Project::getProjectId)
                .collect(Collectors.toSet());
    }
}