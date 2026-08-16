package com.expensemanagement.controller;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.ExpenseHotPathService;
import com.expensemanagement.service.ExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    
    private final ExpenseService expenseService;
    private final ExpenseHotPathService expenseHotPathService;
    private final JwtTokenProvider tokenProvider;

    /**
     * Hot-path JSON create (no multipart). Used by load tests and any client
     * that does not need receipt upload on the sync write path. The UI keeps
     * using multipart {@link #createExpense}.
     */
    @PostMapping(value = "/fast", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExpenseDto> createExpenseFast(
            @Valid @RequestBody ExpenseDto expenseDto,
            HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);

        ExpenseDto created = expenseHotPathService.createFast(expenseDto, userId, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ExpenseDto> createExpense(
            @RequestPart("expense") String expenseJson,
            @RequestPart(value = "receipt", required = false) MultipartFile receiptFile,
            HttpServletRequest request) {
        
        // Parse JSON manually or use ObjectMapper
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ExpenseDto expenseDto;
        try {
            expenseDto = objectMapper.readValue(expenseJson, ExpenseDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid expense data: " + e.getMessage(), e);
        }
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        ExpenseDto created = expenseService.createExpense(expenseDto, userId, organizationId, receiptFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseDto> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseDto expenseDto,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        ExpenseDto updated = expenseService.updateExpense(id, expenseDto, userId, organizationId);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id, HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        expenseService.deleteExpense(id, userId, organizationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Hot-path submit: JDBC status flip + outbox insert in one TX; side effects
     * stay AFTER_COMMIT. Prefer this for load measurement over Hibernate submit.
     */
    @PostMapping("/{id}/submit/fast")
    public ResponseEntity<ExpenseDto> submitExpenseFast(@PathVariable Long id, HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);

        ExpenseDto submitted = expenseHotPathService.submitFast(id, userId, organizationId);
        return ResponseEntity.ok(submitted);
    }
    
    @PostMapping("/{id}/submit")
    public ResponseEntity<ExpenseDto> submitExpense(@PathVariable Long id, HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        ExpenseDto submitted = expenseService.submitExpense(id, userId, organizationId);
        return ResponseEntity.ok(submitted);
    }
    
    @GetMapping
    public ResponseEntity<Page<ExpenseDto>> getExpenses(
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
        
        // Privacy: Everyone sees only their own expenses in the Expenses page
        // Managers approve team expenses via the Approvals page (separate endpoint)
        // Admins can see all expenses
        Long managerId = null;
        if ("ADMIN".equals(userRole)) {
            // Admins can see all expenses or filter by userId if specified
            // userId remains as passed (null = all, specific = filtered)
        } else {
            // EMPLOYEE, MANAGER, HR, FINANCE all see only their own expenses
            userId = currentUserId;
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ExpenseDto> expenses = expenseService.getExpenses(organizationId, userId, managerId, pageable);
        return ResponseEntity.ok(expenses);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseDto> getExpenseById(@PathVariable Long id, HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        Long currentUserId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        try {
            ExpenseDto expense = expenseService.getExpenseById(id, organizationId, currentUserId, userRole);
            return ResponseEntity.ok(expense);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Not authorized")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
