package com.expensemanagement.event;

/**
 * Every domain event this platform emits. Kept as a closed enum (rather than
 * a free-form string) so producers and consumers can't drift apart on
 * spelling, and so a new event type is a visible, reviewable code change.
 */
public enum EventType {
    EXPENSE_SUBMITTED,
    EXPENSE_SCORED,
    EXPENSE_FLAGGED_FOR_REVIEW,
    EXPENSE_APPROVED,
    EXPENSE_REJECTED,
    EXPENSE_PAYMENT_STARTED,
    EXPENSE_PAID,
    EXPENSE_PAYMENT_COMPENSATED
}
