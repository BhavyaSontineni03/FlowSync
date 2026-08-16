package com.expensemanagement.service;

import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Project;
import com.expensemanagement.model.ProjectAssignment;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.ProjectAssignmentRepository;
import com.expensemanagement.repository.ProjectRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing project assignments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectAssignmentService {
    
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final ActivityLogService activityLogService;
    
    @Transactional
    public ProjectAssignment assignEmployeeToProject(Long userId, Long projectId, String role, Long organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!user.getOrganization().getId().equals(organizationId) || 
            !project.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("User and project must belong to the same organization");
        }
        
        // Check if already assigned to THIS project
        if (assignmentRepository.findByUserIdAndProjectIdAndIsActiveTrue(userId, projectId).isPresent()) {
            throw new RuntimeException("User is already assigned to this project");
        }
        
        // Note: Employees CAN be on multiple projects simultaneously
        // We don't deactivate existing assignments when adding a new one
        
        // Set user off bench (they have at least one active project now)
        user.setIsOnBench(false);
        userRepository.save(user);
        
        ProjectAssignment assignment = ProjectAssignment.builder()
                .user(user)
                .project(project)
                .assignedDate(LocalDate.now())
                .role(role != null ? role : "Team Member")
                .isActive(true)
                .build();
        
        assignment = assignmentRepository.save(assignment);
        log.info("Assigned {} to project {}", user.getEmail(), project.getCode());
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.PROJECT_EMPLOYEE_ASSIGNED,
                "Assigned " + user.getFirstName() + " " + user.getLastName() + " to project " + project.getName() + " as " + role,
                user,
                user.getOrganization(),
                "ProjectAssignment",
                assignment.getId(),
                null
        );
        
        return assignment;
    }
    
    @Transactional
    public void unassignEmployeeFromProject(Long userId, Long projectId, Long organizationId) {
        ProjectAssignment assignment = assignmentRepository.findByUserIdAndProjectIdAndIsActiveTrue(userId, projectId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));
        
        if (!assignment.getUser().getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Assignment does not belong to this organization");
        }
        
        assignment.setIsActive(false);
        assignment.setUnassignedDate(LocalDate.now());
        assignmentRepository.save(assignment);
        
        User user = assignment.getUser();
        Project project = assignment.getProject();
        log.info("Unassigned {} from project {}", user.getEmail(), project.getCode());
        
        // Log activity
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.PROJECT_EMPLOYEE_UNASSIGNED,
                "Unassigned " + user.getFirstName() + " " + user.getLastName() + " from project " + project.getName(),
                user,
                user.getOrganization(),
                "ProjectAssignment",
                assignment.getId(),
                null
        );
        
        // Check if user has any other active assignments
        List<ProjectAssignment> activeAssignments = assignmentRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeAssignments.isEmpty()) {
            // User is now on bench
            user.setIsOnBench(true);
            userRepository.save(user);
            log.info("User {} is now on bench", user.getEmail());
        }
    }
    
    public List<ProjectAssignment> getAssignmentsByProject(Long projectId, Long organizationId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Project does not belong to this organization");
        }
        
        return assignmentRepository.findByProjectIdAndIsActiveTrue(projectId);
    }
    
    /**
     * Get all assignments for a user (both active and past)
     * This allows employees to see their complete project history
     */
    public List<ProjectAssignment> getAssignmentsByUser(Long userId) {
        return assignmentRepository.findByUserId(userId);
    }
    
    /**
     * Get only active assignments for a user
     */
    public List<ProjectAssignment> getActiveAssignmentsByUser(Long userId) {
        return assignmentRepository.findByUserIdAndIsActiveTrue(userId);
    }
}
