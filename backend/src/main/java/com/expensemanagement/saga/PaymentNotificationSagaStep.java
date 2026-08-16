package com.expensemanagement.saga;

import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Third step of the payment saga: confirm the payment to the employee (in-app
 * notification, email, websocket push).
 *
 * This step is why PaymentSagaStep needs a real compensate(): notification
 * delivery depends on infrastructure (SMTP, the websocket broker) that is
 * genuinely separate from the database transaction that already committed
 * the payment. If it fails here, the money movement already happened but
 * nobody downstream has been told -- worse than not having paid at all,
 * because the state is now silently inconsistent. Compensating the payment
 * (release the budget, reverse the ledger entry, drop the expense back to
 * APPROVED) turns that into a clean, retriable state instead: the payment
 * step runs again once the notification path recovers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationSagaStep implements SagaStep<ExpenseSagaContext> {

    private final ExpenseRepository expenseRepository;
    private final NotificationService notificationService;

    @Override
    public String name() {
        return "PAYMENT_NOTIFICATION";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(ExpenseSagaContext context) {
        Expense expense = expenseRepository.findById(context.getExpenseId())
                .orElseThrow(() -> new SagaStepException("Expense not found: " + context.getExpenseId()));
        try {
            // financeUser is accepted for audit-trail symmetry with the
            // manual "mark paid" flow but isn't read by the notification
            // body itself, so the saga (which has no acting finance user)
            // passes null rather than fabricating one.
            notificationService.notifyExpensePaid(expense, null);
        } catch (Exception e) {
            throw new SagaStepException("Failed to notify employee of payment for expense " + expense.getId(), e);
        }
    }

    @Override
    public void compensate(ExpenseSagaContext context) {
        // Nothing to undo here -- if this step itself failed, no
        // notification went out, so there's nothing to retract. The
        // orchestrator still calls compensate() on the *earlier* steps
        // (PaymentSagaStep) that did succeed.
        log.debug("No compensation required for {} on expense {}", name(), context.getExpenseId());
    }
}
