package com.expensemanagement.controller;

import com.expensemanagement.dto.ProjectDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for project management operations.
 * Only Admin can manage projects (create, update, delete).
 * All other roles can only view projects.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    
    private final ProjectService projectService;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(
            @Valid @RequestBody ProjectDto projectDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Admin can create projects
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ProjectDto created = projectService.createProject(projectDto, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDto projectDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Admin can update projects
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ProjectDto updated = projectService.updateProject(id, projectDto, organizationId);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Get all projects in the organization.
     * Only Admin can view all projects (privacy: managers should only see their projects via /my-projects)
     */
    @GetMapping
    public ResponseEntity<Page<ProjectDto>> getProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Admin can see all projects (privacy protection)
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ProjectDto> projects = projectService.getProjects(organizationId, pageable);
        return ResponseEntity.ok(projects);
    }
    
    /**
     * Get active projects relevant to the current user.
     * - Admin: All active projects
     * - Others: Only projects they're assigned to
     */
    @GetMapping("/active")
    public ResponseEntity<List<ProjectDto>> getActiveProjects(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        if ("ADMIN".equals(userRole)) {
            // Admin can see all active projects
            List<ProjectDto> projects = projectService.getActiveProjects(organizationId);
            return ResponseEntity.ok(projects);
        } else {
            // Others can only see projects they're assigned to
            List<ProjectDto> projects = projectService.getActiveProjectsForUser(userId);
            return ResponseEntity.ok(projects);
        }
    }
    
    /**
     * Get projects managed by the current user (for managers to see their projects)
     */
    @GetMapping("/my-projects")
    public ResponseEntity<List<ProjectDto>> getMyProjects(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        List<ProjectDto> projects = projectService.getProjectsByManager(userId);
        return ResponseEntity.ok(projects);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> getProjectById(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        ProjectDto project = projectService.getProjectById(id, organizationId);
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            HttpServletRequest request) {

        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);

        // Only Admin can delete projects
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        projectService.deactivateProject(id, organizationId);
        return ResponseEntity.noContent().build();
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
