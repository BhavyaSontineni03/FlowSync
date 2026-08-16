package com.expensemanagement.controller;

import com.expensemanagement.dto.ProjectAssignmentDto;
import com.expensemanagement.model.ProjectAssignment;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.ProjectAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for project assignment operations.
 * Only Admin can manage assignments (assign/unassign employees).
 * Other roles can view assignments.
 */
@RestController
@RequestMapping("/api/project-assignments")
@RequiredArgsConstructor
public class ProjectAssignmentController {
    
    private final ProjectAssignmentService assignmentService;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping
    public ResponseEntity<ProjectAssignmentDto> assignEmployee(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Admin can assign employees to projects
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long userId = Long.valueOf(body.get("userId").toString());
        Long projectId = Long.valueOf(body.get("projectId").toString());
        String role = body.get("role") != null ? body.get("role").toString() : "Team Member";
        
        ProjectAssignment assignment = assignmentService.assignEmployeeToProject(userId, projectId, role, organizationId);
        return ResponseEntity.ok(convertToDto(assignment));
    }
    
    @DeleteMapping("/{userId}/{projectId}")
    public ResponseEntity<Void> unassignEmployee(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Admin can unassign employees from projects
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        assignmentService.unassignEmployeeFromProject(userId, projectId, organizationId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ProjectAssignmentDto>> getAssignmentsByProject(
            @PathVariable Long projectId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        List<ProjectAssignment> assignments = assignmentService.getAssignmentsByProject(projectId, organizationId);
        return ResponseEntity.ok(assignments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ProjectAssignmentDto>> getAssignmentsByUser(
            @PathVariable Long userId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long currentUserId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Users can only see their own assignments unless they're HR/Admin/Manager
        // Managers need to see team assignments for their projects
        if (!"HR".equals(userRole) && !"ADMIN".equals(userRole) && !"MANAGER".equals(userRole) && !userId.equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<ProjectAssignment> assignments = assignmentService.getAssignmentsByUser(userId);
        return ResponseEntity.ok(assignments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }
    
    private ProjectAssignmentDto convertToDto(ProjectAssignment assignment) {
        var project = assignment.getProject();
        String managerName = null;
        if (project.getManager() != null) {
            managerName = project.getManager().getFirstName() + " " + project.getManager().getLastName();
        }
        
        return ProjectAssignmentDto.builder()
                .id(assignment.getId())
                .userId(assignment.getUser().getId())
                .userName(assignment.getUser().getFirstName() + " " + assignment.getUser().getLastName())
                .userEmail(assignment.getUser().getEmail())
                .projectId(project.getId())
                .projectCode(project.getCode())
                .projectName(project.getName())
                .projectStatus(project.getStatus().name())
                .projectStartDate(project.getStartDate())
                .projectEndDate(project.getEndDate())
                .managerName(managerName)
                .assignedDate(assignment.getAssignedDate())
                .unassignedDate(assignment.getUnassignedDate())
                .role(assignment.getRole())
                .isActive(assignment.getIsActive())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
