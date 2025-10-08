//package com.bwc.employee_management_service.specification;
//
//import com.bwc.employee_management_service.entity.Employee;
//import org.springframework.data.jpa.domain.Specification;
//import org.springframework.util.StringUtils;
//
//import java.util.UUID;
//
//public class EmployeeSpecifications {
//    
//    public static Specification<Employee> hasDepartment(String department) {
//        return (root, query, criteriaBuilder) -> 
//            StringUtils.hasText(department) ? 
//            criteriaBuilder.equal(root.get("department"), department) : 
//            null;
//    }
//    
//    public static Specification<Employee> isActive(Boolean active) {
//        return (root, query, criteriaBuilder) -> 
//            active != null ? 
//            criteriaBuilder.equal(root.get("active"), active) : 
//            criteriaBuilder.equal(root.get("active"), true);
//    }
//    
//    public static Specification<Employee> hasManager(UUID managerId) {
//        return (root, query, criteriaBuilder) -> 
//            managerId != null ? 
//            criteriaBuilder.equal(root.get("manager").get("id"), managerId) : 
//            null;
//    }
//    
//    public static Specification<Employee> nameOrEmailContains(String keyword) {
//        return (root, query, criteriaBuilder) -> 
//            StringUtils.hasText(keyword) ? 
//            criteriaBuilder.or(
//                criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), "%" + keyword.toLowerCase() + "%"),
//                criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + keyword.toLowerCase() + "%")
//            ) : null;
//    }
//}