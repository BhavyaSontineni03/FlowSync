package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service for bulk operations on expenses.
 * Handles CSV import/export and bulk approval/rejection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkOperationsService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final ApprovalService approvalService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Import expenses from CSV file.
     * 
     * @param file CSV file
     * @param userId User ID importing expenses
     * @param organizationId Organization ID
     * @return Map with import results (successful, failed, errors)
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public Map<String, Object> importExpensesFromCsv(MultipartFile file, Long userId, Long organizationId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> errors = new ArrayList<>();
        int successful = 0;
        int failed = 0;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {
                try {
                    Expense expense = parseExpenseFromCsvRecord(record, user, organization);
                    expenseRepository.save(expense);
                    successful++;

                    activityLogService.logActivity(
                            com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_CREATED,
                            "Expense imported from CSV: " + expense.getDescription(),
                            user,
                            organization,
                            "Expense",
                            expense.getId(),
                            null
                    );
                } catch (Exception e) {
                    failed++;
                    Map<String, String> error = new HashMap<>();
                    error.put("row", String.valueOf(record.getRecordNumber()));
                    error.put("message", e.getMessage());
                    errors.add(error);
                    log.error("Error importing expense from row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Error reading CSV file", e);
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }

        result.put("successful", successful);
        result.put("failed", failed);
        result.put("total", successful + failed);
        result.put("errors", errors);

        // Send real-time update
        Map<String, Object> update = new HashMap<>();
        update.put("type", "bulk_import");
        update.put("count", successful);
        webSocketService.sendExpenseUpdate(organizationId, update);

        return result;
    }

    /**
     * Bulk approve expenses.
     * 
     * @param expenseIds List of expense IDs
     * @param approverId Approver user ID
     * @param comments Approval comments
     * @return Map with approval results
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public Map<String, Object> bulkApprove(List<Long> expenseIds, Long approverId, String comments) {
        Map<String, Object> result = new HashMap<>();
        List<Long> successful = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        for (Long expenseId : expenseIds) {
            try {
                approvalService.approveExpense(expenseId, approverId, comments);
                successful.add(expenseId);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("expenseId", expenseId);
                error.put("message", e.getMessage());
                failed.add(error);
                log.error("Error approving expense {}: {}", expenseId, e.getMessage());
            }
        }

        result.put("successful", successful.size());
        result.put("failed", failed.size());
        result.put("total", expenseIds.size());
        result.put("successfulIds", successful);
        result.put("failedDetails", failed);

        return result;
    }

    /**
     * Bulk reject expenses.
     * 
     * @param expenseIds List of expense IDs
     * @param approverId Approver user ID
     * @param comments Rejection comments (required)
     * @return Map with rejection results
     */
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public Map<String, Object> bulkReject(List<Long> expenseIds, Long approverId, String comments) {
        Map<String, Object> result = new HashMap<>();
        List<Long> successful = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        for (Long expenseId : expenseIds) {
            try {
                approvalService.rejectExpense(expenseId, approverId, comments);
                successful.add(expenseId);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("expenseId", expenseId);
                error.put("message", e.getMessage());
                failed.add(error);
                log.error("Error rejecting expense {}: {}", expenseId, e.getMessage());
            }
        }

        result.put("successful", successful.size());
        result.put("failed", failed.size());
        result.put("total", expenseIds.size());
        result.put("successfulIds", successful);
        result.put("failedDetails", failed);

        return result;
    }

    /**
     * Export expenses to CSV.
     * 
     * @param organizationId Organization ID
     * @param expenseIds Optional list of expense IDs (if null, exports all)
     * @param response HTTP response
     * @return void (writes directly to response)
     */
    public void exportExpensesToCsv(Long organizationId, List<Long> expenseIds, HttpServletResponse response) throws IOException {
        List<Expense> expenses;
        
        if (expenseIds != null && !expenseIds.isEmpty()) {
            expenses = expenseRepository.findAllById(expenseIds);
            // Filter by organization
            expenses = expenses.stream()
                    .filter(e -> e.getOrganization().getId().equals(organizationId))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            expenses = expenseRepository.findByOrganizationId(organizationId, 
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        }

        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"expenses_" + LocalDate.now() + ".csv\"");

        try (PrintWriter writer = response.getWriter();
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "ID", "Description", "Amount", "Date", "Category", "Status",
                     "Employee", "Notes", "Created At"))) {

            for (Expense expense : expenses) {
                csvPrinter.printRecord(
                        expense.getId(),
                        expense.getDescription(),
                        expense.getAmount(),
                        expense.getExpenseDate().format(DATE_FORMATTER),
                        expense.getCategory().name(),
                        expense.getStatus().name(),
                        expense.getUser().getFirstName() + " " + expense.getUser().getLastName(),
                        expense.getNotes() != null ? expense.getNotes() : "",
                        expense.getCreatedAt().format(DATE_TIME_FORMATTER)
                );
            }

            csvPrinter.flush();
        }
    }

    /**
     * Parse expense from CSV record.
     */
    private Expense parseExpenseFromCsvRecord(CSVRecord record, User user, Organization organization) {
        String description = record.get("Description");
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(record.get("Amount"));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than 0");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + record.get("Amount"));
        }

        LocalDate expenseDate;
        try {
            expenseDate = LocalDate.parse(record.get("Date"), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd: " + record.get("Date"));
        }

        Expense.ExpenseCategory category;
        try {
            category = Expense.ExpenseCategory.valueOf(record.get("Category").toUpperCase());
        } catch (IllegalArgumentException e) {
            category = Expense.ExpenseCategory.OTHER;
        }

        String notes = record.isSet("Notes") ? record.get("Notes") : null;

        return Expense.builder()
                .description(description)
                .amount(amount)
                .expenseDate(expenseDate)
                .category(category)
                .status(Expense.ExpenseStatus.PENDING)
                .notes(notes)
                .user(user)
                .organization(organization)
                .build();
    }
}

