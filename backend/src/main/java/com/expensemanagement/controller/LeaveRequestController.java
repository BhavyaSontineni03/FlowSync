package com.expensemanagement.controller;

import com.expensemanagement.dto.LeaveRequestDto;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.LeaveRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for leave request operations.
 * Handles leave request CRUD, approval, and balance queries.
 */
@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final JwtTokenProvider tokenProvider;

    /**
     * Create a new leave request.
     */
    @PostMapping
    public ResponseEntity<LeaveRequestDto> createLeaveRequest(
            @Valid @RequestBody LeaveRequestDto leaveRequestDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        LeaveRequestDto created = leaveRequestService.createLeaveRequest(leaveRequestDto, userId, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get leave requests with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<LeaveRequestDto>> getLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        Long currentUserId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Privacy: Everyone sees only their own leave requests in the Leave Requests page
        // Managers approve team leave requests via the Approvals page (separate endpoint)
        // Admins can see all leave requests
        Long managerId = null;
        if ("ADMIN".equals(userRole)) {
            // Admins can see all leave requests or filter by userId if specified
            // userId remains as passed (null = all, specific = filtered)
        } else {
            // EMPLOYEE, MANAGER, HR, FINANCE all see only their own leave requests
            userId = currentUserId;
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<LeaveRequestDto> leaveRequests = leaveRequestService.getLeaveRequests(organizationId, userId, managerId, pageable);
        return ResponseEntity.ok(leaveRequests);
    }

    /**
     * Get pending leave requests for approval.
     * Only returns requests from employees where the current user is their assigned manager.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<LeaveRequestDto>> getPendingLeaveRequests(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long managerId = tokenProvider.getUserIdFromToken(token);
        
        // Get pending requests only for employees where the current user is their manager
        List<LeaveRequestDto> pending = leaveRequestService.getPendingLeaveRequestsForManager(managerId);
        return ResponseEntity.ok(pending);
    }

    /**
     * Approve a leave request.
     * Only the employee's assigned manager can approve.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveLeaveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String comments = body != null ? body.get("comments") : null;
        
        try {
            LeaveRequestDto approved = leaveRequestService.approveLeaveRequest(id, approverId, comments);
            return ResponseEntity.ok(approved);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            // Return 403 for authorization errors, 400 for other errors
            if (e.getMessage().contains("Only") || e.getMessage().contains("cannot approve")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Reject a leave request.
     * Only the employee's assigned manager can reject.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeaveRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String comments = body != null ? body.get("comments") : null;
        
        if (comments == null || comments.trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Rejection reason is required.");
            return ResponseEntity.badRequest().body(error);
        }
        
        try {
            LeaveRequestDto rejected = leaveRequestService.rejectLeaveRequest(id, approverId, comments);
            return ResponseEntity.ok(rejected);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            if (e.getMessage().contains("Only") || e.getMessage().contains("cannot reject")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get leave balance for a user (specific type).
     */
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getLeaveBalance(
            @RequestParam(required = false) LeaveRequest.LeaveType leaveType,
            @RequestParam(required = false) Integer year,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        if (year == null) {
            year = java.time.LocalDate.now().getYear();
        }
        
        Map<String, Object> balance = leaveRequestService.getLeaveBalance(userId, leaveType, year);
        return ResponseEntity.ok(balance);
    }

    /**
     * Get complete leave balance summary for a user.
     * Returns all leave types with allocated/used/remaining counts.
     */
    @GetMapping("/balance/summary")
    public ResponseEntity<Map<String, Object>> getLeaveBalanceSummary(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        Map<String, Object> summary = leaveRequestService.getLeaveBalanceSummary(userId);
        return ResponseEntity.ok(summary);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

}

