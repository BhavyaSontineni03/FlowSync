package com.expensemanagement.service;

import com.expensemanagement.dto.AnalyticsDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for analytics and reporting.
 * Provides expense analytics with caching support for improved performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    
    private final ExpenseRepository expenseRepository;
    
    /**
     * Get analytics for an organization within a date range.
     * Results are cached for 5 minutes to improve performance.
     * Returns different analytics based on user role:
     * - MANAGER: Approval-focused analytics (pending, submitted, approved, rejected)
     * - FINANCE: Payment-focused analytics (approved ready to pay, paid)
     * 
     * @param organizationId The organization ID
     * @param startDate Start date (defaults to 6 months ago if null)
     * @param endDate End date (defaults to today if null)
     * @param userRole User role (MANAGER or FINANCE) to determine analytics view
     * @return AnalyticsDto containing expense analytics
     */
    @Cacheable(value = "analytics", key = "#organizationId + '_' + #userRole + '_' + (#startDate != null ? #startDate.toString() : 'null') + '_' + (#endDate != null ? #endDate.toString() : 'null')")
    public AnalyticsDto getAnalytics(Long organizationId, LocalDate startDate, LocalDate endDate, String userRole) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        List<Expense> expenses = expenseRepository.findByOrganizationIdAndDateRange(
                organizationId, startDate, endDate);
        
        AnalyticsDto.AnalyticsDtoBuilder builder = AnalyticsDto.builder();
        
        // Role-based analytics
        if ("FINANCE".equals(userRole)) {
            // Finance view: Payment-focused analytics
            BigDecimal approvedForPayment = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal paidExpenses = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.PAID)
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalExpenses = approvedForPayment.add(paidExpenses);
            
            long approvedCount = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                    .count();
            
            long paidCount = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.PAID)
                    .count();
            
            long totalCount = approvedCount + paidCount;
        
            // Expenses by category (only approved and paid)
            Map<Expense.ExpenseCategory, BigDecimal> categoryAmounts = new HashMap<>();
            Map<Expense.ExpenseCategory, Long> categoryCounts = new HashMap<>();
            
            for (Expense expense : expenses) {
                if (expense.getStatus() == Expense.ExpenseStatus.APPROVED || 
                    expense.getStatus() == Expense.ExpenseStatus.PAID) {
                    categoryAmounts.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
                    categoryCounts.merge(expense.getCategory(), 1L, Long::sum);
                }
            }
            
            List<AnalyticsDto.CategoryExpenseDto> expensesByCategory = categoryAmounts.entrySet().stream()
                    .map(entry -> {
                        AnalyticsDto.CategoryExpenseDto dto = new AnalyticsDto.CategoryExpenseDto();
                        dto.setCategory(entry.getKey().name());
                        dto.setAmount(entry.getValue());
                        dto.setCount(categoryCounts.getOrDefault(entry.getKey(), 0L));
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            // Expenses by month (only approved and paid)
            Map<String, BigDecimal> monthlyAmounts = new HashMap<>();
            Map<String, Long> monthlyCounts = new HashMap<>();
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
            
            for (Expense expense : expenses) {
                if (expense.getStatus() == Expense.ExpenseStatus.APPROVED || 
                    expense.getStatus() == Expense.ExpenseStatus.PAID) {
                    String month = expense.getExpenseDate().format(monthFormatter);
                    monthlyAmounts.merge(month, expense.getAmount(), BigDecimal::add);
                    monthlyCounts.merge(month, 1L, Long::sum);
                }
            }
            
            List<AnalyticsDto.MonthlyExpenseDto> expensesByMonth = monthlyAmounts.entrySet().stream()
                    .map(entry -> {
                        AnalyticsDto.MonthlyExpenseDto dto = new AnalyticsDto.MonthlyExpenseDto();
                        dto.setMonth(entry.getKey());
                        dto.setAmount(entry.getValue());
                        dto.setCount(monthlyCounts.getOrDefault(entry.getKey(), 0L));
                        return dto;
                    })
                    .sorted((a, b) -> a.getMonth().compareTo(b.getMonth()))
                    .collect(Collectors.toList());
            
            // Expenses by status (APPROVED and PAID only)
            Map<String, BigDecimal> expensesByStatus = new HashMap<>();
            expensesByStatus.put("APPROVED", approvedForPayment);
            expensesByStatus.put("PAID", paidExpenses);
            
            return builder
                    .totalExpenses(totalExpenses)
                    .pendingExpenses(BigDecimal.ZERO) // Not relevant for Finance
                    .approvedExpenses(approvedForPayment)
                    .rejectedExpenses(BigDecimal.ZERO) // Not relevant for Finance
                    .totalCount(totalCount)
                    .pendingCount(0L)
                    .approvedCount(approvedCount)
                    .rejectedCount(0L)
                    .expensesByCategory(expensesByCategory)
                    .expensesByMonth(expensesByMonth)
                    .expensesByStatus(expensesByStatus)
                    .build();
        } else {
            // Manager view: Approval-focused analytics
            BigDecimal totalExpenses = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // For managers, "pending" means pending approval, which is only SUBMITTED expenses
            // PENDING expenses are drafts that haven't been submitted yet
            BigDecimal pendingExpenses = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.SUBMITTED)
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal approvedExpenses = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal rejectedExpenses = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.REJECTED)
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long totalCount = expenses.size();
            long pendingCount = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.SUBMITTED)
                    .count();
            long approvedCount = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                    .count();
            long rejectedCount = expenses.stream()
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.REJECTED)
                    .count();
            
            // Expenses by category
            Map<Expense.ExpenseCategory, BigDecimal> categoryAmounts = new HashMap<>();
            Map<Expense.ExpenseCategory, Long> categoryCounts = new HashMap<>();
            
            for (Expense expense : expenses) {
                categoryAmounts.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
                categoryCounts.merge(expense.getCategory(), 1L, Long::sum);
            }
            
            List<AnalyticsDto.CategoryExpenseDto> expensesByCategory = categoryAmounts.entrySet().stream()
                    .map(entry -> {
                        AnalyticsDto.CategoryExpenseDto dto = new AnalyticsDto.CategoryExpenseDto();
                        dto.setCategory(entry.getKey().name());
                        dto.setAmount(entry.getValue());
                        dto.setCount(categoryCounts.getOrDefault(entry.getKey(), 0L));
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            // Expenses by month
            Map<String, BigDecimal> monthlyAmounts = new HashMap<>();
            Map<String, Long> monthlyCounts = new HashMap<>();
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
            
            for (Expense expense : expenses) {
                String month = expense.getExpenseDate().format(monthFormatter);
                monthlyAmounts.merge(month, expense.getAmount(), BigDecimal::add);
                monthlyCounts.merge(month, 1L, Long::sum);
            }
            
            List<AnalyticsDto.MonthlyExpenseDto> expensesByMonth = monthlyAmounts.entrySet().stream()
                    .map(entry -> {
                        AnalyticsDto.MonthlyExpenseDto dto = new AnalyticsDto.MonthlyExpenseDto();
                        dto.setMonth(entry.getKey());
                        dto.setAmount(entry.getValue());
                        dto.setCount(monthlyCounts.getOrDefault(entry.getKey(), 0L));
                        return dto;
                    })
                    .sorted((a, b) -> a.getMonth().compareTo(b.getMonth()))
                    .collect(Collectors.toList());
            
            // Expenses by status (SUBMITTED for pending approval, APPROVED, REJECTED - not PAID for Manager view)
            Map<String, BigDecimal> expensesByStatus = new HashMap<>();
            expensesByStatus.put("SUBMITTED", pendingExpenses); // SUBMITTED = pending approval
            expensesByStatus.put("APPROVED", approvedExpenses);
            expensesByStatus.put("REJECTED", rejectedExpenses);
            
            return builder
                    .totalExpenses(totalExpenses)
                    .pendingExpenses(pendingExpenses)
                    .approvedExpenses(approvedExpenses)
                    .rejectedExpenses(rejectedExpenses)
                    .totalCount(totalCount)
                    .pendingCount(pendingCount)
                    .approvedCount(approvedCount)
                    .rejectedCount(rejectedCount)
                    .expensesByCategory(expensesByCategory)
                    .expensesByMonth(expensesByMonth)
                    .expensesByStatus(expensesByStatus)
                    .build();
        }
    }
}

