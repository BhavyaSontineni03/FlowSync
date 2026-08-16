package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses", indexes = {
    @Index(name = "idx_expense_user", columnList = "user_id"),
    @Index(name = "idx_expense_org", columnList = "organization_id"),
    @Index(name = "idx_expense_status", columnList = "status"),
    @Index(name = "idx_expense_date", columnList = "expense_date"),
    @Index(name = "idx_expense_org_date", columnList = "organization_id, expense_date"),
    @Index(name = "idx_expense_org_status", columnList = "organization_id, status"),
    @Index(name = "idx_expense_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseCategory category = ExpenseCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Column(length = 1000)
    private String notes;

    @Column(length = 500)
    private String receiptPath;

    @Column(length = 500)
    private String receiptUrl;

    @Column(length = 1000)
    private String ocrExtractedData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Approval> approvals = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** When the employee actually submitted this expense for approval (as
     * opposed to createdAt, which is when the draft row was first saved).
     * Null while the expense is still a PENDING draft. Used by the
     * anomaly-scoring saga step for the submission-lag feature. */
    private LocalDateTime submittedAt;

    public enum ExpenseCategory {
        TRAVEL,
        MEALS,
        ACCOMMODATION,
        TRANSPORTATION,
        OFFICE_SUPPLIES,
        SOFTWARE,
        TRAINING,
        ENTERTAINMENT,
        UTILITIES,
        OTHER
    }

    public enum ExpenseStatus {
        PENDING,
        SUBMITTED,
        APPROVED,
        REJECTED,
        PAID
    }
}

