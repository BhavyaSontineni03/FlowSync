package com.expensemanagement.saga;

/** Raised by a SagaStep's execute() to signal that step failed and every
 * previously-completed step in this saga run should be compensated. */
public class SagaStepException extends RuntimeException {
    public SagaStepException(String message) {
        super(message);
    }

    public SagaStepException(String message, Throwable cause) {
        super(message, cause);
    }
}
