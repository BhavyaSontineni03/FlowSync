package com.expensemanagement.controller;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.BulkOperationsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller for bulk operations on expenses.
 * Supports bulk import, export, and batch approval/rejection.
 */
@RestController
@RequestMapping("/api/bulk")
@RequiredArgsConstructor
public class BulkOperationsController {

    private final BulkOperationsService bulkOperationsService;
    private final JwtTokenProvider tokenProvider;

    /**
     * Import expenses from CSV file.
     * 
     * @param file CSV file containing expense data
     * @param request HTTP request
     * @return Response with import results
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importExpenses(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        Map<String, Object> result = bulkOperationsService.importExpensesFromCsv(file, userId, organizationId);
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk approve expenses.
     * 
     * @param expenseIds List of expense IDs to approve
     * @param comments Optional approval comments
     * @param request HTTP request
     * @return Response with approval results
     */
    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> bulkApprove(
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        
        @SuppressWarnings("unchecked")
        List<Long> expenseIds = (List<Long>) requestBody.get("expenseIds");
        String comments = (String) requestBody.get("comments");
        
        String token = getTokenFromRequest(request);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER, ADMIN, and FINANCE can bulk approve
        if (!isApproverRole(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Map<String, Object> result = bulkOperationsService.bulkApprove(expenseIds, approverId, comments);
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk reject expenses.
     * 
     * @param requestBody Request body containing expense IDs and comments
     * @param request HTTP request
     * @return Response with rejection results
     */
    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> bulkReject(
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        
        @SuppressWarnings("unchecked")
        List<Long> expenseIds = (List<Long>) requestBody.get("expenseIds");
        String comments = (String) requestBody.get("comments");
        
        if (comments == null || comments.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Rejection comments are required"));
        }
        
        String token = getTokenFromRequest(request);
        Long approverId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER, ADMIN, and FINANCE can bulk reject
        if (!isApproverRole(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Map<String, Object> result = bulkOperationsService.bulkReject(expenseIds, approverId, comments);
        return ResponseEntity.ok(result);
    }

    /**
     * Export expenses to CSV.
     * Enhanced version with filtering options.
     * 
     * @param expenseIds Optional list of expense IDs to export (if null, exports all)
     * @param request HTTP request
     * @param response HTTP response
     * @return void (writes CSV to response)
     */
    @PostMapping("/export")
    public void bulkExport(
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER, ADMIN, and FINANCE can export
        if (!isExportRole(userRole)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<Long> expenseIds = requestBody != null ? 
                (List<Long>) requestBody.get("expenseIds") : null;
        
        bulkOperationsService.exportExpensesToCsv(organizationId, expenseIds, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isApproverRole(String role) {
        return "MANAGER".equals(role) || "ADMIN".equals(role) || "FINANCE".equals(role);
    }

    private boolean isExportRole(String role) {
        return "MANAGER".equals(role) || "ADMIN".equals(role) || "FINANCE".equals(role);
    }
}

