package com.expensemanagement.service;

import com.expensemanagement.event.ExpenseSubmittedEvent;
import com.expensemanagement.model.ActivityLog;
import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Post-commit submit side effects: activity log, manager notify, websocket.
 * Runs AFTER_COMMIT {@code @Async} so {@code submitExpense} can release its DB
 * connection first.
 *
 * <p>Opens a short write transaction on the async thread and loads the expense
 * with user/organization/manager join-fetched so lazy associations needed by
 * side effects are initialized inside a persistence context (avoids
 * LazyInitializationException that would otherwise be swallowed after HTTP 200).
 *
 * <p>Best-effort only: failures are retried once per effect, then swallowed so
 * they cannot affect the already-committed HTTP submit path. Effects are
 * isolated from each other so one failure does not skip the rest. No outbox;
 * durable delivery for notifications is deferred.
 *
 * <p>Disabled under {@code loadtest} so AFTER_COMMIT side effects do not contend
 * for Hikari with the timed JDBC write path during k6 measurement.
 */
@Component
@Profile("!loadtest")
@RequiredArgsConstructor
@Slf4j
public class ExpenseSubmissionEventListener {

    private static final int MAX_ATTEMPTS = 2;

    private final ExpenseRepository expenseRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;

    @Async("submissionSideEffectsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExpenseSubmitted(ExpenseSubmittedEvent event) {
        expenseRepository.findByIdWithUserOrgAndManager(event.expenseId()).ifPresentOrElse(expense -> {
            runWithRetry("activityLog", event.expenseId(), () ->
                    activityLogService.logActivity(
                            ActivityLog.ActivityType.EXPENSE_SUBMITTED,
                            "Expense submitted: " + expense.getDescription(),
                            expense.getUser(),
                            expense.getOrganization(),
                            "Expense",
                            expense.getId(),
                            null
                    ));
            runWithRetry("notifyManagers", event.expenseId(), () ->
                    notificationService.notifyManagersForApproval(expense));
            runWithRetry("websocket", event.expenseId(), () ->
                    webSocketService.sendExpenseUpdate(event.organizationId(), toDtoSafely(expense)));
        }, () -> log.warn(
                "event=expense_submit_side_effect status=skipped reason=expense_missing expenseId={}",
                event.expenseId()));
    }

    /**
     * Runs {@code action} up to {@link #MAX_ATTEMPTS} times. Never throws;
     * the submit HTTP path must stay unaffected after commit.
     */
    void runWithRetry(String effect, Long expenseId, Runnable action) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                action.run();
                if (attempt > 1) {
                    log.info(
                            "event=expense_submit_side_effect effect={} expenseId={} status=recovered attempt={}",
                            effect, expenseId, attempt);
                }
                return;
            } catch (Exception ex) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn(
                            "event=expense_submit_side_effect effect={} expenseId={} status=retry attempt={} error={}",
                            effect, expenseId, attempt, ex.toString());
                } else {
                    log.error(
                            "event=expense_submit_side_effect effect={} expenseId={} status=failed attempts={} error={}",
                            effect, expenseId, MAX_ATTEMPTS, ex.toString());
                }
            }
        }
    }

    private Object toDtoSafely(Expense expense) {
        // Lightweight payload; websocket is a live hint, not source of truth.
        return java.util.Map.of(
                "id", expense.getId(),
                "status", expense.getStatus().name(),
                "amount", expense.getAmount(),
                "category", expense.getCategory().name()
        );
    }
}
