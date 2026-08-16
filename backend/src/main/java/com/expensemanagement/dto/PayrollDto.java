package com.expensemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long organizationId;
    private Integer periodMonth;
    private Integer periodYear;
    private Integer daysWorked;
    private Integer totalDaysInMonth;
    private Integer paidLeavesUsed;
    private Integer unpaidLeavesUsed;
    private BigDecimal baseSalary;
    private BigDecimal deductions;
    private BigDecimal netSalary;
    private String status;
    private Long processedBy;
    private String processedByName;
    private LocalDateTime processedAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
