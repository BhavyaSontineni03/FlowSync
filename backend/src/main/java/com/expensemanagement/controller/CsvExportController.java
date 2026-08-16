package com.expensemanagement.controller;

import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class CsvExportController {
    
    private final ExpenseRepository expenseRepository;
    private final JwtTokenProvider tokenProvider;
    
    @GetMapping("/expenses/csv")
    public void exportExpensesToCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only MANAGER, ADMIN, and FINANCE can export expenses
        if (!isExportRole(userRole)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        List<Expense> expenses = expenseRepository.findByOrganizationIdAndDateRange(
                organizationId, startDate, endDate);
        
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"expenses_" + LocalDate.now() + ".csv\"");
        
        try (PrintWriter writer = response.getWriter();
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "ID", "Description", "Amount", "Date", "Category", "Status", 
                     "Employee", "Notes", "Created At"))) {
            
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            for (Expense expense : expenses) {
                csvPrinter.printRecord(
                        expense.getId(),
                        expense.getDescription(),
                        expense.getAmount(),
                        expense.getExpenseDate().format(dateFormatter),
                        expense.getCategory().name(),
                        expense.getStatus().name(),
                        expense.getUser().getFirstName() + " " + expense.getUser().getLastName(),
                        expense.getNotes() != null ? expense.getNotes() : "",
                        expense.getCreatedAt().format(dateTimeFormatter)
                );
            }
            
            csvPrinter.flush();
        }
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isExportRole(String role) {
        return "MANAGER".equals(role) || "ADMIN".equals(role) || "FINANCE".equals(role);
    }
}

