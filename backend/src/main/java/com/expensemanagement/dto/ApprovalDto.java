package com.expensemanagement.dto;

import com.expensemanagement.model.Approval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDto {
    private Long id;
    private Long expenseId;
    private Long approverId;
    private String approverName;
    private Approval.ApprovalStatus status;
    private String comments;
    private LocalDateTime createdAt;
    private ExpenseDto expense;
}

