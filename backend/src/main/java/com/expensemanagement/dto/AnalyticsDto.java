package com.expensemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDto {
    private BigDecimal totalExpenses;
    private BigDecimal pendingExpenses;
    private BigDecimal approvedExpenses;
    private BigDecimal rejectedExpenses;
    private Long totalCount;
    private Long pendingCount;
    private Long approvedCount;
    private Long rejectedCount;
    private List<CategoryExpenseDto> expensesByCategory;
    private List<MonthlyExpenseDto> expensesByMonth;
    private Map<String, BigDecimal> expensesByStatus;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryExpenseDto {
        private String category;
        private BigDecimal amount;
        private Long count;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyExpenseDto {
        private String month;
        private BigDecimal amount;
        private Long count;
    }
}

