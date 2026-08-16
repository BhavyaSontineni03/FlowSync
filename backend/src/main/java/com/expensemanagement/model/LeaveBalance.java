package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing annual leave balance for an employee.
 * Tracks allocated and used leaves per type per year.
 * 
 * Industry Standard Leave Allocation:
 * - Unpaid Leave: 12 days per year (fixed)
 * - Vacation/Paid Leave: 20 days per year (configurable)
 * - Sick Leave: 10 days per year (configurable)
 * - Personal Leave: 5 days per year (configurable)
 */
@Entity
@Table(name = "leave_balances", indexes = {
    @Index(name = "idx_leave_balance_user_year", columnList = "user_id, leave_year", unique = true),
    @Index(name = "idx_leave_balance_org", columnList = "organization_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // Named explicitly: "year" is a reserved word in H2 (and several other
    // SQL dialects), which made schema generation fail silently under the
    // test profile -- Hibernate logged a DDL warning instead of a hard
    // failure, so the table was simply missing at insert time.
    @Column(name = "leave_year", nullable = false)
    private Integer year;

    // Paid Leave (Vacation/Annual Leave)
    @Column(nullable = false)
    @Builder.Default
    private Integer paidLeaveAllocated = 20; // Total paid leaves allocated for the year

    @Column(nullable = false)
    @Builder.Default
    private Integer paidLeaveUsed = 0; // Paid leaves used so far

    // Unpaid Leave (Fixed at 12 per year in India as per industry standard)
    @Column(nullable = false)
    @Builder.Default
    private Integer unpaidLeaveAllocated = 12; // Fixed 12 unpaid leaves

    @Column(nullable = false)
    @Builder.Default
    private Integer unpaidLeaveUsed = 0; // Unpaid leaves used so far

    // Sick Leave
    @Column(nullable = false)
    @Builder.Default
    private Integer sickLeaveAllocated = 10;

    @Column(nullable = false)
    @Builder.Default
    private Integer sickLeaveUsed = 0;

    // Personal Leave
    @Column(nullable = false)
    @Builder.Default
    private Integer personalLeaveAllocated = 5;

    @Column(nullable = false)
    @Builder.Default
    private Integer personalLeaveUsed = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Derived getters for remaining leaves
    public Integer getPaidLeaveRemaining() {
        return paidLeaveAllocated - paidLeaveUsed;
    }

    public Integer getUnpaidLeaveRemaining() {
        return unpaidLeaveAllocated - unpaidLeaveUsed;
    }

    public Integer getSickLeaveRemaining() {
        return sickLeaveAllocated - sickLeaveUsed;
    }

    public Integer getPersonalLeaveRemaining() {
        return personalLeaveAllocated - personalLeaveUsed;
    }

    public Integer getTotalAllocated() {
        return paidLeaveAllocated + unpaidLeaveAllocated + sickLeaveAllocated + personalLeaveAllocated;
    }

    public Integer getTotalUsed() {
        return paidLeaveUsed + unpaidLeaveUsed + sickLeaveUsed + personalLeaveUsed;
    }

    public Integer getTotalRemaining() {
        return getTotalAllocated() - getTotalUsed();
    }
}
