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
 * Entity representing assignment of an employee to a project.
 * Tracks which employees are working on which projects.
 */
@Entity
@Table(name = "project_assignments", indexes = {
    @Index(name = "idx_assignment_user", columnList = "user_id"),
    @Index(name = "idx_assignment_project", columnList = "project_id"),
    @Index(name = "idx_assignment_user_project", columnList = "user_id, project_id"),
    @Index(name = "idx_assignment_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private LocalDate assignedDate;

    @Column
    private LocalDate unassignedDate;

    @Column(length = 100)
    private String role; // e.g., "Developer", "Lead", "Manager"

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
