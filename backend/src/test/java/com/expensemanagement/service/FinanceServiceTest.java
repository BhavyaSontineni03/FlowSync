package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Payroll;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.PayrollRepository;
import com.expensemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

/**
 * Unit tests for FinanceService
 * Tests: Mark Expense as Paid, Mark Payroll as Paid, Privacy, Authorization
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private PayrollService payrollService;

    @InjectMocks
    private FinanceService financeService;

    private User financeUser;
    private User employee;
    private Organization organization;
    private Expense expense;
    private Payroll payroll;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(1L);
        organization.setName("Test Org");

        financeUser = new User();
        financeUser.setId(10L);
        financeUser.setFirstName("Finance");
        financeUser.setLastName("User");
        financeUser.setRole(User.UserRole.FINANCE);
        financeUser.setOrganization(organization);

        employee = new User();
        employee.setId(1L);
        employee.setFirstName("Employee");
        employee.setLastName("One");
        employee.setRole(User.UserRole.EMPLOYEE);
        employee.setOrganization(organization);

        expense = Expense.builder()
                .id(100L)
                .user(employee)
                .organization(organization)
                .description("Test Expense")
                .amount(new BigDecimal("100.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.TRAVEL)
                .status(Expense.ExpenseStatus.APPROVED)
                .build();

        payroll = Payroll.builder()
                .id(200L)
                .user(employee)
                .organization(organization)
                .periodMonth(1)
                .periodYear(2026)
                .status(Payroll.PayrollStatus.PROCESSED)
                .netSalary(new BigDecimal("5000.00"))
                .build();
    }

    @Test
    void testMarkExpenseAsPaid_Success() {
        // TC-FIN-004: Finance Marks Single Expense as Paid
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(userRepository.findById(10L)).thenReturn(Optional.of(financeUser));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(notificationService).notifyExpensePaid(any(), any());

        ExpenseDto result = financeService.markExpenseAsPaid(100L, 10L, 1L);

        assertNotNull(result);
        assertEquals(Expense.ExpenseStatus.PAID, expense.getStatus());
        verify(expenseRepository).save(any(Expense.class));
        verify(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        verify(notificationService).notifyExpensePaid(any(), any());
    }

    @Test
    void testMarkExpenseAsPaid_NonFinanceUser() {
        // TC-FIN-018: Manager Cannot Mark Expense as Paid
        User manager = new User();
        manager.setId(2L);
        manager.setRole(User.UserRole.MANAGER);

        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            financeService.markExpenseAsPaid(100L, 2L, 1L);
        });

        assertTrue(exception.getMessage().contains("Only Finance team"));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void testMarkPayrollAsPaid_Success() {
        // TC-FIN-008: Finance Marks Payroll as Paid
        when(payrollRepository.findById(200L)).thenReturn(Optional.of(payroll));
        when(userRepository.findById(10L)).thenReturn(Optional.of(financeUser));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(notificationService).notifyPayrollPaid(any(), any());
        doNothing().when(webSocketService).sendPayrollUpdate(any(), any());

        var result = financeService.markPayrollAsPaid(200L, 10L, 1L);

        assertNotNull(result);
        assertEquals(Payroll.PayrollStatus.PAID, payroll.getStatus());
        verify(payrollRepository).save(any(Payroll.class));
        verify(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any());
        verify(notificationService).notifyPayrollPaid(any(), any());
        verify(webSocketService).sendPayrollUpdate(any(), any());
    }

    @Test
    void testMarkPayrollAsPaid_NonFinanceUser() {
        // TC-PAY-010: Manager Cannot Process Payroll
        User manager = new User();
        manager.setId(2L);
        manager.setRole(User.UserRole.MANAGER);

        when(payrollRepository.findById(200L)).thenReturn(Optional.of(payroll));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            financeService.markPayrollAsPaid(200L, 2L, 1L);
        });

        assertTrue(exception.getMessage().contains("Only Finance team"));
        verify(payrollRepository, never()).save(any());
    }

    @Test
    void testMarkPayrollAsPaid_NotProcessed() {
        // TC-PAY-009: Finance Cannot Mark Non-Processed Payroll as Paid
        payroll.setStatus(Payroll.PayrollStatus.DRAFT);

        // The payroll status is checked before the finance user is looked
        // up (see FinanceService.markPayrollAsPaid), so no user stub is
        // needed here.
        when(payrollRepository.findById(200L)).thenReturn(Optional.of(payroll));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            financeService.markPayrollAsPaid(200L, 10L, 1L);
        });

        assertTrue(exception.getMessage().contains("Only processed payrolls"));
        verify(payrollRepository, never()).save(any());
    }
}
