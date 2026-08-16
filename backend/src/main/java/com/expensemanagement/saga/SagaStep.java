package com.expensemanagement.saga;

/**
 * One step of a saga: a unit of work with an explicit undo. The orchestrator
 * runs steps in order; if a later step throws, it calls compensate() on
 * every step that already completed, in reverse order -- the standard
 * saga-pattern contract.
 *
 * Not every step needs a meaningful compensate() (a step with no side
 * effects, like a read-only scoring call, can leave it a no-op) -- but every
 * step must be able to answer "what do we undo if something after this
 * fails", which is the discipline this interface exists to enforce.
 */
public interface SagaStep<C> {

    String name();

    void execute(C context) throws SagaStepException;

    void compensate(C context);
}
