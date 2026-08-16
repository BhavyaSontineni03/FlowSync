package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Result of scoring one expense submission against the anomaly-detection
 * service (see ml-service/). Kept as its own table rather than columns
 * bolted onto Expense so re-scoring (e.g. after a model upgrade) never has
 * to touch the expense row itself, and so the raw feature values used for a
 * given score stay around for later debugging of "why was this flagged".
 */
@Entity
@Table(name = "expense_anomaly_assessments", indexes = {
    @Index(name = "idx_anomaly_expense", columnList = "expense_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseAnomalyAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false, unique = true)
    private Expense expense;

    @Column(nullable = false)
    private Double anomalyScore;

    @Column(nullable = false)
    private Boolean isAnomalous;

    private Double percentileInReference;

    /** JSON snapshot of the 6 feature values used for this score. Plain TEXT,
     * not @Lob -- @Lob on PostgreSQL maps Strings to the legacy oid
     * large-object type rather than a normal text column. */
    @Column(columnDefinition = "TEXT")
    private String featuresJson;

    @Column(nullable = false, length = 60)
    private String modelVersion;

    /** True when the scoring call failed (circuit open, timeout, etc.) and
     * this row represents the safe fallback rather than a real model score. */
    @Builder.Default
    @Column(nullable = false)
    private boolean scoringUnavailable = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime scoredAt;
}
