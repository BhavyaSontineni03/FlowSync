package com.expensemanagement.controller;

import com.expensemanagement.dto.PayrollDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.PayrollService;
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
 * Controller for payroll operations.
 * HR and Finance can process payroll.
 */
@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {
    
    private final PayrollService payrollService;
    private final JwtTokenProvider tokenProvider;
    
    // Removed calculate, generate-all, and process endpoints - moved to FinanceController
    // Payroll page is now read-only for viewing own payroll only
    
    @GetMapping
    public ResponseEntity<Page<PayrollDto>> getPayrolls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "periodYear") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        Long currentUserId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Privacy: Everyone (Employee, Manager, HR) can ONLY see their own payroll
        // Finance and Admin can see all payrolls (but only in Finance page, not here)
        // In Payroll page, everyone sees only their own
        userId = currentUserId; // Everyone always sees only their own payroll on this page
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PayrollDto> payrolls = payrollService.getPayrolls(organizationId, userId, pageable);
        return ResponseEntity.ok(payrolls);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PayrollDto> getPayrollById(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        PayrollDto payroll = payrollService.getPayrollById(id, organizationId);
        return ResponseEntity.ok(payroll);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
