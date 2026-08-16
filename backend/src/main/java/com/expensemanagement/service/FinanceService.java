package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.dto.PayrollDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Payroll;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.PayrollRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Finance team operations.
 * Handles payment processing and financial workflows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {
    
    private final ExpenseRepository expenseRepository;
    private final PayrollRepository payrollRepository;
    private final PayrollService payrollService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    
    /**
     * Mark an expense as paid.
     * Only Finance team can perform this action.
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public ExpenseDto markExpenseAsPaid(Long expenseId, Long financeUserId, Long organizationId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        if (!expense.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        
        if (expense.getStatus() != Expense.ExpenseStatus.APPROVED) {
            throw new RuntimeException("Only approved expenses can be marked as paid");
        }
        
        User financeUser = userRepository.findById(financeUserId)
                .orElseThrow(() -> new RuntimeException("Finance user not found"));
        
        if (financeUser.getRole() != User.UserRole.FINANCE) {
            throw new RuntimeException("Only Finance team can mark expenses as paid");
        }
        
        expense.setStatus(Expense.ExpenseStatus.PAID);
        expense = expenseRepository.save(expense);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_PAID,
                "Expense marked as paid: " + expense.getDescription() + " - $" + expense.getAmount(),
                financeUser,
                expense.getOrganization(),
                "Expense",
                expense.getId(),
                null
        );
        
        notificationService.notifyExpensePaid(expense, financeUser);
        
        ExpenseDto paidDto = convertToDto(expense);
        webSocketService.sendExpenseUpdate(organizationId, paidDto);
        
        return paidDto;
    }
    
    /**
     * Bulk mark expenses as paid.
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public List<ExpenseDto> bulkMarkAsPaid(List<Long> expenseIds, Long financeUserId, Long organizationId) {
        return expenseIds.stream()
                .map(id -> {
                    try {
                        return markExpenseAsPaid(id, financeUserId, organizationId);
                    } catch (Exception e) {
                        log.error("Error marking expense {} as paid: {}", id, e.getMessage());
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all approved expenses ready for payment.
     */
    public Page<ExpenseDto> getApprovedExpensesForPayment(Long organizationId, Pageable pageable) {
        return expenseRepository.findByOrganizationIdAndStatus(
                organizationId, 
                Expense.ExpenseStatus.APPROVED, 
                pageable
        ).map(this::convertToDto);
    }
    
    /**
     * Get all paid expenses.
     */
    public Page<ExpenseDto> getPaidExpenses(Long organizationId, Pageable pageable) {
        return expenseRepository.findByOrganizationIdAndStatus(
                organizationId, 
                Expense.ExpenseStatus.PAID, 
                pageable
        ).map(this::convertToDto);
    }
    
    /**
     * Mark a payroll as paid.
     * Only Finance team can perform this action.
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public PayrollDto markPayrollAsPaid(Long payrollId, Long financeUserId, Long organizationId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));
        
        if (!payroll.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Payroll does not belong to this organization");
        }
        
        if (payroll.getStatus() != Payroll.PayrollStatus.PROCESSED) {
            throw new RuntimeException("Only processed payrolls can be marked as paid");
        }
        
        User financeUser = userRepository.findById(financeUserId)
                .orElseThrow(() -> new RuntimeException("Finance user not found"));
        
        if (financeUser.getRole() != User.UserRole.FINANCE) {
            throw new RuntimeException("Only Finance team can mark payrolls as paid");
        }
        
        payroll.setStatus(Payroll.PayrollStatus.PAID);
        payroll = payrollRepository.save(payroll);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.PAYROLL_PROCESSED,
                "Payroll marked as paid for " + payroll.getUser().getFirstName() + " " + payroll.getUser().getLastName() + 
                        " - Period: " + payroll.getPeriodMonth() + "/" + payroll.getPeriodYear(),
                financeUser,
                payroll.getOrganization(),
                "Payroll",
                payroll.getId(),
                null
        );
        
        // Notify the employee that their payroll has been paid
        notificationService.notifyPayrollPaid(payroll, financeUser);
        
        PayrollDto payrollDto = convertPayrollToDto(payroll);
        webSocketService.sendPayrollUpdate(payroll.getOrganization().getId(), payrollDto);
        
        return payrollDto;
    }
    
    /**
     * Get all processed payrolls ready for payment.
     */
    public Page<PayrollDto> getProcessedPayrolls(Long organizationId, Pageable pageable) {
        return payrollRepository.findByOrganizationIdAndStatus(
                organizationId, 
                Payroll.PayrollStatus.PROCESSED, 
                pageable
        ).map(this::convertPayrollToDto);
    }
    
    /**
     * Get all paid payrolls.
     */
    public Page<PayrollDto> getPaidPayrolls(Long organizationId, Pageable pageable) {
        return payrollRepository.findByOrganizationIdAndStatus(
                organizationId, 
                Payroll.PayrollStatus.PAID, 
                pageable
        ).map(this::convertPayrollToDto);
    }
    
    /**
     * Generate payroll for all employees for a month.
     * Only Finance team can perform this action.
     */
    public List<PayrollDto> generatePayrollForOrganization(Integer month, Integer year, Long organizationId) {
        return payrollService.generatePayrollForOrganization(month, year, organizationId);
    }
    
    /**
     * Process payroll (mark as processed).
     * Only Finance team can perform this action.
     */
    @Transactional
    public PayrollDto processPayroll(Long payrollId, Long financeUserId, Long organizationId) {
        User financeUser = userRepository.findById(financeUserId)
                .orElseThrow(() -> new RuntimeException("Finance user not found"));
        
        if (financeUser.getRole() != User.UserRole.FINANCE) {
            throw new RuntimeException("Only Finance team can process payroll");
        }
        
        return payrollService.processPayroll(payrollId, financeUserId, organizationId);
    }
    
    /**
     * Get all payrolls (for Finance only).
     */
    public Page<PayrollDto> getAllPayrolls(Long organizationId, Long userId, Pageable pageable) {
        return payrollService.getPayrolls(organizationId, userId, pageable);
    }
    
    private PayrollDto convertPayrollToDto(Payroll payroll) {
        return PayrollDto.builder()
                .id(payroll.getId())
                .userId(payroll.getUser().getId())
                .userName(payroll.getUser().getFirstName() + " " + payroll.getUser().getLastName())
                .userEmail(payroll.getUser().getEmail())
                .organizationId(payroll.getOrganization().getId())
                .periodMonth(payroll.getPeriodMonth())
                .periodYear(payroll.getPeriodYear())
                .daysWorked(payroll.getDaysWorked())
                .totalDaysInMonth(payroll.getTotalDaysInMonth())
                .paidLeavesUsed(payroll.getPaidLeavesUsed())
                .unpaidLeavesUsed(payroll.getUnpaidLeavesUsed())
                .baseSalary(payroll.getBaseSalary())
                .deductions(payroll.getDeductions())
                .netSalary(payroll.getNetSalary())
                .status(payroll.getStatus().name())
                .processedBy(payroll.getProcessedBy() != null ? payroll.getProcessedBy().getId() : null)
                .processedByName(payroll.getProcessedBy() != null ? 
                        payroll.getProcessedBy().getFirstName() + " " + payroll.getProcessedBy().getLastName() : null)
                .processedAt(payroll.getProcessedAt())
                .notes(payroll.getNotes())
                .createdAt(payroll.getCreatedAt())
                .updatedAt(payroll.getUpdatedAt())
                .build();
    }
    
    private ExpenseDto convertToDto(Expense expense) {
        return ExpenseDto.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .status(expense.getStatus())
                .notes(expense.getNotes())
                .receiptPath(expense.getReceiptPath())
                .receiptUrl(expense.getReceiptUrl())
                .userId(expense.getUser().getId())
                .userName(expense.getUser().getFirstName() + " " + expense.getUser().getLastName())
                .organizationId(expense.getOrganization().getId())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
