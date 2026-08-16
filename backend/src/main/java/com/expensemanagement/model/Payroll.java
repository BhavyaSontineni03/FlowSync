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

/**
 * Entity representing payroll for an employee.
 * Calculated based on timesheets (days worked) and leave requests.
 */
@Entity
@Table(name = "payrolls", indexes = {
    @Index(name = "idx_payroll_user", columnList = "user_id"),
    @Index(name = "idx_payroll_period", columnList = "period_month, period_year"),
    @Index(name = "idx_payroll_user_period", columnList = "user_id, period_month, period_year", unique = true),
    @Index(name = "idx_payroll_status", columnList = "status"),
    @Index(name = "idx_payroll_org", columnList = "organization_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private Integer periodMonth; // 1-12

    @Column(nullable = false)
    private Integer periodYear; // e.g., 2024

    // Days calculation
    @Column(nullable = false)
    @Builder.Default
    private Integer daysWorked = 0; // Calculated from approved timesheets

    @Column(nullable = false)
    @Builder.Default
    private Integer totalDaysInMonth = 0; // Total working days in the month

    // Leave calculations
    @Column(nullable = false)
    @Builder.Default
    private Integer paidLeavesUsed = 0; // Paid leaves taken

    @Column(nullable = false)
    @Builder.Default
    private Integer unpaidLeavesUsed = 0; // Unpaid leaves taken

    // Salary calculations
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deductions = BigDecimal.ZERO; // Unpaid leave deductions

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal netSalary = BigDecimal.ZERO; // baseSalary - deductions

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column
    private LocalDateTime processedAt;

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PayrollStatus {
        DRAFT,
        PROCESSED,
        PAID,
        CANCELLED
    }
}
