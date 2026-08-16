package com.expensemanagement.service;

import com.expensemanagement.dto.ProjectDto;
import com.expensemanagement.model.ActivityLog;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Project;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.ProjectAssignmentRepository;
import com.expensemanagement.repository.ProjectRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for project management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ActivityLogService activityLogService;
    
    @Transactional
    public ProjectDto createProject(ProjectDto projectDto, Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        // Check if project code already exists
        if (projectRepository.findByCodeAndOrganizationId(projectDto.getCode(), organizationId).isPresent()) {
            throw new RuntimeException("Project code already exists in this organization");
        }
        
        // Resolve manager if provided
        User manager = null;
        if (projectDto.getManagerId() != null) {
            manager = userRepository.findById(projectDto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
            if (!manager.getOrganization().getId().equals(organizationId)) {
                throw new RuntimeException("Manager does not belong to this organization");
            }
            if (manager.getRole() != User.UserRole.MANAGER && manager.getRole() != User.UserRole.ADMIN) {
                throw new RuntimeException("Assigned user must have MANAGER or ADMIN role");
            }
        }
        
        Project project = Project.builder()
                .code(projectDto.getCode())
                .name(projectDto.getName())
                .description(projectDto.getDescription())
                .startDate(projectDto.getStartDate())
                .endDate(projectDto.getEndDate())
                .status(Project.ProjectStatus.valueOf(projectDto.getStatus()))
                .organization(organization)
                .manager(manager)
                .build();
        
        project = projectRepository.save(project);
        log.info("Created project {} with manager {}", project.getCode(), manager != null ? manager.getEmail() : "none");
        
        // Log activity
        activityLogService.logActivity(
                ActivityLog.ActivityType.PROJECT_CREATED,
                "Created project: " + project.getName() + " (" + project.getCode() + ")",
                organization
        );
        
        return convertToDto(project);
    }
    
    @Transactional
    public ProjectDto updateProject(Long projectId, ProjectDto projectDto, Long organizationId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Project does not belong to this organization");
        }
        
        // Check code uniqueness if changed
        if (!project.getCode().equals(projectDto.getCode())) {
            if (projectRepository.findByCodeAndOrganizationId(projectDto.getCode(), organizationId).isPresent()) {
                throw new RuntimeException("Project code already exists");
            }
        }
        
        // Resolve manager if provided
        if (projectDto.getManagerId() != null) {
            User manager = userRepository.findById(projectDto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
            if (!manager.getOrganization().getId().equals(organizationId)) {
                throw new RuntimeException("Manager does not belong to this organization");
            }
            if (manager.getRole() != User.UserRole.MANAGER && manager.getRole() != User.UserRole.ADMIN) {
                throw new RuntimeException("Assigned user must have MANAGER or ADMIN role");
            }
            project.setManager(manager);
        } else {
            project.setManager(null);
        }
        
        project.setCode(projectDto.getCode());
        project.setName(projectDto.getName());
        project.setDescription(projectDto.getDescription());
        project.setStartDate(projectDto.getStartDate());
        project.setEndDate(projectDto.getEndDate());
        project.setStatus(Project.ProjectStatus.valueOf(projectDto.getStatus()));
        
        project = projectRepository.save(project);
        log.info("Updated project {} with manager {}", project.getCode(), 
                project.getManager() != null ? project.getManager().getEmail() : "none");
        
        // Log activity
        activityLogService.logActivity(
                ActivityLog.ActivityType.PROJECT_UPDATED,
                "Updated project: " + project.getName() + " (" + project.getCode() + ")",
                project.getOrganization()
        );
        
        return convertToDto(project);
    }
    
    public Page<ProjectDto> getProjects(Long organizationId, Pageable pageable) {
        return projectRepository.findByOrganizationId(organizationId, pageable)
                .map(this::convertToDto);
    }
    
    public ProjectDto getProjectById(Long projectId, Long organizationId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Project does not belong to this organization");
        }
        
        return convertToDto(project);
    }
    
    public List<ProjectDto> getActiveProjects(Long organizationId) {
        return projectRepository.findByOrganizationIdAndStatus(
                organizationId, 
                Project.ProjectStatus.ACTIVE
        ).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all projects managed by a specific user
     */
    public List<ProjectDto> getProjectsByManager(Long managerId) {
        return projectRepository.findByManagerId(managerId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get active projects that a user is assigned to (for timesheet dropdown, etc.)
     */
    public List<ProjectDto> getActiveProjectsForUser(Long userId) {
        // Get all active assignments for this user
        List<com.expensemanagement.model.ProjectAssignment> assignments = 
            projectAssignmentRepository.findByUserIdAndIsActiveTrue(userId);
        
        // Extract unique projects that are ACTIVE
        return assignments.stream()
                .map(com.expensemanagement.model.ProjectAssignment::getProject)
                .filter(p -> p.getStatus() == Project.ProjectStatus.ACTIVE)
                .distinct()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateProject(Long projectId, Long organizationId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        if (!project.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Project does not belong to this organization");
        }
        project.setStatus(Project.ProjectStatus.INACTIVE);
        projectRepository.save(project);
        log.info("Deactivated project {}", project.getCode());
        
        // Log activity
        activityLogService.logActivity(
                ActivityLog.ActivityType.PROJECT_DELETED,
                "Deactivated project: " + project.getName() + " (" + project.getCode() + ")",
                project.getOrganization()
        );
    }

    public ProjectDto createProjectInternal(ProjectDto dto, Long organizationId) {
        return createProject(dto, organizationId);
    }

    public ProjectDto updateProjectInternal(Long id, ProjectDto dto, Long organizationId) {
        return updateProject(id, dto, organizationId);
    }

    public void deleteProjectInternal(Long id, Long organizationId) {
        deactivateProject(id, organizationId);
    }
    
    private ProjectDto convertToDto(Project project) {
        ProjectDto.ProjectDtoBuilder builder = ProjectDto.builder()
                .id(project.getId())
                .code(project.getCode())
                .name(project.getName())
                .description(project.getDescription())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .status(project.getStatus().name())
                .organizationId(project.getOrganization().getId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt());
        
        if (project.getManager() != null) {
            builder.managerId(project.getManager().getId())
                   .managerName(project.getManager().getFirstName() + " " + project.getManager().getLastName());
        }
        
        return builder.build();
    }
}
