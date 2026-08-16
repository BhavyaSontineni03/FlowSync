package com.expensemanagement.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-organization, per-month spending envelope. The payment step of the
 * expense-lifecycle saga reserves against this before marking an expense
 * PAID; if the reservation would exceed the allocation, the step fails and
 * the saga compensates (reverting the expense to APPROVED and notifying
 * finance) instead of silently overspending.
 *
 * @Version enables optimistic locking: two payment sagas racing to reserve
 * budget for the same org/period will not both succeed on a read-then-write
 * that should have been exclusive -- the loser gets an optimistic-lock
 * failure, which the saga treats as a retryable compensation trigger.
 */
@Entity
@Table(name = "org_budgets", uniqueConstraints = {
    @UniqueConstraint(name = "uk_org_budget_period", columnNames = {"organization_id", "period_year", "period_month"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @Version
    private Long version;

    public BigDecimal remaining() {
        return allocatedAmount.subtract(consumedAmount);
    }
}
