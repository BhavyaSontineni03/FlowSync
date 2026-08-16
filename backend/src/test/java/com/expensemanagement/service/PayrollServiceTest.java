package com.expensemanagement.service;

import com.expensemanagement.dto.PayrollDto;
import com.expensemanagement.model.*;
import com.expensemanagement.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TimesheetRepository timesheetRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private ActivityLogRepository activityLogRepository;

    private ActivityLogService activityLogService;
    private PayrollService payrollService;

    private Organization testOrg;
    private User testUser;
    private Payroll testPayroll;

    @BeforeEach
    void setUp() {
        activityLogService = new ActivityLogService(activityLogRepository);
        payrollService = new PayrollService(
                payrollRepository,
                userRepository,
                organizationRepository,
                timesheetRepository,
                leaveRequestRepository,
                activityLogService
        );

        testOrg = Organization.builder().id(1L).name("Test Org").subdomain("test").build();

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrg)
                .monthlySalary(new BigDecimal("50000.00"))
                .build();

        testPayroll = Payroll.builder()
                .id(1L)
                .user(testUser)
                .organization(testOrg)
                .periodMonth(11)
                .periodYear(2024)
                .daysWorked(22)
                .totalDaysInMonth(22)
                .paidLeavesUsed(2)
                .unpaidLeavesUsed(0)
                .baseSalary(new BigDecimal("50000.00"))
                .deductions(BigDecimal.ZERO)
                .netSalary(new BigDecimal("50000.00"))
                .status(Payroll.PayrollStatus.DRAFT)
                .build();
    }

    @Test
    void testCalculatePayroll_Success() {
        LocalDate startDate = LocalDate.of(2024, 11, 1);
        LocalDate endDate = LocalDate.of(2024, 11, 30);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(payrollRepository.findByUserIdAndPeriodMonthAndPeriodYear(1L, 11, 2024))
                .thenReturn(Optional.empty());
        // Leave days come from approved LEAVE timesheet entries, not raw
        // LeaveRequest rows -- see PayrollService.calculatePayroll, which
        // treats the timesheet as the source of truth since that's what
        // actually reflects days taken, not just days requested.
        when(timesheetRepository.countApprovedDaysInRange(1L, startDate, endDate)).thenReturn(20);
        when(timesheetRepository.countPaidLeaveDaysInRange(1L, startDate, endDate)).thenReturn(0);
        when(timesheetRepository.countUnpaidLeaveDaysInRange(1L, startDate, endDate)).thenReturn(0);
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollDto result = payrollService.calculatePayroll(1L, 11, 2024, 1L);

        assertNotNull(result);
        assertEquals(11, result.getPeriodMonth());
        assertEquals(2024, result.getPeriodYear());
        assertNotNull(result.getBaseSalary());
        verify(payrollRepository, times(1)).save(any(Payroll.class));
    }

    @Test
    void testCalculatePayroll_WithLeaves() {
        LocalDate startDate = LocalDate.of(2024, 11, 1);
        LocalDate endDate = LocalDate.of(2024, 11, 30);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrg));
        when(payrollRepository.findByUserIdAndPeriodMonthAndPeriodYear(1L, 11, 2024))
                .thenReturn(Optional.empty());
        when(timesheetRepository.countApprovedDaysInRange(1L, startDate, endDate)).thenReturn(20);
        when(timesheetRepository.countPaidLeaveDaysInRange(1L, startDate, endDate)).thenReturn(2);
        when(timesheetRepository.countUnpaidLeaveDaysInRange(1L, startDate, endDate)).thenReturn(1);
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollDto result = payrollService.calculatePayroll(1L, 11, 2024, 1L);

        assertNotNull(result);
        assertEquals(2, result.getPaidLeavesUsed());
        assertEquals(1, result.getUnpaidLeavesUsed());
        assertTrue(result.getDeductions().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testProcessPayroll_Success() {
        // Only Finance processes payroll (see
        // PayrollService.processPayroll, called from FinanceController) --
        // HR manages leave and staffing but isn't the payment authority.
        User finance = User.builder().id(2L).role(User.UserRole.FINANCE).build();

        when(payrollRepository.findById(1L)).thenReturn(Optional.of(testPayroll));
        when(userRepository.findById(2L)).thenReturn(Optional.of(finance));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollDto result = payrollService.processPayroll(1L, 2L, 1L);

        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
        verify(payrollRepository, times(1)).save(any(Payroll.class));
    }

    @Test
    void testProcessPayroll_UnauthorizedRole() {
        User employee = User.builder().id(2L).role(User.UserRole.EMPLOYEE).build();

        when(payrollRepository.findById(1L)).thenReturn(Optional.of(testPayroll));
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThrows(RuntimeException.class, () -> {
            payrollService.processPayroll(1L, 2L, 1L);
        });
    }
}
