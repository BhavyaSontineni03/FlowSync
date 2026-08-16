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
 * Entity representing a timesheet entry.
 * Employees submit timesheets for projects they're assigned to, or use bench code if not assigned.
 * Also supports auto-generated LEAVE entries when leave requests are approved.
 */
@Entity
@Table(name = "timesheets", indexes = {
    @Index(name = "idx_timesheet_user", columnList = "user_id"),
    @Index(name = "idx_timesheet_project", columnList = "project_id"),
    @Index(name = "idx_timesheet_date", columnList = "date"),
    @Index(name = "idx_timesheet_status", columnList = "status"),
    @Index(name = "idx_timesheet_user_date", columnList = "user_id, date"),
    @Index(name = "idx_timesheet_org", columnList = "organization_id"),
    @Index(name = "idx_timesheet_entry_type", columnList = "entry_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timesheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 50)
    private String projectCode; // Project code, "BENCH", or "LEAVE"

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Double hours; // Hours worked (typically 8 for full day, 0 for leave)

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TimesheetStatus status = TimesheetStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(length = 1000)
    private String approvalComments;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Entry type to distinguish between work days and leave days
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    @Builder.Default
    private EntryType entryType = EntryType.WORK;

    // Reference to leave request if this is a leave entry
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id")
    private LeaveRequest leaveRequest;

    // Leave type for display purposes (only set for LEAVE entries)
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type")
    private LeaveRequest.LeaveType leaveType;

    // Whether the leave is paid or unpaid (only for LEAVE entries)
    @Column(name = "is_paid_leave")
    private Boolean isPaidLeave;

    public enum TimesheetStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED
    }

    public enum EntryType {
        WORK,       // Regular work day
        LEAVE,      // Leave day (auto-generated from approved leave request)
        HOLIDAY     // Public holiday (can be added later)
    }

    // Helper method to check if this entry can be modified
    public boolean isEditable() {
        // Leave entries cannot be edited by users - they're auto-generated
        return entryType == EntryType.WORK && status == TimesheetStatus.DRAFT;
    }
}
