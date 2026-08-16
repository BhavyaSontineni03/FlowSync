package com.expensemanagement.service;

import com.expensemanagement.dto.ProjectDto;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Project;
import com.expensemanagement.repository.ActivityLogRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.ProjectAssignmentRepository;
import com.expensemanagement.repository.ProjectRepository;
import com.expensemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    private ActivityLogService activityLogService;
    private ProjectService projectService;

    private Organization testOrg;
    private Project testProject;
    private ProjectDto projectDto;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogService(activityLogRepository);
        projectService = new ProjectService(projectRepository, organizationRepository, userRepository, projectAssignmentRepository, activityLogService);

        testOrg = Organization.builder()
                .id(1L)
                .name("Test Org")
                .subdomain("test")
                .build();

        testProject = Project.builder()
                .id(1L)
                .code("PROJ001")
                .name("Test Project")
                .description("Test Description")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status(Project.ProjectStatus.ACTIVE)
                .organization(testOrg)
                .build();

        projectDto = ProjectDto.builder()
                .code("PROJ001")
                .name("Test Project")
                .description("Test Description")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .status("ACTIVE")
                .build();
    }

    @Test
    void testCreateProject_Success() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(projectRepository.findByCodeAndOrganizationId("PROJ001", 1L)).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        ProjectDto result = projectService.createProject(projectDto, 1L);

        assertNotNull(result);
        assertEquals("PROJ001", result.getCode());
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void testCreateProject_DuplicateCode() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(projectRepository.findByCodeAndOrganizationId("PROJ001", 1L)).thenReturn(Optional.of(testProject));

        assertThrows(RuntimeException.class, () -> {
            projectService.createProject(projectDto, 1L);
        });
    }

    @Test
    void testUpdateProject_Success() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.findByCodeAndOrganizationId("PROJ002", 1L)).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        projectDto.setCode("PROJ002");
        ProjectDto result = projectService.updateProject(1L, projectDto, 1L);

        assertNotNull(result);
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void testGetProjects() {
        List<Project> projects = Arrays.asList(testProject);
        Page<Project> projectPage = new PageImpl<>(projects, PageRequest.of(0, 20), 1);
        when(projectRepository.findByOrganizationId(eq(1L), any(Pageable.class))).thenReturn(projectPage);

        Page<ProjectDto> result = projectService.getProjects(1L, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    void testGetActiveProjects() {
        List<Project> projects = Arrays.asList(testProject);
        when(projectRepository.findByOrganizationIdAndStatus(1L, Project.ProjectStatus.ACTIVE))
                .thenReturn(projects);

        List<ProjectDto> result = projectService.getActiveProjects(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

