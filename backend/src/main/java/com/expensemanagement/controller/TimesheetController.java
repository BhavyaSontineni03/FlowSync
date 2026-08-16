package com.expensemanagement.controller;

import com.expensemanagement.dto.TimesheetDto;
import com.expensemanagement.model.User;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.TimesheetService;
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

import java.util.List;
import java.util.Map;

/**
 * Controller for timesheet operations.
 */
@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
public class TimesheetController {
    
    private final TimesheetService timesheetService;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping
    public ResponseEntity<TimesheetDto> createTimesheet(
            @Valid @RequestBody TimesheetDto timesheetDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        TimesheetDto created = timesheetService.createTimesheet(timesheetDto, userId, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTimesheet(
            @PathVariable Long id,
            @Valid @RequestBody TimesheetDto timesheetDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        try {
            TimesheetDto updated = timesheetService.updateTimesheet(id, timesheetDto, userId, organizationId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            java.util.Map<String, String> error = new java.util.HashMap<>();
            error.put("message", e.getMessage());
            if (e.getMessage().contains("not authorized") || e.getMessage().contains("cannot modify")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @PostMapping("/{id}/submit")
    public ResponseEntity<TimesheetDto> submitTimesheet(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        TimesheetDto submitted = timesheetService.submitTimesheet(id, userId, organizationId);
        return ResponseEntity.ok(submitted);
    }
    
    @PostMapping("/{id}/approve")
    public ResponseEntity<TimesheetDto> approveTimesheet(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        
        // Only Manager, Admin, or user's manager can approve
        if (!"MANAGER".equals(userRole) && !"ADMIN".equals(userRole)) {
            // Check if approver is the user's manager (handled in service)
        }
        
        String comments = body != null ? body.get("comments") : null;
        TimesheetDto approved = timesheetService.approveTimesheet(id, approverId, comments);
        return ResponseEntity.ok(approved);
    }
    
    @PostMapping("/{id}/reject")
    public ResponseEntity<TimesheetDto> rejectTimesheet(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String comments = body.get("comments");
        
        if (comments == null || comments.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        TimesheetDto rejected = timesheetService.rejectTimesheet(id, approverId, comments);
        return ResponseEntity.ok(rejected);
    }
    
    @GetMapping
    public ResponseEntity<Page<TimesheetDto>> getTimesheets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        Long currentUserId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Privacy: Everyone sees only their own timesheets in the Timesheets page
        // Managers approve team timesheets via the Approvals page (separate endpoint)
        // Admins can see all timesheets
        Long managerId = null;
        if ("ADMIN".equals(userRole)) {
            // Admins can see all timesheets or filter by userId if specified
            // userId remains as passed (null = all, specific = filtered)
        } else {
            // EMPLOYEE, MANAGER, HR, FINANCE all see only their own timesheets
            userId = currentUserId;
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<TimesheetDto> timesheets = timesheetService.getTimesheets(organizationId, userId, managerId, pageable);
        return ResponseEntity.ok(timesheets);
    }
    
    /**
     * Get pending timesheets for approval.
     * Only returns timesheets from employees where the current user is their assigned manager.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<TimesheetDto>> getPendingTimesheets(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long managerId = tokenProvider.getUserIdFromToken(token);
        
        // Get pending timesheets only for employees where the current user is their manager
        List<TimesheetDto> pending = timesheetService.getPendingTimesheetsForManager(managerId);
        return ResponseEntity.ok(pending);
    }

    /**
     * Get weekly timesheet entries for the current user.
     * Returns Mon-Fri entries for the specified week.
     * Includes WORK entries and auto-generated LEAVE entries.
     */
    @GetMapping("/weekly")
    public ResponseEntity<List<TimesheetDto>> getWeeklyTimesheets(
            @RequestParam(required = false) String weekStart,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        java.time.LocalDate weekStartDate;
        if (weekStart != null && !weekStart.isEmpty()) {
            weekStartDate = java.time.LocalDate.parse(weekStart);
        } else {
            // Default to current week
            weekStartDate = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        }
        
        List<TimesheetDto> entries = timesheetService.getWeeklyTimesheets(userId, weekStartDate);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get weekly timesheet summary with status for each day.
     * Shows which days have entries, leaves, or are empty.
     */
    @GetMapping("/weekly/summary")
    public ResponseEntity<Map<String, Object>> getWeeklySummary(
            @RequestParam(required = false) String weekStart,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        java.time.LocalDate weekStartDate;
        if (weekStart != null && !weekStart.isEmpty()) {
            weekStartDate = java.time.LocalDate.parse(weekStart);
        } else {
            weekStartDate = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        }
        
        Map<String, Object> summary = timesheetService.getWeeklySummary(userId, weekStartDate);
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
