package com.expensemanagement.saga;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a named sequence of SagaSteps against one expense, tracking progress
 * in SagaExecution and compensating already-completed steps (in reverse
 * order) the moment any step throws.
 *
 * This class intentionally does not manage its own database transaction --
 * each step opens its own (REQUIRES_NEW), because a saga's whole point is
 * coordinating separately-committed units of work. Wrapping the orchestrator
 * itself in one transaction would silently turn it back into the single ACID
 * transaction a saga exists to avoid needing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final AnomalyScoringSagaStep anomalyScoringSagaStep;
    private final PaymentSagaStep paymentSagaStep;
    private final PaymentNotificationSagaStep paymentNotificationSagaStep;
    private final com.expensemanagement.repository.SagaExecutionRepository sagaExecutionRepository;

    public void runExpenseSubmissionSaga(Long expenseId, Long orgId, BigDecimal amount) {
        run("ExpenseSubmissionSaga", expenseId, orgId, amount, List.of(anomalyScoringSagaStep));
    }

    public void runExpensePaymentSaga(Long expenseId, Long orgId, BigDecimal amount) {
        run("ExpensePaymentSaga", expenseId, orgId, amount, List.of(paymentSagaStep, paymentNotificationSagaStep));
    }

    private void run(String sagaType, Long expenseId, Long orgId, BigDecimal amount, List<SagaStep<ExpenseSagaContext>> steps) {
        ExpenseSagaContext context = new ExpenseSagaContext(expenseId, orgId, amount);
        SagaExecution execution = sagaExecutionRepository.save(SagaExecution.builder()
                .sagaType(sagaType)
                .expenseId(expenseId)
                .orgId(orgId)
                .status(SagaStatus.IN_PROGRESS)
                .build());

        List<SagaStep<ExpenseSagaContext>> completedSteps = new ArrayList<>();
        try {
            for (SagaStep<ExpenseSagaContext> step : steps) {
                execution.setCurrentStep(step.name());
                sagaExecutionRepository.save(execution);

                step.execute(context);
                completedSteps.add(step);
            }
            execution.setStatus(SagaStatus.COMPLETED);
            sagaExecutionRepository.save(execution);
            log.info("Saga {} completed for expense {} (org {})", sagaType, expenseId, orgId);

        } catch (Exception e) {
            log.error("Saga {} failed at step '{}' for expense {} (org {}): {}",
                    sagaType, execution.getCurrentStep(), expenseId, orgId, e.getMessage());

            execution.setStatus(SagaStatus.COMPENSATING);
            execution.setLastError(e.getMessage());
            sagaExecutionRepository.save(execution);

            for (int i = completedSteps.size() - 1; i >= 0; i--) {
                SagaStep<ExpenseSagaContext> step = completedSteps.get(i);
                try {
                    step.compensate(context);
                } catch (Exception compensationError) {
                    // A failed compensation is a genuine operational
                    // emergency (state is now inconsistent and the
                    // automatic undo didn't work) -- logged loudly rather
                    // than swallowed, so it surfaces to on-call alerting.
                    log.error("COMPENSATION FAILED for step '{}' in saga {} (expense {}, org {}): {}",
                            step.name(), sagaType, expenseId, orgId, compensationError.getMessage(), compensationError);
                }
            }

            execution.setStatus(SagaStatus.COMPENSATED);
            sagaExecutionRepository.save(execution);
        }
    }
}
