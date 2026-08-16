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
 * Entity representing a project in the organization.
 * Projects have unique codes and can be assigned to employees.
 */
@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_code", columnList = "code"),
    @Index(name = "idx_project_org", columnList = "organization_id"),
    @Index(name = "idx_project_status", columnList = "status"),
    @Index(name = "idx_project_org_code", columnList = "organization_id, code", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Uniqueness is enforced per organization via idx_project_org_code below,
    // not globally -- two different tenants are allowed to both run a
    // "PROJ001". A bare column-level unique constraint here would silently
    // defeat that tenant isolation.
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /**
     * The manager responsible for this project.
     * This is typically a MANAGER who oversees the project and its team.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ProjectStatus {
        ACTIVE,
        INACTIVE,
        COMPLETED,
        ON_HOLD
    }
}
