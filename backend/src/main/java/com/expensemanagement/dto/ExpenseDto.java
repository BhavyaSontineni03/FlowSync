package com.expensemanagement.dto;

import com.expensemanagement.model.Expense;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseDto {
    private Long id;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @NotNull(message = "Category is required")
    private Expense.ExpenseCategory category;

    private Expense.ExpenseStatus status;
    private String notes;
    private String receiptPath;
    private String receiptUrl;
    private String ocrExtractedData;
    private Long userId;
    private String userName;
    private Long organizationId;
    private List<ApprovalDto> approvals;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

