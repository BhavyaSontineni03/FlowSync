package com.expensemanagement.service;

import com.expensemanagement.dto.PayrollDto;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Payroll;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.LeaveRequestRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.PayrollRepository;
import com.expensemanagement.repository.TimesheetRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for payroll processing.
 * Calculates payroll based on timesheets (days worked) and leave requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {
    
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TimesheetRepository timesheetRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ActivityLogService activityLogService;
    
    // Default salary (fallback if user doesn't have salary configured)
    private static final BigDecimal DEFAULT_MONTHLY_SALARY = new BigDecimal("50000.00");
    
    /**
     * Calculate and create payroll for a user for a specific month.
     */
    @Transactional
    public PayrollDto calculatePayroll(Long userId, Integer month, Integer year, Long organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        // Check if payroll already exists
        if (payrollRepository.findByUserIdAndPeriodMonthAndPeriodYear(userId, month, year).isPresent()) {
            throw new RuntimeException("Payroll already exists for this period");
        }
        
        // Calculate date range for the month
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        // Get total working days in month (excluding weekends)
        int totalWorkingDays = calculateWorkingDays(startDate, endDate);
        
        // Get days worked from approved timesheets (WORK entries only, not LEAVE)
        Integer daysWorked = timesheetRepository.countApprovedDaysInRange(userId, startDate, endDate);
        if (daysWorked == null) {
            daysWorked = 0;
        }
        
        // Get leave days from timesheet entries (more accurate than leave requests)
        // This counts the actual LEAVE timesheet entries which are created when leave is approved
        Integer paidLeaveDays = timesheetRepository.countPaidLeaveDaysInRange(userId, startDate, endDate);
        Integer unpaidLeaveDays = timesheetRepository.countUnpaidLeaveDaysInRange(userId, startDate, endDate);
        
        int paidLeavesUsed = paidLeaveDays != null ? paidLeaveDays : 0;
        int unpaidLeavesUsed = unpaidLeaveDays != null ? unpaidLeaveDays : 0;
        
        // Calculate salary - use user's configured salary or default
        BigDecimal baseSalary = user.getMonthlySalary() != null && user.getMonthlySalary().compareTo(BigDecimal.ZERO) > 0
                ? user.getMonthlySalary()
                : DEFAULT_MONTHLY_SALARY;
        BigDecimal dailyRate = baseSalary.divide(new BigDecimal(totalWorkingDays), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal deductions = dailyRate.multiply(new BigDecimal(unpaidLeavesUsed));
        BigDecimal netSalary = baseSalary.subtract(deductions);
        
        Payroll payroll = Payroll.builder()
                .user(user)
                .organization(organization)
                .periodMonth(month)
                .periodYear(year)
                .daysWorked(daysWorked)
                .totalDaysInMonth(totalWorkingDays)
                .paidLeavesUsed(paidLeavesUsed)
                .unpaidLeavesUsed(unpaidLeavesUsed)
                .baseSalary(baseSalary)
                .deductions(deductions)
                .netSalary(netSalary)
                .status(Payroll.PayrollStatus.DRAFT)
                .build();
        
        payroll = payrollRepository.save(payroll);
        
        return convertToDto(payroll);
    }
    
    /**
     * Process payroll (mark as processed).
     */
    @Transactional
    public PayrollDto processPayroll(Long payrollId, Long processorId, Long organizationId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));
        
        if (!payroll.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Payroll does not belong to this organization");
        }
        
        User processor = userRepository.findById(processorId)
                .orElseThrow(() -> new RuntimeException("Processor not found"));
        
        // Only Finance can process payroll (called from FinanceController)
        if (processor.getRole() != User.UserRole.FINANCE) {
            throw new RuntimeException("Only Finance team can process payroll");
        }
        
        payroll.setStatus(Payroll.PayrollStatus.PROCESSED);
        payroll.setProcessedBy(processor);
        payroll.setProcessedAt(java.time.LocalDateTime.now());
        payroll = payrollRepository.save(payroll);
        
        return convertToDto(payroll);
    }
    
    /**
     * Generate payroll for all employees in organization for a month.
     */
    @Transactional
    public List<PayrollDto> generatePayrollForOrganization(Integer month, Integer year, Long organizationId) {
        List<User> employees = userRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(u -> u.getRole() == User.UserRole.EMPLOYEE)
                .collect(Collectors.toList());
        
        return employees.stream()
                .map(employee -> {
                    try {
                        return calculatePayroll(employee.getId(), month, year, organizationId);
                    } catch (Exception e) {
                        log.error("Error calculating payroll for user {}: {}", employee.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }
    
    public Page<PayrollDto> getPayrolls(Long organizationId, Long userId, Pageable pageable) {
        if (userId != null) {
            return payrollRepository.findByUserId(userId, pageable)
                    .map(this::convertToDto);
        }
        return payrollRepository.findByOrganizationId(organizationId, pageable)
                .map(this::convertToDto);
    }
    
    public PayrollDto getPayrollById(Long payrollId, Long organizationId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new RuntimeException("Payroll not found"));
        
        if (!payroll.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Payroll does not belong to this organization");
        }
        
        return convertToDto(payroll);
    }
    
    private int calculateWorkingDays(LocalDate start, LocalDate end) {
        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            // Exclude weekends (Saturday = 6, Sunday = 7)
            int dayOfWeek = current.getDayOfWeek().getValue();
            if (dayOfWeek != 6 && dayOfWeek != 7) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }
    
    private PayrollDto convertToDto(Payroll payroll) {
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
}

