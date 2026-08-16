package com.expensemanagement.saga;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mutable state threaded through one saga run for one expense. Steps read
 * what earlier steps produced and write what later steps (or compensation)
 * will need -- e.g. PaymentSagaStep needs to know whether the payment
 * actually reserved budget before compensate() can decide whether there is
 * anything to reverse.
 */
@Getter
@Setter
public class ExpenseSagaContext {

    private final Long expenseId;
    private final Long orgId;
    private final BigDecimal amount;

    // Populated by AnomalyScoringSagaStep
    private Double anomalyScore;
    private boolean flaggedForReview;

    // Populated by PaymentSagaStep; used by its own compensate()
    private boolean budgetReserved;
    private Long paymentLedgerEntryId;
    private Long orgBudgetId;

    public ExpenseSagaContext(Long expenseId, Long orgId, BigDecimal amount) {
        this.expenseId = expenseId;
        this.orgId = orgId;
        this.amount = amount;
    }
}
