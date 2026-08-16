package com.expensemanagement.event;

/**
 * Fired when an expense submission commits. Listener runs activity log,
 * manager notification, and websocket push AFTER_COMMIT on a separate
 * connection so submitExpense does not hold Hikari while doing side effects.
 */
public record ExpenseSubmittedEvent(Long expenseId, Long organizationId) {
}
