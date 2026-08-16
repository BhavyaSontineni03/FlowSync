package com.expensemanagement.controller;

import com.expensemanagement.dto.ApprovalDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Timesheet;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.LeaveRequestRepository;
import com.expensemanagement.repository.TimesheetRepository;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.ApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {
    
    private final ApprovalService approvalService;
    private final JwtTokenProvider tokenProvider;
    private final com.expensemanagement.service.ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimesheetRepository timesheetRepository;
    
    
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalDto>> getPendingApprovals(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER can access approvals
        if (!"MANAGER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long approverId = tokenProvider.getUserIdFromToken(token);
        List<ApprovalDto> approvals = approvalService.getPendingApprovals(approverId);
        return ResponseEntity.ok(approvals);
    }
    
    @PostMapping("/{expenseId}/approve")
    public ResponseEntity<ApprovalDto> approveExpense(
            @PathVariable Long expenseId,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER can approve
        if (!"MANAGER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String comments = body != null ? body.get("comments") : null;
        
        ApprovalDto approval = approvalService.approveExpense(expenseId, approverId, comments);
        return ResponseEntity.ok(approval);
    }
    
    @PostMapping("/{expenseId}/reject")
    public ResponseEntity<ApprovalDto> rejectExpense(
            @PathVariable Long expenseId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER can reject
        if (!"MANAGER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String comments = body.get("comments");
        
        ApprovalDto approval = approvalService.rejectExpense(expenseId, approverId, comments);
        return ResponseEntity.ok(approval);
    }
    
    @GetMapping("/expense/{expenseId}")
    public ResponseEntity<List<ApprovalDto>> getExpenseApprovals(
            @PathVariable Long expenseId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        // MANAGER can view approval history
        // Also allow employees to see approvals for their own expenses
        if (!"MANAGER".equals(userRole)) {
            // Check if this is the employee's own expense
            Long userId = tokenProvider.getUserIdFromToken(token);
            
            // Verify expense belongs to user
            try {
                com.expensemanagement.dto.ExpenseDto expense = expenseService.getExpenseById(expenseId, organizationId);
                
                if (!expense.getUserId().equals(userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        
        List<ApprovalDto> approvals = approvalService.getExpenseApprovals(expenseId);
        return ResponseEntity.ok(approvals);
    }
    
    /**
     * Get approval stats for manager dashboard
     * Returns counts and totals for expenses, leave requests, and timesheets from team members
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getManagerStats(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER can access stats
        if (!"MANAGER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long managerId = tokenProvider.getUserIdFromToken(token);
        
        Map<String, Object> stats = new HashMap<>();
        
        // Expense stats
        Map<String, Object> expenseStats = new HashMap<>();
        long expensePending = expenseRepository.countByUserManagerIdAndStatus(managerId, Expense.ExpenseStatus.SUBMITTED);
        long expenseApproved = expenseRepository.countByUserManagerIdAndStatus(managerId, Expense.ExpenseStatus.APPROVED);
        long expenseRejected = expenseRepository.countByUserManagerIdAndStatus(managerId, Expense.ExpenseStatus.REJECTED);
        BigDecimal expenseApprovedAmount = expenseRepository.sumAmountByUserManagerIdAndStatus(managerId, Expense.ExpenseStatus.APPROVED);
        BigDecimal expensePendingAmount = expenseRepository.sumAmountByUserManagerIdAndStatus(managerId, Expense.ExpenseStatus.SUBMITTED);
        
        expenseStats.put("pending", expensePending);
        expenseStats.put("approved", expenseApproved);
        expenseStats.put("rejected", expenseRejected);
        expenseStats.put("approvedAmount", expenseApprovedAmount != null ? expenseApprovedAmount : BigDecimal.ZERO);
        expenseStats.put("pendingAmount", expensePendingAmount != null ? expensePendingAmount : BigDecimal.ZERO);
        stats.put("expenses", expenseStats);
        
        // Leave request stats
        Map<String, Object> leaveStats = new HashMap<>();
        long leavePending = leaveRequestRepository.countByUserManagerIdAndStatus(managerId, LeaveRequest.LeaveStatus.PENDING);
        long leaveApproved = leaveRequestRepository.countByUserManagerIdAndStatus(managerId, LeaveRequest.LeaveStatus.APPROVED);
        long leaveRejected = leaveRequestRepository.countByUserManagerIdAndStatus(managerId, LeaveRequest.LeaveStatus.REJECTED);
        Integer leaveApprovedDays = leaveRequestRepository.sumDaysByUserManagerIdAndStatus(managerId, LeaveRequest.LeaveStatus.APPROVED);
        Integer leavePendingDays = leaveRequestRepository.sumDaysByUserManagerIdAndStatus(managerId, LeaveRequest.LeaveStatus.PENDING);
        
        leaveStats.put("pending", leavePending);
        leaveStats.put("approved", leaveApproved);
        leaveStats.put("rejected", leaveRejected);
        leaveStats.put("approvedDays", leaveApprovedDays != null ? leaveApprovedDays : 0);
        leaveStats.put("pendingDays", leavePendingDays != null ? leavePendingDays : 0);
        stats.put("leaveRequests", leaveStats);
        
        // Timesheet stats
        Map<String, Object> timesheetStats = new HashMap<>();
        long timesheetPending = timesheetRepository.countByUserManagerIdAndStatus(managerId, Timesheet.TimesheetStatus.SUBMITTED);
        long timesheetApproved = timesheetRepository.countByUserManagerIdAndStatus(managerId, Timesheet.TimesheetStatus.APPROVED);
        long timesheetRejected = timesheetRepository.countByUserManagerIdAndStatus(managerId, Timesheet.TimesheetStatus.REJECTED);
        Double timesheetApprovedHours = timesheetRepository.sumHoursByUserManagerIdAndStatus(managerId, Timesheet.TimesheetStatus.APPROVED);
        Double timesheetPendingHours = timesheetRepository.sumHoursByUserManagerIdAndStatus(managerId, Timesheet.TimesheetStatus.SUBMITTED);
        
        timesheetStats.put("pending", timesheetPending);
        timesheetStats.put("approved", timesheetApproved);
        timesheetStats.put("rejected", timesheetRejected);
        timesheetStats.put("approvedHours", timesheetApprovedHours != null ? timesheetApprovedHours : 0.0);
        timesheetStats.put("pendingHours", timesheetPendingHours != null ? timesheetPendingHours : 0.0);
        stats.put("timesheets", timesheetStats);
        
        return ResponseEntity.ok(stats);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

