package com.expensemanagement.saga;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Observability record for one run of a saga against one expense. Not
 * consulted for control flow (the orchestrator drives execution in memory,
 * one HTTP/Kafka-listener invocation at a time) -- this table exists so a
 * finance admin or an on-call engineer can answer "what happened to expense
 * #482's payment" without grepping logs, and so a stuck or repeatedly
 * failing saga is queryable.
 */
@Entity
@Table(name = "saga_executions", indexes = {
    @Index(name = "idx_saga_expense", columnList = "expense_id"),
    @Index(name = "idx_saga_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String sagaType;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status;

    @Column(length = 60)
    private String currentStep;

    // Plain TEXT, not @Lob -- @Lob on PostgreSQL maps Strings to the legacy
    // oid large-object type rather than a normal text column.
    @Column(columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
