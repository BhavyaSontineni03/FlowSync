package com.expensemanagement.service;

import com.expensemanagement.dto.LeaveRequestDto;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.LeaveRequestRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.TimesheetRepository;
import com.expensemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaveRequestService
 * Tests: Create, Approve, Reject, Duplicate Prevention, Privacy, Integration
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TimesheetRepository timesheetRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private LeaveBalanceService leaveBalanceService;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private User employee;
    private User manager;
    private Organization organization;
    private LeaveRequestDto leaveRequestDto;

    // Fixed reference dates computed relative to "now" so the suite never rots as real time
    // passes. Anchored two weeks out to safely clear any "no past dates" business rule.
    private static final LocalDate MONDAY = LocalDate.now().plusWeeks(2)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate SATURDAY = MONDAY.plusDays(5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(1L);
        organization.setName("Test Org");

        manager = new User();
        manager.setId(2L);
        manager.setFirstName("Manager");
        manager.setLastName("One");
        manager.setRole(User.UserRole.MANAGER);
        manager.setOrganization(organization);

        employee = new User();
        employee.setId(1L);
        employee.setFirstName("Employee");
        employee.setLastName("One");
        employee.setRole(User.UserRole.EMPLOYEE);
        employee.setOrganization(organization);
        employee.setManager(manager);

        leaveRequestDto = LeaveRequestDto.builder()
                .leaveType(LeaveRequest.LeaveType.VACATION)
                .startDate(MONDAY)
                .endDate(WEDNESDAY)
                .reason("Vacation")
                .build();
    }

    // Test Cases: Leave Request Creation

    @Test
    void testCreateLeaveRequest_Success() {
        // TC-LEAVE-001: Employee Creates Leave Request
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(leaveRequestRepository.findOverlappingPendingOrApprovedLeaves(any(), any(), any()))
                .thenReturn(new ArrayList<>());
        
        java.util.Map<String, Object> balanceCheck = new java.util.HashMap<>();
        balanceCheck.put("sufficient", true);
        balanceCheck.put("remaining", 10);
        balanceCheck.put("category", "Paid Leave");
        when(leaveBalanceService.checkLeaveBalance(any(), any(), anyInt())).thenReturn(balanceCheck);

        java.util.Map<String, Object> balanceSummary = new java.util.HashMap<>();
        when(leaveBalanceService.getBalanceSummary(1L)).thenReturn(balanceSummary);

        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> {
            LeaveRequest lr = invocation.getArgument(0);
            lr.setId(100L);
            return lr;
        });

        LeaveRequestDto result = leaveRequestService.createLeaveRequest(leaveRequestDto, 1L, 1L);

        assertNotNull(result);
        assertEquals(LeaveRequest.LeaveStatus.PENDING, result.getStatus());
        assertEquals(3, result.getNumberOfDays()); // Mon, Tue, Wed (excluding weekends)
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
        verify(notificationService).notifyManagersForLeaveApproval(any());
        verify(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreateLeaveRequest_DuplicatePrevention() {
        // TC-LEAVE-003: Employee Creates Leave Request - Duplicate Date
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        
        LeaveRequest existingLeave = new LeaveRequest();
        existingLeave.setStartDate(MONDAY);
        existingLeave.setEndDate(WEDNESDAY);
        existingLeave.setStatus(LeaveRequest.LeaveStatus.PENDING);
        when(leaveRequestRepository.findOverlappingPendingOrApprovedLeaves(eq(1L), any(), any()))
                .thenReturn(List.of(existingLeave));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.createLeaveRequest(leaveRequestDto, 1L, 1L);
        });

        assertTrue(exception.getMessage().contains("already have"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testCreateLeaveRequest_InsufficientBalance() {
        // TC-LEAVE-002: Employee Creates Leave Request - Insufficient Balance
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(leaveRequestRepository.findOverlappingPendingOrApprovedLeaves(any(), any(), any()))
                .thenReturn(new ArrayList<>());
        
        java.util.Map<String, Object> balanceCheck = new java.util.HashMap<>();
        balanceCheck.put("sufficient", false);
        balanceCheck.put("remaining", 1);
        balanceCheck.put("category", "Paid Leave");
        when(leaveBalanceService.checkLeaveBalance(any(), any(), anyInt())).thenReturn(balanceCheck);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.createLeaveRequest(leaveRequestDto, 1L, 1L);
        });

        assertTrue(exception.getMessage().contains("Insufficient"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testCreateLeaveRequest_WeekendOnly() {
        // TC-LEAVE-004: Employee Creates Leave Request - Weekend Only
        leaveRequestDto.setStartDate(SATURDAY);
        leaveRequestDto.setEndDate(SUNDAY);

        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(leaveRequestRepository.findOverlappingPendingOrApprovedLeaves(any(), any(), any()))
                .thenReturn(new ArrayList<>());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.createLeaveRequest(leaveRequestDto, 1L, 1L);
        });

        assertTrue(exception.getMessage().contains("working day"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testCreateLeaveRequest_PastDate() {
        // TC-LEAVE-005: Employee Creates Leave Request - Past Date
        leaveRequestDto.setStartDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.createLeaveRequest(leaveRequestDto, 1L, 1L);
        });

        assertTrue(exception.getMessage().contains("past dates"));
        verify(leaveRequestRepository, never()).save(any());
    }

    // Test Cases: Leave Request Approval

    @Test
    void testApproveLeaveRequest_Success() {
        // TC-LEAVE-009: Manager Approves Leave Request (Assigned Employee)
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .leaveType(LeaveRequest.LeaveType.VACATION)
                .startDate(MONDAY)
                .endDate(WEDNESDAY)
                .numberOfDays(3)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .isPaid(true)
                .paidDays(3)
                .unpaidDays(0)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(leaveBalanceService).deductLeave(any(), any(), anyInt());
        doNothing().when(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(notificationService).notifyLeaveApproved(any(), any());

        LeaveRequestDto result = leaveRequestService.approveLeaveRequest(100L, 2L, "Approved");

        assertNotNull(result);
        assertEquals(LeaveRequest.LeaveStatus.APPROVED, result.getStatus());
        verify(leaveBalanceService).deductLeave(eq(1L), eq(LeaveRequest.LeaveType.VACATION), eq(3));
        verify(timesheetRepository, times(3)).save(any()); // Mon, Tue, Wed
        verify(notificationService).notifyLeaveApproved(any(), any());
    }

    @Test
    void testApproveLeaveRequest_UnauthorizedManager() {
        // TC-LEAVE-012: Manager Cannot Approve Other Manager's Employee
        User otherManager = new User();
        otherManager.setId(3L);
        otherManager.setRole(User.UserRole.MANAGER);

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherManager));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.approveLeaveRequest(100L, 3L, "Approved");
        });

        assertTrue(exception.getMessage().contains("Only") && exception.getMessage().contains("can approve"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testApproveLeaveRequest_SelfApproval() {
        // TC-LEAVE-013: Manager Cannot Approve Own Leave Request
        LeaveRequest managerLeaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(manager) // Manager's own leave
                .organization(organization)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(managerLeaveRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.approveLeaveRequest(100L, 2L, "Approved");
        });

        assertTrue(exception.getMessage().contains("cannot approve your own"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testApproveLeaveRequest_NoManagerAssigned() {
        // TC-LEAVE-014: Leave Request Approval - No Manager Assigned
        employee.setManager(null);

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.approveLeaveRequest(100L, 2L, "Approved");
        });

        assertTrue(exception.getMessage().contains("does not have an assigned manager"));
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void testApproveLeaveRequest_CreatesTimesheetEntries() {
        // TC-LEAVE-015: Approved Leave Auto-Fills Timesheet
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .leaveType(LeaveRequest.LeaveType.VACATION)
                .startDate(MONDAY)
                .endDate(FRIDAY)
                .numberOfDays(5)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .isPaid(true)
                .paidDays(5)
                .unpaidDays(0)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(timesheetRepository.findByUserIdAndDate(any(), any())).thenReturn(Optional.empty());

        doNothing().when(leaveBalanceService).deductLeave(any(), any(), anyInt());
        doNothing().when(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(notificationService).notifyLeaveApproved(any(), any());

        leaveRequestService.approveLeaveRequest(100L, 2L, "Approved");

        // Verify timesheet entries created for Mon-Fri (5 days)
        verify(timesheetRepository, times(5)).save(any());
    }

    @Test
    void testRejectLeaveRequest_Success() {
        // TC-LEAVE-010: Manager Rejects Leave Request (Assigned Employee)
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .status(LeaveRequest.LeaveStatus.PENDING)
                .build();

        when(leaveRequestRepository.findById(100L)).thenReturn(Optional.of(leaveRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(notificationService).notifyLeaveRejected(any(), any());

        LeaveRequestDto result = leaveRequestService.rejectLeaveRequest(100L, 2L, "Not approved");

        assertNotNull(result);
        assertEquals(LeaveRequest.LeaveStatus.REJECTED, result.getStatus());
        verify(leaveBalanceService, never()).deductLeave(any(), any(), anyInt());
        verify(timesheetRepository, never()).save(any()); // No timesheet entries for rejected leave
    }

    @Test
    void testRejectLeaveRequest_RequiresComments() {
        // TC-LEAVE-011: Manager Cannot Approve Without Comments (Rejection)
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            leaveRequestService.rejectLeaveRequest(100L, 2L, "");
        });

        assertTrue(exception.getMessage().contains("Rejection comments are required"));
    }
}
