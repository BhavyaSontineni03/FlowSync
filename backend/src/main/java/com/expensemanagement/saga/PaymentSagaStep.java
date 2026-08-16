package com.expensemanagement.saga;

import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.EventType;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.OrgBudget;
import com.expensemanagement.model.PaymentLedgerEntry;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrgBudgetRepository;
import com.expensemanagement.repository.PaymentLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Second saga step, run once an expense is approved: reserve budget and
 * mark the expense paid.
 *
 * This is the step with a real, exercised compensation path. Reserving
 * budget and marking PAID happen together here, but if a step later in a
 * larger saga run were to fail (e.g. a payroll-ledger sync step not yet
 * built), compensate() gives back exactly what execute() took: it releases
 * the reserved budget, reverses the ledger entry, and drops the expense back
 * to APPROVED so a human sees an accurate state instead of a phantom
 * payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaStep implements SagaStep<ExpenseSagaContext> {

    private final ExpenseRepository expenseRepository;
    private final OrgBudgetRepository orgBudgetRepository;
    private final PaymentLedgerEntryRepository ledgerRepository;
    private final EventPublisher eventPublisher;

    @Override
    public String name() {
        return "PAYMENT";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(ExpenseSagaContext context) {
        Expense expense = expenseRepository.findById(context.getExpenseId())
                .orElseThrow(() -> new SagaStepException("Expense not found: " + context.getExpenseId()));

        if (expense.getStatus() != Expense.ExpenseStatus.APPROVED) {
            throw new SagaStepException("Expense " + expense.getId() +
                    " is not APPROVED (was " + expense.getStatus() + "); refusing to pay");
        }

        LocalDate today = LocalDate.now();
        OrgBudget budget = orgBudgetRepository
                .findByOrganizationIdAndPeriodYearAndPeriodMonth(context.getOrgId(), today.getYear(), today.getMonthValue())
                .orElseThrow(() -> new SagaStepException(
                        "No budget configured for org " + context.getOrgId() + " for " + today.getYear() + "-" + today.getMonthValue()));

        if (budget.remaining().compareTo(context.getAmount()) < 0) {
            throw new SagaStepException("Org " + context.getOrgId() + " budget exceeded: remaining="
                    + budget.remaining() + ", requested=" + context.getAmount());
        }

        budget.setConsumedAmount(budget.getConsumedAmount().add(context.getAmount()));
        try {
            budget = orgBudgetRepository.saveAndFlush(budget);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Another payment saga reserved against this budget concurrently
            // between our read and write. Treat as a failed step so this
            // saga run compensates cleanly; the caller may retry.
            throw new SagaStepException("Concurrent payment collision on org " + context.getOrgId() + " budget", e);
        }

        PaymentLedgerEntry ledger = ledgerRepository.save(PaymentLedgerEntry.builder()
                .expense(expense)
                .orgBudget(budget)
                .amount(context.getAmount())
                .status(PaymentLedgerEntry.LedgerStatus.COMPLETED)
                .build());

        expense.setStatus(Expense.ExpenseStatus.PAID);
        expenseRepository.save(expense);

        context.setBudgetReserved(true);
        context.setPaymentLedgerEntryId(ledger.getId());
        context.setOrgBudgetId(budget.getId());

        eventPublisher.publish(EventType.EXPENSE_PAID, "Expense", expense.getId(), context.getOrgId(),
                Map.of("amount", context.getAmount().toString(), "ledgerEntryId", ledger.getId()));

        log.info("Paid expense {} for org {}: amount={} budgetRemaining={}",
                expense.getId(), context.getOrgId(), context.getAmount(), budget.remaining());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(ExpenseSagaContext context) {
        if (!context.isBudgetReserved()) {
            log.debug("Nothing to compensate for {} on expense {} (payment never completed)", name(), context.getExpenseId());
            return;
        }

        orgBudgetRepository.findById(context.getOrgBudgetId()).ifPresent(budget -> {
            budget.setConsumedAmount(budget.getConsumedAmount().subtract(context.getAmount()));
            orgBudgetRepository.save(budget);
        });

        ledgerRepository.findById(context.getPaymentLedgerEntryId()).ifPresent(entry -> {
            entry.setStatus(PaymentLedgerEntry.LedgerStatus.REVERSED);
            entry.setReversalReason("Saga compensation: a later step in this saga run failed");
            ledgerRepository.save(entry);
        });

        expenseRepository.findById(context.getExpenseId()).ifPresent(expense -> {
            expense.setStatus(Expense.ExpenseStatus.APPROVED);
            expenseRepository.save(expense);
        });

        eventPublisher.publish(EventType.EXPENSE_PAYMENT_COMPENSATED, "Expense", context.getExpenseId(), context.getOrgId(),
                Map.of("reason", "downstream_saga_step_failed"));

        log.warn("Compensated payment for expense {} (org {}): reverted to APPROVED, budget released",
                context.getExpenseId(), context.getOrgId());
    }
}
