package com.expensemanagement.controller;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.dto.PayrollDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.FinanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for Finance team operations.
 * Handles payment processing and financial workflows.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {
    
    private final FinanceService financeService;
    private final JwtTokenProvider tokenProvider;
    
    /**
     * Mark an expense as paid.
     */
    @PostMapping("/expenses/{expenseId}/mark-paid")
    public ResponseEntity<ExpenseDto> markExpenseAsPaid(
            @PathVariable Long expenseId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long financeUserId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can mark expenses as paid
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ExpenseDto paidExpense = financeService.markExpenseAsPaid(expenseId, financeUserId, organizationId);
        return ResponseEntity.ok(paidExpense);
    }
    
    /**
     * Bulk mark expenses as paid.
     */
    @PostMapping("/expenses/bulk-mark-paid")
    public ResponseEntity<List<ExpenseDto>> bulkMarkAsPaid(
            @RequestBody Map<String, List<Long>> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long financeUserId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can bulk mark expenses as paid
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<Long> expenseIds = body.get("expenseIds");
        List<ExpenseDto> paidExpenses = financeService.bulkMarkAsPaid(expenseIds, financeUserId, organizationId);
        return ResponseEntity.ok(paidExpenses);
    }
    
    /**
     * Get approved expenses ready for payment.
     */
    @GetMapping("/expenses/approved")
    public ResponseEntity<Page<ExpenseDto>> getApprovedExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        if (!"FINANCE".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ExpenseDto> expenses = financeService.getApprovedExpensesForPayment(organizationId, pageable);
        return ResponseEntity.ok(expenses);
    }
    
    /**
     * Get paid expenses.
     */
    @GetMapping("/expenses/paid")
    public ResponseEntity<Page<ExpenseDto>> getPaidExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        if (!"FINANCE".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ExpenseDto> expenses = financeService.getPaidExpenses(organizationId, pageable);
        return ResponseEntity.ok(expenses);
    }
    
    /**
     * Mark a payroll as paid.
     */
    @PostMapping("/payroll/{payrollId}/mark-paid")
    public ResponseEntity<PayrollDto> markPayrollAsPaid(
            @PathVariable Long payrollId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long financeUserId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can mark payrolls as paid
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        PayrollDto paidPayroll = financeService.markPayrollAsPaid(payrollId, financeUserId, organizationId);
        return ResponseEntity.ok(paidPayroll);
    }
    
    /**
     * Get processed payrolls ready for payment.
     */
    @GetMapping("/payroll/processed")
    public ResponseEntity<Page<PayrollDto>> getProcessedPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "periodYear") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PayrollDto> payrolls = financeService.getProcessedPayrolls(organizationId, pageable);
        return ResponseEntity.ok(payrolls);
    }
    
    /**
     * Get paid payrolls.
     */
    @GetMapping("/payroll/paid")
    public ResponseEntity<Page<PayrollDto>> getPaidPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "periodYear") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PayrollDto> payrolls = financeService.getPaidPayrolls(organizationId, pageable);
        return ResponseEntity.ok(payrolls);
    }
    
    /**
     * Generate payroll for all employees for a month.
     * Only Finance can generate payroll.
     */
    @PostMapping("/payroll/generate")
    public ResponseEntity<List<PayrollDto>> generatePayroll(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can generate payroll
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Integer month = Integer.valueOf(body.get("month").toString());
        Integer year = Integer.valueOf(body.get("year").toString());
        
        List<PayrollDto> payrolls = financeService.generatePayrollForOrganization(month, year, organizationId);
        return ResponseEntity.ok(payrolls);
    }
    
    /**
     * Process payroll (mark as processed).
     * Only Finance can process payroll.
     */
    @PostMapping("/payroll/{payrollId}/process")
    public ResponseEntity<PayrollDto> processPayroll(
            @PathVariable Long payrollId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long financeUserId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can process payroll
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        PayrollDto processed = financeService.processPayroll(payrollId, financeUserId, organizationId);
        return ResponseEntity.ok(processed);
    }
    
    /**
     * Get all payrolls (for Finance only).
     */
    @GetMapping("/payroll/all")
    public ResponseEntity<Page<PayrollDto>> getAllPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "periodYear") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only Finance can see all payrolls
        if (!"FINANCE".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PayrollDto> payrolls = financeService.getAllPayrolls(organizationId, userId, pageable);
        return ResponseEntity.ok(payrolls);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
