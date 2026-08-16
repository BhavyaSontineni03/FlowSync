package com.expensemanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AdminRequest {

    public enum RequestType {
        USER_CREATE,
        USER_UPDATE,
        USER_DISABLE,
        PROJECT_CREATE,
        PROJECT_UPDATE,
        PROJECT_DELETE,
        PROJECT_ASSIGN,      // Request to assign employee to project
        PROJECT_UNASSIGN,    // Request to unassign employee from project
        PROFILE_UPDATE       // User requesting to update their own profile details
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = 1000)
    private String comments;

    @Column(length = 500)
    private String description;

    // Plain TEXT, not @Lob -- @Lob on PostgreSQL maps Strings to the legacy
    // oid large-object type rather than a normal text column.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    
    // Helper method to set organization
    public void setOrganization(Organization org) {
        this.organization = org;
        if (org != null) {
            this.organizationId = org.getId();
        }
    }
}
