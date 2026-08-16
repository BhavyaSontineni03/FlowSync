package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs", indexes = {
    @Index(name = "idx_activity_org", columnList = "organization_id"),
    @Index(name = "idx_activity_user", columnList = "user_id"),
    @Index(name = "idx_activity_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_activity_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 50)
    private String entityType;

    @Column
    private Long entityId;

    @Column(length = 2000)
    private String metadata;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ActivityType {
        // Expense activities
        EXPENSE_CREATED,
        EXPENSE_UPDATED,
        EXPENSE_DELETED,
        EXPENSE_SUBMITTED,
        EXPENSE_APPROVED,
        EXPENSE_REJECTED,
        EXPENSE_PAID,
        // User activities
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        // Organization activities
        ORGANIZATION_CREATED,
        ORGANIZATION_UPDATED,
        // Receipt and OCR activities
        RECEIPT_UPLOADED,
        OCR_PROCESSED,
        // Leave request activities
        LEAVE_REQUEST_CREATED,
        LEAVE_REQUEST_UPDATED,
        LEAVE_REQUEST_APPROVED,
        LEAVE_REQUEST_REJECTED,
        LEAVE_REQUEST_CANCELLED,
        // Timesheet activities
        TIMESHEET_CREATED,
        TIMESHEET_SUBMITTED,
        TIMESHEET_APPROVED,
        TIMESHEET_REJECTED,
        // Payroll activities
        PAYROLL_CALCULATED,
        PAYROLL_PROCESSED,
        // Project activities
        PROJECT_CREATED,
        PROJECT_UPDATED,
        PROJECT_DELETED,
        PROJECT_EMPLOYEE_ASSIGNED,
        PROJECT_EMPLOYEE_UNASSIGNED,
        // Admin request activities
        ADMIN_REQUEST_CREATED,
        ADMIN_REQUEST_APPROVED,
        ADMIN_REQUEST_REJECTED,
        // User login
        USER_LOGGED_IN
    }
}

