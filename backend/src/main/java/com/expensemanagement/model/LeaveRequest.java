package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a leave request.
 * Supports various leave types with approval workflow.
 */
@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leave_user", columnList = "user_id"),
    @Index(name = "idx_leave_org", columnList = "organization_id"),
    @Index(name = "idx_leave_status", columnList = "status"),
    @Index(name = "idx_leave_dates", columnList = "start_date, end_date"),
    @Index(name = "idx_leave_org_status", columnList = "organization_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer numberOfDays;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(length = 1000)
    private String approvalComments;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime approvedAt;

    // Paid/Unpaid leave tracking
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPaid = true; // Whether this leave is paid or unpaid

    @Column(nullable = false)
    @Builder.Default
    private Integer paidDays = 0; // Number of paid days

    @Column(nullable = false)
    @Builder.Default
    private Integer unpaidDays = 0; // Number of unpaid days

    public enum LeaveType {
        VACATION,
        SICK_LEAVE,
        PERSONAL_LEAVE,
        UNPAID_LEAVE,
        MATERNITY_LEAVE,
        PATERNITY_LEAVE,
        BEREAVEMENT,
        OTHER
    }

    public enum LeaveStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}

