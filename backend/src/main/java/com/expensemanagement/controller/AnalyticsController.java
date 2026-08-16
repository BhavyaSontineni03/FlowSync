package com.expensemanagement.controller;

import com.expensemanagement.dto.AnalyticsDto;
import com.expensemanagement.dto.LeaveAnalyticsDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.AnalyticsService;
import com.expensemanagement.service.LeaveAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {
    
    private final AnalyticsService analyticsService;
    private final LeaveAnalyticsService leaveAnalyticsService;
    private final JwtTokenProvider tokenProvider;
    
    @GetMapping
    public ResponseEntity<?> getAnalytics(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        
        try {
            String token = getTokenFromRequest(request);
            if (token == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createErrorResponse("Authentication token is missing."));
            }

            String userRole = tokenProvider.getRoleFromToken(token);
            if (userRole == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(createErrorResponse("Invalid token: user role not found."));
            }
            
            Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
            if (organizationId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(createErrorResponse("Organization ID not found in token."));
            }

            // Route to appropriate analytics service based on module with role-based access
            if ("leave-requests".equals(module) || "leave".equals(module)) {
                // Leave analytics: Only Manager can access (for approval-related analytics)
                if (!"MANAGER".equals(userRole)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createErrorResponse("Access denied. Only MANAGER role can access leave analytics."));
                }
                LeaveAnalyticsDto analytics = leaveAnalyticsService.getLeaveAnalytics(organizationId, startDate, endDate);
                return ResponseEntity.ok(analytics);
            } else {
                // Expense analytics: Manager (for approvals) and Finance (for finance activity) can access
                if (!isExpenseAnalyticsRole(userRole)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createErrorResponse("Access denied. Only MANAGER and FINANCE roles can access expense analytics."));
                }
                AnalyticsDto analytics = analyticsService.getAnalytics(organizationId, startDate, endDate, userRole);
                return ResponseEntity.ok(analytics);
            }
        } catch (Exception e) {
            log.error("Error fetching analytics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse("Error fetching analytics: " + e.getMessage()));
        }
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isExpenseAnalyticsRole(String role) {
        // Expense analytics: Manager (for approvals) and Finance (for finance activity)
        return "MANAGER".equals(role) || "FINANCE".equals(role);
    }
    
    private boolean isLeaveAnalyticsRole(String role) {
        // Leave analytics: Only Manager can access (for approval-related analytics)
        return "MANAGER".equals(role);
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", message);
        return errorResponse;
    }
}

