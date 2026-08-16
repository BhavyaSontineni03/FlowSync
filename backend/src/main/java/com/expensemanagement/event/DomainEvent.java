package com.expensemanagement.event;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Append-only event store. This table is the source of truth for
 * "what happened" -- every state transition the expense-lifecycle saga
 * cares about is written here first, inside the same database transaction
 * as the business-entity change it describes. Kafka publication happens
 * only after that transaction commits (see EventPublisher), so this table
 * and the org-scoped Kafka topics never disagree about what was persisted.
 *
 * Rows are never updated or deleted -- replaying them in order for a given
 * aggregateId reconstructs that expense's full history, which is what makes
 * this event sourcing rather than just an audit log.
 */
@Entity
@Table(name = "domain_events", indexes = {
    @Index(name = "idx_domain_event_aggregate", columnList = "aggregate_type, aggregate_id"),
    @Index(name = "idx_domain_event_org", columnList = "org_id"),
    @Index(name = "idx_domain_event_published", columnList = "published")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventType eventType;

    /** e.g. "Expense" -- the type of entity this event describes. */
    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    /** The entity's primary key, e.g. the expense id. */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** JSON-serialized event payload. Kept as text rather than a fixed
     * column-per-field schema so new event types don't require a migration.
     * Explicit TEXT column definition -- plain @Lob on PostgreSQL maps
     * Strings to the legacy oid large-object type, which needs its own
     * vacuum/cleanup story and isn't readable with a plain SELECT. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Flipped to true once the after-commit Kafka publish succeeds. A
     * background sweep (KafkaOutboxRelay) retries any event still false
     * after a grace period, covering the case where the app crashed between
     * commit and publish -- the classic transactional-outbox guarantee. */
    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
