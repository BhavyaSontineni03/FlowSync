package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records the payment side-effect of the saga's payment step, independent of
 * the Expense entity's own status field. Having a dedicated ledger row means
 * a compensated (reversed) payment leaves a visible trail -- "this was
 * charged against the budget, then reversed at 14:32 because X" -- rather
 * than just quietly flipping a status back and losing that history.
 */
@Entity
@Table(name = "payment_ledger_entries", indexes = {
    @Index(name = "idx_ledger_expense", columnList = "expense_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_budget_id", nullable = false)
    private OrgBudget orgBudget;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerStatus status;

    private String reversalReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum LedgerStatus {
        COMPLETED,
        REVERSED
    }
}
