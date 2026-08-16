package com.expensemanagement.service;

import com.expensemanagement.dto.TimesheetDto;
import com.expensemanagement.model.*;
import com.expensemanagement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock
    private TimesheetRepository timesheetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;
    private NotificationService notificationService;
    private ActivityLogService activityLogService;
    private TimesheetService timesheetService;

    private Organization testOrg;
    private User testUser;
    private Project testProject;
    private ProjectAssignment testAssignment;
    private TimesheetDto timesheetDto;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogService(activityLogRepository);
        emailService = new NoOpEmailService(mailSender);
        notificationService = new NotificationService(notificationRepository, userRepository, emailService, webSocketService);
        timesheetService = new TimesheetService(timesheetRepository, userRepository, organizationRepository,
                projectRepository, projectAssignmentRepository, activityLogService, notificationService, webSocketService);

        testOrg = Organization.builder().id(1L).name("Test Org").subdomain("test").build();

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrg)
                .isOnBench(false)
                .build();

        testProject = Project.builder()
                .id(1L)
                .code("PROJ001")
                .name("Test Project")
                .organization(testOrg)
                .build();

        testAssignment = ProjectAssignment.builder()
                .id(1L)
                .user(testUser)
                .project(testProject)
                .isActive(true)
                .build();

        timesheetDto = TimesheetDto.builder()
                .date(LocalDate.of(2026, 8, 17))
                .projectCode("PROJ001")
                .hours(8.0)
                .description("Worked on project")
                .build();
    }

    private static class NoOpEmailService extends EmailService {
        public NoOpEmailService(JavaMailSender sender) {
            super(sender);
        }

        @Override
        public void sendEmail(String to, String subject, String body) {
            // no-op
        }
    }

    @Test
    void testCreateTimesheet_WithProject_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(timesheetRepository.findByUserIdAndDate(1L, timesheetDto.getDate())).thenReturn(Optional.empty());
        when(projectRepository.findByCodeAndOrganizationId("PROJ001", 1L)).thenReturn(Optional.of(testProject));
        when(projectAssignmentRepository.findByUserIdAndProjectIdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(testAssignment));
        when(timesheetRepository.save(any(Timesheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimesheetDto result = timesheetService.createTimesheet(timesheetDto, 1L, 1L);

        assertNotNull(result);
        assertEquals("PROJ001", result.getProjectCode());
        verify(timesheetRepository, times(1)).save(any(Timesheet.class));
    }

    @Test
    void testCreateTimesheet_WithBenchCode_Success() {
        testUser.setIsOnBench(true);
        timesheetDto.setProjectCode("BENCH");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(timesheetRepository.findByUserIdAndDate(1L, timesheetDto.getDate())).thenReturn(Optional.empty());
        when(timesheetRepository.save(any(Timesheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimesheetDto result = timesheetService.createTimesheet(timesheetDto, 1L, 1L);

        assertNotNull(result);
        assertEquals("BENCH", result.getProjectCode());
    }

    @Test
    void testCreateTimesheet_DuplicateDate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(timesheetRepository.findByUserIdAndDate(1L, timesheetDto.getDate()))
                .thenReturn(Optional.of(Timesheet.builder().build()));

        assertThrows(RuntimeException.class, () -> {
            timesheetService.createTimesheet(timesheetDto, 1L, 1L);
        });
    }

    @Test
    void testSubmitTimesheet_Success() {
        Timesheet timesheet = Timesheet.builder()
                .id(1L)
                .user(testUser)
                .status(Timesheet.TimesheetStatus.DRAFT)
                .organization(testOrg)
                .build();

        when(timesheetRepository.findById(1L)).thenReturn(Optional.of(timesheet));
        when(timesheetRepository.save(any(Timesheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimesheetDto result = timesheetService.submitTimesheet(1L, 1L, 1L);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        verify(timesheetRepository, times(1)).save(any(Timesheet.class));
    }

    @Test
    void testApproveTimesheet_Success() {
        User manager = User.builder().id(2L).role(User.UserRole.MANAGER).build();
        testUser.setManager(manager);
        
        Timesheet timesheet = Timesheet.builder()
                .id(1L)
                .user(testUser)
                .status(Timesheet.TimesheetStatus.SUBMITTED)
                .organization(testOrg)
                .build();

        when(timesheetRepository.findById(1L)).thenReturn(Optional.of(timesheet));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(timesheetRepository.save(any(Timesheet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimesheetDto result = timesheetService.approveTimesheet(1L, 2L, "Approved");

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
    }
}
